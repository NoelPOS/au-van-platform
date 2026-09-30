import type { BookingStatus, WaitlistStatus } from "./booking";
import type { TripStatus } from "./inventory";

/**
 * What an operator can see. Everything here is read-only: the API offers no
 * way to promote a student, retry a delivery or release a seat from this
 * screen, because each of those already has an owner and a second path into it
 * is a second place the rule can drift.
 */

/** Every status is reported, the empty ones included, so "none" is visible. */
export type BookingStatusCount = {
  status: BookingStatus;
  count: number;
};

/**
 * One student's place. `position` counts only queued entries and is null for
 * one that has ended. `promotionHoldId` and `promotionExpiresAt` are written
 * by the promotion sweep (#69) and are null until it runs.
 */
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

/**
 * A notification the dispatcher gave up on. `aggregateId` is the thing the
 * event was about — a booking today, a waitlist entry once #69 records
 * promotions — and is an identifier to look up rather than a link to follow.
 */
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
