import { ChevronRight } from "lucide-react";
import { Button } from "./ui/Button";
import { RouteRule } from "./ui/RouteLine";
import type { AvailableTrip, WaitlistEntry } from "../types/booking";
import { formatTime } from "../utils/days";
import { formatBaht } from "../utils/format";

const columns = "grid grid-cols-[4.25rem_minmax(0,1fr)_auto] items-center gap-3";

function Route({ trip }: { trip: AvailableTrip }) {
  return (
    <span className="min-w-0">
      <span className="block truncate font-medium text-ink">
        <span className="sr-only">To </span>
        {trip.destination}
      </span>
      <span className="mt-0.5 flex min-w-0 items-center gap-1.5 text-[13px] text-muted">
        <RouteRule className="w-3" />
        <span className="truncate">{`from ${trip.origin}`}</span>
      </span>
      <span className="mt-0.5 block font-mono text-[12px] text-muted">
        {`${trip.durationMinutes} min · ${formatBaht(trip.fare)}`}
      </span>
    </span>
  );
}

function SeatsLeft({ count }: { count: number }) {
  return (
    <span className="flex items-center gap-1.5">
      <span className="text-right leading-tight">
        <span
          className={`block font-mono text-lg font-medium tabular-nums ${count <= 3 ? "text-warning" : "text-ink"}`}
        >
          {count}
        </span>
        <span className="block text-[11px] text-muted">left</span>
      </span>
      <ChevronRight aria-hidden className="size-4 text-muted" />
    </span>
  );
}

function FullRow({
  trip,
  entry,
  pending,
  onJoin,
  onLeave,
}: {
  trip: AvailableTrip;
  entry: WaitlistEntry | undefined;
  pending: boolean;
  onJoin: () => void;
  onLeave: () => void;
}) {
  return (
    <div className="px-4 py-4">
      <div className={columns}>
        <span className="font-mono text-[22px] font-medium tracking-tight text-muted tabular-nums">
          {formatTime(trip.departureAt)}
        </span>
        <Route trip={trip} />
        <span className="rounded-md border border-line px-2 py-0.5 font-mono text-[11px] tracking-[0.14em] text-muted uppercase">
          Full
        </span>
      </div>
      <div className="mt-3 flex items-center justify-between gap-3 rounded-xl bg-paper px-3 py-2.5">
        <p className="text-[13px] text-muted">
          {entry
            ? `You are number ${entry.position} on the waitlist.`
            : "We message you on LINE if a seat opens up."}
        </p>
        <Button
          className="shrink-0"
          disabled={pending}
          onClick={entry ? onLeave : onJoin}
          variant="secondary"
        >
          {entry ? "Leave waitlist" : "Join waitlist"}
        </Button>
      </div>
    </div>
  );
}

export function DepartureBoard({
  trips,
  waitlist,
  waitlistPending,
  onSelect,
  onJoinWaitlist,
  onLeaveWaitlist,
}: {
  trips: AvailableTrip[];
  waitlist: WaitlistEntry[];
  waitlistPending: boolean;
  onSelect: (trip: AvailableTrip) => void;
  onJoinWaitlist: (trip: AvailableTrip) => void;
  onLeaveWaitlist: (entry: WaitlistEntry) => void;
}) {
  return (
    <section
      aria-label="Departures"
      className="overflow-hidden rounded-2xl border border-line bg-card"
    >
      <div
        aria-hidden
        className={`${columns} border-b border-line px-4 py-2.5 font-mono text-[10px] tracking-[0.16em] text-muted uppercase`}
      >
        <span>Time</span>
        <span>Destination</span>
        <span>Seats</span>
      </div>
      <ol className="divide-y divide-dashed divide-line">
        {trips.map((trip) => (
          <li key={trip.id}>
            {trip.availableSeats > 0 ? (
              <button
                aria-label={`${formatTime(trip.departureAt)}, ${trip.origin} to ${trip.destination}, ${trip.availableSeats} of ${trip.totalSeats} seats left`}
                className={`${columns} w-full px-4 py-4 text-left transition-colors duration-150 ease-out hover:bg-paper/70 active:bg-paper`}
                onClick={() => onSelect(trip)}
                type="button"
              >
                <span className="font-mono text-[22px] font-medium tracking-tight text-ink tabular-nums">
                  {formatTime(trip.departureAt)}
                </span>
                <Route trip={trip} />
                <SeatsLeft count={trip.availableSeats} />
              </button>
            ) : (
              <FullRow
                entry={waitlist.find((queued) => queued.tripId === trip.id)}
                onJoin={() => onJoinWaitlist(trip)}
                onLeave={() => {
                  const entry = waitlist.find((queued) => queued.tripId === trip.id);
                  if (entry) onLeaveWaitlist(entry);
                }}
                pending={waitlistPending}
                trip={trip}
              />
            )}
          </li>
        ))}
      </ol>
    </section>
  );
}
