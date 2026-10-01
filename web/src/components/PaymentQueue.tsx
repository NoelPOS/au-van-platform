import type { PaymentProof } from "../types/payments";
import { formatAgo, formatFare, formatWhen } from "../utils/format";

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
      <div className="rounded-2xl border border-dashed border-line px-6 py-12 text-center">
        <strong className="block font-serif text-xl text-brand-900">
          Nothing to review
        </strong>
        <span className="mt-2 block text-sm text-muted">
          Payment slips appear here as students send them.
        </span>
      </div>
    );

  return (
    <ol aria-label="Slips waiting for review" className="flex flex-col gap-3">
      {proofs.map((proof) => {
        const selected = proof.id === selectedId;
        return (
          <li key={proof.id}>
            <button
              aria-current={selected ? "true" : undefined}
              className={`flex w-full overflow-hidden rounded-xl border text-left transition-colors duration-150 ease-out focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500 ${
                selected
                  ? "border-brand-500 bg-brand-50"
                  : "border-line bg-card hover:border-brand-500/50"
              }`}
              onClick={() => onSelect(proof.id)}
              type="button"
            >
              <span className="min-w-0 flex-1 px-4 py-3.5">
                <span className="block font-mono text-xs tracking-wide text-brand-700">
                  {proof.bookingReference}
                </span>
                <span className="mt-1 block truncate font-semibold text-ink">
                  {proof.passengerName}
                </span>
                <span className="mt-0.5 block truncate text-sm text-muted">
                  {`${proof.trip.origin} → ${proof.trip.destination} · ${formatWhen(proof.trip.departureAt)}`}
                </span>
              </span>
              <Perforation />
              <span className="flex w-28 shrink-0 flex-col items-end justify-center gap-1 px-4 py-3.5 text-right">
                <span className="text-sm font-semibold text-ink tabular-nums">
                  {formatFare(proof.totalFare)}
                </span>
                <time
                  className="text-xs text-muted"
                  dateTime={proof.submittedAt}
                  title={formatWhen(proof.submittedAt)}
                >
                  {formatAgo(proof.submittedAt)}
                </time>
              </span>
            </button>
          </li>
        );
      })}
    </ol>
  );
}

function Perforation() {
  return (
    <span
      aria-hidden="true"
      className="relative w-0 self-stretch border-l-2 border-dashed border-line before:absolute before:-top-[9px] before:-left-[7px] before:size-4 before:rounded-full before:border before:border-line before:bg-paper after:absolute after:-bottom-[9px] after:-left-[7px] after:size-4 after:rounded-full after:border after:border-line after:bg-paper"
    />
  );
}
