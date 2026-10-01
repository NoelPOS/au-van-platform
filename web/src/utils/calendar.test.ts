import { describe, expect, it } from "vitest";
import { addDays, addMonths, chipLabel, dayHeading, monthGrid } from "./calendar";

describe("calendar days", () => {
  it("steps across month and year ends", () => {
    expect(addDays("2026-12-31", 1)).toBe("2027-01-01");
    expect(addDays("2028-02-28", 1)).toBe("2028-02-29");
    expect(addMonths("2026-12", 1)).toBe("2027-01");
    expect(addMonths("2027-01", -1)).toBe("2026-12");
  });

  it("names chips relative to today and adds the month beyond two weeks", () => {
    const today = "2026-10-02";
    expect(chipLabel("2026-10-02", today)).toBe("Today");
    expect(chipLabel("2026-10-03", today)).toBe("Tomorrow");
    expect(chipLabel("2026-10-04", today)).toBe("Sun 4");
    expect(chipLabel("2026-10-15", today)).toBe("Thu 15");
    expect(chipLabel("2026-10-16", today)).toBe("Fri 16 Oct");
    expect(chipLabel("2026-10-01", today)).toBe("Thu 1 Oct");
  });

  it("heads a day with its year only when it is not this year's", () => {
    expect(dayHeading("2026-10-02", "2026-10-02")).toBe("Today · Fri 2 Oct");
    expect(dayHeading("2026-10-03", "2026-10-02")).toBe("Tomorrow · Sat 3 Oct");
    expect(dayHeading("2025-12-31", "2026-01-01")).toBe("Wed, 31 Dec 2025");
  });

  it("lays a month out from Monday", () => {
    const october = monthGrid("2026-10");
    expect(october.slice(0, 4)).toEqual([null, null, null, "2026-10-01"]);
    expect(october).toHaveLength(3 + 31);
    expect(monthGrid("2027-02").at(-1)).toBe("2027-02-28");
  });
});
