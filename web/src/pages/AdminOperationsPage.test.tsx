import { cleanup, fireEvent, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  renderPage,
  route,
  stubApi,
  trip,
} from "../test/operationsFixtures";
import { json } from "../test/renderAdminPage";

async function chooseTheTrip() {
  fireEvent.click(
    await screen.findByRole("button", { name: /AU → Mega Bangna/ }),
  );
}

describe("AdminOperationsPage", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("asks for nothing about a trip until one is chosen", async () => {
    const fetcher = stubApi();

    renderPage();

    expect(
      await screen.findByText("Choose a trip to see its bookings and waitlist."),
    ).toBeInTheDocument();
    expect(
      fetcher.mock.calls.filter(([url]) =>
        String(url).includes("/operations/trips/"),
      ),
    ).toHaveLength(0);
  });

  it("reports the queue, its promotions and the bookings by status", async () => {
    const fetcher = stubApi();

    renderPage();
    await chooseTheTrip();

    expect(await screen.findByText("Somchai P.")).toBeInTheDocument();
    expect(screen.getByText("#1")).toBeInTheDocument();
    expect(screen.getByText("Malee K.")).toBeInTheDocument();
    expect(screen.getByText("#2")).toBeInTheDocument();
    expect(screen.getByText("PROMOTED")).toBeInTheDocument();
    expect(screen.getByText(/Offer ends/)).toBeInTheDocument();
    expect(screen.getByText("WITHDRAWN")).toBeInTheDocument();
    expect(screen.getByText("—")).toBeInTheDocument();
    expect(screen.getByText(/2 of 2 seats claimed/)).toBeInTheDocument();
    expect(screen.getByText("CONFIRMED")).toBeInTheDocument();
    expect(screen.getByText("3")).toBeInTheDocument();
    const call = fetcher.mock.calls.find(([url]) =>
      String(url).includes("/operations/trips/"),
    );
    expect(call?.[0]).toBe("/api/v1/admin/operations/trips/trip-1");
    expect(new Headers(call?.[1]?.headers).get("Authorization")).toBe(
      "Bearer admin-token",
    );
  });

  it("lists the dead letters the dispatcher gave up on", async () => {
    stubApi();

    renderPage();

    expect(await screen.findByText("BOOKING_CANCELLED")).toBeInTheDocument();
    expect(
      screen.getByText("LINE refused the push: 400 invalid recipient."),
    ).toBeInTheDocument();
    expect(screen.getByText("5")).toBeInTheDocument();
  });

  it("says why a trip could not be read rather than showing an empty queue", async () => {
    stubApi({ trip: () => json({ detail: "Trip not found." }, 404) });

    renderPage();
    await chooseTheTrip();

    expect(
      await screen.findByText("Could not load this trip"),
    ).toBeInTheDocument();
    expect(screen.getByText("Trip not found.")).toBeInTheDocument();
    expect(screen.getByText("BOOKING_CANCELLED")).toBeInTheDocument();
  });

  it("says why the dead letters could not be read", async () => {
    stubApi({
      deadLetters: () => json({ detail: "Access denied." }, 403),
    });

    renderPage();

    expect(
      await screen.findByText("Could not load dead letters"),
    ).toBeInTheDocument();
    expect(screen.getByText("Access denied.")).toBeInTheDocument();
  });

  it("offers a retry when the trip listing itself fails", async () => {
    let attempts = 0;
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        if (url.endsWith("/admin/trips")) {
          attempts += 1;
          return attempts === 1
            ? json({ detail: "Access denied." }, 403)
            : json([trip]);
        }
        if (url.endsWith("/admin/routes")) return json([route]);
        if (url.endsWith("/operations/dead-letters")) return json([]);
        throw new Error(`unexpected request: ${url}`);
      }),
    );

    renderPage();

    expect(
      await screen.findByText("Could not load operations"),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));

    expect(
      await screen.findByRole("heading", { name: "Upcoming departures" }),
    ).toBeInTheDocument();
  });
});
