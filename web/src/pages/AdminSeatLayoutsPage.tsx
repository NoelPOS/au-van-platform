import { InventoryPage } from "../components/InventoryPage";
import { SeatLayoutsSection } from "../components/SeatLayoutsSection";
import { useSeatLayouts } from "../hooks/useInventoryQueries";
import type { AuthSession } from "../types/auth";

export function AdminSeatLayoutsPage({ session }: { session: AuthSession }) {
  const seatLayouts = useSeatLayouts(session);

  return (
    <InventoryPage
      description="Reusable seating plans that each van is built from."
      queries={[seatLayouts]}
      title="Seat layouts"
    >
      <SeatLayoutsSection
        session={session}
        layouts={seatLayouts.data ?? []}
      />
    </InventoryPage>
  );
}
