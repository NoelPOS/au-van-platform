import { useState } from "react";
import { PaymentQueue } from "../components/PaymentQueue";
import { SlipReview } from "../components/SlipReview";
import { Button } from "../components/ui/Button";
import { usePaymentProofs } from "../hooks/usePaymentQueries";
import type { AuthSession } from "../types/auth";

export function AdminPaymentReviewPage({ session }: { session: AuthSession }) {
  const [reviewing, setReviewing] = useState<string | null>(null);
  const proofs = usePaymentProofs(session);

  if (proofs.isPending) {
    return (
      <main aria-busy="true" className="mx-auto max-w-7xl px-4 py-10 sm:px-6">
        <p className="sr-only">Loading payment proofs…</p>
        <div className="h-24 max-w-md rounded-xl bg-brand-50 motion-safe:animate-pulse" />
        <div className="mt-8 grid gap-3 lg:max-w-md">
          {[0, 1, 2].map((row) => (
            <div className="h-24 rounded-xl border border-line bg-card motion-safe:animate-pulse" key={row} />
          ))}
        </div>
      </main>
    );
  }

  if (proofs.error) {
    return (
      <main className="mx-auto max-w-xl px-4 py-16 sm:px-6">
        <section className="rounded-2xl border border-danger/30 bg-card p-6">
          <h1 className="font-serif text-2xl text-brand-900">
            Could not load payment proofs
          </h1>
          <p className="my-4 text-muted" role="alert">
            {proofs.error.message}
          </p>
          <Button onClick={() => void proofs.refetch()}>Try again</Button>
        </section>
      </main>
    );
  }

  const queue = proofs.data;
  const selected = queue.find((proof) => proof.id === reviewing) ?? null;

  return (
    <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:py-12">
      <header className="mb-8 max-w-2xl">
        <p className="text-xs font-semibold tracking-[0.14em] text-brand-500 uppercase">
          Payments
        </p>
        <h1 className="mt-2 font-serif text-4xl text-brand-900 sm:text-5xl">
          Payment review
        </h1>
        <p className="mt-3 text-muted">
          Check each payment slip, then approve the booking or send it back with
          a reason.
        </p>
      </header>
      <div className="grid items-start gap-6 lg:grid-cols-[minmax(20rem,26rem)_minmax(0,1fr)]">
        <section
          aria-label="Review queue"
          className={selected ? "hidden lg:block" : undefined}
        >
          <p className="mb-3 text-xs font-semibold tracking-[0.14em] text-muted uppercase tabular-nums">
            {`${queue.length} waiting`}
          </p>
          <PaymentQueue
            onSelect={setReviewing}
            proofs={queue}
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
          <div className="hidden min-h-80 items-center justify-center rounded-2xl border border-dashed border-line p-8 text-center lg:flex">
            <p className="max-w-xs font-serif text-xl text-muted italic">
              Choose a booking reference to see its payment slip.
            </p>
          </div>
        )}
      </div>
    </main>
  );
}
