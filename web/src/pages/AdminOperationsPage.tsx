import { useState } from "react";
import { DeadLettersTable } from "../components/DeadLettersTable";
import { TripOperationsPanel } from "../components/TripOperationsPanel";
import { TripTimeline } from "../components/TripTimeline";
import { Button } from "../components/ui/Button";
import { FailurePanel } from "../components/ui/FailurePanel";
import { useRoutes, useTrips } from "../hooks/useInventoryQueries";
import {
  useDeadLetters,
  useTripOperations,
} from "../hooks/useOperationsQueries";
import type { AuthSession } from "../types/auth";

const skeleton = "rounded-2xl border border-line bg-card motion-safe:animate-pulse";

export function AdminOperationsPage({ session }: { session: AuthSession }) {
  const [tripId, setTripId] = useState<string | null>(null);
  const trips = useTrips(session);
  const routes = useRoutes(session);
  const operations = useTripOperations(session, tripId);
  const deadLetters = useDeadLetters(session);

  const listing = trips.error ?? routes.error;
  if (trips.isPending || routes.isPending) {
    return (
      <main aria-busy="true" className="mx-auto max-w-7xl px-4 py-10 sm:px-6">
        <p className="sr-only">Loading operations…</p>
        <div className="grid gap-6 lg:grid-cols-[20rem_minmax(0,1fr)]">
          <div className={`h-96 ${skeleton}`} />
          <div className={`h-96 ${skeleton}`} />
        </div>
      </main>
    );
  }

  if (listing) {
    return (
      <main className="mx-auto max-w-xl px-4 py-16 sm:px-6">
        <section className="rounded-2xl border border-danger/30 bg-card p-6">
          <h1 className="font-serif text-2xl text-brand-900">
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

  return (
    <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:py-12">
      <header className="mb-10 max-w-2xl">
        <p className="text-xs font-semibold tracking-[0.14em] text-brand-500 uppercase">
          Dispatch
        </p>
        <h1 className="mt-2 font-serif text-4xl text-brand-900 sm:text-5xl">
          Operations
        </h1>
        <p className="mt-3 text-muted">
          What a trip&apos;s bookings are doing, who is queued behind it, and
          which notifications were given up on.
        </p>
      </header>

      <div className="grid items-start gap-8 lg:grid-cols-[20rem_minmax(0,1fr)]">
        <TripTimeline
          onSelect={setTripId}
          routes={routes.data ?? []}
          selectedId={tripId}
          trips={trips.data ?? []}
        />
        <div>
          {tripId === null && (
            <div className="flex min-h-64 items-center justify-center rounded-2xl border border-dashed border-line p-8 text-center">
              <p className="max-w-xs font-serif text-xl text-muted italic">
                Choose a trip to see its bookings and waitlist.
              </p>
            </div>
          )}
          {tripId !== null && operations.isPending && (
            <div className={`h-96 ${skeleton}`}>
              <p className="sr-only">Loading this trip…</p>
            </div>
          )}
          {operations.error && (
            <FailurePanel title="Could not load this trip" error={operations.error} />
          )}
          {trip && <TripOperationsPanel trip={trip} />}
        </div>
      </div>

      <DeadLettersTable
        error={deadLetters.error}
        letters={deadLetters.data}
        loading={deadLetters.isPending}
      />
    </main>
  );
}
