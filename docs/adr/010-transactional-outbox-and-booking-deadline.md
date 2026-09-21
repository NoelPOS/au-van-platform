# ADR-010: Deliver Asynchronous Work from a Transactional Outbox in PostgreSQL, and Give Every Unpaid Booking a Deadline

## Status

Accepted

## Context

Three things this system owes a user happen outside the request that causes
them: a LINE notification, a reminder before departure, and the release of a
seat whose booking was never paid for. None of them can be done inside the
transaction that triggers them — a LINE call inside `BookingWriter`'s
transaction would hold row locks on `seat_claims` for the length of an HTTP
round trip to a third party, and ADR-008 has already established how tightly
held those locks are.

Doing them outside that transaction is where the failure modes live. If the
notification is fired after the commit and the process dies in between, the
booking exists and nobody is told, with nothing recording that anything was
owed. That is the divergence issue #8's first acceptance criterion names.

The second problem is already specified. ADR-009's Consequences section says a
booking can sit in `PAYMENT_UNDER_REVIEW` or `PAYMENT_REJECTED` indefinitely,
holding its seat, "accepted deliberately rather than solved here", and names #8
as the home for the bound. The mechanism is in `V3`'s own comment: a claim
blocks its seat while `booking_id IS NOT NULL OR expires_at > now`, so once a
claim carries a booking there is no expiry left in the row at all.

Three decisions already constrain the answer. ADR-003 makes PostgreSQL
authoritative and gives Redis a supporting role — holds, idempotency, caching,
rate limiting — which does not include durable outbound work. ADR-007's demo
topology omits the separate SQS worker service ("it doubles the Fargate bill to
run code that does not exist") and leaves the SQS queue defined and defaulted
off "once the payment and notification work has designed their interfaces";
this is that design. And `docs/architecture.md` already commits the target
production design to "a transactional outbox and SQS-backed workers", with the
local implementation left to be documented separately — this ADR is that
document.

The legacy application solved a narrower version of this and is worth reading
for what to keep and what to drop. `src/services/reminder.service.ts` has a
`reminder_jobs` collection with `attempts`, a `MAX_REMINDER_ATTEMPTS` of five,
a claim that flips a row to `processing`, and a `unique (bookingId, type)` index
that makes scheduling idempotent — all of that is right and is ported. Around it
sits an in-process `EventBus` (`src/lib/events.ts`) that fires notification
handlers with a four-second timeout and swallows every error to `console.error`.
That is the divergence, in the form it actually takes: the booking commits, the
handler times out, and no record anywhere says a notification was owed. And it
runs on Vercel, so the worker is an HTTP endpoint behind a shared secret
(`src/app/api/internal/reminders/run/route.ts`) driven by a daily cron — a shape
forced by a platform with no long-running process. This API has one.

The legacy also has no unpaid-booking expiry of any kind. A non-cash booking is
created `pending_payment` and stays there forever. So the payment deadline is a
new product rule, not a ported one.

Issue #8 was split into three: #61 (the outbox and the worker boundary), #62
(the payment deadline, the expiry sweep and the seat release) and #63 (LINE
delivery and departure reminders). This ADR records the decision whole, because
the pieces constrain each other; the Consequences section says which sub-issue
implements what.

## Options considered

### Where asynchronous work is recorded

- **Fire the side effect after the commit, in process.** What the legacy does.
  Free, and it loses work on any crash, timeout, or deploy between the commit
  and the send — with no record that anything was lost. Rejected: it fails
  acceptance criterion 1 by construction.
- **Publish to SQS from the application, with no outbox.** The target topology
  names SQS, so this looks like the forward-looking choice. Rejected: the send
  and the database commit are two systems with no shared transaction, so the
  same gap reappears one layer out — the booking commits and the enqueue fails,
  or the enqueue succeeds and the transaction rolls back and a message describes
  a booking that does not exist. It also puts the record of committed work
  outside the database ADR-003 made authoritative, which ADR-008 already
  rejected for the idempotency record on the same grounds.
- **Redis as the queue.** Fast, already in `compose.yaml`. Rejected for the same
  reason, more sharply: Redis is not durable here by configuration or intent,
  and ADR-003 is explicit that Redis failures "must not cause conflicting
  confirmed bookings" — a queue whose loss silently drops a seat release is that
  failure in a different costume.
- **A transactional outbox table in PostgreSQL.** Chosen. The event and the state
  change are one commit, so they cannot diverge; there is nothing to reconcile
  because there is nothing to reconcile *with*.

### What triggers the worker

- **A separate worker process or ECS service.** The eventual production shape,
  and ADR-007 already declined to pay for it in the demo. Rejected for now, and
  the boundary is drawn so that it costs a deployment change rather than a
  rewrite when it is wanted.
- **An internal HTTP endpoint driven by an external cron**, as the legacy does.
  Rejected: it exists to work around Vercel, and it would add a new
  authentication path to `SecurityConfiguration` and a new shared secret for
  nothing.
