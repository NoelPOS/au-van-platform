import { describe, expect, it } from "vitest";
import type { VanRoute } from "../types/inventory";
import { firstClash, reverseOf, shuttleLines, type Shuttle } from "./shuttle";

function route(
  id: string,
  origin: string,
  destination: string,
  minutes: number,
): VanRoute {
  return {
    id,
    origin,
    destination,
    fare: 35,
    durationMinutes: minutes,
    status: "ACTIVE",
  };
}

const out = route("out", "AU", "Mega Bangna", 45);
const back = route("back", "Mega Bangna", "AU", 30);
const paragon = route("paragon", "AU", "Siam Paragon", 60);
const routes = [out, back, paragon];

const shuttle: Shuttle = {
  outId: "out",
  backId: "back",
  vehicleId: "van-1",
  first: "07:00",
  last: "09:00",
  wait: 15,
};

const times = (lines: { time: string; routeId: string }[]) =>
  lines.map((line) => `${line.time} ${line.routeId}`);

describe("shuttleLines", () => {
  it("alternates out and back, waiting after each leg, until the last departure", () => {
    expect(times(shuttleLines(shuttle, routes))).toEqual([
      "07:00 out",
      "08:00 back",
      "08:45 out",
    ]);
  });

  it("keeps a departure that lands exactly on the last time", () => {
    expect(times(shuttleLines({ ...shuttle, last: "08:45" }, routes))).toEqual([
      "07:00 out",
      "08:00 back",
      "08:45 out",
    ]);
    expect(shuttleLines({ ...shuttle, last: "08:44" }, routes)).toHaveLength(2);
  });

  it("leaves room for an empty return on a one-way shuttle", () => {
    expect(times(shuttleLines({ ...shuttle, backId: "" }, routes))).toEqual([
      "07:00 out",
      "08:45 out",
    ]);
  });

  it("stops before midnight instead of wrapping into the next day", () => {
    const late = {
      ...shuttle,
      backId: "",
      first: "22:00",
      last: "23:59",
      wait: 0,
    };
    expect(times(shuttleLines(late, routes))).toEqual([
      "22:00 out",
      "23:30 out",
    ]);
  });

  it("adds nothing when the first departure is after the last", () => {
    expect(shuttleLines({ ...shuttle, first: "10:00" }, routes)).toEqual([]);
  });
});

describe("reverseOf", () => {
  it("finds the route that runs the other way, or nothing", () => {
    expect(reverseOf(out, routes)).toBe(back);
    expect(reverseOf(paragon, routes)).toBeUndefined();
  });
});

describe("firstClash", () => {
  const lines = shuttleLines(shuttle, routes);

  it("finds a line whose trip overlaps one already on the same van", () => {
    const existing = { time: "07:30", routeId: "paragon", vehicleId: "van-1" };
    expect(firstClash(lines, [existing], routes)).toEqual({
      line: lines[0],
      existing,
    });
  });

  it("counts an existing trip still running when the shuttle leaves", () => {
    const existing = { time: "06:30", routeId: "out", vehicleId: "van-1" };
    expect(firstClash(lines, [existing], routes)?.existing).toBe(existing);
  });

  it("lets a trip leave the moment the van gets back", () => {
    const touching = { time: "06:15", routeId: "out", vehicleId: "van-1" };
    expect(firstClash(lines, [touching], routes)).toBeNull();
  });

  it("ignores other vans", () => {
    const other = { time: "07:00", routeId: "out", vehicleId: "van-2" };
    expect(firstClash(lines, [other], routes)).toBeNull();
  });
});
