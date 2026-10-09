import { CopyPlus, Plus } from "lucide-react";
import { useNow } from "../hooks/useNow";
import type { AuthSession } from "../types/auth";
import type { Trip, VanRoute, Vehicle } from "../types/inventory";
import { bangkokTime } from "../utils/dates";
import { plural, timeSpan, linesOf } from "../utils/schedule";
import { ClearDay } from "./ClearDay";
import { Button } from "./ui/Button";
import { EmptyState } from "./ui/EmptyState";
import { RouteRule } from "./ui/RouteLine";
import { StatusBadge } from "./ui/StatusBadge";

const columns =
  "grid grid-cols-[4.25rem_minmax(0,1fr)_auto] items-center gap-3";

export function DayDepartures({
  session,
  day,
  today,
  trips,
  routes,
  vans,
  onEdit,
  onAdd,
  onCopy,
  onSaveAsTemplate,
}: {
  session: AuthSession;
  day: string;
  today: string;
  trips: Trip[];
  routes: VanRoute[];
  vans: Vehicle[];
  onEdit: (trip: Trip) => void;
  onAdd: () => void;
  onCopy: () => void;
  onSaveAsTemplate: () => void;
}) {
  const lines = linesOf(trips);
  const now = useNow();
  const ahead = trips.filter((trip) => Date.parse(trip.departureAt) > now);

  return (
    <div className="flex flex-col gap-6">
      {trips.length === 0 ? (
        <EmptyState
          detail="Add a departure, or lay a day template onto this date."
          title="Nothing scheduled"
        />
      ) : (
        <div>
          <p className="mb-3 font-mono text-[12px] text-muted">
            {plural(trips.length, "departure")} · {timeSpan(lines)}
          </p>
          <ol className="divide-y divide-dashed divide-line overflow-hidden rounded-2xl border border-line">
            {trips.map((trip) => {
              const route = routes.find((entry) => entry.id === trip.routeId);
              const van = vans.find((entry) => entry.id === trip.vehicleId);
              const cancelled = trip.status === "CANCELLED";
              return (
                <li key={trip.id}>
                  <button
                    aria-label={`${cancelled ? "See the cancelled" : "Edit the"} ${bangkokTime(trip.departureAt)} to ${route?.destination ?? "an unknown route"}`}
                    className={`${columns} w-full px-4 py-3.5 text-left transition-colors duration-150 hover:bg-paper/70`}
                    onClick={() => onEdit(trip)}
                    type="button"
                  >
                    <span
                      className={`font-mono text-[22px] font-medium tracking-tight tabular-nums ${cancelled || Date.parse(trip.departureAt) <= now ? "text-muted/70" : "text-ink"}`}
                    >
                      {bangkokTime(trip.departureAt)}
                    </span>
                    <span className="min-w-0">
                      <span className="block truncate font-medium text-ink">
                        {route?.destination ?? "Unknown route"}
                      </span>
                      <span className="mt-0.5 flex items-center gap-1.5 text-[13px] text-muted">
                        <RouteRule className="w-3" />
                        <span className="truncate">from {route?.origin}</span>
                      </span>
                      {cancelled && trip.cancellationReason && (
                        <span className="mt-1 block truncate text-[13px] text-danger italic">
                          {trip.cancellationReason}
                        </span>
                      )}
                    </span>
                    <span className="flex flex-col items-end gap-1">
                      <span className="font-mono text-[12px] text-ink">
                        {van?.code ?? "No van"}
                      </span>
                      {cancelled && <StatusBadge value={trip.status} />}
                    </span>
                  </button>
                </li>
              );
            })}
          </ol>
        </div>
      )}
      <div className="flex flex-wrap gap-2">
        {day >= today && (
          <Button onClick={onAdd}>
            <Plus aria-hidden className="size-4" />
            Add departure
          </Button>
        )}
        {lines.length > 0 && (
          <Button onClick={onCopy} variant="secondary">
            <CopyPlus aria-hidden className="size-4" />
            Copy day to…
          </Button>
        )}
      </div>
      {lines.length > 0 && (
        <p className="-mt-3 text-sm text-muted">
          Run this day again and again?{" "}
          <Button onClick={onSaveAsTemplate} variant="text">
            Save it as a template
          </Button>
        </p>
      )}
      {ahead.length > 0 && <ClearDay day={day} session={session} />}
    </div>
  );
}
