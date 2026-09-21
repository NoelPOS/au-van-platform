import { useEffect, useRef, useState, type FormEvent } from "react";
import type { AuthSession } from "../auth/session";
import { ApiError } from "./booking-api";
import {
  useAvailableTrips,
  useCreateBooking,
  useHoldSeats,
  useJoinWaitlist,
  useLeaveWaitlist,
  useMyBookings,
  useMyWaitlist,
  useReleaseHold,
  useSeatMap,
  useSubmitPaymentProof,
} from "./hooks";
import { ConfirmationSection } from "./sections/ConfirmationSection";
import { MyBookingsSection } from "./sections/MyBookingsSection";
import { PassengerDetailsSection } from "./sections/PassengerDetailsSection";
import { SeatSelectionSection } from "./sections/SeatSelectionSection";
import { TripListSection } from "./sections/TripListSection";
import type {
  AvailableTrip,
  Booking,
  SeatHold,
  WaitlistEntry,
} from "./types";

type Step = "trips" | "seats" | "details" | "confirmed";
type Notice = { tone: "error" | "status"; message: string };

/** Mirrors the API's `booking.max-seats-per-hold`; its 400 is the backstop. */
const maxSeatsPerHold = 4;

function messageOf(error: unknown): string {
  return error instanceof Error
    ? error.message
    : "Something went wrong. Try again.";
}

function isUnauthorized(error: unknown): boolean {
  return error instanceof ApiError && error.status === 401;
}

