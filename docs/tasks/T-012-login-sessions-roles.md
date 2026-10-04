# T-012 Login, logout, password reset, sessions, roles, CSRF and request limits

**Milestone:** M1 | **Size:** L (split in two PRs) | **Depends on:** T-011 | **Status:** Done

Delivered in two PRs. Part 1: Spring Security, sessions, login and logout, lockout, roles, CSRF. Part 2: password reset (FR-ID-04) and the 429 limits on register, resend and reset.

## Why

Families and volunteers can log in and stay logged in safely, and every later endpoint is closed by default to people without the right role.

## References

- Requirements: FR-ID-03, FR-ID-04 (FR-ID-07 role rules), NFR-05, NFR-06, NFR-07
- Design: `docs/architecture/security-and-privacy.md`, `docs/architecture/api-conventions.md`, `docs/architecture/domain-model.md`, ADR-0004
- Contract: `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`, `GET /api/v1/auth/me`, `GET /api/v1/auth/csrf`, `POST /api/v1/auth/password-reset/request`, `POST /api/v1/auth/password-reset/confirm` (429 also on register and resend); `LoginRequest`, `MeResponse`, `PasswordResetRequest`, `PasswordResetConfirmRequest`

## Scope

- Spring Security, deny by default: public endpoints listed in `SecurityConfig`; `/api/v1/staff/**` for VOLUNTEER and ADMIN; `/api/v1/admin/**` for ADMIN; everything else needs a login. 401 `UNAUTHENTICATED` and 403 `FORBIDDEN` as problem+json. Security headers (CSP, Referrer-Policy, Permissions-Policy, frame and content-type options).
- Spring Session JDBC (migration `V4__sessions_and_login_failures.sql`), cookie `__Host-SESSION` (HttpOnly, Secure, SameSite=Lax, Path=/). New session ID on login. Idle timeout 14 days for members, 30 minutes for staff; staff sessions end 8 hours after login (`StaffSessionLimitFilter`).
- CSRF: `XSRF-TOKEN` cookie echoed in `X-XSRF-TOKEN` on every state-changing request; the token is cleared on login and logout. The web client adds the header and fetches `GET /auth/csrf` when the cookie is missing.
- `LoginService`: one 401 message for wrong password, unknown email and disabled account, with a dummy hash check so timing matches. Unverified accounts can log in (`emailVerified` in the response).
- `LoginThrottle`: 5 failures per email or 20 per IP in 15 minutes give 429 `RATE_LIMITED` with `Retry-After`; stored as SHA-256 hashes in `login_failure`; a success clears the email's failures; daily purge after a day (ShedLock job).
- Web: `/login` page, `useMe`, log out button in the header, links by role, guards on member, staff and admin layouts that send anonymous visitors to `/login?next=` and show "You can't open this page" for the wrong role.

- Part 2: `PasswordResetService` (request: silent for unknown or disabled accounts, newest link only; confirm: password policy, single use, marks the email verified, deletes every session of the account and clears its failed logins). `PasswordResetRequested` event; `notification` queues the `PASSWORD_RESET` email.
- Part 2: `RequestThrottle` with migration `V5__request_throttle.sql`: hourly limits from `security-and-privacy.md` (register 10 per IP; reset and verification emails together 3 per email and 20 per IP), hashed keys, daily purge. Limits are `club.identity.request-limits.*`; only the local profile raises the per-IP ones, since every local request comes from one address.
- Part 2 web: "Forgot your password?" link, `/reset-password` page (request form, or new-password form from the link; invalid link offers a new one), "password changed" notice on login, and a rate-limit message on register, resend and reset.

## Acceptance criteria

- [x] Given verified credentials, when logging in, then an HttpOnly `__Host-` session is created and role, family and `emailVerified` are returned.
- [x] Given an unverified account, then login succeeds and says `emailVerified: false` (reserving is blocked in T-040).
- [x] Given a wrong password, an unknown email or a disabled account, then the response is the same 401.
- [x] Given 5 failed attempts within 15 minutes for one email, or 20 from one IP, then further attempts get 429 with `Retry-After` until 15 minutes pass.
- [x] Given logout, then the session is deleted and the old cookie no longer works.
- [x] Given a member session, then it stays for 14 idle days; a staff session for 30 idle minutes and at most 8 hours.
- [x] Given any POST, PUT, PATCH or DELETE endpoint without the CSRF token, then 403 (harness covers future endpoints automatically).
- [x] Given a member on a staff or admin endpoint, or a volunteer on an admin endpoint, then 403; anonymous gets 401.
- [x] Logs from login contain no email or password; the session row holds the account ID only.
- [x] The web app sends visitors to log in and back to the page they asked for, and has no axe violations on `/login`.
- [x] FR-ID-04: given a registered email, a one-hour single-use reset link is queued; unknown and disabled emails get the identical 202 and no email.
- [x] FR-ID-04: given a valid link and a strong password, the password changes, every session of the account ends, the email counts as verified and failed logins are cleared; a used, replaced or expired link gives `TOKEN_INVALID`; a weak password gives a field error and keeps the link usable.
- [x] Given a 4th reset or verification email for one email within an hour (known or unknown), a 21st from one IP, or an 11th registration from one IP, then 429 `RATE_LIMITED` with `Retry-After`; nothing is sent.
- [x] Request limits store only hashes; reset logs contain no email, token or password.

## Out of scope

- An email telling the account holder their password was changed (not in the spec).
- IDOR harness (a second family gets 404): with the first family-owned endpoint (T-013).
- MFA for staff (T-060); staff accounts are created in T-014.
- Trusting `X-Forwarded-For` from the load balancer for the per-IP limit (`server.forward-headers-strategy`): with the AWS setup (T-005). Until then the limit uses the connecting address.

## Design notes and risks

- Lockout by email also blocks the real owner for up to 15 minutes; this is the spec's trade-off and the message is generic.
- Spring Boot applies `server.servlet.session.cookie.*` only with an embedded server, so the cookie is defined in `SessionCookieConfig` to be identical in tests and deployments.
- Tests use `support.Csrf.csrf()` rather than Spring Security's `csrf()`, which replaces the app's cookie token repository in the shared filter chain.

## Definition of Done

As in `docs/00-how-we-work.md`.
