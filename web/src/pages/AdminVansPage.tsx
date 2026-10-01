import { InventoryPage } from "../components/InventoryPage";
import { VehiclesSection } from "../components/VehiclesSection";
import { useSeatLayouts, useVehicles } from "../hooks/useInventoryQueries";
import type { AuthSession } from "../types/auth";

export function AdminVansPage({ session }: { session: AuthSession }) {
  const vehicles = useVehicles(session);
  const seatLayouts = useSeatLayouts(session);

  return (
    <InventoryPage
      description="The physical vans, and the seat layout each one carries."
      queries={[vehicles, seatLayouts]}
      title="Vans"
    >
      <VehiclesSection
        session={session}
        vehicles={vehicles.data ?? []}
        layouts={seatLayouts.data ?? []}
      />
    </InventoryPage>
  );
}
