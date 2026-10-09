import { useState } from "react";
import { Link } from "react-router";
import { TicketListItem } from "../components/TicketListItem";
import { Button } from "../components/ui/Button";
import { EmptyState } from "../components/ui/EmptyState";
import { FailurePanel } from "../components/ui/FailurePanel";
import { SegmentedControl } from "../components/ui/SegmentedControl";
import { Skeleton } from "../components/ui/Skeleton";
import { useMyBookings } from "../hooks/useBookingQueries";
import { useStudent } from "../hooks/useStudent";
import { isUpcoming } from "../utils/tickets";

const pageSize = 10;

export function StudentTicketsPage() {
  const { session } = useStudent();
  const bookings = useMyBookings(session);
  const [view, setView] = useState<"upcoming" | "past">("upcoming");
  const [shown, setShown] = useState(pageSize);

  const all = bookings.data ?? [];
  const departure = (value: string) => Date.parse(value);
  const upcoming = all
    .filter((booking) => isUpcoming(booking))
    .sort((left, right) => departure(left.trip.departureAt) - departure(right.trip.departureAt));
  const past = all
    .filter((booking) => !isUpcoming(booking))
    .sort((left, right) => departure(right.trip.departureAt) - departure(left.trip.departureAt));
  const list = view === "upcoming" ? upcoming : past.slice(0, shown);

  return (
    <>
      <header>
        <p className="font-mono text-[11px] tracking-[0.18em] text-muted uppercase">Tickets</p>
        <h1 className="mt-1.5 font-display text-[34px] leading-[1.05] tracking-tight text-brand-900">
          Your trips
        </h1>
      </header>
      <SegmentedControl
        hideLabel
        label="Show"
        onChange={(next) => {
          setView(next);
          setShown(pageSize);
        }}
        options={[
          { value: "upcoming", label: `Upcoming · ${upcoming.length}` },
          { value: "past", label: "Past" },
        ]}
        value={view}
      />
      {bookings.isPending && <Skeleton className="h-28" />}
      {bookings.error && <FailurePanel error={bookings.error} title="Could not load your tickets" />}
      {bookings.data && list.length === 0 && (
        <EmptyState
          action={
            view === "upcoming" ? (
              <Link className="text-sm font-semibold text-brand-700 underline underline-offset-4" to="/">
                Find a departure
              </Link>
            ) : undefined
          }
          title={view === "upcoming" ? "No trips coming up" : "No past trips yet"}
        />
      )}
      <ul className="flex flex-col gap-3">
        {list.map((booking) => (
          <li key={booking.id}>
            <TicketListItem booking={booking} />
          </li>
        ))}
      </ul>
      {view === "past" && past.length > shown && (
        <Button className="self-center" onClick={() => setShown(shown + pageSize)} variant="secondary">
          Show older tickets
        </Button>
      )}
    </>
  );
}
