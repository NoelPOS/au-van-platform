export type SeatState = "AVAILABLE" | "HELD" | "HELD_BY_YOU" | "BOOKED";
/**
 * A booking now starts at `PENDING_PAYMENT` and only an approved payment proof
 * reaches `CONFIRMED` (ADR-009). The rejected state a student can resubmit
 * from arrives with the admin review in #52.
 */
export type BookingStatus =
  | "PENDING_PAYMENT"
  | "PAYMENT_UNDER_REVIEW"
  | "CONFIRMED"
  | "CANCELLED";

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
  createdAt: string;
};
