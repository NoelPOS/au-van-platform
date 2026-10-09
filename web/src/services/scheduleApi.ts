import type { AuthSession } from "../types/auth";
import type {
  DayCleared,
  DayTemplate,
  DayTemplateInput,
  ScheduleApplied,
  SchedulePlan,
  SchedulePreview,
} from "../types/schedule";
import { adminRequest } from "./inventoryApi";

function post<T>(session: AuthSession, path: string, body?: unknown) {
  return adminRequest<T>(session, path, {
    method: "POST",
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

export const scheduleApi = {
  listTemplates: (session: AuthSession) =>
    adminRequest<DayTemplate[]>(session, "/day-templates"),
  createTemplate: (session: AuthSession, input: DayTemplateInput) =>
    post<DayTemplate>(session, "/day-templates", input),
  updateTemplate: (session: AuthSession, id: string, input: DayTemplateInput) =>
    adminRequest<DayTemplate>(session, `/day-templates/${id}`, {
      method: "PUT",
      body: JSON.stringify(input),
    }),
  deleteTemplate: (session: AuthSession, id: string) =>
    post<void>(session, `/day-templates/${id}/delete`),
  preview: (session: AuthSession, plan: SchedulePlan) =>
    post<SchedulePreview>(session, "/schedule/preview", plan),
  apply: (
    session: AuthSession,
    plan: SchedulePlan & { planHash: string },
    key: string,
  ) =>
    adminRequest<ScheduleApplied>(session, "/schedule/apply", {
      method: "POST",
      headers: { "Idempotency-Key": key },
      body: JSON.stringify(plan),
    }),
  clearDay: (session: AuthSession, date: string) =>
    post<DayCleared>(session, "/schedule/clear-day", { date }),
};
