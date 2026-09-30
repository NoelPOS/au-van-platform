import { useState } from "react";
import { DeadLettersTable } from "../components/DeadLettersTable";
import { TripOperationsPanel } from "../components/TripOperationsPanel";
import { Button } from "../components/ui/Button";
import { FailurePanel } from "../components/ui/FailurePanel";
import { Panel } from "../components/ui/Panel";
import { useRoutes, useTrips } from "../hooks/useInventoryQueries";
import {
  useDeadLetters,
  useTripOperations,
} from "../hooks/useOperationsQueries";
import type { AuthSession } from "../types/auth";
import { formatDeparture } from "../utils/format";

const fieldClass =
  "mt-1 w-full rounded-lg border border-line bg-white px-3 py-2 font-normal text-ink outline-none focus:border-brand";

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
          <FailurePanel
            title="Could not load this trip"
            error={operations.error}
          />
        </div>
      )}

      {trip && <TripOperationsPanel trip={trip} />}

      <DeadLettersTable
        error={deadLetters.error}
        letters={deadLetters.data}
        loading={deadLetters.isPending}
      />
    </main>
  );
}
