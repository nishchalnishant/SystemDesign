> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** The absolute basics of Security in System Design. How to make sure hackers don't destroy your application.
>
> **Key topics:**
> - **Authentication (AuthN):** Proving *who* you are (Passwords, biometrics).
> - **Authorization (AuthZ):** Proving *what* you are allowed to do (Can a standard user delete the database?).
> - **JWTs (JSON Web Tokens):** The modern "VIP Wristband" for websites so users don't have to log in on every single click.
> - **OAuth 2.0:** "Sign in with Google." How to let apps access your data without giving them your password.
> - **Encryption:** Scrambling data so hackers can't read it. (In-Transit vs At-Rest).
> - **Rate Limiting & DDoS:** How to stop a bot army from clicking "Refresh" 10 million times and crashing your servers.
>
> **Key takeaway:** Security isn't a feature you add at the end; if you design an open API without Rate Limiting and Auth, an automated bot will crash it within 5 minutes of launching.

---
module: 01-foundations
status: unread
tags: [01-foundations, system-design, foundations]
---
# Security - System Design Guide

> This guide explains how to secure a distributed system using simple analogies.

---

## 🤷‍♂️ Why Should I Care?

Imagine you build a beautiful app that lets users upload photos. You put it on the internet. 

Five minutes later, an automated hacker bot finds it. The bot sends 1 million fake photos per second to your server. Your server crashes (A DDoS attack). While the server is rebooting, another bot realizes you aren't checking if users are logged in, so it deletes the database. 

On the modern internet, security isn't about stopping a lone hacker in a dark room. It's about stopping thousands of automated scripts that scan the internet 24/7 looking for unprotected doors. If you don't design security into your system from Day 1, your system will be destroyed on Day 2.

---

## 🛂 Authentication vs Authorization

These two words sound similar but mean very different things. 

### 1. Authentication (AuthN) - "Who are you?"
> **💡 Analogy:** Showing your Passport at border control. The guard checks the photo to prove you are actually who you say you are.
- **How we do it:** Passwords, FaceID, Fingerprints, Two-Factor Authentication (2FA) SMS codes. 
- **The Goal:** Verify the user's identity.

### 2. Authorization (AuthZ) - "What are you allowed to do?"
> **💡 Analogy:** Being inside a concert venue. You proved who you are to get in the front door, but the bouncer at the VIP lounge checks if you have the *permission* to enter the VIP area.
- **How we do it:** Role-Based Access Control (RBAC). E.g., User A is an "Admin" and can delete posts. User B is a "Viewer" and can only read posts.
- **The Goal:** Verify the user's permissions.

---

## 🎟️ JWT (JSON Web Tokens)

**The Problem:** HTTP is "Stateless". That means every time you click a link on a website, the server instantly forgets who you are. Do you want to type your password every time you click a photo on Instagram? No.

**The Fix:** JWT (Pronounced "Jot").
> **💡 Analogy:** A VIP Wristband at a club. You show your ID (Password) at the front door once. The bouncer gives you a neon wristband. For the rest of the night, whenever you go to the bar, you just hold up your wristband. The bartender doesn't need to see your ID, they just trust the wristband.

**How it works:**
1. You log in with your password.
2. The server creates a JWT (a long string of scrambled letters) that contains your User ID and an expiration date. 
3. The server cryptographically *signs* the token so a hacker can't forge a fake one.
4. Your browser saves the JWT, and attaches it to every single request you make. The server sees the token and says "Ah, you're logged in!"

---

## 🤝 OAuth 2.0 ("Sign in with Google")

**The Problem:** You download a new Calendar App. The app says "Give us your Google Password so we can read your Google Calendar." You should *never* give your Google password to a random app.

**The Fix:** OAuth 2.0.
> **💡 Analogy:** The Valet Key for a car. You give the Valet a special key that can turn on the engine and drive 10 mph, but it cannot unlock the glovebox or the trunk. 

**How it works:**
1. The Calendar app sends you to Google's actual website.
2. Google asks you: "Do you want to grant this Calendar App permission to *only read* your calendar?"
3. You click "Yes."
4. Google gives the Calendar App a special "Access Token" (like a Valet Key) that only allows it to read calendars, and nothing else. The app never sees your password!

---

## 🔒 Encryption (In-Transit vs At-Rest)

Encryption is scrambling data so that if a hacker intercepts it, it looks like gibberish. You must encrypt data in two places.

### 1. Encryption In-Transit (HTTPS / TLS)
> **💡 Analogy:** Sending a letter in a locked steel briefcase instead of a clear plastic envelope. If the postman opens the briefcase, the letter is in a secret code.
- **What it is:** Encrypting data while it travels over the internet cables from the user's phone to your server. 
- **Why?** So hackers on public Starbucks WiFi can't steal passwords out of the air.

### 2. Encryption At-Rest (AES-256)
> **💡 Analogy:** Locking the filing cabinet inside the office, even though the front door is already locked.
- **What it is:** Scrambling the data *before* you save it to the hard drive in the database.
- **Why?** If a rogue employee walks into the server room and literally steals the physical hard drive, the data is useless to them.

---

## 🛡️ Defending the Castle (DDoS & Rate Limiting)

### The Threat: DDoS (Distributed Denial of Service)
> **💡 Analogy:** 10,000 fake customers swarming a tiny coffee shop, standing at the register, and ordering nothing. The real customers can't get in, and the shop is forced to close.
- Hackers use thousands of infected computers to send massive amounts of junk traffic to your app, overwhelming the CPU until it crashes.

### The Defense: Rate Limiting
> **💡 Analogy:** A bouncer at the coffee shop who says, "One person can only enter the shop once per minute."
- Your API Gateway tracks the IP address of every request. If an IP address tries to click "Login" 500 times in 1 second, the Rate Limiter blocks them instantly and returns a `429 Too Many Requests` error. 

---

## 🎤 Interview Questions to Practice

1. **"What is the difference between Authentication and Authorization?"**
   *Answer:* Authentication is proving *who* you are (verifying an identity with a password). Authorization is proving *what* you can do (verifying permissions, like if an Admin can delete a file).
2. **"How does a system remember a user is logged in?"**
   *Answer:* Using JWTs or Session Cookies. After verifying the password once, the server gives the client a signed token. The client sends that token on every subsequent request like a VIP wristband.
3. **"How do you stop a malicious script from crashing your API?"**
   *Answer:* I would place an API Gateway in front of the servers and configure Rate Limiting, blocking any IP address that sends more than X requests per second.
