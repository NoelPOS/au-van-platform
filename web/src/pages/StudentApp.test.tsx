import { cleanup, fireEvent, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { trip, booking, json } from "../test/bookingFixtures";
import {
  stubApi,
  renderPage,
  selectSeatA1,
  holdFailsWith,
  reachPassengerDetails,
} from "../test/renderStudentApp";

describe("Student app", () => {
  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("books a seat from trip selection through to a confirmation reference", async () => {
    const fetcher = stubApi();

    renderPage();
    await reachPassengerDetails();
    fireEvent.click(screen.getByRole("button", { name: "Confirm booking" }));

    expect(
      await screen.findByRole("heading", { name: "Seats reserved" }),
    ).toBeInTheDocument();
    expect(screen.getByText("AUV-260921-7KQ2M4XR")).toBeInTheDocument();
    const created = fetcher.mock.calls.filter(
      ([url, init]) =>
        url === "/api/v1/bookings" &&
        (init as RequestInit | undefined)?.method === "POST",
    );
    expect(created).toHaveLength(1);
    expect(JSON.parse(String((created[0][1] as RequestInit).body))).toEqual({
      holdId: "hold-1",
      passengerName: "Somchai P.",
      passengerPhone: "0812345678",
    });
  });

  it("lands on the new ticket with its seats and fare", async () => {
    stubApi();

    renderPage();
    await reachPassengerDetails();
    fireEvent.click(screen.getByRole("button", { name: "Confirm booking" }));

    const pass = await screen.findByRole("article", {
      name: "Boarding pass AUV-260921-7KQ2M4XR",
    });
    expect(pass).toHaveTextContent("A1");
    expect(pass).toHaveTextContent("฿35");
    expect(pass).toHaveTextContent("Awaiting payment");
  });

  it("trims the passenger name and phone before sending them", async () => {
    const fetcher = stubApi();

    renderPage();
    await selectSeatA1();
    fireEvent.click(screen.getByRole("button", { name: "Hold seats" }));
    await screen.findByRole("button", { name: "Confirm booking" });
    fireEvent.change(screen.getByLabelText("Full name"), {
      target: { value: "  Somchai P.  " },
    });
    fireEvent.change(screen.getByLabelText("Phone number"), {
      target: { value: " 0812345678 " },
    });
    fireEvent.click(screen.getByRole("button", { name: "Confirm booking" }));

    expect(
      await screen.findByRole("heading", { name: "Seats reserved" }),
    ).toBeInTheDocument();
    const created = fetcher.mock.calls.filter(
      ([url, init]) =>
        url === "/api/v1/bookings" &&
        (init as RequestInit | undefined)?.method === "POST",
    );
    expect(JSON.parse(String((created[0][1] as RequestInit).body))).toEqual({
      holdId: "hold-1",
      passengerName: "Somchai P.",
      passengerPhone: "0812345678",
    });
  });

  it("goes back to the departures from a new ticket", async () => {
    stubApi();

    renderPage();
    await reachPassengerDetails();
    fireEvent.click(screen.getByRole("button", { name: "Confirm booking" }));
    await screen.findByRole("heading", { name: "Seats reserved" });

    fireEvent.click(screen.getByRole("link", { name: "Departures" }));

    expect(
      await screen.findByRole("button", { name: /Mega Bangna/ }),
    ).toBeInTheDocument();
    expect(screen.queryByText("Seats reserved")).not.toBeInTheDocument();
  });

  it("shows a loading state while the trips are being fetched", () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() => new Promise<Response>(() => {})),
    );

    renderPage();

    expect(screen.getByText("Loading departures…")).toBeInTheDocument();
  });

  it("shows an empty state when no trips are scheduled", async () => {
    stubApi({ trips: () => json([]) });

    renderPage();

    expect(await screen.findByText("Nothing scheduled yet")).toBeInTheDocument();
  });

  it("asks the student to sign in again when the access token has expired", async () => {
    stubApi({ trips: () => new Response(null, { status: 401 }) });

    renderPage();

    expect(await screen.findByText("Sign in again")).toBeInTheDocument();
    expect(
      screen.getByText(
        "Your sign-in has expired. Close and reopen AU-Van from LINE to carry on.",
      ),
    ).toBeInTheDocument();
  });

  it("asks the student to sign in again when the hold is refused as expired", async () => {
    await holdFailsWith(null, 401);

    expect(await screen.findByText("Sign in again")).toBeInTheDocument();
    expect(screen.queryByText("Catch the next van")).not.toBeInTheDocument();
  });

  it("offers a retry when the trip list cannot be loaded", async () => {
    let attempts = 0;
    stubApi({
      trips: () => {
        attempts += 1;
        return attempts === 1
          ? json({ detail: "The trips could not be loaded." }, 500)
          : json([trip]);
      },
    });

    renderPage();

    expect(
      await screen.findByText("The trips could not be loaded."),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));

    expect(
      await screen.findByRole("button", { name: /Mega Bangna/ }),
    ).toBeInTheDocument();
  });

  it("lists existing bookings with the reference and the trip they belong to", async () => {
    stubApi({ bookings: () => json([booking]), trips: () => json([]) });

    renderPage("/tickets");

    const ticket = await screen.findByRole("link", {
      name: /AUV-260921-7KQ2M4XR/,
    });
    expect(ticket).toHaveTextContent("Mega Bangna");
    expect(ticket).toHaveTextContent("Seat A1 · AUV-260921-7KQ2M4XR");
    expect(screen.getByText("Awaiting payment")).toHaveClass("text-warning");
  });
});
