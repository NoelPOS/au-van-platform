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
  van,
} from "../test/tripFixtures";
import { AdminTripsPage } from "./AdminTripsPage";

const back = {
  ...route,
  id: "route-2",
  origin: "Mega Bangna",
  destination: "AU",
  durationMinutes: 30,
};
const paragon = {
  ...route,
  id: "route-3",
  destination: "Siam Paragon",
  durationMinutes: 60,
};
const retired = {
  ...route,
  id: "route-4",
  destination: "Old Stop",
  status: "INACTIVE",
};
const spare = { ...van, id: "van-2", code: "VAN-02" };
const parked = { ...van, id: "van-3", code: "VAN-03", status: "INACTIVE" };

const weekday = {
  id: "template-1",
  name: "Weekday",
  departures: [{ time: "07:00", routeId: "route-1", vehicleId: "van-1" }],
};

function render(handlers: Parameters<typeof stubAdminApi>[0] = {}) {
  const fetcher = stubAdminApi({
    "GET /routes": () => json([route, back, paragon, retired]),
    "GET /vehicles": () => json([van, spare, parked]),
    "GET /seat-layouts": () => json([layout]),
    "GET /day-templates": () => json([weekday]),
    ...handlers,
  });
  renderAdminPage(<AdminTripsPage session={adminSession} />);
  return fetcher;
}

function shuttle() {
  return within(drawer().getByRole("group", { name: "Add a shuttle" }));
}

function type(label: string, value: string) {
  fireEvent.change(shuttle().getByLabelText(label), { target: { value } });
}

function choose(label: string, option: string) {
  fireEvent.click(
    shuttle().getByRole("button", { name: new RegExp(`^${label}`) }),
  );
  fireEvent.click(shuttle().getByRole("option", { name: option }));
}

async function openShuttle(template: "New template" | "Edit Weekday") {
  fireEvent.click(await screen.findByRole("button", { name: "Templates" }));
  fireEvent.click(await drawer().findByRole("button", { name: template }));
  fireEvent.click(drawer().getByRole("button", { name: "Add shuttle" }));
}

