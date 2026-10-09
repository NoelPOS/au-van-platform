export function PlanBar({
  count,
  title,
  actionLabel,
  onAction,
  onCancel,
}: {
  count: number;
  title: string;
  actionLabel: string;
  onAction: () => void;
  onCancel: () => void;
}) {
  return (
    <div
      aria-label="Planning"
      className="fixed inset-x-3 bottom-[calc(4.75rem+env(safe-area-inset-bottom))] z-20 rounded-2xl bg-brand-900 text-paper shadow-[0_12px_32px_rgba(20,23,43,0.28)] lg:right-8 lg:bottom-6 lg:left-[calc(16rem+2rem)] lg:mx-auto lg:max-w-2xl"
      role="region"
    >
      <div className="flex items-center gap-3 px-4 py-3 sm:px-5">
        <p aria-live="polite" className="min-w-0 flex-1">
          <span className="block truncate text-[11px] font-semibold tracking-[0.16em] text-brand-100/80 uppercase">
            {title}
          </span>
          <span className="font-display text-xl leading-tight">
            <span className="text-accent">{count}</span>{" "}
            {count === 1 ? "day" : "days"}
            <span className="max-sm:hidden"> chosen</span>
          </span>
        </p>
        <button
          className="min-h-11 shrink-0 rounded-full px-3 text-sm font-semibold text-brand-100 hover:text-white"
          onClick={onCancel}
          type="button"
        >
          Cancel
        </button>
        <button
          className="inline-flex min-h-11 shrink-0 items-center rounded-full bg-paper px-5 text-sm font-semibold text-brand-900 transition-colors hover:bg-white disabled:cursor-not-allowed disabled:opacity-50"
          disabled={count === 0}
          onClick={onAction}
          type="button"
        >
          {actionLabel}
        </button>
      </div>
    </div>
  );
}
