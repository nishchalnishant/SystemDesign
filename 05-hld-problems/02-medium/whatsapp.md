---
module: 05-hld-problems
topic: Medium
status: unread
tags: [05-hld-problems, system-design, medium, whatsapp, messaging, websocket, end-to-end-encryption]
---
# Design WhatsApp (Real-Time Messaging)

> **Difficulty**: Medium | **Asked at**: Meta, Telegram, Slack, Discord

---

## Problem Statement

Design a real-time messaging system like WhatsApp that supports 1-on-1 and group messaging. Messages must be delivered in order, with delivery receipts (sent ✓, delivered ✓✓, read ✓✓). The system must handle billions of messages per day with end-to-end encryption.

---

## Functional Requirements

1. **1-on-1 messaging**: Send/receive messages between two users in real time
2. **Group messaging**: Send messages to groups of up to 256 members
3. **Delivery status**: Sent → Delivered → Read receipts
4. **Media sharing**: Photos, videos, documents up to 100MB
5. **Message history**: Persistent chat history, searchable by the user
6. **Online presence**: Show when a contact was last seen

---

## Non-Functional Requirements

- **Scale**: 2B users, 100B messages/day → 1.16M messages/sec
- **Latency**: Message delivery < 100ms P99 for online users
- **Ordering**: Messages must be delivered in-order within a conversation
- **Reliability**: At-least-once delivery; message must not be lost even if recipient is offline
- **End-to-end encryption**: Server never sees message plaintext

---

## Core Entities

| Entity | Key Fields |
|--------|-----------|
| `User` | user_id, phone_number, display_name, last_seen |
| `Message` | message_id, conversation_id, sender_id, content_encrypted, type (text/image/video), created_at, client_timestamp |
| `Conversation` | conversation_id, type (direct/group), members[], created_at |
| `MessageStatus` | message_id, user_id, status (sent/delivered/read), updated_at |
| `DeviceKey` | user_id, device_id, public_key (for E2E key exchange) |

---

## API Design

**Connection**: WebSocket (persistent, bidirectional). HTTP for REST operations.

```http
WebSocket: wss://msg.whatsapp.com/connect?user_id=u123&token=<auth>
  → client connected, server registers connection in routing table

WS message (client → server):
{
  "type": "message",
  "conversation_id": "conv456",
  "recipient_id": "u789",
  "encrypted_payload": "<base64>",
  "client_msg_id": "msg-uuid-local",
  "client_timestamp": 1735689600000
}

WS message (server → client):
{ "type": "message", "message_id": "m123", "conversation_id": "...", ... }
{ "type": "ack", "client_msg_id": "msg-uuid-local", "server_message_id": "m123" }
{ "type": "status", "message_id": "m123", "status": "delivered", "user_id": "u789" }

REST:
GET /api/v1/conversations/{conv_id}/messages?before=<msg_id>&limit=50
POST /api/v1/conversations/group
PUT /api/v1/conversations/{conv_id}/read?last_message_id=m999
```

---

## High-Level Design

```
Client A                              Client B
  │  WebSocket                          │  WebSocket
  ▼                                     ▼
Chat Server A                       Chat Server B
  │  (stateful: holds WS connections)   │
  │                                     │
  ▼                                     ▼
Message Router (Redis pub/sub)
  │  chat_server_A subscribes to user_A's channel
  │  chat_server_B subscribes to user_B's channel
  │
  ▼
Message Service
  │
  ├── Message Store (Cassandra — partitioned by conversation_id, ordered by created_at)
  ├── Message Queue (Kafka — durability for offline delivery)
  └── Status Service → Redis (online presence) + Cassandra (status history)

Offline delivery path:
  User B offline → Message saved to Cassandra → Push notification via FCM/APNs
  User B comes online → WebSocket connect → pull undelivered messages
```

---

## Deep Dive 1: Message Routing and WebSocket Management

**Problem**: With 2B users and millions of concurrent connections, how does the server route a message from User A (connected to Server 1) to User B (connected to Server 7)?

**Connection registry** (Redis):
```
user:{user_id}:server = "chat-server-7"   # which server holds the WS
user:{user_id}:online = 1                  # TTL = 30s, refreshed via heartbeat
```

