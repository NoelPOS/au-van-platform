import type { TripSeat } from "../types/booking";

const seatClasses: Record<string, string> = {
  selected: "border-brand bg-brand text-white",
  available: "border-line bg-white text-ink hover:border-brand",
  "held by you": "border-brand bg-brand-soft text-brand",
  "held by someone else": "border-line bg-stone-200 text-muted",
  booked: "border-line bg-stone-300 text-muted",
};

const stateText: Record<TripSeat["state"], string> = {
  AVAILABLE: "available",
  HELD: "held by someone else",
  HELD_BY_YOU: "held by you",
  BOOKED: "booked",
};

function seatState(seat: TripSeat, selected: boolean): string {
  return selected ? "selected" : stateText[seat.state];
}

function rowsOf(seats: TripSeat[]): TripSeat[][] {
  const rows = new Map<number, TripSeat[]>();
  for (const seat of [...seats].sort(
    (left, right) => left.columnNumber - right.columnNumber,
  )) {
    rows.set(seat.rowNumber, [...(rows.get(seat.rowNumber) ?? []), seat]);
  }
  return [...rows.entries()]
    .sort(([left], [right]) => left - right)
    .map(([, row]) => row);
}

export function SeatLegend() {
  return (
    <ul className="flex flex-wrap gap-3 text-xs text-muted">
      {["available", "selected", "held by you", "held by someone else", "booked"].map(
        (state) => (
          <li className="flex items-center gap-2" key={state}>
            <span
              aria-hidden="true"
              className={`h-3 w-3 rounded border ${seatClasses[state]}`}
            />
            {state}
          </li>
        ),
      )}
    </ul>
  );
}

export function SeatMap({
  seats,
  selected,
  disabled,
  onToggle,
}: {
  seats: TripSeat[];
  selected: string[];
  disabled: boolean;
  onToggle: (seatId: string) => void;
}) {
  return (
    <div
      aria-label="Seat map"
      className="flex flex-col items-center gap-2"
      role="group"
    >
      {rowsOf(seats).map((row) => (
        <div className="flex gap-2" key={row[0].rowNumber}>
          {row.map((seat) => {
            const isSelected = selected.includes(seat.id);
            const state = seatState(seat, isSelected);
            const taken = seat.state === "HELD" || seat.state === "BOOKED";
            return (
              <button
                aria-label={`Seat ${seat.label}, ${state}`}
                aria-pressed={isSelected}
                className={`h-11 w-11 rounded-lg border text-sm font-semibold transition-colors disabled:cursor-not-allowed ${seatClasses[state]}`}
                disabled={taken || disabled}
                key={seat.id}
                onClick={() => onToggle(seat.id)}
                type="button"
              >
                {seat.label}
              </button>
            );
          })}
        </div>
      ))}
    </div>
  );
}
