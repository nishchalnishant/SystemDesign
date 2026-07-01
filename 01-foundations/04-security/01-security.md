> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Security fundamentals for distributed systems — the mechanisms that go beyond passwords: mTLS, encryption at rest/in transit, secrets management, and defense-in-depth patterns.
>
> **Key topics:**
> - **Authentication vs Authorization** — AuthN (who) vs AuthZ (what)
> - **HTTPS/TLS** — how the handshake works, certificate chains, pinning
> - **mTLS** — mutual authentication between services, zero-trust networking
> - **Encryption at rest** — AES-256, envelope encryption, KMS
> - **Secrets management** — why `.env` files are wrong at scale, Vault/KMS patterns
> - **RBAC vs ABAC** — role-based vs attribute-based access control
> - **OWASP Top 10** — the attack surfaces you must design against
>
> **Key takeaway:** The goal is defense in depth — assume any perimeter will be breached. Encrypt everything, authenticate every service-to-service call, and never store a secret in a repo or config file.

---
module: 01-foundations
status: unread
tags: [01-foundations, system-design, security]
---
# Security in Distributed Systems

For JWT, OAuth 2.0, and token management depth, see `02-oauth-jwt.md`.

---

## Authentication vs Authorization

| Term | Question | Mechanism |
|---|---|---|
| **Authentication (AuthN)** | Who are you? | Password + MFA, JWT, mTLS certificates |
| **Authorization (AuthZ)** | What are you allowed to do? | RBAC, ABAC, IAM policies, OPA |

These are always separate layers. Authentication happens first (at the API gateway/LB); authorization happens per-request in the service logic.

---

## Encryption in Transit: TLS

All data moving across a network — between client and server, between microservices — must be encrypted. TLS (Transport Layer Security, colloquially "HTTPS") is the standard.

### TLS Handshake (Simplified)

```
Client                          Server
  |                               |
  |-- ClientHello (TLS version, cipher suites) →
  |                               |
  |← ServerHello (chosen cipher) + Certificate (public key)
  |                               |
  | [Client verifies cert against trusted CA]
  |                               |
  |-- Key Exchange (client random, encrypted with server's public key) →
  |                               |
  | [Both sides derive symmetric session key]
  |                               |
  |── Encrypted data (AES-256-GCM) ──────────── |
```

After the handshake, all data is encrypted with a **symmetric key** (AES-256-GCM). Public-key crypto is only used for the handshake (too slow for bulk data).

### Certificate Chains

A server certificate is signed by an intermediate CA, which is signed by a root CA. Clients trust root CAs (pre-installed in OS/browser). The chain allows verification without directly trusting every leaf certificate.

```
Root CA (self-signed, trusted by OS)
  └─ Intermediate CA (signed by root)
      └─ Server cert for api.example.com (signed by intermediate)
```

**Let's Encrypt** automates free certificate issuance via ACME protocol. Certificates expire every 90 days; automate renewal with cert-manager (Kubernetes) or certbot.

### TLS Termination

In a microservices setup, TLS is typically terminated at the edge (API gateway / load balancer). Traffic between internal services may be plain HTTP (not ideal) or re-encrypted (better).

---

## Mutual TLS (mTLS)

Standard TLS is one-directional: the client verifies the server's identity (via certificate). The server doesn't verify the client.

**mTLS** is bidirectional: **both** sides present certificates. Each service has a certificate signed by an internal CA. Before any data flows, both sides verify the other's identity.

```
Service A                            Service B
  |                                      |
  |── "Here's my cert (signed by internal CA)" ──→
  |                                      |
  |← "Here's MY cert (signed by internal CA)"
  |                                      |
  | [Both verify via internal CA]
  |                                      |
  |─────── Encrypted, mutually authenticated data ──────|
```

### Why mTLS Matters: Zero-Trust Networking

Traditional network security assumes "internal network = trusted." If an attacker compromises one internal service, they can call any other service freely.

Zero-trust: **no implicit trust based on network location**. Every service must authenticate to every other service. mTLS enforces this — a service can only call another if it has a valid certificate.

**Practical implementation:** Service meshes (Istio, Linkerd) automatically issue and rotate certificates for every service pod. The application doesn't implement mTLS; the sidecar proxy handles it.

```yaml
# Istio: enforce strict mTLS across the entire namespace
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: default
  namespace: production
spec:
  mtls:
    mode: STRICT  # reject any non-mTLS traffic
```

### Certificate Rotation

Certificates expire. Rotation must be automatic — manual rotation at scale (thousands of pods) is infeasible and error-prone.

