> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design an HTTP Tunneling Service (like ngrok) — tests understanding of networking, proxy servers, and establishing long-lived connections for reverse tunneling.
>
> **Key concepts:**
> - Core Entities: `TunnelServer`, `TunnelClient` (runs on localhost), `PublicEndpoint`, `ConnectionManager`.
> - The Tunnel: The `TunnelClient` opens a long-lived TCP connection (or WebSocket) *outbound* to the `TunnelServer`. This bypasses the local NAT/Firewall.
> - Request Routing: When a public user hits `https://xyz.ngrok.io`, the `TunnelServer` looks up the active tunnel for `xyz`, forwards the HTTP request down the established TCP connection to the `TunnelClient`.
> - Replaying/Monitoring (Observer): A nice-to-have feature is a local dashboard that observes all requests passing through the `TunnelClient` to display them to the developer.
>
> **Key takeaway:** The core trick to NAT traversal is that the connection must be initiated from the *inside* (localhost) to the *outside* (public server). The public server then multiplexes incoming web traffic down that established connection.

---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, tunneling, ngrok, proxy, observer, strategy]
---
# Design HTTP Tunneling Service

> **Difficulty**: Hard
> **Asked at**: Amazon, Cloudflare
> **Key Patterns**: Proxy, Observer, Strategy (protocol handling)

---

## Understanding the Problem

Design an ngrok-like HTTP tunneling service that exposes a local port to the internet via a unique public URL. The tunnel server receives incoming public HTTP requests and forwards them to the local client through a persistent connection, then relays the response back to the original caller.

---

## Clarifying Questions

**You**: "Is this HTTP-only tunneling, or do I need to support TCP and WebSocket tunneling as well?"
**Interviewer**: "Start with HTTP. Mention TCP and WebSocket tunneling as extensions."

**You**: "How are public URLs assigned — random subdomains or user-specified?"
**Interviewer**: "Random by default. Mention user-specified as an extension."

**You**: "Does the tunnel client run on the user's machine, and does it maintain a persistent connection to the server?"
**Interviewer**: "Yes, the client initiates and holds a long-lived connection to the tunnel server."

**You**: "Do I need authentication — API keys or tunnel-level auth?"
**Interviewer**: "Mention it as an extension. Focus on core forwarding mechanics."

**You**: "Should I handle the case where the local server is down or slow to respond?"
**Interviewer**: "Yes — timeouts and error responses when the local server is unreachable."

**You**: "Do I need rate limiting per tunnel?"
**Interviewer**: "Mention it as a deep dive."

**You**: "Should I handle HTTPS (TLS termination) at the tunnel server?"
**Interviewer**: "Note it as an extension — the tunnel server holds a wildcard cert."

---

## Final Requirements

**In scope:**
1. `create_tunnel(local_port)` — register a tunnel, return a unique public URL
2. `forward_request(tunnel_id, request)` — relay an incoming HTTP request to the local client
3. `close_tunnel(tunnel_id)` — deregister and clean up
4. `handle_incoming(request)` — entry point for public HTTP requests; route to correct tunnel
5. Persistent connection between tunnel server and tunnel client
6. Timeout handling when local server is unresponsive
7. Request/response correlation (match response to original caller)

**Out of scope:**
- TLS termination / HTTPS
- TCP-level tunneling
- Persistent subdomain assignment across sessions
- Billing and rate limiting (mention only)

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|---------------|
| TunnelServer | Public-facing server; routes incoming requests to tunnels |
| Tunnel | Represents one active tunnel session: subdomain, local_port, connection |
| TunnelRegistry | Maps subdomain -> Tunnel; manages lifecycle |
| TunnelClient | Runs locally; holds persistent connection, forwards to local port |
| RequestForwarder | Sends HTTP request over the tunnel connection, waits for response |
| Session | Per-request correlation: request_id -> waiting caller |
| SubdomainGenerator | Produces unique, collision-free subdomain strings |

---

## Class Design

### Tunnel

| Requirement | What Tunnel must track |
|-------------|----------------------|
| Identity | `tunnel_id: str, subdomain: str` |
| Connection | `connection: Connection` — the persistent socket/channel to the client |
| Local port | `local_port: int` — the port on the client machine |
| State | `status: TunnelStatus` |
| Pending requests | `pending: dict[str, Future]` — request_id -> caller Future |

