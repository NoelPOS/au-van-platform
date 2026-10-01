import { Plus } from "lucide-react";
import { useState } from "react";
import { InventoryPage } from "../components/InventoryPage";
import { TripForm } from "../components/TripForm";
import { Button } from "../components/ui/Button";
import { Drawer } from "../components/ui/Drawer";
import { EmptyState } from "../components/ui/EmptyState";
import { RouteLine } from "../components/ui/RouteLine";
import { SegmentedControl } from "../components/ui/SegmentedControl";
import { StatusBadge } from "../components/ui/StatusBadge";
import { Table, type Column } from "../components/ui/Table";
import {
  useRoutes,
  useSeatLayouts,
  useTrips,
  useVehicles,
} from "../hooks/useInventoryQueries";
import type { AuthSession } from "../types/auth";
import type { Trip } from "../types/inventory";
import { bangkokToday, dayHeading, shortDay } from "../utils/calendar";
import { bangkokDateTime, bangkokTime } from "../utils/dates";
import {
  filterTrips,
  groupByDay,
  type TripFilter,
} from "../utils/tripFilters";

const filters: { value: TripFilter; label: string }[] = [
  { value: "upcoming", label: "Upcoming" },
  { value: "past", label: "Past" },
  { value: "cancelled", label: "Cancelled" },
];

const empty: Record<TripFilter | "none", { title: string; detail: string }> = {
  none: {
    title: "No trips yet",
    detail: "Schedule a departure once a route and a van are ready.",
  },
  upcoming: {
    title: "Nothing on the board",
    detail: "No departures ahead. Schedule one, or a week of them at once.",
  },
  past: {
    title: "No departures have run yet",
    detail: "Trips move here once their departure time has passed.",
  },
  cancelled: {
    title: "No cancelled trips",
    detail: "Every scheduled departure is still running.",
  },
};

export function AdminTripsPage({ session }: { session: AuthSession }) {
  const trips = useTrips(session);
  const routes = useRoutes(session);
  const vans = useVehicles(session);
  const layouts = useSeatLayouts(session);
  const [filter, setFilter] = useState<TripFilter>("upcoming");
  const [now] = useState(Date.now);
  const [editing, setEditing] = useState<Trip | "new" | null>(null);
  const routeList = routes.data ?? [];
  const vanList = vans.data ?? [];
  const allTrips = trips.data ?? [];
  const groups = groupByDay(filterTrips(allTrips, filter, now));
  const today = bangkokToday();
  const close = () => setEditing(null);

  const columns: Column<Trip>[] = [
    {
      label: "Departs",
      render: (trip) => (
        <span className="font-display text-lg text-brand-900">
          {bangkokTime(trip.departureAt)}
        </span>
      ),
    },
    {
      label: "Route",
      render: (trip) => {
        const route = routeList.find((entry) => entry.id === trip.routeId);
        return route ? (
          <RouteLine destination={route.destination} origin={route.origin} />
        ) : (
          "Unknown route"
        );
      },
    },
    {
      label: "Van",
      render: (trip) => (
        <span className="font-mono text-[13px]">
          {vanList.find((entry) => entry.id === trip.vehicleId)?.code ??
            "Unknown van"}
        </span>
      ),
    },
    { label: "Seats", render: (trip) => trip.seats.length },
    { label: "Status", render: (trip) => <StatusBadge value={trip.status} /> },
    {
      label: "",
      align: "end",
      render: (trip) => (
        <Button
          aria-label={`Edit trip on ${bangkokDateTime(trip.departureAt)}`}
          onClick={() => setEditing(trip)}
          variant="text"
        >
          Edit
        </Button>
      ),
    },
  ];

  return (
    <InventoryPage
      action={
        <Button onClick={() => setEditing("new")}>
          <Plus aria-hidden className="size-4" />
          New trip
        </Button>
      }
      description="Every scheduled departure, in Bangkok time."
      queries={[trips, routes, vans, layouts]}
      title="Trips"
    >
      {allTrips.length > 0 && (
        <SegmentedControl
          hideLabel
          label="Show trips"
          onChange={setFilter}
          options={filters}
          value={filter}
        />
      )}
      {groups.length === 0 ? (
        <div className="mt-6 rounded-2xl border border-dashed border-line bg-card/60">
          <EmptyState {...empty[allTrips.length ? filter : "none"]} />
        </div>
      ) : (
        groups.map((group) => (
          <section className="mt-8" key={group.day}>
            <h2 className="mb-3 flex items-baseline justify-between gap-4 font-display text-xl text-brand-900">
              {dayHeading(group.day, today)}
              <span className="font-sans text-[13px] text-muted">
                {group.trips.length === 1
                  ? "1 departure"
                  : `${group.trips.length} departures`}
              </span>
            </h2>
            <Table
              columns={columns}
              empty={null}
              label={`Trips on ${shortDay(group.day)}`}
              rowKey={(trip) => trip.id}
              rows={group.trips}
            />
          </section>
        ))
      )}
      <Drawer
        onClose={close}
        open={editing !== null}
        title={editing === "new" ? "Schedule a trip" : "Edit trip"}
      >
        <TripForm
          layouts={layouts.data ?? []}
          onDone={close}
          routes={routeList}
          session={session}
          trip={editing === "new" ? null : editing}
          trips={allTrips}
          vans={vanList}
        />
      </Drawer>
    </InventoryPage>
  );
}
