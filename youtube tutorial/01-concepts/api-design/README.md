# API Design Concepts

8 single-claim concept files on API design. Each states a claim, gives a line to say verbatim in an interview, and lists the probes that usually follow it.

| File | What it covers |
|------|-----------------|
| [protocol-selection](./protocol-selection.md) | Two questions resolve protocol choice: who calls, and who initiates |
| [cursor-pagination](./cursor-pagination.md) | Why `OFFSET 100000` scans and discards 100k rows and breaks under inserts; cursor pagination fixes it |
| [idempotency-keys](./idempotency-keys.md) | A lost response is indistinguishable from a lost request — idempotency keys resolve retries safely |
| [api-versioning](./api-versioning.md) | Mobile clients never fully migrate, so `/v2/` endpoints are effectively permanent |
| [status-codes](./status-codes.md) | `401` means refresh and retry; `403` means don't bother |
| [interface-first-design](./interface-first-design.md) | Make illegal states unrepresentable; design behavior over exposed state |
| [exceptions-as-contract](./exceptions-as-contract.md) | Use `Optional` for expected absence, exceptions for contract violations |
| [concurrency-in-contract](./concurrency-in-contract.md) | Per-operation atomicity does not imply compound-sequence atomicity |

See also: [INDEX.md](../INDEX.md) for the mistakes table and HLD→LLD mapping for this topic.
