import { apiBaseUrl } from "../api-base-url";
import { authenticatedFetch, type AuthSession } from "../auth/session";
import type { DeadLetter, TripOperations } from "./types";

async function failure(response: Response): Promise<Error> {
  const body = (await response.json().catch(() => null)) as {
    detail?: string;
    code?: string;
  } | null;
  return new Error(
    body?.detail ?? body?.code ?? `Request failed (${response.status}).`,
  );
}

async function request<T>(session: AuthSession, path: string): Promise<T> {
  const response = await authenticatedFetch(
    session,
    `${apiBaseUrl}/api/v1/admin/operations${path}`,
    { headers: { "Content-Type": "application/json" } },
  );

  if (!response.ok) throw await failure(response);

  return (await response.json()) as T;
}

/**
 * Reads only. There is no mutation here and there is nothing to add one to:
 * every endpoint under `/api/v1/admin/operations` is a `GET`.
 */
export const operationsApi = {
  loadTrip: (session: AuthSession, tripId: string) =>
    request<TripOperations>(session, `/trips/${tripId}`),
  listDeadLetters: (session: AuthSession) =>
    request<DeadLetter[]>(session, "/dead-letters"),
};
