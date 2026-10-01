import type { Trip } from "../types/inventory";
import { toBangkokInputs } from "./dates";

export type TripFilter = "upcoming" | "past" | "cancelled";

export function filterTrips(
  trips: Trip[],
  filter: TripFilter,
  now: number,
): Trip[] {
  const shown = trips.filter((trip) => {
    if (trip.status === "CANCELLED") return filter === "cancelled";
    const ahead = Date.parse(trip.departureAt) >= now;
    return filter === (ahead ? "upcoming" : "past");
  });
  const direction = filter === "upcoming" ? 1 : -1;
  return shown.sort(
    (left, right) =>
      direction * (Date.parse(left.departureAt) - Date.parse(right.departureAt)),
  );
}

export function groupByDay(trips: Trip[]): { day: string; trips: Trip[] }[] {
  const groups = new Map<string, Trip[]>();
  for (const trip of trips) {
    const day = toBangkokInputs(trip.departureAt).date;
    groups.set(day, [...(groups.get(day) ?? []), trip]);
  }
  return [...groups].map(([day, grouped]) => ({ day, trips: grouped }));
}
