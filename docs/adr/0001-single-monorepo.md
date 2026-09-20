# ADR-0001: Single monorepo for API, web, contracts, infrastructure and docs

**Status:** Proposed
**Date:** 2026-09-19
**Deciders:** Rajkumar (owner)

## Context

One developer with an AI coding agent builds a React front end, a Spring Boot back end, infrastructure code and specs. The agent works best when it can see the specs, the API contract and both applications in one place, and a single change (for example a new field) touches the contract, the API and the web app together.

## Decision

Use one Git repository named `curiouskids-club` containing `apps/api`, `apps/web`, `contracts`, `infra` and `docs`. No monorepo build tool (Nx, Turborepo, Bazel); a root `Makefile` and path-filtered GitHub Actions jobs are enough.

## Options considered

| Option | Complexity | Cost | Scalability | Team familiarity |
| --- | --- | --- | --- | --- |
| A. Single repo, Makefile and path filters | Low | None | Fine to about 10 apps | High |
| B. Separate repos (api, web, infra, docs) | Medium (cross-repo changes, version drift) | Free | Suits separate teams | High |
| C. Monorepo with a build orchestrator (Nx, Turborepo, Bazel) | High | Learning cost | Suits many teams and packages | Low |

**A pros:** atomic changes across contract, API and UI; one review; the agent sees everything; one CI configuration.
**A cons:** repo grows over time; all contributors see all code (irrelevant for one owner).
**B cons:** a field added to the API needs coordinated PRs; the agent cannot see the other side.
**C cons:** tooling overhead with no benefit at two applications.

## Trade-off analysis

The main risk of a monorepo (coupling between teams) does not exist here. The main benefit (atomic, agent-visible change) is exactly what spec-driven development needs.

## Consequences

- Easier: contract-first changes, consistent tooling, single source of truth for specs.
- Harder: CI needs path filters so web-only changes do not rebuild the API.
- Revisit if a second team owns a separate deployable, or if CI time exceeds 15 minutes.

## Action items

1. [ ] Create the repository and layout (T-001).
2. [ ] Path-filtered CI (T-002).
