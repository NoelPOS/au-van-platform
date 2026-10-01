import { useState } from "react";
import { useTripOperations } from "../hooks/useOperationsQueries";
import type { AuthSession } from "../types/auth";
import type { Trip, VanRoute, Vehicle } from "../types/inventory";
import { bangkokDay, bangkokTime } from "../utils/dates";
import { EmptyState } from "./ui/EmptyState";
import { RouteLine } from "./ui/RouteLine";
import { TextLink } from "./ui/TextLink";
import { TicketStub } from "./ui/TicketStub";

function SeatsClaimed({ session, trip }: { session: AuthSession; trip: Trip }) {
  const operations = useTripOperations(session, trip.id);
  if (operations.isPending) return <span>Counting seats…</span>;
  if (!operations.data) return <span>{trip.seats.length} seats</span>;

  const { claimedSeats, totalSeats } = operations.data;
  const share = totalSeats ? (claimedSeats / totalSeats) * 100 : 0;
  return (
    <span className="inline-flex items-center gap-2.5">
      <span aria-hidden className="h-1 w-16 overflow-hidden rounded-full bg-line">
        <span
          className="block h-full rounded-full bg-brand-500"
          style={{ width: `${share}%` }}
        />
      </span>
      {`${claimedSeats} of ${totalSeats} seats claimed`}
    </span>
  );
}

export function NextDepartures({
  session,
  trips,
  routes,
  vehicles,
}: {
  session: AuthSession;
  trips: Trip[];
  routes: VanRoute[];
  vehicles: Vehicle[];
}) {
  const [now] = useState(Date.now);
  const upcoming = trips
    .filter((trip) => trip.status === "ACTIVE" && Date.parse(trip.departureAt) > now)
    .sort((a, b) => Date.parse(a.departureAt) - Date.parse(b.departureAt))
    .slice(0, 4);

  if (upcoming.length === 0) {
    return (
      <div className="rounded-2xl border border-dashed border-line">
        <EmptyState
          action={<TextLink to="/admin/trips">Schedule a trip</TextLink>}
          detail="Nothing is scheduled to leave. Schedule a trip and it will appear here."
          title="The board is clear."
        />
      </div>
    );
  }

  return (
    <ol className="flex flex-col gap-3">
      {upcoming.map((trip) => {
        const route = routes.find((entry) => entry.id === trip.routeId);
        const van = vehicles.find((entry) => entry.id === trip.vehicleId);
        return (
          <li key={trip.id}>
            <TicketStub
              stub={
                <>
                  <span className="font-display text-[2.5rem] leading-none font-light text-brand-900 tabular-nums">
                    {bangkokTime(trip.departureAt)}
                  </span>
                  <span className="mt-2 text-[11px] font-semibold tracking-[0.14em] text-muted uppercase">
                    {bangkokDay(trip.departureAt)}
                  </span>
                </>
              }
            >
              <p className="font-medium text-ink">
                {route ? (
                  <RouteLine destination={route.destination} origin={route.origin} />
                ) : (
                  "Unknown route"
                )}
              </p>
              <p className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1.5 text-sm text-muted">
                <span className="font-mono text-[13px] text-ink">
                  {van?.code ?? "Unknown van"}
                </span>
                <SeatsClaimed session={session} trip={trip} />
              </p>
            </TicketStub>
          </li>
        );
      })}
    </ol>
  );
}
