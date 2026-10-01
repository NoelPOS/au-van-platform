import { cleanup, fireEvent, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  adminSession,
  json,
  renderAdminPage,
  sentTo,
  stubAdminApi,
} from "../test/renderAdminPage";
import { AdminTripsPage } from "./AdminTripsPage";

const route = {
  id: "route-1",
  origin: "AU",
  destination: "Mega Bangna",
  fare: 35,
  durationMinutes: 45,
  status: "ACTIVE",
};
const van = { id: "van-1", code: "VAN-01", name: "Hiace", seatLayoutId: "l", status: "ACTIVE" };
const trip = {
  id: "trip-1",
  routeId: "route-1",
  vehicleId: "van-1",
  departureAt: "2026-01-05T09:00:00Z",
  fare: 35,
  durationMinutes: 45,
  status: "ACTIVE",
  seats: [],
};

function renderPage() {
  renderAdminPage(<AdminTripsPage session={adminSession} />);
}

function drawer() {
  return within(screen.getByRole("dialog"));
}

const editTrip = { name: "Edit trip on Mon 5 Jan · 16:00" };

describe("AdminTripsPage", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("lists a trip with its Bangkok departure, route line and van", async () => {
    stubAdminApi({
      "GET /trips": () => json([trip]),
      "GET /routes": () => json([route]),
      "GET /vehicles": () => json([van]),
    });

    renderPage();

    const row = await screen.findByRole("row", { name: /VAN-01/ });
    expect(row).toHaveTextContent("Mon 5 Jan");
    expect(row).toHaveTextContent("16:00");
    expect(within(row).getByRole("cell", { name: "AU → Mega Bangna" })).toBeInTheDocument();
  });

  it("schedules a trip from the drawer in Bangkok time", async () => {
    const fetcher = stubAdminApi({
      "GET /routes": () => json([route]),
      "GET /vehicles": () => json([van]),
      "POST /trips": () => json(trip, 201),
    });

    renderPage();

    fireEvent.click(await screen.findByRole("button", { name: "New trip" }));
    expect(drawer().getByLabelText("Route")).toHaveValue("route-1");
    expect(drawer().getByLabelText("Van")).toHaveValue("van-1");
    fireEvent.change(drawer().getByLabelText("Date"), {
      target: { value: "2026-01-05" },
    });
    fireEvent.change(drawer().getByLabelText("Time"), {
      target: { value: "16:00" },
    });
    fireEvent.click(drawer().getByRole("button", { name: "Schedule trip" }));

    expect(await screen.findByText("Trip scheduled")).toBeInTheDocument();
    expect(sentTo(fetcher, "POST /trips")).toEqual([
      {
        routeId: "route-1",
        vehicleId: "van-1",
        departureAt: "2026-01-05T09:00:00.000Z",
      },
    ]);
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("explains what is missing instead of a form when there is no route or van", async () => {
    const fetcher = stubAdminApi({ "GET /routes": () => json([route]) });

    renderPage();

    fireEvent.click(await screen.findByRole("button", { name: "New trip" }));

    expect(drawer().getByText("Not quite ready to schedule")).toBeInTheDocument();
    expect(drawer().getByRole("link", { name: "Add a van" })).toHaveAttribute(
      "href",
      "/admin/vans",
    );
    expect(drawer().queryByRole("button", { name: "Schedule trip" })).toBeNull();
    expect(sentTo(fetcher, "POST /trips")).toEqual([]);
  });

  it("offers only trip statuses the API can deserialise", async () => {
    const fetcher = stubAdminApi({
      "GET /trips": () => json([trip]),
      "PUT /trips/trip-1": (body) => json({ ...trip, ...(body as object) }),
    });

    renderPage();
    fireEvent.click(await screen.findByRole("button", editTrip));

    const offered = Array.from(
      (drawer().getByLabelText("Status") as HTMLSelectElement).options,
    ).map((option) => option.value);
    expect(offered).toEqual(["ACTIVE", "CANCELLED"]);
    fireEvent.click(drawer().getByRole("button", { name: "Close" }));

    for (const status of offered) {
      fireEvent.click(screen.getByRole("button", editTrip));
      fireEvent.change(drawer().getByLabelText("Status"), {
        target: { value: status },
      });
      fireEvent.click(drawer().getByRole("button", { name: "Save trip" }));
      if (status === "CANCELLED")
        fireEvent.click(drawer().getByRole("button", { name: "Yes, save" }));

      await vi.waitFor(() =>
        expect(sentTo(fetcher, "PUT /trips/trip-1")).toContainEqual({
          departureAt: "2026-01-05T09:00:00.000Z",
          status,
        }),
      );
      await vi.waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    }
  });

  it("asks before it cancels a trip and saves nothing if the administrator goes back", async () => {
    const fetcher = stubAdminApi({ "GET /trips": () => json([trip]) });

    renderPage();
    fireEvent.click(await screen.findByRole("button", editTrip));
    fireEvent.change(drawer().getByLabelText("Status"), {
      target: { value: "CANCELLED" },
    });
    fireEvent.click(drawer().getByRole("button", { name: "Save trip" }));

    expect(
      drawer().getByText(/Mark this trip as cancelled/),
    ).toBeInTheDocument();
    fireEvent.click(drawer().getByRole("button", { name: "Go back" }));

    expect(drawer().getByRole("button", { name: "Save trip" })).toBeInTheDocument();
    expect(sentTo(fetcher, "PUT /trips/trip-1")).toEqual([]);
  });

  it("shows a retryable failure when an inventory request is rejected", async () => {
    let refuse = true;
    stubAdminApi({
      "GET /trips": () =>
        refuse ? json({ detail: "Access denied." }, 403) : json([]),
    });

    renderPage();

    expect(await screen.findByText("Could not load trips")).toBeInTheDocument();
    expect(screen.getByText("Access denied.")).toBeInTheDocument();
    refuse = false;
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));

    expect(await screen.findByText("No trips yet")).toBeInTheDocument();
  });
});
