import type { Day } from "../utils/days";

export function DayStrip({
  days,
  counts,
  selected,
  onSelect,
}: {
  days: Day[];
  counts: Map<string, number>;
  selected: string;
  onSelect: (key: string) => void;
}) {
  return (
    <div
      aria-label="Departure day"
      className="-mx-5 flex snap-x scroll-px-5 gap-2 overflow-x-auto px-5 pb-1 [scrollbar-width:none]"
      role="tablist"
    >
      {days.map((day) => {
        const active = day.key === selected;
        const count = counts.get(day.key) ?? 0;
        return (
          <button
            aria-label={`${day.relative ?? day.weekday} ${day.dayNumber} ${day.month}, ${count} ${count === 1 ? "departure" : "departures"}`}
            aria-selected={active}
            className={`flex min-w-[4.25rem] shrink-0 snap-start flex-col items-center rounded-2xl border px-2.5 pt-2.5 pb-2 transition-colors duration-150 ease-out ${
              active
                ? "border-brand-900 bg-brand-900 text-paper"
                : "border-line bg-card text-ink hover:border-ink/25"
            }`}
            key={day.key}
            onClick={() => onSelect(day.key)}
            role="tab"
            type="button"
          >
            <span
              className={`font-mono text-[10px] tracking-[0.1em] uppercase ${active ? "text-paper/70" : "text-muted"}`}
            >
              {day.relative ?? day.weekday}
            </span>
            <span className="mt-1 font-display text-[28px] leading-none">
              {day.dayNumber}
            </span>
            <span
              className={`mt-0.5 text-[11px] ${active ? "text-paper/70" : "text-muted"}`}
            >
              {day.month}
            </span>
            <span aria-hidden className="mt-1.5 flex h-1 gap-0.5">
              {Array.from({ length: Math.min(count, 5) }, (_, index) => (
                <span
                  className={`size-1 rounded-full ${active ? "bg-accent" : "bg-brand-500/50"}`}
                  key={index}
                />
              ))}
            </span>
          </button>
        );
      })}
    </div>
  );
}
