> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Networking fundamentals for system design. How computers actually talk to each other across the globe.
>
> **Key topics:**
> - **The OSI Model:** The 7 steps data takes to get from your app to the physical wire. 
> - **TCP vs UDP:** TCP is a certified mail delivery (reliable but slow). UDP is throwing a newspaper at a porch (unreliable but fast).
> - **HTTP Evolution:** How we went from HTTP/1 (a single-lane road) to HTTP/3 (a multi-lane highway with flying cars).
> - **REST vs GraphQL vs gRPC:** Different ways APIs talk. REST is a fixed restaurant menu, GraphQL is a custom order, gRPC is a walkie-talkie.
> - **Real-Time Patterns:** WebSockets (phone call), SSE (radio broadcast), and Long Polling (waiting on hold).
> - **DNS:** The internet's phonebook, and why changes take 48 hours to update.
>
> **Key takeaway:** Choosing the right network protocol can be the difference between a fast, snappy app and a broken, lagging mess.

---
module: 01-foundations
status: unread
tags: [01-foundations, system-design, foundations]
---
# Networking - System Design Guide

> This guide explains how data moves across the internet, using simple analogies.

---

## 🤷‍♂️ Why Should I Care?

Imagine you order a package from Amazon. You don't just care that it arrives; you care *how* it gets there. Did it come by a slow truck, or a fast airplane? Did you have to sign for it, or was it just dropped on your porch?

When you build an app, your data is the package. If you don't understand networking, you might accidentally choose to send a live video feed via "slow certified mail" instead of "fast delivery", and your users will experience terrible lag. Understanding networking helps you fix slow APIs, broken connections, and dropped messages.

---

## 🧅 The OSI Model (The 7 Layers of Networking)

**The Problem:** Your app tries to fetch data from a server, but it fails. Where is the problem? Is your code broken? Is the server's WiFi off? Did a shark bite an underwater internet cable? 

To solve this, engineers split networking into 7 "Layers" (The OSI Model). When something breaks, you check layer 7 first, then layer 6, all the way down to layer 1. 

> **💡 Analogy: Mailing an international letter.**  
> 7. **Application:** You write the letter.
> 6. **Presentation:** You translate the letter into French.
> 5. **Session:** You open an account with FedEx.
> 4. **Transport:** You choose "Certified Delivery with Signature."
> 3. **Network:** FedEx plans the flight route from New York to Paris.
> 2. **Data Link:** The local mail truck drives it to the airport.
> 1. **Physical:** The actual physical road the truck drives on.

