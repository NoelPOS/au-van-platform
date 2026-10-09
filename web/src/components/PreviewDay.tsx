import type { VanRoute, Vehicle } from "../types/inventory";
import type { PlannedDeparture } from "../types/schedule";
import { dayHeading, shortDay } from "../utils/calendar";
import { RouteRule } from "./ui/RouteLine";

const reasonTone = {
  CREATE: "",
  CLASH: "text-warning",
  PAST: "text-muted",
  UNAVAILABLE: "text-danger",
};

export function PreviewDay({
  date,
  today,
  departures,
  routes,
  vans,
}: {
  date: string;
  today: string;
  departures: PlannedDeparture[];
  routes: VanRoute[];
  vans: Vehicle[];
}) {
  const creating = departures.filter((entry) => entry.outcome === "CREATE");
  const skipped = departures.length - creating.length;

  return (
    <section aria-label={`Plan for ${shortDay(date)}`}>
      <h3 className="mb-2 flex items-baseline justify-between gap-3 font-display text-lg text-brand-900">
        {dayHeading(date, today)}
        <span className="font-mono text-[12px] text-muted">
          {creating.length} new{skipped ? ` · ${skipped} skipped` : ""}
        </span>
      </h3>
      <ol className="divide-y divide-dashed divide-line overflow-hidden rounded-2xl border border-line">
        {departures.map((entry) => {
          const route = routes.find((item) => item.id === entry.routeId);
          const van = vans.find((item) => item.id === entry.vehicleId);
          const creates = entry.outcome === "CREATE";
          return (
            <li
              className={`grid grid-cols-[3.75rem_minmax(0,1fr)_auto] items-center gap-x-3 px-4 py-3 ${creates ? "" : "bg-paper/60"}`}
              key={`${entry.time}-${entry.vehicleId}-${entry.routeId}`}
            >
              <span
                className={`font-mono text-[17px] font-medium tabular-nums ${creates ? "text-ink" : "text-muted line-through"}`}
              >
                {entry.time}
              </span>
              <span className="min-w-0">
                <span className="block truncate text-sm font-medium text-ink">
                  {route?.destination}
                </span>
                <span className="flex items-center gap-1.5 text-[12px] text-muted">
                  <RouteRule className="w-3" />
                  <span className="truncate">from {route?.origin}</span>
                </span>
              </span>
              <span className="font-mono text-[12px] text-muted">
                {van?.code}
              </span>
              {creates ? (
                <span className="col-start-2 col-end-4 mt-0.5 text-[12px] font-medium tracking-[0.12em] text-brand-500 uppercase">
                  New
                </span>
              ) : (
                <span
                  className={`col-start-2 col-end-4 mt-0.5 text-[13px] ${reasonTone[entry.outcome]}`}
                >
                  {entry.reason}
                </span>
              )}
            </li>
          );
        })}
      </ol>
    </section>
  );
}
