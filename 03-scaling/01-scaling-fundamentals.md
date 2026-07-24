> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** How to grow your application from 10 users to 10 million users without it crashing.
>
> **Key topics:**
> - **The Problem:** A single computer can only do so much work. When you get too popular, your server crashes.
> - **Vertical Scaling (Scaling Up):** Buying a bigger, faster, more expensive computer. (Like replacing a bicycle with a Ferrari). It's easy, but has a hard limit.
> - **Horizontal Scaling (Scaling Out):** Buying 100 cheap computers and making them work together. (Like having 100 bicycles). It's infinitely scalable, but much harder to manage.
> - **Stateful vs Stateless:** To scale horizontally, your servers must be "Stateless" (they cannot remember who you are).
>
> **Key takeaway:** Never try to scale a Stateful server. Always separate your App Servers (Stateless) from your Database (Stateful), and scale them independently.

---
module: 03-scaling
status: unread
tags: [03-scaling, system-design, scaling]
---
# Scaling Fundamentals - System Design Guide

> This guide explains the core principles of scaling applications using simple analogies.

---

## 🤷‍♂️ Why Should I Care?

Imagine you run a pizza delivery business out of your kitchen. You have one oven. You can bake 5 pizzas an hour. 
Suddenly, you get a contract to supply pizza for the Super Bowl. You need to bake 50,000 pizzas in one hour. 

Your single oven (Server) cannot physically do it. It will break. 
You have to **Scale** your business. 

In System Design, "Scaling" just means figuring out how to handle more traffic, more data, and more users without your app slowing down or crashing. If you don't build your app to scale from Day 1, you will have to rewrite the entire codebase when you go viral on TikTok.

---

## 📈 Vertical vs Horizontal Scaling

There are exactly two ways to solve the pizza problem.

### 1. Vertical Scaling (Scaling Up)
> **💡 Analogy:** You tear down your kitchen wall, throw away your normal oven, and buy a $500,000 industrial mega-oven that can bake 500 pizzas at once.

- **What it is in tech:** Taking your one server, opening it up, and putting in 10x more RAM and a 100-core CPU. (e.g., Upgrading an AWS EC2 instance from `t2.micro` to `c5.24xlarge`).
- **Pros:** It is incredibly easy. You don't have to change a single line of your code.
- **Cons:** It has a hard ceiling. Eventually, you cannot buy a bigger CPU. Also, it is a Single Point of Failure. If the mega-oven breaks, your entire business is dead.

### 2. Horizontal Scaling (Scaling Out)
> **💡 Analogy:** You keep your normal, cheap oven. But you rent 100 cheap kitchens across the city, and hire 100 normal chefs. 

- **What it is in tech:** Buying 100 cheap, standard servers and putting a Load Balancer in front of them to distribute the work.
- **Pros:** Infinite scalability. If you need more power, just buy another cheap server. If one server catches on fire, the other 99 keep working perfectly (High Availability).
- **Cons:** It is very hard to build. You now have 100 computers that need to talk to each other and coordinate.

---

## 🧠 The Golden Rule: Statelessness

If you want to scale horizontally (100 servers), you must follow the Golden Rule: **Your App Servers must be Stateless.**

> **💡 Analogy:** Imagine you call customer service (Server A) and say, "My name is John." The agent writes "John" on a sticky note and puts it on *their* desk. The call drops. You call back, and you get a different agent (Server B). Server B says, "Who are you?" You have to start all over! This is a **Stateful** system.

A **Stateful** server remembers things locally (in its own RAM or hard drive). If you have 100 stateful servers, users will constantly get logged out because they get routed to a server that doesn't remember them.

> **💡 Analogy:** You call customer service. You say, "My name is John." The agent types "John" into a massive, shared central database. The call drops. You call back, get a new agent (Server B). Server B looks at the central database and says, "Welcome back, John!" This is a **Stateless** system.

A **Stateless** server remembers *nothing*. It saves everything to a shared external Database or Cache (like Redis). 
Because it remembers nothing, you can destroy 50 servers, create 50 new ones, and move users between them instantly without breaking anything. 

**Always make your web servers Stateless.** 

---

## 🎤 Interview Questions to Practice

1. **"What is the difference between Vertical and Horizontal Scaling?"**
   *Answer:* Vertical scaling (scaling up) means adding more power (CPU, RAM) to a single machine. It's simple but has a physical limit and creates a single point of failure. Horizontal scaling (scaling out) means adding more standard machines to a cluster and distributing the load. It is infinitely scalable and highly available, but increases architectural complexity.
