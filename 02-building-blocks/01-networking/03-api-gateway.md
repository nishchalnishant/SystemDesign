> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** The front door to your microservices. How to manage chaos when your app is split into 50 different pieces.
>
> **Key topics:**
> - **The Problem:** If you have an app made of 50 microservices (Users, Payments, Videos), the mobile app shouldn't have to memorize 50 different IP addresses to talk to them.
> - **The API Gateway:** A single "Front Desk" for your entire company. The mobile app only talks to the Front Desk, and the Front Desk routes the request to the correct department.
> - **Cross-Cutting Concerns:** Things that *every* service needs (like checking if a user is logged in, or blocking hackers). Instead of writing security code 50 times, you just put it in the API Gateway once!
> - **Rate Limiting:** The Gateway acts as a bouncer, blocking anyone who tries to send 1,000 requests per second.
> - **BFF (Backend for Frontend):** Creating a special, custom API Gateway just for mobile phones, and a different one just for laptops.
>
> **Key takeaway:** If you have microservices, you *must* have an API Gateway. It simplifies your frontend code and centralizes all your security.

---
module: 02-building-blocks
status: unread
tags: [02-building-blocks, system-design, networking]
---
# API Gateways - System Design Guide

> This guide explains the purpose of an API Gateway in a microservice architecture using simple analogies.

---

## 🤷‍♂️ Why Should I Care?

Imagine a massive hospital. It has a Cardiology department, a Neurology department, an X-Ray lab, and a Pharmacy. 

If you are a sick patient (The Mobile App), it would be incredibly annoying if you had to memorize the GPS coordinates for every single department and drive between them. 
Worse, if every single department had to hire their own personal security guard to check your ID, the hospital would waste millions of dollars on duplicate security.

**The Solution:** The hospital builds a massive "Front Desk Reception" at the main entrance. 
You walk in. You only need to know one address (The API Gateway). You show your ID to the security guard once. The receptionist looks at what you need, and walks you to the correct department. 

When you break a monolith application into Microservices, you create the exact same problem. The API Gateway is your Front Desk. 

---

## 🚪 What exactly does an API Gateway do?

An API Gateway is a piece of software (like AWS API Gateway, Kong, or Apigee) that sits between the public internet and your private microservices. It does 3 main jobs:

### 1. Request Routing (The Receptionist)
The mobile app only has to remember one URL: `api.netflix.com`.
If the mobile app asks for `/users/123`, the Gateway says, "Ah, I will forward this to the User Microservice." 
If the mobile app asks for `/billing`, the Gateway forwards it to the Payment Microservice. 

### 2. Authentication & Security (The Security Guard)
Every single microservice needs to know if the user is actually logged in. 
Instead of writing password-checking code in 50 different microservices, you put it in the API Gateway. 
The Gateway checks the user's JWT (VIP Wristband). If the token is fake, the Gateway instantly kicks the user out. The microservices behind the wall don't even have to worry about it! This is called handling a **Cross-Cutting Concern**.

### 3. Rate Limiting (The Bouncer)
If a hacker tries to launch a DDoS attack by refreshing the page 10,000 times a second, the API Gateway tracks their IP address and blocks them at the front door. The fragile microservices inside are completely protected.

---

## 📱 BFF (Backend For Frontend) Pattern

Sometimes, a single "Front Desk" isn't good enough. 
Imagine a desktop computer on a fast WiFi connection vs a 10-year-old mobile phone on a 3G cell network. 

If the Mobile App asks for a user profile, it only wants the Name and Profile Picture. 
If the Desktop App asks for a user profile, it has a giant screen, so it wants the Name, Picture, full Biography, 10 recent posts, and a list of friends. 

Instead of having one API Gateway that tries to make everyone happy, we create the **BFF Pattern**:
- You build one small API Gateway specifically designed for Mobile Apps (It strips out extra data to save battery and data limits).
- You build a second, different API Gateway specifically designed for Desktop Apps (It grabs massive amounts of data). 

---

## 🆚 API Gateway vs Reverse Proxy vs Load Balancer

These three things sound identical. In the real world, a single piece of software (like NGINX) can actually do all three jobs at the same time! But in a system design interview, you need to know the textbook definitions:

1. **Load Balancer:** A traffic cop. It doesn't care what the message says; it just splits traffic evenly across 10 identical servers so none of them crash.
2. **Reverse Proxy:** A bodyguard. It sits in front of a server to hide the server's true IP address from the internet and decrypt SSL certificates. 
3. **API Gateway:** A smart receptionist. It actively reads the URL, checks passwords, enforces rate limits, and routes traffic to completely different microservices based on what the user asked for.

---

## 🎤 Interview Questions to Practice

1. **"Why do we need an API Gateway in a microservices architecture?"**
   *Answer:* It provides a single entry point for all clients, abstracting away the complex internal architecture. It also centralizes "cross-cutting concerns" like authentication, rate limiting, and logging, so we don't have to duplicate that code in every single microservice.
2. **"What is the BFF (Backend for Frontend) pattern?"**
   *Answer:* Instead of having one massive, generic API Gateway for all clients, we create multiple, smaller API Gateways tailored to specific clients (e.g., one for iOS, one for Web). This allows the iOS gateway to compress data and aggregate requests specifically to save battery and network bandwidth on mobile devices.
3. **"Where should you validate a user's JWT token?"**
   *Answer:* At the API Gateway. Validating it at the edge prevents malicious, unauthenticated traffic from ever reaching your internal network, saving CPU resources on your backend microservices.
