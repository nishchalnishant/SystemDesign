# Data Consistency Patterns

Keeping data correct across service boundaries when you cannot use one transaction.

| File | What it covers |
|------|----------------|
| [The Outbox Pattern](01-outbox-pattern.md) | Atomically writing state and its event via the same transaction. |
| [Two-Phase Commit](02-two-phase-commit.md) | Coordinated commit, and why it blocks. |
| [Saga Pattern](03-saga-pattern.md) | Choreography vs orchestration, with compensating transactions. |
