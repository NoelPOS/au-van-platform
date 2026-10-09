import { ApiError } from "../services/bookingApi";
import type { AuthSession } from "../types/auth";
import type { AvailableTrip, Notice, WaitlistEntry } from "../types/booking";
import { messageOf } from "../utils/errors";
import {
  useJoinWaitlist,
  useLeaveWaitlist,
  useMyWaitlist,
} from "./useBookingQueries";

export function useWaitlist(
  session: AuthSession,
  setNotice: (notice: Notice | null) => void,
) {
  const entries = useMyWaitlist(session);
  const joining = useJoinWaitlist(session);
  const leaving = useLeaveWaitlist(session);

  async function join(trip: AvailableTrip) {
    setNotice(null);
    try {
      const entry = await joining.mutateAsync({
        tripId: trip.id,
        seatsWanted: 1,
      });
      setNotice({
        tone: "status",
        message: `You are number ${entry.position} on the waitlist for ${trip.origin} → ${trip.destination}.`,
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

  async function leave(entry: WaitlistEntry) {
    setNotice(null);
    try {
      await leaving.mutateAsync({ entryId: entry.id });
      setNotice({ tone: "status", message: "You have left the waitlist." });
    } catch (error) {
      // A 404 means the entry is already gone, which is what was asked for.
      if (error instanceof ApiError && error.status === 404) return;
      setNotice({ tone: "error", message: messageOf(error) });
    }
  }

  return {
    entries,
    pending: joining.isPending || leaving.isPending,
    join,
    leave,
  };
}
