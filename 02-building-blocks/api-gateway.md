# API Gateway

> **Single entry point for API traffic: routing, authentication, rate limiting, and protocol translation.**

---

## The Hotel Concierge Analogy

At a 5-star hotel, every guest request goes through the concierge desk. They verify your room key (authentication), route you to the right service — restaurant, spa, room service (routing), tell you "I'm sorry, the spa is fully booked today" (rate limiting), log every request in the concierge log (observability), and communicate in whichever language you need (protocol translation). The kitchen, spa, and housekeeping never deal with guests directly — they only receive well-formed, pre-approved requests from the concierge.

That desk is your API gateway.

**Why it exists**: Without a gateway, every microservice would implement its own auth, rate limiting, TLS termination, and versioning. Clients would need to know 20 different service endpoints. The gateway centralizes all cross-cutting concerns and presents a single, consistent API surface to the outside world.

---

## 1. Concept Overview

An **API gateway** sits between clients and backend services. It handles cross-cutting concerns (auth, rate limiting, logging, routing) so individual services can stay focused on business logic.

---

## 2. Core Principles

### Typical Responsibilities

| Responsibility | Description |
|----------------|-------------|
| **Routing** | Path/host → backend service (e.g. `/users` → user service) |
| **Authentication** | Verify JWT, API key, or OAuth; reject unauthenticated requests |
| **Authorization** | Check permissions (e.g. scope, role) |
| **Rate limiting** | Limit requests per user/key/IP; return 429 when exceeded |
| **Protocol translation** | REST (public) → gRPC (internal); or GraphQL → REST backends |
| **Caching** | Cache responses for read-heavy endpoints |
| **Circuit breaking** | Fail fast when backend is down; avoid cascading failure |
| **Logging / metrics** | Centralized access logs, latency, error rate |

The concierge checks your room key before routing any request (auth happens first, before any routing decision). If you've visited the spa 5 times today, the concierge politely refuses further bookings (rate limiting returns HTTP 429). The guest never sees internal hallways (the backend services remain unexposed).

### Architecture

```
  Clients (Web / Mobile / Partners)
        │
        ▼
  ┌─────────────────┐
  │   API Gateway   │  Auth, rate limit, route, cache, log
  └────────┬────────┘
           │
     ┌─────┼─────┬─────────┐
     ▼     ▼     ▼         ▼
  User  Order  Payment  Notification
  Svc   Svc    Svc      Svc
```

---

## 3. Real-World Usage

- **Kong**: Open-source; plugins for auth, rate limit, logging; on-prem or managed.
- **AWS API Gateway**: Managed; Lambda integration; usage plans and keys.
- **Google Apigee**: Enterprise; monetization, analytics.
- **Nginx / Envoy**: Often used as gateway with custom config or Lua/WASM.

---

## 4. Trade-offs

| Aspect | Pros | Cons |
|--------|------|------|
| **Centralized** | One place for auth, rate limit, routing | Gateway is critical path and potential bottleneck |
| **Protocol translation** | Clients use REST; internals use gRPC | Extra hop and complexity |
| **Managed vs self-hosted** | Managed: less ops | Cost; less control |

**When to use**: Multiple services exposed as one API; need central auth, rate limit, or versioning.  
**When not**: Single service; or when a simple reverse proxy is enough.

---

## 5. Failure Scenarios

| Scenario | Mitigation |
|----------|------------|
| Gateway down | Multiple gateway instances behind LB; stateless design |
| Backend down | Circuit breaker; return 503; retry with backoff |
| Auth/rate-limit bypass | Validate at gateway only; never trust client-supplied headers |
| Latency spike | Timeouts; circuit breaker; scale gateway horizontally |

A stateless gateway is critical: just as the hotel has multiple concierge desks that share the same guest database, any gateway instance can handle any request. No in-memory session state; auth context lives in the JWT.

---

## 6. Performance Considerations

- **Latency**: Gateway adds one hop; minimize logic and use connection pooling to backends.
- **Throughput**: Scale gateway horizontally; avoid heavy logic in hot path.
- **Caching**: Reduces backend load and latency for cacheable GETs.

---

## 7. Implementation Patterns

### JWT Verification + Routing (Java / Spring Cloud Gateway)

```java
@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
            // /users/** → user-service (after JWT check)
            .route("user-service", r -> r
                .path("/users/**")
                .filters(f -> f
                    .filter(jwtAuthFilter())       // auth: check room key
                    .requestRateLimiter(c -> c     // rate limit: 100 req/s per user
                        .setRateLimiter(redisRateLimiter())
                        .setKeyResolver(userKeyResolver()))
                    .circuitBreaker(c -> c         // circuit breaker: fail fast
                        .setName("user-cb")
                        .setFallbackUri("forward:/fallback/users")))
                .uri("lb://user-service"))         // route to registered instances
            .route("order-service", r -> r
                .path("/orders/**")
                .filters(f -> f.filter(jwtAuthFilter()))
                .uri("lb://order-service"))
            .build();
    }
}

// JWT auth filter: concierge checks the room key
@Component
public class JwtAuthFilter implements GatewayFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String token = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (!jwtService.isValid(token)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }
}
```

### Backend for Frontend (BFF)

Different concierge desks for different guest types: a mobile-optimized gateway that collapses several API calls into one response (reducing round trips on slow networks), and a web gateway that returns full payloads. Both share the same backend services.

```
  Mobile App ──▶ Mobile BFF Gateway (compact responses, merged calls)
  Web App    ──▶ Web BFF Gateway    (full responses)
  Partners   ──▶ Partner Gateway    (rate-limited, API-key auth)
                         │
              (all three route to same backends)
```

- **Gateway per environment**: One gateway for public API; optional separate for internal.
- **BFF**: Different gateway per client type (mobile vs web) to tailor responses.
- **Gateway + service mesh**: Gateway at edge; mesh handles service-to-service (mTLS, retries, discovery).

---

## Quick Revision

- **Role**: Single entry; routing, auth, rate limit, protocol translation, caching, circuit breaking.
- **Vs reverse proxy**: Gateway adds auth, rate limit, and API-specific logic; reverse proxy is more generic.
- **Failure**: Stateless; multiple instances; circuit breaker to backends.
- **Interview**: "We use an API gateway for authentication and rate limiting so backends don't each implement it; we route by path to the right microservice and can translate REST to gRPC internally."
