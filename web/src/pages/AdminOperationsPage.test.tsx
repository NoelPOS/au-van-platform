import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { AuthSession } from "../types/auth";
import { AdminOperationsPage } from "./AdminOperationsPage";

const session: AuthSession = {
  accessToken: "admin-token",
  expiresIn: 900,
  user: { id: "admin-id", role: "ADMIN", displayName: "Noel" },
};

const route = {
  id: "route-1",
  origin: "AU",
  destination: "Mega Bangna",
  fare: 35,
  durationMinutes: 45,
  status: "ACTIVE",
};

const trip = {
  id: "trip-1",
  routeId: "route-1",
  vehicleId: "vehicle-1",
  departureAt: "2026-10-01T01:00:00Z",
  fare: 35,
  durationMinutes: 45,
  status: "ACTIVE",
  seats: [{ label: "A1", rowNumber: 1, columnNumber: 1 }],
};

const operations = {
  tripId: "trip-1",
  origin: "AU",
  destination: "Mega Bangna",
  departureAt: "2026-10-01T01:00:00Z",
  tripStatus: "ACTIVE",
  totalSeats: 2,
  claimedSeats: 2,
  bookingsByStatus: [
    { status: "PENDING_PAYMENT", count: 1 },
    { status: "PAYMENT_UNDER_REVIEW", count: 0 },
    { status: "PAYMENT_REJECTED", count: 0 },
    { status: "CONFIRMED", count: 3 },
    { status: "CANCELLED", count: 0 },
  ],
  waitlist: [
    {
      entryId: "entry-1",
      userId: "student-1",
      displayName: "Somchai P.",
      seatsWanted: 1,
      status: "WAITING",
      position: 1,
      joinedAt: "2026-09-21T10:00:00Z",
      promotionHoldId: null,
      promotionExpiresAt: null,
    },
    {
      entryId: "entry-2",
      userId: "student-2",
      displayName: "Malee K.",
      seatsWanted: 2,
      status: "PROMOTED",
      position: 2,
      joinedAt: "2026-09-21T10:05:00Z",
      promotionHoldId: "hold-1",
      promotionExpiresAt: "2026-09-21T10:35:00Z",
    },
    {
      entryId: "entry-3",
      userId: "student-3",
      displayName: null,
      seatsWanted: 1,
      status: "WITHDRAWN",
      position: null,
      joinedAt: "2026-09-21T10:10:00Z",
      promotionHoldId: null,
      promotionExpiresAt: null,
    },
  ],
};

const deadLetter = {
  id: "event-1",
  eventType: "BOOKING_CANCELLED",
  aggregateId: "booking-1",
  recipientUserId: "student-1",
  attempts: 5,
  lastError: "LINE refused the push: 400 invalid recipient.",
  createdAt: "2026-09-21T09:00:00Z",
  processedAt: "2026-09-21T09:10:00Z",
};

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

type Routes = {
  trip?: () => Response;
  deadLetters?: () => Response;
};

function stubApi(overrides: Routes = {}) {
  const fetcher = vi.fn(
    async (input: RequestInfo | URL, _init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith("/operations/trips/trip-1"))
        return overrides.trip?.() ?? json(operations);
      if (url.endsWith("/operations/dead-letters"))
        return overrides.deadLetters?.() ?? json([deadLetter]);
      if (url.endsWith("/admin/trips")) return json([trip]);
      if (url.endsWith("/admin/routes")) return json([route]);
      throw new Error(`unexpected request: ${url}`);
    },
  );
  vi.stubGlobal("fetch", fetcher);
  return fetcher;
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <AdminOperationsPage session={session} />
    </QueryClientProvider>,
  );
}

async function chooseTheTrip() {
  fireEvent.change(await screen.findByLabelText("Trip"), {
    target: { value: "trip-1" },
  });
}

describe("AdminOperationsPage", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("asks for nothing about a trip until one is chosen", async () => {
    const fetcher = stubApi();

    renderPage();

    expect(
      await screen.findByText("Choose a trip to see its bookings and waitlist."),
    ).toBeInTheDocument();
    expect(
      fetcher.mock.calls.filter(([url]) =>
        String(url).includes("/operations/trips/"),
      ),
    ).toHaveLength(0);
  });

  it("reports the queue, its promotions and the bookings by status", async () => {
    const fetcher = stubApi();

    renderPage();
    await chooseTheTrip();

    expect(await screen.findByText("Somchai P.")).toBeInTheDocument();
    expect(screen.getByText("#1")).toBeInTheDocument();
    expect(screen.getByText("Malee K.")).toBeInTheDocument();
    expect(screen.getByText("#2")).toBeInTheDocument();
    // Promoted, with the window the student has to act in.
    expect(screen.getByText("PROMOTED")).toBeInTheDocument();
    expect(screen.getByText(/Offer ends/)).toBeInTheDocument();
    // An entry that has ended is still listed and holds no place.
    expect(screen.getByText("WITHDRAWN")).toBeInTheDocument();
    expect(screen.getByText("—")).toBeInTheDocument();
    expect(screen.getByText(/2 of 2 seats claimed/)).toBeInTheDocument();
    expect(screen.getByText("CONFIRMED")).toBeInTheDocument();
    expect(screen.getByText("3")).toBeInTheDocument();
    const call = fetcher.mock.calls.find(([url]) =>
      String(url).includes("/operations/trips/"),
    );
    expect(call?.[0]).toBe("/api/v1/admin/operations/trips/trip-1");
    expect(new Headers(call?.[1]?.headers).get("Authorization")).toBe(
      "Bearer admin-token",
    );
  });

  it("lists the dead letters the dispatcher gave up on", async () => {
    stubApi();

    renderPage();

    expect(await screen.findByText("BOOKING_CANCELLED")).toBeInTheDocument();
    expect(
      screen.getByText("LINE refused the push: 400 invalid recipient."),
    ).toBeInTheDocument();
    expect(screen.getByText("5")).toBeInTheDocument();
  });

  it("says why a trip could not be read rather than showing an empty queue", async () => {
    stubApi({ trip: () => json({ detail: "Trip not found." }, 404) });

    renderPage();
    await chooseTheTrip();

    expect(
      await screen.findByText("Could not load this trip"),
    ).toBeInTheDocument();
    expect(screen.getByText("Trip not found.")).toBeInTheDocument();
    // The dead letters are a separate read and are unaffected by it.
    expect(screen.getByText("BOOKING_CANCELLED")).toBeInTheDocument();
  });

  it("says why the dead letters could not be read", async () => {
    stubApi({
      deadLetters: () => json({ detail: "Access denied." }, 403),
    });

    renderPage();

    expect(
      await screen.findByText("Could not load dead letters"),
    ).toBeInTheDocument();
    expect(screen.getByText("Access denied.")).toBeInTheDocument();
  });

  it("offers a retry when the trip listing itself fails", async () => {
    let attempts = 0;
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        if (url.endsWith("/admin/trips")) {
          attempts += 1;
          return attempts === 1
            ? json({ detail: "Access denied." }, 403)
            : json([trip]);
        }
        if (url.endsWith("/admin/routes")) return json([route]);
        if (url.endsWith("/operations/dead-letters")) return json([]);
        throw new Error(`unexpected request: ${url}`);
      }),
    );

    renderPage();

    expect(
      await screen.findByText("Could not load operations"),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));

    expect(await screen.findByLabelText("Trip")).toBeInTheDocument();
  });
});
