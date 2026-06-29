# Hard — HLD Problems

High-level design problems for deeper SDE-2 follow-ups and SDE-3-style practice. Focus on correctness, failure recovery, operations, and scaling limits.

## Problems

| Problem | File | Key concepts |
|---------|------|--------------|
| **Distributed Cache** | [distributed-cache.md](distributed-cache.md) | Consistent hashing, replication, failover, hot keys |
| **Distributed Job Scheduler** | [distributed-job-scheduler.md](distributed-job-scheduler.md) | Leases, retries, DLQ, idempotent workers |
| **Payment System** | [payment-system.md](payment-system.md) | ACID, idempotency, double-entry ledger, saga |
| **Hotel Booking** | [hotel-booking.md](hotel-booking.md) | Inventory consistency, date-range locking, overbooking prevention |
| **Ticketmaster Seat Booking** | [ticketmaster-seat-booking.md](ticketmaster-seat-booking.md) | Seat locking, high-concurrency checkout, expiry |

Use the [HLD template](../../07-interview-templates/hld-template.md) and [HLD cheat sheet](../../07-interview-templates/hld-cheat-sheet.md) when practicing.
