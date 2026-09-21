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

Prerequisites: Docker Desktop, Node.js 22+, and Java 21. On macOS, install Java with `brew install openjdk@21`, then add `export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"` to `~/.zshrc` and restart the shell before running Gradle.

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

Start PostgreSQL, Redis, and MinIO:

```sh
docker compose up -d postgres redis minio
```

If you started PostgreSQL before creating `.env`, recreate the local database so it receives the new password. This deletes local development data only:

```sh
docker compose down -v
docker compose up -d postgres redis minio
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

## Payment-proof storage

Payment-proof images live in a private S3 bucket (ADR-009): AWS S3 in the
deployed target, MinIO locally, one client either way. MinIO starts with its
own `minioadmin` / `minioadmin` default, so put that pair in the ignored root
`.env` as `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` -- the AWS SDK's
default credentials chain is what reads them. `PAYMENT_PROOF_BUCKET` and
`PAYMENT_PROOF_REGION` have working defaults; set `PAYMENT_PROOF_ENDPOINT` to
`http://localhost:9000` only when running the API on the host with `./gradlew
bootRun`, since `compose.yaml` already points the `api` container at the MinIO
service.

The bucket is not created for you. Once, after MinIO is up, open the console at
<http://localhost:9001>, sign in with those credentials, and create a bucket
named `au-van-payment-proofs`. Leave it private: nothing outside the API ever
reads it, and no URL to it is ever sent to a browser.

Neither `./gradlew test` nor CI needs any of this. The tests substitute an
in-memory implementation of the storage port, so the suite still runs with no
container and no credential.

## LINE Messaging

Notifications and departure reminders are pushed through the LINE Messaging API
(ADR-010). Set `LINE_CHANNEL_ACCESS_TOKEN` in the ignored root `.env` to the
long-lived channel access token of your **Messaging API** channel.

**This is a different channel from `LINE_CHANNEL_ID`.** That one is a LINE
**Login** channel and is used only to verify a LIFF id token; this one is a LINE
**Messaging API** channel and is used only to send. They are separate channels
with separate credentials, created separately in the LINE Developers console.

**Create the Messaging channel under the same LINE provider as the Login
channel.** A LINE user id is scoped to its provider: the `sub` this system
stores from the Login channel is a valid push target only if the Messaging
channel shares that provider. Put the Messaging channel under a different
provider and every push fails with a recipient error — a `404` naming an unknown
user — which looks exactly like a bug in this API and is not one. Nothing in the
code can detect the mismatch, so this is the thing to check first if no
notification ever arrives.

Two further facts about delivery, both of them normal rather than faults:

- A push reaches only a student who has **added your official account as a
  friend**. There is no way to know in advance, so the first attempt is also the
  discovery: LINE answers `404`, the outbox row is recorded as a dead letter, and
  it is not retried, because retrying it would fail identically forever.
- Each send carries the outbox row's id as `X-Line-Retry-Key`, which LINE
  deduplicates for twenty-four hours. That is what makes a retried send at most
  one message the student actually sees. The whole retry schedule
  (`outbox.backoff-base`, `outbox.backoff-cap`, `outbox.max-attempts`) is set to
  finish in minutes, far inside that window; changing any of the three means
  re-checking it.

Neither `./gradlew test` nor CI needs the token. The tests substitute a
recording implementation of the send port, or drive the real one against a
mocked HTTP server, so the suite runs with no channel and no credential — the
same posture the payment-proof storage takes. Only an actual push to an actual
phone needs a real channel. With the token left empty, notifications are still
recorded and attempted and land as dead letters; set `LINE_MESSAGING_ENABLED` to
`false` to have them recorded and resolved without being attempted at all.

## Run the whole stack in containers

The two `Dockerfile`s build the API and the production web bundle, and
`compose.yaml` runs them next to PostgreSQL, Redis, and MinIO. Nothing here is
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
