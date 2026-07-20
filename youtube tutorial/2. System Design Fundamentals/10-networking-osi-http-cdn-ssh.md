# Networking: OSI Model, HTTP, CDN, SSH & Internet

> **Source**: Videos #14, #15, #24, #74, #75, #44, #81 from the playlist
> - What is OSI Model | Real World Examples
> - What Is A CDN? How Does It Work?
> - HTTP/1 to HTTP/2 to HTTP/3
> - How the Internet Works in 9 Minutes
> - HTTP 1 Vs HTTP 2 Vs HTTP 3!
> - HTTP Status Codes Explained In 5 Minutes
> - How SSH Really Works

---

## OSI Model (7 Layers)

| Layer | Name | Function | Protocols/Examples |
|---|---|---|---|
| 7 | **Application** | User-facing services | HTTP, FTP, SMTP, DNS |
| 6 | **Presentation** | Data formatting, encryption | SSL/TLS, JPEG, JSON |
| 5 | **Session** | Session management | NetBIOS, RPC |
| 4 | **Transport** | End-to-end delivery | TCP, UDP |
| 3 | **Network** | Routing, IP addressing | IP, ICMP, OSPF |
| 2 | **Data Link** | Frame delivery on local network | Ethernet, Wi-Fi, MAC |
| 1 | **Physical** | Raw bit transmission | Cables, radio signals |

### Simplified (TCP/IP Model)
```
Application  (HTTP, DNS, FTP)  ← OSI 5-7
Transport    (TCP, UDP)        ← OSI 4
Internet     (IP)              ← OSI 3
Link         (Ethernet, Wi-Fi) ← OSI 1-2
```

---

## HTTP Evolution

### HTTP/1.0
- One request per TCP connection
- No persistent connections

### HTTP/1.1
- **Persistent connections** (keep-alive)
- **Pipelining** (send multiple requests without waiting, but responses must be in order)
- **Head-of-line blocking**: Slow response blocks all subsequent responses

### HTTP/2
- **Multiplexing**: Multiple requests/responses on single connection simultaneously
- **Header compression** (HPACK)
- **Server push**: Server sends resources before client requests them
- **Binary protocol** (not text)
- Still uses TCP → **TCP head-of-line blocking** remains

### HTTP/3 (QUIC)
- Built on **UDP** (not TCP)
- **No head-of-line blocking** at transport layer
- **Faster connection setup** (0-RTT)
- **Built-in TLS 1.3** encryption
- **Connection migration** (change networks without reconnecting)

```
HTTP/1.1: 3-way handshake + TLS handshake = 3 round trips
HTTP/2:   Same as HTTP/1.1 (still TCP)
HTTP/3:   0-1 round trips (QUIC + TLS combined)
```

---

## CDN (Content Delivery Network)

### What is a CDN?
A distributed network of servers (edge/PoP) that caches content closer to users.

### How it Works
```
User → Nearest Edge Server (cache hit?) → Return cached content
                          (cache miss?) → Fetch from Origin → Cache → Return
```

### What CDNs Cache
- Static: Images, CSS, JS, fonts, videos
- Dynamic: API responses (with short TTL), personalized content (edge compute)

### Key CDN Features
- **Geographic distribution**: Reduce latency
- **DDoS protection**: Absorb traffic spikes
- **SSL/TLS termination**: Handle encryption at edge
- **Image optimization**: Resize, compress on the fly
- **Load balancing**: Distribute across origins

### Popular CDNs
- CloudFront (AWS), Cloud CDN (Google), Azure CDN
- Cloudflare, Akamai, Fastly

---

## How the Internet Works

```
1. You type google.com
2. DNS resolves to IP address
3. TCP connection (3-way handshake) with server
4. TLS handshake (encryption)
5. HTTP request sent
6. Server processes request
7. HTTP response returned
8. Browser renders page
```

### Key Infrastructure
- **ISP**: Internet Service Provider connects you to the internet
- **Submarine cables**: Carry 99% of international internet traffic
- **IXP** (Internet Exchange Point): Where ISPs interconnect
- **BGP** (Border Gateway Protocol): Routes between autonomous systems

---

## How SSH Works

1. **TCP connection** established (port 22)
2. **Key exchange** (Diffie-Hellman): Agree on shared secret
3. **Server authentication**: Client verifies server's host key
4. **User authentication**: Password or public key
5. **Encrypted session**: All data encrypted with shared key

### SSH Key-Based Auth
```
ssh-keygen → generates public + private key pair
Public key  → placed on server (~/.ssh/authorized_keys)
Private key → stays on client
Login: Client proves ownership of private key (challenge-response)
```

---

## HTTP Status Codes Summary

| Code | Meaning | When |
|---|---|---|
| 200 | OK | Successful GET |
| 201 | Created | Successful POST |
| 204 | No Content | Successful DELETE |
| 301 | Moved Permanently | URL changed permanently |
| 304 | Not Modified | Use cached version |
| 400 | Bad Request | Invalid request |
| 401 | Unauthorized | Not authenticated |
| 403 | Forbidden | No permission |
| 404 | Not Found | Resource missing |
| 429 | Too Many Requests | Rate limited |
| 500 | Internal Server Error | Server crashed |
| 502 | Bad Gateway | Upstream server error |
| 503 | Service Unavailable | Server overloaded |
