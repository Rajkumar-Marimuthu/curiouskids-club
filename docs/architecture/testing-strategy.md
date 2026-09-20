# Testing strategy

**Status:** Draft v0.1 | **Date:** 2026-09-19

Tests are the executable form of the specs. Every acceptance criterion in `docs/product/requirements.md` maps to at least one automated test whose name contains the requirement ID.

## Layers

| Layer | Scope | Tools | Runs | Share of tests |
| --- | --- | --- | --- | --- |
| Unit | Domain rules and pure logic (slot bookability, limits, due-date adjustment, promotion eligibility) | JUnit 5, AssertJ, fixed `Clock` | Every save, every PR | About 60% |
| Slice / integration | Controllers with security, repositories and migrations against real PostgreSQL, module services with events | Spring Boot test slices, Testcontainers PostgreSQL, Flyway | Every PR | About 25% |
| Architecture | Module boundaries, layering, no cycles | Spring Modulith `ApplicationModules.verify()`, ArchUnit | Every PR | Small, always on |
| Contract | Running API matches `contracts/openapi.yaml`; generated TS client compiles | springdoc export diff in CI, `tsc` | Every PR | Small |
| Frontend unit and component | Components, hooks, forms, routing, accessibility | Vitest, React Testing Library, MSW, jest-axe | Every PR | About 60% of web tests |
| End to end | Critical journeys through browser, API and database | Playwright against docker compose stack | Every PR (smoke), nightly (full) | About 10% |
| Non-functional | Performance, security, accessibility, resilience | k6, OWASP ZAP baseline, axe in Playwright, restore drill | Nightly / before release | Targeted |

## Rules

1. Write the test from the acceptance criteria before the implementation (red, green, refactor).
2. Database behaviour is tested on real PostgreSQL through Testcontainers. Do not use H2 or mocks for repositories; Postgres features (partial indexes, `SKIP LOCKED`, exclusion constraints) are part of the design.
3. Inject the clock. Tests set the date and time explicitly, including daylight saving transitions in the club timezone.
4. Do not mock what we own. Mock only external systems: SES email sender, Open Library, S3.
5. Every database invariant DB-01 to DB-10 has a test that tries to break it.
6. Tests are independent and order-agnostic; each starts from a clean schema or uses unique data. Use builders for test data, not shared fixtures.
7. A flaky test is a defect: fix or quarantine within a day (tagged `@Flaky`, excluded from the gate, tracked as a task). Never retry to hide it.
8. Never delete or weaken a test to make a build pass; change the spec first if the behaviour is intended to change.

## Must-have scenario tests

| Area | Scenario | Requirement |
| --- | --- | --- |
| Concurrency | 20 threads reserve the last copy: one READY_FOR_PICKUP, 19 WAITLISTED, no double hold | FR-RES-01, DB-01 |
| Concurrency | 30 threads book a slot with 1 place left: one succeeds | FR-RES-01, DB-05 |
| Concurrency | Check-in races a cancel on the same copy: consistent end state, one promotion | FR-CIR-03, FR-RES-04 |
| Idempotency | Same `Idempotency-Key` twice: one reservation | FR-RES-01 |
| Time | Booking cut-off, horizon, closure dates, due-date shift, DST change day | BR-05, BR-06, BR-08 |
| Promotion | Skip families over limit without losing position; expiry passes copy to next | FR-RES-03 |
| Jobs | Expiry and reminder jobs run twice: no duplicate effects or emails | FR-RES-05, FR-NOT-03 |
| Security | Family B requests family A's reservation: 404; volunteer hitting an admin endpoint: 403; CSRF missing: 403 | NFR-05 |
| Privacy | Logs of register, login, reserve contain no email or name | NFR-06 |
| Enumeration | Register and reset for known and unknown emails give identical responses | FR-ID-01, FR-ID-04 |

## End-to-end journeys (Playwright)

1. Parent registers, verifies email (read from Mailpit), logs in, adds a child.
2. Parent searches, opens a book, reserves it with a pickup slot, sees it in My reservations.
3. Volunteer opens the pick list, scans the book out; parent sees the loan and due date.
4. Volunteer scans the book in; the next waitlisted parent is promoted and chooses a slot.
5. Admin creates a window and a closure; affected bookings are cancelled and the family is emailed.
6. Accessibility: axe checks on key pages; keyboard-only reservation flow.

## Coverage and quality gates (enforced in CI)

| Gate | Threshold |
| --- | --- |
| Backend line coverage (JaCoCo) | 80% overall; 90% branch in `circulation` and `scheduling` domain packages |
| Frontend coverage (Vitest) | 80% lines for `features/` |
| Mutation testing (PIT), nightly | 70% mutation score on `circulation` and `scheduling` domain |
| Static analysis | No new Sonar-style or lint warnings; Java formatted with Spotless; TypeScript `strict`, ESLint and Prettier clean |
| Contract | Zero diff between API and `contracts/openapi.yaml` |
| Security scans | No critical or high findings |
| Requirement traceability | Every requirement marked M appears in at least one test name (`make traceability`) |
| Performance smoke (k6, staging, nightly) | 50 virtual users on catalogue, slots and reserve: p95 under 500 ms, error rate under 1% |

## Test data

- Local and staging seed data (`make seed`): 100 sample titles with copies, 2 windows, 5 families, 1 admin, 1 volunteer. Fake data only; never copy production data into other environments.
- Unit and integration tests create their own data through builders (`aTitle().withCopies(2).build()`).

## CI time budget

Pull-request pipeline under 12 minutes: backend and frontend jobs in parallel, e2e smoke in parallel, full e2e and non-functional checks nightly.
