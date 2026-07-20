# API Styles: RPC, gRPC, GraphQL, REST & API Gateway

> **Source**: Videos #16, #17, #18, #29, #37, #51, #63, #83, #86, #91, #94 from the playlist
> - What is RPC? gRPC Introduction
> - What Is GraphQL? REST vs. GraphQL
> - What is API Gateway?
> - Top 6 Most Popular API Architecture Styles
> - Top 7 Ways to 10x Your API Performance
> - Top 9 Most Popular Types of API Testing
> - Top 9 Most Popular API Protocols
> - API Pagination
> - API Vs SDK!

---

## Top API Architecture Styles

### 1. REST (Representational State Transfer)
- Resource-based URLs, HTTP methods
- **Best for**: Public APIs, CRUD operations
- **Format**: JSON over HTTP

### 2. GraphQL
- Single endpoint, client specifies exact data needed
- **Best for**: Complex frontends needing flexible queries
- **Eliminates**: Over-fetching and under-fetching

### 3. gRPC (Google Remote Procedure Call)
- Binary protocol (Protocol Buffers) over HTTP/2
- **Best for**: Internal microservice communication
- **Features**: Streaming, strongly typed, code generation

### 4. WebSocket
- Persistent bidirectional connection
- **Best for**: Real-time apps (chat, gaming, live updates)

### 5. Webhook
- Server pushes data to client's URL when events occur
- **Best for**: Event notifications, integrations

### 6. SOAP
- XML-based, strict standards (WS-Security, WSDL)
- **Best for**: Enterprise, banking, legacy systems

---

## API Architecture Comparison

| Feature | REST | GraphQL | gRPC |
|---|---|---|---|
| Protocol | HTTP/1.1 | HTTP | HTTP/2 |
| Data Format | JSON | JSON | Protobuf (binary) |
| Contract | OpenAPI/Swagger | Schema (SDL) | .proto file |
| Caching | HTTP caching | Complex (POST-based) | Not built-in |
| Over-fetching | Common | Eliminated | N/A |
| Streaming | SSE only | Subscriptions | Full streaming |
| Best For | Public APIs | Flexible frontends | Microservices |

---

## API Gateway

### What is an API Gateway?
Single entry point for all client requests. Routes to appropriate backend services.

### Responsibilities
- **Routing**: Direct requests to correct microservice
- **Authentication/Authorization**: Validate tokens, API keys
- **Rate limiting**: Protect backend from abuse
- **Load balancing**: Distribute across service instances
- **Caching**: Cache frequent responses
- **Request/Response transformation**: Protocol translation
- **Monitoring/Logging**: Centralized observability

### Popular API Gateways
- Kong, AWS API Gateway, NGINX, Envoy, Apigee

---

## API Pagination Patterns

| Pattern | How | Pros | Cons |
|---|---|---|---|
| **Offset** | `?page=2&limit=20` | Simple, jump to page | Slow on large offsets, inconsistent |
| **Cursor** | `?cursor=abc&limit=20` | Consistent, performant | Can't jump to page |
| **Keyset** | `?after_id=123&limit=20` | Very efficient | Requires sortable unique key |

---

## Top 7 Ways to 10x API Performance

1. **Caching**: Redis, CDN, HTTP cache headers
2. **Connection pooling**: Reuse DB/HTTP connections
3. **Pagination**: Don't return all data at once
4. **Async processing**: Use message queues for heavy operations
5. **Compression**: gzip/brotli response bodies
6. **Database optimization**: Indexes, query optimization
7. **N+1 query prevention**: Batch/eager loading

---

## API Security Tips

1. Use **HTTPS** everywhere
2. **Authentication**: OAuth 2.0, JWT, API keys
3. **Rate limiting**: Prevent abuse
4. **Input validation**: Sanitize all inputs
5. **CORS**: Restrict allowed origins
6. **API versioning**: Don't break clients
7. **Logging & Monitoring**: Track suspicious activity
8. **Principle of least privilege**: Minimal permissions

---

## API vs SDK

| API | SDK |
|---|---|
| Interface for communication | Toolkit for building with an API |
| Protocol-agnostic concept | Language-specific library |
| Raw HTTP calls | Pre-built functions/methods |
| More flexible | Easier to use |
| Example: Stripe REST API | Example: Stripe Python SDK |

---

## API Testing Types

1. **Unit Testing**: Test individual functions
2. **Integration Testing**: Test API with dependencies
3. **Functional Testing**: Test business logic
4. **Load Testing**: Test performance under load
5. **Security Testing**: Test for vulnerabilities
6. **Contract Testing**: Validate API contract
7. **Smoke Testing**: Basic health checks
8. **Regression Testing**: Ensure no regressions
9. **Fuzz Testing**: Send random/malformed data
