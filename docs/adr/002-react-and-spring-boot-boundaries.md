# ADR-002: Use React and Spring Boot with Explicit Boundaries

## Status

Accepted

## Context

The legacy application is a Next.js monolith that combines presentation and API routes. The rebuild needs to demonstrate a React frontend and Java backend while preserving the established product workflows.

## Options considered

- Keep the Next.js full-stack monolith.
- Use React with a separate Spring Boot REST API.

## Decision

Use a React application for both `/admin/*` and `/liff/*` route groups. Use Spring Boot to expose REST APIs and own authorization, workflow orchestration, business rules, and integration adapters.

## Consequences

- Frontend and backend can be independently built, tested, and deployed.
- API contracts and cross-origin configuration must be maintained deliberately.
- React must not duplicate server-owned booking, payment, or authorization rules.

