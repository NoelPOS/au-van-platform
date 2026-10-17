export function DemoBadge({
  onLeave,
  dark = false,
}: {
  onLeave: () => void;
  dark?: boolean;
}) {
  return (
    <span className="flex items-center gap-3">
      <span
        className={`rounded border px-1.5 py-0.5 font-mono text-[10px] leading-none font-semibold tracking-[0.18em] uppercase ${dark ? "border-accent/70 text-accent" : "border-warning/50 text-warning"}`}
      >
        Demo
      </span>
      <button
        className={`min-h-11 text-sm underline decoration-1 underline-offset-4 ${dark ? "text-brand-100 hover:text-white" : "text-muted hover:text-ink"}`}
        onClick={onLeave}
        type="button"
      >
        Leave demo
      </button>
    </span>
  );
}
