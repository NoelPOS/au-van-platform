import type { Booking, BookingEventType } from "../types/booking";

export type StampTone = "success" | "warning" | "brand" | "danger" | "muted";

export function needsPayment(booking: Booking): boolean {
  return (
    booking.status === "PENDING_PAYMENT" ||
    booking.status === "PAYMENT_REJECTED"
  );
}

export function isUpcoming(booking: Booking, now = Date.now()): boolean {
  return (
    booking.status !== "CANCELLED" &&
    Date.parse(booking.trip.departureAt) > now
  );
}

export function lastDetail(
  booking: Booking,
  type: BookingEventType,
): string | null {
  return booking.events.findLast((event) => event.type === type)?.detail ?? null;
}

export function wasExpired(booking: Booking): boolean {
  return (
    booking.status === "CANCELLED" &&
    booking.events.some((event) => event.type === "EXPIRED")
  );
}

export function cancelledByStaff(booking: Booking): boolean {
  return booking.events.some((event) => event.type === "TRIP_CANCELLED");
}

export function staffCancellationReason(booking: Booking): string | null {
  const detail = lastDetail(booking, "TRIP_CANCELLED");
  return detail?.split("Reason: ")[1] ?? detail;
}

export function stampOf(booking: Booking): { label: string; tone: StampTone } {
  if (booking.refundStatus === "DUE") return { label: "Refund due", tone: "warning" };
  if (booking.refundStatus === "REFUNDED") return { label: "Refunded", tone: "success" };
  switch (booking.status) {
    case "CONFIRMED":
      return { label: "Confirmed", tone: "success" };
    case "PENDING_PAYMENT":
      return { label: "Awaiting payment", tone: "warning" };
    case "PAYMENT_UNDER_REVIEW":
      return { label: "In review", tone: "brand" };
    case "PAYMENT_REJECTED":
      return { label: "Sent back", tone: "danger" };
    case "CANCELLED":
      return { label: wasExpired(booking) ? "Expired" : "Cancelled", tone: "muted" };
  }
}

export function seatLabels(booking: Booking): string[] {
  return booking.seats.map((seat) => seat.label).sort();
}
