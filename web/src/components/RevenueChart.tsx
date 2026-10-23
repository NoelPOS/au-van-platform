import type { DayInsight } from "../types/insights";
import { shortDay } from "../utils/calendar";
import { formatBaht, seatCount } from "../utils/format";
import { Panel } from "./ui/Panel";

function summaryOf(days: DayInsight[], total: number, best: DayInsight) {
  const span = `${shortDay(days[0].date)} to ${shortDay(days[days.length - 1].date)}`;
  if (total === 0) return `Revenue by day, ${span}: nothing taken.`;
  return `Revenue by day, ${span}: ${formatBaht(total)} in total, the best day ${shortDay(best.date)} with ${formatBaht(best.revenue)}.`;
}

export function RevenueChart({ days }: { days: DayInsight[] }) {
  const total = days.reduce((sum, day) => sum + day.revenue, 0);
  const best = days.reduce((top, day) => (day.revenue > top.revenue ? day : top));
  const scale = best.revenue || 1;

  return (
    <Panel className="px-6 pt-6 pb-5">
      <div className="flex flex-wrap items-baseline justify-between gap-x-6 gap-y-1">
        <h2 className="font-display text-2xl font-light text-brand-900">
          Revenue by day
        </h2>
        {total > 0 && (
          <p className="font-mono text-[12px] text-muted tabular-nums">
            {`Best day ${shortDay(best.date)} · ${formatBaht(best.revenue)}`}
          </p>
        )}
      </div>
      <div
        aria-label={summaryOf(days, total, best)}
        className="mt-6 flex h-44 items-end gap-[3px] border-b border-line"
        role="img"
      >
        {days.map((day) => (
          <div
            className="group flex h-full flex-1 items-end"
            key={day.date}
            title={`${shortDay(day.date)} · ${formatBaht(day.revenue)} · ${seatCount(day.seatsSold)}`}
          >
            <div
              className="w-full rounded-t-[3px] bg-brand-500 transition-colors duration-150 group-hover:bg-brand-900"
              style={{
                height: `${(day.revenue / scale) * 100}%`,
                minHeight: day.revenue > 0 ? 2 : 0,
              }}
            />
          </div>
        ))}
      </div>
      <div className="mt-2 flex justify-between font-mono text-[11px] text-muted">
        <span>{shortDay(days[0].date)}</span>
        <span>{shortDay(days[days.length - 1].date)}</span>
      </div>
    </Panel>
  );
}
