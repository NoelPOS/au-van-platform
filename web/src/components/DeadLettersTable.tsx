import type { DeadLetter } from "../types/operations";
import { formatDeparture } from "../utils/format";
import { EmptyRow, InventoryTable } from "./InventoryTable";
import { FailurePanel } from "./ui/FailurePanel";

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
    <>
      <h2 className="mb-3 text-lg font-bold text-ink">Dead letters</h2>
      {loading && (
        <p className="text-sm text-muted">Loading dead letters…</p>
      )}
      {error && (
        <FailurePanel title="Could not load dead letters" error={error} />
      )}
      {letters && (
        <InventoryTable
          headings={["Event", "About", "Attempts", "Last error", "Given up"]}
        >
          {letters.length === 0 && (
            <EmptyRow
              columns={5}
              title="Nothing was given up on"
              detail="A notification appears here only once its attempts are spent."
            />
          )}
          {letters.map((letter) => (
            <tr className="border-t border-line" key={letter.id}>
              <td className="px-4 py-3 font-semibold text-ink">
                {letter.eventType}
              </td>
              <td className="px-4 py-3 text-muted">{letter.aggregateId}</td>
              <td className="px-4 py-3 text-muted">{letter.attempts}</td>
              <td className="px-4 py-3 text-muted">{letter.lastError ?? "—"}</td>
              <td className="px-4 py-3 text-muted">
                {letter.processedAt ? formatDeparture(letter.processedAt) : "—"}
              </td>
            </tr>
          ))}
        </InventoryTable>
      )}
    </>
  );
}
