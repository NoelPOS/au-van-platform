import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { insightsApi } from "../services/insightsApi";
import type { AuthSession } from "../types/auth";
import type { InsightPeriod } from "../types/insights";

export const insightsKeys = {
  period: ({ from, to }: InsightPeriod) => ["insights", from, to] as const,
};

export function useInsights(session: AuthSession, period: InsightPeriod) {
  return useQuery({
    queryKey: insightsKeys.period(period),
    queryFn: () => insightsApi.load(session, period),
    placeholderData: keepPreviousData,
  });
}
