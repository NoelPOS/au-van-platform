import type { InsightTotals } from "../types/insights";
import { formatBaht, formatPercent } from "../utils/format";

function Figure({
  label,
  value,
  note,
  flagged = false,
}: {
  label: string;
  value: string;
  note: string;
  flagged?: boolean;
}) {
  return (
    <section
      aria-label={label}
      className="rounded-2xl border border-line bg-card px-6 pt-6 pb-5"
    >
      <h2 className="text-[11px] font-semibold tracking-[0.16em] text-muted uppercase">
        {label}
      </h2>
      <p className="mt-2 flex items-center gap-3">
        <span className="font-display text-4xl leading-none font-light text-brand-900 tabular-nums">
          {value}
        </span>
        {flagged && (
          <span aria-hidden className="size-2.5 rounded-full bg-accent" />
        )}
      </p>
      <p className="mt-3 font-mono text-[12px] text-muted tabular-nums">
        {note}
      </p>
    </section>
  );
}

export function InsightsFigures({ totals }: { totals: InsightTotals }) {
  const cancelled =
    totals.cancelledTrips > 0 ? ` · ${totals.cancelledTrips} cancelled` : "";
  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <Figure
        label="Revenue"
        note={`${formatBaht(totals.awaitingPayment)} awaiting payment`}
        value={formatBaht(totals.revenue)}
      />
      <Figure
        label="Seats sold"
        note={`${formatPercent(totals.occupancy)} of ${totals.seatsOffered} offered`}
        value={String(totals.seatsSold)}
      />
      <Figure
        label="Bookings"
        note={`${totals.trips} ${totals.trips === 1 ? "trip" : "trips"}${cancelled}`}
        value={String(totals.bookings)}
      />
      <Figure
        flagged={totals.refundsDue > 0}
        label="Refunds owed"
        note={`${formatBaht(totals.refunded)} already sent`}
        value={formatBaht(totals.refundsDue)}
      />
    </div>
  );
}
