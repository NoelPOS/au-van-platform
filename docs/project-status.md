# Project Status

## Current milestone

**Milestone 2 - Core booking in progress**

The legacy Next.js application has been cloned separately as a read-only migration reference. The rebuild repository has approved scope, architecture, data-model, AWS-target, ADR, issue-template, and pull-request conventions.

## Repository state

- Default branch: `main`
- Last merged implementation commit: `237d894` (`feat(auth): establish LINE identity JWT boundary`)
- Active issue: [#16 Bootstrap initial administrator access](https://github.com/NoelPOS/au-van-platform/issues/16)
- Active branch: `feature/bootstrap-admin-access`
- Legacy reference: `/Users/noelpaingoaksoe/Desktop/AU-Van-reference` (not part of this repository)

## Accepted decisions

- Start with a modular monolith.
- Use React for the web application and Spring Boot for the API.
- Make PostgreSQL authoritative for booking correctness; use Redis as support infrastructure.
- Use low-cost demo hosting first while keeping an AWS-ready target topology.

## Current blockers

None.

## Next step

Implement and review [#16 Bootstrap initial administrator access](https://github.com/NoelPOS/au-van-platform/issues/16). The next ready issue after it is [#5 Manage routes, trips, and vehicle seat layouts](https://github.com/NoelPOS/au-van-platform/issues/5).

## Ordered backlog

The issue tracker is the durable implementation queue. Refine an issue when it becomes the next ready item; do not expand its scope without updating the issue and, when relevant, an ADR.

1. [#16 Bootstrap initial administrator access](https://github.com/NoelPOS/au-van-platform/issues/16)
2. [#5 Manage routes, trips, and vehicle seat layouts](https://github.com/NoelPOS/au-van-platform/issues/5)
3. [#6 Create concurrency-safe seat holds and bookings](https://github.com/NoelPOS/au-van-platform/issues/6)
4. [#7 Review payment proofs and confirm bookings](https://github.com/NoelPOS/au-van-platform/issues/7)
5. [#8 Deliver asynchronous notifications and booking expiry processing](https://github.com/NoelPOS/au-van-platform/issues/8)
6. [#9 Add waitlist promotion and operational visibility](https://github.com/NoelPOS/au-van-platform/issues/9)
7. [#10 Add end-to-end, concurrency, CI/CD, and demo deployment coverage](https://github.com/NoelPOS/au-van-platform/issues/10)
8. [#11 Provision AWS-target infrastructure as code](https://github.com/NoelPOS/au-van-platform/issues/11)
