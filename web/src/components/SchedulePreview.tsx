import { useState } from "react";
import { useApplySchedule, useSchedulePreview } from "../hooks/useSchedulePlan";
import { useToast } from "../hooks/useToast";
import { ApiError } from "../services/bookingApi";
import type { AuthSession } from "../types/auth";
import type { VanRoute, Vehicle } from "../types/inventory";
import type { SchedulePlan } from "../types/schedule";
import { bangkokToday } from "../utils/calendar";
import { countOutcomes, groupByDate, plural } from "../utils/schedule";
import { ErrorMessage } from "./ErrorMessage";
import { PreviewDay } from "./PreviewDay";
import { Button } from "./ui/Button";
import { Skeleton } from "./ui/Skeleton";

function skippedSummary(counts: ReturnType<typeof countOutcomes>) {
  return [
    counts.CLASH && `${plural(counts.CLASH, "clash", "clashes")} skipped`,
    counts.PAST && `${counts.PAST} already passed`,
    counts.UNAVAILABLE && `${counts.UNAVAILABLE} on a retired route or van`,
  ].filter(Boolean);
}

function createLabel(count: number) {
  return count === 0 ? "Nothing to create" : `Create ${plural(count, "departure")}`;
}

export function SchedulePreview({
  session,
  plan,
  routes,
  vans,
  onBack,
  onApplied,
}: {
  session: AuthSession;
  plan: SchedulePlan;
  routes: VanRoute[];
  vans: Vehicle[];
  onBack: () => void;
  onApplied: () => void;
}) {
  const preview = useSchedulePreview(session, plan);
  const apply = useApplySchedule(session);
  const notify = useToast();
  const [attempt, setAttempt] = useState<{ hash: string; key: string } | null>(
    null,
  );
  const changed =
    apply.error instanceof ApiError && apply.error.code === "schedule_changed";

  if (preview.isPending) {
    return (
      <div className="flex flex-col gap-3" role="status">
        <span className="sr-only">
          Checking the plan against the timetable…
        </span>
        <Skeleton className="h-16" />
        <Skeleton className="h-28" />
        <Skeleton className="h-28" />
      </div>
    );
  }
  if (preview.error) return <ErrorMessage error={preview.error} />;

  const { planHash, departures } = preview.data;
  const counts = countOutcomes(departures);
  const skippedNotes = skippedSummary(counts);
  const today = bangkokToday();

  function confirm() {
    // The same plan keeps its key, so a retry cannot create its departures twice.
    const key = attempt?.hash === planHash ? attempt.key : crypto.randomUUID();
    setAttempt({ hash: planHash, key });
    apply.mutate(
      { plan, planHash, key },
      {
        onSuccess: ({ created, skipped }) => {
          notify(
            skipped
              ? `${plural(created, "departure")} created, ${skipped} skipped`
              : `${plural(created, "departure")} created`,
          );
          onApplied();
        },
        onError: (error) => {
          if (error instanceof ApiError && error.code === "schedule_changed")
            void preview.refetch();
        },
      },
    );
  }

  return (
    <div className="flex flex-col gap-6 pb-24">
      <div className="border-b border-line pb-5">
        <p className="font-display text-[2.5rem] leading-none font-light text-brand-900">
          {counts.CREATE}
          <span className="ml-2 font-sans text-base text-muted">
            new {counts.CREATE === 1 ? "departure" : "departures"}
          </span>
        </p>
        {skippedNotes.length > 0 && (
          <p className="mt-2 text-sm text-warning">
            {skippedNotes.join(" · ")}
          </p>
        )}
      </div>
      {changed && (
        <p
          className="rounded-xl border border-warning/25 bg-warning-soft px-4 py-3 text-sm text-warning"
          role="alert"
        >
          The timetable changed while you were looking. This is the updated
          preview; check it and confirm again.
        </p>
      )}
      {groupByDate(departures).map((group) => (
        <PreviewDay
          date={group.date}
          departures={group.departures}
          key={group.date}
          routes={routes}
          today={today}
          vans={vans}
        />
      ))}
      {!changed && <ErrorMessage error={apply.error} />}
      <div className="absolute inset-x-0 bottom-0 flex justify-end gap-2 border-t border-line bg-card px-6 py-4 pb-[max(1rem,env(safe-area-inset-bottom))]">
        <Button onClick={onBack} type="button" variant="secondary">
          Back
        </Button>
        <Button
          disabled={
            counts.CREATE === 0 || apply.isPending || preview.isFetching
          }
          onClick={confirm}
          type="button"
        >
          {apply.isPending ? "Creating…" : createLabel(counts.CREATE)}
        </Button>
      </div>
    </div>
  );
}
