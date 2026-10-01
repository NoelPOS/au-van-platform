import { useId } from "react";
import { labelClass } from "./Field";

export function SegmentedControl<T extends string>({
  label,
  hideLabel = false,
  options,
  value,
  onChange,
}: {
  label: string;
  hideLabel?: boolean;
  options: { value: T; label: string }[];
  value: T;
  onChange: (value: T) => void;
}) {
  const id = useId();
  return (
    <div className="flex flex-col items-start gap-1.5">
      <span className={hideLabel ? "sr-only" : labelClass} id={id}>
        {label}
      </span>
      <div
        aria-labelledby={id}
        className="inline-flex max-w-full rounded-full border border-line bg-paper p-1"
        role="radiogroup"
      >
        {options.map((option) => (
          <label
            className="relative flex min-h-9 cursor-pointer items-center rounded-full px-4 text-sm font-medium whitespace-nowrap text-muted transition-colors duration-150 ease-out hover:text-ink has-checked:bg-card has-checked:text-brand-900 has-checked:shadow-[0_1px_3px_rgba(20,23,43,0.14)] has-focus-visible:outline-2 has-focus-visible:outline-offset-2 has-focus-visible:outline-brand-500"
            key={option.value}
          >
            <input
              checked={option.value === value}
              className="absolute inset-0 cursor-pointer appearance-none rounded-full opacity-0"
              name={id}
              onChange={() => onChange(option.value)}
              type="radio"
              value={option.value}
            />
            {option.label}
          </label>
        ))}
      </div>
    </div>
  );
}
