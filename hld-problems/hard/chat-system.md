# Design Chat System (WhatsApp/Telegram)

> **Difficulty**: Hard  
> **Topics**: WebSockets, Long Polling, Real-time Delivery, Consistency, Offline Support  
> **Time**: 60 minutes  
> **Companies**: Meta (WhatsApp/Messenger), Telegram, Discord, Slack

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

## Connection Management (The "Real-time" Magic)

### Protocols
1. **HTTP (REST)**: Good for Login, Profile Update, History Fetch. **Bad for receiving messages** (High latency/overhead).
2. **Long Polling**: Client holds connection open. Server responds when data arrives. Better but heavy on server resources.
3. **WebSockets (Selected)**: Bi-directional persistent connection. Server pushes messages instantly. ideal for Chat.

### Dealing with 2 Billion Connections
- A single server can manage ~50k concurrent WebSockets (Network IO bound).
- Need **Connection Gateway** layer (Horizontally Scaled).
- **Stateful**: Gateway knows "User A is connected to Box 42".
- Uses **Redis / Zookeeper** to map `UserID -> GatewayMachineID`.

## Architecture

```
       User A                   User B
         │                        ▲
         ▼                        │
   ┌─────────────┐          ┌─────────────┐
   │ Chat        │◀────────▶│ Chat        │
   │ Gateway 1   │          │ Gateway 2   │ (Maintenance of Websockets)
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
```

## Message Flow (1:1 Chat)

1. **User A** sends message via WebSocket to **Gateway 1**.
2. **Message Service** persists message to **Cassandra** (Status: `SENT`).
3. **Message Service** queries Redis: "Where is User B connected?"
4. If **User B** Online (Gateway 2):
   - Forward message to **Gateway 2**.
   - Gateway 2 pushes to **User B** via WebSocket.
   - User B sends ACK (`DELIVERED`) -> Gateway 2 -> A.
5. If **User B** Offline:
   - Push Notification Service (FCM/APNS) triggers "You have a new message".
   - Message sits in DB. When B connects, fetch unread messages.

## Data Schema (Cassandra/NoSQL)

**Why NoSQL?**
- Write-heavy (100B/day).
- Horizontal scaling easier.
- Consistency: `local_quorum` sufficient.

### Message Table
```sql
-- Partition Key: chat_id (keeps chat messages together)
-- Clustering Key: message_id (time-based sort order like Snowflake / KSUID)
CREATE TABLE messages (
    chat_id BIGINT,
    message_id TIMEUUID,
    sender_id BIGINT,
    content TEXT,
    media_url TEXT,
    status TINYINT, -- 1: Sent, 2: Delivered, 3: Read
    PRIMARY KEY (chat_id, message_id DESC)
);
```

## Group Chat Complexity

**Scenario**: Group of 200 people.
- User A sends message.
- Server needs to deliver to 199 users.

**Approach**:
1. **Message Service** fetches Group Members from SQL DB.
2. **Fan-out**:
   - For small groups (<100): Loop and push to each member's Gateway.
   - For large groups/channels (>5k): Write to a Message Queue (Kafka), workers process fan-out in batches.
3. **Optimization**: Don't confirm "Delivered" until X% receive, or just show "Sent". "Read by All" is expensive O(N).

## Sequencing & Consistency

**Problem**: User A sends `Msg1` then `Msg2`. B receives `Msg2` then `Msg1`.
**Solution**:
- **Sequence Numbers**: Assign incremental ID (Sequence ID) per Chat ID.
- **Client Side Re-ordering**:
  - Client receives `Seq 5`. Last seen `Seq 3`.
  - Client knows `Seq 4` is missing.
  - Buffer `Seq 5`, request `Seq 4`, then display in order.
- **Timestamps**: Unreliable due to clock skew. Use **Snowflake IDs** or **Logical Clocks**.

## Interview Q&A

**Q: "How to handle 'Last Seen'?"**
- A: "Heartbeat mechanism. Client pings server every 30s. Store in Redis `User:LastActive`. If >1 min, show 'Last seen at X'. Don't write to Hard DB every ping, only on session close."

**Q: "Multi-device Synching?"**
- A: "Treat each device as a separate 'User' entity in routing. `UserA_Mobile`, `UserA_Desktop`. Fan-out message to ALL active sessions of User A."

**Q: "End-to-End Encryption?"**
- A: "Signal Protocol. Keys generated on client. Server only stores encrypted blob. Server cannot read messages. Only device with Private Key can decrypt."
