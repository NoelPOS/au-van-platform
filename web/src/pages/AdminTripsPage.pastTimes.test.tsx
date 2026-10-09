import {
  act,
  cleanup,
  fireEvent,
  screen,
  within,
} from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  adminSession,
  json,
  renderAdminPage,
  sentTo,
  stubAdminApi,
} from "../test/renderAdminPage";
import {
  drawer,
  freezeBangkokTime,
  layout,
  route,
  tripAt,
  van,
} from "../test/tripFixtures";
import { AdminTripsPage } from "./AdminTripsPage";

async function openSchedule() {
  const fetcher = stubAdminApi({
    "GET /routes": () => json([route]),
    "GET /vehicles": () => json([van]),
    "GET /seat-layouts": () => json([layout]),
    "GET /trips": () =>
      json([tripAt("2026-01-05T00:30:00Z"), tripAt("2026-01-05T09:00:00Z")]),
    "POST /trips": () => json(tripAt("2026-01-04T09:00:00Z"), 201),
  });
  renderAdminPage(<AdminTripsPage session={adminSession} />);
  fireEvent.click(await screen.findByRole("button", { name: "New trip" }));
  return fetcher;
}

function days() {
  return within(drawer().getByRole("group", { name: "Days" }));
}

function suggestion(clock: string) {
  return within(
    drawer().getByRole("group", { name: "This route runs at" }),
  ).getByRole("button", { name: clock });
}

describe("AdminTripsPage past departure times", () => {
  beforeEach(() => freezeBangkokTime("2026-01-04T10:00"));

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("disables today's times that have passed and says why", async () => {
    await openSchedule();

    expect(suggestion("07:30")).toBeEnabled();
    fireEvent.click(days().getByRole("button", { name: "Today" }));

    expect(suggestion("07:30")).toBeDisabled();
    expect(suggestion("16:00")).toBeEnabled();
    expect(
      drawer().getByText("Today, 10:00 and earlier have passed."),
    ).toBeInTheDocument();
    expect(suggestion("07:30")).toHaveAccessibleDescription(
      "Today, 10:00 and earlier have passed.",
    );
  });

  it("refuses a typed time that has passed today and sends nothing", async () => {
    const fetcher = await openSchedule();

    fireEvent.click(days().getByRole("button", { name: "Today" }));
    fireEvent.change(drawer().getByLabelText("Time"), {
      target: { value: "09:45" },
    });
    expect(
      drawer().getByText(
        "09:45 has already passed today. Choose a later time or another day.",
      ),
    ).toBeInTheDocument();
    fireEvent.click(drawer().getByRole("button", { name: "Schedule trip" }));
    await act(async () => {});

    expect(sentTo(fetcher, "POST /trips")).toEqual([]);
  });

  it("accepts the same time on a later day", async () => {
    const fetcher = await openSchedule();

    fireEvent.click(days().getByRole("button", { name: "Tomorrow" }));
    fireEvent.change(drawer().getByLabelText("Time"), {
      target: { value: "09:45" },
    });
    fireEvent.click(drawer().getByRole("button", { name: "Schedule trip" }));

    await vi.waitFor(() =>
      expect(sentTo(fetcher, "POST /trips")).toEqual([
        expect.objectContaining({ departureAt: "2026-01-05T02:45:00.000Z" }),
      ]),
    );
  });

  it("will not offer a day that has passed when a past trip is edited", async () => {
    stubAdminApi({
      "GET /trips": () => json([tripAt("2026-01-02T02:00:00Z", { id: "old" })]),
    });
    renderAdminPage(<AdminTripsPage session={adminSession} />);
    fireEvent.click(await screen.findByRole("radio", { name: "List" }));
    fireEvent.click(screen.getByRole("radio", { name: "Past" }));
    fireEvent.click(
      screen.getByRole("button", { name: "Edit trip on Fri 2 Jan · 09:00" }),
    );

    const day = within(drawer().getByRole("group", { name: "Day" }));
    expect(day.getByRole("button", { name: "Fri 2 Jan" })).toBeDisabled();
    expect(day.getByRole("button", { name: "Today" })).toBeEnabled();
  });
});
