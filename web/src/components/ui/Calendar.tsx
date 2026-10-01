import { ChevronLeft, ChevronRight } from "lucide-react";
import { useState } from "react";
import { addMonths, longDay, monthGrid, monthTitle } from "../../utils/calendar";

const weekdays = ["M", "T", "W", "T", "F", "S", "S"];

const stepClass =
  "grid size-9 place-items-center rounded-full text-muted transition-colors hover:bg-paper hover:text-ink disabled:pointer-events-none disabled:opacity-35";

export function Calendar({
  selected,
  min,
  onPick,
}: {
  selected: string[];
  min: string;
  onPick: (day: string) => void;
}) {
  const first = min.slice(0, 7);
  const [month, setMonth] = useState(
    (selected.find((day) => day >= min) ?? min).slice(0, 7),
  );

  return (
    <div className="w-72 rounded-xl border border-line bg-card p-3 shadow-[0_12px_32px_rgba(20,23,43,0.14)]">
      <div className="mb-2 flex items-center justify-between">
        <button
          aria-label="Previous month"
          className={stepClass}
          disabled={month <= first}
          onClick={() => setMonth(addMonths(month, -1))}
          type="button"
        >
          <ChevronLeft aria-hidden className="size-4" />
        </button>
        <p aria-live="polite" className="font-display text-lg text-brand-900">
          {monthTitle(month)}
        </p>
        <button
          aria-label="Next month"
          className={stepClass}
          onClick={() => setMonth(addMonths(month, 1))}
          type="button"
        >
          <ChevronRight aria-hidden className="size-4" />
        </button>
      </div>
      <div className="grid grid-cols-7 gap-0.5 text-center">
        {weekdays.map((weekday, index) => (
          <span
            aria-hidden
            className="pb-1 text-[11px] font-semibold text-muted"
            key={index}
          >
            {weekday}
          </span>
        ))}
        {monthGrid(month).map((day, index) =>
          day ? (
            <button
              aria-label={longDay(day)}
              aria-pressed={selected.includes(day)}
              className={`aspect-square rounded-full text-sm tabular-nums ${day === min ? "font-semibold text-brand-700" : "text-ink"} transition-colors hover:bg-brand-50 disabled:pointer-events-none disabled:text-muted/45 aria-pressed:bg-brand-600 aria-pressed:text-white`}
              disabled={day < min}
              key={day}
              onClick={() => onPick(day)}
              type="button"
            >
              {Number(day.slice(8))}
            </button>
          ) : (
            <span key={`blank-${index}`} />
          ),
        )}
      </div>
    </div>
  );
}