- **`@Scheduled` polling inside the API, with a database-level claim.** Chosen.
  Every instance may poll; the claim decides who gets the row.

### How a row is claimed

- **`SELECT … FOR UPDATE SKIP LOCKED`.** The idiomatic answer, and it works on
  both databases here — H2 2.4.240 supports the syntax and Hibernate 7.4.5's
  `H2Dialect` emits it. Rejected for now: through Spring Data it is a
  `@QueryHints` lock timeout of `-2` whose meaning is invisible at the call site,
  and it buys throughput this workload will not reach.
- **A conditional `UPDATE` whose `WHERE` carries the current status and the due
  time, with the affected-row count as the answer.** Chosen. Dialect-independent,
  provable on H2, and it reads as what it is. It is also the legacy's own
  `findOneAndUpdate` claim, which is the one part of that design that was right.

### How a claim survives a crash

- **A dead-letter sweeper on a separate timer.** A second mechanism with its own
  bugs.
- **The claim sets `next_attempt_at` forward by a lease, so a row that is claimed
  and never resolved simply becomes due again.** Chosen. One mechanism covers
  retry and recovery, and it is deliberately SQS's own visibility-timeout model:
  when SQS does arrive, it replaces the *trigger*, and the outbox stays as the
  durable record. That is what makes this design SQS-compatible in the sense
  issue #8 asks for, rather than SQS-shaped in name only.

### How duplicate user-visible effects are prevented

- **Exactly-once delivery.** Not available. The send is a network call outside
  the transaction that records its outcome; a process that dies in between
  cannot know whether the message arrived.
- **At-least-once transport, made once-only at the far end by a stable retry
  key.** Chosen. The outbox row's id is the key, it does not change across
  retries, and it is carried to LINE in `X-Line-Retry-Key`, which LINE
  deduplicates for 24 hours. The claim and the terminal-state guard keep the
  number of attempts small; the retry key is what makes them invisible.

### How many tables

- **Three: `outbox_events`, `reminder_jobs`, `notifications`**, as
  `docs/data-model.md` planned and as the legacy had. Rejected: a reminder is an
  outbox row whose `next_attempt_at` is in the future, and a delivery result is
  three columns on the row that caused it. Three tables means writing the claim,
  the backoff and the dead-letter rule three times.
- **One.** Chosen. `docs/data-model.md` is updated to say so.

### What bounds an unpaid booking

- **A timer per status, evaluated in the sweep.** Rejected: the rule lives in
  code, `PAYMENT_UNDER_REVIEW` needs a different clock from `PENDING_PAYMENT`,
  and the sweep needs a join to `trips` to respect departure.
- **Measure from `bookings.updated_at`.** No migration. Rejected: the rule
  becomes implicit, and any future write that touches `updated_at` silently
  resets a student's payment clock.
- **An explicit `bookings.payment_deadline_at`, written by the transitions that
  own it.** Chosen. The three statuses collapse into one predicate with no join,
  the index matches it exactly, and the student can be shown the deadline
  instead of guessing at it.

### What an expired booking becomes

- **A new `BookingStatus.EXPIRED`.** Rejected: a public contract change on
  `BookingResponse.status` and on the web's `BookingStatus` union, for a state
  that is operationally identical to `CANCELLED` — the seats are released and
  the booking is over.
- **`CANCELLED`, with a new `BookingEventType.EXPIRED` carrying the reason and a
  null actor.** Chosen. `booking_events.actor_user_id` is already nullable for
  system-driven transitions and its javadoc says so, and the history is already
  where this codebase puts "why".

## Decision

- Asynchronous work is recorded in `outbox_events` in PostgreSQL, written in the
  same transaction as the domain change it describes. The recorder joins the
  caller's transaction and never opens its own, the same contract
  `IdempotencyService.record` already states.
- One table serves state-change events, scheduled reminders, and delivery
  results. `reminder_jobs` and `notifications` are removed from the planned data
  model.
- A dispatcher claims a due row with one conditional `UPDATE`, sends outside any
  transaction, and records the outcome in a second transaction. The claim pushes
  `next_attempt_at` forward by a lease, so an unresolved claim becomes due again
  without a second mechanism.
- Retries back off exponentially from `outbox.backoff-base` to
  `outbox.backoff-cap`, and stop at `outbox.max-attempts` (five, the legacy's
  own ceiling), leaving the row `DEAD` with its last error. The schedule must
  multiply out to well under LINE's 24-hour retry-key window.
- The trigger is an in-process `@Scheduled` poller, gated on a property that is
  off in tests. The dispatch entry point takes no scheduling concern of its own,
  so an SQS consumer or a separate worker process can call it unchanged.
- **Redis is not used by this work, and that is a decision.** Nothing here may
  depend on it, `management.health.redis.enabled` stays `false` in both
  `application.yml` files, and ADR-003's list of Redis roles stands unextended.
- LINE delivery goes through a `LineMessageSender` port: one production
  implementation against the Messaging API, one recording fake in test sources.
  Each send carries the outbox row id as `X-Line-Retry-Key`. A LINE error that
  can never succeed — unknown recipient, bot not added as a friend — goes `DEAD`
  on the first attempt rather than consuming the retry budget.
