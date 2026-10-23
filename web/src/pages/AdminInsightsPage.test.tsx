import { cleanup, fireEvent, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  adminSession,
  json,
  renderAdminPage,
  stubAdminApi,
} from "../test/renderAdminPage";
import { AdminInsightsPage } from "./AdminInsightsPage";

const week = "GET /insights?from=2026-10-17&to=2026-10-23";

const busyWeek = {
  totals: {
    revenue: 12450,
    refundsDue: 70,
    refunded: 35,
    awaitingPayment: 105,
    bookings: 48,
    seatsSold: 52,
    seatsOffered: 120,
    occupancy: 52 / 120,
    trips: 10,
    cancelledTrips: 1,
  },
  byRoute: [
    { routeId: "asok", origin: "AU", destination: "Asok", trips: 6, seatsSold: 40, seatsOffered: 72, occupancy: 40 / 72, revenue: 9800 },
    { routeId: "mega", origin: "AU", destination: "Mega Bangna", trips: 4, seatsSold: 12, seatsOffered: 48, occupancy: 0.25, revenue: 2650 },
  ],
  byDay: ["17", "18", "19", "20", "21", "22", "23"].map((day, index) => ({
    date: `2026-10-${day}`,
    revenue: index === 3 ? 4200 : 1375,
    seatsSold: 7,
  })),
};

const quietWeek = {
  totals: { ...busyWeek.totals, revenue: 0, refundsDue: 0, refunded: 0, awaitingPayment: 0, bookings: 0, seatsSold: 0, seatsOffered: 0, occupancy: 0, trips: 0, cancelledTrips: 0 },
  byRoute: [],
  byDay: busyWeek.byDay.map((day) => ({ ...day, revenue: 0, seatsSold: 0 })),
};

function figure(label: string) {
  return within(screen.getByRole("region", { name: label }));
}

describe("AdminInsightsPage", () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-10-23T05:00:00Z"));
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("shows the week's figures, the revenue by day and each route", async () => {
    stubAdminApi({ [week]: () => json(busyWeek) });

    renderAdminPage(<AdminInsightsPage session={adminSession} />);

    expect(await screen.findByText("฿12,450")).toBeInTheDocument();
    expect(figure("Revenue").getByText("฿105 awaiting payment")).toBeInTheDocument();
    expect(figure("Seats sold").getByText("52")).toBeInTheDocument();
    expect(figure("Seats sold").getByText("43% of 120 offered")).toBeInTheDocument();
    expect(figure("Bookings").getByText("10 trips · 1 cancelled")).toBeInTheDocument();
    expect(figure("Refunds owed").getByText("฿70")).toBeInTheDocument();
    expect(
      screen.getByRole("img", {
        name: "Revenue by day, Sat 17 Oct to Fri 23 Oct: ฿12,450 in total, the best day Tue 20 Oct with ฿4,200.",
      }),
    ).toBeInTheDocument();

    const rows = within(screen.getByRole("table", { name: "Routes in this period" })).getAllByRole("row");
    expect(rows[1]).toHaveTextContent("AU → Asok");
    expect(within(rows[1]).getByText("40 of 72")).toBeInTheDocument();
    expect(within(rows[1]).getByText("56%")).toBeInTheDocument();
    expect(within(rows[1]).getByText("฿9,800")).toBeInTheDocument();
    expect(rows[2]).toHaveTextContent("AU → Mega Bangna");
  });

  it("asks for the last thirty days and then the month so far, in Bangkok dates", async () => {
    const fetcher = stubAdminApi({ [week]: () => json(busyWeek) });
    renderAdminPage(<AdminInsightsPage session={adminSession} />);
    await screen.findByText("฿12,450");

    fireEvent.click(screen.getByRole("radio", { name: "30 days" }));
    await vi.waitFor(() =>
      expect(fetcher).toHaveBeenCalledWith("/api/v1/admin/insights?from=2026-09-24&to=2026-10-23", expect.anything()),
    );

    fireEvent.click(screen.getByRole("radio", { name: "This month" }));
    await vi.waitFor(() =>
      expect(fetcher).toHaveBeenCalledWith("/api/v1/admin/insights?from=2026-10-01&to=2026-10-23", expect.anything()),
    );
    expect(screen.getByText("Thu 1 Oct – Fri 23 Oct · Bangkok")).toBeInTheDocument();
  });

  it("keeps the figures but says so when nothing departed in the period", async () => {
    stubAdminApi({ [week]: () => json(quietWeek) });

    renderAdminPage(<AdminInsightsPage session={adminSession} />);

    expect(await screen.findByText("No departures in this period")).toBeInTheDocument();
    expect(figure("Revenue").getByText("฿0")).toBeInTheDocument();
    expect(screen.queryByRole("img")).toBeNull();
    expect(screen.queryByRole("table")).toBeNull();
  });

  it("offers a retry when the figures cannot load", async () => {
    let refuse = true;
    stubAdminApi({ [week]: () => (refuse ? json({ detail: "Service unavailable." }, 503) : json(busyWeek)) });

    renderAdminPage(<AdminInsightsPage session={adminSession} />);

    expect(await screen.findByText("Could not load insights")).toBeInTheDocument();
    refuse = false;
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(await screen.findByText("฿12,450")).toBeInTheDocument();
  });
});
