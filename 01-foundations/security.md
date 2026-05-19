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

**Question**: Your API receives a request claiming to be from user ID 42. How does your server know it's actually that user and not anyone who just typed `user_id=42` in a request? What stops me from impersonating any user I want just by guessing their ID?

**Physical constraint**: HTTP is stateless — each request arrives with no memory of previous interactions. The server has no inherent way to link a request to a prior login event. Something must travel with every request that proves identity. That something must be unforgeable by the client without the server's secret, and it must be verifiable by the server at ~0.3ns per CPU cycle (i.e., not require a synchronous DB call on every single request at scale).

**Minimal solution**: After login, generate a random session ID, store it server-side in a DB keyed to the user, and give the ID to the client as a cookie. On every request, look up the session ID in the DB. Works perfectly — until you have 100,000 requests/sec and each one requires a DB round-trip (~5ms) just for auth lookup, saturating your session store.

**Production generalization**: Two approaches emerge. Session-based auth uses a shared external store (Redis instead of a DB — ~0.5ms lookup) for fast lookup with easy revocation. Token-based auth (JWT) shifts verification to the CPU: the server signs a token with its private key, the client presents it, and any server verifies the signature in microseconds without any network call. The tradeoff: JWTs are hard to revoke before expiry, so you pair them with short-lived access tokens and longer-lived refresh tokens stored in the DB for revocation control.

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

**Question**: Authentication tells you *who* a user is. But user Alice is authenticated — she's definitely Alice. Should Alice be able to delete any order in the system? Access any other user's payment data? Issue a refund she didn't initiate? Authentication alone doesn't answer this. What's the second gate?

**Physical constraint**: Every protected API call must evaluate a policy — is this authenticated identity allowed to perform this action on this resource? That evaluation happens in the request hot path. At 50,000 req/sec, even a 1ms policy check adds 50 seconds of aggregate CPU time per second. Complex, attribute-rich policies that require DB lookups or external calls will either bottleneck your API or require aggressive caching of policy decisions.

**Minimal solution**: Hard-code a role check in your handler: `if (user.role != "ADMIN") return 403`. Works for one endpoint, one role. Breaks when you have 200 endpoints, 10 roles, and customers who need custom permission combinations — the hard-coded checks proliferate and become impossible to audit.

**Production generalization**: Three models match different scales of complexity. RBAC maps roles to permissions and assigns roles to users — simple to audit, works for most enterprise systems. ABAC evaluates dynamic attribute policies at runtime — powerful for fine-grained access, expensive to evaluate. ACLs attach a permission list to each individual resource — fine-grained, but unmanageable across millions of objects without a well-designed query layer.

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

**Question**: A third-party fitness app wants to read your Google Calendar to find free workout slots. The simplest approach: give the app your Google password. Now it has your full Google account. If the app is breached, your entire Google account is compromised. What mechanism lets you grant limited, revocable access to one resource without exposing your master credential?

**Physical constraint**: Credentials (username/password) are all-or-nothing — a service that has them has full access to the account. There is no built-in way for a password to grant "read-only access to calendar for 30 days." The only way to scope access is to issue a separate, limited credential — one that carries its own expiry and permission scope — and have the resource server validate that limited credential independently of the master one.

**Minimal solution**: The resource owner (Google) issues a time-limited, scoped token to the third-party app on your explicit approval. The app uses that token, not your password. If the app is compromised, you invalidate the token at Google's authorization server. Your Google password is never exposed.

**Production generalization**: OAuth 2.0 formalizes this flow — it defines how tokens are requested, issued, and validated across four roles (resource owner, client, authorization server, resource server). Different grant flows handle different trust models: a server-side web app that can keep a secret uses Authorization Code flow; a mobile app that cannot uses PKCE. OIDC adds identity on top of OAuth's authorization — the `id_token` tells you who the user is, while the `access_token` tells you what they can do.

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

**Question**: You issue a session token to a user. At 100,000 requests/sec, every API call verifies this token. If verification requires a DB lookup (is this session ID still valid?), you need 100,000 DB reads/sec just for auth — before any business logic runs. What if you could verify the token using only the CPU, with no network call at all?

**Physical constraint**: A DB lookup takes ~1–5ms over the network. At 100,000 req/sec with 1ms auth lookup per request, auth alone consumes 100 seconds of DB query time per second — requiring 100 parallel DB connections just for auth. CPU instruction execution is ~0.3ns. Cryptographic signature verification (RSA or HMAC) runs in microseconds on the CPU. If the token's validity can be proven mathematically rather than by DB lookup, the auth overhead drops by 3–4 orders of magnitude.

**Minimal solution**: Sign a JSON payload with the server's private key. Anyone with the public key can verify the signature in microseconds. The payload contains the user ID, roles, and expiry — no DB needed. Breaks at: token revocation. You can't invalidate a signed JWT before its `exp` claim fires, because there's no registry to check. A stolen token is valid until expiry.

**Production generalization**: Short-lived access tokens (15 min) limit the revocation window to an acceptable level. Long-lived refresh tokens are stored in the DB so they can be explicitly revoked when needed. The access token does the high-frequency, stateless verification; the refresh token does the infrequent, stateful revocation check when rotating access tokens.

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

**Question**: You send your credit card number to a website. The packet travels through your home router, your ISP's network, several backbone routers, and the destination datacenter — roughly 10–15 network hops. Any one of those hops could be operated by someone malicious. How do you send data that only the destination can read, without pre-sharing a secret with them over a secure channel (which doesn't exist yet)?

