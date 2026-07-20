---
id: status-codes
tags: [api-design, http, error-handling]
confidence: 3
last-rehearsed: 2026-07-21
source: 3. API Design — "Status codes"
---
# Status codes

**Claim in one sentence.** Status codes are the part of your contract that clients act on automatically — retry logic, auth refresh, backoff — so getting them wrong breaks client behavior in ways a correct response body cannot repair.

## The set worth knowing

`200` OK · `201` Created · `202` Accepted (async) · `400` malformed · `401` unauthenticated · `403` authenticated but not allowed · `404` · `409` conflict · `429` rate limited (send `Retry-After`) · `500` server fault · `503` overloaded

Two things get noticed: the `401`/`403` distinction, and whether `429` is present at all.

## The distinctions that carry meaning

**`401` vs `403`** — 401 means *I don't know who you are*: the token is missing, expired, or invalid, and the client should refresh it and retry. 403 means *I know exactly who you are and the answer is no*: retrying with a fresh token changes nothing. Returning 403 for an expired token sends clients into a state where they never refresh.

**`400` vs `422`** — 400 is syntactically broken (unparseable JSON). 422 parsed fine but is semantically wrong (end date before start date). Useful for clients that distinguish "my serializer is broken" from "the user typed something invalid."

**`500` vs `503`** — 500 is a bug; retrying hits the same bug. 503 is transient overload and *should* be retried with backoff, ideally with `Retry-After`. Reporting overload as 500 suppresses retries that would have succeeded.

**`202`** — you accepted the work but haven't done it. It must come with something to poll or subscribe to, or the client has no way to learn the outcome.

## What you say in an interview

> "401 versus 403 matters because clients act on it — 401 means refresh your token and retry, 403 means don't bother. And I'd make sure 429 is there with a `Retry-After`, since without it a rate-limited client just retries immediately and makes the overload worse. For async endpoints, 202 plus a status URL to poll."

## `429` and `Retry-After`

A client that gets a bare 429 will typically retry right away — so the rate limiter's response *causes* the retry storm it exists to prevent. `Retry-After` (seconds, or an HTTP date) converts it into an instruction the client can honor.

Pair it with client-side exponential backoff **plus jitter**; without jitter, every throttled client retries at the same instant and the load arrives as a spike. See [retry-budgets](../load-balancing/retry-budgets.md).

## Probes you should survive

- *"When is 404 wrong?"* → When the resource exists but the caller can't see it — that's 403. Though deliberately returning 404 to avoid leaking existence is a legitimate choice; make it consciously.
- *"Is 200 with `{"error": ...}` ever OK?"* → It defeats every piece of infrastructure that reads status codes: proxies, monitoring, client retry logic. GraphQL does it, which is a known cost of GraphQL.
- *"Which codes are safe to retry?"* → 429, 503, 502, 504. Not 400/401/403/404 — those are deterministic. 500 is ambiguous and needs a low retry cap.
- *"What about 409?"* → Conflict with current state — a version mismatch on optimistic concurrency, or a duplicate [idempotency key](./idempotency-keys.md) with a different body.

## Related

[idempotency-keys](./idempotency-keys.md) · [exceptions-as-contract](./exceptions-as-contract.md) · [retry-budgets](../load-balancing/retry-budgets.md) · [api-versioning](./api-versioning.md)
