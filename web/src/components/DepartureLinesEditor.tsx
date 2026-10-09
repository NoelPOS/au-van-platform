import { Plus, X } from "lucide-react";
import type { VanRoute, Vehicle } from "../types/inventory";
import { addMinutes, parseClock } from "../utils/dates";
import { draftLine, type DraftLine } from "../utils/schedule";
import { Button } from "./ui/Button";
import { Input } from "./ui/Input";
import { Select } from "./ui/Select";

export function DepartureLinesEditor({
  lines,
  routes,
  vans,
  showErrors,
  onChange,
}: {
  lines: DraftLine[];
  routes: VanRoute[];
  vans: Vehicle[];
  showErrors: boolean;
  onChange: (lines: DraftLine[]) => void;
}) {
  const routeOptions = routes.map((route) => ({
    value: route.id,
    label: `${route.destination} from ${route.origin}`,
  }));
  const vanOptions = vans.map((van) => ({
    value: van.id,
    label: `${van.code} — ${van.name}`,
  }));

  function update(key: number, change: Partial<DraftLine>) {
    onChange(
      lines.map((line) => (line.key === key ? { ...line, ...change } : line)),
    );
  }

  function add() {
    const last = lines.at(-1);
    const clock = last && parseClock(last.time);
    onChange([
      ...lines,
      draftLine({
        time: clock ? addMinutes(clock, 30) : "",
        routeId: last?.routeId ?? routes[0]?.id ?? "",
        vehicleId: last?.vehicleId ?? vans[0]?.id ?? "",
      }),
    ]);
  }

  return (
    <div className="flex flex-col gap-3">
      <p
        aria-hidden
        className="grid grid-cols-[5.5rem_minmax(0,1fr)_2.75rem] gap-x-2 text-[11px] font-semibold tracking-[0.14em] text-muted uppercase"
      >
        <span>Time</span>
        <span>Route and van</span>
      </p>
      <ol className="divide-y divide-dashed divide-line border-y border-dashed border-line">
        {lines.map((line, index) => {
          const number = index + 1;
          return (
            <li
              className="grid grid-cols-[5.5rem_minmax(0,1fr)_2.75rem] items-start gap-x-2 gap-y-2 py-3"
              key={line.key}
            >
              <Input
                autoComplete="off"
                error={
                  showErrors && !parseClock(line.time) ? "HH:MM" : undefined
                }
                hideLabel
                inputMode="numeric"
                label={`Departure ${number} time`}
                maxLength={5}
                onChange={(event) =>
                  update(line.key, { time: event.target.value })
                }
                placeholder="07:30"
                value={line.time}
              />
              <Select
                hideLabel
                label={`Departure ${number} route`}
                onChange={(routeId) => update(line.key, { routeId })}
                options={routeOptions}
                value={line.routeId}
              />
              <button
                aria-label={`Remove departure ${number}`}
                className="grid size-11 place-items-center rounded-full text-muted transition-colors hover:bg-paper hover:text-danger disabled:pointer-events-none disabled:opacity-30"
                disabled={lines.length === 1}
                onClick={() =>
                  onChange(lines.filter((entry) => entry.key !== line.key))
                }
                type="button"
              >
                <X aria-hidden className="size-4" />
              </button>
              <Select
                className="col-start-2"
                hideLabel
                label={`Departure ${number} van`}
                onChange={(vehicleId) => update(line.key, { vehicleId })}
                options={vanOptions}
                value={line.vehicleId}
              />
            </li>
          );
        })}
      </ol>
      <Button className="self-start" onClick={add} type="button" variant="text">
        <Plus aria-hidden className="size-4" />
        Add departure
      </Button>
    </div>
  );
}
