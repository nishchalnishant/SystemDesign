> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** How modern applications prove who you are (authentication) and what you're allowed to do (authorization) — specifically OAuth 2.0 and JSON Web Tokens (JWT).
>
> **Key topics:**
> - Authentication vs Authorization — the fundamental distinction
> - Session-based auth: the old hotel keycard approach
> - JWT: self-contained tickets and how to read them
> - OAuth 2.0: why "Login with Google" works, and the 4 grant flows
> - Token refresh, revocation, and the hardest security tradeoff
>
> **Key takeaway:** For stateless, distributed microservices → use JWTs. For third-party login delegation → use OAuth 2.0 Authorization Code Flow. Know exactly how to revoke a JWT (it's the hardest part).

---
module: 01-foundations/04-security
status: unread
tags: [oauth, jwt, authentication, authorization, security, tokens]
---

# OAuth 2.0 & JWT — System Design Guide

## Why Should I Care?

Every system design interview that involves users will eventually probe:

> "How does your API know if a user is who they say they are? How do you handle 'Login with Google'? What happens when their session expires?"

Authentication is the **most critical security layer** in any system. Getting it wrong means data breaches, account takeovers, and regulatory fines. This guide demystifies the two dominant standards: **JWT** and **OAuth 2.0**.

---

## The Core Distinction: Authentication vs Authorization

These two terms are constantly confused. Burn this into memory:

| Term | Question it answers | Example |
|---|---|---|
| **Authentication (AuthN)** | *Who are you?* | You are `user_id: 42` |
| **Authorization (AuthZ)** | *What are you allowed to do?* | You can read, but not delete |

An analogy: At an airport, showing your passport to the agent is **authentication** (proving who you are). Your boarding pass specifying seat 14A in Economy is **authorization** (what you're entitled to do).

---

## Part 1: Session-Based Auth — The Old Way

**Analogy:** A hotel keycard. When you check in (log in), the hotel creates a record of your stay in their system and gives you a keycard (session ID). Every time you swipe the card, the door reader calls the hotel front desk to verify your card is still valid.

### How It Works

```
Browser                   Server                    Session Store (Redis)
   |                         |                              |
   |-- POST /login --------->|                              |
   |   {username, password}  |                              |
   |                         |-- store session data ------->|
   |                         |   {sessionId: "abc123",      |
   |                         |    userId: 42, role: "admin"}|
   |<-- Set-Cookie: sid=abc123|                              |
   |                         |                              |
   |-- GET /dashboard ------>|                              |
   |   Cookie: sid=abc123    |-- lookup "abc123" ---------->|
   |                         |<-- {userId: 42, role: admin}--|
   |<-- 200 OK (dashboard) --|                              |
```

**Problem with sessions in distributed systems:** Every server must be able to look up the session store. This requires a shared Redis/database cluster. If Redis goes down, all users are logged out. This is a single point of failure and creates latency.

---

## Part 2: JWT — "Self-Contained Passports"

**Analogy:** Instead of a hotel keycard that requires checking with the front desk, imagine a **passport**. The passport contains your identity information, is stamped and signed by the government, and any border agent anywhere in the world can verify it without calling anyone. It expires on a printed date.

A **JSON Web Token (JWT)** is a self-contained token that carries the user's identity and claims, digitally signed so the server can verify it without a database lookup.

### Structure

A JWT is three Base64-encoded JSON objects, separated by dots: `header.payload.signature`

```
eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9
.
eyJ1c2VySWQiOiI0MiIsInJvbGUiOiJhZG1pbiIsImlhdCI6MTcxOTk5NTYwMCwiZXhwIjoxNzE5OTk5MjAwfQ
.
[digital signature]
```

**Decoded:**

```json
// HEADER
{
  "alg": "RS256",   // Signing algorithm (RS256 = RSA + SHA-256)
  "typ": "JWT"
}

// PAYLOAD (the "claims")
{
  "userId": "42",
  "email": "alice@example.com",
  "role": "admin",
  "iat": 1719995600,   // Issued at (Unix timestamp)
  "exp": 1719999200    // Expires at (Unix timestamp) — 1 hour from iat
}

// SIGNATURE
RSASHA256(
  base64(header) + "." + base64(payload),
  PRIVATE_KEY       // Only the auth server has this
)
```

### How JWT Verification Works

Any service with the **public key** can verify a JWT:

```
Client                   API Server               Auth Server
   |                         |                        |
   |-- GET /orders ---------->|                        |
   |   Authorization: Bearer <JWT>                     |
   |                         |                        |
   |                         | (no network call needed)|
   |                         | 1. Decode JWT           |
   |                         | 2. Verify signature     |
   |                         |    with public key      |
   |                         | 3. Check exp > now      |
   |                         | 4. Authorize user       |
   |<-- 200 OK (orders) -----|                        |
```

**Key advantage:** The API server never needs to call the Auth server or a database. It just verifies the cryptographic signature locally. This is perfect for **microservices**.

### JWT Signing Algorithms

| Algorithm | Type | Use Case |
|---|---|---|
| **HS256** | Symmetric (HMAC) | Simple: one shared secret. All services must share it. |
| **RS256** | Asymmetric (RSA) | Auth server signs with private key. Services verify with public key. Best for microservices. |
| **ES256** | Asymmetric (ECDSA) | Smaller tokens than RS256. Used by modern systems. |

> [!IMPORTANT]
> Always use **RS256 or ES256** in microservices architectures. With HS256, if any service is compromised, the shared secret is exposed and an attacker can forge tokens.

---

## Part 3: Access Tokens & Refresh Tokens

Short-lived JWTs minimize risk (if stolen, they expire quickly). But you don't want users to log in every 15 minutes.

**Solution: Two-token system**

```
Auth Server issues two tokens on login:
  ┌─────────────────────────────────────────┐
  │ Access Token (JWT)                      │
  │   - Lifespan: 15 minutes                │
  │   - Used for: API calls                 │
  │   - Stored in: memory (not localStorage)│
  └─────────────────────────────────────────┘

  ┌─────────────────────────────────────────┐
  │ Refresh Token (opaque)                  │
  │   - Lifespan: 7-30 days                 │
  │   - Used for: getting new access tokens │
  │   - Stored in: HttpOnly cookie          │
  └─────────────────────────────────────────┘
```

**Token refresh flow:**

```
Client                           Auth Server
  |                                   |
  |-- API call (expired access token)→|→ 401 Unauthorized
  |                                   |
  |-- POST /token/refresh ----------->|
  |   Cookie: refresh_token=<opaque>  |
  |                                   |-- verify refresh token in DB
  |<-- new access token (JWT) --------|
  |                                   |
  |-- API call (new access token) --->|→ 200 OK
```

---

## Part 4: The Hardest Part — Revoking JWTs

> **"JWTs are stateless. How do you log a user out or revoke a compromised token before it expires?"**

This is the most common advanced follow-up question. Here are the three approaches:

### Option A: Short Expiry + Accept the Gap
Set JWT expiry to 5-15 minutes. A revoked token is invalid within 15 minutes. Simple, but not immediate.

**Used when:** Security requirements tolerate a small window of residual access.

### Option B: Token Blocklist (Denylist)

Maintain a Redis set of invalidated token JTIs (JWT IDs):

```python
# On logout or compromise
redis.set(f"blocklist:{jwt_id}", "revoked", ex=token_ttl)

# On every API request
def verify_token(token):
    claims = decode_jwt(token)
    if redis.get(f"blocklist:{claims['jti']}"):
        raise UnauthorizedException("Token has been revoked")
    return claims
```

**Tradeoff:** Requires a Redis lookup on every API request — reintroduces some statefulness.

### Option C: Short-Lived Access Tokens Only

- Access tokens expire in 5 minutes.
- Refresh tokens are stored in a DB and can be deleted immediately on logout.
- When a refresh is attempted, the DB check fails and no new access token is issued.

**Used by:** Most production systems (Stripe, GitHub, Google).

---

## Part 5: OAuth 2.0 — "Login With Google" Explained

**Analogy:** You go to a hotel (third-party app). Instead of giving the hotel your house keys (your Google password), you call Google yourself, they give the hotel a **limited-use keycard** that only opens certain doors for a set period. Google never shares your keys.

OAuth 2.0 is a **delegation protocol** — it lets users grant a third-party application limited access to their account on another service, without sharing their password.

### The 4 Roles

| Role | Who It Is | Example |
|---|---|---|
| **Resource Owner** | The user | You |
| **Client** | Third-party app wanting access | Spotify |
| **Authorization Server** | Issues tokens | Google's OAuth server |
| **Resource Server** | API being accessed | Google's APIs (Gmail, Calendar) |

### The Authorization Code Flow (Most Important)

This is the secure, standard flow used for web and mobile apps:

```
Browser/App          Spotify (Client)         Google (Auth Server)   Google APIs
     |                     |                         |                    |
     |-- "Login w/ Google"->|                         |                    |
     |                     |-- Redirect to Google --->|                    |
     |                                               |                    |
     |<------------ Google Login Page --------------|                    |
     |-- (user logs in + approves permissions) ----->|                    |
     |                                               |                    |
     |<-- Redirect back to Spotify with ?code=XYZ ---|                    |
     |                     |                         |                    |
     |                     |-- POST /token ---------->|                    |
     |                     |   {code: XYZ,            |                    |
     |                     |    client_secret: SECRET}|                    |
     |                     |<-- {access_token,        |                    |
     |                     |     refresh_token} -------|                    |
     |                     |                         |                    |
     |                     |-- GET /userinfo -------->|-----> Access Token  |
     |                     |<-- {email, name} --------|<----- User Data     |
     |<-- Logged in! -------|                         |                    |
```

**Why two steps (code → token)?** The authorization code is short-lived and passed through the browser (visible in URL). The actual token exchange happens **server-to-server** using the client secret, which is never exposed to the browser.

### PKCE — For Mobile/SPA Apps

Mobile apps and SPAs can't safely store a `client_secret`. **PKCE** (Proof Key for Code Exchange) replaces the client secret with a one-time cryptographic challenge:

```
App generates: code_verifier = random(32 bytes)
               code_challenge = SHA256(code_verifier)

Step 1: Send code_challenge in the authorization request
Step 2: Send code_verifier in the token request
Google: SHA256(code_verifier) must match stored code_challenge ✓
```

### OAuth 2.0 vs OpenID Connect (OIDC)

| | OAuth 2.0 | OpenID Connect (OIDC) |
|---|---|---|
| **Purpose** | Authorization (what you can do) | Authentication (who you are) |
| **Token** | Access token (opaque or JWT) | ID token (always a JWT with user claims) |
| **Use case** | "Allow Spotify to read my Playlists" | "Log me in with Google" |

OIDC is built **on top of** OAuth 2.0. When you see "Login with Google/GitHub/Apple", that's OIDC.

---

## Part 6: Security Best Practices

| Practice | Why |
|---|---|
| Store access tokens in **memory only** (not localStorage) | localStorage is accessible by JS — XSS attacks can steal it |
| Store refresh tokens in **HttpOnly cookies** | Not accessible via JS — safe from XSS |
| Use **HTTPS only** | Prevents token interception |
| Use **RS256 or ES256** (not HS256) | Asymmetric: compromise of one service doesn't expose signing key |
| Set **short expiry** on access tokens (5-15 min) | Limits blast radius of a stolen token |
| Add **`jti` (JWT ID) claim** | Enables blocklisting individual tokens |
| Validate **all claims** (`exp`, `iss`, `aud`) | Prevents cross-service token reuse |

---

## Common Interview Questions

**Q: How does "Login with Google" work?**
A: OAuth 2.0 Authorization Code Flow. The user authenticates with Google. Google issues an authorization code. Your server exchanges this for an access token + ID token (OIDC). The ID token is a JWT containing the user's identity.

**Q: JWT vs Sessions — which would you use for a microservices architecture?**
A: JWTs. Sessions require every service to query the same session store (coupling and SPOF). JWTs are self-contained and verifiable by any service with the public key — no network call needed.

**Q: How do you log out a user immediately with JWTs?**
A: Option 1: Token blocklist in Redis (fast but adds statefulness). Option 2: Keep access tokens very short-lived (5 min) and invalidate the refresh token in the DB. Most production systems use Option 2.

**Q: What's the difference between OAuth 2.0 and OpenID Connect?**
A: OAuth 2.0 is an authorization framework. OIDC is an authentication layer built on top — it adds a standardized ID token (JWT) so you know *who* the user is, not just what they're allowed to access.

---

> [!TIP]
> **Quick Interview Cheat Sheet**
> - **Session-based auth** → Simple apps, monoliths, when you need immediate revocation
> - **JWT** → Microservices, stateless APIs, mobile apps
> - **OAuth 2.0 Authorization Code** → "Login with Google/GitHub" in web apps
> - **OAuth 2.0 + PKCE** → "Login with Google/GitHub" in mobile/SPA apps
> - **Revoke a JWT** → Short expiry + delete refresh token, or token blocklist in Redis
