import { useCallback, useEffect, useState, type FormEvent, type ReactNode } from 'react'
import type { AuthSession } from '../auth/session'
import { inventoryApi, loadInventory } from './inventory-api'
import type { Inventory, Seat, SeatLayout, Trip, VanRoute, Vehicle } from './types'

type Tab = 'routes' | 'seatLayouts' | 'vehicles' | 'trips'

type Props = {
  session: AuthSession
}

const tabs: { id: Tab; label: string; description: string }[] = [
  { id: 'routes', label: 'Routes', description: 'Journeys, fares, and durations' },
  { id: 'seatLayouts', label: 'Seat layouts', description: 'Reusable van seating plans' },
  { id: 'vehicles', label: 'Vehicles', description: 'Physical vans and their layouts' },
  { id: 'trips', label: 'Trips', description: 'Scheduled van departures' },
]

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'Something went wrong. Please try again.'
}

function toLocalDateTime(value: string): string {
  const date = new Date(value)
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16)
}

function formatTripDate(value: string): string {
  return new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

function StatusBadge({ children }: { children: ReactNode }) {
  return <span className={`status-badge ${String(children).toLowerCase()}`}>{children}</span>
}

function EmptyState({ title, detail }: { title: string; detail: string }) {
  return <div className="empty-state"><strong>{title}</strong><span>{detail}</span></div>
}

function FormCard({ title, children }: { title: string; children: ReactNode }) {
  return <section className="form-card"><h2>{title}</h2>{children}</section>
}

function FormActions({ busy, submitLabel, onCancel }: { busy: boolean; submitLabel: string; onCancel?: () => void }) {
  return <div className="form-actions">
    {onCancel && <button className="button secondary" type="button" onClick={onCancel}>Cancel</button>}
    <button className="button" disabled={busy} type="submit">{busy ? 'Saving…' : submitLabel}</button>
  </div>
}

export function AdminInventoryPage({ session }: Props) {
  const [tab, setTab] = useState<Tab>('routes')
  const [inventory, setInventory] = useState<Inventory | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const refresh = useCallback(async () => {
    try {
      const nextInventory = await loadInventory(session)
      setInventory(nextInventory)
      setLoadError(null)
    } catch (error) {
      setLoadError(errorMessage(error))
    }
  }, [session])

  useEffect(() => {
    const timer = window.setTimeout(() => { void refresh() }, 0)
    return () => window.clearTimeout(timer)
  }, [refresh])

  async function save(action: () => Promise<unknown>) {
    setActionError(null)
    setBusy(true)
    try {
      await action()
      await refresh()
      return true
    } catch (error) {
      setActionError(errorMessage(error))
      return false
    } finally {
      setBusy(false)
    }
  }

  if (!inventory && !loadError) {
    return <main className="admin-shell"><p className="loading">Loading transport inventory…</p></main>
  }

  if (!inventory) {
    return <main className="admin-shell"><section className="error-panel"><h1>Could not load inventory</h1><p>{loadError}</p><button className="button" onClick={() => void refresh()}>Try again</button></section></main>
  }

  return (
    <main className="admin-shell">
      <header className="admin-header">
        <div><p className="eyebrow">AU Van Admin</p><h1>Transport inventory</h1><p>Set up the routes, vans, seats, and departures that bookings will use.</p></div>
        <div className="admin-user">Signed in as {session.user.displayName ?? 'Administrator'}</div>
      </header>

      <nav className="admin-tabs" aria-label="Transport inventory sections">
        {tabs.map((item) => <button key={item.id} className={tab === item.id ? 'active' : ''} onClick={() => { setTab(item.id); setActionError(null) }}><strong>{item.label}</strong><span>{item.description}</span></button>)}
      </nav>

      {actionError && <div className="error-banner" role="alert">{actionError}</div>}

      {tab === 'routes' && <RoutesSection routes={inventory.routes} busy={busy} save={save} session={session} />}
      {tab === 'seatLayouts' && <SeatLayoutsSection layouts={inventory.seatLayouts} busy={busy} save={save} session={session} />}
      {tab === 'vehicles' && <VehiclesSection vehicles={inventory.vehicles} layouts={inventory.seatLayouts} busy={busy} save={save} session={session} />}
      {tab === 'trips' && <TripsSection trips={inventory.trips} routes={inventory.routes} vehicles={inventory.vehicles} busy={busy} save={save} session={session} />}
    </main>
  )
}

function RoutesSection({ routes, busy, save, session }: { routes: VanRoute[]; busy: boolean; save: (action: () => Promise<unknown>) => Promise<boolean>; session: AuthSession }) {
  const [editing, setEditing] = useState<VanRoute | null>(null)
  const route = editing ?? { origin: '', destination: '', fare: 0, durationMinutes: 30, status: 'ACTIVE' as const }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const input = { origin: String(form.get('origin')).trim(), destination: String(form.get('destination')).trim(), fare: Number(form.get('fare')), durationMinutes: Number(form.get('durationMinutes')) }
    const succeeded = await save(() => editing
      ? inventoryApi.updateRoute(session, editing.id, { ...input, status: String(form.get('status')) as VanRoute['status'] })
      : inventoryApi.createRoute(session, input))
    if (succeeded) setEditing(null)
  }

  return <section className="section-grid"><FormCard title={editing ? 'Edit route' : 'New route'}><form key={editing?.id ?? 'new'} onSubmit={submit} className="form-grid">
    <label>Origin<input required name="origin" defaultValue={route.origin} placeholder="Assumption University" /></label>
    <label>Destination<input required name="destination" defaultValue={route.destination} placeholder="Mega Bangna" /></label>
    <label>Fare (THB)<input required name="fare" type="number" min="0" step="0.01" defaultValue={route.fare} /></label>
    <label>Duration (minutes)<input required name="durationMinutes" type="number" min="1" defaultValue={route.durationMinutes} /></label>
    {editing && <label>Status<select name="status" defaultValue={route.status}><option>ACTIVE</option><option>INACTIVE</option></select></label>}
    <FormActions busy={busy} submitLabel={editing ? 'Save route' : 'Create route'} onCancel={editing ? () => setEditing(null) : undefined} />
  </form></FormCard><InventoryTable headings={['Route', 'Fare', 'Duration', 'Status', '']}>
    {routes.length === 0 ? <EmptyRow columns={5}><EmptyState title="No routes yet" detail="Create a route before scheduling a trip." /></EmptyRow> : routes.map((item) => <tr key={item.id}><td><strong>{item.origin} → {item.destination}</strong></td><td>{item.fare.toFixed(2)} THB</td><td>{item.durationMinutes} min</td><td><StatusBadge>{item.status}</StatusBadge></td><td><button className="text-button" onClick={() => setEditing(item)}>Edit</button></td></tr>)}
  </InventoryTable></section>
}

