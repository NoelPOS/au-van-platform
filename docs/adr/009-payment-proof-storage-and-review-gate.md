# ADR-009: Payment-Proof Object Storage and the Payment-Review Gate on Booking Confirmation

## Status

Proposed

## Context

Today a booking is created already `CONFIRMED`: ADR-008 made confirmation exactly-once, but nothing sits between "seats secured" and "money collected." `BookingStatus`'s own doc comment already earmarks this for #7: "Payment states arrive with #7."

Issue #7 asks a student to submit a payment-proof image for a booking and a staff member to approve or reject it, with the decision recorded and object access controlled so that "credentials are never client-exposed." Two things have to be decided together, because they constrain each other: where the proof image lives, and what a booking's status means while a human is looking at it.

The legacy reference application (`~/Desktop/AU-Van-reference`) already solved a version of this and is instructive both for what to keep and what to reject. Its `PaymentProofStoragePort` (`src/lib/storage/payment-proof-storage.port.ts`) had three implementations: local filesystem served from a public `/uploads/...` path, Cloudflare R2 with a public bucket returning a public `https://.../payment-proofs/...` URL stored directly on the payment record, and a base64 "inline" fallback for when R2's TLS handshake failed. All three hand back a URL that anyone who obtains it — the record, a log line, a browser's network tab — can fetch forever, no authentication involved. That is precisely what this issue's "object access is controlled" acceptance criterion rules out: a payment-proof image is a bank transfer slip or a PromptPay QR screenshot, and it can carry an account number.

This repository has no object-storage dependency yet: no AWS SDK on the classpath, no bucket in `infra/`. ADR-004 already named S3/CloudFront as part of the AWS target topology, but nothing has drawn on that until now, so this is the first slice that has to decide how the application actually talks to it.

Issue #7 was split into #51 (accepting and storing a submission) and #52 (the administrator's review). This ADR records the decision whole, because the two halves constrain each other; the consequences section says which half implements what.

## Options considered

### Where the bytes live

