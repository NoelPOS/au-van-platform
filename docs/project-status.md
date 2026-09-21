# Project Status

## Current milestone

**Milestone 2 - Core booking in progress**

The legacy Next.js application has been cloned separately as a read-only migration reference. The rebuild repository has approved scope, architecture, data-model, AWS-target, ADR, issue-template, and pull-request conventions.

## Repository state

- Default branch: `main`, protected: "Web checks", "API checks", and "Commit checks" must pass, branches must be up to date, and the rule applies to administrators.
- Last merged implementation commit: `c47e18a` (`chore(web): proxy local API for LIFF tunnel testing`)
- Active issue: [#22 Establish agent-driven delivery configuration](https://github.com/NoelPOS/au-van-platform/issues/22)
- Active branch: `chore/delivery-agent-configuration`
- Legacy reference: `/Users/noelpaingoaksoe/Desktop/AU-Van-reference` (not part of this repository)
- Delivery is agent-driven: roles in `.claude/agents/`, one cycle in `.claude/commands/next-issue.md`.

## Accepted decisions

- Start with a modular monolith.
- Use React for the web application and Spring Boot for the API.
- Make PostgreSQL authoritative for booking correctness; use Redis as support infrastructure.
- Use low-cost demo hosting first while keeping an AWS-ready target topology.

## Current blockers

None.

## Next step

Merge [#22 Establish agent-driven delivery configuration](https://github.com/NoelPOS/au-van-platform/issues/22). The next ready issue after it is [#6 Create concurrency-safe seat holds and bookings](https://github.com/NoelPOS/au-van-platform/issues/6), which will be split into three focused sub-issues: the seat availability and holds API, the booking and idempotency API, and the LIFF booking flow.

## Ordered backlog

The issue tracker is the durable implementation queue. Refine an issue when it becomes the next ready item; do not expand its scope without updating the issue and, when relevant, an ADR.

1. [#22 Establish agent-driven delivery configuration](https://github.com/NoelPOS/au-van-platform/issues/22)
2. [#6 Create concurrency-safe seat holds and bookings](https://github.com/NoelPOS/au-van-platform/issues/6)
3. [#7 Review payment proofs and confirm bookings](https://github.com/NoelPOS/au-van-platform/issues/7)
4. [#8 Deliver asynchronous notifications and booking expiry processing](https://github.com/NoelPOS/au-van-platform/issues/8)
5. [#9 Add waitlist promotion and operational visibility](https://github.com/NoelPOS/au-van-platform/issues/9)
6. [#10 Add end-to-end, concurrency, CI/CD, and demo deployment coverage](https://github.com/NoelPOS/au-van-platform/issues/10)
7. [#11 Provision AWS-target infrastructure as code](https://github.com/NoelPOS/au-van-platform/issues/11)

Completed: [#5](https://github.com/NoelPOS/au-van-platform/issues/5) (parent), [#18](https://github.com/NoelPOS/au-van-platform/issues/18), and [#19](https://github.com/NoelPOS/au-van-platform/issues/19) — the transport inventory API and administration UI are merged.
