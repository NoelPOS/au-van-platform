import { describe, expect, it } from "vitest";
import {
  bangkokDateTime,
  fromBangkokInputs,
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
});