```
class Tunnel:
- tunnel_id: str
- subdomain: str
- local_port: int
- connection: Connection
- status: TunnelStatus
- pending: dict[str, Future]
+ send_request(request) -> Response
+ receive_response(request_id, response) -> None
+ close() -> None
```

### TunnelServer

```
class TunnelServer:
- registry: TunnelRegistry
- forwarder: RequestForwarder
+ register_tunnel(local_port, connection) -> str    # returns public URL
+ handle_incoming(host, request) -> Response
+ close_tunnel(tunnel_id) -> None
```

### TunnelRegistry

```
class TunnelRegistry:
- tunnels: dict[str, Tunnel]          # tunnel_id -> Tunnel
- subdomain_index: dict[str, str]     # subdomain -> tunnel_id
+ add(tunnel) -> None
+ remove(tunnel_id) -> None
+ find_by_subdomain(subdomain) -> Tunnel | None
+ find_by_id(tunnel_id) -> Tunnel | None
```

### RequestForwarder

```
class RequestForwarder:
- timeout: float
+ forward(tunnel, request) -> Response
```

---

## Implementation

### Core Method: handle_incoming() and forward_request()

**Core logic for handle_incoming:**
1. Extract the subdomain from the `Host` header (e.g., `abc123.tunnel.example.com` -> `abc123`)
2. Look up the Tunnel in the registry by subdomain
3. If no tunnel found, return 404
4. Delegate to `RequestForwarder.forward(tunnel, request)`

**Core logic for forward_request:**
1. Generate a unique `request_id`
2. Create a Future and store it in `tunnel.pending[request_id]`
3. Serialize the HTTP request and send it over the persistent connection
4. Await the Future with a timeout
5. On timeout, return 504; on response, return it

**Edge cases:**
- Tunnel connection dropped mid-request — catch ConnectionError, return 502
- Multiple concurrent requests on the same tunnel — each gets its own request_id/Future
- Response arrives for a request_id that already timed out — discard it

