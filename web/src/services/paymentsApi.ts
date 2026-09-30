import { apiBaseUrl } from "./apiBaseUrl";
import type { AuthSession } from "../types/auth";
import { authenticatedFetch } from "./authService";
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
  // Fetched with the token because an <img src> sends no Authorization header.
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
