# Design Chat System (WhatsApp/Telegram)

> **Difficulty**: Hard
> **Topics**: WebSockets, Long Polling, Real-time Delivery, Consistency, Offline Support
> **Time**: 60 minutes
> **Companies**: Meta (WhatsApp/Messenger), Telegram, Discord, Slack

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
