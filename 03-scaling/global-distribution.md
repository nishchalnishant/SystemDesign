---
module: 03-scaling
status: unread
tags: [03-scaling, system-design, scaling]
---
# Global Distribution & Multi-Region Architecture

> **How to architect systems that operate across geographies with low latency, high availability, and data sovereignty compliance.**

---

## File Mindmap

```
Global Distribution & Multi-Region Architecture
├── Why It Exists
│   ├── Problem → US-EAST only: London p99=180ms, Tokyo=280ms; physics minimum NY→London=68ms
│   └── Physical limit → speed of light ~200,000 km/s in fiber; latency is irreducible below RTT/2
├── Three Motivations
│   ├── Latency → serve users from nearest region (<20ms vs 200ms)
│   ├── Availability → region failure doesn't take down global service
│   └── Compliance → GDPR requires EU data stays in EU (data sovereignty)
├── Active-Active Architecture
│   ├── All regions accept reads AND writes simultaneously
│   ├── Conflict resolution required: LWW (last-write-wins by timestamp), CRDT, or Google Spanner TrueTime
│   ├── CRDTs → G-Counter (increment-only), PN-Counter (inc/dec), OR-Set (add/remove)
│   └── Trade-off: complex conflict resolution; best for: social feeds, shopping carts, analytics
├── Active-Passive Architecture
│   ├── One primary region accepts writes; others are hot standbys
│   ├── Failover: promote replica → update DNS TTL → redirect traffic (RTO: 1-3 min, RPO: seconds)
│   └── Trade-off: simpler consistency; reads in passive regions may be stale
├── Global Load Balancing (4 Layers)
│   ├── L1 GeoDNS / Anycast → Route 53 latency routing → nearest region
│   ├── L2 Global LB (Cloudflare / AWS Global Accelerator) → Anycast IP, TCP at edge
│   ├── L3 Regional LB → ALB/NLB per region
│   └── L4 Service mesh → Envoy sidecar for inter-service routing within region
├── Cross-Region Replication
│   ├── Synchronous → write to all regions before ack; RPO=0, high write latency
│   └── Asynchronous → ack local, replicate async; low latency, RPO=seconds of potential loss
├── Consistency Models
│   ├── Strong → all reads see latest write (Spanner TrueTime, high latency)
│   ├── Eventual → replicas converge "eventually" (Cassandra, DynamoDB default)
│   ├── Causal → preserve cause-effect order (writes by same user see their own writes)
│   └── Session → read-your-own-writes within one client session
├── GDPR Data Residency
│   ├── EU user data must not leave EU region
│   ├── Implementation: regional KMS keys, partition data by user_region at write time
│   └── Cross-region analytics: anonymize/aggregate before export
├── Failover Runbook
│   ├── Step 1: health check detects primary down → alert fires
│   ├── Step 2: verify it's not a monitoring false positive
│   ├── Step 3: promote replica, update Route 53 record (TTL pre-lowered to 60s)
│   └── Step 4: verify traffic routing, run smoke tests, page on-call
├── Trade-offs
│   ├── Pro: p99 latency drops 5-10× with regional deployment
│   ├── Con: cross-region replication adds cost (~$0.02/GB inter-region transfer on AWS)
│   └── Con: Active-Active conflict resolution is hard to implement correctly
└── Interview Angles
    ├── "How do you handle a full region failure?" → Active-Passive failover runbook
    ├── "How do you comply with GDPR?" → regional data partitioning + KMS
    └── Follow-up: split-brain in Active-Active → CRDTs or Spanner-style external time
```

## 1. Why Multi-Region?

**Question**: Your entire infrastructure runs in US-EAST. Your p99 latency for users in London is 180ms. Physics says the round-trip between New York and London is at minimum 68ms (speed of light, ~5,500km each way). You're already at 2.6× the physical minimum. A user in Tokyo sees 280ms. Nothing in your code is wrong — the problem is geography. What do you do?

