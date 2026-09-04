# AU-Van

LINE-integrated van booking system for Assumption University.

This repository is a clean rebuild of the legacy Next.js application. It will use React for the web experiences and Spring Boot, PostgreSQL, and Redis for the backend platform.

## Status

Planning and migration inventory in progress. See [docs](docs/).

## Planned structure

```text
web/        React application: admin and student LIFF route groups
api/        Spring Boot modular monolith
infra/      local containers and deployment infrastructure
tests/e2e/  Playwright end-to-end coverage
docs/       product, architecture, decisions, and migration inventory
```

