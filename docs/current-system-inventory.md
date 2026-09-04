# Current System Inventory

This document records behaviour found in the legacy `NoelPOS/AU-Van` Next.js repository. It is a migration reference, not a statement that the rebuild already implements these features.

## Current implementation

| Area | Legacy implementation | Rebuild disposition |
| --- | --- | --- |
| Web experiences | Next.js App Router with admin pages and mobile-focused LIFF route group | Rebuild in React route groups: `/admin/*` and `/liff/*` |
| API | Next.js Route Handlers | Rebuild as Spring Boot REST API |
| Persistence | MongoDB + Mongoose | Migrate to PostgreSQL |
| Authentication | Admin credentials/session; LIFF LINE ID-token flow | Spring Security with separate admin and LINE identity flows |
| Booking | Booking creation, cancellation, rescheduling, overlapping-trip checks, booking codes | Preserve and strengthen with database transactions |
| Seats | Available/locked/booked states; lock timeout and release | Preserve; PostgreSQL is final authority |
| Payments | Cash, bank-transfer, PromptPay strategies; proof submission and staff review | Preserve manual review; store proof metadata in PostgreSQL |
| Notifications | In-app, email, and LINE push strategies; SSE stream | Preserve event-driven delivery; use SQS worker for durable retries |
| Reminders | Persisted reminder jobs, retry attempts, internal scheduled endpoint | Rebuild as worker-driven asynchronous jobs |
| Storage | Local, inline, and Cloudflare R2 adapters for profile/payment images | Use S3-compatible object storage; target AWS S3 |
| Reliability | Idempotency records, audit logs, route protection, validators | Preserve and formalize in Java services |
| Testing | Vitest unit/integration tests for booking, payments, reminders, LIFF idempotency, LINE push | Replace/extend with JUnit, Testcontainers, Playwright, concurrency tests |

## Legacy user-facing routes

- Student/LIFF: home, routes, route booking, payment, my bookings, profile, notifications, booking edit/reschedule.
- Admin: dashboard, routes, timeslots, bookings, payments, users.
- Auth: administrator sign-in and account creation.

## Legacy domain records

- User
- Route
- Timeslot
- Seat
- Booking
- Payment
- Notification
- Reminder job
- Idempotency key
- Audit log

## Migration notes

- The legacy booking service already contains useful rules: overlapping-trip prevention, two-hour reschedule window, payment state transitions, reminders, and side-effect events.
- The legacy seat service uses a document update pattern for locking. The rebuild must replace this with a PostgreSQL transaction and constraints before treating it as production-safe.
- Existing LINE IDs, payment-proof fields, audit records, and notification statuses should be retained in the future relational data-model design.
- No credentials or production data are copied into this repository.

