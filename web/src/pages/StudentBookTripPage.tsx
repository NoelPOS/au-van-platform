import { ArrowLeft } from "lucide-react";
import { Link, useNavigate, useParams } from "react-router";
import { PassengerDetailsSection } from "../components/PassengerDetailsSection";
import { StickyActionBar } from "../components/StickyActionBar";
import { StudentSeatPicker } from "../components/StudentSeatPicker";
import { Button } from "../components/ui/Button";
import { EmptyState } from "../components/ui/EmptyState";
import { FailurePanel } from "../components/ui/FailurePanel";
import { RouteRule } from "../components/ui/RouteLine";
import { Skeleton } from "../components/ui/Skeleton";
import { maxSeatsPerHold, useBookingFlow } from "../hooks/useBookingFlow";
import { useAvailableTrips, useMyBookings } from "../hooks/useBookingQueries";
import { useStudent } from "../hooks/useStudent";
import type { AvailableTrip } from "../types/booking";
import { formatShortDate, formatTime } from "../utils/days";
import { formatBaht } from "../utils/format";

function lostSeatMessage(labels: string[]): string {
  if (labels.length === 0) return "";
  if (labels.length === 1)
    return `Seat ${labels[0]} was taken by another student and has been removed from your selection.`;
  return `Seats ${labels.join(", ")} were taken by another student and have been removed from your selection.`;
}

function TripHeading({ trip }: { trip: AvailableTrip }) {
  return (
    <header>
      <p className="font-mono text-[40px] leading-none font-medium tracking-tight text-brand-900 tabular-nums">
        {formatTime(trip.departureAt)}
      </p>
      <h1 className="mt-2 font-display text-[28px] leading-tight tracking-tight text-ink">
        <span className="sr-only">To </span>
        {trip.destination}
      </h1>
      <p className="mt-1 flex items-center gap-1.5 text-sm text-muted">
        <RouteRule className="w-4" />
        {`from ${trip.origin}`}
      </p>
      <p className="mt-1 font-mono text-[13px] text-muted">
        {`${formatShortDate(trip.departureAt)} · ${trip.durationMinutes} min · ${formatBaht(trip.fare)} a seat`}
      </p>
    </header>
  );
}

export function StudentBookTripPage() {
  const { tripId } = useParams();
  const { session, setNotice } = useStudent();
  const navigate = useNavigate();
  const trips = useAvailableTrips(session);
  const bookings = useMyBookings(session);
  const trip = trips.data?.find((candidate) => candidate.id === tripId) ?? null;
  const leave = (message: string, to = "/") => {
    void (to === "/" ? trips.refetch() : bookings.refetch());
    navigate(to, { state: { notice: { tone: "error", message } } });
  };
  const flow = useBookingFlow(session, trip, {
    setNotice,
    leave,
    booked: (booking) =>
      navigate(`/tickets/${booking.id}`, { state: { booked: booking } }),
  });

  if (trips.isPending) return <Skeleton className="h-96" />;
  if (!trip)
    return (
      <EmptyState
        action={<Link className="text-sm font-semibold text-brand-700 underline underline-offset-4" to="/">See other departures</Link>}
        detail="It may have filled up, been cancelled, or already left."
        title="That trip is no longer available"
      />
    );

  const latest = bookings.data?.[0];
  const selectedLabels = flow.seats
    .filter((seat) => flow.selectedSeatIds.includes(seat.id))
    .map((seat) => seat.label);

  return (
    <>
      <button
        className="-ml-1 inline-flex min-h-10 items-center gap-1.5 self-start text-sm font-medium text-muted hover:text-ink"
        onClick={() => {
          flow.releaseCurrentHold();
          navigate("/");
        }}
        type="button"
      >
        <ArrowLeft aria-hidden className="size-4" />
        Departures
      </button>
      <TripHeading trip={trip} />

      {flow.step === "details" && flow.hold ? (
        <PassengerDetailsSection
          defaults={{
            name: latest?.passengerName ?? session.user.displayName ?? "",
            phone: latest?.passengerPhone ?? "",
          }}
          hold={flow.hold}
          onChangeSeats={flow.changeSeats}
          onEdit={flow.resetIdempotencyKey}
          onExpire={flow.expireHold}
          onSubmit={(event) => void flow.confirmBooking(event)}
          submitting={flow.createBooking.isPending}
          trip={trip}
        />
      ) : (
        <section aria-label="Choose seats" className="flex flex-col gap-4">
          <p className="text-sm text-muted">
            {`Tap up to ${maxSeatsPerHold} seats. We hold them for 5 minutes while you add passenger details.`}
          </p>
          {/* Stays mounted: a status region inserted with its first text is announced unreliably. */}
          <p
            className={flow.lostSeatLabels.length > 0 ? "rounded-2xl border border-danger/20 bg-danger-soft px-4 py-3 text-sm text-danger" : ""}
            role="status"
          >
            {lostSeatMessage(flow.lostSeatLabels)}
          </p>
          {flow.seatMap.isPending && (
            <div role="status">
              <span className="sr-only">Loading seats…</span>
              <Skeleton className="mx-auto h-96 w-56" />
            </div>
          )}
          {flow.seatMap.error && (
            <FailurePanel error={flow.seatMap.error} title="Could not load seats" />
          )}
          {flow.seats.length > 0 && (
            <StudentSeatPicker
              disabled={flow.holdSeats.isPending}
              onToggle={flow.toggleSeat}
              seats={flow.seats}
              selected={flow.selectedSeatIds}
            />
          )}
          <StickyActionBar>
            <div className="min-w-0 flex-1">
              <p className="truncate font-display text-lg text-brand-900">
                {selectedLabels.length ? selectedLabels.join(" · ") : "No seats yet"}
              </p>
              <p className="text-xs text-muted">
                {selectedLabels.length
                  ? `${selectedLabels.length} ${selectedLabels.length === 1 ? "seat" : "seats"} · ${formatBaht(trip.fare * selectedLabels.length)}`
                  : `Up to ${maxSeatsPerHold} seats`}
              </p>
            </div>
            <Button
              disabled={flow.selectedSeatIds.length === 0 || flow.holdSeats.isPending}
              onClick={() => void flow.holdSelectedSeats()}
              type="button"
            >
              {flow.holdSeats.isPending ? "Holding…" : "Hold seats"}
            </Button>
          </StickyActionBar>
        </section>
      )}
    </>
  );
}
