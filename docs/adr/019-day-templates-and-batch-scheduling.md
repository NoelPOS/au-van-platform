# ADR-019: Plan Departures in Batches from Day Templates, Previewed and Applied Exactly Once

## Status

Accepted

## Context

Administrators scheduled trips one form at a time. A service day has many departures and the days are not uniform: weekdays, Saturdays, and exam weeks all run differently, and service days are not simply Monday to Friday. The owner asked for named, reusable day plans, a calendar to apply them to any set of dates, a preview with clashes flagged before anything is written, *Copy day to…*, and per-day clearing of departures nobody has booked (issue #132). Applying must create exactly the previewed trips, never create one in the past, and be safe to retry.

Two accepted decisions bound the design. ADR-008 made critical writes exactly-once with a stored-response `idempotency_keys` row. `trips_vehicle_departure_unique` (V2) already stops one van having two trips at the same instant.

## Options considered

### What a template is applied through

- **An apply endpoint per source**: one for a template id, another for copying a day. Rejected: two endpoints compute the same plan and would drift.
- **One plan endpoint that takes lines and dates.** Chosen. A plan is a list of `{time, routeId, vehicleId}` lines and a list of dates. A template's lines, or the lines read off an existing day, are sent the same way, so *Apply template* and *Copy day to…* share one planner, one preview, and one commit.

### How a template relates to the trips it makes

- **Trips keep a `day_template_id`.** Rejected: editing a template would raise the question of rewriting trips students have already booked.
- **Applying copies the lines.** Chosen. A template is a starting point; the trips it produced are ordinary trips.

### What counts as a clash

- **Only the unique constraint**: the same van at the same instant. Rejected: it lets one van be scheduled at 07:00 and 07:30 on a 45-minute route, which no driver can run.
- **The van is already out.** Chosen. A departure clashes when its van has an active trip whose departure-to-arrival window overlaps it, a cancelled trip at the exact same time (which still holds the unique constraint), or an earlier line of the same plan. The single-trip form keeps its existing exact-time check; the batch path is stricter because it is where a whole week can go wrong at once.

### How "creates exactly the previewed trips" is guaranteed

- **Trust the client's preview.** Rejected: another administrator, or the clock, can change the plan between preview and confirm.
- **Re-plan at commit and compare a fingerprint.** Chosen. The preview returns `planHash`, the SHA-256 of the departures it would create. Apply re-plans inside its transaction and refuses with `409 schedule_changed` when the hash differs, so it never creates a set the administrator did not see. The fingerprint reuses `IdempotencyService.fingerprint`.

### How a retried apply is kept to one

- **Rely on clash detection alone.** A replay would find every departure already scheduled and create nothing, but the administrator would be told the schedule changed when it was their own first attempt that succeeded.
- **The `Idempotency-Key` pattern from ADR-008.** Chosen. The apply response is two counts, small enough for `idempotency_keys.response_body`, and the orchestration is `BookingService.create`'s: look up, write in a transaction, and on failure look up again. Two concurrent applies with different keys meet at `trips_vehicle_departure_unique`; the loser rolls back whole and is told to re-preview.

### What *Clear day* may remove

- **Cancel the day's trips.** Rejected: trip cancellation and its effect on bookings is issue #131's.
- **Delete only departures nobody has touched.** Chosen. A departure still ahead with no `bookings`, `seat_claims`, or `waitlist_entries` row is deleted; any other is kept and counted. A hold that lands between the check and the delete trips the `seat_claims` foreign key, and the whole clear rolls back as `409 day_changed`.

### Readable validation messages

Spring answers every bean-validation failure with "Invalid request content." The owner asked for the past-departure refusal to say why. A `@RestControllerAdvice` ordered ahead of Boot's problem-details handler uses the first violation whose constraint declares its own sentence, and leaves built-in messages such as "must not be null" on the old generic detail.

## Decision

Store day templates as named lists of Bangkok wall-clock times, each with a route and a van. Plan any set of lines across any set of dates through one planner that skips and reports past, unavailable, and clashing departures. Commit a plan only when it still matches its preview's fingerprint, in one transaction, exactly once per `Idempotency-Key`. Clear a day by deleting only departures that nobody has booked, held, or queued for.

## Consequences

- The inventory module now uses the booking module's `IdempotencyService` and `Problems`, and `TripRepository` names booking entities in one query. The dependency already ran the other way; this makes it two-way inside the monolith.
- A route's duration is now a scheduling input: an inaccurate duration produces a false clash, or misses a real one, in the preview.
- The plan request is bounded at 62 dates and 60 lines a day.
- Clashes are not enforced by the database beyond the exact-time constraint. Two administrators applying overlapping-but-not-identical plans at the same instant can still both succeed.
