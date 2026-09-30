import { cleanup, fireEvent, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  trip,
  booking,
  expiredBooking,
  rejectedBooking,
  json,
} from "../test/bookingFixtures";
import { stubApi, renderPage } from "../test/renderStudentBookingPage";
import { formatDeparture } from "../utils/format";

describe("StudentBookingPage", () => {
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
    const slip = new File(["slip-bytes"], "slip.jpg", { type: "image/jpeg" });

    renderPage();
    fireEvent.change(await screen.findByLabelText("Upload your payment slip"), {
      target: { files: [slip] },
    });
    fireEvent.click(screen.getByRole("button", { name: "Send payment proof" }));

    expect(
      await screen.findByText(
        "Payment proof received. Staff confirm the booking once they have checked it.",
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
    expect(init.body).toBeInstanceOf(FormData);
    expect((init.body as FormData).get("file")).toBe(slip);
    expect(
      await screen.findByText(
        "Your payment proof is with an administrator. This booking is confirmed once they approve it.",
      ),
    ).toBeInTheDocument();
    expect(
      screen.queryByLabelText("Upload your payment slip"),
    ).not.toBeInTheDocument();
  });

  it("tells the student why a slip was rejected and takes another one", async () => {
    const fetcher = stubApi({
      bookings: () => json([rejectedBooking]),
      trips: () => json([]),
    });

    renderPage();

    expect(
      await screen.findByText("The slip is too blurred to read."),
    ).toBeInTheDocument();
    expect(screen.getByText("PAYMENT_REJECTED")).toHaveClass("text-red-700");

    fireEvent.change(screen.getByLabelText("Upload your payment slip"), {
      target: {
        files: [new File(["clearer"], "slip.jpg", { type: "image/jpeg" })],
      },
    });
    fireEvent.click(screen.getByRole("button", { name: "Send payment proof" }));

    await vi.waitFor(() =>
      expect(fetcher).toHaveBeenCalledWith(
        "/api/v1/bookings/booking-1/payment-proof",
        expect.objectContaining({ method: "POST" }),
      ),
    );
  });

  it("reports the reason a payment slip was refused and keeps the form", async () => {
    stubApi({
      bookings: () => json([booking]),
      paymentProof: () =>
        json(
          {
            detail: "A payment proof must be a JPEG, PNG, or WebP image.",
            code: "payment_proof_type_not_supported",
          },
          400,
        ),
    });

    renderPage();
    fireEvent.change(await screen.findByLabelText("Upload your payment slip"), {
      target: { files: [new File(["x"], "slip.pdf", { type: "application/pdf" })] },
    });
    fireEvent.click(screen.getByRole("button", { name: "Send payment proof" }));

    expect(
      await screen.findByText(
        "A payment proof must be a JPEG, PNG, or WebP image.",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Send payment proof" }),
    ).toBeInTheDocument();
  });

  it("paints an approved booking as a success and a cancelled one as a failure", async () => {
    stubApi({
      bookings: () =>
        json([
          { ...booking, status: "CONFIRMED" },
          { ...booking, id: "booking-2", reference: "AUV-260921-CANCELLED", status: "CANCELLED" },
        ]),
      trips: () => json([]),
    });

    renderPage();

    expect(await screen.findByText("CONFIRMED")).toHaveClass("text-emerald-700");
    expect(screen.getByText("CANCELLED")).toHaveClass("text-red-700");
  });

  it("tells a student when their unpaid booking loses its seats", async () => {
    stubApi({ bookings: () => json([booking]), trips: () => json([]) });

    renderPage();

    expect(
      await screen.findByText(
        `Send your payment slip by ${formatDeparture(booking.paymentDeadlineAt)} or these seats are released.`,
      ),
    ).toBeInTheDocument();
    expect(
      screen.queryByText(new RegExp(formatDeparture(trip.departureAt) + " or")),
    ).not.toBeInTheDocument();
  });

  it("says a cancelled booking expired rather than leaving the student guessing", async () => {
    stubApi({ bookings: () => json([expiredBooking]), trips: () => json([]) });

    renderPage();

    expect(
      await screen.findByText(
        "Expired unpaid and released seats A1. Book again if seats are still free.",
      ),
    ).toBeInTheDocument();
    expect(
      screen.queryByLabelText("Upload your payment slip"),
    ).not.toBeInTheDocument();
  });

  it("does not claim a booking the student cancelled themselves expired", async () => {
    stubApi({
      bookings: () =>
        json([{ ...booking, status: "CANCELLED", paymentDeadlineAt: null }]),
      trips: () => json([]),
    });

    renderPage();

    expect(await screen.findByText("CANCELLED")).toBeInTheDocument();
    expect(screen.queryByText(/Expired unpaid/)).not.toBeInTheDocument();
  });
});
