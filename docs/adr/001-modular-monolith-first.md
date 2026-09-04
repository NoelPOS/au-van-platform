# ADR-001: Start as a Modular Monolith

## Status

Accepted

## Context

AU-Van has related booking, payments, seat inventory, notifications, and administration workflows. The project has one primary developer and needs a fast, testable delivery path.

## Options considered

- Begin with independently deployed microservices.
- Begin with a modular monolith with explicit internal boundaries.

## Decision

Begin with a modular monolith. Keep identity, scheduling, booking, payments, notifications, and administration as separate modules inside one Spring Boot application.

## Consequences

- Delivery, debugging, local development, and transactional consistency are simpler.
- Module boundaries and interfaces must be protected so future extraction remains possible.
- Independent scaling of the worker or webhook workload is a future trigger to split deployables.

