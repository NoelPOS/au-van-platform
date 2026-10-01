import { cleanup, fireEvent, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { trip, booking, json } from "../test/bookingFixtures";
import {
  stubApi,
  renderPage,
  selectSeatA1,
  holdFailsWith,
  reachPassengerDetails,
} from "../test/renderStudentBookingPage";

describe("StudentBookingPage", () => {
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

    expect(await screen.findByText("AUV-260921-7KQ2M4XR")).toBeInTheDocument();
    expect(screen.getByText("Seats reserved")).toBeInTheDocument();
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

  it("lists the new booking in my bookings on the confirmation step", async () => {
    let bookingsRequested = 0;
    stubApi({
      bookings: () => {
        bookingsRequested += 1;
        return json(bookingsRequested === 1 ? [] : [booking]);
      },
    });

    renderPage();
    await reachPassengerDetails();
    fireEvent.click(screen.getByRole("button", { name: "Confirm booking" }));

    expect(await screen.findByText("Seats reserved")).toBeInTheDocument();
    expect(
      await screen.findByText("Seat A1 · 35.00 THB"),
    ).toBeInTheDocument();
  });

  it("trims the passenger name and phone before sending them", async () => {
    const fetcher = stubApi();

    renderPage();
    await selectSeatA1();
    fireEvent.click(screen.getByRole("button", { name: "Hold these seats" }));
    await screen.findByRole("button", { name: "Confirm booking" });
    fireEvent.change(screen.getByLabelText("Full name"), {
      target: { value: "  Somchai P.  " },
    });
    fireEvent.change(screen.getByLabelText("Phone number"), {
      target: { value: " 0812345678 " },
    });
    fireEvent.click(screen.getByRole("button", { name: "Confirm booking" }));

    expect(await screen.findByText("AUV-260921-7KQ2M4XR")).toBeInTheDocument();
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

  it("leaves the confirmation when the student goes back to the trip list", async () => {
    stubApi();

    renderPage();
    await reachPassengerDetails();
    fireEvent.click(screen.getByRole("button", { name: "Confirm booking" }));
    await screen.findByText("Seats reserved");

    fireEvent.click(screen.getByRole("button", { name: "Back to trips" }));

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

    expect(screen.getByText("Loading trips…")).toBeInTheDocument();
    expect(screen.getByText("Loading your bookings…")).toBeInTheDocument();
  });

  it("shows an empty state when no trips are scheduled", async () => {
    stubApi({ trips: () => json([]) });

    renderPage();

    expect(
      await screen.findByText(
        "No trips are scheduled right now. Check back later.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("You have no bookings yet.")).toBeInTheDocument();
  });

  it("asks the student to sign in again when the access token has expired", async () => {
    stubApi({ trips: () => new Response(null, { status: 401 }) });

    renderPage();

    expect(await screen.findByText("Sign in again")).toBeInTheDocument();
    expect(
      screen.getByText(
        "Your sign-in has expired. Close and reopen this page from LINE to continue booking.",
      ),
    ).toBeInTheDocument();
  });

  it("asks the student to sign in again when the hold is refused as expired", async () => {
    await holdFailsWith(null, 401);

    expect(await screen.findByText("Sign in again")).toBeInTheDocument();
    expect(screen.queryByText("Upcoming trips")).not.toBeInTheDocument();
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

    renderPage();

    expect(await screen.findByText("AUV-260921-7KQ2M4XR")).toBeInTheDocument();
    expect(screen.getByText("PENDING_PAYMENT")).toHaveClass("text-warning");
    expect(screen.getByText(/^AU → Mega Bangna · /)).toBeInTheDocument();
    expect(screen.getByText("Seat A1 · 35.00 THB")).toBeInTheDocument();
  });
});
