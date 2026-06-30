> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** How microservices find each other in a world where IP addresses change every 5 minutes.
>
> **Key topics:**
> - **The Problem:** In the cloud, servers are constantly dying and being replaced. If the Payment Service is at IP `192.168.1.5` today, it might be at `10.0.0.9` tomorrow. How does the Cart Service know where to find it?
> - **Service Registry:** The "Yellow Pages" of your cloud. Every time a new server spins up, it calls the Registry and says, "Hi, I'm a Payment Server, here is my IP!"
> - **Client-Side Discovery:** The Cart Service asks the Yellow Pages for the address, and then calls the Payment Service directly.
> - **Server-Side Discovery:** The Cart Service asks a Load Balancer, and the Load Balancer checks the Yellow Pages and forwards the call.
>
> **Key takeaway:** Hardcoding IP addresses in your code is a guaranteed way to break your system. Service Discovery automates the process of tracking which servers are alive and where they live.

---
module: 02-building-blocks
status: unread
tags: [02-building-blocks, system-design, networking]
---
# Service Discovery - System Design Guide

> This guide explains how dynamic microservices locate each other using simple analogies.

---

## 🤷‍♂️ Why Should I Care?

In the old days, you bought a physical metal server, plugged it into the wall, and gave it an IP address (like `10.0.0.5`). It sat in a closet for 10 years, and its IP address never changed. You could hardcode `10.0.0.5` directly into your application code, and it worked fine.

Today, we use the Cloud (AWS, Kubernetes). 
In the cloud, servers are "ephemeral" (temporary). If traffic spikes at 2:00 PM, AWS might automatically create 50 brand-new Payment Servers. At 3:00 PM, it might destroy 40 of them to save money. 

Every time a server is created, it gets a random, unpredictable IP address. 

If the Shopping Cart Service needs to talk to the Payment Service, how does it know what the IP address is? You can't hardcode it, because it changes every hour! This is the problem **Service Discovery** solves.

---

## 📖 The Service Registry (The Yellow Pages)

The core of Service Discovery is the **Service Registry** (Tools like Consul, Zookeeper, or Eureka).

> **💡 Analogy:** Moving to a new city before cell phones existed. If your friend moved to a new apartment every single day, you could never find them. But what if there was a magical Phonebook? Every time your friend moved, they called the Phonebook company and updated their address. Whenever you wanted to visit, you just checked the magical Phonebook.

**How it works:**
1. A new Payment Server boots up. It is assigned IP `10.0.5.20`.
2. The server instantly sends a message to the Service Registry: *"Hi! I am a Payment Server, and my IP is 10.0.5.20."*
3. The Service Registry writes this down in its database. 
4. When the Payment Server is destroyed, the Registry crosses it off the list.

Now we have a perfectly updated list of every server in the world. How do other services read it? There are two ways:

---

## 🕵️‍♂️ Client-Side vs Server-Side Discovery

### 1. Client-Side Discovery
> **💡 Analogy:** You want to order a pizza. You open the Yellow Pages, find the phone number for Domino's, and dial the number yourself.

- **How it works:** The Shopping Cart Service reaches out to the Service Registry and says, "Give me a list of all healthy Payment Servers." The Registry replies with 10 IP addresses. The Shopping Cart uses its own internal logic to pick one, and calls it directly.
- **Pros:** Fast. No middleman slowing down the actual connection.
- **Cons:** You have to write complicated code inside the Shopping Cart service to handle the list of 10 IPs and choose one. 

### 2. Server-Side Discovery
> **💡 Analogy:** You want to order a pizza. You call a 1-800 Concierge Service. You say, "I want Domino's." The Concierge looks up the number in the Yellow Pages, dials it for you, and connects the call.

- **How it works:** The Shopping Cart Service simply talks to a Load Balancer. The Load Balancer talks to the Service Registry, gets the IP, and forwards the traffic. 
- **Pros:** Extremely easy for developers. The Shopping Cart Service just blindly sends a message to the Load Balancer and doesn't have to write any complex routing code. 
- **Cons:** The Load Balancer is a middleman, which adds a tiny bit of latency. (This is how AWS Elastic Load Balancers and Kubernetes work natively).

---

## 🩺 Heartbeats (How do we know they are alive?)

What if a Payment Server crashes, but it dies so fast it doesn't have time to tell the Service Registry to cross its name off the list? 
If the Registry still thinks the dead server is alive, it will give that IP to the Shopping Cart, and the transaction will fail!

**The Fix: Heartbeats.**
> **💡 Analogy:** A scuba diver holding a rope. Every 5 seconds, the diver tugs the rope so the person on the boat knows they are alive. If 15 seconds go by with no tug, the person on the boat assumes the diver is in trouble. 

Every microservice must send a "Heartbeat" ping to the Service Registry every few seconds. If the Registry doesn't receive a ping for 30 seconds, it assumes the server crashed and automatically deletes it from the Yellow Pages.

---

## 🎤 Interview Questions to Practice

1. **"Why can't we just hardcode IP addresses in a microservices architecture?"**
   *Answer:* Because modern cloud infrastructure is ephemeral. Virtual machines and containers are constantly created and destroyed based on auto-scaling rules or hardware failures. IP addresses are entirely unpredictable.
2. **"What is a Service Registry?"**
   *Answer:* It is a centralized database (like Consul or Zookeeper) that keeps a live, constantly updated directory of every healthy microservice and its current IP address. 
3. **"What is the difference between Client-Side and Server-Side discovery?"**
   *Answer:* In Client-Side, the microservice queries the registry itself and directly connects to the target. In Server-Side, the microservice sends the request to a Load Balancer or API Gateway, which handles querying the registry and forwarding the request. Server-side is generally preferred as it removes complexity from the application code.
