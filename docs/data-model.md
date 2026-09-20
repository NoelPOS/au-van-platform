# Data Model

The transport-inventory tables below are implemented in migration `V2`. The booking, payment, and asynchronous-workflow tables remain planned.

## Core entities

| Entity | Purpose |
| --- | --- |
| `users` | Student and administrator identities, roles, and LINE linkage |
| `routes` | Active or inactive van routes, fare, and duration |
| `seat_layouts` | Named reusable vehicle seat templates |
| `seat_layout_seats` | Labelled row/column positions inside a reusable layout |
| `vehicles` | Vehicle code, name, active status, and assigned seat layout |
| `trips` | A scheduled route departure, vehicle, status, and snapshot of fare/duration |
| `trip_seats` | Labelled row/column seat snapshot created with a trip |
| `seat_holds` | Short-lived ownership of selected seats before booking confirmation |
| `bookings` | Student booking, passenger details, source channel, price, and lifecycle state |
| `booking_seats` | Booking-to-seat relation |
| `payments` | Payment method, proof metadata, review state, and reviewer details |
| `notifications` | Delivery intent and result for LINE, in-app, and email channels |
| `reminder_jobs` | Scheduled reminder work and retry metadata |
| `idempotency_keys` | Safely replayable critical client writes |
| `audit_logs` | Staff actions and sensitive state changes |
| `outbox_events` | Durable domain events awaiting asynchronous delivery |
| `waitlist_entries` | Optional queue for full timeslots and promotion processing |

## Critical constraints to design

- A seat label and seat position must be unique within a layout and within a trip.
- A vehicle cannot have two trips at the exact same departure time.
- A confirmed booking cannot share a seat and timeslot with another active confirmed booking.
- Booking, payment, hold, and notification status values must be controlled state machines.
- Payment proof is stored in object storage; PostgreSQL stores the object key and metadata only.
- Staff approvals, rejections, and manual overrides must have audit records.
