# AU-Van

LINE-integrated van booking system for Assumption University.

This repository is a clean rebuild of the legacy Next.js application. It will use React for the web experiences and Spring Boot, PostgreSQL, and Redis for the backend platform.

## Status

The planning foundation, local development environment, authentication boundary, initial administrator bootstrap, and transport-inventory API are complete. The transport-inventory administration UI is in progress; see [project status](docs/project-status.md).

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

Point git at the tracked hooks once per clone, so commit messages are checked
locally before they reach CI:

```sh
scripts/setup-hooks.sh
```

Copy the safe local defaults; do not commit the resulting `.env` files. Generate a unique local password, then add it as `POSTGRES_PASSWORD` in the root `.env` file. It is intentionally not supplied by this repository.

```sh
cp .env.example .env
cp web/.env.example web/.env
openssl rand -base64 24
```

Paste the generated value after `POSTGRES_PASSWORD=` in `.env`.

For the authentication boundary, also generate a JWT signing key with `openssl rand -base64 32` and set it as `JWT_SECRET` in the ignored root `.env`. Add `LINE_CHANNEL_ID` only when you are ready to test a real LIFF token exchange. `VITE_LIFF_ID` belongs in the ignored `web/.env`; LINE channel secrets never belong in the frontend.

## Bootstrap the initial administrator

After you know your verified LINE user ID, add it as `ADMIN_BOOTSTRAP_LINE_SUBJECT` in the ignored root `.env`. Then run this one-off command from `api/` after exporting the root environment file:

```sh
set -a
source ../.env
set +a
./gradlew bootstrapAdmin
```

The command creates that local user if needed, or promotes the existing user to `ADMIN`. It is safe to run again. Never expose this operation as a public API endpoint.

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
