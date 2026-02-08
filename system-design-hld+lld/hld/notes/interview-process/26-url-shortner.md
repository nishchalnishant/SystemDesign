# #26 URL shortner

Here’s a complete, time-boxed, 1-hour interview-ready answer for designing a URL Shortening Service (like TinyURL, Bitly). It follows your system design interview structure, including functional & non-functional requirements, APIs/data model, architecture, deep dive, and trade-offs.

***

## 0 – 5 min — Problem recap, scope & assumptions

<br>

Goal: Design a service that converts long URLs into short, unique URLs and allows users to access the original URLs using the short links.

<br>

Scope for interview:

* Shorten long URLs to compact URLs.
* Redirect short URLs to original URLs.
* Handle high read traffic for redirects.
* Optional: analytics (click counts, geolocation, referrers).

<br>

Assumptions:

* Millions of URLs, billions of redirects.
* Short URLs should be unique and collision-free.
* Redirect latency <50 ms.
* Service supports web and API access.
* Optional: expiration for short URLs.

***

## 5 – 15 min — Functional & Non-Functional Requirements

<br>

#### Functional Requirements

<br>

Must

1. URL shortening: Generate a unique short URL for a given long URL.
2. Redirect: Accessing the short URL redirects to the original URL.
3. Idempotency: Same long URL may generate same or different short URLs depending on design.
4. Analytics (optional): Track clicks, referrers, geolocation.

<br>

Should

* Custom short URLs provided by users.
* Expiration and deletion of URLs.
* Rate limiting per user/API key.

<br>

Nice-to-have

* QR code generation.
* Support for vanity URLs.
* A/B testing for short URLs (marketing).

***

#### Non-Functional Requirements

* Latency: Redirect should be very fast (<50 ms).
* Availability: 99.99% uptime; high availability is critical.
* Scalability: Support billions of URLs and redirects.
* Durability: Persistent storage of URL mappings.
* Consistency: Strong consistency for mapping short → long URL.
* Monitoring: Track traffic, errors, latency, and system health.

***

## 15 – 25 min — API / Data Model

<br>

#### APIs

```
# Create short URL
shorten_url(long_url: str, custom_alias: Optional[str]=None, expiry: Optional[datetime]=None) -> short_url

# Redirect short URL
get_long_url(short_url: str) -> long_url

# Optional analytics
get_clicks(short_url: str) -> int
```

#### Data Models

<br>

URLMapping

```
{
  "short_url": "string",
  "long_url": "string",
  "created_at": "datetime",
  "expiry": "datetime|null",
  "click_count": int
}
```

User (optional)

```
{
  "user_id": "string",
  "api_key": "string",
  "urls_created": ["short_url1", "short_url2"]
}
```

***

## 25 – 40 min — High-level architecture & data flow

```
[Client/API] --> [API Gateway] --> [URL Shortening Service] --> [DB / Key-Value Store]
                                         |
                                         v
                                   [Cache Layer (Redis)]
                                         |
                                         v
                                  [Analytics Service (Optional)]
```

Components

1. URL Shortening Service: Generates unique short codes and stores mapping.
2. Database / Key-Value Store: Stores short URL → long URL mappings (Cassandra, DynamoDB, or MySQL).
3. Cache Layer: Hot short URLs in Redis for fast redirects.
4. Analytics Service: Counts clicks, stores metrics asynchronously.
5. API Gateway: Handles user requests, rate-limiting, and routing.

<br>

Data Flow

1. User submits long URL → Shortening Service generates short URL → stores in DB → returns short URL.
2. User accesses short URL → Service checks cache → redirect to long URL. If cache miss → fetch from DB → redirect.
3. Analytics logged asynchronously via message queue.

***

## 40 – 50 min — Deep dive — short URL generation & scaling

<br>

#### Short URL Generation

* Base62 encoding: Convert auto-increment ID to short alphanumeric string.
* Hash-based: Hash long URL → use first N characters. Handle collisions via additional bits.
* Custom alias: Use provided alias, check uniqueness.

<br>

#### Scaling

* Sharding: Split URLs by hash or prefix to multiple DB nodes.
* Caching: Hot URLs in Redis for low-latency redirects.
* Load Balancer: Distribute API traffic across multiple instances.
* CDN (optional): Cache popular short URLs globally for faster access.

<br>

#### Fault Tolerance

* Replicate DB across regions for durability.
* Use retries for analytics events.
* Cache replication for high availability.

***

## 50 – 55 min — Back-of-the-envelope calculations

<br>

Assumptions

* 1B URLs, each short URL mapping \~200 bytes → \~200 GB storage.
* Peak redirects: 10M/sec → caching required.
* Writes (shorten URL requests): 100K/sec → NoSQL DB with high write throughput.
* Cache size: store top 100M hot URLs → \~20 GB Redis cluster.

***

## 55 – 58 min — Monitoring & ops

<br>

Monitoring

* Redirect latency, cache hit/miss.
* URL creation rate.
* Analytics processing latency.
* Error rates and system health.

<br>

Operational concerns

* Handle high read-to-write ratio efficiently.
* Prevent collisions in short URL generation.
* Clean-up expired URLs.

***

## 58 – 60 min — Trade-offs, evolution & summary

<br>

#### Trade-offs

* Base62 vs Hash: Base62 simple, sequential; hash reduces predictability but requires collision handling.
* Cache vs DB: Cache reduces latency for redirects; DB ensures durability.
* SQL vs NoSQL: NoSQL preferred for high write throughput; SQL may be simpler for small scale.

<br>

#### Evolution

1. MVP: Generate short URLs, redirect, basic DB storage.
2. Phase 2: Custom aliases, cache popular URLs, analytics service.
3. Phase 3: Global scaling, CDN caching, expiration, rate-limiting, vanity URLs.

<br>

#### Summary

* System generates unique short URLs and performs fast redirects.
* Uses DB + cache for scalability and low latency.
* Event-driven analytics for clicks.
* Horizontally scalable, fault-tolerant, and monitorable system.

***

If you want, I can next create a sequence diagram showing URL creation, caching, redirect, and analytics logging, which is very useful to explain in interviews.

<br>

Do you want me to create that diagram next?
