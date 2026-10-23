import { expect, test } from "@playwright/test";
import {
  adminSubject,
  createTrip,
  goToOverview,
  signIn,
  signInAsAdmin,
  studentSubject,
} from "./helpers";

test.describe("the administrator journey", () => {
  test("builds a route, a seat layout, a van and a departure, and reads its passengers back", async ({
    page,
  }) => {
    await signInAsAdmin(page);

    const trip = await createTrip(page, ["A1", "A2", "A3", "A4"]);

    const row = page
      .getByRole("table", { name: `${trip.routeLabel} on` })
      .getByRole("row", { name: new RegExp(trip.vehicleCode) });
    await expect(row).toContainText("4");

    await row.getByRole("button", { name: /^Open trip on/ }).click();
    const drawer = page.getByRole("dialog");
    await expect(drawer.getByText(trip.routeLabel)).toBeVisible();
    await expect(drawer.getByRole("radio", { name: "Passengers" })).toBeChecked();
    await expect(drawer.getByText("0 of 4 seats claimed")).toBeVisible();
    await expect(drawer.getByText("Nobody is waiting")).toBeVisible();
    await drawer.getByRole("button", { name: "Close" }).click();

    await goToOverview(page);
    // Empty only because the e2e overlay sets LINE_MESSAGING_ENABLED=false.
    await expect(page.getByText("Nothing was given up on")).toBeVisible();
  });

  test("opens an administration page from its own address and goes back to it", async ({
    page,
  }) => {
    await signIn(page, adminSubject, "/admin/payments");
    await expect(page.getByRole("heading", { name: "Payment review" })).toBeVisible();
    await expect(page).toHaveURL(/\/admin\/payments$/);

    await goToOverview(page);
    await expect(page).toHaveURL(/\/admin$/);
    await page.goBack();
    await expect(page.getByRole("heading", { name: "Payment review" })).toBeVisible();
    await expect(page).toHaveURL(/\/admin\/payments$/);
  });

  for (const address of ["inventory", "operations"]) {
    test(`sends an old ${address} link on to the trips page`, async ({ page }) => {
      await signIn(page, adminSubject, `/admin/${address}`);
      await expect(page.getByRole("heading", { name: "Trips", level: 1 })).toBeVisible();
      await expect(page).toHaveURL(/\/admin\/trips$/);
    });
  }

  test("sends a student who opens an administration address to the booking page", async ({
    page,
  }) => {
    await signIn(page, studentSubject(), "/admin/trips");
    await expect(page.getByRole("heading", { name: "Catch the next van" })).toBeVisible();
    await expect(page).toHaveURL(/\/$/);
    await expect(
      page.getByRole("navigation", { name: "Administration" }),
    ).toHaveCount(0);
  });

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

    const anonymous = await request.get("/api/v1/admin/operations/dead-letters");
    expect(anonymous.status()).toBe(401);
  });
});
