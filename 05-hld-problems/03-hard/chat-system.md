> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a team chat system (Slack) — real-time channel messaging with presence indicators, thread replies, file sharing, and search across message history.
>
> **Key design decisions:**
> - Connection layer: WebSocket gateway servers; each user connects to a gateway; Redis tracks {user_id → gateway_id}; gateways stateless except connection state
> - Message routing: sender → gateway → Kafka → fan-out service → each member's gateway → WebSocket push to clients
> - Channel membership: channel members stored in DB + cached in Redis; fan-out scope determined by membership list
> - Message storage: Cassandra for messages (channel_id as partition key, timestamp as clustering key); append-only; query by channel + time range
> - Threads: each message can have a thread; thread replies stored separately; thread_id = parent message_id
> - Presence: heartbeat every 30s; presence state (online/away/offline) in Redis with TTL; propagated to workspace members on change
> - Search: Elasticsearch indexes message text; index on (workspace_id, channel_id, content); query by workspace + text + time range
>
> **Key takeaway:** The fan-out routing layer (Redis: user → gateway mapping) is the core architectural challenge — without it, you don't know which server to push messages to.

---
module: 05-hld-problems
topic: Hard
status: unread
tags: [05-hld-problems, system-design, hard, chat, websocket, message-routing, group-chat]
---
# Design a Chat System (Slack)

> **Difficulty**: Hard | **Asked at**: Slack, Discord, Microsoft, Meta

---

## Problem Statement

Design a team chat system like Slack. Users belong to workspaces, send messages in channels or direct messages, and receive messages in real time. The system must support thousands of members per channel, message history search, file sharing, and high availability.

---

## Functional Requirements

1. **Direct messages**: 1-on-1 real-time messaging
2. **Channels**: Group conversations with up to 10,000 members
3. **Message history**: Persistent, searchable message history
4. **File sharing**: Upload and share files up to 1 GB
5. **Reactions and threads**: Emoji reactions and threaded replies
6. **Online presence**: Show who is online in the workspace
7. **Search**: Full-text search across message history

---

## Non-Functional Requirements

- **Scale**: 10M daily active users, 1B messages/day → 11,600 messages/sec
- **Latency**: Message delivery < 100ms P99 for online users in the same region
- **Availability**: 99.99% — workspace messaging must not be interrupted
- **Ordering**: Messages in a channel must be delivered in order
- **Storage**: 1B messages/day × 500 bytes avg = 500 GB/day raw message storage

---

## Core Entities

| Entity | Key Fields |
|--------|-----------|
| `Workspace` | workspace_id, name, plan, member_count |
| `User` | user_id, workspace_id, username, display_name, status |
| `Channel` | channel_id, workspace_id, name, type (public/private/dm), member_ids[] |
| `Message` | message_id, channel_id, sender_id, text, attachments[], created_at, thread_id |
| `Reaction` | message_id, emoji, user_ids[] |
| `Membership` | user_id, channel_id, last_read_message_id, joined_at |

---

## API Design

```http
WebSocket: wss://chat.slack.com/ws?workspace_id=W123&token=<auth>

WS: send message
{
  "type": "message",
  "channel_id": "C456",
  "text": "Hello team!",
  "client_msg_id": "uuid-local"
}

WS: receive message
{
  "type": "message",
  "message_id": "M789",
  "channel_id": "C456",
  "sender": { "user_id": "U123", "name": "Alice" },
  "text": "Hello team!",
  "ts": "1735689600.123456"
}

REST:
GET /api/v1/channels/{channel_id}/messages?oldest=<ts>&latest=<ts>&limit=100
POST /api/v1/channels/{channel_id}/messages/{message_id}/reactions
POST /api/v1/messages/{message_id}/replies
GET /api/v1/search?q=deployment+steps&workspace_id=W123
```

---

## High-Level Design

