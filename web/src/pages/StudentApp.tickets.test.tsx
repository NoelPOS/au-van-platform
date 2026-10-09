import { cleanup, fireEvent, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { booking, json } from "../test/bookingFixtures";
import { renderPage, stubApi } from "../test/renderStudentApp";

function ticket(index: number, departureAt: string, status = "CONFIRMED") {
  return {
    ...booking,
    id: `booking-${index}`,
    reference: `AUV-${String(index).padStart(3, "0")}`,
    status,
    trip: { ...booking.trip, departureAt },
  };
}

describe("Student tickets", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("splits upcoming from past and puts the soonest trip first", async () => {
    stubApi({
      bookings: () =>
        json([
          ticket(1, "2030-10-05T01:00:00Z"),
          ticket(2, "2030-10-02T01:00:00Z"),
          ticket(3, "2020-01-01T01:00:00Z"),
          ticket(4, "2030-10-09T01:00:00Z", "CANCELLED"),
        ]),
    });

    renderPage("/tickets");

    const upcoming = await screen.findAllByRole("link", { name: /AUV-00/ });
    expect(upcoming.map((link) => link.getAttribute("href"))).toEqual([
      "/tickets/booking-2",
      "/tickets/booking-1",
    ]);

    fireEvent.click(screen.getByRole("radio", { name: "Past" }));

    const past = screen.getAllByRole("link", { name: /AUV-00/ });
    expect(past.map((link) => link.getAttribute("href"))).toEqual([
      "/tickets/booking-4",
      "/tickets/booking-3",
    ]);
  });

  it("pages a long history ten tickets at a time", async () => {
    stubApi({
      bookings: () =>
        json(
          Array.from({ length: 23 }, (_, index) =>
            ticket(index, `2020-01-${String(index + 1).padStart(2, "0")}T01:00:00Z`),
          ),
        ),
    });

    renderPage("/tickets");
    fireEvent.click(await screen.findByRole("radio", { name: "Past" }));

    expect(screen.getAllByRole("link", { name: /AUV-0/ })).toHaveLength(10);
    fireEvent.click(screen.getByRole("button", { name: "Show older tickets" }));
    expect(screen.getAllByRole("link", { name: /AUV-0/ })).toHaveLength(20);
    fireEvent.click(screen.getByRole("button", { name: "Show older tickets" }));
    expect(screen.getAllByRole("link", { name: /AUV-0/ })).toHaveLength(23);
    expect(screen.queryByRole("button", { name: "Show older tickets" })).not.toBeInTheDocument();
  });

  it("offers a way to book when nothing is coming up", async () => {
    stubApi({ bookings: () => json([]) });

    renderPage("/tickets");

    expect(await screen.findByText("No trips coming up")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Find a departure" })).toHaveAttribute("href", "/");
  });
});
