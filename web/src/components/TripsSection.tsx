import { useState, type FormEvent } from "react";
import { Button } from "./ui/Button";
import { StatusBadge } from "./ui/StatusBadge";
import type { AuthSession } from "../services/authService";
import { EmptyRow, InventoryTable } from "./InventoryTable";
import { ErrorMessage, FormActions, FormCard } from "./FormCard";
import { useCreateTrip, useUpdateTrip } from "../hooks/useInventoryQueries";
import type { Trip, VanRoute, Vehicle } from "../types/inventory";

const fieldClass =
  "mt-1 w-full rounded-lg border border-line bg-white px-3 py-2 font-normal text-ink outline-none focus:border-brand";

function toLocalDateTime(value: string): string {
  const date = new Date(value);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
    .toISOString()
    .slice(0, 16);
}

function formatTripDate(value: string): string {
  return new Intl.DateTimeFormat("en-GB", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

export function TripsSection({
  session,
  trips,
  routes,
  vehicles,
}: {
  session: AuthSession;
  trips: Trip[];
  routes: VanRoute[];
  vehicles: Vehicle[];
}) {
  const [editing, setEditing] = useState<Trip | null>(null);
  const createTrip = useCreateTrip(session);
  const updateTrip = useUpdateTrip(session);
  const trip = editing ?? {
    routeId: routes[0]?.id ?? "",
    vehicleId: vehicles[0]?.id ?? "",
    departureAt: "",
    status: "ACTIVE" as const,
  };
  const ready = routes.length > 0 && vehicles.length > 0;
  const busy = createTrip.isPending || updateTrip.isPending;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const departureAt = new Date(String(form.get("departureAt"))).toISOString();
    if (editing)
      await updateTrip.mutateAsync({
        id: editing.id,
        input: {
          departureAt,
          status: String(form.get("status")) as Trip["status"],
        },
      });
    else
      await createTrip.mutateAsync({
        routeId: String(form.get("routeId")),
        vehicleId: String(form.get("vehicleId")),
        departureAt,
      });
    setEditing(null);
  }

  return (
    <section className="grid items-start gap-5 lg:grid-cols-[minmax(18rem,.8fr)_minmax(0,1.6fr)]">
      <FormCard title={editing ? "Edit trip" : "Schedule trip"}>
        <form
          key={editing?.id ?? "new"}
          onSubmit={(event) => void submit(event)}
          className="grid grid-cols-2 gap-3"
        >
          {!editing && (
            <>
              <label className="text-sm font-semibold text-ink">
                Route
                <select
                  className={fieldClass}
                  required
                  name="routeId"
                  defaultValue={trip.routeId}
                  disabled={!ready}
                >
                  {routes.map((route) => (
                    <option key={route.id} value={route.id}>
                      {route.origin} → {route.destination}
                    </option>
                  ))}
                </select>
              </label>
              <label className="text-sm font-semibold text-ink">
                Vehicle
                <select
                  className={fieldClass}
                  required
                  name="vehicleId"
                  defaultValue={trip.vehicleId}
                  disabled={!ready}
                >
                  {vehicles.map((vehicle) => (
                    <option key={vehicle.id} value={vehicle.id}>
                      {vehicle.code} — {vehicle.name}
                    </option>
                  ))}
                </select>
              </label>
            </>
          )}
          <label className="text-sm font-semibold text-ink">
            Departure
            <input
              className={fieldClass}
              required
              name="departureAt"
              type="datetime-local"
              defaultValue={
                trip.departureAt ? toLocalDateTime(trip.departureAt) : ""
              }
            />
          </label>
          {editing && (
            <label className="text-sm font-semibold text-ink">
              Status
              <select
                className={fieldClass}
                name="status"
                defaultValue={trip.status}
              >
                <option>ACTIVE</option>
                <option>CANCELLED</option>
              </select>
            </label>
          )}
          {!ready && !editing && (
            <p className="col-span-full text-sm text-muted">
              Create at least one route and vehicle first.
            </p>
          )}
          <ErrorMessage error={createTrip.error ?? updateTrip.error} />
          <FormActions
            busy={busy}
            submitLabel={editing ? "Save trip" : "Schedule trip"}
            onCancel={editing ? () => setEditing(null) : undefined}
          />
        </form>
      </FormCard>
      <InventoryTable
        headings={["Departure", "Route", "Vehicle", "Seats", "Status", ""]}
      >
        {trips.length === 0 ? (
          <EmptyRow
            columns={6}
            title="No trips yet"
            detail="Schedule a departure when routes and vehicles are ready."
          />
        ) : (
          trips.map((item) => {
            const route = routes.find((value) => value.id === item.routeId);
            const vehicle = vehicles.find(
              (value) => value.id === item.vehicleId,
            );
            return (
              <tr className="border-t border-line" key={item.id}>
                <td className="px-4 py-3 font-semibold text-ink">
                  {formatTripDate(item.departureAt)}
                </td>
                <td className="px-4 py-3">
                  {route
                    ? `${route.origin} → ${route.destination}`
                    : "Unknown route"}
                </td>
                <td className="px-4 py-3">
                  {vehicle?.code ?? "Unknown vehicle"}
                </td>
                <td className="px-4 py-3">{item.seats.length}</td>
                <td className="px-4 py-3">
                  <StatusBadge value={item.status} />
                </td>
                <td className="px-4 py-3 text-right">
                  <Button variant="text" onClick={() => setEditing(item)}>
                    Edit
                  </Button>
                </td>
              </tr>
            );
          })
        )}
      </InventoryTable>
    </section>
  );
}
