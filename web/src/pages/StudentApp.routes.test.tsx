import { cleanup, fireEvent, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { json, trip } from "../test/bookingFixtures";
import { renderPage, stubApi } from "../test/renderStudentApp";

const megaMorning = { ...trip, id: "mega-1" };
const megaEvening = {
  ...trip,
  id: "mega-2",
  departureAt: "2030-10-01T10:00:00Z",
};
const siam = {
  ...trip,
  id: "siam-1",
  routeId: "route-2",
  destination: "Siam Paragon",
  fare: 90,
  departureAt: "2030-10-01T02:00:00Z",
};

describe("Student picks a route first", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("lists each route once, then shows only that route's departures", async () => {
    stubApi({ trips: () => json([megaMorning, siam, megaEvening]) });

    renderPage();

    const routes = await screen.findByRole("region", { name: "Routes" });
    expect(
      within(routes)
        .getAllByRole("button")
        .map((button) => button.textContent),
    ).toEqual([
      expect.stringMatching(/^To Mega Bangna.*฿35/),
      expect.stringMatching(/^To Siam Paragon.*฿90/),
    ]);
    expect(
      screen.queryByRole("region", { name: "Departures" }),
    ).not.toBeInTheDocument();

    fireEvent.click(
      within(routes).getByRole("button", { name: /Mega Bangna/ }),
    );

    const board = screen.getByRole("region", { name: "Departures" });
    expect(within(board).getAllByRole("button")).toHaveLength(2);
    expect(within(board).getAllByText(/^Arrives \d\d:\d\d$/)).toHaveLength(2);
    expect(within(board).queryByText("Mega Bangna")).not.toBeInTheDocument();
    expect(screen.getByText("To Mega Bangna")).toBeInTheDocument();
    expect(
      within(board).queryByRole("button", { name: /Siam Paragon/ }),
    ).not.toBeInTheDocument();
  });

  it("goes back to every route from Change route", async () => {
    stubApi({ trips: () => json([megaMorning, siam]) });

    renderPage("/?route=route-2");

    const board = await screen.findByRole("region", { name: "Departures" });
    expect(
      within(board).getByRole("button", { name: /Siam Paragon/ }),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Change route" }));

    expect(screen.getByRole("region", { name: "Routes" })).toBeInTheDocument();
  });

  it("skips the choice when only one route runs", async () => {
    stubApi({ trips: () => json([megaMorning, megaEvening]) });

    renderPage();

    expect(
      await screen.findByRole("region", { name: "Departures" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("region", { name: "Routes" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Change route" }),
    ).not.toBeInTheDocument();
  });
});
