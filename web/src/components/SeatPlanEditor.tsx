import { useRef, useState, type KeyboardEvent } from "react";
import { VanSeatPlan } from "./VanSeatPlan";
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
    const next = {
      row: clamp(rowNumber + move[0], draft.rows),
      column: clamp(columnNumber + move[1], draft.columns),
    };
    setActive(next);
    cells.current[`${next.row}:${next.column}`]?.focus();
  }

  function renderCell(rowNumber: number, columnNumber: number) {
    const seat = seatAt(seats, rowNumber, columnNumber);
    const current = rowNumber === row && columnNumber === column;
    return (
      <button
        aria-label={`Row ${rowNumber}, column ${columnNumber}${seat ? `, seat ${seat.label}` : ""}`}
        aria-pressed={Boolean(seat)}
        className={`group grid h-full w-full place-items-center rounded-lg text-[0.7rem] font-semibold tabular-nums transition-colors duration-150 ease-out outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2 ${
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
          <span className="block text-4xl font-semibold tabular-nums text-brand-900">
            {seats.length}
          </span>
          {seats.length === 1 ? " seat placed" : " seats placed"}
        </p>
        {activeSeat ? (
          <div className="grid gap-1">
            <p className="text-[0.7rem] font-semibold uppercase tracking-[0.14em] text-brand-500">
              Row {row}, column {column}
            </p>
            <label className="grid gap-1 text-sm font-semibold text-ink">
              Seat label
              <input
                className="rounded-lg border border-line bg-card px-3 py-2 font-normal text-ink outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
                maxLength={12}
                onChange={(event) => onChange(renameSeat(draft, row, column, event.target.value))}
                placeholder={activeSeat.label}
                value={activeDraftSeat?.customLabel ?? ""}
              />
            </label>
            <p className="text-xs text-muted">Leave empty to keep the automatic label.</p>
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
