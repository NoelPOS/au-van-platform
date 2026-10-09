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
  route,
  showList,
  tripAt,
  tripOperations,
  van,
} from "../test/tripFixtures";
import { AdminTripsPage } from "./AdminTripsPage";

const trip = tripAt("2026-01-05T09:00:00Z", { id: "trip-1" });
const reason = "The van failed its safety check.";
const cancelled = tripAt("2026-01-06T01:00:00Z", {
  id: "trip-2",
  status: "CANCELLED",
  cancellationReason: reason,
});

function stub(update = () => json(trip)) {
  return stubAdminApi({
    "GET /trips": () => json([trip, cancelled]),
    "GET /routes": () => json([route]),
    "GET /vehicles": () => json([van]),
    "GET /operations/trips/trip-1": () => json(tripOperations("trip-1")),
    "PUT /trips/trip-1": update,
  });
}

async function editAndMove() {
  renderAdminPage(<AdminTripsPage session={adminSession} />);
  await showList();
  fireEvent.click(await screen.findByRole("button", { name: "Edit trip on Mon 5 Jan · 16:00" }));
  expect(drawer().queryByText(/get a LINE message with the new time/)).toBeNull();
  fireEvent.change(drawer().getByLabelText("Time"), { target: { value: "17:30" } });
}

describe("AdminTripsPage moving and cancelled trips", () => {
  beforeEach(() => freezeBangkokTime("2026-01-04T10:00"));

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("says how many passengers are told once a departure is moved", async () => {
    const fetcher = stub();

    await editAndMove();

    expect(
      await drawer().findByText("3 booked passengers get a LINE message with the new time."),
    ).toBeInTheDocument();
    fireEvent.click(drawer().getByRole("button", { name: "Save trip" }));
    await vi.waitFor(() =>
      expect(sentTo(fetcher, "PUT /trips/trip-1")).toEqual([
        { departureAt: "2026-01-05T10:30:00.000Z" },
      ]),
    );
  });

  it.each([
    ["departure_too_soon", 400, "A trip can only be moved to a time at least 30 minutes from now."],
    ["vehicle_already_scheduled", 409, "This vehicle already has a trip at that departure time."],
    ["trip_departed", 409, "This trip has already departed and can no longer be changed."],
  ])("keeps the edit open and says why when the API answers %s", async (code, status, detail) => {
    stub(() => json({ code, detail }, status));

    await editAndMove();
    fireEvent.click(drawer().getByRole("button", { name: "Save trip" }));

    expect(await drawer().findByRole("alert")).toHaveTextContent(detail);
    expect(drawer().getByRole("button", { name: "Save trip" })).toBeEnabled();
  });

  it("shows a cancelled trip with its reason instead of an edit form", async () => {
    stub();

    renderAdminPage(<AdminTripsPage session={adminSession} />);
    await showList();
    fireEvent.click(await screen.findByRole("radio", { name: "Cancelled" }));
    const row = within(screen.getByRole("table", { name: "Trips on Tue 6 Jan" }));
    expect(row.getByText(reason)).toBeInTheDocument();
    fireEvent.click(row.getByRole("button", { name: "See cancelled trip on Tue 6 Jan · 08:00" }));

    expect(drawer().getByRole("heading", { name: "Cancelled trip" })).toBeInTheDocument();
    expect(drawer().getByText(reason)).toBeInTheDocument();
    expect(drawer().queryByRole("button", { name: "Save trip" })).toBeNull();
    expect(drawer().queryByRole("button", { name: "Cancel this trip…" })).toBeNull();
  });

  it("shows the reason beside a cancelled departure in the day drawer", async () => {
    stub();

    renderAdminPage(<AdminTripsPage session={adminSession} />);
    const calendar = within(await screen.findByRole("region", { name: "Trip calendar" }));
    fireEvent.click(calendar.getByRole("button", { name: "Tuesday 6 January, 1 departure" }));

    expect(
      drawer().getByRole("button", { name: "See the cancelled 08:00 to Mega Bangna" }),
    ).toHaveTextContent(reason);
  });

  it("shows the reason under a cancelled departure in the week", async () => {
    stub();

    renderAdminPage(<AdminTripsPage session={adminSession} />);
    fireEvent.click(await screen.findByRole("radio", { name: "Week" }));
    fireEvent.click(screen.getByRole("button", { name: "Next week" }));

    const tuesday = within(screen.getByRole("region", { name: "Tuesday 6 January" }));
    expect(tuesday.getByText(reason)).toBeInTheDocument();
  });
});
