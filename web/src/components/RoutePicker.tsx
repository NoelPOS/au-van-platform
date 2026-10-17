import { ChevronRight } from "lucide-react";
import { useNow } from "../hooks/useNow";
import { formatShortDate, formatTime } from "../utils/days";
import { formatBaht } from "../utils/format";
import { nextOpen, type RouteDepartures } from "../utils/tripRoutes";
import { RouteRule } from "./ui/RouteLine";

function nextPhrase(route: RouteDepartures, now: number): string {
  const next = nextOpen(route, now);
  if (!next) return "No open seats right now";
  return `Next ${formatShortDate(next.departureAt)}, ${formatTime(next.departureAt)}`;
}

export function RoutePicker({
  routes,
  onSelect,
}: {
  routes: RouteDepartures[];
  onSelect: (route: RouteDepartures) => void;
}) {
  const now = useNow();

  return (
    <section
      aria-label="Routes"
      className="overflow-hidden rounded-2xl border border-line bg-card"
    >
      <ol className="divide-y divide-dashed divide-line">
        {routes.map((route) => (
          <li key={route.routeId}>
            <button
              className="flex w-full items-center gap-4 px-4 py-4 text-left transition-colors duration-150 ease-out hover:bg-paper/70 active:bg-paper"
              onClick={() => onSelect(route)}
              type="button"
            >
              <span className="min-w-0 flex-1">
                <span className="block font-display text-[22px] leading-tight text-brand-900">
                  <span className="sr-only">To </span>
                  {route.destination}
                </span>
                <span className="mt-1 flex min-w-0 items-center gap-1.5 text-[13px] text-muted">
                  <RouteRule className="w-3" />
                  <span className="truncate">{`from ${route.origin}`}</span>
                </span>
                <span className="mt-1.5 block text-[13px] text-ink">
                  {nextPhrase(route, now)}
                </span>
              </span>
              <span className="flex shrink-0 items-center gap-1.5">
                <span className="text-right leading-tight">
                  <span className="block font-mono text-lg font-medium text-ink tabular-nums">
                    {formatBaht(route.fare)}
                  </span>
                  <span className="block text-[11px] text-muted">
                    {`${route.durationMinutes} min`}
                  </span>
                </span>
                <ChevronRight aria-hidden className="size-4 text-muted" />
              </span>
            </button>
          </li>
        ))}
      </ol>
    </section>
  );
}