- Every booking carries `payment_deadline_at`, set to
  `min(now + booking.payment-window, departureAt - booking.departure-cutoff)` on
  creation and on rejection, and to `departureAt - booking.departure-cutoff`
  while a proof is under review. Defaults: `payment-window` two hours,
  `departure-cutoff` one hour.
- A sweep expires any booking in `PENDING_PAYMENT`, `PAYMENT_UNDER_REVIEW` or
  `PAYMENT_REJECTED` whose deadline has passed: status `CANCELLED`, a
  `BookingEventType.EXPIRED` event with a null actor, its `seat_claims` deleted
  by `deleteByBookingId`, and an outbox row telling the student. The sweep reads
  candidate **ids**, then locks each booking with `BookingRepository.lockById`
  and decides only from what the lock returned — the discipline ADR-008
  established and ADR-009 reaffirmed.
- The sweep also prunes `idempotency_keys` past a retention window, which ADR-008
  named as this issue's.

## Consequences

- **What is implemented where.** #61 implements the outbox: `V7`, the
  `com.auvan.api.outbox` package, the recorder wired into booking creation,
  cancellation, payment-proof submission and the review decision, the
  claim/lease/backoff/dead-letter machinery, the `LineMessageSender` port and
  the scheduling gate. #62 implements `payment_deadline_at`, the expiry sweep,
  `BookingEventType.EXPIRED` and the `idempotency_keys` prune. #63 implements
  the Messaging API client, the retry key on the wire, permanent-versus-transient
  error classification and the departure reminders. Until #63 lands there is no
  implementation of the port at all, and the handler records that omission
  rather than failing — so every row completes and nothing user-visible changes.
- A committed booking always carries the record of what it owes. The proof is
  the rollback test: make the write fail and no outbox row survives; give the
  recorder its own transaction and that test reddens.
- Delivery is at-least-once at the transport and once-only at the far end. If
  LINE's retry key is ever dropped from the request, duplicates become possible
  again and no test in this repository would notice unless one asserts the key
  explicitly — so one does.
- `outbox_events.payload` is `VARCHAR(2000)`, chosen the way ADR-008 chose
  `response_body VARCHAR(4000)`: the widest field it carries today is a review
  note, which the review service bounds at 500 characters, so the column has
  room for the fields #62 and #63 add and still fails loudly at the insert
  rather than letting this table grow without a stated bound.
- `outbox_events` carries no foreign keys. A queue row must not block the
  deletion of the booking or the user it names, and the dispatcher has to
  tolerate an aggregate that has since gone.
- Ordering between two events for one booking is not guaranteed. In practice
  they are human-paced and seconds apart; if it ever matters, claiming per
  aggregate rather than per row is the fix, and it is not needed yet.
- Polling costs one indexed query per instance per interval against a table that
  is empty most of the time. That is the price of not running a second service,
  and ADR-007 already decided not to pay for one.
- **A student can now lose their seats without doing anything.** This is the
  first action in the system that takes something away unprompted. The window is
  configuration, and the deadline is shown in the booking response so it is not a
  surprise, but the behaviour change is real and belongs in a release note rather
  than only in a migration.
- A booking created very close to departure gets a deadline that is already past
  and is expired on the next sweep. That is correct — an unpaid seat twenty
  minutes before departure helps nobody — but it means the payment window is not
  always the payment window, and the response field is what tells the student
  which bound applies.
- `outbox_events` grows without bound unless `SENT` rows are pruned. The same
  sentence ADR-008 wrote about `idempotency_keys` applies here, and this time
  the prune ships with the sweep rather than being deferred to a later issue.
- `LINE_CHANNEL_ACCESS_TOKEN` is a new secret and a **different LINE channel
  type** from the Login channel `LINE_CHANNEL_ID` names. If the Messaging channel
  is created under a different LINE provider, the `sub` this system stores as
  `AppUser.lineSubject` is not a valid push target and every send fails in a way
  that looks like a bug in this code. Tests need neither channel: the port's fake
  covers them, exactly as the in-memory storage lets the suite run with no
  bucket.
- Push reaches only a student who has added the bot as a friend. There is no way
  to detect that in advance, so the first delivery attempt is also the discovery
  mechanism, and a permanent failure is recorded rather than retried.
- The dispatcher runs with no security context. Every query it issues is scoped
  by the row it claimed, never by a caller, and any future work in this package
  has to keep that true.
- This is proven on H2 in PostgreSQL mode, inheriting the caveat ADR-008 and
  ADR-009 both already carry. The claim is a conditional `UPDATE` and the sweep's
  guard is `SELECT … FOR UPDATE`; real PostgreSQL semantics under READ COMMITTED
  remain issue #10's to prove.
- ADR-007's demo table can now have its "SQS queue" row revisited, because the
  interface it was waiting on exists. Nothing is provisioned here; #11 owns that.
