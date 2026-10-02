# ADR-014: Run the Live Demo on an Oracle Always Free VM, Deployed over a Forced-Command SSH Key

## Status

Accepted

## Context

ADR-004 asks for a demo that costs nothing to keep running, and the AWS topology in `infra/` is deliberately brought up only for evidence sessions. The project still needs a URL a reviewer or a student can open on any day: HTTPS, because LIFF refuses a plain-HTTP endpoint, and the whole stack the repository already runs in `compose.yaml` — the API, PostgreSQL, the S3-compatible object store and the nginx-served web build.

A redeploy should follow every green build on `main` with no manual step, and the repository is public, which constrains how CI may reach the server.

## Options considered

- **A free platform tier that sleeps (Render, Koyeb and similar).** No server to administer, but an idle service is stopped, the first request waits for a JVM cold start, and the free databases expire or cap storage. Rejected: the booking-expiry, waitlist and outbox sweeps (ADR-010, ADR-011) do not run while the service sleeps, so a deadline passes unenforced while no one is visiting.
- **A serverless PostgreSQL free tier (Neon) behind any host.** Its compute allowance and auto-suspend make the one-minute sweeps either keep it awake past the allowance or wake it on every poll. Rejected for the same always-on reason, and it would still leave the API needing a host.
- **A self-hosted GitHub Actions runner on the VM.** Simple to wire up, but GitHub advises against self-hosted runners on public repositories: a pull request can change a workflow to run on one, which would put arbitrary code on the production host. Rejected.
- **An Oracle Cloud Always Free Ampere A1 VM running the existing compose stack, with Caddy for TLS and deploys over SSH.** Chosen.

## Decision

The live demo runs on one `VM.Standard.A1.Flex` instance (arm64, Ubuntu 24.04) in Oracle's Always Free allowance, using `compose.yaml` with the `compose.prod.yaml` overlay.

- **One origin.** Caddy is the only service that publishes ports. It obtains a certificate automatically for `PUBLIC_HOST`, an `<ip>.sslip.io` name that needs no DNS purchase, and proxies everything to the web container, which already routes `/api` and `/actuator` to the API. `CORS_ALLOWED_ORIGINS` is set to that one HTTPS origin.
- **Redis is not run.** No code uses it yet and its health indicator is off, so the API starts and reports healthy without it.
- **Deploys are pushed over SSH by a GitHub-hosted runner.** `.github/workflows/deploy.yml` runs after `Continuous integration` succeeds on a push to `main`, or by hand, in a `production` environment limited to `main`. It holds one SSH key, and the server's `authorized_keys` binds that key to `deploy/deploy.sh` with `restrict`, so the key can do nothing but deploy a commit already on `main`; `deploy.sh` accepts a full 40-character sha or nothing, which means the tip of `main`. The host key is pinned from a secret rather than trusted on first use.
- **Every image is published for linux/arm64.** Each digest pin is a multi-arch index, not a single-platform manifest, so the same pins build on the VM as on CI's amd64 runners.

## Consequences

- The demo costs nothing and stays awake, including its sweeps.
- One VM is a single point of failure, with no high availability and no managed backups; `deploy/backup.sh` keeps seven nightly dumps on the same disk, which protects against a bad migration or a mistake, not against losing the VM.
- Images are built on the VM at each deploy, so a deploy takes minutes and competes with the running stack for CPU while Gradle and Vite run. There is no registry and no image signing.
- The `deploy` user is in the `docker` group, which is root-equivalent on the host; the forced command and `restrict` are what keep the CI key from using that.
- Oracle may reclaim Always Free instances it judges idle, and its capacity for A1 shapes varies by region. Both are outside the repository's control.
- The AWS topology in `infra/` remains the production target of ADR-004 and ADR-007; this host does not replace it.