describe("AdminTripsPage shuttle generator", () => {
  beforeEach(() => freezeBangkokTime("2026-01-04T10:00"));

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("previews an out-and-back shuttle, adds it, and saves it with the template", async () => {
    const fetcher = render({
      "POST /day-templates": (body) =>
        json({ id: "new", ...(body as object) }, 201),
    });
    await openShuttle("New template");

    expect(
      shuttle().getByRole("button", { name: /^Route back/ }),
    ).toHaveTextContent("Mega Bangna → AU · 30 min");
    type("First departure", "7:00");
    type("Last departure", "09:00");
    const preview = within(
      shuttle().getByRole("region", { name: "Shuttle preview" }),
    );
    expect(preview.getByText("Adds 3 departures")).toBeInTheDocument();
    expect(
      preview.getAllByRole("listitem").map((item) => item.textContent),
    ).toEqual([
      "07:00AU to Mega BangnaVAN-01",
      "08:00Mega Bangna to AUVAN-01",
      "08:45AU to Mega BangnaVAN-01",
    ]);

    fireEvent.click(shuttle().getByRole("button", { name: "Add to template" }));

    expect(drawer().queryByRole("group", { name: "Add a shuttle" })).toBeNull();
    expect(drawer().getByLabelText("Departure 1 time")).toHaveValue("07:00");
    expect(drawer().getByLabelText("Departure 2 time")).toHaveValue("08:00");
    expect(drawer().getByLabelText("Departure 3 time")).toHaveValue("08:45");
    expect(drawer().queryByLabelText("Departure 4 time")).toBeNull();

    fireEvent.change(drawer().getByLabelText("Name"), {
      target: { value: "Weekday" },
    });
    fireEvent.click(drawer().getByRole("button", { name: "Create template" }));

    expect(await screen.findByText("Template created")).toBeInTheDocument();
    expect(sentTo(fetcher, "POST /day-templates")).toEqual([
      {
        name: "Weekday",
        departures: [
          { time: "07:00", routeId: "route-1", vehicleId: "van-1" },
          { time: "08:00", routeId: "route-2", vehicleId: "van-1" },
          { time: "08:45", routeId: "route-1", vehicleId: "van-1" },
        ],
      },
    ]);
  });

  it("offers only active routes and vans, and none for a one-way run", async () => {
    render();
    await openShuttle("New template");

    choose("Route out", "AU → Siam Paragon · 60 min");
    expect(
      shuttle().getByRole("button", { name: /^Route back/ }),
    ).toHaveTextContent("None, one-way");
    fireEvent.click(shuttle().getByRole("button", { name: /^Route back/ }));
    expect(shuttle().queryByRole("option", { name: /Old Stop/ })).toBeNull();
    fireEvent.click(shuttle().getByRole("button", { name: /^Route back/ }));
    fireEvent.click(shuttle().getByRole("button", { name: /^Van/ }));
    expect(shuttle().queryByRole("option", { name: /VAN-03/ })).toBeNull();
    fireEvent.click(shuttle().getByRole("option", { name: /VAN-02/ }));

    type("First departure", "07:00");
    type("Last departure", "11:00");
    expect(
      within(shuttle().getByRole("region", { name: "Shuttle preview" }))
        .getAllByRole("listitem")
        .map((item) => item.textContent),
    ).toEqual(["07:00AU to Siam ParagonVAN-02", "09:15AU to Siam ParagonVAN-02"]);
  });

  it("explains a shuttle it cannot build and adds nothing", async () => {
    render();
    await openShuttle("New template");

    fireEvent.click(shuttle().getByRole("button", { name: "Add to template" }));
    expect(shuttle().getByLabelText("First departure")).toHaveAttribute(
      "aria-invalid",
      "true",
    );

    type("First departure", "09:00");
    type("Last departure", "08:00");
    expect(
      shuttle().getByText("The last departure is before the first."),
    ).toBeInTheDocument();

    type("Last departure", "12:00");
    choose("Route out", "AU → Siam Paragon · 60 min");
    choose("Route back", "Mega Bangna → AU · 30 min");
    expect(
      shuttle().getByText(
        "Route back has to start at Siam Paragon, where route out ends.",
      ),
    ).toBeInTheDocument();
    fireEvent.click(shuttle().getByRole("button", { name: "Add to template" }));

    expect(drawer().getByLabelText("Departure 1 time")).toHaveValue("");
    expect(drawer().queryByLabelText("Departure 2 time")).toBeNull();
  });

  it("blocks a shuttle that puts a van on a line it is already driving", async () => {
    render();
    await openShuttle("Edit Weekday");

    type("First departure", "07:30");
    type("Last departure", "09:00");
    expect(
      shuttle().getByText(
        "VAN-01 is already on the 07:00 AU → Mega Bangna, so the 07:30 would clash.",
      ),
    ).toBeInTheDocument();
    fireEvent.click(shuttle().getByRole("button", { name: "Add to template" }));
    expect(drawer().queryByLabelText("Departure 2 time")).toBeNull();

    choose("Van", "VAN-02 — Hiace");
    fireEvent.keyDown(shuttle().getByLabelText("Last departure"), {
      key: "Enter",
    });
    await act(async () => {});

    expect(drawer().getByLabelText("Departure 1 time")).toHaveValue("07:00");
    expect(drawer().getByLabelText("Departure 2 time")).toHaveValue("07:30");
    expect(drawer().getByLabelText("Departure 3 time")).toHaveValue("08:30");
  });

  it("closes without adding anything when cancelled", async () => {
    render();
    await openShuttle("Edit Weekday");

    type("First departure", "10:00");
    type("Last departure", "12:00");
    fireEvent.click(shuttle().getByRole("button", { name: "Cancel" }));

    expect(drawer().queryByRole("group", { name: "Add a shuttle" })).toBeNull();
    expect(drawer().queryByLabelText("Departure 2 time")).toBeNull();
  });
});
