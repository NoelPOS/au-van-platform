import { bangkokToday, dayHeading } from "../utils/calendar";
import { Button } from "./ui/Button";
import { labelClass } from "./ui/Field";

export function DaySummary({
  day,
  onChange,
}: {
  day: string;
  onChange: () => void;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <span className={labelClass}>Day</span>
      <p className="flex flex-wrap items-center justify-between gap-3">
        <span className="font-display text-xl text-brand-900">
          {dayHeading(day, bangkokToday())}
        </span>
        <Button onClick={onChange} type="button" variant="text">
          Change days
        </Button>
      </p>
    </div>
  );
}
