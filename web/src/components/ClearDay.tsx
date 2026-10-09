import { useState } from "react";
import { useClearDay } from "../hooks/useSchedulePlan";
import { useToast } from "../hooks/useToast";
import type { AuthSession } from "../types/auth";
import type { DayCleared } from "../types/schedule";
import { shortDay } from "../utils/calendar";
import { plural } from "../utils/schedule";
import { ErrorMessage } from "./ErrorMessage";
import { Button } from "./ui/Button";

function outcome({ removed, kept }: DayCleared) {
  const gone =
    removed === 0
      ? "Nothing removed"
      : `Removed ${plural(removed, "departure")}`;
  return kept === 0
    ? gone
    : `${gone}. Kept ${kept} that students have booked or queued for.`;
}

export function ClearDay({
  session,
  day,
}: {
  session: AuthSession;
  day: string;
}) {
  const clear = useClearDay(session);
  const notify = useToast();
  const [asking, setAsking] = useState(false);

  if (!asking) {
    return (
      <div className="border-t border-line pt-5">
        <button
          className="min-h-11 text-sm font-semibold text-danger underline decoration-1 underline-offset-4 hover:decoration-2"
          onClick={() => setAsking(true)}
          type="button"
        >
          Clear unbooked departures
        </button>
      </div>
    );
  }

  return (
    <div
      aria-label="Clear day"
      className="rounded-xl border border-danger/25 bg-danger-soft p-4"
      role="group"
    >
      <p className="text-sm text-danger">
        Remove every departure still ahead on {shortDay(day)} that nobody has
        booked, held or queued for? Departures with students on them stay.
      </p>
      {clear.error && (
        <div className="mt-3 grid">
          <ErrorMessage error={clear.error} />
        </div>
      )}
      <div className="mt-4 flex justify-end gap-2">
        <Button autoFocus onClick={() => setAsking(false)} variant="secondary">
          Keep them
        </Button>
        <Button
          disabled={clear.isPending}
          onClick={() =>
            clear.mutate(day, {
              onSuccess: (result) => {
                notify(outcome(result));
                setAsking(false);
              },
            })
          }
          variant="danger"
        >
          {clear.isPending ? "Clearing…" : "Yes, clear"}
        </Button>
      </div>
    </div>
  );
}
