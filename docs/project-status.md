# Project Status

## Current milestone

**Milestone 3 - Payments and automation, in progress**

Milestone 2 (core booking) is complete. [#7](https://github.com/NoelPOS/au-van-platform/issues/7) (review payment proofs and confirm bookings) is done: a student can sign in, list trips, hold seats, submit a payment-proof image for an eligible booking, and see it reviewed; an administrator can list submitted proofs, view each image, and approve or reject with a recorded decision — approval confirms the booking, rejection lets the student resubmit. Proof images stay private, reachable only through the authenticated API. Delivered as two focused sub-issues, [#51](https://github.com/NoelPOS/au-van-platform/issues/51) (submission and storage) and [#52](https://github.com/NoelPOS/au-van-platform/issues/52) (admin review), matching how #5 was delivered through #18/#19. Next: [#8](https://github.com/NoelPOS/au-van-platform/issues/8), asynchronous notifications and booking expiry processing.

## Repository state

- Default branch: `main`, protected: "Web checks", "API checks", and "Commit checks" must pass, branches must be up to date, and the rule applies to administrators. "Infrastructure checks" and "Container checks" also run on every pull request but are deliberately not yet required; each is promoted once it has been green on real pull requests.
- Last merged implementation commit: `b0ae36a` (`test(web): keep the approve assertion off an optional chain`)
- No track currently in review.
- This file is owned by the orchestrator. Implementers do not edit it, because every concurrent track would otherwise conflict on it. It is landed as its own `docs/` pull request after each merge.
- Legacy reference: `/Users/noelpaingoaksoe/Desktop/AU-Van-reference` (not part of this repository)
- Delivery is agent-driven: roles in `.claude/agents/`, one cycle in `.claude/commands/next-issue.md`.

## Accepted decisions

- Start with a modular monolith.
- Use React for the web application and Spring Boot for the API.
- Make PostgreSQL authoritative for booking correctness; use Redis as support infrastructure.
- Use low-cost demo hosting first while keeping an AWS-ready target topology.
- Verify LINE identity server-side and issue short-lived AU-Van JWTs.
- Hold and book seats in one `seat_claims` table, with lazy hold expiry and no Redis.
- Write the AWS target topology in Terraform, statically checked and never applied by an agent.
- Make booking creation exactly-once with a stored-response idempotency record and a row lock on the hold's claims ([ADR-008](adr/008-exactly-once-booking-creation.md)).
- Store payment-proof images in a private, API-brokered object store (no presigned URL, no credential ever reaches the browser), and extend `BookingStatus` itself rather than a parallel payment table ([ADR-009](adr/009-payment-proof-storage-and-review-gate.md)).

## Current blockers

None.

Three limitations are known and deliberately deferred.

1. **Concurrency is proven on H2, not PostgreSQL.** The suite runs H2 in PostgreSQL mode, which establishes that the application logic is right — the lock reaches the query, decisions read the locked rows, the flushes make the catch blocks reachable — and establishes nothing about PostgreSQL's own `SELECT … FOR UPDATE` semantics under READ COMMITTED. [#10](https://github.com/NoelPOS/au-van-platform/issues/10) owns it.
2. **Infrastructure is written and statically checked, never executed.** It is designed and codified, not deployed, until the owner applies it.
3. **No end-to-end coverage.** There is no Playwright suite yet; [#10](https://github.com/NoelPOS/au-van-platform/issues/10) owns it.
4. **A booking under payment review holds its seat indefinitely.** `seat_claims` has no independent expiry once a claim carries a `booking_id`, so a booking sitting in `PAYMENT_UNDER_REVIEW` or `PAYMENT_REJECTED` never times out on its own — an explicit, accepted consequence of [ADR-009](adr/009-payment-proof-storage-and-review-gate.md). [#8](https://github.com/NoelPOS/au-van-platform/issues/8) owns giving it a bound.

## Next step

[#8](https://github.com/NoelPOS/au-van-platform/issues/8), asynchronous notifications and booking expiry processing.

## Ordered backlog

The issue tracker is the durable implementation queue. Refine an issue when it becomes the next ready item; do not expand its scope without updating the issue and, when relevant, an ADR.

1. [#8 Deliver asynchronous notifications and booking expiry processing](https://github.com/NoelPOS/au-van-platform/issues/8)
2. [#9 Add waitlist promotion and operational visibility](https://github.com/NoelPOS/au-van-platform/issues/9)
3. [#10 Add end-to-end, concurrency, CI/CD, and demo deployment coverage](https://github.com/NoelPOS/au-van-platform/issues/10)
4. [#11 Provision AWS-target infrastructure as code](https://github.com/NoelPOS/au-van-platform/issues/11)

Filed while building the above, none blocking: [#36](https://github.com/NoelPOS/au-van-platform/issues/36) and [#42](https://github.com/NoelPOS/au-van-platform/issues/42) (permission entries the loop needs, each the owner's to approve on its own), [#38](https://github.com/NoelPOS/au-van-platform/issues/38) (Terraform lock files, which need the binary), and [#43](https://github.com/NoelPOS/au-van-platform/issues/43) (`git -C` bypasses every git deny rule).

Completed: [#5](https://github.com/NoelPOS/au-van-platform/issues/5), [#6](https://github.com/NoelPOS/au-van-platform/issues/6), [#7](https://github.com/NoelPOS/au-van-platform/issues/7), [#18](https://github.com/NoelPOS/au-van-platform/issues/18), [#19](https://github.com/NoelPOS/au-van-platform/issues/19), [#22](https://github.com/NoelPOS/au-van-platform/issues/22), [#24](https://github.com/NoelPOS/au-van-platform/issues/24), [#25](https://github.com/NoelPOS/au-van-platform/issues/25), [#26](https://github.com/NoelPOS/au-van-platform/issues/26), [#27](https://github.com/NoelPOS/au-van-platform/issues/27), [#29](https://github.com/NoelPOS/au-van-platform/issues/29), [#31](https://github.com/NoelPOS/au-van-platform/issues/31), [#32](https://github.com/NoelPOS/au-van-platform/issues/32), [#34](https://github.com/NoelPOS/au-van-platform/issues/34), [#41](https://github.com/NoelPOS/au-van-platform/issues/41), [#39](https://github.com/NoelPOS/au-van-platform/issues/39), [#37](https://github.com/NoelPOS/au-van-platform/issues/37), [#40](https://github.com/NoelPOS/au-van-platform/issues/40), [#51](https://github.com/NoelPOS/au-van-platform/issues/51), [#52](https://github.com/NoelPOS/au-van-platform/issues/52) — the transport inventory API and administration UI, the agent-driven delivery configuration and its hardening, seat holds, idempotent booking creation, the LIFF booking flow, the Flyway repair, the Terraform foundation, the container images, the CORS origin fix, the admin trip status enum fix that closes Milestone 2, the commit-msg guard's comment-trailer fix, the Node 22 toolchain move, and payment-proof submission, storage, and admin review.
