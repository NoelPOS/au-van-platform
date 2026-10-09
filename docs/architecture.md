# Architecture

## Chosen starting point

AU-Van will begin as a modular monolith: one React web application and one Spring Boot backend codebase with clearly separated modules. This keeps delivery and debugging simple while preserving boundaries that can later support independent services.

## Logical components

```text
React web application
  - Admin portal
  - Student LIFF experience
        |
        v
Spring Boot API
  - Identity and access
  - Scheduling and seat inventory
  - Booking
  - Payment review
  - Notifications
  - Administration and audit
        |
        +--> PostgreSQL: authoritative transactional data
        +--> Redis: temporary holds, idempotency, cache, rate limits
        +--> Object storage: payment-proof images
        +--> Queue/worker: retries, reminders, expiry, LINE delivery
```

## Consistency model

PostgreSQL is authoritative for confirmed seat inventory, bookings, payment state, and audit records. Seat-related writes must run in database transactions. Redis can improve responsiveness for short-lived holds, but it cannot independently confirm a booking.

## Asynchronous work

Booking and payment changes may generate notifications, reminders, expiry processing, and waitlist promotion. These side effects must be retryable and observable. ADR-010 records how for the first three, and the outbox half of it is implemented. ADR-011 records how for waitlist promotion, and the queue and the promotion sweep are both implemented.

- Every booking or payment transition that owes outbound work writes an `outbox_events` row **in the same transaction as the state change**. The recorder joins the caller's transaction and never opens one of its own, so the work and the record of it cannot diverge.
- A dispatcher claims a due row with one conditional `UPDATE`, sends outside any transaction, and records the outcome in a second one. The claim pushes the row's `next_attempt_at` forward by a lease, so a worker that dies mid-send leaves a row that simply becomes due again — SQS's visibility-timeout model, expressed in one table.
- Retries back off exponentially and stop at `outbox.max-attempts`, leaving the row in a discoverable `DEAD` state with its last error.
- The trigger is an in-process `@Scheduled` poller gated on `outbox.dispatch.enabled`. It is only a trigger: an SQS consumer or a separate worker process can call the same dispatch entry point unchanged, which is what "SQS-compatible" means here. No Redis is involved, and ADR-010 records that as a decision rather than an omission.
- LINE delivery goes through the `LineMessageSender` port, whose production implementation pushes to the LINE Messaging API. Each send carries the outbox row's id as LINE's `X-Line-Retry-Key`, so a retried send is at most one message the student sees. A failure that can never succeed — an unknown recipient, or a student who has not added the official account — goes `DEAD` on its first attempt instead of consuming the retry budget. With the port unconfigured there is no sender and a recorded event is resolved without being delivered, which is how the suite runs with no credential.
- A departure reminder is an outbox row scheduled into the future when a payment is approved, at twenty-four hours and at one hour before departure, deduplicated by a unique `dedupe_key` and withdrawn if the booking is cancelled or expires. One offset already past at approval is not scheduled at all. `docs/data-model.md` has the rules; they are ported from the legacy application, which is the only place a validated timing exists.

Booking expiry and seat release, the other half of ADR-010, are implemented too.

- Every booking carries `payment_deadline_at`, written only by the transitions that own it: `min(now + booking.payment-window, departureAt - booking.departure-cutoff)` at creation, the later of that and `now + booking.resubmit-window` (still capped at the departure bound) on rejection, and cleared while a proof is under review and once the booking is terminal. The two windows are **new product rules** with no equivalent in the legacy application.
- A scheduled sweep, gated on `booking.expiry.enabled` exactly as the dispatcher is, reads candidate **ids**, locks each booking, and decides only from what the lock returned. An expiry cancels the booking, appends an `EXPIRED` history entry with no actor, deletes its `seat_claims`, and records one outbox row — all in one transaction per booking, so a booking that loses its race neither rolls back nor blocks the batch.
- The same sweep prunes `idempotency_keys` past their retention window, which ADR-008 left owing.

A student who cannot book a full trip can now queue for it, and a seat that comes free is offered to whoever has waited longest. ADR-011 records the decision; the queue, the promotion sweep, and the administrator's read-only view of both at `/api/v1/admin/operations` are implemented.

