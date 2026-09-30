import { useState } from "react";
import { Button } from "../components/ui/Button";
import type { AuthSession } from "../types/auth";
import { InventoryTabs, type InventoryTab } from "../components/InventoryTabs";
import { useRoutes, useSeatLayouts, useTrips, useVehicles } from "../hooks/useInventoryQueries";
import { RoutesSection } from "../components/RoutesSection";
import { SeatLayoutsSection } from "../components/SeatLayoutsSection";
import { TripsSection } from "../components/TripsSection";
import { VehiclesSection } from "../components/VehiclesSection";

function queryError(...errors: (Error | null)[]): Error | null {
  return errors.find((error) => error instanceof Error) ?? null;
}

export function AdminInventoryPage({ session }: { session: AuthSession }) {
  const [tab, setTab] = useState<InventoryTab>("routes");
  const routes = useRoutes(session);
  const seatLayouts = useSeatLayouts(session);
  const vehicles = useVehicles(session);
  const trips = useTrips(session);
  const error = queryError(
    routes.error,
    seatLayouts.error,
    vehicles.error,
    trips.error,
  );

  function retry() {
    void Promise.all([
      routes.refetch(),
      seatLayouts.refetch(),
      vehicles.refetch(),
      trips.refetch(),
    ]);
  }

  if (
    routes.isPending ||
    seatLayouts.isPending ||
    vehicles.isPending ||
    trips.isPending
  ) {
    return (
      <main className="mx-auto max-w-6xl px-6 py-16 text-center text-muted">
        Loading transport inventory…
      </main>
    );
  }

  if (error) {
    return (
      <main className="mx-auto max-w-xl px-6 py-16">
        <section className="rounded-2xl border border-red-200 bg-white p-6">
          <h1 className="text-2xl font-bold text-ink">
            Could not load inventory
          </h1>
          <p className="my-4 text-muted">{error.message}</p>
          <Button onClick={retry}>Try again</Button>
        </section>
      </main>
    );
  }

  return (
    <main className="mx-auto max-w-6xl px-6 py-10">
      <header className="mb-8 flex flex-col justify-between gap-4 sm:flex-row sm:items-start">
        <div>
          <p className="text-xs font-bold uppercase tracking-widest text-brand">
            AU Van Admin
          </p>
          <h1 className="mt-2 text-4xl font-bold tracking-tight text-ink">
            Transport inventory
          </h1>
          <p className="mt-2 text-muted">
            Set up the routes, vans, seats, and departures that bookings will
            use.
          </p>
        </div>
        <p className="rounded-full border border-line bg-white px-3 py-2 text-sm text-muted">
          Signed in as {session.user.displayName ?? "Administrator"}
        </p>
      </header>
      <InventoryTabs active={tab} onChange={setTab} />
      {tab === "routes" && (
        <RoutesSection session={session} routes={routes.data ?? []} />
      )}
      {tab === "seatLayouts" && (
        <SeatLayoutsSection
          session={session}
          layouts={seatLayouts.data ?? []}
        />
      )}
      {tab === "vehicles" && (
        <VehiclesSection
          session={session}
          vehicles={vehicles.data ?? []}
          layouts={seatLayouts.data ?? []}
        />
      )}
      {tab === "trips" && (
        <TripsSection
          session={session}
          trips={trips.data ?? []}
          routes={routes.data ?? []}
          vehicles={vehicles.data ?? []}
        />
      )}
    </main>
  );
}
