import { apiBaseUrl } from "../api-base-url";
import { authenticatedFetch, type AuthSession } from "../auth/session";
import type { AvailableTrip, Booking, SeatHold, TripSeatMap } from "./types";

/**
 * A failed booking request. The flow branches on `code`, never on the status
 * alone: the API answers 409 for several unrelated conditions and only the
 * machine-readable code says which one happened.
 */
export class ApiError extends Error {
  status: number;
  code: string | null;

  constructor(message: string, status: number, code: string | null) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
  }
}

const sessionExpired = "Your sign-in has expired. Sign in with LINE again.";

async function failure(response: Response): Promise<ApiError> {
  // Spring answers a missing or expired token with a bare 401 and no body, so
  // there is nothing to parse and nothing useful to show the student.
  if (response.status === 401) return new ApiError(sessionExpired, 401, null);

  const body = (await response.json().catch(() => null)) as {
    detail?: string;
    code?: string;
  } | null;
  return new ApiError(
    body?.detail ?? `Request failed (${response.status}).`,
    response.status,
    body?.code ?? null,
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

  // Releasing a hold answers 204 with an empty body; parsing it would throw on
  // a successful call.
  if (response.status === 204) return undefined as T;

  return (await response.json()) as T;
}

export const bookingApi = {
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
};
