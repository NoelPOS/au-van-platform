import { cleanup, fireEvent, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { booking, eligible, json, trip } from "../test/bookingFixtures";
import { holdFailsWith, renderPage, stubApi } from "../test/renderStudentApp";

describe("Student fair-booking rules", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("shows a trip whose booking has closed without offering it", async () => {
    const closed = {
      ...trip,
      id: "trip-closed",
      destination: "Future Park",
      bookingClosesAt: "2020-01-01T00:00:00Z",
    };
    stubApi({ trips: () => json([trip, closed]) });

    renderPage();

    expect(
      await screen.findByRole("group", { name: /Future Park, booking closed/ }),
    ).toHaveTextContent("Closed");
    expect(screen.queryByRole("button", { name: /Future Park/ })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Mega Bangna/ })).toBeEnabled();
  });

  it("explains a cool-down before the student tries to book", async () => {
    const message = "Your recent bookings expired without payment, so you can book again from 10 Oct 2030 at 10:31.";
    stubApi({
      eligibility: () =>
        json({ ...eligible, canBook: false, reason: "booking_cooldown", message, retryAt: "2030-10-10T10:31:00+07:00" }),
    });

    renderPage();

    expect(await screen.findByText("Booking is paused for now")).toBeInTheDocument();
    expect(screen.getByText(message)).toBeInTheDocument();
  });

  it("takes a student with an unpaid booking to the ticket that needs paying", async () => {
    stubApi({
      bookings: () => json([booking]),
      hold: () =>
        json(
          {
            code: "unpaid_booking_exists",
            detail: "You have an unpaid booking, AUV-260921-7KQ2M4XR. Pay for it or cancel it before booking another seat.",
            bookingId: "booking-1",
          },
          409,
        ),
    });

    renderPage();
    fireEvent.click(await screen.findByRole("button", { name: /Mega Bangna/ }));
    fireEvent.click(await screen.findByRole("button", { name: "Seat A1, available" }));
    fireEvent.click(screen.getByRole("button", { name: "Hold seats" }));

    expect(
      await screen.findByText(/You have an unpaid booking, AUV-260921-7KQ2M4XR/),
    ).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Pay to keep your seats" })).toBeInTheDocument();
  });

  it("sends a student who already booked this trip to their tickets", async () => {
    await holdFailsWith(
      { code: "already_booked_on_trip", detail: "You already have a booking on this trip." },
      409,
    );

    expect(await screen.findByText("You already have a booking on this trip.")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Your trips" })).toBeInTheDocument();
  });

  it("sends a student back to the departures when booking has closed", async () => {
    await holdFailsWith(
      {
        code: "booking_closed",
        detail: "Booking for this trip has closed. Seats can be booked until 90 minutes before departure.",
      },
      409,
    );

    expect(
      await screen.findByText(/Booking for this trip has closed/),
    ).toBeInTheDocument();
    expect(screen.getByText("Catch the next van")).toBeInTheDocument();
  });
});
