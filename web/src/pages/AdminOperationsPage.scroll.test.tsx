import { cleanup, fireEvent, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderPage, stubApi } from "../test/operationsFixtures";

function screenWhere(matching: string[]) {
  vi.stubGlobal("matchMedia", (query: string) => ({
    matches: matching.includes(query),
    media: query,
  }));
}

async function pickTheTrip() {
  renderPage();
  fireEvent.click(await screen.findByRole("button", { name: /AU → Mega Bangna/ }));
  return screen.getByRole("region", { name: "Selected trip" });
}

describe("AdminOperationsPage on a narrow screen", () => {
  let scroll: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    stubApi();
    scroll = vi.spyOn(Element.prototype, "scrollIntoView");
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("brings the picked trip into view and moves focus to it", async () => {
    screenWhere([]);

    const detail = await pickTheTrip();

    expect(scroll).toHaveBeenCalledWith({ block: "start", behavior: "smooth" });
    expect(scroll.mock.contexts[0]).toBe(detail);
    expect(detail).toHaveFocus();
  });

  it("jumps rather than glides when the reader asks for less motion", async () => {
    screenWhere(["(prefers-reduced-motion: reduce)"]);

    await pickTheTrip();

    expect(scroll).toHaveBeenCalledWith({ block: "start", behavior: "auto" });
  });

  it("leaves the page and focus alone on a wide screen, where the trip sits beside the list", async () => {
    screenWhere(["(min-width: 1024px)"]);

    const detail = await pickTheTrip();

    expect(scroll).not.toHaveBeenCalled();
    expect(detail).not.toHaveFocus();
  });
});
