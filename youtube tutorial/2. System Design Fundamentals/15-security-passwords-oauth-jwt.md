# Security: Passwords, OAuth, JWT & Sessions

> **Source**: Videos #22, #33, #48, #61, #71 from the playlist
> - System Design: How to store passwords in the database?
> - OAuth 2 Explained In Simple Terms
> - Why is JWT popular?
> - Top 12 Tips For API Security
> - Session Vs JWT: The Differences You May Not Know!

---

## Password Storage

### NEVER store passwords in plain text!

### Proper Password Storage Flow
```
Registration:
  password → hash(password + salt) → store hash + salt in DB

Login:
  password → hash(password + stored_salt) → compare with stored hash
```

### Hashing Algorithms (Best → Worst)
| Algorithm | Status | Notes |
|---|---|---|
| **Argon2** | ✅ Best | Winner of Password Hashing Competition |
| **bcrypt** | ✅ Great | Built-in salt, configurable rounds |
| **scrypt** | ✅ Good | Memory-hard (resists GPU attacks) |
| **PBKDF2** | ⚠️ OK | Used by Django, configurable iterations |
| **SHA-256** | ❌ Bad | Too fast, no salt, vulnerable to rainbow tables |
| **MD5** | ❌ Terrible | Broken, never use for passwords |

### Key Concepts
- **Salt**: Random value added to password before hashing (prevents rainbow tables)
- **Pepper**: Secret value added server-side (defense in depth)
- **Work factor**: How many iterations — higher = slower = more secure

---

## OAuth 2.0

### What is OAuth 2.0?
Authorization framework that lets third-party apps access user resources **without sharing passwords**.

### The Flow (Authorization Code Grant)
```
1. User clicks "Login with Google" on YourApp
2. YourApp redirects to Google's auth page
3. User logs in to Google and grants permission
4. Google redirects back with authorization code
5. YourApp exchanges code for access token (server-to-server)
6. YourApp uses access token to call Google APIs
```

### Key Terms
| Term | Description |
|---|---|
| **Resource Owner** | The user |
| **Client** | Your application |
| **Authorization Server** | Google, Facebook (issues tokens) |
| **Resource Server** | API that holds user data |
| **Access Token** | Short-lived token to access resources |
| **Refresh Token** | Long-lived token to get new access tokens |
| **Scope** | Permissions (read:email, write:posts) |

### Grant Types
| Grant | Use Case |
|---|---|
| **Authorization Code** | Server-side web apps (most secure) |
| **Authorization Code + PKCE** | Mobile/SPA apps |
| **Client Credentials** | Machine-to-machine (no user) |
| **Implicit** | ❌ Deprecated — use PKCE instead |

---

## JWT (JSON Web Token)

### Structure
```
Header.Payload.Signature

Header:    { "alg": "HS256", "typ": "JWT" }
Payload:   { "user_id": 123, "role": "admin", "exp": 1700000000 }
Signature: HMACSHA256(base64(header) + "." + base64(payload), secret)
```

### Why JWT is Popular
1. **Stateless**: No server-side session storage needed
2. **Self-contained**: Token carries user info (claims)
3. **Scalable**: Any server can validate (no shared session store)
4. **Cross-domain**: Works across different services/domains

### JWT Limitations
- Can't be revoked easily (until expiry)
- Payload is base64-encoded, NOT encrypted (don't put secrets)
- Token size can be large (sent with every request)

---

## Session vs JWT

| Feature | Session-Based | JWT |
|---|---|---|
| **Storage** | Server-side (DB/Redis) | Client-side (cookie/header) |
| **Stateful/Stateless** | Stateful | Stateless |
| **Scalability** | Requires shared session store | Easy horizontal scaling |
| **Revocation** | Easy (delete from store) | Hard (need blocklist) |
| **Size** | Small session ID | Larger (carries claims) |
| **Security** | Server controls data | Token can be decoded |
| **Best For** | Traditional web apps | APIs, microservices, mobile |

### Best Practices
- Use **short-lived access tokens** (15 min) + **refresh tokens** (7 days)
- Store tokens in **HttpOnly, Secure cookies** (not localStorage)
- Implement **token rotation** on refresh
- Use **refresh token revocation** for logout

---

## API Security Checklist

1. ✅ Use HTTPS everywhere
2. ✅ Authenticate with OAuth 2.0 / JWT
3. ✅ Rate limit all endpoints
4. ✅ Validate and sanitize all inputs
5. ✅ Use parameterized queries (prevent SQL injection)
6. ✅ Implement CORS properly
7. ✅ Don't expose sensitive data in URLs
8. ✅ Use API keys for service-to-service auth
9. ✅ Log and monitor all API access
10. ✅ Implement request size limits
11. ✅ Use security headers (CSP, X-Frame-Options)
12. ✅ Keep dependencies updated
