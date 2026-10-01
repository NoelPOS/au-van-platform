import type { BookingStatus, WaitlistStatus } from "../types/booking";
import type { RouteStatus, TripStatus, VehicleStatus } from "../types/inventory";
import type { PaymentProofStatus } from "../types/payments";

type Status =
  | BookingStatus
  | WaitlistStatus
  | PaymentProofStatus
  | RouteStatus
  | TripStatus
  | VehicleStatus;

const labels: Record<Status, string> = {
  ACTIVE: "Active",
  INACTIVE: "Inactive",
  CANCELLED: "Cancelled",
  PENDING_PAYMENT: "Awaiting payment",
  PAYMENT_UNDER_REVIEW: "In review",
  PAYMENT_REJECTED: "Sent back",
  CONFIRMED: "Confirmed",
  SUBMITTED: "Submitted",
  APPROVED: "Approved",
  REJECTED: "Sent back",
  WAITING: "Waiting",
  PROMOTED: "Seat offered",
  FULFILLED: "Booked",
  WITHDRAWN: "Withdrawn",
  EXPIRED: "Expired",
};

export function statusLabel(value: string): string {
  if (value in labels) return labels[value as Status];
  const words = value.toLowerCase().replaceAll("_", " ");
  return words.charAt(0).toUpperCase() + words.slice(1);
}
