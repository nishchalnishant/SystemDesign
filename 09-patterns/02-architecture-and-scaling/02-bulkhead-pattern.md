---
module: 09-patterns
status: unread
tags: [09-patterns, system-design, patterns]
---
# Bulkhead Pattern

> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** How to design a system so that a failure in one minor component doesn't cause the entire system to crash.
>
> **Key concepts:**
> - **The Ship Metaphor:** A ship is built with watertight compartments (bulkheads). If the hull gets a hole, only one compartment floods, saving the whole ship from sinking.
> - **Connection Pools:** In software, a server has a limited number of "workers" (threads). If you dedicate a small, limited pool of workers to each specific task, a broken task can only use up its own workers, leaving the rest of the system perfectly fine.
> - **Hardware Isolation:** Running critical tasks and non-critical tasks on completely separate servers.
>
> **Key takeaway:** The Bulkhead pattern is a defensive shield. It sacrifices a tiny bit of efficiency in exchange for a massive gain in system stability during emergencies.

---

## 🤷‍♂️ Why Should I Care?

Imagine you run an e-commerce website with 6 features: Login, Search, Cart, Recommendations, and Payments. 

Your web server has exactly 200 "worker threads" (imagine 200 cashiers at a massive grocery store). Any cashier can handle any task. 

Suddenly, the database for the **Payments** system becomes incredibly slow. Every time a customer tries to pay, the cashier gets stuck waiting for 30 seconds. Because millions of people are trying to pay, very quickly, all 200 cashiers get stuck waiting on the Payments database.

Now, a new customer comes to the website just to **Search** for a product. But the Search fails. Why? Because there are no cashiers left to help them! The entire website is completely dead, all because one specific database became slow. 

This is called a **Cascading Failure**.

---

## 🚢 The Sinking Ship Analogy

The Bulkhead Pattern gets its name from shipbuilding. 

In the old days, if a ship hit a rock and got a hole in the hull, water would fill the entire ship, and it would sink. (This is like our 200 cashiers all getting stuck).

Modern ships are built with **Bulkheads** — thick steel walls that divide the ship into 10 watertight compartments. If the ship hits a rock and gets a hole in Compartment 3, the water fills up Compartment 3, but the thick steel walls stop the water from spreading. The ship keeps floating.

**How to build software bulkheads:**
Instead of letting all 200 cashiers do any task, you build physical barriers:
- You assign exactly **30 cashiers** to handle Payments. 
- You assign exactly **30 cashiers** to handle Search.
- And so on.

Now, when the Payments database gets slow, those 30 Payments cashiers get stuck. The Payments system crashes. BUT, the other 170 cashiers are perfectly fine! Customers can still Search, Login, and add things to their Cart. You contained the flood to a single compartment.

---

## Pattern Mindmap

```
Bulkhead Pattern
├── Why It Exists
│   ├── All microservices share one thread pool (e.g., 200 Tomcat threads)
│   ├── Slow downstream (Payments) holds threads → threads exhausted
│   └── Unrelated features (Auth, Search) fail — same thread pool, no isolation
├── Ship Analogy
│   ├── Ship hull divided into watertight compartments (bulkheads)
│   ├── One compartment floods → sealed doors prevent total flooding
│   └── Same principle: one service slow → its pool exhausted, others unaffected
├── Implementation Types
│   ├── Thread Pool Bulkhead
│   │   ├── Separate thread pool per downstream dependency
│   │   ├── Max threads = budget per dependency
│   │   └── Caller blocks on its dedicated pool, not global pool
│   ├── Semaphore Bulkhead
│   │   ├── Count of concurrent calls allowed (no new threads)
│   │   ├── Caller uses its own thread; semaphore limits concurrency
│   │   └── Lower overhead; less isolation than thread pool
│   └── Connection Pool Isolation
│       ├── Separate DB connection pools per service/use case
│       └── Reporting queries don't starve transactional queries
├── Resilience4j Bulkhead API
│   ├── ThreadPoolBulkhead → dedicated executor per upstream call
│   ├── SemaphoreBulkhead → max concurrent calls via Semaphore
│   └── BulkheadRegistry → centralized config and metrics
├── Combined Patterns
│   ├── Bulkhead + Circuit Breaker → isolation + trip on failure rate
│   ├── Bulkhead + Timeout → bounded wait even if pool not exhausted
│   └── Bulkhead + Retry → retry inside bulkhead with backoff
├── Sizing Bulkheads
│   ├── Thread pool size = (target RPS) × (avg latency in sec) × (safety factor)
│   ├── Too small → unnecessary BulkheadFullException (rejected good requests)
│   └── Too large → no isolation benefit (back to shared pool)
└── Real-World Usage
    ├── Netflix Hystrix → pioneer of thread pool bulkheads (now deprecated)
    ├── Resilience4j → modern Java replacement for Hystrix
    ├── Istio → sidecar-level connection pool limits (mesh bulkheads)
    └── AWS ALB → target group limits per service
```

---

## 2. Core Concepts

### 2.1 Thread Pool Bulkhead

This is the exact implementation of the cashier analogy above. 

