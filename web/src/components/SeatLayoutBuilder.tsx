import { useState, type FormEvent } from "react";
import { ErrorMessage, FormActions } from "./FormCard";
import { SeatPlanEditor } from "./SeatPlanEditor";
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

const fieldClass =
  "mt-1 w-full rounded-lg border border-line bg-card px-3 py-2 font-normal text-ink outline-none focus-visible:ring-2 focus-visible:ring-brand-500";

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
    <form
      className="grid gap-6 rounded-2xl border border-line bg-card p-5 sm:p-7"
      onSubmit={submit}
    >
      <div>
        <p className="text-[0.7rem] font-semibold uppercase tracking-[0.14em] text-brand-500">
          Seat layout builder
        </p>
        <h2 className="mt-1 text-2xl font-semibold text-brand-900">
          {layout ? `Edit ${layout.name}` : "New seat layout"}
        </h2>
      </div>
      <label className="text-sm font-semibold text-ink">
        Layout name
        <input
          className={fieldClass}
          name="name"
          onChange={(event) => setName(event.target.value)}
          placeholder="Toyota Commuter 13-seat"
          required
          value={name}
        />
      </label>
      <fieldset className="grid gap-2">
        <legend className="mb-2 text-sm font-semibold text-ink">Start from</legend>
        <div className="flex flex-wrap gap-2">
          {presets.map((preset) => (
            <button
              className="rounded-full border border-line bg-paper px-4 py-2 text-sm text-ink transition-colors duration-150 ease-out hover:border-brand-500 hover:text-brand-700 focus-visible:outline-2 focus-visible:outline-brand-500"
              key={preset.name}
              onClick={() => setDraft(fromPreset(preset))}
              type="button"
            >
              {preset.name}
            </button>
          ))}
        </div>
      </fieldset>
      <div className="grid max-w-xs grid-cols-2 gap-3">
        <label className="text-sm font-semibold text-ink">
          Rows
          <input
            className={fieldClass}
            max={maxRows}
            min={1}
            onChange={(event) =>
              setDraft(resize(draft, toSize(event.target.value, maxRows), draft.columns))
            }
            type="number"
            value={draft.rows}
          />
        </label>
        <label className="text-sm font-semibold text-ink">
          Columns
          <input
            className={fieldClass}
            max={maxColumns}
            min={1}
            onChange={(event) =>
              setDraft(resize(draft, draft.rows, toSize(event.target.value, maxColumns)))
            }
            type="number"
            value={draft.columns}
          />
        </label>
      </div>
      <SeatPlanEditor draft={draft} onChange={setDraft} />
      <ErrorMessage error={inputError ?? error} />
      <FormActions
        busy={busy}
        onCancel={onCancel}
        submitLabel={layout ? "Save layout" : "Create layout"}
      />
    </form>
  );
}
