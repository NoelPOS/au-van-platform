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

function stub(cancel = () => json({ ...trip, status: "CANCELLED" })) {
  return stubAdminApi({
    "GET /trips": () => json([trip]),
    "GET /routes": () => json([route]),
    "GET /vehicles": () => json([van]),
    "GET /operations/trips/trip-1": () => json(tripOperations("trip-1")),
    "POST /trips/trip-1/cancel": cancel,
  });
}

async function openCancel() {
  renderAdminPage(<AdminTripsPage session={adminSession} />);
  await showList();
  fireEvent.click(await screen.findByRole("button", { name: "Edit trip on Mon 5 Jan · 16:00" }));
  fireEvent.click(drawer().getByRole("button", { name: "Cancel this trip…" }));
  await drawer().findByRole("region", { name: "Who this affects" });
}

function giveReason(reason: string) {
  fireEvent.change(drawer().getByLabelText("Reason passengers will read"), {
    target: { value: reason },
  });
}

describe("AdminTripsPage cancelling a trip", () => {
  beforeEach(() => freezeBangkokTime("2026-01-04T10:00"));

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("says who a cancellation affects before anything is sent", async () => {
    stub();

    await openCancel();

    expect(drawer().getByRole("heading", { name: "Cancel this trip?" })).toBeInTheDocument();
    const impact = drawer().getByRole("region", { name: "Who this affects" });
    expect(impact).toHaveTextContent(
      "3 booked passengers lose their seats and get a LINE message with your reason",
    );
    expect(impact).toHaveTextContent("2 have paid, so refunds fall due under Refunds");
    expect(impact).toHaveTextContent("1 student waiting for a seat is taken off the queue and told");
  });

  it("refuses to cancel without a reason", async () => {
    const fetcher = stub();

    await openCancel();
    giveReason("   ");
    fireEvent.click(drawer().getByRole("button", { name: "Cancel trip" }));

    expect(drawer().getByText("Say why, so passengers are not left guessing.")).toBeInTheDocument();
    expect(drawer().getByLabelText("Reason passengers will read")).toHaveAttribute("aria-invalid", "true");
    expect(sentTo(fetcher, "POST /trips/trip-1/cancel")).toEqual([]);
  });

  it("caps the reason at the 300 characters the API accepts", async () => {
    stub();

    await openCancel();

    expect(drawer().getByLabelText("Reason passengers will read")).toHaveAttribute("maxLength", "300");
    giveReason("Driver unwell");
    expect(drawer().getByText("13/300")).toBeInTheDocument();
  });

  it("cancels the trip with the trimmed reason and closes the drawer", async () => {
    const fetcher = stub();

    await openCancel();
    giveReason("  The van failed its safety check.  ");
    fireEvent.click(drawer().getByRole("button", { name: "Cancel trip" }));

    expect(await screen.findByText("Trip cancelled")).toBeInTheDocument();
    expect(sentTo(fetcher, "POST /trips/trip-1/cancel")).toEqual([
      { reason: "The van failed its safety check." },
    ]);
    await vi.waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
  });

  it("goes back to the trip without cancelling when the administrator keeps it", async () => {
    const fetcher = stub();

    await openCancel();
    fireEvent.click(drawer().getByRole("button", { name: "Keep trip" }));

    expect(drawer().getByRole("heading", { name: "Edit trip" })).toBeInTheDocument();
    expect(sentTo(fetcher, "POST /trips/trip-1/cancel")).toEqual([]);
  });

  it.each([
    ["trip_departed", "This trip has already departed and can no longer be changed."],
    ["trip_cancelled", "This trip has been cancelled and can no longer be changed. Create a new trip instead."],
    ["trip_not_found", "Trip not found."],
  ])("keeps the drawer open and says why when the API answers %s", async (code, detail) => {
    stub(() => json({ code, detail }, code === "trip_not_found" ? 404 : 409));

    await openCancel();
    giveReason("Driver unwell");
    fireEvent.click(drawer().getByRole("button", { name: "Cancel trip" }));

    expect(await drawer().findByRole("alert")).toHaveTextContent(detail);
    expect(drawer().getByRole("button", { name: "Cancel trip" })).toBeEnabled();
  });

  it("says so when nobody is on the trip", async () => {
    stubAdminApi({
      "GET /trips": () => json([trip]),
      "GET /operations/trips/trip-1": () =>
        json({ ...tripOperations("trip-1"), bookingsByStatus: [], waitlist: [] }),
    });

    renderAdminPage(<AdminTripsPage session={adminSession} />);
    await showList();
    fireEvent.click(await screen.findByRole("button", { name: "Edit trip on Mon 5 Jan · 16:00" }));
    fireEvent.click(drawer().getByRole("button", { name: "Cancel this trip…" }));

    expect(
      await drawer().findByText("Nobody has booked or queued for this departure."),
    ).toBeInTheDocument();
  });

  it("warns when it cannot count the passengers, and still lets the trip be cancelled", async () => {
    const fetcher = stubAdminApi({
      "GET /trips": () => json([trip]),
      "GET /operations/trips/trip-1": () => json({ detail: "Down." }, 503),
      "POST /trips/trip-1/cancel": () => json({ ...trip, status: "CANCELLED" }),
    });

    renderAdminPage(<AdminTripsPage session={adminSession} />);
    await showList();
    fireEvent.click(await screen.findByRole("button", { name: "Edit trip on Mon 5 Jan · 16:00" }));
    fireEvent.click(drawer().getByRole("button", { name: "Cancel this trip…" }));

    expect(await drawer().findByRole("alert")).toHaveTextContent("Could not count who is booked on this trip.");
    giveReason("Driver unwell");
    fireEvent.click(drawer().getByRole("button", { name: "Cancel trip" }));
    await vi.waitFor(() => expect(sentTo(fetcher, "POST /trips/trip-1/cancel")).toHaveLength(1));
  });

  it("offers no cancel on a trip that has already left", async () => {
    stubAdminApi({ "GET /trips": () => json([tripAt("2026-01-04T01:00:00Z", { id: "gone" })]) });

    renderAdminPage(<AdminTripsPage session={adminSession} />);
    await showList();
    fireEvent.click(await screen.findByRole("radio", { name: "Past" }));
    fireEvent.click(await screen.findByRole("button", { name: "Edit trip on Sun 4 Jan · 08:00" }));

    expect(within(screen.getByRole("dialog")).queryByRole("button", { name: "Cancel this trip…" })).toBeNull();
  });
});
