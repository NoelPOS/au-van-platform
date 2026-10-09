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

const weekday = {
  id: "template-1",
  name: "Weekday",
  departures: [{ time: "07:00", routeId: "route-1", vehicleId: "van-1" }],
};

function render(handlers: Parameters<typeof stubAdminApi>[0] = {}) {
  const fetcher = stubAdminApi({
    "GET /routes": () => json([route]),
    "GET /vehicles": () => json([van]),
    "GET /seat-layouts": () => json([layout]),
    "GET /trips": () =>
      json([tripAt("2026-01-05T00:30:00Z"), tripAt("2026-01-05T09:00:00Z")]),
    ...handlers,
  });
  renderAdminPage(<AdminTripsPage session={adminSession} />);
  return fetcher;
}

function time(number: number, value: string) {
  fireEvent.change(drawer().getByLabelText(`Departure ${number} time`), {
    target: { value },
  });
}

async function openMonday() {
  fireEvent.click(
    await screen.findByRole("button", {
      name: "Monday 5 January, 2 departures",
    }),
  );
}

describe("AdminTripsPage day templates", () => {
  beforeEach(() => freezeBangkokTime("2026-01-04T10:00"));

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("creates a template, each new line starting half an hour after the last", async () => {
    const fetcher = render({
      "POST /day-templates": (body) =>
        json({ id: "new", ...(body as object) }, 201),
    });

    fireEvent.click(await screen.findByRole("button", { name: "Templates" }));
    fireEvent.click(drawer().getByRole("button", { name: "New template" }));
    fireEvent.change(drawer().getByLabelText("Name"), {
      target: { value: " Saturday " },
    });
    time(1, "9:00");
    fireEvent.click(drawer().getByRole("button", { name: "Add departure" }));
    expect(drawer().getByLabelText("Departure 2 time")).toHaveValue("09:30");
    time(2, "08:15");
    fireEvent.click(drawer().getByRole("button", { name: "Create template" }));

    expect(await screen.findByText("Template created")).toBeInTheDocument();
    expect(sentTo(fetcher, "POST /day-templates")).toEqual([
      {
        name: "Saturday",
        departures: [
          { time: "08:15", routeId: "route-1", vehicleId: "van-1" },
          { time: "09:00", routeId: "route-1", vehicleId: "van-1" },
        ],
      },
    ]);
  });

  it("sends nothing while a name, a time or a van is wrong", async () => {
    const fetcher = render();

    fireEvent.click(await screen.findByRole("button", { name: "Templates" }));
    fireEvent.click(drawer().getByRole("button", { name: "New template" }));
    time(1, "7:60");
    fireEvent.click(drawer().getByRole("button", { name: "Add departure" }));
    time(2, "08:00");
    fireEvent.click(drawer().getByRole("button", { name: "Create template" }));
    await act(async () => {});

    expect(drawer().getByText("Give the template a name.")).toBeInTheDocument();
    expect(drawer().getByLabelText("Departure 1 time")).toHaveAttribute(
      "aria-invalid",
      "true",
    );

    fireEvent.change(drawer().getByLabelText("Name"), {
      target: { value: "Doubled" },
    });
    fireEvent.click(drawer().getByRole("button", { name: "Create template" }));
    await act(async () => {});
    expect(sentTo(fetcher, "POST /day-templates")).toEqual([]);

    time(1, "08:00");
    expect(
      drawer().getByText("One van is down twice at 08:00."),
    ).toBeInTheDocument();
    fireEvent.click(drawer().getByRole("button", { name: "Create template" }));
    await act(async () => {});

    expect(sentTo(fetcher, "POST /day-templates")).toEqual([]);
  });

  it("saves an existing day as a template", async () => {
    render();
    await openMonday();

    fireEvent.click(
      drawer().getByRole("button", { name: "Save it as a template" }),
    );

    expect(
      drawer().getByRole("heading", { name: "New template" }),
    ).toBeInTheDocument();
    expect(drawer().getByLabelText("Departure 1 time")).toHaveValue("07:30");
    expect(drawer().getByLabelText("Departure 2 time")).toHaveValue("16:00");
  });

  it("deletes a template only after it is confirmed", async () => {
    const fetcher = render({
      "GET /day-templates": () => json([weekday]),
      "POST /day-templates/template-1/delete": () =>
        new Response(null, { status: 204 }),
    });

    fireEvent.click(await screen.findByRole("button", { name: "Templates" }));
    fireEvent.click(
      await drawer().findByRole("button", { name: "Edit Weekday" }),
    );
    fireEvent.click(drawer().getByRole("button", { name: "Delete template" }));
    const confirm = within(
      drawer().getByRole("group", { name: "Delete template" }),
    );
    fireEvent.click(confirm.getByRole("button", { name: "Keep it" }));
    expect(
      fetcher.mock.calls.some(([input]) => String(input).endsWith("/delete")),
    ).toBe(false);

    fireEvent.click(drawer().getByRole("button", { name: "Delete template" }));
    fireEvent.click(drawer().getByRole("button", { name: "Delete" }));

    expect(await screen.findByText("Template deleted")).toBeInTheDocument();
  });

  it("clears a day's unbooked departures and says what it kept", async () => {
    const fetcher = render({
      "POST /schedule/clear-day": () => json({ removed: 1, kept: 1 }),
    });
    await openMonday();

    fireEvent.click(
      drawer().getByRole("button", { name: "Clear unbooked departures" }),
    );
    fireEvent.click(drawer().getByRole("button", { name: "Yes, clear" }));

    expect(
      await screen.findByText(
        "Removed 1 departure. Kept 1 that students have booked or queued for.",
      ),
    ).toBeInTheDocument();
    expect(sentTo(fetcher, "POST /schedule/clear-day")).toEqual([
      { date: "2026-01-05" },
    ]);
  });

  it("shows why a day could not be cleared", async () => {
    render({
      "POST /schedule/clear-day": () =>
        json(
          {
            code: "day_changed",
            detail: "A student started booking on this day.",
          },
          409,
        ),
    });
    await openMonday();

    fireEvent.click(
      drawer().getByRole("button", { name: "Clear unbooked departures" }),
    );
    fireEvent.click(drawer().getByRole("button", { name: "Yes, clear" }));

    expect(
      await drawer().findByText("A student started booking on this day."),
    ).toBeInTheDocument();
  });
});
