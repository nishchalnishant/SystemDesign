---
module: 07-interview-templates
topic: Security and Compliance
status: unread
tags: [07-interview-templates, security, compliance, oauth, jwt, zero-trust]
---
# Security & Compliance Checklist (System Design Level)

> Interview focus: architecture-level decisions, not code-level vulnerability fixes.
> When asked "how would you secure this system?", answer in terms of perimeter, identity, data, and audit — not "input validation" and "parameterized queries."

---

## Quick Checklist (8 Items)

```
□ 1. AuthN/AuthZ:  who can call this service? (OAuth 2.0 / API key / mTLS for service-to-service)
□ 2. Token lifecycle: how are tokens issued, refreshed, and revoked?
□ 3. Data in transit: TLS everywhere, mTLS for internal service calls
□ 4. Data at rest: encryption for PII / secrets; KMS for key management
□ 5. Secret management: no secrets in env vars or code; use Vault/AWS Secrets Manager
□ 6. Zero-trust network: no implicit trust on the internal network; verify every call
□ 7. Audit logging: immutable log of who did what to which resource and when
□ 8. Compliance hooks: data residency, PII retention limits, right-to-erasure
```

---

## Authentication & Authorization (AuthN/AuthZ)

### Protocol Selection by Use Case

| Caller | Protocol | Reason |
|---|---|---|
| Browser/mobile (user-facing) | OAuth 2.0 + OIDC | Federated identity, user consent, standard token format |
| Service-to-service (internal) | mTLS or JWT with short TTL | No user; machine identity via cert or signed token |
| Third-party API integration | OAuth 2.0 Client Credentials | Scoped access without sharing credentials |
| External developer (API key) | API key + HMAC signing | Simple; suitable for webhook receivers and SDKs |

### OAuth 2.0 Flow Selection

```
Authorization Code + PKCE  → web/mobile apps where user grants consent
  1. App redirects to Auth Server with code_challenge
  2. User logs in, grants consent
  3. Auth Server returns authorization code
  4. App exchanges code + code_verifier for access_token + refresh_token
  5. Use access_token (short TTL: 15 min) for API calls
  6. Use refresh_token (long TTL: 30 days) to get new access_tokens

Client Credentials → service-to-service (no user)
  1. Service POSTs client_id + client_secret to token endpoint
  2. Gets access_token with requested scope
  3. Calls downstream service with Bearer token
  Note: client_secret stored in Vault, not env var

Device Code → TV apps, IoT where keyboard input is hard
Resource Owner Password → ONLY for internal tools (deprecated for public apps)
Implicit → DEPRECATED (replaced by Auth Code + PKCE)
```

### JWT Structure and Security

```
JWT = base64url(header) . base64url(payload) . signature

Header: { "alg": "RS256", "typ": "JWT" }
Payload: {
  "sub": "user_id_123",
  "iss": "https://auth.company.com",
  "aud": "https://api.company.com",
  "exp": 1718000000,
  "iat": 1717999000,
  "scope": "read:orders write:orders",
  "jti": "unique-token-id"   ← JWT ID for revocation
}
Signature: RS256(header + "." + payload, private_key)
```

**Critical JWT fields:**
- `exp` — expiry; always validate; short TTL (15 min for access tokens)
- `aud` — audience; validate the token was issued FOR your service
- `iss` — issuer; validate token came from your auth server
- `jti` — unique ID; required for revocation (store revoked JTIs in Redis with TTL = token TTL)

### Token Revocation (The Hard Problem)

```
Problem: JWTs are self-contained and stateless. A stolen token is valid until expiry.
         If a user logs out or changes password, tokens issued before that are still valid.

Option 1: Short TTL (15 min access token) + refresh token rotation
  - Compromised access token is valid for ≤ 15 minutes
  - On logout: invalidate refresh token (DB lookup) — prevents new access tokens
  - Acceptable for most applications

Option 2: JWT token revocation list (blocklist)
  - On logout/password change: store jti in Redis with TTL = token remaining lifetime
  - Every API validation checks Redis: O(1) lookup
  - Redis becomes a critical dependency for all auth checks
  - Use when: compliance requires immediate revocation (financial, healthcare)

Option 3: Opaque tokens (reference tokens)
  - Auth server issues a random string, not a JWT
  - API gateway calls auth server introspection endpoint on every request
  - Revocation is trivial (delete from auth server DB)
  - Cost: every request incurs a network hop to auth server (mitigate: cache for 30s)
  - Use when: maximum revocation control required
```

