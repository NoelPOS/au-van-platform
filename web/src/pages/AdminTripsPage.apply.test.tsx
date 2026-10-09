import { cleanup, fireEvent, screen, within } from "@testing-library/react";
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
import type { PlannedDeparture } from "../types/schedule";
import { AdminTripsPage } from "./AdminTripsPage";

const line = { time: "07:00", routeId: "route-1", vehicleId: "van-1" };
const weekday = { id: "template-1", name: "Weekday", departures: [line] };
const planned: PlannedDeparture[] = [
  { ...line, date: "2026-01-05", outcome: "CREATE", reason: null },
  {
    ...line,
    date: "2026-01-06",
    outcome: "CLASH",
    reason: "VAN-01 is already on the 06:45 AU → Mega Bangna.",
  },
];

function render(handlers: Parameters<typeof stubAdminApi>[0]) {
  const fetcher = stubAdminApi({
    "GET /routes": () => json([route]),
    "GET /vehicles": () => json([van]),
    "GET /seat-layouts": () => json([layout]),
    "GET /trips": () =>
      json([tripAt("2026-01-05T00:30:00Z"), tripAt("2026-01-05T09:00:00Z")]),
    "GET /day-templates": () => json([weekday]),
    ...handlers,
  });
  renderAdminPage(<AdminTripsPage session={adminSession} />);
  return fetcher;
}

function keysSentTo(fetcher: ReturnType<typeof stubAdminApi>) {
  return fetcher.mock.calls
    .filter(([input]) => String(input).endsWith("/schedule/apply"))
    .map(([, init]) => new Headers(init?.headers).get("Idempotency-Key"));
}

async function pick(...names: string[]) {
  const calendar = within(
    await screen.findByRole("region", { name: "Trip calendar" }),
  );
  for (const name of names)
    fireEvent.click(
      calendar.getByRole("button", { name: new RegExp(`^${name},`) }),
    );
}

async function previewWeekday() {
  fireEvent.click(await screen.findByRole("button", { name: "Select days" }));
  await pick("Monday 5 January", "Tuesday 6 January");
  fireEvent.click(
    within(screen.getByRole("region", { name: "Planning" })).getByRole(
      "button",
      {
        name: "Apply template",
      },
    ),
  );
  fireEvent.click(drawer().getByRole("button", { name: "Preview on 2 days" }));
  await drawer().findByRole("heading", { name: "Apply “Weekday”" });
}

describe("AdminTripsPage applying a plan", () => {
  beforeEach(() => freezeBangkokTime("2026-01-04T10:00"));

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("previews a template on the chosen days and creates exactly what it showed", async () => {
    const fetcher = render({
      "POST /schedule/preview": () =>
        json({ planHash: "hash-1", departures: planned }),
      "POST /schedule/apply": () => json({ created: 1, skipped: 1 }, 201),
    });

    await previewWeekday();

    expect(await drawer().findByText("1 clash skipped")).toBeInTheDocument();
    expect(
      drawer().getByText("VAN-01 is already on the 06:45 AU → Mega Bangna."),
    ).toBeInTheDocument();
    expect(sentTo(fetcher, "POST /schedule/preview")).toEqual([
      { dates: ["2026-01-05", "2026-01-06"], departures: [line] },
    ]);
    fireEvent.click(
      drawer().getByRole("button", { name: "Create 1 departure" }),
    );

    expect(
      await screen.findByText("1 departure created, 1 skipped"),
    ).toBeInTheDocument();
    expect(sentTo(fetcher, "POST /schedule/apply")).toEqual([
      {
        dates: ["2026-01-05", "2026-01-06"],
        departures: [line],
        planHash: "hash-1",
      },
    ]);
    expect(keysSentTo(fetcher)[0]).toMatch(/^[0-9a-f-]{36}$/);
    expect(screen.queryByRole("region", { name: "Planning" })).toBeNull();
  });

  it("retries a failed apply with the same key, and re-previews when the timetable changed", async () => {
    let previews = 0;
    const answers = [
      json({ detail: "Request failed." }, 503),
      json({ code: "schedule_changed", detail: "Changed." }, 409),
      json({ created: 1, skipped: 1 }, 201),
    ];
    const fetcher = render({
      "POST /schedule/preview": () => {
        previews += 1;
        return json({ planHash: `hash-${previews}`, departures: planned });
      },
      "POST /schedule/apply": () => answers.shift() ?? json({}, 500),
    });
    await previewWeekday();
    const create = await drawer().findByRole("button", {
      name: "Create 1 departure",
    });

    fireEvent.click(create);
    expect(await drawer().findByText("Request failed.")).toBeInTheDocument();
    fireEvent.click(create);
    expect(
      await drawer().findByText(/The timetable changed while you were looking/),
    ).toBeInTheDocument();
    await vi.waitFor(() => expect(previews).toBe(2));
    await vi.waitFor(() => expect(create).toBeEnabled());
    fireEvent.click(create);

    expect(
      await screen.findByText("1 departure created, 1 skipped"),
    ).toBeInTheDocument();
    const [first, retry, afterChange] = keysSentTo(fetcher);
    expect(retry).toBe(first);
    expect(afterChange).not.toBe(first);
    expect(
      sentTo(fetcher, "POST /schedule/apply").map(
        (body) => (body as { planHash: string }).planHash,
      ),
    ).toEqual(["hash-1", "hash-1", "hash-2"]);
  });

  it("copies one day's departures onto other days", async () => {
    const fetcher = render({
      "POST /schedule/preview": () =>
        json({ planHash: "hash-1", departures: [] }),
    });

    await pick("Monday 5 January");
    fireEvent.click(drawer().getByRole("button", { name: "Copy day to…" }));
    await pick("Wednesday 7 January");
    fireEvent.click(
      within(screen.getByRole("region", { name: "Planning" })).getByRole(
        "button",
        {
          name: "Preview copy",
        },
      ),
    );

    expect(
      await drawer().findByRole("button", { name: "Nothing to create" }),
    ).toBeDisabled();
    expect(sentTo(fetcher, "POST /schedule/preview")).toEqual([
      {
        dates: ["2026-01-07"],
        departures: [
          { time: "07:30", routeId: "route-1", vehicleId: "van-1" },
          { time: "16:00", routeId: "route-1", vehicleId: "van-1" },
        ],
      },
    ]);
    expect(sentTo(fetcher, "POST /schedule/apply")).toEqual([]);
  });
});
