import { act, cleanup, fireEvent, screen, within } from "@testing-library/react";
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

const inventory = {
  "GET /routes": () => json([route]),
  "GET /vehicles": () => json([van]),
  "GET /seat-layouts": () => json([layout]),
};

async function submitAndSettle(name: string) {
  fireEvent.click(drawer().getByRole("button", { name }));
  await act(async () => {});
}

async function openSchedule(handlers: Parameters<typeof stubAdminApi>[0]) {
  const fetcher = stubAdminApi({ ...inventory, ...handlers });
  renderAdminPage(<AdminTripsPage session={adminSession} />);
  fireEvent.click(await screen.findByRole("button", { name: "New trip" }));
  return fetcher;
}

function day(name: string) {
  return within(drawer().getByRole("group", { name: "Days" })).getByRole(
    "button",
    { name },
  );
}

function typeTime(value: string) {
  fireEvent.change(drawer().getByLabelText("Time"), { target: { value } });
}

describe("AdminTripsPage scheduling", () => {
  beforeEach(() => freezeBangkokTime("2026-01-04T10:00"));

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("schedules one trip from the drawer in Bangkok time", async () => {
    const fetcher = await openSchedule({
      "POST /trips": () => json(tripAt("2026-01-05T09:00:00Z"), 201),
    });

    expect(drawer().getByRole("button", { name: /^Route/ })).toHaveTextContent(
      "AU → Mega Bangna35.00 THB · 45 min",
    );
    fireEvent.click(day("Tomorrow"));
    typeTime("16:00");
    expect(
      drawer().getByText("Arrives 16:45 · 35.00 THB · 3 seats"),
    ).toBeInTheDocument();
    fireEvent.click(drawer().getByRole("button", { name: "Schedule trip" }));

    expect(await screen.findByText("Trip scheduled")).toBeInTheDocument();
    expect(sentTo(fetcher, "POST /trips")).toEqual([
      {
        routeId: "route-1",
        vehicleId: "van-1",
        departureAt: "2026-01-05T09:00:00.000Z",
      },
    ]);
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("creates one trip per chosen day and reports the days the API refused", async () => {
    const fetcher = await openSchedule({
      "POST /trips": (body) =>
        (body as { departureAt: string }).departureAt.startsWith("2026-01-06")
          ? json({ detail: "That van is already on the road then." }, 409)
          : json(tripAt("2026-01-05T09:00:00Z"), 201),
    });

    fireEvent.click(day("Tomorrow"));
    fireEvent.click(day("Tue 6"));
    fireEvent.click(day("Wed 7"));
    typeTime("16:00");
    fireEvent.click(drawer().getByRole("button", { name: "Schedule 3 trips" }));

    const report = await drawer().findByRole("alert");
    expect(report).toHaveTextContent("2 trips scheduled.");
    expect(report).toHaveTextContent(
      "Tue 6 Jan — That van is already on the road then.",
    );
    expect(sentTo(fetcher, "POST /trips")).toHaveLength(3);
    expect(day("Tomorrow")).toHaveAttribute("aria-pressed", "false");
    expect(day("Tue 6")).toHaveAttribute("aria-pressed", "true");
    expect(screen.queryByText("3 trips scheduled")).toBeNull();

    fireEvent.click(drawer().getByRole("button", { name: "Schedule trip" }));

    await vi.waitFor(() => expect(sentTo(fetcher, "POST /trips")).toHaveLength(4));
    expect(sentTo(fetcher, "POST /trips")[3]).toEqual(
      expect.objectContaining({ departureAt: "2026-01-06T09:00:00.000Z" }),
    );
  });

  it("refuses a time that is not a 24-hour clock and sends nothing", async () => {
    const fetcher = await openSchedule({});

    fireEvent.click(day("Tomorrow"));
    typeTime("25:00");
    await submitAndSettle("Schedule trip");

    expect(drawer().getByLabelText("Time")).toHaveAttribute("aria-invalid", "true");
    expect(
      drawer().getByText("Enter a 24-hour time, like 07:30."),
    ).toBeInTheDocument();
    expect(drawer().queryByRole("alert")).toBeNull();
    expect(sentTo(fetcher, "POST /trips")).toEqual([]);
  });

  it("asks for a day before it schedules anything", async () => {
    const fetcher = await openSchedule({});

    typeTime("08:00");
    await submitAndSettle("Schedule trip");

    expect(drawer().getByText("Choose at least one day.")).toBeInTheDocument();
    expect(screen.queryByText(/trips? scheduled/)).toBeNull();
    expect(sentTo(fetcher, "POST /trips")).toEqual([]);
  });

  it("suggests the times the chosen route already runs", async () => {
    await openSchedule({
      "GET /trips": () =>
        json([
          tripAt("2026-01-05T09:00:00Z"),
          tripAt("2026-01-06T09:00:00Z"),
          tripAt("2026-01-06T00:30:00Z"),
          tripAt("2026-01-06T02:00:00Z", { routeId: "route-2" }),
        ]),
    });

    const runs = within(
      drawer().getByRole("group", { name: "This route runs at" }),
    );
    expect(runs.getAllByRole("button").map((chip) => chip.textContent)).toEqual([
      "07:30",
      "16:00",
    ]);
    fireEvent.click(runs.getByRole("button", { name: "07:30" }));
    expect(drawer().getByLabelText("Time")).toHaveValue("07:30");
  });

  it("explains what is missing instead of a form when there is no route or van", async () => {
    const fetcher = await openSchedule({ "GET /vehicles": () => json([]) });

    expect(
      drawer().getByText("Not quite ready to schedule"),
    ).toBeInTheDocument();
    expect(drawer().getByRole("link", { name: "Add a van" })).toHaveAttribute(
      "href",
      "/admin/vans",
    );
    expect(
      drawer().queryByRole("button", { name: "Schedule trip" }),
    ).toBeNull();
    expect(sentTo(fetcher, "POST /trips")).toEqual([]);
  });
});
