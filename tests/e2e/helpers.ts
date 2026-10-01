import { expect, type Page } from "@playwright/test";

import { adminSubject } from "./global-setup";

export { adminSubject };

// Sessions live only in memory, so a reload means signing in again; one journey
// relies on that to refresh what the student sees.

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
 * Date and time inputs as the trip form reads them: Bangkok wall-clock time,
 * which is UTC+7 all year, whatever timezone the browser runs in.
 */
function departureInDays(days: number): { date: string; time: string } {
  const bangkok = new Date(Date.now() + days * 86_400_000 + 7 * 3_600_000);
  const stamp = bangkok.toISOString();
  return { date: stamp.slice(0, 10), time: stamp.slice(11, 16) };
}

export async function signIn(
  page: Page,
  subject: string,
  path = "/",
): Promise<void> {
  await page.goto(path);
  await page.getByLabel("End-to-end sign-in subject").fill(subject);
  await page.getByRole("button", { name: "Sign in as subject" }).click();
}

export async function signInAsAdmin(page: Page): Promise<void> {
  await signIn(page, adminSubject);
  await expect(
    page.getByRole("heading", { name: "Overview" }),
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
    .getByRole("link", { name: label });
}

export async function goToPaymentReview(page: Page): Promise<void> {
  await adminDestination(page, "Payments").click();
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
 * pages of the administrator's own inventory rather than seeded into
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

  await adminDestination(page, "Routes").click();
  await page.getByRole("button", { name: "New route" }).click();
  const routeForm = page.getByRole("dialog", { name: "New route" });
  await routeForm.getByLabel("Origin", { exact: true }).fill(origin);
  await routeForm.getByLabel("Destination", { exact: true }).fill(destination);
  await routeForm.getByLabel("Fare (THB)").fill("120");
  await routeForm.getByLabel("Duration (minutes)").fill("45");
  await routeForm.getByRole("button", { name: "Create route" }).click();
  await expect(page.getByRole("cell", { name: routeLabel, exact: true })).toBeVisible();

  await adminDestination(page, "Seat layouts").click();
  await page.getByLabel("Layout name").fill(layoutName);
  await page
    .getByLabel(/^Seats/)
    .fill(seatLabels.map((label, index) => `${label}, 1, ${index + 1}`).join("\n"));
  await page.getByRole("button", { name: "Create layout" }).click();
  await expect(page.getByRole("cell", { name: layoutName })).toBeVisible();

  await adminDestination(page, "Vans").click();
  await page.getByRole("button", { name: "New van" }).click();
  const vanForm = page.getByRole("dialog", { name: "New van" });
  await vanForm.getByLabel("Van code").fill(vehicleCode);
  await vanForm.getByLabel("Name", { exact: true }).fill(`White Hiace ${suffix}`);
  await vanForm
    .getByLabel("Seat layout")
    .selectOption({ label: `${layoutName} · ${seatLabels.length} seats` });
  await vanForm.getByRole("button", { name: "Add van" }).click();
  await expect(page.getByRole("cell", { name: vehicleCode, exact: true })).toBeVisible();

  await adminDestination(page, "Trips").click();
  await page.getByRole("button", { name: "New trip" }).click();
  const tripForm = page.getByRole("dialog", { name: "Schedule a trip" });
  await tripForm.getByLabel("Route").selectOption({ label: routeLabel });
  await tripForm
    .getByLabel("Van")
    .selectOption({ label: `${vehicleCode} — White Hiace ${suffix}` });
  // A week out, so that a booking's deadline is always the two-hour payment
  // window rather than `departureAt - departure-cutoff`, and the live expiry
  // sweep can never reach a booking made during a test.
  const departure = departureInDays(7);
  await tripForm.getByLabel("Date").fill(departure.date);
  await tripForm.getByLabel("Time").fill(departure.time);
  await tripForm.getByRole("button", { name: "Schedule trip" }).click();
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
