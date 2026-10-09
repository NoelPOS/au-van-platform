import { cleanup, fireEvent, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  adminSession,
  json,
  renderAdminPage,
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

const trips = [
  tripAt("2026-01-05T00:30:00Z"),
  tripAt("2026-01-05T09:00:00Z"),
  tripAt("2026-01-06T01:00:00Z", { status: "CANCELLED" }),
];

async function renderCalendar() {
  stubAdminApi({
    "GET /routes": () => json([route]),
    "GET /vehicles": () => json([van]),
    "GET /seat-layouts": () => json([layout]),
    "GET /trips": () => json(trips),
  });
  renderAdminPage(<AdminTripsPage session={adminSession} />);
  await screen.findByRole("region", { name: "Trip calendar" });
}

function calendar() {
  return within(screen.getByRole("region", { name: "Trip calendar" }));
}

describe("AdminTripsPage calendar", () => {
  beforeEach(() => freezeBangkokTime("2026-01-04T10:00"));

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("opens on this month with each day's departures in Bangkok time", async () => {
    await renderCalendar();

    expect(
      calendar().getByRole("heading", { name: "January 2026" }),
    ).toBeInTheDocument();
    const monday = calendar().getByRole("button", {
      name: "Monday 5 January, 2 departures",
    });
    expect(monday).toHaveTextContent("07:30Mega Bangna16:00Mega Bangna");
    expect(
      calendar().getByRole("button", {
        name: "Tuesday 6 January, 1 departure",
      }),
    ).toHaveTextContent("08:00");
    expect(
      calendar().getByRole("button", {
        name: "Sunday 4 January, 0 departures",
      }),
    ).toBeInTheDocument();

    fireEvent.click(calendar().getByRole("button", { name: "Next month" }));
    expect(
      calendar().getByRole("heading", { name: "February 2026" }),
    ).toBeInTheDocument();
  });

  it("lists a day's departures when the day is clicked, and edits one from there", async () => {
    await renderCalendar();

    fireEvent.click(
      calendar().getByRole("button", {
        name: "Monday 5 January, 2 departures",
      }),
    );

    expect(
      drawer().getByRole("heading", { name: "Monday 5 January" }),
    ).toBeInTheDocument();
    expect(
      drawer().getByText("2 departures · 07:30 – 16:00"),
    ).toBeInTheDocument();
    fireEvent.click(
      drawer().getByRole("button", { name: "Edit the 16:00 to Mega Bangna" }),
    );
    expect(
      drawer().getByRole("heading", { name: "Edit trip" }),
    ).toBeInTheDocument();
    expect(drawer().getByLabelText("Time")).toHaveValue("16:00");
  });

  it("shows a week of departures, the cancelled one struck through", async () => {
    await renderCalendar();

    fireEvent.click(screen.getByRole("radio", { name: "Week" }));

    expect(
      calendar().getByRole("heading", { name: "29 – 4 Jan 2026" }),
    ).toBeInTheDocument();
    fireEvent.click(calendar().getByRole("button", { name: "Next week" }));
    const tuesday = within(
      calendar().getByRole("region", { name: "Tuesday 6 January" }),
    );
    expect(tuesday.getByRole("listitem")).toHaveClass("line-through");
    expect(tuesday.getByText("08:00")).toBeInTheDocument();
  });

  it("selects days to plan, refusing days that have passed", async () => {
    await renderCalendar();

    fireEvent.click(screen.getByRole("button", { name: "Select days" }));

    expect(
      calendar().getByRole("button", {
        name: "Saturday 3 January, 0 departures",
      }),
    ).toBeDisabled();
    fireEvent.click(
      calendar().getByRole("button", {
        name: "Monday 5 January, 2 departures",
      }),
    );
    fireEvent.click(
      calendar().getByRole("button", {
        name: "Select every Wednesday left in January 2026",
      }),
    );

    const bar = within(screen.getByRole("region", { name: "Planning" }));
    expect(bar.getByText("5")).toBeInTheDocument();
    expect(
      calendar().getByRole("button", {
        name: "Wednesday 28 January, 0 departures",
      }),
    ).toHaveAttribute("aria-pressed", "true");
    fireEvent.click(
      calendar().getByRole("button", {
        name: "Select every Wednesday left in January 2026",
      }),
    );
    expect(bar.getByText("1")).toBeInTheDocument();

    fireEvent.click(bar.getByRole("button", { name: "Cancel" }));
    expect(screen.queryByRole("region", { name: "Planning" })).toBeNull();
  });
});
