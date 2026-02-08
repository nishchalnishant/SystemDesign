# Networking - System Design Guide

> **For SDE-3 Interview Preparation**  
> Deep dive into protocols, communication patterns, and network optimizations.

## Table of Contents
1. [OSI Model vs TCP/IP](#osi-model-vs-tcpip)
2. [TCP vs UDP](#tcp-vs-udp)
3. [HTTP Evolution (1.1, 2, 3)](#http-evolution)
4. [Communication Protocols](#communication-protocols)
   - [REST](#rest)
   - [GraphQL](#graphql)
   - [gRPC](#grpc)
   - [WebSockets](#websockets)
5. [Load Balancing (L4 vs L7)](#load-balancing)
6. [API Gateway Pattern](#api-gateway-pattern)

---

## OSI Model vs TCP/IP

| OSI Layer | TCP/IP Layer | Protocols | Responsibility |
|-----------|--------------|-----------|----------------|
| 7. Application | Application | HTTP, DNS, SMTP | User-level data |
| 6. Presentation | Application | SSL/TLS, JPEG | Formatting/Encryption |
| 5. Session | Application | Sockets | Session management |
| 4. Transport | Transport | TCP, UDP | End-to-end delivery |
| 3. Network | Internet | IP, ICMP, BGP | Routing packets |
| 2. Data Link | Link | Ethernet, MAC | Node-to-node transfer |
| 1. Physical | Link | Fiber, WiFi | Bits over wire |

---

## TCP vs UDP

### TCP (Transmission Control Protocol)
- **Features**: Connection-oriented (3-way handshake), Reliable (ACKs), Ordered, Flow Control, Congestion Control.
- **Overhead**: High (Handshake, headers).
- **Use Cases**: Web (HTTP), Email (SMTP), File Transfer (FTP).

### UDP (User Datagram Protocol)
- **Features**: Connectionless, Unreliable (fire and forget), Unordered, Fast.
- **Overhead**: Low.
- **Use Cases**: Video Streaming/VoIP (WebRTC), Gaming, DNS, DHCP.

### QUIC (UDP + Reliability)
- Built on top of UDP to provide reliability and security (TLS 1.3 baked in).
- Basis for **HTTP/3**.
- Solves "Head-of-Line Blocking" present in TCP.

---

## HTTP Evolution

### HTTP/1.1
- **Plain Text**: Human readable.
- **One Request per Connection** (Keep-Alive added later).
- **Head-of-Line Blocking**: One slow request blocks queue.

### HTTP/2
- **Binary Protocols**: More efficient parsing.
- **Multiplexing**: Multiple requests over single TCP connection.
- **Header Compression (HPACK)**.
- **Server Push**.
- **Limitation**: Still suffers from TCP Head-of-Line blocking (packet loss blocks all streams).

### HTTP/3
- **Runs on QUIC (UDP)**.
- **True Multiplexing**: Packet loss in one stream doesn't block others.
- **Built-in TLS 1.3**.
- **Faster Handshake**: 0-RTT or 1-RTT.

---

## Communication Protocols

### 1. REST (Representational State Transfer)
- **Resource-based**: `GET /users/123`.
- **Stateless**: Server stores no session.
- **Pros**: Cacheable, Simple, Universal support.
- **Cons**: Over-fetching/Under-fetching data.

### 2. GraphQL
- **Query Language**: Client requests exactly what it needs.
- **Schema-Driven**: Strongly typed APIs.
- **Pros**: No over-fetching, Single round-trip.
- **Cons**: Complex caching (POST based), N+1 query problem.

### 3. gRPC (Google Remote Procedure Call)
- **Based on Protobuf**: Binary serialization (smaller/faster than JSON).
- **HTTP/2**: Supports streaming (Client/Server/Bi-directional).
- **Strongly Typed**: `.proto` files define contract.
- **Use Cases**: Microservices internal communication, Mobile-to-Server.

**REST vs gRPC:**
| Feature | REST | gRPC |
|---------|------|------|
| Format | JSON (Text) | Protobuf (Binary) |
| Protocol | HTTP/1.1 | HTTP/2 |
| Interface | Resource | Method (RPC) |
| Latency | Medium | Low |
| Browser Support | Excellent | Requires Proxy (gRPC-Web) |

### 4. WebSockets
- **Full Duplex**: Persistent bi-directional connection.
- **Protocol**: Starts as HTTP Upgrade connection -> switches to WS.
- **Use Cases**: Chat apps, Live Dashboards, Multiplayer games.

---

## Load Balancing

### L4 Load Balancer (Transport Layer)
- Routes based on **IP + Port**.
- Simple packet forwarding (NAT).
- **Fast**, low CPU.
- **No data inspection** (can't route based on URL).
- **Examples**: LVS, HAProxy (TCP mode).

### L7 Load Balancer (Application Layer)
- Routes based on **Content** (URL, Headers, Cookies).
- Terminates SSL/TLS.
- **Smarter**: Can do A/B testing routing, Auth checking.
- **Slower**: Needs to decrypt/inspect.
- **Examples**: Nginx, AWS ALB, HAProxy (HTTP mode).

**Algorithm Choices:**
- Round Robin (Simple)
- Least Connections (Adaptive)
- Consistent Hashing (Sticky cache affinity)

---

## API Gateway Pattern

Central entry point for all client requests.

**Functions:**
1.  **Routing**: Route `/api/user` to User Service.
2.  **Auth**: Verify JWT tokens.
3.  **Rate Limiting**: Protect backend.
4.  **Protocol Translation**: REST (Public) -> gRPC (Internal).
5.  **Caching**: Response caching.
6.  **Circuit Breaking**: Fail fast if service is down.

**Technologies**: Kong, Amazon API Gateway, Nginx, Zuul.
