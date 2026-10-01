import { cleanup, fireEvent, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  adminSession,
  json,
  renderAdminPage,
  sentTo,
  stubAdminApi,
} from "../test/renderAdminPage";
import { AdminRoutesPage } from "./AdminRoutesPage";

const route = {
  id: "route-1",
  origin: "AU",
  destination: "Mega Bangna",
  fare: 35,
  durationMinutes: 45,
  status: "ACTIVE",
};

function renderPage() {
  renderAdminPage(<AdminRoutesPage session={adminSession} />);
}

function drawer() {
  return within(screen.getByRole("dialog"));
}

describe("AdminRoutesPage", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("creates a route from the drawer and sends it to the admin API", async () => {
    const fetcher = stubAdminApi({
      "POST /routes": () => json(route, 201),
    });

    renderPage();

    expect(await screen.findByText("No routes yet")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "New route" }));
    fireEvent.change(drawer().getByLabelText("Origin"), {
      target: { value: "AU" },
    });
    fireEvent.change(drawer().getByLabelText("Destination"), {
      target: { value: "Mega Bangna" },
    });
    fireEvent.click(drawer().getByRole("button", { name: "Create route" }));

    expect(await screen.findByText("Route created")).toBeInTheDocument();
    expect(sentTo(fetcher, "POST /routes")).toEqual([
      {
        origin: "AU",
        destination: "Mega Bangna",
        fare: 0,
        durationMinutes: 30,
      },
    ]);
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("keeps the drawer open with the API's reason when a route is refused", async () => {
    stubAdminApi({
      "POST /routes": () => json({ detail: "That route already exists." }, 409),
    });

    renderPage();

    await screen.findByText("No routes yet");
    fireEvent.click(screen.getByRole("button", { name: "New route" }));
    fireEvent.change(drawer().getByLabelText("Origin"), {
      target: { value: "AU" },
    });
    fireEvent.change(drawer().getByLabelText("Destination"), {
      target: { value: "Mega Bangna" },
    });
    fireEvent.click(drawer().getByRole("button", { name: "Create route" }));

    expect(await drawer().findByRole("alert")).toHaveTextContent(
      "That route already exists.",
    );
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.queryByText("Route created")).toBeNull();
  });

  it("lists routes as a route line and edits one in the drawer", async () => {
    const fetcher = stubAdminApi({
      "GET /routes": () => json([route]),
      "PUT /routes/route-1": (body) => json({ ...route, ...(body as object) }),
    });

    renderPage();

    expect(
      await screen.findByRole("cell", { name: "AU → Mega Bangna" }),
    ).toBeInTheDocument();
    expect(screen.getByText("35.00 THB")).toBeInTheDocument();
    fireEvent.click(
      screen.getByRole("button", { name: "Edit AU → Mega Bangna" }),
    );
    expect(drawer().getByLabelText("Origin")).toHaveValue("AU");
    fireEvent.change(drawer().getByLabelText("Fare (THB)"), {
      target: { value: "40" },
    });
    fireEvent.click(drawer().getByRole("button", { name: "Save route" }));

    expect(await screen.findByText("Route updated")).toBeInTheDocument();
    expect(sentTo(fetcher, "PUT /routes/route-1")).toEqual([
      {
        origin: "AU",
        destination: "Mega Bangna",
        fare: 40,
        durationMinutes: 45,
        status: "ACTIVE",
      },
    ]);
  });

  it("asks before it marks a route inactive", async () => {
    const fetcher = stubAdminApi({
      "GET /routes": () => json([route]),
      "PUT /routes/route-1": (body) => json({ ...route, ...(body as object) }),
    });

    renderPage();

    fireEvent.click(
      await screen.findByRole("button", { name: "Edit AU → Mega Bangna" }),
    );
    const status = within(drawer().getByRole("radiogroup", { name: "Status" }));
    expect(status.getByRole("radio", { name: "Active" })).toBeChecked();
    fireEvent.click(status.getByRole("radio", { name: "Inactive" }));
    fireEvent.click(drawer().getByRole("button", { name: "Save route" }));

    const question = within(
      drawer().getByRole("group", { name: "Confirm change" }),
    );
    expect(
      question.getByText(/Mark this route as inactive/),
    ).toBeInTheDocument();
    expect(sentTo(fetcher, "PUT /routes/route-1")).toEqual([]);

    fireEvent.click(question.getByRole("button", { name: "Yes, save" }));

    expect(await screen.findByText("Route updated")).toBeInTheDocument();
    expect(sentTo(fetcher, "PUT /routes/route-1")).toEqual([
      expect.objectContaining({ status: "INACTIVE" }),
    ]);
  });

  it("closes the drawer without saving when the administrator cancels", async () => {
    const fetcher = stubAdminApi({});

    renderPage();

    await screen.findByText("No routes yet");
    fireEvent.click(screen.getByRole("button", { name: "New route" }));
    fireEvent.click(drawer().getByRole("button", { name: "Cancel" }));

    expect(screen.queryByRole("dialog")).toBeNull();
    expect(sentTo(fetcher, "POST /routes")).toEqual([]);
  });
});
