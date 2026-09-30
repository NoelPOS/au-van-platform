import type { TripOperations, WaitlistPlace } from "../types/operations";
import { formatDeparture } from "../utils/format";
import { EmptyRow, InventoryTable } from "./InventoryTable";
import { StatusBadge } from "./ui/StatusBadge";

function place(entry: WaitlistPlace): string {
  return entry.position === null ? "—" : `#${entry.position}`;
}

export function TripOperationsPanel({ trip }: { trip: TripOperations }) {
  return (
    <section className="mb-10 grid items-start gap-5 lg:grid-cols-2">
      <div className="lg:col-span-2">
        <h2 className="text-lg font-bold text-ink">
          {`${trip.origin} → ${trip.destination} · ${formatDeparture(trip.departureAt)}`}
        </h2>
        <p className="mt-1 text-sm text-muted">
          {`${trip.claimedSeats} of ${trip.totalSeats} seats claimed`}
          {" · "}
          <StatusBadge value={trip.tripStatus} />
        </p>
      </div>
      <InventoryTable headings={["Booking status", "Count"]}>
        {trip.bookingsByStatus.map((row) => (
          <tr className="border-t border-line" key={row.status}>
            <td className="px-4 py-3">
              <StatusBadge value={row.status} />
            </td>
            <td className="px-4 py-3 text-muted">{row.count}</td>
          </tr>
        ))}
      </InventoryTable>
      <InventoryTable
        headings={["Place", "Student", "Seats", "Status", "Joined"]}
      >
        {trip.waitlist.length === 0 && (
          <EmptyRow
            columns={5}
            title="Nobody is waiting"
            detail="Students join the queue once every seat is claimed."
          />
        )}
        {trip.waitlist.map((entry) => (
          <tr className="border-t border-line" key={entry.entryId}>
            <td className="px-4 py-3 font-semibold text-ink">
              {place(entry)}
            </td>
            <td className="px-4 py-3 text-muted">
              {entry.displayName ?? entry.userId}
            </td>
            <td className="px-4 py-3 text-muted">{entry.seatsWanted}</td>
            <td className="px-4 py-3">
              <StatusBadge value={entry.status} />
              {entry.promotionExpiresAt && (
                <span className="mt-1 block text-xs text-muted">
                  {`Offer ends ${formatDeparture(entry.promotionExpiresAt)}`}
                </span>
              )}
            </td>
            <td className="px-4 py-3 text-muted">
              {formatDeparture(entry.joinedAt)}
            </td>
          </tr>
        ))}
      </InventoryTable>
    </section>
  );
}