**Physical constraint**: Light travels 200,000 km/sec in fiber (roughly two-thirds of vacuum speed of light). New York to London: ~5,500km → 27ms one-way minimum, 54ms RTT minimum. New York to Tokyo: ~10,800km → 54ms one-way minimum, 108ms RTT minimum. These are lower bounds — actual network paths are longer, and there are switching delays. You cannot make one machine serve users in both cities with <10ms latency. Geography alone forces distribution.

**Minimal solution**: Put a CDN in front for static assets. Most user-facing latency in a read-heavy app comes from static content. A CDN edge node in London serves bytes from London. Works until: your app is dynamic, users need personalized data, or you need writes to be fast from all regions.

**Generalize**: Three distinct motivations each drive a different architecture. Latency drives active-active (all regions serve traffic). Availability drives active-passive (survive a region outage). Compliance drives data residency (EU data stays in EU). These three can coexist, but each adds complexity. Don't build active-active for availability reasons — the consistency problems are harder than the availability problem you're solving.

**Three distinct motivations — each drives different architecture:**

| Motivation | Goal | Solution |
|------------|------|----------|
| **Latency** | Serve users from the nearest datacenter | Active-active, all regions serve traffic |
| **Availability** | Survive a full region outage | Active-passive failover |
| **Data sovereignty** | EU user data stays in EU (GDPR, etc.) | Data residency — region-pinned storage |

---

## 2. Active-Active vs Active-Passive

### Active-Active (Multi-Master)

**Question**: You've deployed to US-EAST and EU-WEST. Both accept writes. A user in Paris updates their profile at the same moment a background job in US-EAST also updates their profile. Both write to their local region. Network delay means each region doesn't know about the other's write for ~100ms. When replication catches up, you have two conflicting versions of the same row. Which one wins? What do you lose?

**Physical constraint**: Speed of light means cross-region replication lag is at minimum 54ms (US to EU). During that window, both regions can accept writes to the same record. There is no way to achieve both low-latency writes and strong consistency across regions — this is a fundamental consequence of CAP theorem combined with geography.

**Minimal solution**: Last-write-wins (LWW) by timestamp. The write with the higher timestamp wins. Works until: clock skew between regions causes a newer write to appear "earlier" and get discarded silently.

**Generalize**: CRDTs (Conflict-free Replicated Data Types) for data structures where merge math is well-defined (counters, sets). Application-level merge for complex data. Google Spanner/CockroachDB for global strong consistency — they add ~20ms write latency to wait for quorum confirmation across regions, but eliminate the conflict problem entirely.

All regions accept reads **and** writes simultaneously. Users are routed to the nearest region via Anycast DNS or GeoDNS.

```
User (Paris) → DNS → EU-WEST region
User (Tokyo) → DNS → AP-NORTHEAST region

Both EU-WEST and AP-NORTHEAST:
  - Serve reads locally (low latency)
  - Accept writes locally
  - Replicate writes to each other (async, ~100ms cross-region lag)
```

**Pros**: Lowest latency for all users, no cold failover
**Cons**: Conflict resolution (same row written in two regions simultaneously), complex consistency guarantees

**Conflict resolution strategies**:
- **Last-write-wins (LWW)**: Highest timestamp wins. Simple, but concurrent updates can silently discard data.
- **Application-level merge**: Each service defines merge logic (e.g. "for shopping cart, union of items")
- **CRDT (Conflict-free Replicated Data Types)**: Counters, sets, maps with built-in merge math. G-Counter increments across regions and always converges correctly.
- **Google Spanner / CockroachDB**: Distributed SQL with external consistency (TrueTime). Accept write latency (~20ms higher) to get global strong consistency.

**Use when**: Shopping carts, user activity logs, social feeds — data that benefits from local writes and can tolerate eventual consistency.

---

### Active-Passive (Primary-Standby)

**Question**: You run active-passive with US-EAST as primary. Your EU-WEST standby has a read replica. A user in London writes data — their write goes over the network to US-EAST (+100ms latency). The primary commits and replicates back to EU-WEST (~50ms). The user reads immediately — they might hit the EU-WEST replica before the replication catches up and see their own write missing. What contracts does active-passive break, and when are they acceptable to break?

