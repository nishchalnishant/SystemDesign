> [!NOTE]
> ** 5-Minute Summary**
>
> **What this covers:** Why Netflix pays programmers to intentionally break their own website.
>
> **Key topics:**
> - **The Problem:** You write a backup plan for when a server catches on fire. But you never actually test the backup plan, because you are terrified of breaking the website. When a real fire happens, the backup plan fails.
> - **The Solution (Chaos Engineering):** Intentionally setting your servers on fire during the middle of the day, while all your engineers are awake and drinking coffee, to prove that your backup plans actually work.
> - **Chaos Monkey:** A famous program invented by Netflix. It randomly unplugs servers in production.
> - **The Blast Radius:** Start small. Break 1 server in a test environment. Then break 1 server in Production. Don't break 50 servers in Production on your first day.
>
> **Key takeaway:** Distributed systems are so complex that the only way to know if they are reliable is to constantly, aggressively break them on purpose.

---
module: 04-advanced-topics
status: unread
tags: [04-advanced-topics, system-design, reliability, chaos-engineering]
---
# Chaos Engineering - System Design Guide

> This guide explains the art of breaking things on purpose using simple analogies.

---

## Why Should I Care?

Imagine a school that installs a brand new, high-tech fire alarm system. They write a 50-page manual on how the students should evacuate. They pat themselves on the back and go home.
5 years later, a real fire happens. The alarm is broken. The students don't know where the exits are. Chaos ensues.

How could the school have prevented this? By running a **Fire Drill**.
Once a month, you pull the alarm on purpose. You watch the students. If they go the wrong way, you fix the signs. You do it over and over until they are perfect at it.

In System Design, this is **Chaos Engineering**.
You spent 3 weeks building a Multi-Region, Active-Active, Auto-Scaling architecture. You wrote code that says: *"If Server A dies, Server B will take over instantly."*
But how do you *know* it works?
You have to run a Fire Drill. You have to intentionally unplug Server A while real users are using your website.

---

## Chaos Monkey (The Netflix Story)

In 2010, Netflix moved their entire business to the AWS Cloud.
They realized a terrifying truth: *AWS servers crash all the time.*

If a server crashed at 3:00 AM on a Sunday, the Netflix engineers would get paged, wake up, panic, and try to fix it while millions of users couldn't watch movies.

So, Netflix invented a piece of software called **Chaos Monkey**.
Chaos Monkey's only job is to randomly turn off Netflix servers during normal business hours (Monday at 2:00 PM).

**Why is this brilliant?**
1. **Engineers are awake:** If something breaks, the team is fully caffeinated and sitting at their desks, ready to fix it.
2. **It forces resilience:** If you know that a monkey is going to randomly unplug your server tomorrow afternoon, you are going to write incredibly good code to ensure the backup server takes over instantly.
3. **The User never notices:** Because the fallback code was forced to be so good, Chaos Monkey kills servers all day long and the Netflix users never even see a loading screen!

---

## The Rules of Chaos (Blast Radius)

You do not run Chaos Monkey on Day 1. There is a scientific method you must follow to avoid actually destroying your company.

1. **Define the "Steady State":** What does a "normal" day look like? (e.g., "Our users get 0 errors, and the app loads in 1 second").
2. **Form a Hypothesis:** "If I turn off the Payment Server, the Circuit Breaker will trip, and the user will get a polite 'Try again later' message instead of a blank white screen."
3. **Run the Experiment (Minimize Blast Radius):** Do NOT turn off the Payment Server for all 10 Million users. Turn it off for just 10 users in a specific city.
4. **Observe:** Did the 10 users get the polite message?
   - *If Yes:* Awesome! Your system is resilient. Expand the blast radius and try it on 100 users.
   - *If No:* Stop the experiment immediately! You just found a bug. Fix the bug, and try again tomorrow.

---

## Interview Questions to Practice

1. **"What is Chaos Engineering?"**
   *Answer:* It is the discipline of experimenting on a distributed system in order to build confidence in the system's capability to withstand turbulent and unexpected conditions in production. It is essentially running controlled "fire drills" on your infrastructure.
2. **"Why do we run Chaos Engineering in Production instead of just a Staging environment?"**
   *Answer:* Because Staging environments are never a 100% perfect copy of Production. They don't have the same chaotic network traffic, the same massive database sizes, or the same weird user behaviors. If you only test failure in Staging, you will still be surprised by how the system actually fails in Production.
