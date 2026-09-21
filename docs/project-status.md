# Project Status

## Current milestone

**Milestone 2 - Core booking, two issues open**

A student can sign in with LINE, list bookable trips, hold seats behind a database constraint, confirm the hold into a booking exactly once, read their own bookings, and cancel one — end to end, through the web UI. Two issues remain open against this milestone: [#41](https://github.com/NoelPOS/au-van-platform/issues/41), which blocks a real student from using the flow through the LIFF tunnel, and [#39](https://github.com/NoelPOS/au-van-platform/issues/39), a small pre-existing admin bug. Milestone 3 (payments and automation) has not started.

## Repository state

- Default branch: `main`, protected: "Web checks", "API checks", and "Commit checks" must pass, branches must be up to date, and the rule applies to administrators. "Infrastructure checks" and "Container checks" also run on every pull request but are deliberately not yet required; each is promoted once it has been green on real pull requests.
- Last merged implementation commit: `e7429b3` (`test(web): leave the confirmation for the trip list`)
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

## Current blockers

None.

Four limitations are known and deliberately deferred.

1. **Concurrency is proven on H2, not PostgreSQL.** The suite runs H2 in PostgreSQL mode, which establishes that the application logic is right — the lock reaches the query, decisions read the locked rows, the flushes make the catch blocks reachable — and establishes nothing about PostgreSQL's own `SELECT … FOR UPDATE` semantics under READ COMMITTED. [#10](https://github.com/NoelPOS/au-van-platform/issues/10) owns it.
2. **The LIFF flow has never run against a real API or a real LINE browser.** Its tests stub `fetch`. Before it is exercised through a tunnel, [#41](https://github.com/NoelPOS/au-van-platform/issues/41) must land: CORS `allowedOrigins` is hard-coded to the Vite dev server and is not environment-driven, so a tunnelled request is rejected on origin before any header matters.
3. **Infrastructure is written and statically checked, never executed.** It is designed and codified, not deployed, until the owner applies it.
4. **No end-to-end coverage.** There is no Playwright suite yet; [#10](https://github.com/NoelPOS/au-van-platform/issues/10) owns that too.

## Next step

[#41](https://github.com/NoelPOS/au-van-platform/issues/41) — it is what stands between the merged booking flow and a student actually using it through the LIFF tunnel. Then [#39](https://github.com/NoelPOS/au-van-platform/issues/39), a small pre-existing admin bug, before starting Milestone 3 at [#7](https://github.com/NoelPOS/au-van-platform/issues/7).

## Ordered backlog

The issue tracker is the durable implementation queue. Refine an issue when it becomes the next ready item; do not expand its scope without updating the issue and, when relevant, an ADR.

1. [#41 CORS allowed origins are hard-coded to the Vite dev server](https://github.com/NoelPOS/au-van-platform/issues/41) — blocks any real use of the LIFF flow
2. [#39 The admin trip form offers a status the API cannot accept](https://github.com/NoelPOS/au-van-platform/issues/39)
3. [#7 Review payment proofs and confirm bookings](https://github.com/NoelPOS/au-van-platform/issues/7)
4. [#8 Deliver asynchronous notifications and booking expiry processing](https://github.com/NoelPOS/au-van-platform/issues/8)
5. [#9 Add waitlist promotion and operational visibility](https://github.com/NoelPOS/au-van-platform/issues/9)
6. [#10 Add end-to-end, concurrency, CI/CD, and demo deployment coverage](https://github.com/NoelPOS/au-van-platform/issues/10)
7. [#11 Provision AWS-target infrastructure as code](https://github.com/NoelPOS/au-van-platform/issues/11)

Filed while building the above, none blocking: [#36](https://github.com/NoelPOS/au-van-platform/issues/36) and [#42](https://github.com/NoelPOS/au-van-platform/issues/42) (permission entries the loop needs, each the owner's to approve on its own), [#37](https://github.com/NoelPOS/au-van-platform/issues/37) (the commit guard accepts a trailer hidden in a comment), [#38](https://github.com/NoelPOS/au-van-platform/issues/38) (Terraform lock files, which need the binary), [#40](https://github.com/NoelPOS/au-van-platform/issues/40) (Node 20 is end-of-life), and [#43](https://github.com/NoelPOS/au-van-platform/issues/43) (`git -C` bypasses every git deny rule).

Completed: [#5](https://github.com/NoelPOS/au-van-platform/issues/5), [#6](https://github.com/NoelPOS/au-van-platform/issues/6), [#18](https://github.com/NoelPOS/au-van-platform/issues/18), [#19](https://github.com/NoelPOS/au-van-platform/issues/19), [#22](https://github.com/NoelPOS/au-van-platform/issues/22), [#24](https://github.com/NoelPOS/au-van-platform/issues/24), [#25](https://github.com/NoelPOS/au-van-platform/issues/25), [#26](https://github.com/NoelPOS/au-van-platform/issues/26), [#27](https://github.com/NoelPOS/au-van-platform/issues/27), [#29](https://github.com/NoelPOS/au-van-platform/issues/29), [#31](https://github.com/NoelPOS/au-van-platform/issues/31), [#32](https://github.com/NoelPOS/au-van-platform/issues/32), [#34](https://github.com/NoelPOS/au-van-platform/issues/34) — the transport inventory API and administration UI, the agent-driven delivery configuration and its hardening, seat holds, idempotent booking creation, the LIFF booking flow, the Flyway repair, the Terraform foundation, and the container images. Two issues remain open against Milestone 2: #41 and #39.