**Physical constraint**: With one primary, all writes must traverse the network to that primary. A user in Tokyo writing to a US-EAST primary adds ~108ms to every write. If you have a global write-heavy application with latency SLOs of <50ms for writes, active-passive is architecturally incompatible with that SLO for non-US users.

**Minimal solution**: Accept the write latency penalty for non-primary-region users. Route all reads to the local replica. Provide "read-your-writes" for each user by routing the user's reads to the primary for 1 second after any write they perform. Works until: primary region fails, or write latency to primary is intolerable for remote users.

**Generalize**: Active-passive is the right default. The consistency model is simple (one writer), failover is well-understood, and the operational complexity is far lower than active-active. Move to active-active only when you have specific latency SLOs for writes in multiple regions, and you have the operational maturity to handle conflicts.

One region (primary) accepts writes. Other regions are warm standbys — they accept reads from their own replicas but not writes.

```
US-EAST (Primary):  ← All writes go here
  ↓ async replication (~50ms)
EU-WEST (Standby):  ← Reads served locally, writes redirect to US-EAST
AP-NORTHEAST (Standby): ← Reads locally, writes redirect
```

**Pros**: Simple consistency (no conflicts — one writer), easy to reason about
**Cons**: Write latency = primary region latency for all users (EU user writing to US-EAST = +100ms RTT), failover requires promoting standby

**Failover procedure**:
1. Detect: primary health check fails for > 30 seconds
2. Elect: select standby with smallest replication lag
3. Promote: standby stops accepting replication, becomes primary
4. Redirect: DNS/load balancer updates to point to new primary (~60 seconds with low TTL)
5. RTO: 1-3 minutes. RPO: seconds of data loss (async replication lag)

**Use when**: Financial transactions, inventory (strong consistency required), systems where write-anywhere is not needed.

---

## 3. GeoDNS and Latency-Based Routing

**Question**: You have three regional deployments. A user in Frankfurt opens your app. DNS resolves your domain to an IP. Which IP? If it's always the same US-EAST IP, the user suffers 90ms RTT on every request. How does DNS become geography-aware, and what are the failure modes when a region goes down?

**Physical constraint**: DNS resolution adds ~10–50ms per lookup (depends on resolver proximity and cache state). DNS TTL controls how long clients cache the answer. A TTL of 300s (5 min) means during a region failover, clients may be sending traffic to a failed region for up to 5 minutes. A TTL of 30s means more DNS load but faster failover propagation.

**Minimal solution**: Low-TTL DNS with manual failover. Point your domain at one IP, manually change DNS if that region fails. Works until: the region fails during a time when no one is watching, or your TTL is too high and the failover propagation is too slow.

**Generalize**: GeoDNS (Route 53 latency-based routing). The authoritative DNS server knows your resolver's approximate location and returns the IP of the lowest-latency endpoint. Health checks on each endpoint. If 3 consecutive checks fail, that endpoint is removed from DNS. With TTL=60, all clients re-resolve within 60 seconds.

```
User request hits DNS resolver → DNS resolver queries authoritative DNS
Authoritative DNS knows resolver's geo location
Returns IP of nearest healthy endpoint

AWS Route 53 Latency-Based Routing:
  DNS measures actual network latency from resolver → each AWS region
  Returns IP of lowest-latency region (not just geographically nearest)
  
  User in Frankfurt:
    us-east-1: 90ms RTT
    eu-west-1: 12ms RTT  ← returned
    ap-northeast-1: 210ms RTT

Anycast:
  Multiple datacenters share the same IP address (BGP announces same /24)
  Internet routing protocol delivers packet to nearest datacenter automatically
  Used by CDNs (Cloudflare, Akamai) and DNS providers
  No DNS TTL issues — routing is at network layer
```

**Health check integration**:
```
Route 53 monitors each region endpoint every 30 seconds
If 3 consecutive health checks fail → remove from DNS rotation
DNS propagation with TTL=60: all clients re-resolve within 60 seconds
During failover: traffic shifts to remaining healthy regions
```

---

## 4. Data Replication Across Regions

