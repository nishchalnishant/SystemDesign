> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** A simple, structured template for designing APIs during a system design interview. 
>
> **Key concepts:**
> - **Protocol Choice (How they talk):** Use REST for public apps (like a Web Browser). Use gRPC for internal servers talking to each other (super fast). Use GraphQL for Mobile apps (saves battery).
> - **Anatomy of an Endpoint:** `METHOD /v1/resource/identifier`. Always explain what goes in, and what comes out.
> - **Pagination (Flipping pages):** "Offset" pagination is like saying "skip 100 pages." It is slow. "Cursor" pagination is like a bookmark. It is fast. Always use Cursor.
> - **Versioning (Upgrades):** Put `/v1/` in your URLs so you don't break old apps when you release `/v2/`.
> - **Idempotency (The Double-Charge Problem):** The magic trick to make sure a user doesn't get charged twice if their internet drops.
>
> **Key takeaway:** The interviewer doesn't just want a list of URLs. They want to see how you handle large amounts of data (Pagination) and network failures (Idempotency).

---
module: 07-interview-templates
status: unread
tags: [07-interview-templates, system-design, interview-templates, api-design]
---
# API Design Template (Interview Reference)

> **Use this when the interviewer asks you to design APIs. This guide uses simple analogies to explain complex API concepts.**

---

## 📡 1. Protocol Selection (How computers talk)

Before writing any URLs, tell the interviewer *how* the computers will talk to each other. 

- **REST (The Universal Language):** Use this for public APIs, websites, or any time humans need to read the data. It's the standard.
- **gRPC (The High-Speed Train):** Use this when two of *your own* backend servers are talking to each other. It sends data as binary (0s and 1s) instead of text, making it extremely fast. 
- **GraphQL (The Custom Order):** Use this for Mobile Apps. Instead of the server sending a massive chunk of data, the phone says, *"I only want the user's name and profile picture, nothing else."* This saves battery and data. 
- **WebSockets (The Open Phone Line):** Use this for Chat Apps or Live Sports Scores. The connection stays open so the server can push messages instantly.

---

## 📝 2. Core Endpoint Design (The Menu)

Keep your URLs clean. Use **Nouns**, not **Verbs**.

**Good Examples:**
- `GET /api/v1/users` -> Get a list of users.
- `POST /api/v1/users` -> Create a new user.
- `GET /api/v1/users/123/orders` -> Get the orders for user 123.

**Bad Examples (Do not do this):**
- `POST /api/v1/createUser` (Don't use verbs!)
- `GET /api/v1/getUserOrders?id=123` (Messy).

### The 4 Main Methods
- **GET:** Read data. (Safe, doesn't change anything).
- **POST:** Create new data. 
- **PUT:** Completely replace data.
- **DELETE:** Delete data.

---

## 📄 3. Pagination (Flipping Pages)

If a user has 10,000 tweets, you cannot send them all at once. You must paginate. There are two ways to do this:

### ❌ Offset Pagination (The Bad Way)
`GET /api/v1/tweets?offset=10000&limit=20`

**Analogy:** This is like telling a librarian, *"Start at book #1, count exactly 10,000 books, and give me the next 20."* The librarian has to count all 10,000 books every single time. It is incredibly slow for large numbers.

### ✅ Cursor Pagination (The Good Way)
`GET /api/v1/tweets?cursor=tweet_id_9999&limit=20`

**Analogy:** This is like giving the librarian a specific bookmark. *"Go straight to the book with ID 9999, and give me the next 20."* The librarian jumps there instantly. It is blazing fast, no matter how many books there are. 

**Always tell the interviewer you will use Cursor Pagination for infinite scrolling feeds.**

---

## 🛡️ 4. Idempotency (The Double-Charge Problem)

**The Problem:** A user clicks "Pay $50". The phone sends the request to the server. The server charges the credit card, but then the user's internet drops before the server can reply "Success". The user thinks it failed, so they click "Pay $50" again. They just got charged $100!

**The Solution (Idempotency):**
The phone generates a unique random ticket number (an `Idempotency-Key`, e.g., `ticket_123`) and attaches it to the request. 
1. The server receives `ticket_123` and charges the card. 
2. It saves a note: *"I already processed ticket_123"*.
3. When the user's internet reconnects and they click "Pay" again, the phone sends `ticket_123` again.
4. The server sees the note, skips the charge, and just replies "Success". 

**Always mention Idempotency for Payment or Checkout APIs.**

---

## 🔄 5. API Versioning (Future-Proofing)

If you change how your API works, you will break every old mobile app that hasn't been updated yet. 

**The Solution:** Always include a version number in your URL!
- Today: `api/v1/users`
- Next Year (when you redesign the app): `api/v2/users`

This allows old phones to keep using `v1` while new phones use `v2`. 

---

## 🚫 6. HTTP Status Codes (How to say "No")

When things break, you must return the correct error code:

- **200 OK:** Everything worked.
- **201 Created:** Everything worked, and a new item was saved.
- **400 Bad Request:** The user sent garbage data (like typing letters into a phone number box).
- **401 Unauthorized:** The user is not logged in.
- **403 Forbidden:** The user is logged in, but tried to access someone else's data. 
- **404 Not Found:** The data doesn't exist.
- **429 Too Many Requests:** The user is clicking too fast (Rate Limiting).
- **500 Internal Server Error:** Your server crashed. (Never show the user exactly why it crashed, just say "Oops!").
