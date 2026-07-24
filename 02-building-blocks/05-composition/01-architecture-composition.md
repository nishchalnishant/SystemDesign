> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** How to put all the building blocks together to create a final, working architecture.
>
> **Key topics:**
> - **The Goal:** You now understand Load Balancers, CDNs, API Gateways, Caches, and Message Brokers. How do they actually connect?
> - **The 3-Tier Architecture:** The classic way to build web apps (Presentation, Logic, Data).
> - **The Path of a Request:** Following a user's click from their mobile phone, through the CDN, into the Gateway, down to the Database, and back.
> - **Putting it together:** A high-level view of how a modern tech company wires these components to handle millions of users without crashing.
>
> **Key takeaway:** In an interview, nobody expects you to build a perfect system. They expect you to draw a logical flow of boxes where every box protects the next box from failing.

---
module: 02-building-blocks
status: unread
tags: [02-building-blocks, system-design, composition]
---
# Architecture Composition - System Design Guide

> This guide explains how to connect the building blocks together using simple analogies.

---

## 🤷‍♂️ Why Should I Care?

Imagine you are building a car. 
You spent all week learning how a steering wheel works (API Gateway). You learned how the transmission works (Message Broker). You learned how the gas tank works (Database). 

But if you just throw all those parts into a pile on the floor, you don't have a car. You have a pile of junk. 

In a System Design interview, the interviewer isn't testing if you know what a Cache is. They are testing if you know *where* to put the Cache. 
If you put the Cache in front of the CDN, your car is going to explode. You have to know how to connect the building blocks in the correct order. 

---

## 🏗️ The Standard 3-Tier Architecture

Almost every system in the world is built on the exact same 3 layers. 

1. **The Presentation Tier (The Front Door):** This is the mobile app, the website, and the CDN. Its only job is to look pretty and load fast.
2. **The Logic Tier (The Brain):** This is your API Gateway and your Application Microservices. Its job is to do math, check passwords, and process payments. 
3. **The Data Tier (The Filing Cabinet):** This is your Main Database and your Cache. Its only job is to save information safely.

---

## 🗺️ The Path of a Request (Tracing the Flow)

Let's trace a single click. A user opens Instagram on their phone and clicks the "Profile" button. Here is the exact path that request takes through a modern architecture:

1. **The User's Phone:** The user clicks the button.
2. **The DNS:** The phone asks the DNS Phonebook for the IP address of Instagram. 
3. **The CDN:** The request hits the global CDN first. If the user's Profile Picture is saved in the CDN, it returns it instantly! (If not, we move to step 4).
4. **The Load Balancer:** The request hits a massive Load Balancer. It looks at the 1,000 servers Instagram owns, finds one that isn't busy, and forwards the request.
5. **The API Gateway:** The request hits the API Gateway. The Gateway checks the user's VIP Wristband (JWT token) to make sure they are actually logged in. It also checks the Rate Limiter to ensure the user isn't a spam bot. It passes the test!
6. **The Application Service (Microservice):** The Gateway forwards the request to the `User Profile Microservice`. 
7. **The Cache:** The Microservice asks the Redis Cache: "Do you have this user's profile text?" 
   - *If Yes (Cache Hit):* It grabs the text and runs back to the user!
   - *If No (Cache Miss):* It moves to step 8.
8. **The Database:** The Microservice goes to the giant PostgreSQL database, finds the profile text, hands it back to the Microservice, and the Microservice saves a copy in the Cache for next time.
9. **The Message Broker (Optional):** As the profile is loading, the Microservice quietly drops a sticky note into Kafka saying: "User 123 just logged in." Later, an Analytics server will read that note and update a graph. 

The data travels all the way back up the chain, and the user's phone displays the Profile. All of this happens in less than **200 milliseconds**.

---

## 🎤 Interview Questions to Practice

1. **"Draw a high-level architecture for a standard web application."**
   *Answer:* (You should be able to draw this flow on a whiteboard: Client -> CDN -> Load Balancer -> API Gateway -> Microservices -> Cache -> Database).
