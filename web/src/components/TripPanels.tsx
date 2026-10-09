import type { AuthSession } from "../types/auth";
import type { SeatLayout, Trip, VanRoute, Vehicle } from "../types/inventory";
import type {
  DayTemplate,
  DepartureLine,
  SchedulePlan,
} from "../types/schedule";
import { longDay } from "../utils/calendar";
import { linesOf, plural } from "../utils/schedule";
import { CancelledTrip } from "./CancelledTrip";
import { CancelTrip } from "./CancelTrip";
import { DayDepartures } from "./DayDepartures";
import { DayTemplateForm } from "./DayTemplateForm";
import { DayTemplateList } from "./DayTemplateList";
import { SchedulePreview } from "./SchedulePreview";
import { TripForm } from "./TripForm";
import { Drawer } from "./ui/Drawer";

export type Panel =
  | { kind: "day"; day: string }
  | { kind: "trip"; trip: Trip | null; day?: string }
  | { kind: "cancel"; trip: Trip }
  | { kind: "templates" }
  | { kind: "template"; template: DayTemplate | null; lines: DepartureLine[] }
  | { kind: "preview"; title: string; plan: SchedulePlan };

export function TripPanels({
  session,
  panel,
  today,
  days,
  trips,
  routes,
  vans,
  layouts,
  templates,
  applyTo,
  onPanel,
  onCopyDay,
  onApplied,
}: {
  session: AuthSession;
  panel: Panel | null;
  today: string;
  days: Map<string, Trip[]>;
  trips: Trip[];
  routes: VanRoute[];
  vans: Vehicle[];
  layouts: SeatLayout[];
  templates: DayTemplate[];
  applyTo: string[];
  onPanel: (panel: Panel | null) => void;
  onCopyDay: (day: string) => void;
  onApplied: () => void;
}) {
  const close = () => onPanel(null);
  const toTemplates = () => onPanel({ kind: "templates" });
  const heading = headingOf(panel, applyTo.length);

  return (
    <Drawer
      description={heading.description}
      onClose={close}
      open={panel !== null}
      title={heading.title}
    >
      {panel?.kind === "day" && (
        <DayDepartures
          day={panel.day}
          onAdd={() => onPanel({ kind: "trip", trip: null, day: panel.day })}
          onCopy={() => onCopyDay(panel.day)}
          onEdit={(trip) => onPanel({ kind: "trip", trip })}
          onSaveAsTemplate={() =>
            onPanel({
              kind: "template",
              template: null,
              lines: linesOf(days.get(panel.day) ?? []),
            })
          }
          routes={routes}
          session={session}
          today={today}
          trips={days.get(panel.day) ?? []}
          vans={vans}
        />
      )}
      {panel?.kind === "trip" && panel.trip?.status === "CANCELLED" && (
        <CancelledTrip
          route={routes.find((route) => route.id === panel.trip?.routeId)}
          trip={panel.trip}
        />
      )}
      {panel?.kind === "trip" && panel.trip?.status !== "CANCELLED" && (
        <TripForm
          layouts={layouts}
          onCancelTrip={(trip) => onPanel({ kind: "cancel", trip })}
          onDone={close}
          routes={routes}
          session={session}
          startDay={panel.day}
          trip={panel.trip}
          trips={trips}
          vans={vans}
        />
      )}
      {panel?.kind === "cancel" && (
        <CancelTrip
          onBack={() => onPanel({ kind: "trip", trip: panel.trip })}
          onDone={close}
          route={routes.find((route) => route.id === panel.trip.routeId)}
          session={session}
          trip={panel.trip}
          van={vans.find((van) => van.id === panel.trip.vehicleId)}
        />
      )}
      {panel?.kind === "templates" && (
        <DayTemplateList
          applyTo={applyTo.length}
          onApply={(template) =>
            onPanel({
              kind: "preview",
              title: `Apply “${template.name}”`,
              plan: { dates: applyTo, departures: template.departures },
            })
          }
          onCreate={() =>
            onPanel({ kind: "template", template: null, lines: [] })
          }
          onEdit={(template) =>
            onPanel({ kind: "template", template, lines: [] })
          }
          templates={templates}
        />
      )}
      {panel?.kind === "template" && (
        <DayTemplateForm
          onDone={toTemplates}
          routes={routes}
          session={session}
          startingLines={panel.lines}
          template={panel.template}
          vans={vans}
        />
      )}
      {panel?.kind === "preview" && (
        <SchedulePreview
          onApplied={onApplied}
          onBack={close}
          plan={panel.plan}
          routes={routes}
          session={session}
          vans={vans}
        />
      )}
    </Drawer>
  );
}

function headingOf(panel: Panel | null, applyTo: number) {
  switch (panel?.kind) {
    case "day":
      return {
        title: longDay(panel.day),
        description: "Departures in Bangkok time",
      };
    case "trip":
      if (panel.trip?.status === "CANCELLED") return { title: "Cancelled trip" };
      return { title: panel.trip ? "Edit trip" : "Schedule a trip" };
    case "cancel":
      return { title: "Cancel this trip?" };
    case "templates":
      return {
        title: "Day templates",
        description: applyTo
          ? `Choose the plan to lay onto ${plural(applyTo, "day")}.`
          : "Reusable plans for a service day.",
      };
    case "template":
      return { title: panel.template ? "Edit template" : "New template" };
    case "preview":
      return {
        title: panel.title,
        description: `${plural(panel.plan.dates.length, "day")} · Bangkok time`,
      };
    default:
      return { title: "" };
  }
}
