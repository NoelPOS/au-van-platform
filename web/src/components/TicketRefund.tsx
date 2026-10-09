import type { Booking } from "../types/booking";
import { formatShortDate } from "../utils/days";
import { formatBaht } from "../utils/format";

export function TicketRefund({ booking }: { booking: Booking }) {
  if (booking.refundStatus === "NONE") return null;
  const refunded = booking.refundStatus === "REFUNDED";
  return (
    <section
      aria-label="Refund"
      className={`rounded-2xl border px-4 py-3.5 ${refunded ? "border-line bg-card" : "border-accent/50 bg-warning-soft"}`}
    >
      <div className="flex items-baseline justify-between gap-3">
        <p className={`font-mono text-[10px] tracking-[0.16em] uppercase ${refunded ? "text-success" : "text-warning"}`}>
          {refunded && booking.refundedAt ? `Refunded ${formatShortDate(booking.refundedAt)}` : "Refund due"}
        </p>
        <p className="font-display text-[26px] leading-none text-brand-900">{formatBaht(booking.totalFare)}</p>
      </div>
      <p className="mt-1.5 text-sm text-ink">
        {refunded
          ? "Staff have sent your fare back."
          : "Staff will send your fare back. This ticket changes once they have."}
      </p>
      {booking.refundNote && <p className="mt-1 text-sm text-muted">{booking.refundNote}</p>}
    </section>
  );
}
