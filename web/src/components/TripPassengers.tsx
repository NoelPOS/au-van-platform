import { useTripOperations } from "../hooks/useOperationsQueries";
import type { AuthSession } from "../types/auth";
import { SeatOccupancy } from "./SeatOccupancy";
import { WaitlistQueue } from "./WaitlistQueue";
import { FailurePanel } from "./ui/FailurePanel";
import { Skeleton } from "./ui/Skeleton";
import { StatusBadge } from "./ui/StatusBadge";

const eyebrow = "mb-3 text-[11px] font-semibold tracking-[0.14em] text-muted uppercase";
const ruled = "border-t border-dashed border-line pt-6";

export function TripPassengers({
  session,
  tripId,
}: {
  session: AuthSession;
  tripId: string;
}) {
  const operations = useTripOperations(session, tripId);

  if (operations.error)
    return <FailurePanel title="Could not load this trip" error={operations.error} />;

  if (!operations.data)
    return (
      <div className="grid gap-4" role="status">
        <span className="sr-only">Loading passengers…</span>
        <Skeleton className="h-72" />
        <Skeleton className="h-32" />
      </div>
    );

  const trip = operations.data;
  return (
    <div className="grid gap-6">
      <section aria-labelledby="occupancy-heading">
        <h3 className={eyebrow} id="occupancy-heading">
          Seats
        </h3>
        <SeatOccupancy
          claimed={trip.claimedSeats}
          session={session}
          total={trip.totalSeats}
          tripId={trip.tripId}
        />
      </section>
      <section aria-labelledby="bookings-heading" className={ruled}>
        <h3 className={eyebrow} id="bookings-heading">
          Bookings by status
        </h3>
        <dl className="divide-y divide-line">
          {trip.bookingsByStatus.map((row) => (
            <div className="flex items-center justify-between py-2" key={row.status}>
              <dt>
                <StatusBadge value={row.status} />
              </dt>
              <dd
                className={`font-display text-xl font-light tabular-nums ${row.count === 0 ? "text-muted" : "text-brand-900"}`}
              >
                {row.count}
              </dd>
            </div>
          ))}
        </dl>
      </section>
      <section aria-labelledby="waitlist-heading" className={ruled}>
        <h3 className={eyebrow} id="waitlist-heading">
          Waitlist
        </h3>
        <WaitlistQueue entries={trip.waitlist} />
      </section>
    </div>
  );
}
