---
module: 05-hld-problems
topic: Medium
status: interview-ready
tags: [05-hld-problems, system-design, medium]
---
# Design WhatsApp

> **Difficulty**: Medium
> **Topics**: WebSockets, Message Delivery, E2E Encryption, Presence
> **Time**: 45 min
> **Companies**: Meta, Google, Amazon

---

## Clarifying Questions

1. "Are we building 1:1 messaging, group chats, or both?"
2. "Do we need end-to-end encryption? WhatsApp uses Signal Protocol."
3. "What delivery guarantees — at-least-once or exactly-once? (WhatsApp deduplicates at client.)"
4. "Do we need media (photos/video) or just text messages?"
5. "What's the scale — 500M DAU? That drives connection management design."
6. "Do we need read receipts (delivered/read ticks)?"

---

## Back-of-Envelope

```
500M DAU, each sends ~5 messages/day
  Writes: 500M × 5 / 86,400 = ~29K messages/sec
  Peak: ~3× avg = ~87K messages/sec → call it 694K/sec at absolute peak

Connection management:
  500M users, 60% online at peak = 300M concurrent WebSocket connections
  Each gateway: ~50K connections → 300M / 50K = 6,000 gateway servers

Media storage:
  Avg media message: 500KB, 20% of messages have media
  694K × 0.2 × 500KB = ~70GB/sec → CDN required; S3 for durable storage
  WhatsApp: 8 PB/day media at peak (includes video)

Message store:
  Avg message: 500 bytes × 694K/sec = ~350 MB/sec → Cassandra append-only
```

---

## APIs

```
// WebSocket connection (persistent)
WS /connect
  client sends: { type: "auth", token: "..." }
  server sends: { type: "ack", gateway_id: "gw-us-east-1-042" }

// Send message (over WebSocket)
WS send: { type: "message", conversation_id, to_user_id, content_encrypted, msg_id, idempotency_key }
  server ack: { type: "sent", msg_id, server_timestamp }

// Fetch message history (REST, for reconnect)
GET /api/v1/conversations/{conversation_id}/messages?after={msg_id}&limit=50
  -> { messages: [...], has_more: bool }
```

---

## Architecture

```
Client (Mobile)
  |
  | WebSocket (persistent, TLS)
  |
WebSocket Gateway (6,000 servers)
  +-- Redis presence: SET ws:{user_id} {gateway_id} EX 30  (30s TTL heartbeat)
  +-- Accepts message from sender
  +-- Looks up recipient gateway via Redis: GET ws:{recipient_id}
  +-- Routes message to recipient's gateway (gRPC)
  +-- Stores message to Cassandra (async, via Kafka)

If recipient offline:
  +-- Store in Cassandra as "pending"
  +-- Push notification via FCM/APNs

Cassandra (message store):
  Partition: (user_id, conversation_id)
  Cluster: message_id DESC (TIMEUUID)
  query: "get last 50 messages in this conversation for this user"

Kafka: "message.sent" events → async storage, analytics, backup
```

---

## Data Model

```sql
-- Cassandra messages table
-- partition key: (user_id, conversation_id)
-- clustering: message_id DESC (TIMEUUID for time-ordering + uniqueness)
-- columns: sender_id, content (ciphertext), media_s3_key, status, sent_at

-- Cassandra conversations table
-- partition key: user_id
-- clustering: last_message_at DESC
-- columns: conversation_id, other_user_id (or group_id), unread_count, last_preview

-- PostgreSQL (user accounts and group metadata)
CREATE TABLE users (
    user_id         BIGINT PRIMARY KEY,
    phone_number    VARCHAR(20) UNIQUE,
    public_key      BYTEA,           -- Signal Protocol X3DH prekey bundle
    last_seen_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE groups (
    group_id    UUID PRIMARY KEY,
    name        TEXT,
    creator_id  BIGINT,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE group_members (
    group_id    UUID,
    user_id     BIGINT,
    joined_at   TIMESTAMPTZ,
    PRIMARY KEY (group_id, user_id)
);
```

---

## Key Design Decisions

**1. WebSocket gateways with Redis presence**
HTTP is request-response — can't push messages to client. WebSocket maintains a persistent bidirectional connection. Each of 6,000 gateway servers holds ~50K connections. Redis stores `ws:{user_id} → gateway_id` with 30s TTL (heartbeat refreshes it). To deliver a message: (1) lookup recipient's gateway in Redis, (2) route via gRPC to that gateway, (3) gateway pushes to the WebSocket connection. If Redis key is missing: user is offline → push notification.