In Istio, certificates are rotated every 24 hours by default using SPIFFE (Secure Production Identity Framework for Everyone) — a standard for workload identity.

---

## Encryption at Rest

Data stored on disk must be encrypted. If a disk is stolen or a cloud storage bucket is misconfigured, the data is unreadable.

### AES-256

The standard symmetric cipher for bulk data encryption. AES-256 in GCM mode provides both confidentiality and integrity.

All major databases and cloud storage services support transparent encryption at rest:
- **PostgreSQL:** Tablespace encryption (or OS-level with dm-crypt/LUKS)
- **AWS RDS:** AES-256, enabled at instance creation
- **S3:** Server-side encryption (SSE-S3, SSE-KMS, SSE-C)
- **Kafka:** Disk encryption via OS layer; payload encryption in the application

### Envelope Encryption

The key management problem: if you encrypt data with a key, where do you store the key? Not on the same disk.

Envelope encryption solves this with two-level key hierarchy:

```
[Data Encryption Key (DEK)]
  → Encrypts the actual data (AES-256)
  → Generated per-object or per-field
  → Stored alongside the encrypted data, but itself encrypted

[Key Encryption Key (KEK)] — stored in KMS
  → Encrypts the DEK
  → Never leaves the KMS (all crypto happens in KMS hardware)
```

```
Encrypt:
  1. Generate DEK (random 256-bit key)
  2. Encrypt data with DEK
  3. Call KMS: Encrypt(DEK) → encrypted_DEK
  4. Store: {encrypted_data, encrypted_DEK}

Decrypt:
  1. Load: {encrypted_data, encrypted_DEK}
  2. Call KMS: Decrypt(encrypted_DEK) → DEK
  3. Decrypt data with DEK
```

**Why:** Key rotation only requires re-encrypting the DEK (tiny), not re-encrypting all data (massive). Compromise of the KEK is controlled by the KMS hardware.

**Tools:** AWS KMS, Google Cloud KMS, HashiCorp Vault, Azure Key Vault.

---

## Secrets Management

Secrets are credentials that must be kept confidential: database passwords, API keys, TLS private keys, OAuth client secrets.

### What Not to Do

| Anti-pattern | Risk |
|---|---|
| Store in git repo | Any repo clone = credential exposure; git history is permanent |
| Store in environment variables on disk | Visible to all processes, logged by accident |
| Hardcode in source code | Inevitably committed, audited, and rotated painfully |
| Store in config files | Config files end up in S3, backups, CI artifacts |

### Secrets Management with HashiCorp Vault

Vault is the industry standard for dynamic secrets:

```
Application → Vault API → Vault Server → [encrypted secret store]

Dynamic secrets:
  App requests: "give me Postgres credentials"
  Vault creates: a temporary DB user valid for 1 hour
  App uses it, it expires automatically
  No long-lived static credentials
```

**Key capabilities:**
- **Dynamic secrets:** Generate short-lived credentials per-request (Postgres, AWS IAM, SSH).
- **Leases and renewal:** Credentials expire; services must renew or they're automatically revoked.
- **Audit log:** Every secret access is logged.
- **Encryption as a service:** Encrypt/decrypt without exposing keys to the caller.

### Kubernetes Secrets + External Secrets Operator

In Kubernetes, secrets are stored in etcd (base64-encoded, not encrypted by default — enable etcd encryption at rest). For production:
- Encrypt etcd at rest.
- Use **External Secrets Operator** to sync from AWS Secrets Manager, Vault, or GCP Secret Manager into Kubernetes Secrets.
- Mount as files or environment variables into pods.

```yaml
# External Secrets Operator: sync from AWS Secrets Manager
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: db-credentials
spec:
  secretStoreRef:
    name: aws-secrets-manager
    kind: SecretStore
  target:
    name: db-credentials  # Kubernetes Secret name
  data:
  - secretKey: password
    remoteRef:
      key: prod/postgres/credentials
      property: password
```

---

## Access Control: RBAC vs ABAC

### Role-Based Access Control (RBAC)

Users are assigned roles. Roles have permissions. Simple and fast.

```
User Alice → role: admin → permissions: [read, write, delete]
User Bob   → role: viewer → permissions: [read]
```

Used by: Kubernetes (`ClusterRole`, `RoleBinding`), AWS IAM, most web applications.

**Limitation:** Roles become numerous when fine-grained control is needed (admin-for-region-X, viewer-for-resource-type-Y). Role explosion.

### Attribute-Based Access Control (ABAC)

Policies evaluated against attributes of the user, resource, and environment. Much more expressive.

