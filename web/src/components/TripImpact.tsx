import { useTripOperations } from "../hooks/useOperationsQueries";
import type { AuthSession } from "../types/auth";
import type { BookingStatus } from "../types/booking";
import type { TripOperations } from "../types/operations";
import { Skeleton } from "./ui/Skeleton";

const booked: BookingStatus[] = ["PENDING_PAYMENT", "PAYMENT_UNDER_REVIEW", "PAYMENT_REJECTED", "CONFIRMED"];
const paid: BookingStatus[] = ["PAYMENT_UNDER_REVIEW", "CONFIRMED"];

function impactOf(trip: TripOperations) {
  const count = (statuses: BookingStatus[]) =>
    trip.bookingsByStatus
      .filter((entry) => statuses.includes(entry.status))
      .reduce((sum, entry) => sum + entry.count, 0);
  return {
    passengers: count(booked),
    paid: count(paid),
    waiting: trip.waitlist.filter((entry) => entry.position !== null).length,
  };
}

function Row({ count, children }: { count: number; children: string }) {
  return (
    <li className="grid grid-cols-[2.5rem_minmax(0,1fr)] items-baseline gap-3 px-4 py-3">
      <span className="font-display text-[28px] leading-none text-brand-900 tabular-nums">{count}</span>{" "}
      <span className="text-sm text-ink">{children}</span>
    </li>
  );
}

export function TripImpact({
  session,
  tripId,
  change,
}: {
  session: AuthSession;
  tripId: string;
  change: "cancel" | "move";
}) {
  const operations = useTripOperations(session, tripId);

  if (operations.isPending) return <Skeleton className="h-16" />;
  if (operations.error)
    return (
      <p className="rounded-xl border border-warning/25 bg-warning-soft px-4 py-3 text-sm text-warning" role="alert">
        Could not count who is booked on this trip. Check it under Operations before you go ahead.
      </p>
    );

  const { passengers, paid: paidCount, waiting } = impactOf(operations.data);
  const one = passengers === 1;

  if (change === "move")
    return (
      <p className="rounded-xl bg-paper px-4 py-3 text-sm text-ink">
        {passengers === 0
          ? "Nobody has booked this departure yet."
          : `${passengers} booked ${one ? "passenger gets" : "passengers get"} a LINE message with the new time.`}
      </p>
    );

  if (passengers + waiting === 0)
    return (
      <p className="rounded-xl bg-paper px-4 py-3 text-sm text-ink">
        Nobody has booked or queued for this departure.
      </p>
    );

  return (
    <section aria-label="Who this affects" className="rounded-xl border border-line bg-paper/60">
      <h3 className="px-4 pt-3 font-mono text-[10px] tracking-[0.16em] text-muted uppercase">Who this affects</h3>
      <ul className="divide-y divide-dashed divide-line">
        <Row count={passengers}>
          {`booked ${one ? "passenger loses their seat" : "passengers lose their seats"} and ${one ? "gets" : "get"} a LINE message with your reason`}
        </Row>
        <Row count={paidCount}>
          {paidCount === 0
            ? "have paid, so there is nothing to refund"
            : `${paidCount === 1 ? "has paid, so a refund falls" : "have paid, so refunds fall"} due under Refunds`}
        </Row>
        {waiting > 0 && (
          <Row count={waiting}>
            {`${waiting === 1 ? "student waiting for a seat is" : "students waiting for a seat are"} taken off the queue and told`}
          </Row>
        )}
      </ul>
    </section>
  );
}
