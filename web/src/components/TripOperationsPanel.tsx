import type { TripOperations } from "../types/operations";
import { formatWhen } from "../utils/format";
import { OccupancyGrid } from "./OccupancyGrid";
import { WaitlistQueue } from "./WaitlistQueue";
import { StatusBadge } from "./ui/StatusBadge";

const eyebrow = "mb-3 text-xs font-semibold tracking-[0.14em] text-muted uppercase";

export function TripOperationsPanel({ trip }: { trip: TripOperations }) {
  return (
    <article className="rounded-2xl border border-line bg-card">
      <header className="p-5 sm:p-6">
        <p className="text-xs font-semibold tracking-[0.14em] text-brand-500 uppercase">
          {formatWhen(trip.departureAt)}
        </p>
        <div className="mt-2 flex flex-wrap items-center justify-between gap-3">
          <h2 className="font-serif text-2xl text-brand-900 sm:text-3xl">
            {`${trip.origin} → ${trip.destination}`}
          </h2>
          <StatusBadge value={trip.tripStatus} />
        </div>
        <div aria-hidden="true" className="mt-4 flex items-center gap-2">
          <span className="size-2.5 rounded-full bg-brand-600" />
          <span className="flex-1 border-t-2 border-dashed border-line" />
          <span className="size-2.5 rounded-full border-2 border-brand-600" />
        </div>
      </header>
      <div className="grid gap-8 border-t border-dashed border-line p-5 sm:p-6 md:grid-cols-2">
        <section aria-labelledby="occupancy-heading">
          <h3 className={eyebrow} id="occupancy-heading">
            Seats
          </h3>
          <OccupancyGrid claimed={trip.claimedSeats} total={trip.totalSeats} />
        </section>
        <section aria-labelledby="bookings-heading">
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
                  className={`font-serif text-xl tabular-nums ${row.count === 0 ? "text-muted" : "text-brand-900"}`}
                >
                  {row.count}
                </dd>
              </div>
            ))}
          </dl>
        </section>
      </div>
      <section
        aria-labelledby="waitlist-heading"
        className="border-t border-dashed border-line p-5 sm:p-6"
      >
        <h3 className={eyebrow} id="waitlist-heading">
          Waitlist
        </h3>
        <WaitlistQueue entries={trip.waitlist} />
      </section>
    </article>
  );
}
