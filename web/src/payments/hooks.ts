import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { AuthSession } from "../auth/session";
import { paymentsApi } from "./payments-api";

const paymentKeys = {
  proofs: ["payments", "proofs"] as const,
};

export function usePaymentProofs(session: AuthSession) {
  return useQuery({
    queryKey: paymentKeys.proofs,
    queryFn: () => paymentsApi.listProofs(session),
  });
}

/**
 * A decision always refreshes the queue: the proof just decided leaves it, and
 * anything another administrator decided meanwhile leaves it too.
 */
function useDecision(
  session: AuthSession,
  decide: (session: AuthSession, proofId: string, note: string) => Promise<unknown>,
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (variables: { proofId: string; note: string }) =>
      decide(session, variables.proofId, variables.note),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: paymentKeys.proofs }),
  });
}

export function useApproveProof(session: AuthSession) {
  return useDecision(session, paymentsApi.approveProof);
}

export function useRejectProof(session: AuthSession) {
  return useDecision(session, paymentsApi.rejectProof);
}