### RBAC vs ABAC

```
RBAC (Role-Based Access Control):
  User → has roles → roles have permissions
  Example: user has role "order:admin" → can cancel any order
  Simple to manage; use for most systems

ABAC (Attribute-Based Access Control):
  Policy engine evaluates: user attributes + resource attributes + environment
  Example: user.department == resource.department AND time.hour < 18
  Flexible but complex; use for fine-grained multi-tenant access (OPA / Cedar)

Interview shortcut:
  "We'd use RBAC at the service boundary (coarse-grained access: can this user access Orders Service?)
   and ABAC or ownership checks within the service (can this user edit THIS specific order?)"
```

---

## Data in Transit

### TLS Everywhere

```
External traffic: TLS 1.2 minimum, TLS 1.3 preferred
  - Terminate TLS at load balancer / API gateway, not at application
  - Use HSTS header to prevent protocol downgrade attacks
  - Certificate rotation: use managed certs (AWS ACM, GCP-managed) — auto-rotate

Internal traffic (service-to-service): two options
  Option 1: mTLS — both sides present certificates; mutual identity
  Option 2: JWT Bearer token — caller presents signed JWT to each downstream service
  Recommendation: mTLS via service mesh (Istio) — transparent to app code
```

### mTLS for Service Identity

```
Each service has a certificate issued by internal CA (SPIFFE/SPIRE or Istio CA).
Certificate contains: workload identity (SPIFFE URI: spiffe://cluster.local/ns/prod/sa/payments)

On each connection:
  Caller presents cert → receiver validates against CA
  Receiver presents cert → caller validates against CA
  Connection established only if both certs are valid

Benefits:
  - Service identity is cryptographic, not "came from the internal network"
  - Works in zero-trust model (no implicit trust by IP range)
  - Lateral movement attacks blocked (compromised service A can't impersonate service B)
```

---

## Data at Rest

### Encryption Strategy

```
Layer 1: Disk/storage encryption
  S3 SSE-KMS, EBS encryption, RDS encryption at rest
  Transparent to application; protects against physical disk theft

Layer 2: Application-level encryption (field-level)
  Encrypt specific sensitive fields (SSN, CC number, PII) in the application
  before writing to DB
  Allows: revoke field-level key without re-encrypting entire DB
  Use for: PCI DSS (credit cards), HIPAA (health data)

Layer 3: Client-side encryption
  Data encrypted on client before leaving device
  Server never sees plaintext
  Use for: end-to-end encrypted messaging (Signal), zero-knowledge architectures
```

### Key Management

```
NEVER store encryption keys in:
  - Application code / config files
  - Environment variables (leak via /proc/environ, logs)
  - Database alongside encrypted data

DO:
  - Use managed KMS (AWS KMS, GCP Cloud KMS, HashiCorp Vault)
  - Envelope encryption: data key encrypts data; master key encrypts data key
    Master key never leaves KMS; only the encrypted data key is stored with data
  - Key rotation: rotate data keys annually (or on suspected compromise)
    Envelope encryption makes this cheap: re-encrypt only data keys, not data
```

### Secret Management

```
Application secrets (DB passwords, API keys, OAuth client secrets):
  Store in: HashiCorp Vault / AWS Secrets Manager / GCP Secret Manager
  Inject as: ephemeral env var at startup OR fetched via SDK at runtime
  Rotate: automated rotation via Vault's dynamic secrets (DB passwords rotated every 1h)

What NOT to do:
  - git-committed secrets → scan with truffleHog / git-secrets in CI
  - Plaintext in Kubernetes ConfigMaps → use Kubernetes Secrets (base64 is not encryption!)
  - Hardcoded in Docker images → scan with Trivy
```

---

## Zero-Trust Network Model

### Principles

```
Traditional perimeter model: "inside the firewall = trusted"
  - Compromised VM inside the network can reach any other service
  - VPN users have broad internal access

Zero-trust model: "never trust, always verify"
  - Every request must be authenticated and authorized
  - Regardless of source IP, network segment, or VPN status
  - Least-privilege access: each service has access to only what it needs
```

### Implementation at System Design Level

```
1. Service identity (mTLS / SPIFFE): every service has a cryptographic identity
2. Per-call authorization: every service call is authorized by policy
   (OPA sidecar / Istio AuthorizationPolicy)
3. Network segmentation: services grouped by sensitivity
   - Public-facing tier: API gateway, web servers (can receive external traffic)
   - Application tier: business logic services (no direct external access)
   - Data tier: databases, caches (accessible only from application tier)
   Even with network segments, still require mTLS within segments
4. Egress control: restrict outbound connections from each service
   (e.g., payments service should ONLY be able to call Stripe, not arbitrary URLs)
```

