import { expect, type Page } from "@playwright/test";

import { adminSubject } from "./global-setup";

export { adminSubject };

/**
 * Shared steps for the two journeys.
 *
 * <p>**Everything starts at `/` and clicks its way in.** `App.tsx` and
 * `AdminPage.tsx` switch on React state rather than a router — "Local state
 * rather than a router: there are three destinations and no routing dependency"
 * — so there is no `/admin` to deep-link to, and a reload drops the session
 * along with the token held in it. Signing in again is therefore the only way
 * to refresh a page's data from outside it, and one journey below uses exactly
 * that when the administrator changes something the student has to see.
 */

let sequence = 0;

/** Unique per test, so every run and every worker has its own fixtures. */
function uniqueSuffix(): string {
  sequence += 1;
  return `${Date.now().toString(36)}${sequence}`;
}

export function studentSubject(): string {
  return `e2e-student-${uniqueSuffix()}`;
}

/**
 * A `datetime-local` value, in the browser's timezone. The configuration pins
 * that to UTC so this string and what the page renders cannot disagree.
 */
function departureInDays(days: number): string {
  const when = new Date(Date.now() + days * 86_400_000);
  when.setUTCSeconds(0, 0);
  return when.toISOString().slice(0, 16);
}

async function signIn(page: Page, subject: string): Promise<void> {
  await page.goto("/");
  await page.getByLabel("End-to-end sign-in subject").fill(subject);
  await page.getByRole("button", { name: "Sign in as subject" }).click();
}

export async function signInAsAdmin(page: Page): Promise<void> {
  await signIn(page, adminSubject);
  await expect(
    page.getByRole("heading", { name: "Transport inventory" }),
  ).toBeVisible();
}

export async function signInAsStudent(
  page: Page,
  subject: string,
): Promise<void> {
  await signIn(page, subject);
  await expect(page.getByRole("heading", { name: "Book a seat" })).toBeVisible();
}

function adminDestination(page: Page, label: string) {
  return page
    .getByRole("navigation", { name: "Administration" })
    .getByRole("button", { name: label });
}

function inventoryTab(page: Page, label: string) {
  return page
    .getByRole("navigation", { name: "Transport inventory sections" })
    .getByRole("button", { name: new RegExp(`^${label}`) });
}

export async function goToPaymentReview(page: Page): Promise<void> {
  await adminDestination(page, "Payment review").click();
  await expect(page.getByRole("heading", { name: "Payment review" })).toBeVisible();
}

export async function goToOperations(page: Page): Promise<void> {
  await adminDestination(page, "Operations").click();
  await expect(page.getByRole("heading", { name: "Operations" })).toBeVisible();
}

export type TripFixture = {
  routeLabel: string;
  vehicleCode: string;
  seatLabels: string[];
};

/**
 * A route, a seat layout, a van and a departure, created through the four
 * sections of the administrator's own inventory screen rather than seeded into
 * the database. That is the point: it is the administrator journey, and it is
 * also every other test's fixture, so a break in it is a break in the product.
 */
