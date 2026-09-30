export type SeatState = "AVAILABLE" | "HELD" | "HELD_BY_YOU" | "BOOKED";
export type BookingStatus =
  | "PENDING_PAYMENT"
  | "PAYMENT_UNDER_REVIEW"
  | "PAYMENT_REJECTED"
  | "CONFIRMED"
  | "CANCELLED";

export type BookingEventType =
  | "CREATED"
  | "PAYMENT_PROOF_SUBMITTED"
  | "PAYMENT_APPROVED"
  | "PAYMENT_REJECTED"
  | "CANCELLED"
  | "EXPIRED";

export type BookingEvent = {
  type: BookingEventType;
  detail: string | null;
  actorUserId: string | null;
  createdAt: string;
};

export type WaitlistStatus =
  | "WAITING"
  | "PROMOTED"
  | "FULFILLED"
  | "WITHDRAWN"
  | "EXPIRED";

export type WaitlistEntry = {
  id: string;
  tripId: string;
  seatsWanted: number;
  status: WaitlistStatus;
  position: number | null;
  joinedAt: string;
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
  paymentDeadlineAt: string | null;
  createdAt: string;
};

export type BookingStep = "trips" | "seats" | "details" | "confirmed";

export type Notice = { tone: "error" | "status"; message: string };
