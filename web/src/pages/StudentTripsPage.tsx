import { useNavigate, useSearchParams } from "react-router";
import { CooldownBanner } from "../components/CooldownBanner";
import { DayStrip } from "../components/DayStrip";
import { DepartureBoard } from "../components/DepartureBoard";
import { UnpaidBookingBanner } from "../components/UnpaidBookingBanner";
import { Button } from "../components/ui/Button";
import { EmptyState } from "../components/ui/EmptyState";
import { FailurePanel } from "../components/ui/FailurePanel";
import { Skeleton } from "../components/ui/Skeleton";
import {
  useAvailableTrips,
  useBookingEligibility,
  useMyBookings,
} from "../hooks/useBookingQueries";
import { useStudent } from "../hooks/useStudent";
import { useWaitlist } from "../hooks/useWaitlist";
import { dayOf, formatLongDate, groupByDay } from "../utils/days";

export function StudentTripsPage() {
  const { session, setNotice } = useStudent();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const trips = useAvailableTrips(session);
  const bookings = useMyBookings(session);
  const eligibility = useBookingEligibility(session);
  const waitlist = useWaitlist(session, setNotice);

  const byDay = groupByDay(trips.data ?? [], (trip) => trip.departureAt);
  const keys = [...byDay.keys()];
  const requested = params.get("day");
  const selected = requested && byDay.has(requested) ? requested : keys[0];
  const departures = selected ? (byDay.get(selected) ?? []) : [];

  return (
    <>
      <header>
        <p className="font-mono text-[11px] tracking-[0.18em] text-muted uppercase">
          Departures
        </p>
        <h1 className="mt-1.5 font-display text-[34px] leading-[1.05] tracking-tight text-brand-900">
          Catch the next van
        </h1>
        {selected && (
          <p className="mt-1.5 font-display text-lg text-muted italic">
            {formatLongDate(departures[0].departureAt)}
          </p>
        )}
      </header>

      <UnpaidBookingBanner bookings={bookings.data ?? []} />
      <CooldownBanner eligibility={eligibility.data} />

      {trips.isPending && (
        <div className="flex flex-col gap-3" role="status">
          <span className="sr-only">Loading departures…</span>
          <Skeleton className="h-24" />
          <Skeleton className="h-64" />
        </div>
      )}
      {trips.error && (
        <FailurePanel error={trips.error} title="Could not load departures">
          <Button onClick={() => void trips.refetch()} variant="secondary">
            Try again
          </Button>
        </FailurePanel>
      )}
      {trips.data && keys.length === 0 && (
        <EmptyState
          detail="No vans are scheduled right now. New departures appear here as soon as staff add them."
          title="Nothing scheduled yet"
        />
      )}
      {selected && (
        <>
          <DayStrip
            counts={new Map(keys.map((key) => [key, byDay.get(key)?.length ?? 0]))}
            days={keys.map((key) => dayOf(key))}
            onSelect={(key) => setParams({ day: key }, { replace: true })}
            selected={selected}
          />
          <DepartureBoard
            onJoinWaitlist={(trip) => void waitlist.join(trip)}
            onLeaveWaitlist={(entry) => void waitlist.leave(entry)}
            onSelect={(trip) => navigate(`/trips/${trip.id}`)}
            trips={departures}
            waitlist={waitlist.entries.data ?? []}
            waitlistPending={waitlist.pending}
          />
        </>
      )}
    </>
  );
}
