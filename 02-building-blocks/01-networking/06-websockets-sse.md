> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** How servers push data to browsers in real time — the three competing approaches: Long Polling, Server-Sent Events (SSE), and WebSockets.
>
> **Key topics:**
> - Why HTTP's request-response model breaks down for real-time apps
> - Long Polling: the "spinning wheel" trick
> - Server-Sent Events (SSE): a one-way news ticker
> - WebSockets: a permanent two-way phone call
> - Exact tradeoffs: latency, scalability, infra complexity, and when to pick each
>
> **Key takeaway:** Pick WebSockets for bidirectional real-time (chat, gaming). Pick SSE for server-to-client streams (notifications, live scores). Use Long Polling only as a last resort when WebSockets are blocked.

---
module: 02-building-blocks/01-networking
status: unread
tags: [websocket, sse, long-polling, real-time, networking]
---

# WebSockets, SSE & Long Polling — System Design Guide

## Why Should I Care?

Imagine you're designing WhatsApp. A user sends a message. How does it instantly appear on your friend's screen without them clicking "Refresh"?

HTTP is a pull protocol — you ask, the server answers, and the connection closes. But real-time apps need the **server to push data to the client**. This is called **server-push**, and there are three main ways to achieve it.

Getting this choice wrong in a system design interview is a red flag. Interviewers expect you to know the exact tradeoff for each approach.

---

## The Problem with Plain HTTP

In standard HTTP:
1. Client sends a request.
2. Server sends a response.
3. Connection closes. Done.

For a chat app, this means your friend would only receive your message if they refreshed the page. Not great.

The three solutions below are ordered from **simplest and oldest** to **most powerful and modern**.

---

## 1. Long Polling — "The Spinning Wheel Trick"

**Analogy:** You call a restaurant to ask if your table is ready. They say "hold on" and don't hang up. You wait on hold until a table opens, then they tell you. You hang up, and immediately call back to wait again.

### How It Works

```
Client                         Server
  |                               |
  |--- GET /messages ------------>|
  |         (holds connection)    |
  |         (waiting...)          |
  |         (30s timeout?)        |
  |<-- 200 OK {new messages} -----|
  |                               |
  |--- GET /messages ------------>|  (immediately reconnects)
  |         (waiting again...)    |
```

1. Client sends a normal HTTP request.
2. Server **does not respond immediately**. It holds the connection open.
3. When new data arrives, the server sends the response and closes the connection.
4. Client immediately sends another request and waits again.

### Tradeoffs

| Aspect | Detail |
|---|---|
| **Latency** | ~100-500ms (1 full round-trip per message) |
| **Scalability** | Poor — each client holds an open thread on the server |
| **Infrastructure** | Works with any HTTP stack, no special setup |
| **Message direction** | Client → Server AND Server → Client (bidirectional) |
| **Firewall friendly** | ✅ Yes (plain HTTP) |

### When to Use

- Legacy systems where WebSockets are blocked by corporate firewalls.
- Very infrequent updates (e.g., polling for a background job to complete).

### Real World

GitHub's pull request notifications historically used long polling. It's largely been replaced by SSE or WebSockets.

---

## 2. Server-Sent Events (SSE) — "The News Ticker"

**Analogy:** You subscribe to a newspaper. The newspaper company prints new editions and mails them to you automatically. You can't mail letters *back* to them through the same channel.

SSE is a **one-way, persistent HTTP stream** from server to client. Once the connection is open, the server can push unlimited events down to the client. The client cannot send data back through the same stream (it uses separate HTTP requests for that).

### How It Works

```
Client                         Server
  |                               |
  |--- GET /events -------------->|
  |                               |  (connection stays open)
  |<-- data: {"score": 45} -------|
  |<-- data: {"score": 48} -------|
  |<-- data: {"score": 51} -------|
  |         (stream continues...) |
```

The server sends chunks in a special `text/event-stream` format:
```
data: {"user": "alice", "message": "Hello!"}\n\n
data: {"user": "bob", "message": "Hey there!"}\n\n
```

### Browser Support (Native API)

```javascript
const evtSource = new EventSource("/api/notifications");

evtSource.onmessage = (event) => {
  const data = JSON.parse(event.data);
  console.log("New notification:", data);
};
```

### Tradeoffs

| Aspect | Detail |
|---|---|
| **Latency** | ~10-50ms (persistent connection, no round-trip overhead) |
| **Scalability** | Good — single persistent connection per client, but server holds it |
| **Infrastructure** | Works over HTTP/1.1 and HTTP/2. HTTP/2 is vastly more efficient (multiplexing) |
| **Message direction** | Server → Client **only** |
| **Firewall friendly** | ✅ Yes (plain HTTP) |
| **Auto-reconnect** | ✅ Built into the browser EventSource API |

### When to Use

- Live notifications (new email, order status, stock price alerts).
- Live dashboards (metrics, sports scores, election results).
- Streaming AI responses (ChatGPT streams tokens to you via SSE).

### Real World

- **ChatGPT** uses SSE to stream tokens to the browser as they're generated.
- **Twitter/X** uses SSE for live tweet counts.
- **GitHub Actions** streams CI/CD build logs via SSE.

---

## 3. WebSockets — "The Permanent Phone Call"

**Analogy:** You call your friend on the phone. The call connects. Now both of you can speak and listen at any time without taking turns. Either of you can hang up. This is a **full-duplex**, **persistent**, **bidirectional** connection.

WebSockets upgrade an HTTP connection into a persistent TCP channel. Both the client AND the server can send messages to each other at any time.

### How It Works — The Handshake

