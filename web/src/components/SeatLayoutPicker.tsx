import { Check } from "lucide-react";
import { useId } from "react";
import type { SeatLayout } from "../types/inventory";
import { seatCount } from "../utils/format";
import { VanThumbnail } from "./VanThumbnail";
import { labelClass } from "./ui/Field";

export function SeatLayoutPicker({
  layouts,
  defaultValue,
}: {
  layouts: SeatLayout[];
  defaultValue: string;
}) {
  const id = useId();
  return (
    <fieldset>
      <legend className={`${labelClass} mb-1.5`}>Seat layout</legend>
      <div className="grid grid-cols-2 gap-3">
        {layouts.map((layout) => (
          <label
            className="group relative flex cursor-pointer flex-col items-center gap-3 rounded-xl border border-line bg-card px-3 pt-4 pb-3 text-center transition-colors duration-150 ease-out hover:border-ink/25 has-checked:border-brand-500 has-checked:bg-brand-50 has-focus-visible:outline-2 has-focus-visible:outline-offset-2 has-focus-visible:outline-brand-500"
            key={layout.id}
          >
            <input
              aria-describedby={`${id}-${layout.id}`}
              aria-label={layout.name}
              className="sr-only"
              defaultChecked={layout.id === defaultValue}
              name="seatLayoutId"
              required
              type="radio"
              value={layout.id}
            />
            <Check
              aria-hidden
              className="absolute top-2.5 right-2.5 hidden size-4 text-brand-500 group-has-checked:block"
            />
            <span className="block w-12">
              <VanThumbnail seats={layout.seats} />
            </span>
            <span className="w-full min-w-0">
              <span className="block truncate text-sm font-medium text-ink">
                {layout.name}
              </span>
              <span
                className="block text-xs text-muted tabular-nums"
                id={`${id}-${layout.id}`}
              >
                {seatCount(layout.seats.length)}
              </span>
            </span>
          </label>
        ))}
      </div>
    </fieldset>
  );
}
