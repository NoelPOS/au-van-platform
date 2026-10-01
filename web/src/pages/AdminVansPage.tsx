import { Plus } from "lucide-react";
import { useState } from "react";
import { InventoryPage } from "../components/InventoryPage";
import { VanForm } from "../components/VanForm";
import { Button } from "../components/ui/Button";
import { Drawer } from "../components/ui/Drawer";
import { EmptyState } from "../components/ui/EmptyState";
import { StatusBadge } from "../components/ui/StatusBadge";
import { Table, type Column } from "../components/ui/Table";
import { useSeatLayouts, useVehicles } from "../hooks/useInventoryQueries";
import type { AuthSession } from "../types/auth";
import type { Vehicle } from "../types/inventory";

export function AdminVansPage({ session }: { session: AuthSession }) {
  const vans = useVehicles(session);
  const seatLayouts = useSeatLayouts(session);
  const [editing, setEditing] = useState<Vehicle | "new" | null>(null);
  const layouts = seatLayouts.data ?? [];
  const close = () => setEditing(null);

  const columns: Column<Vehicle>[] = [
    {
      label: "Code",
      render: (van) => (
        <span className="font-mono text-[13px]">{van.code}</span>
      ),
    },
    { label: "Name", render: (van) => van.name },
    {
      label: "Seat layout",
      render: (van) => {
        const layout = layouts.find((entry) => entry.id === van.seatLayoutId);
        return layout ? (
          <>
            {layout.name}
            <span className="text-muted">{` · ${layout.seats.length} seats`}</span>
          </>
        ) : (
          "Unknown layout"
        );
      },
    },
    { label: "Status", render: (van) => <StatusBadge value={van.status} /> },
    {
      label: "",
      align: "end",
      render: (van) => (
        <Button
          aria-label={`Edit ${van.code}`}
          onClick={() => setEditing(van)}
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
          New van
        </Button>
      }
      description="The physical vans, and the seat layout each one carries."
      queries={[vans, seatLayouts]}
      title="Vans"
    >
      <Table
        columns={columns}
        empty={
          <EmptyState
            detail="Add a van once it has a seat layout to carry."
            title="No vans yet"
          />
        }
        label="Vans"
        rowKey={(van) => van.id}
        rows={vans.data ?? []}
      />
      <Drawer
        onClose={close}
        open={editing !== null}
        title={editing === "new" ? "New van" : "Edit van"}
      >
        <VanForm
          layouts={layouts}
          onDone={close}
          session={session}
          van={editing === "new" ? null : editing}
        />
      </Drawer>
    </InventoryPage>
  );
}
