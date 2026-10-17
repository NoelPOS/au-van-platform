import { BoardingPass } from "./BoardingPass";
import type { Booking } from "../types/booking";

const sampleBooking: Booking = {
  id: "sample",
  reference: "AUV-261020-7KQ2M4XR",
  status: "CONFIRMED",
  trip: {
    id: "sample-trip",
    origin: "Assumption University",
    destination: "Mega Bangna",
    departureAt: "2026-10-20T00:30:00Z",
  },
  passengerName: "Ploy S.",
  passengerPhone: "",
  totalFare: 35,
  seats: [{ seatId: "sample-seat", label: "B2" }],
  events: [],
  paymentDeadlineAt: null,
  cancellableUntil: null,
  refundStatus: "NONE",
  refundedAt: null,
  refundNote: null,
  createdAt: "2026-10-19T09:12:00Z",
};

export function HeroTicket() {
  return (
    <figure className="mx-auto w-full max-w-[22rem] lg:max-w-[24rem]">
      <div className="relative">
        <div
          aria-hidden
          className="absolute inset-0 translate-x-2 translate-y-5 sm:translate-x-4 rotate-[4deg] rounded-[22px] border border-line bg-brand-50"
        />
        <div className="relative -rotate-2 shadow-[0_24px_48px_-28px_rgba(27,37,102,0.45)]">
          <BoardingPass booking={sampleBooking} />
        </div>
      </div>
      <figcaption className="mt-10 max-w-[18rem] font-mono text-[11px] leading-5 text-muted">
        A confirmed boarding pass, as a student sees it after staff approve
        the slip.
      </figcaption>
    </figure>
  );
}