```java
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public enum TunnelStatus {
    ACTIVE, CLOSED
}

public final class HttpRequest {
    private final String method;
    private final String path;
    private final Map<String, String> headers;
    private final String body;
    private final String host;

    public HttpRequest(String method, String path, Map<String, String> headers, String body, String host) {
        this.method = method;
        this.path = path;
        this.headers = headers;
        this.body = body;
        this.host = host != null ? host : "";
    }

    public String getMethod() { return method; }
    public String getPath() { return path; }
    public Map<String, String> getHeaders() { return headers; }
    public String getBody() { return body; }
    public String getHost() { return host; }
}

public final class HttpResponse {
    private final int statusCode;
    private final Map<String, String> headers;
    private final String body;

    public HttpResponse(int statusCode, Map<String, String> headers, String body) {
        this.statusCode = statusCode;
        this.headers = headers;
        this.body = body;
    }

    public int getStatusCode() { return statusCode; }
    public Map<String, String> getHeaders() { return headers; }
    public String getBody() { return body; }
}

/**
 * Represents the persistent connection from tunnel server to tunnel client.
 * In a real implementation this wraps a WebSocket or raw TCP socket.
 */
public class Connection {
    private final String connId;
    private volatile boolean alive = true;
    // In real code: a Socket or Netty Channel

    public Connection(String connId) {
        this.connId = connId;
    }

    /** Send a serialized request to the tunnel client. */
    public void send(Map<String, Object> data) {
        if (!alive) {
            throw new IllegalStateException("Connection is closed");
        }
        // Real: socket.getOutputStream().write(toJson(data).getBytes());
        System.out.println("[Connection " + connId + "] Sending: " + data);
    }

    public boolean isAlive() { return alive; }

    public void close() { alive = false; }
}

public class Tunnel {
    private final String tunnelId;
    private final String subdomain;
    private final int localPort;
    private final Connection connection;
    private volatile TunnelStatus status;
    private final Map<String, PendingRequest> pending = new HashMap<>(); // request_id -> pending entry
    private final ReentrantLock lock = new ReentrantLock();

    private static final class PendingRequest {
        final Object monitor = new Object();
        volatile boolean completed = false;
        volatile HttpResponse response;
    }

    public Tunnel(String tunnelId, String subdomain, int localPort, Connection connection) {
        this.tunnelId = tunnelId;
        this.subdomain = subdomain;
        this.localPort = localPort;
        this.connection = connection;
        this.status = TunnelStatus.ACTIVE;
    }

    /** Forward request over persistent connection; block until response. */
    public HttpResponse sendRequest(HttpRequest request, double timeoutSeconds) {
        String requestId = UUID.randomUUID().toString();
        PendingRequest pendingRequest = new PendingRequest();

        lock.lock();
        try {
            pending.put(requestId, pendingRequest);
        } finally {
            lock.unlock();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("request_id", requestId);
        payload.put("method", request.getMethod());
        payload.put("path", request.getPath());
        payload.put("headers", request.getHeaders());
        payload.put("body", request.getBody());
        payload.put("local_port", localPort);

        try {
            connection.send(payload);
        } catch (IllegalStateException e) {
            lock.lock();
            try {
                pending.remove(requestId);
            } finally {
                lock.unlock();
            }
            return new HttpResponse(502, Collections.emptyMap(), "Bad Gateway: tunnel connection lost");
        }

        boolean completed;
        synchronized (pendingRequest.monitor) {
            long deadline = System.currentTimeMillis() + (long) (timeoutSeconds * 1000);
            while (!pendingRequest.completed) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) break;
                try {
                    pendingRequest.monitor.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            completed = pendingRequest.completed;
        }

        lock.lock();
        try {
            pending.remove(requestId);
        } finally {
            lock.unlock();
        }

        if (!completed) {
            return new HttpResponse(504, Collections.emptyMap(), "Gateway Timeout: local server did not respond");
        }

        return pendingRequest.response;
    }

    /** Called when client sends back a response for a pending request. */
    public void receiveResponse(String requestId, HttpResponse response) {
        PendingRequest entry;
        lock.lock();
        try {
            entry = pending.get(requestId);
        } finally {
            lock.unlock();
        }
        if (entry == null) {
            return; // request already timed out; discard
        }
        synchronized (entry.monitor) {
            entry.response = response;
            entry.completed = true;
            entry.monitor.notifyAll();
        }
    }

    public void close() {
        status = TunnelStatus.CLOSED;
        connection.close();
        // Fail all pending requests
        lock.lock();
        try {
            for (PendingRequest entry : pending.values()) {
                synchronized (entry.monitor) {
                    entry.response = new HttpResponse(502, Collections.emptyMap(), "Tunnel closed");
                    entry.completed = true;
                    entry.monitor.notifyAll();
                }
            }
            pending.clear();
        } finally {
            lock.unlock();
        }
    }

    public String getTunnelId() { return tunnelId; }
    public String getSubdomain() { return subdomain; }
    public int getLocalPort() { return localPort; }
    public Connection getConnection() { return connection; }
    public TunnelStatus getStatus() { return status; }
}

public class SubdomainGenerator {
    public static final String BASE_DOMAIN = "tunnel.example.com";

    public String generate() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8); // e.g., "a3f9c012"
    }

    public String publicUrl(String subdomain) {
        return String.format("https://%s.%s", subdomain, BASE_DOMAIN);
    }
}

public class TunnelRegistry {
    private final Map<String, Tunnel> byId = new HashMap<>();        // tunnel_id -> Tunnel
    private final Map<String, String> bySubdomain = new HashMap<>(); // subdomain -> tunnel_id
    private final ReentrantLock lock = new ReentrantLock();

    public void add(Tunnel tunnel) {
        lock.lock();
        try {
            byId.put(tunnel.getTunnelId(), tunnel);
            bySubdomain.put(tunnel.getSubdomain(), tunnel.getTunnelId());
        } finally {
            lock.unlock();
        }
    }

    public void remove(String tunnelId) {
        lock.lock();
        try {
            Tunnel tunnel = byId.remove(tunnelId);
            if (tunnel != null) {
                bySubdomain.remove(tunnel.getSubdomain());
            }
        } finally {
            lock.unlock();
        }
    }

    public Tunnel findBySubdomain(String subdomain) {
        lock.lock();
        try {
            String tid = bySubdomain.get(subdomain);
            return tid != null ? byId.get(tid) : null;
        } finally {
            lock.unlock();
        }
    }

    public Tunnel findById(String tunnelId) {
        lock.lock();
        try {
            return byId.get(tunnelId);
        } finally {
            lock.unlock();
        }
    }
}

public class TunnelServer {
    private final TunnelRegistry registry = new TunnelRegistry();
    private final SubdomainGenerator subdomainGen = new SubdomainGenerator();
    private final double requestTimeout;

    public TunnelServer(double requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    /** Called when a tunnel client connects. Returns the public URL. */
    public String registerTunnel(int localPort, Connection connection) {
        String subdomain = subdomainGen.generate();
        String tunnelId = UUID.randomUUID().toString();
        Tunnel tunnel = new Tunnel(tunnelId, subdomain, localPort, connection);
        registry.add(tunnel);
        String publicUrl = subdomainGen.publicUrl(subdomain);
        System.out.println("Tunnel created: " + publicUrl + " -> localhost:" + localPort);
        return publicUrl;
    }

    /** Entry point for all public HTTP traffic. */
    public HttpResponse handleIncoming(HttpRequest request) {
        String subdomain = extractSubdomain(request.getHost());
        if (subdomain == null) {
            return new HttpResponse(400, Collections.emptyMap(), "Bad Request: missing or invalid Host header");
        }

        Tunnel tunnel = registry.findBySubdomain(subdomain);
        if (tunnel == null) {
            return new HttpResponse(404, Collections.emptyMap(), "Tunnel not found");
        }

        if (tunnel.getStatus() != TunnelStatus.ACTIVE) {
            return new HttpResponse(503, Collections.emptyMap(), "Tunnel is not active");
        }

        return tunnel.sendRequest(request, requestTimeout);
    }

    public void closeTunnel(String tunnelId) {
        Tunnel tunnel = registry.findById(tunnelId);
        if (tunnel != null) {
            tunnel.close();
            registry.remove(tunnelId);
        }
    }

    private String extractSubdomain(String host) {
        // host = "abc123.tunnel.example.com"
        // Returns "abc123"
        String base = SubdomainGenerator.BASE_DOMAIN;
        if (host != null && host.endsWith("." + base)) {
            return host.substring(0, host.length() - (base.length() + 1));
        }
        return null;
    }
}
```

