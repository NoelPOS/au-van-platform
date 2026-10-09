import { ChevronLeft, ChevronRight } from "lucide-react";
import { useState } from "react";
import type { Vehicle } from "../types/inventory";
import { addDays, addMonths, monthTitle } from "../utils/calendar";
import { weekStart } from "../utils/schedule";
import { MonthGrid, type CalendarProps } from "./MonthGrid";
import { WeekGrid } from "./WeekGrid";

const stepClass =
  "grid size-11 place-items-center rounded-full text-muted transition-colors hover:bg-card hover:text-ink";

const dayAndMonth = new Intl.DateTimeFormat("en-GB", {
  timeZone: "UTC",
  day: "numeric",
  month: "short",
  year: "numeric",
});

function weekTitle(start: string) {
  const end = addDays(start, 6);
  const last = dayAndMonth.format(new Date(`${end}T00:00:00Z`));
  return `${Number(start.slice(8))} – ${last}`;
}

export function TripCalendar({
  view,
  vans,
  ...props
}: CalendarProps & { view: "month" | "week"; vans: Vehicle[] }) {
  const [anchor, setAnchor] = useState(props.today);
  const month = anchor.slice(0, 7);
  const start = weekStart(anchor);

  function step(direction: 1 | -1) {
    setAnchor(
      view === "month"
        ? `${addMonths(month, direction)}-01`
        : addDays(start, 7 * direction),
    );
  }

  return (
    <section aria-label="Trip calendar" className="mt-6">
      <div className="mb-3 flex items-center justify-between gap-3">
        <h2
          aria-live="polite"
          className="font-display text-2xl text-brand-900 sm:text-[1.75rem]"
        >
          {view === "month" ? monthTitle(month) : weekTitle(start)}
        </h2>
        <div className="flex items-center">
          <button
            aria-label={view === "month" ? "Previous month" : "Previous week"}
            className={stepClass}
            onClick={() => step(-1)}
            type="button"
          >
            <ChevronLeft aria-hidden className="size-5" />
          </button>
          <button
            className="min-h-11 rounded-full px-3 text-sm font-semibold text-brand-500 hover:text-brand-700"
            onClick={() => setAnchor(props.today)}
            type="button"
          >
            Today
          </button>
          <button
            aria-label={view === "month" ? "Next month" : "Next week"}
            className={stepClass}
            onClick={() => step(1)}
            type="button"
          >
            <ChevronRight aria-hidden className="size-5" />
          </button>
        </div>
      </div>
      {view === "month" ? (
        <MonthGrid month={month} {...props} />
      ) : (
        <WeekGrid start={start} vans={vans} {...props} />
      )}
    </section>
  );
}
