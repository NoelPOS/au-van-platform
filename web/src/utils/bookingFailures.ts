import { ApiError } from "../services/bookingApi";
import { messageOf } from "./errors";

export type BookingFailure =
  | { action: "leave"; message: string; to?: string }
  | { action: "chooseSeats"; message: string }
  | { action: "notice"; message: string; newKey?: boolean };

const tripGone = "That trip is no longer available. Choose another.";
const tripDeparted = "That trip has already departed. Choose another.";

function refusedByRules(error: ApiError): BookingFailure | null {
  const destinations: Record<string, string> = {
    booking_closed: "/",
    booking_cooldown: "/",
    already_booked_on_trip: "/tickets",
    unpaid_booking_exists: error.bookingId ? `/tickets/${error.bookingId}` : "/tickets",
  };
  const to = destinations[error.code ?? ""];
  return to ? { action: "leave", message: error.message, to } : null;
}

export function holdFailure(error: unknown): BookingFailure {
  if (error instanceof ApiError) {
    const refusal = refusedByRules(error);
    if (refusal) return refusal;
    if (error.code === "trip_departed") return { action: "leave", message: tripDeparted };
    if (error.code === "trip_not_available" || error.status === 404)
      return { action: "leave", message: tripGone };
  }
  return { action: "notice", message: messageOf(error) };
}

export function bookingFailure(error: unknown): BookingFailure {
  if (!(error instanceof ApiError)) return { action: "notice", message: messageOf(error) };
  const refusal = refusedByRules(error);
  if (refusal) return refusal;
  switch (error.code) {
    case "hold_expired":
      return {
        action: "chooseSeats",
        message: "Your seat hold expired before the booking was confirmed. Choose your seats again.",
      };
    case "hold_not_found":
      return {
        action: "chooseSeats",
        message: "That seat hold is no longer available. Choose your seats again.",
      };
    case "hold_already_used":
      // A lost 201 can hide this booking, so land on the list that shows it.
      return {
        action: "leave",
        message: "Those seats are already booked. If that was you, the booking is below.",
        to: "/tickets",
      };
    case "trip_not_available":
      return { action: "leave", message: tripGone };
    case "trip_departed":
      return { action: "leave", message: tripDeparted };
    case "idempotency_key_reused":
      return {
        action: "notice",
        message: "That booking attempt could not be completed. Try again.",
        newKey: true,
      };
  }
  if (error.status === 400)
    return {
      action: "notice",
      message: "Check the passenger name and phone number, then try again.",
    };
  return { action: "notice", message: messageOf(error) };
}
