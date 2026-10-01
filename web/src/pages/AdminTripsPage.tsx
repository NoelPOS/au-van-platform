import { Plus } from "lucide-react";
import { useState } from "react";
import { InventoryPage } from "../components/InventoryPage";
import { TripForm } from "../components/TripForm";
import { Button } from "../components/ui/Button";
import { Drawer } from "../components/ui/Drawer";
import { EmptyState } from "../components/ui/EmptyState";
import { RouteLine } from "../components/ui/RouteLine";
import { StatusBadge } from "../components/ui/StatusBadge";
import { Table, type Column } from "../components/ui/Table";
import { useRoutes, useTrips, useVehicles } from "../hooks/useInventoryQueries";
import type { AuthSession } from "../types/auth";
import type { Trip } from "../types/inventory";
import { bangkokDateTime, bangkokDay, bangkokTime } from "../utils/dates";

export function AdminTripsPage({ session }: { session: AuthSession }) {
  const trips = useTrips(session);
  const routes = useRoutes(session);
  const vans = useVehicles(session);
  const [editing, setEditing] = useState<Trip | "new" | null>(null);
  const routeList = routes.data ?? [];
  const vanList = vans.data ?? [];
  const close = () => setEditing(null);

  const columns: Column<Trip>[] = [
    {
      label: "Departure",
      render: (trip) => (
        <span className="inline-flex items-baseline gap-2 whitespace-nowrap">
          <span className="text-muted">{bangkokDay(trip.departureAt)}</span>
          <span aria-hidden className="text-line">·</span>
          <span className="font-display text-lg text-brand-900">
            {bangkokTime(trip.departureAt)}
          </span>
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
      queries={[trips, routes, vans]}
      title="Trips"
    >
      <Table
        columns={columns}
        empty={
          <EmptyState
            detail="Schedule a departure once a route and a van are ready."
            title="No trips yet"
          />
        }
        label="Trips"
        rowKey={(trip) => trip.id}
        rows={trips.data ?? []}
      />
      <Drawer
        onClose={close}
        open={editing !== null}
        title={editing === "new" ? "Schedule a trip" : "Edit trip"}
      >
        <TripForm
          onDone={close}
          routes={routeList}
          session={session}
          trip={editing === "new" ? null : editing}
          vans={vanList}
        />
      </Drawer>
    </InventoryPage>
  );
}
