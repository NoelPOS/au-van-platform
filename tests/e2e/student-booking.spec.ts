import { expect, test, type Browser, type Page } from "@playwright/test";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import {
  backToTrips,
  bookSeat,
  bookingCard,
  chooseTrip,
  createTrip,
  goToPaymentReview,
  openProof,
  signInAsAdmin,
  signInAsStudent,
  studentSubject,
  type TripFixture,
} from "./helpers";

const slip = join(
  dirname(fileURLToPath(import.meta.url)),
  "fixtures",
  "payment-slip.png",
);

/**
 * An administrator page in its own context, because half of these tests need
 * one acting while a student is mid-journey and a session lives in React state
 * rather than in storage.
 */
async function administrator(browser: Browser): Promise<Page> {
  const page = await (await browser.newContext()).newPage();
  await signInAsAdmin(page);
  return page;
}

async function submitProof(
  page: Page,
  reference: string,
  file: Parameters<ReturnType<Page["getByLabel"]>["setInputFiles"]>[0],
): Promise<void> {
  const card = bookingCard(page, reference);
  await card.getByLabel("Upload your payment slip").setInputFiles(file);
  await card.getByRole("button", { name: "Send payment proof" }).click();
}

test.describe("the student journey", () => {
  /**
   * The spine: browse, hold, confirm, pay, and be told what happened — the same
   * four screens the legacy application's own LIFF route set named as the ones
   * that mattered (`routes`, `book`, `payment`, `mybookings`).
   *
   * <p>Every assertion is about what the student sees. The one exception is the
   * booking reference, which is read off the creation response because the
   * administrator's queue is keyed on it.
   */
  test("books a seat, sends a payment slip, and sees the booking confirmed once an administrator approves it", async ({
    page,
    browser,
  }) => {
    const admin = await administrator(browser);
    const trip = await createTrip(admin, ["A1", "A2", "A3", "A4"]);

    const subject = studentSubject();
    await signInAsStudent(page, subject);
    await expect(page.getByRole("button", { name: trip.routeLabel })).toContainText(
      "4 of 4 seats free",
    );

    const reference = await bookSeat(page, trip, "A1");
    await backToTrips(page);

    const card = bookingCard(page, reference);
    await expect(card).toContainText("PENDING_PAYMENT");
    await expect(card).toContainText("Seat A1");

    await submitProof(page, reference, slip);
    await expect(
      page.getByText("Payment proof received.", { exact: false }),
    ).toBeVisible();
    await expect(card).toContainText("PAYMENT_UNDER_REVIEW");
    await expect(card).toContainText(
      "Your payment proof is with an administrator.",
    );

    await goToPaymentReview(admin);
    await openProof(admin, reference);
    await admin.getByLabel("Note to the student").fill("Slip checked, amount matches.");
    await admin.getByRole("button", { name: "Approve payment" }).click();
    await expect(admin.getByRole("button", { name: reference })).toHaveCount(0);

    // Signing in again rather than reloading: a reload drops the session, and
    // nothing on the student's page polls the booking list.
    await signInAsStudent(page, subject);
    await expect(bookingCard(page, reference)).toContainText("CONFIRMED");

    await admin.context().close();
  });

  /**
   * The rejection half, which is the one a student actually has to act on: the
   * reason has to reach them, and the booking has to be payable again.
   */
  test("shows a student why a payment slip was rejected, and lets them send another", async ({
    page,
    browser,
  }) => {
    const admin = await administrator(browser);
    const trip = await createTrip(admin, ["A1", "A2"]);

    const subject = studentSubject();
    await signInAsStudent(page, subject);
    const reference = await bookSeat(page, trip, "A1");
    await backToTrips(page);
    await submitProof(page, reference, slip);
    await expect(bookingCard(page, reference)).toContainText(
      "PAYMENT_UNDER_REVIEW",
    );

    await goToPaymentReview(admin);
    await openProof(admin, reference);
    // Refused before anything goes out: the student is being asked to send a
    // better slip and cannot without being told why.
    await admin.getByRole("button", { name: "Reject payment" }).click();
    await expect(admin.getByRole("alert")).toContainText(
      "Say why the slip was rejected, so the student can fix it.",
    );

    await admin
      .getByLabel("Note to the student")
      .fill("The slip shows 100 THB and the fare is 120 THB.");
    await admin.getByRole("button", { name: "Reject payment" }).click();
    await expect(admin.getByRole("button", { name: reference })).toHaveCount(0);

    await signInAsStudent(page, subject);
    const card = bookingCard(page, reference);
    await expect(card).toContainText("PAYMENT_REJECTED");
    await expect(card).toContainText(
      "The slip shows 100 THB and the fare is 120 THB.",
    );
    await expect(
      card.getByRole("button", { name: "Send payment proof" }),
    ).toBeVisible();

    await admin.context().close();
  });

  /**
   * A slip the API will not take. Both refusals are the documented ones —
   * `payment_proof_type_not_supported` and `payment_proof_too_large` — and the
   * booking survives both, which is the part worth proving: the upload keeps
   * the student where they are, and a third, valid slip still works.
   */
  test("refuses a payment slip that is not an image or is over the ceiling, and keeps the booking payable", async ({
    page,
    browser,
  }) => {
    const admin = await administrator(browser);
    const trip = await createTrip(admin, ["A1", "A2"]);
    await admin.context().close();

    await signInAsStudent(page, studentSubject());
    const reference = await bookSeat(page, trip, "A1");
    await backToTrips(page);

    await submitProof(page, reference, {
      name: "receipt.txt",
      mimeType: "text/plain",
      buffer: Buffer.from("paid, honestly"),
    });
    await expect(page.getByRole("alert")).toContainText(
      "A payment proof must be a JPEG, PNG, or WebP image.",
    );
    await expect(bookingCard(page, reference)).toContainText("PENDING_PAYMENT");

    // Over payment-proof.max-file-size (5MB) and under the multipart backstop
    // (8MB), so the application's own validation is what refuses it.
    await submitProof(page, reference, {
      name: "huge.png",
      mimeType: "image/png",
      buffer: Buffer.concat([readFileSync(slip), Buffer.alloc(6 * 1024 * 1024)]),
    });
    await expect(page.getByRole("alert")).toContainText(
      "A payment proof must be 5MB or smaller.",
    );
    await expect(bookingCard(page, reference)).toContainText("PENDING_PAYMENT");

    await submitProof(page, reference, slip);
    await expect(bookingCard(page, reference)).toContainText(
      "PAYMENT_UNDER_REVIEW",
    );
  });

  /**
   * Two students for one seat. `StudentBookingPage` derives availability from
   * the latest seat-map poll on every render, so there are two honest outcomes
   * depending on whether the poll or the click lands first: the seat is pruned
   * out of the selection, or the hold itself is refused. Both are the same
   * product promise, and the assertion is the promise rather than the timing —
   * the second student does not get the seat and does not reach the passenger
   * form.
   */
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
    await page.getByRole("button", { name: "Hold these seats" }).click();
    await expect(page.getByRole("heading", { name: "Passenger details" })).toBeVisible();

    const hold = second.getByRole("button", { name: "Hold these seats" });
    if (await hold.isEnabled()) await hold.click();

    await expect(
      second.getByRole("button", { name: "Seat A1, held by someone else" }),
    ).toBeVisible();
    await expect(
      second.getByRole("heading", { name: "Passenger details" }),
    ).toHaveCount(0);
    // The seat beside it is still free, so the refusal is about the seat rather
    // than about the trip.
    await expect(
      second.getByRole("button", { name: "Seat A2, available" }),
    ).toBeEnabled();

    await second.context().close();
  });

  /**
   * A hold that lapses. `booking.hold-ttl` is forty seconds in the overlay
   * rather than the five minutes a real student gets, so this waits out a real
   * expiry instead of asserting about a timer.
   */
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
    await page.getByRole("button", { name: "Hold these seats" }).click();
    await expect(page.getByRole("timer")).toContainText("Seats held for 0:");
    await page.getByLabel("Full name").fill("Somchai P.");
    await page.getByLabel("Phone number").fill("0812345678");

    // Scoped by its text: `SeatSelectionSection` keeps a second, empty
    // `role="status"` region mounted for the seats it loses to the poll.
    await expect(
      page.getByText("Your seat hold expired. Choose your seats again."),
    ).toBeVisible({ timeout: 60_000 });
    await expect(page.getByRole("heading", { name: "Passenger details" })).toHaveCount(0);

    // And the seat is free again, because a lapsed hold stops blocking it with
    // no code running at that moment (ADR-006's lazy expiry).
    await expect(
      page.getByRole("button", { name: "Seat A1, available" }),
    ).toBeEnabled();
  });
});