**2. Signal Protocol E2E encryption**
Server stores zero plaintext. Messages are encrypted on sender's device using Signal Protocol's Double Ratchet algorithm. Key exchange uses X3DH (Extended Triple Diffie-Hellman): each user uploads a prekey bundle to the server (public keys only). When Alice wants to message Bob: she fetches Bob's prekey bundle from the server, computes a shared secret, encrypts. Server receives ciphertext → stores ciphertext → delivers ciphertext. Server can never decrypt. Even if the server is compromised, messages stay private.

**3. Delivery state machine: 1 gray → 2 gray → 2 blue**
- Sent (1 gray tick): message stored on server, server acked to sender
- Delivered (2 gray ticks): message delivered to recipient's device; device acks to server
- Read (2 blue ticks): recipient opened conversation; device sends read receipt
State transitions stored per message in Cassandra. Read receipts sent over WebSocket back to sender's gateway.

**4. Media: upload to CDN, send URL via encrypted message**
Large files not sent through WebSocket. Flow: (1) sender uploads media to S3 via pre-signed URL, (2) sender includes S3 URL + encryption key in the encrypted message payload, (3) recipient decrypts message, downloads from CDN using the embedded URL and decrypts locally. Server never sees the media content. WhatsApp deletes media from CDN after 30 days.

---

## Deep Dives

**Group messaging fan-out**
Group with 256 members: sender sends one encrypted message to the server. Server fans out to all 256 members' inboxes (256 Cassandra writes). For large groups (>100), WhatsApp uses "Sender Keys" — sender distributes a group session key once; subsequent messages are encrypted once with that key, not 256 times. Reduces encryption overhead from O(N) to O(1) per message.

**Offline message delivery**
Recipient's gateway is empty (user offline). Message stored in Cassandra with status=PENDING. FCM/APNs push notification sent (encrypted, content-free — just a "you have a new message" ping). When user reconnects: gateway queries Cassandra for all PENDING messages for that user, delivers via WebSocket, updates status to DELIVERED.

---

## Failure Scenarios

| Failure | Impact | Mitigation |
|---------|--------|------------|
| Gateway crash | ~50K users disconnected | Clients reconnect within 30s; messages in Cassandra pending delivery |
| Redis presence miss | Can't find recipient's gateway | Fall back to push notification; recipient fetches messages on reconnect |
| Cassandra node failure | Message writes may fail | RF=3, quorum writes (W=2); auto-repair; message retry from client |
| Push notification failure | Offline user doesn't know about message | Retry via exponential backoff; user sees messages on next app open |
| Message delivered twice | Duplicate display | Client deduplicates by msg_id; idempotency_key prevents double-store |

---

## Interview Questions Asked

### Meta
1. **"How does WhatsApp's E2E encryption work at a system design level — where do keys live?"** → Public keys live on WhatsApp's key server (fetched when initiating a new conversation). Private keys live only on the device, never uploaded. The key server stores each user's prekey bundle (identity key, signed prekey, one-time prekeys). When Alice opens a conversation with Bob, she fetches Bob's bundle from the server, computes a shared secret via X3DH, then uses Double Ratchet for all subsequent messages. The server is a key distribution service — it never touches plaintext. The interviewer wants to hear: server only stores public keys, ciphertext transits through server, plaintext never leaves devices.
2. **"How do you handle the case where a user's device is replaced — they lost their phone and reinstalled WhatsApp?"** → New device = new key pair. WhatsApp re-registers the user's phone number to the new device. All existing conversations show a "security code changed" notice — this is the Signal Protocol's safety number changing, alerting contacts that the key changed. Message history is on the old device (encrypted backup to Google Drive/iCloud, protected by a separate backup key). Previous encrypted messages cannot be decrypted on the new device without the backup key.

