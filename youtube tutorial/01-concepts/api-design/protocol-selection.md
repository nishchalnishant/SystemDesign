---
id: protocol-selection
tags: [api-design, networking, grpc, graphql]
confidence: 3
last-rehearsed: 2026-07-20
source: 3. API Design — "Protocol"
---
# Protocol selection

**Claim in one sentence.** The protocol follows from who the caller is and which direction data flows — and answering "REST" reflexively in a design with heavy internal fan-out is a missed signal.

## The decision table

| Protocol | Use when | Why |
|---|---|---|
| **REST** | Public API, third-party clients | Universal, cacheable, debuggable |
| **gRPC** | Internal service-to-service | Binary, HTTP/2 multiplexing, generated stubs |
| **GraphQL** | Mobile with varied view needs | Client picks fields, avoids over-fetching |
| **WebSocket / SSE** | Server must push (chat, live scores) | Persistent connection |

Two questions resolve nearly every case:

1. **Who calls this?** Third parties ⇒ REST, because they need to debug it with curl and cache it with standard infrastructure. Your own services ⇒ gRPC, because you control both ends and can take the binary encoding and codegen.
2. **Who initiates?** If the server must push, request/response is the wrong shape regardless of the answer to (1). SSE for one-directional (live scores, notifications), WebSocket for bidirectional (chat, collaborative editing).

## What you say in an interview

> "Public-facing I'd keep REST — third parties need to curl it and standard caching works. But between the internal services here there's heavy fan-out, so gRPC: binary encoding, HTTP/2 multiplexing over one connection, and generated stubs mean the contract is compile-time-checked. For the live feed the server initiates, so that's SSE — one-directional, and it reconnects natively unlike a raw WebSocket."

## What each one actually buys

**gRPC** — the win is HTTP/2 multiplexing (many concurrent calls on one connection, no per-request handshake) plus protobuf's compactness. The schema is the real benefit: breaking a contract is a compile error rather than a production 500. The cost is that browsers can't speak it without a proxy, and you lose curl-level debuggability.

**GraphQL** — solves over-fetching for clients with divergent needs, especially mobile on poor networks. It moves query cost to the server: an unbounded nested query is a denial of service, so you need depth limiting, complexity scoring, and dataloader batching to avoid N+1. Caching is also harder — every query is a POST to one endpoint, so HTTP caching doesn't apply.

**SSE vs WebSocket** — SSE is plain HTTP, reconnects automatically, and traverses proxies cleanly. Prefer it whenever the flow is server-to-client only. WebSocket is the answer only when the client also needs to push on the same connection.

## Probes you should survive

- *"Why not gRPC everywhere?"* → Browsers need grpc-web plus a proxy, and you lose human-readable debugging. Internal-only is where it pays.
- *"What breaks with GraphQL at scale?"* → N+1 resolvers and unbounded query cost. Dataloader batching and complexity limits are mandatory, not optional.
- *"WebSocket for notifications?"* → Overkill if the server is the only sender. SSE gives you reconnection for free.
- *"Does REST mean JSON over HTTP?"* → Not strictly, but in an interview it does. The distinction worth drawing is resource-shaped URLs and correct verb semantics, not RESTfulness purity.

## Related

[cursor-pagination](./cursor-pagination.md) · [api-versioning](./api-versioning.md) · [status-codes](./status-codes.md)
