import type { ReactNode } from "react";

export const controlClass =
  "h-11 w-full rounded-lg border border-line bg-card px-3 text-[15px] text-ink transition-colors duration-150 ease-out placeholder:text-muted/70 hover:border-ink/25 focus:border-brand-500 disabled:cursor-not-allowed disabled:bg-paper disabled:text-muted";

export function Field({
  id,
  label,
  hint,
  className = "",
  children,
}: {
  id: string;
  label: string;
  hint?: string;
  className?: string;
  children: ReactNode;
}) {
  return (
    <div className={`flex flex-col gap-1.5 ${className}`}>
      <label className="text-[13px] font-medium text-ink" htmlFor={id}>
        {label}
      </label>
      {children}
      {hint && (
        <p className="text-xs text-muted" id={`${id}-hint`}>
          {hint}
        </p>
      )}
    </div>
  );
}
