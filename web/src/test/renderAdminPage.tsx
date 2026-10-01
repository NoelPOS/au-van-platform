import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import type { ReactNode } from "react";
import { MemoryRouter } from "react-router";
import { vi } from "vitest";
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

type Handler = (body: unknown) => Response;

function keyOf(input: RequestInfo | URL, init?: RequestInit) {
  return `${init?.method ?? "GET"} ${String(input).replace("/api/v1/admin", "")}`;
}

export function stubAdminApi(handlers: Record<string, Handler>) {
  const fetcher = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const handler = handlers[keyOf(input, init)];
    const body = init?.body ? (JSON.parse(String(init.body)) as unknown) : undefined;
    return handler ? handler(body) : json([]);
  });
  vi.stubGlobal("fetch", fetcher);
  return fetcher;
}

export function sentTo(fetcher: ReturnType<typeof stubAdminApi>, key: string) {
  return fetcher.mock.calls
    .filter(([input, init]) => keyOf(input, init) === key)
    .map(([, init]) => JSON.parse(String(init?.body)) as unknown);
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