---

## Verification

**Scenario: Client creates a tunnel, external request arrives, local server responds**

1. Client connects to `TunnelServer` over persistent WebSocket, calls `register_tunnel(local_port=8080, connection)`
2. Server generates subdomain `"a3f9c012"`, creates Tunnel, stores in registry
3. Returns `"https://a3f9c012.tunnel.example.com"`
4. External user sends `GET https://a3f9c012.tunnel.example.com/api/health`
5. `handle_incoming(request)` -> `_extract_subdomain("a3f9c012.tunnel.example.com")` -> `"a3f9c012"`
6. `registry.find_by_subdomain("a3f9c012")` -> Tunnel found
7. `tunnel.send_request(request, timeout=30)` -> generates request_id `"req-001"`, creates Event, sends payload to client
8. Client receives payload, forwards to `localhost:8080/api/health`
9. Local server returns 200; client sends response back to server with `request_id="req-001"`
10. `tunnel.receive_response("req-001", response)` -> sets the Event, unblocks step 7
11. `handle_incoming` returns the 200 response to the external caller

---

## Deep Dive & Extensibility

### 1. "What is the difference between HTTP tunneling and TCP tunneling?"

HTTP tunneling operates at the application layer: the tunnel server understands HTTP requests and responses, can inspect headers, modify them, and log at the request level.

TCP tunneling operates at the transport layer: raw bytes are forwarded without interpretation. This supports any protocol (databases, SSH, custom binary protocols) but the server cannot inspect or modify traffic.

```java
/** Forwards raw bytes — no HTTP awareness. */
public class TCPTunnel {
    private final Connection connection;

    public TCPTunnel(Connection connection) {
        this.connection = connection;
    }

    public byte[] forwardBytes(byte[] data) {
        // Send raw bytes to client, receive raw bytes back
        String requestId = UUID.randomUUID().toString();
        connection.sendRaw(data, requestId);
        return waitForRawResponse(requestId);
    }

    private byte[] waitForRawResponse(String requestId) {
        // Blocks until the raw response for requestId arrives
        throw new UnsupportedOperationException("not implemented");
    }
}
```

For TCP tunneling, the public server listens on a port (not a subdomain) and the tunnel identifies by port number rather than hostname.

