# ADR-0008: Backend build tool is Gradle, not Maven

**Status:** Accepted
**Date:** 2026-09-20
**Deciders:** Rajkumar (owner)

## Context

ADR-0003 decided Maven for the backend build. T-001's scaffold (done by hand at start.spring.io) was generated as a Gradle project instead (`build.gradle`, `gradlew`, no `pom.xml`). This was discovered while starting T-002 (repo tooling, local stack, CI), which needs to pick one build tool to wire the `Makefile` and CI around.

Re-scaffolding to Maven at this point would touch the one file that exists (`Application`, soon renamed `ClubApplication`) for no functional benefit — no feature code has been written yet, so there is nothing Maven-specific to preserve, and nothing Gradle-specific to migrate away from either.

## Decision

Keep Gradle. It satisfies the same criteria ADR-0003 used to choose the backend stack: mainstream, well supported, and friendly to an AI coding agent (strong conventions, wrapper-pinned version, large training corpus). This supersedes only the build-tool row of ADR-0003; the rest of that ADR (Java 25, Spring Boot 4.1.x, PostgreSQL, React 19, etc.) stands.

## Options considered

### Option A: Keep Gradle (chosen)

| Dimension | Assessment |
| --- | --- |
| Complexity | Low — no change to what already exists |
| Cost | None |
| Scalability | Equivalent to Maven for this project's needs |
| Team familiarity | High — owner's choice originally was "Maven over Gradle: convention-heavy, well understood, stable with agents", both of which apply equally to Gradle |

**Pros:** No rework; Gradle wrapper (`./gradlew`) gives the same reproducibility guarantee as the Maven wrapper; Spotless, JaCoCo, Testcontainers, Spring Boot's Gradle plugin are all first-class.
**Cons:** Groovy/Kotlin DSL build scripts are less uniformly conventional than Maven's XML, which matters slightly less for an agent working from a small, mostly-generated `build.gradle`.

### Option B: Re-scaffold with Maven

| Dimension | Assessment |
| --- | --- |
| Complexity | Low, but pure rework |
| Cost | Half a day of otherwise-avoidable churn |
| Scalability | Equivalent |
| Team familiarity | Matches the original ADR-0003 text exactly |

**Pros:** Matches ADR-0003 literally.
**Cons:** No functional gain; delays T-002 for zero user-visible benefit.

## Trade-off analysis

Both tools meet ADR-0003's actual criteria (mainstream, agent-friendly, long-term support). The only reason to prefer Maven was that it was written down first; since the scaffold already exists in Gradle and no code depends on the choice yet, keeping it is the lower-cost path with no measurable downside.

## Consequences

- Easier: no rework; `Makefile` and CI (T-002) are written once, against Gradle.
- Harder: nothing specific — later contributors reading ADR-0003 need this ADR's pointer to know the build-tool row is superseded.
- Revisit: only if a concrete Gradle limitation is hit (none anticipated).

## Action items

1. [x] Update CLAUDE.md's "Maven wrapper" reference to "Gradle wrapper" (T-002).
2. [x] Add a pointer note in ADR-0003 to this ADR for the build-tool row (T-002).