```
Client                         Server
  |                               |
  |--- HTTP Upgrade Request ------>|
  |    (Upgrade: websocket)        |
  |<-- 101 Switching Protocols ----|
  |   ====== WS Connection ======  |
  |--- "Hello!" ------------------>|  (client sends)
  |<-- "Hi back!" ----------------|  (server sends)
  |--- "Ping" -------------------->|
  |<-- "Pong" ---------------------|
  |   (connection stays open for   |
  |    the lifetime of the session)|
```

### Code Example

**Server (Node.js with `ws` library):**
```javascript
const WebSocket = require('ws');
const wss = new WebSocket.Server({ port: 8080 });

wss.on('connection', (ws) => {
  ws.on('message', (message) => {
    console.log('received:', message);
    // Broadcast to all connected clients
    wss.clients.forEach(client => {
      if (client.readyState === WebSocket.OPEN) {
        client.send(message);
      }
    });
  });
});
```

**Client (Browser):**
```javascript
const ws = new WebSocket('wss://chat.example.com');

ws.onopen = () => ws.send(JSON.stringify({ type: 'join', room: 'general' }));
ws.onmessage = (event) => {
  const msg = JSON.parse(event.data);
  appendMessage(msg);
};
```

### Tradeoffs

| Aspect | Detail |
|---|---|
| **Latency** | ~1-10ms (lowest of all three) |
| **Scalability** | Challenging — each connection is stateful; requires sticky sessions or a pub/sub broker |
| **Infrastructure** | Requires WebSocket-aware load balancers and proxies |
| **Message direction** | Bidirectional (full-duplex) |
| **Firewall friendly** | ⚠️ Sometimes blocked by corporate firewalls (non-standard port) |
| **Auto-reconnect** | ❌ Must implement yourself |

### When to Use

- Chat applications (WhatsApp, Slack, Discord).
- Multiplayer gaming (moves need to travel both ways instantly).
- Collaborative editing (Google Docs cursor positions).
- Financial trading dashboards (real-time bid/ask streams).

### Real World

- **Discord** handles millions of simultaneous WebSocket connections.
- **Slack** uses WebSockets for all message delivery.
- **Robinhood** uses WebSockets for live stock quotes.

---

## Head-to-Head Comparison

| Feature | Long Polling | SSE | WebSockets |
|---|---|---|---|
| Direction | Bidirectional | Server → Client only | Bidirectional |
| Latency | High (~100-500ms) | Low (~10-50ms) | Very Low (~1-10ms) |
| Protocol | HTTP | HTTP | WebSocket (over TCP) |
| Load Balancer | Standard | Standard | Needs sticky sessions |
| Firewall friendly | ✅ Yes | ✅ Yes | ⚠️ Sometimes |
| Browser support | All | All (not IE) | All modern |
| Server load | High (many threads) | Medium | Medium-High (stateful) |
| Auto-reconnect | Manual | ✅ Built-in | ❌ Manual |
| Best for | Background jobs | Notifications, streams | Chat, gaming, trading |

---

## The "Celebrity Problem" — Scaling WebSockets

Imagine 1 million users connected to a chat room. When a celebrity sends a message, you need to fan it out to all 1M WebSocket connections.

**The naive approach fails:** A single server can't hold 1M WebSocket connections (each is a file descriptor + TCP state).

**The production solution:**
```
User A (WS) ─┐
User B (WS) ─┼─► WebSocket Gateway ─► Message Broker (Kafka/Redis Pub/Sub) ─► All WS Gateways ─► All Users
User C (WS) ─┘                                                                 (fan-out)
```

1. Each WebSocket server handles a subset of connections (e.g., 50K per server).
2. When a message arrives, it's published to Redis Pub/Sub or Kafka.
3. Every WebSocket server subscribes and pushes to its own connected clients.

This is exactly how **Discord** and **Slack** scale to millions of concurrent connections.

---

## Interview Decision Framework

> "What protocol should I use for real-time communication?"

```
Is the communication bidirectional?
   ├── YES → WebSockets
   └── NO (server pushes only) →
         Is this a high-frequency stream?
            ├── YES (>1 msg/sec) → SSE
            └── NO → SSE or Long Polling
                      Is WebSocket support blocked?
                         ├── YES → Long Polling
                         └── NO → SSE
```

---

## Common Interview Questions

**Q: How does WhatsApp deliver messages instantly?**
A: WhatsApp uses a persistent WebSocket connection per device. When you send a message, it goes to a server, which pushes it down the recipient's open WebSocket connection.

**Q: Why use SSE over WebSockets for notifications?**
A: SSE is simpler — it works over plain HTTP, load balancers don't need special config, auto-reconnect is built in, and for one-way server-to-client pushes, the full-duplex overhead of WebSockets is unnecessary.

**Q: How does ChatGPT stream its responses?**
A: It uses SSE. The server generates tokens one by one and streams them as `data: {token}` events. The browser renders each token as it arrives, giving the "typing" effect.

**Q: What happens when a WebSocket server crashes?**
A: All clients connected to that server lose their connection. They must reconnect — typically with exponential backoff — and potentially to a different server. The application layer must handle rehydrating the session state.

---

> [!TIP]
> **Quick Interview Cheat Sheet**
> - **Chat / Gaming** → WebSockets (full-duplex)
> - **Notifications / Live Scores / AI streaming** → SSE (one-way, simpler)
> - **Background job polling / legacy** → Long Polling (fallback only)

---

## Applied In

This concept is used by **4 problems** in this repo:

**High-Level Design**

- [Design WhatsApp (Real-Time Messaging)](../../05-hld-problems/02-medium/whatsapp.md)
- [Design a Chat System (Slack)](../../05-hld-problems/03-hard/chat-system.md)
- [Design an LLM Chat System (ChatGPT)](../../05-hld-problems/03-hard/llm-chat-system.md)
- [Design a Ride-Sharing Service (Uber)](../../05-hld-problems/03-hard/ride-sharing.md)

