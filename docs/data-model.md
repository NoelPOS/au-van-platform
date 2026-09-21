# Data Model

The transport-inventory tables are implemented in migration `V2`. The booking tables are created by migration `V3` and completed by `V4`, and all five are now in use. `V5` adds `payment_proofs` and `V6` its reviewer columns. `V7` adds `outbox_events`, which is the only asynchronous-workflow table there will be: ADR-010 replaced the planned `reminder_jobs` and `notifications` with it. `V8` adds `bookings.payment_deadline_at`, which is what finally bounds a held seat. `V9` adds `waitlist_entries`, the queue for a trip whose seats are all claimed.

## Schema ownership

Flyway owns the schema. The migrations under `api/src/main/resources/db/migration` are the only thing that creates or changes a table, and `spring.jpa.hibernate.ddl-auto` is `validate` in both `application.yml` files, so Hibernate checks the mappings against the migrated schema at startup and refuses to start when they disagree.

Entity mappings therefore describe the table rather than define it. Column lengths and named unique constraints are repeated in the mapping so that a divergence shows up in review; `CHECK` clauses and the indexes in `V3` and `V4` exist only in SQL, because `validate` checks neither, so mirroring them would buy nothing.

Until issue #27 this was not the case: `flyway-core` was on the classpath without `spring-boot-flyway`, which is where Spring Boot 4 moved `FlywayAutoConfiguration`, so no Flyway bean existed and no migration had ever run. Tests passed on a schema Hibernate generated from the mappings, and against PostgreSQL the application had no tables at all.

## Core entities

| Entity | Migration | Purpose |
| --- | --- | --- |
| `app_users` | `V1` | Student and administrator identities, roles, and LINE linkage |
| `routes` | `V2` | Active or inactive van routes, fare, and duration |
| `seat_layouts` | `V2` | Named reusable vehicle seat templates |
| `seat_layout_seats` | `V2` | Labelled row/column positions inside a reusable layout |
| `vehicles` | `V2` | Vehicle code, name, active status, and assigned seat layout |
| `trips` | `V2` | A scheduled route departure, vehicle, status, and snapshot of fare/duration |
| `trip_seats` | `V2` | Labelled row/column seat snapshot created with a trip |
| `seat_claims` | `V3` | One claimed seat, held for a few minutes or attached to a booking |
| `bookings` | `V3`, `V4`, `V8` | Student booking, passenger details, price, lifecycle state, customer-facing reference, and payment deadline |
| `booking_seats` | `V3` | Which seats a booking bought, kept after cancellation frees the claims |
| `booking_events` | `V3` | Append-only history of a booking's state changes |
| `idempotency_keys` | `V3`, `V4` | The response a critical client write already produced, for replay on retry |
| `payment_proofs` | `V5`, `V6` | One payment-proof submission: its object key, metadata, and the administrator's decision |
| `outbox_events` | `V7` | Outbound work a committed transaction owes, with its claim, retry, and delivery state |
| `notifications` | dropped | Delivery intent and result; ADR-010 folded both into `outbox_events` |
| `reminder_jobs` | dropped | Scheduled reminders; ADR-010 made one a future-dated `outbox_events` row |
| `waitlist_entries` | `V9` | One student's place in the queue for a trip whose seats are all claimed |
| `audit_logs` | planned | Staff actions and sensitive state changes |

## Seat claims

`seat_claims` covers both a short-lived hold and a booked seat in one row, so a seat is never unprotected between the two. ADR-006 records the decision.

- `UNIQUE (trip_seat_id)` is the only thing that prevents overselling. A seat is claimable exactly when it has no row.
- There is no status column. A claim is released by deleting its row, so the table cannot describe a state that is not true.
- `expires_at` is always set. A claim blocks its seat while `booking_id IS NOT NULL OR expires_at > now`.
- `hold_id` groups the rows one student created in one selection, which is what the release endpoint addresses.
- Expiry is lazy. Reads derive the seat state and write nothing; the next transaction that wants the seat deletes the expired row.

Seat state is derived on every read and never stored: `AVAILABLE`, `HELD`, `HELD_BY_YOU`, or `BOOKED`.

## Bookings and idempotency

A booking is created from a hold, exactly once. ADR-008 records the decision.

