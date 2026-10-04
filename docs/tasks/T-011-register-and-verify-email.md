# T-011 Register family and verify email

**Milestone:** M1 | **Size:** M | **Depends on:** T-010 | **Status:** Done

## Why

Parents can create a family account and prove they own their email address, which reserving will require (BR-29).

## References

- Requirements: FR-ID-01, FR-ID-02, NFR-06, NFR-07
- Rules: BR-28, BR-29, BR-31
- Design: `docs/architecture/domain-model.md`, `docs/architecture/security-and-privacy.md`, `docs/architecture/api-conventions.md`
- Contract: `POST /api/v1/auth/register`, `POST /api/v1/auth/verify-email`, `POST /api/v1/auth/verify-email/resend`; `Problem` schema

## Scope

- Migration `V3__identity_accounts.sql`: `family`, `account` (unique on `lower(email)`, role, status and consent checks), `verification_token` (SHA-256 hash only, unique); outbox foreign key to `account`.
- `RegistrationService`: register (Argon2id hash, common-password check, silent for known emails, safe under concurrent duplicates), verify (single use, 24 hours, row lock), resend (replaces older links, silent for unknown or verified emails).
- `EmailVerificationRequested` event; `notification` queues the T-010 `VERIFY_EMAIL` email in the same transaction.
- `ApiException` can carry field errors; new `TOKEN_INVALID` code; `Problem` schema in the contract for the generated client.
- Web: `/register` and `/verify-email` pages with field errors and a resend form; `TextField` and `CheckboxField` components.

## Acceptance criteria

- [x] Given a new email and valid details with both consent boxes ticked, then an unverified MEMBER account and family are created, consent version and time stored, and a verification email queued.
- [x] Given an email that already exists (any case), then the response is identical and no email is sent.
- [x] Given a password under 12 characters or on the common list, then registration is rejected with a field error.
- [x] Given simultaneous registrations of one email, then one account is created and every caller gets the same response.
- [x] Given a valid link within 24 hours, then the account is verified; the same link then fails with `TOKEN_INVALID`.
- [x] Given an expired, used, replaced or unknown token, then `TOKEN_INVALID`; the user can ask for a new link, which invalidates older ones.
- [x] Logs of registering, verifying and resending contain no email, name, token or password.
- [x] The register and verify pages have no axe violations; e2e registers, reads the email from Mailpit and verifies.

## Out of scope

- Login, sessions, CSRF and rate limits on register and resend (T-012).
- Checking passwords against breached-password services (would need an external call).
- Legal pages for the consent text (T-061); the version comes from `club.identity.consent-version`.

## Design notes and risks

- Enumeration: the password is hashed before the email lookup, so known and unknown emails take similar time.
- Until T-012 adds rate limits, resend could be used to send repeated links to an unverified address. Not deployed anywhere yet.
