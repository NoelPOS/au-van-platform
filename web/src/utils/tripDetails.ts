import type { SeatLayout, Trip, VanRoute, Vehicle } from "../types/inventory";
import { addMinutes, bangkokTime } from "./dates";
import { formatFare, seatCount } from "./format";

export function seatsOf(van: Vehicle | undefined, layouts: SeatLayout[]) {
  return layouts.find((layout) => layout.id === van?.seatLayoutId)?.seats
    .length;
}

export function routeOption(route: VanRoute) {
  return {
    value: route.id,
    label: `${route.origin} → ${route.destination}`,
    detail: `${formatFare(route.fare)} · ${route.durationMinutes} min`,
  };
}

export function vanOption(van: Vehicle, layouts: SeatLayout[]) {
  const seats = seatsOf(van, layouts);
  return {
    value: van.id,
    label: `${van.code} — ${van.name}`,
    detail: seats === undefined ? undefined : seatCount(seats),
  };
}

export function departureTimes(
  trips: Trip[],
  routeId: string,
  except?: string,
): string[] {
  const times = trips
    .filter((trip) => trip.routeId === routeId && trip.id !== except)
    .map((trip) => bangkokTime(trip.departureAt));
  return [...new Set(times)].sort();
}

export function tripSummary(trip: {
  clock: string | null;
  durationMinutes?: number;
  fare?: number;
  seats?: number;
}): string {
  const { clock, durationMinutes, fare, seats } = trip;
  return [
    clock &&
      durationMinutes !== undefined &&
      `Arrives ${addMinutes(clock, durationMinutes)}`,
    fare !== undefined && formatFare(fare),
    seats !== undefined && seatCount(seats),
  ]
    .filter(Boolean)
    .join(" · ");
}
