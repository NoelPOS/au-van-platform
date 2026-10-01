import { useRef, useState, type KeyboardEvent } from "react";
import { VanSeatPlan } from "./VanSeatPlan";
import { Input } from "./ui/Input";
import {
  labelSeats,
  renameSeat,
  seatAt,
  toggleSeat,
  type Draft,
} from "../utils/seatLayout";

const moves: Record<string, [number, number]> = {
  ArrowUp: [-1, 0],
  ArrowDown: [1, 0],
  ArrowLeft: [0, -1],
  ArrowRight: [0, 1],
};

const clamp = (value: number, max: number) => Math.min(max, Math.max(1, value));

export function SeatPlanEditor({
  draft,
  onChange,
}: {
  draft: Draft;
  onChange: (draft: Draft) => void;
}) {
  const [active, setActive] = useState({ row: 1, column: 1 });
  const cells = useRef<Record<string, HTMLButtonElement | null>>({});
  const seats = labelSeats(draft);
  const row = clamp(active.row, draft.rows);
  const column = clamp(active.column, draft.columns);
  const activeSeat = seatAt(seats, row, column);
  const activeDraftSeat = seatAt(draft.seats, row, column);
  const aisle = (columnNumber: number) =>
    !draft.seats.some((seat) => seat.columnNumber === columnNumber);

  function moveFocus(event: KeyboardEvent, rowNumber: number, columnNumber: number) {
    const move = moves[event.key];
    if (!move) return;
    event.preventDefault();
    const next = { row: rowNumber + move[0], column: columnNumber + move[1] };
    const target = cells.current[`${next.row}:${next.column}`];
    if (!target) return;
    setActive(next);
    target.focus();
  }

  function renderCell(rowNumber: number, columnNumber: number) {
    const seat = seatAt(seats, rowNumber, columnNumber);
    const current = rowNumber === row && columnNumber === column;
    return (
      <button
        aria-label={`Row ${rowNumber}, column ${columnNumber}${seat ? `, seat ${seat.label}` : ""}`}
        aria-pressed={Boolean(seat)}
        className={`group grid h-full w-full place-items-center rounded-lg text-[0.7rem] font-semibold tabular-nums transition-colors duration-150 ease-out ${
          seat
            ? "rounded-t-xl border-b-4 border-brand-900 bg-brand-600 text-white hover:bg-brand-700"
            : aisle(columnNumber)
              ? "bg-brand-50 text-brand-500 hover:bg-brand-100"
              : "border border-dashed border-line text-muted hover:border-brand-500 hover:text-brand-600"
        }`}
        onClick={() => {
          setActive({ row: rowNumber, column: columnNumber });
          onChange(toggleSeat(draft, rowNumber, columnNumber));
        }}
        onKeyDown={(event) => moveFocus(event, rowNumber, columnNumber)}
        ref={(node) => {
          cells.current[`${rowNumber}:${columnNumber}`] = node;
        }}
        tabIndex={current ? 0 : -1}
        type="button"
      >
        {seat ? (
          <span className="max-w-full truncate px-0.5">{seat.label}</span>
        ) : (
          <span aria-hidden="true" className="opacity-0 group-hover:opacity-100 group-focus-visible:opacity-100">
            +
          </span>
        )}
      </button>
    );
  }

  return (
    <div className="grid items-start gap-6 sm:grid-cols-[minmax(0,1fr)_14rem]">
      <div className="grid justify-items-center gap-3">
        <VanSeatPlan
          columns={draft.columns}
          label="Seat plan"
          renderCell={renderCell}
          rows={draft.rows}
        />
        <p className="max-w-xs text-center text-xs text-muted">
          Front of the van at the top. The driver sits on the right and the
          sliding door is on the left. Leave a column empty for the aisle.
        </p>
      </div>
      <div className="grid gap-4">
        <p aria-live="polite" className="text-sm text-muted">
          <span className="block font-display text-5xl font-light tabular-nums text-brand-900">
            {seats.length}
          </span>
          {seats.length === 1 ? " seat placed" : " seats placed"}
        </p>
        {activeSeat ? (
          <div className="grid gap-2">
            <p className="text-[11px] font-semibold tracking-[0.16em] text-brand-500 uppercase">
              Row {row}, column {column}
            </p>
            <Input
              hint="Leave empty to keep the automatic label."
              label="Seat label"
              maxLength={12}
              onChange={(event) => onChange(renameSeat(draft, row, column, event.target.value))}
              placeholder={activeSeat.label}
              value={activeDraftSeat?.customLabel ?? ""}
            />
          </div>
        ) : (
          <p className="text-sm text-muted">
            Choose a space in the van to place a seat. Arrow keys move between
            spaces; Space or Enter places or removes a seat.
          </p>
        )}
      </div>
    </div>
  );
}
