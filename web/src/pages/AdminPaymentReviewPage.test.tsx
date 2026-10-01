import { cleanup, fireEvent, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  openSlip,
  proof,
  renderPage,
  stubApi,
  stubObjectUrls,
} from "../test/paymentFixtures";
import { json } from "../test/renderAdminPage";

const reference = proof.bookingReference;

describe("AdminPaymentReviewPage", () => {
  beforeEach(stubObjectUrls);

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("lists what is waiting and fetches the slip with the administrator's token", async () => {
    const fetcher = stubApi();

    renderPage();
    await openSlip(reference);

    expect(await screen.findByAltText(/Payment slip for booking/)).toHaveAttribute(
      "src",
      "blob:the-slip",
    );
    const call = fetcher.mock.calls.find(([url]) => String(url).endsWith("/image"));
    expect(call?.[0]).toBe("/api/v1/admin/payment-proofs/proof-1/image");
    expect(new Headers(call?.[1]?.headers).get("Authorization")).toBe(
      "Bearer admin-token",
    );
    const row = within(screen.getByRole("list", { name: "Slips waiting for review" }))
      .getByRole("listitem");
    expect(row).toHaveTextContent(reference);
    expect(row).toHaveTextContent("Somchai P.");
    expect(row).toHaveTextContent("AU → Mega Bangna");
    expect(row).toHaveTextContent("35THB");
  });

  it("revokes the slip's object URL when the screen goes away", async () => {
    stubApi();

    const view = renderPage();
    await openSlip(reference);
    await screen.findByAltText(/Payment slip for booking/);
    view.unmount();

    expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:the-slip");
  });

  it("approves a slip and drops it from the queue", async () => {
    let decided = 0;
    const fetcher = stubApi({
      queue: () => json(decided === 0 ? [proof] : []),
      decision: () => {
        decided += 1;
        return json({ id: "booking-1", status: "CONFIRMED" });
      },
    });

    renderPage();
    await openSlip(reference);
    fireEvent.click(screen.getByRole("button", { name: "Approve payment" }));

    expect(await screen.findByText("Nothing to review")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(
      `Payment approved for ${reference}`,
    );
    const call = fetcher.mock.calls.find(([url]) =>
      String(url).includes("/approve"),
    );
    expect(call?.[0]).toBe("/api/v1/admin/payment-proofs/proof-1/approve");
    const init = call?.[1] as RequestInit;
    expect(init.method).toBe("POST");
    expect(init.body).toBe(JSON.stringify({ note: "" }));
  });

  it("refuses to send a slip back without a reason and sends the reason once it has one", async () => {
    const fetcher = stubApi();

    renderPage();
    await openSlip(reference);
    fireEvent.click(screen.getByRole("button", { name: "Send back to student" }));

    expect(
      await screen.findByText(
        "Say why you are sending it back, so the student can fix it.",
      ),
    ).toBeInTheDocument();
    expect(
      fetcher.mock.calls.filter(([url]) => String(url).includes("/reject")),
    ).toHaveLength(0);

    fireEvent.change(screen.getByLabelText("Note to the student"), {
      target: { value: "  The slip is too blurred to read.  " },
    });
    fireEvent.click(screen.getByRole("button", { name: "Send back to student" }));

    await vi.waitFor(() =>
      expect(fetcher).toHaveBeenCalledWith(
        "/api/v1/admin/payment-proofs/proof-1/reject",
        expect.objectContaining({
          method: "POST",
          body: JSON.stringify({ note: "The slip is too blurred to read." }),
        }),
      ),
    );
  });

  it("shows why the queue could not be loaded rather than an empty screen", async () => {
    stubApi({ queue: () => json({ detail: "Access denied." }, 403) });

    renderPage();

    expect(
      await screen.findByText("Could not load payment proofs"),
    ).toBeInTheDocument();
    expect(screen.getByText("Access denied.")).toBeInTheDocument();
  });

  it("keeps the slip open and reports a decision the API refused", async () => {
    stubApi({
      decision: () =>
        json(
          {
            detail: "That payment proof has already been reviewed.",
            code: "payment_proof_already_decided",
          },
          409,
        ),
    });

    renderPage();
    await openSlip(reference);
    fireEvent.click(screen.getByRole("button", { name: "Approve payment" }));

    expect(
      await screen.findByText("That payment proof has already been reviewed."),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Approve payment" }),
    ).toBeInTheDocument();
  });
});
