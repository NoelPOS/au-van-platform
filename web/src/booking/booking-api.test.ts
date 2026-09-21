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
    await bookingApi.listWaitlist(session);
    await bookingApi.joinWaitlist(session, { tripId: "trip-1", seatsWanted: 1 });
    await bookingApi.leaveWaitlist(session, "waitlist-1");

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
      ["/api/v1/waitlist", "GET"],
      ["/api/v1/waitlist", "POST"],
      // A POST, not a DELETE: the API's CORS policy allows no DELETE, so a
      // DELETE would work behind the dev proxy and fail in a real browser.
      ["/api/v1/waitlist/waitlist-1/leave", "POST"],
    ]);
    const bookingRequest = fetcher.mock.calls[4][1] as RequestInit;
    const headers = new Headers(bookingRequest.headers);
    expect(headers.get("Authorization")).toBe("Bearer student-token");
    expect(headers.get("Idempotency-Key")).toBe("key-1");
  });

  it("posts a payment proof as multipart without a JSON content type", async () => {
    // The shared request() helper always sets application/json; a multipart
    // body has to bypass it so the browser writes its own boundary header.
    const fetcher = vi.fn().mockImplementation(() => Promise.resolve(json({})));
    vi.stubGlobal("fetch", fetcher);
    const slip = new File(["slip-bytes"], "slip.jpg", { type: "image/jpeg" });

    await bookingApi.submitPaymentProof(session, "booking-1", slip);

    const [url, init] = fetcher.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("/api/v1/bookings/booking-1/payment-proof");
    expect(init.method).toBe("POST");
    const headers = new Headers(init.headers);
    expect(headers.get("Content-Type")).toBeNull();
    expect(headers.get("Authorization")).toBe("Bearer student-token");
    expect(init.body).toBeInstanceOf(FormData);
    expect((init.body as FormData).get("file")).toBe(slip);
  });

  it("carries the code when a payment proof is refused", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation(() =>
        Promise.resolve(
          json(
            {
              detail: "This booking is not waiting for a payment proof.",
              code: "booking_not_awaiting_payment",
            },
            409,
          ),
        ),
      ),
    );

    const error = (await bookingApi
      .submitPaymentProof(
        session,
        "booking-1",
        new File(["x"], "slip.jpg", { type: "image/jpeg" }),
      )
      .catch((thrown) => thrown)) as ApiError;

    expect(error.status).toBe(409);
    expect(error.code).toBe("booking_not_awaiting_payment");
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
