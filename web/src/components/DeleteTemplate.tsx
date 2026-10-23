import { useState } from "react";
import { Button } from "./ui/Button";

export function DeleteTemplate({
  name,
  busy,
  onDelete,
}: {
  name: string;
  busy: boolean;
  onDelete: () => void;
}) {
  const [asking, setAsking] = useState(false);
  if (!asking)
    return (
      <button
        className="min-h-11 justify-self-start text-sm font-semibold text-danger underline decoration-1 underline-offset-4 hover:decoration-2"
        onClick={() => setAsking(true)}
        type="button"
      >
        Delete template
      </button>
    );
  return (
    <div
      aria-label="Delete template"
      className="rounded-xl border border-danger/25 bg-danger-soft p-4"
      role="group"
    >
      <p className="text-sm text-danger">
        Delete “{name}”? Trips already made from it stay as they are.
      </p>
      <div className="mt-4 flex justify-end gap-2">
        <Button
          autoFocus
          onClick={() => setAsking(false)}
          type="button"
          variant="secondary"
        >
          Keep it
        </Button>
        <Button
          disabled={busy}
          onClick={onDelete}
          type="button"
          variant="danger"
        >
          Delete
        </Button>
      </div>
    </div>
  );
}
