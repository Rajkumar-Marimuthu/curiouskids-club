# ADR-0004: First-party authentication with server-side sessions

**Status:** Proposed
**Date:** 2026-09-19
**Deciders:** Rajkumar (owner)

## Context

Families and volunteers log in through one web app that shares an origin with the API. There is no mobile app, third-party API consumer, or single sign-on requirement in release 1. Children's data is involved, so token theft and cross-site attacks matter more than statelessness.

## Decision

Use Spring Security with email and password accounts, server-side sessions stored in PostgreSQL (Spring Session JDBC), a `__Host-` prefixed HttpOnly Secure SameSite=Lax cookie, and CSRF tokens on state-changing requests. No JWTs in the browser. TOTP multi-factor authentication for volunteers and admins before public launch.

## Options considered

| Option | Complexity | Cost | Scalability | Team familiarity |
| --- | --- | --- | --- | --- |
| A. Session cookie plus CSRF (Spring Security, Spring Session JDBC) | Low to medium | None | Any number of stateless instances (sessions in the database) | High |
| B. JWT access and refresh tokens held by the SPA | Medium | None | Stateless | High |
| C. External identity provider (Amazon Cognito, Keycloak, Auth0) | Medium to high | Cognito is cheap at this size; Keycloak needs its own hosting | Excellent | Medium |

**A pros:** tokens are not readable by JavaScript, so XSS cannot steal them; logout and revocation are immediate; simple with same-origin hosting; no external dependency.
**A cons:** you own password storage, reset and MFA flows; session table adds small database load.
**B cons:** tokens in browser storage are exposed to XSS; revocation is hard; more moving parts for no benefit here.
**C pros:** offloads passwords and MFA; social login. **C cons:** lock-in, more configuration, user data in another system, and a UI to integrate.

## Trade-off analysis

For a same-origin SPA and API, cookie sessions are the safest and simplest. Owning credential flows is manageable with framework support and is covered by tests (FR-ID-01 to 04). External providers become attractive when requirements change.

## Consequences

- Easier: revocation, strong browser-side security, no token handling in the front end.
- Harder: we maintain reset, verification and MFA flows; sessions need cleanup jobs.
- Revisit if we add social login or single sign-on, a native mobile app, child logins, or public API clients; then evaluate Cognito or an OIDC provider and supersede this ADR.

## Action items

1. [ ] Implement in T-012 with a reusable IDOR and CSRF test harness.
2. [ ] MFA in T-060.
