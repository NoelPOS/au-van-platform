export function CountBadge({
  count,
  label,
  className = "",
}: {
  count: number;
  label: string;
  className?: string;
}) {
  if (count === 0) return null;
  return (
    <span
      className={`rounded-full bg-accent px-1.5 py-0.5 font-mono text-[10px] leading-none font-semibold text-brand-900 tabular-nums ${className}`}
    >
      <span aria-hidden>{count}</span>
      <span className="sr-only">{`, ${count} ${label}`}</span>
    </span>
  );
}
