import { cleanup, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  adminSession,
  json,
  renderAdminPage,
  stubAdminApi,
} from "../test/renderAdminPage";
import { AdminSeatLayoutsPage } from "./AdminSeatLayoutsPage";

describe("AdminSeatLayoutsPage", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("hands the seat layouts the API returned to the layout editor", async () => {
    stubAdminApi({
      "GET /seat-layouts": () =>
        json([{ id: "layout-1", name: "Commuter 13", seats: [] }]),
    });

    renderAdminPage(<AdminSeatLayoutsPage session={adminSession} />);

    expect(
      screen.getByRole("heading", { name: "Seat layouts", level: 1 }),
    ).toBeInTheDocument();
    expect(await screen.findByText("Commuter 13")).toBeInTheDocument();
  });

  it("shows a retryable failure when the layouts cannot be loaded", async () => {
    stubAdminApi({
      "GET /seat-layouts": () => json({ detail: "Access denied." }, 403),
    });

    renderAdminPage(<AdminSeatLayoutsPage session={adminSession} />);

    expect(
      await screen.findByText("Could not load seat layouts"),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Try again" }),
    ).toBeInTheDocument();
  });
});