- `waitlist_entries` holds one row per student per trip, ordered by when they joined, with the position derived on every read rather than stored. Joining a trip that still has a free seat is refused, so the queue only ever holds students who could not simply book.
- Promotion is a **scheduled sweep**, not a hook on the places that free a seat. Three of those places run code and a fourth runs none at all — ADR-006 made hold expiry lazy, and an abandoned hold is the commonest way a seat comes free. One sweep covers all four, gated on `booking.waitlist.enabled` exactly as the expiry sweep and the dispatcher are. It reads candidate **ids**, locks each entry, and decides only from what the lock returned, in one transaction per entry.
- A promotion is a **seat hold**, not a booking: `seat_claims` rows under a fresh `hold_id` expiring at `min(now + booking.waitlist.promotion-window, departureAt - booking.departure-cutoff)`. The promoted student then walks the existing hold, confirm, pay and review path with no special case in it, and `seat_claims_trip_seat_unique` decides a race with a direct booker exactly as it does between two students.
- **One chance per promotion.** A student who does nothing before the window runs out ends `EXPIRED`, an outbox row tells them, and the seat goes to the next in line. The same sweep marks an entry `FULFILLED` when it finds the seats it offered have been booked; the booking path itself knows nothing about the waitlist.
- Both `WAITLIST_PROMOTED` and `WAITLIST_PROMOTION_EXPIRED` are recorded in the promoting transaction and carry **no `dedupe_key`** — that column is unique table-wide and is what marks a row as a reminder, so a waitlist notification with one could be killed by an unrelated cancellation. Their `aggregate_id` is a waitlist entry rather than a booking, which is what that column now means.

## Boundaries

- React does not contain booking, payment, or authorization rules.
- Controllers validate and orchestrate only.
- Spring application/domain services own workflows and state transitions.
- Infrastructure adapters isolate PostgreSQL, Redis, S3, SQS, and LINE APIs from core business logic.

## Accepted decisions

- [ADR-001: Start as a Modular Monolith](adr/001-modular-monolith-first.md)
- [ADR-002: Use React and Spring Boot with Explicit Boundaries](adr/002-react-and-spring-boot-boundaries.md)
- [ADR-003: PostgreSQL Is the Booking Authority; Redis Is Supporting Infrastructure](adr/003-postgresql-authority-and-redis-support.md)
- [ADR-004: Use a Cost-Conscious Demo with an AWS-Ready Production Target](adr/004-cost-conscious-demo-and-aws-target.md)
- [ADR-005: Verify LINE Identity Server-Side and Issue Short-Lived AU-Van JWTs](adr/005-line-identity-exchange-and-short-lived-jwt.md)
- [ADR-006: Hold and Book Seats in One `seat_claims` Table with Lazy Expiry](adr/006-seat-claims-single-table-and-lazy-hold-expiry.md)
- [ADR-007: Use Terraform for the AWS Target Infrastructure](adr/007-terraform-for-aws-target-infrastructure.md)
- [ADR-008: Create a Booking Exactly Once, from a Locked Hold and a Stored Response](adr/008-exactly-once-booking-creation.md)
- [ADR-009: Payment-Proof Object Storage and the Payment-Review Gate on Booking Confirmation](adr/009-payment-proof-storage-and-review-gate.md)
- [ADR-010: Deliver Asynchronous Work from a Transactional Outbox in PostgreSQL, and Give Every Unpaid Booking a Deadline](adr/010-transactional-outbox-and-booking-deadline.md)
- [ADR-011: Promote a Waitlisted Student by Sweep, into a Time-Bounded Seat Hold](adr/011-waitlist-promotion-by-sweep.md) (proposed)
- [ADR-016: Enforce Fair Booking Rules at Hold and at Booking, Serialised on the Student](adr/016-fair-booking-rules.md)
- [ADR-017: Cancel and Reschedule Trips in One Locked Transaction, and Track Refunds on the Booking](adr/017-trip-cancellation-cascade-and-refund-tracking.md)
- [ADR-019: Plan Departures in Batches from Day Templates, Previewed and Applied Exactly Once](adr/019-day-templates-and-batch-scheduling.md)
