---
id: api-versioning
tags: [api-design, compatibility, evolution]
confidence: 3
last-rehearsed: 2026-07-21
source: 3. API Design — "Versioning"
---
# API versioning

**Claim in one sentence.** Mobile clients never fully migrate, so a `/v2/` is not a transition — it is a permanent commitment to run both versions, which makes additive-only evolution the only cheap path.

## Why it happens

A web client updates when the user reloads. A mobile client updates when the user chooses to, on a schedule you do not control, on devices that may never update again. There is always a long tail still calling `/v1/`, and turning it off breaks real users.

So the cost of a breaking change is not "a migration" — it is running and maintaining two code paths indefinitely. That asymmetry is what makes the additive discipline worth so much.

## The rules

`/v1/` in the path. Then:

- **Add fields freely** — clients that don't know a field ignore it
- **Never remove a field** — someone reads it
- **Never repurpose a field** — the worst option, because it fails *silently*: old clients keep parsing successfully and get wrong meaning
- **Never tighten validation** — a request that used to succeed must keep succeeding
- **Never change a default** — omitting a field must keep meaning what it meant

Anything that survives those rules is not a breaking change and needs no `/v2/`.

## What you say in an interview

> "Version in the path, `/v1/`, and then work hard to never need `/v2/` — adding fields is free, removing or repurposing them isn't. The reason to care is that mobile clients never fully migrate, so shipping a v2 means running both forever, not for a quarter. When it is genuinely unavoidable, I'd run them side by side and instrument per-version traffic so there's data behind any deprecation conversation."

## Where the version goes

| Placement | Note |
|---|---|
| **Path** (`/v1/users`) | Most common, visible in logs, trivially routable at the gateway |
| Header (`Accept: application/vnd.api.v1+json`) | Purer REST, keeps URLs stable; harder to debug, easy to omit |
| Query param (`?version=1`) | Tangles with caching; avoid |

Path in an interview. It routes at the load balancer without parsing headers, and it shows up in every log line and trace.

## Probes you should survive

- *"How do you ever deprecate v1?"* → Instrument per-version traffic, contact the remaining callers, set a sunset date with `Deprecation` / `Sunset` headers. It's a relationship problem more than a technical one.
- *"Why is repurposing worse than removing?"* → Removing fails loudly; the client errors and you find out. Repurposing succeeds and means something different — silent data corruption.
- *"Do internal services need versioning?"* → Less — you can deploy both sides. But not zero: during a rolling deploy old and new run simultaneously, so each change still has to be compatible across one hop.
- *"GraphQL versioning?"* → Deprecate fields rather than version the endpoint; `@deprecated` plus field-level usage metrics is the idiom.

## Related

[protocol-selection](./protocol-selection.md) · [status-codes](./status-codes.md) · [cursor-pagination](./cursor-pagination.md)
