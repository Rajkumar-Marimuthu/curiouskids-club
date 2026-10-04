# Requirements

**Status:** Draft v0.1 | **Date:** 2026-09-19

Each requirement has a stable ID, a statement, the business rules it relies on, and acceptance criteria in Given/When/Then form. Priority: **M** = must (release 1), **S** = should (release 1 if time), **L** = later. Tests must name the requirement ID.

## Identity (FR-ID)

**FR-ID-01 (M) Register a family account.** A parent registers with email, password, name, optional phone, and consent. Rules: BR-28, BR-29, BR-31.
- Given a new email, when the parent submits valid details and ticks the consent boxes, then an unverified MEMBER account is created and a verification email is queued.
- Given an email that already exists, when registering, then the response does not reveal that the email exists (same message as success).
- Given a weak password (under 12 characters or in the common-password list), then registration is rejected with field errors.

**FR-ID-02 (M) Verify email.**
- Given a valid unexpired token (24 hours, single use), when the link is opened, then the account becomes verified.
- Given an expired or used token, then the user can request a new one; the old one is invalid.

**FR-ID-03 (M) Log in and out.**
- Given verified credentials, when logging in, then an HttpOnly session is created and the user's role and family are returned.
- Given 5 failed attempts within 15 minutes for one account or 20 from one IP, then further attempts are blocked for 15 minutes with a generic message.
- Given an unverified account, when logging in, then login succeeds but reserving is blocked with `EMAIL_NOT_VERIFIED`.

**FR-ID-04 (M) Reset password.**
- Given a registered email, when a reset is requested, then an email with a one-hour single-use token is queued; the response is identical for unknown emails.
- Given a valid token and a strong new password, then the password changes and all existing sessions are revoked.
- Given a reset completed from the emailed link, then the email address also counts as verified (the link proves ownership).
- Given more than 3 reset or verification emails requested for one email address within an hour, or more than 20 such requests from one IP, then further requests get 429 `RATE_LIMITED`; known and unknown emails are limited the same way.

**FR-ID-05 (M) Manage child profiles.** Rules: BR-15, BR-30.
- Given a logged-in member, when adding a child with first name and age band, then it is saved under the family.
- Given 6 children already, then adding another fails with `LIMIT_REACHED`.
- Given a request containing any other child field, then it is rejected (no extra personal data is accepted).

**FR-ID-06 (M) Manage my profile and preferences.** Change name, phone, password, email (re-verification required), and reminder preferences (BR-36).

**FR-ID-07 (M) Staff accounts.** Rules: BR-31.
- Given an admin, when inviting a volunteer by email, then a single-use invitation link is sent; accepting it creates a VOLUNTEER account.
- Given a volunteer, then admin-only screens and endpoints return 403.

**FR-ID-08 (S) My data: export and delete.**
- Given a member, when requesting export, then a machine-readable file of their family data is produced within 24 hours.
- Given a member with no open loans, when requesting deletion, then personal data is anonymised and the account disabled; loan history is retained without personal data.

## Catalogue (FR-CAT)

**FR-CAT-01 (M) Browse and search.** Public, no login.
- Given the catalogue, when searching text, then results match title, author and series with typo tolerance, sorted by relevance.
- Filters: category, age band, availability (available now). Sort: title, newest, most borrowed. Pagination default 24 per page.
- Given 10,000 titles, then a search returns in under 500 ms server time (NFR-02).

**FR-CAT-02 (M) Title detail.** Shows cover, author, description, age band, categories, number of copies and how many are available, and the earliest pickup slot if one is available. Barcodes and internal notes are never shown to members.

**FR-CAT-03 (M) Create and edit a title (staff).** Title, author(s), ISBN-13, description, publisher, year, language, age band(s), categories, cover.

**FR-CAT-04 (M) Add by ISBN.**
- Given a valid ISBN-13, when a volunteer scans or types it, then details and cover are fetched from the external source and pre-filled for confirmation.
- Given the external source is down or returns nothing, then the form opens empty with a warning; nothing is lost. The lookup times out after 3 seconds.
- Given an ISBN that already exists, then the volunteer is offered "add another copy".

