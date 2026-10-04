# T-014 Staff invitations and user administration (part 1 of 2: invitations)

**Milestone:** M1 | **Size:** M (split in two PRs) | **Depends on:** T-012 | **Status:** In progress

Part 1 (this PR): invitations, accepting them, and the first admin. Part 2: the user list, revoking invitations, deactivating and reactivating accounts.

## Why

Volunteers and admins run the club, and only an admin can bring them in (BR-31), so the club needs invitations and a safe way to get its first admin without a password in configuration.

## References

- Requirements: FR-ID-07, FR-ADM-03 (invitations audited), NFR-06
- Rules: BR-31
- Design: `docs/architecture/domain-model.md` (`staff_invitation`), `docs/architecture/security-and-privacy.md` (tokens, retention), `docs/architecture/deployment-and-operations.md` (`CLUB_BOOTSTRAP_ADMIN_EMAIL`)
- Contract: `POST /api/v1/admin/invitations`, `POST /api/v1/auth/invitations/accept`; `InvitationRequest`, `InvitationResponse`, `InvitationAcceptRequest`

## Scope

- Migration `V8__staff_invitation.sql`: `staff_invitation` with a partial unique index for one open invitation per email; the unused `STAFF_INVITE` token type is removed.
- `StaffInvitationService`: invite (admin only, VOLUNTEER or ADMIN), accept (creates a verified staff account with no family), first-admin invitation, and a daily purge of closed invitations after 30 days.
- `FirstAdminInvitation`: at start-up, invites `CLUB_BOOTSTRAP_ADMIN_EMAIL` while no admin exists. The local profile uses `admin@curiouskids.local` (see Mailpit).
- Audit entries `STAFF_INVITED` and `STAFF_INVITATION_ACCEPTED` with IDs and the role only.
- Web: the "Accept your invitation" page and an invite form on the admin Users page.

## Acceptance criteria

- [x] Given an admin, when inviting an email as VOLUNTEER or ADMIN, then a 7-day single-use link is emailed; accepting it creates a verified account with that role and no family.
- [x] Given a newer invitation for the same email, then the older link no longer works.
- [x] Given an email that already has an account, then the invitation is refused with `CONFLICT`; if the email gains an account before the link is used, the link fails.
- [x] Given a volunteer or member, then inviting gets 403; anonymous gets 401.
- [x] Given the same link used twice at once, then one account is created.
- [x] Given no admin and `CLUB_BOOTSTRAP_ADMIN_EMAIL`, then an ADMIN invitation is sent at start-up, and not again while it still works or once an admin exists.
- [x] Invitations and acceptances are audited; logs and audit details carry no email or token.
- [x] The pages have no axe violations.

## Out of scope

- User list, pending invitations and revoking them, deactivating and reactivating accounts: part 2.
- MFA for staff (T-060).

## Design notes and risks

- The first-admin invitation is idempotent across instances: the partial unique index lets only one open invitation per email exist.
- Accepting locks the invitation row, so a link cannot create two accounts.

## Definition of Done

As in `docs/00-how-we-work.md`.
