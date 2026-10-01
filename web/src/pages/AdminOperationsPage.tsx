import { useRef, useState } from "react";
import { DeadLettersTable } from "../components/DeadLettersTable";
import { TripOperationsPanel } from "../components/TripOperationsPanel";
import { TripTimeline } from "../components/TripTimeline";
import { Button } from "../components/ui/Button";
import { EmptyState } from "../components/ui/EmptyState";
import { FailurePanel } from "../components/ui/FailurePanel";
import { PageHeader } from "../components/ui/PageHeader";
import { Panel } from "../components/ui/Panel";
import { Skeleton } from "../components/ui/Skeleton";
import { useRoutes, useTrips } from "../hooks/useInventoryQueries";
import {
  useDeadLetters,
  useTripOperations,
} from "../hooks/useOperationsQueries";
import type { AuthSession } from "../types/auth";

const page = "mx-auto max-w-6xl px-5 py-8 sm:px-8 lg:px-12 lg:py-14";

export function AdminOperationsPage({ session }: { session: AuthSession }) {
  const [tripId, setTripId] = useState<string | null>(null);
  const trips = useTrips(session);
  const routes = useRoutes(session);
  const operations = useTripOperations(session, tripId);
  const deadLetters = useDeadLetters(session);
  const detail = useRef<HTMLElement>(null);

  function choose(id: string) {
    setTripId(id);
    if (window.matchMedia("(min-width: 1024px)").matches) return;
    const still = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    detail.current?.scrollIntoView({ block: "start", behavior: still ? "auto" : "smooth" });
    detail.current?.focus({ preventScroll: true });
  }

  const listing = trips.error ?? routes.error;
  if (trips.isPending || routes.isPending) {
    return (
      <main className={page}>
        <div className="grid gap-6 lg:grid-cols-[20rem_minmax(0,1fr)]" role="status">
          <span className="sr-only">Loading operations…</span>
          <Skeleton className="h-96" />
          <Skeleton className="h-96" />
        </div>
      </main>
    );
  }

  if (listing) {
    return (
      <main className={page}>
        <Panel className="max-w-xl p-6">
          <h1 className="font-display text-2xl font-light text-brand-900">
            Could not load operations
          </h1>
          <p className="mt-2 mb-5 text-muted" role="alert">
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
        </Panel>
      </main>
    );
  }

  const trip = operations.data ?? null;

  return (
    <main className={page}>
      <PageHeader
        description="What a trip's bookings are doing, who is queued behind it, and which notifications were given up on."
        eyebrow="Dispatch"
        title="Operations"
      />

      <div className="mt-10 grid items-start gap-8 lg:grid-cols-[20rem_minmax(0,1fr)]">
        <TripTimeline
          onSelect={choose}
          routes={routes.data ?? []}
          selectedId={tripId}
          trips={trips.data ?? []}
        />
        <section
          aria-label="Selected trip"
          className="scroll-mt-20 outline-none"
          ref={detail}
          tabIndex={-1}
        >
          {tripId === null && (
            <div className="rounded-2xl border border-dashed border-line">
              <EmptyState title="Choose a trip to see its bookings and waitlist." />
            </div>
          )}
          {tripId !== null && operations.isPending && (
            <div role="status">
              <span className="sr-only">Loading this trip…</span>
              <Skeleton className="h-96" />
            </div>
          )}
          {operations.error && (
            <FailurePanel title="Could not load this trip" error={operations.error} />
          )}
          {trip && <TripOperationsPanel session={session} trip={trip} />}
        </section>
      </div>

      <DeadLettersTable
        error={deadLetters.error}
        letters={deadLetters.data}
        loading={deadLetters.isPending}
      />
    </main>
  );
}
