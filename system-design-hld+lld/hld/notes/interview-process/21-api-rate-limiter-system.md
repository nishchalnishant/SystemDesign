# #21 API rate limiter system

Here’s a complete, time-boxed, 1-hour interview-ready answer for designing an API Rate Limiter System. It follows your system design interview structure, including functional & non-functional requirements, APIs/data model, architecture, deep dive, and trade-offs.

***

## 0 – 5 min — Problem recap, scope & assumptions

<br>

Goal: Design a system that limits the number of API requests a client (user, service, or app) can make in a given time window. The system should prevent abuse, ensure fair usage, and maintain high availability.

<br>

Scope for interview:

* Rate limiting per API key / user / client IP.
* Support distributed microservices and multiple data centers.
* Configurable limits (per second, minute, hour).
* Support burst traffic (short spikes) gracefully.
* Handle high request throughput with low latency.

<br>

Assumptions:

* Peak requests: 1M requests/sec.
* Time windows configurable per client.
* Use cases: public APIs, internal microservices, SaaS platforms.
* Clients are authenticated using API keys.
* System should be horizontally scalable.

***

## 5 – 15 min — Functional & Non-Functional Requirements

<br>

#### Functional Requirements

<br>

Must

1. Limit enforcement: Restrict API usage per user/client key.
2. Configurable rules: Support rate limits per API endpoint, client type, or subscription plan.
3. Distributed support: Works across multiple servers/microservices.
4. Real-time tracking: Track request counts in near real-time.
5. Response headers: Return headers indicating remaining quota and reset time.
6. Burst handling: Allow short bursts beyond the rate limit (optional).

<br>

Should

* Support multiple algorithms: fixed window, sliding window, token bucket, leaky bucket.
* Alerting when limits are exceeded or abused.
* Integration with API gateways or service mesh.

<br>

Nice-to-have

* Tiered limits based on subscription.
* Analytics on usage patterns.
* Auto-throttling or dynamic rate limits based on system load.

***

#### Non-Functional Requirements

* Latency: Limit checks should be <1 ms to avoid API slowdowns.
* Availability: 99.99% uptime; failure must fail open or default safe.
* Scalability: Handle millions of requests/sec; horizontally scalable.
* Consistency: Strong consistency per client is preferable but eventual consistency acceptable for distributed systems.
* Durability: Persist usage counters for audits or billing.
* Fault tolerance: Continue to enforce limits even during node failures.
* Monitoring: Track request counts, exceeded limits, node health.

***

## 15 – 25 min — API / Data Model

<br>

#### APIs

```
# Check if a request can be allowed
is_allowed(api_key: str, endpoint: str) -> bool

# Configure rate limits
set_rate_limit(api_key: str, endpoint: str, limit: int, window: int)

# Get current usage and quota
get_usage(api_key: str, endpoint: str) -> {count, remaining, reset_time}
```

#### Data Models

<br>

Client Rate Limit

```
{
  "api_key": "string",
  "endpoint": "/api/v1/resource",
  "limit": 1000,       # requests
  "window": 60          # seconds
}
```

Usage Counter

```
{
  "api_key": "string",
  "endpoint": "/api/v1/resource",
  "window_start": "timestamp",
  "request_count": int
}
```

***

## 25 – 40 min — High-level architecture & data flow

```
[Client] --> [API Gateway / Proxy] --> [Rate Limiter Service] --> [Redis / In-Memory Store]
                                      |
                                      v
                              [Persistent Storage / DB] (optional for audit)
```

Components

1. API Gateway / Proxy: Receives requests, calls Rate Limiter before forwarding to backend.
2. Rate Limiter Service: Checks if request exceeds limits. Implements algorithms (fixed window, sliding window, token bucket).
3. In-Memory Store: Redis / Memcached to store counters for low-latency access.
4. Persistent Storage: Optional DB to store counters for auditing or billing.
5. Monitoring & Alerting: Track request trends, exceeded limits, system health.

<br>

Data Flow

1. Client sends request → API Gateway.
2. Gateway calls Rate Limiter with API key and endpoint.
3. Rate Limiter checks usage in Redis / memory → allows or rejects request.
4. If rejected → return 429 Too Many Requests with remaining quota/reset info.
5. Periodically sync counters to persistent DB for audits.

***

## 40 – 50 min — Deep dive — algorithms, scaling, reliability

<br>

#### Rate Limiting Algorithms

1. Fixed Window: Simple counter per window; can cause spikes at window boundaries.
2. Sliding Window: Smooths requests across window; more accurate.
3. Token Bucket: Allows bursts; tokens refill at fixed rate.
4. Leaky Bucket: Smooth out traffic; requests processed at constant rate.

<br>

#### Scaling & Distribution

* Sharding: Redis clusters partitioned by API key or client ID.
* Replication: Multi-node Redis cluster for fault tolerance.
* Local caches: Each gateway node keeps local counters for ultra-low latency, periodically sync to central store.
* Consistency: Use atomic increment operations (Redis INCR) to prevent race conditions.

<br>

#### Fault Tolerance

* If Redis unavailable → optionally allow requests (fail open) or deny all (fail closed).
* Use replication and persistence (RDB/AOF) in Redis for durability.

***

## 50 – 55 min — Back-of-the-envelope calculations

<br>

Assumptions

* 1M requests/sec peak, 10M clients.
* Each usage counter: 64 bytes → 10M clients → 640 MB (fits in-memory).
* Latency: Redis INCR \~1 ms; total request processing <2 ms including network.
* Scaling: Horizontally add Redis nodes and gateway instances for linear scaling.

<br>

Storage

* Persist counters daily for audits → 640 MB/day → \~0.25 TB/year (very manageable).

***

## 55 – 58 min — Monitoring & ops

<br>

Monitoring

* Requests per second, rejected requests (429 rate).
* Latency of limit checks.
* Node health and memory usage.

<br>

Operational concerns

* Update rate limits dynamically without downtime.
* Handle Redis failover gracefully.
* Alert on unusual spikes or limit violations.

***

## 58 – 60 min — Trade-offs, evolution & summary

<br>

#### Trade-offs

* Memory vs Accuracy: Approximate counters (e.g., Redis HyperLogLog) reduce memory but less precise.
* Centralized vs Local counters: Local caches reduce latency but require periodic sync.
* Fixed vs Sliding Window: Fixed window is simpler; sliding window smoother but more complex.

<br>

#### Evolution

1. MVP: Fixed window per API key in Redis; fail open on Redis failure.
2. Phase 2: Sliding window or token bucket; distributed across multiple nodes.
3. Phase 3: Dynamic tiered limits, per endpoint limits, analytics dashboards, adaptive throttling.

<br>

#### Summary

* Build a highly available, low-latency API Rate Limiter:
  * API Gateway calls Rate Limiter.
  * Redis or in-memory store maintains counters.
  * Algorithms: fixed window, sliding window, token bucket.
  * Horizontally scalable and fault-tolerant.
  * Tracks usage, prevents abuse, provides quota info, and supports dynamic updates.

***

If you want, I can next create a sequence diagram showing request flow through the rate limiter with counters update, which is very handy to explain in an interview.

<br>

Do you want me to create that diagram next?
