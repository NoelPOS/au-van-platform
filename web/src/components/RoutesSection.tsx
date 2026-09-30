import { useState, type FormEvent } from "react";
import { Button } from "./ui/Button";
import { StatusBadge } from "./ui/StatusBadge";
import type { AuthSession } from "../services/authService";
import { InventoryTable, EmptyRow } from "./InventoryTable";
import { ErrorMessage, FormActions, FormCard } from "./FormCard";
import { useCreateRoute, useUpdateRoute } from "../hooks/useInventoryQueries";
import type { VanRoute } from "../types/inventory";

const fieldClass =
  "mt-1 w-full rounded-lg border border-line bg-white px-3 py-2 font-normal text-ink outline-none focus:border-brand";

export function RoutesSection({
  session,
  routes,
}: {
  session: AuthSession;
  routes: VanRoute[];
}) {
  const [editing, setEditing] = useState<VanRoute | null>(null);
  const createRoute = useCreateRoute(session);
  const updateRoute = useUpdateRoute(session);
  const route = editing ?? {
    origin: "",
    destination: "",
    fare: 0,
    durationMinutes: 30,
    status: "ACTIVE" as const,
  };
  const busy = createRoute.isPending || updateRoute.isPending;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const input = {
      origin: String(form.get("origin")).trim(),
      destination: String(form.get("destination")).trim(),
      fare: Number(form.get("fare")),
      durationMinutes: Number(form.get("durationMinutes")),
    };
    if (editing)
      await updateRoute.mutateAsync({
        id: editing.id,
        input: {
          ...input,
          status: String(form.get("status")) as VanRoute["status"],
        },
      });
    else await createRoute.mutateAsync(input);
    setEditing(null);
  }

  return (
    <section className="grid items-start gap-5 lg:grid-cols-[minmax(18rem,.8fr)_minmax(0,1.6fr)]">
      <FormCard title={editing ? "Edit route" : "New route"}>
        <form
          key={editing?.id ?? "new"}
          onSubmit={(event) => void submit(event)}
          className="grid grid-cols-2 gap-3"
        >
          <label className="text-sm font-semibold text-ink">
            Origin
            <input
              className={fieldClass}
              required
              name="origin"
              defaultValue={route.origin}
              placeholder="Assumption University"
            />
          </label>
          <label className="text-sm font-semibold text-ink">
            Destination
            <input
              className={fieldClass}
              required
              name="destination"
              defaultValue={route.destination}
              placeholder="Mega Bangna"
            />
          </label>
          <label className="text-sm font-semibold text-ink">
            Fare (THB)
            <input
              className={fieldClass}
              required
              name="fare"
              type="number"
              min="0"
              step="0.01"
              defaultValue={route.fare}
            />
          </label>
          <label className="text-sm font-semibold text-ink">
            Duration (minutes)
            <input
              className={fieldClass}
              required
              name="durationMinutes"
              type="number"
              min="1"
              defaultValue={route.durationMinutes}
            />
          </label>
          {editing && (
            <label className="text-sm font-semibold text-ink">
              Status
              <select
                className={fieldClass}
                name="status"
                defaultValue={route.status}
              >
                <option>ACTIVE</option>
                <option>INACTIVE</option>
              </select>
            </label>
          )}
          <ErrorMessage error={createRoute.error ?? updateRoute.error} />
          <FormActions
            busy={busy}
            submitLabel={editing ? "Save route" : "Create route"}
            onCancel={editing ? () => setEditing(null) : undefined}
          />
        </form>
      </FormCard>
      <InventoryTable headings={["Route", "Fare", "Duration", "Status", ""]}>
        {routes.length === 0 ? (
          <EmptyRow
            columns={5}
            title="No routes yet"
            detail="Create a route before scheduling a trip."
          />
        ) : (
          routes.map((item) => (
            <tr className="border-t border-line" key={item.id}>
              <td className="px-4 py-3 font-semibold text-ink">
                {item.origin} → {item.destination}
              </td>
              <td className="px-4 py-3">{item.fare.toFixed(2)} THB</td>
              <td className="px-4 py-3">{item.durationMinutes} min</td>
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
