import type { ReactNode } from "react";
import type { Booking } from "../types/booking";
import { formatShortDate, formatTime } from "../utils/days";
import { formatBaht } from "../utils/format";
import { seatLabels, stampOf } from "../utils/tickets";
import { StatusStamp } from "./StatusStamp";

function Detail({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt className="font-mono text-[10px] tracking-[0.16em] text-muted uppercase">
        {label}
      </dt>
      <dd className="mt-1 font-mono text-[15px] font-medium text-ink tabular-nums">
        {children}
      </dd>
    </div>
  );
}

function Perforation() {
  const notch = "absolute top-1/2 size-6 -translate-y-1/2 rounded-full border border-line bg-paper";
  return (
    <div aria-hidden className="relative h-6">
      <span className={`${notch} -left-3`} />
      <span className="absolute inset-x-5 top-1/2 border-t-2 border-dashed border-line" />
      <span className={`${notch} -right-3`} />
    </div>
  );
}

export function BoardingPass({ booking }: { booking: Booking }) {
  const stamp = stampOf(booking);
  const seats = seatLabels(booking);
  const cancelled = booking.status === "CANCELLED";
  return (
    <article
      aria-label={`Boarding pass ${booking.reference}`}
      className={`relative overflow-hidden rounded-[22px] border border-line bg-card ${cancelled ? "opacity-75" : ""}`}
    >
      <div className="px-5 pt-5">
        <div className="flex items-baseline justify-between gap-3">
          <p className="font-mono text-[10px] tracking-[0.2em] text-muted uppercase">
            Boarding pass
          </p>
          <p className="font-mono text-xs text-muted">{booking.reference}</p>
        </div>
        <div className="mt-4 grid grid-cols-[0.75rem_1fr] gap-x-3">
          <span aria-hidden className="flex flex-col items-center pt-[1.35rem] pb-[1.1rem]">
            <span className="size-2.5 shrink-0 rounded-full bg-brand-500" />
            <span className="w-0 flex-1 border-l-2 border-dotted border-brand-500/50" />
            <span className="size-2.5 shrink-0 rounded-full border-2 border-brand-500" />
          </span>
          <div className="min-w-0">
            <p className="font-mono text-[10px] tracking-[0.16em] text-muted uppercase">From</p>
            <p className="mt-0.5 text-lg leading-tight text-ink">{booking.trip.origin}</p>
            <p className="mt-4 font-mono text-[10px] tracking-[0.16em] text-muted uppercase">To</p>
            <p className="mt-0.5 font-display text-[30px] leading-[1.1] tracking-tight text-brand-900">
              {booking.trip.destination}
            </p>
          </div>
        </div>
        <dl className="mt-5 grid grid-cols-3 gap-3 pb-4">
          <Detail label="Date">{formatShortDate(booking.trip.departureAt)}</Detail>
          <Detail label="Departs">{formatTime(booking.trip.departureAt)}</Detail>
          <Detail label="Fare">{formatBaht(booking.totalFare)}</Detail>
        </dl>
      </div>
      <Perforation />
      <div className="flex items-end justify-between gap-4 px-5 pt-3 pb-5">
        <div className="min-w-0">
          <p className="font-mono text-[10px] tracking-[0.16em] text-muted uppercase">
            {seats.length === 1 ? "Seat" : "Seats"}
          </p>
          <p className="mt-0.5 font-display text-[40px] leading-none tracking-tight text-brand-900">
            {seats.join(" ")}
          </p>
          <p className="mt-2 truncate text-sm text-muted">{booking.passengerName}</p>
        </div>
        <div className="pb-1">
          <StatusStamp label={stamp.label} tone={stamp.tone} />
        </div>
      </div>
    </article>
  );
}