**Question**: You have a primary in US-EAST with synchronous replication to EU-WEST. Every write must wait for EU-WEST to acknowledge before returning to the client. Measured: writes that used to take 5ms now take 60ms (5ms + 54ms RTT + 1ms for EU-WEST to write to WAL). Your p99 write latency jumped 12×. How do you decide between synchronous (zero data loss, high write latency) and asynchronous (low latency, possible data loss on failover)?

**Physical constraint**: Synchronous replication requires waiting for the ACK to travel the full network RTT before returning to the client. That latency is irreducible — it's bounded below by the speed of light. For US to EU that's ~54ms. You cannot have both zero-RPO cross-region replication and low write latency. These are mutually exclusive for any non-zero geographic distance.

**Minimal solution**: Accept the latency. Use synchronous replication only for the primary to its nearest standby (same region, ~1ms RTT). Use async replication to remote regions. Your RPO = replication lag to remote regions (typically seconds).

**Generalize**: Tiered replication. Within a region: synchronous to a local standby (sub-millisecond RTT, zero RPO). Across regions: asynchronous. Tune RPO vs write latency per business requirement. Financial systems may need zero RPO across regions — they pay the 50ms write latency tax. Most applications do not need this.

### Database Replication Modes

| Mode | Latency | Consistency | Data Loss Risk |
|------|---------|-------------|----------------|
| **Synchronous** | High (+RTT) | Strong (no loss) | Zero |
| **Asynchronous** | Low (fire+forget) | Eventual | Seconds of loss on failure |
| **Semi-synchronous** | Medium | Near-strong | Sub-second loss |

**PostgreSQL cross-region replication**:
```
US-EAST (Primary):
  postgresql.conf:
    synchronous_standby_names = 'eu-west1'  # Optional: sync to EU
    wal_level = logical  # For logical replication across major versions

EU-WEST (Standby):
  recovery.conf:
    primary_conninfo = 'host=us-east.rds.amazonaws.com ...'
    recovery_target_timeline = 'latest'

Streaming replication:
  Changes written to WAL → streamed to standby → applied
  Replication lag: typically 10-100ms cross-region (async mode)
```

### Object Storage (S3) Replication

```
S3 Cross-Region Replication (CRR):
  Source bucket: s3://media-us-east-1
  → Async replication to s3://media-eu-west-1

Replication time:
  99% of objects replicated within 15 minutes
  S3 Replication Time Control (RTC): guaranteed within 15 minutes (SLA-backed)

Use for:
  User-uploaded content (images, videos)
  Application artifacts (ML models, feature stores)
  Audit logs (compliance requirement: logs in each jurisdiction)
```

---

## 5. CRDTs for Conflict-Free Multi-Region Writes

**Question**: You have a page-view counter replicated across US-EAST and EU-WEST. Both regions receive increments simultaneously. EU-WEST goes from 100 to 105 (5 increments). US-EAST goes from 100 to 103 (3 increments). After replication, what should the counter be? 108. But naive replication would give you 105 or 103 depending on which replica "wins". How do you design a data structure that always converges to the correct value regardless of replication order?

**Physical constraint**: With async replication, any two replicas can independently receive writes during the replication lag window (tens to hundreds of milliseconds cross-region). There is no way to prevent this with low write latency. The data structure itself must be designed to merge correctly.

**Minimal solution**: Last-write-wins. The replica with the higher timestamp "wins." Simple but loses data — the 5 EU increments and 3 US increments should both be counted, but LWW keeps only one side.

**Generalize**: CRDTs. A G-Counter gives each node its own counter slot. Merge = max per slot. Total = sum of all slots. Each region's increments are tracked independently; merge is always correct; convergence is mathematically guaranteed.

```
Problem: Two regions increment a counter simultaneously.
  EU-WEST: counter = 5 → increments to 6, replicates to US-EAST
  US-EAST: counter = 5 → increments to 6, replicates to EU-WEST
  After replication: both think counter = 6, but actual value = 7

CRDT G-Counter (Grow-only Counter):
  Each node maintains its own count:
  EU-WEST: {eu: 1, us: 0}  → total = 1
  US-EAST: {eu: 0, us: 1}  → total = 1
  After replication: both have {eu: 1, us: 1} → total = 2 ✓

  Merge operation: take max of each node's count per entry
  Mathematical guarantee: always converges to correct value

CRDT types used in production:
  G-Counter: page view counts, likes (increment only)
  PN-Counter: inventory (increment + decrement)
  OR-Set: shopping cart (add + remove with conflict-free merge)
  LWW-Element-Set: user preferences (last write wins per key)

Used by: Riak, Cassandra (tunable), Redis Enterprise, Aerospike
```

