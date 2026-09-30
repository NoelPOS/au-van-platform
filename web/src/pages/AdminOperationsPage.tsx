import { useState } from "react";
import type { AuthSession } from "../types/auth";
import { formatDeparture } from "../utils/format";
import { Button } from "../components/ui/Button";
import { Panel } from "../components/ui/Panel";
import { StatusBadge } from "../components/ui/StatusBadge";
import {
  EmptyRow,
  InventoryTable,
} from "../components/InventoryTable";
import { useRoutes, useTrips } from "../hooks/useInventoryQueries";
import { useDeadLetters, useTripOperations } from "../hooks/useOperationsQueries";
import type { WaitlistPlace } from "../types/operations";

const fieldClass =
  "mt-1 w-full rounded-lg border border-line bg-white px-3 py-2 font-normal text-ink outline-none focus:border-brand";

function Failure({ title, error }: { title: string; error: Error }) {
  return (
    <Panel className="border-red-200 p-5">
      <strong className="block text-ink">{title}</strong>
      <span className="mt-1 block text-muted" role="alert">
        {error.message}
      </span>
    </Panel>
  );
}

/** A place, or a dash for an entry that has ended and holds none. */
function place(entry: WaitlistPlace): string {
  return entry.position === null ? "—" : `#${entry.position}`;
}

/**
 * What an operator can see, and nothing they can press.
 *
 * <p>Every promotion, retry and release already has an owner — the promotion
 * sweep, the outbox dispatcher, the expiry sweep — so this screen reports and
 * those mechanisms act. There is deliberately no button here to promote a
 * student or re-send a dead letter.
 */