**FR-CAT-05 (M) Manage copies (staff).** Each copy has a unique barcode, status, condition and notes. Adding copies generates or accepts barcodes. Status changes are audited. Rules: copy statuses.

**FR-CAT-06 (S) Bulk import from CSV.**
- Given a CSV of titles and copies, when uploaded, then a dry run reports errors per row without saving; the admin then confirms the import; the import is idempotent on ISBN plus barcode.

**FR-CAT-07 (S) Manage categories and age bands (admin).**

## Scheduling (FR-SCH)

**FR-SCH-01 (M) Manage handover windows (admin).** Rules: BR-01 to BR-03.
- Given an admin, when creating a window (weekday, start, end, capacity), then future occurrences appear within the booking horizon.
- Given overlapping windows on one weekday, then creation is rejected.
- Given a capacity reduced below current bookings, then the change is rejected with the count of bookings.

**FR-SCH-02 (M) Manage closures (admin).** Rules: BR-04.
- Given a date with bookings, when the admin closes it, then the UI lists affected reservations and requires confirmation; on confirm they are cancelled, families emailed, copies released.

**FR-SCH-03 (M) List bookable pickup slots.** Rules: BR-05, BR-06.
- Given today's date and time in the club timezone, when listing slots, then only future windows within the horizon and before the cut-off, not closed, with remaining capacity greater than zero are marked bookable; full slots show as full.

## Reservations (FR-RES)

**FR-RES-01 (M) Reserve a title with a pickup slot.** Rules: BR-16, BR-12, BR-14, BR-29.
- Given a title with an available copy and a bookable slot, when a verified member reserves, then one copy is HELD, the slot count increases, the reservation is READY_FOR_PICKUP, and a confirmation email is queued.
- Given no slot is supplied while a copy is available, then `SLOT_REQUIRED`.
- Given the slot became full meanwhile, then `SLOT_FULL` and no copy is held.
- Given two members reserve the last copy at the same instant, then exactly one succeeds and the other is waitlisted; no copy is held twice.
- Given the same request is retried with the same `Idempotency-Key`, then the same reservation is returned, not a second one.

**FR-RES-02 (M) Join the waitlist.** Rules: BR-17, BR-13.
- Given no available copy, when reserving, then the reservation is WAITLISTED at the end of the queue and the member sees their position.
- Given the family already has the title reserved or on loan, then `ALREADY_HOLDING_TITLE`.

**FR-RES-03 (M) Promotion and slot choice.** Rules: BR-18, BR-19.
- Given a copy becomes available and the first waitlisted family is eligible, then their reservation becomes AWAITING_SLOT, the copy is HELD, and they are emailed.
- Given a family over its limits, then it is skipped without losing its position.
- Given the member chooses a slot in time, then READY_FOR_PICKUP; otherwise EXPIRED and the next family is promoted.

**FR-RES-04 (M) Cancel.** Rules: BR-22.
- Given READY_FOR_PICKUP before the cut-off, when the member cancels, then the copy is released (and promoted to the next waitlister if any) and the slot capacity is freed.
- Given after the cut-off, then only a volunteer can cancel.

**FR-RES-05 (M) Hold expiry and no-show.** Rules: BR-20, BR-21.
- Given READY_FOR_PICKUP not collected by the window end, when the expiry job runs (every 5 minutes) or the volunteer marks no-show, then it becomes EXPIRED and the copy is released. Running the job twice changes nothing more.

**FR-RES-06 (M) Limits enforced.** Rules: BR-12 to BR-14. Given the limits, then reserving beyond them fails with `LIMIT_REACHED` and states which limit.

**FR-RES-07 (M) My reservations and loans.** The member sees active reservations (with slot, position, or action needed), loans with due dates and overdue flag, and history.

## Circulation (FR-CIR)

**FR-CIR-01 (M) Daily pick list.** A volunteer selects a date and window and sees the families expected, the titles and barcodes to hand over, and status per line, sorted by family name. Works on a phone.

