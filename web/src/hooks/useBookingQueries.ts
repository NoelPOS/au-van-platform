import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { AuthSession } from "../services/authService";
import { bookingApi } from "../services/bookingApi";

const bookingKeys = {
  trips: ["booking", "trips"] as const,
  seatMap: (tripId: string) => ["booking", "seat-map", tripId] as const,
  bookings: ["booking", "bookings"] as const,
  waitlist: ["booking", "waitlist"] as const,
};

export function useAvailableTrips(session: AuthSession) {
  return useQuery({
    queryKey: bookingKeys.trips,
    queryFn: () => bookingApi.listTrips(session),
  });
}

/**
 * The seat map is never served from cache: the application-wide staleTime of
 * thirty seconds would hand a returning student a map that no longer matches
 * the trip, and they would pick a seat that is already gone.
 */
export function useSeatMap(
  session: AuthSession,
  tripId: string | null,
  polling: boolean,
) {
  return useQuery({
    queryKey: bookingKeys.seatMap(tripId ?? ""),
    queryFn: () => bookingApi.getSeatMap(session, tripId ?? ""),
    enabled: tripId !== null,
    staleTime: 0,
    refetchInterval: polling ? 10_000 : false,
  });
}

export function useMyBookings(session: AuthSession) {
  return useQuery({
    queryKey: bookingKeys.bookings,
    queryFn: () => bookingApi.listBookings(session),
  });
}

export function useMyWaitlist(session: AuthSession) {
  return useQuery({
    queryKey: bookingKeys.waitlist,
    queryFn: () => bookingApi.listWaitlist(session),
  });
}

/**
 * Both waitlist mutations invalidate the trip list as well as the queue: a
 * position is derived from the queue on the server, and joining or leaving is
 * also the moment to find out whether the trip is still full.
 */
export function useJoinWaitlist(session: AuthSession) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { tripId: string; seatsWanted: number }) =>
      bookingApi.joinWaitlist(session, input),
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: bookingKeys.waitlist });
      void queryClient.invalidateQueries({ queryKey: bookingKeys.trips });
    },
  });
}

export function useLeaveWaitlist(session: AuthSession) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { entryId: string }) =>
      bookingApi.leaveWaitlist(session, input.entryId),
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: bookingKeys.waitlist });
      void queryClient.invalidateQueries({ queryKey: bookingKeys.trips });
    },
  });
}

export function useHoldSeats(session: AuthSession) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { tripId: string; seatIds: string[] }) =>
      bookingApi.holdSeats(session, input),
    // Refresh on failure too: a rejected hold means somebody else took a seat.
    onSettled: (_data, _error, input) =>
      queryClient.invalidateQueries({
        queryKey: bookingKeys.seatMap(input.tripId),
      }),
  });
}

export function useReleaseHold(session: AuthSession) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { holdId: string; tripId: string }) =>
      bookingApi.releaseHold(session, input.holdId),
    onSettled: (_data, _error, input) =>
      queryClient.invalidateQueries({
        queryKey: bookingKeys.seatMap(input.tripId),
      }),
  });
}

export function useSubmitPaymentProof(session: AuthSession) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (variables: { bookingId: string; file: File }) =>
      bookingApi.submitPaymentProof(session, variables.bookingId, variables.file),
    // The booking comes back with its new status, but the list beside it is
    // what the student is looking at.
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: bookingKeys.bookings }),
  });
}

export function useCreateBooking(session: AuthSession) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (variables: {
      idempotencyKey: string;
      input: { holdId: string; passengerName: string; passengerPhone: string };
    }) =>
      bookingApi.createBooking(
        session,
        variables.idempotencyKey,
        variables.input,
      ),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: bookingKeys.bookings }),
  });
}
