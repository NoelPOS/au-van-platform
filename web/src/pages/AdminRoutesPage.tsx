import { Plus } from "lucide-react";
import { useState } from "react";
import { InventoryPage } from "../components/InventoryPage";
import { RouteForm } from "../components/RouteForm";
import { Button } from "../components/ui/Button";
import { Drawer } from "../components/ui/Drawer";
import { EmptyState } from "../components/ui/EmptyState";
import { RouteLine } from "../components/ui/RouteLine";
import { StatusBadge } from "../components/ui/StatusBadge";
import { Table, type Column } from "../components/ui/Table";
import { useRoutes } from "../hooks/useInventoryQueries";
import type { AuthSession } from "../types/auth";
import type { VanRoute } from "../types/inventory";
import { formatFare } from "../utils/format";

export function AdminRoutesPage({ session }: { session: AuthSession }) {
  const routes = useRoutes(session);
  const [editing, setEditing] = useState<VanRoute | "new" | null>(null);
  const close = () => setEditing(null);

  const columns: Column<VanRoute>[] = [
    {
      label: "Route",
      render: (route) => (
        <RouteLine destination={route.destination} origin={route.origin} />
      ),
    },
    { label: "Fare", render: (route) => formatFare(route.fare) },
    { label: "Duration", render: (route) => `${route.durationMinutes} min` },
    { label: "Status", render: (route) => <StatusBadge value={route.status} /> },
    {
      label: "",
      align: "end",
      render: (route) => (
        <Button
          aria-label={`Edit ${route.origin} → ${route.destination}`}
          onClick={() => setEditing(route)}
          variant="text"
        >
          Edit
        </Button>
      ),
    },
  ];

  return (
    <InventoryPage
      action={
        <Button onClick={() => setEditing("new")}>
          <Plus aria-hidden className="size-4" />
          New route
        </Button>
      }
      description="Journeys, their fares, and how long they take."
      queries={[routes]}
      title="Routes"
    >
      <Table
        columns={columns}
        empty={
          <EmptyState
            detail="Create a route before scheduling a trip."
            title="No routes yet"
          />
        }
        label="Routes"
        rowKey={(route) => route.id}
        rows={routes.data ?? []}
      />
      <Drawer
        onClose={close}
        open={editing !== null}
        title={editing === "new" ? "New route" : "Edit route"}
      >
        <RouteForm
          onDone={close}
          route={editing === "new" ? null : editing}
          session={session}
        />
      </Drawer>
    </InventoryPage>
  );
}
