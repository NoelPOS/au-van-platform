import { expect, test } from "@playwright/test";
import {
  adminSubject,
  createTrip,
  goToOperations,
  signIn,
  signInAsAdmin,
  studentSubject,
} from "./helpers";

/**
 * The administrator journey: sign in, build the inventory a booking needs, and
 * read the result back on the operations screen.
 */
test.describe("the administrator journey", () => {
  test("builds a route, a seat layout, a van and a departure, and reads the trip back in operations", async ({
    page,
  }) => {
    await signInAsAdmin(page);

    const trip = await createTrip(page, ["A1", "A2", "A3", "A4"]);

    // The trip row carries the seat count the layout gave it, which is the one
    // number that proves the four sections were joined up rather than each
    // having merely accepted a form.
    const row = page.getByRole("row", { name: new RegExp(trip.vehicleCode) });
    await expect(row).toContainText(trip.routeLabel);
    await expect(row).toContainText("4");

    await goToOperations(page);
    await page.getByRole("button", { name: trip.routeLabel }).click();
    await expect(
      page.getByRole("heading", { name: new RegExp(trip.routeLabel) }),
    ).toBeVisible();
    await expect(page.getByText("0 of 4 seats claimed")).toBeVisible();
    await expect(page.getByText("Nobody is waiting")).toBeVisible();

    // Nothing was given up on, which is the overlay's LINE_MESSAGING_ENABLED=false
    // doing its job: with a sender configured against a channel that does not
    // exist, every notification this run produced would be a dead letter here.
    await expect(page.getByText("Nothing was given up on")).toBeVisible();
  });

  test("opens an administration page from its own address and goes back to it", async ({
    page,
  }) => {
    await signIn(page, adminSubject, "/admin/payments");
    await expect(page.getByRole("heading", { name: "Payment review" })).toBeVisible();
    await expect(page).toHaveURL(/\/admin\/payments$/);

    await goToOperations(page);
    await expect(page).toHaveURL(/\/admin\/operations$/);
    await page.goBack();
    await expect(page.getByRole("heading", { name: "Payment review" })).toBeVisible();
    await expect(page).toHaveURL(/\/admin\/payments$/);
  });

  test("sends an old inventory link on to the trips page", async ({ page }) => {
    await signIn(page, adminSubject, "/admin/inventory");
    await expect(page.getByRole("heading", { name: "Trips", level: 1 })).toBeVisible();
    await expect(page).toHaveURL(/\/admin\/trips$/);
  });

  test("sends a student who opens an administration address to the booking page", async ({
    page,
  }) => {
    await signIn(page, studentSubject(), "/admin/operations");
    await expect(page.getByRole("heading", { name: "Book a seat" })).toBeVisible();
    await expect(page).toHaveURL(/\/$/);
    await expect(
      page.getByRole("navigation", { name: "Administration" }),
    ).toHaveCount(0);
  });

  /**
   * The failure path that matters most on this screen.
   *
   * <p>`SecurityConfiguration`'s `/api/v1/admin/**` matcher is the only thing
   * enforcing `ROLE_ADMIN` on these paths. `OperationsIntegrationTests` covers
   * it at the API layer; this covers it against the deployed stack, with a token
   * minted the way a real student's is.
   *
   * <p>The administrator's own 200 on the same paths is asserted alongside, so
   * a 403 caused by the path simply not existing cannot pass as authorization.
   */
  test("refuses a student's token the administrator's operational data", async ({
    request,
  }) => {
    async function tokenFor(subject: string): Promise<string> {
      const response = await request.post("/api/v1/auth/line/exchange", {
        data: { idToken: subject },
      });
      expect(response.ok()).toBeTruthy();
      return ((await response.json()) as { accessToken: string }).accessToken;
    }

    const student = await tokenFor(studentSubject());
    const administrator = await tokenFor(adminSubject);
    const adminOnly = [
      "/api/v1/admin/operations/dead-letters",
      "/api/v1/admin/payment-proofs",
      "/api/v1/admin/routes",
      "/api/v1/admin/access-check",
    ];

    for (const path of adminOnly) {
      const refused = await request.get(path, {
        headers: { Authorization: `Bearer ${student}` },
      });
      expect(refused.status(), `${path} with a student token`).toBe(403);

      const allowed = await request.get(path, {
        headers: { Authorization: `Bearer ${administrator}` },
      });
      expect(allowed.ok(), `${path} with an administrator token`).toBeTruthy();
    }

    // And with no token at all, so the 403 above is authorization rather than
    // the endpoint being open and answering oddly.
    const anonymous = await request.get("/api/v1/admin/operations/dead-letters");
    expect(anonymous.status()).toBe(401);
  });
});