export async function createTrip(
  page: Page,
  seatLabels: string[],
): Promise<TripFixture> {
  const suffix = uniqueSuffix();
  const origin = `Assumption University ${suffix}`;
  const destination = `Mega Bangna ${suffix}`;
  const routeLabel = `${origin} → ${destination}`;
  const layoutName = `Hiace ${suffix}`;
  const vehicleCode = `VAN-${suffix}`;

  await inventoryTab(page, "Routes").click();
  await page.getByLabel("Origin", { exact: true }).fill(origin);
  await page.getByLabel("Destination", { exact: true }).fill(destination);
  await page.getByLabel("Fare (THB)").fill("120");
  await page.getByLabel("Duration (minutes)").fill("45");
  await page.getByRole("button", { name: "Create route" }).click();
  await expect(page.getByRole("cell", { name: routeLabel })).toBeVisible();

  await inventoryTab(page, "Seat layouts").click();
  await page.getByLabel("Layout name").fill(layoutName);
  await page
    .getByLabel(/^Seats/)
    .fill(seatLabels.map((label, index) => `${label}, 1, ${index + 1}`).join("\n"));
  await page.getByRole("button", { name: "Create layout" }).click();
  await expect(page.getByRole("cell", { name: layoutName })).toBeVisible();

  await inventoryTab(page, "Vehicles").click();
  await page.getByLabel("Vehicle code").fill(vehicleCode);
  await page.getByLabel("Name", { exact: true }).fill(`White Hiace ${suffix}`);
  await page.getByLabel("Seat layout").selectOption({ label: layoutName });
  await page.getByRole("button", { name: "Create vehicle" }).click();
  await expect(page.getByRole("cell", { name: vehicleCode })).toBeVisible();

  await inventoryTab(page, "Trips").click();
  await page.getByLabel(/^Route/).selectOption({ label: routeLabel });
  await page
    .getByLabel(/^Vehicle/)
    .selectOption({ label: `${vehicleCode} — White Hiace ${suffix}` });
  // A week out, so that a booking's deadline is always the two-hour payment
  // window rather than `departureAt - departure-cutoff`, and the live expiry
  // sweep can never reach a booking made during a test.
  await page.getByLabel("Departure").fill(departureInDays(7));
  await page.getByRole("button", { name: "Schedule trip" }).click();
  await expect(
    page.getByRole("row", { name: new RegExp(vehicleCode) }),
  ).toBeVisible();

  return { routeLabel, vehicleCode, seatLabels };
}

export async function chooseTrip(page: Page, trip: TripFixture): Promise<void> {
  await page.getByRole("button", { name: trip.routeLabel }).click();
  await expect(page.getByRole("group", { name: "Seat map" })).toBeVisible();
}

/**
 * Holds one seat, fills the passenger form and confirms, returning the booking
 * reference the API minted. The reference is read off the response rather than
 * scraped out of the confirmation panel, because the administrator's review
 * queue is keyed on it and a misread would look like a missing booking.
 */
export async function bookSeat(
  page: Page,
  trip: TripFixture,
  seatLabel: string,
): Promise<string> {
  await chooseTrip(page, trip);
  await page.getByRole("button", { name: `Seat ${seatLabel}, available` }).click();
  await page.getByRole("button", { name: "Hold these seats" }).click();
  await expect(page.getByRole("heading", { name: "Passenger details" })).toBeVisible();

  await page.getByLabel("Full name").fill("Somchai P.");
  await page.getByLabel("Phone number").fill("0812345678");
  const created = page.waitForResponse(
    (response) =>
      response.url().endsWith("/api/v1/bookings") &&
      response.request().method() === "POST",
  );
  await page.getByRole("button", { name: "Confirm booking" }).click();
  const booking = (await (await created).json()) as { reference: string };

  await expect(page.getByRole("heading", { name: "Seats reserved" })).toBeVisible();
  await expect(page.getByText(booking.reference).first()).toBeVisible();
  return booking.reference;
}

/** The `<li>` in My bookings that carries this reference. */
export function bookingCard(page: Page, reference: string) {
  return page.getByRole("listitem").filter({ hasText: reference });
}

export async function backToTrips(page: Page): Promise<void> {
  await page.getByRole("button", { name: "Back to trips" }).click();
  await expect(page.getByRole("heading", { name: "My bookings" })).toBeVisible();
}

/**
 * Opens a booking reference in the administrator's review queue. The queue is
 * every student's, so it is found by reference rather than by position.
 */
export async function openProof(page: Page, reference: string): Promise<void> {
  await expect(page.getByRole("button", { name: reference })).toBeVisible();
  await page.getByRole("button", { name: reference }).click();
  await expect(page.getByRole("heading", { name: reference })).toBeVisible();
  await expect(
    page.getByRole("img", { name: `Payment slip for booking ${reference}` }),
  ).toBeVisible();
}
