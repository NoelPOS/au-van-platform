import { cleanup, fireEvent, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { booking, json } from "../test/bookingFixtures";
import {
  stubApi,
  renderPage,
  reachPassengerDetails,
} from "../test/renderStudentBookingPage";

describe("StudentBookingPage", () => {
  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("reuses one idempotency key across a retry and mints a new one when the form changes", async () => {
    const keys: string[] = [];
    stubApi({
      createBooking: (init) => {
        keys.push(new Headers(init.headers).get("Idempotency-Key") ?? "");
        return json({ detail: "The service is unavailable." }, 503);
      },
    });

    renderPage();
    await reachPassengerDetails();
    fireEvent.click(screen.getByRole("button", { name: "Confirm booking" }));
    expect(
      await screen.findByText("The service is unavailable."),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Confirm booking" }));
    await vi.waitFor(() => expect(keys).toHaveLength(2));
    expect(keys[0]).toBe(keys[1]);
    expect(keys[0]).not.toBe("");

    fireEvent.input(screen.getByLabelText("Phone number"), {
      target: { value: "0899999999" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Confirm booking" }));
    await vi.waitFor(() => expect(keys).toHaveLength(3));
    expect(keys[2]).not.toBe(keys[1]);
  });

  it("mints a new idempotency key once the server has rejected the old one", async () => {
    const keys: string[] = [];
    stubApi({
      createBooking: (init) => {
        keys.push(new Headers(init.headers).get("Idempotency-Key") ?? "");
        return keys.length === 1
          ? json(
              {
                detail: "This idempotency key was used with different content.",
                code: "idempotency_key_reused",
              },
              409,
            )
          : json(booking, 201);
      },
    });

    renderPage();
    await reachPassengerDetails();
    fireEvent.click(screen.getByRole("button", { name: "Confirm booking" }));
    expect(
      await screen.findByText(
        "That booking attempt could not be completed. Try again.",
      ),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Confirm booking" }));
    expect(await screen.findByText("AUV-260921-7KQ2M4XR")).toBeInTheDocument();
    expect(keys).toHaveLength(2);
    expect(keys[1]).not.toBe(keys[0]);
    expect(keys[1]).not.toBe("");
  });

  it("mints a new idempotency key for a booking made against a new hold", async () => {
    const keys: string[] = [];
    stubApi({
      createBooking: (init) => {
        keys.push(new Headers(init.headers).get("Idempotency-Key") ?? "");
        return keys.length === 1
          ? json({ detail: "The service is unavailable." }, 503)
          : json(booking, 201);
      },
    });

    renderPage();
    await reachPassengerDetails();
    fireEvent.click(screen.getByRole("button", { name: "Confirm booking" }));
    expect(
      await screen.findByText("The service is unavailable."),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Change seats" }));
    fireEvent.click(
      await screen.findByRole("button", { name: "Seat A1, available" }),
    );
    fireEvent.click(screen.getByRole("button", { name: "Hold these seats" }));
    await screen.findByRole("button", { name: "Confirm booking" });
    fireEvent.change(screen.getByLabelText("Full name"), {
      target: { value: "Somchai P." },
    });
    fireEvent.change(screen.getByLabelText("Phone number"), {
      target: { value: "0812345678" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Confirm booking" }));

    expect(await screen.findByText("AUV-260921-7KQ2M4XR")).toBeInTheDocument();
    expect(keys).toHaveLength(2);
    expect(keys[1]).not.toBe(keys[0]);
  });

  it("points an already-used hold at my bookings rather than the seat map", async () => {
    let bookingsRequested = 0;
    stubApi({
      bookings: () => {
        bookingsRequested += 1;
        return json(bookingsRequested === 1 ? [] : [booking]);
      },
      createBooking: () =>
        json(
          { detail: "This hold is already booked.", code: "hold_already_used" },
          409,
        ),
    });

    renderPage();
    await reachPassengerDetails();
    fireEvent.click(screen.getByRole("button", { name: "Confirm booking" }));

    expect(
      await screen.findByText(
        "Those seats are already booked. If that was you, the booking is in My bookings below.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("My bookings")).toBeInTheDocument();
    expect(
      await screen.findByText("AUV-260921-7KQ2M4XR"),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("group", { name: "Seat map" }),
    ).not.toBeInTheDocument();
  });

  it("keeps the student on the form when the passenger details are rejected", async () => {
    stubApi({
      createBooking: () => json({ detail: "Invalid request content." }, 400),
    });

    renderPage();
    await reachPassengerDetails();
    fireEvent.click(screen.getByRole("button", { name: "Confirm booking" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Check the passenger name and phone number, then try again.",
    );
    expect(
      screen.getByRole("button", { name: "Confirm booking" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("timer")).toBeInTheDocument();
  });
});
