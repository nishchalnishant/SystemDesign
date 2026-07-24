> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** How to hide your servers from the internet so hackers can't attack them directly.
>
> **Key topics:**
> - **Forward Proxy vs Reverse Proxy:** A Forward Proxy hides the *user* from the internet (like a VPN). A Reverse Proxy hides the *server* from the internet. 
> - **Security:** Your actual servers sit behind a locked wall. The internet only talks to the Reverse Proxy.
> - **SSL Termination:** The Reverse Proxy handles the heavy lifting of decrypting secure traffic so your servers don't have to.
> - **Caching:** If 1,000 people ask for the same image, the Reverse Proxy saves a copy and hands it out, so your main server doesn't have to do the work 1,000 times.
>
> **Key takeaway:** Never put your backend application server directly on the public internet. Always put a Reverse Proxy (like NGINX or HAProxy) in front of it.

---
module: 02-building-blocks
status: unread
tags: [02-building-blocks, system-design, networking]
---
# Reverse Proxies - System Design Guide

> This guide explains the difference between a Forward Proxy and a Reverse Proxy using simple analogies.

---

## 🤷‍♂️ Why Should I Care?

Imagine a famous celebrity (Your Server) is sitting in a room. If you publish their exact address on the internet, thousands of fans and stalkers will show up and overwhelm them. 

Instead, the celebrity hires a **Bodyguard (The Reverse Proxy)**. 
The celebrity sits in a secret, locked room. Fans on the street can only walk up to the Bodyguard. The fan gives a letter to the Bodyguard, the Bodyguard inspects the letter to make sure it's safe, walks into the secret room, hands it to the celebrity, gets the reply, and walks back out to hand it to the fan. 

The fans never see the celebrity, and they never know the celebrity's true address. 

In System Design, putting your actual application server directly on the public internet is incredibly dangerous. Hackers will find it and attack it. Instead, we use a Reverse Proxy.

---

## 🔄 Forward Proxy vs Reverse Proxy

People get these two confused all the time. It all comes down to *who* is being hidden.

### 1. The Forward Proxy (Hiding the User)
> **💡 Analogy:** A VPN (Virtual Private Network) or a Corporate Firewall. You are trying to access Facebook from your work computer, but your boss blocked it. So, you connect to a Forward Proxy in another country, and *it* connects to Facebook for you.

- **Who is hidden?** The User (The Client).
- **How it works:** Facebook thinks it's talking to a random computer in Canada. It has no idea who you actually are. 

### 2. The Reverse Proxy (Hiding the Server)
> **💡 Analogy:** Calling a company's 1-800 Customer Service number. You think you are calling "The Company," but actually, you are talking to a receptionist who transfers you to a random employee in a back room. You don't know the employee's direct phone number.

- **Who is hidden?** The Server.
- **How it works:** The user thinks they are talking directly to `google.com`. But actually, they are talking to a Reverse Proxy, which is secretly fetching the data from a hidden internal server.

---

## 🛡️ What else does a Reverse Proxy do?

A Reverse Proxy (like NGINX) doesn't just pass messages back and forth. Because it sits at the front door, it can do a lot of heavy lifting to make your actual servers faster.

### 1. SSL Termination (Decrypting Traffic)
Encrypting and decrypting data (HTTPS) takes a lot of math and CPU power. 
If your main server has to decrypt every single message, it will slow down. 
Instead, the Reverse Proxy decrypts the traffic at the front door (SSL Termination). Then, it sends the plain, unencrypted message to your server over your private, secure internal network. Your server saves massive amounts of CPU power!

### 2. Static Content Caching
If 10,000 users visit your website and download the exact same `logo.png` image, your main server shouldn't have to fetch that image from the hard drive 10,000 times. 
The Reverse Proxy can save a copy of the image (Caching). When the next 9,999 users ask for the logo, the Reverse Proxy just hands them the copy instantly. The main server doesn't even know it happened!

### 3. Compression
Before sending a massive HTML file back to the user, the Reverse Proxy can zip it up (GZIP) so it downloads faster on a mobile phone.

---

## 🎤 Interview Questions to Practice

