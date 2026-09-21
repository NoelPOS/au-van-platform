import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { AuthSession } from "../auth/session";
import { StudentBookingPage } from "./StudentBookingPage";
import type { SeatState } from "./types";

const session: AuthSession = {
  accessToken: "student-token",
  expiresIn: 900,
  user: { id: "student-id", role: "STUDENT", displayName: "Somchai" },
};

const trip = {
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

const booking = {
  id: "booking-1",
  reference: "AUV-260921-7KQ2M4XR",
  status: "CONFIRMED",
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
  createdAt: "2026-09-21T10:01:12Z",
};

function seatMap(a1: SeatState = "AVAILABLE") {
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

function hold(expiresAt = new Date(Date.now() + 300_000).toISOString()) {
  return {
    holdId: "hold-1",
    tripId: "trip-1",
    expiresAt,
    seats: [{ seatId: "seat-a1", label: "A1" }],
  };
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

type Routes = {
  trips?: () => Response;
  seats?: () => Response;
  hold?: () => Response;
  release?: () => Response;
  bookings?: () => Response;
  createBooking?: (init: RequestInit) => Response;
};

function stubApi(routes: Routes = {}) {
  const fetcher = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    const method = init?.method ?? "GET";
    if (url.endsWith("/seats")) return routes.seats?.() ?? json(seatMap());
    if (url.includes("/release"))
      return routes.release?.() ?? new Response(null, { status: 204 });
    if (url === "/api/v1/seat-holds")
      return routes.hold?.() ?? json(hold(), 201);
    if (url === "/api/v1/bookings" && method === "POST")
      return (
        routes.createBooking?.(init ?? {}) ?? json(booking, 201)
      );
    if (url === "/api/v1/bookings") return routes.bookings?.() ?? json([]);
    if (url === "/api/v1/trips") return routes.trips?.() ?? json([trip]);
    throw new Error(`unexpected request: ${method} ${url}`);
  });
  vi.stubGlobal("fetch", fetcher);
  return fetcher;
}

function renderPage() {
  // The same defaults the application runs with, so a seat map served from
  // cache would show up here.
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: 30_000 } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <StudentBookingPage session={session} />
    </QueryClientProvider>,
  );
}

/**
 * Advances faked timers and lets React settle. RTL's own waiters cannot do
 * this: `waitFor` and `findBy*` look for a global `jest`, never find one under
 * Vitest, and then wait on an interval that the fake clock has frozen. The
 * trailing millisecond flushes the query client's own batched notification.
 */
async function tick(milliseconds = 0) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(milliseconds);
    await vi.advanceTimersByTimeAsync(1);
  });
}

async function selectSeatA1() {
  fireEvent.click(await screen.findByRole("button", { name: /Mega Bangna/ }));
  fireEvent.click(await screen.findByRole("button", { name: "Seat A1, available" }));
}

async function holdFailsWith(body: unknown, status: number) {
  stubApi({ hold: () => json(body, status) });
  renderPage();
  await selectSeatA1();
  fireEvent.click(screen.getByRole("button", { name: "Hold these seats" }));
}

async function reachPassengerDetails() {
  await selectSeatA1();
  fireEvent.click(screen.getByRole("button", { name: "Hold these seats" }));
  await screen.findByRole("button", { name: "Confirm booking" });
  fireEvent.change(screen.getByLabelText("Full name"), {
    target: { value: "Somchai P." },
  });
  fireEvent.change(screen.getByLabelText("Phone number"), {
    target: { value: "0812345678" },
  });
}