- **PostgreSQL `bytea` column.** No new infrastructure, and it is the database ADR-003 already made authoritative for bookings. Rejected: it couples multi-megabyte binary blobs into the same OLTP database that ADR-008's row-locked, latency-sensitive booking writes depend on, and bloats every backup with data nobody reads outside a review click.
- **Local filesystem on the API container, public path** (the legacy's default driver). Free and simple locally. Rejected outright: its own request path is the public-URL anti-pattern this issue exists to close off, and ADR-004's Fargate target runs more than one task with no shared disk between them — a proof uploaded to one task is invisible to a request served by another.
- **S3, private bucket, all access brokered by the API.** Chosen. Matches ADR-004's already-decided target topology; a private bucket with no public-read grant and no credential ever reaching the browser satisfies the acceptance criterion directly.

### How the browser reaches the bytes

- **Presigned PUT for upload, presigned GET for viewing.** Keeps large file bytes off the API's own request path and is the standard S3 pattern. Rejected for this slice: it adds a second authenticated "mint me a URL" endpoint on both the submit and the review side, a client-side awareness of S3 URL shapes, and TTL/clock-skew handling — real complexity for a five-megabyte image on an infrequent action. The API already proxies every other write in this codebase (booking creation, seat holds); breaking that pattern only for this one feature is the opposite of boring.
- **API-proxied multipart upload and streamed download.** Chosen. The student `POST`s multipart form data to the API, which validates and writes to S3 server-side; the admin `GET`s the image through an authenticated admin endpoint that streams it back from S3. No S3 credential and no presigned URL is ever sent to the browser — the browser only ever holds its own AU-Van JWT, exactly as it does for every other endpoint today.

### Local development and test parity

- **Two separate storage implementations (real S3 client + a bespoke local-disk driver), selected by profile**, as the legacy did with local/R2/inline. Rejected: three storage backends is exactly the kind of variance the legacy piled up (down to a TLS-handshake-failure fallback) that this rebuild is meant to simplify away, and a bespoke local-disk driver would need its own public-path serving to be useful for manual testing, reintroducing the anti-pattern this ADR is rejecting above.
- **One S3-client implementation against a configurable endpoint: AWS S3 for the demo/target, MinIO locally and in CI.** Chosen. MinIO speaks the S3 API, so the same `S3PaymentProofStorage` class is exercised in dev, CI, and the deployed target — the only thing that changes is the endpoint setting and locally generated dummy credentials, the same shape `compose.yaml` already uses for Postgres and Redis. A second, in-memory fake implementation lives in test sources for unit-level substitution, giving the port a genuine second implementation per AGENTS.md's interface rule without a second *production* backend to maintain.

### What a booking's status means while payment is under review

- **Leave `Booking.status` as `CONFIRMED`/`CANCELLED` only; track payment review entirely on a separate `Payment`/`PaymentProof` row.** This is what the legacy did (a `Booking.status` and a separate `Payment.status`, kept in sync by hand in `payment.service.ts`). Rejected here: `BookingStatus`'s own doc comment already commits this codebase to extending the enum itself, two parallel state machines are exactly the "keep them in sync by hand" hazard the legacy's own service demonstrates, and every existing booking read (`BookingResponse`, `MyBookingsSection`) already renders off `Booking.status` alone.
- **Extend `BookingStatus` with `PENDING_PAYMENT`, `PAYMENT_UNDER_REVIEW`, `PAYMENT_REJECTED`, and change booking creation's initial status from `CONFIRMED` to `PENDING_PAYMENT`.** Chosen. One state machine, one place every client already reads. `PAYMENT_REJECTED` is not a dead end: the student can resubmit, moving the booking back to `PAYMENT_UNDER_REVIEW`.

### Seat claims during review

- **Release `seat_claims` on rejection**, as cancellation already does. Rejected for this slice: it would force a full re-hold (find seats again, race other students for them) after a correctable mistake like a blurry photo, which is worse for the exact campus-booking urgency ADR-008's own context section describes.
- **Leave the claim held through `PAYMENT_UNDER_REVIEW` and `PAYMENT_REJECTED`.** Chosen. `seat_claims` already has no independent expiry once `booking_id` is set (`V3`'s comment: "a claim blocks its seat while `booking_id IS NOT NULL OR expires_at > now`"), so this costs nothing new to implement. It does mean a booking can sit unpaid, holding a seat, indefinitely, until #8's expiry processing gives it a bound — an accepted, explicit consequence below, not a silent gap.

## Decision

- Store payment-proof images in a private S3 bucket (MinIO locally/in CI, AWS S3 for the demo/target per ADR-004), reached only through one `PaymentProofStorage`-backed client whose endpoint is configuration, never a second bespoke driver.
- Every byte crosses the API: students upload via authenticated multipart `POST`, admins view via an authenticated streaming `GET`. No presigned URL and no storage credential ever reaches the browser.
- `BookingStatus` gains `PENDING_PAYMENT` (the new initial status — booking creation no longer produces an immediately `CONFIRMED` booking), `PAYMENT_UNDER_REVIEW`, and `PAYMENT_REJECTED`. Approval is the only path to `CONFIRMED` from `PAYMENT_UNDER_REVIEW`.
- Rejection does not release the booking's `seat_claims`; the student may resubmit a proof against the same booking.
- Reviewer decisions are recorded as `booking_events`, reusing the audit trail `BookingEvent.actorUserId` already exists for, rather than a new audit table.

## Consequences

- `BookingResponse.status` gains three new values. This is a public API contract change: any client code that switches exhaustively on `BookingStatus` (including the web app's own `MyBookingsSection`/`StatusBadge`) must be updated in the same change, and this must be called out explicitly in the pull request per AGENTS.md's review conventions.
- Booking creation's behavior changes from "seats secured and paid" to "seats secured, payment pending" for every new booking from this point on. Existing tests that assert immediate `CONFIRMED` after hold confirmation (`BookingIntegrationTests`) must be updated, not left to rot alongside new ones.
- This is the project's first dependency on an external object store. It adds one AWS SDK dependency, one new set of environment variables (bucket, region, optional endpoint override, credentials), and one new `compose.yaml` service. The private bucket and its bucket-scoped IAM policy still have to reach `infra/`, written and statically checked and never applied by an agent, the way every other `infra/*.tf` in this repository already works; neither #51 nor #52 deploys anything, so that belongs with the next demo deployment. Cost at demo scale is negligible, but this is the first line item in the AWS target that application code actually exercises, and the eventual deployment needs the owner to provision the real bucket.
- A booking can now sit in `PAYMENT_UNDER_REVIEW` or `PAYMENT_REJECTED` indefinitely, holding its seat, since neither status carries an expiry in this slice. This is accepted deliberately rather than solved here; #8 (asynchronous notifications and booking expiry processing) is the natural, already-scoped home for a review/resubmission timeout.
- Concurrent approve/reject of the same proof, and a concurrent approve racing a student cancellation, both need row-lock discipline analogous to ADR-008's. This is proven on H2 in PostgreSQL mode only, inheriting the same caveat ADR-008 already documents; real PostgreSQL `FOR UPDATE` semantics remain issue #10's to prove.
- The API now streams potentially multi-megabyte image bytes on the admin review path. At current traffic this is a non-issue; if proof volume grows enough for this to matter, presigned GET URLs are the natural next step and this ADR's "how the browser reaches the bytes" section is where that trade-off should be revisited.
- The decision lands in two changes. #51 implements the storage port and its S3 client, the `payment_proofs` table, `PENDING_PAYMENT` and `PAYMENT_UNDER_REVIEW`, the student submit endpoint and upload UI, and the MinIO service. #52 adds `PAYMENT_REJECTED`, the administrator's list/view/approve/reject endpoints and UI, the reviewer columns on `payment_proofs`, the remaining `booking_events` types, and the row-lock concurrency coverage. Until #52 lands, an approved booking cannot be reached at all: every booking created after #51 waits for a review that has no endpoint yet, which is why the two are sequenced back to back rather than released separately.
