# ADR-008: Create a Booking Exactly Once, from a Locked Hold and a Stored Response

## Status

Accepted

## Context

A student picks seats, holds them, and confirms. Confirmation is the moment money and a seat become committed, and it happens over a phone on a university campus: the request is retried, double-tapped, and resumed after the connection drops. Two properties have to hold no matter how many copies of the request arrive.

A hold must become **at most one** booking. ADR-006 made `UNIQUE (trip_seat_id)` on `seat_claims` the sole authority on overselling, and it is enough for holds because a hold is an `INSERT`. Confirmation is not. It is `UPDATE seat_claims SET booking_id = ?` on rows that already exist, so two concurrent confirmations of one hold both satisfy the unique index and both succeed — two bookings, one seat, and no constraint anywhere that was violated. The schema cannot see this race at all.

A retry must return **the first answer**, not a second booking and not an error. That is what an `Idempotency-Key` is for, and `V3` created `idempotency_keys` for it, but with no column for the request it was recorded against. Without one, a key reused for a different payload is indistinguishable from an honest retry, and the endpoint would cheerfully answer a request for seats B with the booking it made for seats A.

## Options considered

- **Rely on the unique constraint alone, as holds do.** Free, and wrong for the reason above: confirmation updates rows rather than inserting them, so there is nothing for a constraint to refuse.
- **Optimistic locking (`@Version`) on `seat_claims`.** It would detect the second confirmation, but only by failing the loser with an `OptimisticLockException` after it had already built a booking, and it adds a column to the table ADR-006 deliberately kept free of mutable state.
- **A unique index on `seat_claims.booking_id`, or on the hold.** A hold has several claims, so `booking_id` is not unique by design, and `hold_id` is not unique either.
- **`SELECT ... FOR UPDATE` on the hold's claims, and decide from the rows it returns.** Chosen.
- **Redis for the idempotency record**, keyed with a TTL. Fast, and it puts the record of a committed financial write outside the database that holds the write. ADR-003 already rejected exactly that split.
- **Re-render the response on replay** instead of storing it. Cheaper, and it answers a retry that arrives after a cancellation with a cancelled booking under `201 Created`.
- **Store the rendered response next to the booking, in the same transaction.** Chosen.

## Decision

Confirmation takes a `PESSIMISTIC_WRITE` lock on the hold's claims as its first statement, and every subsequent decision — whose hold it is, whether it is already booked, whether it has expired — is made from the rows that query returned. A reading taken before the lock is stale and defeats the lock entirely. There is no fetch join on the locking query, because PostgreSQL refuses `FOR UPDATE` on the nullable side of an outer join.

The response is stored, not re-rendered. `V4` adds `request_hash` to `idempotency_keys`: a SHA-256 over the serialised **validated** request, so two retries differing only in whitespace or field order are recognised as one request, while the same key presented with a different payload is refused with `409`. A replay returns the stored bytes verbatim, so the client cannot tell a replay from the original and has nothing to branch on.

`V4` also adds `bookings.reference`, the customer-facing `AUV-YYMMDD-XXXXXXXX` code, with a unique constraint. Payment (#7) and notification (#8) both key their messages off a booking code in the legacy application, and the web client is being built against this response shape right now; adding it later would change a published contract.

The orchestration owns **no transaction**. It runs three: the pre-check, the write, and — when the write loses a race — the replay. This is not a style choice. Hibernate marks a transaction rollback-only at flush, when it converts a constraint violation, so by the time any `catch` runs the surrounding transaction is already dead and could not perform the replay read. The write itself flushes twice, after the booking and after the idempotency record, because `@UuidGenerator` is not an identity generator: without an explicit flush the inserts defer to the commit, every `catch` block is unreachable, and a lost race escapes as a 500.

Errors gained a machine-readable `code` member alongside RFC 7807's `detail`. This endpoint answers `409` for seven distinct conditions, and "your hold expired, pick again" and "you have already booked this, go and look at it" need different handling in the client. The `code` was added to the existing seat-hold and trip errors in the same change, so the flow has one error idiom rather than two.

Cancellation deletes the booking's claims to free the seats, and reads the booked seats back from `booking_seats`, which survives. There is no cancellation cutoff window: the legacy application refuses only an already-cancelled booking, and its two-hour rule belongs to rescheduling.

## Consequences

- One hold produces one booking, and the second confirmation is told so rather than being allowed to succeed. The proof is a concurrency test; removing the lock annotation turns it red.
- Confirmations of the same hold serialise. They were already contending for the same rows, so nothing that was previously parallel becomes sequential, but a confirmation now holds row locks for the length of its transaction and a slow confirmation blocks its own retries.
- A retry is answered from the record and never reads the hold, so it cannot be affected by anything that happened to the hold since.
- `response_body` is `VARCHAR(4000)`. A booking of four seats with a full history fits with room to spare, but an endpoint with a larger response cannot reuse this table as it stands.
- `idempotency_keys` grows without bound. Nothing prunes it yet; expiry processing in #8 is the natural home for that, and the rows are small.
- `seat_claims` gained a correctness guard that is not obvious from its name: `deleteByIdIn` refuses rows that carry a `booking_id`. Every caller chooses the rows to delete from a read taken earlier in its transaction, and a confirmation can sell one of those seats in between. The row lock does not help — the reclaimer simply waits, then deletes a row that has been paid for. Matching on the current value of `booking_id` is what refuses.
- The booking reference is eight characters over a thirty-symbol alphabet with no `0/O`, `1/I/L` or `U`, which is about 39 bits. That is comfortable for a campus van service and nowhere near collision-proof in general; the unique constraint decides, and a collision is reported as a conflict rather than trusted not to happen.
- This is all proven on H2, which reports lock contention as either a constraint violation or a lock failure depending on timing. Both are handled. Concurrency coverage against real PostgreSQL remains issue #10's.
