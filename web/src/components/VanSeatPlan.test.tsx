import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { VanSeatPlan } from "./VanSeatPlan";

function renderPlan(label?: string) {
  return render(
    <VanSeatPlan
      columns={3}
      label={label}
      renderCell={(row, column) =>
        row === 2 && column === 2 ? null : <span>{`${row}-${column}`}</span>
      }
      rows={2}
    />,
  );
}

const position = (text: string) => screen.getByText(text).parentElement!.style;

describe("VanSeatPlan", () => {
  afterEach(cleanup);

  it("lays cells out front to back and left to right, leaving out empty ones", () => {
    renderPlan("Seat plan");

    expect(screen.getByRole("group", { name: "Seat plan" })).toBeInTheDocument();
    expect(parseFloat(position("1-1").left)).toBeLessThan(parseFloat(position("1-2").left));
    expect(parseFloat(position("1-1").top)).toBeLessThan(parseFloat(position("2-1").top));
    expect(screen.queryByText("2-2")).not.toBeInTheDocument();
  });

  it("draws the driver on the right and the sliding door on the left", () => {
    const { container } = renderPlan("Seat plan");
    const width = Number(container.querySelector("svg")!.getAttribute("viewBox")!.split(" ")[2]);
    const driver = Number(screen.getByText("Driver").getAttribute("x"));
    const door = container.querySelector(".fill-accent")!;

    expect(driver).toBeGreaterThan(width / 2);
    expect(Number(door.getAttribute("x"))).toBeLessThan(width / 2);
  });

  it("is hidden from assistive technology when it has no label", () => {
    const { container } = renderPlan();

    expect(container.firstElementChild).toHaveAttribute("aria-hidden", "true");
    expect(screen.queryByRole("group")).not.toBeInTheDocument();
  });
});
