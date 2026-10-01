import { useId } from "react";
import { Input } from "./ui/Input";

export function TimeField({
  value,
  onChange,
  suggestions,
  error,
}: {
  value: string;
  onChange: (value: string) => void;
  suggestions: string[];
  error?: string;
}) {
  const id = useId();
  return (
    <div className="flex flex-wrap items-start gap-x-4 gap-y-3">
      <Input
        autoComplete="off"
        className="w-32"
        error={error}
        inputMode="numeric"
        label="Time"
        maxLength={5}
        onChange={(event) => onChange(event.target.value)}
        placeholder="HH:MM"
        value={value}
      />
      {suggestions.length > 0 && (
        <div className="flex flex-col gap-1.5">
          <span className="text-[13px] text-muted" id={id}>
            This route runs at
          </span>
          <div
            aria-labelledby={id}
            className="flex flex-wrap gap-1.5"
            role="group"
          >
            {suggestions.map((clock) => (
              <button
                aria-pressed={clock === value}
                className="inline-flex min-h-11 items-center rounded-full border border-line bg-paper px-3 font-mono text-[13px] text-ink transition-colors duration-150 hover:border-ink/25 aria-pressed:border-brand-500 aria-pressed:bg-brand-50 aria-pressed:text-brand-700"
                key={clock}
                onClick={() => onChange(clock)}
                type="button"
              >
                {clock}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
