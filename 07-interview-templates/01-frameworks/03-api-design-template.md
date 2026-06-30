> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** A structured template for designing APIs during an interview. It guides you from protocol choice to endpoint definition to advanced topics like versioning and pagination.
>
> **Key concepts:**
> - Protocol Choice: Justify REST (CRUD), gRPC (internal microservices, low latency), or GraphQL (mobile, avoiding over-fetching).
> - Anatomy of an Endpoint: `METHOD /v1/resource/identifier`. Always specify headers, request payload, and response format.
> - Pagination: Cursor-based (better for infinite scroll, changing datasets) vs. Offset-based (better for jumping to page $N$, but slow on large offsets).
> - Versioning: Path versioning (`/v1/users`) vs. Header versioning (`Accept-Version: v1`).
> - Idempotency: Essential for payment/transaction APIs (using `Idempotency-Key` headers).
>
> **Key takeaway:** Don't just list endpoints. The interviewer wants to see you consider the *design* of the API—how you handle pagination, backwards compatibility (versioning), and network failures (idempotency).

---
module: 07-interview-templates
status: unread
tags: [07-interview-templates, system-design, interview-templates, api-design]
---
# API Design Template (Interview Reference)

> **Use this when the interviewer asks you to design APIs, asks about API versioning strategy, or when Phase 3 of an HLD interview needs more structure.**

---

## Quick-Reference Checklist

When designing APIs in an interview, cover these in order:
1. Protocol choice (REST vs gRPC vs GraphQL) — justify based on client type
2. Core endpoints (1 per functional requirement, no extras)
3. Request/response shape for critical paths
4. Pagination strategy
5. Idempotency (for writes)
6. Auth mechanism
7. Versioning strategy
8. Error response contract

---

## Protocol Selection

```
REST        → external/public APIs; third-party developers; browser clients; standard tooling (OpenAPI, Postman)
gRPC        → internal service-to-service; both ends controlled; streaming needed; latency-critical
GraphQL     → mobile BFF (back-end for front-end); client controls what fields to fetch; avoids over/under-fetching
WebSocket   → bidirectional real-time: chat, live feeds, collaborative editing
SSE         → server-push only: live score updates, notification feeds (simpler than WebSocket when client never sends)
```

**One-line interview justification template:**
> "The public API is REST — universally understood, browser-friendly, good OpenAPI tooling. Internal services use gRPC — both ends owned, Protobuf is faster than JSON, and we use streaming for live updates."

---

## Core Endpoint Design

### Resource naming
```
Collection:  GET  /api/v1/users              (list)
             POST /api/v1/users              (create)
Instance:    GET  /api/v1/users/{user_id}    (read)
             PUT  /api/v1/users/{user_id}    (replace)
             PATCH /api/v1/users/{user_id}   (partial update)
             DELETE /api/v1/users/{user_id}  (delete)

Nested:      GET  /api/v1/users/{user_id}/orders
Sub-actions: POST /api/v1/orders/{order_id}/cancel   ← use nouns in path, action as final segment
```

### HTTP method semantics
| Method | Idempotent | Safe | Use for |
|--------|-----------|------|---------|
| GET | Yes | Yes | Read |
| PUT | Yes | No | Full replace |
| PATCH | No | No | Partial update |
| POST | No | No | Create, non-idempotent actions |
| DELETE | Yes | No | Delete |

---

## Request/Response Shapes

### Standard envelope (optional — only if interviewer asks)
```json
{
  "data": { ... },
  "meta": { "request_id": "abc123", "timestamp": "2026-06-19T10:00:00Z" },
  "error": null
}
```

### Error response — always consistent
```json
{
  "error": {
    "code": "ROOM_NOT_AVAILABLE",      // machine-readable, stable across versions
    "message": "Room 101 is booked for the requested dates",  // human-readable
    "details": { "conflicting_dates": ["2026-07-01", "2026-07-02"] },
    "request_id": "abc123"             // for log correlation
  }
}
```

**HTTP status codes to know cold:**
```
200 OK           — success with body
201 Created      — POST created a resource; include Location header
202 Accepted     — async operation started; poll or webhook
204 No Content   — success, no body (DELETE, some PUTs)
400 Bad Request  — client input error; include error.code
401 Unauthorized — not authenticated
403 Forbidden    — authenticated but not authorized
404 Not Found    — resource doesn't exist
409 Conflict     — state conflict (double-booking, version mismatch)
422 Unprocessable — valid syntax but semantic error (e.g., check_out before check_in)
429 Too Many Requests — rate limited; include Retry-After header
500 Internal Server Error — never expose stack traces
503 Service Unavailable  — circuit breaker open, shed load gracefully
```

