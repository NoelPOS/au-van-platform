import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { Logo } from "./Logo";

describe("Logo", () => {
  afterEach(cleanup);

  it("names itself by the wordmark and hides the mark from assistive tech", () => {
    const { container } = render(
      <h1>
        <Logo />
      </h1>,
    );

    expect(screen.getByRole("heading", { name: "AU·Van" })).toBeInTheDocument();
    expect(container.querySelector("svg")).toHaveAttribute("aria-hidden", "true");
  });
});
