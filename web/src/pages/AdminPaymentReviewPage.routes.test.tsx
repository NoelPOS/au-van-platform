import { cleanup, fireEvent, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { slip, stubApi } from "../test/paymentFixtures";
import {
  adminSession,
  json,
  renderAdminPage,
} from "../test/renderAdminPage";
import { RouteSearch } from "../test/RouteSearch";
import { route, tripAt } from "../test/tripFixtures";
import { AdminPaymentReviewPage } from "./AdminPaymentReviewPage";

const back = { ...route, id: "route-2", origin: "Mega Bangna", destination: "AU" };
const outbound = slip("proof-1", "AUV-260921-OUTBOUND");
const inbound = {
  ...slip("proof-2", "AUV-260921-INBOUND1"),
  trip: { ...outbound.trip, id: "trip-2", origin: "Mega Bangna", destination: "AU" },
};

function renderQueue(path = "/admin/payments") {
  stubApi({
    queue: () => json([outbound, inbound]),
    routes: () => json([route, back]),
    trips: () =>
      json([
        tripAt("2026-10-01T01:00:00Z", { id: "trip-1" }),
        tripAt("2026-10-01T09:00:00Z", { id: "trip-2", routeId: "route-2" }),
      ]),
  });
  renderAdminPage(
    <>
      <AdminPaymentReviewPage session={adminSession} />
      <RouteSearch />
    </>,
    path,
  );
}

function waiting() {
  return within(screen.getByRole("list", { name: "Slips waiting for review" }))
    .getAllByRole("button")
    .map((button) => button.textContent);
}

describe("AdminPaymentReviewPage routes", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("narrows the waiting slips to the routes chosen", async () => {
    renderQueue();
    const routes = within(await screen.findByRole("group", { name: "Routes" }));

    expect(waiting()).toEqual(["AUV-260921-OUTBOUND", "AUV-260921-INBOUND1"]);
    fireEvent.click(routes.getByRole("button", { name: "Mega Bangna → AU" }));

    expect(waiting()).toEqual(["AUV-260921-INBOUND1"]);
    expect(screen.getByText("1 waiting")).toBeInTheDocument();
    expect(screen.getByTestId("route-search")).toHaveTextContent("route-2");
  });

  it("opens with the routes already in the address", async () => {
    renderQueue("/admin/payments?routes=route-1");

    await screen.findByRole("group", { name: "Routes" });
    expect(waiting()).toEqual(["AUV-260921-OUTBOUND"]);
  });

  it("offers no route choice while every slip is on one route", async () => {
    stubApi({ routes: () => json([route, back]) });
    renderAdminPage(<AdminPaymentReviewPage session={adminSession} />);

    expect(await screen.findByText("1 waiting")).toBeInTheDocument();
    expect(screen.queryByRole("group", { name: "Routes" })).toBeNull();
  });
});