export function AdminOperationsPage({ session }: { session: AuthSession }) {
  const [tripId, setTripId] = useState<string | null>(null);
  const trips = useTrips(session);
  const routes = useRoutes(session);
  const operations = useTripOperations(session, tripId);
  const deadLetters = useDeadLetters(session);

  const listing = trips.error ?? routes.error;
  if (trips.isPending || routes.isPending) {
    return (
      <main className="mx-auto max-w-6xl px-6 py-16 text-center text-muted">
        Loading operations…
      </main>
    );
  }

  if (listing) {
    return (
      <main className="mx-auto max-w-xl px-6 py-16">
        <section className="rounded-2xl border border-red-200 bg-white p-6">
          <h1 className="text-2xl font-bold text-ink">
            Could not load operations
          </h1>
          <p className="my-4 text-muted" role="alert">
            {listing.message}
          </p>
          <Button
            onClick={() => {
              void trips.refetch();
              void routes.refetch();
            }}
          >
            Try again
          </Button>
        </section>
      </main>
    );
  }

  const trip = operations.data ?? null;
  const schedule = trips.data ?? [];
  const routeList = routes.data ?? [];

  return (
    <main className="mx-auto max-w-6xl px-6 py-10">
      <header className="mb-8">
        <p className="text-xs font-bold uppercase tracking-widest text-brand">
          AU Van Admin
        </p>
        <h1 className="mt-2 text-4xl font-bold tracking-tight text-ink">
          Operations
        </h1>
        <p className="mt-2 text-muted">
          What a trip&apos;s bookings are doing, who is queued behind it, and
          which notifications were given up on.
        </p>
      </header>

      <label className="mb-6 block max-w-md text-sm font-semibold text-ink">
        Trip
        <select
          className={fieldClass}
          onChange={(event) => setTripId(event.target.value || null)}
          value={tripId ?? ""}
        >
          <option value="">Choose a trip…</option>
          {schedule.map((entry) => {
            const route = routeList.find((value) => value.id === entry.routeId);
            const label = route
              ? `${route.origin} → ${route.destination}`
              : "Unknown route";
            return (
              <option key={entry.id} value={entry.id}>
                {`${formatDeparture(entry.departureAt)} · ${label}`}
              </option>
            );
          })}
        </select>
      </label>

      {tripId === null && (
        <Panel className="mb-8 p-5 text-sm text-muted">
          Choose a trip to see its bookings and waitlist.
        </Panel>
      )}
      {tripId !== null && operations.isPending && (
        <p className="mb-8 text-sm text-muted">Loading this trip…</p>
      )}
      {operations.error && (
        <div className="mb-8">
          <Failure
            title="Could not load this trip"
            error={operations.error}
          />
        </div>
      )}

      {trip && (
        <section className="mb-10 grid items-start gap-5 lg:grid-cols-2">
          <div className="lg:col-span-2">
            <h2 className="text-lg font-bold text-ink">
              {`${trip.origin} → ${trip.destination} · ${formatDeparture(trip.departureAt)}`}
            </h2>
            <p className="mt-1 text-sm text-muted">
              {`${trip.claimedSeats} of ${trip.totalSeats} seats claimed`}
              {" · "}
              <StatusBadge value={trip.tripStatus} />
            </p>
          </div>
          <InventoryTable headings={["Booking status", "Count"]}>
            {trip.bookingsByStatus.map((row) => (
              <tr className="border-t border-line" key={row.status}>
                <td className="px-4 py-3">
                  <StatusBadge value={row.status} />
                </td>
                <td className="px-4 py-3 text-muted">{row.count}</td>
              </tr>
            ))}
          </InventoryTable>
          <InventoryTable
            headings={["Place", "Student", "Seats", "Status", "Joined"]}
          >
            {trip.waitlist.length === 0 && (
              <EmptyRow
                columns={5}
                title="Nobody is waiting"
                detail="Students join the queue once every seat is claimed."
              />
            )}
            {trip.waitlist.map((entry) => (
              <tr className="border-t border-line" key={entry.entryId}>
                <td className="px-4 py-3 font-semibold text-ink">
                  {place(entry)}
                </td>
                <td className="px-4 py-3 text-muted">
                  {entry.displayName ?? entry.userId}
                </td>
                <td className="px-4 py-3 text-muted">{entry.seatsWanted}</td>
                <td className="px-4 py-3">
                  <StatusBadge value={entry.status} />
                  {entry.promotionExpiresAt && (
                    <span className="mt-1 block text-xs text-muted">
                      {`Offer ends ${formatDeparture(entry.promotionExpiresAt)}`}
                    </span>
                  )}
                </td>
                <td className="px-4 py-3 text-muted">
                  {formatDeparture(entry.joinedAt)}
                </td>
              </tr>
            ))}
          </InventoryTable>
        </section>
      )}

      <h2 className="mb-3 text-lg font-bold text-ink">Dead letters</h2>
      {deadLetters.isPending && (
        <p className="text-sm text-muted">Loading dead letters…</p>
      )}
      {deadLetters.error && (
        <Failure
          title="Could not load dead letters"
          error={deadLetters.error}
        />
      )}
      {deadLetters.data && (
        <InventoryTable
          headings={["Event", "About", "Attempts", "Last error", "Given up"]}
        >
          {deadLetters.data.length === 0 && (
            <EmptyRow
              columns={5}
              title="Nothing was given up on"
              detail="A notification appears here only once its attempts are spent."
            />
          )}
          {deadLetters.data.map((letter) => (
            <tr className="border-t border-line" key={letter.id}>
              <td className="px-4 py-3 font-semibold text-ink">
                {letter.eventType}
              </td>
              <td className="px-4 py-3 text-muted">{letter.aggregateId}</td>
              <td className="px-4 py-3 text-muted">{letter.attempts}</td>
              <td className="px-4 py-3 text-muted">{letter.lastError ?? "—"}</td>
              <td className="px-4 py-3 text-muted">
                {letter.processedAt ? formatDeparture(letter.processedAt) : "—"}
              </td>
            </tr>
          ))}
        </InventoryTable>
      )}
    </main>
  );
}
