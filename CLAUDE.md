# CLAUDE.md

Guidance for the AI coding agent working in this repository. Read this first, then the specs named in the task card.

## Project

Curiouskids Club: a platform for a small home-based kids' community. Release 1 is the lending library: online registration, 24x7 reservation, and pickup/return only in fixed daily time windows. Other areas (courses, events, digital learning) may follow as new modules; do not build for them until a spec and ADR exist. Runs cheaply today, must scale to 10,000 books and about 1,000 families without a rewrite. Operated by 1 to 2 volunteers, so admin work must be minutes a day.

## Source of truth (read before coding)

1. The task card in `docs/tasks/backlog.md` (or `docs/tasks/T-xxx-*.md`) you were asked to do.
2. Requirements: `docs/product/requirements.md`; rules: `docs/product/business-rules.md`.
3. Design: `docs/architecture/*.md`; decisions: `docs/adr/*.md`.
4. API shape: `contracts/openapi.yaml`.

If the spec and the request conflict, or the spec is silent on something that changes behaviour, stop and ask. Do not invent business rules. If behaviour must change, update the spec in the same PR in a separate commit prefixed `spec:`.

## Stack (see docs/adr/0003-tech-stack.md)

- Backend: Java 25 (LTS), Spring Boot 4.1.x, Gradle wrapper (ADR-0008), Spring Modulith, Spring Data JPA, Flyway, PostgreSQL.
- Frontend: React 19, TypeScript (strict), Vite, Node 24 LTS, TanStack Query, React Router.
- Tests: JUnit 5, AssertJ, Testcontainers (real PostgreSQL), Vitest, React Testing Library, MSW, Playwright.
- Infra: Docker, Terraform, AWS (ECS Fargate, RDS, S3, CloudFront, SES), GitHub Actions.

## Commands

The Makefile is the only entry point. Do not invent other ways to build or test.

```
make dev       # local stack with hot reload
make verify    # format check + lint + all unit/integration tests + build (must pass before a PR)
make api-test  # backend tests only
make web-test  # frontend tests only
make e2e       # Playwright against the full local stack
make format    # apply formatters
```

## Architecture rules (non-negotiable)

- One deployable backend: a modular monolith. Modules: `identity`, `catalogue`, `scheduling`, `circulation`, `notification`, plus `shared`.
- A module may use another module only through its public root package (services, events, DTOs). Never import another module's `internal` package. `ApplicationModules.verify()` and ArchUnit tests enforce this; do not disable them.
- Cross-module reactions use application events (Spring Modulith), not direct calls into internals.
- Controllers are thin: validate input, call a service, map to a DTO. Business rules live in module services and domain classes, never in controllers or SQL scattered around.
- Entities are never returned from controllers; use DTOs (records).
- Inject `java.time.Clock`; never call `Instant.now()` or `LocalDate.now()` directly in business code. All timestamps are stored in UTC; slot dates and times use the club timezone (`club.timezone`).
- Money is not used in release 1. If it appears, use `BigDecimal` and integer minor units in the DB.

## Database rules

- Schema changes only through Flyway migrations in `apps/api/src/main/resources/db/migration`, named `V<number>__<description>.sql`.
- Never edit a migration that has been merged. Add a new one.
- Invariants are enforced by the database as well as code (unique and partial unique indexes, foreign keys, check constraints). See `docs/architecture/domain-model.md`.
- Every table has `id` (UUID), `created_at`, `updated_at`; use optimistic locking (`version`) on aggregates that users edit.

## API rules

- Edit `contracts/openapi.yaml` first, then implement. CI fails if the running API and the contract differ.
- Errors are RFC 9457 problem+json. Every error has a stable `code` (for example `SLOT_FULL`). See `docs/architecture/api-conventions.md`.
- The web app uses the client generated from the contract. Never hand-write API types in `apps/web`.

## Testing rules

- Write tests from the acceptance criteria first (red), then code (green), then refactor.
- Name or tag tests with the requirement ID, for example `@DisplayName("FR-RES-01: reserve a title with a pickup slot")`.
- Use Testcontainers PostgreSQL for anything touching the database. Do not use H2.
- Concurrency-sensitive rules (double booking, slot capacity) need a multi-threaded test.
- Do not mock what you own; mock only external systems (email, ISBN lookup).

## Security and privacy rules

- Never commit secrets. Configuration comes from environment variables; local defaults live in `infra/local`.
- Never log personal data (emails, names, child details) or tokens.
- Never put tokens or user data in `localStorage`. Auth is an HttpOnly session cookie plus CSRF token (docs/adr/0004).
- Collect the minimum about children: first name and age band only.

## Working method

1. State which task card and requirement IDs you are implementing.
2. Present a short plan and wait for approval before editing files.
3. Keep the change to one task card and roughly one PR. If it is growing, stop and propose a split.
4. Run `make verify` and fix failures before reporting done. Report what you ran and the result.
5. Summarise: files changed, tests added, spec changes, anything left undone.

## Never

- Never run against or ask for production credentials. Deployments are done by the CI pipeline after human approval.
- Never disable, skip or delete a failing test to get green.
- Never add a dependency without stating why and checking it is maintained and licence-compatible.
- Never create files outside the layout in README.md without asking.
