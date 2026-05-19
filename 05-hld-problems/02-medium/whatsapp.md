# Design WhatsApp

> **Difficulty**: Medium
> **Topics**: WebSockets, Message Queuing, End-to-End Encryption, Cassandra
> **Time**: 60 minutes
> **Companies**: Meta, Telegram, Signal, Slack

---

## What Breaks Without This System?

Before WhatsApp, international messaging cost $0.10–$0.25 per SMS. A family in Brazil messaging relatives in Portugal paid per message, switched off notifications to avoid charges, and missed messages entirely when roaming. Communication was economically gatekept.

The technical failure without a designed messaging system:

- **Polling approach**: client polls the server every 5 seconds — "any new messages?" 2B users × 12 polls/minute = 24B requests/minute = 400M requests/second to origin servers. Infeasible. Server infrastructure must support every user simultaneously even when nothing is happening.
- **HTTP long-polling**: server holds connection open until a message arrives. Better than polling, but HTTP is stateless — no clean way to hold millions of connections open, and mobile networks tear down connections on sleep/background.
- **No offline delivery**: if the recipient's device is offline when a message is sent, where does the message wait? Without a store-and-forward layer, the message is lost. Without retry + delivery receipt, the sender has no idea whether the message was received.
- **No E2E encryption by default**: without it, the server operator can read all messages. Privacy is impossible to retrofit — it must be in the design from the start.

---

## Derive the Architecture

**Step 1 — The connection problem: HTTP vs WebSocket**
HTTP is request-response: client initiates, server responds, connection closes. To push a message from server to client, you'd need the client to poll. At 2B users polling every 5 seconds = 400M requests/sec. WebSocket: client initiates a TCP connection upgrade, then the connection stays open. Server can push at any time. Cost: one persistent TCP connection per active user. At 2B users, you can't hold all connections on one server — you need a fleet of connection servers (chat servers).

**Step 2 — The routing problem: how does a message get to the right connection?**
User A (connected to Chat Server 7) sends a message to User B (connected to Chat Server 23). Chat Server 7 doesn't know where User B is connected. Solution: a routing layer that maps `user_id → chat_server_id`. When User B connects, it registers with a coordination service (ZooKeeper or a Redis hash). When Chat Server 7 needs to deliver to User B, it looks up the routing table, finds Chat Server 23, and forwards the message via internal gRPC. Chat Server 23 pushes over User B's WebSocket.

**Step 3 — The offline delivery problem: store and forward**
User B's phone is off. Chat Server 23 has no active WebSocket for User B. The message must be stored until User B comes online. Storage: Cassandra is purpose-built for this — write-heavy, append-only, time-ordered, horizontally scalable. Schema: `(conversation_id, message_timestamp, message_id) → message_payload`. When User B reconnects, the chat server fetches undelivered messages from Cassandra and pushes them in order.

**Step 4 — Delivery receipts: the three-tick model (sent / delivered / read)**
- Sent (one gray tick): message written to Cassandra server-side.
- Delivered (two gray ticks): message pushed to recipient's device and acknowledged.
- Read (two blue ticks): recipient opened the conversation.

Each state transition sends an ACK event back to the sender's chat server, which updates the message state and pushes the tick update to the sender's device.

**Step 5 — End-to-end encryption**
Each client generates a key pair on install (Signal Protocol: Curve25519, AES-256, HMAC-SHA256). Public keys are registered with WhatsApp's key server. When A sends to B, A fetches B's public key, derives a shared session key, encrypts the message locally, sends the ciphertext. The server stores and routes opaque ciphertext — it cannot decrypt. The server only knows: sender, recipient, timestamp, and size.

