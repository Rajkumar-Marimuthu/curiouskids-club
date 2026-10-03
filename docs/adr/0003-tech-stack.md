# ADR-0003: Technology stack and versions

**Status:** Proposed
**Date:** 2026-09-19
**Deciders:** Rajkumar (owner)

> Note: the build-tool choice below (Maven) is superseded by [ADR-0008](0008-build-tool-gradle.md); the backend uses Gradle. The rest of this ADR stands.

## Context

The owner is a senior engineer and chose React for the front end and Spring Boot (Java) for the back end. The stack must be mainstream, well supported for at least 3 years, friendly to AI coding agents (strong typing, conventions, large training corpus), and easy to hire for.

## Decision

| Layer | Choice | Version policy |
| --- | --- | --- |
| Language and runtime | Java 25 (LTS) | Stay on the current LTS |
| Framework | Spring Boot 4.1.x, Spring Framework 7, Spring Security, Spring Data JPA (Hibernate), Spring Modulith, Spring Session JDBC | Latest 4.1 patch; move to the next minor within 3 months and before OSS support ends |
| Build | Maven with wrapper | Pinned by the wrapper |
| Database | PostgreSQL, latest major supported by Amazon RDS | Upgrade yearly; Flyway migrations |
| Migrations | Flyway | |
| API docs and contract | OpenAPI 3.1 handwritten, springdoc-openapi for verification | See ADR-0005 |
| Front end | React 19, TypeScript (strict), Vite 8, Node 24 LTS | Node stays on Active LTS; `.nvmrc` pins it |
| Front-end libraries | React Router, TanStack Query, React Hook Form with Zod, Tailwind CSS with Radix primitives, react-i18next, openapi-typescript client | Latest stable, reviewed via Dependabot |
| Testing | JUnit 5, AssertJ, Testcontainers, ArchUnit, JaCoCo; Vitest, Testing Library, MSW, jest-axe, Playwright, k6 | |
| Quality | Spotless (Java), ESLint and Prettier, gitleaks, CodeQL, Trivy, Dependabot | |
| Delivery | Docker, GitHub Actions, Terraform, AWS (ADR-0006) | |

Versions verified against public release pages on 2026-09-19: Spring Boot 4.1.1 was current (released 2026-06-30, open-source support to 2027-07-31); React 19.3 latest; Vite 8.3 current; Node 24 was Active LTS. Re-check when starting T-001, and let start.spring.io choose compatible dependency versions rather than pinning by hand.

## Options considered

### Back end

| Option | Complexity | Cost | Scalability | Team familiarity |
| --- | --- | --- | --- | --- |
| A. Java and Spring Boot | Medium | Free | Excellent | High (owner's choice) |
| B. Kotlin and Spring Boot | Medium | Free | Excellent | Lower |
| C. Python and Django | Low | Free | Very good | Lower; would give a free admin |

A chosen: the owner's expertise and the enterprise ecosystem outweigh Django's speed of admin scaffolding. The admin screens will be built as part of the React app (T-014, T-020, T-030).

### Front end

| Option | Complexity | Cost | Scalability | Team familiarity |
| --- | --- | --- | --- | --- |
| A. React single-page app (Vite) served from S3 and CloudFront | Low | Lowest | Static hosting scales freely | High |
| B. Next.js with server rendering | Medium to high (a second server runtime) | Higher | Good | Medium |

A chosen: no public search-engine discovery requirement, and one runtime (Java) to operate. Revisit if the public catalogue needs SEO or link previews; a static pre-render of book pages would be the first step.

### Build tool

Maven over Gradle: convention-heavy, well understood, stable with agents; Gradle Kotlin DSL is an acceptable alternative but adds nothing here.

## Trade-off analysis

The stack is more code than Django but plays to the owner's strengths and is the most common enterprise combination, which helps both maintainability and hand-over. Type safety end to end (Java types, OpenAPI contract, generated TypeScript client) gives the AI agent fast, precise feedback from compilers and tests.

## Consequences

- Easier: hiring, long-term support, strong tooling, safe refactoring.
- Harder: more boilerplate than Django; the admin UI must be built.
- Revisit annually and when a Spring Boot minor approaches end of open-source support.

## Action items

1. [ ] Scaffold with these versions (T-001).
2. [ ] Dependabot and a quarterly "upgrade week" note in the backlog (T-002).
