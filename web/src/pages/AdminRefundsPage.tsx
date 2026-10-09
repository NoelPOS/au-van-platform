import { RefundRow } from "../components/RefundRow";
import { Button } from "../components/ui/Button";
import { EmptyState } from "../components/ui/EmptyState";
import { FailurePanel } from "../components/ui/FailurePanel";
import { PageHeader } from "../components/ui/PageHeader";
import { Skeleton } from "../components/ui/Skeleton";
import { useRefundsDue } from "../hooks/useRefundQueries";
import type { AuthSession } from "../types/auth";
import { formatBaht } from "../utils/format";

export function AdminRefundsPage({ session }: { session: AuthSession }) {
  const refunds = useRefundsDue(session);
  const due = refunds.data ?? [];
  const total = due.reduce((sum, booking) => sum + booking.totalFare, 0);

  return (
    <main className="mx-auto max-w-6xl px-5 py-8 sm:px-8 lg:px-12 lg:py-14">
      <PageHeader
        description="Fares owed back after a cancellation, oldest first. Send each one, then record it here."
        eyebrow="Payments"
        title="Refunds"
      />
      <div className="mt-8 max-w-3xl">
        {refunds.isPending && (
          <div className="flex flex-col gap-3" role="status">
            <span className="sr-only">Loading refunds…</span>
            <Skeleton className="h-28" />
            <Skeleton className="h-28" />
          </div>
        )}
        {refunds.error && (
          <FailurePanel error={refunds.error} title="Could not load refunds">
            <Button onClick={() => void refunds.refetch()} variant="secondary">
              Try again
            </Button>
          </FailurePanel>
        )}
        {refunds.data && due.length === 0 && (
          <div className="rounded-2xl border border-dashed border-line">
            <EmptyState
              detail="A refund appears here when a paid booking is cancelled."
              title="Nothing to refund"
            />
          </div>
        )}
        {due.length > 0 && (
          <>
            <p className="mb-3 font-mono text-[12px] text-muted tabular-nums">
              {`${due.length} to send · ${formatBaht(total)} in total`}
            </p>
            <ol
              aria-label="Refunds due"
              className="divide-y divide-dashed divide-line overflow-hidden rounded-2xl border border-line bg-card"
            >
              {due.map((booking) => (
                <RefundRow booking={booking} key={booking.id} session={session} />
              ))}
            </ol>
          </>
        )}
      </div>
    </main>
  );
}
