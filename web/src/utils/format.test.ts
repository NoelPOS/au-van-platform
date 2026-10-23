import { describe, expect, it } from "vitest";
import { formatAgo, formatBaht, formatPercent } from "./format";

describe("formatAgo", () => {
  it("says how long ago a slip arrived in the largest whole unit", () => {
    const now = Date.parse("2026-10-02T12:00:00Z");
    expect(formatAgo("2026-10-02T11:59:30Z", now)).toBe("just now");
    expect(formatAgo("2026-10-02T11:48:00Z", now)).toBe("12 min ago");
    expect(formatAgo("2026-10-02T09:00:00Z", now)).toBe("3 h ago");
    expect(formatAgo("2026-09-30T11:00:00Z", now)).toBe("2 d ago");
  });
});

describe("formatBaht", () => {
  it("shows whole baht bare, satang to two places, and groups thousands", () => {
    expect(formatBaht(35)).toBe("฿35");
    expect(formatBaht(35.5)).toBe("฿35.50");
    expect(formatBaht(12450)).toBe("฿12,450");
  });
});

describe("formatPercent", () => {
  it("rounds a ratio to a whole percentage", () => {
    expect(formatPercent(0)).toBe("0%");
    expect(formatPercent(5 / 12)).toBe("42%");
  });
});
