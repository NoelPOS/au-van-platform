import { ArrowLeft } from "lucide-react";
import { Link, useLocation, useParams } from "react-router";
import { BoardingPass } from "../components/BoardingPass";
import { CancelBooking } from "../components/CancelBooking";
import { SlipUpload } from "../components/SlipUpload";
import { TicketTimeline } from "../components/TicketTimeline";
import { EmptyState } from "../components/ui/EmptyState";
import { Skeleton } from "../components/ui/Skeleton";
import { useMyBookings, useSubmitPaymentProof } from "../hooks/useBookingQueries";
import { useStudent } from "../hooks/useStudent";
import type { Booking } from "../types/booking";
import { deadlinePhrase } from "../utils/days";
import { messageOf } from "../utils/errors";
import { formatBaht } from "../utils/format";
import { lastDetail, needsPayment, wasExpired } from "../utils/tickets";

function headline(booking: Booking, justBooked: boolean): [string, string] {
  switch (booking.status) {
    case "PENDING_PAYMENT":
      return [
        justBooked ? "Seats reserved" : "Pay to keep your seats",
        booking.paymentDeadlineAt
          ? `Send your payment slip by ${deadlinePhrase(booking.paymentDeadlineAt)}, or the seats go back on sale.`
          : "Send your payment slip to keep the seats.",
      ];
    case "PAYMENT_REJECTED":
      return [
        "Your slip was sent back",
        booking.paymentDeadlineAt
          ? `Send a new slip by ${deadlinePhrase(booking.paymentDeadlineAt)} to keep the seats.`
          : "Send a new slip to keep the seats.",
      ];
    case "PAYMENT_UNDER_REVIEW":
      return ["Slip received", "Staff are checking it. We message you on LINE once it is approved."];
    case "CONFIRMED":
      return ["You’re all set", "Show this pass when you board."];
    case "CANCELLED":
      return wasExpired(booking)
        ? ["This booking expired", "No slip arrived in time, so the seats went back on sale."]
        : ["This booking was cancelled", "The seats went back on sale."];
  }
}

export function StudentTicketPage() {
  const { bookingId } = useParams();
  const { session, setNotice } = useStudent();
  const location = useLocation();
  const bookings = useMyBookings(session);
  const submit = useSubmitPaymentProof(session);
  const carried = (location.state as { booked?: Booking } | null)?.booked;
  const booking =
    bookings.data?.find((candidate) => candidate.id === bookingId) ??
    (carried?.id === bookingId ? carried : undefined);

  if (!booking && bookings.isPending) return <Skeleton className="h-96" />;
  if (!booking)
    return (
      <EmptyState
        action={<Link className="text-sm font-semibold text-brand-700 underline underline-offset-4" to="/tickets">See your tickets</Link>}
        title="We could not find that ticket"
      />
    );

  const [title, detail] = headline(booking, carried?.id === booking.id);
  const rejection = booking.status === "PAYMENT_REJECTED" ? lastDetail(booking, "PAYMENT_REJECTED") : null;

  async function send(file: File) {
    if (!booking) return;
    setNotice(null);
    try {
      await submit.mutateAsync({ bookingId: booking.id, file });
      setNotice({ tone: "status", message: "Payment slip received. Staff confirm the booking once they have checked it." });
    } catch {
      // Shown beside the upload by SlipUpload.
    }
  }

  return (
    <>
      <Link className="-ml-1 inline-flex min-h-10 items-center gap-1.5 self-start text-sm font-medium text-muted hover:text-ink" to="/tickets">
        <ArrowLeft aria-hidden className="size-4" />
        Tickets
      </Link>
      <header>
        <h1 className="font-display text-[30px] leading-tight tracking-tight text-brand-900">{title}</h1>
        <p className="mt-1.5 text-[15px] text-muted">{detail}</p>
      </header>
      <BoardingPass booking={booking} />
      {rejection && (
        <blockquote className="rounded-2xl border border-danger/20 bg-danger-soft px-4 py-3.5">
          <p className="font-mono text-[10px] tracking-[0.16em] text-danger uppercase">Note from staff</p>
          <p className="mt-1 text-sm text-ink">{rejection}</p>
        </blockquote>
      )}
      {needsPayment(booking) && (
        <SlipUpload
          amount={formatBaht(booking.totalFare)}
          error={submit.error ? messageOf(submit.error) : null}
          onChoose={submit.reset}
          onSend={(file) => void send(file)}
          sending={submit.isPending}
        />
      )}
      <TicketTimeline booking={booking} />
      {needsPayment(booking) && <CancelBooking booking={booking} />}
    </>
  );
}