function parseSeats(value: string): Seat[] {
  const seats = value.split('\n').filter(Boolean).map((line) => {
    const [label, rowNumber, columnNumber] = line.split(',').map((part) => part.trim())
    return { label, rowNumber: Number(rowNumber), columnNumber: Number(columnNumber) }
  })
  if (!seats.length || seats.some((seat) => !seat.label || !Number.isInteger(seat.rowNumber) || !Number.isInteger(seat.columnNumber))) {
    throw new Error('Use one seat per line: label, row number, column number. Example: A1, 1, 1')
  }
  return seats
}

function SeatLayoutsSection({ layouts, busy, save, session }: { layouts: SeatLayout[]; busy: boolean; save: (action: () => Promise<unknown>) => Promise<boolean>; session: AuthSession }) {
  const [editing, setEditing] = useState<SeatLayout | null>(null)
  const [seatError, setSeatError] = useState<string | null>(null)
  const layout = editing ?? { name: '', seats: [] }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    let seats: Seat[]
    try {
      seats = parseSeats(String(form.get('seats')))
      setSeatError(null)
    } catch (error) {
      setSeatError(errorMessage(error))
      return
    }
    const input = { name: String(form.get('name')).trim(), seats }
    const succeeded = await save(() => editing ? inventoryApi.updateSeatLayout(session, editing.id, input) : inventoryApi.createSeatLayout(session, input))
    if (succeeded) setEditing(null)
  }

  return <section className="section-grid"><FormCard title={editing ? 'Edit seat layout' : 'New seat layout'}><form key={editing?.id ?? 'new'} onSubmit={submit} className="form-grid">
    <label>Layout name<input required name="name" defaultValue={layout.name} placeholder="Toyota Hiace 12-seat" /></label>
    <label className="full-width">Seats<textarea required name="seats" defaultValue={layout.seats.map((seat) => `${seat.label}, ${seat.rowNumber}, ${seat.columnNumber}`).join('\n')} placeholder={'A1, 1, 1\nA2, 1, 2'} /><small>One seat per line: label, row number, column number.</small></label>
    {seatError && <p className="field-error full-width" role="alert">{seatError}</p>}
    <FormActions busy={busy} submitLabel={editing ? 'Save layout' : 'Create layout'} onCancel={editing ? () => setEditing(null) : undefined} />
  </form></FormCard><InventoryTable headings={['Layout', 'Seats', 'Preview', '']}>
    {layouts.length === 0 ? <EmptyRow columns={4}><EmptyState title="No seat layouts yet" detail="Create a reusable layout before adding a vehicle." /></EmptyRow> : layouts.map((item) => <tr key={item.id}><td><strong>{item.name}</strong></td><td>{item.seats.length}</td><td>{item.seats.map((seat) => seat.label).join(', ')}</td><td><button className="text-button" onClick={() => setEditing(item)}>Edit</button></td></tr>)}
  </InventoryTable></section>
}

