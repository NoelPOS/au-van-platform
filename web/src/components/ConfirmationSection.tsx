import { Button } from "./ui/Button";
import { Panel } from "./ui/Panel";
import { formatDeparture, formatFare } from "../utils/format";
import type { Booking } from "../types/booking";

export function ConfirmationSection({
  booking,
  onDone,
}: {
  booking: Booking;
  onDone: () => void;
}) {
  return (
    <Panel className="p-5">
      {/*
        Not "confirmed": ADR-009 put a payment review in front of that, and
        this booking is PENDING_PAYMENT until staff approve the proof the
        student uploads from My bookings below.
      */}
      <h2 className="text-lg font-bold text-ink">Seats reserved</h2>
      <p className="mt-1 text-sm text-muted">
        Your seats are held. Upload your payment slip in My bookings below, and
        staff confirm the booking once they have checked it.
      </p>
      <p className="mt-4 text-2xl font-bold tracking-tight text-brand">
        {booking.reference}
      </p>
      <dl className="mt-4 grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
        <dt className="text-muted">Trip</dt>
        <dd className="text-ink">
          {booking.trip.origin} → {booking.trip.destination}
        </dd>
        <dt className="text-muted">Departure</dt>
        <dd className="text-ink">{formatDeparture(booking.trip.departureAt)}</dd>
        <dt className="text-muted">Seats</dt>
        <dd className="text-ink">
          {booking.seats.map((seat) => seat.label).join(", ")}
        </dd>
        <dt className="text-muted">Passenger</dt>
        <dd className="text-ink">{booking.passengerName}</dd>
        <dt className="text-muted">Total</dt>
        <dd className="text-ink">{formatFare(booking.totalFare)}</dd>
      </dl>
      <Button className="mt-5" onClick={onDone} type="button">
        Back to trips
      </Button>
    </Panel>
  );
}
