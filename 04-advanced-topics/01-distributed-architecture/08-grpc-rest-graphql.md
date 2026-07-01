---
module: 04-advanced-topics
topic: distributed-architecture
status: unread
tags: [04-advanced-topics, distributed-systems, grpc, rest, graphql, api-design, protocols]
---
# gRPC vs REST vs GraphQL

Three API paradigms, each with distinct use cases. Picking the wrong one for your system adds latency, increases payload size, over-fetches data, or creates tight coupling between services.

---

## REST (Representational State Transfer)

The dominant API paradigm on the web. Built on HTTP/1.1 (or HTTP/2), uses standard verbs (GET, POST, PUT, DELETE, PATCH), and models resources as URLs.

### Core Principles

- **Stateless**: Each request carries all information needed to process it; server holds no client state
- **Resource-oriented**: URLs identify resources (`/users/123`, `/orders/456`), not actions
- **Uniform interface**: HTTP verbs define operations; HTTP status codes communicate results
- **Cacheable**: GET responses can be cached by clients, proxies, CDNs

### Example

```http
# Create an order
POST /api/v1/orders
Content-Type: application/json

{
  "user_id": "u-123",
  "items": [
    { "product_id": "p-456", "quantity": 2 }
  ],
  "delivery_address_id": "addr-789"
}

Response 201 Created:
{
  "order_id": "o-111",
  "status": "pending",
  "total": 59.98,
  "estimated_delivery": "2026-07-03"
}

# Fetch the order
GET /api/v1/orders/o-111
Response 200 OK: { "order_id": "o-111", "status": "shipped", ... }

# Update status
PATCH /api/v1/orders/o-111
{ "status": "cancelled" }
```

### REST Characteristics

| Property | Value |
|----------|-------|
| **Transport** | HTTP/1.1 or HTTP/2 |
| **Serialization** | JSON (text, human-readable), XML (rare), occasionally Protobuf |
| **Versioning** | URL path (`/v1/`, `/v2/`) or `Accept` header |
| **Schema** | Optional (OpenAPI/Swagger) — often unenforced at runtime |
| **Tooling** | Excellent: curl, Postman, browser, every language has HTTP libraries |
| **Caching** | Native HTTP caching (ETag, Cache-Control) |
| **Streaming** | Not native; workarounds: SSE, long-polling, chunked transfer |

### REST Pitfalls

**Over-fetching**: `GET /users/123` returns name, email, avatar, preferences, address, subscription_plan — but the client only needs name and avatar. The excess data wastes bandwidth.

**Under-fetching (N+1)**: Display a feed of 20 posts with author names. Requires `GET /posts` (1 request) + `GET /users/{user_id}` for each author (20 requests). Total: 21 HTTP round trips.

**Versioning drift**: `/v1/orders` and `/v2/orders` diverge over time. Clients stay on v1 forever. Maintaining multiple versions is expensive.

---

## gRPC (Google Remote Procedure Call)

Protocol Buffers (Protobuf) over HTTP/2. Designed for service-to-service communication where performance, type safety, and bidirectional streaming matter.

### Schema First: .proto File

```protobuf
syntax = "proto3";

package orders;

service OrderService {
  rpc CreateOrder (CreateOrderRequest) returns (CreateOrderResponse);
  rpc GetOrder (GetOrderRequest) returns (Order);
  rpc StreamOrderUpdates (GetOrderRequest) returns (stream OrderUpdate);  // server streaming
  rpc BatchGetOrders (stream GetOrderRequest) returns (stream Order);     // bidirectional
}

message CreateOrderRequest {
  string user_id = 1;
  repeated OrderItem items = 2;
  string delivery_address_id = 3;
}

message CreateOrderResponse {
  string order_id = 1;
  string status = 2;
  double total = 3;
}

message Order {
  string order_id = 1;
  string user_id = 2;
  string status = 3;
  double total = 4;
  int64 created_at = 5;
}

message OrderUpdate {
  string order_id = 1;
  string new_status = 2;
  int64 timestamp = 3;
}
```

### Generated Client Usage (Java)

