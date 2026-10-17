import { afterEach, describe, expect, it, vi } from "vitest";
import {
  configuredLiffId,
  demoLoginEnabled,
  e2eAuthEnabled,
  insideLineApp,
  liffUrl,
  returningFromLine,
} from "./liffConfig";

describe("LIFF configuration", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
    vi.resetModules();
    vi.restoreAllMocks();
  });

  it("does not expose a configured LIFF ID when the local environment is unset", () => {
    expect(configuredLiffId()).toBeNull();
  });

  it("leaves the end-to-end sign-in off in a build that did not ask for it", () => {
    expect(e2eAuthEnabled).toBe(false);
  });

  // e2eAuthEnabled is a module-level constant, so only a fresh import re-reads it.
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

  it("shows the demo buttons only in a build that says exactly true", async () => {
    expect(demoLoginEnabled).toBe(false);

    vi.stubEnv("VITE_DEMO_LOGIN", "true");
    vi.resetModules();
    expect((await import("./liffConfig")).demoLoginEnabled).toBe(true);

    vi.stubEnv("VITE_DEMO_LOGIN", "false");
    vi.resetModules();
    expect((await import("./liffConfig")).demoLoginEnabled).toBe(false);
  });

  it("builds the LINE app link from the LIFF ID, and none without one", () => {
    expect(liffUrl()).toBeNull();

    vi.stubEnv("VITE_LIFF_ID", "1234567890-abcdefgh");
    expect(liffUrl()).toBe("https://liff.line.me/1234567890-abcdefgh");
  });

  it("knows the LINE app's own browser from its user agent", () => {
    const agent = vi.spyOn(navigator, "userAgent", "get");

    agent.mockReturnValue("Mozilla/5.0 (iPhone) Mobile/15E148 Safari Line/14.2.0");
    expect(insideLineApp()).toBe(true);

    agent.mockReturnValue("Mozilla/5.0 (Macintosh) Safari/605.1.15");
    expect(insideLineApp()).toBe(false);
  });

  it("recognises a page load that comes back from LINE login", () => {
    expect(returningFromLine("?code=abc&state=xyz")).toBe(true);
    expect(returningFromLine("?liffClientId=123&liffRedirectUri=x")).toBe(true);
    expect(returningFromLine("?liff.state=%2Ftickets")).toBe(true);
    expect(returningFromLine("")).toBe(false);
    expect(returningFromLine("?ref=github")).toBe(false);
  });
});
