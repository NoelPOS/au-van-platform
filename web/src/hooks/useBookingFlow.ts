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
import { bookingFailure, holdFailure, type BookingFailure } from "../utils/bookingFailures";
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
    leave: (message: string, to?: string) => void;
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
  const selectedSeatIds = seatMap.data
    ? selected.filter((seatId) => takeable.has(seatId))
    : selected;
  const lostSeatLabels = seats
    .filter((seat) => selected.includes(seat.id) && !takeable.has(seat.id))
    .map((seat) => seat.label);

  const tripIsGone = seatMap.error instanceof ApiError && seatMap.error.status === 404;
  useEffect(() => {
    if (tripIsGone)
      leaveFlow("That trip is no longer available. Choose another.");
    // Only on the edge into the 404: leaveFlow is redeclared every render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tripIsGone]);

  function leaveFlow(message: string, to?: string) {
    keyRef.current = null;
    leave(message, to);
  }

  function startOver() {
    setStep("seats");
    setSelected([]);
    setHold(null);
    keyRef.current = null;
  }

  function backToSeats(notice: Notice) {
    startOver();
    setNotice(notice);
    void seatMap.refetch();
  }

  function recover(failure: BookingFailure) {
    if (failure.action === "leave") return leaveFlow(failure.message, failure.to);
    if (failure.action === "chooseSeats")
      return backToSeats({ tone: "error", message: failure.message });
    // Keep the hold and, unless refused, the key: a same-key retry cannot book twice.
    if (failure.newKey) keyRef.current = null;
    setNotice({ tone: "error", message: failure.message });
  }

  function releaseCurrentHold() {
    if (hold && trip)
      releaseHold.mutate({ holdId: hold.holdId, tripId: trip.id });
  }

  function toggleSeat(seatId: string) {
    if (selectedSeatIds.includes(seatId)) {
      setNotice(null);
      setSelected(selectedSeatIds.filter((id) => id !== seatId));
      return;
    }
    if (selectedSeatIds.length === maxSeatsPerHold) {
      setNotice({
        tone: "status",
        message: `You can hold at most ${maxSeatsPerHold} seats at a time.`,
      });
      return;
    }
    setNotice(null);
    setSelected([...selectedSeatIds, seatId]);
  }

  async function holdSelectedSeats() {
    if (!trip || selectedSeatIds.length === 0) return;
    setNotice(null);
    try {
      const created = await holdSeats.mutateAsync({
        tripId: trip.id,
        seatIds: selectedSeatIds,
      });
      setHold(created);
      setSelected(created.seats.map((seat) => seat.seatId));
      keyRef.current = null;
      setStep("details");
    } catch (error) {
      recover(holdFailure(error));
    }
  }

  function expireHold() {
    backToSeats({
      tone: "status",
      message: "Your seat hold expired. Choose your seats again.",
    });
  }

  function changeSeats() {
    releaseCurrentHold();
    startOver();
    setNotice(null);
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
      recover(bookingFailure(error));
    }
  }

  return {
    step,
    hold,
    seatMap,
    holdSeats,
    createBooking,
    seats,
    selectedSeatIds,
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
