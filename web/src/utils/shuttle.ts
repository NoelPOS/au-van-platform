import type { VanRoute } from "../types/inventory";
import type { DepartureLine } from "../types/schedule";

export type Shuttle = {
  outId: string;
  backId: string;
  vehicleId: string;
  first: string;
  last: string;
  wait: number;
};

export type Clash = { line: DepartureLine; existing: DepartureLine };

function minutesOf(clock: string): number {
  const [hours, minutes] = clock.split(":").map(Number);
  return hours * 60 + minutes;
}

function clockOf(minutes: number): string {
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${pad(Math.floor(minutes / 60))}:${pad(minutes % 60)}`;
}

export function reverseOf(
  route: VanRoute | undefined,
  routes: VanRoute[],
): VanRoute | undefined {
  return routes.find(
    (other) =>
      other.origin === route?.destination &&
      other.destination === route?.origin,
  );
}

export function shuttleLines(
  shuttle: Shuttle,
  routes: VanRoute[],
): DepartureLine[] {
  const out = routes.find((route) => route.id === shuttle.outId);
  const back = routes.find((route) => route.id === shuttle.backId);
  if (!out) return [];
  const last = minutesOf(shuttle.last);
  const lines: DepartureLine[] = [];
  let leg = out;
  let at = minutesOf(shuttle.first);
  while (at <= last) {
    lines.push({
      time: clockOf(at),
      routeId: leg.id,
      vehicleId: shuttle.vehicleId,
    });
    if (back) {
      at += leg.durationMinutes + shuttle.wait;
      leg = leg === out ? back : out;
    } else {
      at += 2 * out.durationMinutes + shuttle.wait;
    }
  }
  return lines;
}

export function firstClash(
  lines: DepartureLine[],
  existing: DepartureLine[],
  routes: VanRoute[],
): Clash | null {
  const window = (line: DepartureLine) => {
    const start = minutesOf(line.time);
    const route = routes.find((item) => item.id === line.routeId);
    return { start, end: start + (route?.durationMinutes ?? 0) };
  };
  for (const line of lines) {
    const mine = window(line);
    for (const other of existing) {
      if (other.vehicleId !== line.vehicleId) continue;
      const theirs = window(other);
      if (mine.start < theirs.end && theirs.start < mine.end)
        return { line, existing: other };
    }
  }
  return null;
}
