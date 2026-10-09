import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { refundsApi } from "../services/refundsApi";
import type { AuthSession } from "../types/auth";

export const refundKeys = { due: ["payments", "refunds"] as const };

export function useRefundsDue(session: AuthSession) {
  return useQuery({
    queryKey: refundKeys.due,
    queryFn: () => refundsApi.listDue(session),
  });
}

export function useMarkRefunded(session: AuthSession) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { bookingId: string; note: string }) =>
      refundsApi.markRefunded(session, input.bookingId, input.note),
    onSettled: () => queryClient.invalidateQueries({ queryKey: refundKeys.due }),
  });
}
