# ADR-018: Push Live Updates as Server-Sent Invalidation Signals, Fanned Out by PostgreSQL LISTEN/NOTIFY

## Status

Accepted

## Context

Students and administrators learn about each other's actions by polling. A ticket waiting on a payment decision refetches every 15 seconds, the trip list every 30, an open seat map every 10, and the administrator's payment queue only when the page is opened again. Issue #133 asks that nobody has to press refresh: an approval should reach the student's open ticket within about two seconds, and a new slip should reach the payment queue at once.

Three constraints shape the answer.

- **More than one API instance.** The AWS target (ADR-007) runs several API tasks behind a load balancer. A change committed on one instance has to reach a browser connected to another.
- **PostgreSQL is the source of truth** (ADR-003). A signal must follow a commit and must never announce a change that was rolled back. Redis is not running in the deployed stack at all (`compose.prod.yaml` keeps it in the `local` profile).
- **EventSource cannot send an `Authorization` header.** The API authenticates every request with a short-lived bearer JWT (ADR-005), which the browser holds in memory.

## Options considered

### Transport to the browser

- **Faster polling.** No new moving parts, but every open tab pays for it all the time, and a two-second target means polling every two seconds.
- **WebSockets.** Two-way, which nothing here needs. It wants a message broker or a STOMP layer on the server, its own reconnect logic in the client, and an `Upgrade` path through Caddy, nginx and CloudFront.
- **Server-sent events.** One-way, plain HTTP, built into the browser as `EventSource`, and served by Spring MVC's `SseEmitter` with no new dependency. Chosen.

### Fan-out between API instances

- **In process only.** Breaks as soon as there are two instances.
- **Redis pub/sub.** Not deployed, and it would publish outside the database transaction, so it could announce a rollback.
- **PostgreSQL `LISTEN/NOTIFY`.** Already running. A `NOTIFY` issued inside a transaction is delivered only if that transaction commits, which is exactly the guarantee wanted. Chosen.

### Authenticating the stream

- **The access token in the query string.** Puts a fifteen-minute credential for the whole API into URLs, where proxies and access logs keep it.
- **A cookie.** A second credential type with its own CSRF and cross-origin rules, for one endpoint.
- **`fetch` with a streaming body instead of `EventSource`.** Keeps the header, but gives up the browser's event-stream parser and needs a hand-written one.
- **A short-lived stream ticket.** Chosen.

## Decision

**Tickets.** `POST /api/v1/events/ticket`, authenticated with the normal bearer token, returns a JWT signed with the same key, valid for one minute (`live-updates.ticket-ttl`), whose audience is the API audience with `-events` appended. `GET /api/v1/events?ticket=…` is the only route Spring Security leaves open, and the controller verifies the ticket itself. The different audience makes the two credentials exclusive: an access token is refused as a ticket, and a ticket is refused as an access token, so the only credential that ever appears in a URL opens a stream and nothing else. It is stateless, so any instance can verify a ticket another issued. The API never logs it.

**Signals.** An event is `{"kind": …, "id": …}` and nothing more. It tells the browser what to refetch, never what changed, so every byte of real data still comes through an authenticated request.

- `booking` — anything about one student's bookings or waitlist place. It is published by `OutboxRecorder.record`, the one call every booking and payment transition already makes, with the outbox row's aggregate id. It goes to that student and to administrators.
- `trip` — a trip's seats changed. It is published where `seat_claims` rows are taken or freed: seat hold and release, booking cancellation and expiry, and waitlist promotion and withdrawal. It goes to everyone.

A student never receives another student's `booking` signal. The owner is used for routing and is never sent.

**Delivery.** `LiveSignalPublisher` has two implementations, chosen by `live-updates.postgres-notify`. `LiveSignalPublisherImpl` runs `pg_notify` on the caller's transaction and keeps one pooled connection per instance listening on the `live_updates` channel. Each instance then hands the signal to its own open streams. `InProcessLiveSignalPublisher` delivers after commit through a transaction synchronization. The test suite uses it, because H2 has no `LISTEN/NOTIFY`.

**Keeping the stream open.** A comment line goes out every 25 seconds, under the web image's 30-second `proxy_read_timeout`. A stream ends after one access-token lifetime, and the browser reconnects with a fresh ticket. No proxy needed a change. An event stream was measured through each one with its configuration unchanged, and each passed every event on as it was written. Caddy streams `text/event-stream` and leaves it uncompressed despite `encode`. The web image's nginx streams it with or without `X-Accel-Buffering: no`. The Vite dev proxy streams it as well.

**The browser.** `useLiveUpdates(session)` is mounted once in the student layout and once in the admin layout. It opens one `EventSource` and maps each signal to `invalidateQueries` on the existing TanStack Query keys. When the stream fails, it closes it, waits 1, 2, 4… up to 30 seconds, and reconnects with a new ticket. After a reconnect it invalidates everything it watches, because changes made while it was down sent no signal. It stops on unmount, and when the ticket request is refused with 401. The existing polling intervals stay as the fallback.

## Consequences

- An approval reaches an open ticket as fast as the commit plus one refetch. The Playwright journey now asserts five seconds where it used to allow thirty.
- Signals are best effort and at most once, with no replay. A signal sent while the listener is reconnecting, or while a browser is between streams, is lost. Polling and the catch-up invalidation after a reconnect cover that gap, so a missed signal costs freshness, never correctness.
- Some changes send no signal at all. A seat hold lapses without any code running (ADR-006), so other students learn of it from the seat-map poll. Administrators' edits to trips and routes are not signalled either.
- Each instance holds one database connection for `LISTEN`. It counts against the pool, and the API driver is now a compile-time dependency, because JDBC has no API for notifications.
- A stream can outlive the session that opened it by up to one access-token lifetime. It carries only kinds and ids, and every refetch it prompts needs a valid token.
- A ticket can appear in proxy access logs. It expires a minute after it is issued and can open only a stream.
- The listener writes to every matching stream on one thread, so a very slow client can delay the others. That is acceptable at this scale and is the first thing to revisit if streams grow into the thousands.
- The CloudFront demo topology (`infra/demo`) is unchanged and out of scope. Its default 30-second origin read timeout is above the heartbeat interval, but no one has measured a stream through it.
