import { useState } from "react";
import { useDeleteSeatLayout } from "../hooks/useInventoryQueries";
import { useToast } from "../hooks/useToast";
import type { AuthSession } from "../types/auth";
import type { SeatLayout } from "../types/inventory";
import { seatCount } from "../utils/format";
import { VanThumbnail } from "./VanThumbnail";
import { Button } from "./ui/Button";

export function SavedLayoutRow({
  session,
  layout,
  onEdit,
}: {
  session: AuthSession;
  layout: SeatLayout;
  onEdit: () => void;
}) {
  const [asking, setAsking] = useState(false);
  const deleteLayout = useDeleteSeatLayout(session);
  const notify = useToast();

  function remove() {
    deleteLayout.mutate(layout.id, {
      onSuccess: () => notify("Seat layout deleted."),
      onError: (error) => {
        notify(error.message, "danger");
        setAsking(false);
      },
    });
  }

  return (
    <li className="flex flex-wrap items-center gap-4 rounded-2xl border border-line bg-card p-4">
      <div className="w-14 shrink-0">
        <VanThumbnail seats={layout.seats} />
      </div>
      <div className="min-w-0 flex-1">
        <h3 className="truncate font-display text-xl text-brand-900">{layout.name}</h3>
        <p className="text-sm tabular-nums text-muted">{seatCount(layout.seats.length)}</p>
      </div>
      {asking ? (
        <div className="flex items-center gap-2">
          <span className="text-sm text-muted">Delete this layout?</span>
          <Button onClick={() => setAsking(false)} type="button" variant="secondary">
            Go back
          </Button>
          <Button
            disabled={deleteLayout.isPending}
            onClick={remove}
            type="button"
            variant="danger"
          >
            {deleteLayout.isPending ? "Deleting…" : "Yes, delete"}
          </Button>
        </div>
      ) : (
        <div className="flex items-center gap-3">
          <Button aria-label={`Edit ${layout.name}`} onClick={onEdit} type="button" variant="text">
            Edit →
          </Button>
          <Button
            aria-label={`Delete ${layout.name}`}
            onClick={() => setAsking(true)}
            type="button"
            variant="text"
          >
            Delete
          </Button>
        </div>
      )}
    </li>
  );
}
