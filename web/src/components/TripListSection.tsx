import { Button } from "./ui/Button";
import { Panel } from "./ui/Panel";
import { formatDeparture, formatFare } from "../utils/format";
import type { AvailableTrip, WaitlistEntry } from "../types/booking";

export function TripListSection({
  trips,
  waitlist,
  loading,
  error,
  waitlistPending,
  onRetry,
  onSelect,
  onJoinWaitlist,
  onLeaveWaitlist,
}: {
  trips: AvailableTrip[];
  waitlist: WaitlistEntry[];
  loading: boolean;
  error: Error | null;
  waitlistPending: boolean;
  onRetry: () => void;
  onSelect: (trip: AvailableTrip) => void;
  onJoinWaitlist: (trip: AvailableTrip) => void;
  onLeaveWaitlist: (entry: WaitlistEntry) => void;
}) {
  return (
    <Panel className="p-5">
      <h2 className="mb-4 text-lg font-bold text-ink">Upcoming trips</h2>
      {loading && <p className="text-sm text-muted">Loading trips…</p>}
      {error && (
        <div role="alert">
          <p className="text-sm text-red-700">{error.message}</p>
          <Button className="mt-3" onClick={onRetry}>
            Try again
          </Button>
        </div>
      )}
      {!loading && !error && trips.length === 0 && (
        <p className="text-sm text-muted">
          No trips are scheduled right now. Check back later.
        </p>
      )}
      <ul className="flex flex-col gap-3">
        {trips.map((trip) => {
          const summary = `${formatDeparture(trip.departureAt)} · ${formatFare(trip.fare)} · `;
          // The waitlist exists only for a trip nobody can book. A trip with a
          // seat free is a trip to book, and the API refuses a join on one.
          if (trip.availableSeats > 0) {
            return (
              <li key={trip.id}>
                <button
                  className="w-full rounded-xl border border-line bg-white px-4 py-3 text-left transition-colors hover:border-brand"
                  onClick={() => onSelect(trip)}
                  type="button"
                >
                  <span className="block font-semibold text-ink">
                    {trip.origin} → {trip.destination}
                  </span>
                  <span className="mt-1 block text-sm text-muted">
                    {summary}
                    {`${trip.availableSeats} of ${trip.totalSeats} seats free`}
                  </span>
                </button>
              </li>
            );
          }
          // A full trip is a block rather than a button: it carries the join
          // and leave controls, and a button inside a button is invalid.
          const entry = waitlist.find((queued) => queued.tripId === trip.id);
          return (
            <li key={trip.id}>
              <div className="w-full rounded-xl border border-line bg-white px-4 py-3">
                <span className="block font-semibold text-ink">
                  {trip.origin} → {trip.destination}
                </span>
                <span className="mt-1 block text-sm text-muted">
                  {summary}full
                </span>
                {entry ? (
                  <div className="mt-3 flex flex-wrap items-center justify-between gap-2">
                    <p className="text-sm text-brand">
                      You are number {entry.position} on the waitlist.
                    </p>
                    <Button
                      disabled={waitlistPending}
                      onClick={() => onLeaveWaitlist(entry)}
                      variant="secondary"
                    >
                      Leave waitlist
                    </Button>
                  </div>
                ) : (
                  <div className="mt-3 flex flex-wrap items-center justify-between gap-2">
                    <p className="text-sm text-muted">
                      Join the waitlist and we will message you if a seat opens
                      up.
                    </p>
                    <Button
                      disabled={waitlistPending}
                      onClick={() => onJoinWaitlist(trip)}
                    >
                      Join waitlist
                    </Button>
                  </div>
                )}
              </div>
            </li>
          );
        })}
      </ul>
    </Panel>
  );
}
