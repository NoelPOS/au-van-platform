import { afterEach, describe, expect, it, vi } from "vitest";
import { configuredLiffId, e2eAuthEnabled } from "./liffConfig";

describe("LIFF configuration", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
    vi.resetModules();
  });

  it("does not expose a configured LIFF ID when the local environment is unset", () => {
    expect(configuredLiffId()).toBeNull();
  });

  it("leaves the end-to-end sign-in off in a build that did not ask for it", () => {
    expect(e2eAuthEnabled).toBe(false);
  });

  /**
   * Re-imported rather than re-read: `e2eAuthEnabled` is a module-level
   * constant on purpose, because that is what lets the bundler fold the control
   * away entirely, so the only way it can change is a fresh evaluation.
   */
  it("turns the end-to-end sign-in on only for the exact string the build flag documents", async () => {
    vi.stubEnv("VITE_E2E_AUTH", "true");
    vi.resetModules();
    expect((await import("./liffConfig")).e2eAuthEnabled).toBe(true);

    vi.stubEnv("VITE_E2E_AUTH", "");
    vi.resetModules();
    expect((await import("./liffConfig")).e2eAuthEnabled).toBe(false);

    vi.stubEnv("VITE_E2E_AUTH", "1");
    vi.resetModules();
    expect((await import("./liffConfig")).e2eAuthEnabled).toBe(false);
  });
});
