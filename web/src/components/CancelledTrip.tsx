import type { Trip, VanRoute } from "../types/inventory";
import { bangkokDateTime } from "../utils/dates";
import { StatusStamp } from "./StatusStamp";
import { RouteLine } from "./ui/RouteLine";

export function CancelledTrip({ trip, route }: { trip: Trip; route?: VanRoute }) {
  return (
    <div className="grid gap-6">
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <p className="font-mono text-[13px] text-muted tabular-nums">{bangkokDateTime(trip.departureAt)}</p>
          {route && (
            <p className="mt-1 font-medium text-ink">
              <RouteLine destination={route.destination} origin={route.origin} />
            </p>
          )}
        </div>
        <StatusStamp label="Cancelled" tone="danger" />
      </div>
      {trip.cancellationReason && (
        <blockquote className="rounded-2xl border border-danger/20 bg-danger-soft px-4 py-3.5">
          <p className="font-mono text-[10px] tracking-[0.16em] text-danger uppercase">Reason given</p>
          <p className="mt-1 text-sm text-ink">{trip.cancellationReason}</p>
        </blockquote>
      )}
      <p className="text-sm text-muted">
        A cancelled trip stays cancelled. If the van runs after all, schedule a new departure.
      </p>
    </div>
  );
}
