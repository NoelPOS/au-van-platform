import { expect, test, type Browser, type Page } from "@playwright/test";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import {
  boardingPass,
  bookSeat,
  chooseTrip,
  findDeparture,
  openTicket,
  createTrip,
  goToPaymentReview,
  openProof,
  signInAsAdmin,
  signInAsStudent,
  studentSubject,
} from "./helpers";

const slip = join(
  dirname(fileURLToPath(import.meta.url)),
  "fixtures",
  "payment-slip.png",
);

async function administrator(browser: Browser): Promise<Page> {
  const page = await (await browser.newContext()).newPage();
  await signInAsAdmin(page);
  return page;
}

async function submitProof(
  page: Page,
  file: Parameters<ReturnType<Page["getByLabel"]>["setInputFiles"]>[0],
): Promise<void> {
  await page.getByLabel("Payment slip image").setInputFiles(file);
  const send = page.getByRole("button", { name: "Send slip" });
  if (await send.isVisible()) await send.click();
}

test.describe("the student journey", () => {
  test("books a seat, sends a payment slip, and sees the booking confirmed once an administrator approves it", async ({
    page,
    browser,
  }) => {
    const admin = await administrator(browser);
    const trip = await createTrip(admin, ["A1", "A2", "A3", "A4"]);

    const subject = studentSubject();
    await signInAsStudent(page, subject);
    await expect(await findDeparture(page, trip)).toHaveAccessibleName(
      /4 of 4 seats left/,
    );

    const reference = await bookSeat(page, trip, "A1");
    const pass = boardingPass(page, reference);
    await expect(pass).toContainText("Awaiting payment");
    await expect(pass).toContainText("A1");
    await goToPaymentReview(admin);

    await submitProof(page, slip);
    await expect(
      page.getByText("Payment slip received.", { exact: false }),
    ).toBeVisible();
    await expect(pass).toContainText("In review");
    await expect(page.getByRole("heading", { name: "Slip received" })).toBeVisible();

    // No reload: only the live stream can bring the slip into the open queue.
    await expect(admin.getByRole("button", { name: reference })).toBeVisible({ timeout: 5_000 });
    await openProof(admin, reference);
    await admin.getByLabel("Note to the student").fill("Slip checked, amount matches.");
    await admin.getByRole("button", { name: "Approve payment" }).click();
    await expect(admin.getByRole("button", { name: reference })).toHaveCount(0);

    // No reload: the live stream must beat the ticket's 15-second poll.
    await expect(pass).toContainText("Confirmed", { timeout: 5_000 });

    await admin.context().close();
  });

  test("shows a student why a payment slip was rejected, and lets them send another", async ({
    page,
    browser,
  }) => {
    const admin = await administrator(browser);
    const trip = await createTrip(admin, ["A1", "A2"]);

    const subject = studentSubject();
    await signInAsStudent(page, subject);
    const reference = await bookSeat(page, trip, "A1");
    await submitProof(page, slip);
    await expect(boardingPass(page, reference)).toContainText("In review");

    await goToPaymentReview(admin);
    await openProof(admin, reference);
    await admin.getByRole("button", { name: "Send back to student" }).click();
    await expect(admin.getByRole("alert")).toContainText(
      "Say why you are sending it back, so the student can fix it.",
    );

    await admin
      .getByLabel("Note to the student")
      .fill("The slip shows 100 THB and the fare is 120 THB.");
    await admin.getByRole("button", { name: "Send back to student" }).click();
    await expect(admin.getByRole("button", { name: reference })).toHaveCount(0);

    // Sessions live only in memory, so this page load shows the rejected ticket afresh.
    await signInAsStudent(page, subject);
    await openTicket(page, reference);
    await expect(boardingPass(page, reference)).toContainText("Sent back");
    await expect(
      page.getByText("The slip shows 100 THB and the fare is 120 THB."),
    ).toBeVisible();
    await expect(page.getByLabel("Payment slip image")).toBeAttached();

    await admin.context().close();
  });

  test("refuses a payment slip that is not an image or is over the ceiling, and keeps the booking payable", async ({
    page,
    browser,
  }) => {
    const admin = await administrator(browser);
    const trip = await createTrip(admin, ["A1", "A2"]);
    await admin.context().close();

    await signInAsStudent(page, studentSubject());
    const reference = await bookSeat(page, trip, "A1");

    await submitProof(page, {
      name: "receipt.txt",
      mimeType: "text/plain",
      buffer: Buffer.from("paid, honestly"),
    });
    await expect(page.getByRole("alert").filter({ hasText: "JPEG" })).toContainText(
      "A payment proof must be a JPEG, PNG, or WebP image.",
    );
    await expect(boardingPass(page, reference)).toContainText("Awaiting payment");

    await submitProof(page, {
      name: "huge.png",
      mimeType: "image/png",
      buffer: Buffer.concat([readFileSync(slip), Buffer.alloc(6 * 1024 * 1024)]),
    });
    await expect(page.getByRole("alert").filter({ hasText: "5MB" })).toContainText(
      "A payment proof must be 5MB or smaller.",
    );
    await expect(boardingPass(page, reference)).toContainText("Awaiting payment");

    await submitProof(page, slip);
    await expect(boardingPass(page, reference)).toContainText("In review");
  });

  test("refuses a second student the seat another student is already holding", async ({
    page,
    browser,
  }) => {
    const admin = await administrator(browser);
    const trip = await createTrip(admin, ["A1", "A2"]);
    await admin.context().close();

    const second = await (await browser.newContext()).newPage();
    await signInAsStudent(second, studentSubject());
    await chooseTrip(second, trip);
    await second.getByRole("button", { name: "Seat A1, available" }).click();

    await signInAsStudent(page, studentSubject());
    await chooseTrip(page, trip);
    await page.getByRole("button", { name: "Seat A1, available" }).click();
    await page.getByRole("button", { name: "Hold seats" }).click();
    await expect(page.getByRole("heading", { name: "Passenger details" })).toBeVisible();

    // A seat-map poll may already have dropped the seat, which disables the button.
    const hold = second.getByRole("button", { name: "Hold seats" });
    if (await hold.isEnabled()) await hold.click();

    await expect(
      second.getByRole("button", { name: "Seat A1, held by someone else" }),
    ).toBeVisible();
    await expect(
      second.getByRole("heading", { name: "Passenger details" }),
    ).toHaveCount(0);
    await expect(
      second.getByRole("button", { name: "Seat A2, available" }),
    ).toBeEnabled();

    await second.context().close();
  });

  test("sends a student back to the seat map when their hold runs out", async ({
    page,
    browser,
  }) => {
    const admin = await administrator(browser);
    const trip = await createTrip(admin, ["A1", "A2"]);
    await admin.context().close();

    await signInAsStudent(page, studentSubject());
    await chooseTrip(page, trip);
    await page.getByRole("button", { name: "Seat A1, available" }).click();
    await page.getByRole("button", { name: "Hold seats" }).click();
    await expect(page.getByRole("timer")).toContainText("Seats held for 0:");
    await page.getByLabel("Full name").fill("Somchai P.");
    await page.getByLabel("Phone number").fill("0812345678");

    // The e2e overlay sets booking.hold-ttl to 40 seconds.
    await expect(
      page.getByText("Your seat hold expired. Choose your seats again."),
    ).toBeVisible({ timeout: 60_000 });
    await expect(page.getByRole("heading", { name: "Passenger details" })).toHaveCount(0);

    await expect(
      page.getByRole("button", { name: "Seat A1, available" }),
    ).toBeEnabled();
  });
});
