import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import { vi } from "vitest";
import { AdminOperationsPage } from "../pages/AdminOperationsPage";
import type { AuthSession } from "../types/auth";

export function inDays(days: number) {
  return new Date(Date.now() + days * 86_400_000).toISOString();
}

export const session: AuthSession = {
  accessToken: "admin-token",
  expiresIn: 900,
  user: { id: "admin-id", role: "ADMIN", displayName: "Noel" },
};

export const route = {
  id: "route-1",
  origin: "AU",
  destination: "Mega Bangna",
  fare: 35,
  durationMinutes: 45,
  status: "ACTIVE",
};

export const trip = {
  id: "trip-1",
  routeId: "route-1",
  vehicleId: "vehicle-1",
  departureAt: inDays(1),
  fare: 35,
  durationMinutes: 45,
  status: "ACTIVE",
  seats: [{ label: "A1", rowNumber: 1, columnNumber: 1 }],
};

export const operations = {
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

export const deadLetter = {
  id: "event-1",
  eventType: "BOOKING_CANCELLED",
  aggregateId: "booking-1",
  recipientUserId: "student-1",
  attempts: 5,
  lastError: "LINE refused the push: 400 invalid recipient.",
  createdAt: "2026-09-21T09:00:00Z",
  processedAt: "2026-09-21T09:10:00Z",
};

export function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

type Routes = {
  trips?: () => Response;
  trip?: () => Response;
  deadLetters?: () => Response;
};

export function stubApi(overrides: Routes = {}) {
  const fetcher = vi.fn(
    async (input: RequestInfo | URL, _init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith("/operations/trips/trip-1"))
        return overrides.trip?.() ?? json(operations);
      if (url.endsWith("/operations/dead-letters"))
        return overrides.deadLetters?.() ?? json([deadLetter]);
      if (url.endsWith("/admin/trips")) return overrides.trips?.() ?? json([trip]);
      if (url.endsWith("/admin/routes")) return json([route]);
      throw new Error(`unexpected request: ${url}`);
    },
  );
  vi.stubGlobal("fetch", fetcher);
  return fetcher;
}

export function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <AdminOperationsPage session={session} />
    </QueryClientProvider>,
  );
}
