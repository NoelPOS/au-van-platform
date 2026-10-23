import { cleanup, fireEvent, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { inDays, route, stubApi, trip } from "../test/operationsFixtures";
import {
  adminSession,
  json,
  renderAdminPage,
} from "../test/renderAdminPage";
import { RouteSearch } from "../test/RouteSearch";
import { AdminOperationsPage } from "./AdminOperationsPage";

const out = expect.stringContaining("AU → Mega Bangna");
const home = expect.stringContaining("Mega Bangna → AU");
const back = { ...route, id: "route-2", origin: "Mega Bangna", destination: "AU" };
const returning = { ...trip, id: "trip-2", routeId: "route-2", departureAt: inDays(2) };

function renderOperations(path = "/admin/operations") {
  stubApi({
    routes: () => json([route, back]),
    trips: () => json([trip, returning]),
  });
  renderAdminPage(
    <>
      <AdminOperationsPage session={adminSession} />
      <RouteSearch />
    </>,
    path,
  );
}

function departures() {
  return within(screen.getByRole("region", { name: "Upcoming departures" }))
    .getAllByRole("button")
    .map((button) => button.textContent);
}

describe("AdminOperationsPage routes", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("narrows the departures to the routes chosen", async () => {
    renderOperations();
    const routes = within(await screen.findByRole("group", { name: "Routes" }));

    expect(departures()).toEqual([out, home]);
    fireEvent.click(routes.getByRole("button", { name: "AU → Mega Bangna" }));

    expect(departures()).toEqual([out]);
    expect(screen.getByTestId("route-search")).toHaveTextContent("route-1");
  });

  it("opens with the routes already in the address", async () => {
    renderOperations("/admin/operations?routes=route-2");

    await screen.findByRole("group", { name: "Routes" });
    expect(departures()).toEqual([home]);
  });
});
