import { ApiError } from "../services/bookingApi";
import type { AuthSession } from "../types/auth";
import type { AvailableTrip, Notice, WaitlistEntry } from "../types/booking";
import { messageOf } from "../utils/errors";
import {
  useJoinWaitlist,
  useLeaveWaitlist,
  useMyWaitlist,
  useSubmitPaymentProof,
} from "./useBookingQueries";

export function useWaitlistAndProof(
  session: AuthSession,
  setNotice: (notice: Notice | null) => void,
) {
  const waitlist = useMyWaitlist(session);
  const submitProof = useSubmitPaymentProof(session);
  const joinWaitlist = useJoinWaitlist(session);
  const leaveWaitlist = useLeaveWaitlist(session);

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

  return {
    waitlist,
    submitProof,
    waitlistPending: joinWaitlist.isPending || leaveWaitlist.isPending,
    joinTheWaitlist,
    leaveTheWaitlist,
    submitPaymentProof,
  };
}
