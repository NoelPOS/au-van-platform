# Architecture

## Chosen starting point

AU-Van will begin as a modular monolith: one React web application and one Spring Boot backend codebase with clearly separated modules. This keeps delivery and debugging simple while preserving boundaries that can later support independent services.

## Logical components

```text
React web application
  - Admin portal
  - Student LIFF experience
        |
        v
Spring Boot API
  - Identity and access
  - Scheduling and seat inventory
  - Booking
  - Payment review
  - Notifications
  - Administration and audit
        |
        +--> PostgreSQL: authoritative transactional data
        +--> Redis: temporary holds, idempotency, cache, rate limits
        +--> Object storage: payment-proof images
        +--> Queue/worker: retries, reminders, expiry, LINE delivery
```

## Consistency model

PostgreSQL is authoritative for confirmed seat inventory, bookings, payment state, and audit records. Seat-related writes must run in database transactions. Redis can improve responsiveness for short-lived holds, but it cannot independently confirm a booking.

## Asynchronous work

Booking and payment changes may generate notifications, reminders, expiry processing, and waitlist promotion. These side effects must be retryable and observable. The target production design uses a transactional outbox and SQS-backed workers; the local development implementation will be documented separately.

## Boundaries

- React does not contain booking, payment, or authorization rules.
- Controllers validate and orchestrate only.
- Spring application/domain services own workflows and state transitions.
- Infrastructure adapters isolate PostgreSQL, Redis, S3, SQS, and LINE APIs from core business logic.

## Accepted decisions

- [ADR-001: Start as a Modular Monolith](adr/001-modular-monolith-first.md)
- [ADR-002: Use React and Spring Boot with Explicit Boundaries](adr/002-react-and-spring-boot-boundaries.md)
- [ADR-003: PostgreSQL Is the Booking Authority; Redis Is Supporting Infrastructure](adr/003-postgresql-authority-and-redis-support.md)
- [ADR-004: Use a Cost-Conscious Demo with an AWS-Ready Production Target](adr/004-cost-conscious-demo-and-aws-target.md)
- [ADR-005: Verify LINE Identity Server-Side and Issue Short-Lived AU-Van JWTs](adr/005-line-identity-exchange-and-short-lived-jwt.md)
- [ADR-006: Hold and Book Seats in One `seat_claims` Table with Lazy Expiry](adr/006-seat-claims-single-table-and-lazy-hold-expiry.md)
- [ADR-007: Use Terraform for the AWS Target Infrastructure](adr/007-terraform-for-aws-target-infrastructure.md)
- [ADR-008: Create a Booking Exactly Once, from a Locked Hold and a Stored Response](adr/008-exactly-once-booking-creation.md)
