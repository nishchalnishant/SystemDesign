# Networking - System Design Guide

> **For SDE-3 Interview Preparation**  
> Protocols, communication patterns, DNS internals, and network design — with real-world analogies.

## Table of Contents
1. [OSI Model vs TCP/IP](#osi-model-vs-tcpip)
2. [TCP vs UDP](#tcp-vs-udp)
3. [QUIC](#quic)
4. [HTTP Evolution (1.1 → 2 → 3)](#http-evolution)
5. [REST vs GraphQL vs gRPC](#rest-vs-graphql-vs-grpc)
6. [Real-Time Communication Patterns](#real-time-communication-patterns)
7. [Load Balancing](#load-balancing)
8. [API Gateway](#api-gateway)
9. [DNS Deep Dive](#dns-deep-dive)

---

## File Mindmap

```
Networking
├── Why It Exists
│   ├── Problem → API fails; no mental model = 30min blaming wrong layer
│   └── Forces → 7 transformations from app bytes to wire signal; each layer = different failure mode
├── Core Concepts
│   ├── OSI Model → 7 layers (Physical→Data Link→Network→Transport→Session→Presentation→Application)
│   ├── TCP/IP → 4-layer practical stack (Link→Internet→Transport→Application)
│   ├── TCP → reliable, ordered, connection-oriented; 3-way handshake before data
│   ├── UDP → unreliable, no handshake; lower latency; used for video/DNS/gaming
│   └── QUIC → UDP-based; 0-RTT reconnect; multiplexed streams; no head-of-line blocking
├── HTTP Evolution
│   ├── HTTP/1.1 → persistent connections; 6 parallel connections per host; head-of-line blocking
│   ├── HTTP/2 → binary framing; multiplexing on one TCP connection; server push; header compression
│   └── HTTP/3 → QUIC transport; 0-RTT; no TCP head-of-line blocking; better mobile performance
├── API Protocols
│   ├── REST → HTTP verbs; stateless; JSON; wide tooling support → higher overhead per call
│   ├── GraphQL → single endpoint; client specifies shape → N+1 query risk; caching harder
│   └── gRPC → Protobuf binary; HTTP/2; streaming; strong typing → not browser-native; harder debug
├── Real-Time Patterns
│   ├── WebSockets → full-duplex persistent TCP; chat, live feeds → stateful, hard to load balance
│   ├── SSE (Server-Sent Events) → server push over HTTP/1.1; unidirectional; auto-reconnect
│   └── Long Polling → client holds open request; server replies when data ready → higher overhead
├── DNS Deep Dive
│   ├── Recursive resolver → queries iteratively on client's behalf; caches results
│   ├── TTL → controls cache duration; low TTL = faster failover; high TTL = fewer lookups
│   └── Anycast → same IP announced from many PoPs; BGP routes user to nearest node
├── Failure Modes
│   ├── Layer 3 routing loop → traceroute to locate hop; check BGP announcements
│   ├── TCP SYN flood → server exhausts half-open connections → SYN cookies mitigation
│   └── DNS poisoning → DNSSEC validation; resolver cache TTL minimizes window
├── Real-World Usage
│   ├── Cloudflare → anycast IP; 300+ PoPs; routes user to nearest edge in <1 BGP hop
│   ├── gRPC (Google, Netflix) → internal microservice calls; Protobuf cuts payload 60–80%
│   └── WebSockets (Slack, Discord) → persistent connection per client; message push without polling
└── Interview Angles
    ├── "TCP vs UDP — when do you choose UDP?" → latency > reliability; DNS, video streaming, games
    ├── "HTTP/2 vs HTTP/3 — key difference?" → HTTP/2 still TCP (HoL blocking); HTTP/3 uses QUIC/UDP
    ├── "REST vs gRPC — when to use gRPC?" → internal services; streaming; high-throughput binary data
    └── Follow-up: "How does DNS failover work and what are the pitfalls?" → TTL caching delays; low TTL cost
```

---

## OSI Model vs TCP/IP

**Question**: Your API call fails. Is the problem in your application code, in the network routing between machines, in the physical link between your server and its switch, or somewhere else? Without a mental model of which layer handles what, you will waste 30 minutes blaming the wrong thing. How do you instantly narrow the search space?

**Physical constraint**: Data traveling from one process on one machine to another process on another machine passes through at least seven distinct transformations — from your application's bytes to electrical signals on a wire, then back. Each transformation is handled by different hardware and software, with different failure modes. A broken NIC (Layer 2) and a misconfigured route (Layer 3) and a port-blocked firewall (Layer 4) and a slow database query (Layer 7) all produce "the API call failed" from the application's perspective.

**Minimal solution**: Name each layer and define what it owns. When something fails, start at Layer 7 (application) and descend until you find a layer behaving abnormally. The first layer whose metrics are out of spec is where the problem lives.

**Production generalization**: The OSI model is a diagnostic tool, not just an academic taxonomy. Every debugging session in distributed systems is implicitly a top-down layer traversal — from application logs to TCP retransmit metrics to network path tracing. Knowing which protocol lives at which layer tells you which command to run next.

> **Analogy: An international letter.**  
> Your words are the Application layer. The envelope format is Presentation. The tracking number is Session. The delivery guarantee is Transport. Routing through sorting facilities is Network. The local mail truck is Data Link. The physical road is Physical.

| OSI Layer | TCP/IP Layer | Protocols | Responsibility |
|-----------|--------------|-----------|----------------|
| 7. Application | Application | HTTP, DNS, SMTP | User-level data |
| 6. Presentation | Application | SSL/TLS, JPEG | Formatting/Encryption |
| 5. Session | Application | Sockets | Session management |
| 4. Transport | Transport | TCP, UDP | End-to-end delivery |
| 3. Network | Internet | IP, ICMP, BGP | Routing packets |
| 2. Data Link | Link | Ethernet, MAC | Node-to-node transfer |
| 1. Physical | Link | Fiber, WiFi | Bits over wire |

**Troubleshooting with OSI — "When your API is slow, which layer do you check first?"**

Work top-down. Start at Layer 7 (Application): Is the service returning slow responses? Check application logs, query plans, thread pools. If the app is healthy, drop to Layer 4 (Transport): Is TCP connection establishment slow? Packet loss? High retransmit rate? Check `ss -s`, `netstat`. Still fine? Drop to Layer 3 (Network): Is routing causing extra hops? Is there packet loss between nodes? `traceroute`, `mtr`. Layer 2/1 failures (bad NIC, bad cable, WiFi interference) are rare in cloud environments but show up as sudden CRC errors or flapping interfaces.

**Interview rule**: If it's latency under load → start at L7. If it's intermittent drops → start at L4/L3. If it's a whole datacenter going dark → L2/L1.

---

## TCP vs UDP

**Question**: You're designing a live multiplayer game. Player positions update 60 times per second. You have two choices: use TCP (guaranteed delivery, in-order) or UDP (best effort, no ordering). With TCP, if a position packet is lost, TCP stalls all subsequent packets until the lost one is retransmitted. The player sees their character freeze for 50–200ms waiting for one stale position update. With UDP, the lost packet is simply skipped — the next update arrives and the client interpolates. Which is better for real-time games, and why does it matter?

**Physical constraint**: TCP's reliability comes from a retransmit-and-wait mechanism — when a packet is lost, the receiver cannot advance past the gap until the missing bytes arrive in order. This is head-of-line blocking at the transport layer. Retransmit round-trips take at minimum one RTT (~1ms same-DC, ~20–100ms cross-country), during which all packets after the gap sit in a buffer unused. For data where the latest value supersedes all prior values (game positions, video frames, voice samples), waiting for stale data is strictly worse than skipping it.

**Minimal solution**: Use UDP for any real-time stream where a late packet is worse than a lost one. Implement only the reliability you actually need in the application layer — for a game, that means ordered delivery for critical events (player deaths) but not for position updates.

**Production generalization**: TCP for everything where losing a byte is unacceptable — HTTP, database connections, file transfers, email. UDP for latency-sensitive streams where partial data is preferable to stale data — games, VoIP, video conferencing, DNS lookups, DHCP. QUIC (the foundation of HTTP/3) essentially reimplements TCP's reliability per-stream on top of UDP, getting the best of both models.

### TCP (Transmission Control Protocol)

> **Analogy: Wiring a bank transfer.**  
> You fill out a form, the bank verifies your identity, you sign, they confirm on the other end, you get a receipt. Every step is acknowledged. It takes longer, but $50,000 does not vanish. This is why **bank transfers use TCP** — you cannot lose a packet and shrug.

- Connection-oriented (3-way handshake: SYN → SYN-ACK → ACK).
- Reliable: packets are acknowledged, retransmitted if lost.
- Ordered: bytes arrive in the sequence they were sent.
- Flow control + congestion control built in.
- **Use Cases**: HTTP/HTTPS, email (SMTP), file transfer (FTP), databases.

### UDP (User Datagram Protocol)

> **Analogy: A video call.**  
> During a Zoom call, a few dropped frames are invisible to the human eye. If the network retransmitted every lost video packet, you'd see the person freeze mid-sentence and then stutter through a second of old video — far worse than just skipping the frame. **Video calls use UDP** because a stale packet delivered late is worse than no packet at all.

- Connectionless — no handshake, no acknowledgement.
- No ordering guarantees.
- Low overhead, low latency.
- Application handles any reliability it needs (or deliberately ignores it).
- **Use Cases**: Video streaming, VoIP, online gaming, DNS queries, DHCP.

### TCP vs UDP at a Glance

| Property | TCP | UDP |
|----------|-----|-----|
| Connection | 3-way handshake | None |
| Reliability | Guaranteed | Best-effort |
| Ordering | Yes | No |
| Speed | Slower (overhead) | Faster |
| Head-of-line blocking | Yes | No |
| Real example | Bank transfer | Video call |

---

## QUIC

**Question**: HTTP/2 promised multiplexing — multiple requests over one TCP connection with no waiting. You deploy it, expecting faster page loads. On a lossy mobile network (2% packet loss is normal), your users report that HTTP/2 pages actually load *slower* than HTTP/1.1. How? HTTP/2 was supposed to fix head-of-line blocking.

**Physical constraint**: HTTP/2 fixed *application-layer* head-of-line blocking — multiple HTTP streams share one TCP connection so an HTTP response no longer blocks others. But TCP doesn't know about HTTP streams. When a TCP packet is lost, the TCP layer stalls the *entire* connection until the missing packet is retransmitted, regardless of how many HTTP streams are waiting. With HTTP/1.1 you had 6 TCP connections — one lost packet stalled 1/6 of your streams. With HTTP/2 you have 1 TCP connection — one lost packet stalls all streams. On lossy networks, HTTP/2 is strictly worse.

**Minimal solution**: The only fix is to move stream management below TCP — into the transport layer itself. If the transport protocol understands streams, a lost packet can block only the stream it belongs to while other streams continue.

**Production generalization**: QUIC rebuilds TCP's reliability and congestion control per-stream on top of UDP. A lost packet blocks only its own stream. TLS 1.3 is baked into the QUIC handshake (not layered on top), reducing connection setup to 1 RTT and 0-RTT for resumption. Connection migration (changing IP without reconnecting) works because QUIC uses connection IDs rather than IP:port tuples. HTTP/3 is HTTP over QUIC.

**The backstory**: HTTP/2 introduced multiplexing — multiple streams over one TCP connection. But TCP doesn't know about HTTP streams. To TCP, it's all one byte sequence. When a single packet is lost, TCP stops the entire connection and retransmits it before delivering anything else. All HTTP/2 streams freeze waiting for one dropped packet. This is **TCP's head-of-line blocking problem**, and HTTP/2 actually made it worse than HTTP/1.1 (which used multiple TCP connections).

> **Analogy: Multiple independent conveyor belts vs one shared belt.**  
> HTTP/2 over TCP is one conveyor belt split into "lanes" with tape. When the belt jams (packet loss), every lane stops. QUIC is genuinely separate conveyor belts: one jams, the others keep moving. The belts happen to share the same floor (UDP), but they're mechanically independent.

**How QUIC solves it:**
- Built on UDP — bypasses TCP's in-order delivery requirement.
- Implements its own reliability, ordering, and congestion control **per stream**.
- A lost packet blocks only the stream it belongs to; other streams continue.
- **TLS 1.3 is baked in** — not layered on top. The handshake and encryption happen together in 1-RTT (or 0-RTT for repeat connections).
- **Connection migration**: QUIC connections are identified by a Connection ID, not IP:port. If you switch from WiFi to 4G, the connection survives without reconnecting.

**QUIC is the foundation of HTTP/3.**

---

## HTTP Evolution

HTTP/1.1 had a problem. HTTP/2 solved it but created a different problem. HTTP/3 solved that.

### HTTP/1.1 — The Single-Lane Road

> **Analogy**: A single-lane mountain road. One car goes, it completes its journey, then the next car goes. If a truck breaks down halfway, everyone behind it waits.

HTTP/1.1 introduced persistent connections (Keep-Alive) — you reuse the TCP connection instead of opening a new one per request. But requests are still **sequential**. You send request A, wait for response A, then send request B. Browsers worked around this by opening 6–8 parallel TCP connections per domain, which is wasteful and still capped.

**Problem**: Head-of-line blocking. One slow response holds up everything behind it on that connection.

### HTTP/2 — The Multi-Lane Highway

> **Analogy**: A six-lane highway. Multiple cars travel simultaneously. Huge improvement. But if there's a bridge collapse (packet loss in the TCP layer), every single lane stops — because there's still only one bridge underneath.

HTTP/2 solved HTTP/1.1's application-layer head-of-line blocking:
- **Binary framing**: Requests and responses are split into binary frames, not text.
- **Multiplexing**: Multiple request/response streams share one TCP connection concurrently. No waiting.
- **Header compression (HPACK)**: Headers are compressed and deduplicated across requests.
- **Server Push**: Server can proactively send resources (e.g., push CSS before the browser asks).

**New problem**: TCP head-of-line blocking. When any packet is lost, all HTTP/2 streams on that TCP connection stall while TCP retransmits. With one connection instead of six, the impact is worse than HTTP/1.1 on lossy networks.

### HTTP/3 — Independent Helicopter Lanes

> **Analogy**: Each stream has its own air corridor. One helicopter going down doesn't ground the others. And every helicopter comes with a built-in encrypted communications system (TLS 1.3 — no separate setup needed).

HTTP/3 runs on QUIC (UDP):
- **True per-stream multiplexing**: Packet loss in stream A does not affect stream B. The transport layer understands streams.
- **Built-in TLS 1.3**: Encryption is not optional or bolted on. It's part of the protocol.
- **Faster handshake**: 1-RTT for new connections, 0-RTT for resuming known connections (client sends data with the first packet).
- **Connection migration**: Switch networks without reconnecting (QUIC Connection ID).

| Version | Transport | Multiplexing | HOL Blocking | Handshake |
|---------|-----------|--------------|--------------|-----------|
| HTTP/1.1 | TCP | No (sequential) | App + Transport | TCP (1 RTT) |
| HTTP/2 | TCP | Yes (app level) | Transport only | TCP + TLS (2 RTT) |
| HTTP/3 | QUIC/UDP | Yes (transport level) | None | 1-RTT / 0-RTT |

---

## REST vs GraphQL vs gRPC

**Question**: Your mobile app's home screen needs: the user's name and avatar, their 5 most recent orders with product thumbnails, and 3 personalized recommendations. With REST, that's 3 separate API calls: `GET /users/me`, `GET /orders?limit=5`, `GET /recommendations`. On a 4G mobile connection with 50ms RTT per request, those 3 serial calls take at least 150ms before any rendering starts. A desktop browser on a 1Gbps fiber line wouldn't care. Your mobile users do. What's the tradeoff — and when does it matter?

**Physical constraint**: REST resources are fixed shapes defined server-side. Getting exactly the fields you need for a specific screen requires either: multiple round-trips (under-fetching) each costing one RTT, or one large response that contains far more data than needed (over-fetching). On mobile, bandwidth is measured in kilobits and RTT in tens of milliseconds — both matter. GraphQL eliminates round-trips by letting the client specify exactly what it needs in one query. gRPC eliminates payload overhead via binary Protocol Buffers but requires both client and server to share a `.proto` contract.

**Minimal solution**: REST. Simple, well-understood, works with HTTP caching out of the box. Breaks when mobile client payload optimization becomes critical or when internal service-to-service calls need streaming and binary efficiency.

**Production generalization**: REST for public APIs where discoverability, broad tooling, and HTTP caching matter. GraphQL for BFF layers where different clients (mobile vs web) need different data shapes from the same backend. gRPC for internal microservice communication where both ends are owned by your team and you need streaming, strong typing, and minimal serialization overhead.

### REST

> **Analogy: A fixed restaurant menu.**  
> You get what's on the menu. Want only the burger patty without the bun? You get the full burger. Need to know which drinks are available? That's a separate request. Predictable, universally understood, but inflexible.

- Resource-based URLs: `GET /users/123`, `POST /orders`.
- Stateless. Each request is self-contained.
- HTTP caching works natively (`Cache-Control`, `ETag`).
- **Over-fetching**: Response includes fields you don't need.
- **Under-fetching**: You need multiple round-trips for related data (`GET /users/123` then `GET /users/123/orders`).

### GraphQL

> **Analogy: A custom order at a restaurant.**  
> You tell the kitchen exactly what you want: "Burger patty from the burger, fries from the combo, chocolate milkshake from the kids' menu — hold the pickles." One trip to the kitchen, one tray returns with precisely what you asked. The kitchen (server) has to be smarter, but the client gets exactly what it needs.

- Client specifies exactly which fields it needs in a single query.
- Schema-driven: strongly typed via SDL (Schema Definition Language).
- No over-fetching or under-fetching.
- **N+1 query problem**: `posts { author { name } }` triggers one DB call per post for the author. Solve with DataLoader (batch + cache within a request).
- Caching is hard: most queries are POST with unique bodies; HTTP caching doesn't apply.

### gRPC

> **Analogy: A walkie-talkie.**  
> REST is a letter — text-based, structured, occasional. gRPC is a walkie-talkie: binary signals, low latency, real-time, two-way. Both parties agree on a channel (the `.proto` contract) and speak in a compressed, efficient format that's far faster than plain text.

- Binary serialization via Protocol Buffers: 3–10× smaller and faster than JSON.
- Runs on HTTP/2: supports client streaming, server streaming, and bidirectional streaming.
- Strongly typed `.proto` files define the contract; client/server code is generated.
- No browser support natively (requires gRPC-Web proxy).

```java
// proto definition
service OrderService {
  rpc GetOrder (OrderRequest) returns (OrderResponse);
  rpc StreamOrders (OrderRequest) returns (stream OrderResponse);
}

// Java generated stub usage
OrderServiceGrpc.OrderServiceBlockingStub stub =
    OrderServiceGrpc.newBlockingStub(channel);
OrderResponse response = stub.getOrder(
    OrderRequest.newBuilder().setOrderId("abc123").build()
);
```

### Decision Guide

| Criterion | Use REST | Use GraphQL | Use gRPC |
|-----------|----------|-------------|----------|
| Client type | Browser, public API | Mobile app, BFF layer | Internal microservices |
| Data shape | Predictable, simple | Complex, varies by screen | Fixed, schema-driven |
| Caching | Yes (HTTP native) | Difficult | No HTTP caching |
| Latency sensitivity | Low | Low-Medium | High (binary, HTTP/2) |
| Streaming | No | Subscriptions | Yes (native) |
| Both ends controlled? | No | No | Yes |

**Use REST when**: Building a public API, browser clients are primary consumers, or you want HTTP caching out of the box.  
**Use GraphQL when**: Mobile clients need different data shapes than web clients, you have a BFF (Backend for Frontend) layer, or the client should drive what data it fetches.  
**Use gRPC when**: Both client and server are internal services you control, you need streaming, or latency and payload size are critical.

---

## Real-Time Communication Patterns

**Question**: You're building a live notification system — users should see order status updates within 1 second of the server event. HTTP is request-response: the client asks, the server answers. The server cannot push data to a client that hasn't asked. You need updates to flow server→client without the client constantly asking. What are your options and what does each cost?

**Physical constraint**: HTTP 1.1 is half-duplex: the client opens a TCP connection, sends a request, gets a response, and the connection idles. A server cannot initiate data delivery to a client that is sitting idle — the server doesn't have a socket to the client. For sub-second updates, the client must either periodically re-open the connection (polling, costly at scale) or maintain a persistent connection that the server can write to at any time (WebSocket, SSE).

**Minimal solution**: Poll every second. Simple, stateless, works everywhere. Breaks when you have 1 million concurrent users polling every second: 1 million HTTP requests/sec, 999,900 of which return empty (nothing changed). Every empty response wastes a TCP connection open/close and one server thread for the duration.

**Production generalization**: SSE for server-to-client streams (notifications, live feeds) — HTTP-based, auto-reconnects, one persistent connection per user. WebSockets for full-duplex bidirectional communication (chat, collaborative editing, live games) — persistent TCP connection, stateful, requires sticky sessions or a pub/sub fan-out layer. SSE is almost always simpler than WebSockets; only choose WebSockets when you genuinely need client→server low-latency messages.

### Short Polling

> **Analogy: A child in the back seat asking "Are we there yet?" every 5 minutes.**  
> The answer is "no" 95% of the time. You're burning effort on empty responses.

```
Client → Server: "Any updates?"   (every 5 seconds)
Server → Client: "Nope."
Server → Client: "Nope."
Server → Client: "Yes! Here's the update."
```

Simple. Stateless. Works everywhere. Max latency = polling interval. Wasteful at scale.

### Long Polling

> **Analogy: You call a friend and they put you on hold.**  
> Instead of hanging up and calling back every 5 minutes, you stay on the line. Your friend comes back when they have something to say — or after 30 seconds, whichever comes first.

```
Client → Server: "Give me updates when available."
Server: [holds connection open — up to 30s timeout]
Server → Client: "Here's the update."  (or timeout fires)
Client → Server: "Give me updates." (immediately reconnects)
```

Lower average latency than polling. Each request can hit a different server (stateless). Useful when WebSockets are blocked by proxies or firewalls.

### SSE (Server-Sent Events)

> **Analogy: A sports radio broadcast.**  
> The station (server) continuously transmits. You tune in (connect once) and just listen. You can't talk back over the same channel — but for score updates and live commentary, you don't need to.

```java
// Java (Spring Boot) SSE endpoint
@GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter streamEvents() {
    SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
    executorService.submit(() -> {
        try {
            for (int i = 0; i < 10; i++) {
                emitter.send(SseEmitter.event()
                    .name("update")
                    .data("score: " + i));
                Thread.sleep(1000);
            }
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    });
    return emitter;
}
```

One-way (server → client). Built on regular HTTP — no special protocol. Browser `EventSource` API handles automatic reconnect. Use for live feeds, CI/CD progress, log streaming, notification pushes.

### WebSockets

> **Analogy: A phone call.**  
> You dial, they pick up, and now you have an open two-way channel. Either side can speak at any time. The connection stays open until one of you hangs up.

```java
// Java (Spring Boot) WebSocket handler
@Component
public class ChatHandler extends TextWebSocketHandler {

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message)
            throws Exception {
        String payload = message.getPayload();
        // broadcast to all connected sessions
        session.sendMessage(new TextMessage("Echo: " + payload));
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        System.out.println("Connected: " + session.getId());
    }
}
```

Full duplex: persistent bi-directional connection over a single TCP connection. Starts as an HTTP request (`Upgrade: websocket`), then switches protocol. Stateful connections — load balancers must use sticky sessions, or use a pub/sub fan-out layer (Redis Pub/Sub) so any server can deliver any message.

### Comparison Table

| Pattern | Direction | Latency | Connection State | Complexity | Use When |
|---------|-----------|---------|-----------------|------------|----------|
| Short Polling | Client → Server (repeat) | High (interval) | Stateless | Low | Infrequent status checks, simple dashboards |
| Long Polling | Client → Server (held) | Medium | Stateless | Medium | Moderate real-time, WebSocket-blocked environments |
| SSE | Server → Client (stream) | Low | Server tracks emitters | Low-Medium | Live feeds, one-way push (notifications, logs, scores) |
| WebSockets | Bidirectional | Very Low | Stateful | High | Chat, multiplayer gaming, collaborative editing |

---

## Load Balancing

Load balancers distribute incoming traffic across multiple servers to prevent any single instance from becoming a bottleneck. L4 balancers route at the TCP/IP layer (by IP + port) without inspecting content — fast, but dumb. L7 balancers inspect HTTP content (URL, headers, cookies) to make intelligent routing decisions — slower to process, but can route `/api/video` to the video cluster and `/api/auth` to the auth cluster.

**Common algorithms**: Round Robin (equal distribution), Least Connections (route to least busy server), Consistent Hashing (same key always routes to same server — essential for cache affinity).

→ Deep dive: [02-building-blocks/load-balancers.md](../02-building-blocks/load-balancers.md)

---

## API Gateway

An API Gateway is the single front door to your microservices. It centralizes cross-cutting concerns — authentication, rate limiting, SSL termination, routing, protocol translation (REST → gRPC), caching, and observability — so individual services don't each re-implement them. Clients talk to one endpoint; the gateway routes to the right backend.

**Technologies**: Kong, Amazon API Gateway, Nginx, Envoy, Zuul, Traefik.

→ Deep dive: [02-building-blocks/api-gateway.md](../02-building-blocks/api-gateway.md)

---

## DNS Deep Dive

**Question**: You have a service running at IP `93.184.216.34`. Your clients connect to it by hostname `api.example.com`. You update the IP to `93.184.216.35` during a migration. 12 hours later, some clients are still hitting the old IP. The old server is down. Those clients are seeing errors. Why, and what should you have done 48 hours ago to prevent this?

**Physical constraint**: There is no global registry that immediately notifies every client of a DNS change. DNS is a distributed caching system — each resolver (your ISP's nameserver, Google's 8.8.8.8, your laptop's stub resolver) caches DNS answers for the duration of the TTL set on the record. When you update a record, each cached copy must independently expire before the resolver re-queries the authoritative server. A resolver that cached your record 5 minutes ago with a 24-hour TTL will continue serving the old value for the next 23 hours and 55 minutes, regardless of what you've changed at the authoritative server.

**Minimal solution**: Lower your TTL to 60 seconds 48 hours before any planned migration. Wait for the old high-TTL records to expire everywhere (roughly 2× the previous TTL). Now perform the migration — the worst-case stale window is 60 seconds rather than 24 hours.

**Production generalization**: DNS TTL is not just a performance knob — it is a migration safety knob. Pre-lower TTLs before migrations. Run both old and new destinations simultaneously during the TTL expiry window. Use DNS for geographic routing (GeoDNS, latency-based routing in Route 53) but not for real-time failover — TTL propagation is too slow for sub-minute recovery. Use a load balancer's health checks for real-time failover within a region.

> **Analogy: A distributed phone book where every library keeps a local copy — but each copy expires on a different schedule.**  
> When you look up "google.com", your local library (resolver) checks its cached copy first. If it's expired, it asks the regional library, which asks the national archive, which finally asks the original publisher. Every library caches the answer for its own TTL period before it expires and must be refreshed. This is why DNS changes don't propagate instantly — old copies in libraries around the world take time to expire.

### How DNS Resolution Works (8 Steps)

When you type `api.example.com` in a browser:

1. **Browser cache**: Browser checks its own DNS cache (TTL-bound). Hit → done.
2. **OS cache**: Browser asks the OS resolver. OS checks its local cache (`/etc/hosts`, system cache). Hit → done.
3. **Recursive Resolver**: OS forwards the query to your ISP's (or configured) recursive resolver (e.g., `8.8.8.8`). The resolver is the workhorse — it does all the following lookups on your behalf.
4. **Root Name Server**: Resolver asks a Root Name Server (13 root server clusters globally): "Who handles `.com`?" Root responds with the address of the `.com` TLD server.
5. **TLD Name Server**: Resolver asks the `.com` TLD server: "Who handles `example.com`?" TLD responds with the address of Example's authoritative name server.
6. **Authoritative Name Server**: Resolver asks Example's authoritative DNS server: "What is the IP of `api.example.com`?" Authoritative server responds with the actual IP (e.g., `93.184.216.34`).
7. **Resolver caches and returns**: Resolver caches the answer for the TTL specified in the record, then returns it to your OS.
8. **OS returns to browser**: Browser caches the IP for the TTL period and opens a TCP connection to `93.184.216.34`.

### TTL and Caching

TTL (Time To Live) is set on each DNS record by the domain owner. A TTL of `300` means every resolver and client that looks up this record will cache it for 300 seconds (5 minutes) before querying again.

- **Low TTL (60–300s)**: Faster propagation of changes. More DNS query load.
- **High TTL (3600–86400s)**: Fewer queries, better performance. Slow propagation.
- **Rule of thumb**: Lower TTL 24–48 hours before a planned migration so caches drain quickly.

### Why DNS Propagation Takes Up to 48 Hours

There is no single "propagation" event. What actually happens is a **TTL expiry chain**:

1. You update your DNS record on your authoritative server (instant).
2. Every resolver worldwide that cached the old record keeps serving it until its cached TTL expires.
3. Some ISP resolvers ignore TTLs and override with their own (typically 24–48h) caching policy.
4. Each resolver independently queries on its own schedule.

"48-hour propagation" is the worst-case scenario: a resolver that just cached your old record with a 24-hour TTL will serve stale data for up to 24 more hours, and some ISPs add their own extra caching on top.

**Implication**: During a DNS-based migration, run both old and new destinations simultaneously during the TTL expiry window.

### DNS as a Load Balancing Mechanism

DNS can distribute traffic without dedicated hardware:

- **Round-Robin DNS**: Return multiple A records for the same hostname. Resolvers cycle through them. Simple, but no health checking — dead servers still get traffic.
- **Weighted DNS**: Return different IPs at different frequencies (e.g., 80% to us-east-1, 20% to us-west-2). Used for traffic shaping and gradual migrations.
- **GeoDNS**: Return different IPs based on the geographic location of the resolver. A user in Tokyo resolves `api.example.com` to the Tokyo region; a user in Frankfurt resolves it to the EU region. This is how global services achieve low-latency routing without client configuration.
- **Latency-Based Routing**: Route each client to the region with lowest measured latency (AWS Route 53 supports this).

**Limitation of DNS load balancing**: TTL means changes take time to propagate. DNS is not appropriate for real-time failover (use a load balancer or anycast routing for sub-second failover).

### DNS Failure Modes

**DNS is down → the internet appears broken.**  
Most applications resolve hostnames, not IPs. If your resolver is unreachable or your authoritative server is down, every service lookup fails — even if the actual servers behind those hostnames are healthy. Classic symptom: `curl https://api.example.com` fails with "could not resolve host", but `curl https://93.184.216.34` works. Always monitor DNS separately from your application health checks.

**DNS Cache Poisoning:**  
An attacker injects a forged DNS response into a resolver's cache. The resolver serves the malicious IP to all clients for the TTL duration. Clients connect to the attacker's server instead of the real one — credentials, tokens, and data are exposed. **DNSSEC** (DNS Security Extensions) cryptographically signs DNS records, allowing resolvers to verify authenticity and reject forged responses. Adoption is still incomplete (many authoritative zones are not signed), so DNSSEC alone is not sufficient — always use HTTPS with certificate validation as the final layer of trust.

**DNS amplification attack:**  
Attacker sends small DNS queries with a spoofed source IP (the victim's IP). DNS servers respond with large answers to the victim. Amplification factor of 50–70× is common. Mitigated by rate limiting on resolvers and BCP38 (network ingress filtering to drop spoofed-source packets).

### DNS Record Types Quick Reference

| Record | Purpose | Example |
|--------|---------|---------|
| A | IPv4 address for hostname | `api.example.com → 93.184.216.34` |
| AAAA | IPv6 address | `api.example.com → 2606:2800::1` |
| CNAME | Alias to another hostname | `www.example.com → example.com` |
| MX | Mail server for domain | `example.com → mail.example.com` |
| TXT | Arbitrary text (SPF, DKIM) | `"v=spf1 include:..."` |
| NS | Authoritative name servers | `example.com → ns1.example.com` |
| SOA | Zone authority metadata | Start of Authority record |

---

## Interview Questions Asked

### Conceptual
1. **"What happens when you type google.com in a browser?"** → DNS resolution (recursive resolver → root → TLD → authoritative) → TCP 3-way handshake → TLS handshake → HTTP GET → server processes → response rendered. Testing: breadth of networking knowledge and ability to name each layer correctly.
2. **"Explain TLS handshake steps"** → ClientHello (cipher suites) → ServerHello + certificate → client verifies cert → key exchange (ECDHE) → session keys derived → Finished messages. Testing: security fundamentals and whether you know where latency comes from (1–2 RTTs for TLS 1.2, 1 RTT for TLS 1.3).
3. **"What is HTTP/2 multiplexing and how does it help?"** → Multiple requests share one TCP connection via logical streams, eliminating per-request connection overhead. Reduces latency and avoids head-of-line blocking at the HTTP layer (but not TCP layer). Testing: do you know what problem it solves over HTTP/1.1.
4. **"What is head-of-line blocking and how does HTTP/2 solve it?"** → HTTP/1.1: a slow response blocks all subsequent responses on that connection. HTTP/2: independent streams on one TCP connection — a slow stream doesn't block others. Note: TCP itself still has HOL blocking (HTTP/3/QUIC fixes this). Testing: nuanced protocol knowledge.
5. **"What is gRPC and why use it over REST for internal services?"** → gRPC uses HTTP/2 + Protocol Buffers: binary serialization (smaller payloads), bidirectional streaming, strongly typed contracts via `.proto` files, auto-generated client/server stubs. REST is better for public APIs (human-readable, broad tooling). Testing: microservices protocol trade-off awareness.

### Comparison / Trade-off
1. **"TCP vs UDP — when to use each?"** → TCP: reliable, ordered, connection-oriented — for anything where data loss is unacceptable (HTTP, databases, file transfers). UDP: low-latency, no retry overhead — for video streaming, gaming, DNS queries, VoIP where a late packet is worse than a lost one.
2. **"When would you use WebSocket vs SSE vs long polling?"** → WebSocket: full-duplex, low-latency bidirectional (chat, live collaboration). SSE: server-to-client stream only, simpler, auto-reconnects (live dashboards, notifications). Long polling: fallback for restrictive firewalls/proxies. Testing: matching protocol to requirement.

### Scenario / Design
1. **"How does DNS load balancing work?"** → Authoritative server returns multiple A records; resolver picks one (usually round-robin). GeoDNS returns different IPs based on resolver location. Limitation: TTL delays failover; not suitable for real-time health-based routing — use a load balancer for that.
