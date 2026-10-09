import { useState } from "react";
import type { Trip, VanRoute, Vehicle } from "../types/inventory";
import { dayHeading, shortDay } from "../utils/calendar";
import { bangkokDateTime, bangkokTime } from "../utils/dates";
import { filterTrips, groupByDay, type TripFilter } from "../utils/tripFilters";
import { Button } from "./ui/Button";
import { EmptyState } from "./ui/EmptyState";
import { RouteLine } from "./ui/RouteLine";
import { SegmentedControl } from "./ui/SegmentedControl";
import { StatusBadge } from "./ui/StatusBadge";
import { Table, type Column } from "./ui/Table";

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

export function TripList({
  trips,
  routes,
  vans,
  today,
  onEdit,
}: {
  trips: Trip[];
  routes: VanRoute[];
  vans: Vehicle[];
  today: string;
  onEdit: (trip: Trip) => void;
}) {
  const [filter, setFilter] = useState<TripFilter>("upcoming");
  const [now] = useState(Date.now);
  const groups = groupByDay(filterTrips(trips, filter, now));

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
        const route = routes.find((entry) => entry.id === trip.routeId);
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
          {vans.find((entry) => entry.id === trip.vehicleId)?.code ??
            "Unknown van"}
        </span>
      ),
    },
    { label: "Seats", render: (trip) => trip.seats.length },
    {
      label: "Status",
      render: (trip) => (
        <span className="flex flex-col items-start gap-1">
          <StatusBadge value={trip.status} />
          {trip.cancellationReason && (
            <span className="max-w-56 text-[13px] text-danger italic">
              {trip.cancellationReason}
            </span>
          )}
        </span>
      ),
    },
    {
      label: "",
      align: "end",
      render: (trip) => (
        <Button
          aria-label={`${trip.status === "CANCELLED" ? "See cancelled" : "Edit"} trip on ${bangkokDateTime(trip.departureAt)}`}
          onClick={() => onEdit(trip)}
          variant="text"
        >
          {trip.status === "CANCELLED" ? "See" : "Edit"}
        </Button>
      ),
    },
  ];

  return (
    <div className="mt-6">
      {trips.length > 0 && (
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
          <EmptyState {...empty[trips.length ? filter : "none"]} />
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
    </div>
  );
}
