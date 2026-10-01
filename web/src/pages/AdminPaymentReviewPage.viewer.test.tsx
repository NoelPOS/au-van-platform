import { cleanup, fireEvent, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  openSlip,
  proof,
  renderPage,
  stubApi,
  stubObjectUrls,
} from "../test/paymentFixtures";

const reference = proof.bookingReference;

async function enlarge() {
  await openSlip(reference);
  const trigger = await screen.findByRole("button", { name: "Enlarge the slip" });
  trigger.focus();
  fireEvent.click(trigger);
  return screen.getByRole("dialog", {
    name: `Payment slip for booking ${reference} at full size`,
  });
}

describe("AdminPaymentReviewPage viewing and deciding", () => {
  beforeEach(stubObjectUrls);

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("enlarges the slip and hands focus back when Escape closes it", async () => {
    stubApi();

    renderPage();
    const viewer = await enlarge();

    expect(within(viewer).getByRole("img")).toHaveAttribute("src", "blob:the-slip");
    expect(within(viewer).getByRole("button", { name: "Close" })).toHaveFocus();
    expect(fireEvent.keyDown(viewer, { key: "Tab" })).toBe(false);

    fireEvent.keyDown(viewer, { key: "Escape" });

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Enlarge the slip" })).toHaveFocus();
  });

  it("closes the enlarged slip from its close button", async () => {
    stubApi();

    renderPage();
    const viewer = await enlarge();
    fireEvent.click(within(viewer).getByRole("button", { name: "Close" }));

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("sends a slip back with a common reason picked from the list", async () => {
    const fetcher = stubApi();

    renderPage();
    await openSlip(reference);
    fireEvent.click(screen.getByRole("button", { name: "Wrong amount" }));

    expect(screen.getByLabelText("Note to the student")).toHaveValue(
      "The amount on the slip does not match the fare.",
    );
    fireEvent.click(screen.getByRole("button", { name: "Send back to student" }));

    await vi.waitFor(() =>
      expect(fetcher).toHaveBeenCalledWith(
        "/api/v1/admin/payment-proofs/proof-1/reject",
        expect.objectContaining({
          body: JSON.stringify({
            note: "The amount on the slip does not match the fare.",
          }),
        }),
      ),
    );
  });

  it("marks the open slip in the queue and gives the queue back on a phone", async () => {
    stubApi();

    renderPage();
    await openSlip(reference);

    const row = screen.getByRole("button", { name: new RegExp(reference) });
    expect(row).toHaveAttribute("aria-current", "true");
    expect(screen.getByRole("region", { name: "Review queue" })).toHaveClass("hidden");

    fireEvent.click(screen.getByRole("button", { name: "Back to the queue" }));

    expect(screen.queryByRole("heading", { name: reference })).not.toBeInTheDocument();
    expect(row).not.toHaveAttribute("aria-current");
    expect(screen.getByRole("region", { name: "Review queue" })).not.toHaveClass("hidden");
  });
});