---

## Pagination

### Offset pagination (simple, avoid for large datasets)
```
GET /api/v1/posts?offset=100&limit=20
→ { data: [...], total: 10000, offset: 100, limit: 20 }
```
**Problem:** `OFFSET 100000 LIMIT 20` in SQL scans 100,020 rows. Inconsistent on inserts during pagination.

### Cursor-based pagination (production choice)
```
GET /api/v1/posts?cursor=eyJpZCI6MTAwfQ&limit=20
→ { data: [...], next_cursor: "eyJpZCI6MTIwfQ", has_more: true }

SQL: WHERE id > $cursor_id ORDER BY id ASC LIMIT 20
     ← cursor is opaque to client (base64 of {"id": 100})
```
**Advantages:** O(1) DB cost regardless of page depth; stable under concurrent inserts.
**Limitation:** Can't jump to page 500; no total count (intentional — total count is expensive).

### Time-based cursor (for feeds)
```
GET /api/v1/feed?before=2026-06-19T10:00:00Z&limit=20
→ { data: [...], oldest_timestamp: "2026-06-19T09:55:00Z", has_more: true }
```
Use when sorting by `created_at`. Cursor = timestamp of last item returned.

**Interview answer for "how do you paginate?":**
> "Cursor-based pagination — the cursor encodes the last-seen ID (or timestamp). Each page fetches `WHERE id > cursor LIMIT N`. This is O(1) regardless of page depth and is stable under concurrent inserts. Offset pagination degrades to O(N) for deep pages."

---

## Idempotency

Idempotency means: calling the same operation multiple times has the same effect as calling it once. Critical for: payments, bookings, email sends, any POST that creates state.

### Idempotency key pattern
```
POST /api/v1/payments
Headers:
  Idempotency-Key: client-generated-uuid-v4     ← client generates once, retries same key

Server logic:
  1. Look up idempotency_key in Redis (or DB)
  2. If found and status='completed': return cached response (no duplicate charge)
  3. If found and status='in_progress': return 409 or 202 (wait)
  4. If not found: process, store result keyed by idempotency_key (TTL: 24h)
```

```sql
CREATE TABLE idempotency_keys (
  key           TEXT PRIMARY KEY,
  response_code INT,
  response_body JSONB,
  created_at    TIMESTAMP,
  expires_at    TIMESTAMP
);
```

**When to require idempotency keys:**
- Any POST that charges money
- Any POST that sends a message (email, SMS, push)
- Any POST that creates a non-reversible resource

**Interview answer:**
> "For the payment endpoint, clients send an `Idempotency-Key` UUID with each request. The server caches the response for 24 hours. If a network timeout causes a retry, the second request hits the cache and returns the original response — no duplicate charge."

---

## API Versioning

### Approaches

**URL versioning (recommended for public APIs):**
```
/api/v1/users
/api/v2/users    ← new version; v1 still works
```
- Explicit, discoverable, easy to route
- Clients must update URLs when migrating

**Header versioning:**
```
GET /api/users
Accept: application/vnd.myapi.v2+json
```
- Clean URLs; clients control version without URL change
- Harder to test in browser; harder to cache (Vary header required)

**Query param:**
```
GET /api/users?version=2
```
- Simple but pollutes query string; rarely used in production

### Breaking vs non-breaking changes

**Non-breaking (can deploy without client changes):**
- Adding optional fields to response
- Adding optional request fields with defaults
- Adding new endpoints
- Expanding enum values (if client ignores unknown values)
- Relaxing validation rules

**Breaking (requires new API version):**
- Removing fields from response
- Renaming fields
- Changing field types (string → integer)
- Changing endpoint paths or HTTP methods
- Adding required request fields
- Restricting validation rules

### Deprecation strategy
```
1. Release /v2 with new design
2. Add deprecation header to /v1 responses:
   Deprecation: true
   Sunset: 2027-01-01        ← RFC 8594; date after which v1 is removed
   Link: <https://api.example.com/v2/migration-guide>; rel="successor-version"
3. Track v1 usage in metrics; contact heavy users before sunset
4. Remove v1 6–12 months after v2 GA
```

---

## gRPC API Design

