import type { DeadLetter } from "../types/operations";
import { bangkokDateTime } from "../utils/dates";
import { EmptyState } from "./ui/EmptyState";
import { FailurePanel } from "./ui/FailurePanel";
import { Skeleton } from "./ui/Skeleton";
import { Table, type Column } from "./ui/Table";

const columns: Column<DeadLetter>[] = [
  {
    label: "Event",
    render: (letter) => <span className="font-mono text-[13px]">{letter.eventType}</span>,
  },
  {
    label: "About",
    render: (letter) => (
      <span className="font-mono text-xs break-all text-muted">{letter.aggregateId}</span>
    ),
  },
  { label: "Attempts", render: (letter) => letter.attempts },
  {
    label: "Last error",
    render: (letter) => <span className="text-danger">{letter.lastError ?? "—"}</span>,
  },
  {
    label: "Given up",
    render: (letter) =>
      letter.processedAt ? bangkokDateTime(letter.processedAt) : "—",
  },
];

export function DeadLettersTable({
  letters,
  loading,
  error,
}: {
  letters: DeadLetter[] | undefined;
  loading: boolean;
  error: Error | null;
}) {
  return (
    <section aria-labelledby="dead-letters-heading" className="mt-14">
      <div className="mb-5">
        <p className="text-[11px] font-semibold tracking-[0.14em] text-brand-500 uppercase">
          Notifications
        </p>
        <h2 className="mt-1 font-display text-2xl font-light text-brand-900" id="dead-letters-heading">
          Dead letters
        </h2>
        <p className="mt-1 text-sm text-muted">
          Messages to students that ran out of attempts.
        </p>
      </div>
      {loading && (
        <div role="status">
          <span className="sr-only">Loading dead letters…</span>
          <Skeleton className="h-28" />
        </div>
      )}
      {error && <FailurePanel title="Could not load dead letters" error={error} />}
      {letters && (
        <Table
          columns={columns}
          empty={
            <EmptyState
              detail="A notification appears here only once its attempts are spent."
              title="Nothing was given up on"
            />
          }
          label="Dead letters"
          rowKey={(letter) => letter.id}
          rows={letters}
        />
      )}
    </section>
  );
}
