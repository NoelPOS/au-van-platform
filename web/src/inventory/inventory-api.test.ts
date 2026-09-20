import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AuthSession } from '../auth/session'
import { inventoryApi } from './inventory-api'

const session: AuthSession = {
  accessToken: 'admin-token',
  expiresIn: 900,
  user: { id: 'admin-id', role: 'ADMIN', displayName: 'Noel' },
}

function json(body: unknown) {
  return new Response(JSON.stringify(body), { headers: { 'Content-Type': 'application/json' } })
}

describe('inventory API client', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('maps every inventory write to its protected API endpoint', async () => {
    const fetcher = vi.fn().mockImplementation(() => Promise.resolve(json({})))
    vi.stubGlobal('fetch', fetcher)

    await inventoryApi.createSeatLayout(session, { name: 'Hiace', seats: [{ label: 'A1', rowNumber: 1, columnNumber: 1 }] })
    await inventoryApi.updateSeatLayout(session, 'layout-1', { name: 'Hiace', seats: [{ label: 'A1', rowNumber: 1, columnNumber: 1 }] })
    await inventoryApi.createVehicle(session, { code: 'VAN-01', name: 'White van', seatLayoutId: 'layout-1' })
    await inventoryApi.updateVehicle(session, 'vehicle-1', { code: 'VAN-01', name: 'White van', seatLayoutId: 'layout-1', status: 'ACTIVE' })
    await inventoryApi.createTrip(session, { routeId: 'route-1', vehicleId: 'vehicle-1', departureAt: '2026-10-01T01:00:00.000Z' })
    await inventoryApi.updateTrip(session, 'trip-1', { departureAt: '2026-10-01T01:00:00.000Z', status: 'CANCELLED' })

    expect(fetcher.mock.calls.map(([url, options]) => [url, (options as RequestInit).method])).toEqual([
      ['/api/v1/admin/seat-layouts', 'POST'],
      ['/api/v1/admin/seat-layouts/layout-1', 'PUT'],
      ['/api/v1/admin/vehicles', 'POST'],
      ['/api/v1/admin/vehicles/vehicle-1', 'PUT'],
      ['/api/v1/admin/trips', 'POST'],
      ['/api/v1/admin/trips/trip-1', 'PUT'],
    ])
    const firstRequest = fetcher.mock.calls[0][1] as RequestInit
    expect(new Headers(firstRequest.headers).get('Authorization')).toBe('Bearer admin-token')
  })
})
