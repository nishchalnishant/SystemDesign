# Proxy, Reverse Proxy & Load Balancing

> **Source**: Videos #19, #40, #58, #87 from the playlist
> - Proxy vs Reverse Proxy (Real-world Examples)
> - Top 6 Load Balancing Algorithms Every Developer Should Know
> - Reverse Proxy vs API Gateway vs Load Balancer
> - What is a LOAD BALANCER really about?

---

## Forward Proxy vs Reverse Proxy

### Forward Proxy
```
Client → Proxy → Internet → Server
```
- Sits in front of **clients**
- Client knows it's using a proxy
- **Use cases**: Anonymity, content filtering, caching, bypassing geo-restrictions
- **Examples**: VPN, corporate proxy, Squid

### Reverse Proxy
```
Client → Internet → Reverse Proxy → Server(s)
```
- Sits in front of **servers**
- Client doesn't know about the proxy
- **Use cases**: Load balancing, SSL termination, caching, security
- **Examples**: NGINX, HAProxy, Envoy, Traefik

---

## Load Balancer

### What Does a Load Balancer Do?
Distributes incoming traffic across multiple servers to:
- Prevent any single server from being overwhelmed
- Improve availability (route around failures)
- Enable horizontal scaling

### Load Balancing Algorithms

| Algorithm | Description | Best For |
|---|---|---|
| **Round Robin** | Rotate through servers sequentially | Equal server capacity |
| **Weighted Round Robin** | More traffic to more powerful servers | Mixed server capacity |
| **Least Connections** | Send to server with fewest active connections | Varying request durations |
| **Weighted Least Connections** | Least connections + server capacity | Mixed capacity + varying durations |
| **IP Hash** | Hash client IP → consistent server | Session affinity |
| **Least Response Time** | Send to fastest responding server | Performance-sensitive apps |

### Layer 4 vs Layer 7 Load Balancing

| Feature | Layer 4 (Transport) | Layer 7 (Application) |
|---|---|---|
| **Operates on** | TCP/UDP packets | HTTP requests |
| **Routing based on** | IP + Port | URL, headers, cookies, body |
| **Speed** | Faster (less processing) | Slower (must read HTTP) |
| **Intelligence** | Basic | Content-based routing |
| **SSL Termination** | Pass-through or terminate | Terminate and inspect |
| **Use case** | High throughput, simple routing | Path-based routing, A/B testing |

---

## Reverse Proxy vs API Gateway vs Load Balancer

| Feature | Reverse Proxy | Load Balancer | API Gateway |
|---|---|---|---|
| **Primary job** | Forward requests | Distribute traffic | Manage APIs |
| **SSL termination** | ✅ | ✅ | ✅ |
| **Caching** | ✅ | ❌ | ✅ |
| **Rate limiting** | Basic | ❌ | ✅ |
| **Auth** | Basic | ❌ | ✅ |
| **Load balancing** | ✅ | ✅ (primary) | ✅ |
| **Request transformation** | Basic | ❌ | ✅ |
| **API versioning** | ❌ | ❌ | ✅ |
| **Monitoring/Analytics** | Basic | Health checks | ✅ Full |

### In Practice
- **NGINX** can act as all three (reverse proxy + LB + basic gateway)
- **API Gateway** adds business logic (auth, rate limiting, analytics)
- **Load Balancer** is specialized for traffic distribution

---

## Health Checks

Load balancers perform health checks to detect unhealthy servers:
- **Active**: LB periodically pings servers (HTTP GET /health)
- **Passive**: LB monitors response errors and latency
- Unhealthy servers are removed from rotation
- Re-added when health checks pass again
