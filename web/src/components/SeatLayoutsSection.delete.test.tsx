import { cleanup, fireEvent, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { adminSession, json, renderAdminPage } from "../test/renderAdminPage";
import type { SeatLayout } from "../types/inventory";
import { SeatLayoutsSection } from "./SeatLayoutsSection";

const hiace: SeatLayout = {
  id: "layout-1",
  name: "Hiace",
  seats: [{ label: "A1", rowNumber: 1, columnNumber: 1 }],
};

function stubFetch(response: () => Response) {
  const fetcher = vi.fn(async () => response());
  vi.stubGlobal("fetch", fetcher);
  return fetcher;
}

function askToDelete() {
  renderAdminPage(<SeatLayoutsSection layouts={[hiace]} session={adminSession} />);
  fireEvent.click(screen.getByRole("button", { name: "Delete Hiace" }));
}

describe("deleting a seat layout", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("asks first, then deletes the layout", async () => {
    const fetcher = stubFetch(() => new Response(null, { status: 204 }));
    askToDelete();

    expect(screen.getByText("Delete this layout?")).toBeInTheDocument();
    expect(fetcher).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Yes, delete" }));

    expect(await screen.findByText("Seat layout deleted.")).toBeInTheDocument();
    expect(fetcher).toHaveBeenCalledWith(
      "/api/v1/admin/seat-layouts/layout-1",
      expect.objectContaining({ method: "DELETE" }),
    );
  });

  it("keeps the layout when the admin goes back", () => {
    const fetcher = stubFetch(() => new Response(null, { status: 204 }));
    askToDelete();

    fireEvent.click(screen.getByRole("button", { name: "Go back" }));

    expect(screen.getByRole("button", { name: "Delete Hiace" })).toBeInTheDocument();
    expect(fetcher).not.toHaveBeenCalled();
  });

  it("shows which vans still use the layout when the API refuses", async () => {
    stubFetch(() =>
      json({ detail: "Used by VAN-01. Move them to another layout first." }, 409),
    );
    askToDelete();

    fireEvent.click(screen.getByRole("button", { name: "Yes, delete" }));

    expect(
      await screen.findByText("Used by VAN-01. Move them to another layout first."),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Delete Hiace" })).toBeInTheDocument();
  });
});
