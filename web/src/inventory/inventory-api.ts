import { apiBaseUrl } from '../api-base-url'
import { authenticatedFetch, type AuthSession } from '../auth/session'
import type { Inventory, Seat, SeatLayout, Trip, VanRoute, Vehicle } from './types'

async function request<T>(session: AuthSession, path: string, init: RequestInit = {}): Promise<T> {
  const response = await authenticatedFetch(session, `${apiBaseUrl}/api/v1/admin${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init.headers },
  })

  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as { detail?: string; message?: string; code?: string } | null
    throw new Error(body?.detail ?? body?.message ?? body?.code ?? `Request failed (${response.status}).`)
  }

  return (await response.json()) as T
}

export function loadInventory(session: AuthSession): Promise<Inventory> {
  return Promise.all([
    request<VanRoute[]>(session, '/routes'),
    request<SeatLayout[]>(session, '/seat-layouts'),
    request<Vehicle[]>(session, '/vehicles'),
    request<Trip[]>(session, '/trips'),
  ]).then(([routes, seatLayouts, vehicles, trips]) => ({ routes, seatLayouts, vehicles, trips }))
}

export const inventoryApi = {
  createRoute: (session: AuthSession, input: Pick<VanRoute, 'origin' | 'destination' | 'fare' | 'durationMinutes'>) =>
    request<VanRoute>(session, '/routes', { method: 'POST', body: JSON.stringify(input) }),
  updateRoute: (session: AuthSession, id: string, input: Partial<VanRoute>) =>
    request<VanRoute>(session, `/routes/${id}`, { method: 'PUT', body: JSON.stringify(input) }),
  createSeatLayout: (session: AuthSession, input: { name: string; seats: Seat[] }) =>
    request<SeatLayout>(session, '/seat-layouts', { method: 'POST', body: JSON.stringify(input) }),
  updateSeatLayout: (session: AuthSession, id: string, input: { name: string; seats: Seat[] }) =>
    request<SeatLayout>(session, `/seat-layouts/${id}`, { method: 'PUT', body: JSON.stringify(input) }),
  createVehicle: (session: AuthSession, input: Pick<Vehicle, 'code' | 'name' | 'seatLayoutId'>) =>
    request<Vehicle>(session, '/vehicles', { method: 'POST', body: JSON.stringify(input) }),
  updateVehicle: (session: AuthSession, id: string, input: Pick<Vehicle, 'code' | 'name' | 'seatLayoutId' | 'status'>) =>
    request<Vehicle>(session, `/vehicles/${id}`, { method: 'PUT', body: JSON.stringify(input) }),
  createTrip: (session: AuthSession, input: Pick<Trip, 'routeId' | 'vehicleId' | 'departureAt'>) =>
    request<Trip>(session, '/trips', { method: 'POST', body: JSON.stringify(input) }),
  updateTrip: (session: AuthSession, id: string, input: Pick<Trip, 'departureAt' | 'status'>) =>
    request<Trip>(session, `/trips/${id}`, { method: 'PUT', body: JSON.stringify(input) }),
}
