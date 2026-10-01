import type { DeadLetter } from "../types/operations";
import { formatWhen } from "../utils/format";
import { FailurePanel } from "./ui/FailurePanel";

const headings = ["Event", "About", "Attempts", "Last error", "Given up"];

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
      <div className="mb-4 border-b border-line pb-3">
        <p className="text-xs font-semibold tracking-[0.14em] text-brand-500 uppercase">
          Notifications
        </p>
        <h2 className="mt-1 font-serif text-2xl text-brand-900" id="dead-letters-heading">
          Dead letters
        </h2>
        <p className="mt-1 text-sm text-muted">
          Messages to students that ran out of attempts.
        </p>
      </div>
      {loading && (
        <div className="h-28 rounded-2xl border border-line bg-card motion-safe:animate-pulse">
          <p className="sr-only">Loading dead letters…</p>
        </div>
      )}
      {error && <FailurePanel title="Could not load dead letters" error={error} />}
      {letters?.length === 0 && (
        <p className="rounded-2xl border border-dashed border-line px-6 py-10 text-center">
          <strong className="block font-serif text-lg text-brand-900">
            Nothing was given up on
          </strong>
          <span className="mt-1 block text-sm text-muted">
            A notification appears here only once its attempts are spent.
          </span>
        </p>
      )}
      {letters && letters.length > 0 && (
        <div className="overflow-x-auto rounded-2xl border border-line bg-card">
          <table className="w-full min-w-160 border-collapse text-sm">
            <thead>
              <tr>
                {headings.map((heading) => (
                  <th
                    className="px-4 py-3 text-left text-xs font-semibold tracking-[0.14em] text-muted uppercase"
                    key={heading}
                    scope="col"
                  >
                    {heading}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {letters.map((letter) => (
                <tr className="border-t border-line align-top" key={letter.id}>
                  <td className="px-4 py-3 font-mono text-xs font-semibold text-ink">
                    {letter.eventType}
                  </td>
                  <td className="px-4 py-3 font-mono text-xs text-muted">
                    {letter.aggregateId}
                  </td>
                  <td className="px-4 py-3 font-serif text-lg text-brand-900 tabular-nums">
                    {letter.attempts}
                  </td>
                  <td className="max-w-80 px-4 py-3 text-danger">{letter.lastError ?? "—"}</td>
                  <td className="px-4 py-3 whitespace-nowrap text-muted tabular-nums">
                    {letter.processedAt ? formatWhen(letter.processedAt) : "—"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
