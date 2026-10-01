import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { AuthSession } from "../types/auth";
import type { SeatLayout } from "../types/inventory";
import { SeatLayoutsSection } from "./SeatLayoutsSection";

const session: AuthSession = {
  accessToken: "admin-token",
  expiresIn: 900,
  user: { id: "admin-id", role: "ADMIN", displayName: "Noel" },
};

const hiace: SeatLayout = {
  id: "layout-1",
  name: "Hiace",
  seats: [
    { label: "A1", rowNumber: 1, columnNumber: 1 },
    { label: "Jump", rowNumber: 1, columnNumber: 3 },
    { label: "B1", rowNumber: 2, columnNumber: 1 },
  ],
};

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function renderSection(layouts: SeatLayout[] = []) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={queryClient}>
      <SeatLayoutsSection layouts={layouts} session={session} />
    </QueryClientProvider>,
  );
}

function stubFetch(response: () => Response = () => json(hiace, 201)) {
  const fetcher = vi.fn(async () => response());
  vi.stubGlobal("fetch", fetcher);
  return fetcher;
}

function cell(row: number, column: number) {
  return screen.getByLabelText(new RegExp(`^Row ${row}, column ${column}(,|$)`), {
    selector: "button",
  });
}

const seatCount = () => screen.getByText(/placed$/);

function startBlank(name = "Shuttle") {
  fireEvent.change(screen.getByLabelText("Layout name"), { target: { value: name } });
  fireEvent.click(screen.getByText("Blank"));
}

function sentBody(fetcher: ReturnType<typeof stubFetch>) {
  const [, init] = fetcher.mock.calls[0] as unknown as [string, RequestInit];
  return { method: init.method, body: JSON.parse(String(init.body)) as unknown };
}