---

## 6. Consistency Models Across Regions

**Question**: After a cross-region write, you read back from a different region 50ms later. Do you see your write? The answer depends on your consistency model. What does each model guarantee, and what is the corresponding write latency tax?

**Physical constraint**: To guarantee that a read in any region reflects the most recent write globally, the write must not be acknowledged until a quorum of nodes across all regions has persisted it. That quorum round-trip is bounded below by the speed-of-light RTT between your most distant regions. For a global system, that's 100–200ms. Every strong-consistency write pays this price.

**Minimal solution**: Eventual consistency. Write locally, replicate async. Reads may be stale by the replication lag (seconds). Zero latency tax on writes. The cost: users can see stale data, and you need to design your application and UX around that.

**Generalize**: Choose the weakest consistency model that the business requirement tolerates. User preferences: eventual (a few seconds of staleness is fine). Financial ledger: strong (no staleness tolerable). User's own writes: session consistency (user must see their own writes, others can lag).

```
Strong Consistency (linearizability):
  Every read returns the most recent write — globally.
  Implementation: Raft/Paxos quorum across ALL regions
  Cost: Write must wait for quorum ACK across regions → +100-200ms latency
  Google Spanner, CockroachDB, YugabyteDB achieve this with GPS/TrueTime

Eventual Consistency:
  Reads may return stale data; system converges over time (seconds)
  Cost: Zero extra latency for writes (local write, async replicate)
  Risk: User A writes; User B reads from different region → sees old value
  Cassandra, DynamoDB (default), Riak

Causal Consistency:
  Reads always see writes that causally preceded them.
  If User A updates profile → User A's subsequent reads see the update
  Other users may see the old value
  Implementation: Vector clocks or Lamport timestamps on writes
  MongoDB (sessions), DynamoDB (strongly consistent reads per item)

Session Consistency (Read-your-writes):
  Each user's reads see their own writes.
  Route user's reads to the same region as their writes for a session
  OR route to primary for reads immediately after a write
  Good middle ground for user-facing applications
```

---

## 7. Regional Data Sovereignty (GDPR, Data Residency)

**Question**: You're building for global users. A German user signs up. Their name, email, and address are EU personal data under GDPR. If you replicate that row to US-EAST for performance, you've transferred EU PII outside the EU — potentially a GDPR violation. How do you architect a globally distributed system while keeping specific users' data in specific regions?

**Physical constraint**: You cannot build a global system where all data is physically collocated in all regions — data has to live somewhere. Data residency means you must track which data belongs to which user, and which user belongs to which legal jurisdiction, and ensure that data never leaves its legal jurisdiction.

**Minimal solution**: Tag each user record with a `data_region` field at signup. Route all reads and writes for EU users through the EU region only. Don't replicate EU rows outside EU. Works until: you need to run a global analytics query (you can't, by design), or a user travels and their "home region" is ambiguous.

**Generalize**: Regional key management. Even if data is accidentally replicated (a bug, a misconfiguration), if the encryption keys for EU data live only in an EU KMS, the data is unreadable outside EU. Defense in depth: routing + no-replication policy + encryption with regional keys.

```
GDPR requires: EU personal data must not be transferred outside EU without safeguards.
LGPD (Brazil), PDPA (India), PIPL (China): similar requirements

Architecture patterns:

1. Region-pinned user records
   Each user tagged with data_region: 'EU' | 'US' | 'APAC'
   All writes for EU users → EU region only
   EU users' data NEVER replicates outside EU
   Non-personal metadata (analytics aggregates) may be global

2. Federated identity
   Authentication service: global (auth tokens are not PII)
   User profile service: region-specific (name, email, address = PII)
   App queries profile → routed to user's home region

3. Encryption with regional key management
   EU data encrypted with keys in AWS EU Key Management Service (KMS)
   US region physically cannot decrypt EU keys
   Even if data is accidentally replicated, it's unreadable

Implementation:
  User signup: assign data_region based on IP/declared country
  Tag every DB row, S3 object, Kafka message with data_region
  API gateway: verify user is querying their home region
  Alert if any EU PII row is replicated to non-EU store
```

