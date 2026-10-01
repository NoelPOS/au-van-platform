import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";

export type ChipTone = "neutral" | "brand" | "success" | "warning" | "danger";

const tones: Record<ChipTone, string> = {
  neutral: "border-line bg-paper text-muted",
  brand: "border-brand-100 bg-brand-50 text-brand-700",
  success: "border-success/20 bg-success-soft text-success",
  warning: "border-warning/20 bg-warning-soft text-warning",
  danger: "border-danger/20 bg-danger-soft text-danger",
};

export function Chip({
  tone = "neutral",
  icon: Icon,
  children,
}: {
  tone?: ChipTone;
  icon?: LucideIcon;
  children: ReactNode;
}) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 font-mono text-[11px] font-medium tracking-wide whitespace-nowrap ${tones[tone]}`}
    >
      {Icon && (
        <Icon aria-hidden className="size-3.5 shrink-0" strokeWidth={2} />
      )}
      {children}
    </span>
  );
}
