import { useState } from "react";
import { useApproveProof, useRejectProof } from "../hooks/usePaymentQueries";
import type { AuthSession } from "../types/auth";
import type { PaymentProof } from "../types/payments";
import { formatFare, formatWhen } from "../utils/format";
import { ProofImage } from "./ProofImage";
import { Button } from "./ui/Button";

// Mirrors the API's own ceiling; its 400 is the backstop.
const maxNoteLength = 500;

const reasons = [
  { label: "Wrong amount", note: "The amount on the slip does not match the fare." },
  { label: "Unreadable", note: "The slip is too blurred to read." },
  { label: "Wrong account", note: "The transfer went to a different account." },
];

export function SlipReview({
  proof,
  session,
  onBack,
  onDecided,
}: {
  proof: PaymentProof;
  session: AuthSession;
  onBack: () => void;
  onDecided: () => void;
}) {
  const [note, setNote] = useState("");
  const [noteError, setNoteError] = useState<string | null>(null);
  const approve = useApproveProof(session);
  const reject = useRejectProof(session);
  const busy = approve.isPending || reject.isPending;
  const failure = approve.error ?? reject.error ?? null;

  async function decide(decision: "approve" | "reject") {
    const trimmed = note.trim();
    if (decision === "reject" && trimmed === "") {
      setNoteError("Say why you are sending it back, so the student can fix it.");
      return;
    }
    setNoteError(null);
    const mutation = decision === "approve" ? approve : reject;
    try {
      await mutation.mutateAsync({ proofId: proof.id, note: trimmed });
      onDecided();
    } catch {
    }
  }

  return (
    <article className="rounded-2xl border border-line bg-card">
      <header className="border-b border-dashed border-line p-5 sm:p-6">
        <button
          className="mb-4 inline-flex min-h-11 items-center gap-2 text-sm font-semibold text-brand-500 underline underline-offset-4 lg:hidden"
          onClick={onBack}
          type="button"
        >
          <span aria-hidden="true">←</span>
          Back to the queue
        </button>
        <p className="text-xs font-semibold tracking-[0.14em] text-muted uppercase">
          Slip under review
        </p>
        <h2 className="mt-1 font-mono text-lg font-semibold text-brand-900 sm:text-xl">
          {proof.bookingReference}
        </h2>
        <dl className="mt-4 grid grid-cols-3 gap-x-6 gap-y-3 text-sm">
          <Fact label="Passenger" value={proof.passengerName} />
          <Fact label="Phone" value={proof.passengerPhone} />
          <Fact label="Fare" value={formatFare(proof.totalFare)} />
          <Fact
            className="col-span-3"
            label="Trip"
            value={`${proof.trip.origin} → ${proof.trip.destination} · ${formatWhen(proof.trip.departureAt)}`}
          />
        </dl>
      </header>
      <div className="grid gap-6 p-5 sm:p-6 xl:grid-cols-[minmax(0,1fr)_18rem]">
        <ProofImage proofId={proof.id} reference={proof.bookingReference} session={session} />
        <div className="flex flex-col gap-3">
          <label className="text-sm font-semibold text-ink" htmlFor="review-note">
            Note to the student
          </label>
          <div aria-label="Common reasons to send a slip back" className="flex flex-wrap gap-2" role="group">
            {reasons.map((reason) => (
              <button
                className="min-h-9 rounded-full border border-line bg-paper px-3 text-xs font-semibold text-ink hover:border-brand-500 focus-visible:outline-2 focus-visible:outline-brand-500"
                key={reason.label}
                onClick={() => setNote(reason.note)}
                type="button"
              >
                {reason.label}
              </button>
            ))}
          </div>
          <textarea
            className="w-full rounded-lg border border-line bg-paper px-3 py-2 text-sm text-ink outline-none focus:border-brand-500 focus-visible:outline-2 focus-visible:outline-brand-500"
            id="review-note"
            maxLength={maxNoteLength}
            onChange={(event) => setNote(event.target.value)}
            rows={4}
            value={note}
          />
          {noteError && <Alert message={noteError} />}
          {failure && <Alert message={failure.message} />}
          <div className="mt-1 flex flex-col gap-2 sm:flex-row xl:flex-col">
            <Button disabled={busy} onClick={() => void decide("approve")}>
              {busy ? "Saving…" : "Approve payment"}
            </Button>
            <Button disabled={busy} onClick={() => void decide("reject")} variant="secondary">
              Send back to student
            </Button>
          </div>
        </div>
      </div>
    </article>
  );
}

function Fact({
  label,
  value,
  className = "",
}: {
  label: string;
  value: string;
  className?: string;
}) {
  return (
    <div className={`min-w-0 ${className}`}>
      <dt className="text-xs tracking-[0.14em] text-muted uppercase">{label}</dt>
      <dd className="mt-0.5 truncate font-medium text-ink tabular-nums">{value}</dd>
    </div>
  );
}

function Alert({ message }: { message: string }) {
  return (
    <p
      className="rounded-lg border border-danger/30 bg-danger/5 px-3 py-2 text-sm text-danger"
      role="alert"
    >
      {message}
    </p>
  );
}
