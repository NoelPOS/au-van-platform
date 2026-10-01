import { describe, expect, it } from "vitest";
import {
  addMinutes,
  bangkokDateTime,
  fromBangkokInputs,
  parseClock,
  toBangkokInputs,
} from "./dates";

describe("Bangkok dates", () => {
  it("formats an instant as the day and clock time in Bangkok", () => {
    expect(bangkokDateTime("2026-10-02T15:14:00Z")).toBe("Fri 2 Oct · 22:14");
  });

  it("splits an instant into Bangkok date and time inputs, across midnight", () => {
    expect(toBangkokInputs("2026-10-02T20:30:00Z")).toEqual({
      date: "2026-10-03",
      time: "03:30",
    });
  });

  it("reads Bangkok date and time inputs back as the same instant", () => {
    expect(fromBangkokInputs("2026-10-03", "03:30")).toBe(
      "2026-10-02T20:30:00.000Z",
    );
  });

  it("reads a typed 24-hour clock time and refuses anything else", () => {
    expect(parseClock("07:30")).toBe("07:30");
    expect(parseClock(" 7:05 ")).toBe("07:05");
    expect(parseClock("23:59")).toBe("23:59");
    for (const text of ["24:00", "12:60", "7.30", "730", "", "07:30pm"])
      expect(parseClock(text)).toBeNull();
  });

  it("adds a journey's minutes to a clock time, past midnight", () => {
    expect(addMinutes("21:59", 45)).toBe("22:44");
    expect(addMinutes("23:30", 45)).toBe("00:15");
  });
});
