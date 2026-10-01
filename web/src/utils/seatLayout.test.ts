import { describe, expect, it } from "vitest";
import {
  fromPreset,
  fromSeats,
  labelSeats,
  presets,
  renameSeat,
  resize,
  toggleSeat,
  type Draft,
} from "./seatLayout";

const blank: Draft = { rows: 3, columns: 4, seats: [] };

describe("seat layout presets", () => {
  it("place as many seats as each preset's name promises", () => {
    for (const preset of presets) {
      const promised = Number(/(\d+) seats/.exec(preset.name)?.[1] ?? 0);
      expect(fromPreset(preset).seats).toHaveLength(promised);
    }
  });

  it("size the van to the preset's rows and columns", () => {
    const commuter = fromPreset(presets[0]);
    expect(commuter).toMatchObject({ rows: 4, columns: 4 });
    expect(commuter.seats[0]).toEqual({ rowNumber: 1, columnNumber: 1 });
  });
});

describe("toggleSeat", () => {
  it("adds a seat to an empty space and removes it on the second toggle", () => {
    const placed = toggleSeat(blank, 2, 3);
    expect(placed.seats).toEqual([{ rowNumber: 2, columnNumber: 3 }]);
    expect(toggleSeat(placed, 2, 3).seats).toEqual([]);
  });

  it("forgets a renamed seat's label once the seat is removed", () => {
    const renamed = renameSeat(toggleSeat(blank, 1, 1), 1, 1, "Front");
    const replaced = toggleSeat(toggleSeat(renamed, 1, 1), 1, 1);
    expect(labelSeats(replaced)[0].label).toBe("A1");
  });
});

describe("labelSeats", () => {
  it("letters rows front to back and numbers seats left to right, skipping the aisle", () => {
    let draft = blank;
    for (const [row, column] of [[2, 4], [1, 3], [1, 1], [2, 1], [1, 4]]) {
      draft = toggleSeat(draft, row, column);
    }
    expect(labelSeats(draft)).toEqual([
      { label: "A1", rowNumber: 1, columnNumber: 1 },
      { label: "A2", rowNumber: 1, columnNumber: 3 },
      { label: "A3", rowNumber: 1, columnNumber: 4 },
      { label: "B1", rowNumber: 2, columnNumber: 1 },
      { label: "B2", rowNumber: 2, columnNumber: 4 },
    ]);
  });

  it("uses a renamed seat's label, trimmed, in place of the automatic one", () => {
    const draft = renameSeat(toggleSeat(blank, 1, 2), 1, 2, " Front ");
    expect(labelSeats(draft)).toEqual([
      { label: "Front", rowNumber: 1, columnNumber: 2 },
    ]);
  });

  it("returns to the automatic label when a rename is cleared", () => {
    const renamed = renameSeat(toggleSeat(blank, 1, 2), 1, 2, "Front");
    expect(labelSeats(renameSeat(renamed, 1, 2, "  "))[0].label).toBe("A1");
  });
});

describe("resize", () => {
  it("drops seats that fall outside the smaller van", () => {
    const draft = toggleSeat(toggleSeat(blank, 3, 1), 1, 4);
    expect(resize(draft, 2, 4).seats).toEqual([{ rowNumber: 1, columnNumber: 4 }]);
    expect(resize(draft, 3, 3)).toEqual({
      rows: 3,
      columns: 3,
      seats: [{ rowNumber: 3, columnNumber: 1 }],
    });
  });
});

describe("fromSeats", () => {
  it("sizes the builder to the furthest seat and keeps only labels it would not generate", () => {
    const draft = fromSeats([
      { label: "A1", rowNumber: 1, columnNumber: 1 },
      { label: "Jump", rowNumber: 1, columnNumber: 3 },
      { label: "B1", rowNumber: 3, columnNumber: 2 },
    ]);
    expect(draft).toEqual({
      rows: 3,
      columns: 3,
      seats: [
        { rowNumber: 1, columnNumber: 1 },
        { rowNumber: 1, columnNumber: 3, customLabel: "Jump" },
        { rowNumber: 3, columnNumber: 2, customLabel: "B1" },
      ],
    });
  });

  it("round-trips an API layout back to the same seats", () => {
    const seats = [
      { label: "A1", rowNumber: 1, columnNumber: 1 },
      { label: "A2", rowNumber: 1, columnNumber: 2 },
      { label: "Rear", rowNumber: 2, columnNumber: 2 },
    ];
    expect(labelSeats(fromSeats(seats))).toEqual(seats);
  });

  it("gives an empty layout a one-space van rather than none", () => {
    expect(fromSeats([])).toEqual({ rows: 1, columns: 1, seats: [] });
  });
});
