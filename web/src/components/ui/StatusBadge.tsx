/**
 * Three tones, not two. A booking waiting for payment or sitting in front of
 * an administrator is neither a success nor a failure, and painting it green
 * would tell a student their seat is paid for when it is not — while red would
 * say something is wrong when nothing is. Amber is the "still in progress"
 * tone. Anything unrecognised keeps falling back to the failure colour.
 */
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
