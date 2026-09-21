export function StatusBadge({ value }: { value: string }) {
  const active = value === "ACTIVE" || value === "CONFIRMED";
  return (
    <span
      className={`rounded-full px-2 py-1 text-xs font-bold ${active ? "bg-emerald-50 text-emerald-700" : "bg-red-50 text-red-700"}`}
    >
      {value}
    </span>
  );
}
