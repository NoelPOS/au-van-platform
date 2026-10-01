import { useState } from "react";
import type { AuthSession } from "../types/auth";
import { SeatLayoutBuilder } from "./SeatLayoutBuilder";
import { VanThumbnail } from "./VanThumbnail";
import { Button } from "./ui/Button";
import { EmptyState } from "./ui/EmptyState";
import { Panel } from "./ui/Panel";
import { useToast } from "../hooks/useToast";
import { useCreateSeatLayout, useUpdateSeatLayout } from "../hooks/useInventoryQueries";
import type { Seat, SeatLayout } from "../types/inventory";
import { seatCount } from "../utils/format";

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
              <li
                className="flex items-center gap-4 rounded-2xl border border-line bg-card p-4"
                key={item.id}
              >
                <div className="w-14 shrink-0">
                  <VanThumbnail seats={item.seats} />
                </div>
                <div className="min-w-0 flex-1">
                  <h3 className="truncate font-display text-xl text-brand-900">{item.name}</h3>
                  <p className="text-sm tabular-nums text-muted">
                    {seatCount(item.seats.length)}
                  </p>
                </div>
                <Button
                  aria-label={`Edit ${item.name}`}
                  onClick={() => setEditing(item)}
                  type="button"
                  variant="text"
                >
                  Edit →
                </Button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}