### Proto file conventions
```protobuf
syntax = "proto3";
package bookings.v1;

service BookingService {
  rpc CreateBooking(CreateBookingRequest) returns (CreateBookingResponse);
  rpc GetBooking(GetBookingRequest) returns (Booking);
  rpc ListBookings(ListBookingsRequest) returns (stream Booking);  // server streaming
  rpc CancelBooking(CancelBookingRequest) returns (CancelBookingResponse);
}

message CreateBookingRequest {
  string room_id = 1;
  string user_id = 2;
  google.type.Date check_in = 3;
  google.type.Date check_out = 4;
  string idempotency_key = 5;   // include in every mutating RPC
}
```

### gRPC backward compatibility
- **Never remove or rename fields** — use field numbers; old clients ignore unknown fields
- **Never change field types** — this is a breaking change even if names match
- **Deprecate by adding new fields**, not changing existing ones
- **Versioning**: use package namespace (`bookings.v1`, `bookings.v2`); both coexist in registry

### gRPC error codes (map to HTTP)
```
OK (200), INVALID_ARGUMENT (400), NOT_FOUND (404),
ALREADY_EXISTS (409), PERMISSION_DENIED (403),
UNAUTHENTICATED (401), RESOURCE_EXHAUSTED (429),
INTERNAL (500), UNAVAILABLE (503)
```

---

## Auth in API Design

### Patterns

**JWT (stateless):**
```
POST /auth/token → { access_token, expires_in: 3600, refresh_token }
GET /api/v1/users  Authorization: Bearer <access_token>

Validate: verify signature locally (no DB lookup) → fast, scalable
Problem: can't revoke before expiry → keep TTL short (15 min); use refresh tokens
```

**API Key (service-to-service):**
```
GET /api/v1/data  X-API-Key: sk_live_abc123

Store hashed key in DB (bcrypt hash); compare hash on each request
Scope keys to specific endpoints/permissions
```

**OAuth 2.0 flows by use case:**
```
User login (web app)         → Authorization Code + PKCE
Machine-to-machine           → Client Credentials
Mobile app                   → Authorization Code + PKCE (no client secret)
Legacy server app            → Resource Owner Password (avoid; use Auth Code)
```

**Interview answer for "how do you handle auth?":**
> "API Gateway validates JWTs on every request — signature check only, no DB roundtrip. Tokens expire in 15 minutes. Refresh tokens (24h TTL, stored in HttpOnly cookie) get new access tokens. For service-to-service calls within the cluster, we use mTLS + service identity — no token needed."

---

## Rate Limiting Headers

```
X-RateLimit-Limit: 1000         ← requests allowed per window
X-RateLimit-Remaining: 942      ← requests left in current window
X-RateLimit-Reset: 1750000000   ← Unix timestamp when window resets
Retry-After: 60                 ← seconds to wait (on 429 response only)
```

---

## Interview Angles

**"How do you version your API?"**
> "URL versioning for public APIs — /v1, /v2 coexist. Non-breaking changes (new optional fields, new endpoints) deploy without version bump. Breaking changes get a new version. We add a `Sunset` header 6 months before removing an old version and track usage metrics to contact heavy consumers."

**"How do you handle retries safely?"**
> "All mutating endpoints accept an `Idempotency-Key` header. Server caches responses by key for 24 hours. Clients generate a UUID once and include it on every retry attempt. Safe to retry any POST on network timeout."

**"REST vs gRPC — when do you choose?"**
> "REST for anything with external consumers — browser clients, third-party integrations — where discoverability and standard tooling matter. gRPC for internal service mesh — Protobuf is 5–10× smaller than JSON, streaming is built-in, and we get type safety at compile time. GraphQL for mobile BFFs where different screen sizes need different field subsets."

**"How do you paginate 10M records?"**
> "Cursor-based, never offset. The cursor encodes the last-seen ID. Query is `WHERE id > :cursor LIMIT N` — O(log N) index scan regardless of page depth. Offset pagination does a full scan to skip N rows, which kills DB performance at page 10,000."

---

## See Also

- `07-interview-templates/hld-template.md` — Phase 3 (API design) in the 45-min framework
- `07-interview-templates/trade-offs-cheat-sheet.md` — REST vs gRPC vs GraphQL comparison table
- `02-building-blocks/rate-limiting.md` — token bucket, sliding window implementations
- `07-interview-templates/security-compliance-checklist.md` — auth flows, OWASP
