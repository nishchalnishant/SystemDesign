---
module: 09-patterns
status: unread
tags: [09-patterns, system-design, patterns]
---
# System Design Anti-Patterns

> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** The most common mistakes engineers make when designing complex systems. Knowing what *not* to do is just as important as knowing what to do.
>
> **Key concepts:**
> - **Distributed Monolith:** Splitting an app into microservices, but keeping them so tangled together that if one fails, they all fail. 
> - **Shared Database:** Multiple microservices reading and writing to the exact same database tables.
> - **N+1 Queries:** Making 100 small trips to the database instead of 1 big trip.
> - **Thundering Herd:** When a broken server comes back online, and 10,000 waiting users hit it at the exact same millisecond, crashing it again.
>
> **Key takeaway:** Senior engineers are defined by the anti-patterns they avoid. In an interview, spotting these traps and explaining why they are bad is a massive green flag.

---

## 1. The Distributed Monolith

### 🏃‍♂️ The Three-Legged Race Analogy

Imagine you have a giant, heavy backpack (a Monolith) that is hard to carry. 
To fix this, you decide to give 8 different people a small piece of the backpack (Microservices). 
But then, you tie all 8 of their legs together. 

If one person trips, they all fall down. If they want to walk forward, all 8 people have to coordinate their steps perfectly. You have added all the complexity of coordinating 8 people, but you haven't actually gained any speed or independence.

**In software:**
You split your code into 8 microservices. But:
- You still deploy all 8 at the exact same time.
- If the User Service goes down, the Checkout Service crashes.
- They all share the exact same database.

**The Fix:** True microservices must be completely independent. You must be able to deploy the Checkout Service on a Tuesday at 2 PM, while the User Service is completely broken, and Checkout should still work fine.

---

## 2. The Shared Database

### 👨‍🍳 The Shared Cutting Board Analogy

Imagine two chefs in a restaurant kitchen. One is chopping raw chicken. The other is chopping fresh strawberries. Instead of giving them each their own cutting board, you force them to share the exact same cutting board at the exact same time.

Inevitably, the strawberry chef is going to end up with raw chicken on their fruit. 

**In software:**
You have an Orders Microservice and an Inventory Microservice. They both read and write directly to the exact same SQL database table. 
- If the Inventory team changes the name of a column, the Orders code instantly crashes.
- If the Inventory team writes a heavy query, the database slows down, and Orders start failing.

**The Fix:** Every microservice gets its own, private database (its own cutting board). If the Inventory service wants to know about an Order, it is absolutely *not* allowed to look in the Orders database. It must politely ask the Orders Service via an API call.

---

## 3. Synchronous Call Chains

### 🎯 The Line of Dominos Analogy

Imagine you are ordering a pizza. To make the pizza, the cashier must talk to the chef, who must talk to the farmer, who must talk to the cow to get the cheese. You have to stand at the register and wait for this entire chain of communication to finish before you get your receipt. 

If the cow is asleep, the farmer is stuck. Because the farmer is stuck, the chef is stuck. Because the chef is stuck, the cashier is stuck. Because the cashier is stuck, *you* are stuck. 

**In software:**
The user clicks "Buy". 
1. The API calls the Order Service.
2. Order Service waits for the Payment Service.
3. Payment Service waits for the Email Service. 

If the Email Service is slow, the entire chain freezes. The user stares at a loading spinner for 30 seconds until the website crashes.

**The Fix:** Break the chain using Asynchronous Queues. The user clicks "Buy". The Order Service takes their money and instantly says "Success!" (returning the receipt). Behind the scenes, it drops a sticky note in a queue saying "Send an email later." If the Email Service is slow, nobody cares. The user already got their success message.

---

## 4. The God Service

### 🇨🇭 The 50-Pound Swiss Army Knife Analogy

A standard Swiss Army Knife is great—it has a knife, a screwdriver, and a corkscrew. 
But imagine you kept adding to it. Now it has a hammer, a chainsaw, a microwave, and a car engine attached to it. It weighs 50 pounds. You can't fit it in your pocket. It is completely useless.

**In software:**
A startup builds one "Backend API". It handles logins. Then they add billing. Then they add PDF generation, analytics, and video encoding. 
Three years later, 50 engineers are all trying to push code to the exact same file. Every time someone adds a new feature, they accidentally break the PDF generator. Deploying the code takes 45 minutes.

**The Fix:** Split the Swiss Army Knife into a normal toolbox. Use Domain-Driven Design (DDD). Group related things together (e.g., all Billing code goes into a Billing Service). 

---

## 5. The N+1 Query Problem

### 🍎 The Grocery Store Analogy

Imagine you are baking a pie and you need 100 apples. 
Instead of taking a basket, walking into the grocery store, putting 100 apples in the basket, and walking home (1 trip)...
You walk to the store, buy 1 apple, and walk home. Then you walk back to the store, buy 1 apple, and walk home. You do this 100 times. 