---

## 8. Global Load Balancing Architecture

```
Layer 1: DNS (GeoDNS / Anycast)
  Global DNS → returns nearest region's anycast IP
  TTL: 30-60 seconds for fast failover
  
Layer 2: CDN Edge (Cloudflare / Akamai)
  Static assets, edge caching, DDoS protection
  Terminates TLS at edge (~30 PoPs globally)
  
Layer 3: Regional Load Balancer (AWS ALB / GCP GLB)
  HTTP/2 load balancing within a region
  Health checks, SSL offload
  
Layer 4: Service Mesh (Envoy / Istio)
  East-west traffic within region
  mTLS, circuit breaking, retries

Full request path:
  User → DNS (10ms) → CDN edge (0-5ms cache hit OR pass-through)
       → Regional LB (1ms) → Service mesh (0-1ms) → App server
  
  For cache hit: ~15ms total
  For cache miss: ~20-50ms depending on region proximity
```

---

## 9. Multi-Region Database Decision Matrix

| Requirement | Solution | Trade-off |
|------------|----------|-----------|
| Read latency < 10ms globally | Local read replicas per region | Stale reads (eventual) |
| Write latency < 50ms globally | Active-active (Cassandra/DynamoDB) | Conflict resolution complexity |
| Zero data loss on region failure | Synchronous multi-region replication (Spanner) | 20-50ms added write latency |
| GDPR data residency | Region-pinned storage, no cross-region replication of PII | Cannot serve EU users from US during EU outage |
| High write throughput, global | Cassandra with LOCAL_QUORUM | Replication lag, eventual consistency |

---

## 10. Failover Runbook

```
Region failure detected:
  P0 Alert: "us-east-1 health checks failing for > 60 seconds"

Automated (within 2 minutes):
  1. Route 53 removes us-east-1 from DNS rotation
  2. Traffic shifts to eu-west-1 (next lowest latency for affected users)
  3. eu-west-1 auto-scales to handle additional load

Manual verification (within 5 minutes):
  1. Confirm it's a region failure, not a fluke (CloudWatch metrics)
  2. Check eu-west-1 replication lag at time of failure → estimate RPO
  3. Notify on-call team: "us-east-1 down, RPO = ~30 seconds"

If active-passive setup — promote standby:
  4. Run promote script: makes eu-west-1 primary (stop replication, accept writes)
  5. Update database connection strings (via Consul/SSM Parameter Store)
  6. Verify: smoke test critical write paths

Recovery (when us-east-1 recovers):
  7. Start us-east-1 in standby mode (replicate from eu-west-1)
  8. Monitor replication lag until caught up
  9. Controlled failback: shift traffic gradually 10% → 25% → 50% → 100%
```

---

## Quick Revision

- **Active-active**: All regions serve writes, async replication, CRDTs for conflict resolution.
- **Active-passive**: Primary + standbys, promotes on failure, simpler consistency, failover = 1-3 min.
- **GeoDNS**: Routes users to nearest healthy region; TTL = 30-60s for fast failover.
- **Data sovereignty**: Region-pinned PII, KMS keys per region, no cross-region replication of personal data.
- **CRDT**: Math-based merge that always converges — no conflicts in multi-writer systems.
- **Consistency choice**: Spanner for global strong consistency (+ latency cost); Cassandra/DynamoDB for eventual (+ conflict handling).
- **Interview**: "I'd start active-passive for simplicity — primary in US-EAST with read replicas in EU-WEST and AP-NORTHEAST. Users in each region read locally with ~50ms replication lag accepted. Writes from all regions go to the primary (+100ms for EU/AP users). When we outgrow this — either for write latency or to handle primary region outages more gracefully — we'd evaluate active-active with CRDTs for non-critical data and Spanner for financial data."