```
Client (WebSocket)
  │
  ▼
WebSocket Gateway (stateful — holds WS connections)
  │  Connection registry: Redis hash { user_id → gateway_id }
  │
  ▼
Message Service
  │ 1. Validate + persist to Cassandra
  │ 2. Publish to Kafka channel-messages (partition by channel_id)
  │
  ▼
Fan-out Service (Kafka consumer)
  │ For each message in channel C:
  │   lookup members of C (Redis cache)
  │   for each member who is online:
  │     find their gateway (Redis)
  │     push via internal gRPC to that gateway
  │   for each member who is offline:
  │     increment their unread count (Redis)
  │     queue for push notification
  │
  ▼
WebSocket Gateway → Client

Storage:
  Cassandra: messages (partition by channel_id, cluster by message_id)
  PostgreSQL: channels, workspaces, memberships (relational)
  Elasticsearch: full-text search index
  S3: file attachments
```

---

## Deep Dive 1: Fan-Out for Large Channels

**Problem**: A Slack channel with 10,000 members receives a message. The fan-out service must notify all 10,000 online members. At 11,600 messages/sec globally, and assuming channels average 100 members, that's 1.16M notifications/sec. For a channel with 10,000 members, one message requires 10,000 targeted WebSocket pushes.

**Naive fan-out bottleneck**: Looking up 10,000 members' gateway assignments from Redis per message = 10,000 Redis GET calls. At 100 µs/call, that's 1 second just for lookups.

**Batched Redis MGET**: Instead of 10,000 individual GET calls, batch into Redis pipeline calls of 100 keys each = 100 pipeline calls × 100 keys = 10,000 lookups in ~50ms.

**Channel member cache**: Cache channel membership in Redis as a sorted set: `channel_members:{channel_id}` = SET of user_ids. For 10K members, one SMEMBERS call returns all 10K. Cached for 5 minutes; invalidated on join/leave.

**Selective fan-out**: Only notify members who are **online** (have an active WebSocket). Offline members get their unread count incremented in Redis; messages are fetched via REST when they reconnect. This reduces fan-out for large channels from 10K pushes to (10K × active_fraction) pushes — typically 20-30% are online.

**Fan-out queue sharding**: Fan-out work is partitioned by `channel_id` across fan-out workers. All messages to the same channel are processed by the same worker (prevents out-of-order notification delivery for the same channel).

> 🎯 **Staff signal:** The senior framing is that fan-out cost is O(online members), not O(members) — so the load-bearing optimization is **selective fan-out to online sockets only**, with offline members downgraded to an unread-counter increment they reconcile via REST on reconnect. That single choice cuts a 10K-member push to ~2-3K and, more importantly, decouples delivery cost from channel size for the (large) offline fraction. Two E6 details make it real: (1) partitioning fan-out work by `channel_id` so one worker owns a channel is what preserves *per-channel* order under concurrency — sharding by message would reorder; and (2) the batching insight that 10K individual Redis GETs (~1s) must become pipelined MGETs (~50ms), because at fan-out scale the *lookup* is the bottleneck, not the push. E5 says "fan out to members"; E6 makes the cost proportional to who's actually listening and pins ordering to the shard key.

---

## Deep Dive 2: Message Ordering and the Timestamp Problem

**Problem**: Two users send messages simultaneously to the same channel. Both messages must appear in a consistent order for all viewers of the channel.

**Wall clock unreliability**: Two servers in different data centers will have slightly different wall clocks. Using server timestamps as the ordering key → two simultaneous messages may appear in different orders for different viewers.

**Slack's approach — `ts` (timestamp-based ID)**:
- Each message gets a `ts` value: `{unix_epoch}.{microseconds}`, e.g., `1735689600.123456`
- The Message Service assigns `ts` using a per-channel sequence: a Redis atomic counter `msg_seq:{channel_id}` ensures monotonically increasing sequence numbers within a channel
- `message_id = f"{channel_id}.{seq_num}"` guarantees strict ordering within a channel

**Cassandra storage**:
```
CREATE TABLE messages (
  channel_id text,
  message_id text,  -- Snowflake-like, time-ordered
  sender_id text,
  text text,
  created_at timestamp,
  PRIMARY KEY (channel_id, message_id)
) WITH CLUSTERING ORDER BY (message_id DESC);
```
Partition key: `channel_id` — all messages in a channel go to the same partition. Clustering key: `message_id` DESC — messages sorted newest-first within partition. One Cassandra query retrieves a page of messages in order.

