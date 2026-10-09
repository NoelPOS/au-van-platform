import { cleanup, fireEvent, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  adminSession,
  json,
  renderAdminPage,
  sentTo,
  stubAdminApi,
} from "../test/renderAdminPage";
import {
  drawer,
  freezeBangkokTime,
  layout,
  route,
  showList,
  tripAt,
  van,
} from "../test/tripFixtures";
import { AdminTripsPage } from "./AdminTripsPage";

const trip = tripAt("2026-01-05T09:00:00Z", { id: "trip-1" });
const editTrip = { name: "Edit trip on Mon 5 Jan · 16:00" };

async function renderPage() {
  renderAdminPage(<AdminTripsPage session={adminSession} />);
  await showList();
}

describe("AdminTripsPage", () => {
  beforeEach(() => freezeBangkokTime("2026-01-04T10:00"));

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("lists a trip under its Bangkok day with its time, route line and van", async () => {
    stubAdminApi({
      "GET /trips": () => json([trip]),
      "GET /routes": () => json([route]),
      "GET /vehicles": () => json([van]),
      "GET /seat-layouts": () => json([layout]),
    });

    await renderPage();

    const day = await screen.findByRole("table", { name: "Trips on Mon 5 Jan" });
    const row = within(day).getByRole("row", { name: /VAN-01/ });
    expect(row).toHaveTextContent("16:00");
    expect(
      within(row).getByRole("cell", { name: "AU → Mega Bangna" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: /Tomorrow · Mon 5 Jan/ }),
    ).toBeInTheDocument();
  });

  it("moves an edited trip to another day and time as one departure", async () => {
    const fetcher = stubAdminApi({
      "GET /trips": () => json([trip]),
      "PUT /trips/trip-1": (body) => json({ ...trip, ...(body as object) }),
    });

    await renderPage();
    fireEvent.click(await screen.findByRole("button", editTrip));

    const days = within(drawer().getByRole("group", { name: "Day" }));
    expect(days.getByRole("button", { name: "Tomorrow" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    fireEvent.click(days.getByRole("button", { name: "Wed 7" }));
    expect(days.getByRole("button", { name: "Tomorrow" })).toHaveAttribute(
      "aria-pressed",
      "false",
    );
    fireEvent.change(drawer().getByLabelText("Time"), {
      target: { value: "7:05" },
    });
    fireEvent.click(drawer().getByRole("button", { name: "Save trip" }));

    expect(await screen.findByText("Trip updated")).toBeInTheDocument();
    expect(sentTo(fetcher, "PUT /trips/trip-1")).toEqual([
      { departureAt: "2026-01-07T00:05:00.000Z" },
    ]);
  });

  it("shows a retryable failure when an inventory request is rejected", async () => {
    let refuse = true;
    stubAdminApi({
      "GET /trips": () =>
        refuse ? json({ detail: "Access denied." }, 403) : json([]),
    });

    renderAdminPage(<AdminTripsPage session={adminSession} />);

    expect(await screen.findByText("Could not load trips")).toBeInTheDocument();
    expect(screen.getByText("Access denied.")).toBeInTheDocument();
    refuse = false;
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));
    await showList();

    expect(await screen.findByText("No trips yet")).toBeInTheDocument();
    expect(screen.queryByRole("radiogroup", { name: "Show trips" })).toBeNull();
  });
});
