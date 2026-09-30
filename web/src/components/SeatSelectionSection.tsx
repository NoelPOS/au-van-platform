import { Button } from "./ui/Button";
import { Panel } from "./ui/Panel";
import { SeatLegend, SeatMap } from "./SeatMap";
import { formatDeparture, formatFare } from "../utils/format";
import type { AvailableTrip, TripSeat } from "../types/booking";

function lostSeatMessage(labels: string[]): string {
  if (labels.length === 0) return "";
  if (labels.length === 1)
    return `Seat ${labels[0]} was taken by another student and has been removed from your selection.`;
  return `Seats ${labels.join(", ")} were taken by another student and have been removed from your selection.`;
}

export function SeatSelectionSection({
  trip,
  seats,
  selected,
  lostSeatLabels,
  maxSeats,
  loading,
  error,
  holding,
  onToggle,
  onHold,
  onBack,
}: {
  trip: AvailableTrip;
  seats: TripSeat[];
  selected: string[];
  lostSeatLabels: string[];
  maxSeats: number;
  loading: boolean;
  error: Error | null;
  holding: boolean;
  onToggle: (seatId: string) => void;
  onHold: () => void;
  onBack: () => void;
}) {
  return (
    <Panel className="p-5">
      <h2 className="text-lg font-bold text-ink">
        {trip.origin} → {trip.destination}
      </h2>
      <p className="mt-1 text-sm text-muted">
        {formatDeparture(trip.departureAt)} · {formatFare(trip.fare)} per seat ·
        up to {maxSeats} seats
      </p>
      {loading && <p className="mt-4 text-sm text-muted">Loading seats…</p>}
      {error && (
        <div className="mt-4" role="alert">
          <p className="text-sm text-red-700">{error.message}</p>
        </div>
      )}
      {/* Stays mounted: a status region inserted together with its first
          text is announced unreliably. */}
      <p
        className={lostSeatLabels.length > 0 ? "mt-4 text-sm text-red-700" : ""}
        role="status"
      >
        {lostSeatMessage(lostSeatLabels)}
      </p>
      {seats.length > 0 && (
        <div className="mt-4 flex flex-col gap-4">
          <SeatLegend />
          <SeatMap
            disabled={holding}
            onToggle={onToggle}
            seats={seats}
            selected={selected}
          />
          <p className="text-sm text-muted">
            {selected.length === 0
              ? "Choose at least one seat."
              : `${selected.length} seat${selected.length === 1 ? "" : "s"} selected · ${formatFare(trip.fare * selected.length)}`}
          </p>
        </div>
      )}
      <div className="mt-5 flex gap-2">
        <Button onClick={onBack} type="button" variant="secondary">
          Back to trips
        </Button>
        <Button
          disabled={selected.length === 0 || holding}
          onClick={onHold}
          type="button"
        >
          {holding ? "Holding seats…" : "Hold these seats"}
        </Button>
      </div>
    </Panel>
  );
}