> 🎯 **Staff signal:** The key realization is that you only need a *total order per channel*, not a global one — and that lets you sidestep the unsolvable problem (synchronized wall clocks across data centers) entirely. A per-channel Redis atomic counter (`msg_seq:{channel_id}`) gives strict monotonic sequence numbers scoped to exactly the ordering domain that matters, and that sequence doubles as the Cassandra clustering key so storage is pre-sorted and a page is one partition read. Naming *why global ordering is both impossible and unnecessary* is the E6 line: cross-channel ordering has no observer (no user reads two channels as one stream), so paying for a global sequencer would be solving a problem nobody has. The tradeoff to acknowledge: the per-channel counter is a single point of serialization per channel — fine, because a channel is already a natural low-contention shard, and it's exactly the same `channel_id` partitioning the fan-out and storage layers use. E5 reaches for timestamps; E6 scopes ordering to the channel and reuses that scope as the shard key everywhere.

---

## Deep Dive 3: Message Search

**Problem**: Slack's search must find "deployment steps" across all messages in a 5,000-member workspace with 3 years of history. Users expect results in < 1 second.

**Elasticsearch** per workspace:
- Index: one document per message with fields: `message_id, channel_id, sender_id, text, ts, workspace_id`
- Shard by `workspace_id` — keeps workspace data co-located, enables fast per-workspace queries
- Analyzer: English stemming + stopword removal on `text` field
- Query: `{ "bool": { "must": { "match": { "text": "deployment steps" } }, "filter": { "term": { "workspace_id": "W123" } } } }`

