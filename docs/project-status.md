# Project Status

## Current milestone

**Milestone 2 - Core booking in progress**

The legacy Next.js application has been cloned separately as a read-only migration reference. The rebuild repository has approved scope, architecture, data-model, AWS-target, ADR, issue-template, and pull-request conventions.

## Repository state

- Default branch: `main`, protected: "Web checks", "API checks", and "Commit checks" must pass, branches must be up to date, and the rule applies to administrators.
- Last merged implementation commit: `86b323c` (`fix(booking): release holds with the bulk delete too`)
- Active issues, running as concurrent tracks on disjoint file surfaces: [#27 migrations](https://github.com/NoelPOS/au-van-platform/issues/27) on `fix/apply-database-migrations`, [#29 delivery cycle](https://github.com/NoelPOS/au-van-platform/issues/29) on `chore/harden-delivery-cycle`, and [#32 Terraform foundation](https://github.com/NoelPOS/au-van-platform/issues/32) on `feature/terraform-foundation`
- This file is owned by the orchestrator. Implementers do not edit it, because every concurrent track would otherwise conflict on it.
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

## Current blockers

None. [#27](https://github.com/NoelPOS/au-van-platform/issues/27) — that Flyway auto-configuration moved to a module `api/build.gradle` never declared, so `V1`–`V3` had never been applied and Hibernate generated the schema from the mappings — is fixed and in review. Its fix also resolved the pre-existing `SeatLayout.replaceSeats` collision that only became visible once the migrations ran.

Two limitations are known and deliberately deferred. The API test suite runs on H2 in PostgreSQL mode rather than real PostgreSQL, so concurrency behaviour is proven only on H2; that belongs to [#10](https://github.com/NoelPOS/au-van-platform/issues/10). Infrastructure code is statically checked but never executed, so it is designed and codified rather than deployed until the owner applies it themselves.

## Next step

Merge the three tracks in review: [#27](https://github.com/NoelPOS/au-van-platform/issues/27), [#29](https://github.com/NoelPOS/au-van-platform/issues/29) and [#32](https://github.com/NoelPOS/au-van-platform/issues/32). Then [#31 Containerize the API and web build](https://github.com/NoelPOS/au-van-platform/issues/31), which blocks the AWS topology because no Dockerfile exists, followed by [#25 Create bookings idempotently](https://github.com/NoelPOS/au-van-platform/issues/25) and [#26 Build the LIFF seat booking flow](https://github.com/NoelPOS/au-van-platform/issues/26).

## Ordered backlog

The issue tracker is the durable implementation queue. Refine an issue when it becomes the next ready item; do not expand its scope without updating the issue and, when relevant, an ADR.

1. [#27 Database migrations have never been applied](https://github.com/NoelPOS/au-van-platform/issues/27) — in review
2. [#29 Harden the delivery cycle](https://github.com/NoelPOS/au-van-platform/issues/29) — in review
3. [#32 Establish the Terraform foundation](https://github.com/NoelPOS/au-van-platform/issues/32) — in review
4. [#31 Containerize the API and web build](https://github.com/NoelPOS/au-van-platform/issues/31)
5. [#25 Create bookings idempotently with an auditable state history](https://github.com/NoelPOS/au-van-platform/issues/25)
6. [#26 Build the LIFF seat booking flow](https://github.com/NoelPOS/au-van-platform/issues/26)
5. [#7 Review payment proofs and confirm bookings](https://github.com/NoelPOS/au-van-platform/issues/7)
6. [#8 Deliver asynchronous notifications and booking expiry processing](https://github.com/NoelPOS/au-van-platform/issues/8)
7. [#9 Add waitlist promotion and operational visibility](https://github.com/NoelPOS/au-van-platform/issues/9)
8. [#10 Add end-to-end, concurrency, CI/CD, and demo deployment coverage](https://github.com/NoelPOS/au-van-platform/issues/10)
9. [#11 Provision AWS-target infrastructure as code](https://github.com/NoelPOS/au-van-platform/issues/11)

Completed: [#5](https://github.com/NoelPOS/au-van-platform/issues/5) (parent), [#18](https://github.com/NoelPOS/au-van-platform/issues/18), [#19](https://github.com/NoelPOS/au-van-platform/issues/19), and [#22](https://github.com/NoelPOS/au-van-platform/issues/22) — the transport inventory API, administration UI, and agent-driven delivery configuration are merged.
