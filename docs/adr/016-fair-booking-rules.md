# ADR-016: Enforce Fair Booking Rules at Hold and at Booking, Serialised on the Student

## Status

Accepted

## Context

Nothing stopped one student from holding seats they never meant to pay for. A booking holds its seats until `payment_deadline_at` (ADR-010), so a student could book trip after trip, let every booking lapse, and keep seats away from everyone else for two hours at a time. Two deadline rules were also unfair to students who did pay: a booking under review could expire while the slip sat unread in the queue, and a rejection restarted the full payment window, which rewarded a slip sent back late more than one sent back early.

The repository owner agreed seven rules on 2026-10-09 (issue #129):

1. **One unpaid booking at a time.** `PENDING_PAYMENT` or `PAYMENT_REJECTED` blocks a new hold and a new booking. `PAYMENT_UNDER_REVIEW` does not.
2. **A pause after repeated expiries.** Two expiries inside any rolling seven days block holding and booking until twenty-four hours after the later one. Cancelling your own booking never counts. An administrator can lift the pause; expiries before the lift stop counting.
3. **The deadline stops when a slip is uploaded.** `PAYMENT_UNDER_REVIEW` is not expirable.
4. **A sent-back slip gets a fair window.** The new deadline is the later of the original deadline and now plus thirty minutes, still capped at the departure bound.
5. **Booking closes ninety minutes before departure.** Holds and waitlist joins after that are refused, and the catalogue says when.
6. **One booking per student per trip.** A non-cancelled booking on a trip blocks another hold on it and joining its waitlist.
7. The student can ask up front whether they may book, and why not.

These are new product rules. The legacy application has none of them.

Two accepted decisions bound the design. ADR-006 and ADR-008 made `seat_claims` and its unique key the only authority on seats, and confirmation a locked, exactly-once write. ADR-010 gave every unpaid booking its own deadline and an `EXPIRED` history entry when it lapses.

## Options considered

### Where the student-level rules are checked

- **At seat hold only.** It is where the student first acts, so the refusal comes early. Rejected as the only check: a hold taken before the student had an unpaid booking, or a hold handed out by a waitlist promotion (ADR-011), would still become a booking.
- **At booking creation only.** It is the write that creates the unpaid booking, so it is the one place the rule must hold. Rejected as the only check: the student would pick seats and fill in the passenger form before being told no.
- **At both, through one service.** Chosen. `BookingEligibilityService` owns rules 1, 2 and 6; the hold path asks it so the refusal is early, and the booking path asks it again so the rule is true. A waitlist promotion still produces a hold, as ADR-011 decided, and its booking runs the same check.

### How two simultaneous bookings by one student are kept to one unpaid booking

A student can hold seats on two trips, because neither hold is a booking. If both are confirmed at once, each transaction reads "no unpaid booking" before the other commits, and both succeed. `BookingConcurrencyPostgresTests` forces that interleaving against PostgreSQL, and it leaves two unpaid bookings without the lock chosen below.

- **A partial unique index on `bookings (user_id)` for the unpaid statuses.** Rejected: ADR-006 already refused partial indexes because H2, which runs the whole suite, does not support them. It would also turn a rejected slip, which moves between statuses, into an index maintenance concern.
- **`SERIALIZABLE` isolation on booking creation.** Rejected: it fails the loser with a serialisation error the caller has to retry, and it changes the isolation of the one write ADR-008 has already proved under `READ COMMITTED`.
- **A PostgreSQL advisory lock keyed on the student.** Rejected: it does not exist in H2, so the suite could not exercise it.
- **A `PESSIMISTIC_WRITE` lock on the student's `app_users` row inside the booking transaction.** Chosen. It is the lock style ADR-008 already uses, both databases support it, and it serialises exactly one student's bookings against each other and nothing else. It is taken after the hold's claims are locked, so the order is always claims then user, and two of one student's confirmations cannot deadlock. The hold path takes no lock: a hold is not a booking, and the booking path decides.

### How the pause remembers an administrator's lift

- **A column on `app_users`.** Rejected: it keeps only the latest lift and couples the auth module's table to a booking rule.
- **An append-only `booking_cooldown_clears` table.** Chosen. Each row records the student, the administrator, and when, so it is also the audit the issue asks for. The pause counts only `EXPIRED` events after the student's latest lift.

### How the pause is computed

- **A counter maintained on expiry.** Rejected: it has to be decremented when the window slides, and a counter that drifts is invisible.
- **Derived on read from `booking_events`.** Chosen. Expiries are already recorded as `EXPIRED` events with no actor, and a student's own cancellation is a `CANCELLED` event, so "self-cancellation never counts" needs no extra column. Only expiries newer than `now - lookback - duration` can still matter, so the read is bounded.

### What stops the deadline under review

- **Keep the departure bound as the deadline and let the sweep skip the status.** Rejected: a student would still be shown a deadline that no longer applies.
- **Clear `payment_deadline_at` on submission and drop `PAYMENT_UNDER_REVIEW` from both the sweep's candidate query and the locked re-check.** Chosen. `NULL` already means "never expires" (ADR-010). Migration `V10` clears the deadline on rows already under review.

### What "the original deadline" is on rejection

- **A new column storing the first deadline.** Rejected: the original deadline is `paymentDeadlineFor(departureAt, createdAt)`, and `created_at` never changes.
- **Derive it from `created_at`.** Chosen.

### What booking closure applies to

- **Holds, waitlist joins, and waitlist promotions.** Chosen. A promotion is a hold the system takes for a student, so it stops at the same moment. A booking made from a hold taken before closure is still accepted: the hold lasts at most a few minutes, and refusing it would fail a student who was on time.

## Decision

Enforce rules 1, 2 and 6 in `BookingEligibilityService`, called from `SeatHoldService.hold` and from `BookingWriter.create`, and rule 6 alone from `WaitlistService.join`. In `BookingWriter.create`, lock the student's `app_users` row after the hold's claims and before the check. Enforce rule 5 beside the existing trip checks in the hold, waitlist-join and promotion paths. Clear the deadline when a slip is submitted, and give a rejection `min(max(original, now + resubmit-window), departure bound)`. Record each lift of a pause in `booking_cooldown_clears`. Serve rule 7 as `GET /api/v1/me/booking-eligibility`.

Every refusal is a `409` problem with a stable `code`: `unpaid_booking_exists` (with `bookingId` and `bookingReference`), `booking_cooldown` (with `retryAt`), `booking_closed`, and `already_booked_on_trip`. Every window is a `booking.*` property: `closes-before-departure`, `resubmit-window`, and `cooldown.expiries`, `cooldown.lookback`, `cooldown.duration`.

## Consequences

- `seat_claims` remains the only authority on seats. The new rules refuse requests before any claim is written and add nothing to the claim races ADR-006 and ADR-008 settled.
- One student's booking confirmations are serialised; the lock adds no waiting between different students. A sign-in by the same student rewrites that `app_users` row, so it waits for an in-flight confirmation to commit.
- A booking under review can now hold its seats until an administrator decides, even past the departure bound. The review queue is the only thing that ends it, so a slow queue costs seats instead of students.
- With the default windows, a promotion can no longer be cut short by the departure bound: promotions stop ninety minutes before departure, and the thirty-minute window then ends before the sixty-minute departure bound. `promotionDeadlineFor` keeps the cap for other configurations.
- A student who joined a waitlist and later booked the same trip can still be promoted, because promotion does not check the student. The resulting hold cannot become a second booking. The seat stays held until the promotion lapses, as for any student who ignores an offer.
- Clearing a pause also forgives any earlier single expiry, because only expiries after the latest lift count.
