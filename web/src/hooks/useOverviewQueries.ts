import { useQuery } from "@tanstack/react-query";
import { operationsApi } from "../services/operationsApi";
import { paymentsApi } from "../services/paymentsApi";
import type { AuthSession } from "../types/auth";

// Own keys, so a count read here never passes for the fresh list another page needs.
const overviewKeys = {
  proofs: ["overview", "payment-proofs"] as const,
  deadLetters: ["overview", "dead-letters"] as const,
  trip: (tripId: string) => ["overview", "trip", tripId] as const,
};

export function useWaitingProofs(session: AuthSession) {
  return useQuery({
    queryKey: overviewKeys.proofs,
    queryFn: () => paymentsApi.listProofs(session),
    staleTime: 0,
  });
}

export function useGivenUpNotifications(session: AuthSession) {
  return useQuery({
    queryKey: overviewKeys.deadLetters,
    queryFn: () => operationsApi.listDeadLetters(session),
    staleTime: 0,
  });
}

export function useTripSeats(session: AuthSession, tripId: string) {
  return useQuery({
    queryKey: overviewKeys.trip(tripId),
    queryFn: () => operationsApi.loadTrip(session, tripId),
    staleTime: 0,
  });
}
