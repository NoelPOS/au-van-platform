import { useQuery } from "@tanstack/react-query";
import type { AuthSession } from "../types/auth";
import { bookingApi } from "../services/bookingApi";
import { operationsApi } from "../services/operationsApi";

const operationsKeys = {
  trip: (tripId: string) => ["operations", "trip", tripId] as const,
  seats: (tripId: string) => ["operations", "seats", tripId] as const,
};

export function useTripOperations(session: AuthSession, tripId: string | null) {
  return useQuery({
    queryKey: operationsKeys.trip(tripId ?? ""),
    queryFn: () => operationsApi.loadTrip(session, tripId as string),
    enabled: tripId !== null,
  });
}

export function useTripSeatStates(session: AuthSession, tripId: string) {
  return useQuery({
    queryKey: operationsKeys.seats(tripId),
    queryFn: () => bookingApi.getSeatMap(session, tripId),
    staleTime: 0,
  });
}
