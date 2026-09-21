export type SeatState = "AVAILABLE" | "HELD" | "HELD_BY_YOU" | "BOOKED";
/**
 * A booking now starts at `PENDING_PAYMENT` and only an approved payment proof
 * reaches `CONFIRMED` (ADR-009). `PAYMENT_REJECTED` is not a dead end: the
 * booking keeps its seats and the student can send another slip.
 */
export type BookingStatus =
  | "PENDING_PAYMENT"
  | "PAYMENT_UNDER_REVIEW"
  | "PAYMENT_REJECTED"
  | "CONFIRMED"
  | "CANCELLED";

/**
 * `EXPIRED` is the sweep releasing an unpaid booking's seats. The booking's
 * status is `CANCELLED` either way — ADR-010 declined to add a status for a
 * state that is operationally identical — so this event is the only thing that
 * says the student did not cancel it themselves.
 */
export type BookingEventType =
  | "CREATED"
  | "PAYMENT_PROOF_SUBMITTED"
  | "PAYMENT_APPROVED"
  | "PAYMENT_REJECTED"
  | "CANCELLED"
  | "EXPIRED";

/**
 * One entry in a booking's history. `actorUserId` is whoever caused it, which
 * for a review is a member of staff rather than the student reading it.
 */
export type BookingEvent = {
  type: BookingEventType;
  detail: string | null;
  actorUserId: string | null;
  createdAt: string;
};

export type AvailableTrip = {
  id: string;
  routeId: string;
  origin: string;
  destination: string;
  departureAt: string;
  fare: number;
  durationMinutes: number;
  totalSeats: number;
  availableSeats: number;
};

export type TripSeat = {
  id: string;
  label: string;
  rowNumber: number;
  columnNumber: number;
  state: SeatState;
};

export type TripSeatMap = {
  tripId: string;
  departureAt: string;
  fare: number;
  seats: TripSeat[];
};

export type HeldSeat = {
  seatId: string;
  label: string;
};

export type SeatHold = {
  holdId: string;
  tripId: string;
  expiresAt: string;
  seats: HeldSeat[];
};

/**
 * Route and departure arrive nested on a booking because the trip list carries
 * only future active trips: a past or cancelled trip could never be resolved
 * client-side for a my-bookings row.
 */
export type Booking = {
  id: string;
  reference: string;
  status: BookingStatus;
  trip: {
    id: string;
    origin: string;
    destination: string;
    departureAt: string;
  };
  passengerName: string;
  passengerPhone: string;
  totalFare: number;
  seats: HeldSeat[];
  events: BookingEvent[];
  /**
   * When these seats are released if the booking is still unpaid, and null for
   * one that never expires — every confirmed or cancelled booking, and any row
   * written before the deadline existed.
   */
  paymentDeadlineAt: string | null;
  createdAt: string;
};
