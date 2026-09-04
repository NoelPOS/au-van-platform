# Data Model

This is the target relational model at planning stage. Exact table definitions and migrations will be designed after review.

## Core entities

| Entity | Purpose |
| --- | --- |
| `users` | Student and administrator identities, roles, and LINE linkage |
| `routes` | Active or inactive van routes, fare, duration, and route details |
| `timeslots` | A scheduled route departure with operational status and capacity |
| `seats` | Numbered seat inventory for a timeslot |
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

- A seat must be unique within a timeslot.
- A confirmed booking cannot share a seat and timeslot with another active confirmed booking.
- Booking, payment, hold, and notification status values must be controlled state machines.
- Payment proof is stored in object storage; PostgreSQL stores the object key and metadata only.
- Staff approvals, rejections, and manual overrides must have audit records.

