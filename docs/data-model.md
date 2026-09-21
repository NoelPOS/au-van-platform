# Data Model

The transport-inventory tables are implemented in migration `V2`. The booking tables are created by migration `V3`, of which only `seat_claims` is used so far. The payment and asynchronous-workflow tables remain planned.

## Schema ownership

Flyway owns the schema. The migrations under `api/src/main/resources/db/migration` are the only thing that creates or changes a table, and `spring.jpa.hibernate.ddl-auto` is `validate` in both `application.yml` files, so Hibernate checks the mappings against the migrated schema at startup and refuses to start when they disagree.

Entity mappings therefore describe the table rather than define it. Column lengths and named unique constraints are repeated in the mapping so that a divergence shows up in review; `CHECK` clauses and the indexes in `V3` exist only in SQL, because `validate` checks neither, so mirroring them would buy nothing.

Until issue #27 this was not the case: `flyway-core` was on the classpath without `spring-boot-flyway`, which is where Spring Boot 4 moved `FlywayAutoConfiguration`, so no Flyway bean existed and no migration had ever run. Tests passed on a schema Hibernate generated from the mappings, and against PostgreSQL the application had no tables at all.

## Core entities

| Entity | Migration | Purpose |
| --- | --- | --- |
| `app_users` | `V1` | Student and administrator identities, roles, and LINE linkage |
| `routes` | `V2` | Active or inactive van routes, fare, and duration |
| `seat_layouts` | `V2` | Named reusable vehicle seat templates |
| `seat_layout_seats` | `V2` | Labelled row/column positions inside a reusable layout |
| `vehicles` | `V2` | Vehicle code, name, active status, and assigned seat layout |
| `trips` | `V2` | A scheduled route departure, vehicle, status, and snapshot of fare/duration |
| `trip_seats` | `V2` | Labelled row/column seat snapshot created with a trip |
| `seat_claims` | `V3` | One claimed seat, held for a few minutes or attached to a booking |
| `bookings` | `V3` | Student booking, passenger details, price, and lifecycle state — planned use in #25 |
| `booking_seats` | `V3` | Booking-to-seat relation — planned use in #25 |
| `booking_events` | `V3` | Audit trail of booking state changes — planned use in #25 |
| `idempotency_keys` | `V3` | Safely replayable critical client writes — planned use in #25 |
| `payments` | planned | Payment method, proof metadata, review state, and reviewer details |
| `notifications` | planned | Delivery intent and result for LINE, in-app, and email channels |
| `reminder_jobs` | planned | Scheduled reminder work and retry metadata |
| `audit_logs` | planned | Staff actions and sensitive state changes |
| `outbox_events` | planned | Durable domain events awaiting asynchronous delivery |
| `waitlist_entries` | planned | Optional queue for full timeslots and promotion processing |

## Seat claims

`seat_claims` covers both a short-lived hold and a booked seat in one row, so a seat is never unprotected between the two. ADR-006 records the decision.

- `UNIQUE (trip_seat_id)` is the only thing that prevents overselling. A seat is claimable exactly when it has no row.
- There is no status column. A claim is released by deleting its row, so the table cannot describe a state that is not true.
- `expires_at` is always set. A claim blocks its seat while `booking_id IS NOT NULL OR expires_at > now`.
- `hold_id` groups the rows one student created in one selection, which is what the release endpoint addresses.
- Expiry is lazy. Reads derive the seat state and write nothing; the next transaction that wants the seat deletes the expired row.

Seat state is derived on every read and never stored: `AVAILABLE`, `HELD`, `HELD_BY_YOU`, or `BOOKED`.

## Critical constraints to design

- A seat label and seat position must be unique within a layout and within a trip.
- A vehicle cannot have two trips at the exact same departure time.
- A trip seat can carry at most one claim, held or booked. `seat_claims.trip_seat_id` is unique and this is what makes a confirmed booking unable to share a seat with another.
- Booking, payment, and notification status values must be controlled state machines. Hold state is deliberately not one; see ADR-006.
- Payment proof is stored in object storage; PostgreSQL stores the object key and metadata only.
- Staff approvals, rejections, and manual overrides must have audit records.
