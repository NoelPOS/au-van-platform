import { useState, type FormEvent } from "react";
import {
  useDeleteDayTemplate,
  useSaveDayTemplate,
} from "../hooks/useDayTemplates";
import { useToast } from "../hooks/useToast";
import type { AuthSession } from "../types/auth";
import type { VanRoute, Vehicle } from "../types/inventory";
import type { DayTemplate, DepartureLine } from "../types/schedule";
import { parseClock } from "../utils/dates";
import { draftLine, type DraftLine } from "../utils/schedule";
import { DepartureLinesEditor } from "./DepartureLinesEditor";
import { ErrorMessage } from "./ErrorMessage";
import { FormActions } from "./FormActions";
import { Button } from "./ui/Button";
import { Input } from "./ui/Input";

function doubleBooked(lines: DraftLine[]): string | null {
  const seen = new Set<string>();
  for (const line of lines) {
    const slot = `${line.vehicleId}@${parseClock(line.time)}`;
    if (seen.has(slot))
      return `One van is down twice at ${parseClock(line.time)}.`;
    seen.add(slot);
  }
  return null;
}

function DeleteTemplate({
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
        <Button disabled={busy} onClick={onDelete} type="button" variant="danger">
          Delete
        </Button>
      </div>
    </div>
  );
}

export function DayTemplateForm({
  session,
  template,
  startingLines,
  routes,
  vans,
  onDone,
}: {
  session: AuthSession;
  template: DayTemplate | null;
  startingLines: DepartureLine[];
  routes: VanRoute[];
  vans: Vehicle[];
  onDone: () => void;
}) {
  const save = useSaveDayTemplate(session);
  const remove = useDeleteDayTemplate(session);
  const notify = useToast();
  const [name, setName] = useState(template?.name ?? "");
  const [lines, setLines] = useState(() => {
    const start = template?.departures ?? startingLines;
    const blank = {
      time: "",
      routeId: routes[0]?.id ?? "",
      vehicleId: vans[0]?.id ?? "",
    };
    return (start.length ? start : [blank]).map(draftLine);
  });
  const [submitted, setSubmitted] = useState(false);
  const clash = doubleBooked(lines);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitted(true);
    if (!name.trim() || clash) return;
    if (lines.some((line) => !parseClock(line.time))) return;
    const departures = lines
      .map(({ time, routeId, vehicleId }) => ({
        time: parseClock(time) ?? time,
        routeId,
        vehicleId,
      }))
      .sort((left, right) => left.time.localeCompare(right.time));
    save.mutate(
      { id: template?.id, input: { name: name.trim(), departures } },
      {
        onSuccess: () => {
          notify(template ? "Template saved" : "Template created");
          onDone();
        },
      },
    );
  }

  return (
    <form className="grid gap-6" noValidate onSubmit={submit}>
      <Input
        error={
          submitted && !name.trim() ? "Give the template a name." : undefined
        }
        label="Name"
        maxLength={60}
        onChange={(event) => setName(event.target.value)}
        placeholder="Weekday, Saturday, Exam week…"
        value={name}
      />
      <fieldset className="grid gap-3">
        <legend className="mb-3 text-[11px] font-semibold tracking-[0.16em] text-muted uppercase">
          Departures · Bangkok time
        </legend>
        <DepartureLinesEditor
          lines={lines}
          onChange={setLines}
          routes={routes}
          showErrors={submitted}
          vans={vans}
        />
        {clash && <p className="text-sm text-danger">{clash}</p>}
      </fieldset>
      <ErrorMessage error={save.error ?? remove.error} />
      <FormActions
        busy={save.isPending}
        onCancel={onDone}
        submitLabel={template ? "Save template" : "Create template"}
      />
      {template && (
        <DeleteTemplate
          busy={remove.isPending}
          name={template.name}
          onDelete={() =>
            remove.mutate(template.id, {
              onSuccess: () => {
                notify("Template deleted");
                onDone();
              },
            })
          }
        />
      )}
    </form>
  );
}
