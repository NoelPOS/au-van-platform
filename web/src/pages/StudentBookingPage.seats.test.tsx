import { cleanup, fireEvent, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { trip, seatMap, json } from "../test/bookingFixtures";
import {
  stubApi,
  renderPage,
  tick,
  selectSeatA1,
} from "../test/renderStudentBookingPage";
import type { SeatState } from "../types/booking";

describe("StudentBookingPage", () => {
  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("names each seat state so it is not carried by colour alone", async () => {
    stubApi();

    renderPage();
    fireEvent.click(await screen.findByRole("button", { name: /Mega Bangna/ }));

    expect(
      await screen.findByRole("button", { name: "Seat A1, available" }),
    ).toBeEnabled();
    expect(
      screen.getByRole("button", { name: "Seat A2, held by someone else" }),
    ).toBeDisabled();
    expect(
      screen.getByRole("button", { name: "Seat B1, held by you" }),
    ).toBeEnabled();
    expect(
      screen.getByRole("button", { name: "Seat B2, booked" }),
    ).toBeDisabled();

    expect(
      screen.getByRole("button", { name: "Seat A1, available", pressed: false }),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Seat A1, available" }));
    expect(
      screen.getByRole("button", { name: "Seat A1, selected", pressed: true }),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Seat A1, selected" }));
    expect(
      screen.getByRole("button", { name: "Seat A1, available", pressed: false }),
    ).toBeInTheDocument();
  });

  it("shows a loading state while the seat map is being fetched", async () => {
    let releaseSeats: (response: Response) => void = () => {};
    stubApi({
      seats: () =>
        new Promise<Response>((resolve) => {
          releaseSeats = resolve;
        }),
    });

    renderPage();
    fireEvent.click(await screen.findByRole("button", { name: /Mega Bangna/ }));

    expect(await screen.findByText("Loading seats…")).toBeInTheDocument();
    releaseSeats(json(seatMap()));
  });

  it("explains a lost seat race and refreshes the map instead of failing silently", async () => {
    let seatsRequested = 0;
    stubApi({
      seats: () => {
        seatsRequested += 1;
        return json(seatMap(seatsRequested === 1 ? "AVAILABLE" : "HELD"));
      },
      hold: () =>
        json(
          {
            detail: "One or more seats are no longer available.",
            code: "seat_taken",
          },
          409,
        ),
    });

    renderPage();
    await selectSeatA1();
    fireEvent.click(screen.getByRole("button", { name: "Hold these seats" }));

    expect(
      await screen.findByText("One or more seats are no longer available."),
    ).toBeInTheDocument();
    expect(
      await screen.findByRole("button", { name: "Seat A1, held by someone else" }),
    ).toBeInTheDocument();
    expect(screen.getByText("Choose at least one seat.")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Hold these seats" }),
    ).toBeDisabled();
  });

  it("keeps the student on the seat step when the map fails for another reason", async () => {
    stubApi({
      seats: () => json({ detail: "The seat map could not be loaded." }, 500),
    });

    renderPage();
    fireEvent.click(await screen.findByRole("button", { name: /Mega Bangna/ }));

    expect(
      await screen.findByText("The seat map could not be loaded."),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Back to trips" }),
    ).toBeInTheDocument();
  });

  it("drops a selected seat that the poll shows has been taken", async () => {
    vi.useFakeTimers();
    let seatsRequested = 0;
    stubApi({
      seats: () => {
        seatsRequested += 1;
        return json(seatMap(seatsRequested === 1 ? "AVAILABLE" : "BOOKED"));
      },
    });

    renderPage();
    await tick();
    fireEvent.click(screen.getByRole("button", { name: /Mega Bangna/ }));
    await tick();
    fireEvent.click(screen.getByRole("button", { name: "Seat A1, available" }));

    await tick(10_000);

    expect(seatsRequested).toBeGreaterThan(1);
    expect(
      screen.getAllByRole("status").map((region) => region.textContent),
    ).toContain(
      "Seat A1 was taken by another student and has been removed from your selection.",
    );
    expect(screen.getByText("Choose at least one seat.")).toBeInTheDocument();
  });

  it("pluralises the message when the poll takes more than one selected seat", async () => {
    vi.useFakeTimers();
    let seatsRequested = 0;
    stubApi({
      seats: () => {
        seatsRequested += 1;
        if (seatsRequested === 1) return json(seatMap());
        return json({
          ...seatMap(),
          seats: seatMap().seats.map((seat) => ({
            ...seat,
            state: "BOOKED" as SeatState,
          })),
        });
      },
    });

    renderPage();
    await tick();
    fireEvent.click(screen.getByRole("button", { name: /Mega Bangna/ }));
    await tick();
    fireEvent.click(screen.getByRole("button", { name: "Seat A1, available" }));
    fireEvent.click(screen.getByRole("button", { name: "Seat B1, held by you" }));

    await tick(10_000);

    expect(
      screen.getByText(
        "Seats A1, B1 were taken by another student and have been removed from your selection.",
      ),
    ).toBeInTheDocument();
  });

  it("never serves a cached seat map to a student who comes back to a trip", async () => {
    let seatsRequested = 0;
    stubApi({
      seats: () => {
        seatsRequested += 1;
        return json(seatMap());
      },
    });

    renderPage();
    await selectSeatA1();
    fireEvent.click(screen.getByRole("button", { name: "Back to trips" }));
    fireEvent.click(await screen.findByRole("button", { name: /Mega Bangna/ }));

    await vi.waitFor(() => expect(seatsRequested).toBe(2));
  });

  it("refuses to select more seats than one hold may carry", async () => {
    stubApi({
      seats: () =>
        json({
          tripId: "trip-1",
          departureAt: trip.departureAt,
          fare: 35,
          seats: ["A1", "A2", "A3", "A4", "A5"].map((label, index) => ({
            id: `seat-${label.toLowerCase()}`,
            label,
            rowNumber: 1,
            columnNumber: index + 1,
            state: "AVAILABLE" as SeatState,
          })),
        }),
    });

    renderPage();
    fireEvent.click(await screen.findByRole("button", { name: /Mega Bangna/ }));
    for (const label of ["A1", "A2", "A3", "A4", "A5"]) {
      fireEvent.click(
        await screen.findByRole("button", { name: `Seat ${label}, available` }),
      );
    }

    expect(
      screen.getAllByRole("status").map((region) => region.textContent),
    ).toContain("You can hold at most 4 seats at a time.");
    expect(screen.getByText("4 seats selected · 140.00 THB")).toBeInTheDocument();
  });
});
