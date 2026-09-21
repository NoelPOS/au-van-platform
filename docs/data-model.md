# Data Model

The transport-inventory tables are implemented in migration `V2`. The booking tables are created by migration `V3` and completed by `V4`, and all five are now in use. `V5` adds `payment_proofs` and `V6` its reviewer columns. `V7` adds `outbox_events`, which is the only asynchronous-workflow table there will be: ADR-010 replaced the planned `reminder_jobs` and `notifications` with it. `V8` adds `bookings.payment_deadline_at`, which is what finally bounds a held seat.

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
| `audit_logs` | planned | Staff actions and sensitive state changes |
| `waitlist_entries` | planned | Optional queue for full timeslots and promotion processing |

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

## Asynchronous work

Everything a committed transaction owes the outside world is one row in `outbox_events`. ADR-010 records the decision.

- The row is written **in the same transaction as the state change it describes**, by a recorder that joins the caller's transaction and never opens its own. A rolled-back booking leaves no row; a committed one leaves exactly one. There is no second system to reconcile with.
- A dispatch claims a row with one conditional `UPDATE` whose `WHERE` carries the current `status` and `next_attempt_at`, and the affected-row count is the answer. That is the only thing that stops two workers sending one message, and the only thing that stops a `SENT` or `DEAD` row being sent again.
- The claim pushes `next_attempt_at` forward by a lease and increments `attempts`, so a worker that dies mid-send leaves a row that becomes due again on its own and has still spent an attempt. No separate sweeper exists or is needed.
- `status` is `PENDING`, `IN_FLIGHT`, `SENT`, or `DEAD`. The first two are claimable; the last two are terminal. A row that fails `outbox.max-attempts` times lands `DEAD` with `last_error` set and is never retried again.
- `payload` is `VARCHAR(2000)` of JSON — facts about the booking, not message text, so rewording a message does not have to be migrated into rows written before it. The width is deliberate; ADR-010 gives the arithmetic.
- `dedupe_key` is unique and nullable: null for a state-change event, which legitimately repeats, and set for a scheduled reminder in #63, where the constraint is what makes scheduling idempotent.
- There are **no foreign keys** on this table. A queue row must not block the deletion of the booking or the user it names, and the dispatcher must tolerate an aggregate that has since gone.
- The index is `(status, next_attempt_at)`, which is exactly the dispatcher's predicate.

## Critical constraints to design

- A seat label and seat position must be unique within a layout and within a trip.
- A vehicle cannot have two trips at the exact same departure time.
- A trip seat can carry at most one claim, held or booked. `seat_claims.trip_seat_id` is unique and this is what makes a confirmed booking unable to share a seat with another.
- Booking, payment, and notification status values must be controlled state machines. Hold state is deliberately not one; see ADR-006.
- Payment proof is stored in object storage; PostgreSQL stores the object key and metadata only.
- Staff approvals, rejections, and manual overrides must have audit records.