### 2. "How does subdomain routing work?"

The public tunnel server needs a wildcard DNS record: `*.tunnel.example.com -> server_ip`. The server holds a wildcard TLS certificate for `*.tunnel.example.com`. When a request arrives, the `Host` header contains the full hostname. The server parses the subdomain prefix and looks it up in the TunnelRegistry.

```java
private String extractSubdomain(String host) {
    // Strip port if present: "abc123.tunnel.example.com:443"
    host = host.split(":")[0];
    String base = "tunnel.example.com";
    if (host.endsWith("." + base) && !host.equals(base)) {
        return host.substring(0, host.length() - (base.length() + 1));
    }
    return null;
}
```

For collision avoidance, the SubdomainGenerator should check the registry before returning a new subdomain and retry if already taken.

### 3. "How does request/response buffering work for slow clients?"

When the local server is slow, the tunnel server holds the response in memory (buffered in the Future). For very large responses, streaming is better: instead of buffering the full response, send chunks back to the caller as they arrive.

```java
public class StreamingTunnel extends Tunnel {
    private final Map<String, BlockingQueue<byte[]>> pendingStreams = new ConcurrentHashMap<>();
    private static final byte[] SENTINEL = new byte[0];

    public StreamingTunnel(String tunnelId, String subdomain, int localPort, Connection connection) {
        super(tunnelId, subdomain, localPort, connection);
    }

    /** Returns an iterator of response chunks. */
    public Iterator<byte[]> sendRequestStreaming(HttpRequest request) {
        String requestId = UUID.randomUUID().toString();
        BlockingQueue<byte[]> chunkQueue = new LinkedBlockingQueue<>();
        pendingStreams.put(requestId, chunkQueue);

        Map<String, Object> payload = new LinkedHashMap<>(serialize(request));
        payload.put("request_id", requestId);
        payload.put("streaming", true);
        getConnection().send(payload);

        return new Iterator<byte[]>() {
            private byte[] next = advance();

            private byte[] advance() {
                try {
                    byte[] chunk = chunkQueue.poll(30, TimeUnit.SECONDS);
                    return (chunk == null || chunk == SENTINEL) ? null : chunk;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }

            @Override
            public boolean hasNext() { return next != null; }

            @Override
            public byte[] next() {
                byte[] current = next;
                next = advance();
                return current;
            }
        };
    }

    public void receiveChunk(String requestId, byte[] chunk, boolean done) {
        BlockingQueue<byte[]> q = pendingStreams.get(requestId);
        if (q != null) {
            q.add(chunk);
            if (done) {
                q.add(SENTINEL);
            }
        }
    }

    private Map<String, Object> serialize(HttpRequest request) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("method", request.getMethod());
        map.put("path", request.getPath());
        map.put("headers", request.getHeaders());
        map.put("body", request.getBody());
        return map;
    }
}
```

### 4. "How do you implement rate limiting per tunnel?"

Each Tunnel maintains a token bucket or sliding window counter. Before forwarding a request, check if the rate limit is exceeded.

```java
public class RateLimitedTunnel extends Tunnel {
    private final int requestsPerSecond;
    private final List<Long> timestamps = new ArrayList<>(); // sliding window, millis
    private final ReentrantLock rateLimitLock = new ReentrantLock();

    public RateLimitedTunnel(String tunnelId, String subdomain, int localPort,
                              Connection connection, int requestsPerSecond) {
        super(tunnelId, subdomain, localPort, connection);
        this.requestsPerSecond = requestsPerSecond;
    }

    private boolean checkRateLimit() {
        long now = System.currentTimeMillis();
        rateLimitLock.lock();
        try {
            // Remove timestamps older than 1 second
            timestamps.removeIf(t -> now - t >= 1000);
            if (timestamps.size() >= requestsPerSecond) {
                return false;
            }
            timestamps.add(now);
            return true;
        } finally {
            rateLimitLock.unlock();
        }
    }

    @Override
    public HttpResponse sendRequest(HttpRequest request, double timeoutSeconds) {
        if (!checkRateLimit()) {
            return new HttpResponse(429, Map.of("Retry-After", "1"), "Too Many Requests");
        }
        return super.sendRequest(request, timeoutSeconds);
    }
}
```

### 5. "How do you support WebSocket tunneling?"