```
Allow access if:
  user.department == resource.department
  AND user.clearance_level >= resource.classification
  AND request.time BETWEEN 09:00 AND 18:00
```

**Tools:** Open Policy Agent (OPA), AWS Verified Permissions, Cedar (Amazon).

Used by: Google, AWS at the policy engine level, enterprise compliance-heavy systems.

---

## OWASP Top 10 — Design-Level Defenses

These are the most common attack classes. Each has a design-level mitigation.

| Attack | Mitigation |
|---|---|
| **Injection (SQL, command)** | Parameterized queries, ORM; never interpolate user input into SQL |
| **Broken Authentication** | Short-lived JWTs, MFA, rate-limit login attempts |
| **Sensitive Data Exposure** | Encrypt at rest (AES-256) + in transit (TLS); no PII in logs |
| **Broken Access Control** | Enforce authZ on every endpoint; deny by default; validate on server |
| **Security Misconfiguration** | Disable debug endpoints in prod; remove default credentials; least-privilege IAM |
| **XSS** | Content-Security-Policy headers; sanitize and escape all user-generated output |
| **Insecure Deserialization** | Never deserialize untrusted data into objects; use schema validation |
| **Using Components with Known Vulnerabilities** | SCA (Snyk, Dependabot); pin dependency versions; regular audits |
| **Insufficient Logging** | Log all auth events, access denials; structured logs with trace IDs; alert on anomalies |
| **SSRF** | Validate and allowlist outbound URLs; block metadata endpoints (169.254.169.254) |

---

## Defense in Depth

No single security control is sufficient. Layer them:

```
Internet
  │
  ▼
WAF (Web Application Firewall)  ← blocks OWASP attacks, DDoS
  │
  ▼
API Gateway + Rate Limiter       ← AuthN, rate limiting
  │
  ▼
Service Mesh (mTLS)              ← zero-trust east-west
  │
  ▼
Application AuthZ                ← RBAC / ABAC per endpoint
  │
  ▼
Encrypted DB (AES-256 at rest)   ← data protection
  │
  ▼
Secrets in Vault / KMS           ← no static credentials anywhere
```

Breach any one layer → the others still protect the data.

---

## Interview Questions to Practice

1. **"What is the difference between encryption in transit and encryption at rest? When do you need each?"**
   *Encryption in transit (TLS) protects data moving over the network — prevents eavesdropping on public or internal networks. Encryption at rest (AES-256) protects data stored on disk — if a disk is stolen or a DB dump is leaked, data is unreadable. You need both: TLS for all HTTP traffic (and between internal services via mTLS), AES-256 for all persistent storage (DB, S3, Kafka topics with sensitive data).*

2. **"What is mTLS and when would you use it?"**
   *Mutual TLS means both client and server present certificates; both verify the other's identity before any data flows. Standard TLS only verifies the server. Use mTLS for internal service-to-service communication in a zero-trust network — it prevents a compromised service from calling other services without a valid certificate. Service meshes (Istio, Linkerd) implement mTLS transparently via sidecars.*

3. **"How do you manage database credentials in a production Kubernetes cluster?"**
   *Never put credentials in code or unencrypted config. Use Vault (HashiCorp) or AWS Secrets Manager with dynamic credentials: the app requests Postgres credentials from Vault at runtime; Vault creates a temporary DB user valid for 1 hour and returns the credentials; they expire automatically. In Kubernetes, use External Secrets Operator to sync secrets into Kubernetes Secret objects and mount them as files into pods. Enable etcd encryption at rest so secrets are encrypted in the cluster's data store.*

4. **"Describe envelope encryption and why it's used."**
   *Envelope encryption uses two-level key hierarchy: a Data Encryption Key (DEK) encrypts the data, and a Key Encryption Key (KEK) encrypts the DEK. The KEK never leaves the KMS hardware. This lets you rotate keys efficiently — rotating the KEK only requires re-encrypting the (small) DEK, not re-encrypting all data. Compromise of the DEK exposes only that one object; the KEK is protected by KMS hardware. AWS KMS, GCP KMS, and Vault all use this pattern.*

5. **"How would you prevent SQL injection in a system that takes user input and queries a database?"**
   *Never interpolate user input into SQL strings. Always use parameterized queries (prepared statements) or an ORM that handles parameterization. Example: `cursor.execute("SELECT * FROM users WHERE id = %s", (user_id,))` — the DB driver handles escaping. At design level: apply least-privilege DB roles (the app user has only SELECT/INSERT, not DROP TABLE), use an ORM with input validation, and add WAF rules to detect common injection patterns at the edge.*
