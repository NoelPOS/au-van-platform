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

const otherTrip = {
  ...trip,
  id: "trip-2",
  destination: "Siam Paragon",
  departureAt: "2026-10-01T03:00:00Z",
};

const booking = {
  id: "booking-1",
  reference: "AUV-260921-7KQ2M4XR",
  // A new booking is PENDING_PAYMENT: ADR-009 made the payment review the
  // only path to CONFIRMED.
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
  seats?: () => Response | Promise<Response>;
  hold?: () => Response;
  release?: () => Response;
  bookings?: () => Response;
  createBooking?: (init: RequestInit) => Response;
  paymentProof?: (init: RequestInit) => Response;
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
    if (url.endsWith("/payment-proof"))
      return (
        routes.paymentProof?.(init ?? {}) ??
        json({ ...booking, status: "PAYMENT_UNDER_REVIEW" })
      );
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
    expect(screen.getByText("Seats reserved")).toBeInTheDocument();
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

  it("lists the new booking in my bookings on the confirmation step", async () => {
    // The confirmation step renders My bookings, so a list still showing the
    // pre-booking state is visibly wrong: the booking has to be invalidated
    // into it.
    let bookingsRequested = 0;
    stubApi({
      bookings: () => {
        bookingsRequested += 1;
        return json(bookingsRequested === 1 ? [] : [booking]);
      },
    });

    renderPage();
    await reachPassengerDetails();
    fireEvent.click(screen.getByRole("button", { name: "Confirm booking" }));

    expect(await screen.findByText("Seats reserved")).toBeInTheDocument();
    // Only My bookings renders the seats-and-fare line, so this is the list
    // rather than the confirmation panel beside it.
    expect(
      await screen.findByText("Seat A1 · 35.00 THB"),
    ).toBeInTheDocument();
  });

  it("trims the passenger name and phone before sending them", async () => {
    const fetcher = stubApi();

    renderPage();
    await selectSeatA1();
    fireEvent.click(screen.getByRole("button", { name: "Hold these seats" }));
    await screen.findByRole("button", { name: "Confirm booking" });
    fireEvent.change(screen.getByLabelText("Full name"), {
      target: { value: "  Somchai P.  " },
    });
    fireEvent.change(screen.getByLabelText("Phone number"), {
      target: { value: " 0812345678 " },
    });
    fireEvent.click(screen.getByRole("button", { name: "Confirm booking" }));

    expect(await screen.findByText("AUV-260921-7KQ2M4XR")).toBeInTheDocument();
    const created = fetcher.mock.calls.filter(
      ([url, init]) =>
        url === "/api/v1/bookings" &&
        (init as RequestInit | undefined)?.method === "POST",
    );
    expect(JSON.parse(String((created[0][1] as RequestInit).body))).toEqual({
      holdId: "hold-1",
      passengerName: "Somchai P.",
      passengerPhone: "0812345678",
    });
  });

  it("leaves the confirmation when the student goes back to the trip list", async () => {
    stubApi();

    renderPage();
    await reachPassengerDetails();
    fireEvent.click(screen.getByRole("button", { name: "Confirm booking" }));
    await screen.findByText("Seats reserved");

    fireEvent.click(screen.getByRole("button", { name: "Back to trips" }));

    expect(
      await screen.findByRole("button", { name: /Mega Bangna/ }),
    ).toBeInTheDocument();
    expect(screen.queryByText("Seats reserved")).not.toBeInTheDocument();
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

    // The name carries the state and `aria-pressed` carries the toggle, so a
    // screen reader reads the same thing twice over rather than not at all.
    expect(
      screen.getByRole("button", { name: "Seat A1, available", pressed: false }),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Seat A1, available" }));
    expect(
      screen.getByRole("button", { name: "Seat A1, selected", pressed: true }),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Seat A1, selected" }));
    expect(
      screen.getByRole("button", { name: "Seat A1, available", pressed: false }),
    ).toBeInTheDocument();
  });

  it("shows a loading state while the seat map is being fetched", async () => {
    let releaseSeats: (response: Response) => void = () => {};
    stubApi({
      seats: () =>
        new Promise<Response>((resolve) => {
          releaseSeats = resolve;
        }),
    });

    renderPage();
    fireEvent.click(await screen.findByRole("button", { name: /Mega Bangna/ }));

    expect(await screen.findByText("Loading seats…")).toBeInTheDocument();
    releaseSeats(json(seatMap()));
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
    // Nothing is selected any more, so holding again would send an empty
    // request the server would only reject.
    expect(
      screen.getByRole("button", { name: "Hold these seats" }),
    ).toBeDisabled();
  });

  it("returns to the trip list when the trip is withdrawn before the hold", async () => {
    await holdFailsWith(
      { detail: "This trip is no longer available.", code: "trip_not_available" },
      409,
    );

    // Found by role, not by text: the notice is an error and has to reach a
    // screen reader as one the moment it appears.
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "That trip is no longer available. Choose another.",
    );
    expect(screen.getByText("Upcoming trips")).toBeInTheDocument();
  });

  it("drops the withdrawn trip from the list the student lands back on", async () => {
    // The message alone is not the remedy: with the application-wide thirty
    // second staleTime and the trips query permanently mounted, the refetch in
    // the transition is the only thing taking the dead trip off the list.
    let tripsRequested = 0;
    stubApi({
      trips: () => {
        tripsRequested += 1;
        return json(tripsRequested === 1 ? [trip, otherTrip] : [otherTrip]);
      },
      hold: () =>
        json(
          {
            detail: "This trip is no longer available.",
            code: "trip_not_available",
          },
          409,
        ),
    });

    renderPage();
    await selectSeatA1();
    fireEvent.click(screen.getByRole("button", { name: "Hold these seats" }));

    expect(
      await screen.findByText(
        "That trip is no longer available. Choose another.",
      ),
    ).toBeInTheDocument();
    await vi.waitFor(() =>
      expect(
        screen.queryByRole("button", { name: /Mega Bangna/ }),
      ).not.toBeInTheDocument(),
    );
    expect(
      screen.getByRole("button", { name: /Siam Paragon/ }),
    ).toBeInTheDocument();
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

  it("returns to the trip list when the seat map says the trip is gone", async () => {
    stubApi({ seats: () => json({ detail: "Trip not found." }, 404) });

    renderPage();
    fireEvent.click(await screen.findByRole("button", { name: /Mega Bangna/ }));

    expect(
      await screen.findByText(
        "That trip is no longer available. Choose another.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("Upcoming trips")).toBeInTheDocument();
    expect(
      screen.queryByRole("group", { name: "Seat map" }),
    ).not.toBeInTheDocument();
  });

  it("keeps the student on the seat step when the map fails for another reason", async () => {
    stubApi({
      seats: () => json({ detail: "The seat map could not be loaded." }, 500),
    });

    renderPage();
    fireEvent.click(await screen.findByRole("button", { name: /Mega Bangna/ }));

    expect(
      await screen.findByText("The seat map could not be loaded."),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Back to trips" }),
    ).toBeInTheDocument();
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
    // Nothing the student did takes the seat away, so the only way they learn
    // about it is the live region announcing itself.
    expect(
      screen.getAllByRole("status").map((region) => region.textContent),
    ).toContain(
      "Seat A1 was taken by another student and has been removed from your selection.",
    );
    expect(screen.getByText("Choose at least one seat.")).toBeInTheDocument();
  });

  it("pluralises the message when the poll takes more than one selected seat", async () => {
    vi.useFakeTimers();
    let seatsRequested = 0;
    stubApi({
      seats: () => {
        seatsRequested += 1;
        if (seatsRequested === 1) return json(seatMap());
        return json({
          ...seatMap(),
          seats: seatMap().seats.map((seat) => ({
            ...seat,
            state: "BOOKED" as SeatState,
          })),
        });
      },
    });

    renderPage();
    await tick();
    fireEvent.click(screen.getByRole("button", { name: /Mega Bangna/ }));
    await tick();
    fireEvent.click(screen.getByRole("button", { name: "Seat A1, available" }));
    fireEvent.click(screen.getByRole("button", { name: "Seat B1, held by you" }));

    await tick(10_000);

    expect(
      screen.getByText(
        "Seats A1, B1 were taken by another student and have been removed from your selection.",
      ),
    ).toBeInTheDocument();
  });

  it("shows the hold countdown and returns to seat selection when it expires", async () => {
    vi.useFakeTimers();
    let seatsRequested = 0;
    stubApi({
      hold: () => json(hold(new Date(Date.now() + 60_000).toISOString()), 201),
      seats: () => {
        seatsRequested += 1;
        return json(seatMap());
      },
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

    const beforeExpiry = seatsRequested;
    await tick(30_000);

    // A polite live region rather than plain text: the expiry is not an error
    // the student caused, but it has to be announced.
    expect(
      screen.getAllByRole("status").map((region) => region.textContent),
    ).toContain("Your seat hold expired. Choose your seats again.");
    expect(
      screen.getByRole("group", { name: "Seat map" }),
    ).toBeInTheDocument();
    // The map the student comes back to is the one the hold was released into.
    expect(seatsRequested).toBeGreaterThan(beforeExpiry);
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

  it("mints a new idempotency key for a booking made against a new hold", async () => {
    const keys: string[] = [];
    stubApi({
      createBooking: (init) => {
        keys.push(new Headers(init.headers).get("Idempotency-Key") ?? "");
        return keys.length === 1
          ? json({ detail: "The service is unavailable." }, 503)
          : json(booking, 201);
      },
    });

    renderPage();
    await reachPassengerDetails();
    fireEvent.click(screen.getByRole("button", { name: "Confirm booking" }));
    expect(
      await screen.findByText("The service is unavailable."),
    ).toBeInTheDocument();

    // The abandoned attempt's key must not travel onto a different hold: the
    // payload would differ and the server would reject a retry the student
    // cannot act on.
    fireEvent.click(screen.getByRole("button", { name: "Change seats" }));
    fireEvent.click(
      await screen.findByRole("button", { name: "Seat A1, available" }),
    );
    fireEvent.click(screen.getByRole("button", { name: "Hold these seats" }));
    await screen.findByRole("button", { name: "Confirm booking" });
    fireEvent.change(screen.getByLabelText("Full name"), {
      target: { value: "Somchai P." },
    });
    fireEvent.change(screen.getByLabelText("Phone number"), {
      target: { value: "0812345678" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Confirm booking" }));

    expect(await screen.findByText("AUV-260921-7KQ2M4XR")).toBeInTheDocument();
    expect(keys).toHaveLength(2);
    expect(keys[1]).not.toBe(keys[0]);
  });

  it("sends the student back to seat selection when the hold expired before confirmation", async () => {
    let seatsRequested = 0;
    stubApi({
      seats: () => {
        seatsRequested += 1;
        return json(seatMap());
      },
      createBooking: () =>
        json({ detail: "This seat hold has expired.", code: "hold_expired" }, 409),
    });

    renderPage();
    await reachPassengerDetails();
    const beforeFailure = seatsRequested;
    fireEvent.click(screen.getByRole("button", { name: "Confirm booking" }));

    expect(
      await screen.findByText(
        "Your seat hold expired before the booking was confirmed. Choose your seats again.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole("group", { name: "Seat map" })).toBeInTheDocument();
    // Nothing else refreshes the map on the way back: the poll is only
    // rescheduled, so the student would pick from the map the expired hold
    // was taken against.
    await vi.waitFor(() =>
      expect(seatsRequested).toBeGreaterThan(beforeFailure),
    );
  });

  it("points an already-used hold at my bookings rather than the seat map", async () => {
    // The 201 the student never saw is a real booking, so the message has to
    // land somewhere that shows it.
    let bookingsRequested = 0;
    stubApi({
      // Empty on first load: only the refetch can put the hidden booking on
      // screen, so the message and the list agree.
      bookings: () => {
        bookingsRequested += 1;
        return json(bookingsRequested === 1 ? [] : [booking]);
      },
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
        "Those seats are already booked. If that was you, the booking is in My bookings below.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("My bookings")).toBeInTheDocument();
    expect(
      await screen.findByText("AUV-260921-7KQ2M4XR"),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("group", { name: "Seat map" }),
    ).not.toBeInTheDocument();
  });

  it("sends the student back to seat selection when the hold no longer exists", async () => {
    stubApi({
      createBooking: () =>
        json(
          { detail: "This seat hold no longer exists.", code: "hold_not_found" },
          404,
        ),
    });

    renderPage();
    await reachPassengerDetails();
    fireEvent.click(screen.getByRole("button", { name: "Confirm booking" }));

    // Without this branch the 404 falls into the generic retry case and the
    // student retries against a hold that is not there.
    expect(
      await screen.findByText(
        "That seat hold is no longer available. Choose your seats again.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole("group", { name: "Seat map" })).toBeInTheDocument();
  });

  it("sends the student back to the trip list when the trip is withdrawn", async () => {
    stubApi({
      createBooking: () =>
        json(
          {
            detail: "This trip is no longer available.",
            code: "trip_not_available",
          },
          409,
        ),
    });

    renderPage();
    await reachPassengerDetails();
    fireEvent.click(screen.getByRole("button", { name: "Confirm booking" }));

    expect(
      await screen.findByText(
        "That trip is no longer available. Choose another.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("Upcoming trips")).toBeInTheDocument();
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

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Check the passenger name and phone number, then try again.",
    );
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

  it("asks the student to sign in again when the hold is refused as expired", async () => {
    // The sign-in screen replaces the page, so a 401 mid-flow needs no notice
    // of its own in the failure handler.
    await holdFailsWith(null, 401);

    expect(await screen.findByText("Sign in again")).toBeInTheDocument();
    expect(screen.queryByText("Upcoming trips")).not.toBeInTheDocument();
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
    // Waiting for payment is neither the success colour nor the failure one
    // the badge falls back to for anything it does not recognise.
    expect(screen.getByText("PENDING_PAYMENT")).toHaveClass("text-amber-800");
    expect(screen.getByText(/^AU → Mega Bangna · /)).toBeInTheDocument();
    expect(screen.getByText("Seat A1 · 35.00 THB")).toBeInTheDocument();
  });

  it("sends a payment slip as multipart and lets the browser set the boundary", async () => {
    // The shared request() helper forces application/json, which would
    // mis-type the upload; this call has to go around it.
    let uploaded = 0;
    const fetcher = stubApi({
      bookings: () =>
        json([uploaded === 0 ? booking : { ...booking, status: "PAYMENT_UNDER_REVIEW" }]),
      paymentProof: () => {
        uploaded += 1;
        return json({ ...booking, status: "PAYMENT_UNDER_REVIEW" });
      },
    });
    const slip = new File(["slip-bytes"], "slip.jpg", { type: "image/jpeg" });

    renderPage();
    fireEvent.change(await screen.findByLabelText("Upload your payment slip"), {
      target: { files: [slip] },
    });
    fireEvent.click(screen.getByRole("button", { name: "Send payment proof" }));

    expect(
      await screen.findByText(
        "Payment proof received. Staff confirm the booking once they have checked it.",
      ),
    ).toBeInTheDocument();
    const call = fetcher.mock.calls.find(([url]) =>
      String(url).endsWith("/payment-proof"),
    );
    expect(call?.[0]).toBe("/api/v1/bookings/booking-1/payment-proof");
    const init = call?.[1] as RequestInit;
    expect(init.method).toBe("POST");
    const headers = new Headers(init.headers);
    expect(headers.get("Content-Type")).toBeNull();
    expect(headers.get("Authorization")).toBe("Bearer student-token");
    expect(init.body).toBeInstanceOf(FormData);
    expect((init.body as FormData).get("file")).toBe(slip);
    // The list is refetched, so the student sees the new state rather than
    // the upload form they just used.
    expect(
      await screen.findByText(
        "Your payment proof is with an administrator. This booking is confirmed once they approve it.",
      ),
    ).toBeInTheDocument();
    expect(
      screen.queryByLabelText("Upload your payment slip"),
    ).not.toBeInTheDocument();
  });

  it("reports the reason a payment slip was refused and keeps the form", async () => {
    stubApi({
      bookings: () => json([booking]),
      paymentProof: () =>
        json(
          {
            detail: "A payment proof must be a JPEG, PNG, or WebP image.",
            code: "payment_proof_type_not_supported",
          },
          400,
        ),
    });

    renderPage();
    fireEvent.change(await screen.findByLabelText("Upload your payment slip"), {
      target: { files: [new File(["x"], "slip.pdf", { type: "application/pdf" })] },
    });
    fireEvent.click(screen.getByRole("button", { name: "Send payment proof" }));

    expect(
      await screen.findByText(
        "A payment proof must be a JPEG, PNG, or WebP image.",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Send payment proof" }),
    ).toBeInTheDocument();
  });

  it("paints an approved booking as a success and a cancelled one as a failure", async () => {
    stubApi({
      bookings: () =>
        json([
          { ...booking, status: "CONFIRMED" },
          { ...booking, id: "booking-2", reference: "AUV-260921-CANCELLED", status: "CANCELLED" },
        ]),
      trips: () => json([]),
    });

    renderPage();

    expect(await screen.findByText("CONFIRMED")).toHaveClass("text-emerald-700");
    expect(screen.getByText("CANCELLED")).toHaveClass("text-red-700");
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
      screen.getAllByRole("status").map((region) => region.textContent),
    ).toContain("You can hold at most 4 seats at a time.");
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

  it("lets the student take back seats their own failed release left held", async () => {
    // "Change seats" releases the hold without waiting on it, so a failed
    // release drops the student onto a map where their own seats read
    // HELD_BY_YOU. The map only shows that state once the release has
    // invalidated it, and those seats have to stay selectable.
    let seatsRequested = 0;
    stubApi({
      release: () => json({ detail: "The hold could not be released." }, 500),
      seats: () => {
        seatsRequested += 1;
        return json(seatMap(seatsRequested < 3 ? "AVAILABLE" : "HELD_BY_YOU"));
      },
    });

    renderPage();
    await reachPassengerDetails();
    fireEvent.click(screen.getByRole("button", { name: "Change seats" }));

    fireEvent.click(
      await screen.findByRole("button", { name: "Seat A1, held by you" }),
    );

    expect(
      screen.getByRole("button", { name: "Seat A1, selected", pressed: true }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Hold these seats" }),
    ).toBeEnabled();
  });
});
