> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** How to stop a tiny failure in one service from crashing your entire company.
>
> **Key topics:**
> - **The Problem:** The "Cascading Failure." If the Email Service gets slow, the Payment Service waits for it. Then the Cart Service waits for the Payment Service. Soon, the entire system is frozen.
> - **The Solution (Circuit Breaker):** Just like the electrical box in your house. If a wire draws too much power, the breaker "trips" and shuts off the electricity to save the house from burning down. 
> - **Closed State:** Everything is normal. Traffic flows.
> - **Open State:** The service is broken. The Circuit Breaker trips and instantly blocks all traffic to that service so it has time to recover.
> - **Half-Open State:** The Circuit Breaker slowly lets 1 or 2 requests through to see if the service is fixed yet.
>
> **Key takeaway:** In microservices, services *will* fail. A Circuit Breaker accepts the failure gracefully instead of letting it destroy the rest of the architecture.

---
module: 02-building-blocks
status: unread
tags: [02-building-blocks, system-design, performance]
---
# Circuit Breakers - System Design Guide

> This guide explains how to prevent cascading failures using simple analogies.

---

## 🤷‍♂️ Why Should I Care?

Imagine an assembly line. 
Person A packs a box, hands it to Person B. Person B tapes it, and hands it to Person C. Person C puts a label on it. 

Suddenly, Person C runs out of labels. He stops to look for more. 
Because Person C stopped, Person B is standing there holding a taped box, waiting. 
Because Person B is waiting, Person A is standing there holding an untaped box, waiting. 

Because one person ran out of labels, the *entire factory* has ground to a halt. This is called a **Cascading Failure**.

In System Design, this happens when the `Search Service` calls the `Database`. If the Database is slow (taking 30 seconds to reply instead of 1 second), the Search Service just sits there waiting. Soon, all the RAM on the Search Service is used up by waiting users, and it crashes. 

To fix this, we use a **Circuit Breaker**. 
If the Database takes 30 seconds to reply, the Circuit Breaker says, "Nope! The Database is broken. Stop waiting. Just show the user an error immediately." It sacrifices the Search feature to save the rest of the factory.

---

## 🔌 How it Works (The 3 States)

Just like the electrical breaker box in your house, a software Circuit Breaker (like Resilience4j) has three states:

### 1. CLOSED (Everything is normal)
- **What it means:** Electricity is flowing. 
- **How it works:** The Circuit Breaker allows all requests to pass through to the database. It is secretly counting how many requests succeed and how many fail.
- **When does it change?** If the failure rate goes over a certain threshold (e.g., 50% of requests fail in the last 10 seconds), the breaker "trips."

### 2. OPEN (The circuit is tripped)
- **What it means:** The wire is cut. Electricity stops.
- **How it works:** The Circuit Breaker instantly blocks *all* requests to the database. It doesn't even try to send them. It just immediately returns an error to the user.
- **Why?** It gives the database time to recover. If the database is struggling to survive, hitting it with 1,000 more requests will definitely kill it. 
- **When does it change?** The Circuit Breaker sets a timer (e.g., 30 seconds). When the timer expires, it moves to the next state.

### 3. HALF-OPEN (Testing the waters)
- **What it means:** We think it might be fixed, but we aren't sure. 
- **How it works:** The Circuit Breaker lets exactly 5 requests pass through to the database. 
  - If those 5 requests succeed, the breaker says, "Hooray, it's fixed!" and snaps back to **CLOSED**.
  - If any of those 5 requests fail, the breaker says, "Nope, still broken," and snaps back to **OPEN** for another 30 seconds.

---

## 🛡️ The Fallback (Plan B)

When the Circuit Breaker is **OPEN**, what does the user see?
Instead of showing a nasty "HTTP 500: Server Error" screen, good engineers write a **Fallback**.

> **💡 Analogy:** If the label maker (Person C) is broken, the Fallback plan is: "Just write the address with a Sharpie."

**Real-world examples of Fallbacks:**
- If Netflix's Recommendation Engine breaks, the Circuit Breaker trips. The Fallback plan is: Just show everyone a hardcoded list of the "Top 10 Classic Movies." The user never even notices it's broken!
- If a Shopping Cart's connection to the Pricing Database breaks, the Fallback plan is: Use the cached prices from 1 hour ago. 

---

## 🎤 Interview Questions to Practice

1. **"What is a cascading failure in a microservices architecture?"**
   *Answer:* It's when a failure or slowdown in one downstream service causes the services calling it to consume all their threads/resources waiting for a response, eventually causing those upstream services to crash as well, bringing down the whole system.
2. **"How does a Circuit Breaker prevent cascading failures?"**
   *Answer:* By monitoring the failure rate of a service. If the failure rate exceeds a threshold, the breaker "trips" into the OPEN state and immediately fails any new requests without even trying to contact the broken service. This frees up resources on the calling service and gives the broken service time to recover.
3. **"What is the HALF-OPEN state?"**
   *Answer:* After a timeout period in the OPEN state, the breaker transitions to HALF-OPEN to test if the underlying problem is fixed. It allows a small number of test requests through. If they succeed, the breaker resets to CLOSED. If they fail, it goes back to OPEN.
