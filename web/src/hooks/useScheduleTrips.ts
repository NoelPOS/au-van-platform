import { useState } from "react";
import type { AuthSession } from "../types/auth";
import { fromBangkokInputs } from "../utils/dates";
import { messageOf } from "../utils/errors";
import { useCreateTrip } from "./useInventoryQueries";

export type ScheduleReport = {
  scheduled: string[];
  failed: { day: string; message: string }[];
};

export function useScheduleTrips(session: AuthSession) {
  const createTrip = useCreateTrip(session);
  const [saving, setSaving] = useState(false);
  const [report, setReport] = useState<ScheduleReport | null>(null);

  async function schedule(
    days: string[],
    clock: string,
    trip: { routeId: string; vehicleId: string },
  ): Promise<ScheduleReport> {
    setSaving(true);
    const result: ScheduleReport = { scheduled: [], failed: [] };
    for (const day of days) {
      try {
        await createTrip.mutateAsync({
          ...trip,
          departureAt: fromBangkokInputs(day, clock),
        });
        result.scheduled.push(day);
      } catch (error) {
        result.failed.push({ day, message: messageOf(error) });
      }
    }
    setSaving(false);
    setReport(result);
    return result;
  }

  return { schedule, saving, report };
}
