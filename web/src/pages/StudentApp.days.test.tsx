import { cleanup, fireEvent, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { booking, json, rejectedBooking, trip } from "../test/bookingFixtures";
import { renderPage, stubApi } from "../test/renderStudentApp";

const thursday = { ...trip, id: "thu-1", departureAt: "2030-10-03T01:00:00Z" };
const thursdayLate = {
  ...trip,
  id: "thu-2",
  destination: "Siam Paragon",
  departureAt: "2030-10-03T09:00:00Z",
};
const tuesday = { ...trip, id: "tue-1", destination: "Airport Rail Link" };

describe("Student departures by day", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("shows one day at a time, earliest first, with a tab per day", async () => {
    stubApi({ trips: () => json([thursdayLate, thursday, tuesday]) });

    renderPage();

    const days = await screen.findAllByRole("tab");
    expect(days.map((day) => day.getAttribute("aria-label"))).toEqual([
      "Tue 1 Oct, 1 departure",
      "Thu 3 Oct, 2 departures",
    ]);
    expect(days[0]).toHaveAttribute("aria-selected", "true");
    const board = screen.getByRole("region", { name: "Departures" });
    expect(within(board).getAllByRole("button")).toHaveLength(1);
    expect(within(board).getByRole("button", { name: /Airport Rail Link/ })).toBeInTheDocument();

    fireEvent.click(days[1]);

    const thursdayBoard = screen.getByRole("region", { name: "Departures" });
    expect(
      within(thursdayBoard)
        .getAllByRole("button")
        .map((button) => button.getAttribute("aria-label")?.split(",")[1].trim()),
    ).toEqual(["AU to Mega Bangna", "AU to Siam Paragon"]);
    expect(screen.getByRole("tab", { name: /Thu 3 Oct/ })).toHaveAttribute(
      "aria-selected",
      "true",
    );
  });

  it("points a student with an unpaid booking at the slip it needs", async () => {
    stubApi({ bookings: () => json([booking]) });

    renderPage();

    const banner = await screen.findByRole("link", { name: /waiting for payment/ });
    expect(banner).toHaveAttribute("href", "/tickets/booking-1");
  });

  it("says a slip was sent back when that is why the booking is unpaid", async () => {
    stubApi({ bookings: () => json([rejectedBooking]) });

    renderPage();

    expect(
      await screen.findByRole("link", { name: /Your payment slip was sent back/ }),
    ).toBeInTheDocument();
  });

  it("counts the tickets that need the student on the Tickets tab", async () => {
    stubApi({
      bookings: () =>
        json([booking, { ...rejectedBooking, id: "booking-2" }, { ...booking, id: "booking-3", status: "CONFIRMED" }]),
    });

    renderPage();

    expect(
      await screen.findByRole("link", { name: "Tickets, 2 need you" }),
    ).toBeInTheDocument();
  });
});
