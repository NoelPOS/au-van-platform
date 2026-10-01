import { useTripSeatStates } from "../hooks/useOperationsQueries";
import type { AuthSession } from "../types/auth";
import type { SeatState } from "../types/booking";
import { fromSeats, seatAt } from "../utils/seatLayout";
import { FailurePanel } from "./ui/FailurePanel";
import { Skeleton } from "./ui/Skeleton";
import { VanSeatPlan } from "./VanSeatPlan";

const open = { label: "open", className: "border border-line bg-paper text-ink" };
const held = { label: "held", className: "border border-accent bg-accent/25 text-brand-900" };
const booked = { label: "booked", className: "bg-brand-600 text-white" };

const looks: Record<SeatState, typeof open> = {
  AVAILABLE: open,
  HELD: held,
  HELD_BY_YOU: held,
  BOOKED: booked,
};

export function SeatOccupancy({
  session,
  tripId,
  claimed,
  total,
}: {
  session: AuthSession;
  tripId: string;
  claimed: number;
  total: number;
}) {
  const seatMap = useTripSeatStates(session, tripId);
  const seats = seatMap.data?.seats ?? [];
  const { rows, columns } = fromSeats(seats);

  return (
    <div>
      <p className="mb-4 text-sm text-ink">{`${claimed} of ${total} seats claimed`}</p>
      {seatMap.isPending && (
        <div role="status">
          <span className="sr-only">Loading the seat plan…</span>
          <Skeleton className="h-72 max-w-56" />
        </div>
      )}
      {seatMap.error && <FailurePanel title="Could not load the seat plan" error={seatMap.error} />}
      {seatMap.data && (
        <>
          <VanSeatPlan
            cellSize={44}
            columns={columns}
            label="Seat occupancy"
            renderCell={(rowNumber, columnNumber) => {
              const seat = seatAt(seats, rowNumber, columnNumber);
              if (!seat) return null;
              const look = looks[seat.state];
              return (
                <span
                  aria-label={`${seat.label}, ${look.label}`}
                  className={`grid h-full w-full place-items-center rounded-md font-mono text-[11px] font-medium ${look.className}`}
                  role="img"
                >
                  {seat.label}
                </span>
              );
            }}
            rows={rows}
          />
          <ul aria-label="Seat legend" className="mt-4 flex flex-wrap gap-4 text-xs text-muted">
            {[open, held, booked].map((look) => (
              <li className="flex items-center gap-1.5 capitalize" key={look.label}>
                <span aria-hidden="true" className={`size-3 rounded-sm ${look.className}`} />
                {look.label}
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  );
}
