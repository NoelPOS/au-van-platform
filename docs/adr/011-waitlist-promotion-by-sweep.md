# ADR-011: Promote a Waitlisted Student by Sweep, into a Time-Bounded Seat Hold

## Status

Accepted

## Context

A trip fills up. A student who wants a seat on it has, today, nothing to do but reload the seat map and hope. Issue #9 asks for a waitlist whose promotion is deterministic, does not violate the booking concurrency guarantees, and tells the student through the asynchronous workflow rather than a side channel.

Nothing here can be ported. The legacy Next.js application at `~/Desktop/AU-Van-reference` has no waitlist: `src/models/` contains `AuditLog`, `Booking`, `IdempotencyKey`, `Notification`, `Payment`, `ReminderJob`, `Route`, `Seat`, `Timeslot` and `User`, and a case-insensitive search of the whole tree for `waitlist`, `standby` or `capacity` matches nothing. This repository has no waitlist either — `docs/data-model.md` listed `waitlist_entries` as planned, with the legacy's own vocabulary ("full timeslots"), and `docs/architecture.md` named waitlist promotion as asynchronous work without saying how. **So the ordering rule, the promotion window and the rule for a student who does not act are new product rules**, exactly as ADR-010's payment deadline was, and they have to be written down rather than inferred.

Three accepted decisions bound the answer, and one of them is easy to violate without noticing.

ADR-006 made `seat_claims` the single table for both a hold and a booked seat, with `UNIQUE (trip_seat_id)` as the *sole* authority on overselling, and made expiry **lazy**: a hold stops blocking its seat when `expires_at` passes, and **no code runs at that moment**. "Reads compare `expires_at` to the current time and write nothing." That sentence is what makes a trigger-on-release design wrong, because the commonest way a seat becomes free — an abandoned five-minute hold — fires no event at all.

ADR-008 made booking confirmation exactly-once with a `PESSIMISTIC_WRITE` lock on the hold's claims and the rule that every decision is made from what the lock returned. ADR-009 reaffirmed it for the payment review, and ADR-010 reaffirmed it again for the expiry sweep, adding the discipline of reading candidate **ids** and locking each one.

ADR-010 put every asynchronous side effect in `outbox_events`, written in the same transaction as the change it describes, dispatched by a claim-and-lease poller, with no Redis.

## Options considered

### What a promotion produces

- **A booking, created on the student's behalf.** The most direct reading of "promotion". Rejected: a booking needs a passenger name and phone that nobody supplied, commits the student to a fare they have not agreed to, and starts a `payment_deadline_at` against a purchase they never made. It also means a second, parallel creation path alongside `BookingWriter`, with its own idempotency story.
- **A reservation of the *right* to book, held outside `seat_claims`.** Rejected sharply: the seat's authority would then live in two tables and `UNIQUE (trip_seat_id)` would stop being the sole answer to overselling, which is the one property ADR-006 exists to protect.
- **A seat hold, in `seat_claims`, with a longer expiry.** Chosen. A promotion is an `INSERT`, so the unique constraint decides its races exactly as it decides two students racing for a seat today. The student then walks the existing hold → confirm → pay → review path with no special case anywhere in it.

### What triggers a promotion

- **A hook at each point that frees a seat.** The issue's own wording, and it looks like the responsive choice. Rejected: there are three such points that run code — `BookingService.cancel`, `BookingExpiryWriter.expire`, `SeatHoldService.release` — and a fourth that runs none, because ADR-006 made hold expiry lazy by design. The most common release is the one with no hook to hang anything on, so this option needs a sweep anyway and ships four places that can each drift.
- **A database trigger on `seat_claims`.** Rejected: business rules in SQL, invisible to the test suite and to review, and still blind to a hold that expired without being deleted.
- **A scheduled sweep that derives free seats and promotes.** Chosen. It covers all four release paths with one mechanism, it is the shape `BookingExpiryService` and `OutboxDispatcher` already have, and its latency is one poll interval — the bound booking expiry already accepts.

### How a promotion is ordered

- **Priority by seats wanted, so a single seat fills faster.** Rejected: it is unexplainable to the student at the back of the queue and there is no product rule asking for it.
- **First in, first out, by when the student joined.** Chosen. It is the only ordering a student can predict, and "deterministic" in the acceptance criterion means predictable, not merely repeatable.

### How a student's place is stored

- **A `position` integer, maintained on every join and leave.** Rejected: every leave rewrites every row behind it, and a position that drifts from reality is invisible.
- **`joined_at`, with the position derived on read.** Chosen. Nothing to keep in sync, for the same reason ADR-006 has no `status` column on `seat_claims`.

### How a second join is refused

- **A partial unique index on the active statuses.** Rejected outright: ADR-006 already rejected partial indexes because H2 does not support them and the suite would stop exercising the constraint the design rests on.
- **A plain `UNIQUE (trip_id, user_id)`, reused on rejoin.** Chosen. One row per student per trip forever; leaving and rejoining resets `joined_at` and costs the student their place, which is both fair and the only behaviour this constraint permits.

### What happens when a promoted student does nothing

- **Back to the front of the queue.** Rejected: one unresponsive student cycles the seat until departure.
- **Back to the end of the queue.** Rejected for a weaker version of the same reason, and it makes the queue's length meaningless.
- **The entry ends, and the seat goes to the next student.** Chosen. One chance per promotion, with the window long enough to be fair.

### How long a promotion lasts

- **`booking.hold-ttl`, the existing five minutes.** Rejected: five minutes is calibrated for a student sitting in the seat map, not for someone who has to notice a LINE push first.
- **A window of its own, bounded by departure.** Chosen. Thirty minutes, capped at `departureAt - booking.departure-cutoff`, so a promotion never outlives the seat's usefulness — the same bound `BookingProperties.paymentDeadlineFor` already applies.

