import { NextDepartures } from "../components/NextDepartures";
import { OverviewCounter } from "../components/OverviewCounter";
import { PageHeader } from "../components/ui/PageHeader";
import { Skeleton } from "../components/ui/Skeleton";
import { useRoutes, useTrips, useVehicles } from "../hooks/useInventoryQueries";
import {
  useGivenUpNotifications,
  useWaitingProofs,
} from "../hooks/useOverviewQueries";
import type { AuthSession } from "../types/auth";
import { bangkokDay } from "../utils/dates";

export function AdminOverviewPage({ session }: { session: AuthSession }) {
  const trips = useTrips(session);
  const routes = useRoutes(session);
  const vehicles = useVehicles(session);
  const proofs = useWaitingProofs(session);
  const deadLetters = useGivenUpNotifications(session);
  const board = [trips, routes, vehicles];
  const boardError = board.find((query) => query.error)?.error;

  return (
    <main className="mx-auto max-w-6xl px-5 py-8 sm:px-8 lg:px-12 lg:py-14">
      <PageHeader
        description="What leaves next, and what is waiting on you."
        eyebrow={`${bangkokDay(new Date())} · Bangkok`}
        title="Overview"
      />
      <div className="mt-10 grid items-start gap-10 lg:grid-cols-[minmax(0,1.7fr)_minmax(16rem,1fr)]">
        <section aria-labelledby="next-departures">
          <h2
            className="mb-4 font-display text-2xl font-light text-brand-900"
            id="next-departures"
          >
            Next departures
          </h2>
          {boardError ? (
            <p className="text-sm text-danger" role="alert">
              {boardError.message}
            </p>
          ) : board.some((query) => query.isPending) ? (
            <div className="flex flex-col gap-3">
              <Skeleton className="h-28" />
              <Skeleton className="h-28" />
            </div>
          ) : (
            <NextDepartures
              routes={routes.data ?? []}
              session={session}
              trips={trips.data ?? []}
              vehicles={vehicles.data ?? []}
            />
          )}
        </section>
        <div className="flex flex-col gap-4 lg:pt-12">
          <OverviewCounter
            action="Review slips"
            query={proofs}
            settled="Every slip has been reviewed."
            title="Slips waiting for review"
            to="/admin/payments"
            waiting="Students are waiting on a decision."
          />
          <OverviewCounter
            action="Open operations"
            query={deadLetters}
            settled="Every notification went out."
            title="Notifications given up on"
            to="/admin/operations"
            waiting="These never reached the student."
          />
        </div>
      </div>
    </main>
  );
}