- Confirmation takes a `PESSIMISTIC_WRITE` lock on the hold's `seat_claims` rows and decides from what that query returned. The unique constraint cannot see this race: confirmation updates rows that already exist rather than inserting new ones, so two confirmations of one hold would both satisfy it.
- `bookings.reference` is the customer-facing code, `AUV-YYMMDD-XXXXXXXX` over a thirty-symbol alphabet that omits `0/O`, `1/I/L`, and `U`. `bookings_reference_unique` decides a collision.
- `booking_seats` records what was bought. Cancellation deletes the booking's `seat_claims` rows so other students can take the seats, and the booking still says which seats it had.
- `booking_events` is append-only, oldest first, and `actor_user_id` is nullable for system-driven transitions. A payment review records the administrator there, so a student's own booking response can carry a staff user id.
- `idempotency_keys` stores the rendered response against `(user_id, endpoint, idempotency_key)`, which is unique, plus `request_hash`: a SHA-256 over the serialised validated request. A retry replays the stored bytes; the same key with a different payload is refused. The response is stored rather than re-rendered, so a retry arriving after a cancellation still returns what was sent the first time.
- `seat_claims` deletions by id refuse rows that carry a `booking_id`, so a reclaim or a release cannot free a seat that a concurrent confirmation has just sold.

## Payment proofs

A booking is created `PENDING_PAYMENT` and only an approved payment proof reaches `CONFIRMED`. ADR-009 records the decision.

- `payment_proofs` holds one row per submission, so a rejection and the resubmission that follows it keep their history instead of overwriting each other.
- The image is in object storage and only `object_key` is in PostgreSQL. Nothing that reads this table can hand out the bytes: every read goes back through the API, which authorizes it itself.
- `object_key` is `payment-proofs/{bookingId}/{timestamp}-{uuid}{ext}` and unique, so no submission can overwrite another's image.
- `status` is `SUBMITTED` until an administrator decides, and `reviewed_by_user_id`, `reviewed_at`, and `review_note` record the decision. `payment_proofs_review_recorded` is a `CHECK`: a row that is no longer `SUBMITTED` must name who decided it and when, and a `SUBMITTED` row must name neither.
- A decision locks the booking's row first and is made only from what the lock returned, the same discipline ADR-008 established for confirmation. Approval moves the booking to `CONFIRMED`, rejection to `PAYMENT_REJECTED`, from which the student may submit another proof.
- The submission is one transaction, and the image is written to storage before any row. A storage failure therefore leaves no proof row, no status change, and no history entry.
- `booking_events` carries `PAYMENT_PROOF_SUBMITTED`, `PAYMENT_APPROVED`, and `PAYMENT_REJECTED`, so the audit trail stays in the one append-only table rather than growing a parallel one. A rejection's note is the event's detail, which is how the student is told what to fix.
- `seat_claims` are not released while a booking waits for payment or for review, so the only thing that ever frees them is `bookings.payment_deadline_at` running out. ADR-009 accepted the unbounded hold as a consequence and ADR-010 decided the bound; the "Unpaid bookings expire" section below is the bound as implemented.

## Unpaid bookings expire

Every booking carries its own deadline and a sweep releases the seats of the ones that pass it. ADR-010 records the decision.

- `bookings.payment_deadline_at` is nullable, and `NULL` means "never expires" — the right answer for every `CONFIRMED` or `CANCELLED` row. Only the transitions that own it write it: creation and rejection set `min(now + booking.payment-window, departureAt - booking.departure-cutoff)`, a submitted proof moves it to the departure bound alone, and confirming, cancelling, or expiring clears it.
- The window is measured from an explicit column rather than from `updated_at`, so no future write can silently reset a student's payment clock. It is a **new product rule**, not one ported from the legacy application, which never expires an unpaid booking at all. The defaults are two hours and one hour before departure.
- A booking under review is bounded by departure rather than by a timer, so a slow reviewer never costs a student their booking while the seat is still worth recycling.
- The sweep's predicate is `status IN ('PENDING_PAYMENT','PAYMENT_UNDER_REVIEW','PAYMENT_REJECTED') AND payment_deadline_at <= now`, with no join, and `bookings_payment_deadline_idx` is exactly that predicate.
- It selects **ids only**, then locks each booking with `BookingRepository.lockById` and decides from what the lock returned. A candidate read is a hint: loading the entities would put them in the persistence context and every later read would hand the pre-lock instance back, so a booking confirmed in between would still look expirable and its paid seat would be freed.
- Expiry produces `CANCELLED`, not a new status, plus a `booking_events` row of type `EXPIRED` with a null `actor_user_id` — the column is nullable for exactly this kind of system-driven transition. The seats are released with `deleteByBookingId`, which is the only delete that will free a claim carrying a `booking_id`.
- An expired booking's payment proof stays `SUBMITTED`, because `payment_proofs_review_recorded` requires a reviewer on anything else and the sweep has none. The review queue excludes proofs whose booking is `CANCELLED` instead, so an administrator is never left with a row they cannot clear.
- The same sweep prunes `idempotency_keys` past `booking.idempotency-key-retention`, which ADR-008 named as this work's to do. The window has to stay longer than any client's retry horizon: a key dropped early stops replaying and the retry writes a second booking.

