import type { ReactNode } from "react";

export const controlClass =
  "h-11 w-full rounded-lg border border-line bg-card px-3 text-[15px] text-ink transition-colors duration-150 ease-out placeholder:text-muted/70 hover:border-ink/25 focus:border-brand-500 disabled:cursor-not-allowed disabled:bg-paper disabled:text-muted aria-invalid:border-danger";

export const labelClass = "text-[13px] font-medium text-ink";

export function Field({
  id,
  label,
  hint,
  error,
  hideLabel = false,
  className = "",
  children,
}: {
  id: string;
  label: string;
  hideLabel?: boolean;
  hint?: string;
  error?: string;
  className?: string;
  children: ReactNode;
}) {
  return (
    <div className={`flex flex-col gap-1.5 ${className}`}>
      <label
        className={hideLabel ? "sr-only" : labelClass}
        htmlFor={id}
        id={`${id}-label`}
      >
        {label}
      </label>
      {children}
      {(error ?? hint) && (
        <p
          className={`text-xs ${error ? "text-danger" : "text-muted"}`}
          id={`${id}-hint`}
        >
          {error ?? hint}
        </p>
      )}
    </div>
  );
}
