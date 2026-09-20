# ADR-0002: Modular monolith (Spring Modulith) instead of microservices

**Status:** Proposed (pending owner confirmation, OQ-07)
**Date:** 2026-09-19
**Deciders:** Rajkumar (owner)

## Context

The system is small: at most about 1,000 families, 10,000 books and under 100 concurrent users, run by 1 to 2 volunteers and built by one developer. The core rules (reservations, slot capacity, waitlist, loans) need strong consistency across catalogue, scheduling and circulation data. The owner wants an enterprise-grade, scalable, maintainable design.

## Decision

Build one deployable Spring Boot application organised as modules with enforced boundaries (`identity`, `catalogue`, `scheduling`, `circulation`, `notification`, `shared`) using Spring Modulith, one PostgreSQL database, and events between modules. Design so any module can be extracted later.

## Options considered

| Option | Complexity | Cost | Scalability | Team familiarity |
| --- | --- | --- | --- | --- |
| A. Modular monolith | Low to medium | Lowest (1 service, 1 database) | Scales vertically and horizontally as stateless instances; ample for the target | High |
| B. Microservices per module | High | 3 to 5 times the runtime and pipeline overhead | Independent scaling not needed at this size | Medium |
| C. Layered monolith without boundaries | Low | Lowest | Same runtime scaling, but decays into a big ball of mud | High |

**A pros:** one transaction covers "hold a copy and book a slot", so no sagas or eventual consistency for the critical path; simple deploys, debugging and local development; boundaries are tested so the design stays clean; extraction path exists.
**A cons:** one deployment unit (all modules release together); discipline needed to keep boundaries, which the tests enforce.
**B pros:** independent deploy and scaling, technology freedom.
**B cons:** distributed transactions for reservations, network failure modes, multiple pipelines, contract versioning, observability overhead, higher hosting cost (each service needs its own container and often database); needs a team to justify it.
**C cons:** no enforcement; maintainability suffers.

## Trade-off analysis

Microservices solve organisational and independent-scaling problems this project does not have, and they make the hardest requirement (no double booking) harder. A modular monolith gives the maintainability and evolvability of clear modules without distributed-system cost. "Enterprise-grade" here means clear boundaries, tests, CI/CD, security, observability and infrastructure as code, all of which apply equally.

## Consequences

- Easier: correctness of reservations, local development, testing, cost, refactoring across modules; new feature areas (courses, events, learning) become new modules that reuse `identity`, `scheduling` and `notification`.
- Harder: nothing significant now; extraction later needs an anti-corruption layer and data split.
- Revisit and consider extracting a module when one of these is true and measured: a module needs independent scaling or availability; a second team owns it; release cadence conflicts; a module's load dominates the database. The likely first candidate is `notification`.

## Action items

1. [ ] Owner confirms or overrides (answer OQ-07 in `vision-and-scope.md`); if overridden, supersede this ADR and rewrite `overview.md` before T-003.
2. [ ] Module verification tests in CI (T-003).
