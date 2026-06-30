> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** A cheat sheet linking big fancy tech words to the actual interview questions where you need to use them.
>
> **Key concepts:**
> - If you want to practice **WebSockets**: Look at Chat Apps, or Live Dashboards.
> - If you want to practice **Graph Databases**: Look at Social Network News Feeds (Friends of Friends).
> - If you want to practice **Map coordinates (Geohashes)**: Look at Uber or Yelp.
> - If you want to practice **Rate limiting**: Look at API Gateways or DDoS protection.
>
> **Key takeaway:** If you feel weak on a concept like "Message Queues", find the problem that tests it (e.g., YouTube Video Processing) and study that. 

---
module: 07-interview-templates
topic: Concept to Problem Map
status: unread
tags: [07-interview-templates, cross-reference, navigation, cheat-sheets]
---
# Concept → Problem Map

> **Use this map to figure out what you actually need to study for a specific interview question.**

---

## 🧱 Big Tech Blocks → Which problems use them?

| If you want to learn about... | Practice these problems... |
|---------------|----------------------|
| **Caching (Redis)** | URL Shortener, Twitter Feed, Leaderboards. |
| **CDN (Cloudflare)** | YouTube, Instagram, Netflix. (Anytime you need to load a photo or video fast). |
| **Message Queues (Kafka)** | YouTube (processing videos), Uber (matching drivers in the background), Notifications. |
| **Sharding (Breaking DBs into pieces)** | URL Shortener, Twitter Feed, WhatsApp. (Anytime you have a billion users). |
| **Rate Limiting** | API Gateway design, DDoS protection. |
| **Distributed Locks** | Ticketmaster (Don't let two people buy the same seat!), Hotel Booking. |

---

## 🧠 Smart Algorithms → Which problems use them?

| The Algorithm / Math | Why do you need it? | Practice these problems |
|---------------------------|----------|----------|
| **Trie (Prefix Tree)** | Finding words that start with "App..." really fast. | Autocomplete, Google Search bar. |
| **Inverted Index** | Finding which books contain the word "Wizard". | Search Engines. |
| **Quadtree / Geohash** | Finding things on a map near you. | Uber, Yelp, Tinder, Pokemon Go. |
| **Snowflake ID** | Generating unique 16-digit ID numbers for millions of things a second without crashing. | URL Shortener, Twitter (giving every Tweet an ID). |
| **Token Bucket** | Keeping track of how many requests a user is allowed to make. | Rate Limiter. |
| **Min-Heap (Priority Queue)** | Finding the "Top 10" of something instantly. | Leaderboards, Job Schedulers. |

---

## 🏗️ The Problem → What you MUST know before the interview

| If the interview is: | You MUST study these concepts first: |
|---------|---------------------------|
| **Design a URL Shortener** | Base62 encoding, Database Sharding, Caching. |
| **Design a Rate Limiter** | Token bucket algorithm, Redis, Sliding windows. |
| **Design Google Autocomplete** | Trie data structure, Caching prefixes. |
| **Design a Web Crawler** | Breadth-First Search (BFS), Bloom filters (to not crawl the same page twice). |
| **Design Twitter / Instagram Feed** | Push vs Pull architecture (Fan-out), CDN for images, Redis sorted sets. |
| **Design YouTube** | Chunking videos (uploading in pieces), Video transcoding, CDN. |
| **Design WhatsApp** | WebSockets (real-time chat), Message ordering, What happens when the user is offline? |
| **Design Uber** | Geohash (Maps), Matching algorithms, WebSockets (seeing the car move). |
| **Design Ticketmaster / Hotel Booking** | ACID Transactions, Distributed Locks (prevent double-booking). |
| **Design a Payment System** | Idempotency (Never charge a credit card twice!), ACID Transactions. |
