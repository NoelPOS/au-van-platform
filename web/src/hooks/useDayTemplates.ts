import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { scheduleApi } from "../services/scheduleApi";
import type { AuthSession } from "../types/auth";
import type { DayTemplateInput } from "../types/schedule";

const templatesKey = ["inventory", "day-templates"] as const;

export function useDayTemplates(session: AuthSession) {
  return useQuery({
    queryKey: templatesKey,
    queryFn: () => scheduleApi.listTemplates(session),
  });
}

export function useSaveDayTemplate(session: AuthSession) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, input }: { id?: string; input: DayTemplateInput }) =>
      id
        ? scheduleApi.updateTemplate(session, id, input)
        : scheduleApi.createTemplate(session, input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: templatesKey }),
  });
}

export function useDeleteDayTemplate(session: AuthSession) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => scheduleApi.deleteTemplate(session, id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: templatesKey }),
  });
}
