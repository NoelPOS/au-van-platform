import { cleanup, fireEvent, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  adminSession,
  json,
  renderAdminPage,
  sentTo,
  stubAdminApi,
} from "../test/renderAdminPage";
import { AdminVansPage } from "./AdminVansPage";

const layout = {
  id: "layout-1",
  name: "Commuter",
  seats: [
    { label: "A1", rowNumber: 1, columnNumber: 1 },
    { label: "A2", rowNumber: 1, columnNumber: 2 },
  ],
};
const hiace = {
  id: "layout-2",
  name: "Hiace",
  seats: [{ label: "A1", rowNumber: 1, columnNumber: 1 }],
};
const van = {
  id: "van-1",
  code: "VAN-01",
  name: "White Hiace",
  seatLayoutId: "layout-1",
  status: "ACTIVE",
};

function renderPage() {
  renderAdminPage(<AdminVansPage session={adminSession} />);
}

function drawer() {
  return within(screen.getByRole("dialog"));
}

describe("AdminVansPage", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("adds a van from the drawer with the layout card it was given", async () => {
    const fetcher = stubAdminApi({
      "GET /seat-layouts": () => json([layout, hiace]),
      "POST /vehicles": () => json(van, 201),
    });

    renderPage();

    expect(await screen.findByText("No vans yet")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "New van" }));
    fireEvent.change(drawer().getByLabelText("Van code"), {
      target: { value: "VAN-01" },
    });
    fireEvent.change(drawer().getByLabelText("Name"), {
      target: { value: "White Hiace" },
    });
    const commuter = drawer().getByRole("radio", { name: "Commuter" });
    expect(commuter).toBeChecked();
    expect(commuter).toHaveAccessibleDescription("2 seats");
    expect(drawer().getByRole("radio", { name: "Hiace" })).toHaveAccessibleDescription(
      "1 seat",
    );
    fireEvent.click(drawer().getByRole("radio", { name: "Hiace" }));
    fireEvent.click(drawer().getByRole("button", { name: "Add van" }));

    expect(await screen.findByText("Van added")).toBeInTheDocument();
    expect(sentTo(fetcher, "POST /vehicles")).toEqual([
      { code: "VAN-01", name: "White Hiace", seatLayoutId: "layout-2" },
    ]);
  });

  it("lists a van with its layout and saves an edit with its status", async () => {
    const fetcher = stubAdminApi({
      "GET /seat-layouts": () => json([layout]),
      "GET /vehicles": () => json([van]),
      "PUT /vehicles/van-1": (body) => json({ ...van, ...(body as object) }),
    });

    renderPage();

    const row = await screen.findByRole("row", { name: /VAN-01/ });
    expect(row).toHaveTextContent("Commuter · 2 seats");
    fireEvent.click(within(row).getByRole("button", { name: "Edit VAN-01" }));
    expect(drawer().getByRole("radio", { name: "Commuter" })).toBeChecked();
    fireEvent.change(drawer().getByLabelText("Name"), {
      target: { value: "Blue Hiace" },
    });
    fireEvent.click(drawer().getByRole("button", { name: "Save van" }));

    expect(await screen.findByText("Van updated")).toBeInTheDocument();
    expect(sentTo(fetcher, "PUT /vehicles/van-1")).toEqual([
      {
        code: "VAN-01",
        name: "Blue Hiace",
        seatLayoutId: "layout-1",
        status: "ACTIVE",
      },
    ]);
  });

  it("asks before it marks a van inactive", async () => {
    const fetcher = stubAdminApi({
      "GET /seat-layouts": () => json([layout]),
      "GET /vehicles": () => json([van]),
      "PUT /vehicles/van-1": (body) => json({ ...van, ...(body as object) }),
    });

    renderPage();

    fireEvent.click(await screen.findByRole("button", { name: "Edit VAN-01" }));
    fireEvent.click(drawer().getByRole("radio", { name: "Inactive" }));
    fireEvent.click(drawer().getByRole("button", { name: "Save van" }));
    expect(drawer().getByText(/Mark this van as inactive/)).toBeInTheDocument();
    expect(sentTo(fetcher, "PUT /vehicles/van-1")).toEqual([]);

    fireEvent.click(drawer().getByRole("button", { name: "Yes, save" }));

    expect(await screen.findByText("Van updated")).toBeInTheDocument();
    expect(sentTo(fetcher, "PUT /vehicles/van-1")).toEqual([
      expect.objectContaining({ status: "INACTIVE" }),
    ]);
  });

  it("points to seat layouts instead of a form when there are none", async () => {
    stubAdminApi({});

    renderPage();

    fireEvent.click(await screen.findByRole("button", { name: "New van" }));

    expect(drawer().getByText("No seat layouts yet")).toBeInTheDocument();
    expect(
      drawer().getByRole("link", { name: "Create a seat layout" }),
    ).toHaveAttribute("href", "/admin/seat-layouts");
    expect(drawer().queryByLabelText("Van code")).toBeNull();
  });
});
