import { useState } from "react";
import type { AuthSession } from "../types/auth";
import { formatDeparture, formatFare } from "../utils/format";
import { Button } from "../components/ui/Button";
import { Panel } from "../components/ui/Panel";
import { EmptyRow, InventoryTable } from "../components/InventoryTable";
import { ProofImage } from "../components/ProofImage";
import { useApproveProof, usePaymentProofs, useRejectProof } from "../hooks/usePaymentQueries";
import type { PaymentProof } from "../types/payments";

// Mirrors the API's own ceiling; its 400 is the backstop.
const maxNoteLength = 500;

export function AdminPaymentReviewPage({ session }: { session: AuthSession }) {
  const [reviewing, setReviewing] = useState<string | null>(null);
  const [note, setNote] = useState("");
  const [noteError, setNoteError] = useState<string | null>(null);
  const proofs = usePaymentProofs(session);
  const approve = useApproveProof(session);
  const reject = useRejectProof(session);

  const queue = proofs.data ?? [];
  const selected = queue.find((proof) => proof.id === reviewing) ?? null;
  const busy = approve.isPending || reject.isPending;
  const failure = approve.error ?? reject.error ?? null;

  function review(proof: PaymentProof) {
    setReviewing(proof.id);
    setNote("");
    setNoteError(null);
    approve.reset();
    reject.reset();
  }

  async function decide(decision: "approve" | "reject") {
    if (!selected) return;
    const trimmed = note.trim();
    if (decision === "reject" && trimmed === "") {
      setNoteError("Say why the slip was rejected, so the student can fix it.");
      return;
    }
    setNoteError(null);
    const mutation = decision === "approve" ? approve : reject;
    try {
      await mutation.mutateAsync({ proofId: selected.id, note: trimmed });
      setReviewing(null);
      setNote("");
    } catch {
    }
  }

  if (proofs.isPending) {
    return (
      <main className="mx-auto max-w-6xl px-6 py-16 text-center text-muted">
        Loading payment proofs…
      </main>
    );
  }

  if (proofs.error) {
    return (
      <main className="mx-auto max-w-xl px-6 py-16">
        <section className="rounded-2xl border border-red-200 bg-white p-6">
          <h1 className="text-2xl font-bold text-ink">
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

  return (
    <main className="mx-auto max-w-6xl px-6 py-10">
      <header className="mb-8">
        <p className="text-xs font-bold uppercase tracking-widest text-brand">
          AU Van Admin
        </p>
        <h1 className="mt-2 text-4xl font-bold tracking-tight text-ink">
          Payment review
        </h1>
        <p className="mt-2 text-muted">
          Check each payment slip, then approve the booking or send it back with
          a reason.
        </p>
      </header>
      <section className="grid items-start gap-5 lg:grid-cols-[minmax(0,1.4fr)_minmax(20rem,1fr)]">
        <InventoryTable
          headings={["Booking", "Passenger", "Trip", "Fare", "Submitted"]}
        >
          {queue.length === 0 && (
            <EmptyRow
              columns={5}
              title="Nothing to review"
              detail="Payment slips appear here as students send them."
            />
          )}
          {queue.map((proof) => (
            <tr className="border-t border-line" key={proof.id}>
              <td className="px-4 py-3">
                <Button variant="text" onClick={() => review(proof)}>
                  {proof.bookingReference}
                </Button>
              </td>
              <td className="px-4 py-3 text-muted">{proof.passengerName}</td>
              <td className="px-4 py-3 text-muted">
                {`${proof.trip.origin} → ${proof.trip.destination} · ${formatDeparture(proof.trip.departureAt)}`}
              </td>
              <td className="px-4 py-3 text-muted">
                {formatFare(proof.totalFare)}
              </td>
              <td className="px-4 py-3 text-muted">
                {formatDeparture(proof.submittedAt)}
              </td>
            </tr>
          ))}
        </InventoryTable>
        <Panel className="p-5">
          {!selected ? (
            <p className="text-sm text-muted">
              Choose a booking reference to see its payment slip.
            </p>
          ) : (
            <div className="flex flex-col gap-3">
              <h2 className="text-lg font-bold text-ink">
                {selected.bookingReference}
              </h2>
              <p className="text-sm text-muted">
                {`${selected.passengerName} · ${selected.passengerPhone} · ${formatFare(selected.totalFare)}`}
              </p>
              <ProofImage
                key={selected.id}
                proofId={selected.id}
                reference={selected.bookingReference}
                session={session}
              />
              <label className="text-sm font-semibold text-ink" htmlFor="review-note">
                Note to the student
              </label>
              <textarea
                className="w-full rounded-lg border border-line bg-white px-3 py-2 text-sm text-ink outline-none focus:border-brand"
                id="review-note"
                maxLength={maxNoteLength}
                onChange={(event) => setNote(event.target.value)}
                rows={3}
                value={note}
              />
              {noteError && (
                <p
                  className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700"
                  role="alert"
                >
                  {noteError}
                </p>
              )}
              {failure && (
                <p
                  className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700"
                  role="alert"
                >
                  {failure.message}
                </p>
              )}
              <div className="flex gap-2">
                <Button disabled={busy} onClick={() => void decide("approve")}>
                  {busy ? "Saving…" : "Approve payment"}
                </Button>
                <Button
                  disabled={busy}
                  onClick={() => void decide("reject")}
                  variant="secondary"
                >
                  Reject payment
                </Button>
              </div>
            </div>
          )}
        </Panel>
      </section>
    </main>
  );
}
