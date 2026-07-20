---
id: lb-request-mutation
tags: [load-balancing, networking, tls, security]
confidence: 3
last-rehearsed: 2026-07-21
source: 5. Load Balancing — "What the LB does to your request"
---
# What the LB does to your request

**Claim in one sentence.** A load balancer is not transparent — it hides the client IP, terminates TLS, and reshapes connection patterns — and each of those causes a specific, recurring class of production bug.

## Client IP is lost

The backend sees the LB's IP on every connection. That silently breaks IP-based rate limiting (every request appears to come from one address), geolocation, and audit logging.

- **L7** — the LB appends `X-Forwarded-For: <client>, <proxy1>`. **Clients can forge this header**, so trust only the entries your own proxies added: count from the right, and treat anything beyond your known proxy depth as attacker-controlled. Taking the leftmost value is the classic vulnerability — it's the one the client wrote.
- **L4** — there's no HTTP layer to add a header, so use the **PROXY protocol**: a short text preamble carrying the real source address ahead of the TCP payload. Both ends must be configured; enabling it on one side only breaks the connection outright, because the receiver parses the preamble as application data or vice versa.

## TLS terminates at the LB

Backends receive plaintext, which means:

- The LB is where TLS version and cipher policy are enforced
- The LB — not the backend — holds your certificates
- The LB→backend hop is unencrypted unless you re-encrypt (TLS passthrough, or mTLS via a service mesh)

Whether that last one matters depends on whether you trust the network between them. In a shared VPC or multi-tenant environment, you shouldn't.

## Connection reuse reshapes traffic

The LB maintains a keepalive pool to backends. Consequences worth stating:

- A load test through the LB and one hitting backends directly give **different numbers** — different connection counts, different handshake costs
- `least-connections` counts *LB-to-backend* connections, not client connections. With multiplexing (HTTP/2, gRPC) those diverge sharply — one backend connection can carry many client streams, so the count stops tracking real load

## What you say in an interview

> "Three things the LB changes. The backend loses the client IP, so IP rate limiting needs `X-Forwarded-For` — and read it from the right, since clients can forge the left entries. TLS terminates at the LB, so it holds the certs and the backend hop is plaintext unless I re-encrypt. And the LB pools connections to backends, which matters because least-connections is counting those, not client connections — with HTTP/2 multiplexing that count stops reflecting actual load."

## Probes you should survive

- *"Why is the leftmost `X-Forwarded-For` unsafe?"* → The client wrote it. A client can send `X-Forwarded-For: 1.2.3.4` and your proxy appends to it — so the leftmost entry is attacker-chosen input.
- *"When do you re-encrypt to the backend?"* → When the network between them isn't trusted, or a compliance regime requires encryption in transit end to end.
- *"Why does DSR help?"* → **Direct Server Return** takes the LB out of the response path — requests go through it, responses go backend-to-client directly. For video and large downloads, where responses dwarf requests, that multiplies effective capacity.
- *"How does HTTP/2 break least-connections?"* → One connection carries many concurrent streams, so connection count no longer approximates in-flight work. You need request-level counting instead.

## Related

[lb-algorithm-selection](./lb-algorithm-selection.md) · [lb-sizing](./lb-sizing.md) · [lb-lifecycle](./lb-lifecycle.md)
