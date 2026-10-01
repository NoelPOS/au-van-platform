import { cleanup, fireEvent, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { adminSession as session, json, renderAdminPage } from "../test/renderAdminPage";
import { AdminRoutesPage } from "./AdminRoutesPage";
import { AdminSeatLayoutsPage } from "./AdminSeatLayoutsPage";
import { AdminTripsPage } from "./AdminTripsPage";
import { AdminVansPage } from "./AdminVansPage";

describe("inventory pages", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("loads the routes page and sends a new route to the admin API", async () => {
    const fetcher = vi.fn(
      async (input: RequestInfo | URL, init?: RequestInit) => {
        if (String(input).endsWith("/routes") && init?.method === "POST") {
          return json(
            {
              id: "route-1",
              origin: "AU",
              destination: "Mega Bangna",
              fare: 35,
              durationMinutes: 45,
              status: "ACTIVE",
            },
            201,
          );
        }
        return json([]);
      },
    );
    vi.stubGlobal("fetch", fetcher);

    renderAdminPage(<AdminRoutesPage session={session} />);

    expect(await screen.findByText("No routes yet")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Origin"), {
      target: { value: "AU" },
    });
    fireEvent.change(screen.getByLabelText("Destination"), {
      target: { value: "Mega Bangna" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Create route" }));

    await vi.waitFor(() =>
      expect(fetcher).toHaveBeenCalledWith(
        "/api/v1/admin/routes",
        expect.objectContaining({
          method: "POST",
          body: JSON.stringify({
            origin: "AU",
            destination: "Mega Bangna",
            fare: 0,
            durationMinutes: 30,
          }),
        }),
      ),
    );
  });

  it("shows a retryable failure when the inventory request is rejected", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockImplementation(() =>
          Promise.resolve(json({ detail: "Access denied." }, 403)),
        ),
    );

    renderAdminPage(<AdminTripsPage session={session} />);

    expect(await screen.findByText("Could not load trips")).toBeInTheDocument();
    expect(screen.getByText("Access denied.")).toBeInTheDocument();
  });

  it("provides seat-layout, vehicle, and trip management pages", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation(() => Promise.resolve(json([]))),
    );

    renderAdminPage(<AdminSeatLayoutsPage session={session} />);
    expect(await screen.findByLabelText("Layout name")).toBeInTheDocument();
    cleanup();

    renderAdminPage(<AdminVansPage session={session} />);
    expect(await screen.findByLabelText("Vehicle code")).toBeInTheDocument();
    cleanup();

    renderAdminPage(<AdminTripsPage session={session} />);
    expect(await screen.findByLabelText("Departure")).toBeInTheDocument();
  });

  it("offers only trip statuses the API can deserialise", async () => {
    const trip = {
      id: "trip-1",
      routeId: "route-1",
      vehicleId: "vehicle-1",
      departureAt: "2026-01-05T09:00:00Z",
      fare: 35,
      durationMinutes: 45,
      status: "ACTIVE",
      seats: [],
    };
    const fetcher = vi.fn(
      async (input: RequestInfo | URL, init?: RequestInit) => {
        if (String(input).endsWith("/trips") && !init?.method) {
          return json([trip]);
        }
        if (
          String(input).endsWith("/trips/trip-1") &&
          init?.method === "PUT"
        ) {
          const body = JSON.parse(String(init.body)) as { status: string };
          return json({ ...trip, status: body.status });
        }
        return json([]);
      },
    );
    vi.stubGlobal("fetch", fetcher);

    renderAdminPage(<AdminTripsPage session={session} />);
    fireEvent.click(await screen.findByRole("button", { name: "Edit" }));

    const offered = Array.from(
      (screen.getByLabelText("Status") as HTMLSelectElement).options,
    ).map((option) => option.value);
    expect(offered).toEqual(["ACTIVE", "CANCELLED"]);

    for (const status of offered) {
      fireEvent.click(screen.getByRole("button", { name: "Edit" }));
      fireEvent.change(screen.getByLabelText("Status"), {
        target: { value: status },
      });
      fireEvent.click(screen.getByRole("button", { name: "Save trip" }));

      // datetime-local is minute-precision, so the resubmitted value gains ":00.000Z".
      await vi.waitFor(() =>
        expect(fetcher).toHaveBeenCalledWith(
          expect.stringContaining("/trips/trip-1"),
          expect.objectContaining({
            method: "PUT",
            body: JSON.stringify({
              departureAt: "2026-01-05T09:00:00.000Z",
              status,
            }),
          }),
        ),
      );
      await screen.findByRole("button", { name: "Edit" });
    }
  });
});