2. **"Why must web servers be stateless to scale horizontally?"**
   *Answer:* If servers hold state locally (like a user's session data in RAM), then a user must always be routed to that exact same server (sticky sessions), which breaks load balancing and causes errors if that server dies. By making servers stateless, any server can handle any request by fetching the necessary state from a shared external datastore like Redis.
3. **"When would you choose Vertical Scaling over Horizontal Scaling?"**
   *Answer:* I would choose Vertical Scaling for a small, simple application where speed of development is critical, traffic is low and predictable, or when running a legacy monolithic relational database that is difficult or impossible to shard across multiple machines.

---

# 🎯 SDE-3 Deep Dive

The vertical/horizontal/stateless story is the foundation. Seniors are judged on **the laws that bound scaling, what to scale for, and where statelessness actually leaks.**

## The laws that cap your speedup

- **Amdahl's Law:** if a fraction `s` of work is serial, max speedup with N cores is `1 / (s + (1−s)/N)`. Even 5% serial work caps you at 20× no matter how many machines you add. The lesson: **find and kill the serial bottleneck** (a global lock, a single-writer DB, a coordinator) — throwing hardware at it hits a wall.
- **Universal Scalability Law (USL):** adds a **coherency cost** — beyond a point, throughput *decreases* as you add nodes, because they spend more time coordinating (cross-talk, cache coherence, lock contention) than working. Real systems have a peak N; past it, more servers = *less* throughput. This is why "just add servers" fails.
- **Little's Law:** `L = λ · W` (concurrency = arrival-rate × latency). Lets you size pools: to serve 10k req/s at 20ms each you need ~200 concurrent slots. Also shows why **latency spikes silently blow up concurrency** and exhaust thread/connection pools.

## Scale for the *dimension that's actually constrained*

"Scaling" isn't one axis. Name which one:

| Bottleneck | Scale by |
|---|---|
| Read QPS | Read replicas + cache |
| Write QPS | Sharding (split the write set) |
| Storage | Sharding / tiered storage |
| CPU-bound compute | Horizontal stateless fleet |
| Connection count | Connection pooling, L4 LB, edge termination |
| Fan-out / tail latency | Reduce dependencies, hedge requests |

The senior move: **measure to find the binding constraint, then scale that one.** Adding app servers when the DB is the bottleneck just moves the queue.

## Where statelessness leaks (and what to do)

"Stateless services" is the ideal, but state has to live *somewhere*, and some workloads are inherently stateful:

- **Sessions →** externalize to Redis/JWT. Solved.
- **WebSocket / long-lived connections** are stateful by nature — the connection *is* state pinned to one server. Scale with a connection-routing layer + pub/sub (Redis, or a gateway that tracks which server holds which connection). See [`../02-building-blocks/01-networking/06-websockets-sse.md`](../02-building-blocks/01-networking/06-websockets-sse.md).
- **Sticky sessions** are the anti-pattern to call out: they break even load distribution and lose state on instance death. Use them only as a last resort.
- **Stateful data systems** (databases, Kafka, ZooKeeper) can't be casually cloned — they scale via partitioning + replication + consensus, which is a different toolkit than stateless-fleet cloning.

## Autoscaling — the reactive-vs-predictive trap

- Reactive autoscaling (scale on CPU/latency) **lags** — new instances take minutes to boot and warm caches, so a sharp spike is served by an under-provisioned fleet. Mitigate with pre-warming, faster boot (pre-baked AMIs/containers), and headroom.
- **Predictive/scheduled scaling** for known patterns (business hours, flash sales) beats reactive.
- Scaling the stateless tier is easy; the DB rarely autoscales the same way — it's usually the real ceiling. Design the app so the DB isn't on the hot path for every request (cache, async writes).

## Interview probes you should survive

- *"You added 50 servers and throughput barely moved — why?"* → Amdahl/USL: a serial bottleneck (single-writer DB, global lock) or coordination overhead dominates. Find and remove the serial part; more nodes can even hurt (USL).
- *"How many worker threads to serve 10k req/s at 50ms latency?"* → Little's Law: 10000 × 0.05 = 500 concurrent. Size the pool to that plus headroom.
- *"WebSockets don't fit the stateless model — how do you scale them?"* → The connection is pinned state; route by connection ID, back it with pub/sub for cross-server delivery, and externalize the messaging fabric.
- *"Your autoscaler reacts too slowly to spikes — options?"* → Pre-warm/keep headroom, speed up instance boot, use predictive/scheduled scaling for known peaks, and shield the DB with caching so the spike doesn't reach it.
