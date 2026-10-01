import { screen, within } from "@testing-library/react";
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