**Message routing flow**:
1. User A sends message to Chat Server A
2. Chat Server A: save message to Cassandra (durable), publish to Redis channel `user:{B}:inbox`
3. Chat Server B (subscribed to `user:{B}:inbox` via Redis pub/sub) receives the event
4. Chat Server B: push message to User B's WebSocket connection

**User B offline**: Redis pub/sub has no subscriber. Message is already in Cassandra. Notification service sends push notification. When B reconnects: pull unread messages from Cassandra since `last_read_message_id`.

**Chat server scalability**: Each chat server holds ~100K WebSocket connections (Go goroutines or Node.js event loop handle this efficiently). 2B users × 30% concurrently active = 600M connections → 6,000 chat servers.

---

## Deep Dive 2: Message Ordering and Exactly-Once Delivery

**Problem**: Messages must arrive in order. User A sends M1, M2, M3 — B must see them as M1, M2, M3 even if M2 arrives before M1.

**Client-side timestamps + server-side sequencing**:
1. Client attaches a monotonically increasing `client_timestamp` to each message
2. Server assigns a `message_id` (Snowflake — time-ordered)
3. Cassandra stores messages by `(conversation_id, message_id)` — clustering key ensures sort order

**Client-side ordering**: Client renders messages sorted by `client_timestamp`. If a message arrives out of order (network reorder), client holds it in a buffer and renders once the gap is filled.

**Deduplication**: Client attaches a `client_msg_id` (UUID generated locally). Server checks Redis `SETNX dedup:{client_msg_id} 1 EX 86400` before saving. If key exists → duplicate → return existing `message_id`.

**Read receipt ordering**: "Read" status for a conversation is stored as `last_read_message_id` per user. When A opens the chat and reads up to M99, A sends `{ "conversation_id": ..., "last_read_message_id": "M99" }`. Server updates Cassandra and notifies B's client to show double blue checkmarks up to M99.

---

## Deep Dive 3: End-to-End Encryption (Signal Protocol)

**Problem**: WhatsApp must not be able to read user messages, even with server access. Messages must be encrypted on the client device and decrypted only on the recipient's device.

**Signal Protocol (simplified)**:
1. **Key generation**: Each user generates a public/private key pair on their device. Public key uploaded to WhatsApp servers: `PUT /api/v1/keys/public`.
2. **Key exchange (X3DH)**: Before the first message to User B, User A fetches B's public key. Uses Elliptic Curve Diffie-Hellman to derive a shared secret without transmitting it. Both A and B can independently compute the same shared secret.
3. **Double Ratchet**: Shared secret seeds a Double Ratchet algorithm that generates a new encryption key for every message. Forward secrecy: compromising today's key doesn't reveal past messages.
4. **Message encryption**: Message plaintext encrypted with AES-256 using the per-message key. Server receives and stores only ciphertext.
5. **Server's role**: Store ciphertext blobs, route messages, store public keys. Server never holds the shared secret or plaintext.

**Multi-device challenge**: User B has iPhone + laptop. Both need the same messages. Each device has its own key pair. When A sends a message to B, the client encrypts separately for each of B's devices and sends multiple ciphertexts to the server. Server delivers each ciphertext to the appropriate device.

**Key transparency**: WhatsApp provides a "Security Code" (fingerprint of the shared key pair) that users can verify out-of-band. Mismatched code = man-in-the-middle attack.

---

## Interviewer Questions by Level

**Junior**:
- Why do messaging apps use WebSockets instead of HTTP polling?
- What is a delivery receipt (✓ vs ✓✓ vs blue ✓✓)? How is it implemented?
- What happens to a message when the recipient is offline?

**Mid-level**:
- How does the message router know which server holds a user's WebSocket connection?
- How do you handle message ordering across a distributed system?
- Why is Cassandra a better choice than PostgreSQL for storing chat messages?

**Senior**:
- Design the multi-device message sync — how do messages appear on all a user's devices?
- Explain the Signal Protocol at a high level — why does it provide forward secrecy?
- A group has 256 members. When one member sends a message, how is it delivered to all 256? What's the server-side load?
- How would you design message search (search across the user's entire message history) while maintaining E2E encryption?
