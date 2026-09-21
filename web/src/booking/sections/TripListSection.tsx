import { Button } from "../../components/ui/Button";
import { Panel } from "../../components/ui/Panel";
import { formatDeparture, formatFare } from "../format";
import type { AvailableTrip } from "../types";

export function TripListSection({
  trips,
  loading,
  error,
  onRetry,
  onSelect,
}: {
  trips: AvailableTrip[];
  loading: boolean;
  error: Error | null;
  onRetry: () => void;
  onSelect: (trip: AvailableTrip) => void;
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
        {trips.map((trip) => (
          <li key={trip.id}>
            <button
              className="w-full rounded-xl border border-line bg-white px-4 py-3 text-left transition-colors hover:border-brand disabled:cursor-not-allowed disabled:opacity-60"
              disabled={trip.availableSeats === 0}
              onClick={() => onSelect(trip)}
              type="button"
            >
              <span className="block font-semibold text-ink">
                {trip.origin} → {trip.destination}
              </span>
              <span className="mt-1 block text-sm text-muted">
                {formatDeparture(trip.departureAt)} · {formatFare(trip.fare)} ·{" "}
                {trip.availableSeats === 0
                  ? "full"
                  : `${trip.availableSeats} of ${trip.totalSeats} seats free`}
              </span>
            </button>
          </li>
        ))}
      </ul>
    </Panel>
  );
}
