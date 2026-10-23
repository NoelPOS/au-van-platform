import { ArrowLeftRight } from "lucide-react";
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
import { DeleteTemplate } from "./DeleteTemplate";
import { DepartureLinesEditor } from "./DepartureLinesEditor";
import { ErrorMessage } from "./ErrorMessage";
import { FormActions } from "./FormActions";
import { ShuttleForm } from "./ShuttleForm";
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
  const [shuttling, setShuttling] = useState(false);
  const clash = doubleBooked(lines);

  function addShuttle(added: DepartureLine[]) {
    setLines([
      ...lines.filter((line) => line.time.trim()),
      ...added.map(draftLine),
    ]);
    setShuttling(false);
  }

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
          actions={
            !shuttling && (
              <Button
                onClick={() => setShuttling(true)}
                type="button"
                variant="text"
              >
                <ArrowLeftRight aria-hidden className="size-4" />
                Add shuttle
              </Button>
            )
          }
          lines={lines}
          onChange={setLines}
          routes={routes}
          showErrors={submitted}
          vans={vans}
        />
        {shuttling && (
          <ShuttleForm
            existing={lines}
            onAdd={addShuttle}
            onCancel={() => setShuttling(false)}
            routes={routes}
            vans={vans}
          />
        )}
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