## The waitlist

A student who cannot book a full trip joins its queue instead, sees where they stand, and can leave. A seat that comes free is offered to whoever has waited longest. ADR-011 records the decision. Joining, leaving, reading a place, the promotion sweep, and the administrator's read-only view of the queue in `OperationsViewService` are all implemented.

- One row per `(trip_id, user_id)`, enforced by a plain `waitlist_entries_trip_user_unique`. Not a partial index on the active statuses: ADR-006 rejected partial indexes because H2 does not support them, so the suite would stop exercising the one constraint this shape rests on.
- `status` is `WAITING`, `PROMOTED`, `FULFILLED`, `WITHDRAWN`, or `EXPIRED`. The first two are the queued states — the only ones that occupy a place — and the other three are terminal. `WAITING` and `WITHDRAWN` are the student's own doing; the promotion sweep writes the other three, each from behind the entry's row lock.
- **Position is derived, never stored.** The ordering is `ORDER BY joined_at, id` over the queued entries alone, so a student who leaves stops occupying a place for everyone behind them with nothing to update. A maintained `position` integer would have to rewrite every row behind a leaver, and one that had drifted from reality would be invisible — the same reasoning that leaves `seat_claims` without a status column.
- Joining is idempotent. A repeated join returns the entry that already exists, with the place it already had; tapping the button twice must not cost a student their place.
- Leaving and re-joining **reuses the same row with a fresh `joined_at`**, so it costs the student their place. That is the only behaviour one row per student per trip permits, and ADR-011 accepts it as the price of refusing a partial index.
- Joining is refused for a trip that still has a free seat (`waitlist_not_needed`), for a trip that is not `ACTIVE` or has departed, and for more seats than `booking.max-seats-per-hold`. "Free" is `SeatAvailabilityService.freeSeatsOf`, the one derivation the seat map draws itself from and the promotion sweep promotes onto, so a lapsed hold makes a trip bookable and therefore not queueable.
- Student reads and writes are **scoped by owner in the query**, never filtered after loading, so someone else's entry answers exactly as one that does not exist — `BookingRepository.findByIdAndUserId`'s rule, and `SeatHoldService.release`'s `hold_not_found` idiom.
- Leaving is `POST /api/v1/waitlist/{id}/leave`. It is not a `DELETE` because `SecurityConfiguration`'s CORS `allowedMethods` has none, and a `DELETE` would pass every test here and fail only in a real browser.
- `trip_id` has a foreign key to `trips`; `user_id` is a plain UUID on the mapping with the same `app_users` foreign key `bookings.user_id` carries.
- `promotion_hold_id` and `promotion_expires_at` are the sweep's columns: the hold it created for the student and when that hold stops being theirs. `waitlist_entries_candidate_idx` is `(trip_id, status, joined_at)`, the candidate query, and `waitlist_entries_promotion_idx` is `(status, promotion_expires_at)`, the lapse sweep's predicate.
- Leaving a promoted entry **releases its hold** in the same transaction, so a seat never stays with a student who has walked away. The release is `SeatClaimRepository.deleteByIdIn`, whose `booking_id is null` guard means a student who booked the seat first keeps what they paid for.
- A promotion writes one `outbox_events` row in the transaction that made it, with the entry as its `aggregate_id` and **no `dedupe_key`**; the lapse that ends an unused promotion writes another. A join still writes nothing.
- A join writes no `outbox_events` row and needs no `Idempotency-Key`: nothing is delivered and no money is committed. The unique constraint is the whole of its idempotency.

## Asynchronous work

Everything a committed transaction owes the outside world is one row in `outbox_events`. ADR-010 records the decision.

