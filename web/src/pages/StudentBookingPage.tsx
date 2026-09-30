import { BookingNotice } from "../components/BookingNotice";
import { ConfirmationSection } from "../components/ConfirmationSection";
import { MyBookingsSection } from "../components/MyBookingsSection";
import { PassengerDetailsSection } from "../components/PassengerDetailsSection";
import { SeatSelectionSection } from "../components/SeatSelectionSection";
import { TripListSection } from "../components/TripListSection";
import { maxSeatsPerHold, useBookingFlow } from "../hooks/useBookingFlow";
import { useWaitlistAndProof } from "../hooks/useWaitlistAndProof";
import type { AuthSession } from "../types/auth";
import { isUnauthorized } from "../utils/errors";

export function StudentBookingPage({ session }: { session: AuthSession }) {
  const flow = useBookingFlow(session);
  const extras = useWaitlistAndProof(session, flow.setNotice);

  if (
    [
      flow.trips.error,
      flow.bookings.error,
      flow.seatMap.error,
      flow.holdSeats.error,
      flow.createBooking.error,
      extras.submitProof.error,
      extras.waitlist.error,
    ].some(isUnauthorized)
  ) {
    return (
      <main className="mx-auto max-w-xl px-6 py-16">
        <h1 className="text-2xl font-bold text-ink">Sign in again</h1>
        <p className="mt-3 text-muted" role="alert">
          Your sign-in has expired. Close and reopen this page from LINE to
          continue booking.
        </p>
      </main>
    );
  }

  const { step, trip, hold, confirmed } = flow;

  return (
    <main className="mx-auto flex max-w-2xl flex-col gap-5 px-6 py-10">
      <header>
        <p className="text-xs font-bold uppercase tracking-widest text-brand">
          AU-Van
        </p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight text-ink">
          Book a seat
        </h1>
        <p className="mt-2 text-muted">
          Signed in as {session.user.displayName ?? "student"}.
        </p>
      </header>
      <BookingNotice notice={flow.notice} />
      {step === "trips" && (
        <TripListSection
          error={flow.trips.error}
          loading={flow.trips.isPending}
          onJoinWaitlist={(next) => void extras.joinTheWaitlist(next)}
          onLeaveWaitlist={(entry) => void extras.leaveTheWaitlist(entry)}
          onRetry={() => void flow.trips.refetch()}
          onSelect={flow.chooseTrip}
          trips={flow.trips.data ?? []}
          waitlist={extras.waitlist.data ?? []}
          waitlistPending={extras.waitlistPending}
        />
      )}
      {step === "seats" && trip && (
        <SeatSelectionSection
          error={flow.seatMap.error}
          holding={flow.holdSeats.isPending}
          loading={flow.seatMap.isPending}
          lostSeatLabels={flow.lostSeatLabels}
          maxSeats={maxSeatsPerHold}
          onBack={flow.backFromSeats}
          onHold={() => void flow.holdSelectedSeats()}
          onToggle={flow.toggleSeat}
          seats={flow.seats}
          selected={flow.effective}
          trip={trip}
        />
      )}
      {step === "details" && trip && hold && (
        <PassengerDetailsSection
          hold={hold}
          onChangeSeats={flow.changeSeats}
          onEdit={flow.resetIdempotencyKey}
          onExpire={flow.expireHold}
          onSubmit={(event) => void flow.confirmBooking(event)}
          submitting={flow.createBooking.isPending}
          trip={trip}
        />
      )}
      {step === "confirmed" && confirmed && (
        <ConfirmationSection
          booking={confirmed}
          onDone={flow.finishConfirmation}
        />
      )}
      {(step === "trips" || step === "confirmed") && (
        <MyBookingsSection
          bookings={flow.bookings.data ?? []}
          error={flow.bookings.error}
          loading={flow.bookings.isPending}
          onSubmitProof={(bookingId, file) =>
            void extras.submitPaymentProof(bookingId, file)
          }
          submitting={extras.submitProof.isPending}
        />
      )}
    </main>
  );
}
