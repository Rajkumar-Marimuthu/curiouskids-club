# ADR-0007: Database constraints and short pessimistic locks for reservations and slots

**Status:** Proposed
**Date:** 2026-09-19
**Deciders:** Rajkumar (owner)

## Context

The riskiest rules are: a physical copy must never be held or lent twice, and a handover window must never exceed its capacity, even when several families reserve at the same moment and the API runs on more than one instance. Volumes are low, so correctness beats maximum throughput.

## Decision

Enforce invariants in PostgreSQL with unique partial indexes and check constraints (DB-01 to DB-10). Serialise the few contested operations with short row locks taken in a fixed order (family, slot occurrence, copy) using `SELECT ... FOR UPDATE`, and `FOR UPDATE SKIP LOCKED` for choosing a free copy and for background jobs. Use optimistic locking (`version`) for ordinary user-edited records. Require idempotency keys on reserve and check-out.

## Options considered

| Option | Complexity | Cost | Scalability | Team familiarity |
| --- | --- | --- | --- | --- |
| A. Constraints plus short pessimistic row locks | Medium | None | Ample (thousands of reservations a day) | High |
| B. Optimistic locking with retries only | Medium | None | Good under low contention; retry storms on hot rows | High |
| C. Distributed lock (Redis) | High | Extra service | Good | Medium |
| D. SERIALIZABLE isolation everywhere | Medium | None | Serialization failures need retries throughout | Medium |

**A pros:** correctness guaranteed by the database even if application code has a bug; no extra infrastructure; simple mental model; contention is tiny.
**A cons:** must keep the lock order and transactions short; deadlock possible if the order is violated (covered by tests).
**B cons:** the last-copy and last-slot cases are exactly the high-contention cases where retries cause user-visible failures.
**C:** an extra failure mode and cost for no gain. **D:** blanket retry logic complicates every use case.

## Trade-off analysis

The system is low volume and rule-heavy, so simple, provable correctness wins. The constraints act as a second line of defence behind the application logic.

## Consequences

- Easier: reasoning about correctness; property of "never double booked" is testable by trying to violate it.
- Harder: lock order must be documented and respected in every new use case that touches these tables.
- Revisit only if load tests show lock contention, in which case shard or queue slot booking rather than remove constraints.

## Action items

1. [ ] Implement per `docs/architecture/domain-model.md` in T-040, T-041, T-042.
2. [ ] Multi-threaded tests listed in `testing-strategy.md`.
