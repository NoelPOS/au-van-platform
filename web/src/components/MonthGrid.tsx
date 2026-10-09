import { Check } from "lucide-react";
import type { Trip, VanRoute } from "../types/inventory";
import { longDay, monthGrid, monthTitle } from "../utils/calendar";
import { bangkokTime } from "../utils/dates";
import { plural } from "../utils/schedule";

const weekdays = [
  ["Mon", "Monday"],
  ["Tue", "Tuesday"],
  ["Wed", "Wednesday"],
  ["Thu", "Thursday"],
  ["Fri", "Friday"],
  ["Sat", "Saturday"],
  ["Sun", "Sunday"],
];

export type CalendarProps = {
  days: Map<string, Trip[]>;
  routes: VanRoute[];
  today: string;
  selecting: boolean;
  selected: string[];
  onOpen: (day: string) => void;
  onToggle: (days: string[]) => void;
};

function tripTone(trip: Trip, past: boolean) {
  if (trip.status === "CANCELLED") return "text-muted line-through";
  return past ? "text-muted" : "text-ink";
}

function cellTone(day: string, today: string, chosen: boolean) {
  if (chosen) return "bg-brand-50 ring-2 ring-brand-600 ring-inset";
  if (day < today) return "bg-card text-muted hover:bg-brand-50/60";
  return "bg-card hover:bg-brand-50/60";
}

export function MonthGrid({
  month,
  days,
  routes,
  today,
  selecting,
  selected,
  onOpen,
  onToggle,
}: CalendarProps & { month: string }) {
  const grid = monthGrid(month);
  const cells = [
    ...grid,
    ...Array<null>((7 - (grid.length % 7)) % 7).fill(null),
  ];
  const open = (day: string) => (selecting ? onToggle([day]) : onOpen(day));

  function everyWeekday(index: number) {
    return cells.filter(
      (day, position): day is string =>
        day !== null && position % 7 === index && day >= today,
    );
  }

  return (
    <div className="overflow-hidden rounded-2xl border border-line">
      <div className="grid grid-cols-7 gap-px bg-line">
        {weekdays.map(([short, long], index) =>
          selecting ? (
            <button
              aria-label={`Select every ${long} left in ${monthTitle(month)}`}
              className="bg-card py-2 text-[11px] font-semibold tracking-[0.14em] text-brand-500 uppercase underline decoration-dotted underline-offset-4 hover:bg-brand-50 disabled:text-muted disabled:no-underline"
              disabled={everyWeekday(index).length === 0}
              key={short}
              onClick={() => onToggle(everyWeekday(index))}
              type="button"
            >
              {short}
            </button>
          ) : (
            <span
              aria-hidden
              className="bg-card py-2 text-center text-[11px] font-semibold tracking-[0.14em] text-muted uppercase"
              key={short}
            >
              {short}
            </span>
          ),
        )}
        {cells.map((day, index) => {
          if (!day) return <span className="bg-paper" key={`blank-${index}`} />;
          const trips = days.get(day) ?? [];
          const chosen = selecting && selected.includes(day);
          const shown = trips.slice(0, 3);
          return (
            <button
              aria-label={`${longDay(day)}, ${plural(trips.length, "departure")}`}
              aria-pressed={selecting ? chosen : undefined}
              className={`relative flex min-h-16 flex-col items-start gap-1 p-1.5 text-left transition-colors duration-150 disabled:cursor-not-allowed sm:min-h-28 sm:p-2.5 ${cellTone(day, today, chosen)}`}
              disabled={selecting && day < today}
              key={day}
              onClick={() => open(day)}
              type="button"
            >
              <span
                className={`-mt-1 -ml-1 grid size-7 place-items-center rounded-full font-display text-base leading-none sm:text-lg ${day === today ? "bg-accent text-brand-900" : ""}`}
              >
                {Number(day.slice(8))}
              </span>
              {chosen && (
                <Check
                  aria-hidden
                  className="absolute top-1.5 right-1.5 size-3.5 text-brand-600"
                  strokeWidth={3}
                />
              )}
              <span aria-hidden className="mt-auto flex gap-0.5 sm:hidden">
                {trips.slice(0, 5).map((trip) => (
                  <span
                    className={`size-1.5 rounded-full ${trip.status === "CANCELLED" ? "border border-brand-500/60" : "bg-brand-500/60"}`}
                    key={trip.id}
                  />
                ))}
              </span>
              <span className="hidden w-full flex-col gap-0.5 sm:flex">
                {shown.map((trip) => (
                  <span
                    className={`flex min-w-0 gap-1.5 font-mono text-[11.5px] leading-tight ${tripTone(trip, day < today)}`}
                    key={trip.id}
                    title={trip.cancellationReason ?? undefined}
                  >
                    {bangkokTime(trip.departureAt)}
                    <span className="truncate font-sans text-muted">
                      {routes.find((route) => route.id === trip.routeId)
                        ?.destination ?? ""}
                    </span>
                  </span>
                ))}
                {trips.length > shown.length && (
                  <span className="text-[11px] text-brand-500">
                    +{trips.length - shown.length} more
                  </span>
                )}
              </span>
            </button>
          );
        })}
      </div>
    </div>
  );
}
