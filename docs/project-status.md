# Project Status

## Current milestone

**Milestone 4 - Quality and deployment, in progress**

Milestones 1–3 are complete, and every feature is built and tested. Milestone 4 has delivered real-PostgreSQL concurrency proof and Playwright end-to-end coverage ([#10](https://github.com/NoelPOS/au-van-platform/issues/10)), the demo AWS topology as statically validated Terraform ([#75](https://github.com/NoelPOS/au-van-platform/issues/75)), and a simplicity pass:

- **Rules.** `AGENTS.md` has a Simplicity section: near-zero comments, file size limits, a classic flat web layout, and no unused files ([#92](https://github.com/NoelPOS/au-van-platform/issues/92)).
- **API comments.** Comment lines in the API went from 3,909 to 89 in a comments-only change. The rationale they carried moved into ADR-008, 010 and 011 ([#94](https://github.com/NoelPOS/au-van-platform/issues/94)).
- **Web layout.** The web app now uses `components/`, `pages/`, `hooks/`, `services/`, `types/` and `utils/`. React Router gives real URLs for `/admin/inventory`, `/admin/payments` and `/admin/operations`, behind a role guard. A CloudFront Function on the default behaviour serves those URLs on reload ([#93](https://github.com/NoelPOS/au-van-platform/issues/93)).
- **Local object store.** The pinned MinIO image stopped pulling anonymously, so the local and CI object store is now the Versity S3 Gateway ([#88](https://github.com/NoelPOS/au-van-platform/issues/88)).
- **Payment review fix.** Switching payment slips in admin review no longer shows the previous slip ([#96](https://github.com/NoelPOS/au-van-platform/issues/96)).

Since then:

- **Live demo.** The app runs at `auvan.duckdns.org` on one EC2 host, and every green build of `main` deploys to it over an SSH key that can only run `deploy/deploy.sh` (ADR-014, ADR-015). The AWS topology was applied once on 2026-10-04 to capture the evidence in the README, then destroyed.
- **Product.** A rebuilt student app around departures and tickets, PromptPay payment instructions, fair booking rules (ADR-016), trip cancellation, moves and refunds (ADR-017), live updates over server-sent events (ADR-018), a trip calendar with day templates (ADR-019), flags for reused payment slips, demo sign-in and a landing page (ADR-020), the LINE rich menu, and an intro video.
- **Route-first booking** ([#157](https://github.com/NoelPOS/au-van-platform/pull/157), [#159](https://github.com/NoelPOS/au-van-platform/pull/159), [#160](https://github.com/NoelPOS/au-van-platform/pull/160)). Students pick a route, with From and To filters and routes grouped by origin, before a day and a time. Departures show their arrival time. The admin Payments link shows how many slips are waiting, and so does the browser tab title.
- **Rolling schedule** ([#158](https://github.com/NoelPOS/au-van-platform/pull/158), ADR-021). On the demo host the API fills the next seven days from the "Weekday" day template at startup and every night at 00:05 Bangkok time.
- **Insights** ([#162](https://github.com/NoelPOS/au-van-platform/pull/162)). `/admin/insights` shows revenue, seats sold, occupancy and refunds owed for a period, with breakdowns by route and by day.
- **In-use guards** ([#163](https://github.com/NoelPOS/au-van-platform/pull/163), closes [#161](https://github.com/NoelPOS/au-van-platform/issues/161)). A van or route with upcoming trips cannot be made inactive, and a seat layout a van uses cannot be deleted.
- **Shuttle generator** ([#164](https://github.com/NoelPOS/au-van-platform/pull/164)). The day template editor builds an out-and-back run for one van from a first and last departure and a wait at each end, and refuses a run that clashes with the template's existing lines.
- **Route identity in admin** ([#165](https://github.com/NoelPOS/au-van-platform/pull/165), ADR-022). Each route has a colour shared by both directions and a short label. The calendar and list group and colour departures by route, and Trips and Payments filter by route, kept in `?routes=`.
- **Operations folded into Trips** ([#166](https://github.com/NoelPOS/au-van-platform/pull/166), ADR-022). Opening a departure shows Passengers and Edit tabs, failed notifications are listed on the Overview, and `/admin/operations` redirects to `/admin/trips`.

Still open: [#76](https://github.com/NoelPOS/au-van-platform/issues/76), the owner's rehearsed deployment and teardown of the AWS topology, which closes [#11](https://github.com/NoelPOS/au-van-platform/issues/11).

## Repository state

- Default branch: `main`, protected: "Web checks", "API checks", and "Commit checks" must pass, branches must be up to date, and the rule applies to administrators. "Infrastructure checks" and "Container checks" also run on every pull request but are deliberately not yet required; each is promoted once it has been green on real pull requests. Two more non-required jobs joined this milestone, on the same footing: "Database concurrency checks" and "End-to-end checks" — promote each once it has been green on two or three real pull requests.
- Last merged implementation commit: `b3ec38b` (`test(web): read a trip's passengers from the trips page end to end`)
- In review: [#167](https://github.com/NoelPOS/au-van-platform/pull/167), refreshed screenshots for route-first booking and the Trips Passengers tab.
- This file is owned by the orchestrator. Implementers do not edit it, because every concurrent track would otherwise conflict on it. It is landed as its own `docs/` pull request after each merge.
- Legacy reference: `/Users/noelpaingoaksoe/Desktop/AU-Van-reference` (not part of this repository)
- Delivery is agent-driven: roles in `.claude/agents/`, one cycle in `.claude/commands/next-issue.md`.
- Since 2026-10-01 the owner has dropped the independent review step. Pull requests merge once CI is green.
- Agents may read and edit the ignored `.env` files ([#90](https://github.com/NoelPOS/au-van-platform/issues/90)).
- Every `aws` command stops at a permission prompt for the owner ([#86](https://github.com/NoelPOS/au-van-platform/issues/86)).
- `terraform apply` and `terraform destroy` stay denied to agents.

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
- Keep code simple: near-zero comments, small files, a classic flat React layout with React Router, and no unused files (`AGENTS.md`, Simplicity).
- Sign in to the end-to-end suite through a configurable LINE verification host and a build-time-only, bundle-excluded test sign-in path, rather than a stub bean in main sources ([ADR-013](adr/013-end-to-end-sign-in-without-a-line-channel.md)).
- Run the live demo on one EC2 host with Docker Compose, deployed from CI over a forced-command SSH key ([ADR-014](adr/014-free-always-on-host-with-ssh-deploys.md), [ADR-015](adr/015-live-demo-on-an-ec2-host.md)).
- Enforce fair booking rules at hold and at booking ([ADR-016](adr/016-fair-booking-rules.md)).
- Cancel and move trips in one locked transaction, and track refunds on the booking ([ADR-017](adr/017-trip-cancellation-cascade-and-refund-tracking.md)).
- Push live updates as server-sent invalidation signals fanned out by PostgreSQL `LISTEN/NOTIFY` ([ADR-018](adr/018-live-updates-over-sse-and-listen-notify.md)).
- Plan departures in batches from day templates, previewed and applied exactly once ([ADR-019](adr/019-day-templates-and-batch-scheduling.md)).
- Sign in to the live demo without LINE, behind a switch that is off by default ([ADR-020](adr/020-demo-sign-in.md)).
- Keep the next week scheduled from a day template with an in-app job, off by default ([ADR-021](adr/021-rolling-schedule-from-a-day-template.md)).
- Students pick a route first; staff views stay organised by day and use route colours derived in the browser ([ADR-022](adr/022-route-first-navigation-and-route-identity.md)).

## Current blockers

None.

One limitation is known and deliberately deferred.

1. **The AWS topology is not running.** It was applied once for evidence on 2026-10-04 and destroyed the same day to save cost. The live demo runs on the EC2 host instead. [#76](https://github.com/NoelPOS/au-van-platform/issues/76) stays open until the owner has rehearsed deployment and teardown end to end.

## Next step

Prepare the live demo for the owner's Gosoft interview on 2026-11-07. Merge [#167](https://github.com/NoelPOS/au-van-platform/pull/167) so the README and landing screenshots match route-first booking and the Trips Passengers tab. Then rehearse the walk-through on `auvan.duckdns.org`: a demo student picks a route, holds a seat and sends a slip; demo staff approve it, open the departure's Passengers tab and show Insights. Check that the rolling schedule has filled the week. Until the interview, only fix what the rehearsal finds broken.

[#76](https://github.com/NoelPOS/au-van-platform/issues/76) follows: the owner's rehearsed deployment and teardown of the AWS topology. An agent can help with `aws` commands, but only after the owner approves each one.

## Ordered backlog

The issue tracker is the durable implementation queue. Refine an issue when it becomes the next ready item; do not expand its scope without updating the issue and, when relevant, an ADR.

1. [#76 Demo deployment secrets contract and runbooks](https://github.com/NoelPOS/au-van-platform/issues/76) — owner action; [#11](https://github.com/NoelPOS/au-van-platform/issues/11) closes once this does.

Filed while building the above, none blocking: [#36](https://github.com/NoelPOS/au-van-platform/issues/36) and [#42](https://github.com/NoelPOS/au-van-platform/issues/42) (permission entries the loop needs, each the owner's to approve on its own), [#38](https://github.com/NoelPOS/au-van-platform/issues/38) (Terraform lock files, which need the binary), and [#43](https://github.com/NoelPOS/au-van-platform/issues/43) (`git -C` bypasses every git deny rule).

Completed: [#5](https://github.com/NoelPOS/au-van-platform/issues/5), [#6](https://github.com/NoelPOS/au-van-platform/issues/6), [#7](https://github.com/NoelPOS/au-van-platform/issues/7), [#8](https://github.com/NoelPOS/au-van-platform/issues/8), [#9](https://github.com/NoelPOS/au-van-platform/issues/9), [#10](https://github.com/NoelPOS/au-van-platform/issues/10), [#18](https://github.com/NoelPOS/au-van-platform/issues/18), [#19](https://github.com/NoelPOS/au-van-platform/issues/19), [#22](https://github.com/NoelPOS/au-van-platform/issues/22), [#24](https://github.com/NoelPOS/au-van-platform/issues/24), [#25](https://github.com/NoelPOS/au-van-platform/issues/25), [#26](https://github.com/NoelPOS/au-van-platform/issues/26), [#27](https://github.com/NoelPOS/au-van-platform/issues/27), [#29](https://github.com/NoelPOS/au-van-platform/issues/29), [#31](https://github.com/NoelPOS/au-van-platform/issues/31), [#32](https://github.com/NoelPOS/au-van-platform/issues/32), [#34](https://github.com/NoelPOS/au-van-platform/issues/34), [#41](https://github.com/NoelPOS/au-van-platform/issues/41), [#39](https://github.com/NoelPOS/au-van-platform/issues/39), [#37](https://github.com/NoelPOS/au-van-platform/issues/37), [#40](https://github.com/NoelPOS/au-van-platform/issues/40), [#51](https://github.com/NoelPOS/au-van-platform/issues/51), [#52](https://github.com/NoelPOS/au-van-platform/issues/52), [#61](https://github.com/NoelPOS/au-van-platform/issues/61), [#62](https://github.com/NoelPOS/au-van-platform/issues/62), [#63](https://github.com/NoelPOS/au-van-platform/issues/63), [#68](https://github.com/NoelPOS/au-van-platform/issues/68), [#69](https://github.com/NoelPOS/au-van-platform/issues/69), [#70](https://github.com/NoelPOS/au-van-platform/issues/70), [#75](https://github.com/NoelPOS/au-van-platform/issues/75), [#77](https://github.com/NoelPOS/au-van-platform/issues/77), [#78](https://github.com/NoelPOS/au-van-platform/issues/78), [#79](https://github.com/NoelPOS/au-van-platform/issues/79), [#80](https://github.com/NoelPOS/au-van-platform/issues/80), [#86](https://github.com/NoelPOS/au-van-platform/issues/86), [#88](https://github.com/NoelPOS/au-van-platform/issues/88), [#90](https://github.com/NoelPOS/au-van-platform/issues/90), [#92](https://github.com/NoelPOS/au-van-platform/issues/92), [#93](https://github.com/NoelPOS/au-van-platform/issues/93), [#94](https://github.com/NoelPOS/au-van-platform/issues/94), [#96](https://github.com/NoelPOS/au-van-platform/issues/96) — the transport inventory API and administration UI, the agent-driven delivery configuration and its hardening, seat holds, idempotent booking creation, the LIFF booking flow, the Flyway repair, the Terraform foundation, the container images, the CORS origin fix, the admin trip status enum fix that closes Milestone 2, the commit-msg guard's comment-trailer fix, the Node 22 toolchain move, payment-proof submission, storage, and admin review, the transactional outbox, booking expiry, and LINE delivery that close out #8, the waitlist join/promotion/admin-visibility work that closes out #9 and Milestone 3, real-PostgreSQL concurrency proof and Playwright end-to-end coverage that close out #10, the demo hosting topology that closes out #75, and the simplicity pass (rules, API comments, flat web layout with React Router, Versity object store, payment-slip fix).
