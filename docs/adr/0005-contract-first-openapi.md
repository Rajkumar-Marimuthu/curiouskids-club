# ADR-0005: Contract-first REST API with OpenAPI and a generated TypeScript client

**Status:** Proposed
**Date:** 2026-09-19
**Deciders:** Rajkumar (owner)

## Context

The API and the web app are built by an AI agent working from specs. The API shape is a spec artefact that should be reviewed before code is written, and the web app must never drift from what the API really does.

## Decision

Maintain `contracts/openapi.yaml` (OpenAPI 3.1) by hand as the API specification. Implement the API against it. CI exports the running API's OpenAPI document with springdoc-openapi and fails if it differs from the contract. Generate the TypeScript client from the contract with openapi-typescript into `apps/web/src/api` and commit it; CI fails if regeneration produces a diff.

## Options considered

| Option | Complexity | Cost | Scalability | Team familiarity |
| --- | --- | --- | --- | --- |
| A. Contract-first YAML plus CI diff against the running API | Medium | None | Good | Medium |
| B. Code-first: generate the contract from controllers | Low | None | Good | High |
| C. GraphQL | High | None | Good | Medium |
| D. Generate server stubs from the contract (openapi-generator) | High | None | Good | Medium |

**A pros:** the API is reviewed as a spec before implementation, matching spec-driven development; the agent has an unambiguous target; drift is caught mechanically.
**A cons:** two artefacts to keep in step (the diff test enforces it).
**B cons:** the contract is a by-product, so API design is decided in code review of controllers, and accidental changes slip through.
**C:** a richer query model that this simple resource-oriented API does not need.
**D:** generated server code fights hand-written module design and adds build complexity.

## Trade-off analysis

The CI diff gives most of the safety of stub generation without its cost, and keeps controllers hand-written and thin.

## Consequences

- Easier: front-end and back-end changes reviewed against one contract; typed client; mock server (MSW) from the same contract.
- Harder: every endpoint change edits the YAML first.
- Revisit if the contract becomes hard to maintain by hand (then consider splitting it into files or moving to code-first with a snapshot test).

## Action items

1. [ ] Contract, export test and `make api-client` (T-003).
2. [ ] Client generation checks in CI (T-004).
