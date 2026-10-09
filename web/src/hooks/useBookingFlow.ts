import { useEffect, useRef, useState, type FormEvent } from "react";
import { ApiError } from "../services/bookingApi";
import type { AuthSession } from "../types/auth";
import type {
  AvailableTrip,
  Booking,
  BookingStep,
  Notice,
  SeatHold,
} from "../types/booking";
import { messageOf } from "../utils/errors";
import {
  useCreateBooking,
  useHoldSeats,
  useReleaseHold,
  useSeatMap,
} from "./useBookingQueries";

// Mirrors the API's booking.max-seats-per-hold; its 400 is the backstop.
export const maxSeatsPerHold = 4;

export function useBookingFlow(
  session: AuthSession,
  trip: AvailableTrip | null,
  {
    setNotice,
    leave,
    booked,
  }: {
    setNotice: (notice: Notice | null) => void;
    leave: (message: string, to?: "/" | "/tickets") => void;
    booked: (booking: Booking) => void;
  },
) {
  const [step, setStep] = useState<BookingStep>("seats");
  const [selected, setSelected] = useState<string[]>([]);
  const [hold, setHold] = useState<SeatHold | null>(null);
  // One key per booking attempt, kept across a failure so a retry cannot book twice.
  const keyRef = useRef<string | null>(null);

  const seatMap = useSeatMap(
    session,
    trip?.id ?? null,
    step === "seats" && hold === null,
  );
  const holdSeats = useHoldSeats(session);
  const releaseHold = useReleaseHold(session);
  const createBooking = useCreateBooking(session);

  const seats = seatMap.data?.seats ?? [];
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

  const tripIsGone = seatMap.error instanceof ApiError && seatMap.error.status === 404;
  useEffect(() => {
    if (tripIsGone)
      backToTrips("That trip is no longer available. Choose another.");
    // Only on the edge into the 404: backToTrips is redeclared every render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tripIsGone]);

  function backToTrips(message: string) {
    keyRef.current = null;
    leave(message);
  }

  function backToSeats(message: string) {
    setStep("seats");
    setSelected([]);
    setHold(null);
    keyRef.current = null;
    setNotice({ tone: "error", message });
    void seatMap.refetch();
  }

  function releaseCurrentHold() {
    if (hold && trip)
      releaseHold.mutate({ holdId: hold.holdId, tripId: trip.id });
  }

  function toggleSeat(seatId: string) {
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
    releaseCurrentHold();
    setHold(null);
    setSelected([]);
    keyRef.current = null;
    setNotice(null);
    setStep("seats");
  }

  function resetIdempotencyKey() {
    // A changed payload needs a new key, or the API rejects it as a reused one.
    keyRef.current = null;
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
      // No local reset: the page is leaving, and a reset would flash the seat map first.
      keyRef.current = null;
      booked(booking);
    } catch (error) {
      onBookingFailure(error);
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
      // A lost 201 can hide this booking, so land on the list that shows it.
      keyRef.current = null;
      return leave(
        "Those seats are already booked. If that was you, the booking is below.",
        "/tickets",
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
    // Keep the hold and the key: retrying with the same key cannot book twice.
    setNotice({ tone: "error", message: messageOf(error) });
  }

  return {
    step,
    hold,
    seatMap,
    holdSeats,
    createBooking,
    seats,
    effective,
    lostSeatLabels,
    toggleSeat,
    holdSelectedSeats,
    releaseCurrentHold,
    expireHold,
    changeSeats,
    resetIdempotencyKey,
    confirmBooking,
  };
}