## Decision

- `waitlist_entries` holds one row per `(trip_id, user_id)`, with a plain `UNIQUE (trip_id, user_id)`, a `seats_wanted`, a `joined_at`, and a status of `WAITING`, `PROMOTED`, `FULFILLED`, `WITHDRAWN` or `EXPIRED`. Position is derived from `joined_at`, never stored.
- Joining is refused for a trip that still has free seats, for a trip that is not `ACTIVE`, for one that has departed, and for more seats than `booking.max-seats-per-hold`.
- Promotion is a scheduled sweep, gated on `booking.waitlist.enabled`, which is `false` in the test configuration exactly as `booking.expiry.enabled` and `outbox.dispatch.enabled` are. Tests call the sweep directly.
- The sweep reads candidate **ids**, then locks each entry with `WaitlistEntryRepository.lockById` and decides only from what the lock returned — ADR-008's discipline, as ADR-009 and ADR-010 each restated it. It carries no transaction of its own; each entry is promoted in a transaction of its own, so one that loses its race neither rolls back nor blocks the batch.
- A promotion **inserts `seat_claims` rows** for the student under a fresh `hold_id`, expiring at `min(now + booking.waitlist.promotion-window, departureAt - booking.departure-cutoff)`. `seat_claims_trip_seat_unique` decides every race with a direct booker, and a promoter that loses leaves its entry `WAITING` for the next sweep. The insert is followed by an explicit `flush()` inside the `try`, because `@UuidGenerator` is not an identity generator and the `catch` is otherwise unreachable.
- A promoted student holds an ordinary hold and books through `BookingService.create` unchanged. The entry becomes `FULFILLED`.
- A promotion whose window lapses is resolved by the same sweep: the entry becomes `EXPIRED`, an outbox row tells the student, and the seat is promoted to the next entry on a later pass.
- `OutboxEventType` gains `WAITLIST_PROMOTED` and `WAITLIST_PROMOTION_EXPIRED`, recorded through `OutboxRecorder.record` inside the promoting transaction. Neither carries a `dedupe_key`: that column is unique across the whole table and is what `OutboxEventRepository.cancelScheduled` uses to identify a reminder, so a waitlist row with one could be killed by an unrelated booking cancellation.
- `outbox_events.aggregate_id` now means "the thing this event is about", which for these two values is a waitlist entry rather than a booking. The column carries no foreign key by deliberate design, so this is a documentation change and not a schema one.
- Operational visibility is an administrator-only surface at `/api/v1/admin/operations` showing, per trip, bookings by status, the waitlist queue with its promotions, and the `DEAD` outbox rows that `OutboxEventRepository.markDead` already records for exactly this purpose. The view is read-only: promotion, retry and cancellation each keep their single existing path.
- **Redis is still not used, and that is still a decision.** ADR-010's sentence extends here unchanged: each entry's row lock decides who promotes it, so nothing needs a distributed lock.

### Delivery

This decision is implemented across three issues, because a new domain concept, a sweep with two distinct races, the outbox handoff, a student surface and an admin surface are not one reviewable diff. Issue #68 lands the table, the entity, the repository, joining, leaving, reading a place, the `booking.waitlist` configuration, and the student surface. Issue #69 lands the promotion sweep and its notifications. Issue #70 lands the administrator's view. All three have landed: #68 and #70 are on `main`, and #69 is the pull request carrying this sentence, the last of the three — so the status above is `Accepted` with its merge.

## Consequences

- A seat freed by any of the four paths — cancellation, expiry, an explicit hold release, or a hold that simply lapsed with no code running — reaches the waitlist through one mechanism. The proof is the lapsed-hold test: it is the only one that fails if promotion is moved to the release call sites.
- Promotion is not instant. A student is told within one poll interval of the seat coming free, and the trade is the same one booking expiry already made.
- Overselling stays impossible for the reason it was already impossible. A promotion is an `INSERT`, so nothing new has to hold; the concurrency tests prove it by counting rows.
- A promoter that loses a race does nothing and leaves the entry alone. That is the ordinary outcome, not an error, and it means a promotion attempt is cheap to retry.
- **One chance per promotion is a real product rule with a real cost.** A student who misses the push loses the seat and their place. The window is configuration and the deadline is in the response, but this is the third thing in the system that takes something away unprompted and belongs in a release note.
- Rejoining after leaving costs the student their place, because one row per student per trip is what a plain unique constraint gives. This is a deliberate consequence of refusing a partial index, and it is defensible on its own terms.
- The queue holds only students who could not simply book, because joining a trip with free seats is refused. That keeps the sweep's candidate set small and the ordering meaningful.
- `waitlist_entries` grows with one row per interested student per trip and nothing prunes it. The rows are small and bounded by trips, and the same sentence ADR-008 wrote about `idempotency_keys` and ADR-010 about `outbox_events` applies; a prune belongs in the expiry sweep if it is ever needed.
- A promoted hold is indistinguishable from a self-made one in `seat_claims` and reads as `HELD_BY_YOU` on the seat map. That is what keeps the booking path free of special cases, and it also means the seat map cannot explain *why* the student has a hold; the notification and the waitlist read do that.
- `booking_events` is not extended. A promotion happens before any booking exists, so its history lives on the entry and in `outbox_events`, and the append-only booking history stays about bookings.
- This is proven on H2 in PostgreSQL mode and inherits the caveat ADR-008, ADR-009 and ADR-010 all carry. Real PostgreSQL semantics under READ COMMITTED remain issue #10's.
