import { CircleCheck } from "lucide-react";
import type { ScheduleReport } from "../hooks/useScheduleTrips";
import { shortDay } from "../utils/calendar";

export function ScheduleOutcome({ report }: { report: ScheduleReport | null }) {
  if (!report || report.failed.length === 0) return null;
  const scheduled = report.scheduled.length;
  return (
    <div className="grid gap-3" role="alert">
      {scheduled > 0 && (
        <p className="flex items-center gap-2 text-sm text-success">
          <CircleCheck aria-hidden className="size-4 shrink-0" />
          {scheduled === 1 ? "1 trip" : `${scheduled} trips`} scheduled.
        </p>
      )}
      <div className="rounded-xl border border-danger/20 bg-danger-soft px-4 py-3 text-sm text-danger">
        <p className="font-semibold">Not scheduled</p>
        <ul className="mt-1.5 grid gap-1">
          {report.failed.map(({ day, message }) => (
            <li key={day}>
              <span className="font-medium tabular-nums">{shortDay(day)}</span>
              {` — ${message}`}
            </li>
          ))}
        </ul>
        <p className="mt-2 text-ink/70">
          These days are still selected, so you can fix the problem and try
          again.
        </p>
      </div>
    </div>
  );
}
