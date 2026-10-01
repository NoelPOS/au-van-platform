import type { WaitlistPlace } from "../types/operations";
import { bangkokDateTime } from "../utils/dates";
import { EmptyState } from "./ui/EmptyState";
import { StatusBadge } from "./ui/StatusBadge";

export function WaitlistQueue({ entries }: { entries: WaitlistPlace[] }) {
  if (entries.length === 0)
    return (
      <EmptyState
        detail="Students join the queue once every seat is claimed."
        title="Nobody is waiting"
      />
    );

  return (
    <ol className="divide-y divide-line">
      {entries.map((entry) => (
        <li className="flex flex-wrap items-center gap-x-4 gap-y-2 py-3" key={entry.entryId}>
          <span className="w-10 font-display text-2xl font-light text-brand-900 tabular-nums">
            {entry.position === null ? "—" : `#${entry.position}`}
          </span>
          <span className="min-w-0 flex-1">
            <span className="block truncate font-semibold text-ink">
              {entry.displayName ?? entry.userId}
            </span>
            <span className="text-xs text-muted">
              {`${entry.seatsWanted} seat${entry.seatsWanted === 1 ? "" : "s"} · joined ${bangkokDateTime(entry.joinedAt)}`}
            </span>
          </span>
          <span className="text-right">
            <StatusBadge value={entry.status} />
            {entry.promotionExpiresAt && (
              <span className="mt-1 block text-xs text-muted">
                {`Offer ends ${bangkokDateTime(entry.promotionExpiresAt)}`}
              </span>
            )}
          </span>
        </li>
      ))}
    </ol>
  );
}