```
Without Bulkhead:
┌────────────────────────────────────────────┐
│  Shared Thread Pool (200 workers)          │
│  [Pay][Pay][Pay][Pay][Pay][Pay]...(all     │
│   ...stuck waiting for slow Payments DB)   │
│  Search: no workers left → CRASH           │
└────────────────────────────────────────────┘

With Thread Pool Bulkhead:
┌──────────┐ ┌──────────┐ ┌──────────────────┐
│Auth Pool │ │Search    │ │Payments Pool     │
│(20 wkrs) │ │Pool(20)  │ │(30 workers)      │
│[A][A][A] │ │[S][S][S] │ │[P][P][P]...(all  │
│  healthy │ │ healthy  │ │  blocked — OK,   │
└──────────┘ └──────────┘ │  contained here) │
                          └──────────────────┘
```

### 2.2 Connection Pool Isolation (Database Bulkheads)

You can also use bulkheads on your database. 

Imagine you have a single PostgreSQL database that handles two things:
1. Fast, instant actions (like a user logging in).
2. Huge, slow analytics reports that take 60 seconds to run.

If 100 people try to run analytics reports at the same time, the database will dedicate all of its power to those slow reports. Suddenly, nobody can log in.

**The Fix:** You create two Connection Pools (Bulkheads) for the database. You tell the database: "Never use more than 20% of your power on analytics reports." Now, the reports might be slow, but logins will never crash.

### 2.3 Sizing: How big should the compartments be?

If you make the Payments compartment too small (e.g., 2 workers), legitimate customers will get rejected because the compartment fills up instantly. If you make it too big (e.g., 180 workers), you lose the protection of the bulkhead.

To find the perfect size, engineers use a math equation called **Little's Law**: 
`Workers Needed = (Requests Per Second) × (Average Time Per Request in Seconds)`

*Example:* 
- We expect 100 people to pay per second.
- A normal payment takes 0.2 seconds.
- 100 × 0.2 = 20 workers needed.
- We add a small safety buffer and set the Bulkhead to **30 workers**.

---

## 3. Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     API Gateway                                 │
│  Incoming requests: /auth /search /checkout /recommendations    │
└──────────────────────────────┬──────────────────────────────────┘
                               │
              ┌────────────────┼──────────────────┐
              ▼                ▼                  ▼
┌──────────────────┐ ┌───────────────┐ ┌──────────────────────┐
│  Auth Service    │ │ Search Service│ │  Checkout Service    │
│                  │ │               │ │                      │
│ ┌──────────────┐ │ │ ┌───────────┐ │ │ ┌──────────────────┐ │
│ │ Bulkhead     │ │ │ │Bulkhead   │ │ │ │Bulkhead: Payments│ │
│ │ Max: 20 thds │ │ │ │Max: 50    │ │ │ │Max: 30 threads   │ │
│ └──────┬───────┘ │ │ └─────┬─────┘ │ │ └────────┬─────────┘ │
└────────┼─────────┘ └───────┼───────┘ │          │           │
         │                   │         │          ▼           │
         ▼                   ▼         │   Payments Service   │
    AuthDB             Elasticsearch   │   (slow — isolated)  │
                                       └──────────────────────┘
```

---

## 4. The "Three Musketeers" of Resilience

In production, you almost never use a Bulkhead by itself. You combine it with two other patterns: **Timeouts** and **Circuit Breakers**.

1. **Timeout**: "If the cashier takes longer than 2 seconds, give up and walk away." (Prevents workers from being stuck forever).
2. **Bulkhead**: "Only 30 cashiers are allowed to handle payments." (Prevents the whole store from freezing).
3. **Circuit Breaker**: "If 10 cashiers fail in a row, close the payments aisle completely for 5 minutes so the broken database can recover."

---

## Java Code Example (Resilience4j)

*Note: You don't need to memorize this syntax, just understand that libraries like Resilience4j handle all the hard math for you.*

```java
@Configuration
public class BulkheadConfig {

    @Bean
    public ThreadPoolBulkheadRegistry threadPoolBulkheadRegistry() {
        ThreadPoolBulkheadConfig config = ThreadPoolBulkheadConfig.custom()
            .maxThreadPoolSize(30)           // Exactly 30 workers for Payments
            .coreThreadPoolSize(10)          // Keep 10 workers on standby always
            .queueCapacity(5)                // Allow a small line of 5 people to wait
            .build();

        return ThreadPoolBulkheadRegistry.of(config);
    }
}
```

---

## 🎤 Interview Talking Points

**Q: "All your microservices share a thread pool. The Payments service slows down and the whole system fails. How do you fix this?"**
> "This is a classic cascading failure caused by thread exhaustion. I would implement the Bulkhead Pattern. I would assign a dedicated, fixed-size thread pool specifically for the Payments service. That way, if Payments gets slow, it will only exhaust its own small pool of threads. The rest of the system's threads remain free to serve traffic for Search, Auth, and other healthy services. It acts exactly like a watertight compartment on a ship."

**Q: "How do you figure out exactly how many threads to give the Payments bulkhead?"**
> "I would use Little's Law, which is `Arrival Rate × Average Latency`. If we expect 100 requests per second, and the average payment takes 200 milliseconds (0.2 seconds), Little's Law dictates we need 20 threads just to handle normal traffic. I would add a standard safety buffer of 50%, bringing the bulkhead size to 30 threads."

**Q: "If the Bulkhead is completely full, what happens to the 31st user?"**
> "They receive an immediate 'BulkheadFullException' without waiting. At that point, we must have a local 'Fallback' response ready—like a friendly error message or a cached result. We must absolutely *not* try to call another remote service during the fallback, or we risk exhausting a different bulkhead!"
