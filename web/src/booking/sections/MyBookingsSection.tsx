import { Panel } from "../../components/ui/Panel";
import { StatusBadge } from "../../components/ui/StatusBadge";
import { formatDeparture, formatFare } from "../format";
import type { Booking } from "../types";

export function MyBookingsSection({
  bookings,
  loading,
  error,
}: {
  bookings: Booking[];
  loading: boolean;
  error: Error | null;
}) {
  return (
    <Panel className="p-5">
      <h2 className="mb-4 text-lg font-bold text-ink">My bookings</h2>
      {loading && <p className="text-sm text-muted">Loading your bookings…</p>}
      {error && (
        <p className="text-sm text-red-700" role="alert">
          {error.message}
        </p>
      )}
      {!loading && !error && bookings.length === 0 && (
        <p className="text-sm text-muted">You have no bookings yet.</p>
      )}
      <ul className="flex flex-col gap-3">
        {bookings.map((booking) => (
          <li
            className="rounded-xl border border-line px-4 py-3"
            key={booking.id}
          >
            <div className="flex items-center justify-between gap-3">
              <span className="font-semibold text-ink">
                {booking.reference}
              </span>
              <StatusBadge value={booking.status} />
            </div>
            <p className="mt-1 text-sm text-muted">
              {`${booking.trip.origin} → ${booking.trip.destination} · ${formatDeparture(booking.trip.departureAt)}`}
            </p>
            <p className="mt-1 text-sm text-muted">
              {`Seat ${booking.seats.map((seat) => seat.label).join(", ")} · ${formatFare(booking.totalFare)}`}
            </p>
          </li>
        ))}
      </ul>
    </Panel>
  );
}
