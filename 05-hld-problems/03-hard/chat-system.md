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
