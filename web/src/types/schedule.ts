export type DepartureLine = {
  time: string;
  routeId: string;
  vehicleId: string;
};

export type DayTemplate = {
  id: string;
  name: string;
  departures: DepartureLine[];
};

export type DayTemplateInput = Omit<DayTemplate, "id">;

export type SchedulePlan = {
  dates: string[];
  departures: DepartureLine[];
};

export type PlanOutcome = "CREATE" | "CLASH" | "PAST" | "UNAVAILABLE";

export type PlannedDeparture = DepartureLine & {
  date: string;
  outcome: PlanOutcome;
  reason: string | null;
};

export type SchedulePreview = {
  planHash: string;
  departures: PlannedDeparture[];
};

export type ScheduleApplied = { created: number; skipped: number };

export type DayCleared = { removed: number; kept: number };
