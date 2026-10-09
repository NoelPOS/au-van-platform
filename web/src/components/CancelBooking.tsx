import { useState } from "react";
import { useNavigate } from "react-router";
import { Button } from "./ui/Button";
import { useCancelBooking } from "../hooks/useBookingQueries";
import { useNow } from "../hooks/useNow";
import { useStudent } from "../hooks/useStudent";
import { ApiError } from "../services/bookingApi";
import type { Booking } from "../types/booking";
import { deadlinePhrase } from "../utils/days";
import { messageOf } from "../utils/errors";
import { formatBaht } from "../utils/format";

function leadTime(booking: Booking, until: string) {
  const minutes = Math.round((Date.parse(booking.trip.departureAt) - Date.parse(until)) / 60_000);
  if (minutes % 60 !== 0) return `${minutes} minutes`;
  return minutes === 60 ? "an hour" : `${minutes / 60} hours`;
}

function Closed({ booking, until }: { booking: Booking; until: string }) {
  return (
    <section aria-label="Cancelling closed" className="rounded-2xl border border-line bg-card px-4 py-3.5">
      <p className="font-mono text-[10px] tracking-[0.16em] text-muted uppercase">
        Cancelling closed {deadlinePhrase(until)}
      </p>
      <p className="mt-1 text-sm text-ink">
        {`Bookings can be cancelled until ${leadTime(booking, until)} before departure.`}
      </p>
    </section>
  );
}

export function CancelBooking({ booking }: { booking: Booking }) {
  const { session } = useStudent();
  const navigate = useNavigate();
  const cancel = useCancelBooking(session);
  const now = useNow();
  const [asking, setAsking] = useState(false);
  const until = booking.cancellableUntil;
  const paid = booking.status === "CONFIRMED";

  if (!until || Date.parse(booking.trip.departureAt) <= now) return null;
  const closedByServer = cancel.error instanceof ApiError && cancel.error.code === "cancellation_closed";
  if (closedByServer || now >= Date.parse(until)) return <Closed booking={booking} until={until} />;
  if (booking.status === "PAYMENT_UNDER_REVIEW")
    return (
      <p className="self-center text-center text-sm text-muted">
        You can cancel once staff have checked your slip.
      </p>
    );

  async function confirm() {
    try {
      const cancelled = await cancel.mutateAsync(booking.id);
      const message =
        cancelled.refundStatus === "DUE"
          ? `Booking ${booking.reference} is cancelled. Staff will refund your ${formatBaht(booking.totalFare)}.`
          : `Booking ${booking.reference} is cancelled and its seats are free again.`;
      navigate("/tickets", { state: { notice: { tone: "status", message } } });
    } catch {
      // Shown below.
    }
  }

  if (!asking)
    return (
      <div className="flex flex-col items-center">
        <button
          className="min-h-11 px-2 text-sm font-medium text-muted underline decoration-muted/40 underline-offset-4 hover:text-danger"
          onClick={() => setAsking(true)}
          type="button"
        >
          Cancel this booking
        </button>
        <p className="font-mono text-xs text-muted">{`You can cancel until ${deadlinePhrase(until)}`}</p>
      </div>
    );

  return (
    <section aria-label="Cancel booking" className="rounded-2xl border border-danger/25 bg-danger-soft p-4">
      <p className="text-sm font-semibold text-ink">Cancel this booking?</p>
      <p className="mt-1 text-sm text-muted">
        {paid
          ? `Your seats go back on sale. You have paid ${formatBaht(booking.totalFare)}, so staff will arrange a refund.`
          : "Your seats go straight back on sale."}
      </p>
      {cancel.error && (
        <p className="mt-2 text-sm text-danger" role="alert">
          {messageOf(cancel.error)}
        </p>
      )}
      <div className="mt-3 flex gap-2">
        <Button onClick={() => setAsking(false)} type="button" variant="secondary">
          Keep booking
        </Button>
        <Button disabled={cancel.isPending} onClick={() => void confirm()} type="button" variant="danger">
          {cancel.isPending ? "Cancelling…" : "Yes, cancel"}
        </Button>
      </div>
    </section>
  );
}
