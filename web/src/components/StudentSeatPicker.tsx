import type { TripSeat } from "../types/booking";
import { fromSeats, seatAt } from "../utils/seatLayout";
import { VanSeatPlan } from "./VanSeatPlan";

type Look = { text: string; className: string };

const looks = {
  available: {
    text: "available",
    className: "border border-ink/20 bg-card text-ink hover:border-brand-500",
  },
  selected: {
    text: "selected",
    className:
      "border border-brand-700 bg-brand-600 text-white shadow-[0_3px_0_var(--color-brand-900)]",
  },
  mine: {
    text: "held by you",
    className: "border-2 border-brand-500 bg-brand-50 text-brand-700",
  },
  held: {
    text: "held by someone else",
    className:
      "border border-line text-muted/70 bg-[repeating-linear-gradient(135deg,var(--color-line)_0_2px,transparent_2px_7px)]",
  },
  booked: { text: "booked", className: "bg-line text-muted/70" },
} satisfies Record<string, Look>;

function lookOf(seat: TripSeat, selected: boolean): Look {
  if (selected) return looks.selected;
  if (seat.state === "HELD_BY_YOU") return looks.mine;
  if (seat.state === "HELD") return looks.held;
  if (seat.state === "BOOKED") return looks.booked;
  return looks.available;
}

export function StudentSeatPicker({
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
  const { rows, columns } = fromSeats(seats);
  return (
    <div className="flex flex-col items-center gap-5">
      <VanSeatPlan
        columns={columns}
        label="Seat map"
        renderCell={(rowNumber, columnNumber) => {
          const seat = seatAt(seats, rowNumber, columnNumber);
          if (!seat) return null;
          const isSelected = selected.includes(seat.id);
          const look = lookOf(seat, isSelected);
          const taken = seat.state === "HELD" || seat.state === "BOOKED";
          return (
            <button
              aria-label={`Seat ${seat.label}, ${look.text}`}
              aria-pressed={isSelected}
              className={`grid h-full w-full place-items-center rounded-t-xl rounded-b-md font-mono text-[13px] font-medium transition-[background-color,border-color,transform] duration-150 ease-out active:translate-y-px disabled:cursor-not-allowed ${look.className}`}
              disabled={taken || disabled}
              onClick={() => onToggle(seat.id)}
              type="button"
            >
              {seat.label}
            </button>
          );
        }}
        rows={rows}
      />
      <ul
        aria-label="Seat legend"
        className="flex flex-wrap justify-center gap-x-4 gap-y-2 text-xs text-muted"
      >
        {[looks.available, looks.selected, looks.held, looks.booked].map((look) => (
          <li className="flex items-center gap-1.5" key={look.text}>
            <span
              aria-hidden
              className={`size-3.5 rounded-t-[5px] rounded-b-[3px] ${look.className} shadow-none`}
            />
            {look.text === "held by someone else" ? "held" : look.text}
          </li>
        ))}
      </ul>
    </div>
  );
}
