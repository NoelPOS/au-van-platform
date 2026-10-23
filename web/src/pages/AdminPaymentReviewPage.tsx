import { useState } from "react";
import { PaymentQueue } from "../components/PaymentQueue";
import { RouteFilter } from "../components/RouteFilter";
import { SlipReview } from "../components/SlipReview";
import { Button } from "../components/ui/Button";
import { EmptyState } from "../components/ui/EmptyState";
import { PageHeader } from "../components/ui/PageHeader";
import { Panel } from "../components/ui/Panel";
import { Skeleton } from "../components/ui/Skeleton";
import { useRoutes, useTrips } from "../hooks/useInventoryQueries";
import { usePaymentProofs } from "../hooks/usePaymentQueries";
import { useRouteFilter } from "../hooks/useRouteFilter";
import type { AuthSession } from "../types/auth";
import type { PaymentProof } from "../types/payments";
import { routeIdentities, routesIn } from "../utils/routeIdentity";

const page = "mx-auto max-w-6xl px-5 py-8 sm:px-8 lg:px-12 lg:py-14";

export function AdminPaymentReviewPage({ session }: { session: AuthSession }) {
  const [reviewing, setReviewing] = useState<string | null>(null);
  const proofs = usePaymentProofs(session);
  const routes = useRoutes(session);
  const trips = useTrips(session);
  const filter = useRouteFilter();

  if (proofs.isPending) {
    return (
      <main className={page}>
        <div className="flex flex-col gap-3 lg:max-w-md" role="status">
          <span className="sr-only">Loading payment proofs…</span>
          <Skeleton className="mb-7 h-28" />
          <Skeleton className="h-28" />
          <Skeleton className="h-28" />
        </div>
      </main>
    );
  }

  if (proofs.error) {
    return (
      <main className={page}>
        <Panel className="max-w-xl p-6">
          <h1 className="font-display text-2xl font-light text-brand-900">
            Could not load payment proofs
          </h1>
          <p className="mt-2 mb-5 text-muted" role="alert">
            {proofs.error.message}
          </p>
          <Button onClick={() => void proofs.refetch()}>Try again</Button>
        </Panel>
      </main>
    );
  }

  const queue = proofs.data;
  const selected = queue.find((proof) => proof.id === reviewing) ?? null;
  const routeOf = (proof: PaymentProof) =>
    trips.data?.find((trip) => trip.id === proof.trip.id)?.routeId ?? "";
  const shown = queue.filter((proof) => filter.shows(routeOf(proof)));

  return (
    <main className={page}>
      <PageHeader
        description="Check each payment slip, then approve the booking or send it back with a reason."
        eyebrow="Payments"
        title="Payment review"
      />
      <RouteFilter
        chosen={filter.chosen}
        className="mt-6"
        onChoose={filter.choose}
        routes={routesIn(routeIdentities(routes.data ?? []), [
          ...queue.map(routeOf),
          ...filter.chosen,
        ])}
      />
      <div className="mt-8 grid items-start gap-6 lg:grid-cols-[minmax(18rem,24rem)_minmax(0,1fr)]">
        <section
          aria-label="Review queue"
          className={selected ? "hidden lg:block" : undefined}
        >
          <p className="mb-3 text-[11px] font-semibold tracking-[0.14em] text-muted uppercase tabular-nums">
            {`${shown.length} waiting`}
          </p>
          <PaymentQueue
            onSelect={setReviewing}
            proofs={shown}
            selectedId={reviewing}
          />
        </section>
        {selected ? (
          <SlipReview
            key={selected.id}
            onBack={() => setReviewing(null)}
            onDecided={() => setReviewing(null)}
            proof={selected}
            session={session}
          />
        ) : (
          <div className="hidden rounded-2xl border border-dashed border-line lg:block">
            <EmptyState title="Choose a booking reference to see its payment slip." />
          </div>
        )}
      </div>
    </main>
  );
}
