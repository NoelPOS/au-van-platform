import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { useState } from "react";
import { afterEach, describe, expect, it } from "vitest";
import { SegmentedControl } from "./SegmentedControl";

type Status = "ACTIVE" | "INACTIVE";

function Status({ hideLabel }: { hideLabel?: boolean }) {
  const [value, setValue] = useState<Status>("ACTIVE");
  return (
    <>
      <SegmentedControl
        hideLabel={hideLabel}
        label="Status"
        onChange={setValue}
        options={[
          { value: "ACTIVE", label: "Active" },
          { value: "INACTIVE", label: "Inactive" },
        ]}
        value={value}
      />
      <output>{value}</output>
    </>
  );
}

describe("SegmentedControl", () => {
  afterEach(cleanup);

  it("is a labelled radio group with exactly one choice checked", () => {
    render(<Status />);

    const group = within(screen.getByRole("radiogroup", { name: "Status" }));
    expect(group.getByRole("radio", { name: "Active" })).toBeChecked();
    expect(group.getByRole("radio", { name: "Inactive" })).not.toBeChecked();

    fireEvent.click(group.getByRole("radio", { name: "Inactive" }));

    expect(group.getByRole("radio", { name: "Inactive" })).toBeChecked();
    expect(group.getByRole("radio", { name: "Active" })).not.toBeChecked();
    expect(screen.getByRole("status")).toHaveTextContent("INACTIVE");
  });

  it("keeps its name for assistive technology when the label is hidden", () => {
    render(<Status hideLabel />);

    expect(screen.getByText("Status")).toHaveClass("sr-only");
    expect(screen.getByRole("radiogroup", { name: "Status" })).toBeInTheDocument();
  });
});
