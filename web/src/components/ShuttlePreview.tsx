import type { VanRoute, Vehicle } from "../types/inventory";
import type { DepartureLine } from "../types/schedule";
import { plural } from "../utils/schedule";

export function ShuttlePreview({
  lines,
  routes,
  vans,
}: {
  lines: DepartureLine[];
  routes: VanRoute[];
  vans: Vehicle[];
}) {
  return (
    <section aria-label="Shuttle preview">
      <p className="mb-2 text-sm font-medium text-ink">
        Adds {plural(lines.length, "departure")}
      </p>
      <ol className="divide-y divide-dashed divide-line overflow-hidden rounded-2xl border border-line bg-card">
        {lines.map((line) => {
          const route = routes.find((item) => item.id === line.routeId);
          return (
            <li
              className="grid grid-cols-[3.75rem_minmax(0,1fr)_auto] items-center gap-x-3 px-4 py-2.5"
              key={line.time}
            >
              <span className="font-mono text-[15px] font-medium tabular-nums text-ink">
                {line.time}
              </span>
              <span className="truncate text-sm text-ink">
                <span className="sr-only">{`${route?.origin} `}</span>
                <span className="text-muted">to </span>
                {route?.destination}
              </span>
              <span className="font-mono text-[12px] text-muted">
                {vans.find((van) => van.id === line.vehicleId)?.code}
              </span>
            </li>
          );
        })}
      </ol>
    </section>
  );
}
