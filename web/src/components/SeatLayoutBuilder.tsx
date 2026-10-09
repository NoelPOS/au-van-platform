import { useState, type FormEvent } from "react";
import { ErrorMessage } from "./ErrorMessage";
import { FormActions } from "./FormActions";
import { SeatPlanEditor } from "./SeatPlanEditor";
import { Button } from "./ui/Button";
import { Input } from "./ui/Input";
import { Panel } from "./ui/Panel";
import type { Seat, SeatLayout } from "../types/inventory";
import {
  fromPreset,
  fromSeats,
  labelSeats,
  maxColumns,
  maxRows,
  presets,
  resize,
} from "../utils/seatLayout";

const toSize = (value: string, max: number) =>
  Math.min(max, Math.max(1, Math.trunc(Number(value)) || 1));

export function SeatLayoutBuilder({
  layout,
  busy,
  error,
  onSave,
  onCancel,
}: {
  layout: SeatLayout | null;
  busy: boolean;
  error: unknown;
  onSave: (input: { name: string; seats: Seat[] }) => void;
  onCancel?: () => void;
}) {
  const [name, setName] = useState(layout?.name ?? "");
  const [draft, setDraft] = useState(() =>
    layout ? fromSeats(layout.seats) : fromPreset(presets[0]),
  );
  const [inputError, setInputError] = useState<Error | null>(null);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const seats = labelSeats(draft);
    if (!seats.length) {
      setInputError(new Error("Place at least one seat in the van."));
      return;
    }
    setInputError(null);
    onSave({ name: name.trim(), seats });
  }

  return (
    <Panel className="p-5 sm:p-7">
      <form className="grid gap-6" onSubmit={submit}>
        <div>
          <p className="text-[11px] font-semibold tracking-[0.16em] text-brand-500 uppercase">
            Seat layout builder
          </p>
          <h2 className="mt-2 font-display text-3xl font-light text-brand-900">
            {layout ? `Edit ${layout.name}` : "New seat layout"}
          </h2>
        </div>
        <Input
          label="Layout name"
          name="name"
          onChange={(event) => setName(event.target.value)}
          placeholder="Toyota Commuter 13-seat"
          required
          value={name}
        />
        <fieldset>
          <legend className="mb-2 text-[13px] font-medium text-ink">Start from</legend>
          <div className="flex flex-wrap gap-2">
            {presets.map((preset) => (
              <Button
                key={preset.name}
                onClick={() => setDraft(fromPreset(preset))}
                type="button"
                variant="secondary"
              >
                {preset.name}
              </Button>
            ))}
          </div>
        </fieldset>
        <div className="grid max-w-xs grid-cols-2 gap-3">
          <Input
            label="Rows"
            max={maxRows}
            min={1}
            onChange={(event) =>
              setDraft(resize(draft, toSize(event.target.value, maxRows), draft.columns))
            }
            type="number"
            value={draft.rows}
          />
          <Input
            label="Columns"
            max={maxColumns}
            min={1}
            onChange={(event) =>
              setDraft(resize(draft, draft.rows, toSize(event.target.value, maxColumns)))
            }
            type="number"
            value={draft.columns}
          />
        </div>
        <SeatPlanEditor draft={draft} onChange={setDraft} />
        <ErrorMessage error={inputError ?? error} />
        <FormActions
          busy={busy}
          onCancel={onCancel}
          submitLabel={layout ? "Save layout" : "Create layout"}
        />
      </form>
    </Panel>
  );
}
