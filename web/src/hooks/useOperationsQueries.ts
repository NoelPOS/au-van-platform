import { useQuery } from "@tanstack/react-query";
import type { AuthSession } from "../types/auth";
import { operationsApi } from "../services/operationsApi";

const operationsKeys = {
  trip: (tripId: string) => ["operations", "trip", tripId] as const,
  deadLetters: ["operations", "dead-letters"] as const,
};

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