**Authorization in search**: User can only see messages in channels they're a member of. Filter: `"terms": { "channel_id": [list of user's channel_ids] }`. User's channel list is cached in Redis.

**Indexing pipeline**: Messages flow from Cassandra → Kafka → Elasticsearch indexer. Indexing lag ~2 seconds. Messages become searchable within 2 seconds of being sent.

**File attachment search**: File names and text content (from OCR/PDF parsing) are indexed alongside the message. Image OCR runs asynchronously after upload: S3 → Lambda (Tesseract OCR) → Elasticsearch update.

> 🎯 **Staff signal:** The detail most candidates miss is that chat search is an *authorization-filtered* search, not a relevance problem — a user must never see a hit from a private channel they aren't in. So the ACL can't be a post-filter (that leaks result counts and paginates wrong); it has to be pushed *into the query* as a `terms: channel_id ∈ [user's channels]` filter clause, with the membership list cached in Redis. Getting that boundary right is the senior tell. The second E6 point is treating the index as a *derived, eventually-consistent view*: Cassandra is the source of truth, Elasticsearch is fed async via Kafka with ~2s lag, and you shard the index by `workspace_id` so a query fans out to one shard, not the cluster. Stating the ~2s searchability lag as an accepted tradeoff — search is not read-your-writes — rather than pretending it's synchronous, is the difference between E5 and E6.

---

## Interviewer Questions by Level

**Junior**:
- Why does Slack use WebSockets instead of HTTP for message delivery?
- What is a Slack workspace and channel? How are they related?
- What happens to messages when a user is offline?

**Mid-level**:
- How do you route a message from one user's WebSocket gateway to another user's WebSocket gateway?
- How do you ensure messages in a channel appear in the same order for all viewers?
- How does search work in Slack? What makes it different from a regular DB query?

**Senior**:
- Design the fan-out system for a 10,000-member channel. How do you deliver a message to all online members within 100ms?
- How do you handle a workspace with 500,000 members (Enterprise Grid) where a message posted in #general must reach all members?
- How do you implement message retention policies — automatically delete messages older than 90 days across petabytes of Cassandra data?
- Design the Slack status/presence system — how do you track and broadcast online/offline/DND status for millions of users?

---

## Back-of-Envelope Estimation

**Scale inputs (given in NFRs):**
- 500M users; 100M DAU; 100B messages/day; < 500ms message delivery; 1-year message history

**Message throughput:**
- 100B messages/day ÷ 86,400 sec = **~1.16M messages/sec** average
- Peak (evenings, events): 5× = **~5.8M messages/sec**
- Each message: `{msg_id, sender_id, receiver_id/group_id, content, ts, status}` ≈ 500 bytes (text avg ~50 chars, with metadata)
- Peak write throughput: 5.8M × 500 bytes = **~2.9 GB/sec** inbound

**Message fan-out:**
- Group messages: assume 10% of messages go to groups averaging 50 members
- Fan-out for group messages: 5.8M/sec × 10% × 50 = **29M delivery events/sec** peak
- Individual messages: 5.8M/sec × 90% = **5.22M deliveries/sec**
- Total delivery events: **~34.2M delivery events/sec** peak

**WebSocket connections:**
- 100M DAU; peak simultaneous online: 20% = **20M concurrent WebSocket connections**
- Each connection: ~50 KB RAM (kernel socket buffer + app state) = 20M × 50 KB = **~1 TB RAM** for connection state
- At 64 GB RAM per server: **~16 WebSocket servers** needed just for connection state
- Each server handles 20M ÷ 16 = **1.25M connections** — achievable with async I/O (Node.js/Netty)
- For message fan-out: 34.2M events/sec ÷ 16 servers = **~2.1M events/sec per server** to push to connected clients

**Message storage:**
- 100B messages/day × 500 bytes × 365 days = **~18.25 PB/year** raw
- With compression (text compresses ~4×): **~4.5 PB/year** physical
- Cassandra: partitioned by `(sender_id, receiver_id)` for 1:1 chats; `(group_id)` for groups
- Replication factor 3: **~13.5 PB/year** physical storage with redundancy
- Hot data (last 7 days): kept in SSD tier; cold data (7 days–1 year): moved to HDD tier

**Delivery receipt throughput:**
- Every message delivery generates 2 receipts (delivered + read): 1.16M messages/sec × 2 = **2.32M receipt events/sec**
- Receipt: `{msg_id, status, ts}` = 30 bytes → 2.32M × 30 bytes = **~70 MB/sec** of receipt writes

**Architecture decisions driven by these numbers:**
- **Dedicated WebSocket gateway tier, not HTTP request/response**: 20M concurrent connections need persistent sockets for < 500ms delivery. HTTP request/response for each message delivery would require 20M concurrent open HTTP connections + polling overhead. Dedicated WebSocket servers use async I/O (event loop, not one thread per connection) — 1.25M connections per server is achievable. HTTP polling at 20M users × every 500ms = 40M HTTP requests/sec — far exceeds the WebSocket model.
- **Message queue (Kafka) between ingest and delivery for fan-out**: At 34.2M delivery events/sec, synchronously pushing to all 50 group members in the HTTP handler would tie up the request thread for 50 × delivery time. Kafka decouples: ingest writes one message to Kafka; a fan-out service reads and distributes to 50 member delivery queues. This also handles offline users (messages queued in Kafka until the user comes online).
- **Cassandra for message history at PB scale**: Message writes are append-only (never UPDATE, just INSERT). Reads are: "give me messages in conversation X from time T1 to T2" — a time-range scan within a partition. Cassandra's partition-key model (`sender_id + receiver_id` → partition) with clustering key (`ts`) is exactly this access pattern. At 4.5 PB/year after compression, Cassandra's horizontal scaling (add nodes, data redistributes automatically) and tunable consistency (ONE for reads to reduce latency) matches requirements better than PostgreSQL (hard to shard this schema) or DynamoDB (expensive at this data volume).

---

## Related

**Concepts used in this design**

- [WebSockets & SSE](../../02-building-blocks/01-networking/06-websockets-sse.md)
- [Message Brokers](../../02-building-blocks/04-coordination/01-message-brokers.md)
- [Sharding](../../02-building-blocks/03-data-partitioning/01-sharding.md)
- [Consistency & Conflicts](../../01-foundations/05-advanced-distributed-theory/01-consistency-and-conflicts.md)
- [ZooKeeper Internals](../../04-advanced-topics/03-internals/10-zookeeper-internals.md)

**Practice next**

- [WhatsApp](../02-medium/whatsapp.md)
- [Notification Service](../02-medium/notification-service.md)

WhatsApp is the 1:1 subset; notifications share the delivery path.

**Frameworks**: [HLD Template](../../07-interview-templates/01-frameworks/01-hld-template.md) · [Capacity Estimation](../../07-interview-templates/02-cheat-sheets/02-capacity-estimation.md) · [Trade-offs Cheat Sheet](../../07-interview-templates/02-cheat-sheets/01-trade-offs-cheat-sheet.md)
