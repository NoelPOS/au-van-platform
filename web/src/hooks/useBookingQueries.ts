import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { AuthSession } from "../types/auth";
import type { Booking } from "../types/booking";
import { bookingApi } from "../services/bookingApi";

const bookingKeys = {
  trips: ["booking", "trips"] as const,
  seatMap: (tripId: string) => ["booking", "seat-map", tripId] as const,
  bookings: ["booking", "bookings"] as const,
  waitlist: ["booking", "waitlist"] as const,
  eligibility: ["booking", "eligibility"] as const,
};

export function useBookingEligibility(session: AuthSession) {
  return useQuery({
    queryKey: bookingKeys.eligibility,
    queryFn: () => bookingApi.getEligibility(session),
    staleTime: 5_000,
    refetchInterval: 60_000,
  });
}

export function useAvailableTrips(session: AuthSession) {
  return useQuery({
    queryKey: bookingKeys.trips,
    queryFn: () => bookingApi.listTrips(session),
    refetchInterval: 30_000,
  });
}

// Never cached: the app-wide staleTime would offer seats that are already gone.
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
    staleTime: 5_000,
    refetchInterval: (query) =>
      query.state.data?.some(awaitsDecision) ? 15_000 : 60_000,
  });
}

function awaitsDecision(booking: Booking): boolean {
  return (
    booking.status === "PENDING_PAYMENT" ||
    booking.status === "PAYMENT_UNDER_REVIEW" ||
    booking.status === "PAYMENT_REJECTED"
  );
}

export function useCancelBooking(session: AuthSession) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (bookingId: string) =>
      bookingApi.cancelBooking(session, bookingId),
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: bookingKeys.bookings });
      void queryClient.invalidateQueries({ queryKey: bookingKeys.eligibility });
      void queryClient.invalidateQueries({ queryKey: bookingKeys.trips });
    },
  });
}

export function useMyWaitlist(session: AuthSession) {
  return useQuery({
    queryKey: bookingKeys.waitlist,
    queryFn: () => bookingApi.listWaitlist(session),
    refetchInterval: 30_000,
  });
}

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
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: bookingKeys.bookings });
      void queryClient.invalidateQueries({ queryKey: bookingKeys.eligibility });
    },
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
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: bookingKeys.bookings });
      void queryClient.invalidateQueries({ queryKey: bookingKeys.eligibility });
      void queryClient.invalidateQueries({ queryKey: bookingKeys.trips });
    },
  });
}