describe("StudentBookingPage", () => {
  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("books a seat from trip selection through to a confirmation reference", async () => {
    const fetcher = stubApi();

    renderPage();
    await reachPassengerDetails();
    fireEvent.click(screen.getByRole("button", { name: "Confirm booking" }));

    expect(await screen.findByText("AUV-260921-7KQ2M4XR")).toBeInTheDocument();
    expect(screen.getByText("Booking confirmed")).toBeInTheDocument();
    const created = fetcher.mock.calls.filter(
      ([url, init]) =>
        url === "/api/v1/bookings" &&
        (init as RequestInit | undefined)?.method === "POST",
    );
    expect(created).toHaveLength(1);
    expect(JSON.parse(String((created[0][1] as RequestInit).body))).toEqual({
      holdId: "hold-1",
      passengerName: "Somchai P.",
      passengerPhone: "0812345678",
    });
  });

  it("names each seat state so it is not carried by colour alone", async () => {
    stubApi();

    renderPage();
    fireEvent.click(await screen.findByRole("button", { name: /Mega Bangna/ }));

    expect(
      await screen.findByRole("button", { name: "Seat A1, available" }),
    ).toBeEnabled();
    expect(
      screen.getByRole("button", { name: "Seat A2, held by someone else" }),
    ).toBeDisabled();
    expect(
      screen.getByRole("button", { name: "Seat B1, held by you" }),
    ).toBeEnabled();
    expect(
      screen.getByRole("button", { name: "Seat B2, booked" }),
    ).toBeDisabled();

    fireEvent.click(screen.getByRole("button", { name: "Seat A1, available" }));
    expect(
      screen.getByRole("button", { name: "Seat A1, selected" }),
    ).toBeInTheDocument();
  });

  it("explains a lost seat race and refreshes the map instead of failing silently", async () => {
    let seatsRequested = 0;
    stubApi({
      seats: () => {
        seatsRequested += 1;
        return json(seatMap(seatsRequested === 1 ? "AVAILABLE" : "HELD"));
      },
      hold: () =>
        json(
          {
            detail: "One or more seats are no longer available.",
            code: "seat_taken",
          },
          409,
        ),
    });

    renderPage();
    await selectSeatA1();
    fireEvent.click(screen.getByRole("button", { name: "Hold these seats" }));

    expect(
      await screen.findByText("One or more seats are no longer available."),
    ).toBeInTheDocument();
    expect(
      await screen.findByRole("button", { name: "Seat A1, held by someone else" }),
    ).toBeInTheDocument();
    expect(screen.getByText("Choose at least one seat.")).toBeInTheDocument();
  });

  it("returns to the trip list when the trip is withdrawn before the hold", async () => {
    await holdFailsWith(
      { detail: "This trip is no longer available.", code: "trip_not_available" },
      409,
    );

    expect(
      await screen.findByText(
        "That trip is no longer available. Choose another.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("Upcoming trips")).toBeInTheDocument();
  });

  it("returns to the trip list when the trip departs before the hold", async () => {
    await holdFailsWith(
      { detail: "This trip has already departed.", code: "trip_departed" },
      409,
    );

    expect(
      await screen.findByText("That trip has already departed. Choose another."),
    ).toBeInTheDocument();
    expect(screen.getByText("Upcoming trips")).toBeInTheDocument();
  });

  it("returns to the trip list when the hold finds no such trip", async () => {
    await holdFailsWith({ detail: "Trip not found." }, 404);

    expect(
      await screen.findByText(
        "That trip is no longer available. Choose another.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("Upcoming trips")).toBeInTheDocument();
  });

  it("drops a selected seat that the poll shows has been taken", async () => {
    vi.useFakeTimers();
    let seatsRequested = 0;
    stubApi({
      seats: () => {
        seatsRequested += 1;
        return json(seatMap(seatsRequested === 1 ? "AVAILABLE" : "BOOKED"));
      },
    });

    renderPage();
    await tick();
    fireEvent.click(screen.getByRole("button", { name: /Mega Bangna/ }));
    await tick();
    fireEvent.click(screen.getByRole("button", { name: "Seat A1, available" }));

    await tick(10_000);

    expect(seatsRequested).toBeGreaterThan(1);
    expect(
      screen.getByText(
        "Seat A1 was taken by another student and has been removed from your selection.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("Choose at least one seat.")).toBeInTheDocument();
  });

  it("shows the hold countdown and returns to seat selection when it expires", async () => {
    vi.useFakeTimers();
    stubApi({
      hold: () => json(hold(new Date(Date.now() + 60_000).toISOString()), 201),
    });

    renderPage();
    await tick();
    fireEvent.click(screen.getByRole("button", { name: /Mega Bangna/ }));
    await tick();
    fireEvent.click(screen.getByRole("button", { name: "Seat A1, available" }));
    fireEvent.click(screen.getByRole("button", { name: "Hold these seats" }));
    await tick();

    expect(screen.getByRole("timer")).toHaveTextContent("Seats held for 1:00");
    await tick(30_000);
    expect(screen.getByRole("timer")).toHaveTextContent("Seats held for 0:30");

    await tick(30_000);

    expect(
      screen.getByText("Your seat hold expired. Choose your seats again."),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("group", { name: "Seat map" }),
    ).toBeInTheDocument();
  });

  it("reuses one idempotency key across a retry and mints a new one when the form changes", async () => {
    const keys: string[] = [];
    stubApi({
      createBooking: (init) => {
        keys.push(new Headers(init.headers).get("Idempotency-Key") ?? "");
        return json({ detail: "The service is unavailable." }, 503);
      },
    });

    renderPage();
    await reachPassengerDetails();
    fireEvent.click(screen.getByRole("button", { name: "Confirm booking" }));
    expect(
      await screen.findByText("The service is unavailable."),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Confirm booking" }));
    await vi.waitFor(() => expect(keys).toHaveLength(2));
    expect(keys[0]).toBe(keys[1]);
    expect(keys[0]).not.toBe("");

    fireEvent.input(screen.getByLabelText("Phone number"), {
      target: { value: "0899999999" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Confirm booking" }));
    await vi.waitFor(() => expect(keys).toHaveLength(3));
    expect(keys[2]).not.toBe(keys[1]);
  });

  it("mints a new idempotency key once the server has rejected the old one", async () => {
    const keys: string[] = [];
    stubApi({
      createBooking: (init) => {
        keys.push(new Headers(init.headers).get("Idempotency-Key") ?? "");
        return keys.length === 1
          ? json(
              {
                detail: "This idempotency key was used with different content.",
                code: "idempotency_key_reused",
              },
              409,
            )
          : json(booking, 201);
      },
    });

    renderPage();
    await reachPassengerDetails();
    fireEvent.click(screen.getByRole("button", { name: "Confirm booking" }));
    expect(
      await screen.findByText(
        "That booking attempt could not be completed. Try again.",
      ),
    ).toBeInTheDocument();

    // Without a fresh key the student retries under the rejected one forever.
    fireEvent.click(screen.getByRole("button", { name: "Confirm booking" }));
    expect(await screen.findByText("AUV-260921-7KQ2M4XR")).toBeInTheDocument();
    expect(keys).toHaveLength(2);
    expect(keys[1]).not.toBe(keys[0]);
    expect(keys[1]).not.toBe("");
  });

  it("sends the student back to seat selection when the hold expired before confirmation", async () => {
    stubApi({
      createBooking: () =>
        json({ detail: "This seat hold has expired.", code: "hold_expired" }, 409),
    });

    renderPage();
    await reachPassengerDetails();
    fireEvent.click(screen.getByRole("button", { name: "Confirm booking" }));

    expect(
      await screen.findByText(
        "Your seat hold expired before the booking was confirmed. Choose your seats again.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole("group", { name: "Seat map" })).toBeInTheDocument();
  });

  it("distinguishes a hold that has already been used from one that expired", async () => {
    stubApi({
      createBooking: () =>
        json(
          { detail: "This hold is already booked.", code: "hold_already_used" },
          409,
        ),
    });

    renderPage();
    await reachPassengerDetails();
    fireEvent.click(screen.getByRole("button", { name: "Confirm booking" }));

    expect(
      await screen.findByText(
        "Those seats have already been booked. Choose your seats again.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole("group", { name: "Seat map" })).toBeInTheDocument();
  });

  it("sends the student back to the trip list when the trip has departed", async () => {
    stubApi({
      createBooking: () =>
        json(
          { detail: "This trip has already departed.", code: "trip_departed" },
          409,
        ),
    });

    renderPage();
    await reachPassengerDetails();
    fireEvent.click(screen.getByRole("button", { name: "Confirm booking" }));

    expect(
      await screen.findByText(
        "That trip has already departed. Choose another.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("Upcoming trips")).toBeInTheDocument();
  });

  it("keeps the student on the form when the passenger details are rejected", async () => {
    stubApi({
      createBooking: () => json({ detail: "Invalid request content." }, 400),
    });

    renderPage();
    await reachPassengerDetails();
    fireEvent.click(screen.getByRole("button", { name: "Confirm booking" }));

    expect(
      await screen.findByText(
        "Check the passenger name and phone number, then try again.",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Confirm booking" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("timer")).toBeInTheDocument();
  });

  it("shows a loading state while the trips are being fetched", () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() => new Promise<Response>(() => {})),
    );

    renderPage();

    expect(screen.getByText("Loading trips…")).toBeInTheDocument();
    expect(screen.getByText("Loading your bookings…")).toBeInTheDocument();
  });

  it("shows an empty state when no trips are scheduled", async () => {
    stubApi({ trips: () => json([]) });

    renderPage();

    expect(
      await screen.findByText(
        "No trips are scheduled right now. Check back later.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("You have no bookings yet.")).toBeInTheDocument();
  });

  it("asks the student to sign in again when the access token has expired", async () => {
    stubApi({ trips: () => new Response(null, { status: 401 }) });

    renderPage();

    expect(await screen.findByText("Sign in again")).toBeInTheDocument();
    expect(
      screen.getByText(
        "Your sign-in has expired. Close and reopen this page from LINE to continue booking.",
      ),
    ).toBeInTheDocument();
  });

  it("offers a retry when the trip list cannot be loaded", async () => {
    let attempts = 0;
    stubApi({
      trips: () => {
        attempts += 1;
        return attempts === 1
          ? json({ detail: "The trips could not be loaded." }, 500)
          : json([trip]);
      },
    });

    renderPage();

    expect(
      await screen.findByText("The trips could not be loaded."),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));

    expect(
      await screen.findByRole("button", { name: /Mega Bangna/ }),
    ).toBeInTheDocument();
  });

  it("lists existing bookings with the reference and the trip they belong to", async () => {
    stubApi({ bookings: () => json([booking]), trips: () => json([]) });

    renderPage();

    expect(await screen.findByText("AUV-260921-7KQ2M4XR")).toBeInTheDocument();
    expect(screen.getByText("CONFIRMED")).toBeInTheDocument();
    expect(screen.getByText(/^AU → Mega Bangna · /)).toBeInTheDocument();
    expect(screen.getByText("Seat A1 · 35.00 THB")).toBeInTheDocument();
  });

  it("never serves a cached seat map to a student who comes back to a trip", async () => {
    let seatsRequested = 0;
    stubApi({
      seats: () => {
        seatsRequested += 1;
        return json(seatMap());
      },
    });

    renderPage();
    await selectSeatA1();
    fireEvent.click(screen.getByRole("button", { name: "Back to trips" }));
    fireEvent.click(await screen.findByRole("button", { name: /Mega Bangna/ }));

    await vi.waitFor(() => expect(seatsRequested).toBe(2));
  });

  it("refuses to select more seats than one hold may carry", async () => {
    stubApi({
      seats: () =>
        json({
          tripId: "trip-1",
          departureAt: trip.departureAt,
          fare: 35,
          seats: ["A1", "A2", "A3", "A4", "A5"].map((label, index) => ({
            id: `seat-${label.toLowerCase()}`,
            label,
            rowNumber: 1,
            columnNumber: index + 1,
            state: "AVAILABLE" as SeatState,
          })),
        }),
    });

    renderPage();
    fireEvent.click(await screen.findByRole("button", { name: /Mega Bangna/ }));
    for (const label of ["A1", "A2", "A3", "A4", "A5"]) {
      fireEvent.click(
        await screen.findByRole("button", { name: `Seat ${label}, available` }),
      );
    }

    expect(
      screen.getByText("You can hold at most 4 seats at a time."),
    ).toBeInTheDocument();
    expect(screen.getByText("4 seats selected · 140.00 THB")).toBeInTheDocument();
  });

  it("releases the hold when the student goes back to change seats", async () => {
    const fetcher = stubApi();

    renderPage();
    await reachPassengerDetails();
    fireEvent.click(screen.getByRole("button", { name: "Change seats" }));

    expect(
      await screen.findByRole("group", { name: "Seat map" }),
    ).toBeInTheDocument();
    await vi.waitFor(() =>
      expect(
        fetcher.mock.calls.some(
          ([url]) => url === "/api/v1/seat-holds/hold-1/release",
        ),
      ).toBe(true),
    );
  });
});
