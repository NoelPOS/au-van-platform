import { useState, type FormEvent } from "react";
import { Button } from "./ui/Button";
import type { AuthSession } from "../types/auth";
import { EmptyRow, InventoryTable } from "./InventoryTable";
import { ErrorMessage, FormActions, FormCard } from "./FormCard";
import { useCreateSeatLayout, useUpdateSeatLayout } from "../hooks/useInventoryQueries";
import type { Seat, SeatLayout } from "../types/inventory";

const fieldClass =
  "mt-1 w-full rounded-lg border border-line bg-white px-3 py-2 font-normal text-ink outline-none focus:border-brand";

function parseSeats(value: string): Seat[] {
  const seats = value
    .split("\n")
    .filter(Boolean)
    .map((line) => {
      const [label, rowNumber, columnNumber] = line
        .split(",")
        .map((part) => part.trim());
      return {
        label,
        rowNumber: Number(rowNumber),
        columnNumber: Number(columnNumber),
      };
    });
  if (
    !seats.length ||
    seats.some(
      (seat) =>
        !seat.label ||
        !Number.isInteger(seat.rowNumber) ||
        !Number.isInteger(seat.columnNumber),
    )
  )
    throw new Error(
      "Use one seat per line: label, row number, column number. Example: A1, 1, 1",
    );
  return seats;
}

export function SeatLayoutsSection({
  session,
  layouts,
}: {
  session: AuthSession;
  layouts: SeatLayout[];
}) {
  const [editing, setEditing] = useState<SeatLayout | null>(null);
  const [inputError, setInputError] = useState<string | null>(null);
  const createLayout = useCreateSeatLayout(session);
  const updateLayout = useUpdateSeatLayout(session);
  const layout = editing ?? { name: "", seats: [] };
  const busy = createLayout.isPending || updateLayout.isPending;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    try {
      const input = {
        name: String(form.get("name")).trim(),
        seats: parseSeats(String(form.get("seats"))),
      };
      setInputError(null);
      if (editing) await updateLayout.mutateAsync({ id: editing.id, input });
      else await createLayout.mutateAsync(input);
      setEditing(null);
    } catch (error) {
      setInputError(
        error instanceof Error ? error.message : "Invalid seat layout.",
      );
    }
  }

  return (
    <section className="grid items-start gap-5 lg:grid-cols-[minmax(18rem,.8fr)_minmax(0,1.6fr)]">
      <FormCard title={editing ? "Edit seat layout" : "New seat layout"}>
        <form
          key={editing?.id ?? "new"}
          onSubmit={(event) => void submit(event)}
          className="grid gap-3"
        >
          <label className="text-sm font-semibold text-ink">
            Layout name
            <input
              className={fieldClass}
              required
              name="name"
              defaultValue={layout.name}
              placeholder="Toyota Hiace 12-seat"
            />
          </label>
          <label className="text-sm font-semibold text-ink">
            Seats
            <textarea
              className={`${fieldClass} min-h-32`}
              required
              name="seats"
              defaultValue={layout.seats
                .map(
                  (seat) =>
                    `${seat.label}, ${seat.rowNumber}, ${seat.columnNumber}`,
                )
                .join("\n")}
              placeholder={"A1, 1, 1\nA2, 1, 2"}
            />
            <span className="mt-1 block text-xs font-normal text-muted">
              One seat per line: label, row number, column number.
            </span>
          </label>
          {inputError && (
            <p
              className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700"
              role="alert"
            >
              {inputError}
            </p>
          )}
          <ErrorMessage error={createLayout.error ?? updateLayout.error} />
          <FormActions
            busy={busy}
            submitLabel={editing ? "Save layout" : "Create layout"}
            onCancel={editing ? () => setEditing(null) : undefined}
          />
        </form>
      </FormCard>
      <InventoryTable headings={["Layout", "Seats", "Preview", ""]}>
        {layouts.length === 0 ? (
          <EmptyRow
            columns={4}
            title="No seat layouts yet"
            detail="Create a reusable layout before adding a vehicle."
          />
        ) : (
          layouts.map((item) => (
            <tr className="border-t border-line" key={item.id}>
              <td className="px-4 py-3 font-semibold text-ink">{item.name}</td>
              <td className="px-4 py-3">{item.seats.length}</td>
              <td className="px-4 py-3">
                {item.seats.map((seat) => seat.label).join(", ")}
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
