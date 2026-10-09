import { CalendarDays } from "lucide-react";
import { useId, useRef, useState, type KeyboardEvent } from "react";
import { useDismiss } from "../hooks/useDismiss";
import { bangkokToday, chipLabel, nextDays } from "../utils/calendar";
import { Calendar } from "./ui/Calendar";
import { labelClass } from "./ui/Field";

const chipClass =
  "inline-flex min-h-10 items-center gap-1.5 rounded-full border border-line bg-card px-3.5 text-sm font-medium text-ink tabular-nums transition-colors duration-150 ease-out hover:border-ink/25 disabled:cursor-not-allowed disabled:border-dashed disabled:text-muted disabled:line-through aria-pressed:border-brand-600 aria-pressed:bg-brand-600 aria-pressed:text-white aria-expanded:border-brand-500";

export function DayPicker({
  selected,
  multiple,
  onChange,
  error,
}: {
  selected: string[];
  multiple: boolean;
  onChange: (days: string[]) => void;
  error?: string;
}) {
  const id = useId();
  const today = bangkokToday();
  const [calendarOpen, setCalendarOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const later = useRef<HTMLButtonElement>(null);
  const chips = [...new Set([...nextDays(today, 14), ...selected])].sort();

  useDismiss(root, calendarOpen, () => setCalendarOpen(false));

  function toggle(day: string) {
    if (!multiple) return onChange([day]);
    onChange(
      selected.includes(day)
        ? selected.filter((chosen) => chosen !== day)
        : [...selected, day].sort(),
    );
  }

  function onKeyDown(event: KeyboardEvent) {
    if (event.key !== "Escape" || !calendarOpen) return;
    event.preventDefault();
    setCalendarOpen(false);
    later.current?.focus();
  }

  return (
    <div className="relative flex flex-col gap-1.5" onKeyDown={onKeyDown} ref={root}>
      <p className="flex justify-between gap-3">
        <span className={labelClass} id={id}>
          {multiple ? "Days" : "Day"}
        </span>
        {multiple && selected.length > 1 && (
          <span className="text-[13px] text-muted">
            {selected.length} days chosen
          </span>
        )}
      </p>
      <div
        aria-describedby={error ? `${id}-error` : undefined}
        aria-labelledby={id}
        className="flex flex-wrap gap-2"
        role="group"
      >
        {chips.map((day) => (
          <button
            aria-pressed={selected.includes(day)}
            className={chipClass}
            disabled={day < today}
            key={day}
            title={day < today ? "This day has passed." : undefined}
            onClick={() => toggle(day)}
            type="button"
          >
            {chipLabel(day, today)}
          </button>
        ))}
        <button
          aria-expanded={calendarOpen}
          className={chipClass}
          onClick={() => setCalendarOpen((open) => !open)}
          ref={later}
          type="button"
        >
          <CalendarDays aria-hidden className="size-4 text-brand-500" />
          Later date
        </button>
      </div>
      {error && (
        <p className="text-xs text-danger" id={`${id}-error`}>
          {error}
        </p>
      )}
      {calendarOpen && (
        <div className="absolute top-full left-0 z-20 mt-2">
          <Calendar
            min={today}
            onPick={(day) => {
              toggle(day);
              if (multiple) return;
              setCalendarOpen(false);
              later.current?.focus();
            }}
            selected={selected}
          />
        </div>
      )}
    </div>
  );
}
