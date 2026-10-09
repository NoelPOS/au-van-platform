# ADR-017: Cancel and Reschedule Trips in One Locked Transaction, and Track Refunds on the Booking

## Status

Accepted

## Context

An administrator could set a trip to `CANCELLED` through the generic trip update, and nothing else happened. Every booking on the trip stayed active, the seats stayed claimed, nobody was told, and a student who had paid had no record that money was owed. Moving a trip's departure was just as silent. Students could also cancel a paid booking minutes before departure.

The repository owner agreed six rules on 2026-10-09 (issue #131):

1. Cancelling a trip needs a reason of 1–300 characters. In one transaction it cancels every active booking, releases their seats, and sends each student a LINE message with the reason. It also closes waiting waitlist entries and tells those students.
2. A `CONFIRMED` booking cancelled by a trip cancellation or by its student owes a refund. So does a `PAYMENT_UNDER_REVIEW` booking on a cancelled trip, because the student may have paid. Administrators list refunds that are due and record each one as refunded, with who, when, and an optional note.
3. Students may cancel until two hours before departure.
4. Changing a trip's departure tells every active passenger the old and new times. A new time inside the booking cutoff is refused.
5. A trip whose departure has passed cannot be updated or cancelled.
6. A cancelled trip cannot be reactivated.

Three accepted decisions bound the design. ADR-001 keeps the modules separate, and `inventory` imports nothing from any other module. ADR-008 requires every booking decision to be made from what a lock returned. ADR-010 requires every message to be an outbox row written in the transaction that caused it.

## Options considered

### Where a trip change that affects passengers lives

- **`inventory.TripService` calls a booking service.** This is the shortest call path. Rejected: `inventory` would import `booking`, which already imports `inventory`, and the cycle is exactly what ADR-001 asks the modules to avoid.
- **`TripService` publishes a Spring event, and `booking` listens in the same transaction.** This keeps the import direction. Rejected: the refusal of a new time inside the booking cutoff would sit in a listener, where a reader of the update would not find it.
- **`booking` owns every trip change that can reach a passenger.** Chosen. `TripChangeService` serves `PUT /api/v1/admin/trips/{id}` and `POST /api/v1/admin/trips/{id}/cancel`. `inventory` keeps listing and creating trips and still imports nothing from `booking`.

### How a booking is kept off a trip that is being cancelled

A student confirming a hold reads the trip as `ACTIVE`. If the cancellation commits before that booking does, the cascade's read of the trip's bookings misses it, and a `PENDING_PAYMENT` booking survives on a cancelled trip. The foreign key does not help: a booking insert takes `FOR KEY SHARE` on the trip, and the status update does not conflict with it. `TripCancellationConcurrencyPostgresTests` forces this interleaving.

- **Re-check the trip after inserting the booking.** Rejected: the re-check reads the same uncommitted state the first check did.
- **`SERIALIZABLE` isolation on both transactions.** Rejected for the reason ADR-016 gave: the loser fails with an error the caller has to retry.
- **A `PESSIMISTIC_WRITE` lock on the `trips` row.** Chosen. `BookingWriter.create` takes it after the hold's claims and before the student's row, so the order is claims, trip, user. Cancellation and update take it first. A booking that wins the lock is then seen by the cascade; a booking that loses it sees `CANCELLED` and is refused. Confirmations on one trip are serialised against each other. A van has a handful of seats, so this costs nothing measurable.

### What the cascade releases

- **Every `seat_claims` row on the trip.** Rejected: an unbooked hold can be locked by a `BookingWriter` that is waiting for the trip lock the cascade holds, so deleting the hold would deadlock.
- **Only claims attached to the trip's bookings, in one bulk delete after one flush.** Chosen. A hold on a cancelled trip can no longer become a booking, and it lapses on its own as ADR-006 designed.

### Where a refund is recorded

- **A `refunds` table.** Rejected: a booking owes at most one refund, of its whole fare, so a second table adds a join and a uniqueness rule and records nothing new.
- **Columns on `bookings`: `refund_status` (`NONE`, `DUE`, `REFUNDED`), `refunded_at`, `refunded_by_user_id`, `refund_note`.** Chosen. A `REFUNDED` entry in `booking_events` puts the refund in the timeline.

### What a reschedule does to reminders and deadlines

- **Leave them.** Rejected: a confirmed passenger would get a "departs in 1 hour" reminder at the old time. An unpaid booking on a trip moved earlier would keep a deadline past the new departure bound that ADR-010 promises.
- **Rebuild them.** Chosen. Unsent reminders are deleted and scheduled again for the new time. They are deleted rather than marked `DEAD` because their `dedupe_key` must be free to be written again. An unpaid booking's deadline is capped at the new departure bound.

## Decision

- `TripChangeService` locks the trip and refuses any change once departure has passed (`trip_departed`) or after the trip is cancelled (`trip_cancelled`), including reactivating it.
- Cancelling reads the trip's active booking ids and locks each booking before deciding anything. Each booking becomes `CANCELLED`, with a `TRIP_CANCELLED` booking event naming the administrator and the reason, and one `TRIP_CANCELLED` outbox row. It owes a refund if it was `CONFIRMED` or `PAYMENT_UNDER_REVIEW`, and its unsent reminders are withdrawn. `WAITING` and `PROMOTED` waitlist entries become `CANCELLED`, and each gets a `TRIP_CANCELLED` outbox row whose aggregate is the entry. The reason is stored on `trips.cancellation_reason`.
- The generic update refuses `status: CANCELLED` (`trip_cancellation_needs_reason`). A new departure must be at least `booking.closes-before-departure` away (`departure_too_soon`). Every active booking gets a `TRIP_RESCHEDULED` event and outbox row with both times. Confirmed bookings have their reminders rebuilt, and unpaid bookings have their deadline capped.
- A student can cancel until `booking.cancellation-closes-before-departure` (two hours) before departure (`cancellation_closed`). Booking responses carry `cancellableUntil`, which is null once cancelled. A `CONFIRMED` booking the student cancels owes a refund.
- `GET /api/v1/admin/refunds` lists bookings with a refund due, oldest first. `POST /api/v1/admin/refunds/{bookingId}/mark-refunded` records the refund, and refuses with `refund_not_due` or `refund_already_recorded`.

## Consequences

- A trip cancellation is all or nothing. `TripCancellationAtomicityTests` makes the second outbox write fail and finds nothing cancelled.
- One LINE message per affected booking or waitlist entry. Each message is an outbox row, so it carries ADR-010's retry key and is delivered once at the far end.
- The four concurrency tests need real PostgreSQL: run against H2, two of them fail. They run in the "Database concurrency checks" job, which is not a required check.
- A student can no longer cancel an unpaid booking inside two hours of departure. A booking made between two hours and ninety minutes before departure can therefore only be paid or left to expire, and that expiry counts towards ADR-016's pause.
- A student who cancels a `PAYMENT_UNDER_REVIEW` booking themselves owes nothing under these rules. The slip stays in the review queue, and approving it is refused, because the booking is no longer under review.
- A short delay to a trip departing within ninety minutes is refused, because the new time is inside the booking cutoff.
- A promotion hold on a cancelled trip is left to lapse rather than released.
