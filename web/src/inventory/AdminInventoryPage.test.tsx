import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AuthSession } from '../auth/session'
import { AdminInventoryPage } from './AdminInventoryPage'

const session: AuthSession = {
  accessToken: 'admin-token',
  expiresIn: 900,
  user: { id: 'admin-id', role: 'ADMIN', displayName: 'Noel' },
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('AdminInventoryPage', () => {
  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  it('loads the route section and sends a new route to the admin API', async () => {
    const fetcher = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      if (String(input).endsWith('/routes') && init?.method === 'POST') {
        return json({ id: 'route-1', origin: 'AU', destination: 'Mega Bangna', fare: 35, durationMinutes: 45, status: 'ACTIVE' }, 201)
      }
      return json([])
    })
    vi.stubGlobal('fetch', fetcher)

    render(<AdminInventoryPage session={session} />)

    expect(await screen.findByText('No routes yet')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Origin'), { target: { value: 'AU' } })
    fireEvent.change(screen.getByLabelText('Destination'), { target: { value: 'Mega Bangna' } })
    fireEvent.click(screen.getByRole('button', { name: 'Create route' }))

    await vi.waitFor(() => expect(fetcher).toHaveBeenCalledWith(
      '/api/v1/admin/routes',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ origin: 'AU', destination: 'Mega Bangna', fare: 0, durationMinutes: 30 }) }),
    ))
  })

  it('shows a retryable failure when the inventory request is rejected', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(() => Promise.resolve(json({ detail: 'Access denied.' }, 403))))

    render(<AdminInventoryPage session={session} />)

    expect(await screen.findByText('Could not load inventory')).toBeInTheDocument()
    expect(screen.getByText('Access denied.')).toBeInTheDocument()
  })
})
