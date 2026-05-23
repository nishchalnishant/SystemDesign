---
module: 05-hld-problems
topic: Hard
status: unread
tags: [05-hld-problems, system-design, hard]
---
# Design Chat System (WhatsApp/Telegram)

> **Difficulty**: Hard
> **Topics**: WebSockets, Long Polling, Real-time Delivery, Consistency, Offline Support
> **Time**: 60 minutes
> **Companies**: Meta (WhatsApp/Messenger), Telegram, Discord, Slack

---

## Problem Mindmap

```
Chat System (WhatsApp/Telegram)
├── Problem Constraints
│   ├── Scale → 2B users, 100B messages/day = 1.16M messages/sec, 200M concurrent connections = 4K gateway servers
│   ├── Latency target → message delivery < 100ms (online users); offline delivery within seconds of reconnect
│   └── Core hardness → routing 1.16M msgs/sec across 4K stateful gateways + message ordering + offline durability
├── Architecture Derivation
│   ├── Step 1 → HTTP polling → 2B users × 1 poll/sec = 2B req/sec pure overhead; 99% requests produce nothing
│   ├── Step 2 → WebSocket per user → server pushes; 1 server handles ~50K connections; need routing across servers
│   ├── Step 3 → Redis presence registry: user_id → gateway_id → route messages to correct server; offline → queue in Cassandra
│   └── Step 4 → Server-side sequence numbers per conversation → monotonic INCR; client re-orders on gap detection
├── Core Components
│   ├── WebSocket Gateway → stateful; 50K connections/server; heartbeat every 30s updates Redis TTL
│   ├── Redis presence → "presence:{user_id}" → {gateway_id, last_active}; TTL 30s; refreshed by heartbeat
│   ├── Message Service → stateless; Redis lookup → forward to gateway or queue in Cassandra if offline
│   ├── Cassandra → partition by chat_id, cluster by message_id TIMEUUID DESC; 100B msgs/day = 20TB/day; 7-day = 140TB
│   └── Snowflake message IDs → 41-bit timestamp + 10-bit machine + 12-bit sequence; sortable; monotonic per conversation
├── Data Model
│   ├── messages → Cassandra (chat_id BIGINT, message_id TIMEUUID, sender_id BIGINT, content TEXT, status TINYINT, PRIMARY KEY (chat_id, message_id DESC))
│   └── conversations → PostgreSQL (conversation_id, type ENUM[1:1, GROUP], participants[], created_at, last_message_id)
├── APIs
│   ├── WS /connect → upgrade HTTP; register presence in Redis; subscribe to message events
│   ├── WS send → {recipient_id, message_id, encrypted_payload, chat_id} → ACK {SENT}
│   └── GET /messages/{chat_id}?after={message_id} → [{message_id, sender_id, content, status}] paginated
├── Critical Trade-offs
│   ├── Fan-out on write vs read → fan-out on write for groups ≤ 256 members (WhatsApp limit); each member gets inbox write
│   ├── Cassandra vs PostgreSQL → Cassandra for messages; write-heavy time-series; no joins; partition by chat_id = fast inbox
│   └── E2EE → Signal Protocol Double Ratchet; server stores only encrypted blobs; keys on device; server blind to content
├── Failure Scenarios
│   ├── Gateway crash → heartbeat TTL expires; user goes offline in Redis; messages queue in Cassandra; client reconnects + fetches
│   ├── Redis split-brain → brief stale routing; delivery attempt fails; fallback to offline path (Cassandra + FCM push notification)
│   └── Message duplication on retry → TIMEUUID as idempotency key; Cassandra INSERT IF NOT EXISTS; client deduplicates by message_id
└── Interview Angles
    ├── Meta → "Design WhatsApp" → WebSocket gateways + Redis presence routing + Cassandra offline = complete answer
    ├── Telegram → "How do you handle 256-member group messages?" → fan-out on write to each member's Cassandra inbox via Kafka workers
    └── Follow-up → "Exactly-once delivery?" → at-least-once in practice; client-side Bloom filter of seen message_ids for dedup before render
```

---

## Problem Statement

Design a one-on-one and group chat application like WhatsApp.
- **One-on-one chat**: User A sends message to User B.
- **Group chat**: User A sends to Group (B, C, D).
- **Status**: Sent, Delivered, Read receipts.
- **Online/Offline Status**.

