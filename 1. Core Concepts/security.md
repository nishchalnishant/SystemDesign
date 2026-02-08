# Security - System Design Guide

> **For SDE-3 Interview Preparation**  
> Securing distribute systems, Authentication vs Authorization, and Common Vulnerabilities.

## Table of Contents
1. [Authentication (AuthN) Types](#authentication-types)
2. [Authorization (AuthZ) Models](#authorization-models)
3. [OAuth 2.0 & OIDC](#oauth-20--oidc)
4. [Transport Security (TLS/HTTPS)](#transport-security)
5. [Common Vulnerabilities (OWASP)](#common-vulnerabilities)
6. [Security Patterns](#security-patterns)

---

## Authentication Types

### 1. Session-Based Auth (Stateful)
- **Flow**: User logs in -> Server creates session ID -> Stored in DB/Redis -> Sent to client as Cookie.
- **Pros**: Easy revocation (delete session).
- **Cons**: Server memory/storage lookups, harder to scale across regions.

### 2. Token-Based Auth (Stateless - JWT)
- **Flow**: User logs in -> Server signs JWT -> Sent to client.
- **JWT Structure**: Header + Payload (Claims) + Signature.
- **Verification**: CPU only (verify crypto signature), no DB lookup needed.
- **Challenges**:
    - **Revocation**: Hard to revoke (JWT is valid until expiry).
    - **Solution**: Short-lived Access Token (15 min) + Refresh Token (7 days). Store Refresh Token in DB.

### 3. Mutual TLS (mTLS)
- **Definition**: Client and Server authenticate *each other* using Certificates.
- **Use Case**: Zero Trust microservices (Service Mesh like Istio).
- **Pros**: Extremely secure against MITM and spoofing.
- **Cons**: Certificate management complexity.

---

## Authorization Models

### 1. RBAC (Role-Based Access Control)
- Assign permissions to **Roles** (Admin, Editor, Viewer).
- Assign **Roles** to **Users**.
- **Simple**, widely used.

### 2. ABAC (Attribute-Based Access Control)
- Fine-grained. Based on policies.
- "Allow if User.Department == IT AND Resource.Type == Server AND Time < 5PM".
- **Dynamic** but complex to evaluate.

### 3. ACL (Access Control Lists)
- List of permissions attached to a specific object.
- "File A: User1 (Read), User2 (Write)".

---

## OAuth 2.0 & OIDC

### Concepts
- **Resource Owner**: User.
- **Client**: The App (Web/Mobile).
- **Authorization Server**: Identity Provider (Google/Okta).
- **Resource Server**: The API holding data.

### Common Flows

#### 1. Authorization Code Flow (Standard)
Best for Web Apps.
1. Client redirects User to Auth Server.
2. User logs in.
3. Auth Server redirects back with `code`.
4. Client exchanges `code` + `client_secret` for `access_token`.

#### 2. Authorization Code with PKCE (Mobile/SPA)
For public clients that can't hide a `client_secret`.
- Uses a `code_verifier` (random string) and `code_challenge` (hash) to prevent code interception.

#### 3. Client Credentials Flow
Machine-to-Machine (Service A calling Service B).
- No User involved.
- Service A sends `client_id` + `client_secret` to get token.

### OIDC (OpenID Connect)
- Layer on top of OAuth 2.0.
- Adds **Authentication** (Identity).
- Returns `id_token` (JWT) with user info (email, name).

---

## Transport Security

### HTTPS / TLS 1.3
1. **Client Hello**: Supported ciphers.
2. **Server Hello**: Selected cipher + Certificate (Public Key).
3. **Key Exchange**: Diffie-Hellman to establish shared *symmetric* key.
4. **Encrypted Session**: All data encrypted with shared key.

**SNI (Server Name Indication)**: Allows responding with correct cert for multiple domains on one IP.

---

## Common Vulnerabilities

### 1. SQL Injection
- **Attack**: Malicious SQL in input `user = ' OR 1=1 --`.
- **Defense**: **Prepared Statements** (Parameterized queries). Never concatenate strings.

### 2. XSS (Cross-Site Scripting)
- **Attack**: Injecting JS scripts into pages viewed by others.
- **Defense**:
    - **Content Security Policy (CSP)** headers.
    - **Escape/Sanitize** HTML output.
    - **HttpOnly Cookies** (JS can't read auth cookies).

### 3. CSRF (Cross-Site Request Forgery)
- **Attack**: Malicious site tricks browser into sending request to bank.com (using user's cookie).
- **Defense**: **CSRF Tokens** (hidden form fields), **SameSite Cookie** attribute.

### 4. DDoS (Distributed Denial of Service)
- **Defense at Scale**:
    - **Rate Limiting**: Token bucket per IP.
    - **CDN**: Absorb volumetric attacks.
    - **WAF (Web App Firewall)**: Block suspicious patterns.

---

## Security Patterns

### Zero Trust Architecture
- "Never trust, always verify".
- Network location implies no trust.
- Every request (even internal) must be Authenticated, Authorized, and Encrypted (mTLS).

### Secrets Management
- Never store secrets in code/git.
- Use **Vault / AWS Secrets Manager**.
- Rotate secrets regularly.
