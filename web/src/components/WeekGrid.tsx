import { Check } from "lucide-react";
import type { Vehicle } from "../types/inventory";
import { addDays, longDay } from "../utils/calendar";
import { bangkokTime } from "../utils/dates";
import { plural } from "../utils/schedule";
import type { CalendarProps } from "./MonthGrid";
import { RouteRule } from "./ui/RouteLine";

const weekday = new Intl.DateTimeFormat("en-GB", {
  timeZone: "UTC",
  weekday: "short",
});

export function WeekGrid({
  start,
  days,
  routes,
  vans,
  today,
  selecting,
  selected,
  onOpen,
  onToggle,
}: CalendarProps & { start: string; vans: Vehicle[] }) {
  const week = Array.from({ length: 7 }, (_, index) => addDays(start, index));

  return (
    <div className="grid overflow-hidden rounded-2xl border border-line bg-line max-lg:gap-px lg:grid-cols-7 lg:gap-px">
      {week.map((day) => {
        const trips = days.get(day) ?? [];
        const chosen = selecting && selected.includes(day);
        const past = day < today;
        return (
          <section
            aria-label={longDay(day)}
            className={`flex flex-col max-lg:flex-row max-lg:items-start ${chosen ? "bg-brand-50" : past ? "bg-paper/70" : "bg-card"}`}
            key={day}
          >
            <button
              aria-label={`${longDay(day)}, ${plural(trips.length, "departure")}`}
              aria-pressed={selecting ? chosen : undefined}
              className={`flex shrink-0 items-baseline gap-2 px-3 py-3 text-left transition-colors duration-150 hover:bg-brand-50/70 disabled:cursor-not-allowed max-lg:w-20 max-lg:flex-col max-lg:gap-1 lg:border-b lg:border-dashed lg:border-line ${chosen ? "ring-2 ring-brand-600 ring-inset" : ""}`}
              disabled={selecting && past}
              onClick={() => (selecting ? onToggle([day]) : onOpen(day))}
              type="button"
            >
              <span
                className={`font-mono text-[10px] tracking-[0.14em] uppercase ${day === today ? "text-warning" : "text-muted"}`}
              >
                {day === today
                  ? "Today"
                  : weekday.format(new Date(`${day}T00:00:00Z`))}
              </span>
              <span className="flex items-center gap-1.5 font-display text-2xl leading-none text-brand-900">
                {Number(day.slice(8))}
                {chosen && (
                  <Check
                    aria-hidden
                    className="size-4 text-brand-600"
                    strokeWidth={3}
                  />
                )}
              </span>
            </button>
            {trips.length === 0 ? (
              <p className="px-3 py-3 text-[13px] text-muted/80 italic lg:py-4">
                No departures
              </p>
            ) : (
              <ol className="flex min-w-0 flex-1 flex-col divide-y divide-dashed divide-line">
                {trips.map((trip) => {
                  const route = routes.find(
                    (entry) => entry.id === trip.routeId,
                  );
                  const cancelled = trip.status === "CANCELLED";
                  return (
                    <li
                      className={`px-3 py-2 ${cancelled ? "text-muted line-through" : ""}`}
                      key={trip.id}
                    >
                      <span className="flex items-baseline justify-between gap-2">
                        <span className="font-mono text-[15px] font-medium text-ink tabular-nums">
                          {bangkokTime(trip.departureAt)}
                        </span>
                        <span className="font-mono text-[11px] text-muted">
                          {vans.find((van) => van.id === trip.vehicleId)?.code}
                        </span>
                      </span>
                      <span className="mt-0.5 flex min-w-0 items-center gap-1.5 text-[13px] text-muted">
                        <RouteRule className="w-2.5" />
                        <span className="truncate">{route?.destination}</span>
                      </span>
                    </li>
                  );
                })}
              </ol>
            )}
          </section>
        );
      })}
    </div>
  );
}
