import { useState, type FormEvent } from "react";
import { useUpdateTrip } from "../hooks/useInventoryQueries";
import { useScheduleTrips } from "../hooks/useScheduleTrips";
import { useToast } from "../hooks/useToast";
import type { AuthSession } from "../types/auth";
import type {
  SeatLayout,
  Trip,
  TripStatus,
  VanRoute,
  Vehicle,
} from "../types/inventory";
import { fromBangkokInputs, parseClock, toBangkokInputs } from "../utils/dates";
import {
  departureTimes,
  routeOption,
  seatsOf,
  tripSummary,
  vanOption,
} from "../utils/tripDetails";
import { DayPicker } from "./DayPicker";
import { ErrorMessage, FormActions } from "./FormCard";
import { ScheduleOutcome } from "./ScheduleOutcome";
import { TimeField } from "./TimeField";
import { EmptyState } from "./ui/EmptyState";
import { SegmentedControl } from "./ui/SegmentedControl";
import { Select } from "./ui/Select";
import { TextLink } from "./ui/TextLink";
import { statusLabel } from "../utils/statusLabels";

const statuses = (["ACTIVE", "CANCELLED"] satisfies TripStatus[]).map(
  (value) => ({ value, label: statusLabel(value) }),
);

export function TripForm({
  session,
  trip,
  routes,
  vans,
  layouts,
  trips,
  onDone,
}: {
  session: AuthSession;
  trip: Trip | null;
  routes: VanRoute[];
  vans: Vehicle[];
  layouts: SeatLayout[];
  trips: Trip[];
  onDone: () => void;
}) {
  const updateTrip = useUpdateTrip(session);
  const { schedule, saving, report } = useScheduleTrips(session);
  const notify = useToast();
  const initial = trip ? toBangkokInputs(trip.departureAt) : null;
  const [routeId, setRouteId] = useState(trip?.routeId ?? routes[0]?.id ?? "");
  const [vehicleId, setVehicleId] = useState(trip?.vehicleId ?? vans[0]?.id ?? "");
  const [days, setDays] = useState(initial ? [initial.date] : []);
  const [time, setTime] = useState(initial?.time ?? "");
  const [status, setStatus] = useState<TripStatus>(trip?.status ?? "ACTIVE");
  const [submitted, setSubmitted] = useState(false);

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

  const clock = parseClock(time);
  const route = routes.find((entry) => entry.id === routeId);
  const summary = tripSummary({
    clock,
    durationMinutes: trip?.durationMinutes ?? route?.durationMinutes,
    fare: trip?.fare ?? route?.fare,
    seats: trip
      ? trip.seats.length
      : seatsOf(
          vans.find((van) => van.id === vehicleId),
          layouts,
        ),
  });

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitted(true);
    if (!clock || days.length === 0) return;
    if (trip) {
      const departureAt = fromBangkokInputs(days[0], clock);
      updateTrip.mutate(
        { id: trip.id, input: { departureAt, status } },
        {
          onSuccess: () => {
            notify("Trip updated");
            onDone();
          },
        },
      );
      return;
    }
    const result = await schedule(days, clock, { routeId, vehicleId });
    setDays((current) =>
      current.filter((day) => !result.scheduled.includes(day)),
    );
    if (result.failed.length > 0) return;
    const count = result.scheduled.length;
    notify(count === 1 ? "Trip scheduled" : `${count} trips scheduled`);
    onDone();
  }

  return (
    <form className="grid gap-6" noValidate onSubmit={submit}>
      {!trip && (
        <>
          <Select
            label="Route"
            name="routeId"
            onChange={setRouteId}
            options={routes.map(routeOption)}
            value={routeId}
          />
          <Select
            label="Van"
            name="vehicleId"
            onChange={setVehicleId}
            options={vans.map((van) => vanOption(van, layouts))}
            value={vehicleId}
          />
        </>
      )}
      <fieldset className="grid gap-5">
        <legend className="mb-4 text-[11px] font-semibold tracking-[0.16em] text-muted uppercase">
          Departure · Bangkok time
        </legend>
        <DayPicker
          error={
            submitted && days.length === 0
              ? "Choose at least one day."
              : undefined
          }
          multiple={!trip}
          onChange={setDays}
          selected={days}
        />
        <TimeField
          error={
            submitted && !clock
              ? "Enter a 24-hour time, like 07:30."
              : undefined
          }
          onChange={setTime}
          suggestions={departureTimes(trips, routeId, trip?.id)}
          value={time}
        />
      </fieldset>
      {trip && (
        <SegmentedControl
          label="Status"
          onChange={setStatus}
          options={statuses}
          value={status}
        />
      )}
      <p
        aria-live="polite"
        className="rounded-xl bg-paper px-4 py-3 font-mono text-[13px] text-brand-900 tabular-nums"
      >
        {summary}
      </p>
      <ScheduleOutcome report={report} />
      <ErrorMessage error={updateTrip.error} />
      <FormActions
        busy={saving || updateTrip.isPending}
        confirm={
          trip?.status === "ACTIVE" && status === "CANCELLED"
            ? "Mark this trip as cancelled? You can set it back to active later."
            : undefined
        }
        onCancel={onDone}
        submitLabel={
          trip
            ? "Save trip"
            : days.length > 1
              ? `Schedule ${days.length} trips`
              : "Schedule trip"
        }
      />
    </form>
  );
}
