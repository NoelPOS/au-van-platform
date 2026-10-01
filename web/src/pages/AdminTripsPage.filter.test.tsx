import { cleanup, fireEvent, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  adminSession,
  json,
  renderAdminPage,
  stubAdminApi,
} from "../test/renderAdminPage";
import { freezeBangkokTime, tripAt, van } from "../test/tripFixtures";
import { AdminTripsPage } from "./AdminTripsPage";

function renderWith(trips: ReturnType<typeof tripAt>[]) {
  stubAdminApi({
    "GET /trips": () => json(trips),
    "GET /vehicles": () => json([van]),
  });
  renderAdminPage(<AdminTripsPage session={adminSession} />);
}

function dayHeadings() {
  return screen
    .getAllByRole("heading", { level: 2 })
    .map((heading) => heading.firstChild?.textContent);
}

function times(table: string) {
  return within(screen.getByRole("table", { name: table }))
    .getAllByRole("row")
    .slice(1)
    .map((row) => within(row).getAllByRole("cell")[0].textContent);
}

describe("AdminTripsPage filters", () => {
  beforeEach(() => freezeBangkokTime("2026-01-04T10:00"));

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("shows upcoming trips first, grouped by Bangkok day in departure order", async () => {
    renderWith([
      tripAt("2026-01-05T18:30:00Z"),
      tripAt("2026-01-04T05:00:00Z"),
      tripAt("2026-01-04T16:59:00Z"),
      tripAt("2026-01-04T01:00:00Z"),
      tripAt("2026-01-06T01:00:00Z", { status: "CANCELLED" }),
    ]);

    await screen.findByRole("table", { name: "Trips on Sun 4 Jan" });
    expect(screen.getByRole("radio", { name: "Upcoming" })).toBeChecked();
    expect(dayHeadings()).toEqual(["Today · Sun 4 Jan", "Tue 6 Jan"]);
    expect(times("Trips on Sun 4 Jan")).toEqual(["12:00", "23:59"]);
    expect(times("Trips on Tue 6 Jan")).toEqual(["01:30"]);
  });

  it("shows departed and cancelled trips under their own filters, latest first", async () => {
    renderWith([
      tripAt("2026-01-04T01:00:00Z"),
      tripAt("2026-01-02T01:00:00Z"),
      tripAt("2026-01-03T01:00:00Z"),
      tripAt("2026-01-05T09:00:00Z"),
      tripAt("2026-01-06T01:00:00Z", { status: "CANCELLED" }),
    ]);

    fireEvent.click(await screen.findByRole("radio", { name: "Past" }));
    expect(dayHeadings()).toEqual([
      "Today · Sun 4 Jan",
      "Sat 3 Jan",
      "Fri 2 Jan",
    ]);

    fireEvent.click(screen.getByRole("radio", { name: "Cancelled" }));
    expect(dayHeadings()).toEqual(["Tue 6 Jan"]);
    expect(screen.queryByRole("table", { name: "Trips on Mon 5 Jan" })).toBeNull();
  });

  it("says what is missing for a filter with nothing in it", async () => {
    renderWith([tripAt("2026-01-02T01:00:00Z")]);

    expect(await screen.findByText("Nothing on the board")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("radio", { name: "Cancelled" }));
    expect(screen.getByText("No cancelled trips")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("radio", { name: "Past" }));
    expect(screen.getByRole("table", { name: "Trips on Fri 2 Jan" })).toBeInTheDocument();
  });
});
