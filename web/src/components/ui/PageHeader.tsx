import type { ReactNode } from "react";

export function PageHeader({
  eyebrow,
  title,
  description,
  action,
}: {
  eyebrow: string;
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <header className="flex flex-col gap-6 border-b border-line pb-8 sm:flex-row sm:items-end sm:justify-between">
      <div className="max-w-2xl">
        <p className="text-[11px] font-semibold tracking-[0.16em] text-brand-500 uppercase">
          {eyebrow}
        </p>
        <h1 className="mt-3 font-display text-[2.5rem] leading-[1.05] font-light tracking-[-0.015em] text-brand-900 sm:text-[3.25rem]">
          {title}
        </h1>
        {description && (
          <p className="mt-3 font-display text-lg text-muted italic">
            {description}
          </p>
        )}
      </div>
      {action && <div className="shrink-0">{action}</div>}
    </header>
  );
}
