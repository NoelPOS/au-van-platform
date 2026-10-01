import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { useState } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Select } from "./Select";

const options = [
  { value: "r1", label: "AU → Mega Bangna", detail: "70.00 THB · 30 min" },
  { value: "r2", label: "AU → Siam", detail: "90.00 THB · 55 min" },
  { value: "r3", label: "Bangna → AU" },
  { value: "r4", label: "Suvarnabhumi → AU" },
];

function Form({ onSubmit }: { onSubmit: (data: FormData) => void }) {
  const [value, setValue] = useState("r1");
  return (
    <form
      onSubmit={(event) => {
        event.preventDefault();
        onSubmit(new FormData(event.currentTarget));
      }}
    >
      <Select
        label="Route"
        name="routeId"
        onChange={setValue}
        options={options}
        value={value}
      />
      <p>Outside</p>
      <button type="submit">Save</button>
    </form>
  );
}

function setup() {
  const onSubmit = vi.fn<(data: FormData) => void>();
  render(<Form onSubmit={onSubmit} />);
  const trigger = screen.getByRole("button", { name: /^Route/ });
  return { onSubmit, trigger };
}

function listbox() {
  return screen.getByRole("listbox", { name: "Route" });
}

function active() {
  const id = listbox().getAttribute("aria-activedescendant");
  return document.getElementById(id ?? "")?.textContent;
}

function press(key: string) {
  fireEvent.keyDown(document.activeElement!, { key });
}

describe("Select", () => {
  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it("opens a listbox of rich options from its labelled trigger", () => {
    const { trigger } = setup();

    expect(trigger).toHaveAttribute("aria-haspopup", "listbox");
    expect(trigger).toHaveAttribute("aria-expanded", "false");
    expect(trigger).toHaveAccessibleName("Route AU → Mega Bangna");
    fireEvent.click(trigger);

    expect(trigger).toHaveAttribute("aria-expanded", "true");
    expect(listbox()).toHaveFocus();
    expect(
      screen.getByRole("option", { name: /AU → Siam/ }),
    ).toHaveTextContent("90.00 THB · 55 min");
    expect(screen.getByRole("option", { selected: true })).toHaveTextContent(
      "AU → Mega Bangna",
    );
  });

  it("moves with the arrow keys, Home and End, and picks with Enter", () => {
    const { trigger } = setup();

    trigger.focus();
    press("ArrowDown");
    expect(active()).toMatch(/^AU → Mega Bangna/);
    press("ArrowDown");
    press("ArrowDown");
    expect(active()).toBe("Bangna → AU");
    press("End");
    press("ArrowDown");
    expect(active()).toBe("Suvarnabhumi → AU");
    press("Home");
    press("ArrowUp");
    expect(active()).toMatch(/^AU → Mega Bangna/);
    press("ArrowDown");
    press("Enter");

    expect(screen.queryByRole("listbox")).toBeNull();
    expect(trigger).toHaveFocus();
    expect(trigger).toHaveTextContent("AU → Siam");
  });

  it("jumps to an option by typing the start of its label and picks it with Space", () => {
    const { trigger } = setup();

    vi.useFakeTimers({ toFake: ["Date"] });
    fireEvent.click(trigger);
    press("s");
    expect(active()).toBe("Suvarnabhumi → AU");
    vi.advanceTimersByTime(1000);
    press("b");
    press("a");
    expect(active()).toBe("Bangna → AU");
    vi.advanceTimersByTime(1000);
    press("a");
    expect(active()).toMatch(/^AU → Mega Bangna/);
    vi.advanceTimersByTime(1000);
    press("a");
    expect(active()).toMatch(/^AU → Siam/);
    press(" ");

    expect(trigger).toHaveTextContent("AU → Siam");
  });

  it("closes on Escape without choosing, and keeps the Escape from the dialog", () => {
    const { trigger } = setup();

    fireEvent.click(trigger);
    press("ArrowDown");
    const escape = fireEvent.keyDown(listbox(), { key: "Escape" });

    expect(escape).toBe(false);
    expect(screen.queryByRole("listbox")).toBeNull();
    expect(trigger).toHaveFocus();
    expect(trigger).toHaveTextContent("AU → Mega Bangna");
  });

  it("closes when the pointer goes down outside it, but not inside it", () => {
    const { trigger } = setup();

    fireEvent.click(trigger);
    fireEvent.pointerDown(screen.getByRole("option", { name: /AU → Siam/ }));
    expect(listbox()).toBeInTheDocument();
    fireEvent.pointerDown(screen.getByText("Outside"));

    expect(screen.queryByRole("listbox")).toBeNull();
    expect(trigger).toHaveAttribute("aria-expanded", "false");
  });

  it("submits the chosen value under its name", () => {
    const { onSubmit, trigger } = setup();

    fireEvent.click(trigger);
    fireEvent.click(screen.getByRole("option", { name: "Bangna → AU" }));
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(onSubmit.mock.calls[0][0].get("routeId")).toBe("r3");
  });
});
