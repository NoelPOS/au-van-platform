import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it } from "vitest";
import { Input } from "./Input";

describe("Input", () => {
  afterEach(cleanup);

  it("replaces a pre-filled number instead of appending to it", async () => {
    const user = userEvent.setup();
    render(<Input defaultValue={30} label="Duration" type="number" />);

    await user.click(screen.getByLabelText("Duration"));
    await user.keyboard("45");

    expect(screen.getByLabelText("Duration")).toHaveValue(45);
  });

  it("stops the click's mouseup from clearing that selection", () => {
    render(<Input defaultValue={30} label="Duration" type="number" />);

    expect(fireEvent.mouseUp(screen.getByLabelText("Duration"))).toBe(false);
  });

  it("keeps the cursor behaviour of a text field", async () => {
    const user = userEvent.setup();
    render(<Input defaultValue="AU" label="Origin" />);

    await user.click(screen.getByLabelText("Origin"));
    await user.keyboard(" Campus");

    expect(screen.getByLabelText("Origin")).toHaveValue("AU Campus");
  });
});
