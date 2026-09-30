const tones: Record<string, string> = {
  ACTIVE: "bg-emerald-50 text-emerald-700",
  CONFIRMED: "bg-emerald-50 text-emerald-700",
  PENDING_PAYMENT: "bg-amber-50 text-amber-800",
  PAYMENT_UNDER_REVIEW: "bg-amber-50 text-amber-800",
};

export function StatusBadge({ value }: { value: string }) {
  return (
    <span
      className={`rounded-full px-2 py-1 text-xs font-bold ${tones[value] ?? "bg-red-50 text-red-700"}`}
    >
      {value}
    </span>
  );
}
