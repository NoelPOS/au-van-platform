import { apiBaseUrl } from "./apiBaseUrl";
import { authenticatedFetch, type AuthSession } from "./authService";
import type { Booking } from "../types/booking";
import type { PaymentProof } from "../types/payments";

async function failure(response: Response): Promise<Error> {
  const body = (await response.json().catch(() => null)) as {
    detail?: string;
    code?: string;
  } | null;
  return new Error(
    body?.detail ?? body?.code ?? `Request failed (${response.status}).`,
  );
}

async function request<T>(
  session: AuthSession,
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const response = await authenticatedFetch(
    session,
    `${apiBaseUrl}/api/v1/admin${path}`,
    { ...init, headers: { "Content-Type": "application/json", ...init.headers } },
  );

  if (!response.ok) throw await failure(response);

  return (await response.json()) as T;
}

export const paymentsApi = {
  listProofs: (session: AuthSession) =>
    request<PaymentProof[]>(session, "/payment-proofs"),
  approveProof: (session: AuthSession, proofId: string, note: string) =>
    request<Booking>(session, `/payment-proofs/${proofId}/approve`, {
      method: "POST",
      body: JSON.stringify({ note }),
    }),
  rejectProof: (session: AuthSession, proofId: string, note: string) =>
    request<Booking>(session, `/payment-proofs/${proofId}/reject`, {
      method: "POST",
      body: JSON.stringify({ note }),
    }),
  /**
   * The one call that cannot go through `request()`: it sets
   * `Content-Type: application/json` and parses the body as JSON, and this
   * body is an image. An `<img src>` is not an option either — the browser
   * sends no `Authorization` header for one, so the endpoint would answer 401
   * and the administrator would see a broken image.
   */
  loadProofImage: async (
    session: AuthSession,
    proofId: string,
  ): Promise<Blob> => {
    const response = await authenticatedFetch(
      session,
      `${apiBaseUrl}/api/v1/admin/payment-proofs/${proofId}/image`,
    );

    if (!response.ok) throw await failure(response);

    return await response.blob();
  },
};
