import { cleanup, fireEvent, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  booking,
  expiredBooking,
  rejectedBooking,
  json,
} from "../test/bookingFixtures";
import { stubApi, renderPage } from "../test/renderStudentApp";
import { deadlinePhrase } from "../utils/days";

const slip = new File(["slip-bytes"], "slip.jpg", { type: "image/jpeg" });

function chooseSlip(file: File) {
  fireEvent.change(screen.getByLabelText("Payment slip image"), {
    target: { files: [file] },
  });
}

describe("Student ticket", () => {
  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("sends a payment slip as multipart and lets the browser set the boundary", async () => {
    let uploaded = 0;
    const fetcher = stubApi({
      bookings: () =>
        json([uploaded === 0 ? booking : { ...booking, status: "PAYMENT_UNDER_REVIEW" }]),
      paymentProof: () => {
        uploaded += 1;
        return json({ ...booking, status: "PAYMENT_UNDER_REVIEW" });
      },
    });

    renderPage("/tickets/booking-1");
    await screen.findByRole("heading", { name: "Pay to keep your seats" });
    chooseSlip(slip);
    expect(screen.getByRole("img", { name: "Your payment slip" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Send slip" }));

    expect(
      await screen.findByText(
        "Payment slip received. Staff confirm the booking once they have checked it.",
      ),
    ).toBeInTheDocument();
    const call = fetcher.mock.calls.find(([url]) =>
      String(url).endsWith("/payment-proof"),
    );
    expect(call?.[0]).toBe("/api/v1/bookings/booking-1/payment-proof");
    const init = call?.[1] as RequestInit;
    expect(init.method).toBe("POST");
    const headers = new Headers(init.headers);
    expect(headers.get("Content-Type")).toBeNull();
    expect(headers.get("Authorization")).toBe("Bearer student-token");
    expect((init.body as FormData).get("file")).toBe(slip);
    expect(
      await screen.findByRole("heading", { name: "Slip received" }),
    ).toBeInTheDocument();
    expect(screen.queryByLabelText("Payment slip image")).not.toBeInTheDocument();
  });

  it("refuses a file that is not an image before uploading anything", async () => {
    const fetcher = stubApi({ bookings: () => json([booking]) });

    renderPage("/tickets/booking-1");
    await screen.findByRole("heading", { name: "Pay to keep your seats" });
    chooseSlip(new File(["x"], "slip.pdf", { type: "application/pdf" }));

    expect(
      screen.getByText("A payment proof must be a JPEG, PNG, or WebP image."),
    ).toHaveAttribute("role", "alert");
    expect(screen.queryByRole("button", { name: "Send slip" })).not.toBeInTheDocument();
    expect(
      fetcher.mock.calls.some(([url]) => String(url).endsWith("/payment-proof")),
    ).toBe(false);
  });

  it("refuses a slip over 5MB before uploading anything", async () => {
    stubApi({ bookings: () => json([booking]) });

    renderPage("/tickets/booking-1");
    await screen.findByRole("heading", { name: "Pay to keep your seats" });
    chooseSlip(new File([new Uint8Array(5 * 1024 * 1024 + 1)], "big.png", { type: "image/png" }));

    expect(
      screen.getByText("A payment proof must be 5MB or smaller."),
    ).toBeInTheDocument();
  });

  it("reports the reason the API refused a slip and keeps the slip ready to resend", async () => {
    stubApi({
      bookings: () => json([booking]),
      paymentProof: () =>
        json({ detail: "The payment proof could not be stored. Please try again." }, 503),
    });

    renderPage("/tickets/booking-1");
    await screen.findByRole("heading", { name: "Pay to keep your seats" });
    chooseSlip(slip);
    fireEvent.click(screen.getByRole("button", { name: "Send slip" }));

    expect(
      await screen.findByText("The payment proof could not be stored. Please try again."),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Send slip" })).toBeEnabled();
  });

  it("tells the student why a slip was sent back and takes another one", async () => {
    const fetcher = stubApi({ bookings: () => json([rejectedBooking]) });

    renderPage("/tickets/booking-1");

    expect(
      await screen.findByText("The slip is too blurred to read."),
    ).toBeInTheDocument();
    expect(screen.getByText("Sent back")).toHaveClass("text-danger");
    chooseSlip(slip);
    fireEvent.click(screen.getByRole("button", { name: "Send slip" }));

    await vi.waitFor(() =>
      expect(fetcher).toHaveBeenCalledWith(
        "/api/v1/bookings/booking-1/payment-proof",
        expect.objectContaining({ method: "POST" }),
      ),
    );
  });

  it("names the payment deadline rather than the departure", async () => {
    stubApi({ bookings: () => json([booking]) });

    renderPage("/tickets/booking-1");

    expect(
      await screen.findByText(
        `Send your payment slip by ${deadlinePhrase(booking.paymentDeadlineAt)}, or the seats go back on sale.`,
      ),
    ).toBeInTheDocument();
  });

  it("stamps an approved booking as confirmed", async () => {
    stubApi({ bookings: () => json([{ ...booking, status: "CONFIRMED" }]) });

    renderPage("/tickets/booking-1");

    expect(await screen.findByText("Confirmed")).toHaveClass("text-success");
    expect(screen.getByRole("heading", { name: "You’re all set" })).toBeInTheDocument();
    expect(screen.queryByLabelText("Payment slip image")).not.toBeInTheDocument();
  });

  it("says a cancelled booking expired rather than leaving the student guessing", async () => {
    stubApi({ bookings: () => json([{ ...expiredBooking, id: "booking-1" }]) });

    renderPage("/tickets/booking-1");

    expect(
      await screen.findByRole("heading", { name: "This booking expired" }),
    ).toBeInTheDocument();
    expect(screen.getByText("Expired")).toBeInTheDocument();
    expect(screen.queryByLabelText("Payment slip image")).not.toBeInTheDocument();
  });

  it("does not claim a booking the student cancelled themselves expired", async () => {
    stubApi({
      bookings: () =>
        json([{ ...booking, status: "CANCELLED", paymentDeadlineAt: null }]),
    });

    renderPage("/tickets/booking-1");

    expect(
      await screen.findByRole("heading", { name: "This booking was cancelled" }),
    ).toBeInTheDocument();
    expect(screen.queryByText("Expired")).not.toBeInTheDocument();
  });

  it("says so when the ticket does not exist", async () => {
    stubApi({ bookings: () => json([]) });

    renderPage("/tickets/nope");

    expect(await screen.findByText("We could not find that ticket")).toBeInTheDocument();
  });
});
