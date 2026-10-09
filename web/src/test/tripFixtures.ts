import { fireEvent, screen, within } from "@testing-library/react";
import { vi } from "vitest";
import type { Trip } from "../types/inventory";

export const route = {
  id: "route-1",
  origin: "AU",
  destination: "Mega Bangna",
  fare: 35,
  durationMinutes: 45,
  status: "ACTIVE",
};

export const layout = {
  id: "layout-1",
  name: "Commuter",
  seats: [
    { label: "A1", rowNumber: 1, columnNumber: 1 },
    { label: "A2", rowNumber: 1, columnNumber: 2 },
    { label: "A3", rowNumber: 1, columnNumber: 3 },
  ],
};

export const van = {
  id: "van-1",
  code: "VAN-01",
  name: "Hiace",
  seatLayoutId: "layout-1",
  status: "ACTIVE",
};

export function tripAt(departureAt: string, overrides: Partial<Trip> = {}) {
  return {
    id: `trip-${departureAt}`,
    routeId: "route-1",
    vehicleId: "van-1",
    departureAt,
    fare: 35,
    durationMinutes: 45,
    status: "ACTIVE",
    cancellationReason: null,
    seats: [],
    ...overrides,
  };
}

export function freezeBangkokTime(wallClock: string) {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date(`${wallClock}:00+07:00`));
}

export function drawer() {
  return within(screen.getByRole("dialog"));
}

export async function showList() {
  fireEvent.click(await screen.findByRole("radio", { name: "List" }));
}

export function tripOperations(tripId: string) {
  return {
    tripId,
    origin: "AU",
    destination: "Mega Bangna",
    departureAt: "2026-01-05T09:00:00Z",
    tripStatus: "ACTIVE",
    totalSeats: 3,
    claimedSeats: 3,
    bookingsByStatus: [
      { status: "PENDING_PAYMENT", count: 1 },
      { status: "PAYMENT_UNDER_REVIEW", count: 1 },
      { status: "PAYMENT_REJECTED", count: 0 },
      { status: "CONFIRMED", count: 1 },
      { status: "CANCELLED", count: 4 },
    ],
    waitlist: [place("w-1", "WAITING", 1), place("w-2", "WITHDRAWN", null)],
  };
}

function place(entryId: string, status: string, position: number | null) {
  return {
    entryId,
    userId: `user-${entryId}`,
    displayName: null,
    seatsWanted: 1,
    status,
    position,
    joinedAt: "2026-01-04T01:00:00Z",
    promotionHoldId: null,
    promotionExpiresAt: null,
  };
}
