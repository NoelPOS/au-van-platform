import { describe, expect, it } from "vitest";
import { configuredLiffId } from "./liff-config";

describe("LIFF configuration", () => {
  it("does not expose a configured LIFF ID when the local environment is unset", () => {
    expect(configuredLiffId()).toBeNull();
  });
});
