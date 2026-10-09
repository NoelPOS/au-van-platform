import { cleanup, fireEvent, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { booking, json } from "../test/bookingFixtures";
import { renderPage, stubApi } from "../test/renderStudentApp";
import { deadlinePhrase } from "../utils/days";

const confirmed = { ...booking, status: "CONFIRMED", totalFare: 70 };
const hour = 3_600_000;

function closingSoon() {
  return {
    ...booking,
    trip: { ...booking.trip, departureAt: new Date(Date.now() + hour).toISOString() },
    cancellableUntil: new Date(Date.now() - hour).toISOString(),
  };
}

function cancelCalls(fetcher: ReturnType<typeof stubApi>) {
  return fetcher.mock.calls.filter(([url]) => String(url).endsWith("/cancel"));
}

describe("Student cancelling a booking", () => {
  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("cancels an unpaid booking only after the student confirms", async () => {
    const fetcher = stubApi({ bookings: () => json([booking]) });

    renderPage("/tickets/booking-1");
    fireEvent.click(await screen.findByRole("button", { name: "Cancel this booking" }));
    fireEvent.click(screen.getByRole("button", { name: "Keep booking" }));
    expect(cancelCalls(fetcher)).toHaveLength(0);

    fireEvent.click(screen.getByRole("button", { name: "Cancel this booking" }));
    expect(screen.getByText("Your seats go straight back on sale.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Yes, cancel" }));

    expect(
      await screen.findByText("Booking AUV-260921-7KQ2M4XR is cancelled and its seats are free again."),
    ).toBeInTheDocument();
    expect(fetcher).toHaveBeenCalledWith(
      "/api/v1/bookings/booking-1/cancel",
      expect.objectContaining({ method: "POST" }),
    );
  });

  it("names the cutoff beside the cancel button", async () => {
    stubApi({ bookings: () => json([booking]) });

    renderPage("/tickets/booking-1");

    expect(
      await screen.findByText(`You can cancel until ${deadlinePhrase(booking.cancellableUntil)}`),
    ).toBeInTheDocument();
  });

  it("cancels a paid booking and says a refund will be arranged", async () => {
    stubApi({
      bookings: () => json([confirmed]),
      cancel: () => json({ ...confirmed, status: "CANCELLED", refundStatus: "DUE" }),
    });

    renderPage("/tickets/booking-1");
    fireEvent.click(await screen.findByRole("button", { name: "Cancel this booking" }));

    const ask = within(screen.getByRole("region", { name: "Cancel booking" }));
    expect(
      ask.getByText("Your seats go back on sale. You have paid ฿70, so staff will arrange a refund."),
    ).toBeInTheDocument();
    fireEvent.click(ask.getByRole("button", { name: "Yes, cancel" }));

    expect(
      await screen.findByText("Booking AUV-260921-7KQ2M4XR is cancelled. Staff will refund your ฿70."),
    ).toBeInTheDocument();
  });

  it("explains the cutoff instead of offering to cancel once it has passed", async () => {
    stubApi({ bookings: () => json([closingSoon()]) });

    renderPage("/tickets/booking-1");

    const closed = await screen.findByRole("region", { name: "Cancelling closed" });
    expect(closed).toHaveTextContent("Bookings can be cancelled until 2 hours before departure.");
    expect(screen.queryByRole("button", { name: "Cancel this booking" })).not.toBeInTheDocument();
  });

  it("switches to the explanation when the API says cancelling has closed", async () => {
    stubApi({
      bookings: () => json([booking]),
      cancel: () =>
        json({ code: "cancellation_closed", detail: "This booking can no longer be cancelled." }, 409),
    });

    renderPage("/tickets/booking-1");
    fireEvent.click(await screen.findByRole("button", { name: "Cancel this booking" }));
    fireEvent.click(screen.getByRole("button", { name: "Yes, cancel" }));

    expect(await screen.findByRole("region", { name: "Cancelling closed" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Yes, cancel" })).not.toBeInTheDocument();
  });

  it("keeps the confirm open with the reason when a cancel fails for another reason", async () => {
    stubApi({
      bookings: () => json([booking]),
      cancel: () =>
        json({ code: "booking_already_cancelled", detail: "That booking has already been cancelled." }, 409),
    });

    renderPage("/tickets/booking-1");
    fireEvent.click(await screen.findByRole("button", { name: "Cancel this booking" }));
    fireEvent.click(screen.getByRole("button", { name: "Yes, cancel" }));

    const ask = within(screen.getByRole("region", { name: "Cancel booking" }));
    expect(await ask.findByRole("alert")).toHaveTextContent("That booking has already been cancelled.");
    expect(screen.getByRole("button", { name: "Yes, cancel" })).toBeEnabled();
  });

  it("waits for staff to check a slip before offering to cancel", async () => {
    stubApi({ bookings: () => json([{ ...booking, status: "PAYMENT_UNDER_REVIEW" }]) });

    renderPage("/tickets/booking-1");

    expect(await screen.findByText("You can cancel once staff have checked your slip.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Cancel this booking" })).not.toBeInTheDocument();
  });

  it("offers nothing to cancel on a booking that is already cancelled", async () => {
    stubApi({
      bookings: () => json([{ ...booking, status: "CANCELLED", cancellableUntil: null }]),
    });

    renderPage("/tickets/booking-1");

    await screen.findByRole("heading", { name: "This booking was cancelled" });
    expect(screen.queryByRole("button", { name: "Cancel this booking" })).not.toBeInTheDocument();
    expect(screen.queryByRole("region", { name: "Cancelling closed" })).not.toBeInTheDocument();
  });

  it("offers nothing once the trip has left", async () => {
    const departed = {
      ...booking,
      status: "CONFIRMED",
      trip: { ...booking.trip, departureAt: new Date(Date.now() - hour).toISOString() },
      cancellableUntil: new Date(Date.now() - 3 * hour).toISOString(),
    };
    stubApi({ bookings: () => json([departed]) });

    renderPage("/tickets/booking-1");

    await screen.findByRole("heading", { name: "You’re all set" });
    expect(screen.queryByRole("button", { name: "Cancel this booking" })).not.toBeInTheDocument();
    expect(screen.queryByRole("region", { name: "Cancelling closed" })).not.toBeInTheDocument();
  });
});
