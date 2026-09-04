# Project Status

## Current milestone

**Milestone 0 - Planning foundation**

The legacy Next.js application has been cloned separately as a read-only migration reference. The rebuild repository contains approved initial scope, architecture, data-model, AWS-target, and working-agreement documents.

## Repository state

- Default branch: `main`
- Baseline commit: `06817e5` (`docs: establish AU-Van planning foundation`)
- Active issue: [#1 Document foundational architecture decisions and project status](https://github.com/NoelPOS/au-van-platform/issues/1)
- Active branch: `docs/adr-and-project-status`
- Legacy reference: `/Users/noelpaingoaksoe/Desktop/AU-Van-reference` (not part of this repository)

## Accepted decisions

- Start with a modular monolith.
- Use React for the web application and Spring Boot for the API.
- Make PostgreSQL authoritative for booking correctness; use Redis as support infrastructure.
- Use low-cost demo hosting first while keeping an AWS-ready target topology.

## Current blockers

None. The next milestone requires the foundational documentation PR to be reviewed and merged.

## Next step

Merge issue #1, then create the first implementation-planning issue: bootstrap the React/Spring Boot/PostgreSQL/Redis local development environment.

