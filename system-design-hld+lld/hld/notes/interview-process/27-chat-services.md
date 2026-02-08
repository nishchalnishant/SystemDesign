# #27 Chat services

Here’s a complete, time-boxed, 1-hour interview-ready answer for designing a Global Chat Service (like WhatsApp or Facebook Messenger). It follows your system design interview structure, including functional & non-functional requirements, APIs/data model, architecture, deep dive, and trade-offs.

***

## 0 – 5 min — Problem recap, scope & assumptions

<br>

Goal: Design a scalable, real-time chat service that allows users to send messages individually or in groups, supports message delivery and persistence, and works globally across devices.

<br>

Scope for interview:

* One-on-one chats and group chats.
* Real-time message delivery.
* Message persistence and history.
* Presence status (online/offline).
* Push notifications.
* End-to-end encryption (optional).

<br>

Assumptions:

* Hundreds of millions of users, billions of messages/day.
* Peak concurrent connections \~10–50M.
* Message delivery latency <200 ms for online users.
* Mobile and web clients.
* Message order preserved per conversation.

***

## 5 – 15 min — Functional & Non-Functional Requirements

<br>

#### Functional Requirements

<br>

Must

1. Send/receive messages: One-on-one and group chat.
2. Message persistence: Store messages for later retrieval.
3. Message ordering: Preserve order per chat.
4. Read receipts: Track delivered and read messages.
5. Presence: Show online/offline status.
6. Notifications: Push notifications for offline users.

<br>

Should

* Support media messages (images, videos, files).
* Support message editing and deletion.
* Typing indicators.

<br>

Nice-to-have

* End-to-end encryption.
* Message search.
* Multi-device sync.
* Reactions to messages.

***

#### Non-Functional Requirements

* Latency: <200 ms for message delivery.
* Availability: 99.99% uptime globally.
* Scalability: Handle millions of concurrent users and high message throughput.
* Durability: Persist all messages reliably.
* Consistency: Strong ordering per conversation; eventual consistency acceptable across devices.
* Monitoring: Track message delivery latency, failures, and server health.

***

## 15 – 25 min — API / Data Model

<br>

#### APIs

```
# Send message
send_message(sender_id: str, receiver_id: str, content: str, chat_id: str=None) -> message_id

# Receive messages
get_messages(chat_id: str, limit: int=50, last_message_id: str=None) -> List[Message]

# Presence
get_presence(user_id: str) -> "online/offline"

# Optional: read receipt
mark_as_read(user_id: str, message_id: str)
```

#### Data Models

<br>

User

```
{
  "user_id": "string",
  "username": "string",
  "device_tokens": ["token1", "token2"]
}
```

Chat

```
{
  "chat_id": "string",
  "participants": ["user_id1", "user_id2", ...],
  "is_group": bool
}
```

Message

```
{
  "message_id": "string",
  "chat_id": "string",
  "sender_id": "string",
  "content": "string",
  "timestamp": "datetime",
  "status": "sent/delivered/read"
}
```

***

## 25 – 40 min — High-level architecture & data flow

```
[Client] --> [API Gateway] --> [Chat Service] --> [Message Queue]
                                         |
                                         v
                                    [Database / Storage]
                                         |
                                         v
                                     [Cache / Pub-Sub]
```

Components

1. Chat Service: Handles send/receive API calls, message ordering, presence.
2. Message Queue / Pub-Sub: RabbitMQ/Kafka for message delivery.
3. Database / Storage: Persistent storage for messages (NoSQL like Cassandra for high write throughput).
4. Cache Layer: Redis for recent messages, online presence, and typing indicators.
5. Push Notification Service: Notify offline users via FCM/APNs.

<br>

Data Flow

1. User sends message → Chat Service → Message Queue → Delivery to online users via WebSocket → Persist in DB.
2. Offline users → Chat Service triggers push notifications → Delivered when device is online.
3. Message history requests → Fetch from cache or DB.

***

## 40 – 50 min — Deep dive — message delivery, scaling, concurrency

<br>

#### Message Delivery

* WebSocket connections for real-time delivery.
* Fan-out per chat: For group messages, send to all participants.
* Delivery acknowledgment: Message Queue tracks delivery to each recipient.

<br>

#### Scaling

* Horizontal sharding: Partition users by user\_id across multiple chat service instances.
* Partitioned queues: Each chat or conversation partitioned across queues for ordering.
* Caching: Recent messages, online presence, and typing indicators in Redis.

<br>

#### Fault Tolerance

* Persistent queues for reliable message delivery.
* Database replication for durability.
* Retry mechanism for undelivered messages.

***

## 50 – 55 min — Back-of-the-envelope calculations

<br>

Assumptions

* 100M daily active users, 10B messages/day → \~115k messages/sec.
* Each message \~1 KB → \~10 GB/day storage.
* Peak concurrency 10M → WebSocket connections need horizontal scaling.
* Caching top 100 messages per chat per user → manageable in Redis cluster.

<br>

Storage

* NoSQL DB like Cassandra for horizontal scalability.
* Cache recent messages and online status in Redis.
* Message queues for reliable delivery.

***

## 55 – 58 min — Monitoring & ops

<br>

Monitoring

* Message delivery latency and failure rates.
* Queue lag and processing rate.
* Online user count and connection health.
* Database and cache performance metrics.

<br>

Operational concerns

* Handling peak traffic (holidays, events).
* Device reconnection and message sync.
* Multi-region replication for global availability.

***

## 58 – 60 min — Trade-offs, evolution & summary

<br>

#### Trade-offs

* WebSocket vs polling: WebSocket for real-time; polling simpler but higher latency.
* NoSQL vs SQL: NoSQL for high write throughput; SQL may support complex queries but slower.
* Push vs pull notifications: Push reduces delay but increases complexity; pull simpler but higher latency.

<br>

#### Evolution

1. MVP: One-on-one chat with message persistence, basic delivery.
2. Phase 2: Group chats, push notifications, online presence.
3. Phase 3: Multi-device sync, media messages, reactions, encryption, global scaling.

<br>

#### Summary

* System enables real-time messaging for one-on-one and group chats.
* Uses WebSocket, message queues, NoSQL DB, and cache for scalable, low-latency, reliable delivery.
* Horizontally scalable, fault-tolerant, monitorable, and capable of evolving to global usage with multi-device support.

***

I can next create a sequence diagram showing message sending, queue delivery, DB persistence, and client notification, which is extremely useful to explain in interviews.

