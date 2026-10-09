import type { PaymentProof } from "../types/payments";
import { bangkokDateTime } from "../utils/dates";
import { formatAgo } from "../utils/format";
import { Chip } from "./ui/Chip";
import { EmptyState } from "./ui/EmptyState";
import { RouteLine } from "./ui/RouteLine";
import { TicketStub } from "./ui/TicketStub";

export function PaymentQueue({
  proofs,
  selectedId,
  onSelect,
}: {
  proofs: PaymentProof[];
  selectedId: string | null;
  onSelect: (proofId: string) => void;
}) {
  if (proofs.length === 0)
    return (
      <div className="rounded-2xl border border-dashed border-line">
        <EmptyState
          detail="Payment slips appear here as students send them."
          title="Nothing to review"
        />
      </div>
    );

  return (
    <ol aria-label="Slips waiting for review" className="flex flex-col gap-3">
      {proofs.map((proof) => {
        const selected = proof.id === selectedId;
        return (
          <li key={proof.id}>
            <TicketStub
              className={selected ? "ring-2 ring-brand-500" : "hover:ring-1 hover:ring-brand-500/40"}
              stub={
                <>
                  <span className="font-display text-[1.75rem] leading-none font-light text-brand-900 tabular-nums">
                    {proof.totalFare}
                  </span>
                  <span className="mt-2 text-[11px] font-semibold tracking-[0.14em] text-muted uppercase">
                    THB
                  </span>
                </>
              }
            >
              <div className="flex flex-wrap items-center gap-2">
                <button
                  aria-current={selected ? "true" : undefined}
                  className="font-mono text-[13px] font-medium text-brand-700 after:absolute after:inset-0 focus-visible:outline-none focus-visible:after:outline-2 focus-visible:after:outline-brand-500"
                  onClick={() => onSelect(proof.id)}
                  type="button"
                >
                  {proof.bookingReference}
                </button>
                {proof.sameSlipBookings.length > 0 && <Chip tone="warning">Possible reuse</Chip>}
              </div>
              <p className="mt-1.5 truncate font-medium text-ink">{proof.passengerName}</p>
              <p className="mt-1 text-sm text-muted">
                <RouteLine destination={proof.trip.destination} origin={proof.trip.origin} />
              </p>
              <p className="mt-2 flex flex-wrap gap-x-3 text-xs text-muted tabular-nums">
                <span>{bangkokDateTime(proof.trip.departureAt)}</span>
                <time dateTime={proof.submittedAt} title={bangkokDateTime(proof.submittedAt)}>
                  {`Sent ${formatAgo(proof.submittedAt)}`}
                </time>
              </p>
            </TicketStub>
          </li>
        );
      })}
    </ol>
  );
}
