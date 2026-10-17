# ADR-020: Sign In to the Live Demo Without LINE

## Status

Accepted

## Context

Every way into the application goes through LINE (ADR-005). The live demo at `auvan.duckdns.org` (ADR-015) holds no real students and exists to be shown. The owner has one LINE account, so a demo can show either the student side or the staff side, never both, and a visitor without the LINE app cannot try it at all.

ADR-013 already avoids LINE for the end-to-end suite, but only by pointing the API at a fake verification host. That is safe because no deployed API is ever pointed there. It is not a way to sign in to a deployed demo.

## Options considered

- **A second LINE account.** The owner does not have one, and it still leaves visitors without LINE unable to try the demo. Rejected.
- **Point the demo host at ADR-013's fake verifier.** Anyone could then sign in as any subject, including the real administrator's. Rejected.
- **Seed fixed demo accounts with passwords.** Adds a password store and a login form for accounts that are thrown away. Rejected.
- **Two demo endpoints behind a switch that is off by default.** Chosen.

## Decision

`POST /api/v1/auth/demo/student` and `POST /api/v1/auth/demo/staff` exist only when `auth.demo.enabled` is `true` (`DEMO_LOGIN_ENABLED`). The default is off, and off they answer `404`, as though they were never built. Only `compose.prod.yaml`, the live demo host, turns them on by default.

The student endpoint is open to anyone. It creates an ordinary `app_users` row whose LINE subject is `demo-student:<uuid>` and returns the same `AuthResponse` as the LINE exchange. A browser that sends back the `studentId` it was given is signed in as the same demo student, so its bookings survive the 15-minute token expiry. An id that belongs to anyone other than a demo student is ignored and a new demo student is created, so the endpoint can never sign in a LINE user or the demo staff.

The staff endpoint signs in one fixed administrator, LINE subject `demo-staff`, created on first use. It requires the passcode in `auth.demo.staff-passcode` (`DEMO_STAFF_PASSCODE`), held only by the owner in the host's ignored `.env`, compared with `MessageDigest.isEqual` so the comparison takes the same time however much of the guess is right. A wrong passcode answers `401` with code `invalid_passcode`. With no passcode configured the staff endpoint answers `404` while the student endpoint keeps working.

A demo staff token lasts two hours (`auth.demo.staff-token-ttl`, `DEMO_STAFF_TOKEN_TTL`) instead of the usual 15 minutes, because an interview demo runs longer than that. Staff cannot renew silently the way demo students do, since that would mean storing the passcode in the browser. Demo student and LINE tokens keep the 15-minute lifetime.

No LINE message is recorded for a demo user: `OutboxRecorder` writes no outbox row when the recipient's LINE subject starts with `demo-`. Such a push could only fail and be retried five times. Live updates on the page are unaffected.

## Consequences

- The owner can show both sides of the system from one browser, and a visitor can try the student side without LINE.
- Anyone can create demo students on the demo host, and each one is a row in `app_users`. That is acceptable on a host with no real students, and the reason the switch is off everywhere else.
- The only protection against guessing the staff passcode is its length. There is no rate limiter, so it must be long and random, for example `openssl rand -base64 24`. Anyone who has it is an administrator of the demo.
- The switch is the security control. Turning it on for a host with real students would let anyone in as a student, so it must stay off outside the demo host.
- Demo users never receive a LINE message, so the demo cannot show a notification arriving on a phone. A LINE sign-in still can.
- Two first-time staff sign-ins at the same instant can race on the unique LINE subject, and the loser gets an error. Signing in again succeeds.
