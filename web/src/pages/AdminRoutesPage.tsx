import { InventoryPage } from "../components/InventoryPage";
import { RoutesSection } from "../components/RoutesSection";
import { useRoutes } from "../hooks/useInventoryQueries";
import type { AuthSession } from "../types/auth";

export function AdminRoutesPage({ session }: { session: AuthSession }) {
  const routes = useRoutes(session);

  return (
    <InventoryPage
      description="Journeys, their fares, and how long they take."
      queries={[routes]}
      title="Routes"
    >
      <RoutesSection session={session} routes={routes.data ?? []} />
    </InventoryPage>
  );
}
