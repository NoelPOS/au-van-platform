import { Plus } from "lucide-react";
import type { DayTemplate } from "../types/schedule";
import { plural, timeSpan } from "../utils/schedule";
import { Button } from "./ui/Button";
import { EmptyState } from "./ui/EmptyState";

export function DayTemplateList({
  templates,
  applyTo,
  onApply,
  onEdit,
  onCreate,
}: {
  templates: DayTemplate[];
  applyTo: number;
  onApply: (template: DayTemplate) => void;
  onEdit: (template: DayTemplate) => void;
  onCreate: () => void;
}) {
  if (templates.length === 0) {
    return (
      <EmptyState
        action={<Button onClick={onCreate}>New template</Button>}
        detail="Write a day's departures down once, then lay them onto any dates."
        title="No templates yet"
      />
    );
  }

  return (
    <div className="flex flex-col gap-5">
      {applyTo === 0 && (
        <p className="text-sm text-muted">
          To use one, choose Select days on the calendar, tick the dates, then
          Apply template.
        </p>
      )}
      <ul className="divide-y divide-dashed divide-line overflow-hidden rounded-2xl border border-line">
        {templates.map((template) => {
          const vans = new Set(
            template.departures.map((line) => line.vehicleId),
          );
          const times = template.departures.map((line) => line.time);
          return (
            <li className="px-4 py-4" key={template.id}>
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <h3 className="font-display text-xl leading-tight text-brand-900">
                    {template.name}
                  </h3>
                  <p className="mt-1 text-[13px] text-muted">
                    {plural(times.length, "departure")} ·{" "}
                    {plural(vans.size, "van")} ·{" "}
                    <span className="font-mono">
                      {timeSpan(template.departures)}
                    </span>
                  </p>
                </div>
                <Button
                  aria-label={`Edit ${template.name}`}
                  onClick={() => onEdit(template)}
                  variant="text"
                >
                  Edit
                </Button>
              </div>
              <p
                aria-hidden
                className="mt-3 flex flex-wrap gap-x-3 gap-y-1 font-mono text-[12px] text-brand-700"
              >
                {times.slice(0, 12).map((time, index) => (
                  <span key={`${time}-${index}`}>{time}</span>
                ))}
                {times.length > 12 && (
                  <span className="text-muted">+{times.length - 12}</span>
                )}
              </p>
              {applyTo > 0 && (
                <Button
                  className="mt-4 w-full"
                  onClick={() => onApply(template)}
                  variant="secondary"
                >
                  Preview on {plural(applyTo, "day")}
                </Button>
              )}
            </li>
          );
        })}
      </ul>
      <Button className="self-start" onClick={onCreate} variant="secondary">
        <Plus aria-hidden className="size-4" />
        New template
      </Button>
    </div>
  );
}