| OSI Layer | Protocols | What it does |
|-----------|-----------|----------------|
| **7. Application** | HTTP, DNS | Your actual app code and API requests. |
| **6. Presentation** | SSL/TLS | Encrypting the data (so hackers can't read it). |
| **5. Session** | Sockets | Opening a connection between two computers. |
| **4. Transport** | TCP, UDP | Deciding *how* to deliver the data (reliable vs fast). |
| **3. Network** | IP, BGP | Routing the data across the globe via IP addresses. |
| **2. Data Link** | MAC | Sending data between two physical machines in the same room. |
| **1. Physical** | Fiber, WiFi | The literal copper wires, radio waves, or fiber optic cables. |

---

## 🚚 TCP vs UDP (How data is delivered)

At Layer 4 (Transport), you have to choose how your data is delivered.

### TCP (Transmission Control Protocol)
> **💡 Analogy: A Bank Transfer.**  
> If you wire $50,000, you want the bank to confirm every single step. It takes longer, but the money is guaranteed not to vanish. 

- **How it works:** It requires a "handshake" before sending data (Computer A: "Are you ready?" -> Computer B: "Yes!" -> Computer A: "Here is the data"). If a packet of data is lost in transit, TCP stops *everything* and resends it. 
- **Use for:** Websites (HTTP), Emails, Text Messages, Database saves. (Anytime losing data is unacceptable).

### UDP (User Datagram Protocol)
> **💡 Analogy: A Live Video Call.**  
> If you are on a Zoom call and your internet stutters, you don't want the video to freeze for 5 seconds while it waits to download the missing frames. You just want it to skip the glitch and keep playing live. A missing frame is better than a delayed frame!

- **How it works:** It just blasts data at the receiver as fast as possible. No handshakes, no checking if the data arrived, no resending lost data.
- **Use for:** Live Video streaming, Multiplayer Gaming, Voice calls. (Anytime speed is more important than perfect accuracy).

---

## 🛣️ HTTP Evolution (HTTP/1 vs 2 vs 3)

HTTP is how web browsers talk to web servers. It has evolved over time.

### HTTP/1.1 (The Single-Lane Road)
> **💡 Analogy:** A single-lane drive-thru. Car A orders, pays, and gets their food. Only then can Car B order. If Car A orders a massive feast that takes 10 minutes to cook, Car B is stuck waiting.
- **The Problem:** "Head-of-line blocking." One slow request blocks all other requests behind it.

### HTTP/2 (The Multi-Lane Highway)
> **💡 Analogy:** A multi-lane highway. Multiple cars can drive at the same time. But, because they share the same physical road (a single TCP connection), if there is a massive crash (a dropped network packet), the *entire* highway stops while it is cleaned up.
- **The Fix:** It allowed multiple requests to happen at the exact same time (multiplexing).

### HTTP/3 / QUIC (The Flying Cars)
> **💡 Analogy:** Every car gets its own personal helicopter. If one helicopter crashes, the others keep flying completely unaffected.
- **The Fix:** HTTP/3 abandons TCP entirely and uses UDP (via a protocol called QUIC). This means if one part of a website fails to load, the rest of the website continues loading instantly without waiting!

---

## 🗣️ API Protocols: REST vs GraphQL vs gRPC

When your frontend talks to your backend, it needs a language. 

### REST
> **💡 Analogy: A fixed restaurant menu.**  
> You can only order what is on the menu. If you want a burger without the bun, you still get the full burger and have to throw the bun away yourself. If you want a drink, you have to make a second, separate order.
- **Pros:** Extremely simple, works everywhere, easy to cache.
- **Cons:** You often get too much data (Over-fetching) or have to make multiple requests to get what you want (Under-fetching).

### GraphQL
> **💡 Analogy: A custom order.**  
> You walk into the kitchen and say exactly what you want: "I want the burger patty, but with the fries from the combo, and a shake from the kids menu." You get exactly what you asked for on one tray.
- **Pros:** Perfect for mobile apps where downloading extra data slows down the phone. 
- **Cons:** Harder to set up, and difficult to cache.

### gRPC
> **💡 Analogy: A military walkie-talkie.**  
> REST and GraphQL send data as plain text (like English). gRPC sends data as compressed, binary code (like Morse Code). 
- **Pros:** Lightning fast, and the data is 3 to 10 times smaller than REST!
- **Cons:** Web browsers can't easily read it. 
- **Use for:** Internal servers talking to *other* internal servers.

---

## ⚡ Real-Time Communication

Normal APIs only work when the user *asks* for data. What if the server needs to *push* data to the user? (Like a live chat app, or a live sports score).

1. **Short Polling (The Annoying Kid):** 
   - The app asks the server "Any updates?" every 5 seconds. 
   - *Analogy:* A kid in the backseat asking "Are we there yet?" constantly. Very wasteful.
2. **Long Polling (Putting on hold):** 
   - The app asks for an update, and the server puts the app "on hold" until an update actually happens. 
3. **SSE / Server-Sent Events (The Radio Broadcast):** 
   - The server pushes a continuous stream of data to the app. The app can listen, but cannot talk back. 
   - *Great for:* Live sports scores, stock tickers.
4. **WebSockets (The Phone Call):** 
   - A fully open, two-way connection. Both the app and the server can talk and listen at the exact same time. 
   - *Great for:* Multiplayer games, WhatsApp chat, collaborative Google Docs.

---

## 📖 DNS (The Internet's Phonebook)

**The Problem:** Computers only understand numbers (IP Addresses like `93.184.216.34`). Humans only understand words (like `google.com`). 

**The Solution:** DNS (Domain Name System). When you type `google.com`, your computer asks the DNS Phonebook, "Hey, what is the IP number for google.com?" 

### Why do DNS changes take 48 hours?
Imagine if every library in the world kept a copy of the phonebook to speed things up. 
If you change your phone number, you tell the central library. But it takes time for every local library around the world to throw away their old copy and download the new one. 

This is called **TTL (Time To Live)**. If your TTL is set to 24 hours, local internet providers will wait 24 hours before checking for updates. 
*Pro-Tip:* If you are moving your website to a new server, lower your TTL to 60 seconds a few days before you move!

---

## 🎤 Interview Questions to Practice

1. **"When would you choose UDP over TCP?"**
   *Answer:* I would use UDP for live video streaming or gaming, where a dropped packet (a quick glitch) is much better than a delayed packet (the whole game freezing while waiting for old data to arrive).
2. **"What is the difference between WebSockets and SSE?"**
   *Answer:* SSE is a one-way street (Server pushing live scores to a client). WebSockets are a two-way street (Client and Server chatting back and forth in a multiplayer game).
3. **"Why use gRPC instead of REST?"**
   *Answer:* For internal microservices. gRPC uses binary data instead of text, making it much smaller and faster than REST, but it's harder for front-end web browsers to use natively.
