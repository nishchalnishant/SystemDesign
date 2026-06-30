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
