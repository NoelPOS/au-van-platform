import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router";
import { StudentBookTripPage } from "../pages/StudentBookTripPage";
import { StudentLayout } from "../pages/StudentLayout";
import { StudentTicketPage } from "../pages/StudentTicketPage";
import { StudentTicketsPage } from "../pages/StudentTicketsPage";
import { StudentTripsPage } from "../pages/StudentTripsPage";
import {
  booking,
  eligible,
  hold,
  json,
  seatMap,
  session,
  trip,
  waitlistEntry,
} from "./bookingFixtures";

type Routes = {
  trips?: () => Response;
  seats?: () => Response | Promise<Response>;
  hold?: () => Response;
  release?: () => Response;
  bookings?: () => Response;
  createBooking?: (init: RequestInit) => Response;
  paymentProof?: (init: RequestInit) => Response;
  cancel?: () => Response;
  eligibility?: () => Response;
  waitlist?: () => Response;
  joinWaitlist?: () => Response;
  leaveWaitlist?: () => Response;
};

export function stubApi(routes: Routes = {}) {
  const fetcher = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    const method = init?.method ?? "GET";
    if (url.endsWith("/seats")) return routes.seats?.() ?? json(seatMap());
    if (url.endsWith("/leave"))
      return routes.leaveWaitlist?.() ?? new Response(null, { status: 204 });
    if (url === "/api/v1/waitlist" && method === "POST")
      return routes.joinWaitlist?.() ?? json(waitlistEntry, 201);
    if (url === "/api/v1/waitlist") return routes.waitlist?.() ?? json([]);
    if (url.includes("/release"))
      return routes.release?.() ?? new Response(null, { status: 204 });
    if (url === "/api/v1/seat-holds")
      return routes.hold?.() ?? json(hold(), 201);
    if (url === "/api/v1/me/booking-eligibility")
      return routes.eligibility?.() ?? json(eligible);
    if (url.endsWith("/cancel"))
      return routes.cancel?.() ?? json({ ...booking, status: "CANCELLED" });
    if (url.endsWith("/payment-proof"))
      return (
        routes.paymentProof?.(init ?? {}) ??
        json({ ...booking, status: "PAYMENT_UNDER_REVIEW" })
      );
    if (url === "/api/v1/bookings" && method === "POST")
      return (
        routes.createBooking?.(init ?? {}) ?? json(booking, 201)
      );
    if (url === "/api/v1/bookings") return routes.bookings?.() ?? json([]);
    if (url === "/api/v1/trips") return routes.trips?.() ?? json([trip]);
    throw new Error(`unexpected request: ${method} ${url}`);
  });
  vi.stubGlobal("fetch", fetcher);
  return fetcher;
}

export function renderPage(path = "/") {
  // The application's own defaults, so a seat map served from cache shows up here.
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: 30_000 } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route element={<StudentLayout session={session} />} path="/">
            <Route element={<StudentTripsPage />} index />
            <Route element={<StudentBookTripPage />} path="trips/:tripId" />
            <Route element={<StudentTicketsPage />} path="tickets" />
            <Route element={<StudentTicketPage />} path="tickets/:bookingId" />
          </Route>
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

// RTL's waitFor and findBy* stall under Vitest's fake timers, so advance the
// clock here; the trailing millisecond flushes React Query's batched notify.
export async function tick(milliseconds = 0) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(milliseconds);
    await vi.advanceTimersByTimeAsync(1);
  });
}

export async function selectSeatA1() {
  fireEvent.click(await screen.findByRole("button", { name: /Mega Bangna/ }));
  fireEvent.click(await screen.findByRole("button", { name: "Seat A1, available" }));
}

export async function holdFailsWith(body: unknown, status: number) {
  stubApi({ hold: () => json(body, status) });
  renderPage();
  await selectSeatA1();
  fireEvent.click(screen.getByRole("button", { name: "Hold seats" }));
}

export async function reachPassengerDetails() {
  await selectSeatA1();
  fireEvent.click(screen.getByRole("button", { name: "Hold seats" }));
  await screen.findByRole("button", { name: "Confirm booking" });
  fireEvent.change(screen.getByLabelText("Full name"), {
    target: { value: "Somchai P." },
  });
  fireEvent.change(screen.getByLabelText("Phone number"), {
    target: { value: "0812345678" },
  });
}
