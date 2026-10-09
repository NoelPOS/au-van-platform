import { cleanup, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { adminSession, json, renderAdminPage, stubAdminApi } from "../test/renderAdminPage";
import { AdminLayout } from "./AdminLayout";

class SilentEventSource {
  close() {}
}

describe("AdminLayout refunds badge", () => {
  beforeEach(() => vi.stubGlobal("EventSource", SilentEventSource));

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("counts the refunds due beside Refunds and on the phone's More button", async () => {
    stubAdminApi({
      "POST /api/v1/events/ticket": () => json({ ticket: "t" }),
      "GET /refunds": () => json([{ id: "booking-1" }, { id: "booking-2" }]),
    });

    renderAdminPage(<AdminLayout session={adminSession} />);

    expect(await screen.findByRole("link", { name: "Refunds, 2 refunds due" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "More, 2 refunds due" })).toBeInTheDocument();
  });

  it("shows no count when nothing is due", async () => {
    const fetcher = stubAdminApi({
      "POST /api/v1/events/ticket": () => json({ ticket: "t" }),
      "GET /refunds": () => json([]),
    });

    renderAdminPage(<AdminLayout session={adminSession} />);

    await vi.waitFor(() => expect(fetcher).toHaveBeenCalledWith("/api/v1/admin/refunds", expect.anything()));
    expect(screen.getByRole("link", { name: "Refunds" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "More" })).toBeInTheDocument();
  });
});
