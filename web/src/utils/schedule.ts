import type { Trip } from "../types/inventory";
import type {
  DepartureLine,
  PlannedDeparture,
  PlanOutcome,
} from "../types/schedule";
import { addDays } from "./calendar";
import { bangkokTime, toBangkokInputs } from "./dates";

export type DraftLine = DepartureLine & { key: number };

let nextKey = 0;

export function draftLine(line: DepartureLine): DraftLine {
  nextKey += 1;
  return { ...line, key: nextKey };
}

export function tripsByDay(trips: Trip[]): Map<string, Trip[]> {
  const days = new Map<string, Trip[]>();
  const ordered = [...trips].sort(
    (left, right) =>
      Date.parse(left.departureAt) - Date.parse(right.departureAt),
  );
  for (const trip of ordered) {
    const day = toBangkokInputs(trip.departureAt).date;
    days.set(day, [...(days.get(day) ?? []), trip]);
  }
  return days;
}

export function linesOf(trips: Trip[]): DepartureLine[] {
  return trips
    .filter((trip) => trip.status === "ACTIVE")
    .map((trip) => ({
      time: bangkokTime(trip.departureAt),
      routeId: trip.routeId,
      vehicleId: trip.vehicleId,
    }));
}

export function timeSpan(lines: DepartureLine[]): string {
  const times = lines.map((line) => line.time).sort();
  if (times.length === 0) return "";
  const first = times[0];
  const last = times[times.length - 1];
  return first === last ? first : `${first} – ${last}`;
}

export function countOutcomes(
  departures: PlannedDeparture[],
): Record<PlanOutcome, number> {
  const counts = { CREATE: 0, CLASH: 0, PAST: 0, UNAVAILABLE: 0 };
  for (const departure of departures) counts[departure.outcome] += 1;
  return counts;
}

export function groupByDate(
  departures: PlannedDeparture[],
): { date: string; departures: PlannedDeparture[] }[] {
  const groups = new Map<string, PlannedDeparture[]>();
  for (const departure of departures) {
    groups.set(departure.date, [
      ...(groups.get(departure.date) ?? []),
      departure,
    ]);
  }
  return [...groups].map(([date, grouped]) => ({ date, departures: grouped }));
}

export function weekStart(day: string): string {
  const weekday = (new Date(`${day}T00:00:00Z`).getUTCDay() + 6) % 7;
  return addDays(day, -weekday);
}

export function plural(
  count: number,
  noun: string,
  nouns = `${noun}s`,
): string {
  return `${count} ${count === 1 ? noun : nouns}`;
}

export function passedToday(days: string[], now: number): string | null {
  const today = toBangkokInputs(new Date(now).toISOString());
  return days.includes(today.date) ? today.time : null;
}

export function pastClockError(
  clock: string | null,
  passedUntil: string | null,
): string | undefined {
  if (!clock || !passedUntil || clock > passedUntil) return undefined;
  return `${clock} has already passed today. Choose a later time or another day.`;
}