### Google
1. **"How would you design Google Messages (RCS) to work across carriers and devices?"** → RCS is carrier-backed, so delivery goes through carrier infrastructure rather than your own servers. The architecture differs: message routing uses carrier MSISDN lookup (phone number → carrier), not a direct WebSocket connection. For Google's system: Google acts as the intermediary when both parties use Google Messages — similar to WhatsApp's WebSocket model. When one party is on a carrier RCS: route through the carrier's IMS gateway. The design challenge is fallback: if RCS delivery fails, fall back to SMS.
2. **"How do you scale to 1B concurrent WebSocket connections globally?"** → Geographically distributed gateway clusters. Users connect to their nearest regional gateway (DNS-based routing, anycast). Messages between regions travel over your internal backbone (not the public internet). Presence (Redis) is replicated across regions with ~100ms lag for cross-region routing. At 1B connections and 50K per server: 20,000 gateway servers. Horizontal scaling — add servers as connection count grows.

### Common Follow-ups
1. **"Two users send messages simultaneously. How do you order them in a group chat?"** → Server assigns a logical timestamp (Lamport clock or Snowflake ID) when it receives and stores the message. This is the "server order" — not necessarily causal order, but deterministic. All members see messages in the same server-assigned order. For causal ordering (true happens-before): vector clocks per conversation, but this is complex and WhatsApp doesn't do it. Server timestamp is sufficient.
2. **"How do you handle a user in a chat group of 1,000 people?"** → Fan-out: 1,000 Cassandra writes per message + 1,000 WebSocket pushes (1,000 gateway lookups via Redis). At 10 messages/min in a 1,000-person group: 10,000 Redis lookups + Cassandra writes/min. Manageable. WhatsApp caps group size at 1,024. Sender Key optimization: one encryption + 1,024 deliveries, not 1,024 encryptions.
3. **"What happens when the message is sent but the recipient never receives it?"** → Message sits in Cassandra as status=PENDING. Server retries delivery: (1) try WebSocket if user reconnects within 30s; (2) push notification via FCM/APNs; (3) retry push every 24h for up to 30 days. After 30 days: message is deleted from server (E2E design — server isn't meant to store messages long-term). User sees a single gray tick forever if recipient never came online.

---

## Interviewer Follow-Up Questions

**On WebSocket and connections:**
- "How do you scale WebSocket connections to 300M concurrent?" → Stateful servers (WebSocket gateways), each holding up to 100K connections (limited by file descriptors and RAM, ~10KB per connection = 1GB for 100K). Deploy 3,000+ gateway servers across regions. The key insight: these servers are memory-bound, not CPU-bound — scale out (more servers) not up (bigger servers). Load balancers distribute new connections; sticky sessions aren't needed because any gateway can route messages via Redis presence lookup.
- "A gateway server restarts. What happens to its 50K connections?" → All 50K clients detect the TCP disconnect and reconnect. Client uses exponential backoff (random jitter to prevent thundering herd). Reconnect process: auth → presence registration in Redis → fetch pending messages from Cassandra. Total time: ~2-5 seconds. During reconnect, incoming messages are stored as PENDING in Cassandra, delivered on reconnect. This is why Cassandra storage is non-optional — WebSocket delivery alone is not durable.
- "How does your presence system handle a user rapidly toggling between online and offline?" → Redis key `ws:{user_id}` has TTL=30s, refreshed every 15s by heartbeat. Rapid reconnect: each reconnect re-registers with potentially a new gateway_id. Old gateway_id in Redis is overwritten. If user goes offline, key expires naturally after 30s (no explicit deletion on disconnect — reduces race conditions). "Last seen" timestamp is updated on disconnect (WebSocket close event or heartbeat timeout).

**On message delivery:**
- "How do you ensure a message is delivered exactly once?" → WhatsApp uses at-least-once delivery at the server level. Deduplication at the client: each message has a globally unique msg_id (Snowflake). Client maintains a local set of received msg_ids. On duplicate delivery: client discards the duplicate silently. Server-side: Cassandra write with IF NOT EXISTS (lightweight transaction) prevents duplicate storage. The combination gives effectively-once delivery from the user's perspective.
- "What is the Signal Protocol and why does it give forward secrecy?" → Signal Protocol = X3DH (initial key exchange) + Double Ratchet (per-message key derivation). Double Ratchet derives a new encryption key for each message. Forward secrecy: even if an attacker captures today's traffic and later compromises the private key, they cannot decrypt past messages because past message keys are deleted after use. Break-in recovery: even if one session key is compromised, future messages use different keys (derived independently). This is why Signal/WhatsApp is considered the gold standard for secure messaging.
