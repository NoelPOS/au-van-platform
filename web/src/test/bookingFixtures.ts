import type { AuthSession } from "../types/auth";
import type { SeatState } from "../types/booking";

export const session: AuthSession = {
  accessToken: "student-token",
  expiresIn: 900,
  user: { id: "student-id", role: "STUDENT", displayName: "Somchai" },
};

export const trip = {
  id: "trip-1",
  routeId: "route-1",
  origin: "AU",
  destination: "Mega Bangna",
  departureAt: "2026-10-01T01:00:00Z",
  fare: 35,
  durationMinutes: 45,
  totalSeats: 4,
  availableSeats: 3,
};

export const otherTrip = {
  ...trip,
  id: "trip-2",
  destination: "Siam Paragon",
  departureAt: "2026-10-01T03:00:00Z",
};

export const fullTrip = {
  ...trip,
  id: "trip-full",
  destination: "Future Park",
  availableSeats: 0,
};

export const waitlistEntry = {
  id: "waitlist-1",
  tripId: "trip-full",
  seatsWanted: 1,
  status: "WAITING",
  position: 2,
  joinedAt: "2026-09-21T10:00:00Z",
};

export const booking = {
  id: "booking-1",
  reference: "AUV-260921-7KQ2M4XR",
  status: "PENDING_PAYMENT",
  trip: {
    id: "trip-1",
    origin: "AU",
    destination: "Mega Bangna",
    departureAt: "2026-10-01T01:00:00Z",
  },
  passengerName: "Somchai P.",
  passengerPhone: "0812345678",
  totalFare: 35,
  seats: [{ seatId: "seat-a1", label: "A1" }],
  events: [
    {
      type: "CREATED",
      detail: "Booked seat A1.",
      actorUserId: "student-id",
      createdAt: "2026-09-21T10:01:12Z",
    },
  ],
  paymentDeadlineAt: "2026-09-21T12:01:12Z",
  createdAt: "2026-09-21T10:01:12Z",
};

export const expiredBooking = {
  ...booking,
  id: "booking-expired",
  reference: "AUV-260921-EXPIRED1",
  status: "CANCELLED",
  paymentDeadlineAt: null,
  events: [
    ...booking.events,
    {
      type: "EXPIRED",
      detail: "Expired unpaid and released seats A1.",
      actorUserId: null,
      createdAt: "2026-09-21T12:01:12Z",
    },
  ],
};

export const rejectedBooking = {
  ...booking,
  status: "PAYMENT_REJECTED",
  events: [
    ...booking.events,
    {
      type: "PAYMENT_REJECTED",
      detail: "The slip is too blurred to read.",
      actorUserId: "admin-id",
      createdAt: "2026-09-21T11:02:00Z",
    },
  ],
};

export function seatMap(a1: SeatState = "AVAILABLE") {
  return {
    tripId: "trip-1",
    departureAt: trip.departureAt,
    fare: 35,
    seats: [
      { id: "seat-a1", label: "A1", rowNumber: 1, columnNumber: 1, state: a1 },
      {
        id: "seat-a2",
        label: "A2",
        rowNumber: 1,
        columnNumber: 2,
        state: "HELD" as SeatState,
      },
      {
        id: "seat-b1",
        label: "B1",
        rowNumber: 2,
        columnNumber: 1,
        state: "HELD_BY_YOU" as SeatState,
      },
      {
        id: "seat-b2",
        label: "B2",
        rowNumber: 2,
        columnNumber: 2,
        state: "BOOKED" as SeatState,
      },
    ],
  };
}

export function hold(
  expiresAt = new Date(Date.now() + 300_000).toISOString(),
) {
  return {
    holdId: "hold-1",
    tripId: "trip-1",
    expiresAt,
    seats: [{ seatId: "seat-a1", label: "A1" }],
  };
}

export function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
