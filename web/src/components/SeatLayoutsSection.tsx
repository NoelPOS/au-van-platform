import { useState } from "react";
import type { AuthSession } from "../types/auth";
import { SeatLayoutBuilder } from "./SeatLayoutBuilder";
import { SavedLayoutRow } from "./SavedLayoutRow";
import { EmptyState } from "./ui/EmptyState";
import { Panel } from "./ui/Panel";
import { useToast } from "../hooks/useToast";
import { useCreateSeatLayout, useUpdateSeatLayout } from "../hooks/useInventoryQueries";
import type { Seat, SeatLayout } from "../types/inventory";

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
  const notify = useToast();

  function save(input: { name: string; seats: Seat[] }) {
    const done = {
      onSuccess: () => {
        notify(editing ? "Seat layout saved." : "Seat layout created.");
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
        <h2 className="text-[11px] font-semibold tracking-[0.16em] text-muted uppercase">
          Saved layouts
        </h2>
        {layouts.length === 0 ? (
          <Panel>
            <EmptyState
              detail="Create a reusable layout before adding a van."
              title="No seat layouts yet"
            />
          </Panel>
        ) : (
          <ul aria-label="Saved layouts" className="grid gap-3">
            {layouts.map((item) => (
              <SavedLayoutRow
                key={item.id}
                layout={item}
                onEdit={() => setEditing(item)}
                session={session}
              />
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}
