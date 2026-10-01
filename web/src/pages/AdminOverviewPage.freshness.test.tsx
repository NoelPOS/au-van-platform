import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { useTripOperations } from "../hooks/useOperationsQueries";
import { usePaymentProofs } from "../hooks/usePaymentQueries";
import { adminSession, json, stubAdminApi } from "../test/renderAdminPage";
import { AdminOverviewPage } from "./AdminOverviewPage";

function ReviewQueue() {
  const proofs = usePaymentProofs(adminSession);
  return <p>{`Queue: ${proofs.data?.length ?? "…"}`}</p>;
}

function TripSeats() {
  const trip = useTripOperations(adminSession, "trip-1");
  return <p>{`Claimed: ${trip.data?.claimedSeats ?? "…"}`}</p>;
}

function renderFromOverview() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: 30_000 } },
  });
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/admin"]}>
        <Routes>
          <Route
            path="/admin"
            element={<AdminOverviewPage session={adminSession} />}
          />
          <Route path="/admin/payments" element={<ReviewQueue />} />
          <Route path="/admin/operations" element={<TripSeats />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const trip = {
  id: "trip-1",
  routeId: "route-1",
  vehicleId: "van-1",
  departureAt: new Date(Date.now() + 3_600_000).toISOString(),
  status: "ACTIVE",
  seats: [],
};

describe("AdminOverviewPage freshness", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("leaves the review queue to fetch its own list after the overview counted it", async () => {
    let proofs: { id: string }[] = [];
    stubAdminApi({ "GET /payment-proofs": () => json(proofs) });

    renderFromOverview();
    await screen.findByText("Every slip has been reviewed.");
    proofs = [{ id: "proof-1" }];
    fireEvent.click(screen.getByRole("link", { name: "Review slips" }));

    expect(await screen.findByText("Queue: 1")).toBeInTheDocument();
  });

  it("leaves operations to fetch a trip's seats after the overview showed them", async () => {
    let claimed = 0;
    stubAdminApi({
      "GET /trips": () => json([trip]),
      "GET /operations/trips/trip-1": () =>
        json({ claimedSeats: claimed, totalSeats: 4 }),
    });

    renderFromOverview();
    await screen.findByText("0 of 4 seats claimed");
    claimed = 3;
    fireEvent.click(screen.getByRole("link", { name: "Open operations" }));

    expect(await screen.findByText("Claimed: 3")).toBeInTheDocument();
  });
});
