import { cleanup, fireEvent, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { seatMap, hold, json } from "../test/bookingFixtures";
import {
  stubApi,
  renderPage,
  tick,
  reachPassengerDetails,
} from "../test/renderStudentBookingPage";

describe("StudentBookingPage", () => {
  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("shows the hold countdown and returns to seat selection when it expires", async () => {
    vi.useFakeTimers();
    let seatsRequested = 0;
    stubApi({
      hold: () => json(hold(new Date(Date.now() + 60_000).toISOString()), 201),
      seats: () => {
        seatsRequested += 1;
        return json(seatMap());
      },
    });

    renderPage();
    await tick();
    fireEvent.click(screen.getByRole("button", { name: /Mega Bangna/ }));
    await tick();
    fireEvent.click(screen.getByRole("button", { name: "Seat A1, available" }));
    fireEvent.click(screen.getByRole("button", { name: "Hold these seats" }));
    await tick();

    expect(screen.getByRole("timer")).toHaveTextContent("Seats held for 1:00");
    await tick(30_000);
    expect(screen.getByRole("timer")).toHaveTextContent("Seats held for 0:30");

    const beforeExpiry = seatsRequested;
    await tick(30_000);

    expect(
      screen.getAllByRole("status").map((region) => region.textContent),
    ).toContain("Your seat hold expired. Choose your seats again.");
    expect(
      screen.getByRole("group", { name: "Seat map" }),
    ).toBeInTheDocument();
    expect(seatsRequested).toBeGreaterThan(beforeExpiry);
  });

  it("sends the student back to seat selection when the hold expired before confirmation", async () => {
    let seatsRequested = 0;
    stubApi({
      seats: () => {
        seatsRequested += 1;
        return json(seatMap());
      },
      createBooking: () =>
        json({ detail: "This seat hold has expired.", code: "hold_expired" }, 409),
    });

    renderPage();
    await reachPassengerDetails();
    const beforeFailure = seatsRequested;
    fireEvent.click(screen.getByRole("button", { name: "Confirm booking" }));

    expect(
      await screen.findByText(
        "Your seat hold expired before the booking was confirmed. Choose your seats again.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole("group", { name: "Seat map" })).toBeInTheDocument();
    await vi.waitFor(() =>
      expect(seatsRequested).toBeGreaterThan(beforeFailure),
    );
  });

  it("sends the student back to seat selection when the hold no longer exists", async () => {
    stubApi({
      createBooking: () =>
        json(
          { detail: "This seat hold no longer exists.", code: "hold_not_found" },
          404,
        ),
    });

    renderPage();
    await reachPassengerDetails();
    fireEvent.click(screen.getByRole("button", { name: "Confirm booking" }));

    expect(
      await screen.findByText(
        "That seat hold is no longer available. Choose your seats again.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole("group", { name: "Seat map" })).toBeInTheDocument();
  });

  it("releases the hold when the student goes back to change seats", async () => {
    const fetcher = stubApi();

    renderPage();
    await reachPassengerDetails();
    fireEvent.click(screen.getByRole("button", { name: "Change seats" }));

    expect(
      await screen.findByRole("group", { name: "Seat map" }),
    ).toBeInTheDocument();
    await vi.waitFor(() =>
      expect(
        fetcher.mock.calls.some(
          ([url]) => url === "/api/v1/seat-holds/hold-1/release",
        ),
      ).toBe(true),
    );
  });

  it("lets the student take back seats their own failed release left held", async () => {
    let seatsRequested = 0;
    stubApi({
      release: () => json({ detail: "The hold could not be released." }, 500),
      seats: () => {
        seatsRequested += 1;
        return json(seatMap(seatsRequested < 3 ? "AVAILABLE" : "HELD_BY_YOU"));
      },
    });

    renderPage();
    await reachPassengerDetails();
    fireEvent.click(screen.getByRole("button", { name: "Change seats" }));

    fireEvent.click(
      await screen.findByRole("button", { name: "Seat A1, held by you" }),
    );

    expect(
      screen.getByRole("button", { name: "Seat A1, selected", pressed: true }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Hold these seats" }),
    ).toBeEnabled();
  });
});