**FR-CIR-02 (M) Check out by scan.** Rules: BR-24, BR-07, BR-08.
- Given a READY_FOR_PICKUP reservation, when the volunteer scans the copy barcode, then a loan is created with the due date, the reservation becomes COLLECTED, the copy ON_LOAN.
- Given a barcode not matching a reservation on this date, then it is refused with the reason unless the volunteer overrides with a reason.

**FR-CIR-03 (M) Check in by scan.** Rules: BR-25, BR-18.
- Given an ON_LOAN copy, when scanned, then the loan is RETURNED, the copy AVAILABLE (or DAMAGED if flagged), and promotion runs; the volunteer sees who is next in the queue.
- Given a scan of a copy that is not on loan, then a clear message is shown and nothing changes.

**FR-CIR-04 (M) Renew.** Rules: BR-09.
- Given an eligible loan, when renewed, then the due date extends by the loan period; given a waitlist exists or the loan is overdue, then `RENEWAL_NOT_ALLOWED`.

**FR-CIR-05 (M) Overdue list.** Rules: BR-10, BR-11. Volunteers see overdue loans by days overdue with family contact and the reminders sent; a "possible lost" queue after 30 days.

**FR-CIR-06 (M) Mark lost or damaged.** Rules: BR-26.

**FR-CIR-07 (S) On-behalf actions.** Rules: BR-27. A volunteer can search a family and reserve or check out for them.

## Notifications (FR-NOT)

**FR-NOT-01 (M) Transactional emails.** Verification, password reset, invitation, reservation confirmation, cancellation, promotion, closure cancellation. Plain, kid-friendly, mobile-friendly, with a text alternative.

**FR-NOT-02 (M) Scheduled reminders.** Rules: BR-32 to BR-36.

**FR-NOT-03 (M) Reliable delivery.**
- Given an email is created in the same transaction as the business change (outbox), then it is sent even if the app restarts.
- Given a send failure, then it is retried with backoff up to 5 times, then marked FAILED and shown to admin.
- Given a retry or job re-run, then the same reminder is never sent twice (unique key per recipient, type and subject).

## Administration (FR-ADM)

**FR-ADM-01 (M) Settings.** Admin edits the rule settings in business-rules.md with validation and an audit entry per change.

**FR-ADM-02 (M) Dashboard.** Today's windows with expected count, overdue count, promotions awaiting a slot, flagged families, failed emails.

**FR-ADM-03 (M) Audit log.** Who did what and when for staff actions: check-out/in, overrides, status changes, settings, user invites, closures. Read-only for admins; no personal data beyond IDs.

## Non-functional requirements (NFR)

| ID | Requirement | Measure |
| --- | --- | --- |
| NFR-01 | Availability | 99.5% monthly for the web app (single region, single-AZ database at launch) |
| NFR-02 | Performance | 95th percentile server time under 500 ms for catalogue, slot and reservation calls at 50 concurrent users with 10,000 titles |
| NFR-03 | Scalability | Stateless API; horizontal scale to 2+ instances with no code change; database is the only stateful part |
| NFR-04 | Data integrity | No double-held copy and no over-full slot under concurrent load; enforced by database constraints |
| NFR-05 | Security | OWASP ASVS level 1 controls; no critical or high vulnerabilities at release; see security spec |
| NFR-06 | Privacy | Children's data minimised; consent recorded; export and delete supported |
| NFR-07 | Accessibility | WCAG 2.2 AA for member screens; keyboard navigable; volunteer screens usable one-handed on a phone |
| NFR-08 | Compatibility | Last 2 versions of Chrome, Safari, Firefox, Edge; iOS Safari and Android Chrome |
| NFR-09 | Observability | Structured JSON logs with correlation ID, metrics, health endpoints, alerting on errors and failed jobs |
| NFR-10 | Recoverability | Data loss at most 5 minutes; restore within 4 hours; restore drilled before launch and quarterly |
| NFR-11 | Maintainability | Module boundaries enforced by tests; at least 80% line coverage overall, at least 90% branch coverage in circulation and scheduling domain code |
| NFR-12 | Internationalisation | All user-facing text externalised; dates and times shown in the club timezone |
| NFR-13 | Cost | Launch hosting at or under $65 a month; no always-on component that is not needed |
