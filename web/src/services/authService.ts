import type { AuthSession } from "../types/auth";
import { apiBaseUrl } from "./apiBaseUrl";

type ExchangeResponse = AuthSession;

export async function exchangeLineIdToken(
  idToken: string,
  fetcher: typeof fetch = fetch,
): Promise<AuthSession> {
  const response = await fetcher(`${apiBaseUrl}/api/v1/auth/line/exchange`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ idToken }),
  });

  if (!response.ok) {
    throw new Error("LINE authentication could not be completed.");
  }

  return (await response.json()) as ExchangeResponse;
}

export async function authenticatedFetch(
  session: AuthSession,
  input: RequestInfo | URL,
  init: RequestInit = {},
  fetcher: typeof fetch = fetch,
): Promise<Response> {
  const headers = new Headers(init.headers);
  headers.set("Authorization", `Bearer ${session.accessToken}`);
  return fetcher(input, { ...init, headers });
}
