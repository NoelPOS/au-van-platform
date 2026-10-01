import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import type { ReactNode } from "react";
import { MemoryRouter } from "react-router";
import { ToastProvider } from "../components/ui/Toast";
import type { AuthSession } from "../types/auth";

export const adminSession: AuthSession = {
  accessToken: "admin-token",
  expiresIn: 900,
  user: { id: "admin-id", role: "ADMIN", displayName: "Noel" },
};

export function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

export function renderAdminPage(page: ReactNode) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <ToastProvider>{page}</ToastProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
