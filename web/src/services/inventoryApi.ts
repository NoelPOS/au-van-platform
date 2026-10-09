import { apiBaseUrl } from "./apiBaseUrl";
import type { AuthSession } from "../types/auth";
import { authenticatedFetch } from "./authService";
import { ApiError } from "./bookingApi";
import type { Seat, SeatLayout, Trip, VanRoute, Vehicle } from "../types/inventory";

export async function adminRequest<T>(
  session: AuthSession,
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const response = await authenticatedFetch(
    session,
    `${apiBaseUrl}/api/v1/admin${path}`,
    {
      ...init,
      headers: { "Content-Type": "application/json", ...init.headers },
    },
  );

  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as {
      detail?: string;
      message?: string;
      code?: string;
    } | null;
    throw new ApiError(
      body?.detail ??
        body?.message ??
        body?.code ??
        `Request failed (${response.status}).`,
      response.status,
      body?.code ?? null,
    );
  }

  if (response.status === 204) return undefined as T;

  return (await response.json()) as T;
}

export const inventoryApi = {
  listRoutes: (session: AuthSession) => adminRequest<VanRoute[]>(session, "/routes"),
  listSeatLayouts: (session: AuthSession) =>
    adminRequest<SeatLayout[]>(session, "/seat-layouts"),
  listVehicles: (session: AuthSession) =>
    adminRequest<Vehicle[]>(session, "/vehicles"),
  listTrips: (session: AuthSession) => adminRequest<Trip[]>(session, "/trips"),
  createRoute: (
    session: AuthSession,
    input: Pick<
      VanRoute,
      "origin" | "destination" | "fare" | "durationMinutes"
    >,
  ) =>
    adminRequest<VanRoute>(session, "/routes", {
      method: "POST",
      body: JSON.stringify(input),
    }),
  updateRoute: (session: AuthSession, id: string, input: Partial<VanRoute>) =>
    adminRequest<VanRoute>(session, `/routes/${id}`, {
      method: "PUT",
      body: JSON.stringify(input),
    }),
  createSeatLayout: (
    session: AuthSession,
    input: { name: string; seats: Seat[] },
  ) =>
    adminRequest<SeatLayout>(session, "/seat-layouts", {
      method: "POST",
      body: JSON.stringify(input),
    }),
  updateSeatLayout: (
    session: AuthSession,
    id: string,
    input: { name: string; seats: Seat[] },
  ) =>
    adminRequest<SeatLayout>(session, `/seat-layouts/${id}`, {
      method: "PUT",
      body: JSON.stringify(input),
    }),
  createVehicle: (
    session: AuthSession,
    input: Pick<Vehicle, "code" | "name" | "seatLayoutId">,
  ) =>
    adminRequest<Vehicle>(session, "/vehicles", {
      method: "POST",
      body: JSON.stringify(input),
    }),
  updateVehicle: (
    session: AuthSession,
    id: string,
    input: Pick<Vehicle, "code" | "name" | "seatLayoutId" | "status">,
  ) =>
    adminRequest<Vehicle>(session, `/vehicles/${id}`, {
      method: "PUT",
      body: JSON.stringify(input),
    }),
  createTrip: (
    session: AuthSession,
    input: Pick<Trip, "routeId" | "vehicleId" | "departureAt">,
  ) =>
    adminRequest<Trip>(session, "/trips", {
      method: "POST",
      body: JSON.stringify(input),
    }),
  updateTrip: (
    session: AuthSession,
    id: string,
    input: Pick<Trip, "departureAt" | "status">,
  ) =>
    adminRequest<Trip>(session, `/trips/${id}`, {
      method: "PUT",
      body: JSON.stringify(input),
    }),
};
