import { expect, type Page } from "@playwright/test";

import { adminSubject } from "./global-setup";

export { adminSubject };

let sequence = 0;

function uniqueSuffix(): string {
  sequence += 1;
  return `${Date.now().toString(36)}${sequence}`;
}

export function studentSubject(): string {
  return `e2e-student-${uniqueSuffix()}`;
}

const bangkokOffsetMs = 7 * 3_600_000;

function departureInDays(days: number): { dayChip: string; time: string } {
  const bangkok = new Date(Date.now() + days * 86_400_000 + bangkokOffsetMs);
  const dayChip = new Intl.DateTimeFormat("en-GB", {
    timeZone: "UTC",
    weekday: "short",
    day: "numeric",
  }).format(bangkok);
  return { dayChip, time: bangkok.toISOString().slice(11, 16) };
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
  await expect(
    page.getByRole("heading", { name: "Catch the next van" }),
  ).toBeVisible();
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
  departureName: string;
  vehicleCode: string;
  seatLabels: string[];
};

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
  await page.getByRole("button", { name: "Blank" }).click();
  await page.getByLabel("Columns").fill(String(seatLabels.length));
  for (const [index, label] of seatLabels.entries()) {
    await page.getByRole("button", { name: `Row 1, column ${index + 1}` }).click();
    await expect(
      page.getByRole("button", { name: `Row 1, column ${index + 1}, seat ${label}` }),
    ).toHaveAttribute("aria-pressed", "true");
  }
  await page.getByRole("button", { name: "Create layout" }).click();
  await expect(
    page.getByRole("list", { name: "Saved layouts" }).getByRole("heading", { name: layoutName }),
  ).toBeVisible();

  await adminDestination(page, "Vans").click();
  await page.getByRole("button", { name: "New van" }).click();
  const vanForm = page.getByRole("dialog", { name: "New van" });
  await vanForm.getByLabel("Van code").fill(vehicleCode);
  await vanForm.getByLabel("Name", { exact: true }).fill(`White Hiace ${suffix}`);
  await vanForm.getByRole("radio", { name: layoutName, exact: true }).check();
  await vanForm.getByRole("button", { name: "Add van" }).click();
  await expect(page.getByRole("cell", { name: vehicleCode, exact: true })).toBeVisible();

  await adminDestination(page, "Trips").click();
  await page.getByRole("button", { name: "New trip" }).click();
  const tripForm = page.getByRole("dialog", { name: "Schedule a trip" });
  await tripForm.getByRole("button", { name: /^Route/ }).click();
  await tripForm.getByRole("option", { name: routeLabel }).click();
  await tripForm.getByRole("button", { name: /^Van/ }).click();
  await tripForm
    .getByRole("option", { name: `${vehicleCode} — White Hiace ${suffix}` })
    .click();
  // A week out, so the payment deadline is the two-hour window and the expiry sweep never runs mid-test.
  const departure = departureInDays(7);
  await tripForm
    .getByRole("group", { name: "Days" })
    .getByRole("button", { name: departure.dayChip, exact: true })
    .click();
  await tripForm.getByLabel("Time", { exact: true }).fill(departure.time);
  await tripForm.getByRole("button", { name: "Schedule trip" }).click();
  await expect(tripForm).toBeHidden();
  await page.getByRole("radio", { name: "List" }).check();
  await expect(
    page.getByRole("row", { name: new RegExp(vehicleCode) }),
  ).toBeVisible();

  return {
    routeLabel,
    departureName: `${origin} to ${destination}`,
    vehicleCode,
    seatLabels,
  };
}

export async function findDeparture(page: Page, trip: TripFixture) {
  const departure = page.getByRole("button", { name: trip.departureName });
  await expect(page.getByRole("tab").first()).toBeVisible();
  for (const day of await page.getByRole("tab").all()) {
    await day.click();
    if (await departure.isVisible()) break;
  }
  return departure;
}

export async function chooseTrip(page: Page, trip: TripFixture): Promise<void> {
  await (await findDeparture(page, trip)).click();
  await expect(page.getByRole("group", { name: "Seat map" })).toBeVisible();
}

export async function bookSeat(
  page: Page,
  trip: TripFixture,
  seatLabel: string,
): Promise<string> {
  await chooseTrip(page, trip);
  await page.getByRole("button", { name: `Seat ${seatLabel}, available` }).click();
  await page.getByRole("button", { name: "Hold seats" }).click();
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

export function boardingPass(page: Page, reference: string) {
  return page.getByRole("article", { name: `Boarding pass ${reference}` });
}

export async function openTicket(page: Page, reference: string): Promise<void> {
  await page
    .getByRole("navigation", { name: "Student" })
    .getByRole("link", { name: /^Tickets/ })
    .click();
  await page.getByRole("link", { name: new RegExp(reference) }).click();
  await expect(boardingPass(page, reference)).toBeVisible();
}

export async function openProof(page: Page, reference: string): Promise<void> {
  const row = page.getByRole("button", { name: reference });
  await expect(row).toBeVisible();
  // Clicked away from the reference button, so the ticket itself must open the proof.
  await page
    .getByRole("listitem")
    .filter({ has: row })
    .click({ position: { x: 24, y: 24 } });
  await expect(page.getByRole("heading", { name: reference })).toBeVisible();
  await expect(
    page.getByRole("img", { name: `Payment slip for booking ${reference}` }),
  ).toBeVisible();
}
