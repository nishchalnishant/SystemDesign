> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** How to make Netflix load instantly for a user in Australia, even though the main server is in California.
>
> **Key topics:**
> - **The Problem:** The speed of light. Data takes 150 milliseconds to cross the ocean. If a webpage requires 100 images, it will take 15 seconds to load.
> - **The Solution:** A Content Delivery Network (CDN). A global network of "mini-servers" placed in every major city in the world.
> - **PoPs (Points of Presence):** The physical locations of these mini-servers.
> - **Push vs Pull CDNs:** Does your main server push the video to the CDN, or does the CDN pull the video when a user asks for it?
> - **TTL (Time to Live):** How long the CDN is allowed to keep the video before it has to ask the main server for a fresh copy.
>
> **Key takeaway:** Never serve static files (images, videos, HTML, Javascript) from your main application server. Always put them in an S3 bucket behind a CDN.

---
module: 02-building-blocks
status: unread
tags: [02-building-blocks, system-design, networking]
---
# Content Delivery Networks (CDN) - System Design Guide

> This guide explains how to deliver large files across the globe instantly using simple analogies.

---

## 🤷‍♂️ Why Should I Care?

Imagine you own a bakery in New York. You bake the best cookies in the world. 
A customer in Tokyo orders a cookie. You put it in a box, and mail it on an airplane. It takes 15 hours to arrive. 

If 1,000 people in Tokyo order cookies every day, mailing them from New York is incredibly inefficient, slow, and expensive. 
Instead, you rent a tiny warehouse in Tokyo. You bake 1,000 cookies in New York, fly them to Tokyo *once*, and put them in the warehouse. 

Now, when a Tokyo customer orders a cookie, they get it instantly from the local warehouse. 

In System Design, this is called a **Content Delivery Network (CDN)**. (Companies like Cloudflare or AWS CloudFront).
If your main server is in New York, and a user in Tokyo wants to watch a 5GB 4K video, you don't send it across the ocean. You send it to a CDN server in Tokyo once, and the Tokyo server hands it out to the local users instantly.

---

## 🌍 How CDNs Work (Points of Presence)

A CDN provider (like Cloudflare) owns thousands of physical servers all over the planet. These physical locations are called **PoPs (Points of Presence) or Edge Servers**.

When a user in London visits your website, their browser doesn't connect to your main server in New York. Their browser automatically connects to the closest CDN Edge Server in London. 

If the London CDN has the image (A Cache Hit), it sends it instantly. 
If it doesn't have the image (A Cache Miss), it quickly asks the New York server for the image, saves a copy in London, and then hands it to the user.

### What kind of data goes in a CDN?
Only **Static Data**. This means files that do not change based on who is logged in. 
- ✅ Images (`logo.png`)
- ✅ Videos (`movie.mp4`)
- ✅ Javascript & CSS files (`styles.css`)
- ❌ Bank Balances (This is **Dynamic Data**. It changes for every single user. This must come from the main Database).

---

## 📥 Push vs Pull CDNs

How does the data actually get into the CDN warehouse in the first place? There are two ways:

### 1. Pull CDN (The Lazy Way)
> **💡 Analogy:** A library that only buys a book *after* someone asks for it. 
- **How it works:** You put your image on your main server. You don't tell the CDN anything. When the very first user in Tokyo asks for the image, the Tokyo CDN says "I don't have it." The CDN *pulls* it from New York, and saves it. 
- **Pros:** Completely automatic. Easiest to set up.
- **Cons:** The very first user to request the file will experience a slow load time (A Cache Miss).

### 2. Push CDN (The Proactive Way)
> **💡 Analogy:** A magazine publisher mailing the new issue to every newsstand in the country before the sun comes up.
- **How it works:** When you upload a new video to your server, your code immediately *pushes* a copy of the video to every CDN location around the world.
- **Pros:** The video is instantly available everywhere. Nobody ever experiences a slow load time.
- **Cons:** Harder to program. And if nobody in Antarctica ever watches the video, you just wasted money storing it on the Antarctica CDN server.

---

## ⏱️ Cache Invalidation & TTL (Time To Live)

**The Problem:** You update your company's `logo.png` from a blue logo to a red logo. You save it on your main server. But all the CDNs around the world still have the old blue logo saved! Users are seeing the wrong logo.

**The Fix:** 
You can't just expect the CDN to hold the file forever. You must assign a **TTL (Time to Live)** to every file. 
> **💡 Analogy:** Putting an expiration date on a gallon of milk. 

If you set the TTL of `logo.png` to 24 hours, the CDN will hold the image for exactly 24 hours. Once the time expires, the CDN throws the image in the trash. The next time a user asks for it, the CDN is forced to go back to your main server and download the newest version. 

*(Pro-Tip: If you need to update a file instantly, you can manually trigger a "Cache Invalidation" to force the CDN to delete the old file immediately, but this costs money!).*

---

## 🎤 Interview Questions to Practice

1. **"What is the difference between a CDN and a regular Cache (like Redis)?"**
   *Answer:* A Cache (like Redis) usually sits inside your own data center right next to your database, and is used to store dynamic data (like a user's session). A CDN is a globally distributed network of servers designed to store static files (images, videos) physically closer to the end user to reduce network latency.
2. **"What happens if a user requests an image that isn't in the CDN?"**
   *Answer:* This is called a Cache Miss. The CDN Edge Server will forward the request back to the Origin Server (your main server), download the image, serve it to the user, and cache a copy locally for the next user.
3. **"How do you update a file that is already cached in a CDN?"**
   *Answer:* You either wait for the file's TTL (Time to Live) to expire, or you issue an explicit "Cache Invalidation" request to the CDN provider to purge the file from all Edge Servers immediately. (Another common trick is "Versioning" — instead of overwriting `logo.png`, you upload `logo_v2.png` and update your HTML to point to the new URL!).