function VehiclesSection({ vehicles, layouts, busy, save, session }: { vehicles: Vehicle[]; layouts: SeatLayout[]; busy: boolean; save: (action: () => Promise<unknown>) => Promise<boolean>; session: AuthSession }) {
  const [editing, setEditing] = useState<Vehicle | null>(null)
  const vehicle = editing ?? { code: '', name: '', seatLayoutId: layouts[0]?.id ?? '', status: 'ACTIVE' as const }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const input = { code: String(form.get('code')).trim(), name: String(form.get('name')).trim(), seatLayoutId: String(form.get('seatLayoutId')), status: String(form.get('status')) as Vehicle['status'] }
    const succeeded = await save(() => editing ? inventoryApi.updateVehicle(session, editing.id, input) : inventoryApi.createVehicle(session, input))
    if (succeeded) setEditing(null)
  }

  return <section className="section-grid"><FormCard title={editing ? 'Edit vehicle' : 'New vehicle'}><form key={editing?.id ?? 'new'} onSubmit={submit} className="form-grid">
    <label>Vehicle code<input required name="code" defaultValue={vehicle.code} placeholder="VAN-01" /></label><label>Name<input required name="name" defaultValue={vehicle.name} placeholder="White Toyota Hiace" /></label>
    <label>Seat layout<select required name="seatLayoutId" defaultValue={vehicle.seatLayoutId} disabled={!layouts.length}><option value="">Select a layout</option>{layouts.map((layout) => <option key={layout.id} value={layout.id}>{layout.name}</option>)}</select></label>
    {editing && <label>Status<select name="status" defaultValue={vehicle.status}><option>ACTIVE</option><option>INACTIVE</option></select></label>}
    {!layouts.length && <p className="form-note">Create a seat layout first.</p>}<FormActions busy={busy} submitLabel={editing ? 'Save vehicle' : 'Create vehicle'} onCancel={editing ? () => setEditing(null) : undefined} />
  </form></FormCard><InventoryTable headings={['Code', 'Vehicle', 'Seat layout', 'Status', '']}>
    {vehicles.length === 0 ? <EmptyRow columns={5}><EmptyState title="No vehicles yet" detail="Add a van after creating its seat layout." /></EmptyRow> : vehicles.map((item) => <tr key={item.id}><td><strong>{item.code}</strong></td><td>{item.name}</td><td>{layouts.find((layout) => layout.id === item.seatLayoutId)?.name ?? 'Unknown layout'}</td><td><StatusBadge>{item.status}</StatusBadge></td><td><button className="text-button" onClick={() => setEditing(item)}>Edit</button></td></tr>)}
  </InventoryTable></section>
}

