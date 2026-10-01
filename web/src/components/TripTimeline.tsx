import { useState } from "react";
import type { Trip, VanRoute } from "../types/inventory";
import { formatDay, formatTime } from "../utils/format";
import { StatusBadge } from "./ui/StatusBadge";

type Props = {
  trips: Trip[];
  routes: VanRoute[];
  selectedId: string | null;
  onSelect: (tripId: string) => void;
};

function byDay(trips: Trip[]): [string, Trip[]][] {
  const days = new Map<string, Trip[]>();
  for (const trip of trips) {
    const day = formatDay(trip.departureAt);
    days.set(day, [...(days.get(day) ?? []), trip]);
  }
  return [...days];
}

export function TripTimeline({ trips, routes, selectedId, onSelect }: Props) {
  const [now] = useState(Date.now);
  const sorted = [...trips].sort(
    (a, b) => Date.parse(a.departureAt) - Date.parse(b.departureAt),
  );
  const upcoming = sorted.filter((trip) => Date.parse(trip.departureAt) >= now);
  const earlier = sorted.filter((trip) => Date.parse(trip.departureAt) < now).reverse();
  const shared = { routes, selectedId, onSelect };

  return (
    <section aria-labelledby="timeline-heading">
      <h2
        className="mb-4 text-xs font-semibold tracking-[0.14em] text-muted uppercase"
        id="timeline-heading"
      >
        Upcoming departures
      </h2>
      {upcoming.length === 0 ? (
        <p className="font-serif text-lg text-muted italic">
          No departures ahead. Schedule one from Trips and it lands here.
        </p>
      ) : (
        <TripDays trips={upcoming} {...shared} />
      )}
      {earlier.length > 0 && (
        <details className="mt-8 border-t border-line pt-4">
          <summary className="min-h-11 cursor-pointer py-2 text-sm font-semibold text-brand-500">
            {`Earlier departures (${earlier.length})`}
          </summary>
          <div className="mt-3"><TripDays trips={earlier} {...shared} /></div>
        </details>
      )}
    </section>
  );
}

function TripDays({ trips, routes, selectedId, onSelect }: Props) {
  return byDay(trips).map(([day, entries]) => (
    <div className="mb-5" key={day}>
      <h3 className="mb-2 font-serif text-sm text-brand-700 italic">{day}</h3>
      <ol aria-label={day} className="ml-1.5 border-l border-line">
        {entries.map((trip) => {
          const route = routes.find((value) => value.id === trip.routeId);
          const selected = trip.id === selectedId;
          const seats = trip.seats.length;
          return (
            <li className="relative pl-5" key={trip.id}>
              <span
                aria-hidden="true"
                className={`absolute top-6.5 -left-1.5 size-3 rounded-full border-2 ${
                  selected ? "border-accent bg-accent" : "border-line bg-paper"
                }`}
              />
              <button
                aria-pressed={selected}
                className={`my-1 flex w-full items-center gap-4 rounded-xl border px-4 py-3 text-left transition-colors duration-150 ease-out focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500 ${
                  selected
                    ? "border-brand-500 bg-brand-50"
                    : "border-transparent hover:border-line hover:bg-card"
                }`}
                onClick={() => onSelect(trip.id)}
                type="button"
              >
                <span className="font-serif text-2xl text-brand-900 tabular-nums">
                  {formatTime(trip.departureAt)}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-semibold text-ink">
                    {route ? `${route.origin} → ${route.destination}` : "Unknown route"}
                  </span>
                  <span className="text-xs text-muted">
                    {`${seats} seat${seats === 1 ? "" : "s"}`}
                  </span>
                </span>
                {trip.status === "CANCELLED" && <StatusBadge value={trip.status} />}
              </button>
            </li>
          );
        })}
      </ol>
    </div>
  ));
}