**Scale:**
- 2 Billion Users.
- 100 Billion messages/day.
- Low Latency (Real-time).

---

## Analogy

Two people shouting between buildings — works nearby but breaks at scale. A walkie-talkie solves it for 2. A dispatch center (WebSocket server with message routing) solves it for millions.

Think of your chat gateway as a dispatcher at a massive phone exchange. When User A sends a message, the dispatcher has to know exactly which switchboard (gateway machine) User B is connected to, route the call, and confirm delivery — all in under 100ms. The hard part: message ordering, offline delivery, and 2 billion simultaneous connections.

---

## What Breaks Without This System?

Without persistent WebSocket connections and a message routing layer, clients must poll for new messages over HTTP. At 2B users polling every second = 2B req/sec — no web infrastructure can sustain that. Even at 5-second intervals, that's 400M req/sec of pure overhead producing no messages for the vast majority of requests. Without durable offline storage, a message sent to a user who is offline simply disappears — recipients miss messages every time they lose connectivity, which is the core failure mode of SMS and makes the product unreliable as a communication tool.

---

## Derive the Architecture

**1 server, HTTP polling**: Clients poll `GET /messages?since=last_seen_id` every second. Server queries DB for new messages. Works for 100 users. Breaks when: 1M users × 1 req/sec = 1M req/sec on a server that handles ~10K HTTP req/sec — 99% of requests are wasted (no new messages). Fix: replace polling with a persistent connection that the server pushes to.

**Single server, WebSocket connections**: Each client holds a persistent WebSocket connection. Server pushes messages directly to the connected socket. Eliminates polling. A single Node.js server handles ~50K concurrent WebSocket connections. Breaks when: 200M concurrent users ÷ 50K/server = 4,000 gateway servers — to route A's message to B, the sender's server must know which of the 4,000 servers B is connected to. Fix: maintain a connection registry (user_id → server_id) in Redis, looked up on every message send.

**Connection registry in Redis**: On connect, write `user_id → gateway_server_id` to Redis (TTL = session lifetime). On message send: (1) look up B's server_id in Redis, (2) forward message to that server via internal channel, (3) that server pushes to B's WebSocket. Handles 200M concurrent connections across 4K gateways. Breaks when: User B is offline — the registry lookup fails, the message has nowhere to go and is dropped. Fix: if B is offline, persist the message to a durable message store keyed by B's user_id; deliver in order when B reconnects.

**Durable offline message store**: Messages written to Cassandra partitioned by (recipient_user_id, conversation_id). On reconnect, client fetches undelivered messages ordered by sequence number. Handles offline delivery with no message loss. Breaks when: User A sends "Hello" and "How are you?" in quick succession — the two messages may arrive at the server in different orders due to network jitter, and Cassandra inserts may be timestamped identically (clock skew). Fix: assign a monotonic sequence number per conversation on the server side (not client timestamp) before inserting.

**Server-side sequence numbers per conversation**: A sequence counter (Redis INCR or DB auto-increment) assigns monotonically increasing IDs per conversation. Clients display messages in sequence order, not arrival order. Out-of-order delivery is corrected at render time. Breaks when: a 1:1 message to a 256-member group requires delivering to all 256 members individually — at 100B messages/day with average group size 10, fan-out = 1T delivery operations/day. Fan-out for a 256-member group with all members online = 256 WebSocket pushes + 256 DB writes per message. Fix: async fan-out via a message queue — one write to the queue triggers worker pool to handle the 256 deliveries without blocking the sender.

---

## Why This Is Hard

1. **Connection scale**: A single server handles ~50K WebSocket connections. At 2B users with ~10% concurrency, you need tens of thousands of gateway machines — and you must know which user is on which machine in real time.
2. **Message ordering**: User A sends "Hello" then "How are you?" The network may deliver them out of order. Timestamps can't be trusted (clock skew). You need sequence numbers per conversation.
3. **Offline delivery**: User B is on a plane. Messages must queue durably and flush in order when B reconnects — possibly hours later on a different device.
4. **Group fan-out**: A single message to a 256-member group requires 255 individual deliveries, each with independent delivery acknowledgment. At scale, this creates a write amplification problem.
5. **Read receipts at scale**: "Read by all" in a 256-person group means tracking 256 individual ACKs per message. Storing and querying this efficiently is an OLAP-level problem masquerading as a chat feature.

---

## Requirements

