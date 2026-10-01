import { InventoryPage } from "../components/InventoryPage";
import { TripsSection } from "../components/TripsSection";
import { useRoutes, useTrips, useVehicles } from "../hooks/useInventoryQueries";
import type { AuthSession } from "../types/auth";

export function AdminTripsPage({ session }: { session: AuthSession }) {
  const trips = useTrips(session);
  const routes = useRoutes(session);
  const vehicles = useVehicles(session);

  return (
    <InventoryPage
      description="Every scheduled departure, in Bangkok time."
      queries={[trips, routes, vehicles]}
      title="Trips"
    >
      <TripsSection
        session={session}
        trips={trips.data ?? []}
        routes={routes.data ?? []}
        vehicles={vehicles.data ?? []}
      />
    </InventoryPage>
  );
}