**In software:**
You want to show a list of 100 recent orders, including the name of the user who made the order.
```python
# The N+1 Mistake (101 trips to the database)
orders = database.get_100_recent_orders() # 1 trip
for order in orders:
    user_name = database.get_user(order.user_id) # 100 trips!
```
In development, with 3 orders, this looks perfectly fast. In production, with millions of users, this crashes your database instantly.

**The Fix:** Grab all the apples at once (Batching / SQL JOINs).
```python
# The Fix (2 trips to the database)
orders = database.get_100_recent_orders() 
all_user_ids = [order.user_id for order in orders]
all_users = database.get_users_by_ids(all_user_ids) 
```

---

## 6. Missing Idempotency (The Double Charge)

### 💳 The Slow Credit Card Machine Analogy

You go to buy a coffee. You swipe your credit card. The machine says "Loading..." for 60 seconds and then times out. The cashier says, "Oops, it didn't work. Swipe it again." You swipe it again, and it works.
The next day, you check your bank account. You were charged twice for the same coffee. The first swipe *did* work behind the scenes, it was just too slow to tell the cashier.

**In software:**
If a user's internet drops while they are placing an order, their phone will automatically retry the request. If your server is not smart enough to realize "I already processed this exact order 2 seconds ago", you will charge the user twice and ship them two items.

**The Fix:** **Idempotency Keys**. 
When the user clicks "Buy", their phone generates a unique random ID (e.g., `Order-12345`). The server saves `Order-12345` in the database. If the phone retries and sends `Order-12345` again, the server checks the database, sees the ID already exists, and says "I already did this, ignore."

---

## 7. Premature Optimization

### 🚌 The 100-Seat Bus Analogy

Imagine a couple just got married and wants to buy a car. The husband says, "Well, we might have 8 kids someday, and they might all have friends. We need to be prepared!" So he buys a 100-seat commercial diesel bus. It costs $500,000, gets 3 miles to the gallon, and they can't park it at their house. 

**In software:**
A startup with 500 users decides to build a massive, complex, auto-scaling Kubernetes microservice architecture using Apache Kafka and Cassandra because "we might have 100 million users someday." 
They spend 6 months building infrastructure instead of building the product. They run out of money and go bankrupt before they reach 2,000 users.

**The Fix:** Build what you need *now*. Start with a simple Monolith and a standard PostgreSQL database. Instagram ran on a single server for its first 13 million users. Only add complexity when you have actual, mathematical proof that your current system is failing.

---

## 8. The Thundering Herd

### 🚪 The Black Friday Doors Analogy

Imagine a popular store on Black Friday. There are 10,000 people waiting outside. If you open a normal-sized door, all 10,000 people try to sprint through at the exact same millisecond. They get stuck in the doorframe, people get trampled, and nobody gets inside. 

**In software:**
Your database goes offline for 60 seconds. While it's offline, 10,000 user requests pile up in a waiting line. The second the database comes back online, all 10,000 requests hit it at the exact same millisecond. The database is instantly overwhelmed and crashes again. 

**The Fix:** **Jitter**. 
Instead of having everyone retry at the exact same time, you add randomness. You tell user 1 to retry in 1 second, user 2 to retry in 3.4 seconds, user 3 to retry in 7.1 seconds. This spreads the herd out into an orderly line.

---

## 9. Chatty Services

### ☎️ The "10 Phone Calls" Analogy

Imagine you need to ask your mom for a recipe. 
- You call her, ask for the flour amount, and hang up. 
- You call her back, ask for the sugar amount, and hang up. 
- You call her back, ask for the oven temperature, and hang up.
This takes forever because you spend most of your time dialing the phone and saying "Hello".

**In software:**
To load a user dashboard, the frontend makes 15 different API calls to 15 different microservices (`/get-name`, `/get-balance`, `/get-history`). Every single API call requires establishing a network connection, checking security certificates, and sending headers. 

**The Fix:** The **Backend for Frontend (BFF)** pattern. You make ONE API call to a specific aggregator server (`/get-dashboard`). That server quickly gathers all the info internally (because servers talking to servers is blazing fast) and returns one neat package to the user's phone.

---

## Quick Reference: Anti-Pattern Checklist

| Anti-Pattern | ELI5 Analogy | The Fix |
|-------------|-------------|-----|
| **Distributed Monolith** | Tying the legs of 8 runners together. | Ensure services can be deployed entirely independently. |
| **Shared Database** | Two chefs sharing one cutting board. | Database-per-service pattern. |
| **Sync call chains** | A line of dominos. | Use Async queues (Kafka/SQS) for non-critical tasks. |
| **God Service** | A 50-pound Swiss army knife. | Split by business domain (DDD). |
| **N+1 Queries** | 100 trips to the grocery store for 100 apples. | Batch queries / SQL JOINs. |
| **Missing idempotency** | Double charging a credit card. | Use unique Idempotency Keys from the client. |
| **Premature optimization**| Buying a 100-seat bus for a family of 2. | Start simple. Measure first. Add complexity later. |
| **Thundering herd** | 10,000 people rushing a door at once. | Add random "Jitter" to retry times. |
| **Chatty services** | Calling mom 10 times for 1 recipe. | Aggregation APIs (BFF or GraphQL). |
