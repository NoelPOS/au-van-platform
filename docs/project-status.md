# Project Status

## Current milestone

**Milestone 4 - Quality and deployment, in progress**

Milestone 3 (payments and automation) is complete. [#10](https://github.com/NoelPOS/au-van-platform/issues/10) is done: the concurrency guarantees already proven on H2 are now additionally proven against a real PostgreSQL 17 container (Testcontainers, a non-required `Database concurrency checks` CI job), and a Playwright suite exercises the critical student and admin journeys end to end, including failure paths and an authorization-boundary check, in a non-required `End-to-end checks` job — the E2E sign-in seam is a configurable LINE verification host plus a build-time-only, bundle-excluded test sign-in path ([ADR-013](adr/013-end-to-end-sign-in-without-a-line-channel.md)), safe by construction since a fake token only verifies against a fake host the real deployed config never points at. [#11](https://github.com/NoelPOS/au-van-platform/issues/11) (production-target infrastructure) is in progress: its demo-hosting half, [#75](https://github.com/NoelPOS/au-van-platform/issues/75), is done — a full demo topology (ECR, RDS, ECS Fargate, ALB, CloudFront, IAM, monitoring) as statically validated Terraform, never applied by an agent, with secret delivery via RDS-managed and SSM-referenced values that Terraform itself never creates or reads ([ADR-012](adr/012-deployed-secret-delivery.md)). #10 was delivered as [#77](https://github.com/NoelPOS/au-van-platform/issues/77)/[#78](https://github.com/NoelPOS/au-van-platform/issues/78), #75 as [#79](https://github.com/NoelPOS/au-van-platform/issues/79)/[#80](https://github.com/NoelPOS/au-van-platform/issues/80). Next: [#76](https://github.com/NoelPOS/au-van-platform/issues/76), the secrets contract and deployment/teardown runbooks — this is the repository owner's own work, not an agent's; #11 closes once it's done.

## Repository state

- Default branch: `main`, protected: "Web checks", "API checks", and "Commit checks" must pass, branches must be up to date, and the rule applies to administrators. "Infrastructure checks" and "Container checks" also run on every pull request but are deliberately not yet required; each is promoted once it has been green on real pull requests. Two more non-required jobs joined this milestone, on the same footing: "Database concurrency checks" and "End-to-end checks" — promote each once it has been green on two or three real pull requests.
- Last merged implementation commit: `45f7b7d` (`docs: correct two counts in the e2e overlay header`)
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
- Record outbound work in a transactional outbox in PostgreSQL rather than Redis or a message broker, and give every unpaid booking its own deadline rather than sweeping on a separate timer ([ADR-010](adr/010-transactional-outbox-and-booking-deadline.md)).
- Promote a waitlisted student by a scheduled sweep rather than hooking the seat-release call sites, and give a promotion a seat hold rather than a booking ([ADR-011](adr/011-waitlist-promotion-by-sweep.md)).
- Deliver deployed secrets through RDS-managed Secrets Manager and pre-created SSM parameters that Terraform never creates or reads, rather than any value passing through tfvars or state ([ADR-012](adr/012-deployed-secret-delivery.md)).
- Sign in to the end-to-end suite through a configurable LINE verification host and a build-time-only, bundle-excluded test sign-in path, rather than a stub bean in main sources ([ADR-013](adr/013-end-to-end-sign-in-without-a-line-channel.md)).

## Current blockers

None.

One limitation is known and deliberately deferred.

1. **Infrastructure is written and statically checked, never executed.** The demo topology ([#75](https://github.com/NoelPOS/au-van-platform/issues/75)) is designed and codified, not deployed, until the owner applies it. [#76](https://github.com/NoelPOS/au-van-platform/issues/76) is the owner's runbook for doing so.

## Next step

[#76](https://github.com/NoelPOS/au-van-platform/issues/76), the demo deployment secrets contract and runbooks — the repository owner's own work: applying the budget guardrail and the demo topology, creating the SSM parameters, running the first deployment, verifying the health check and smoke test, and rehearsing teardown. No agent performs any part of this.

## Ordered backlog

The issue tracker is the durable implementation queue. Refine an issue when it becomes the next ready item; do not expand its scope without updating the issue and, when relevant, an ADR.

1. [#76 Demo deployment secrets contract and runbooks](https://github.com/NoelPOS/au-van-platform/issues/76) — owner action; [#11](https://github.com/NoelPOS/au-van-platform/issues/11) closes once this does.

Filed while building the above, none blocking: [#36](https://github.com/NoelPOS/au-van-platform/issues/36) and [#42](https://github.com/NoelPOS/au-van-platform/issues/42) (permission entries the loop needs, each the owner's to approve on its own), [#38](https://github.com/NoelPOS/au-van-platform/issues/38) (Terraform lock files, which need the binary), and [#43](https://github.com/NoelPOS/au-van-platform/issues/43) (`git -C` bypasses every git deny rule).

Completed: [#5](https://github.com/NoelPOS/au-van-platform/issues/5), [#6](https://github.com/NoelPOS/au-van-platform/issues/6), [#7](https://github.com/NoelPOS/au-van-platform/issues/7), [#8](https://github.com/NoelPOS/au-van-platform/issues/8), [#9](https://github.com/NoelPOS/au-van-platform/issues/9), [#10](https://github.com/NoelPOS/au-van-platform/issues/10), [#18](https://github.com/NoelPOS/au-van-platform/issues/18), [#19](https://github.com/NoelPOS/au-van-platform/issues/19), [#22](https://github.com/NoelPOS/au-van-platform/issues/22), [#24](https://github.com/NoelPOS/au-van-platform/issues/24), [#25](https://github.com/NoelPOS/au-van-platform/issues/25), [#26](https://github.com/NoelPOS/au-van-platform/issues/26), [#27](https://github.com/NoelPOS/au-van-platform/issues/27), [#29](https://github.com/NoelPOS/au-van-platform/issues/29), [#31](https://github.com/NoelPOS/au-van-platform/issues/31), [#32](https://github.com/NoelPOS/au-van-platform/issues/32), [#34](https://github.com/NoelPOS/au-van-platform/issues/34), [#41](https://github.com/NoelPOS/au-van-platform/issues/41), [#39](https://github.com/NoelPOS/au-van-platform/issues/39), [#37](https://github.com/NoelPOS/au-van-platform/issues/37), [#40](https://github.com/NoelPOS/au-van-platform/issues/40), [#51](https://github.com/NoelPOS/au-van-platform/issues/51), [#52](https://github.com/NoelPOS/au-van-platform/issues/52), [#61](https://github.com/NoelPOS/au-van-platform/issues/61), [#62](https://github.com/NoelPOS/au-van-platform/issues/62), [#63](https://github.com/NoelPOS/au-van-platform/issues/63), [#68](https://github.com/NoelPOS/au-van-platform/issues/68), [#69](https://github.com/NoelPOS/au-van-platform/issues/69), [#70](https://github.com/NoelPOS/au-van-platform/issues/70), [#75](https://github.com/NoelPOS/au-van-platform/issues/75), [#77](https://github.com/NoelPOS/au-van-platform/issues/77), [#78](https://github.com/NoelPOS/au-van-platform/issues/78), [#79](https://github.com/NoelPOS/au-van-platform/issues/79), [#80](https://github.com/NoelPOS/au-van-platform/issues/80) — the transport inventory API and administration UI, the agent-driven delivery configuration and its hardening, seat holds, idempotent booking creation, the LIFF booking flow, the Flyway repair, the Terraform foundation, the container images, the CORS origin fix, the admin trip status enum fix that closes Milestone 2, the commit-msg guard's comment-trailer fix, the Node 22 toolchain move, payment-proof submission, storage, and admin review, the transactional outbox, booking expiry, and LINE delivery that close out #8, the waitlist join/promotion/admin-visibility work that closes out #9 and Milestone 3, real-PostgreSQL concurrency proof and Playwright end-to-end coverage that close out #10, and the demo hosting topology that closes out #75.
