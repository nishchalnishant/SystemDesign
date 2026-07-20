# API Design in System Design Interviews

> **Topic from**: [API Design in System Design Interviews w/ Meta Staff Engineer](https://www.youtube.com/watch?v=DQ57zYedMdQ) — Hello Interview
>
> **Note**: These notes are written from general knowledge on the topic, not transcribed from the video.

**See also**: [HLD template](../../07-interview-templates/01-frameworks/03-api-design-template.md) · [Basics notes](../1.%20System%20Design%20Basics/06-api-design.md) · [API styles](../2.%20System%20Design%20Fundamentals/11-api-styles-gateway-graphql-grpc.md)

**Related in this directory** — the interface-hiding principle below recurs throughout:
- [Caching](../4.%20Caching/01-caching-hld-and-lld.md) — the same principle applied to eviction policy
- [Load Balancing](../5.%20Load%20Balancing/01-load-balancing-hld-and-lld.md) — applied to balancing strategy
- [Rate Limiting](../7.%20Rate%20Limiting/README.md) — the 429 / `Retry-After` side of the status-code contract

---

API design shows up in two very different interview formats, and conflating them is a common failure. In HLD the API is a **contract between services** — you are asked roughly ten lines of endpoints and then you move on. In LLD the API is a **contract between classes** — you are asked to design the interfaces themselves, and that is the whole hour.

---

# Part 1 — HLD: The API as a Service Contract

## Where it belongs in the interview

Define APIs immediately after functional requirements, before you draw any boxes. The endpoint list is the cheapest way to prove you understood the problem, and it constrains everything downstream — your data model, your partitioning, your caching.

Spend **3–5 minutes**. Writing twelve endpoints when the interviewer wanted three is a real way to lose time on a 45-minute problem.

## Deriving endpoints from requirements

Map each functional requirement to exactly one endpoint. If a requirement needs three endpoints, it was probably two requirements.

| Requirement | Endpoint |
|---|---|
| "User can post a tweet" | `POST /v1/tweets` |
| "User can view their feed" | `GET /v1/feed?cursor=…` |
| "User can follow another user" | `POST /v1/users/{id}/followers` |

State the shape out loud — request body, response body, status code. Vague endpoints hide the hard parts.

## The four decisions worth defending

Everything else is detail. These four are where seniority shows.

### 1. Protocol

| Protocol | Use when | Why |
|---|---|---|
| **REST** | Public API, third-party clients | Universal, cacheable, debuggable |
| **gRPC** | Internal service-to-service | Binary, HTTP/2 multiplexing, generated stubs |
| **GraphQL** | Mobile with varied view needs | Client picks fields, avoids over-fetching |
| **WebSocket / SSE** | Server must push (chat, live scores) | Persistent connection |

Do not say "REST" reflexively. In a design with heavy internal fan-out, gRPC is the better answer and saying so is a signal.

### 2. Pagination

Always cursor, never offset, for anything feed-shaped.

```
GET /v1/feed?limit=20&cursor=eyJpZCI6MTIzfQ==

{ "items": [...], "next_cursor": "eyJpZCI6MTQzfQ==" }
```

Offset pagination breaks in two ways: `OFFSET 100000` forces the DB to scan and discard 100k rows, and inserts during paging shift the window so users see duplicates or gaps. Cursors encode a stable sort key and have neither problem.

### 3. Idempotency

Any non-GET endpoint that moves money, sends a message, or creates a resource needs an idempotency key.

```
POST /v1/payments
Idempotency-Key: 7c9e6679-…

→ server stores key → result, TTL ~24h
→ retry with same key returns the original response, does not re-charge
```

The failure this prevents: client sends request, server processes it, response is lost to a network blip, client retries, user is charged twice. Interviewers ask about this specifically — it is the clearest test of whether you have run something in production.

### 4. Versioning

`/v1/` in the path. Add fields freely; never remove or repurpose one. Breaking changes mean `/v2/` running alongside `/v1/` until clients migrate — and mobile clients never fully migrate, so plan to run both indefinitely.

## Status codes

`200` OK · `201` Created · `202` Accepted (async) · `400` malformed · `401` unauthenticated · `403` authenticated but not allowed · `404` · `409` conflict · `429` rate limited (send `Retry-After`) · `500` server fault · `503` overloaded

The `401` vs `403` distinction and the presence of `429` are the two things people notice.

---

# Part 2 — LLD: The API as a Class Contract

In LLD, "API" means the public surface of your classes. The skill is deciding what to expose and what to hide — and most candidates expose far too much.

## Start with the interface, not the class

Write the interface first, then implement. It forces you to think about the contract before the data.

```java
public interface RateLimiter {
    boolean tryAcquire(String clientId);
}
```

That is the entire public API of a rate limiter. Whether it is token bucket or sliding window is an implementation detail the caller must not see. If your interface leaks `getTokenCount()` or `getWindowStart()`, you have exposed the algorithm and locked yourself out of changing it.

## Design rules that carry weight

### Hide the representation

```java
// Leaks internal state — caller can mutate your list
public List<Item> getItems() { return this.items; }

// Safe
public List<Item> getItems() { return Collections.unmodifiableList(items); }
```

Returning a mutable internal collection means any caller can corrupt your object's invariants. This is a frequent, easily-avoided finding in LLD reviews.

### Make illegal states unrepresentable

```java
// Bad — nothing stops NEW → COMPLETED
order.setStatus(Status.COMPLETED);

// Good — transitions validated, invalid ones impossible
order.markPaid();      // throws if not in NEW
order.markShipped();   // throws if not in PAID
```

Setters that accept any enum value push validation onto every caller. Intention-revealing methods keep the state machine inside the object that owns it.

### Constructors establish invariants

If an object cannot be used without a dependency, take it in the constructor — not via a setter. A half-constructed object that compiles but throws on first use is a design defect.

```java
public class OrderService {
    private final PaymentGateway gateway;
    private final OrderRepository repo;

    public OrderService(PaymentGateway gateway, OrderRepository repo) {
        this.gateway = Objects.requireNonNull(gateway);
        this.repo = Objects.requireNonNull(repo);
    }
}
```

Constructor injection also makes the class testable — you can pass a fake gateway. Field injection or `new PaymentGateway()` inside the class makes unit testing impossible, and interviewers probe for that.

### Depend on abstractions

`OrderService` should take `PaymentGateway` (interface), not `StripeGateway` (concrete). This is the D in SOLID and the single most-tested principle in LLD rounds, because it is what lets you add PayPal without editing `OrderService`.

### Keep interfaces narrow

A `Machine` interface with `print()`, `scan()`, and `fax()` forces a basic printer to implement `fax()` with a thrown exception. Split it. Clients should not depend on methods they do not call — the I in SOLID.

## Exceptions are part of the contract

What a method throws is as much API as what it returns.

- Throw a **domain exception** (`InsufficientBalanceException`), not `RuntimeException`
- Never swallow into a `null` return — the caller cannot distinguish "absent" from "failed"
- Use `Optional<T>` for legitimately-absent values, exceptions for genuine failures

```java
Optional<User> findById(String id);              // may legitimately not exist
User getById(String id) throws UserNotFound;     // absence is an error here
```

## Concurrency belongs in the contract

If an object will be touched by multiple threads, say so and enforce it. Thread safety is not documentation — it is behavior.

```java
public class InventoryService {
    private final ConcurrentHashMap<String, AtomicInteger> stock = new ConcurrentHashMap<>();

    /** Thread-safe. Returns false if insufficient stock. */
    public boolean reserve(String sku, int qty) {
        AtomicInteger count = stock.get(sku);
        if (count == null) return false;
        while (true) {
            int current = count.get();
            if (current < qty) return false;
            if (count.compareAndSet(current, current - qty)) return true;
        }
    }
}
```

The CAS loop matters: a `get()`-then-`set()` pair would let two threads both read stock of 1 and both succeed, overselling. Stating "this method is thread-safe, here is why" is a strong senior signal.

**See**: [concurrency notes](../../06-lld/04-concurrency/) for lock choice and thread-pool sizing.

---

# The connection between the two

The same endpoint exists at both levels, and it should look consistent:

```
HLD:  POST /v1/orders  {items, idempotency_key}  →  201 {order_id}

LLD:  interface OrderService {
          Order placeOrder(OrderRequest request) throws PaymentException;
      }
```

The HTTP idempotency key becomes a field on `OrderRequest`. The `409 Conflict` becomes an exception type. When an interviewer moves from HLD to LLD on the same problem, showing that the layers line up is the point.

---

## Common mistakes

| Mistake | Fix |
|---|---|
| Verbs in REST paths (`/createUser`) | Nouns + HTTP method |
| Offset pagination on a feed | Cursor |
| No idempotency on payments | `Idempotency-Key` header |
| Getters/setters on every field | Expose behavior, not state |
| Returning mutable internals | `unmodifiableList` / defensive copy |
| Concrete types in constructors | Depend on interfaces |
| `throws Exception` | Specific domain exceptions |
| Designing all endpoints before requirements are settled | Endpoints derive from requirements |

---

## Key takeaways

- HLD API design is **3–5 minutes** — endpoint list, then move on
- Cursor pagination and idempotency keys are the two details that most reliably signal production experience
- LLD API design is about **what you hide**, not what you expose
- Constructor injection + interface dependencies = testable, and interviewers check for testability
- Thread safety is part of the contract; state it explicitly and show the mechanism
