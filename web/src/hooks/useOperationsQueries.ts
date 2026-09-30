import { useQuery } from "@tanstack/react-query";
import type { AuthSession } from "../services/authService";
import { operationsApi } from "../services/operationsApi";

const operationsKeys = {
  trip: (tripId: string) => ["operations", "trip", tripId] as const,
  deadLetters: ["operations", "dead-letters"] as const,
};

/**
 * One trip's operational picture. Nothing is read until a trip is chosen: the
 * screen opens with no trip selected, and `enabled` is what keeps it from
 * asking the API about the empty string.
 */
export function useTripOperations(session: AuthSession, tripId: string | null) {
  return useQuery({
    queryKey: operationsKeys.trip(tripId ?? ""),
    queryFn: () => operationsApi.loadTrip(session, tripId as string),
    enabled: tripId !== null,
  });
}

export function useDeadLetters(session: AuthSession) {
  return useQuery({
    queryKey: operationsKeys.deadLetters,
    queryFn: () => operationsApi.listDeadLetters(session),
  });
}
