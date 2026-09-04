# AU-Van Working Agreement

## Purpose

AU-Van is a LINE-integrated booking system for Assumption University van services. The rebuild preserves validated product behaviour from the legacy Next.js application while replacing its runtime with React, Spring Boot, PostgreSQL, and Redis.

## Delivery workflow

1. Discuss and record scope or architecture decisions before implementation.
2. Track each implementation slice in a GitHub issue with acceptance criteria.
3. Use one focused branch and pull request per issue.
4. Run the required automated checks before merging.
5. Update relevant documentation when behaviour or an architectural decision changes.

## Naming and review conventions

- Issue titles use `<Type>: <outcome>` in sentence case. Allowed types are `Feature`, `Bug`, `Docs`, `Architecture`, `Chore`, and `Security`.
- Branch names use `<type>/<short-kebab-case-description>`; for example, `feature/seat-holds` or `docs/architecture-decisions`.
- Commit and pull-request titles use Conventional Commits; for example, `feat(booking): add seat hold expiry`.
- Every pull request links its issue, explains the outcome, records verification, and calls out any security, data-consistency, or architecture impact.
- Keep a pull request focused. Split unrelated changes into separate issues and branches.

## Architecture rules

- Start as a modular monolith. Do not introduce microservices without an ADR.
- Keep React responsible for presentation and client interactions only.
- Keep controllers thin. Place business rules in the Spring domain/application layers.
- PostgreSQL is the source of truth for bookings, payments, seats, and audit data.
- Redis supports short-lived holds, idempotency, caching, and rate limiting; it must not be the final authority for confirmed bookings.
- Treat notification delivery, reminders, expiry processing, and other side effects as retryable asynchronous work.
- Verify all external input and enforce authorization at API boundaries.

## Quality rules

- Every feature needs success and failure-path tests.
- Critical booking changes require integration and concurrency coverage.
- User-visible booking and payment flows require Playwright E2E coverage when practical.
- Prefer small, reviewable commits using Conventional Commits.
- Never commit credentials, connection strings, access tokens, payment slips, or production data.

## Documentation rules

- `docs/` is the source of truth for approved scope and architecture.
- Create an ADR for decisions that materially affect architecture, data consistency, security, deployment, or cost.
- Keep ADRs concise: context, options, decision, consequences.
- Documentation describes the current state accurately; planned work must be labeled as planned.
