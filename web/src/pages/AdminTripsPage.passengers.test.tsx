import { cleanup, fireEvent, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { operations, seatMap } from "../test/operationsFixtures";
import {
  adminSession,
  json,
  renderAdminPage,
  stubAdminApi,
} from "../test/renderAdminPage";
import {
  drawer,
  freezeBangkokTime,
  openTrip,
  route,
  showList,
  tripAt,
  van,
} from "../test/tripFixtures";
import { AdminTripsPage } from "./AdminTripsPage";

const trip = tripAt("2026-01-05T09:00:00Z", { id: "trip-1" });
const back = { ...route, id: "route-2", origin: "Mega Bangna", destination: "AU" };
const returning = tripAt("2026-01-06T09:00:00Z", { id: "trip-2", routeId: "route-2" });
const opened = "Open trip on Mon 5 Jan · 16:00";

function stub(handlers: Parameters<typeof stubAdminApi>[0] = {}) {
  return stubAdminApi({
    "GET /trips": () => json([trip, returning]),
    "GET /routes": () => json([route, back]),
    "GET /vehicles": () => json([van]),
    "GET /operations/trips/trip-1": () => json(operations),
    "GET /api/v1/trips/trip-1/seats": () => json(seatMap(["BOOKED", "AVAILABLE"])),
    ...handlers,
  });
}

function callsTo(fetcher: ReturnType<typeof stub>, ending: string) {
  return fetcher.mock.calls.filter(([url]) => String(url).endsWith(ending));
}

async function openListed(path = "/admin/trips") {
  renderAdminPage(<AdminTripsPage session={adminSession} />, path);
  await showList();
  fireEvent.click(await screen.findByRole("button", { name: opened }));
}

describe("AdminTripsPage passengers", () => {
  beforeEach(() => freezeBangkokTime("2026-01-04T10:00"));

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("asks nothing about a trip's passengers until the trip is opened", async () => {
    const fetcher = stub();

    renderAdminPage(<AdminTripsPage session={adminSession} />);
    await showList();
    await screen.findByRole("button", { name: opened });

    expect(callsTo(fetcher, "/operations/trips/trip-1")).toHaveLength(0);
  });

  it("opens a trip on its passengers: seats, bookings by status and the queue", async () => {
    const fetcher = stub();

    await openListed();

    expect(drawer().getByRole("heading", { name: "Mon 5 Jan · 16:00" })).toBeInTheDocument();
    expect(drawer().getByText("AU → Mega Bangna")).toBeInTheDocument();
    expect(drawer().getByRole("radio", { name: "Passengers" })).toBeChecked();
    expect(await drawer().findByText("Somchai P.")).toBeInTheDocument();
    expect(drawer().getByText("#1")).toBeInTheDocument();
    expect(drawer().getByText("Malee K.")).toBeInTheDocument();
    expect(drawer().getByText("#2")).toBeInTheDocument();
    expect(drawer().getByText("Seat offered")).toBeInTheDocument();
    expect(drawer().getByText(/Offer ends/)).toBeInTheDocument();
    expect(drawer().getByText("—")).toBeInTheDocument();
    expect(drawer().getByText("2 of 2 seats claimed")).toBeInTheDocument();
    expect(drawer().getByText("Confirmed")).toBeInTheDocument();
    expect(drawer().getByText("3")).toBeInTheDocument();
    const [call] = callsTo(fetcher, "/operations/trips/trip-1");
    expect(call?.[0]).toBe("/api/v1/admin/operations/trips/trip-1");
    expect(new Headers(call?.[1]?.headers).get("Authorization")).toBe("Bearer admin-token");
  });

  it("draws each seat in the van with its state, and a legend", async () => {
    const fetcher = stub({
      "GET /api/v1/trips/trip-1/seats": () =>
        json(seatMap(["BOOKED", "HELD", "AVAILABLE", "HELD_BY_YOU"])),
    });

    await openListed();

    const plan = within(await drawer().findByRole("group", { name: "Seat occupancy" }));
    expect(plan.getByRole("img", { name: "A1, booked" })).toHaveTextContent("A1");
    expect(plan.getByRole("img", { name: "A2, held" })).toBeInTheDocument();
    expect(plan.getByRole("img", { name: "A3, open" })).toBeInTheDocument();
    expect(plan.getByRole("img", { name: "A4, held" })).toBeInTheDocument();
    const legend = within(drawer().getByRole("list", { name: "Seat legend" }));
    expect(legend.getAllByRole("listitem").map((item) => item.textContent)).toEqual([
      "open",
      "held",
      "booked",
    ]);
    const [call] = callsTo(fetcher, "/trips/trip-1/seats");
    expect(call?.[0]).toBe("/api/v1/trips/trip-1/seats");
    expect(new Headers(call?.[1]?.headers).get("Authorization")).toBe("Bearer admin-token");
  });

  it("says why the seat plan could not be read and keeps the rest of the trip", async () => {
    stub({ "GET /api/v1/trips/trip-1/seats": () => json({ detail: "Trip not found." }, 404) });

    await openListed();

    expect(await drawer().findByText("Could not load the seat plan")).toBeInTheDocument();
    expect(drawer().getByText("2 of 2 seats claimed")).toBeInTheDocument();
    expect(drawer().getByText("Somchai P.")).toBeInTheDocument();
  });

  it("says why the passengers could not be read and still lets the trip be edited", async () => {
    stub({ "GET /operations/trips/trip-1": () => json({ detail: "Trip not found." }, 404) });

    await openListed();

    expect(await drawer().findByText("Could not load this trip")).toBeInTheDocument();
    expect(drawer().getByText("Trip not found.")).toBeInTheDocument();
    fireEvent.click(drawer().getByRole("radio", { name: "Edit" }));
    expect(drawer().getByLabelText("Time")).toHaveValue("16:00");
  });

  it("opens a departure from the day drawer on its passengers", async () => {
    stub();

    renderAdminPage(<AdminTripsPage session={adminSession} />);
    const calendar = within(await screen.findByRole("region", { name: "Trip calendar" }));
    fireEvent.click(calendar.getByRole("button", { name: "Monday 5 January, 1 departure" }));
    fireEvent.click(drawer().getByRole("button", { name: "Open the 16:00 to Mega Bangna" }));

    expect(await drawer().findByText("Somchai P.")).toBeInTheDocument();
    expect(drawer().getByRole("heading", { name: "Mon 5 Jan · 16:00" })).toBeInTheDocument();
  });

  it("keeps a cancelled trip's passengers a tab away from its reason", async () => {
    stub({
      "GET /trips": () =>
        json([{ ...trip, status: "CANCELLED", cancellationReason: "Driver unwell" }]),
    });

    renderAdminPage(<AdminTripsPage session={adminSession} />);
    await showList();
    fireEvent.click(await screen.findByRole("radio", { name: "Cancelled" }));
    await openTrip("Open cancelled trip on Mon 5 Jan · 16:00", "Passengers");

    expect(drawer().getByText("AU → Mega Bangna · Cancelled")).toBeInTheDocument();
    expect(await drawer().findByText("Somchai P.")).toBeInTheDocument();
    fireEvent.click(drawer().getByRole("radio", { name: "Details" }));
    expect(drawer().getByText("Driver unwell")).toBeInTheDocument();
    expect(drawer().queryByText("Somchai P.")).toBeNull();
  });

  it("opens the passengers of a trip listed under a chosen route", async () => {
    const fetcher = stub({
      "GET /operations/trips/trip-2": () => json({ ...operations, tripId: "trip-2" }),
    });

    renderAdminPage(<AdminTripsPage session={adminSession} />, "/admin/trips?routes=route-2");
    await showList();
    fireEvent.click(await screen.findByRole("button", { name: "Open trip on Tue 6 Jan · 16:00" }));

    expect(screen.queryByRole("button", { name: opened })).toBeNull();
    expect(await drawer().findByText("Mega Bangna → AU")).toBeInTheDocument();
    await vi.waitFor(() => expect(callsTo(fetcher, "/operations/trips/trip-2")).toHaveLength(1));
    expect(callsTo(fetcher, "/operations/trips/trip-1")).toHaveLength(0);
  });
});
