import type { AuthSession } from "../types/auth";
import type { InsightPeriod, Insights } from "../types/insights";
import { adminRequest } from "./inventoryApi";

export const insightsApi = {
  load: (session: AuthSession, { from, to }: InsightPeriod) =>
    adminRequest<Insights>(session, `/insights?from=${from}&to=${to}`),
};
