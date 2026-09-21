import { afterEach, describe, expect, it, vi } from "vitest";
import type { AuthSession } from "../auth/session";
import { ApiError, bookingApi } from "./booking-api";

const session: AuthSession = {
  accessToken: "student-token",
  expiresIn: 900,
  user: { id: "student-id", role: "STUDENT", displayName: "Somchai" },
};

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

describe("booking API client", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("maps every booking call to its student API endpoint", async () => {
    const fetcher = vi.fn().mockImplementation(() => Promise.resolve(json({})));
    vi.stubGlobal("fetch", fetcher);

    await bookingApi.listTrips(session);
    await bookingApi.getSeatMap(session, "trip-1");
    await bookingApi.holdSeats(session, {
      tripId: "trip-1",
      seatIds: ["seat-1"],
    });
    await bookingApi.listBookings(session);
    await bookingApi.createBooking(session, "key-1", {
      holdId: "hold-1",
      passengerName: "Somchai P.",
      passengerPhone: "0812345678",
    });

    expect(
      fetcher.mock.calls.map(([url, options]) => [
        url,
        (options as RequestInit).method ?? "GET",
      ]),
    ).toEqual([
      ["/api/v1/trips", "GET"],
      ["/api/v1/trips/trip-1/seats", "GET"],
      ["/api/v1/seat-holds", "POST"],
      ["/api/v1/bookings", "GET"],
      ["/api/v1/bookings", "POST"],
    ]);
    const bookingRequest = fetcher.mock.calls[4][1] as RequestInit;
    const headers = new Headers(bookingRequest.headers);
    expect(headers.get("Authorization")).toBe("Bearer student-token");
    expect(headers.get("Idempotency-Key")).toBe("key-1");
  });

  it("completes a hold release even though it answers 204 with no body", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockImplementation(() =>
          Promise.resolve(new Response(null, { status: 204 })),
        ),
    );

    await expect(
      bookingApi.releaseHold(session, "hold-1"),
    ).resolves.toBeUndefined();
  });

  it("reports an expired sign-in rather than a bare status code", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockImplementation(() =>
          Promise.resolve(new Response(null, { status: 401 })),
        ),
    );

    const error = await bookingApi.listTrips(session).catch((thrown) => thrown);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(401);
    expect((error as ApiError).message).toBe(
      "Your sign-in has expired. Sign in with LINE again.",
    );
  });

  it("carries the server's machine-readable code on a conflict", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation(() =>
        Promise.resolve(
          json(
            { detail: "This seat hold has expired.", code: "hold_expired" },
            409,
          ),
        ),
      ),
    );

    const error = (await bookingApi
      .createBooking(session, "key-1", {
        holdId: "hold-1",
        passengerName: "Somchai P.",
        passengerPhone: "0812345678",
      })
      .catch((thrown) => thrown)) as ApiError;

    expect(error.status).toBe(409);
    expect(error.code).toBe("hold_expired");
    expect(error.message).toBe("This seat hold has expired.");
  });
});
