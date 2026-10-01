import { useState } from "react";
import type { AuthSession } from "../types/auth";
import { SeatLayoutBuilder } from "./SeatLayoutBuilder";
import { VanSeatPlan } from "./VanSeatPlan";
import { useCreateSeatLayout, useUpdateSeatLayout } from "../hooks/useInventoryQueries";
import type { Seat, SeatLayout } from "../types/inventory";
import { fromSeats, seatAt } from "../utils/seatLayout";

function VanThumbnail({ seats }: { seats: Seat[] }) {
  const { rows, columns } = fromSeats(seats);
  return (
    <VanSeatPlan
      cellSize={9}
      columns={columns}
      renderCell={(rowNumber, columnNumber) =>
        seatAt(seats, rowNumber, columnNumber) && (
          <span className="block h-full w-full rounded-[3px] bg-brand-600" />
        )
      }
      rows={rows}
    />
  );
}

export function SeatLayoutsSection({
  session,
  layouts,
}: {
  session: AuthSession;
  layouts: SeatLayout[];
}) {
  const [editing, setEditing] = useState<SeatLayout | null>(null);
  const [saved, setSaved] = useState(0);
  const createLayout = useCreateSeatLayout(session);
  const updateLayout = useUpdateSeatLayout(session);
  const mutation = editing ? updateLayout : createLayout;

  function save(input: { name: string; seats: Seat[] }) {
    const done = {
      onSuccess: () => {
        setEditing(null);
        setSaved((count) => count + 1);
      },
    };
    if (editing) updateLayout.mutate({ id: editing.id, input }, done);
    else createLayout.mutate(input, done);
  }

  return (
    <section className="grid items-start gap-6 xl:grid-cols-[minmax(0,1.6fr)_minmax(16rem,1fr)]">
      <SeatLayoutBuilder
        busy={mutation.isPending}
        error={mutation.error}
        key={`${editing?.id ?? "new"}-${saved}`}
        layout={editing}
        onCancel={editing ? () => setEditing(null) : undefined}
        onSave={save}
      />
      <div className="grid gap-3">
        <h2 className="text-[0.7rem] font-semibold uppercase tracking-[0.14em] text-muted">
          Saved layouts
        </h2>
        {layouts.length === 0 ? (
          <p className="rounded-2xl border border-dashed border-line px-5 py-8 text-center">
            <strong className="block text-ink">No seat layouts yet</strong>
            <span className="mt-1 block text-sm text-muted">
              Create a reusable layout before adding a vehicle.
            </span>
          </p>
        ) : (
          <ul aria-label="Saved layouts" className="grid gap-3">
            {layouts.map((item) => (
              <li
                className="flex items-center gap-4 rounded-2xl border border-line bg-card p-4"
                key={item.id}
              >
                <div className="w-14 shrink-0">
                  <VanThumbnail seats={item.seats} />
                </div>
                <div className="min-w-0 flex-1">
                  <h3 className="truncate font-semibold text-brand-900">{item.name}</h3>
                  <p className="text-sm tabular-nums text-muted">
                    {item.seats.length} {item.seats.length === 1 ? "seat" : "seats"}
                  </p>
                </div>
                <button
                  className="text-sm font-semibold text-brand-600 underline underline-offset-4 hover:text-brand-900 focus-visible:outline-2 focus-visible:outline-brand-500"
                  onClick={() => setEditing(item)}
                  type="button"
                >
                  Edit <span className="sr-only">{item.name}</span>
                  <span aria-hidden="true">→</span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}
