---
module: 07-interview-templates
topic: Worked Examples — Full 45-Minute Session Walkthroughs
status: unread
tags: [07-interview-templates, interview, worked-examples, walkthrough]
---
# Worked Examples

> Three complete session walkthroughs showing exactly what a strong SDE-3 answer looks like: what to say, when to say it, and how to handle being pushed. Read these before your first mock interview.

---

## How to Use

Each example is structured as a transcript with interviewer interruptions. Annotations in `[brackets]` explain WHY the candidate says what they say. Use these to build the habit before you practice on your own.

---

## Example 1 — HLD: Design a Notification System

**Company context:** Meta. 45 minutes.

---

**Interviewer:** "Design a notification system."

**Candidate:** "Sure. Before I start, I want to make sure I'm solving the right problem. A few quick questions:

One — what channels are we supporting? Push, email, SMS, or all three?

Two — what's the scale? Are we talking millions of users, billions?

Three — are notifications triggered by external events from other services, or does something call us directly?"

[ANNOTATION: Three targeted questions. Each one changes the architecture. Push-only is simpler than multi-channel. Billions of users means fan-out matters. Event-driven vs sync call changes the ingestion model.]

**Interviewer:** "All three channels. 500 million users, roughly 10 million notifications per day. Triggered by events from other services."

**Candidate:** "Perfect. Let me state my assumptions: the triggering services own their events, we're just a delivery system. Users can configure per-channel preferences. And notifications must not duplicate — same event shouldn't deliver twice.

Let me do quick back-of-envelope: 10M notifications per day = roughly 115 per second average. With push as 60% of traffic, that's about 70 push/sec. Very manageable for a single cluster, but the spiky nature matters — a viral post could spike to 10K events/sec. I'll design for the spike.

Let me sketch the architecture."

[ANNOTATION: States assumptions as facts, not questions. Does estimation and draws a conclusion from it (design for spike). This is SDE-3 framing.]

**Candidate:** "Here's the high-level flow:

```
Triggering Services → Kafka (event bus) → Notification Router → Channel Queues → Delivery Workers → Providers
                                                     ↓
                                             User Preference Store (Redis)
```

Walk me through each component:

**Kafka ingestion:** Triggering services (Order Service, Social Service, etc.) publish events to Kafka topics — one topic per event type: `order.shipped`, `user.followed`. This decouples them from us. We're just a consumer. If we go down, events accumulate in Kafka and we catch up when we recover.

**Notification Router:** Reads from Kafka. For each event, it looks up which users to notify and their preferences. It filters users who have opted out. Then routes to channel-specific queues: `queue.push`, `queue.email`, `queue.sms`.

**User Preference Store:** Preferences change infrequently — 'I turned off email notifications' happens once. So I'd store preferences in a DB (Postgres) as source of truth, with a Redis cache with 60-second TTL. Most lookups hit Redis. Cache miss → DB → repopulate.

**Channel queues:** Separate SQS queues or Kafka topics per channel. This is the bulkhead — if the email provider is slow, it doesn't affect push delivery.

**Delivery workers:** One pool per channel. They consume from the queue, call the provider API (FCM for push, SendGrid for email, Twilio for SMS), handle retries with exponential backoff, and write delivery status to a `delivery_log` table.

Does that make sense so far? Any area you'd like me to go deeper on?"

