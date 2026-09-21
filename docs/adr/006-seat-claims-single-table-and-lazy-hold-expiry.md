# ADR-006: Hold and Book Seats in One `seat_claims` Table with Lazy Expiry

## Status

Accepted

## Context

A student picks seats, holds them for a few minutes, and then books them. Two students must never hold or book the same seat, and a hold that is abandoned must free its seat again without anyone intervening.

ADR-003 already settled that PostgreSQL, not Redis, is authoritative for seat state. What remained open was the shape of that state: how a hold is represented, how it becomes a booking, and what makes an abandoned hold stop blocking its seat.

Two properties decide the design. A seat must never be claimable twice, which is a uniqueness question. And a hold must expire, which is a liveness question. Any design that answers the first with application logic, or the second with a background job, has a window in which it is wrong.

## Options considered

- **A `seat_holds` table with a mutable `status` column** (`HELD`, `RELEASED`, `EXPIRED`, `BOOKED`). A unique index then cannot be a plain `UNIQUE (trip_seat_id)`, because released and expired rows keep accumulating on the same seat. It needs a partial unique index, which PostgreSQL supports and H2 does not, so the test suite would stop exercising the one constraint the whole design rests on. Status also drifts: every new code path has to remember to set it, and a row whose status disagrees with reality silently locks a seat forever.
- **Separate `seat_holds` and `booking_seats` tables.** Confirming a booking means deleting the hold and inserting the booked row. Between those two statements the seat is protected by nothing, and no single constraint spans both tables, so there is no way to make the database refuse an oversell across the pair.
- **Redis keys with a TTL for holds.** Expiry becomes free and the hot path gets fast. But the seat's authority then lives outside the database that owns bookings, so a Redis eviction, restart, or split brain can double-sell a seat. ADR-003 rejected exactly this.
- **A scheduled sweeper that deletes expired holds.** Correctness would depend on the sweeper running. Between a hold expiring and the sweep, the seat reads as held although it is free; if the sweeper stops, seats leak permanently. The legacy application also called its sweeper at the top of every read, which turns every seat map into a write and a contention point.
- **One `seat_claims` table with `UNIQUE (trip_seat_id)`, rows deleted on release, and expiry derived on read.** Chosen; described below.

## Decision

One table, `seat_claims`, holds both a short-lived hold and a booked seat, with a plain `UNIQUE (trip_seat_id)` as the sole oversell authority. A seat is claimable exactly when it has no row.

There is no `status` column. A claim is released by deleting its row, so the table cannot describe a state that is not true. `expires_at` is `NOT NULL` on every row, and a claim blocks its seat when `booking_id IS NOT NULL OR expires_at > now`: a hold blocks until it expires, a booked claim blocks for good.

Confirming a hold into a booking is therefore a single `UPDATE ... SET booking_id = ?` on a row that already exists, so the seat is never unprotected between hold and booking.

Expiry is lazy. Reads compare `expires_at` to the current time and write nothing, so an expired hold reads as available immediately and no scheduler has to have run. The next transaction that wants one of those seats deletes the expired rows and inserts its own. Correctness never depends on a sweeper; a sweeper added later for issue #8 is only housekeeping.

Application-level checks stay, but only to produce a readable message. The constraint decides every race.

`V3__create_booking_tables.sql` creates `bookings`, `booking_seats`, `seat_claims`, `booking_events`, and `idempotency_keys` together so that `seat_claims.booking_id` has its foreign key from the start. Its DDL avoids partial indexes, `EXCLUDE` constraints, and JSONB, so the same schema runs on the H2 database the test suite uses.

## Consequences

- Oversell is impossible for reasons the database enforces, not reasons the service remembers to check. A concurrency test can prove it by counting rows.
- A seat map is a read. It costs no writes and no locks, and it is correct the instant a hold expires.
- Expired rows survive until something wants their seat. The table therefore carries dead rows on quiet trips. They are harmless — they never block a seat — and issue #8's expiry processing can sweep them for tidiness rather than for correctness.
- Releasing and reclaiming are deletes, so a hold's history is not recoverable from this table. `booking_events` is where an audit trail belongs.
- Hibernate orders inserts before deletes within a single flush. Any transaction that reclaims rows and then inserts new claims on the same seats must flush between the two, or it collides with the rows it just deleted. This bites the common case, not a rare one: a student re-selecting a seat they already hold. `SeatHoldService` flushes explicitly and a test pins the behaviour.
- `@UuidGenerator` is not an identity generator, so an insert otherwise defers to the commit-time flush, after the service method has returned. Translating a lost race into a 409 requires an explicit flush inside the `try`. The same latent defect exists in `TripService.create`, whose catch block is unreachable today.
- Enforcing "at most four seats" and "five minute hold" stays in configuration (`BookingProperties`) rather than in the schema, because both are product rules that will change and neither is a correctness boundary.
