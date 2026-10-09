import { apiBaseUrl } from "./apiBaseUrl";
import type { AuthSession } from "../types/auth";
import { authenticatedFetch } from "./authService";
import type {
  AvailableTrip,
  Booking,
  BookingEligibility,
  SeatHold,
  TripSeatMap,
  WaitlistEntry,
} from "../types/booking";

export class ApiError extends Error {
  status: number;
  code: string | null;
  bookingId: string | null;

  constructor(
    message: string,
    status: number,
    code: string | null,
    bookingId: string | null = null,
  ) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.bookingId = bookingId;
  }
}

const sessionExpired = "Your sign-in has expired. Sign in with LINE again.";

async function failure(response: Response): Promise<ApiError> {
  if (response.status === 401) return new ApiError(sessionExpired, 401, null);

  const body = (await response.json().catch(() => null)) as {
    detail?: string;
    code?: string;
    bookingId?: string;
  } | null;
  return new ApiError(
    body?.detail ?? `Request failed (${response.status}).`,
    response.status,
    body?.code ?? null,
    body?.bookingId ?? null,
  );
}

async function request<T>(
  session: AuthSession,
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const response = await authenticatedFetch(session, `${apiBaseUrl}/api/v1${path}`, {
    ...init,
    headers: { "Content-Type": "application/json", ...init.headers },
  });

  if (!response.ok) throw await failure(response);

  if (response.status === 204) return undefined as T;

  return (await response.json()) as T;
}

export const bookingApi = {
  getEligibility: (session: AuthSession) =>
    request<BookingEligibility>(session, "/me/booking-eligibility"),
  listTrips: (session: AuthSession) =>
    request<AvailableTrip[]>(session, "/trips"),
  getSeatMap: (session: AuthSession, tripId: string) =>
    request<TripSeatMap>(session, `/trips/${tripId}/seats`),
  holdSeats: (
    session: AuthSession,
    input: { tripId: string; seatIds: string[] },
  ) =>
    request<SeatHold>(session, "/seat-holds", {
      method: "POST",
      body: JSON.stringify(input),
    }),
  releaseHold: (session: AuthSession, holdId: string) =>
    request<void>(session, `/seat-holds/${holdId}/release`, { method: "POST" }),
  listBookings: (session: AuthSession) =>
    request<Booking[]>(session, "/bookings"),
  cancelBooking: (session: AuthSession, bookingId: string) =>
    request<Booking>(session, `/bookings/${bookingId}/cancel`, {
      method: "POST",
    }),
  listWaitlist: (session: AuthSession) =>
    request<WaitlistEntry[]>(session, "/waitlist"),
  joinWaitlist: (
    session: AuthSession,
    input: { tripId: string; seatsWanted: number },
  ) =>
    request<WaitlistEntry>(session, "/waitlist", {
      method: "POST",
      body: JSON.stringify(input),
    }),
  // A POST, not a DELETE: the API's CORS policy allows no DELETE.
  leaveWaitlist: (session: AuthSession, entryId: string) =>
    request<void>(session, `/waitlist/${entryId}/leave`, { method: "POST" }),
  createBooking: (
    session: AuthSession,
    idempotencyKey: string,
    input: { holdId: string; passengerName: string; passengerPhone: string },
  ) =>
    request<Booking>(session, "/bookings", {
      method: "POST",
      headers: { "Idempotency-Key": idempotencyKey },
      body: JSON.stringify(input),
    }),
  // Bypasses request(): only the browser can set the multipart boundary.
  submitPaymentProof: async (
    session: AuthSession,
    bookingId: string,
    file: File,
  ): Promise<Booking> => {
    const body = new FormData();
    body.append("file", file);
    const response = await authenticatedFetch(
      session,
      `${apiBaseUrl}/api/v1/bookings/${bookingId}/payment-proof`,
      { method: "POST", body },
    );

    if (!response.ok) throw await failure(response);

    return (await response.json()) as Booking;
  },
};