[ANNOTATION: Checks in after the HLD. Narrates every component's purpose and failure behavior. Bulkhead justification is explicit. Proactively mentions the cache design before being asked.]

**Interviewer:** "Walk me through deduplication — how do you make sure we don't send the same notification twice?"

**Candidate:** "Good question — this is subtle. Duplicates can happen in two places:

First, at the Kafka consumer. If the Router crashes after writing to the channel queue but before committing the Kafka offset, it'll reprocess the same event on restart. The fix: idempotent writes to the channel queue. The Router computes an idempotency key — `SHA256(event_id + user_id + channel)` — and writes it to Redis with a 24-hour TTL before enqueuing. On restart, it re-sees the event, checks Redis, finds the key already exists, and skips.

Second, at the delivery worker. If the worker crashes after calling the provider but before acknowledging the queue message, it'll retry the call. Provider APIs can accept a deduplication ID. For FCM push, the message ID is my idempotency key. For Twilio, there's no built-in dedup, so I store `(user_id, event_id, channel) → delivered_at` in a separate Redis set with 24h TTL, and check before calling.

The 24-hour TTL means we could theoretically duplicate a notification for the same event after 24 hours, but in practice events don't arrive that stale."

[ANNOTATION: Goes two layers deep unprompted. Distinguishes dedup at the queue consumer from dedup at the provider. Explains the TTL trade-off honestly instead of claiming it's perfect.]

**Interviewer:** "What happens when we need to send a notification to 10 million people at once — like a system-wide announcement?"

**Candidate:** "That's a different workload — broadcast vs event-triggered. At 10M recipients, the Router can't look up and enqueue 10M messages synchronously. A few options:

Option A: The Router enqueues a single 'broadcast' message to a special topic. A broadcast fan-out service reads it, fetches user IDs in batches of 10K, and enqueues 10M individual channel messages. The fan-out is async and can take 5–10 minutes — acceptable for a non-urgent announcement.

Option B: For urgent announcements, use a template-based approach. Delivery workers receive a single message: 'send this template to all active users.' They paginate over the user table themselves. This distributes the fan-out work across the entire worker pool in parallel.

I'd use Option A for this system — it keeps the normal notification path unchanged and adds the broadcast path as a parallel flow. The trade-off is that it's eventually consistent — users receive the notification at slightly different times."

[ANNOTATION: Immediately recognizes this is a different problem, not just scaling the existing design. Offers two options, picks one with explicit reasoning. This is SDE-3 trade-off articulation.]

**Interviewer:** "How do you monitor this system?"

**Candidate:** "Three layers:

**Business metrics:** Delivery success rate per channel per hour. Alert if push success rate drops below 95% — that's our SLA. Also: delivery latency (time from event received to notification delivered), which I'd track as P50/P99.

**Queue health:** Kafka consumer lag per consumer group. If lag is growing, the Router or workers are falling behind. Alert at lag > 10K messages. SQS queue depth per channel queue — alert at depth > 50K.

**Infrastructure:** Worker fleet CPU and memory. DLQ depth — anything in the DLQ means retries have failed; I'd alert at any DLQ message and require manual review.

For tracing: I'd propagate a `notification_id` from the moment the event is ingested through every hop. On failure, I can trace exactly where it dropped."

[ANNOTATION: Covers all three monitoring layers. Gives specific thresholds, not just "we'd add alerts." Mentions distributed tracing. This closes the operational story — the dimension most commonly missed at SDE-3.]

---

**Rubric score for this session:**
- A1 Requirements: 4/4 (3 targeted questions, explicit assumptions)
- A2 HLD: 3.8/4 avg (write path, read path, failure modes, monitoring all covered)
- A3 Deep dive: 4/4 (dedup went 2 levels deep; broadcast recognized as different problem)
- A4 Communication: 4/4 (thought out loud, checked in, defended positions)

---

## Example 2 — HLD: Design a Key-Value Store (Hard, Google-style)

**Company context:** Google. 45 minutes. Note: Google expects first-principles reasoning.

---

**Interviewer:** "Design a distributed key-value store."

**Candidate:** "I want to understand the requirements before I pick an approach, since this design changes significantly based on them.

Three questions: One — what's the access pattern? Read-heavy, write-heavy, or balanced? Two — what consistency guarantee is required — linearizable reads, or is eventual okay? Three — what's the target scale — billions of keys, millions of operations per second?"

**Interviewer:** "Write-heavy, about 70% writes. Eventual consistency is acceptable — latency matters more. One billion keys, 500K operations per second."

**Candidate:** "Okay, that shapes the design significantly. Write-heavy + eventual = LSM-tree storage (not B-tree). 500K ops/sec at eventual consistency = no need for Paxos on every write.

Let me estimate storage: 1B keys × average 1KB value = 1TB raw data. With 3× replication that's 3TB. Fits on maybe 30 nodes with 100GB of fast SSD each. I'll design for that cluster size.

Key design decision upfront: for write-heavy workloads, a B-tree would cause write amplification because every random write requires a read-modify-write on a 4KB page. An LSM tree batches writes into sequential memtable flushes — write throughput is much higher. The trade-off is read amplification: reads may need to check multiple SSTables. But since reads are only 30% of our workload, I'll accept read amplification.

Here's the architecture:

```
Client → Coordinator Node
              ↓ consistent hash → 3 replica nodes
         [Node A] [Node B] [Node C]
              ↓
         MemTable (in-memory write buffer)
              ↓ flush at threshold
         SSTable on disk
              ↓ background compaction
         Compacted SSTables
```

Let me walk through each decision."

[ANNOTATION: Did estimation before drawing. Stated the core storage design decision (LSM vs B-tree) as the first thing — because it's the most consequential decision and shows first-principles thinking. Google interviewers are looking for exactly this.]

**Candidate:** "**Routing:** I'll use consistent hashing. Each key hashes to a position on a ring; the write goes to the 3 nodes clockwise from that position. Consistent hashing means adding/removing nodes only remaps K/N keys — critical for a 30-node cluster where node churn is frequent. With 150 virtual nodes per physical node, the key distribution is uniform within 5% variance.

**Write path:** Client sends `PUT(key, value)` to any coordinator node. Coordinator hashes the key, identifies the 3 replica nodes, sends the write to all 3 concurrently. With eventual consistency (`W=1`), we return success after 1 replica acknowledges. The other 2 replicas acknowledge asynchronously. This minimizes write latency at the cost of a brief inconsistency window.

**Read path:** `GET(key)` goes to coordinator, which routes to the 3 replicas. With `R=1`, we return the first response. If two replicas return different values (due to a recent concurrent write), read repair reconciles them — the node with the lower timestamp accepts the newer value. This is how Cassandra handles it."

**Interviewer:** "You mentioned W=1 for writes. What's the failure scenario if the coordinating node crashes immediately after the 1 acknowledgment?"

**Candidate:** "Good catch. If the coordinator crashes before the other 2 replicas receive the write, those 2 replicas never get the value. The client got a success response. So we have a 'lost write' from the perspective of other clients — they'll read from the 2 stale replicas and not see the value.

Mitigation options:

Option A: Increase W to 2 (`QUORUM`). Now the write is durable on 2 of 3 replicas before returning success. Coordinator crash after that point doesn't lose the write. Trade-off: higher write latency, because we wait for 2 acknowledgments.

Option B: Keep W=1 but add hinted handoff. If a replica is unreachable, the coordinator stores the write as a 'hint' and replays it when the replica recovers. This doesn't help with coordinator crash specifically, but reduces window for replica divergence.

Option C: Write-ahead log on the coordinator. Before forwarding the write, append it to a local WAL. On recovery, replay the WAL to the replicas that didn't acknowledge.

For a truly write-heavy, eventually-consistent system, I'd recommend Option A (W=2) as the default for most use cases. Only drop to W=1 for applications where losing an occasional write is explicitly acceptable — metrics, analytics counters."

[ANNOTATION: Didn't just describe the failure — analyzed it to its consequence (lost write from client perspective). Then gave 3 options with trade-offs. Recommended one with a caveat. This is the multi-level depth Google expects.]

**Interviewer:** "How does your system handle a network partition where nodes A and B can talk to each other but not to node C?"

**Candidate:** "This is the classic CAP partition scenario. Since we've chosen eventual consistency over linearizability, we're a CP system — or more precisely, we've chosen availability over consistency: both sides of the partition will continue accepting writes.

During the partition: node C accepts writes independently. Nodes A and B accept writes independently. After the partition heals, we have conflicting versions of some keys.

Conflict resolution: I'd use a last-write-wins strategy with hybrid logical clocks (HLC) for timestamps. HLC combines physical time with a logical counter, so timestamps are monotonically increasing even with clock skew — safer than wall-clock timestamps alone.

After partition heals: nodes exchange their Merkle trees to identify which key ranges diverged. For conflicting keys, the higher HLC timestamp wins. This is deterministic — no human intervention needed.

Edge case: if the application requires that both writes are preserved (e.g., 'append to a list'), last-write-wins would lose one of them. For that, I'd use a CRDT — specifically a G-Set or OR-Set. But for a generic key-value store, LWW is the pragmatic default."

[ANNOTATION: Names the CAP choice explicitly. Uses HLC (not just wall clock) — this shows depth. Mentions Merkle trees for anti-entropy reconciliation. Offers CRDT as an alternative for append-only data. Google rewards knowing when your chosen approach breaks.]

---

**Rubric score for this session:**
- A1 Requirements: 4/4
- A2 HLD: 4/4 (LSM choice justified from first principles, not just named)
- A3 Deep dive: 4/4 (went 3 levels deep on W=1 failure; partition handled precisely)
- A4 Communication: 4/4 (every decision framed as decision + alternative + cost)

---

## Example 3 — LLD: Design a Thread-Safe Bounded Cache with TTL

**Company context:** Amazon. 45 minutes.

---

**Interviewer:** "Design a thread-safe, bounded LRU cache with TTL support in Java."

**Candidate:** "A few quick questions to scope the design:

One — is this a single-JVM cache or distributed? Two — what operations do we need: get, put, delete, or more? Three — is there a preference for exact LRU (precise eviction) vs approximate (acceptable if occasionally evicts slightly wrong entry)?"

**Interviewer:** "Single JVM. Get, put, and optional delete. Exact LRU is preferred."

**Candidate:** "Alright. Let me think about the core data structure first, because that drives everything else.

LRU requires O(1) get, O(1) put, and O(1) eviction of the least-recently-used entry. The standard structure for this is a `HashMap` + doubly-linked list: the map gives O(1) key lookup; the list maintains insertion/access order; the tail of the list is always the LRU entry (O(1) removal).

Java provides `LinkedHashMap` with `accessOrder=true` which is exactly this structure internally. I'll use it as the core, but I need to extend it to handle:
1. Thread safety — `LinkedHashMap` is not thread-safe
2. Capacity enforcement with LRU eviction
3. TTL expiry

Let me design the class structure before writing code."

[ANNOTATION: Identifies the data structure from requirements before writing code. Acknowledges `LinkedHashMap` but immediately lists what it doesn't handle — showing you understand it's a building block, not the full solution.]

**Candidate:** "Here are the main classes:

```
BoundedLRUCache<K, V>         — public interface
  - LinkedHashMap<K, CacheEntry<V>>  — internal store
  - ReentrantReadWriteLock           — concurrency
  - ScheduledExecutorService         — TTL reaper

CacheEntry<V>                 — internal wrapper
  - V value
  - long expiresAt            — epoch millis, or Long.MAX_VALUE if no TTL
```

Now let me walk through the concurrency model before I write any methods.

`LinkedHashMap.get()` is not read-only — it moves the accessed entry to the head of the list (that's what makes it LRU-ordered). So `get()` is a structural modification. That means I can't use a `ReadWriteLock`'s read lock for `get()` — it would allow concurrent structural modifications. Both `get()` and `put()` need the write lock.

The TTL reaper runs in a `ScheduledExecutorService`. It also acquires the write lock while iterating and removing expired entries.

This is simpler than a `ReadWriteLock` — just a single `ReentrantLock`. But I should mention: if we were willing to sacrifice exact LRU for approximate LRU (like Caffeine's approach), we could use a concurrent structure with much better read throughput. For exact LRU with a write lock, concurrent reads are serialized. That's the fundamental tension here."

[ANNOTATION: Caught the subtle issue that `get()` modifies structure in LRU — so ReadWriteLock doesn't help. States this explicitly before writing code. Shows understanding of why the simpler lock is correct.]

**Candidate:** "Let me write the core methods:

```java
public class BoundedLRUCache<K, V> {
    private final int capacity;
    private final LinkedHashMap<K, CacheEntry<V>> store;
    private final ReentrantLock lock = new ReentrantLock();
    private final ScheduledExecutorService reaper;

    public BoundedLRUCache(int capacity) {
        this.capacity = capacity;
        this.store = new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, CacheEntry<V>> eldest) {
                return size() > capacity;
            }
        };
        this.reaper = Executors.newSingleThreadScheduledExecutor();
        reaper.scheduleAtFixedRate(this::evictExpired, 1, 1, TimeUnit.MINUTES);
    }

    public V get(K key) {
        lock.lock();
        try {
            CacheEntry<V> entry = store.get(key);
            if (entry == null) return null;
            if (entry.isExpired()) {
                store.remove(key);
                return null;
            }
            return entry.value;
        } finally {
            lock.unlock();
        }
    }

    public void put(K key, V value, long ttlMs) {
        long expiresAt = ttlMs > 0 ? System.currentTimeMillis() + ttlMs : Long.MAX_VALUE;
        lock.lock();
        try {
            store.put(key, new CacheEntry<>(value, expiresAt));
            // removeEldestEntry handles capacity enforcement automatically
        } finally {
            lock.unlock();
        }
    }

    private void evictExpired() {
        lock.lock();
        try {
            store.entrySet().removeIf(e -> e.getValue().isExpired());
        } finally {
            lock.unlock();
        }
    }

    // Shutdown hook for the reaper
    public void shutdown() {
        reaper.shutdown();
    }
}

private static class CacheEntry<V> {
    final V value;
    final long expiresAt;
    CacheEntry(V value, long expiresAt) { this.value = value; this.expiresAt = expiresAt; }
    boolean isExpired() { return System.currentTimeMillis() > expiresAt; }
}
```"

[ANNOTATION: `removeEldestEntry` is the hook in `LinkedHashMap` for capacity enforcement — using it is cleaner than manual eviction. `ReentrantLock` in try-finally is correct (no deadlock from exceptions). Reaper uses a scheduled executor, not a raw thread. `shutdown()` exists for resource cleanup.]

**Interviewer:** "Why not use `ConcurrentHashMap` here? That would give you better read concurrency."

**Candidate:** "Good challenge. `ConcurrentHashMap` gives concurrent reads and segment-level write concurrency — it would eliminate the global write lock for `get`. The problem is LRU order maintenance.

LRU order requires a total ordering of all entries by access time. That ordering is maintained by the doubly-linked list inside `LinkedHashMap`. To maintain this list correctly under concurrent access, every `get` (which reorders the list) and every `put` (which may evict) must be atomic with respect to each other.

`ConcurrentHashMap` doesn't maintain any ordering — it's a pure hash map. To add LRU ordering on top of it, I'd need a concurrent doubly-linked list, which requires CAS operations on multiple pointers atomically — non-trivial and error-prone.

Caffeine (the gold standard Java cache library) does this with a ring buffer that amortizes the ordering maintenance: reads enqueue a 'move to front' operation into a per-thread ring buffer; a background thread drains the buffer and reorders periodically. It achieves near-`ConcurrentHashMap` read throughput with approximate LRU. The trade-off: LRU order is not exact during high concurrency — entries may be evicted slightly out of true LRU order.

For this problem, the interviewer said 'exact LRU is preferred,' so I used the simpler `LinkedHashMap + ReentrantLock` approach. If approximate LRU were acceptable and we needed 100K+ reads/second, I'd implement the Caffeine-style ring buffer or just use Caffeine directly."

[ANNOTATION: Didn't just say "ConcurrentHashMap doesn't maintain order." Explained WHY — the doubly-linked list is the constraint. Referenced Caffeine as the production-grade solution and explained how it solves the problem. Tied the recommendation back to the stated requirement (exact LRU). This is SDE-3 depth.]

**Interviewer:** "What are the edge cases you haven't handled?"

**Candidate:** "Three that I see:

One — **clock skew in TTL**: `System.currentTimeMillis()` can jump backwards (NTP correction). If time moves backward, an entry's TTL effectively extends. Fix: use `System.nanoTime()` for TTL duration — it's monotonic. At entry creation: `expiresNano = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ttlMs)`. This doesn't prevent clock jumps from affecting wall time but ensures the TTL duration is always correct relative to system uptime.

Two — **null values**: `store.get(key)` returns null both when the key is absent and when the value is null. I should distinguish them. Fix: return `Optional<V>` from `get()`, or use a sentinel null wrapper in `CacheEntry`.

Three — **reaper thread starvation**: If `evictExpired()` holds the lock for a long time (large cache with many expired entries), `get()` and `put()` callers block. Fix: `evictExpired()` should be time-bounded — collect up to N expired keys per run, release lock, yield, then continue. Or: lazy expiry only (check on `get()`) with no reaper — simpler but expired entries count against capacity until accessed."

[ANNOTATION: Three real edge cases, not hand-wavy ones. Each has a specific fix. This is what separates SDE-3 from SDE-2 in LLD — knowing the sharp edges of your own design before being told.]

---

**Rubric score for this session:**
- B1 Object model: 4/4 (`CacheEntry` wrapper, proper encapsulation)
- B2 Concurrency: 4/4 (caught the `get()` structural modification issue; explained why RWLock doesn't help)
- B3 Code quality: 4/4 (clean methods, `removeEldestEntry`, `try-finally`)
- B4 Extensibility: 4/4 (acknowledged Caffeine; stated single-node vs distributed path)

---

## Patterns Across All Three Examples

Observe these patterns — they appear in all SDE-3 answers:

**1. The estimation → architecture connection**
Every example did estimation and drew a conclusion from it. Estimation isn't a ritual — it justifies the next architectural choice.

**2. The "good catch" response to follow-up questions**
When the interviewer pressed on W=1 failure or ConcurrentHashMap, the candidate said "good catch" (or equivalent) and then dug deeper rather than defending the previous answer. SDE-3s don't treat follow-up questions as challenges — they treat them as opportunities to show depth.

**3. Explicit trade-off articulation**
Every decision included: what was chosen, what was the alternative, why this over that, and what's lost. Never just "I'd use X."

**4. Closing with operations**
All three examples ended with a monitoring/operational story before the interviewer asked. This is the most consistently missed signal in SDE-3 interviews.

**5. Edge cases stated before being asked**
Example 3 volunteered edge cases at the end. This shows you've thought beyond the happy path — the mark of someone who has built and operated production systems.
