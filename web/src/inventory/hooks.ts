import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { AuthSession } from "../auth/session";
import { inventoryApi } from "./inventory-api";
import type { Seat, Trip, VanRoute, Vehicle } from "./types";

const inventoryKeys = {
  routes: ["inventory", "routes"] as const,
  seatLayouts: ["inventory", "seat-layouts"] as const,
  vehicles: ["inventory", "vehicles"] as const,
  trips: ["inventory", "trips"] as const,
};

export function useRoutes(session: AuthSession) {
  return useQuery({
    queryKey: inventoryKeys.routes,
    queryFn: () => inventoryApi.listRoutes(session),
  });
}

export function useSeatLayouts(session: AuthSession) {
  return useQuery({
    queryKey: inventoryKeys.seatLayouts,
    queryFn: () => inventoryApi.listSeatLayouts(session),
  });
}

export function useVehicles(session: AuthSession) {
  return useQuery({
    queryKey: inventoryKeys.vehicles,
    queryFn: () => inventoryApi.listVehicles(session),
  });
}

export function useTrips(session: AuthSession) {
  return useQuery({
    queryKey: inventoryKeys.trips,
    queryFn: () => inventoryApi.listTrips(session),
  });
}

function useInventoryMutation<TVariables>(
  key: readonly string[],
  mutationFn: (variables: TVariables) => Promise<unknown>,
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: key }),
  });
}

export function useCreateRoute(session: AuthSession) {
  return useInventoryMutation<
    Pick<VanRoute, "origin" | "destination" | "fare" | "durationMinutes">
  >(inventoryKeys.routes, (input) => inventoryApi.createRoute(session, input));
}

export function useUpdateRoute(session: AuthSession) {
  return useInventoryMutation<{ id: string; input: Partial<VanRoute> }>(
    inventoryKeys.routes,
    ({ id, input }) => inventoryApi.updateRoute(session, id, input),
  );
}

export function useCreateSeatLayout(session: AuthSession) {
  return useInventoryMutation<{ name: string; seats: Seat[] }>(
    inventoryKeys.seatLayouts,
    (input) => inventoryApi.createSeatLayout(session, input),
  );
}

export function useUpdateSeatLayout(session: AuthSession) {
  return useInventoryMutation<{
    id: string;
    input: { name: string; seats: Seat[] };
  }>(inventoryKeys.seatLayouts, ({ id, input }) =>
    inventoryApi.updateSeatLayout(session, id, input),
  );
}

export function useCreateVehicle(session: AuthSession) {
  return useInventoryMutation<Pick<Vehicle, "code" | "name" | "seatLayoutId">>(
    inventoryKeys.vehicles,
    (input) => inventoryApi.createVehicle(session, input),
  );
}

export function useUpdateVehicle(session: AuthSession) {
  return useInventoryMutation<{
    id: string;
    input: Pick<Vehicle, "code" | "name" | "seatLayoutId" | "status">;
  }>(inventoryKeys.vehicles, ({ id, input }) =>
    inventoryApi.updateVehicle(session, id, input),
  );
}

export function useCreateTrip(session: AuthSession) {
  return useInventoryMutation<
    Pick<Trip, "routeId" | "vehicleId" | "departureAt">
  >(inventoryKeys.trips, (input) => inventoryApi.createTrip(session, input));
}

export function useUpdateTrip(session: AuthSession) {
  return useInventoryMutation<{
    id: string;
    input: Pick<Trip, "departureAt" | "status">;
  }>(inventoryKeys.trips, ({ id, input }) =>
    inventoryApi.updateTrip(session, id, input),
  );
}
