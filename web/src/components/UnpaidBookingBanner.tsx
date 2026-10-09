import { ArrowRight } from "lucide-react";
import { Link } from "react-router";
import type { Booking } from "../types/booking";
import { deadlinePhrase } from "../utils/days";
import { needsPayment } from "../utils/tickets";

export function UnpaidBookingBanner({ bookings }: { bookings: Booking[] }) {
  const unpaid = bookings.find(needsPayment);
  if (!unpaid) return null;
  const sentBack = unpaid.status === "PAYMENT_REJECTED";
  return (
    <Link
      className="group flex items-center gap-3 rounded-2xl border border-accent/50 bg-warning-soft px-4 py-3.5 transition-colors hover:border-accent"
      to={`/tickets/${unpaid.id}`}
    >
      <span className="min-w-0 flex-1">
        <span className="block text-sm font-semibold text-ink">
          {sentBack
            ? "Your payment slip was sent back"
            : "Your seats are waiting for payment"}
        </span>
        <span className="mt-0.5 block text-sm text-warning">
          {unpaid.paymentDeadlineAt
            ? `Send a slip by ${deadlinePhrase(unpaid.paymentDeadlineAt)} to keep them.`
            : "Send a slip to keep them."}
        </span>
      </span>
      <span className="inline-flex shrink-0 items-center gap-1 text-sm font-semibold text-brand-700">
        Pay
        <ArrowRight
          aria-hidden
          className="size-4 transition-transform duration-150 group-hover:translate-x-0.5"
        />
      </span>
    </Link>
  );
}
