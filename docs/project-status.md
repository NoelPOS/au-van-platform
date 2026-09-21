# Project Status

## Current milestone

**Milestone 2 - Core booking in progress**

The legacy Next.js application has been cloned separately as a read-only migration reference. The rebuild repository has approved scope, architecture, data-model, AWS-target, ADR, issue-template, and pull-request conventions.

## Repository state

- Default branch: `main`, protected: "Web checks", "API checks", and "Commit checks" must pass, branches must be up to date, and the rule applies to administrators.
- Last merged implementation commit: `c47e18a` (`chore(web): proxy local API for LIFF tunnel testing`)
- Active issue: [#24 Provide seat availability and concurrency-safe seat holds](https://github.com/NoelPOS/au-van-platform/issues/24)
- Active branch: `feature/seat-holds`
- Legacy reference: `/Users/noelpaingoaksoe/Desktop/AU-Van-reference` (not part of this repository)
- Delivery is agent-driven: roles in `.claude/agents/`, one cycle in `.claude/commands/next-issue.md`.

## Accepted decisions

- Start with a modular monolith.
- Use React for the web application and Spring Boot for the API.
- Make PostgreSQL authoritative for booking correctness; use Redis as support infrastructure.
- Use low-cost demo hosting first while keeping an AWS-ready target topology.
- Verify LINE identity server-side and issue short-lived AU-Van JWTs.
- Hold and book seats in one `seat_claims` table, with lazy hold expiry and no Redis.

## Current blockers

None blocking #24. One defect found while implementing it and deliberately left for its own issue: Spring Boot 4 moved Flyway auto-configuration into the `spring-boot-flyway` module, which `api/build.gradle` does not declare, so no Flyway bean is created and the `V1`–`V3` migrations never run. Hibernate's embedded-database `create-drop` builds the schema instead, in tests and in any environment using this classpath. Adding the module makes the migrations run and immediately exposes a second, pre-existing defect in `SeatLayoutService.update`, which re-inserts layout seats before deleting the old ones and violates `seat_layout_seats_label_unique`. Both belong in one focused fix.

## Next step

Review and merge [#24 Provide seat availability and concurrency-safe seat holds](https://github.com/NoelPOS/au-van-platform/issues/24) on `feature/seat-holds`, then raise the Flyway defect above as its own issue. The next ready implementation issue is [#25 Create bookings idempotently with an auditable state history](https://github.com/NoelPOS/au-van-platform/issues/25), followed by [#26 Build the LIFF seat booking flow](https://github.com/NoelPOS/au-van-platform/issues/26).

## Ordered backlog

The issue tracker is the durable implementation queue. Refine an issue when it becomes the next ready item; do not expand its scope without updating the issue and, when relevant, an ADR.

1. [#24 Provide seat availability and concurrency-safe seat holds](https://github.com/NoelPOS/au-van-platform/issues/24) — in review
2. [#25 Create bookings idempotently with an auditable state history](https://github.com/NoelPOS/au-van-platform/issues/25)
3. [#26 Build the LIFF seat booking flow](https://github.com/NoelPOS/au-van-platform/issues/26)
4. [#7 Review payment proofs and confirm bookings](https://github.com/NoelPOS/au-van-platform/issues/7)
5. [#8 Deliver asynchronous notifications and booking expiry processing](https://github.com/NoelPOS/au-van-platform/issues/8)
6. [#9 Add waitlist promotion and operational visibility](https://github.com/NoelPOS/au-van-platform/issues/9)
7. [#10 Add end-to-end, concurrency, CI/CD, and demo deployment coverage](https://github.com/NoelPOS/au-van-platform/issues/10)
8. [#11 Provision AWS-target infrastructure as code](https://github.com/NoelPOS/au-van-platform/issues/11)

Completed: [#5](https://github.com/NoelPOS/au-van-platform/issues/5) (parent), [#18](https://github.com/NoelPOS/au-van-platform/issues/18), [#19](https://github.com/NoelPOS/au-van-platform/issues/19), and [#22](https://github.com/NoelPOS/au-van-platform/issues/22) — the transport inventory API, administration UI, and agent-driven delivery configuration are merged.
