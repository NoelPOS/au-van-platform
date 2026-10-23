import { ArrowLeft } from "lucide-react";
import { useNavigate, useSearchParams } from "react-router";
import { CooldownBanner } from "../components/CooldownBanner";
import { DayStrip } from "../components/DayStrip";
import { DepartureBoard } from "../components/DepartureBoard";
import { RouteFilters } from "../components/RouteFilters";
import { RoutePicker } from "../components/RoutePicker";
import { UnpaidBookingBanner } from "../components/UnpaidBookingBanner";
import { Button } from "../components/ui/Button";
import { EmptyState } from "../components/ui/EmptyState";
import { FailurePanel } from "../components/ui/FailurePanel";
import { Skeleton } from "../components/ui/Skeleton";
import {
  useAvailableTrips,
  useBookingEligibility,
  useMyBookings,
} from "../hooks/useBookingQueries";
import { useStudent } from "../hooks/useStudent";
import { useWaitlist } from "../hooks/useWaitlist";
import { dayOf, formatLongDate, groupByDay } from "../utils/days";
import { formatBaht } from "../utils/format";
import {
  groupByRoute,
  matchesFilter,
  type RouteFilter,
} from "../utils/tripRoutes";

export function StudentTripsPage() {
  const { session, setNotice } = useStudent();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const trips = useAvailableTrips(session);
  const bookings = useMyBookings(session);
  const eligibility = useBookingEligibility(session);
  const waitlist = useWaitlist(session, setNotice);

  const routes = groupByRoute(trips.data ?? []);
  const route =
    routes.find((entry) => entry.routeId === params.get("route")) ??
    (routes.length === 1 ? routes[0] : undefined);
  const filter: RouteFilter = {
    from: params.get("from") ?? "",
    to: params.get("to") ?? "",
  };
  const matching = routes.filter((entry) => matchesFilter(entry, filter));
  const byDay = groupByDay(route?.trips ?? [], (trip) => trip.departureAt);
  const keys = [...byDay.keys()];
  const requested = params.get("day");
  const selected = requested && byDay.has(requested) ? requested : keys[0];
  const departures = selected ? (byDay.get(selected) ?? []) : [];

  const subtitle =
    route && selected
      ? formatLongDate(departures[0].departureAt)
      : routes.length > 1
        ? "Where are you heading?"
        : null;

  function show(
    next: { route?: string; day?: string } & Partial<RouteFilter>,
    replace = Boolean(next.day),
  ) {
    const search = new URLSearchParams();
    for (const [key, value] of Object.entries(next)) {
      if (value) search.set(key, value);
    }
    setParams(search, { replace });
  }

  return (
    <>
      <header>
        <p className="font-mono text-[11px] tracking-[0.18em] text-muted uppercase">
          Departures
        </p>
        <h1 className="mt-1.5 font-display text-[34px] leading-[1.05] tracking-tight text-brand-900">
          Catch the next van
        </h1>
        {subtitle && (
          <p className="mt-1.5 font-display text-lg text-muted italic">
            {subtitle}
          </p>
        )}
      </header>

      <UnpaidBookingBanner bookings={bookings.data ?? []} />
      <CooldownBanner eligibility={eligibility.data} />

      {trips.isPending && (
        <div className="flex flex-col gap-3" role="status">
          <span className="sr-only">Loading departures…</span>
          <Skeleton className="h-24" />
          <Skeleton className="h-64" />
        </div>
      )}
      {trips.error && (
        <FailurePanel error={trips.error} title="Could not load departures">
          <Button onClick={() => void trips.refetch()} variant="secondary">
            Try again
          </Button>
        </FailurePanel>
      )}
      {trips.data && routes.length === 0 && (
        <EmptyState
          detail="No vans are scheduled right now. New departures appear here as soon as staff add them."
          title="Nothing scheduled yet"
        />
      )}
      {!route && routes.length > 0 && (
        <>
          <RouteFilters
            filter={filter}
            onChange={(next) => show(next, true)}
            routes={routes}
          />
          {matching.length > 0 ? (
            <RoutePicker
              onSelect={(picked) => show({ ...filter, route: picked.routeId })}
              routes={matching}
            />
          ) : (
            <EmptyState
              action={
                <button
                  className="text-sm font-semibold text-brand-700 underline underline-offset-4"
                  onClick={() => show({}, true)}
                  type="button"
                >
                  Clear filters
                </button>
              }
              detail="No van runs between those stops right now."
              title="No matching routes"
            />
          )}
        </>
      )}
      {route && selected && (
        <>
          <div className="flex items-center justify-between gap-3">
            <p className="min-w-0 text-sm">
              <span className="block truncate font-semibold text-ink">
                {`To ${route.destination}`}
              </span>
              <span className="block truncate text-muted">
                {`from ${route.origin} · ${formatBaht(route.fare)}`}
              </span>
            </p>
            {routes.length > 1 && (
              <button
                className="-mr-1 inline-flex min-h-10 shrink-0 items-center gap-1.5 text-sm font-medium text-brand-700 hover:text-brand-900"
                onClick={() => show(filter)}
                type="button"
              >
                <ArrowLeft aria-hidden className="size-4" />
                Change route
              </button>
            )}
          </div>
          <DayStrip
            counts={new Map(keys.map((key) => [key, byDay.get(key)?.length ?? 0]))}
            days={keys.map((key) => dayOf(key))}
            onSelect={(key) =>
              show({ ...filter, route: route.routeId, day: key })
            }
            selected={selected}
          />
          <DepartureBoard
            onJoinWaitlist={(trip) => void waitlist.join(trip)}
            onLeaveWaitlist={(entry) => void waitlist.leave(entry)}
            onSelect={(trip) => navigate(`/trips/${trip.id}`)}
            trips={departures}
            waitlist={waitlist.entries.data ?? []}
            waitlistPending={waitlist.pending}
          />
        </>
      )}
    </>
  );
}
