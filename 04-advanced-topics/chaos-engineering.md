# Chaos Engineering & Resilience

> **For SDE-3 Interview Preparation**  
> "Stability is not a state, it's a practice."

## Table of Contents
1. [What is Chaos Engineering?](#what-is-chaos-engineering)
2. [Principles](#principles)
3. [Fault Injection Types](#fault-injection-types)
4. [Resilience Patterns](#resilience-patterns)
5. [Game Days](#game-days)
6. [Tools](#tools)

---

## File Mindmap

```
Chaos Engineering & Resilience
├── Why It Exists
│   ├── Problem → circuit breakers, retries, fallbacks exist in code; never tested in production; theory ≠ evidence
│   └── Physical limit → staging has different traffic (no real users), different load → timing-sensitive bugs only appear at production scale
├── Definition
│   ├── Discipline of controlled failure experiments to build confidence in system resilience
│   ├── NOT random destruction, NOT testing without safeguards
│   └── IS: controlled experiments, validate hypothesis, minimize blast radius
├── Principles (5 Steps)
│   ├── Step 1: Define steady state → e.g., error rate < 1%, p99 < 200ms
│   ├── Step 2: Hypothesize → "if we kill one replica, latency increases < 10%"
│   ├── Step 3: Inject fault → kill the replica
│   ├── Step 4: Verify → did the metric stay within bounds?
│   └── Step 5: Fix → if hypothesis violated, fix the weakness; without this step it's a fire drill, not engineering
├── Fault Injection Types
│   ├── Resource Exhaustion
│   │   ├── CPU Spike → stress-ng at 100% → validates autoscaler response time
│   │   ├── Memory Leak → consume RAM until OOM Killer triggers → validates graceful restart
│   │   └── Disk Fill → 100% full → validates log rotation, ENOSPC error handling
│   ├── Network Attacks
│   │   ├── Latency → +500ms delay (simulate cross-region lag) → validates timeout configs
│   │   ├── Packet Loss → drop 5% packets → validates retry logic
│   │   ├── Blackhole → drop all traffic to specific IP → validates circuit breaker
│   │   └── DNS Failure → block port 53 → validates DNS caching + fallback
│   └── Application State
│       ├── Clock Skew → change system time → breaks distributed locks, Lamport clocks
│       ├── Process Kill → kill -9 main process → validates crash recovery + restart speed
│       └── Certificate Expiry → use expired cert → validates cert monitoring + rotation
├── Resilience Patterns (revealed by chaos experiments)
│   ├── Circuit Breaker → failure rate > 50% → trip; fail fast; don't wait for timeout; Resilience4j
│   ├── Bulkhead → separate thread pools per downstream; slow Image Service ≠ exhaust User Service threads
│   ├── Retry + Jitter → wait = base * 2^attempt + Random(0,100ms); prevents thundering herd
│   └── Fallback → Recommendations down → show "Trending Now" static list; Price down → show cached price + staleness warning
├── Game Days
│   ├── Structured production experiment: preparation → execution (2-3h) → observation → reporting
│   ├── "Master of Disaster" role → dedicated observer watching metrics in real time
│   ├── Blast radius control → start single AZ, expand scope only after each level passes
│   └── Example: Redis Leader failover → expected < 30s; actual 5 min (client library didn't refresh topology) → Bug Found
├── Tools
│   ├── Chaos Monkey (Netflix) → random EC2 instance termination; the original
│   ├── Gremlin → SaaS, controlled, enterprise-grade, safe defaults
│   ├── Chaos Mesh → Kubernetes; YAML-based (PodKill, NetworkDelay)
│   ├── LitmusChaos → K8s native, declarative, cloud-native
│   └── Toxiproxy (Shopify) → network simulation; great for unit testing network partitions
├── Trade-offs
│   ├── Pro: converts "I believe this is resilient" into evidence
│   └── Con: risk of real customer impact if blast radius is not controlled
└── Interview Angles
    ├── "How do you ensure system resilience?" → design with CB + bulkheads + chaos validation
    ├── "What is a Game Day?" → structured production experiment with defined stop conditions
    └── Follow-up: how do you limit blast radius → start with 1 AZ, 1 cluster, auto-rollback on SLO breach
```

## What is Chaos Engineering?

**Question**: You have circuit breakers, retries, fallbacks, and health checks. Your architecture diagram says the system is resilient. You have never actually killed a database replica in production. You have never actually dropped 5% of network packets on your payment service. How do you know the circuit breaker actually trips at the right threshold? How do you know the fallback actually serves cached data instead of returning an error? You have a theory of resilience. How do you convert it into evidence?

**Physical constraint**: Software systems degrade in ways that cannot be fully simulated in staging or unit tests. Staging has different traffic patterns, different network topology, and different load profiles than production. A circuit breaker configured to trip at 50% failure rate in a staging environment with 10 req/sec will behave differently under 50,000 req/sec in production where thread pool dynamics, GC pressure, and connection pool exhaustion interact. The only way to know how a system behaves under failure is to introduce failure into it.

**Minimal solution**: Trust your code review and unit tests. Breaks at: unit tests do not test the interaction between a misconfigured client library timeout and an overloaded downstream. Code review cannot catch the case where two correctly-implemented services interact incorrectly under failure conditions. Integration tests in staging don't reproduce the timing and load characteristics that reveal race conditions in failover logic.

**Production generalization**: Chaos engineering is the discipline of designing controlled failure experiments, injecting them into a live system, and measuring whether the system's actual behavior matches your hypotheses. It converts "I believe this is resilient" into "I have evidence this is resilient under these specific failure conditions." The key word is *controlled*: defined hypothesis, defined blast radius, automatic stop conditions, rollback plan.

**Chaos Engineering** is the discipline of experimenting on a system in order to build confidence in the system's capability to withstand turbulent conditions in production.

**Goal**: Identify weaknesses *before* they manifest in customer-facing outages.

**It is NOT**:
- Randomly breaking things
- Testing in production without safeguards
- "Breaking things" just for fun

**It IS**:
- Controlled experiments
- Validating hypothesis ("If DB fails, cache should serve stale data")
- Minimizing blast radius

---

## Principles

**Question**: Before you break anything, what must you define — otherwise the experiment is just random destruction?

**Physical constraint**: Without a baseline, you cannot tell if the system got worse during an experiment. Without a hypothesis, you have no way to interpret what you observe. Without a stop condition, a failed experiment can cause a real customer outage. The scientific method is not bureaucracy — it is the minimum structure required to distinguish "the system handled this failure" from "the system failed but we didn't notice."

**Minimal solution**: Just kill a server and watch what happens. Breaks at: you don't know if what you're observing is normal variance or failure-caused degradation; you don't know when to stop; you don't have a hypothesis to confirm or refute.

**Production generalization**: The five steps below are the minimum viable experiment structure. Steps 1–2 happen before anything breaks. Step 5 converts observations into system improvements — without it, you have a fire drill, not engineering.

1.  **Start with Steady State**: Define "normal" behavior (e.g., < 1% error rate).
2.  **Hypothesize**: "If we kill one replica, latency will increase by < 10%".
3.  **Inject Fault**: Kill the replica.
4.  **Verify**: Did latency stay within bounds?
5.  **Fix**: If it failed, fix the weakness.

---

## Fault Injection Types

**Question**: Hardware doesn't just fail — it fails in specific ways. A disk doesn't randomly "break"; it either fills up, has high seek latency, or corrupts writes. A network doesn't just "go away"; it adds latency, drops packets, or routes to the wrong host. If you don't test the actual failure modes your hardware exhibits, your resilience tests are testing imaginary failures. What are the real failure classes?

**Physical constraint**: Each layer of the stack has its own failure vocabulary. Disk: full (4ms seek degrades to 50ms+, or ENOSPC). Memory: OOM killer starts terminating processes (not graceful shutdown). Network: packets are delayed (adding 50–500ms), dropped (creating timeout failures), or reordered (breaking stateful protocols). Clocks: drift ±200ms/day, creating incorrect ordering in distributed systems. Each of these has different observable symptoms and requires a different resilience pattern.

**Minimal solution**: Test crash-stop failures only (kill -9 a process). Breaks at: the majority of real production failures are not crash-stop. Slow network, full disk, and clock skew are all more common than hard crashes in mature systems — and harder to detect because they don't produce obvious error signals.

**Production generalization**: Match your fault injection to the failure modes your infrastructure actually exhibits. Cloud VMs have different failure characteristics than bare metal. Network partitions are more common than node crashes in AWS. The fault taxonomy below maps each fault type to the resilience pattern it validates.

### 1. Resource Exhaustion
- **CPU Spike**: Run `stress-ng` to hit 100% CPU.
- **Memory Leak**: Consume RAM until OOM Killer triggers.
- **Disk Fill**: Fill disk to 100%.

### 2. Network Attacks
- **Latency**: Add 500ms delay to all packets (simulate cross-region lag).
- **Packet Loss**: Drop 5% of packets.
- **Blackhole**: Drop all traffic to specific IP (simulate dependency down).
- **DNS Failure**: Block DNS port 53.

### 3. Application State
- **Clock Skew**: Change system time (breaking distributed locks).
- **Process Kill**: `kill -9` main process.
- **Certificate Expiry**: Use expired certs.

---

## Resilience Patterns

**Question**: Chaos engineering reveals a failure mode: when the Recommendations Service goes slow, the entire homepage API times out after 30 seconds, and all 200 thread-pool threads are consumed within 4 seconds. Which pattern fixes this, and why does adding more threads not solve the problem?

**Physical constraint**: Adding threads delays the problem by a constant factor but does not fix it. If 200 threads fill in 4 seconds, 400 threads fill in 8 seconds — the outcome is the same, just delayed. The root cause is that threads are a shared resource between all downstream calls. A fast path (user data) and a slow path (recommendations) compete for the same pool. Isolation — not more threads — is the fix.

**Minimal solution**: Increase the thread pool size and the timeout. Breaks at: you have now delayed your own OOM event and given a slow downstream more time to consume your threads. A 30-second timeout with 400 threads at 100 req/sec means 400 threads × 30 seconds = 12,000 thread-seconds of capacity consumed by one slow dependency.

**Production generalization**: Isolate failure domains. Give each downstream service its own thread pool (bulkhead). Stop calling failing services entirely after a failure threshold (circuit breaker). Make calls with strict timeouts so a slow downstream fails fast rather than slowly. Accept degraded functionality (serve cached recommendations) rather than failing the entire request.

When Chaos reveals a weakness, use these patterns to fix it.

### 1. Circuit Breaker
- **Problem**: One slow dependency cascades to entire system.
- **Solution**: If failure rate > 50%, "open" circuit and fail fast immediately. Do not wait for timeout.
- **Tools**: Netflix Hystrix, Resilience4j.

### 2. Bulkhead Pattern
- **Analogy**: Ship compartments. If one floods, ship doesn't sink.
- **Implementation**: Separate thread pools for different downstream services.
- **Benefit**: Slow "Image Service" won't exhaust threads for "User Service".

### 3. Retry with Exponential Backoff + Jitter
- **Problem**: **Thundering Herd**. If service recovers, 10,000 clients retry simultaneously -> kills it again.
- **Solution**:
    - `Wait = Base * 2^Attempt`
    - `Jitter = Random(0, 100ms)`

### 4. Fallback Strategies
- **Graceful Degradation**:
    - Recommendation Service Down? -> Show "Trending Now" (static list).
    - Price Service Down? -> Show cached price (with staleness warning).

---

## Game Days

**Question**: Your chaos experiments run automatically in CI and staging. But a Redis cluster failover test revealed a 5-minute degradation in staging — you aren't sure if the same would happen in production with real traffic patterns, real connection pool sizes, and real downstream dependencies. How do you validate resilience in production without causing a real customer-facing incident?

**Physical constraint**: Production behavior emerges from the interaction of real traffic load, real data volumes, real connection counts, and real dependency response times. Staging cannot fully replicate this because traffic patterns are different (no real users), data volumes are smaller (faster queries), and infrastructure is smaller (different connection pool saturation points). Some failure modes only appear at production scale.

**Minimal solution**: Never test in production — only use staging. Breaks at: the Redis failover bug only appears because of a specific interaction between the client library's topology-refresh interval and the connection pool exhaustion pattern under real load. Staging didn't reproduce it.

**Production generalization**: A Game Day is a controlled production experiment with defined scope, defined stop conditions, a dedicated observer monitoring metrics in real time, and a rollback plan executed if the stop condition is triggered. The blast radius is minimized by starting with a single region, a single AZ, or a single cluster — and expanding scope only after each level passes.

**Structured Chaos Event**:
1.  **Preparation**: Pick a date. Select a "Master of Disaster".
2.  **Execution**: Run experiments (2-3 hours).
3.  **Observation**: Monitory metrics/logs.
4.  **Reporting**: Document findings. Create tickets for fixes.

**Example Scenario**:
- **Role**: SDE-3 Lead.
- **Experiment**: "What happens if the primary Leader of our Redis Cluster dies?"
- **Expectation**: Automatic failover to Replica in < 30s. No data loss.
- **Reality**: Failover took 5 mins because client library didn't refresh topology. **Bug Found!**

---

## Tools

| Tool | Focus | Usage |
|------|-------|-------|
| **Chaos Monkey** (Netflix) | Random instance termination | AWS EC2 (The original) |
| **Gremlin** | SaaS Platform | Safe, controlled, enterprise grade |
| **Chaos Mesh** | Kubernetes | YAML based experiments (PodKill, NetworkDelay) |
| **LitmusChaos** | Kubernetes | Cloud-native, declarative |
| **Toxiproxy** (Shopify) | Network simulation | Great for unit testing network partitions |

---

## Interview Tips

**Q: "How do you ensure your system is resilient?"**

**A:**
1.  "I design for failure using patterns like **Circuit Breakers** and **Bulkheads**."
2.  "We practice **Chaos Engineering**—running Game Days to validate our assumptions."
3.  "For Distributed Systems, I specifically test **Network Partitions** (split-brain) and **Clock Skew**."
4.  "I ensure purely **idempotent** operations so retries are safe."
