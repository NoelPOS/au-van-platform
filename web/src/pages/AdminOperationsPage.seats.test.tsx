import { cleanup, fireEvent, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderPage, seatMap, stubApi } from "../test/operationsFixtures";
import { json } from "../test/renderAdminPage";

async function chooseTheTrip() {
  fireEvent.click(await screen.findByRole("button", { name: /AU → Mega Bangna/ }));
}

describe("AdminOperationsPage seat plan", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("draws each seat in the van with its label and state, and a legend", async () => {
    const fetcher = stubApi({
      seats: () => json(seatMap(["BOOKED", "HELD", "AVAILABLE", "HELD_BY_YOU"])),
    });

    renderPage();
    await chooseTheTrip();

    const plan = await screen.findByRole("group", { name: "Seat occupancy" });
    expect(within(plan).getByRole("img", { name: "A1, booked" })).toHaveTextContent("A1");
    expect(within(plan).getByRole("img", { name: "A2, held" })).toBeInTheDocument();
    expect(within(plan).getByRole("img", { name: "A3, open" })).toBeInTheDocument();
    expect(within(plan).getByRole("img", { name: "A4, held" })).toBeInTheDocument();
    const legend = within(screen.getByRole("list", { name: "Seat legend" }));
    expect(legend.getAllByRole("listitem").map((item) => item.textContent)).toEqual([
      "open",
      "held",
      "booked",
    ]);
    const call = fetcher.mock.calls.find(([url]) => String(url).endsWith("/seats"));
    expect(call?.[0]).toBe("/api/v1/trips/trip-1/seats");
    expect(new Headers(call?.[1]?.headers).get("Authorization")).toBe(
      "Bearer admin-token",
    );
  });

  it("says why the seat plan could not be read and keeps the rest of the trip", async () => {
    stubApi({ seats: () => json({ detail: "Trip not found." }, 404) });

    renderPage();
    await chooseTheTrip();

    expect(await screen.findByText("Could not load the seat plan")).toBeInTheDocument();
    expect(screen.getByText("Trip not found.")).toBeInTheDocument();
    expect(screen.getByText(/2 of 2 seats claimed/)).toBeInTheDocument();
    expect(screen.getByText("Somchai P.")).toBeInTheDocument();
  });
});
