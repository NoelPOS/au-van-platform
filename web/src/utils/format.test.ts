import { describe, expect, it } from "vitest";
import { formatAgo } from "./format";

describe("formatAgo", () => {
  it("says how long ago a slip arrived in the largest whole unit", () => {
    const now = Date.parse("2026-10-02T12:00:00Z");
    expect(formatAgo("2026-10-02T11:59:30Z", now)).toBe("just now");
    expect(formatAgo("2026-10-02T11:48:00Z", now)).toBe("12 min ago");
    expect(formatAgo("2026-10-02T09:00:00Z", now)).toBe("3 h ago");
    expect(formatAgo("2026-09-30T11:00:00Z", now)).toBe("2 d ago");
  });
});
