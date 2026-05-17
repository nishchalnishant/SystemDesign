# Global Distribution & Multi-Region Architecture

> **How to architect systems that operate across geographies with low latency, high availability, and data sovereignty compliance.**

---

## 1. Why Multi-Region?

**Three distinct motivations — each drives different architecture:**

| Motivation | Goal | Solution |
|------------|------|----------|
| **Latency** | Serve users from the nearest datacenter | Active-active, all regions serve traffic |
| **Availability** | Survive a full region outage | Active-passive failover |
| **Data sovereignty** | EU user data stays in EU (GDPR, etc.) | Data residency — region-pinned storage |

> **Analogy**: A multinational bank. Branches exist in London, New York, and Singapore. For speed, customers transact at their local branch. For resilience, if London burns down, customers can still access records from the backup vault in Frankfurt. For compliance, European account data must legally stay in Europe.

---

## 2. Active-Active vs Active-Passive

### Active-Active (Multi-Master)

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
