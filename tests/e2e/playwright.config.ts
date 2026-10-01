import { defineConfig, devices } from "@playwright/test";

/**
 * The suite runs against the stack `compose.e2e.yaml` stands up, on the web
 * container's single origin — the SPA and `/api` behind one host, which is the
 * contract the bundle relies on and the one `Container checks` already proves.
 * It never starts a server itself: `npm run stack:up` does that, and CI does the
 * same thing in its own step, so a failure is a failure of the product rather
 * than of a web server Playwright happened to boot differently.
 */
export default defineConfig({
  testDir: ".",
  globalSetup: "./global-setup.ts",

  // One worker, deliberately. There is one database behind these tests and the
  // administrator is one promoted row; running them in parallel would buy a few
  // seconds and cost the ability to read a failure.
  workers: 1,
  fullyParallel: false,

  // No retries. A flaky end-to-end test that passes on the second attempt is a
  // bug report this repository would rather read than hide, and the two places
  // that could race — the ten-second seat-map poll and the one-minute sweeps —
  // are each handled deliberately in the specs and in the overlay.
  retries: 0,
  forbidOnly: !!process.env.CI,

  // Generous, because one spec deliberately waits out a forty-second seat hold.
  timeout: 120_000,
  expect: { timeout: 15_000 },

  reporter: process.env.CI
    ? [["github"], ["html", { open: "never" }]]
    : [["list"], ["html", { open: "never" }]],

  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:8081",
    // Pinned, because the specs read back what `Intl.DateTimeFormat` rendered
    // in the browser's timezone. Left to the machine, a developer's timezone
    // and CI's UTC would disagree about the same trip.
    timezoneId: "UTC",
    locale: "en-GB",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "off",
  },

  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
