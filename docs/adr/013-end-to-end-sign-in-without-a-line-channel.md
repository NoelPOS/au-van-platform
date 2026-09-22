# ADR-013: End-to-End Sign-In Without a LINE Channel

## Status

Accepted

## Context

Every screen in this application is behind LINE. The web application calls `liff.init()` before it can obtain an id token, and the API posts that token to `https://api.line.me/oauth2/v2.1/verify` before it will issue an AU-Van JWT (ADR-005). Playwright cannot drive a LINE login, and CI holds no LINE account.

Yet `AGENTS.md` requires that "User-visible booking and payment flows require Playwright E2E coverage when practical", and `docs/project-status.md` has carried "No end-to-end coverage" as a known limitation since the project began. Without a way to sign in, the suite cannot exist.

The rest of the suite has already solved this shape of problem twice, both times without weakening the product: payment-proof storage substitutes an in-memory implementation of a port, and LINE messaging either substitutes a recording sender or drives the real one against a mocked server through the configurable `notification.line.api-base-url`. "The suite runs with no channel and no credential" is an established property here, not a new ambition.

## Options considered

- **Use a real LINE channel in CI.** It puts a credential in CI for a test, ties the suite to an external service's availability, and cannot be run by a contributor who does not own the channel. Rejected.
- **A stub `LineTokenVerifier` bean in main sources, behind a Spring profile.** The cheapest change, and the worst one: it puts an authentication bypass inside the artefact that gets deployed, one property away from being switched on. The interface already exists, which makes this easy, which is the trap. Rejected.
- **Intercept LINE's traffic from Playwright.** `liff.init()` also reads URL parameters and browser storage; stubbing it from outside is brittle and breaks on an SDK bump. Rejected.
- **Mint the JWT directly in the test harness.** Requires reimplementing `JwtService` in TypeScript and keeps a copy of the signing rules in a second language. Rejected.
- **Make the verification host configurable and stub the host.** Chosen.

## Decision

Two seams, neither of which is an authentication bypass.

`auth.line.api-base-url` is added, defaulting to `https://api.line.me`, and `LineTokenVerifierImpl` reads it instead of the hard-coded literal. **No validation is removed**: the verifier still requires `iss` to be `https://access.line.me`, `aud` to equal the configured channel id, and `sub` to be non-blank. This mirrors `notification.line.api-base-url` exactly, which already exists for the same reason on the sending side.

The E2E compose overlay points that key at a small fake verification service that echoes the submitted id token back as `sub`. The fake lives in `tests/e2e/`, never in `api/` or `web/` source.

The web application gains a build-time flag, `VITE_E2E_AUTH`, gating a sign-in control that posts a chosen subject straight to the exchange endpoint. Because Vite inlines build-time values, an image built without the flag contains no such code, and CI asserts that by grepping the served bundle — the same technique the existing "Check that no API host was inlined into the bundle" step uses.

## Consequences

- The E2E suite needs no LINE channel, no credential and no network egress, the same posture the unit and integration suites already have.
- The sign-in path is safe by construction: a made-up id token is accepted only by an API deliberately pointed away from LINE. Against real LINE it is refused.
- One production code path changes — the verifier's base URL — and it is a configuration value with a safe default, not a branch.
- A deployment that misconfigures `auth.line.api-base-url` accepts forged identities. That is a real hazard and the reason this is an ADR rather than a detail. The default is the mitigation; the reviewer checks it on every change to that file.
- Two CI assertions become load-bearing and must not be deleted: the production bundle carries no E2E marker, and the verifier's default host is LINE's. The first is a step in `Container checks`, grepping the bundle copied out of the running web container; the second is a step in `API checks`, which is a **required** status check, because a default this load-bearing is the whole mitigation.
- **`e2eAuthEnabled` is a module-level constant rather than a function, and that is load-bearing.** Vite inlines `import.meta.env.VITE_E2E_AUTH` either way, but only a constant is propagated across module boundaries: written as a call, the branch survives minification and the control's markup stays in the production bundle. That was measured, not assumed, and the bundle grep is what keeps it measured.

## Delivery

Landed whole in the pull request for issue #78, together with the suite the seam exists for. `Accepted` on merge rather than `Proposed`, following ADR-010 and ADR-011 — and not repeating ADR-009's mistake of shipping its implementation while leaving the record saying the decision was still being considered, which issue #58 had to correct afterwards.
