# T-013 Profile and child profiles

**Milestone:** M1 | **Size:** S (split in two PRs) | **Depends on:** T-012 | **Status:** Done

Part 1 (PR #20): child profiles (FR-ID-05) and the reusable other-family access test. Part 2: profile, password and email change, reminder preferences (FR-ID-06).

## Why

Families record which children borrow, with the least personal data possible, before reserving arrives in T-040.

## References

- Requirements: FR-ID-05, NFR-06, NFR-07
- Rules: BR-15 (`limits.max-children`, 6), BR-30
- Design: `docs/architecture/domain-model.md` (`child`), `docs/architecture/security-and-privacy.md` (ownership, 404 for another family's data), `docs/architecture/api-conventions.md`
- Contract: `GET/POST /api/v1/me/children`, `PATCH/DELETE /api/v1/me/children/{id}`; `ChildRequest`, `ChildUpdateRequest`, `ChildResponse`

## Scope

- Migration `V6__child.sql`: `child` (first name 1 to 40 characters, age band check, foreign key to `family` with cascade).
- `ChildService`: list, add (locks the family row and counts against `limits.max-children`), change, remove. The family always comes from the session; another family's child is `NOT_FOUND`.
- `/api/v1/me/**` is for MEMBER accounts only (staff 403, anonymous 401).
- Request bodies reject unknown fields everywhere (`spring.jackson.deserialization.fail-on-unknown-properties`), with a field error naming the field.
- Test support: `TestAccounts.Account.session()` and the `OtherFamily.cannotReach` harness for every later family-owned endpoint.
- Web: the "Your children" page (list, add, edit, remove with confirmation), a "Children" link in the member menu, and a `SelectField` component.

## Acceptance criteria

- [x] Given a logged-in member, when adding a child with first name and age band, then it is saved under the family.
- [x] Given 6 children already, then adding another fails with `LIMIT_REACHED`; simultaneous adds never pass 6.
- [x] Given a request with any other child field (on add or change), then it is rejected with `VALIDATION_FAILED` naming the field.
- [x] Given a blank or over-long name or an unknown age band, then a field error.
- [x] Given another family, then changing or removing the child gets 404 and the list does not show it.
- [x] Logs about children carry IDs only.
- [x] The page has no axe violations.

## Part 2: profile and preferences (FR-ID-06)

### References

- Requirements: FR-ID-06, FR-ID-03 (wrong current passwords count as failed logins), NFR-06
- Rules: BR-36 (pickup and due-soon reminders can be switched off, overdue cannot)
- Design: `docs/architecture/domain-model.md` (`account`, `verification_token`), `docs/architecture/security-and-privacy.md` (tokens, sessions, request limits)
- Contract: `GET/PATCH /api/v1/me/profile`, `POST /api/v1/me/password`, `POST /api/v1/me/email`, `POST /api/v1/auth/email-change/confirm`; `ProfileResponse`, `ProfileUpdateRequest`, `PasswordChangeRequest`, `EmailChangeRequest`, `EmailChangeConfirmRequest`

### Scope

- Migration `V7__account_profile.sql`: `account.pending_email`, `remind_pickup` and `remind_due_soon` (default true); token type `EMAIL_CHANGE`.
- `ProfileService`: profile read and change (name and phone on the family, toggles on the account), password change, email change request and confirmation.
- Emails: `EMAIL_CHANGE` (link to `/confirm-email`, 24 hours) to the new address and `EMAIL_CHANGED` (notice, no link) to the previous one.
- Web: the "Your account" page (details, reminder emails, login email, password) and the public "Confirm your new email" page.

### Acceptance criteria

- [x] Given a member, then name, phone and the two reminder toggles can be changed; both toggles start on; any other field (such as an overdue toggle) is rejected naming the field.
- [x] Given a wrong current password, then a password or email change is refused with a field error, and it counts towards the login lockout (429 after 5).
- [x] Given a password change, then this session continues under a new ID, every other session ends, and older reset links stop working.
- [x] Given an email change, then the account keeps its address until the 24-hour link sent to the new address is used; the link is single use and a newer request replaces it; the previous address gets a notice.
- [x] Given a new address that already has an account, then the response is the same and no link is sent; if it gains one before confirmation, the link fails and nothing changes, also when two accounts confirm at once.
- [x] Logs about profiles carry IDs only; the pages have no axe violations.

## Out of scope

- Using the reminder toggles: the scheduled reminder jobs (T-050) read them.
- Password and email change for staff accounts (T-014 or later).
- Choosing a child on a reservation (T-040).

## Definition of Done

As in `docs/00-how-we-work.md`.
