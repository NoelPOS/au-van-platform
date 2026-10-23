import { useId, useState } from "react";
import type { VanRoute, Vehicle } from "../types/inventory";
import type { DepartureLine } from "../types/schedule";
import { parseClock } from "../utils/dates";
import { firstClash, reverseOf, shuttleLines } from "../utils/shuttle";
import { ShuttlePreview } from "./ShuttlePreview";
import { Button } from "./ui/Button";
import { Input } from "./ui/Input";
import { Select } from "./ui/Select";

function routeName(route: VanRoute | undefined) {
  return route ? `${route.origin} → ${route.destination}` : "";
}

export function ShuttleForm({
  routes,
  vans,
  existing,
  onAdd,
  onCancel,
}: {
  routes: VanRoute[];
  vans: Vehicle[];
  existing: DepartureLine[];
  onAdd: (lines: DepartureLine[]) => void;
  onCancel: () => void;
}) {
  const headingId = useId();
  const active = routes.filter((route) => route.status === "ACTIVE");
  const activeVans = vans.filter((van) => van.status === "ACTIVE");
  const [outId, setOutId] = useState(active[0]?.id ?? "");
  const [backId, setBackId] = useState(
    () => reverseOf(active[0], active)?.id ?? "",
  );
  const [vehicleId, setVehicleId] = useState(activeVans[0]?.id ?? "");
  const [first, setFirst] = useState("");
  const [last, setLast] = useState("");
  const [wait, setWait] = useState("15");
  const [tried, setTried] = useState(false);

  const routeOf = (id: string) => routes.find((route) => route.id === id);
  const out = routeOf(outId);
  const back = routeOf(backId);
  const firstClock = parseClock(first);
  const lastClock = parseClock(last);
  const waitMinutes = /^\d+$/.test(wait.trim()) ? Number(wait) : null;
  const lines =
    firstClock && lastClock && waitMinutes !== null
      ? shuttleLines(
          {
            outId,
            backId,
            vehicleId,
            first: firstClock,
            last: lastClock,
            wait: waitMinutes,
          },
          active,
        )
      : [];
  const timed = existing.flatMap((line) => {
    const time = parseClock(line.time);
    return time ? [{ ...line, time }] : [];
  });
  const clash = firstClash(lines, timed, routes);

  function problem() {
    if (out && back && back.origin !== out.destination)
      return `Route back has to start at ${out.destination}, where route out ends.`;
    if (firstClock && lastClock && lastClock < firstClock)
      return "The last departure is before the first.";
    if (clash) {
      const code = vans.find((van) => van.id === clash.line.vehicleId)?.code;
      const trip = `${clash.existing.time} ${routeName(routeOf(clash.existing.routeId))}`;
      return `${code} is already on the ${trip}, so the ${clash.line.time} would clash.`;
    }
    return null;
  }
  const message = problem();

  function chooseOut(id: string) {
    setOutId(id);
    setBackId(reverseOf(routeOf(id), active)?.id ?? "");
  }

  function add() {
    setTried(true);
    if (message || lines.length === 0) return;
    onAdd(lines);
  }

  const routeOptions = active.map((route) => ({
    value: route.id,
    label: `${routeName(route)} · ${route.durationMinutes} min`,
  }));

  return (
    <div
      aria-labelledby={headingId}
      className="grid gap-4 rounded-xl border border-line bg-paper/60 p-4"
      onKeyDown={(event) => {
        if (
          event.key !== "Enter" ||
          !(event.target instanceof HTMLInputElement)
        )
          return;
        // Enter would otherwise submit the template form around this one.
        event.preventDefault();
        add();
      }}
      role="group"
    >
      <div>
        <h3 className="font-display text-lg text-brand-900" id={headingId}>
          Add a shuttle
        </h3>
        <p className="mt-0.5 text-sm text-muted">
          One van running out and back between two times.
        </p>
      </div>
      <Select
        label="Route out"
        onChange={chooseOut}
        options={routeOptions}
        value={outId}
      />
      <Select
        label="Route back"
        onChange={setBackId}
        options={[{ value: "", label: "None, one-way" }, ...routeOptions]}
        value={backId}
      />
      <Select
        label="Van"
        onChange={setVehicleId}
        options={activeVans.map((van) => ({
          value: van.id,
          label: `${van.code} — ${van.name}`,
        }))}
        value={vehicleId}
      />
      <div className="grid grid-cols-2 gap-4">
        <Input
          autoComplete="off"
          error={tried && !firstClock ? "HH:MM" : undefined}
          inputMode="numeric"
          label="First departure"
          maxLength={5}
          onChange={(event) => setFirst(event.target.value)}
          placeholder="07:00"
          value={first}
        />
        <Input
          autoComplete="off"
          error={tried && !lastClock ? "HH:MM" : undefined}
          inputMode="numeric"
          label="Last departure"
          maxLength={5}
          onChange={(event) => setLast(event.target.value)}
          placeholder="19:00"
          value={last}
        />
        <Input
          error={tried && waitMinutes === null ? "0 or more" : undefined}
          label="Wait at each end (min)"
          min="0"
          onChange={(event) => setWait(event.target.value)}
          type="number"
          value={wait}
        />
      </div>
      {lines.length > 0 && (
        <ShuttlePreview lines={lines} routes={routes} vans={vans} />
      )}
      {message && <p className="text-sm text-danger">{message}</p>}
      <div className="flex justify-end gap-2">
        <Button onClick={onCancel} type="button" variant="secondary">
          Cancel
        </Button>
        <Button onClick={add} type="button">
          Add to template
        </Button>
      </div>
    </div>
  );
}
