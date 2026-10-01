import { describe, expect, it } from "vitest";
import { statusLabel } from "./statusLabels";

describe("statusLabel", () => {
  it("names each status the way a person would say it", () => {
    expect(statusLabel("PENDING_PAYMENT")).toBe("Awaiting payment");
    expect(statusLabel("PAYMENT_UNDER_REVIEW")).toBe("In review");
    expect(statusLabel("PAYMENT_REJECTED")).toBe("Sent back");
    expect(statusLabel("PROMOTED")).toBe("Seat offered");
    expect(statusLabel("ACTIVE")).toBe("Active");
  });

  it("falls back to a readable form of a status it has no label for", () => {
    expect(statusLabel("ON_HOLD_FOR_DRIVER")).toBe("On hold for driver");
  });
});
