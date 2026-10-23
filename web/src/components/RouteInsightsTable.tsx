import type { RouteInsight } from "../types/insights";
import { formatBaht, formatPercent } from "../utils/format";
import { EmptyState } from "./ui/EmptyState";
import { RouteLine } from "./ui/RouteLine";
import { Table, type Column } from "./ui/Table";

const columns: Column<RouteInsight>[] = [
  {
    label: "Route",
    render: (route) => (
      <RouteLine destination={route.destination} origin={route.origin} />
    ),
  },
  { label: "Trips", render: (route) => route.trips },
  {
    label: "Seats sold",
    render: (route) => `${route.seatsSold} of ${route.seatsOffered}`,
  },
  {
    label: "Occupancy",
    render: (route) => (
      <span className="flex items-center gap-2.5">
        <span aria-hidden className="h-1.5 w-16 overflow-hidden rounded-full bg-line">
          <span
            className="block h-full rounded-full bg-brand-500"
            style={{ width: formatPercent(route.occupancy) }}
          />
        </span>
        {formatPercent(route.occupancy)}
      </span>
    ),
  },
  {
    label: "Revenue",
    align: "end",
    render: (route) => (
      <span className="font-display text-lg text-brand-900">
        {formatBaht(route.revenue)}
      </span>
    ),
  },
];

export function RouteInsightsTable({ routes }: { routes: RouteInsight[] }) {
  return (
    <section aria-labelledby="route-insights-heading">
      <div className="mb-5">
        <p className="text-[11px] font-semibold tracking-[0.14em] text-brand-500 uppercase">
          Demand
        </p>
        <h2
          className="mt-1 font-display text-2xl font-light text-brand-900"
          id="route-insights-heading"
        >
          By route
        </h2>
        <p className="mt-1 text-sm text-muted">
          Highest revenue first. Cancelled trips are left out.
        </p>
      </div>
      <Table
        columns={columns}
        empty={<EmptyState title="No routes ran in this period" />}
        label="Routes in this period"
        rowKey={(route) => route.routeId}
        rows={routes}
      />
    </section>
  );
}