**Step 6 — Group messages**
A group with 500 members: one message must be delivered to 500 devices. Two approaches:
- Fan-out on write (server side): server delivers the message to all 500 members individually. 500 WebSocket pushes per message. Controlled fan-out, but 500x amplification.
- Sender-key (WhatsApp's actual approach for groups): each member of the group holds a shared "sender key." A sends one encrypted message; each recipient decrypts using the sender key. One server write, client-side fan-out. Efficient for large groups.

**Step 7 — Media messages**
Audio/video/photo cannot go through the WebSocket stream (binary size, no retry semantics). Client uploads media to WhatsApp's CDN (blob storage), gets back a URL + encryption key. Sends a message containing the URL + encryption key (encrypted with Signal Protocol). Recipient downloads media from CDN directly; decrypts locally. Server never sees the media content.

---

## Real-Life Analogy

Two people having a private conversation in a phone booth. End-to-end encrypted means the phone company can hear nothing — only the two people in the booths can understand each other, even if someone taps the line. The phone company just routes the call.

The challenge: **what happens when one person's phone booth is temporarily unavailable?** The message must queue somewhere. But because it's end-to-end encrypted, the server can't read it — it's just an opaque blob that needs to be held until delivery.

Group chats are conference calls: one person speaks, and the server needs to route that audio to every participant. With 256 members, that's 256 delivery paths, each with its own offline-queuing problem.

Scale this to 2 billion users, 694,000 messages/second, and you have WhatsApp.

---

## Why This Is Hard

1. **Persistent connection management**: Unlike HTTP (request-response), messaging requires the server to push messages to clients proactively. At 500M DAU, that's 500M open WebSocket connections — each holding state, consuming memory, requiring heartbeats. You need 10,000 connection servers just for this.
2. **Message ordering in group chats**: If 3 people send messages simultaneously in a group, which order is canonical? Without a central sequencer, members may see different orderings. With a central sequencer, it's a bottleneck.
3. **Offline delivery reliability**: If a user is offline, messages must be queued and delivered when they reconnect, potentially hours or days later. The queue must survive server crashes (durability), but also can't grow unbounded.
4. **End-to-end encryption at scale**: The server stores ciphertext it can't read. This makes server-side features like search, spam detection, and content moderation very difficult — you're deliberately blind to the content you're routing.
5. **Delivery receipts and consistency**: The "double checkmark" (delivered) and "blue checkmark" (read) status must be propagated back to the sender in real-time. This doubles the message traffic for every message sent.
6. **Media at 92 GB/sec ingress**: Media handling must be separate from the message pipeline. Uploading a photo before sending it (vs. attaching and sending simultaneously) is a deliberate design choice to separate the pipelines.

---

## Requirements

### Functional Requirements
1. **One-on-one messaging** (text, images, videos, documents)
2. **Group chats** (up to 256 members)
3. **Message delivery status** (sent ✓, delivered ✓✓, read ✓✓ blue)
4. **End-to-end encryption** (E2EE)
5. **Voice and video calls** (peer-to-peer)
6. **Last seen** and **online status**
7. **Message persistence** (offline delivery)
8. **Push notifications** for offline users

### Non-Functional Requirements
1. **Low latency**: < 100ms message delivery (online-to-online)
2. **High availability**: 99.99% uptime
3. **Scalability**: 2 billion users, 100 billion messages/day
4. **Security**: E2EE — server cannot read message content
5. **Reliability**: Messages must not be lost; exactly-once delivery

---

## Capacity Estimation

### Traffic Estimates
- **Daily Active Users (DAU)**: 500 million
- **Messages per user/day**: 40
- **Total messages/day**: 20 billion
- **Peak QPS**: 20B / 86400 × 3 = **694,000 messages/sec**
- **Media messages**: 20% of total = 4 billion/day

### Storage Estimates
- **Text message**: 100 bytes (encrypted)
- **Media average**: 2 MB (photos avg, with 10MB videos)
- **Daily storage**:
  - Text: 16B × 100 bytes = 1.6 TB
  - Media: 4B × 2 MB (avg) = 8 PB
  - **Total**: ~8 PB/day
- **With 90-day retention**: 8 PB × 90 = **720 PB**

### Infrastructure Requirements
- **WebSocket connections**: 500M concurrent (peak)
- **Servers per connection limit** (50K per server): 10,000 WebSocket gateway servers
- **Kafka throughput**: 694K messages/sec across message queues
- **Cassandra nodes**: ~200 nodes for 720 PB with 3× replication

---

## High-Level Architecture

```
┌──────────┐   ┌──────────┐   ┌──────────────┐
│ Mobile 1 │   │ Mobile 2 │   │ WhatsApp Web │
└─────┬────┘   └────┬─────┘   └──────┬───────┘
      │              │                │
      └──────────────┼────────────────┘
                     │ WSS (WebSocket Secure)
                     ▼
         ┌────────────────────┐
         │  Load Balancer     │ (Layer 4, sticky by userId)
         └──────┬─────────────┘
                │
     ┌──────────┼───────────────┐
     ▼          ▼               ▼
┌────────┐  ┌────────┐    ┌─────────────┐
│ WS GW1 │  │ WS GW2 │    │ WS GW N     │  (10,000 gateways)
└────┬───┘  └────┬───┘    └──────┬──────┘
     │           │               │
     └───────────┼───────────────┘
                 │
     ┌───────────┼──────────────────┐
     ▼           ▼                  ▼
Message      Presence           Group
Service      Service            Service
     │                              │
     ▼                              ▼
  Kafka                          Kafka
(messages)                   (group-fanout)
     │                              │
     ▼                              ▼
Cassandra                     Delivery Workers
(message store)

Redis: Session mapping (userId → gatewayId), online status, undelivered message queue

Push: FCM (Android) / APNs (iOS) for offline users
```

---

## Core Components

### 1. WebSocket Connection Management

**Why WebSocket over HTTP long-polling or SSE?**
- WebSocket: Bi-directional, persistent, low overhead. Ideal for chat — users both send and receive continuously.
- SSE: Uni-directional (server → client only). Good for feeds/notifications, wrong for chat.
- Long-polling: Client opens HTTP request, server holds it until data available. High per-message overhead (HTTP headers ~500 bytes vs. WebSocket frame ~2 bytes). Fallback only.

```
Connection setup:
1. Client → Load Balancer (sticky session by userId)
2. Load Balancer → Assign to WS Gateway (consistent hashing by userId)
3. WS Gateway: Validate auth token
4. Session Manager: SET user:{userId}:gateway → {gatewayId} in Redis (TTL: 5 min)
5. Client ↔ Gateway: PING/PONG heartbeat every 30s (refreshes TTL)
6. On disconnect: DEL user:{userId}:gateway, update last_seen timestamp

Why sticky sessions?
  User reconnects to same gateway → avoids re-establishing session from scratch
  Reduces session renegotiation overhead by ~10×
```

### 2. Message Delivery Flow

```
Sender (Alice, on WS Gateway 1) → sends message to Bob

1. Alice → WS GW1: {to: "bob", content: <encrypted_blob>, messageId: "uuid"}
2. WS GW1 → Message Service:
   a. Generate server-side messageId (for idempotency)
   b. Store in Cassandra: message persisted
   c. Publish to Kafka: topic "messages"
   d. ACK to Alice: "sent ✓" (Alice's message is safely persisted)

3. Kafka → Delivery Worker:
   a. Look up Bob's gateway: GET user:bob:gateway → "WS GW2"
   b. If Bob online (gateway entry exists):
      → Push to WS GW2 → Deliver to Bob
      → Bob's app ACKs: "delivered ✓✓"
      → Delivery Worker updates Cassandra status
      → Sends delivery receipt back to Alice: "✓✓"
   c. If Bob offline (no gateway entry):
      → Queue message: LPUSH inbox:bob <message_json>
      → Send push notification via FCM/APNs
      → Bob opens app → WebSocket connects → drains inbox queue → delivers messages

4. Bob reads chat:
   → Read receipt sent to Delivery Worker
   → Alice receives "✓✓ blue" (read)
```

**Message Status States:**
1. **Sent ✓** (single checkmark): Server has persisted the message in Cassandra
2. **Delivered ✓✓** (two checkmarks): Delivered to recipient's device
3. **Read ✓✓ blue** (two blue checkmarks): Recipient opened the chat

### 3. Group Messaging

Group chats (up to 256 members) require fanout to all members:

```
Alice sends to group "Family" (256 members):

1. Alice → Message Service: {to: "group:family", content: <blob>}
2. Message Service → Kafka topic "group-messages"
3. Delivery Workers (parallel):
   a. Fetch group member list from PostgreSQL (256 members)
   b. For each online member: push directly via their gateway
   c. For each offline member: queue in Redis inbox + push notification
4. Message stored once in Cassandra (NOT once per member — storage efficiency)
5. Each member's inbox references the single message by message_id

Storage model:
  Single copy: Store message once, reference from each member's read-cursor
  Trade-off: Storage efficient but requires join on read
  Alternative: Copy per member (faster reads, 256× more storage)
```

**Group Schema (PostgreSQL):**
```sql
CREATE TABLE groups (
    group_id UUID PRIMARY KEY,
    name VARCHAR(100),
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE group_members (
    group_id UUID REFERENCES groups(group_id),
    user_id BIGINT,
    role ENUM('admin', 'member'),
    joined_at TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY (group_id, user_id)
);
```

### 4. End-to-End Encryption (Signal Protocol)

WhatsApp uses the Signal Protocol (Double Ratchet Algorithm):

```
Key Exchange (one-time setup):
1. Alice registers: uploads identity key + signed prekey + one-time prekeys to server
2. When Alice wants to message Bob:
   a. Alice → Server: "Give me Bob's key bundle"
   b. Server → Alice: Bob's identity key + signed prekey + one-time prekey
   c. Alice performs Diffie-Hellman key exchange locally → derives shared session key
   d. Alice encrypts message with session key
   e. Alice → Server: encrypted blob (server cannot decrypt)
   f. Server → Bob: encrypted blob (Bob decrypts locally)

Double Ratchet guarantees:
  Forward secrecy: Compromising today's key doesn't expose past messages
  Break-in recovery: After compromise, new keys generated per message
```

**Server's role:**
- Stores encrypted blobs only — zero knowledge of content
- Stores public keys for key exchange
- Routes messages between users
- Holds encrypted media

**Key Storage Schema:**
```sql
CREATE TABLE user_keys (
    user_id BIGINT PRIMARY KEY,
    identity_key BYTEA,          -- Long-term identity key (public)
    signed_prekey BYTEA,          -- Rotated monthly
    one_time_prekeys BYTEA[]      -- Consumed once per conversation start
);
```

### 5. Media Handling

Media is decoupled from message delivery — this is deliberate:

```
Media Upload (before sending):
1. Sender → Media API: Upload encrypted photo
2. Media API → S3: Store encrypted blob
3. S3 → Media API: S3 URL
4. Media API → Sender: mediaId

Message Send (after upload):
1. Sender → Message Service: {text: "pic", mediaId: "media_xyz", mediaKey: <AES key>}
   (AES key is included in the E2EE message — server never sees it)
2. Normal message delivery pipeline

Media Download (by recipient):
1. Recipient → CDN: GET /media/media_xyz
2. CDN → S3: fetch encrypted blob
3. Client: decrypt locally using mediaKey from message
```

**Why separate upload?**
- Upload can be done in the background before the user hits "send" (progressive loading)
- If upload fails, message is not sent — no orphaned media
- Media can be retried independently without resending the message
- Large media files don't block the low-latency message delivery pipeline

**Media encryption:**
- Client generates random AES-256 key per media file
- Encrypts media client-side before upload
- Shares AES key within the E2EE message
- Server stores only ciphertext — cannot access media content

### 6. Voice/Video Calls (WebRTC)

```
Signaling (via HTTPS — WhatsApp server):
1. Alice → Signal Server: "I want to call Bob"
2. Signal Server → Bob: "Incoming call from Alice"
3. Bob accepts → both exchange SDP (Session Description Protocol)
4. ICE candidate exchange via server

Media (direct peer-to-peer WebRTC):
  Alice ←──────────────────────────── Bob
         RTP/UDP (encrypted, direct)

If NAT/firewall blocks P2P (10-20% of calls):
  Alice → TURN Server → Bob  (relay fallback)
  TURN server routes encrypted media — cannot decrypt (E2EE)
```

STUN: Helps clients discover their public IP/port (NAT traversal)
TURN: Relay server when P2P fails (more expensive — WhatsApp minimizes TURN usage)

---

## Database Design

### Message Storage (Cassandra)

```sql
CREATE TABLE messages (
    user_id BIGINT,               -- Partition key (user's inbox)
    conversation_id UUID,          -- Clustering key
    message_id TIMEUUID,           -- Clustering key (time-sortable UUID)
    sender_id BIGINT,
    content BLOB,                  -- Encrypted ciphertext
    media_id UUID,
    timestamp TIMESTAMP,
    status ENUM('sent', 'delivered', 'read'),
    PRIMARY KEY ((user_id, conversation_id), message_id)
) WITH CLUSTERING ORDER BY (message_id DESC);
```

**Why Cassandra?**
- **Write-heavy**: 694K messages/sec — Cassandra's append-only LSM-tree is optimized for this
- **Time-series**: message_id (TIMEUUID) provides natural time ordering within a conversation
- **Scalability**: Linear horizontal scaling — add nodes, capacity grows proportionally
- **No joins needed**: Each message is fully denormalized — no foreign key lookups

**Query patterns:**
```sql
-- Get latest messages for a conversation (paginated)
SELECT * FROM messages
WHERE user_id = 123 AND conversation_id = 'abc'
LIMIT 50;

-- Pagination (cursor-based)
SELECT * FROM messages
WHERE user_id = 123 AND conversation_id = 'abc'
AND message_id < 'last_message_id'
LIMIT 50;
```

### Undelivered Messages (Redis)

```java
// Queue messages for offline user
redis.lpush("inbox:" + userId, messageJson);

// On user reconnect:
List<String> messages = redis.lrange("inbox:" + userId, 0, -1);
for (String msg : messages) {
    deliver(msg);
}
redis.delete("inbox:" + userId);
```

**Why Redis for offline queue (not Cassandra)?**
- Messages are transient — needed only until delivery
- Redis gives O(1) push/pop and automatic expiry
- Cassandra is for permanent message history; Redis is for the delivery pipeline
- Size limit: cap inbox at 10,000 messages to prevent unbounded growth for long-offline users

---

## Scalability Strategies

### 1. Sharding Strategy

```
Shard by user_id:
  Shard 0: user_id % 16 = 0
  Shard 1: user_id % 16 = 1
  ...

Benefit: All of a user's conversations are co-located on one shard
         → Single-shard reads for conversation history

Challenge: Conversations between users on different shards
Solution: Store each message in BOTH participants' shards
          (write amplification: 2× storage, but read locality is maintained)
```

### 2. WebSocket Connection Scaling

```
Challenge: 500M concurrent connections at peak

Scaling math:
  50,000 connections per gateway server
  500M / 50,000 = 10,000 gateway servers needed

  Each server: 32 GB RAM for connections + buffers
  10,000 × 32 GB = 320 TB RAM (expensive but feasible for WhatsApp's revenue)

Auto-scaling:
  Monitor connection count per server
  Scale out when > 80% capacity (40,000 connections)
  Load balancer uses consistent hashing to minimize session disruption on scale events
```

### 3. Message Ordering in Group Chats

**The problem:** If Alice, Bob, and Carol all send messages simultaneously in a group, which order is canonical?

**Option 1: Lamport timestamps**
- Each message carries a logical clock value
- Simple, but can't totally order concurrent messages from different clients

**Option 2: Server-assigned sequence numbers**
- Group message service assigns a monotonic sequence per conversation
- Total ordering guaranteed
- Bottleneck: single sequencer per conversation

**Option 3: CRDTs (Conflict-Free Replicated Data Types)**
- Messages are modeled as a set with causal dependencies
- Concurrent messages are all valid; clients display in a deterministic order (e.g., alphabetical by sender as tiebreaker)
- WhatsApp's actual approach for group message ordering

---

## Failure Scenarios

### WebSocket Gateway Crash

```
Problem: Gateway crashes with 50K active connections

Recovery:
1. Clients detect disconnect (heartbeat timeout: 30 seconds)
2. Clients reconnect → Load Balancer assigns new gateway
3. New gateway fetches session state from Redis (user:userId:gateway entry recreated)
4. Any messages sent to user during reconnect window:
   → Kafka retains them (7-day retention)
   → Delivery workers retry delivery to new gateway
   → Redis inbox queues messages for offline delivery

RTO: < 30 seconds (heartbeat timeout + reconnect)
Messages delivered: Eventually (no loss, possible brief delay)
```

### Message Delivery Retry

```java
int maxRetries = 3;
for (int attempt = 0; attempt < maxRetries; attempt++) {
    try {
        if (deliverMessage(msg)) {
            markDelivered(msg.getId());
            return;
        }
    } catch (Exception e) {
        // Exponential backoff: 1s, 2s, 4s
        Thread.sleep((long) Math.pow(2, attempt) * 1000);
    }
}

// After all retries: send push notification
fcm.send(userId, msg);
// Message remains in Cassandra — user fetches history on next open
```

### Cassandra Node Failure

- Replication factor = 3 across multiple availability zones
- Quorum reads/writes: R=2, W=2 — survives one node failure transparently
- Hinted handoff: Other nodes absorb writes for failed node
- Anti-entropy repair: Merkle tree comparison restores consistency after recovery

---

## Trade-offs

| Aspect | Choice | Trade-off |
|--------|--------|-----------|
| **Connection protocol** | WebSocket | Persistent connection overhead vs. real-time push capability |
| **E2EE** | Signal Protocol (Double Ratchet) | Privacy + forward secrecy vs. server-side features disabled (search, moderation) |
| **Message storage** | Cassandra | Write throughput vs. complex multi-key queries |
| **Group fanout** | Async (Kafka) | Eventual delivery vs. instant fan-out (simpler but bottleneck at scale) |
| **Media handling** | Separate upload pipeline | Reliability (independent retry) vs. single atomic upload+send |
| **Offline queue** | Redis inbox | Fast and transient vs. no persistence across Redis restarts (use AOF) |

---

## Advanced Features

### Typing Indicators

```java
// Ephemeral — not stored in Cassandra
redis.setex("typing:" + conversationId + ":" + userId, 5, "true");

// Broadcast to conversation participants via WebSocket
websocketGateway.broadcast(conversationId, {
    "type": "typing",
    "userId": userId,
    "expiresAt": System.currentTimeMillis() + 5000
});
```

TTL: 5 seconds. No persistence needed — display typing indicator for 5s, then clear.

### Message Reactions

```sql
CREATE TABLE reactions (
    message_id TIMEUUID,
    user_id BIGINT,
    reaction VARCHAR(10),   -- "❤️", "👍", "😂", "😮", "😢", "🙌"
    PRIMARY KEY (message_id, user_id)   -- One reaction per user per message
);
```

### Status Updates (Stories)

- 24-hour TTL using S3 lifecycle policies (same approach as Instagram)
- Active status list in Redis sorted set (score = expiry timestamp)
- View count tracking in Redis, periodic flush to Cassandra

---

## Monitoring & Reliability

### Key Metrics
- **Message delivery latency**: p50 < 50ms, p99 < 200ms, p999 < 2s
- **Delivery success rate**: > 99.99%
- **WebSocket connection count**: Per gateway server (alert at > 45K)
- **Push notification delivery rate**: > 95%
- **Kafka consumer lag**: Alert if > 60 seconds behind

### Disaster Recovery
- **Multi-region Cassandra**: 3 replicas across geographic regions
- **WAL (Write-Ahead Log)**: Cassandra commitlog for crash recovery
- **Daily S3 snapshots**: Cassandra nodetool snapshot → S3
- **RTO**: < 5 minutes (Cassandra regional failover)
- **RPO**: < 1 minute (Cassandra commitlog flush interval)

---

## Interview Discussion Points

**Q: How to ensure message ordering in group chats?**
- Lamport timestamps for casual ordering (simple, not total)
- Server-assigned sequence number per conversation (total order, sequencer bottleneck)
- CRDTs for conflict-free ordering across concurrent messages (most scalable)
- WhatsApp trade-off: slight reordering visible to different group members is acceptable

**Q: Handling message floods (spam)?**
- Rate limiting: Max 100 msg/min per user (Redis sliding window counter)
- Bloom filter: Detect duplicate message hash (copy-paste spam detection)
- ML model for spam pattern detection (applied to message metadata, not content — E2EE prevents content inspection)

**Q: How does E2EE affect server-side features?**
- Search: Client-side search only (search within downloaded messages)
- Content moderation: Only detectable by user reporting — server sees ciphertext
- Backup: End-to-end encrypted backups to iCloud/Google Drive (key stored on device)
- This is a deliberate privacy-security trade-off that WhatsApp explicitly chose

**Q: What's the hardest problem in WhatsApp at scale?**
- Connection management: 10,000 gateway servers maintaining 500M persistent connections
- Each connection requires heartbeat, session state, reconnect handling
- This is more of an infrastructure/operations challenge than an algorithmic one

---

## Interview Questions Asked

### Meta
1. **"Design WhatsApp messaging for 2 billion users."** → Tests WebSocket management and offline delivery; key answer: persistent WebSocket per device to a gateway server, messages stored in Cassandra with delivery status, push notification via FCM/APNs when socket is closed.

### Google
1. **"How would you design real-time messaging at scale?"** → Tests connection architecture; key answer: stateful gateway servers with consistent hashing to route user connections, message fan-out via internal message bus, Redis for presence/session mapping.

### Common Follow-ups
1. **"How does end-to-end encryption key exchange work?"** → Tests E2EE fundamentals; Signal Protocol — each device publishes a set of one-time pre-keys to the server; sender fetches recipient's public key bundle and derives a shared secret locally; server never sees plaintext.
2. **"How do you guarantee message ordering?"** → Tests distributed ordering; server assigns a monotonically increasing sequence number per conversation; client buffers and reorders by sequence before display; gaps trigger a re-fetch.
3. **"How do read receipts (double tick) work?"** → Tests delivery state machine; single tick = server received; double tick = recipient device ACKed delivery via WebSocket; blue tick = recipient opened the conversation; each state written to message status table in Cassandra.
4. **"How does group message fan-out work?"** → Tests write amplification; server creates one copy per group member in their message inbox (server-side fan-out); for large groups (> 256), use a shared group mailbox that all members poll — avoids O(N) writes per message.
5. **"How do you handle 'last seen' and 'typing indicators' at scale?"** → Tests ephemeral state design; typing indicators sent over WebSocket with a 3s TTL — no persistence needed; last seen written to Redis with async flush to Cassandra; rate-limited to prevent flooding presence service.