- The row is written **in the same transaction as the state change it describes**, by a recorder that joins the caller's transaction and never opens its own. A rolled-back booking leaves no row; a committed one leaves exactly one. There is no second system to reconcile with.
- A dispatch claims a row with one conditional `UPDATE` whose `WHERE` carries the current `status` and `next_attempt_at`, and the affected-row count is the answer. That is the only thing that stops two workers sending one message, and the only thing that stops a `SENT` or `DEAD` row being sent again.
- The claim pushes `next_attempt_at` forward by a lease and increments `attempts`, so a worker that dies mid-send leaves a row that becomes due again on its own and has still spent an attempt. No separate sweeper exists or is needed.
- `status` is `PENDING`, `IN_FLIGHT`, `SENT`, or `DEAD`. The first two are claimable; the last two are terminal. A row that fails `outbox.max-attempts` times lands `DEAD` with `last_error` set and is never retried again. So does one whose failure can never succeed, on its first attempt — see delivery below.
- `payload` is `VARCHAR(2000)` of JSON — facts about the booking, or about the trip for a waitlist promotion, and never message text, so rewording a message does not have to be migrated into rows written before it. The width is deliberate; ADR-010 gives the arithmetic.
- `dedupe_key` is unique and nullable: null for a state-change event, which legitimately repeats, and set for a departure reminder, where the constraint is what makes scheduling idempotent. It is therefore also the column that identifies a row as a reminder, which is what the withdrawal below selects on.
- `aggregate_id` is the thing the row is about: the booking for every event type but the two waitlist ones, whose aggregate is a `waitlist_entries` row because a promotion happens before any booking exists. Widening that meaning cost a javadoc and this line and no migration, for the reason below.
- There are **no foreign keys** on this table. A queue row must not block the deletion of the booking or the user it names, and the dispatcher must tolerate an aggregate that has since gone.
- The index is `(status, next_attempt_at)`, which is exactly the dispatcher's predicate.

### Delivery to LINE

A dispatched row becomes one push to the student's LINE account, through the LINE Messaging API. The channel is configured by `notification.line.*` and is a **different LINE channel** from the Login channel `auth.line.channel-id` names; README.md records why that distinction can break delivery in a way no code here can detect.

- Each send carries the outbox row's id as `X-Line-Retry-Key`. LINE deduplicates on it for twenty-four hours, and the id does not change across retries, which is what makes an at-least-once transport at most one message a student sees. The configured backoff, cap, and attempt ceiling multiply out to minutes, so the whole schedule finishes far inside that window; changing any of them means re-checking it.
- A `409` from LINE is **success**, not failure: it means a request under this retry key was already accepted, so the message arrived on an earlier attempt whose answer this worker never saw.
- A `5xx`, a `429`, or no answer at all is transient, and the row backs off and is retried. Every other `4xx` is permanent and the row goes `DEAD` on the attempt that produced it. `404` is the one that occurs in practice — an unknown user id, or a student who has never added the official account as a friend — and it answers identically forever, so retrying it would cost `outbox.max-attempts` futile sends for every notification that student is ever owed.
- With no channel access token configured, a send fails as transient and the row retries; the application still starts. With `notification.line.enabled` false there is no sender at all, and the handler records the omission and resolves the row. That is how the test suite runs with no channel and no credential.

### Departure reminders

A reminder is not a second mechanism. It is an `outbox_events` row whose `next_attempt_at` is in the future and whose `dedupe_key` is set — which is why ADR-010 dropped the planned `reminder_jobs` table rather than writing the claim, the backoff, and the dead-letter rule a second time. The timings are ported from the legacy application, the only place a validated rule for them exists.

- Two offsets, `DEPARTURE_REMINDER_24H` and `DEPARTURE_REMINDER_1H`, each due at the trip's `departure_at` minus its offset. The legacy's third mode, a daily batch at 01:00 UTC, is not ported: it exists to make one Vercel cron slot cover every booking, a constraint this API does not have.
- They are scheduled when a payment proof is **approved**, inside that approval's own transaction, so an approval that rolls back schedules nothing.
- A reminder whose moment has already passed is **not queued at all**. A row due in the past is a row due now, so queueing the twenty-four hour reminder for a booking approved twelve hours before departure would fire it immediately, announcing notice the student has not got.
- `dedupe_key` is `"<bookingId>:<type>"`, the legacy's `unique (bookingId, type)` written as one column. The unique constraint is what makes scheduling idempotent.
- Cancelling a booking marks its unsent (`PENDING`) reminders `DEAD` whatever their due time — a reminder that has come due but that no worker has claimed yet is exactly the one that must not go out; expiry does the same. Without it a student who cancelled yesterday is told this afternoon that their trip departs in an hour. The withdrawal is scoped to rows carrying a `dedupe_key`, so the cancellation's or expiry's own message — which the student does need — is untouched, as is a reminder that has already been sent or that another worker is mid-send on.

## Critical constraints to design

- A seat label and seat position must be unique within a layout and within a trip.
- A vehicle cannot have two trips at the exact same departure time.
- A trip seat can carry at most one claim, held or booked. `seat_claims.trip_seat_id` is unique and this is what makes a confirmed booking unable to share a seat with another.
- Booking, payment, and notification status values must be controlled state machines. Hold state is deliberately not one; see ADR-006.
- Payment proof is stored in object storage; PostgreSQL stores the object key and metadata only.
- Staff approvals, rejections, and manual overrides must have audit records.
