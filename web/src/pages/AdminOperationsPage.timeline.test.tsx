import { cleanup, fireEvent, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { formatDay } from "../utils/format";
import {
  inDays,
  json,
  renderPage,
  stubApi,
  trip,
} from "../test/operationsFixtures";

const sooner = { ...trip, id: "trip-sooner", departureAt: inDays(1) };
const later = { ...trip, id: "trip-later", departureAt: inDays(3) };
const gone = { ...trip, id: "trip-gone", departureAt: inDays(-2) };

describe("AdminOperationsPage trip timeline", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("lists what is ahead soonest first and keeps departed trips aside", async () => {
    stubApi({ trips: () => json([later, gone, sooner]) });

    renderPage();
    const timeline = (
      await screen.findByRole("heading", { name: "Upcoming departures" })
    ).closest("section") as HTMLElement;
    const days = within(timeline)
      .getAllByRole("list")
      .map((list) => list.getAttribute("aria-label"));
    const earlier = within(timeline).getByText("Earlier departures (1)")
      .parentElement as HTMLElement;

    expect(days).toEqual([
      formatDay(sooner.departureAt),
      formatDay(later.departureAt),
      formatDay(gone.departureAt),
    ]);
    expect(within(earlier).getByRole("list")).toHaveAccessibleName(
      formatDay(gone.departureAt),
    );
  });

  it("loads the trip picked from the timeline and marks it as the one shown", async () => {
    const fetcher = stubApi({ trips: () => json([trip, later]) });

    renderPage();
    const [first, second] = await screen.findAllByRole("button", {
      name: /AU → Mega Bangna/,
    });
    fireEvent.click(first);

    expect(
      await screen.findByRole("heading", { name: "AU → Mega Bangna" }),
    ).toBeInTheDocument();
    expect(first).toHaveAttribute("aria-pressed", "true");
    expect(second).toHaveAttribute("aria-pressed", "false");
    expect(
      fetcher.mock.calls.filter(([url]) =>
        String(url).endsWith("/operations/trips/trip-1"),
      ),
    ).toHaveLength(1);
  });

  it("says plainly when nothing is scheduled ahead", async () => {
    stubApi({ trips: () => json([gone]) });

    renderPage();

    expect(
      await screen.findByText(
        "No departures ahead. Schedule one from Trips and it lands here.",
      ),
    ).toBeInTheDocument();
  });
});