WebSocket connections are upgraded HTTP connections. The tunnel server needs to:
1. Detect the `Upgrade: websocket` header in the initial HTTP request
2. Complete the WebSocket handshake on behalf of the client
3. Forward all subsequent WebSocket frames bidirectionally through the persistent tunnel connection

```java
public HttpResponse handleIncoming(HttpRequest request) {
    String upgrade = request.getHeaders().getOrDefault("Upgrade", "");
    if (upgrade.equalsIgnoreCase("websocket")) {
        return handleWebSocketUpgrade(request);
    }
    return handleHttp(request);
}

private HttpResponse handleWebSocketUpgrade(HttpRequest request) {
    Tunnel tunnel = registry.findBySubdomain(extractSubdomain(request.getHost()));
    // Complete WS handshake: send 101 Switching Protocols to caller
    // Then enter bidirectional frame relay mode
    // Each WS frame from public side -> tunnel connection -> local server
    // Each WS frame from local server -> tunnel connection -> public side
    throw new UnsupportedOperationException("not implemented");
}
```

---

## Interviewer Questions by Level

**Junior**: How does the tunnel client tell the server where to forward a request?

**Mid-level**: How do multiple concurrent requests share the same persistent tunnel connection without mixing up their responses?

**Senior**: Compare HTTP tunneling vs TCP tunneling. When would you choose each, and what changes in the architecture?

---

## Common Interview Questions

- **Q: How does the tunnel maintain a persistent connection?**
  A: The tunnel client initiates a long-lived WebSocket or HTTP/2 connection to the tunnel server and keeps it open. The server writes requests into this connection; the client reads, forwards to localhost, and writes responses back.

- **Q: How does the server route an incoming request to the correct tunnel?**
  A: The `Host` header contains the subdomain (e.g., `abc123.tunnel.example.com`). The server strips the base domain, extracts `abc123`, and looks it up in the TunnelRegistry.

- **Q: What happens if the local server is down?**
  A: The tunnel client receives the forwarded request, attempts to connect to `localhost:{port}`, gets a connection refused error, and sends back a 502 or 503 response to the tunnel server, which relays it to the original caller.

- **Q: What is the latency impact of tunneling?**
  A: Every request adds at least 2 extra round trips: caller -> tunnel server -> client machine -> local server -> client -> tunnel server -> caller. Latency is dominated by the network RTT between the caller and the tunnel server, and between the tunnel server and the client machine.

- **Q: How do you correlate request and response when multiple requests use the same connection?**
  A: Each request is tagged with a unique `request_id`. The server stores a (request_id -> Future/Event) mapping. When the client sends back a response, it includes the `request_id`, allowing the server to unblock the correct waiting caller.

- **Q: What security risks does a tunneling service introduce?**
  A: The tunnel exposes a local service to the internet without the local machine having a public IP or open port. Risks: anyone with the URL can reach the local service; no auth by default. Mitigations: per-tunnel auth tokens, IP allowlisting, rate limiting, HTTPS-only with HSTS.

- **Q: How would you handle very large request or response bodies?**
  A: Use chunked transfer encoding. The tunnel forwards chunks as they arrive rather than buffering the entire body. This reduces memory usage and improves latency for large uploads/downloads.

- **Q: How would you implement custom (non-random) subdomains?**
  A: Allow users to specify a desired subdomain during tunnel creation. Check the registry for conflicts; if taken, return an error. Premium users get reserved subdomains persisted across sessions.

---

## Related

**Patterns applied here**

- [Proxy Pattern](../../03-design-patterns/02-structural/proxy-pattern.md)
- [Observer Pattern](../../03-design-patterns/03-behavioral/observer-pattern.md)
- [State Pattern](../../03-design-patterns/03-behavioral/state-pattern.md)

**SOLID focus**: [Dependency Inversion](../../02-solid-principles/05-dependency-inversion.md) · [Interface Segregation](../../02-solid-principles/04-interface-segregation.md)

**Concurrency**: [Concurrency Patterns](../../04-concurrency/concurrency-patterns.md) · [Futures & Async Patterns](../../04-concurrency/futures-async-patterns.md)

**Practice next**

- [Design Pub/Sub](../03-domain-specific/23-design-pub-sub.md)
- [Design Download Manager](32-design-download-manager.md)

Connection multiplexing and stream handling overlap.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../01-oop-fundamentals/uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