**Physical constraint**: Public network packets can be read by anyone operating an intermediate router — this is not a theoretical threat, it is the default nature of TCP/IP routing. The problem of establishing a shared secret over a public channel without the secret ever appearing in plaintext on that channel was considered mathematically impossible until Diffie-Hellman (1976). The key insight: two parties can agree on a shared secret by each contributing a public value derived from a private one, such that an observer sees only the public values but cannot reconstruct the secret.

**Minimal solution**: Use asymmetric cryptography to establish a shared symmetric key (expensive but only done once per connection), then encrypt all subsequent data with that symmetric key (fast). This is exactly what TLS does: the handshake establishes the session key, and then AES-GCM encrypts the stream at ~10 GB/sec per CPU core.

**Production generalization**: TLS 1.3 reduced the handshake from 2 RTTs (TLS 1.2) to 1 RTT, and to 0-RTT for session resumption. The certificate chain from a trusted CA solves the "how do I know this is really the bank's server?" problem — the CA has already vouched for the domain owner. mTLS extends this to also authenticate the client, used in zero-trust internal service communication.

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

**Question**: You're logged into your bank at `bank.com`. In another tab, you visit `evil.com`. That page runs JavaScript that makes a `POST /transfer` request to `bank.com/api/transfer` — and your browser helpfully includes your `bank.com` session cookie. The bank's server receives a valid, authenticated request to transfer money. You never clicked anything on the bank's site. What prevents this?

**Physical constraint**: Browsers automatically include cookies for a domain on any HTTP request to that domain, regardless of which page initiated the request. The network request arrives at the server with valid credentials. The server has no way to distinguish "this request came from my own page" from "this request came from evil.com" just by looking at the request. The defense must happen at the browser, not the server — the browser must decide whether to expose the server's response to the initiating script.

**Minimal solution**: Same-Origin Policy (SOP): browsers block scripts on `evil.com` from reading responses from `bank.com`. The request still reaches the server, but the browser suppresses the response. This handles reads. Writes (POST, PUT, DELETE that mutate state) require a CSRF token or SameSite cookie attribute as an additional defense.

**Production generalization**: CORS allows servers to explicitly whitelist origins that are permitted to read cross-origin responses — enabling legitimate cross-origin use cases (a React app on `app.example.com` calling `api.example.com`) while blocking all others. CSRF tokens defend against state-mutating requests from foreign origins by requiring a secret that only the legitimate page can possess. The `SameSite=Strict` cookie attribute prevents cookies from being sent on cross-site requests at all — the cleanest defense for most modern apps.

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

**Question**: Your login endpoint is: `SELECT * FROM users WHERE email = '<input>' AND password = '<input>'`. A user submits the email `' OR 1=1 --`. The resulting query is: `SELECT * FROM users WHERE email = '' OR 1=1 --' AND password = '...'`. The `OR 1=1` makes the WHERE clause always true; the `--` comments out the rest. The query returns every user in the database. You are now logged in as the first user in the table — likely an admin. How does this happen, and what stops it?

**Physical constraint**: SQL query construction via string concatenation conflates two distinct channels: the *structure* of the query (SQL syntax) and the *data* being queried (user input). The database parser sees both channels as one string and cannot distinguish between "syntax the developer wrote" and "syntax the user injected." At the CPU level, the SQL is compiled from the final string — there is no memory of which characters came from code versus user input.

**Minimal solution**: Compile the query structure first, separately from the data. Parameterized queries (prepared statements) send the SQL template to the database engine once for compilation, then bind data values as typed parameters. The database treats parameter values as pure data — no amount of SQL syntax inside a parameter can affect the query structure.

**Production generalization**: Parameterized queries solve SQL injection completely when used consistently. Defense in depth adds: least-privilege DB user (the app account cannot DROP tables), WAF rules at the edge to block obvious injection attempts, and input validation as an early rejection layer. For ORM users: verify your ORM actually uses parameterized queries — raw query methods often don't.

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

**Question**: Your microservices run on a private corporate network. Service A calls Service B directly over HTTP — no authentication between them, because "they're on the same trusted network." An attacker gains access to any one internal machine — maybe via a compromised developer laptop, a supply-chain attack in a dependency, or a misconfigured cloud storage bucket that exposed credentials. That attacker is now on the "trusted" network. What prevents them from calling Service B directly and exfiltrating all user data?

**Physical constraint**: A network perimeter assumes that everything inside is trustworthy — but the perimeter can be breached. Lateral movement (attacker moving from the initial foothold to more sensitive services) is fast: a network-level attacker with valid credentials can issue API calls in milliseconds, limited only by discovery time. If internal services have no authentication layer, the attacker has read/write access to every service the compromised machine can reach.

**Minimal solution**: Treat every service-to-service call as untrusted until proven otherwise. Require every request to carry a verifiable credential (mTLS certificate or a short-lived service token). This is the Zero Trust principle applied at the service level.

**Production generalization**: Multiple complementary security layers ensure that breaching one layer doesn't immediately expose the crown jewels. WAF at the edge filters malformed requests. API Gateway enforces auth and rate limiting. mTLS between services ensures each service verifies its caller's identity cryptographically. Least-privilege IAM roles mean a compromised service can only access the resources it legitimately needs. Audit logging at every layer creates an evidence trail for breach detection and forensics.

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
