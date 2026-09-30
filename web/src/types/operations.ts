import type { BookingStatus, WaitlistStatus } from "./booking";
import type { TripStatus } from "./inventory";

export type BookingStatusCount = {
  status: BookingStatus;
  count: number;
};

export type WaitlistPlace = {
  entryId: string;
  userId: string;
  displayName: string | null;
  seatsWanted: number;
  status: WaitlistStatus;
  position: number | null;
  joinedAt: string;
  promotionHoldId: string | null;
  promotionExpiresAt: string | null;
};

export type TripOperations = {
  tripId: string;
  origin: string;
  destination: string;
  departureAt: string;
  tripStatus: TripStatus;
  totalSeats: number;
  claimedSeats: number;
  bookingsByStatus: BookingStatusCount[];
  waitlist: WaitlistPlace[];
};

export type DeadLetter = {
  id: string;
  eventType: string;
  aggregateId: string;
  recipientUserId: string;
  attempts: number;
  lastError: string | null;
  createdAt: string;
  processedAt: string | null;
};
