import { cleanup, fireEvent, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  trip,
  fullTrip,
  waitlistEntry,
  json,
} from "../test/bookingFixtures";
import { stubApi, renderPage } from "../test/renderStudentBookingPage";

describe("StudentBookingPage waitlist", () => {
  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("offers the waitlist only on a trip with no seats left", async () => {
    stubApi({ trips: () => json([trip, fullTrip]) });

    renderPage();

    expect(
      await screen.findByRole("button", { name: "Join waitlist" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /3 of 4 seats free/ }),
    ).toBeEnabled();
    expect(screen.getAllByRole("button", { name: "Join waitlist" })).toHaveLength(
      1,
    );
  });

  it("joins the waitlist and reports the place the API gave back", async () => {
    const fetcher = stubApi({ trips: () => json([fullTrip]) });

    renderPage();
    fireEvent.click(await screen.findByRole("button", { name: "Join waitlist" }));

    expect(
      await screen.findByText(
        "You are number 2 on the waitlist for AU → Future Park.",
      ),
    ).toBeInTheDocument();
    const joined = fetcher.mock.calls.filter(
      ([url, init]) =>
        url === "/api/v1/waitlist" &&
        (init as RequestInit | undefined)?.method === "POST",
    );
    expect(joined).toHaveLength(1);
    expect(JSON.parse(String((joined[0][1] as RequestInit).body))).toEqual({
      tripId: "trip-full",
      seatsWanted: 1,
    });
  });

  it("shows the student's place and a way out once they are queued", async () => {
    stubApi({
      trips: () => json([fullTrip]),
      waitlist: () => json([waitlistEntry]),
    });

    renderPage();

    expect(
      await screen.findByText("You are number 2 on the waitlist."),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Join waitlist" }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Leave waitlist" }),
    ).toBeInTheDocument();
  });

  it("leaves the waitlist through POST /leave, never DELETE", async () => {
    let queued = true;
    const fetcher = stubApi({
      trips: () => json([fullTrip]),
      waitlist: () => json(queued ? [waitlistEntry] : []),
      leaveWaitlist: () => {
        queued = false;
        return new Response(null, { status: 204 });
      },
    });

    renderPage();
    fireEvent.click(
      await screen.findByRole("button", { name: "Leave waitlist" }),
    );

    expect(
      await screen.findByText("You have left the waitlist."),
    ).toBeInTheDocument();
    expect(
      await screen.findByRole("button", { name: "Join waitlist" }),
    ).toBeInTheDocument();
    expect(
      fetcher.mock.calls.map(([url, init]) => [
        url,
        (init as RequestInit | undefined)?.method ?? "GET",
      ]),
    ).toContainEqual(["/api/v1/waitlist/waitlist-1/leave", "POST"]);
  });

  it("sends the student back to booking when the trip has a seat again", async () => {
    stubApi({
      trips: () => json([fullTrip]),
      joinWaitlist: () =>
        json(
          {
            detail:
              "This trip still has seats. Book one instead of joining the waitlist.",
            code: "waitlist_not_needed",
          },
          409,
        ),
    });

    renderPage();
    fireEvent.click(await screen.findByRole("button", { name: "Join waitlist" }));

    expect(
      await screen.findByText("That trip has a seat free again. Book it instead."),
    ).toBeInTheDocument();
  });

  it("reports a failed join without pretending the student is queued", async () => {
    stubApi({
      trips: () => json([fullTrip]),
      joinWaitlist: () =>
        json({ detail: "The waitlist is unavailable." }, 500),
    });

    renderPage();
    fireEvent.click(await screen.findByRole("button", { name: "Join waitlist" }));

    expect(
      await screen.findByText("The waitlist is unavailable."),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Join waitlist" }),
    ).toBeInTheDocument();
  });
});
