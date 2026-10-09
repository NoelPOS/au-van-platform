import { useState, type FormEvent } from "react";
import { useMarkRefunded } from "../hooks/useRefundQueries";
import { useToast } from "../hooks/useToast";
import { ApiError } from "../services/bookingApi";
import type { AuthSession } from "../types/auth";
import type { Booking } from "../types/booking";
import { bangkokDateTime } from "../utils/dates";
import { formatAgo, formatBaht } from "../utils/format";
import { ErrorMessage } from "./FormCard";
import { Button } from "./ui/Button";
import { Input } from "./ui/Input";
import { RouteLine } from "./ui/RouteLine";

const goneFromQueue = ["refund_already_recorded", "refund_not_due", "booking_not_found"];

function cancellationNote(booking: Booking): string {
  const cancellation = booking.events.findLast((event) => event.type === "TRIP_CANCELLED" || event.type === "CANCELLED");
  if (!cancellation) return "";
  const who = cancellation.type === "TRIP_CANCELLED" ? "trip cancelled" : "student cancelled";
  return ` · ${who} ${formatAgo(cancellation.createdAt)}`;
}

export function RefundRow({ session, booking }: { session: AuthSession; booking: Booking }) {
  const markRefunded = useMarkRefunded(session);
  const notify = useToast();
  const [recording, setRecording] = useState(false);
  const [note, setNote] = useState("");
  const amount = formatBaht(booking.totalFare);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    markRefunded.mutate(
      { bookingId: booking.id, note: note.trim() },
      {
        onSuccess: () => notify(`Refund of ${amount} recorded for ${booking.reference}`),
        onError: (error) => {
          if (error instanceof ApiError && error.code && goneFromQueue.includes(error.code))
            notify(error.message, "danger");
        },
      },
    );
  }

  return (
    <li className="grid grid-cols-[4.5rem_minmax(0,1fr)] gap-x-4 gap-y-3 px-4 py-4 sm:grid-cols-[6rem_minmax(0,1fr)_auto] sm:px-5">
      <span className="font-display text-[28px] leading-none text-brand-900 tabular-nums">{amount}</span>
      <div className="min-w-0">
        <p className="font-mono text-[13px] font-medium text-brand-700">{booking.reference}</p>
        <p className="mt-1 truncate font-medium text-ink">
          {booking.passengerName}
          <a className="ml-2 font-mono text-[13px] font-normal text-muted hover:text-ink" href={`tel:${booking.passengerPhone}`}>
            {booking.passengerPhone}
          </a>
        </p>
        <p className="mt-1 text-sm text-muted">
          <RouteLine destination={booking.trip.destination} origin={booking.trip.origin} />
        </p>
        <p className="mt-1.5 font-mono text-xs text-muted tabular-nums">
          {`Departs ${bangkokDateTime(booking.trip.departureAt)}${cancellationNote(booking)}`}
        </p>
      </div>
      {!recording && (
        <div className="col-start-2 sm:col-start-3 sm:row-start-1 sm:self-center">
          <Button onClick={() => setRecording(true)} variant="secondary">
            Mark refunded
          </Button>
        </div>
      )}
      {recording && (
        <form
          aria-label={`Record refund for ${booking.reference}`}
          className="col-span-full grid gap-3 rounded-xl border border-line bg-paper p-4 sm:col-start-2"
          onSubmit={submit}
        >
          <Input
            hint="Optional. The transfer reference, or how the money went back."
            label="Note for the record"
            maxLength={500}
            onChange={(event) => setNote(event.target.value)}
            value={note}
          />
          <ErrorMessage error={markRefunded.error} />
          <div className="flex flex-wrap justify-end gap-2">
            <Button onClick={() => setRecording(false)} type="button" variant="secondary">
              Not yet
            </Button>
            <Button disabled={markRefunded.isPending} type="submit">
              {markRefunded.isPending ? "Saving…" : `Record ${amount} refunded`}
            </Button>
          </div>
        </form>
      )}
    </li>
  );
}
