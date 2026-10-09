import { Clock } from "lucide-react";
import type { BookingEligibility } from "../types/booking";

export function CooldownBanner({ eligibility }: { eligibility: BookingEligibility | undefined }) {
  if (eligibility?.reason !== "booking_cooldown" || !eligibility.message) return null;
  return (
    <aside className="flex items-start gap-3 rounded-2xl border border-line bg-card px-4 py-3.5" role="status">
      <Clock aria-hidden className="mt-0.5 size-5 shrink-0 text-muted" strokeWidth={1.75} />
      <div>
        <p className="text-sm font-semibold text-ink">Booking is paused for now</p>
        <p className="mt-0.5 text-sm text-muted">{eligibility.message}</p>
      </div>
    </aside>
  );
}