3. **"What does it mean to minimize the 'Blast Radius'?"**
   *Answer:* It means starting your chaos experiments as small as possible to limit potential damage to real users. Instead of taking down an entire data center, you start by killing a single container, or simulating latency for only 1% of the traffic. You only expand the blast radius once the system proves it can handle the smaller failure.

---

# 🎯 SDE-3 Deep Dive

Chaos Monkey + blast radius is the "why." Seniors are expected to **design a chaos program**: the failure taxonomy to inject, the automatic-abort safety machinery, how it connects to SLOs/error budgets, and where GameDays and continuous verification fit.

## The failure taxonomy — what you actually inject

"Kill a server" is one lever. A mature program injects across the whole failure surface:

| Class | Injection | Verifies |
|---|---|---|
| **Instance** | Kill a node/pod (Chaos Monkey) | Failover, self-healing, replica takeover |
| **Network latency** | Add 200ms between services | Timeouts, retries, circuit breakers ([`../01-distributed-architecture/`](../01-distributed-architecture/)) |
| **Network partition** | Blackhole traffic between AZs | Split-brain handling, quorum behavior |
| **Dependency failure** | Return errors from a downstream | Graceful degradation, fallbacks, bulkheads |
| **Resource** | Exhaust CPU/disk/memory | Autoscaling, backpressure, load shedding |
| **Region (Chaos Kong)** | Kill an entire AWS region | Multi-region failover ([`../../03-scaling/04-global-distribution.md`](../../03-scaling/04-global-distribution.md)) |
| **Clock skew** | Skew a node's clock | Time-dependent logic, cert/token expiry |

## The safety machinery — chaos without an outage

Running this in prod is only responsible with guardrails. Name them:

- **Automated abort / "big red button":** the experiment continuously watches the steady-state metric (error rate, latency SLI) and **auto-rolls-back the instant it breaches a threshold** — you don't wait for a human. This is non-negotiable for prod chaos.
- **Business-hours + on-call awareness:** run when the team is caffeinated and watching (the Chaos Monkey insight), never blind at 3am.
- **Progressive blast radius:** 1 pod → 1% traffic → 1 AZ → region, gating each expansion on a green result.
- **Steady-state as a hypothesis test:** define the SLI, predict the outcome, and treat a *surprising* result (system didn't degrade gracefully) as the bug you were hunting.

## Where it fits: SLOs, error budgets, and GameDays

- Chaos consumes **error budget** ([`01-observability`](01-observability.md) / SRE): if you're already burning budget, you *pause* chaos — it's a discretionary spend against your reliability target.
- **GameDays** are scheduled, human-in-the-loop disaster rehearsals (e.g., "we fail over the primary region at 2pm") — the manual, higher-stakes cousin of automated continuous chaos. They validate **runbooks and human response**, not just code.
- **Continuous verification** is the end state: chaos experiments run automatically in CI/CD as a resilience regression suite, so a newly introduced missing timeout or retry storm is caught before it reaches customers.

## The anti-patterns seniors call out

- Chaos with **no monitoring** — if you can't observe the steady state, you can't tell success from a real outage you just caused.
- Chaos with **no automated rollback** — you've just built an outage generator.
- Testing only **instance kill** — most real incidents are *gray failures* (latency, partial degradation, dependency brownouts), not clean crashes. Inject latency and errors, not just death.
- Running it in **staging only** — staging lacks prod's traffic, data scale, and topology, so it won't reproduce the failure modes that actually page you.

## Interview probes you should survive

- *"How do you run chaos in production without causing a real outage?"* → Small blast radius + a continuously-evaluated steady-state metric that auto-aborts on breach, run during business hours with on-call watching.
- *"What failures beyond killing a server would you inject?"* → Latency, network partitions, dependency errors, resource exhaustion, clock skew, whole-region loss — because most incidents are gray failures, not clean crashes.
- *"How does chaos relate to SLOs?"* → It spends error budget; you run it when budget is healthy and pause when you're already burning it. It's a controlled reliability investment.
- *"Chaos Monkey vs a GameDay?"* → Chaos Monkey is automated, continuous, small-scope instance kills; a GameDay is a scheduled, human-led, larger-scope disaster rehearsal that also tests runbooks and human response.
- *"Why not just test failure in staging?"* → Staging isn't a faithful copy — traffic patterns, data volume, and topology differ, so it hides the very failure modes that surface in prod.