1. **"What is the difference between a Forward Proxy and a Reverse Proxy?"**
   *Answer:* A Forward Proxy sits in front of the *client* to hide the client's identity from the internet (like a VPN). A Reverse Proxy sits in front of the *server* to hide the server's internal IP address from the internet and protect it from direct attacks.
2. **"Why do we use SSL Termination at the Reverse Proxy?"**
   *Answer:* Decrypting HTTPS traffic is CPU-intensive. By having the Reverse Proxy handle the decryption, we free up CPU resources on our backend application servers to handle actual business logic. The internal traffic is then sent unencrypted over a trusted, private VPC network.
3. **"Is a Reverse Proxy the same thing as a Load Balancer?"**
   *Answer:* They are very similar, and modern tools (like NGINX or HAProxy) often do both! But technically, a Reverse Proxy is designed to hide and protect a server, while a Load Balancer is designed to distribute traffic across *multiple* servers.

---

# 🎯 SDE-3 Deep Dive

The above covers *what* a reverse proxy is. Seniors get asked to **place it in the request path, distinguish it from adjacent components, and reason about failure and TLS.**

## The full edge stack — who does what

Interviewers want you to *not* conflate these:

| Layer | Job | Example |
|---|---|---|
| **CDN** | Cache static assets close to users, absorb DDoS at the edge | CloudFront, Cloudflare |
| **L4 Load Balancer** | Distribute TCP/UDP connections; no HTTP awareness; fastest | AWS NLB, IPVS |
| **L7 Reverse Proxy / LB** | Route by URL/header/cookie, terminate TLS, retry, rate-limit | NGINX, Envoy, ALB |
| **API Gateway** | L7 proxy + auth, quotas, request transformation, per-API routing | Kong, AWS API Gateway |

A reverse proxy is the **L7** box. "Load balancer" and "reverse proxy" overlap because one process (NGINX/Envoy) plays both — the *distinction* is the job, not the binary.

## L4 vs L7 — the trade you must be able to state

- **L4** forwards packets by IP:port. Can't see the URL, so it can't do path routing or HTTP retries — but it's cheap, low-latency, and preserves the connection end-to-end. Handles millions of connections.
- **L7** parses the HTTP request. Enables path/host routing, sticky sessions by cookie, header rewriting, response caching, WAF, per-route rate limits — at the cost of terminating and re-originating the connection (more CPU, more latency).
- Common pattern: **L4 in front, L7 behind** — NLB spreads connections across a fleet of Envoy instances that do the smart routing.

## TLS termination vs passthrough vs re-encryption

- **Termination:** decrypt at the proxy, plaintext to backend. Simplest, offloads CPU, lets the proxy inspect/route. Requires a *trusted* internal network.
- **Re-encryption (TLS bridging):** decrypt at proxy, re-encrypt to backend. Needed for compliance (PCI, zero-trust) where even the internal hop must be encrypted.
- **Passthrough:** proxy forwards encrypted bytes without decrypting (L4). The backend terminates TLS. Used when the proxy must not see plaintext, at the cost of losing L7 features.

## Failure reasoning

- The reverse proxy is now on the critical path → it's a **potential single point of failure**. Run it as a horizontally-scaled fleet behind an L4 LB (or an anycast VIP), never a single box.
- **Connection pooling / keep-alive:** the proxy multiplexes many short client connections onto a small pool of long-lived backend connections, saving backends from connection-setup storms — a real reason to have one even with a single backend.
- **Health checks + outlier ejection:** the proxy actively probes backends and ejects ones returning 5xx / timing out, which is where reverse-proxy and load-balancer responsibilities merge.

## Interview probes you should survive

- *"CDN, reverse proxy, load balancer, API gateway — draw the order."* → Client → CDN → L4 LB → L7 reverse proxy / API gateway → services. Each layer sheds a different concern.
- *"Where do you terminate TLS and why?"* → At the L7 edge for CPU offload and routing; re-encrypt to the backend if the internal network isn't trusted (zero-trust/PCI).
- *"Your reverse proxy is a SPOF — fix it."* → Fleet of proxies behind an L4 LB or anycast VIP; health-check and auto-replace. State is externalized so any proxy can serve any request.
