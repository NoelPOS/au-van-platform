import { cleanup, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  openSlip,
  proof,
  renderPage,
  stubApi,
  stubObjectUrls,
} from "../test/paymentFixtures";
import { json } from "../test/renderAdminPage";

const reused = {
  ...proof,
  sameSlipBookings: [
    { bookingId: "booking-9", bookingReference: "AUV-260920-4HPW8ZQT" },
    { bookingId: "booking-7", bookingReference: "AUV-260919-Q3MXK2RA" },
  ],
};

function queueRow() {
  return within(screen.getByRole("list", { name: "Slips waiting for review" })).getByRole(
    "listitem",
  );
}

describe("AdminPaymentReviewPage reused slips", () => {
  beforeEach(stubObjectUrls);

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("warns that the slip was also sent for other bookings and leaves the decision open", async () => {
    stubApi({ queue: () => json([reused]) });

    renderPage();
    await openSlip(proof.bookingReference);

    expect(screen.getByRole("note")).toHaveTextContent(
      "Possible reused slip — also sent for AUV-260920-4HPW8ZQT, AUV-260919-Q3MXK2RA.",
    );
    expect(queueRow()).toHaveTextContent("Possible reuse");
    expect(screen.getByRole("button", { name: "Approve payment" })).toBeEnabled();
  });

  it("says nothing about reuse when no other booking sent the same slip", async () => {
    stubApi();

    renderPage();
    await openSlip(proof.bookingReference);

    expect(await screen.findByRole("button", { name: "Approve payment" })).toBeEnabled();
    expect(screen.queryByRole("note")).not.toBeInTheDocument();
    expect(screen.queryByText(/reuse/i)).not.toBeInTheDocument();
    expect(queueRow()).not.toHaveTextContent("Possible reuse");
  });
});
