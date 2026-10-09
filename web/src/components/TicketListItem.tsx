import { ChevronRight } from "lucide-react";
import { Link } from "react-router";
import type { Booking } from "../types/booking";
import { formatTime } from "../utils/days";
import { seatLabels, stampOf } from "../utils/tickets";
import { StatusStamp } from "./StatusStamp";

const monthFormat = new Intl.DateTimeFormat("en-GB", { month: "short" });

export function TicketListItem({ booking }: { booking: Booking }) {
  const departure = new Date(booking.trip.departureAt);
  const stamp = stampOf(booking);
  const seats = seatLabels(booking);
  return (
    <Link
      aria-label={`${booking.reference}, ${booking.trip.origin} to ${booking.trip.destination}, ${stamp.label}`}
      className="group relative flex overflow-hidden rounded-2xl border border-line bg-card transition-colors duration-150 ease-out hover:border-ink/25"
      to={`/tickets/${booking.id}`}
    >
      <div className="flex w-[4.5rem] shrink-0 flex-col items-center justify-center py-4">
        <span className="font-display text-[30px] leading-none text-brand-900">{departure.getDate()}</span>
        <span className="mt-1 font-mono text-[10px] tracking-[0.16em] text-muted uppercase">
          {monthFormat.format(departure)}
        </span>
      </div>
      <span aria-hidden className="absolute -top-2 left-[4.5rem] size-4 -translate-x-1/2 rounded-full border border-line bg-paper" />
      <span aria-hidden className="absolute -bottom-2 left-[4.5rem] size-4 -translate-x-1/2 rounded-full border border-line bg-paper" />
      <div className="flex min-w-0 flex-1 items-center gap-3 border-l border-dashed border-line py-4 pr-3 pl-4">
        <div className="min-w-0 flex-1">
          <p className="truncate font-medium text-ink">
            <span className="font-mono tabular-nums">{formatTime(booking.trip.departureAt)}</span>
            <span className="mx-1.5 text-muted">·</span>
            {booking.trip.destination}
          </p>
          <p className="mt-0.5 truncate text-[13px] text-muted">
            {`${seats.length === 1 ? "Seat" : "Seats"} ${seats.join(", ")} · ${booking.reference}`}
          </p>
          <div className="mt-2.5">
            <StatusStamp label={stamp.label} tone={stamp.tone} />
          </div>
        </div>
        <ChevronRight aria-hidden className="size-4 shrink-0 text-muted transition-transform duration-150 group-hover:translate-x-0.5" />
      </div>
    </Link>
  );
}
