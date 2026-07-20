# DNS (Domain Name System)

> **Source**: [Everything You Need to Know About DNS: Crash Course System Design #4](https://www.youtube.com/playlist?list=PLCRMIe5FDPsd0gVs500xeOewfySTsmEjf) — Video #2

---

## What is DNS?

DNS is the **phonebook of the internet** — translates human-readable domain names (google.com) into IP addresses (142.250.80.46).

---

## DNS Resolution Flow

```
User types google.com
       ↓
1. Browser Cache (check local cache)
       ↓ (miss)
2. OS Cache / hosts file
       ↓ (miss)
3. Recursive DNS Resolver (ISP's resolver)
       ↓ (miss)
4. Root Name Server (.) → "Try .com TLD server"
       ↓
5. TLD Name Server (.com) → "Try Google's authoritative server"
       ↓
6. Authoritative Name Server (google.com) → "IP is 142.250.80.46"
       ↓
7. Response cached at each level, returned to browser
```

---

## DNS Record Types

| Record | Purpose | Example |
|---|---|---|
| **A** | Maps domain to IPv4 address | `google.com → 142.250.80.46` |
| **AAAA** | Maps domain to IPv6 address | `google.com → 2607:f8b0::` |
| **CNAME** | Alias to another domain | `www.google.com → google.com` |
| **MX** | Mail server for domain | `google.com → smtp.google.com` |
| **NS** | Authoritative name server | `google.com → ns1.google.com` |
| **TXT** | Arbitrary text (verification) | SPF, DKIM, domain verification |
| **SRV** | Service location | Service discovery in microservices |

---

## TTL (Time to Live)

- Duration a DNS record is cached
- Lower TTL = faster propagation, more DNS queries
- Higher TTL = fewer queries, slower updates
- Typical: 300s (5 min) to 86400s (24 hours)

---

## DNS in System Design

### DNS-Based Load Balancing
- Return multiple A records (round-robin DNS)
- **Pros**: Simple, no extra infrastructure
- **Cons**: No health checking, TTL caching causes uneven distribution

### GeoDNS
- Return different IPs based on client's location
- Used by CDNs (Cloudflare, AWS Route 53)
- Route users to nearest data center

### DNS Failover
- Health-check monitored DNS records
- Automatically remove unhealthy servers
- AWS Route 53 health checks

---

## Key Takeaways
- DNS adds latency (cold lookup ~100-200ms), caching mitigates this
- DNS is hierarchical and distributed — no single point of failure
- Use DNS for **geo-routing** and **service discovery** in distributed systems
