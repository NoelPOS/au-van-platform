import { useState, type FormEvent } from "react";
import { useCreateTrip, useUpdateTrip } from "../hooks/useInventoryQueries";
import { useToast } from "../hooks/useToast";
import type { AuthSession } from "../types/auth";
import type { Trip, TripStatus, VanRoute, Vehicle } from "../types/inventory";
import { fromBangkokInputs, toBangkokInputs } from "../utils/dates";
import { ErrorMessage, FormActions } from "./FormCard";
import { EmptyState } from "./ui/EmptyState";
import { Input } from "./ui/Input";
import { Select } from "./ui/Select";
import { TextLink } from "./ui/TextLink";

export function TripForm({
  session,
  trip,
  routes,
  vans,
  onDone,
}: {
  session: AuthSession;
  trip: Trip | null;
  routes: VanRoute[];
  vans: Vehicle[];
  onDone: () => void;
}) {
  const createTrip = useCreateTrip(session);
  const updateTrip = useUpdateTrip(session);
  const notify = useToast();
  const [status, setStatus] = useState<TripStatus>(trip?.status ?? "ACTIVE");
  const departure = trip ? toBangkokInputs(trip.departureAt) : { date: "", time: "" };
  const cancelling = trip?.status === "ACTIVE" && status === "CANCELLED";

  if (!trip && (routes.length === 0 || vans.length === 0)) {
    return (
      <EmptyState
        action={
          <TextLink to={routes.length ? "/admin/vans" : "/admin/routes"}>
            {routes.length ? "Add a van" : "Create a route"}
          </TextLink>
        }
        detail="A trip needs a route to run and a van to run it."
        title="Not quite ready to schedule"
      />
    );
  }

  function saved(message: string) {
    return () => {
      notify(message);
      onDone();
    };
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const departureAt = fromBangkokInputs(
      String(form.get("date")),
      String(form.get("time")),
    );
    if (trip)
      updateTrip.mutate(
        { id: trip.id, input: { departureAt, status } },
        { onSuccess: saved("Trip updated") },
      );
    else
      createTrip.mutate(
        {
          routeId: String(form.get("routeId")),
          vehicleId: String(form.get("vehicleId")),
          departureAt,
        },
        { onSuccess: saved("Trip scheduled") },
      );
  }

  return (
    <form className="grid gap-5" onSubmit={submit}>
      {!trip && (
        <>
          <Select label="Route" name="routeId" required>
            {routes.map((route) => (
              <option key={route.id} value={route.id}>
                {route.origin} → {route.destination}
              </option>
            ))}
          </Select>
          <Select label="Van" name="vehicleId" required>
            {vans.map((van) => (
              <option key={van.id} value={van.id}>
                {van.code} — {van.name}
              </option>
            ))}
          </Select>
        </>
      )}
      <fieldset className="grid grid-cols-[1.4fr_1fr] gap-4">
        <legend className="mb-3 text-[11px] font-semibold tracking-[0.16em] text-muted uppercase">
          Departure · Bangkok time
        </legend>
        <Input
          defaultValue={departure.date}
          label="Date"
          name="date"
          required
          type="date"
        />
        <Input
          defaultValue={departure.time}
          label="Time"
          name="time"
          required
          type="time"
        />
      </fieldset>
      {trip && (
        <Select
          label="Status"
          onChange={(event) => setStatus(event.target.value as TripStatus)}
          value={status}
        >
          <option value="ACTIVE">Active</option>
          <option value="CANCELLED">Cancelled</option>
        </Select>
      )}
      <ErrorMessage error={createTrip.error ?? updateTrip.error} />
      <FormActions
        busy={createTrip.isPending || updateTrip.isPending}
        confirm={
          cancelling
            ? "Mark this trip as cancelled? You can set it back to active later."
            : undefined
        }
        onCancel={onDone}
        submitLabel={trip ? "Save trip" : "Schedule trip"}
      />
    </form>
  );
}
