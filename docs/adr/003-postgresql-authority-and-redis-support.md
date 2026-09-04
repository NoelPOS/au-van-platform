# ADR-003: PostgreSQL Is the Booking Authority; Redis Is Supporting Infrastructure

## Status

Accepted

## Context

Seat allocation and payment status are correctness-sensitive. The system must prevent conflicting confirmed bookings under concurrent requests and tolerate retries.

## Options considered

- Use Redis locks as the source of truth for seat allocation.
- Use PostgreSQL transactions and constraints as the source of truth, with Redis as support.

## Decision

PostgreSQL is authoritative for seats, holds, bookings, payments, and audit data. Seat confirmation must be protected by PostgreSQL transactions, constraints, and explicit lifecycle transitions. Redis supports short-lived holds, idempotency, caching, and rate limiting.

## Consequences

- Correctness does not depend on Redis availability or persistence.
- Booking operations need careful transaction design and concurrency testing.
- Redis failures can degrade convenience features but must not cause conflicting confirmed bookings.

