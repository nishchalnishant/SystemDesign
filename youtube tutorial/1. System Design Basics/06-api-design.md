# API Design in System Design Interviews

> **Source**: [API Design in System Design Interviews w/ Meta Staff Engineer](https://www.youtube.com/watch?v=1HOVtQ-_fcE&list=PL5q3E8eRUieVFeK1oLahJ8KONkAxDpqk2&index=6)

---

## Why API Design Matters

- APIs are the **contract** between client and server (and between services)
- Good API design shows you think about **usability, scalability, and backwards compatibility**
- In interviews, defining APIs early frames the rest of your design

---

## REST API Design

### Core Principles
- **Resource-oriented**: URLs represent resources (nouns), not actions (verbs)
- **Stateless**: Each request contains all information needed to process it
- **HTTP Methods** map to CRUD operations

### HTTP Methods

| Method | Action | Idempotent | Safe |
|---|---|---|---|
| `GET` | Read a resource | Yes | Yes |
| `POST` | Create a resource | No | No |
| `PUT` | Replace a resource entirely | Yes | No |
| `PATCH` | Partially update a resource | No* | No |
| `DELETE` | Delete a resource | Yes | No |

### URL Design Best Practices
```
GET    /users              → List all users
GET    /users/{id}         → Get specific user
POST   /users              → Create new user
PUT    /users/{id}         → Update entire user
PATCH  /users/{id}         → Partial update
DELETE /users/{id}         → Delete user

GET    /users/{id}/posts   → Get user's posts (nested resource)
POST   /users/{id}/posts   → Create post for user
```

### Naming Conventions
- Use **plural nouns**: `/users` not `/user`
- Use **kebab-case**: `/user-profiles` not `/userProfiles`
- **Avoid verbs** in URLs: `/users/{id}/activate` → `PATCH /users/{id}` with `{ "status": "active" }`
- Use **nesting** for relationships: `/users/{id}/orders/{orderId}`
- Limit nesting depth to **2-3 levels max**

---

## HTTP Status Codes

| Code | Meaning | When to Use |
|---|---|---|
| `200` | OK | Successful GET, PUT, PATCH |
| `201` | Created | Successful POST that creates a resource |
| `204` | No Content | Successful DELETE |
| `400` | Bad Request | Invalid request body/params |
| `401` | Unauthorized | Missing or invalid authentication |
| `403` | Forbidden | Authenticated but no permission |
| `404` | Not Found | Resource doesn't exist |
| `409` | Conflict | Resource already exists or state conflict |
| `429` | Too Many Requests | Rate limit exceeded |
| `500` | Internal Server Error | Server-side failure |
| `503` | Service Unavailable | Server temporarily down |

---

## Pagination

### Offset-Based Pagination
```
GET /posts?page=2&limit=20
```
- **Pros**: Simple, can jump to any page
- **Cons**: Inconsistent with real-time data (new items shift pages), poor performance on large offsets

### Cursor-Based Pagination
```
GET /posts?cursor=abc123&limit=20
Response: { "data": [...], "next_cursor": "def456" }
```
- **Pros**: Consistent results, good performance regardless of dataset size
- **Cons**: Can't jump to arbitrary page, cursor is opaque
- **Best for**: Social feeds, timelines, real-time data

### Keyset Pagination
```
GET /posts?after_id=12345&limit=20
```
- Uses the last seen ID to fetch the next page
- Very efficient with indexed columns

---

## Rate Limiting

### Why Rate Limit?
- Protect against abuse and DDoS
- Ensure fair usage across clients
- Prevent resource exhaustion

### Common Algorithms
| Algorithm | Description |
|---|---|
| **Token Bucket** | Tokens added at fixed rate; request consumes a token |
| **Sliding Window** | Count requests in a rolling time window |
| **Fixed Window** | Count requests in fixed time intervals |
| **Leaky Bucket** | Requests processed at a constant rate |

### Response Headers
```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 45
X-RateLimit-Reset: 1620000000
```

---

## API Versioning

### URL Versioning (Most Common)
```
/api/v1/users
/api/v2/users
```

### Header Versioning
```
Accept: application/vnd.myapi.v2+json
```

### Query Parameter Versioning
```
/users?version=2
```

### Best Practice
- Use **URL versioning** for simplicity
- Support **at least 2 versions** simultaneously
- Deprecation timeline: announce → sunset → remove

---

## Authentication & Authorization

### Common Auth Methods
| Method | Use Case |
|---|---|
| **API Key** | Server-to-server, simple auth |
| **JWT (JSON Web Token)** | Stateless auth, mobile/web apps |
| **OAuth 2.0** | Third-party authorization (Login with Google) |
| **Session Cookie** | Web applications |

### JWT Structure
```
Header.Payload.Signature

Header:  { "alg": "HS256", "typ": "JWT" }
Payload: { "user_id": "123", "exp": 1620000000 }
Signature: HMACSHA256(base64(header) + "." + base64(payload), secret)
```

---

## GraphQL vs REST vs gRPC

| Feature | REST | GraphQL | gRPC |
|---|---|---|---|
| **Protocol** | HTTP/JSON | HTTP/JSON | HTTP/2 + Protobuf |
| **Data Fetching** | Fixed endpoints | Client specifies fields | Pre-defined service methods |
| **Over-fetching** | Common | Eliminated | N/A |
| **Under-fetching** | Common (multiple calls) | Eliminated | N/A |
| **Performance** | Good | Good | Excellent (binary) |
| **Use Case** | Public APIs | Complex client needs | Internal microservices |
| **Learning Curve** | Low | Medium | High |

---

## API Design Patterns

### 1. Idempotency
- Making the same request multiple times produces the same result
- Critical for **payment and order APIs**
- Use **idempotency keys**: `Idempotency-Key: abc123` in request header
- Server stores the result and returns it for duplicate requests

### 2. Bulk Operations
```
POST /users/bulk
Body: { "users": [ {...}, {...}, {...} ] }
Response: { "results": [ { "status": "created" }, { "status": "error", "message": "..." } ] }
```
- Reduce network round trips
- Return per-item status (partial success)

### 3. Long-Running Operations
```
POST /exports → 202 Accepted, { "task_id": "t123" }
GET  /tasks/t123 → { "status": "processing", "progress": 45 }
GET  /tasks/t123 → { "status": "complete", "result_url": "..." }
```
- Use **polling** or **webhooks** for completion notification

### 4. Webhooks
```
POST /webhooks
Body: { "url": "https://myapp.com/callback", "events": ["order.completed"] }
```
- Server pushes events to client's URL
- Include **signature verification** for security

---

## Error Response Format

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid email format",
    "details": [
      {
        "field": "email",
        "message": "Must be a valid email address"
      }
    ],
    "request_id": "req_abc123"
  }
}
```

---

## Interview Tips

1. **Define APIs early** in the interview — it frames your entire design
2. Be explicit about **request/response shapes** (show JSON examples)
3. Mention **pagination** for list endpoints (cursor-based for feeds)
4. Discuss **rate limiting** to show production awareness
5. Address **backward compatibility** and versioning
6. Use **idempotency keys** for mutation endpoints
7. Consider **partial failure** in bulk operations
