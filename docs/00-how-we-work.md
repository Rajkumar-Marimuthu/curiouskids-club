# How we work: spec-driven development with an AI agent

Intent is the source of truth. We write down what and why, then how, then small tasks. The agent implements one task at a time, verified by tests. The human owns the specs, the architecture decisions and the review of every pull request.

## Three levels of specification

| Level | Question it answers | Lives in | Changes when |
| --- | --- | --- | --- |
| Product | What and why? | `docs/product/` | The business need or a rule changes |
| Architecture | How, and why this way? | `docs/architecture/`, `docs/adr/`, `contracts/openapi.yaml` | A design or technology decision changes |
| Task | What is the next reviewable slice? | `docs/tasks/` | Every sprint; written just in time |

Rule: a behaviour change starts as a spec change, then tests, then code. A code change without a spec change is only a refactor or a bug fix against an existing spec.

## Requirement traceability

Every functional requirement has an ID (`FR-RES-01`) with acceptance criteria in Given/When/Then form. Tests reference the ID in their display name, and PR descriptions list the IDs they satisfy. To find coverage of a requirement, search the repo for its ID.

## The per-task loop

1. **Pick** the next task card from `docs/tasks/backlog.md`. Check its Definition of Ready (below).
2. **Branch** from `main`: `feat/T-040-reserve-title` (types: `feat`, `fix`, `chore`, `docs`, `spec`, `refactor`).
3. **Plan.** Start Claude Code in plan mode from the repo root. Prompt: "Implement T-040. Read CLAUDE.md, the task card and the referenced requirements, rules and design. Propose a plan; do not edit files yet." Review the plan; correct it before approving.
4. **Tests first.** The agent writes failing tests from the acceptance criteria. Review them; they are the executable spec.
5. **Implement** until the tests pass. Small commits, conventional messages (`feat(circulation): reserve title with pickup slot [T-040]`).
6. **Verify** with `make verify`. The agent runs it and fixes failures; it may not weaken tests.
7. **Review the diff yourself** in the IDE, then open a PR using the template. CI must be green.
8. **Update specs** in the same PR if behaviour or design changed (`spec:` commit). Add or update an ADR for any significant decision.
9. **Merge** by squash. Tick the task card as done.

## Definition of Ready (task card)

- Requirement IDs and rules referenced; acceptance criteria written and testable.
- Dependencies done. API changes described (or the contract updated in the same PR).
- Out of scope stated. Fits in roughly one PR (about 400 changed lines excluding generated code and tests).

## Definition of Done (every task)

- Acceptance criteria met and covered by automated tests that name the requirement IDs.
- `make verify` passes locally and CI passes.
- No new module-boundary violations; `contracts/openapi.yaml` matches the running API.
- Migrations are forward-only and tested on a fresh database and on the previous schema.
- No secrets, no personal data in logs, no new lint or security warnings.
- Specs and ADRs updated; the task card ticked; changelog line added if user-visible.
- Reviewed by the human owner.

## Branching and commits

- Trunk-based: `main` is always releasable. Short-lived branches (one to three days).
- `main` is protected: PR required, CI green, at least one approving review (the owner), no force-push.
- Conventional Commits. Squash-merge; the PR title becomes the commit message.
- `CODEOWNERS`: the owner is required on `docs/`, `contracts/`, `infra/`, `db/migration/` and `CLAUDE.md`.

## Working with the agent

- One task per session. Start a fresh session for the next task so context stays small and focused.
- Start in plan mode. Once a kind of task has gone well several times, allow the agent to accept file edits automatically inside the working directory, but keep command approvals for anything that touches the network, cloud or Git remotes.
- Use Git worktrees (`git worktree add ../curiouskids-T-041 feat/T-041-waitlist`) to run two independent tasks in parallel.
- The agent never holds production credentials. Terraform plans are produced in CI; applying to production needs your approval in the pipeline.
- Keep `CLAUDE.md` short and current. When the agent makes the same mistake twice, add a rule there.

## Changing the plan

- New idea or rule change: edit the product spec, add or adjust task cards, then implement.
- Reversing a technology decision: write a new ADR that supersedes the old one; do not edit history.
- Unknowns: add to "Open questions" in `docs/product/vision-and-scope.md` with an owner and a date.