### Functional
1. 1:1 Chat & Group Chat (Max 256 members).
2. Message Acknowledgment (Sent, Delivered, Read).
3. Last Seen / Online Status.
4. Media Support (Images/Video) - (Design separate Asset Service).
5. **Persistent History**: Multi-device login support.

### Non-Functional
- **Low Latency**: < 100ms delivery.
- **Consistency**: Order of messages must be preserved (e.g., "Hello" before "How are you?").
- **Availability**: High.
- **Security**: End-to-End Encryption (E2EE) (Optional advanced topic).

---

## Connection Management (The "Real-time" Magic)

### Protocols
1. **HTTP (REST)**: Good for Login, Profile Update, History Fetch. **Bad for receiving messages** (High latency/overhead).
2. **Long Polling**: Client holds connection open. Server responds when data arrives. Better but heavy on server resources.
3. **WebSockets (Selected)**: Bi-directional persistent connection. Server pushes messages instantly. Ideal for Chat.

### Dealing with 2 Billion Connections
- A single server can manage ~50K concurrent WebSockets (Network IO bound).
- Need **Connection Gateway** layer (Horizontally Scaled).
- **Stateful**: Gateway knows "User A is connected to Box 42".
- Uses **Redis / Zookeeper** to map `UserID -> GatewayMachineID`.

---

## Architecture

```
       User A                   User B
         │                        ▲
         ▼                        │
   ┌─────────────┐          ┌─────────────┐
   │ Chat        │◀────────▶│ Chat        │
   │ Gateway 1   │          │ Gateway 2   │ (Maintains WebSockets)
   └─────┬───────┘          └──────┬──────┘
         │                         ▲
         ▼                         │
   ┌─────────────┐          ┌─────────────┐
   │ Message     │─────────▶│ Message     │
   │ Service     │          │ Service     │ (Stateless)
   └─────┬───────┘          └─────────────┘
         │
         ├───▶ Kafka (Topic: "chat-messages")
         │
         ▼
   ┌─────────────┐
   │ Cassandra   │ (Chat History / Inbox)
   │ / HBase     │
   └─────────────┘

   ┌─────────────┐
   │ Redis       │ (UserID → GatewayMachineID mapping)
   │             │ (LastActive, Online Status)
   └─────────────┘
```

---

## Message Flow (1:1 Chat)

1. **User A** sends message via WebSocket to **Gateway 1**.
2. **Message Service** persists message to **Cassandra** (Status: `SENT`).
3. **Message Service** queries Redis: "Where is User B connected?"
4. If **User B** Online (Gateway 2):
   - Forward message to **Gateway 2**.
   - Gateway 2 pushes to **User B** via WebSocket.
   - User B sends ACK (`DELIVERED`) → Gateway 2 → A.
5. If **User B** Offline:
   - Push Notification Service (FCM/APNS) triggers "You have a new message".
   - Message sits in DB. When B connects, fetch unread messages.

---

## Data Schema (Cassandra/NoSQL)

**Why NoSQL?**
- Write-heavy (100B/day).
- Horizontal scaling easier.
- Consistency: `local_quorum` sufficient.

### Message Table
```sql
-- Partition Key: chat_id (keeps chat messages together on the same node)
-- Clustering Key: message_id (time-based sort order like Snowflake / KSUID)
CREATE TABLE messages (
    chat_id      BIGINT,
    message_id   TIMEUUID,
    sender_id    BIGINT,
    content      TEXT,
    media_url    TEXT,
    status       TINYINT, -- 1: Sent, 2: Delivered, 3: Read
    PRIMARY KEY (chat_id, message_id DESC)
);
```

### User Presence Table (Redis, not Cassandra)
```
Key:   "presence:{user_id}"
Value: { gateway_id: "gw-42", last_active: 1700000000, status: "online" }
TTL:   30 seconds (refreshed by heartbeat)
```

---

## Group Chat Complexity

**Scenario**: Group of 200 people.
- User A sends message.
- Server needs to deliver to 199 users.

**Approach**:
1. **Message Service** fetches Group Members from SQL DB.
2. **Fan-out**:
   - For small groups (<100): Loop and push to each member's Gateway.
   - For large groups/channels (>5K): Write to a Message Queue (Kafka), workers process fan-out in batches.
3. **Optimization**: Don't confirm "Delivered" until X% receive, or just show "Sent". "Read by All" is expensive O(N).

