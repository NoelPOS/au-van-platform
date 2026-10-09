import { CalendarCheck, LayoutTemplate, Plus } from "lucide-react";
import { useState } from "react";
import { InventoryPage } from "../components/InventoryPage";
import { PlanBar } from "../components/PlanBar";
import { TripCalendar } from "../components/TripCalendar";
import { TripList } from "../components/TripList";
import { TripPanels, type Panel } from "../components/TripPanels";
import { Button } from "../components/ui/Button";
import { SegmentedControl } from "../components/ui/SegmentedControl";
import { useDayTemplates } from "../hooks/useDayTemplates";
import {
  useRoutes,
  useSeatLayouts,
  useTrips,
  useVehicles,
} from "../hooks/useInventoryQueries";
import type { AuthSession } from "../types/auth";
import type { DepartureLine } from "../types/schedule";
import { bangkokToday, shortDay } from "../utils/calendar";
import { linesOf, tripsByDay } from "../utils/schedule";

type View = "month" | "week" | "list";

type Planning = { copy: { day: string; lines: DepartureLine[] } | null };

const views: { value: View; label: string }[] = [
  { value: "month", label: "Month" },
  { value: "week", label: "Week" },
  { value: "list", label: "List" },
];

export function AdminTripsPage({ session }: { session: AuthSession }) {
  const trips = useTrips(session);
  const routes = useRoutes(session);
  const vans = useVehicles(session);
  const layouts = useSeatLayouts(session);
  const templates = useDayTemplates(session);
  const [view, setView] = useState<View>("month");
  const [panel, setPanel] = useState<Panel | null>(null);
  const [planning, setPlanning] = useState<Planning | null>(null);
  const [selected, setSelected] = useState<string[]>([]);
  const allTrips = trips.data ?? [];
  const routeList = routes.data ?? [];
  const vanList = vans.data ?? [];
  const days = tripsByDay(allTrips);
  const today = bangkokToday();

  function plan(copy: Planning["copy"]) {
    setPlanning({ copy });
    setSelected([]);
    setPanel(null);
    if (view === "list") setView("month");
  }

  function stopPlanning() {
    setPlanning(null);
    setSelected([]);
  }

  function toggle(toggled: string[]) {
    const allIn = toggled.every((day) => selected.includes(day));
    setSelected(
      allIn
        ? selected.filter((day) => !toggled.includes(day))
        : [...new Set([...selected, ...toggled])].sort(),
    );
  }

  function preview() {
    const copy = planning?.copy;
    if (!copy) return setPanel({ kind: "templates" });
    setPanel({
      kind: "preview",
      title: `Copy ${shortDay(copy.day)}`,
      plan: { dates: selected, departures: copy.lines },
    });
  }

  return (
    <InventoryPage
      action={
        <div className="flex flex-wrap gap-2">
          <Button onClick={() => setPanel({ kind: "trip", trip: null })}>
            <Plus aria-hidden className="size-4" />
            New trip
          </Button>
          <Button
            onClick={() => setPanel({ kind: "templates" })}
            variant="secondary"
          >
            <LayoutTemplate aria-hidden className="size-4" />
            Templates
          </Button>
        </div>
      }
      description="Every scheduled departure, in Bangkok time."
      queries={[trips, routes, vans, layouts]}
      title="Trips"
    >
      <div className="flex flex-wrap items-center justify-between gap-3">
        <SegmentedControl
          hideLabel
          label="View"
          onChange={setView}
          options={views}
          value={view}
        />
        {view !== "list" && !planning && (
          <Button onClick={() => plan(null)} variant="secondary">
            <CalendarCheck aria-hidden className="size-4" />
            Select days
          </Button>
        )}
      </div>
      {view === "list" ? (
        <TripList
          onEdit={(trip) => setPanel({ kind: "trip", trip })}
          routes={routeList}
          today={today}
          trips={allTrips}
          vans={vanList}
        />
      ) : (
        <div className={planning ? "pb-32 lg:pb-24" : undefined}>
          <TripCalendar
            days={days}
            onOpen={(day) => setPanel({ kind: "day", day })}
            onToggle={toggle}
            routes={routeList}
            selected={selected}
            selecting={planning !== null}
            today={today}
            vans={vanList}
            view={view}
          />
        </div>
      )}
      {planning && (
        <PlanBar
          actionLabel={planning.copy ? "Preview copy" : "Apply template"}
          count={selected.length}
          onAction={preview}
          onCancel={stopPlanning}
          title={
            planning.copy
              ? `Copy ${shortDay(planning.copy.day)} to`
              : "Plan days"
          }
        />
      )}
      <TripPanels
        applyTo={planning && !planning.copy ? selected : []}
        days={days}
        layouts={layouts.data ?? []}
        onApplied={() => {
          setPanel(null);
          stopPlanning();
        }}
        onCopyDay={(day) => plan({ day, lines: linesOf(days.get(day) ?? []) })}
        onPanel={setPanel}
        panel={panel}
        routes={routeList}
        session={session}
        templates={templates.data ?? []}
        today={today}
        trips={allTrips}
        vans={vanList}
      />
    </InventoryPage>
  );
}
