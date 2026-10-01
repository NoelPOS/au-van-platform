import { cleanup, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { adminSession, json, renderAdminPage } from "../test/renderAdminPage";
import { AdminOverviewPage } from "./AdminOverviewPage";

const hour = 3_600_000;
const route = {
  id: "route-1",
  origin: "AU",
  destination: "Mega Bangna",
  fare: 35,
  durationMinutes: 45,
  status: "ACTIVE",
};
const van = { id: "van-1", code: "VAN-01", name: "Hiace", seatLayoutId: "l", status: "ACTIVE" };

function trip(id: string, departureAt: Date, status = "ACTIVE") {
  return {
    id,
    routeId: "route-1",
    vehicleId: "van-1",
    departureAt: departureAt.toISOString(),
    fare: 35,
    durationMinutes: 45,
    status,
    seats: [{ label: "A1", rowNumber: 1, columnNumber: 1 }],
  };
}

function stubApi(responses: Record<string, () => Response>) {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input).replace("/api/v1/admin", "");
      return (responses[path] ?? (() => json([])))();
    }),
  );
}

function renderPage() {
  renderAdminPage(<AdminOverviewPage session={adminSession} />);
}

describe("AdminOverviewPage", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("lists upcoming active departures soonest first, with the seats each has claimed", async () => {
    const soon = new Date(Date.now() + 2 * hour);
    const later = new Date(Date.now() + 26 * hour);
    stubApi({
      "/trips": () =>
        json([
          trip("later", later),
          trip("gone", new Date(Date.now() - hour)),
          trip("cancelled", new Date(Date.now() + hour), "CANCELLED"),
          trip("soon", soon),
        ]),
      "/routes": () => json([route]),
      "/vehicles": () => json([van]),
      "/operations/trips/soon": () => json({ claimedSeats: 3, totalSeats: 13 }),
      "/operations/trips/later": () => json({ claimedSeats: 0, totalSeats: 13 }),
    });

    renderPage();

    const departures = within(
      await screen.findByRole("region", { name: "Next departures" }),
    );
    const cards = await departures.findAllByRole("listitem");
    expect(cards).toHaveLength(2);
    expect(cards[0]).toHaveTextContent("AU → Mega Bangna");
    expect(cards[0]).toHaveTextContent("VAN-01");
    expect(
      await within(cards[0]).findByText("3 of 13 seats claimed"),
    ).toBeInTheDocument();
    expect(
      await within(cards[1]).findByText("0 of 13 seats claimed"),
    ).toBeInTheDocument();
  });

  it("counts the slips waiting for review and the notifications given up on", async () => {
    stubApi({
      "/payment-proofs": () => json([{ id: "p1" }, { id: "p2" }]),
      "/operations/dead-letters": () => json([{ id: "d1" }]),
    });

    renderPage();

    const slips = within(
      screen.getByRole("region", { name: "Slips waiting for review" }),
    );
    expect(await slips.findByText("2")).toBeInTheDocument();
    expect(slips.getByRole("link", { name: "Review slips" })).toHaveAttribute(
      "href",
      "/admin/payments",
    );
    const letters = within(
      screen.getByRole("region", { name: "Notifications given up on" }),
    );
    expect(await letters.findByText("1")).toBeInTheDocument();
    expect(
      letters.getByRole("link", { name: "Open operations" }),
    ).toHaveAttribute("href", "/admin/operations");
  });

  it("reads as a clear board when nothing is scheduled and nothing is waiting", async () => {
    stubApi({});

    renderPage();

    expect(await screen.findByText("The board is clear.")).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Schedule a trip" }),
    ).toHaveAttribute("href", "/admin/trips");
    expect(
      await screen.findByText("Every slip has been reviewed."),
    ).toBeInTheDocument();
    expect(
      await screen.findByText("Every notification went out."),
    ).toBeInTheDocument();
  });

  it("shows a failed count where it belongs and keeps the rest of the board", async () => {
    stubApi({
      "/payment-proofs": () => json({ detail: "Review queue is down." }, 503),
    });

    renderPage();

    const slips = within(
      screen.getByRole("region", { name: "Slips waiting for review" }),
    );
    expect(await slips.findByRole("alert")).toHaveTextContent(
      "Review queue is down.",
    );
    expect(await screen.findByText("The board is clear.")).toBeInTheDocument();
  });
});