export function StudentBookingPage({ session }: { session: AuthSession }) {
  const [step, setStep] = useState<Step>("trips");
  const [trip, setTrip] = useState<AvailableTrip | null>(null);
  const [selected, setSelected] = useState<string[]>([]);
  const [hold, setHold] = useState<SeatHold | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [confirmed, setConfirmed] = useState<Booking | null>(null);
  // One idempotency key per booking attempt, deliberately kept across a failed
  // attempt so a retry cannot create a second booking.
  const keyRef = useRef<string | null>(null);

  const trips = useAvailableTrips(session);
  const bookings = useMyBookings(session);
  const waitlist = useMyWaitlist(session);
  const seatMap = useSeatMap(
    session,
    trip?.id ?? null,
    step === "seats" && hold === null,
  );
  const holdSeats = useHoldSeats(session);
  const releaseHold = useReleaseHold(session);
  const createBooking = useCreateBooking(session);
  const submitProof = useSubmitPaymentProof(session);
  const joinWaitlist = useJoinWaitlist(session);
  const leaveWaitlist = useLeaveWaitlist(session);

  const seats = seatMap.data?.seats ?? [];
  // The poll can never fight the selection: availability is derived from the
  // latest map on every render and never written back into `selected`.
  const takeable = new Set(
    seats
      .filter((seat) => seat.state === "AVAILABLE" || seat.state === "HELD_BY_YOU")
      .map((seat) => seat.id),
  );
  const effective = seatMap.data
    ? selected.filter((seatId) => takeable.has(seatId))
    : selected;
  const lostSeatLabels = seats
    .filter((seat) => selected.includes(seat.id) && !takeable.has(seat.id))
    .map((seat) => seat.label);

  // A 404 on the map means the trip itself is gone. Rendering the error beside
  // an empty grid would leave the student on a dead trip that is still in the
  // list, so this is the same forced transition the other trip-gone codes take.
  const tripIsGone = seatMap.error instanceof ApiError && seatMap.error.status === 404;
  useEffect(() => {
    if (tripIsGone)
      backToTrips("That trip is no longer available. Choose another.");
    // `backToTrips` is redeclared every render, so depending on it would fire
    // the transition on every render instead of on the edge into the 404.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tripIsGone]);

  if (
    [
      trips.error,
      bookings.error,
      seatMap.error,
      holdSeats.error,
      createBooking.error,
      submitProof.error,
      waitlist.error,
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

  function backToTrips(message: string) {
    setStep("trips");
    setTrip(null);
    setSelected([]);
    setHold(null);
    keyRef.current = null;
    setNotice({ tone: "error", message });
    void trips.refetch();
  }

  function backToSeats(message: string) {
    setStep("seats");
    setSelected([]);
    setHold(null);
    keyRef.current = null;
    setNotice({ tone: "error", message });
    void seatMap.refetch();
  }

  function chooseTrip(next: AvailableTrip) {
    setTrip(next);
    setSelected([]);
    setHold(null);
    keyRef.current = null;
    setNotice(null);
    setStep("seats");
  }

  /**
   * A refused join is almost always the trip having a seat again, which the
   * student would rather book than queue for, so both failures that mean that
   * send them back to a refreshed list instead of leaving a message beside a
   * stale row.
   */
  async function joinTheWaitlist(next: AvailableTrip) {
    setNotice(null);
    try {
      const entry = await joinWaitlist.mutateAsync({
        tripId: next.id,
        seatsWanted: 1,
      });
      setNotice({
        tone: "status",
        message: `You are number ${entry.position} on the waitlist for ${next.origin} → ${next.destination}.`,
      });
    } catch (error) {
      const code = error instanceof ApiError ? error.code : null;
      if (code === "waitlist_not_needed")
        return setNotice({
          tone: "status",
          message: "That trip has a seat free again. Book it instead.",
        });
      if (code === "trip_not_available" || code === "trip_departed")
        return setNotice({
          tone: "error",
          message: "That trip is no longer available. Choose another.",
        });
      setNotice({ tone: "error", message: messageOf(error) });
    }
  }

  async function leaveTheWaitlist(entry: WaitlistEntry) {
    setNotice(null);
    try {
      await leaveWaitlist.mutateAsync({ entryId: entry.id });
      setNotice({ tone: "status", message: "You have left the waitlist." });
    } catch (error) {
      // A 404 means the entry is already gone, which is what was asked for;
      // the refreshed queue on the way out says so on its own.
      if (error instanceof ApiError && error.status === 404) return;
      setNotice({ tone: "error", message: messageOf(error) });
    }
  }

  function toggleSeat(seatId: string) {
    // Built from the pruned selection, so a seat lost to the poll drops out
    // for good on the next interaction.
    if (effective.includes(seatId)) {
      setNotice(null);
      setSelected(effective.filter((id) => id !== seatId));
      return;
    }
    if (effective.length === maxSeatsPerHold) {
      setNotice({
        tone: "status",
        message: `You can hold at most ${maxSeatsPerHold} seats at a time.`,
      });
      return;
    }
    setNotice(null);
    setSelected([...effective, seatId]);
  }

  async function holdSelectedSeats() {
    if (!trip || effective.length === 0) return;
    setNotice(null);
    try {
      const created = await holdSeats.mutateAsync({
        tripId: trip.id,
        seatIds: effective,
      });
      setHold(created);
      setSelected(created.seats.map((seat) => seat.seatId));
      keyRef.current = null;
      setStep("details");
    } catch (error) {
      onHoldFailure(error);
    }
  }

  function onHoldFailure(error: unknown) {
    const code = error instanceof ApiError ? error.code : null;
    const status = error instanceof ApiError ? error.status : 0;
    if (code === "trip_not_available")
      return backToTrips("That trip is no longer available. Choose another.");
    if (code === "trip_departed")
      return backToTrips("That trip has already departed. Choose another.");
    if (status === 404)
      return backToTrips("That trip is no longer available. Choose another.");
    // A seat was taken first: the map has been refreshed and the selection is
    // pruned from it, so the student only has to pick again.
    setNotice({ tone: "error", message: messageOf(error) });
  }

  function expireHold() {
    setHold(null);
    setSelected([]);
    keyRef.current = null;
    setStep("seats");
    setNotice({
      tone: "status",
      message: "Your seat hold expired. Choose your seats again.",
    });
    void seatMap.refetch();
  }

  function changeSeats() {
    if (hold && trip)
      releaseHold.mutate({ holdId: hold.holdId, tripId: trip.id });
    setHold(null);
    setSelected([]);
    keyRef.current = null;
    setNotice(null);
    setStep("seats");
  }

  async function confirmBooking(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!hold) return;
    const form = new FormData(event.currentTarget);
    keyRef.current ??= crypto.randomUUID();
    setNotice(null);
    try {
      const booking = await createBooking.mutateAsync({
        idempotencyKey: keyRef.current,
        input: {
          holdId: hold.holdId,
          passengerName: String(form.get("passengerName")).trim(),
          passengerPhone: String(form.get("passengerPhone")).trim(),
        },
      });
      keyRef.current = null;
      setHold(null);
      setSelected([]);
      setConfirmed(booking);
      setStep("confirmed");
    } catch (error) {
      onBookingFailure(error);
    }
  }

  /**
   * The upload keeps the student where they are: nothing else on the page
   * depends on it, and a refused file is fixed by picking another one.
   */
  async function submitPaymentProof(bookingId: string, file: File) {
    setNotice(null);
    try {
      await submitProof.mutateAsync({ bookingId, file });
      setNotice({
        tone: "status",
        message:
          "Payment proof received. Staff confirm the booking once they have checked it.",
      });
    } catch (error) {
      setNotice({ tone: "error", message: messageOf(error) });
    }
  }

  function onBookingFailure(error: unknown) {
    const code = error instanceof ApiError ? error.code : null;
    const status = error instanceof ApiError ? error.status : 0;
    if (code === "hold_expired")
      return backToSeats(
        "Your seat hold expired before the booking was confirmed. Choose your seats again.",
      );
    if (code === "hold_already_used") {
      // A dropped connection can hide a successful 201, and a form edit then
      // mints a new key: the booking exists. Sending the student back to the
      // seat map would hide the very booking they are being told about, so
      // this one lands on the list that shows it.
      void bookings.refetch();
      return backToTrips(
        "Those seats are already booked. If that was you, the booking is in My bookings below.",
      );
    }
    if (code === "hold_not_found")
      return backToSeats(
        "That seat hold is no longer available. Choose your seats again.",
      );
    if (code === "trip_not_available")
      return backToTrips("That trip is no longer available. Choose another.");
    if (code === "trip_departed")
      return backToTrips("That trip has already departed. Choose another.");
    if (code === "idempotency_key_reused") {
      keyRef.current = null;
      return setNotice({
        tone: "error",
        message: "That booking attempt could not be completed. Try again.",
      });
    }
    if (status === 400)
      return setNotice({
        tone: "error",
        message: "Check the passenger name and phone number, then try again.",
      });
    // Anything else keeps the hold and the idempotency key: this is the retry
    // case, and retrying cannot create a second booking.
    setNotice({ tone: "error", message: messageOf(error) });
  }

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
      {/*
        Both live regions stay mounted and only their text changes. A
        `role="status"` region inserted together with its first text is
        announced unreliably, and swapping the role on one element has the
        same problem. Empty, the wrapper is taken out of the flex flow rather
        than unmounted, so it neither leaves a gap nor leaves the tree.
      */}
      <div className={notice ? "" : "sr-only"}>
        <p
          className={
            notice?.tone === "error"
              ? "rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700"
              : ""
          }
          role="alert"
        >
          {notice?.tone === "error" ? notice.message : ""}
        </p>
        <p
          className={
            notice?.tone === "status"
              ? "rounded-xl bg-brand-soft px-4 py-3 text-sm text-brand"
              : ""
          }
          role="status"
        >
          {notice?.tone === "status" ? notice.message : ""}
        </p>
      </div>
      {step === "trips" && (
        <TripListSection
          error={trips.error}
          loading={trips.isPending}
          onJoinWaitlist={(next) => void joinTheWaitlist(next)}
          onLeaveWaitlist={(entry) => void leaveTheWaitlist(entry)}
          onRetry={() => void trips.refetch()}
          onSelect={chooseTrip}
          trips={trips.data ?? []}
          waitlist={waitlist.data ?? []}
          waitlistPending={joinWaitlist.isPending || leaveWaitlist.isPending}
        />
      )}
      {step === "seats" && trip && (
        <SeatSelectionSection
          error={seatMap.error}
          holding={holdSeats.isPending}
          loading={seatMap.isPending}
          lostSeatLabels={lostSeatLabels}
          maxSeats={maxSeatsPerHold}
          onBack={() => {
            setTrip(null);
            setSelected([]);
            setNotice(null);
            setStep("trips");
          }}
          onHold={() => void holdSelectedSeats()}
          onToggle={toggleSeat}
          seats={seats}
          selected={effective}
          trip={trip}
        />
      )}
      {step === "details" && trip && hold && (
        <PassengerDetailsSection
          hold={hold}
          onChangeSeats={changeSeats}
          onEdit={() => {
            // A new payload needs a new key, or the API rejects the retry as a
            // key reused with different content.
            keyRef.current = null;
          }}
          onExpire={expireHold}
          onSubmit={(event) => void confirmBooking(event)}
          submitting={createBooking.isPending}
          trip={trip}
        />
      )}
      {step === "confirmed" && confirmed && (
        <ConfirmationSection
          booking={confirmed}
          onDone={() => {
            setConfirmed(null);
            setTrip(null);
            setStep("trips");
          }}
        />
      )}
      {(step === "trips" || step === "confirmed") && (
        <MyBookingsSection
          bookings={bookings.data ?? []}
          error={bookings.error}
          loading={bookings.isPending}
          onSubmitProof={(bookingId, file) =>
            void submitPaymentProof(bookingId, file)
          }
          submitting={submitProof.isPending}
        />
      )}
    </main>
  );
}
