# AU-Van

LINE-integrated van booking system for Assumption University.

This repository is a clean rebuild of the legacy Next.js application. It will use React for the web experiences and Spring Boot, PostgreSQL, and Redis for the backend platform.

## Status

The planning foundation, local development environment, authentication boundary, initial administrator bootstrap, and transport-inventory API are complete. The transport-inventory administration UI is in progress; see [project status](docs/project-status.md).

## Planned structure

```text
web/        React application: admin and student LIFF route groups
api/        Spring Boot modular monolith
infra/      Terraform for the AWS target topology
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

For the authentication boundary, also generate a JWT signing key with `openssl rand -base64 32` and set it as `JWT_SECRET` in the ignored root `.env`. Add `LINE_CHANNEL_ID` only when you are ready to test a real LIFF token exchange. `VITE_LIFF_ID` belongs in the ignored `web/.env`; LINE channel secrets never belong in the frontend. `CORS_ALLOWED_ORIGINS` defaults to the Vite dev server and needs no change locally; add the LIFF tunnel's origin to it, comma-separated, only when testing through one -- never a wildcard, since these requests carry an `Authorization` header.

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

## Run the whole stack in containers

The two `Dockerfile`s build the API and the production web bundle, and
`compose.yaml` runs them next to PostgreSQL and Redis. Nothing here is
deployed anywhere and no registry is involved.

```sh
docker compose up -d --wait
```

It reads the same ignored root `.env`, so `POSTGRES_PASSWORD` and `JWT_SECRET`
have to be set first: the API refuses to start without a signing key, and
Flyway needs the database. The API answers on `http://localhost:8080` and the
production web build on `http://localhost:8081`.

That second port serves the bundle and proxies `/api` and `/actuator` to the
API, so the browser sees a single origin. This is the same contract the Vite
dev proxy gives you at `http://localhost:5173` and, per ADR-007, the one
CloudFront gives the deployed demo. `VITE_API_BASE_URL` is therefore
deliberately unset for a container build: Vite inlines build-time values into
the bundle, so an image built with a host in it would point every visitor's
browser at that host.

`VITE_LIFF_ID` is the exception, because the LINE SDK needs it before any
network call. It is a build argument, and an image built with one is specific
to that LINE channel.

```sh
docker compose build --build-arg VITE_LIFF_ID=your-liff-id web
```

Stop the stack with `docker compose down`, or `docker compose down -v` to
delete the local database volume as well.

Run validation locally with `cd web && npm run lint && npm run test && npm run build` and `cd api && ./gradlew test`.
