# Project Status

## Current milestone

**Milestone 0 - Planning foundation complete**

The legacy Next.js application has been cloned separately as a read-only migration reference. The rebuild repository has approved scope, architecture, data-model, AWS-target, ADR, issue-template, and pull-request conventions.

## Repository state

- Default branch: `main`
- Last merged planning commit: `9d4e145` (`docs: standardize issue and pull request conventions`)
- Active issue: [#12 Define project continuation and backlog protocol](https://github.com/NoelPOS/au-van-platform/issues/12)
- Active branch: `docs/project-continuation-protocol`
- Legacy reference: `/Users/noelpaingoaksoe/Desktop/AU-Van-reference` (not part of this repository)

## Accepted decisions

- Start with a modular monolith.
- Use React for the web application and Spring Boot for the API.
- Make PostgreSQL authoritative for booking correctness; use Redis as support infrastructure.
- Use low-cost demo hosting first while keeping an AWS-ready target topology.

## Current blockers

None.

## Next step

Review and merge issue #12. Then start [#3 Bootstrap local React and Spring Boot development environment](https://github.com/NoelPOS/au-van-platform/issues/3).

## Ordered backlog

The issue tracker is the durable implementation queue. Refine an issue when it becomes the next ready item; do not expand its scope without updating the issue and, when relevant, an ADR.

1. [#3 Bootstrap local React and Spring Boot development environment](https://github.com/NoelPOS/au-van-platform/issues/3)
2. [#4 Establish identity and LINE authentication boundaries](https://github.com/NoelPOS/au-van-platform/issues/4)
3. [#5 Manage routes, trips, and vehicle seat layouts](https://github.com/NoelPOS/au-van-platform/issues/5)
4. [#6 Create concurrency-safe seat holds and bookings](https://github.com/NoelPOS/au-van-platform/issues/6)
5. [#7 Review payment proofs and confirm bookings](https://github.com/NoelPOS/au-van-platform/issues/7)
6. [#8 Deliver asynchronous notifications and booking expiry processing](https://github.com/NoelPOS/au-van-platform/issues/8)
7. [#9 Add waitlist promotion and operational visibility](https://github.com/NoelPOS/au-van-platform/issues/9)
8. [#10 Add end-to-end, concurrency, CI/CD, and demo deployment coverage](https://github.com/NoelPOS/au-van-platform/issues/10)
9. [#11 Provision AWS-target infrastructure as code](https://github.com/NoelPOS/au-van-platform/issues/11)
