import type { FormEvent } from "react";
import { Button } from "../../components/ui/Button";
import { Panel } from "../../components/ui/Panel";
import { StatusBadge } from "../../components/ui/StatusBadge";
import { formatDeparture, formatFare } from "../format";
import type { Booking } from "../types";

/** Mirrors the API's own allowlist; its 400 is the backstop. */
const acceptedImageTypes = "image/jpeg,image/png,image/webp";

export function MyBookingsSection({
  bookings,
  loading,
  error,
  onSubmitProof,
  submitting,
}: {
  bookings: Booking[];
  loading: boolean;
  error: Error | null;
  onSubmitProof: (bookingId: string, file: File) => void;
  submitting: boolean;
}) {
  return (
    <Panel className="p-5">
      <h2 className="mb-4 text-lg font-bold text-ink">My bookings</h2>
      {loading && <p className="text-sm text-muted">Loading your bookings…</p>}
      {error && (
        <p className="text-sm text-red-700" role="alert">
          {error.message}
        </p>
      )}
      {!loading && !error && bookings.length === 0 && (
        <p className="text-sm text-muted">You have no bookings yet.</p>
      )}
      <ul className="flex flex-col gap-3">
        {bookings.map((booking) => (
          <li
            className="rounded-xl border border-line px-4 py-3"
            key={booking.id}
          >
            <div className="flex items-center justify-between gap-3">
              <span className="font-semibold text-ink">
                {booking.reference}
              </span>
              <StatusBadge value={booking.status} />
            </div>
            <p className="mt-1 text-sm text-muted">
              {`${booking.trip.origin} → ${booking.trip.destination} · ${formatDeparture(booking.trip.departureAt)}`}
            </p>
            <p className="mt-1 text-sm text-muted">
              {`Seat ${booking.seats.map((seat) => seat.label).join(", ")} · ${formatFare(booking.totalFare)}`}
            </p>
            {booking.status === "PENDING_PAYMENT" && (
              <PaymentProofForm
                bookingId={booking.id}
                onSubmitProof={onSubmitProof}
                submitting={submitting}
              />
            )}
            {booking.status === "PAYMENT_UNDER_REVIEW" && (
              <p className="mt-3 text-sm text-muted">
                Your payment proof is with an administrator. This booking is
                confirmed once they approve it.
              </p>
            )}
          </li>
        ))}
      </ul>
    </Panel>
  );
}

function PaymentProofForm({
  bookingId,
  onSubmitProof,
  submitting,
}: {
  bookingId: string;
  onSubmitProof: (bookingId: string, file: File) => void;
  submitting: boolean;
}) {
  // The file is read off the form on submit rather than held in state: there
  // is nothing to do with it in between, and a controlled file input cannot
  // be reset the way the rest of this form can.
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const input = event.currentTarget.elements.namedItem(
      "file",
    ) as HTMLInputElement | null;
    const file = input?.files?.[0];
    if (file) onSubmitProof(bookingId, file);
  }

  return (
    <form className="mt-3 flex flex-col gap-2" onSubmit={submit}>
      <label
        className="text-sm font-semibold text-ink"
        htmlFor={`payment-proof-${bookingId}`}
      >
        Upload your payment slip
      </label>
      <p className="text-sm text-muted">
        A JPEG, PNG, or WebP image of up to 5MB. Only AU-Van staff can see it.
      </p>
      <input
        accept={acceptedImageTypes}
        className="text-sm text-ink"
        id={`payment-proof-${bookingId}`}
        name="file"
        type="file"
      />
      <Button className="self-start" disabled={submitting} type="submit">
        {submitting ? "Sending…" : "Send payment proof"}
      </Button>
    </form>
  );
}