### Istio AuthorizationPolicy (Zero-Trust in Practice)

```yaml
# payments service can ONLY receive calls from checkout service
apiVersion: security.istio.io/v1beta1
kind: AuthorizationPolicy
metadata:
  name: payments-ingress
  namespace: prod
spec:
  selector:
    matchLabels:
      app: payments
  action: ALLOW
  rules:
  - from:
    - source:
        principals:
        - "cluster.local/ns/prod/sa/checkout"  # only checkout service's SPIFFE identity
    to:
    - operation:
        methods: ["POST"]
        paths: ["/v1/charge", "/v1/refund"]
```

---

## Audit Logging

### What to Log (and What Not To)

```
Log:
  - Authentication events: login, logout, failed login, token refresh
  - Authorization decisions: access granted, access denied (with resource + caller)
  - Data mutations: who created/updated/deleted which record and when
  - Admin actions: role assignments, config changes, key rotations
  - API calls: request metadata (caller identity, method, resource, timestamp)
    NOT request body (may contain PII) unless explicitly required

Don't log:
  - Passwords, secrets, tokens (even partial, even hashed — use reference IDs)
  - Full PII in plaintext (log user_id, not email + SSN)
  - Request bodies unless auditing a specific high-risk endpoint
```

### Audit Log Architecture

```
Requirements for audit logs:
  - Immutable: nobody (including admins) should be able to delete or modify logs
  - Tamper-evident: detect if logs are modified
  - Durable: logs survive storage failures
  - Queryable: compliance team needs to answer "who accessed this record in the last 30 days"

Implementation:
  Application → writes structured log events (JSON) → Kafka topic "audit.events"
  Kafka consumer → writes to S3 (immutable, versioned bucket + S3 Object Lock)
                 → writes to Elasticsearch (queryable for dashboards)

S3 Object Lock:
  COMPLIANCE mode: even the root account cannot delete logs during retention period
  Retention period: regulatory minimum (e.g., 7 years for financial, 6 years HIPAA)

Tamper evidence:
  Option 1: hash chaining (each log entry includes hash of previous entry)
  Option 2: write to append-only ledger (AWS QLDB, transparent logs)
```

### Log Format (Structured)

```json
{
  "event_type": "DATA_ACCESS",
  "timestamp": "2026-06-19T10:30:00Z",
  "actor": {
    "type": "USER",
    "id": "user_abc123",
    "ip": "203.0.113.1",
    "session_id": "sess_xyz"
  },
  "resource": {
    "type": "ORDER",
    "id": "order_987654",
    "action": "READ"
  },
  "result": "ALLOWED",
  "request_id": "req_uuid_here",
  "service": "orders-service",
  "environment": "prod"
}
```

---

## Compliance Frameworks at Architecture Level

### Data Residency

```
Requirement: data for EU users must not leave the EU
             (GDPR Article 44; local laws in Russia, China, India)

Architecture approach:
  1. Region-aware routing: route EU users to EU deployment; US users to US deployment
     (GeoDNS at load balancer level)
  2. Separate data stores per region: EU Postgres cluster, US Postgres cluster
     No cross-region replication for user PII
  3. Control plane (auth, config) may need to be replicated per region or hosted separately
  4. Backups: S3 buckets in the same region; don't replicate to cross-region backup by default

Data tagging: tag PII fields in schema with data_residency_zone
  enables automated policy enforcement and discovery
```

### PII Handling

```
PII categories (GDPR): name, email, phone, IP address, location, cookies/device IDs,
                        health data, financial data, biometrics

Minimize:
  Don't collect what you don't need. If you don't store the field, you can't lose it.

Pseudonymization:
  Replace direct identifiers with a pseudonym (user_id instead of email in analytics events)
  Map stored in a separate, access-controlled system

Encryption at field level:
  Encrypt SSN, payment info, health data before storing
  Application decrypts on access with audit log entry

Data retention limits:
  Define max retention per data type (e.g., user sessions: 90 days; order records: 7 years)
  Automated deletion job runs nightly: DELETE WHERE created_at < NOW() - retention_period
```

### Right to Erasure (GDPR Article 17)

