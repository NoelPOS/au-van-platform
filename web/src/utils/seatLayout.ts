import type { Seat } from "../types/inventory";

export type DraftSeat = {
  rowNumber: number;
  columnNumber: number;
  customLabel?: string;
};

export type Draft = { rows: number; columns: number; seats: DraftSeat[] };

export type Preset = { name: string; columns: number; rows: number[][] };

export const maxRows = 8;
export const maxColumns = 6;

export const presets: Preset[] = [
  {
    name: "Toyota Commuter · 13 seats",
    columns: 4,
    rows: [
      [1, 3, 4],
      [1, 3, 4],
      [1, 3, 4],
      [1, 2, 3, 4],
    ],
  },
  {
    name: "Toyota Hiace · 10 seats",
    columns: 4,
    rows: [
      [1, 3, 4],
      [1, 3, 4],
      [1, 2, 3, 4],
    ],
  },
  { name: "Blank", columns: 4, rows: [[], [], [], [], []] },
];

export function fromPreset(preset: Preset): Draft {
  return {
    rows: preset.rows.length,
    columns: preset.columns,
    seats: preset.rows.flatMap((columns, index) =>
      columns.map((columnNumber) => ({ rowNumber: index + 1, columnNumber })),
    ),
  };
}

export function rowLetter(rowNumber: number): string {
  return String.fromCharCode(64 + rowNumber);
}

function byPosition(left: DraftSeat, right: DraftSeat): number {
  return left.rowNumber - right.rowNumber || left.columnNumber - right.columnNumber;
}

export function labelSeats(draft: Draft): Seat[] {
  const countInRow = new Map<number, number>();
  return [...draft.seats].sort(byPosition).map((seat) => {
    const ordinal = (countInRow.get(seat.rowNumber) ?? 0) + 1;
    countInRow.set(seat.rowNumber, ordinal);
    return {
      label: seat.customLabel?.trim() ?? `${rowLetter(seat.rowNumber)}${ordinal}`,
      rowNumber: seat.rowNumber,
      columnNumber: seat.columnNumber,
    };
  });
}

export function seatAt<T extends Omit<Seat, "label">>(
  seats: T[],
  rowNumber: number,
  columnNumber: number,
): T | undefined {
  return seats.find(
    (seat) => seat.rowNumber === rowNumber && seat.columnNumber === columnNumber,
  );
}

export function toggleSeat(draft: Draft, rowNumber: number, columnNumber: number): Draft {
  const seats = seatAt(draft.seats, rowNumber, columnNumber)
    ? draft.seats.filter(
        (seat) => seat.rowNumber !== rowNumber || seat.columnNumber !== columnNumber,
      )
    : [...draft.seats, { rowNumber, columnNumber }];
  return { ...draft, seats };
}

export function renameSeat(
  draft: Draft,
  rowNumber: number,
  columnNumber: number,
  label: string,
): Draft {
  const seats = draft.seats.map((seat) =>
    seat.rowNumber === rowNumber && seat.columnNumber === columnNumber
      ? { rowNumber, columnNumber, ...(label.trim() ? { customLabel: label } : {}) }
      : seat,
  );
  return { ...draft, seats };
}

export function resize(draft: Draft, rows: number, columns: number): Draft {
  return {
    rows,
    columns,
    seats: draft.seats.filter(
      (seat) => seat.rowNumber <= rows && seat.columnNumber <= columns,
    ),
  };
}

export function fromSeats(seats: Seat[]): Draft {
  const draft = {
    rows: Math.max(1, ...seats.map((seat) => seat.rowNumber)),
    columns: Math.max(1, ...seats.map((seat) => seat.columnNumber)),
    seats: seats.map(({ rowNumber, columnNumber }) => ({ rowNumber, columnNumber })),
  };
  const automatic = labelSeats(draft);
  return {
    ...draft,
    seats: draft.seats.map((seat) => {
      const { label } = seatAt(seats, seat.rowNumber, seat.columnNumber)!;
      const generated = seatAt(automatic, seat.rowNumber, seat.columnNumber)!.label;
      return label === generated ? seat : { ...seat, customLabel: label };
    }),
  };
}
