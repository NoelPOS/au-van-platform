import { cleanup, fireEvent, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  adminSession,
  json,
  renderAdminPage,
  stubAdminApi,
} from "../test/renderAdminPage";
import { RouteSearch } from "../test/RouteSearch";
import {
  freezeBangkokTime,
  layout,
  route,
  showList,
  tripAt,
  van,
} from "../test/tripFixtures";
import { AdminTripsPage } from "./AdminTripsPage";

const back = { ...route, id: "route-2", origin: "Mega Bangna", destination: "AU" };
const siam = { ...route, id: "route-3", destination: "Siam Paragon" };
const siamBack = { ...siam, id: "route-4", origin: "Siam Paragon", destination: "AU" };

const trips = [
  tripAt("2026-01-05T09:00:00Z"),
  tripAt("2026-01-04T23:00:00Z", { routeId: "route-2" }),
  tripAt("2026-01-05T00:30:00Z"),
  tripAt("2026-01-06T05:00:00Z", { routeId: "route-4" }),
  tripAt("2026-01-06T04:00:00Z", { routeId: "route-3" }),
  tripAt("2026-01-06T03:00:00Z", { routeId: "route-2" }),
  tripAt("2026-01-06T02:00:00Z"),
];

async function renderTrips(path = "/admin/trips") {
  stubAdminApi({
    "GET /routes": () => json([siamBack, back, siam, route]),
    "GET /vehicles": () => json([van]),
    "GET /seat-layouts": () => json([layout]),
    "GET /trips": () => json(trips),
  });
  renderAdminPage(
    <>
      <AdminTripsPage session={adminSession} />
      <RouteSearch />
    </>,
    path,
  );
  await screen.findByRole("group", { name: "Routes" });
}

function legend() {
  return within(screen.getByRole("group", { name: "Routes" }));
}

function day(name: string) {
  return screen.getByRole("button", { name: new RegExp(`^${name},`) });
}

describe("AdminTripsPage routes", () => {
  beforeEach(() => freezeBangkokTime("2026-01-04T10:00"));

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("counts each day's departures by route, folding a crowded day into more", async () => {
    await renderTrips();

    expect(day("Monday 5 January")).toHaveTextContent(
      "to Mega Bangna2from Mega Bangna1",
    );
    expect(day("Tuesday 6 January")).toHaveTextContent(
      "to Mega Bangna1from Mega Bangna1+2 more",
    );
  });

  it("tints each week departure with its route and hides routes from the legend", async () => {
    await renderTrips();
    fireEvent.click(screen.getByRole("radio", { name: "Week" }));
    fireEvent.click(screen.getByRole("button", { name: "Next week" }));
    const monday = within(screen.getByRole("region", { name: "Monday 5 January" }));

    expect(monday.getByText("06:00").closest("li")).toHaveTextContent(
      "from Mega Bangna",
    );
    expect(monday.getByText("06:00").closest("li")).toHaveClass("bg-route-1/8");

    fireEvent.click(legend().getByRole("button", { name: "Mega Bangna → AU" }));
    expect(monday.getByText("06:00")).toBeInTheDocument();
    expect(monday.queryByText("16:00")).toBeNull();
    expect(legend().getByRole("button", { name: "All routes" })).toHaveAttribute(
      "aria-pressed",
      "false",
    );

    fireEvent.click(legend().getByRole("button", { name: "Mega Bangna → AU" }));
    expect(monday.getByText("16:00")).toBeInTheDocument();
    expect(legend().getByRole("button", { name: "All routes" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
  });

  it("groups each listed day by route, earliest departure first", async () => {
    await renderTrips();
    await showList();

    const monday = screen.getAllByRole("table", { name: / on Mon 5 Jan$/ });
    expect(monday.map((table) => table.getAttribute("aria-label"))).toEqual([
      "AU → Mega Bangna on Mon 5 Jan",
      "Mega Bangna → AU on Mon 5 Jan",
    ]);
    expect(
      within(monday[0])
        .getAllByRole("row")
        .slice(1)
        .map((row) => within(row).getAllByRole("cell")[0].textContent),
    ).toEqual(["07:30", "16:00"]);
    expect(
      screen.getAllByRole("table", { name: / on Tue 6 Jan$/ }),
    ).toHaveLength(4);
  });

  it("keeps the chosen routes in the address so a reload shows the same", async () => {
    await renderTrips("/admin/trips?routes=route-2");

    expect(legend().getByRole("button", { name: "Mega Bangna → AU" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    expect(day("Monday 5 January")).toHaveTextContent("from Mega Bangna1");
    expect(day("Monday 5 January")).not.toHaveTextContent("to Mega Bangna");

    fireEvent.click(legend().getByRole("button", { name: "AU → Siam Paragon" }));
    expect(screen.getByTestId("route-search")).toHaveTextContent("route-2,route-3");

    fireEvent.click(legend().getByRole("button", { name: "All routes" }));
    expect(screen.getByTestId("route-search")).toHaveTextContent("all");
    expect(day("Monday 5 January")).toHaveTextContent("to Mega Bangna2");
  });
});
