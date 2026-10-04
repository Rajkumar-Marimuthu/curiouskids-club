# T-010 Notification outbox and email sender

**Milestone:** M1 | **Size:** M | **Depends on:** T-003 | **Status:** Done

## Why

Every later email (verification, reset, invitation, reservations, reminders) must reach families reliably and exactly once, even across restarts and provider outages.

## References

- Requirements: FR-NOT-01, FR-NOT-03
- Rules: DB-09 (`docs/architecture/domain-model.md`)
- Design: `docs/architecture/overview.md` (scheduled jobs, ShedLock), `docs/architecture/security-and-privacy.md`, `docs/architecture/deployment-and-operations.md` (alarms, `MAIL_FROM`, `APP_BASE_URL`, `AWS_REGION`)
- Contract: no endpoints

## Scope

- Migration `V2__notification_outbox.sql`: `notification_outbox` with unique `dedupe_key`, status and attempts checks, partial indexes for due and failed rows.
- Public API in `notification`: `Notifications.enqueue(OutboxEmail)` (joins the caller's transaction, ignores a repeated dedupe key) and `Notifications.failedCount()`; `EmailType` with the payload keys each template needs.
- `OutboxDispatcher`: claims due rows one at a time with `FOR UPDATE SKIP LOCKED`; on failure waits 1, 5, 15, 60 minutes; after the 5th failed attempt the row is FAILED. Logs carry the row ID, type and exception type only.
- `OutboxJobs`: dispatch every 30 seconds and a daily retention run, both under ShedLock (`SchedulingConfig` in `shared`).
- `OutboxRetention`: clears `recipient_email` and `payload` of rows sent more than 30 days ago.
- `OutboxMetrics`: gauge `club.outbox.emails{status=pending|failed}` for the "any outbox row FAILED" alarm.
- `EmailSender` port with SMTP (Mailpit) and SES adapters, chosen by `club.notification.transport`.
- `EmailTemplates`: Thymeleaf HTML and plain-text templates per type under `templates/email/`, a shared mobile-friendly layout, links built from `APP_BASE_URL`.
- Email types now: `VERIFY_EMAIL`, `PASSWORD_RESET`, `STAFF_INVITATION`. Later tasks add theirs with their templates.

## Acceptance criteria

- [x] Given an email is queued in the same transaction as the business change, when that transaction commits, then a later dispatcher run sends it (it is in the database, so it survives a restart).
- [x] Given the business transaction rolls back, then no email is queued.
- [x] Given `enqueue` is called outside a transaction, then it is refused.
- [x] Given a send failure, then it is retried after 1, 5, 15 and 60 minutes, and after 5 failed attempts it is FAILED, counted by `failedCount()` and never retried.
- [x] Given a retry or job re-run queues the same email again (same dedupe key), then it is queued and sent once (DB-09, also enforced by the unique index).
- [x] Given several dispatchers run at once, then each email is sent exactly once.
- [x] Every email has an HTML body (mobile viewport, max width 560px) and a plain-text alternative containing the link.
- [x] Logs from failures contain no email address.
- [x] Emails sent more than 30 days ago have their address and values cleared.

## Out of scope

- Admin page listing FAILED emails (T-052 dashboard reads `failedCount()`).
- Templates for reservation, cancellation, promotion and closure emails (T-040 to T-042) and reminders (T-050).
- Foreign key from `recipient_account_id` to `account` (T-011 creates `account`).
- Setting `club.notification.transport=ses` in the staging and production profiles, the SES identity and the CloudWatch alarm (T-005, T-064).

## Design notes and risks

- At-least-once: a crash after the provider accepted an email but before the row is marked SENT resends it on the next run. Acceptable for transactional email; the window is one email.
- Payload values are inserted into templates, so callers pass only system values (paths with tokens, dates, catalogue text), never text a member typed. `OutboxEmail` accepts only the keys its type declares and only paths inside the web app.
- Tokens sit in `payload` until the row is cleared; tokens expire long before that (24 hours at most for members, 7 days for invitations).

## Definition of Done

As in `docs/00-how-we-work.md`.
