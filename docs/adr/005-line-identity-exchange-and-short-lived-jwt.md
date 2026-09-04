# ADR-005: Verify LINE Identity Server-Side and Issue Short-Lived AU-Van JWTs

## Status

Accepted

## Context

LIFF gives the React application a LINE ID token. Browser-provided profile fields and claimed roles can be forged, while future booking and administrator actions need API-enforced identity and authorization.

## Options considered

- Trust the profile details supplied by the LIFF browser.
- Send every business request to LINE for verification.
- Verify the LINE ID token once at the API boundary, then issue a short-lived AU-Van JWT.

## Decision

React sends only the LINE ID token to Spring Boot. Spring Boot verifies it with LINE using the configured LINE channel ID, resolves a local user by the verified LINE subject, and issues a 15-minute JWT signed by AU-Van.

The JWT contains the local user ID and application role. Spring Security validates it on later API requests. New verified users receive the `STUDENT` role; administrator promotion is a controlled operational action. The browser cannot select a role.

## Consequences

- The API, rather than React route visibility, enforces authorization.
- LINE credentials and the JWT signing key remain server-side configuration.
- A user must exchange a fresh LINE token after the AU-Van token expires until a future refresh/session design is approved.
- The LINE verification client can be replaced in tests, avoiding live external credentials in CI.
