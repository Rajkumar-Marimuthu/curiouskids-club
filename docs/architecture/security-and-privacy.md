# Security and privacy

**Status:** Draft v0.1 | **Date:** 2026-09-19 | Decisions: ADR-0004 (authentication). This is engineering guidance, not legal advice; confirm the privacy law for the operating country (OQ-01).

## Principles

1. Collect as little as possible, especially about children.
2. Deny by default: every endpoint needs an explicit rule; every data access is checked against the caller's identity, never against an ID the client sends.
3. Defence in depth: framework protections, database constraints, network isolation, monitoring.
4. Secure by default in code and configuration; the agent may not weaken a control to make a test pass.

## Threats and controls

| Threat | Control |
| --- | --- |
| Account enumeration (register, login, reset reveal whether an email exists) | Identical responses and similar timing for known and unknown emails (FR-ID-01, FR-ID-04) |
| Password guessing and credential stuffing | Rate limits per account and per IP, lockout window, strong hashing, breached/common password rejection (FR-ID-03) |
| Session theft, CSRF | HttpOnly, Secure, SameSite=Lax cookie with `__Host-` prefix; CSRF token header on all state-changing requests; session rotation on login; idle and absolute timeouts |
| XSS | React output encoding, no `dangerouslySetInnerHTML`, strict Content Security Policy, no inline scripts |
| Broken access control (one family reading another's data) | Ownership derived from the session; `@PreAuthorize` role checks; per-endpoint tests that a second family gets 404 |
| Privilege misuse by volunteers | Least privilege roles, audit log of staff actions, overrides need a reason, MFA for staff |
| Injection | Parameterised queries via JPA and Spring Data only; no string-built SQL; Bean Validation on all input |
| Email abuse (spamming via reset or verification) | Rate limits, token single-use and expiry, no user-supplied text in emails |
| Malicious CSV or file upload | Size and row limits, content-type sniffing, parse in a sandboxed library, images re-encoded, never executed |
| SSRF through ISBN lookup | Outbound calls only to an allow-listed host, short timeouts, no user-supplied URLs |
| Secret leakage | Secrets in AWS Secrets Manager or SSM; none in git; gitleaks in pre-commit and CI |
| Vulnerable dependencies | Dependabot, dependency audit, container image scan, CodeQL; critical and high findings block release |
| Data loss | Backups and restore drills (NFR-10), deletion protection on the database |
| Personal data in logs | Log only IDs and codes; tests assert that logs from key flows contain no email, name or token |

## Authentication and sessions

- First-party accounts with server-side sessions stored in PostgreSQL (Spring Session JDBC), not JWTs in the browser.
- Password hashing: Argon2id (Spring Security `Argon2PasswordEncoder`) or bcrypt with cost 12 or more, behind the delegating encoder so it can be upgraded. Minimum length 12, no composition rules, reject common and previously breached passwords.
- Sessions: members 14 days sliding; staff 30 minutes idle and 8 hours absolute. Logout and password change revoke sessions.
- Tokens (email verification 24 hours, password reset 1 hour, staff invitation 7 days): random 256-bit, stored hashed, single use.
- MFA (TOTP) required for VOLUNTEER and ADMIN before public launch (T-060). Recovery codes generated at enrolment.

## Authorization

| Role | Can |
| --- | --- |
| Anonymous | Browse catalogue and slots, register, log in, reset password |
| MEMBER | Everything about their own family: profile, children, reservations, loans, renewals, data export and deletion |
| VOLUNTEER | Pick list, check-out and check-in, no-show, overdue, on-behalf actions, catalogue add and edit, copy status |
| ADMIN | All volunteer actions plus windows, closures, settings, users, invitations, audit log |

Rules: check role at the controller (`@PreAuthorize`) and ownership in the service. A resource that exists but is not yours returns 404. Staff endpoints live under `/staff` and `/admin` and are additionally restricted by role in the security filter chain.

## Web hardening

| Header or setting | Value |
| --- | --- |
| Content-Security-Policy | `default-src 'self'; img-src 'self' data: <covers host>; script-src 'self'; style-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'` |
| Strict-Transport-Security | `max-age=31536000; includeSubDomains` |
| X-Content-Type-Options / Referrer-Policy | `nosniff` / `strict-origin-when-cross-origin` |
| Permissions-Policy | `camera=(self), microphone=(), geolocation=()` (camera needed for barcode scanning) |
| Cookies | `__Host-SESSION` and `XSRF-TOKEN`: Secure, Path=/, SameSite=Lax (session also HttpOnly) |

CloudFront adds the headers; the API also sets them so local and staging behave the same.

## Privacy by design

Data inventory:

| Data | Whose | Why | Retention |
| --- | --- | --- | --- |
| Email, password hash, name, optional phone | Parent | Account and reminders | Until deletion or inactivity limit (BR-37) |
| Consent version and timestamp | Parent | Proof of consent | With account |
| Child first name or nickname, age band | Child | Suggest suitable books | With family; deleted with account |
| Loan and reservation history | Family | Operate the library, overdue follow-up | Anonymised on account deletion |
| Outbox email address and template values | Recipient | Send transactional emails | Cleared 30 days after sending; FAILED rows kept for admin review |
| Audit log (IDs only) | Staff and family IDs | Accountability | 2 years |
| Application logs (no personal data) | Requests | Operations | 30 days |

Rules:

- Never collect surname, birth date, school, address, photo or free-text notes about a child.
- No analytics or advertising trackers, no third-party scripts, no external fonts. Only strictly necessary cookies (session, CSRF), so a cookie consent banner should not be needed; confirm locally.
- Provide a privacy notice and terms, versioned; consent recorded (BR-28).
- Subject rights: export (FR-ID-08) and deletion; a documented process to answer within the legal deadline.
- Processors: AWS (hosting, SES). Use a region appropriate to the operating country (OQ-01) and a data processing agreement.
- Breach process: a runbook covers detection, containment, assessment and notification within the legal deadline (for example 72 hours under UK/EU GDPR).
- Staff see only what their task needs; family contact details appear on the pick list and overdue list, not in bulk exports.

## Secure development

- Threat-model each new feature briefly in its task card ("what could go wrong?").
- Security tests are part of the suite: access control (IDOR), CSRF, rate limits, header presence, log redaction.
- CI: gitleaks, dependency audit (Maven and npm), Trivy image scan, CodeQL. Findings at critical or high severity fail the build.
- Before launch: an independent review pass (agent-assisted plus a human) against OWASP ASVS level 1, and a penetration-style check with OWASP ZAP baseline against staging.
