import { cleanup, fireEvent, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { trip, otherTrip, json } from "../test/bookingFixtures";
import {
  stubApi,
  renderPage,
  selectSeatA1,
  holdFailsWith,
  reachPassengerDetails,
} from "../test/renderStudentBookingPage";

describe("StudentBookingPage", () => {
  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("returns to the trip list when the trip is withdrawn before the hold", async () => {
    await holdFailsWith(
      { detail: "This trip is no longer available.", code: "trip_not_available" },
      409,
    );

    // Found by role, not by text: the notice is an error and has to reach a
    // screen reader as one the moment it appears.
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "That trip is no longer available. Choose another.",
    );
    expect(screen.getByText("Upcoming trips")).toBeInTheDocument();
  });

  it("drops the withdrawn trip from the list the student lands back on", async () => {
    // The message alone is not the remedy: with the application-wide thirty
    // second staleTime and the trips query permanently mounted, the refetch in
    // the transition is the only thing taking the dead trip off the list.
    let tripsRequested = 0;
    stubApi({
      trips: () => {
        tripsRequested += 1;
        return json(tripsRequested === 1 ? [trip, otherTrip] : [otherTrip]);
      },
      hold: () =>
        json(
          {
            detail: "This trip is no longer available.",
            code: "trip_not_available",
          },
          409,
        ),
    });

    renderPage();
    await selectSeatA1();
    fireEvent.click(screen.getByRole("button", { name: "Hold these seats" }));

    expect(
      await screen.findByText(
        "That trip is no longer available. Choose another.",
      ),
    ).toBeInTheDocument();
    await vi.waitFor(() =>
      expect(
        screen.queryByRole("button", { name: /Mega Bangna/ }),
      ).not.toBeInTheDocument(),
    );
    expect(
      screen.getByRole("button", { name: /Siam Paragon/ }),
    ).toBeInTheDocument();
  });

  it("returns to the trip list when the trip departs before the hold", async () => {
    await holdFailsWith(
      { detail: "This trip has already departed.", code: "trip_departed" },
      409,
    );

    expect(
      await screen.findByText("That trip has already departed. Choose another."),
    ).toBeInTheDocument();
    expect(screen.getByText("Upcoming trips")).toBeInTheDocument();
  });

  it("returns to the trip list when the hold finds no such trip", async () => {
    await holdFailsWith({ detail: "Trip not found." }, 404);

    expect(
      await screen.findByText(
        "That trip is no longer available. Choose another.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("Upcoming trips")).toBeInTheDocument();
  });

  it("returns to the trip list when the seat map says the trip is gone", async () => {
    stubApi({ seats: () => json({ detail: "Trip not found." }, 404) });

    renderPage();
    fireEvent.click(await screen.findByRole("button", { name: /Mega Bangna/ }));

    expect(
      await screen.findByText(
        "That trip is no longer available. Choose another.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("Upcoming trips")).toBeInTheDocument();
    expect(
      screen.queryByRole("group", { name: "Seat map" }),
    ).not.toBeInTheDocument();
  });

  it("sends the student back to the trip list when the trip is withdrawn", async () => {
    stubApi({
      createBooking: () =>
        json(
          {
            detail: "This trip is no longer available.",
            code: "trip_not_available",
          },
          409,
        ),
    });

    renderPage();
    await reachPassengerDetails();
    fireEvent.click(screen.getByRole("button", { name: "Confirm booking" }));

    expect(
      await screen.findByText(
        "That trip is no longer available. Choose another.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("Upcoming trips")).toBeInTheDocument();
  });

  it("sends the student back to the trip list when the trip has departed", async () => {
    stubApi({
      createBooking: () =>
        json(
          { detail: "This trip has already departed.", code: "trip_departed" },
          409,
        ),
    });

    renderPage();
    await reachPassengerDetails();
    fireEvent.click(screen.getByRole("button", { name: "Confirm booking" }));

    expect(
      await screen.findByText(
        "That trip has already departed. Choose another.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("Upcoming trips")).toBeInTheDocument();
  });
});
