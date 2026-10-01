import type { WaitlistPlace } from "../types/operations";
import { formatWhen } from "../utils/format";
import { StatusBadge } from "./ui/StatusBadge";

export function WaitlistQueue({ entries }: { entries: WaitlistPlace[] }) {
  if (entries.length === 0)
    return (
      <p className="text-sm">
        <strong className="block font-serif text-lg text-brand-900">Nobody is waiting</strong>
        <span className="mt-1 block text-muted">
          Students join the queue once every seat is claimed.
        </span>
      </p>
    );

  return (
    <ol className="divide-y divide-line">
      {entries.map((entry) => (
        <li className="flex flex-wrap items-center gap-x-4 gap-y-2 py-3" key={entry.entryId}>
          <span className="w-10 font-serif text-2xl text-brand-900 tabular-nums">
            {entry.position === null ? "—" : `#${entry.position}`}
          </span>
          <span className="min-w-0 flex-1">
            <span className="block truncate font-semibold text-ink">
              {entry.displayName ?? entry.userId}
            </span>
            <span className="text-xs text-muted">
              {`${entry.seatsWanted} seat${entry.seatsWanted === 1 ? "" : "s"} · joined ${formatWhen(entry.joinedAt)}`}
            </span>
          </span>
          <span className="text-right">
            <StatusBadge value={entry.status} />
            {entry.promotionExpiresAt && (
              <span className="mt-1 block text-xs text-muted">
                {`Offer ends ${formatWhen(entry.promotionExpiresAt)}`}
              </span>
            )}
          </span>
        </li>
      ))}
    </ol>
  );
}
