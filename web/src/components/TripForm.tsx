import { useState, type FormEvent } from "react";
import { useUpdateTrip } from "../hooks/useInventoryQueries";
import { useNow } from "../hooks/useNow";
import { useScheduleTrips } from "../hooks/useScheduleTrips";
import { useToast } from "../hooks/useToast";
import type { AuthSession } from "../types/auth";
import type { SeatLayout, Trip, VanRoute, Vehicle } from "../types/inventory";
import { fromBangkokInputs, parseClock, toBangkokInputs } from "../utils/dates";
import { passedToday, pastClockError } from "../utils/schedule";
import {
  departureTimes,
  routeOption,
  seatsOf,
  tripSummary,
  vanOption,
} from "../utils/tripDetails";
import { DayPicker } from "./DayPicker";
import { ErrorMessage, FormActions } from "./FormCard";
import { NotReadyToSchedule } from "./NotReadyToSchedule";
import { ScheduleOutcome } from "./ScheduleOutcome";
import { TimeField } from "./TimeField";
import { TripImpact } from "./TripImpact";
import { Select } from "./ui/Select";

export function TripForm({
  session,
  trip,
  routes,
  vans,
  layouts,
  trips,
  startDay,
  onDone,
  onCancelTrip,
}: {
  session: AuthSession;
  trip: Trip | null;
  startDay?: string;
  routes: VanRoute[];
  vans: Vehicle[];
  layouts: SeatLayout[];
  trips: Trip[];
  onDone: () => void;
  onCancelTrip: (trip: Trip) => void;
}) {
  const updateTrip = useUpdateTrip(session);
  const { schedule, saving, report } = useScheduleTrips(session);
  const notify = useToast();
  const initial = trip ? toBangkokInputs(trip.departureAt) : null;
  const [routeId, setRouteId] = useState(trip?.routeId ?? routes[0]?.id ?? "");
  const [vehicleId, setVehicleId] = useState(trip?.vehicleId ?? vans[0]?.id ?? "");
  const [days, setDays] = useState(
    initial ? [initial.date] : startDay ? [startDay] : [],
  );
  const [time, setTime] = useState(initial?.time ?? "");
  const [submitted, setSubmitted] = useState(false);
  const now = useNow();

  if (!trip && (routes.length === 0 || vans.length === 0))
    return <NotReadyToSchedule hasRoutes={routes.length > 0} />;

  const clock = parseClock(time);
  const passedUntil = passedToday(days, now);
  const timeError =
    submitted && !clock
      ? "Enter a 24-hour time, like 07:30."
      : pastClockError(clock, passedUntil);
  const route = routes.find((entry) => entry.id === routeId);
  const moved =
    trip !== null &&
    clock !== null &&
    days.length === 1 &&
    fromBangkokInputs(days[0], clock) !== new Date(trip.departureAt).toISOString();
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
    if (pastClockError(clock, passedToday(days, Date.now()))) return;
    if (trip) {
      const departureAt = fromBangkokInputs(days[0], clock);
      updateTrip.mutate(
        { id: trip.id, departureAt },
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
          error={timeError}
          onChange={setTime}
          passedUntil={passedUntil}
          suggestions={departureTimes(trips, routeId, trip?.id)}
          value={time}
        />
      </fieldset>
      {moved && (
        <TripImpact change="move" session={session} tripId={trip.id} />
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
        onCancel={onDone}
        submitLabel={submitLabel(trip !== null, days.length)}
      />
      {trip && Date.parse(trip.departureAt) > now && (
        <button
          className="min-h-11 justify-self-start text-sm font-semibold text-danger underline decoration-1 underline-offset-4 hover:decoration-2"
          onClick={() => onCancelTrip(trip)}
          type="button"
        >
          Cancel this trip…
        </button>
      )}
    </form>
  );
}

function submitLabel(editing: boolean, dayCount: number): string {
  if (editing) return "Save trip";
  return dayCount > 1 ? `Schedule ${dayCount} trips` : "Schedule trip";
}
