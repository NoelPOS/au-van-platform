# AU-Van

LINE-integrated van booking system for Assumption University.

This repository is a clean rebuild of the legacy Next.js application. It will use React for the web experiences and Spring Boot, PostgreSQL, and Redis for the backend platform.

## Status

The planning foundation is complete. The current implementation task is the local development bootstrap; see [project status](docs/project-status.md).

## Planned structure

```text
web/        React application: admin and student LIFF route groups
api/        Spring Boot modular monolith
infra/      local containers and deployment infrastructure
tests/e2e/  Playwright end-to-end coverage
docs/       product, architecture, decisions, and migration inventory
```

## Local development

Prerequisites: Docker Desktop, Node.js 20+, and Java 21. On macOS, install Java with `brew install openjdk@21`, then add `export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"` to `~/.zshrc` and restart the shell before running Gradle.

Copy the safe local defaults; do not commit the resulting `.env` files. Generate a unique local password, then add it as `POSTGRES_PASSWORD` in the root `.env` file. It is intentionally not supplied by this repository.

```sh
cp .env.example .env
cp web/.env.example web/.env
openssl rand -base64 24
```

Paste the generated value after `POSTGRES_PASSWORD=` in `.env`.

Start PostgreSQL and Redis:

```sh
docker compose up -d postgres redis
```

If you started PostgreSQL before creating `.env`, recreate the local database so it receives the new password. This deletes local development data only:

```sh
docker compose down -v
docker compose up -d postgres redis
```

In separate terminals, start the API and web app. Spring Boot uses the database password exported from the ignored root `.env` file:

```sh
cd api
set -a
source ../.env
set +a
./gradlew bootRun
```

```sh
cd web
npm ci
npm run dev
```

The frontend runs at `http://localhost:5173` and displays the result of the API health check. The API health endpoint is `http://localhost:8080/actuator/health`.

Run validation locally with `cd web && npm run lint && npm run test && npm run build` and `cd api && ./gradlew test`.
