import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { StatusBadge } from "./StatusBadge";

describe("StatusBadge", () => {
  afterEach(cleanup);

  it.each([
    ["APPROVED", "Approved", "text-success"],
    ["FULFILLED", "Booked", "text-success"],
    ["SUBMITTED", "Submitted", "text-warning"],
    ["WAITING", "Waiting", "text-warning"],
    ["PROMOTED", "Seat offered", "text-warning"],
    ["WITHDRAWN", "Withdrawn", "text-danger"],
  ])("shows %s as %s in its own tone", (value, label, tone) => {
    render(<StatusBadge value={value} />);

    expect(screen.getByText(label)).toHaveClass(tone);
  });
});
