# Curiouskids Club

A platform for a small home-based kids' community. The first feature is a lending library: online registration, 24x7 book reservation and scheduled pickup and return, starting with about 100 books and growing to 10,000 without re-platforming. Later areas (digital learning, play, meetups, summer courses) are added as new modules; see "Future scope" in `docs/product/vision-and-scope.md`.

**Status:** pre-implementation. The spec pack in `docs/` is the source of truth; code is built task by task from it (see `docs/tasks/backlog.md`).

## What it does

- Parents register a family account and add child profiles.
- Members browse the catalogue and reserve a book at any time, choosing a pickup slot.
- Pickup and return happen only in fixed daily windows (for example 5:00 pm and 7:00 pm), enforced by the system.
- Volunteers scan books in and out, see a daily pick list, and get overdue and no-show lists.
- Email reminders for pickup, due and overdue.

## Repository layout

```
curiouskids-club/
├── apps/
│   ├── api/              Spring Boot (Java, Maven) modular monolith
│   └── web/              React + TypeScript + Vite single-page app
├── contracts/
│   └── openapi.yaml      REST API contract (edited first, see docs/adr/0005)
├── infra/
│   ├── local/            docker-compose for local dev (PostgreSQL, Mailpit)
│   └── terraform/        AWS infrastructure as code
├── docs/                 Specs, architecture, ADRs, task backlog (start here)
├── .github/              CI workflows, PR template, CODEOWNERS
├── CLAUDE.md             Instructions for the AI coding agent
├── Makefile              One-command dev, test, verify, build targets
└── README.md
```

`apps/`, `contracts/` and `infra/` are created during tasks T-001 to T-005. `docs/` and `CLAUDE.md` exist from the first commit.

## Documentation index

| Read this | For |
| --- | --- |
| [docs/00-how-we-work.md](docs/00-how-we-work.md) | The spec-driven workflow, branching, Definition of Done |
| [docs/product/vision-and-scope.md](docs/product/vision-and-scope.md) | Users, goals, non-goals, open questions |
| [docs/product/requirements.md](docs/product/requirements.md) | Functional and non-functional requirements with acceptance criteria |
| [docs/product/business-rules.md](docs/product/business-rules.md) | Slot, loan, hold and limit rules with configurable defaults |
| [docs/architecture/overview.md](docs/architecture/overview.md) | System context, modules, package layout |
| [docs/architecture/domain-model.md](docs/architecture/domain-model.md) | Entities, constraints, state machines, concurrency |
| [docs/architecture/api-conventions.md](docs/architecture/api-conventions.md) | REST style, errors, pagination, resource map |
| [docs/architecture/security-and-privacy.md](docs/architecture/security-and-privacy.md) | Auth, data protection, children's data |
| [docs/architecture/testing-strategy.md](docs/architecture/testing-strategy.md) | Test layers, tools, coverage gates |
| [docs/architecture/deployment-and-operations.md](docs/architecture/deployment-and-operations.md) | Environments, CI/CD, AWS, monitoring, backups |
| [docs/adr/](docs/adr/) | Architecture Decision Records (why we chose what we chose) |
| [docs/tasks/backlog.md](docs/tasks/backlog.md) | Milestones and task cards, the order of work |

## Quick start (available after T-002)

```
make dev        # start PostgreSQL + Mailpit, run API and web with hot reload
make verify     # format check, lint, unit + integration tests, build (run before every PR)
make e2e        # Playwright end-to-end tests against the full stack
```

## Working with the AI agent

Run Claude Code from the repository root so it sees `CLAUDE.md`, the specs and both apps. Use plan mode first, one task card per branch. Details in [docs/00-how-we-work.md](docs/00-how-we-work.md).