function TripsSection({ trips, routes, vehicles, busy, save, session }: { trips: Trip[]; routes: VanRoute[]; vehicles: Vehicle[]; busy: boolean; save: (action: () => Promise<unknown>) => Promise<boolean>; session: AuthSession }) {
  const [editing, setEditing] = useState<Trip | null>(null)
  const trip = editing ?? { routeId: routes[0]?.id ?? '', vehicleId: vehicles[0]?.id ?? '', departureAt: '', status: 'SCHEDULED' as const }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const departureAt = new Date(String(form.get('departureAt'))).toISOString()
    const succeeded = await save(() => editing
      ? inventoryApi.updateTrip(session, editing.id, { departureAt, status: String(form.get('status')) as Trip['status'] })
      : inventoryApi.createTrip(session, { routeId: String(form.get('routeId')), vehicleId: String(form.get('vehicleId')), departureAt }))
    if (succeeded) setEditing(null)
  }

  const ready = routes.length > 0 && vehicles.length > 0
  return <section className="section-grid"><FormCard title={editing ? 'Edit trip' : 'Schedule trip'}><form key={editing?.id ?? 'new'} onSubmit={submit} className="form-grid">
    {!editing && <><label>Route<select required name="routeId" defaultValue={trip.routeId} disabled={!ready}>{routes.map((route) => <option key={route.id} value={route.id}>{route.origin} → {route.destination}</option>)}</select></label><label>Vehicle<select required name="vehicleId" defaultValue={trip.vehicleId} disabled={!ready}>{vehicles.map((vehicle) => <option key={vehicle.id} value={vehicle.id}>{vehicle.code} — {vehicle.name}</option>)}</select></label></>}
    <label>Departure<input required name="departureAt" type="datetime-local" defaultValue={trip.departureAt ? toLocalDateTime(trip.departureAt) : ''} /></label>
    {editing && <label>Status<select name="status" defaultValue={trip.status}><option>SCHEDULED</option><option>CANCELLED</option></select></label>}
    {!ready && !editing && <p className="form-note">Create at least one route and vehicle first.</p>}<FormActions busy={busy} submitLabel={editing ? 'Save trip' : 'Schedule trip'} onCancel={editing ? () => setEditing(null) : undefined} />
  </form></FormCard><InventoryTable headings={['Departure', 'Route', 'Vehicle', 'Seats', 'Status', '']}>
    {trips.length === 0 ? <EmptyRow columns={6}><EmptyState title="No trips yet" detail="Schedule a departure when routes and vehicles are ready." /></EmptyRow> : trips.map((item) => { const route = routes.find((value) => value.id === item.routeId); const vehicle = vehicles.find((value) => value.id === item.vehicleId); return <tr key={item.id}><td><strong>{formatTripDate(item.departureAt)}</strong></td><td>{route ? `${route.origin} → ${route.destination}` : 'Unknown route'}</td><td>{vehicle?.code ?? 'Unknown vehicle'}</td><td>{item.seats.length}</td><td><StatusBadge>{item.status}</StatusBadge></td><td><button className="text-button" onClick={() => setEditing(item)}>Edit</button></td></tr> })}
  </InventoryTable></section>
}

function InventoryTable({ headings, children }: { headings: string[]; children: ReactNode }) {
  return <section className="table-card"><div className="table-scroll"><table><thead><tr>{headings.map((heading, index) => <th key={`${heading}-${index}`}>{heading}</th>)}</tr></thead><tbody>{children}</tbody></table></div></section>
}

function EmptyRow({ columns, children }: { columns: number; children: ReactNode }) {
  return <tr><td colSpan={columns}>{children}</td></tr>
}
