import { useState, type FormEvent } from "react";
import { Button } from "./ui/Button";
import { StatusBadge } from "./ui/StatusBadge";
import type { AuthSession } from "../services/authService";
import { EmptyRow, InventoryTable } from "./InventoryTable";
import { ErrorMessage, FormActions, FormCard } from "./FormCard";
import { useCreateVehicle, useUpdateVehicle } from "../hooks/useInventoryQueries";
import type { SeatLayout, Vehicle } from "../types/inventory";

const fieldClass =
  "mt-1 w-full rounded-lg border border-line bg-white px-3 py-2 font-normal text-ink outline-none focus:border-brand";

export function VehiclesSection({
  session,
  vehicles,
  layouts,
}: {
  session: AuthSession;
  vehicles: Vehicle[];
  layouts: SeatLayout[];
}) {
  const [editing, setEditing] = useState<Vehicle | null>(null);
  const createVehicle = useCreateVehicle(session);
  const updateVehicle = useUpdateVehicle(session);
  const vehicle = editing ?? {
    code: "",
    name: "",
    seatLayoutId: layouts[0]?.id ?? "",
    status: "ACTIVE" as const,
  };
  const busy = createVehicle.isPending || updateVehicle.isPending;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const input = {
      code: String(form.get("code")).trim(),
      name: String(form.get("name")).trim(),
      seatLayoutId: String(form.get("seatLayoutId")),
      status: String(form.get("status")) as Vehicle["status"],
    };
    if (editing) await updateVehicle.mutateAsync({ id: editing.id, input });
    else await createVehicle.mutateAsync(input);
    setEditing(null);
  }

  return (
    <section className="grid items-start gap-5 lg:grid-cols-[minmax(18rem,.8fr)_minmax(0,1.6fr)]">
      <FormCard title={editing ? "Edit vehicle" : "New vehicle"}>
        <form
          key={editing?.id ?? "new"}
          onSubmit={(event) => void submit(event)}
          className="grid grid-cols-2 gap-3"
        >
          <label className="text-sm font-semibold text-ink">
            Vehicle code
            <input
              className={fieldClass}
              required
              name="code"
              defaultValue={vehicle.code}
              placeholder="VAN-01"
            />
          </label>
          <label className="text-sm font-semibold text-ink">
            Name
            <input
              className={fieldClass}
              required
              name="name"
              defaultValue={vehicle.name}
              placeholder="White Toyota Hiace"
            />
          </label>
          <label className="col-span-full text-sm font-semibold text-ink">
            Seat layout
            <select
              className={fieldClass}
              required
              name="seatLayoutId"
              defaultValue={vehicle.seatLayoutId}
              disabled={!layouts.length}
            >
              <option value="">Select a layout</option>
              {layouts.map((layout) => (
                <option key={layout.id} value={layout.id}>
                  {layout.name}
                </option>
              ))}
            </select>
          </label>
          {editing && (
            <label className="text-sm font-semibold text-ink">
              Status
              <select
                className={fieldClass}
                name="status"
                defaultValue={vehicle.status}
              >
                <option>ACTIVE</option>
                <option>INACTIVE</option>
              </select>
            </label>
          )}
          {!layouts.length && (
            <p className="col-span-full text-sm text-muted">
              Create a seat layout first.
            </p>
          )}
          <ErrorMessage error={createVehicle.error ?? updateVehicle.error} />
          <FormActions
            busy={busy}
            submitLabel={editing ? "Save vehicle" : "Create vehicle"}
            onCancel={editing ? () => setEditing(null) : undefined}
          />
        </form>
      </FormCard>
      <InventoryTable
        headings={["Code", "Vehicle", "Seat layout", "Status", ""]}
      >
        {vehicles.length === 0 ? (
          <EmptyRow
            columns={5}
            title="No vehicles yet"
            detail="Add a van after creating its seat layout."
          />
        ) : (
          vehicles.map((item) => (
            <tr className="border-t border-line" key={item.id}>
              <td className="px-4 py-3 font-semibold text-ink">{item.code}</td>
              <td className="px-4 py-3">{item.name}</td>
              <td className="px-4 py-3">
                {layouts.find((layout) => layout.id === item.seatLayoutId)
                  ?.name ?? "Unknown layout"}
              </td>
              <td className="px-4 py-3">
                <StatusBadge value={item.status} />
              </td>
              <td className="px-4 py-3 text-right">
                <Button variant="text" onClick={() => setEditing(item)}>
                  Edit
                </Button>
              </td>
            </tr>
          ))
        )}
      </InventoryTable>
    </section>
  );
}
