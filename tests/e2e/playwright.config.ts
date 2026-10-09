import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: ".",
  globalSetup: "./global-setup.ts",

  // One database and one promoted administrator: the tests run serially.
  workers: 1,
  fullyParallel: false,

  retries: 0,
  forbidOnly: !!process.env.CI,

  // One spec waits out a 40-second seat hold.
  timeout: 120_000,
  expect: { timeout: 15_000 },

  reporter: process.env.CI
    ? [["github"], ["html", { open: "never" }]]
    : [["list"], ["html", { open: "never" }]],

  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:8081",
    // The specs read back dates rendered in the browser's timezone.
    timezoneId: "UTC",
    locale: "en-GB",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "off",
  },

  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