```
"Delete my account" = user invokes right to erasure

Challenges:
  - Data spread across 15 microservices, 20 tables, 3 data warehouses, audit logs, backups
  - Audit logs may be legally required to retain

Implementation:
  1. User Deletion Service: coordinates deletion across all services
     Publishes "user.deletion.requested" event to Kafka
     Each service has a consumer that handles its own data

  2. Soft delete first: mark user as deleted_at, remove from user-facing APIs immediately
     Background job hard-deletes after 30-day "recovery" window

  3. Anonymization instead of deletion for records with legal retention requirement:
     Replace user_id with "DELETED_USER", clear PII fields
     Order records (7-year legal hold) → keep financial data, anonymize personal data

  4. Data warehouse:
     Can't delete individual rows from Parquet files efficiently
     Approach: re-export table partitions with the deleted user filtered out
     Or: maintain a "deletion list" and filter it at query time (slower but simpler)

  5. Backups:
     Don't restore backups without re-applying deletion list
     Automated backup verification must check that deleted users are absent
```

---

## OWASP Top-10 at Architecture Level

| OWASP Category | Architecture-Level Mitigation |
|---|---|
| A01 Broken Access Control | RBAC enforced at API gateway; per-resource ownership check at service; deny by default |
| A02 Cryptographic Failures | TLS everywhere; KMS for keys; no plaintext PII in logs or S3 |
| A03 Injection | Parameterized queries (code-level); WAF for SQL/NoSQL injection patterns |
| A04 Insecure Design | Threat modeling during architecture review; principle of least privilege |
| A05 Security Misconfiguration | IaC for all infra (no manual console changes); CIS benchmark scanning (Checkov) |
| A06 Vulnerable Components | Dependency scanning in CI (Snyk/Dependabot); container scanning (Trivy) |
| A07 AuthN/AuthZ Failures | MFA for admin; short-lived tokens; session revocation; no shared credentials |
| A08 Data Integrity Failures | Signed artifacts in CI/CD pipeline; SRI hashes for CDN assets |
| A09 Logging Failures | Centralized immutable audit log; alerting on auth failures |
| A10 SSRF | Egress allowlist per service; block metadata endpoint (169.254.169.254) |

---

## Interview Answer Framework

When asked "How would you secure system X?":

```
1. Perimeter: What can reach the system from outside?
   → API gateway terminates external TLS; WAF for injection/DDoS
   → Public endpoints explicitly listed; everything else private

2. Identity: Who is calling each service?
   → Users: OAuth 2.0 + JWT (short TTL, validate aud/iss/exp)
   → Services: mTLS with SPIFFE identity
   → Least privilege: each service has only the permissions it needs

3. Data: What sensitive data exists?
   → PII / secrets: encrypted at rest with KMS; field-level encryption for high sensitivity
   → In transit: TLS 1.3; mTLS internal
   → Secrets: Vault dynamic secrets; never in env vars or code

4. Audit: How do you know when something goes wrong?
   → Immutable audit log: who accessed what, when
   → Alerting: anomaly detection on auth failures, unusual access patterns
   → Incident response: log retention for forensics (minimum 1 year queryable, 7 years archived)

5. Compliance: Any regulatory constraints?
   → Data residency: GeoDNS + region-isolated storage
   → Right to erasure: User Deletion Service coordinates across all services
   → Retention limits: automated deletion jobs per data type
```

---

## Quick Revision

- **OAuth 2.0**: Authorization Code + PKCE for user-facing; Client Credentials for service-to-service
- **JWT**: validate `exp`, `aud`, `iss`; short TTL (15 min); revoke via `jti` blocklist in Redis
- **mTLS**: both sides present certs; service identity is cryptographic; required for zero-trust
- **Zero-trust**: never trust by network location; always verify; least-privilege egress policies
- **Audit logs**: immutable (S3 Object Lock COMPLIANCE), structured JSON, no PII in plaintext
- **Right to erasure**: User Deletion Service + Kafka fan-out; anonymize where deletion is impossible (legal holds)
- **Key management**: envelope encryption; keys in KMS, not env vars; rotate via re-encrypting data keys only

---

## See Also

- `07-interview-templates/api-design-template.md` — API key management, OAuth flows for APIs
- `04-advanced-topics/microservices.md` — Istio mTLS, PeerAuthentication, AuthorizationPolicy
- `09-patterns/outbox.md` — Outbox for User Deletion Service event fan-out
- `02-building-blocks/rate-limiting.md` — rate limiting for auth endpoint brute force protection
