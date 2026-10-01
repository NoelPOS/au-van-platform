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
  fireEvent.click(await screen.findByRole("button", { name: "Enlarge the slip" }));
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

  it("enlarges the slip in a modal and drops it when the dialog is dismissed", async () => {
    stubApi();

    renderPage();
    const viewer = await enlarge();

    expect(viewer).toHaveAttribute("open");
    expect(within(viewer).getByRole("img")).toHaveAttribute("src", "blob:the-slip");

    fireEvent(viewer, new Event("close"));

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
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
    expect(await screen.findByText(`${reference} sent back to the student`)).toBeInTheDocument();
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

  it("makes the whole ticket, stub included, the row's one click target", async () => {
    stubApi();

    renderPage();
    const row = await screen.findByRole("button", { name: reference });
    const ticket = row.closest("article") as HTMLElement;

    expect(row).toHaveClass("after:absolute", "after:inset-0");
    expect(ticket).toHaveClass("relative");
    for (let node = row.parentElement; node !== ticket; node = node!.parentElement)
      expect(node).not.toHaveClass("relative");
    expect(within(ticket).getAllByRole("button")).toEqual([row]);
  });
});
