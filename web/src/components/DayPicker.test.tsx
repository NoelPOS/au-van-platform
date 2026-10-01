import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { useState } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { freezeBangkokTime } from "../test/tripFixtures";
import { DayPicker } from "./DayPicker";

function Picker({ multiple }: { multiple: boolean }) {
  const [days, setDays] = useState<string[]>([]);
  return (
    <>
      <DayPicker multiple={multiple} onChange={setDays} selected={days} />
      <output>{days.join(",")}</output>
    </>
  );
}

function chips(name: string) {
  return within(screen.getByRole("group", { name }));
}

describe("DayPicker", () => {
  beforeEach(() => freezeBangkokTime("2026-01-30T23:30"));

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it("offers the next fourteen Bangkok days as chips and toggles several", () => {
    render(<Picker multiple />);

    const labels = chips("Days")
      .getAllByRole("button", { pressed: false })
      .map((chip) => chip.textContent);
    expect(labels).toHaveLength(14);
    expect(labels.slice(0, 3)).toEqual(["Today", "Tomorrow", "Sun 1"]);
    expect(labels.at(-1)).toBe("Thu 12");

    fireEvent.click(chips("Days").getByRole("button", { name: "Sun 1" }));
    fireEvent.click(chips("Days").getByRole("button", { name: "Today" }));
    fireEvent.click(chips("Days").getByRole("button", { name: "Tomorrow" }));
    fireEvent.click(chips("Days").getByRole("button", { name: "Today" }));

    expect(screen.getByRole("status")).toHaveTextContent("2026-01-31,2026-02-01");
    expect(screen.getByText("2 days chosen")).toBeInTheDocument();
  });

  it("picks a later date from a Monday-first calendar that refuses past days", () => {
    render(<Picker multiple />);

    fireEvent.click(screen.getByRole("button", { name: "Later date" }));
    expect(screen.getByText("January 2026")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Thursday 29 January" }),
    ).toBeDisabled();
    expect(screen.getByRole("button", { name: "Previous month" })).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "Next month" }));
    fireEvent.click(screen.getByRole("button", { name: "Next month" }));

    expect(screen.getByText("March 2026")).toBeInTheDocument();
    const firstDay = screen.getByRole("button", { name: "Sunday 1 March" });
    const cells = Array.from(firstDay.parentElement!.children);
    expect(cells.slice(0, 7).map((cell) => cell.textContent).join("")).toBe(
      "MTWTFSS",
    );
    expect(cells.indexOf(firstDay) % 7).toBe(6);
    fireEvent.click(screen.getByRole("button", { name: "Tuesday 17 March" }));

    expect(screen.getByRole("status")).toHaveTextContent("2026-03-17");
    expect(
      chips("Days").getByRole("button", { name: "Tue 17 Mar" }),
    ).toHaveAttribute("aria-pressed", "true");
  });

  it("closes the calendar on Escape without letting the drawer see it", () => {
    render(<Picker multiple={false} />);

    fireEvent.click(screen.getByRole("button", { name: "Later date" }));
    const escape = fireEvent.keyDown(
      screen.getByRole("button", { name: "Next month" }),
      { key: "Escape" },
    );

    expect(escape).toBe(false);
    expect(screen.queryByText("January 2026")).toBeNull();
    expect(screen.getByRole("button", { name: "Later date" })).toHaveFocus();
  });

  it("keeps a single day and closes the calendar once one is picked", () => {
    render(<Picker multiple={false} />);

    fireEvent.click(chips("Day").getByRole("button", { name: "Today" }));
    fireEvent.click(screen.getByRole("button", { name: "Later date" }));
    fireEvent.click(screen.getByRole("button", { name: "Saturday 31 January" }));

    expect(screen.queryByText("January 2026")).toBeNull();
    expect(screen.getByRole("status")).toHaveTextContent("2026-01-31");
  });
});
