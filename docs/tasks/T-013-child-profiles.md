# T-013 Profile and child profiles (part 1 of 2: child profiles)

**Milestone:** M1 | **Size:** S (split in two PRs) | **Depends on:** T-012 | **Status:** In progress

Part 1 (this PR): child profiles (FR-ID-05) and the reusable other-family access test. Part 2: profile, password and email change, reminder preferences (FR-ID-06).

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

## Out of scope

- FR-ID-06 profile, password, email and reminder preferences: part 2.
- Choosing a child on a reservation (T-040).

## Definition of Done

As in `docs/00-how-we-work.md`.
