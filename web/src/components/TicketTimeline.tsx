import type { Booking, BookingEventType } from "../types/booking";
import { formatShortDate, formatTime } from "../utils/days";

const titles: Record<BookingEventType, string> = {
  CREATED: "Booked",
  PAYMENT_PROOF_SUBMITTED: "Payment slip sent",
  PAYMENT_APPROVED: "Payment approved",
  PAYMENT_REJECTED: "Slip sent back",
  CANCELLED: "Cancelled",
  EXPIRED: "Expired unpaid",
  TRIP_CANCELLED: "Trip cancelled by staff",
  TRIP_RESCHEDULED: "Departure moved",
  REFUNDED: "Refunded",
};

export function TicketTimeline({ booking }: { booking: Booking }) {
  const events = [...booking.events].reverse();
  return (
    <section aria-label="History">
      <h2 className="font-mono text-[11px] tracking-[0.18em] text-muted uppercase">History</h2>
      <ol className="mt-3">
        {events.map((event, index) => (
          <li className="relative flex gap-3 pb-4 last:pb-0" key={`${event.type}-${event.createdAt}`}>
            {index < events.length - 1 && (
              <span aria-hidden className="absolute top-3 bottom-0 left-[4px] border-l-2 border-dotted border-line" />
            )}
            <span
              aria-hidden
              className={`relative mt-1.5 size-2.5 shrink-0 rounded-full ${index === 0 ? "bg-brand-600" : "border-2 border-brand-500/50 bg-paper"}`}
            />
            <div className="min-w-0">
              <p className="text-sm font-medium text-ink">{titles[event.type]}</p>
              <p className="font-mono text-xs text-muted">
                {`${formatShortDate(event.createdAt)} · ${formatTime(event.createdAt)}`}
              </p>
            </div>
          </li>
        ))}
      </ol>
    </section>
  );
}