**Fan-out Write vs Fan-out Read trade-off:**
- **Fan-out on Write** (WhatsApp): Write message to each recipient's inbox at send time. Fast reads, expensive writes.
- **Fan-out on Read** (Twitter): Single storage; each reader fetches at query time. Cheap writes, expensive reads.
- For chat (max 256 members): Fan-out on Write is acceptable.

---

## Sequencing & Consistency

**Problem**: User A sends `Msg1` then `Msg2`. B receives `Msg2` then `Msg1`.

**Solution**:
- **Sequence Numbers**: Assign incremental ID (Sequence ID) per Chat ID.
- **Client-Side Re-ordering**:
  - Client receives `Seq 5`. Last seen `Seq 3`.
  - Client knows `Seq 4` is missing.
  - Buffer `Seq 5`, request `Seq 4`, then display in order.
- **Timestamps**: Unreliable due to clock skew. Use **Snowflake IDs** or **Logical Clocks**.

**Snowflake ID structure:**
```
64-bit ID:
├─ 41 bits: Timestamp (milliseconds since epoch) → sortable
├─ 10 bits: Machine ID
└─ 12 bits: Sequence number (per machine per ms)
```

---

## Failure Scenarios

### Gateway Crash
```
User B is connected to Gateway 7.
Gateway 7 crashes.

Effect:
- B's WebSocket connection drops.
- Redis still shows B → Gateway 7 (stale).
- Messages sent to Gateway 7 fail.

Mitigation:
- Gateway sends heartbeat to Redis (TTL 10s).
- On TTL expiry: B appears offline; messages queue in DB.
- B reconnects to a new gateway; fetches missed messages on reconnect.
```

### Kafka Consumer Lag
```
Group chat fan-out falls behind.
200K messages/sec but fan-out workers processing 150K/sec.

Mitigation:
- Add consumer instances (Kafka partitions allow parallel consumption).
- Circuit breaker: If lag > 1M messages, alert + auto-scale.
```

### Message Duplication (Network Retry)
```
Gateway 1 forwards message to Gateway 2.
Gateway 2 processes but response is lost.
Gateway 1 retries.
Gateway 2 receives duplicate.

Mitigation:
- Message deduplication via idempotent message_id (TIMEUUID).
- Gateway 2: INSERT IF NOT EXISTS.
```

### Split Brain (Redis Failover)
```
Redis primary dies; replica promoted.
Brief window where UserID → GatewayID mapping is stale.

Mitigation:
- Accept a brief inconsistency: message delivery attempt fails, fallback
  to "offline" path (queue in DB + push notification).
- Messages will sync on next heartbeat.
```

---

## Capacity Estimates

```
Messages:
100B messages/day ÷ 86,400s = 1.16M messages/sec

Storage (Cassandra):
Average message: 200 bytes
100B × 200B = 20 TB/day
7-day retention: 140 TB

WebSocket connections:
2B users × 10% concurrent = 200M connections
200M ÷ 50K per server = 4,000 gateway servers

Redis (presence):
200M active users × 100 bytes = 20 GB (fits in memory)
```

---

## Interview Talking Points

**Q: "How to handle 'Last Seen'?"**
- A: "Heartbeat mechanism. Client pings server every 30s. Store in Redis `User:LastActive`. If >1 min, show 'Last seen at X'. Don't write to hard DB every ping — only on session close."

**Q: "Multi-device Syncing?"**
- A: "Treat each device as a separate 'User' entity in routing: `UserA_Mobile`, `UserA_Desktop`. Fan-out message to ALL active sessions of User A. Maintain a per-device offset (like Kafka consumer offset) so each device knows what it has and hasn't received."

**Q: "End-to-End Encryption?"**
- A: "Signal Protocol. Keys generated on client. Server only stores encrypted blob. Server cannot read messages. Only the device with the Private Key can decrypt. The tricky part: key exchange when a user adds a new device."

**Q: "How do you handle 100B messages/day without Cassandra melting?"**
- A: "Cassandra is optimized for write-heavy time-series workloads. Partition by chat_id keeps all messages for a conversation on the same node. Clustering by TIMEUUID gives free time-ordered retrieval. No joins, no transactions required."

**Q: "What's the hardest part you haven't solved above?"**
- A: "Exactly-once delivery in the presence of client restarts. We do at-least-once (message duplication possible). Fixing this requires the client to participate in deduplication — store seen message_ids in a local bloom filter and discard duplicates before rendering."
