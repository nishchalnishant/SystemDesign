# Security - System Design Guide

> **For SDE-3 Interview Preparation**  
> Securing distributed systems, Authentication vs Authorization, and Common Vulnerabilities — with real-world analogies.

## Table of Contents
1. [Authentication (AuthN) Types](#authentication-types)
2. [Authorization (AuthZ) Models](#authorization-models)
3. [OAuth 2.0 & OIDC](#oauth-20--oidc)
4. [JWT (JSON Web Tokens)](#jwt-json-web-tokens)
5. [Transport Security (TLS/HTTPS)](#transport-security)
6. [CORS](#cors)
7. [Common Vulnerabilities (OWASP)](#common-vulnerabilities)
8. [Security Patterns](#security-patterns)

---

## Authentication Types

### 1. Session-Based Auth (Stateful)

- **Flow**: User logs in → Server creates session ID → Session stored in DB/Redis → Session ID sent to client as a Cookie.
- **Pros**: Easy revocation (delete the session from the store and it's instantly invalid).
- **Cons**: Server must store and look up sessions; harder to scale across regions without a shared session store.

> **Analogy:** A coat check at a restaurant. You hand in your coat, they give you a numbered token. Every time you want your coat, they look up your number in their book and retrieve it. The token is meaningless without the coat-check register.

### 2. Token-Based Auth (Stateless — JWT)

- **Flow**: User logs in → Server signs a JWT → JWT sent to client (typically in a header or cookie).
- **JWT Structure**: `Header.Payload.Signature` (Base64-encoded, not encrypted by default).
- **Verification**: Pure CPU work (verify the cryptographic signature). No DB lookup needed.
- **Challenges**:
  - **Revocation**: Hard to revoke before expiry — there is no central register to delete from.
  - **Solution**: Use short-lived Access Tokens (15 min) paired with a longer-lived Refresh Token (7 days). Store Refresh Tokens in the DB so they can be revoked.

### 3. Mutual TLS (mTLS)

- **Definition**: Both client and server authenticate each other using certificates — not just the server proving itself to the client.
- **Use Case**: Zero Trust microservices, service mesh (Istio, Linkerd).
- **Pros**: Extremely secure against MITM and spoofing. Cryptographically verified identity on both sides.
- **Cons**: Certificate management complexity (rotation, distribution, expiry).

> See the [mTLS analogy](#httpsm-tls) below.

---

## Authorization Models

### 1. RBAC (Role-Based Access Control)

> **Analogy: A company's badge system.** Your badge grants access based on your job title. "Manager" badges open the server room. "Intern" badges only open the lobby. The security system doesn't know you personally — it knows your role.

- Assign permissions to **Roles** (Admin, Editor, Viewer).
- Assign **Roles** to **Users**.
- Simple, widely used, easy to audit.

### 2. ABAC (Attribute-Based Access Control)

> **Analogy: A smart door that reads the full context.** It checks: "Is it a weekday? Is this person in the IT department? Is the resource classified as internal-only? Is it before 5 PM?" All conditions must be true for access to be granted.

- Fine-grained, policy-driven.
- `"Allow if User.Department == IT AND Resource.Type == Server AND Time < 5PM"`
- Dynamic and flexible but computationally expensive to evaluate at scale.

### 3. ACL (Access Control Lists)

> **Analogy: A VIP list attached to a specific event.** The list travels with the venue, not the person. "Event A: Alice (read), Bob (write), Carol (admin)." Different events have entirely different lists.

- List of permissions attached to a specific resource object.
- `File A: User1 (Read), User2 (Write)`.
- Fine-grained but can become unmanageable at scale.

---

## OAuth 2.0 & OIDC

### The Hotel Key Card Analogy

> **OAuth 2.0 is like a hotel key card system.**  
>
> You (the **user**) check into a hotel (the **OAuth Authorization Server** — e.g., Google). The hotel gives you a key card (the **access token**) that only opens specific doors — your room, the gym, the parking garage — but not the manager's office (these are **scopes**).
>
> You then hand that key card to a contractor (the **third-party application**) who needs to access your room to fix the plumbing. The contractor never sees your actual room key or your personal credentials — they only have the limited-access card you gave them.
>
> At any point, you can call the front desk and **deactivate the key card**. The contractor immediately loses access without you needing to change your own credentials.

### Core Roles

- **Resource Owner**: The user who owns the data.
- **Client**: The application requesting access (web app, mobile app).
- **Authorization Server**: The identity provider (Google, Okta, Auth0) that issues tokens.
- **Resource Server**: The API that hosts the protected data and validates tokens.

### Common Flows

#### 1. Authorization Code Flow (Web Apps)

Best for server-side web applications that can keep a `client_secret` secure.

1. Client redirects user to Authorization Server.
2. User logs in and grants consent.
3. Authorization Server redirects back with a short-lived `code`.
4. Server exchanges `code` + `client_secret` for an `access_token` (back-channel — never exposed to browser).

#### 2. Authorization Code with PKCE (Mobile/SPA)

For public clients that cannot securely store a `client_secret` (single-page apps, mobile apps).

- Client generates a random `code_verifier` and a hashed `code_challenge`.
- Even if the `code` is intercepted, it's useless without the original `code_verifier`.

#### 3. Client Credentials Flow (Machine-to-Machine)

Service A calling Service B with no user involved.

- Service A sends `client_id` + `client_secret` directly to the Authorization Server.
- Receives a token scoped to service-level permissions.

### OIDC (OpenID Connect)

- A layer on top of OAuth 2.0 that adds **Authentication** (who the user is, not just what they can access).
- Returns an `id_token` (a JWT) containing user identity claims: `email`, `name`, `sub` (subject ID).
- OAuth 2.0 alone is authorization ("what can this app do?"). OIDC adds identity ("who is the user?").

---

## JWT (JSON Web Tokens)

### The Music Festival Wristband Analogy

> **A JWT is like a VIP wristband at a music festival.**
>
> When you buy a VIP ticket, the organizer gives you a wristband that says: "VIP access, valid Saturday only, issued by FestivalCorp." The wristband is physically **signed** (a holographic seal, a specific pattern) so that any staff member at any entrance can glance at it and verify it's real — without radioing back to HQ to check a list.
>
> This is fast and scalable. But it has one critical weakness: **if someone steals your wristband, it works until the end of Saturday.** The staff can't know it was stolen just by looking at it. The only defenses are making wristbands expire quickly (short TTL) and revoking them via a central blocklist if reported stolen.

### JWT Structure

```
Header:   {"alg": "RS256", "typ": "JWT"}
Payload:  {"sub": "user123", "role": "admin", "exp": 1700000000}
Signature: HMACSHA256(base64(header) + "." + base64(payload), secret)
```

All three parts are Base64-encoded and joined with dots: `xxxxx.yyyyy.zzzzz`

**Important**: The payload is encoded, not encrypted. Anyone can decode it. Never put secrets in a JWT payload. Use HTTPS to protect it in transit.

### Access Token + Refresh Token Pattern

```
Access Token:  Short-lived (15 min). Stateless. Verifiable anywhere.
Refresh Token: Long-lived (7–30 days). Stored in the DB. Can be revoked.

Flow:
1. User logs in → get both tokens.
2. Use Access Token for API calls (fast, no DB hit).
3. Access Token expires → use Refresh Token to get a new Access Token.
4. If Refresh Token is revoked in DB → user must re-authenticate.
```

---

## Transport Security

### The TLS Handshake: Two Spies Meeting

> **Analogy: Two spies meeting for the first time at a café.**  
>
> Before they exchange any secrets, they need to verify each other's identities and agree on a private language no one else understands.
>
> 1. Spy A shows credentials (the **certificate** — issued and signed by a trusted authority, like a government ID).  
> 2. Spy B verifies the credentials are genuine (checks the **CA signature** — the certificate authority's stamp of approval).  
> 3. Together, they perform a quick ritual (the **Diffie-Hellman key exchange**) to agree on a secret code word — without ever saying the code word aloud in the café.  
> 4. From that point on, all conversation uses that private code. Anyone listening hears gibberish.

**TLS 1.3 Handshake Steps:**
1. **Client Hello**: Client sends supported cipher suites + key share.
2. **Server Hello**: Server selects cipher, sends its **Certificate** (containing the public key, signed by a CA).
3. **Key Exchange**: Diffie-Hellman establishes a shared symmetric key — never transmitted directly.
4. **Encrypted Session**: All subsequent data is encrypted with the symmetric key.

**SNI (Server Name Indication)**: Allows a single IP to serve certificates for multiple domains. The client includes the target hostname in the Client Hello so the server knows which certificate to present.

### HTTPS vs mTLS

> **Regular TLS: Only the server shows ID.**  
> When you visit your bank's website, your browser verifies that the server is genuinely the bank (via the certificate). The bank doesn't verify who you are at the TLS layer — it trusts the connection and uses a login form for that.
>
> **mTLS (Mutual TLS): Both sides show ID.**  
> In a Zero Trust microservice environment, Service A and Service B both present certificates when they connect. Neither side trusts the other just because they're on the same internal network. Both identities are cryptographically verified before any data flows. This is like a secure facility where every door requires both a key card and a fingerprint scan — from both sides.

| | TLS | mTLS |
|--|-----|------|
| Server authenticated? | Yes | Yes |
| Client authenticated? | No (at TLS layer) | Yes |
| Use case | Browser → Server (public web) | Service → Service (internal) |
| Credential type | Server cert (CA-signed) | Both sides have certs |

---

## CORS

### The Nightclub with a Guest List

> **CORS is like a nightclub bouncer with a guest list.**
>
> The nightclub (your API at `api.example.com`) has a list of approved venues that can send guests (origins like `app.example.com`, `partner.com`).
>
> When someone tries to enter, the bouncer (the browser's CORS enforcement) checks: "Is this person's origin on the list?" If yes, they walk right in. If not — regardless of how well-dressed they are, how much they argue, or how legitimate their business is — the bouncer turns them away at the door.
>
> Crucially, the bouncer doesn't stop the request from being made — the server still receives it. The bouncer stops the browser from showing the response to the requesting page. CORS is a browser security mechanism, not a server firewall.

```
Request from https://evil.com to https://api.bank.com:
→ Browser sends request (it arrives at the server)
→ Server responds, but without "Access-Control-Allow-Origin: https://evil.com"
→ Browser: blocks the response from being read by the evil.com script
→ User sees an error

Request from https://app.bank.com to https://api.bank.com:
→ Server responds with "Access-Control-Allow-Origin: https://app.bank.com"
→ Browser: allows the response through
```

**Preflight requests**: For non-simple requests (e.g., `PUT`, `DELETE`, custom headers), the browser sends an `OPTIONS` request first to check if the real request is allowed.

---

## Common Vulnerabilities

### 1. SQL Injection

> **Analogy: A magic phrase that unlocks any door.**  
> Imagine a locksmith who opens doors for anyone who says the right words. A normal customer says "please open room 204." But an attacker learns that saying "open any door, ignore the lock" makes the locksmith comply. The locksmith follows verbal instructions literally without questioning them.
>
> **Parameterized queries are a locksmith who only understands keys, not verbal commands.** You hand over a key (parameter), the locksmith checks if it fits the lock (the query is pre-compiled). No amount of clever phrasing can change what door gets opened.

- **Attack**: `user = ' OR 1=1 --'` — this closes the string early and appends a condition that's always true, returning all users.
- **Defense**: **Prepared Statements** (parameterized queries). The SQL structure is compiled first; user input is treated strictly as data, never as SQL code.
- **Never** concatenate user input directly into SQL strings.

### 2. XSS (Cross-Site Scripting)

> **Analogy: A forged message on a public bulletin board.**  
> An attacker pins a note to the bulletin board that says "Click here for a prize" — but the note actually activates a trap for everyone who reads the board. The bulletin board (your website) trusted the note because it looked like legitimate content.

- **Attack**: Injecting a `<script>` tag into user-generated content that other users see.
- **Defense**:
  - **Content Security Policy (CSP)** headers: tell browsers which scripts are trusted.
  - **Escape/Sanitize** HTML output before rendering.
  - **HttpOnly Cookies**: JavaScript cannot read auth cookies even if XSS occurs.

### 3. CSRF (Cross-Site Request Forgery)

> **Analogy: A forged letter with your signature.**  
> An attacker tricks you into signing a blank piece of paper, then writes a money transfer order above your signature. Your bank sees the real signature and executes the transfer, not knowing you never wrote the order.

- **Attack**: A malicious website causes your browser to send a request to `bank.com` using your existing session cookie.
- **Defense**: **CSRF Tokens** (a secret token tied to your session, included in every form). **SameSite Cookie** attribute prevents cookies from being sent on cross-site requests.

### 4. Rate Limiting for Security

> **Analogy: A bank ATM that locks after 3 wrong PINs.**  
> The ATM doesn't just protect against slow guessing — it assumes an attacker will try fast. After 3 failures, it locks the card. No amount of additional attempts helps.

Rate limiting is not just about QPS capacity — it's a security control:
- **Brute force protection**: Lock an account after N failed login attempts. Require CAPTCHA or exponential backoff.
- **Credential stuffing**: Rate limit login endpoints per IP. Detect and block accounts where the same password is tried across many usernames.
- **API abuse**: Token bucket per API key or IP to prevent scraping and DoS.

### 5. DDoS (Distributed Denial of Service)

- **Defense at Scale**:
  - **Rate Limiting**: Token bucket per IP at the edge.
  - **CDN**: Absorb volumetric attacks before they reach your origin. Cloudflare, Akamai, AWS CloudFront.
  - **WAF (Web Application Firewall)**: Block suspicious request patterns.
  - **Anycast routing**: Distribute attack traffic across multiple global data centers.

---

## Security Patterns

### Zero Trust Architecture

> **Analogy: A secure government facility where every door requires a badge scan, even if you're already inside the building.**  
> The old model assumed that if you were inside the perimeter (the corporate network), you could be trusted. Zero Trust assumes the perimeter doesn't exist — an attacker may already be inside. Every request, from any service to any other service, must authenticate and authorize.

- "Never trust, always verify."
- Network location implies zero trust — internal traffic is treated the same as external.
- Every request (even microservice-to-microservice) must be: **Authenticated** (who are you?), **Authorized** (are you allowed?), and **Encrypted** (mTLS).

### Secrets Management

- Never store secrets in code or git repositories.
- Use **HashiCorp Vault**, **AWS Secrets Manager**, or **GCP Secret Manager**.
- Rotate secrets regularly. Audit access to secrets.
- Use short-lived dynamic credentials where possible (Vault can issue a temporary DB password valid for 1 hour).

### Defense in Depth

> **Analogy: A medieval castle with multiple layers of defense.**  
> Moat → outer wall → inner wall → keep → vault. An attacker who breaches one layer doesn't immediately reach the crown jewels. Each layer buys time and limits blast radius.

Apply multiple, independent security controls so that the failure of any single control doesn't compromise the system:
- WAF at the edge
- API Gateway for auth/rate limiting
- mTLS between services
- Least-privilege IAM roles at the infrastructure level
- Encrypted data at rest
- Audit logging at every layer