```java
// Protobuf compiler generates this client code from the .proto file
ManagedChannel channel = ManagedChannelBuilder
    .forAddress("order-service", 9090)
    .usePlaintext()
    .build();

OrderServiceGrpc.OrderServiceBlockingStub stub =
    OrderServiceGrpc.newBlockingStub(channel);

CreateOrderRequest request = CreateOrderRequest.newBuilder()
    .setUserId("u-123")
    .addItems(OrderItem.newBuilder().setProductId("p-456").setQuantity(2))
    .setDeliveryAddressId("addr-789")
    .build();

CreateOrderResponse response = stub.createOrder(request);
System.out.println("Order created: " + response.getOrderId());

// Server streaming: receive real-time updates
Iterator<OrderUpdate> updates = stub.streamOrderUpdates(
    GetOrderRequest.newBuilder().setOrderId(response.getOrderId()).build()
);
while (updates.hasNext()) {
    OrderUpdate update = updates.next();
    System.out.println("Status: " + update.getNewStatus());
}
```

### gRPC Characteristics

| Property | Value |
|----------|-------|
| **Transport** | HTTP/2 (multiplexed, binary framing, header compression) |
| **Serialization** | Protobuf (binary, ~3–10× smaller than JSON, ~5–10× faster to serialize) |
| **Schema** | Required (`.proto` file); compile-time type safety |
| **Versioning** | Field numbers in Protobuf (backward/forward compatible by convention) |
| **Tooling** | Good for server-side; limited native browser support (needs gRPC-Web proxy) |
| **Streaming** | Native: client streaming, server streaming, bidirectional streaming |
| **Caching** | Not native (HTTP/2 doesn't reuse standard HTTP caching headers for RPCs) |

### gRPC Advantages

- **Binary efficiency**: Protobuf is compact and fast. For high-QPS internal calls, this reduces CPU and bandwidth meaningfully.
- **Type safety**: The `.proto` file is a contract. Mismatched field types are caught at compile time, not at runtime.
- **Bidirectional streaming**: Built on HTTP/2 multiplexing. One TCP connection supports concurrent streams in both directions — ideal for real-time updates, chat, telemetry.
- **Code generation**: `protoc` generates client and server stubs in every major language. The client calls look like local function calls.
- **Built-in deadlines**: gRPC propagates deadlines across service boundaries. If the frontend sets a 500ms deadline, every downstream call inherits it and times out accordingly.

### gRPC Limitations

- **Not browser-native**: Browsers can't send raw HTTP/2 frames directly. gRPC-Web is a workaround (adds an Envoy proxy layer) but adds operational complexity.
- **Proto schema required**: Every change requires recompiling and deploying updated stubs to all clients and servers. Loose coupling is harder.
- **Human-unreadable payloads**: Protobuf is binary. You can't `curl` a gRPC endpoint and read the response; you need grpcurl or BloomRPC.

---

## GraphQL

A query language for APIs. The client specifies exactly what data it needs; the server returns exactly that.

### Schema (SDL)

```graphql
type Query {
  order(id: ID!): Order
  user(id: ID!): User
  orders(userId: ID!, status: OrderStatus): [Order!]!
}

type Mutation {
  createOrder(input: CreateOrderInput!): CreateOrderPayload!
  cancelOrder(orderId: ID!): CancelOrderPayload!
}

type Subscription {
  orderUpdates(orderId: ID!): OrderUpdate!
}

type Order {
  id: ID!
  status: OrderStatus!
  total: Float!
  items: [OrderItem!]!
  user: User!           # resolver fetches User by order.userId
  createdAt: DateTime!
}

type User {
  id: ID!
  name: String!
  email: String!
  orders: [Order!]!
}

enum OrderStatus { PENDING PROCESSING SHIPPED DELIVERED CANCELLED }
```

### Client Queries

```graphql
# Fetch only what the feed component needs — no over-fetching
query FeedQuery {
  orders(userId: "u-123", status: PENDING) {
    id
    status
    total
    items {
      productName
      quantity
    }
  }
}

# A different component needs different fields — same endpoint, different query
query OrderDetailQuery($orderId: ID!) {
  order(id: $orderId) {
    id
    status
    total
    user {
      name
      email
    }
    items {
      productId
      productName
      quantity
      price
    }
    createdAt
  }
}

# Real-time subscription
subscription {
  orderUpdates(orderId: "o-111") {
    newStatus
    timestamp
  }
}
```

### GraphQL Characteristics

| Property | Value |
|----------|-------|
| **Transport** | HTTP/1.1 or HTTP/2 (single endpoint: `POST /graphql`) |
| **Serialization** | JSON |
| **Schema** | Required (SDL); introspection allows clients to discover schema |
| **Versioning** | Usually none — add new fields, deprecate old ones; one endpoint forever |
| **Tooling** | Excellent: GraphiQL, Apollo Studio, Relay |
| **Caching** | Harder (queries are POST bodies; standard HTTP caching doesn't apply by default) |
| **Real-time** | Via subscriptions (usually WebSocket) |

### GraphQL Advantages

- **Eliminates over-fetching**: Mobile app requesting a user profile asks for only name + avatar; web app asks for name + avatar + preferences + address. Same endpoint, different queries.
- **Eliminates N+1 at the protocol level**: One query can fetch posts + all their authors in a single request. No 21 HTTP round trips.
- **No versioning**: Add `newField` to the schema. Old clients ignore it. Deprecate `oldField` without removing it. One endpoint, backward-compatible by convention.
- **Schema introspection**: `__schema` query returns the full type system. Tools auto-generate typed clients.

### GraphQL Pitfalls

**N+1 at the resolver level**: `orders { user { name } }` calls the `User` resolver once per order. 100 orders = 100 DB queries for user names. Fix: DataLoader (batch-by-key: collect all user IDs, one `WHERE id IN (...)` query, return results by key).

```javascript
// Without DataLoader: N DB queries
Order.user = async (order) => db.query("SELECT * FROM users WHERE id = ?", [order.userId])

// With DataLoader: 1 batched DB query
const userLoader = new DataLoader(async (userIds) => {
    const users = await db.query("SELECT * FROM users WHERE id = ANY(?)", [userIds])
    return userIds.map(id => users.find(u => u.id === id))
})
Order.user = async (order) => userLoader.load(order.userId)
```

**Arbitrary depth queries (DoS risk)**: A malicious client can query `user { orders { user { orders { user { ... } } } } }` to unlimited depth, causing the server to join tables recursively until it dies. Mitigate with query depth limits and complexity analysis.

```javascript
import depthLimit from 'graphql-depth-limit'
const server = new ApolloServer({
    validationRules: [depthLimit(7)],  // max 7 levels of nesting
})
```

**Caching complexity**: HTTP GET caching works naturally for REST (`GET /orders/123` has a cache key). For GraphQL, all queries are `POST /graphql` with different bodies — HTTP caching doesn't apply. Use persisted queries (hash the query, cache by hash) or application-level caching (Redis, Apollo Client cache).

---

## Comparison Matrix

| | REST | gRPC | GraphQL |
|---|---|---|---|
| **Primary use case** | Public APIs, web backends | Service-to-service (microservices) | Flexible client queries (mobile, web) |
| **Transport** | HTTP/1.1 or HTTP/2 | HTTP/2 only | HTTP/1.1 or HTTP/2 |
| **Payload format** | JSON (human-readable) | Protobuf (binary, compact) | JSON |
| **Schema** | Optional (OpenAPI) | Required (.proto) | Required (SDL) |
| **Type safety** | None at runtime (unless JSON Schema) | Compile-time (generated stubs) | Runtime (via schema validation) |
| **Streaming** | SSE / long-poll (not native) | Native (4 modes) | Subscriptions (WebSocket) |
| **Browser support** | Native | Needs gRPC-Web proxy | Native |
| **Caching** | Native HTTP caching | Not native | Complex; needs CDN-level solutions |
| **Over-fetching** | Yes | Yes | No |
| **N+1 at protocol** | Yes | Yes | No (one query = one request) |
| **N+1 at server** | No | No | Yes (mitigate with DataLoader) |
| **Versioning** | URL versioning | Field numbers (mostly compatible) | Additive; no breaking versions |

---

## When to Use What

### Use REST when:
- Building a public API (third-party developers, unknown clients)
- Team expects to use `curl`, Postman, or standard HTTP tooling for debugging
- Responses are naturally cacheable by CDN or browser (content pages, product listings)
- Simple CRUD operations with clear resource boundaries

### Use gRPC when:
- Service-to-service (internal microservice calls in a private network)
- Latency and throughput are critical (high-QPS internal calls, data pipelines)
- Need bidirectional streaming (real-time telemetry, chat between services, video frames)
- Type safety between services matters (avoids runtime field mismatches)
- Polyglot: services in Go, Java, Python — Protobuf generates idiomatic code for all

### Use GraphQL when:
- Multiple clients (mobile, web, desktop) need different subsets of the same data
- The data model is deeply hierarchical and relational (social graph, product catalog with variants)
- Frontend teams want to iterate quickly without backend changes (add a field to the query, not to the REST endpoint)
- Building a developer platform where third parties write queries (GitHub API, Shopify API)

---

## Common Hybrid Architectures

**gRPC internally + REST externally** (most common at scale):
```
Mobile/Web ─── REST API Gateway ──▶ BFF (Backend for Frontend) ─── gRPC ──▶ Microservices
```
Internal services use gRPC for performance. The BFF or API Gateway translates to REST/JSON for browsers.

**GraphQL at the edge** (Meta, GitHub, Shopify model):
```
Mobile/Web ─── GraphQL API ──▶ GraphQL Gateway ─── REST/gRPC ──▶ Microservices
```
The GraphQL layer is a thin aggregation/translation layer. Downstream services remain REST or gRPC.

**REST + gRPC streaming**:
```
Client ─── REST for CRUD ──▶ Service
Client ─── gRPC (streaming) for real-time data ──▶ Service
```
Different protocols for different access patterns to the same service.

---

## Interview Questions to Practice

1. **"Why would you choose gRPC over REST for internal microservice communication?"**
   *Protobuf binary serialization is 3–10× smaller and 5–10× faster to parse than JSON. HTTP/2 multiplexing eliminates head-of-line blocking — multiple concurrent RPC streams on one TCP connection. Native bidirectional streaming enables real-time patterns impossible in REST. Generated client stubs provide compile-time type safety, catching field mismatches before deployment. The trade-off: not browser-native, requires Proto schema discipline, and binary payloads are harder to debug.*

2. **"A mobile app team complains that REST APIs return too much data and require too many calls. What would you recommend?"**
   *GraphQL solves both problems directly. Over-fetching disappears — the mobile query specifies exactly the fields it needs. Under-fetching disappears — a single GraphQL query can fetch an order + its items + the user's name in one HTTP request, replacing 3+ REST calls. The main implementation concern is the N+1 problem at the resolver level: use DataLoader to batch database queries by key. GraphQL's introspection also lets the mobile team discover available fields without reading documentation.*

3. **"What is Protobuf and why does gRPC use it instead of JSON?"**
   *Protocol Buffers is a binary serialization format. Each field is identified by a number (not a name string), so field names aren't transmitted — the schema is compiled into both client and server. This makes payloads 3–10× smaller than JSON (no field name strings, efficient varint encoding for integers). Parsing is faster: binary parsing beats JSON string tokenization. It's also strongly typed — the compiler rejects type mismatches. The trade-off: not human-readable; need `protoc` compiler; changing field numbers is a breaking change.*

4. **"How would you implement versioning in GraphQL vs REST?"**
   *REST: increment the URL path (`/v1/`, `/v2/`). Old versions must be maintained until all clients migrate. Multiple codebaths diverge over time — high maintenance cost. GraphQL: no versioning needed. Add new fields (old clients ignore them). Deprecate old fields with `@deprecated(reason: "use newField instead")` without removing them. The schema's additive nature means one endpoint remains valid indefinitely. The risk: deprecated fields accumulate; use schema linting tools to track and eventually remove fields with no active clients.*