describe("SeatLayoutsSection", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("opens on a preset van and says when no layouts are saved yet", () => {
    renderSection();

    expect(screen.getByLabelText("Layout name")).toBeInTheDocument();
    expect(screen.getByRole("group", { name: "Seat plan" })).toBeInTheDocument();
    expect(seatCount()).toHaveTextContent("13 seats placed");
    expect(screen.getByText("No seat layouts yet")).toBeInTheDocument();
  });

  it("places a seat on click, labels it, and removes it on a second click", () => {
    renderSection();
    startBlank();

    expect(cell(1, 2)).toHaveAttribute("aria-pressed", "false");
    fireEvent.click(cell(1, 2));
    expect(cell(1, 2)).toHaveAttribute("aria-pressed", "true");
    expect(cell(1, 2)).toHaveAccessibleName("Row 1, column 2, seat A1");
    expect(seatCount()).toHaveTextContent("1 seat placed");

    fireEvent.click(cell(1, 2));
    expect(cell(1, 2)).toHaveAttribute("aria-pressed", "false");
    expect(seatCount()).toHaveTextContent("0 seats placed");
  });

  it("moves focus between spaces with the arrow keys and toggles the focused one", () => {
    renderSection();
    startBlank();

    cell(1, 1).focus();
    fireEvent.keyDown(cell(1, 1), { key: "ArrowRight" });
    expect(cell(1, 2)).toHaveFocus();
    fireEvent.keyDown(cell(1, 2), { key: "ArrowDown" });
    expect(cell(2, 2)).toHaveFocus();
    expect(cell(2, 2)).toHaveAttribute("tabindex", "0");
    expect(cell(1, 1)).toHaveAttribute("tabindex", "-1");
    fireEvent.keyDown(cell(2, 2), { key: "ArrowLeft" });
    fireEvent.keyDown(cell(2, 1), { key: "ArrowLeft" });
    expect(cell(2, 1)).toHaveFocus();
    expect(cell(2, 1)).toHaveAttribute("tabindex", "0");
    fireEvent.keyDown(cell(2, 1), { key: "ArrowUp" });
    fireEvent.keyDown(cell(1, 1), { key: "ArrowUp" });
    expect(cell(1, 1)).toHaveFocus();
    expect(cell(1, 1)).toHaveAttribute("tabindex", "0");

    fireEvent.click(document.activeElement as HTMLElement);
    expect(cell(1, 1)).toHaveAttribute("aria-pressed", "true");
  });

  it("keeps the selected space when an arrow key points out of the van", () => {
    renderSection();
    startBlank();

    fireEvent.click(cell(1, 4));
    fireEvent.keyDown(cell(1, 4), { key: "ArrowRight" });
    fireEvent.change(screen.getByLabelText("Columns"), { target: { value: "6" } });

    expect(cell(1, 4)).toHaveAttribute("tabindex", "0");
    expect(cell(1, 5)).toHaveAttribute("tabindex", "-1");
  });

  it("creates the layout with the placed seats in the API's shape", async () => {
    const fetcher = stubFetch();
    renderSection();
    startBlank();

    fireEvent.click(cell(1, 1));
    fireEvent.click(cell(1, 3));
    fireEvent.click(cell(2, 3));
    fireEvent.change(screen.getByLabelText("Seat label"), { target: { value: "Rear" } });
    fireEvent.click(screen.getByRole("button", { name: "Create layout" }));

    await vi.waitFor(() => expect(fetcher).toHaveBeenCalledTimes(1));
    expect(fetcher).toHaveBeenCalledWith("/api/v1/admin/seat-layouts", expect.anything());
    expect(sentBody(fetcher)).toEqual({
      method: "POST",
      body: {
        name: "Shuttle",
        seats: [
          { label: "A1", rowNumber: 1, columnNumber: 1 },
          { label: "A2", rowNumber: 1, columnNumber: 3 },
          { label: "Rear", rowNumber: 2, columnNumber: 3 },
        ],
      },
    });
    await vi.waitFor(() => expect(screen.getByLabelText("Layout name")).toHaveValue(""));
  });

  it("refuses to save a van with no seats in it", () => {
    const fetcher = stubFetch();
    renderSection();
    startBlank();

    fireEvent.click(screen.getByRole("button", { name: "Create layout" }));

    expect(screen.getByRole("alert")).toHaveTextContent("Place at least one seat in the van.");
    expect(fetcher).not.toHaveBeenCalled();
  });

  it("shows the API's validation message inline", async () => {
    stubFetch(() => json({ detail: "Seat labels must be unique." }, 400));
    renderSection();
    startBlank();
    fireEvent.click(cell(1, 1));

    fireEvent.click(screen.getByRole("button", { name: "Create layout" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Seat labels must be unique.");
    expect(screen.getByLabelText("Layout name")).toHaveValue("Shuttle");
  });

  it("lists saved layouts with a van thumbnail instead of seat labels", () => {
    renderSection([hiace]);

    const item = within(screen.getByRole("list", { name: "Saved layouts" })).getByRole(
      "listitem",
    );
    expect(item).toHaveTextContent("Hiace");
    expect(item).toHaveTextContent("3 seats");
    expect(item).not.toHaveTextContent("A1");
    expect(item.querySelectorAll("svg")).toHaveLength(1);
  });

  it("loads an existing layout into the builder and saves it back unchanged", async () => {
    const fetcher = stubFetch(() => json(hiace));
    renderSection([hiace]);

    fireEvent.click(screen.getByRole("button", { name: "Edit Hiace" }));

    expect(screen.getByRole("heading", { name: "Edit Hiace" })).toBeInTheDocument();
    expect(screen.getByLabelText("Layout name")).toHaveValue("Hiace");
    expect(screen.getByLabelText("Rows")).toHaveValue(2);
    expect(screen.getByLabelText("Columns")).toHaveValue(3);
    expect(cell(1, 3)).toHaveAccessibleName("Row 1, column 3, seat Jump");
    expect(cell(1, 2)).toHaveAttribute("aria-pressed", "false");

    fireEvent.click(screen.getByRole("button", { name: "Save layout" }));

    await vi.waitFor(() => expect(fetcher).toHaveBeenCalledTimes(1));
    expect(fetcher).toHaveBeenCalledWith(
      "/api/v1/admin/seat-layouts/layout-1",
      expect.anything(),
    );
    expect(sentBody(fetcher)).toEqual({
      method: "PUT",
      body: { name: "Hiace", seats: hiace.seats },
    });
    expect(await screen.findByRole("heading", { name: "New seat layout" })).toBeInTheDocument();
  });

  it("drops the edit without saving when it is cancelled", () => {
    const fetcher = stubFetch();
    renderSection([hiace]);

    fireEvent.click(screen.getByRole("button", { name: "Edit Hiace" }));
    fireEvent.click(screen.getByRole("button", { name: "Cancel" }));

    expect(screen.getByRole("heading", { name: "New seat layout" })).toBeInTheDocument();
    expect(fetcher).not.toHaveBeenCalled();
  });
});
