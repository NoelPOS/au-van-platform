import type { AuthSession } from "../types/auth";
import { apiBaseUrl } from "./apiBaseUrl";

type ExchangeResponse = AuthSession;

const exchangeFailed = "LINE authentication could not be completed.";

export class LineTokenRejected extends Error {}
export class DemoSignInUnavailable extends Error {}
export class WrongPasscode extends Error {}

export async function exchangeLineIdToken(
  idToken: string,
  fetcher: typeof fetch = fetch,
): Promise<AuthSession> {
  const response = await fetcher(`${apiBaseUrl}/api/v1/auth/line/exchange`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ idToken }),
  });

  if (response.status === 401) throw new LineTokenRejected(exchangeFailed);
  if (!response.ok) throw new Error(exchangeFailed);

  return (await response.json()) as ExchangeResponse;
}

async function demoSignIn(
  path: string,
  body: object,
  fetcher: typeof fetch,
): Promise<AuthSession> {
  const response = await fetcher(`${apiBaseUrl}/api/v1/auth/demo/${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

  if (response.status === 404)
    throw new DemoSignInUnavailable("The demo is not switched on.");
  if (response.status === 401)
    throw new WrongPasscode("That passcode is not right.");
  if (!response.ok) throw new Error("The demo sign-in did not finish. Try again.");

  return (await response.json()) as AuthSession;
}

export function signInAsDemoStudent(
  studentId?: string,
  fetcher: typeof fetch = fetch,
): Promise<AuthSession> {
  return demoSignIn("student", studentId ? { studentId } : {}, fetcher);
}

export function signInAsDemoStaff(
  passcode: string,
  fetcher: typeof fetch = fetch,
): Promise<AuthSession> {
  return demoSignIn("staff", { passcode }, fetcher);
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
