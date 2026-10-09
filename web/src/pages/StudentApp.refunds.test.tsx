import { cleanup, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { booking, json } from "../test/bookingFixtures";
import { renderPage, stubApi } from "../test/renderStudentApp";
import { formatShortDate } from "../utils/days";

const paid = { ...booking, status: "CONFIRMED", totalFare: 70 };

const cancelledByStaff = {
  ...paid,
  status: "CANCELLED",
  cancellableUntil: null,
  refundStatus: "DUE",
  events: [
    ...booking.events,
    {
      type: "PAYMENT_APPROVED",
      detail: null,
      actorUserId: "admin-id",
      createdAt: "2026-09-21T11:00:00Z",
    },
    {
      type: "TRIP_CANCELLED",
      detail: "Trip cancelled and seats A1 released. A refund of 70.00 THB is due. Reason: The van failed its safety check.",
      actorUserId: "admin-id",
      createdAt: "2026-09-22T08:00:00Z",
    },
  ],
};

const refunded = {
  ...cancelledByStaff,
  refundStatus: "REFUNDED",
  refundedAt: "2026-09-23T03:00:00Z",
  refundNote: "Sent by PromptPay.",
  events: [
    ...cancelledByStaff.events,
    {
      type: "REFUNDED",
      detail: "Refunded 70.00 THB. Sent by PromptPay.",
      actorUserId: "admin-id",
      createdAt: "2026-09-23T03:00:00Z",
    },
  ],
};

describe("Student ticket after a cancellation", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("tells the student staff cancelled the trip, why, and that a refund is due", async () => {
    stubApi({ bookings: () => json([cancelledByStaff]) });

    renderPage("/tickets/booking-1");

    expect(await screen.findByRole("heading", { name: "Staff cancelled this trip" })).toBeInTheDocument();
    expect(screen.getByText("The seats went back on sale. Staff will refund your fare.")).toBeInTheDocument();
    expect(screen.getByText("The van failed its safety check.")).toBeInTheDocument();
    expect(screen.getByText("Refund due", { selector: "span" })).toHaveClass("text-warning");
    const refund = within(screen.getByRole("region", { name: "Refund" }));
    expect(refund.getByText("Refund due")).toBeInTheDocument();
    expect(refund.getByText("฿70")).toBeInTheDocument();
  });

  it("shows when a refund was sent and the note staff left with it", async () => {
    stubApi({ bookings: () => json([refunded]) });

    renderPage("/tickets/booking-1");

    expect(await screen.findByText("Refunded", { selector: "span" })).toHaveClass("text-success");
    const refund = within(screen.getByRole("region", { name: "Refund" }));
    expect(refund.getByText(`Refunded ${formatShortDate(refunded.refundedAt)}`)).toBeInTheDocument();
    expect(refund.getByText("Sent by PromptPay.")).toBeInTheDocument();
    expect(
      screen.getByText("The seats went back on sale and your fare was refunded."),
    ).toBeInTheDocument();
  });

  it("shows no refund on a cancelled booking that was never paid", async () => {
    stubApi({
      bookings: () => json([{ ...cancelledByStaff, status: "CANCELLED", refundStatus: "NONE" }]),
    });

    renderPage("/tickets/booking-1");

    await screen.findByRole("heading", { name: "Staff cancelled this trip" });
    expect(screen.getByText("The seats went back on sale.")).toBeInTheDocument();
    expect(screen.queryByRole("region", { name: "Refund" })).not.toBeInTheDocument();
    expect(screen.getByText("Cancelled", { selector: "span" })).toBeInTheDocument();
  });

  it("names staff cancellations, moved departures and refunds in the history", async () => {
    const moved = {
      type: "TRIP_RESCHEDULED",
      detail: "Departure moved.",
      actorUserId: "admin-id",
      createdAt: "2026-09-21T12:00:00Z",
    };
    stubApi({ bookings: () => json([{ ...refunded, events: [...refunded.events, moved] }]) });

    renderPage("/tickets/booking-1");

    const history = within(await screen.findByRole("region", { name: "History" }));
    expect(history.getByText("Trip cancelled by staff")).toBeInTheDocument();
    expect(history.getByText("Departure moved")).toBeInTheDocument();
    expect(history.getByText("Refunded")).toBeInTheDocument();
  });
});