2. **"Where would you place the Rate Limiter in this architecture?"**
   *Answer:* At the API Gateway (or a dedicated proxy just behind the edge load balancer). You want to drop malicious traffic as early as possible before it consumes CPU cycles on your internal microservices or database.
3. **"If the database is slow, how would you protect the microservices from a cascading failure?"**
   *Answer:* I would implement a Circuit Breaker pattern inside the Microservice, wrap the database calls with timeouts, and ensure that if the database fails, the Microservice returns a Fallback response (perhaps from the Cache) instead of hanging indefinitely.

---

# 🎯 SDE-3 Deep Dive

The happy-path flow above is table stakes. Seniors are judged on **why each box is where it is, what the request budget looks like, and how the read and write paths diverge.**

## Every box exists to shed a specific concern

The interviewer's real question is *"defend this ordering."* Each layer removes one class of work before the next, most expensive, layer sees it:

| Layer | Sheds | If you skip it |
|---|---|---|
| CDN | Static-asset traffic + DDoS volume | Origin serves every image; melts under load |
| L4 LB | Uneven connection distribution | Hot servers, cold servers |
| API Gateway | Unauthed / rate-exceeded / malformed requests | Bad traffic reaches (and can crash) services |
| Cache | Repeated identical reads | Every read hits the DB — the scarcest resource |
| Message broker | Synchronous coupling to slow work | User waits on email/analytics; failures cascade |

The principle to state out loud: **push work outward and drop bad/cheap traffic as early as possible, so the expensive, stateful, hard-to-scale layer (the database) sees the least.**

## The latency budget — think in a request budget

A senior traces the flow *with a time budget*, not just boxes. For a 200ms p99 target:

- CDN/edge TLS: ~10–30ms (user→edge RTT)
- LB + gateway (auth, rate check): ~1–5ms
- Service logic: ~5–20ms
- Cache hit: ~1ms; **cache miss + DB: ~10–50ms** (the cliff)
- Cross-service fan-out multiplies **tail** latency — see the tail-latency math in [`../../01-foundations/01-system-design-basics/01-fundamentals.md`](../../01-foundations/01-system-design-basics/01-fundamentals.md).

The insight: your p99 is dominated by the cache-miss + DB path and by any **fan-out** (calling N services means waiting on the slowest of N). Cut tail latency by reducing fan-out, hedging requests, and raising cache hit-rate — not by shaving the median.

## Read path vs write path — they're different systems

The single flow above hides that **reads and writes compose differently**:

- **Read path:** CDN → LB → gateway → service → **cache → read replica**. Optimized for throughput and low latency; tolerates slight staleness. Scale by adding replicas + cache.
- **Write path:** LB → gateway → service → **primary DB** (single writer) → replicate out → **invalidate/update cache** → emit event to broker for downstream (search index, analytics, notifications via CDC/outbox).
- The write path is where consistency lives: cache invalidation ordering, replication lag (read-your-writes), and the dual-write problem all bite here. This is why the broker/CDC layer exists — to fan a single write out to many derived stores **asynchronously and reliably**.

## Stateless services, externalized state

The reason this composition scales horizontally: **application/logic-tier services hold no state** — session, cache, and data all live in the data tier. Any request can hit any service instance, so you scale by cloning services behind the LB. The moment a service holds local state (in-memory session, a WebSocket connection), you've introduced sticky routing and a scaling ceiling — call that out as the thing to avoid.

## Interview probes you should survive

- *"Defend this ordering — why gateway before service, cache before DB?"* → Each layer sheds a cheaper class of work so the expensive stateful layer sees the least; drop bad traffic at the edge.
- *"Where does your p99 latency actually come from?"* → Cache-miss + DB path and service fan-out (slowest-of-N). Raise hit-rate, cut fan-out, hedge — don't chase the median.
- *"How does a write propagate to search, cache, and analytics without dual-write bugs?"* → Write to the primary in one transaction, then fan out asynchronously via CDC/outbox → broker → consumers. Each derived store is eventually consistent and independently retriable.
- *"What breaks horizontal scaling of the logic tier?"* → Local state. Keep services stateless and push all state to the data tier so any instance serves any request.
