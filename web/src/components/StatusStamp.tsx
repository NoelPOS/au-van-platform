import type { StampTone } from "../utils/tickets";

const tones: Record<StampTone, string> = {
  success: "text-success",
  warning: "text-warning",
  brand: "text-brand-600",
  danger: "text-danger",
  muted: "text-muted",
};

export function StatusStamp({ label, tone }: { label: string; tone: StampTone }) {
  return (
    <span
      className={`inline-block -rotate-6 rounded-md border-2 border-current px-2.5 py-1 font-mono text-[11px] leading-none font-bold tracking-[0.16em] whitespace-nowrap uppercase outline-1 outline-offset-2 outline-current ${tones[tone]}`}
    >
      {label}
    </span>
  );
}
