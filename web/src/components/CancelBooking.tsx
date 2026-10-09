import { useState } from "react";
import { useNavigate } from "react-router";
import { Button } from "./ui/Button";
import { useCancelBooking } from "../hooks/useBookingQueries";
import { useStudent } from "../hooks/useStudent";
import type { Booking } from "../types/booking";
import { messageOf } from "../utils/errors";

export function CancelBooking({ booking }: { booking: Booking }) {
  const { session } = useStudent();
  const navigate = useNavigate();
  const cancel = useCancelBooking(session);
  const [asking, setAsking] = useState(false);

  async function confirm() {
    try {
      await cancel.mutateAsync(booking.id);
      navigate("/tickets", {
        state: { notice: { tone: "status", message: `Booking ${booking.reference} is cancelled and its seats are free again.` } },
      });
    } catch {
      // Shown below.
    }
  }

  if (!asking)
    return (
      <button
        className="min-h-11 self-center px-2 text-sm font-medium text-muted underline decoration-muted/40 underline-offset-4 hover:text-danger"
        onClick={() => setAsking(true)}
        type="button"
      >
        Cancel this booking
      </button>
    );

  return (
    <section aria-label="Cancel booking" className="rounded-2xl border border-line bg-card p-4">
      <p className="text-sm font-semibold text-ink">Cancel this booking?</p>
      <p className="mt-1 text-sm text-muted">Your seats go straight back on sale.</p>
      {cancel.error && (
        <p className="mt-2 text-sm text-danger" role="alert">
          {messageOf(cancel.error)}
        </p>
      )}
      <div className="mt-3 flex gap-2">
        <Button onClick={() => setAsking(false)} type="button" variant="secondary">
          Keep booking
        </Button>
        <Button
          disabled={cancel.isPending}
          onClick={() => void confirm()}
          type="button"
          variant="danger"
        >
          {cancel.isPending ? "Cancelling…" : "Yes, cancel"}
        </Button>
      </div>
    </section>
  );
}
