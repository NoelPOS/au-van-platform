import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { scheduleApi } from "../services/scheduleApi";
import type { AuthSession } from "../types/auth";
import type { SchedulePlan } from "../types/schedule";

const tripsKey = ["inventory", "trips"] as const;

export function useSchedulePreview(session: AuthSession, plan: SchedulePlan) {
  return useQuery({
    queryKey: ["schedule-preview", plan],
    queryFn: () => scheduleApi.preview(session, plan),
    gcTime: 0,
  });
}

export function useApplySchedule(session: AuthSession) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      plan,
      planHash,
      key,
    }: {
      plan: SchedulePlan;
      planHash: string;
      key: string;
    }) => scheduleApi.apply(session, { ...plan, planHash }, key),
    onSettled: () => queryClient.invalidateQueries({ queryKey: tripsKey }),
  });
}

export function useClearDay(session: AuthSession) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (date: string) => scheduleApi.clearDay(session, date),
    onSettled: () => queryClient.invalidateQueries({ queryKey: tripsKey }),
  });
}
