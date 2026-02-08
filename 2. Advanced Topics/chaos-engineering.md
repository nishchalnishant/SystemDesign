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

## What is Chaos Engineering?

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

1.  **Start with Steady State**: Define "normal" behavior (e.g., < 1% error rate).
2.  **Hypothesize**: "If we kill one replica, latency will increase by < 10%".
3.  **Inject Fault**: Kill the replica.
4.  **Verify**: Did latency stay within bounds?
5.  **Fix**: If it failed, fix the weakness.

---

## Fault Injection Types

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
