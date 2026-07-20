# YouTube Tutorial Notes

Notes built from system design video playlists and individual videos, organized by topic.

> **On sourcing:** Folders 1 and 2 are note-per-video from two playlists. Folders 3–7 were built differently — YouTube transcripts were not extractable, so only the **video title** was taken as the topic, and the content was written from general knowledge. Each of those files carries a note saying so.

---

## Contents

| # | Folder | Source | Files |
|---|---|---|---|
| 1 | [System Design Basics](./1.%20System%20Design%20Basics/) | [Hello Interview playlist](https://www.youtube.com/playlist?list=PL5q3E8eRUieVFeK1oLahJ8KONkAxDpqk2) | [Index](./1.%20System%20Design%20Basics/00-playlist-index.md) + 10 notes |
| 2 | [System Design Fundamentals](./2.%20System%20Design%20Fundamentals/) | [ByteByteGo playlist](https://www.youtube.com/playlist?list=PLCRMIe5FDPsd0gVs500xeOewfySTsmEjf) (101 videos) | [Index](./2.%20System%20Design%20Fundamentals/00-playlist-index.md) + 29 notes |
| 3 | [API Design](./3.%20API%20Design/) | Hello Interview | [HLD + LLD](./3.%20API%20Design/01-api-design-hld-and-lld.md) |
| 4 | [Caching](./4.%20Caching/) | Hello Interview | [HLD + LLD](./4.%20Caching/01-caching-hld-and-lld.md) |
| 5 | [Load Balancing](./5.%20Load%20Balancing/) | Software Developer Diaries | [HLD + LLD](./5.%20Load%20Balancing/01-load-balancing-hld-and-lld.md) |
| 6 | [Scalability](./6.%20Scalability/) | Caleb Curry | [HLD + LLD](./6.%20Scalability/01-scalability-hld-and-lld.md) |
| 7 | [Rate Limiting](./7.%20Rate%20Limiting/) | Hello Interview | [Stub →](./7.%20Rate%20Limiting/README.md) covered in main repo |

---

## How folders 3–7 relate to the main repo

The main repo already covers the standard interview topics thoroughly. Rather than duplicate it, each of these folders was gap-checked first: existing coverage was searched for the specific technical terms, and **only the genuinely absent material was written**. Every file opens with a **Prerequisites** block cross-linking the fundamentals it deliberately does not repeat.

Folder 7 is the clearest case of this policy — rate limiting was already covered end to end, so it is a stub pointing at [`05-hld-problems/01-easy/rate-limiter.md`](../05-hld-problems/01-easy/rate-limiter.md) rather than a redundant file. The one real gap found (GCRA) was added in place to [`02-building-blocks/02-performance/02-rate-limiting.md`](../02-building-blocks/02-performance/02-rate-limiting.md).

---

## Reading order

These topics interlock. A reasonable path:

1. **[API Design](./3.%20API%20Design/01-api-design-hld-and-lld.md)** — the contract, at both HLD and LLD levels. Establishes the interface-hiding principle the other files reuse.
2. **[Caching](./4.%20Caching/01-caching-hld-and-lld.md)** — sizing math and the cache-aside race. Introduces hot keys.
3. **[Load Balancing](./5.%20Load%20Balancing/01-load-balancing-hld-and-lld.md)** — P2C, draining, outlier detection. The registry design is reused as a concurrency pattern.
4. **[Scalability](./6.%20Scalability/01-scalability-hld-and-lld.md)** — Amdahl, USL, Little's Law. Explains *why* the previous three matter quantitatively; read it last so the laws land on concrete examples.
5. **[Rate Limiting](./7.%20Rate%20Limiting/README.md)** — load shedding's cousin; follows naturally from Scalability's backpressure section.

---

## Recurring threads across folders 3–7

Worth noticing, because interviewers move between these levels:

- **Two different sizing methods, worth not confusing.** [Scalability](./6.%20Scalability/01-scalability-hld-and-lld.md) sizes thread and connection pools from Little's Law (`threads = throughput × latency`). [Load Balancing](./5.%20Load%20Balancing/01-load-balancing-hld-and-lld.md) sizes on resource exhaustion instead — file descriptors, socket buffers, and the ~28k ephemeral port ceiling per source IP/destination pair. For long-lived connections you size on *connections*, not requests/sec, which is why Little's Law is not the tool there.
- **Hiding the policy behind an interface** appears as eviction strategy ([Caching](./4.%20Caching/01-caching-hld-and-lld.md)), balancing strategy ([Load Balancing](./5.%20Load%20Balancing/01-load-balancing-hld-and-lld.md)), and API contracts ([API Design](./3.%20API%20Design/01-api-design-hld-and-lld.md)).
- **Reducing coordination** is the single idea behind sharding, `LongAdder`, copy-on-write registries, and GCRA's single-value atomicity.
- **Tail latency vs average** — caching helps the average; hedging, draining, and outlier detection help the tail.
