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

```python
import uuid
import time
import threading
from enum import Enum
from dataclasses import dataclass, field
from typing import Optional


class TunnelStatus(Enum):
    ACTIVE = "active"
    CLOSED = "closed"


@dataclass
class HttpRequest:
    method: str
    path: str
    headers: dict
    body: str
    host: str = ""


@dataclass
class HttpResponse:
    status_code: int
    headers: dict
    body: str


class Connection:
    """
    Represents the persistent connection from tunnel server to tunnel client.
    In a real implementation this wraps a WebSocket or raw TCP socket.
    """
    def __init__(self, conn_id: str):
        self.conn_id = conn_id
        self._alive = True
        # In real code: socket or asyncio StreamWriter

    def send(self, data: dict):
        """Send a serialized request to the tunnel client."""
        if not self._alive:
            raise ConnectionError("Connection is closed")
        # Real: self._socket.send(json.dumps(data).encode())
        print(f"[Connection {self.conn_id}] Sending: {data}")

    def is_alive(self) -> bool:
        return self._alive

    def close(self):
        self._alive = False


class Tunnel:
    def __init__(self, tunnel_id: str, subdomain: str, local_port: int, connection: Connection):
        self.tunnel_id = tunnel_id
        self.subdomain = subdomain
        self.local_port = local_port
        self.connection = connection
        self.status = TunnelStatus.ACTIVE
        self._pending: dict = {}   # request_id -> threading.Event + response holder
        self._lock = threading.Lock()

    def send_request(self, request: HttpRequest, timeout: float = 30.0) -> HttpResponse:
        """Forward request over persistent connection; block until response."""
        request_id = str(uuid.uuid4())
        event = threading.Event()
        response_holder = [None]

        with self._lock:
            self._pending[request_id] = (event, response_holder)

        payload = {
            "request_id": request_id,
            "method": request.method,
            "path": request.path,
            "headers": request.headers,
            "body": request.body,
            "local_port": self.local_port,
        }

        try:
            self.connection.send(payload)
        except ConnectionError:
            with self._lock:
                self._pending.pop(request_id, None)
            return HttpResponse(502, {}, "Bad Gateway: tunnel connection lost")

        completed = event.wait(timeout=timeout)

        with self._lock:
            self._pending.pop(request_id, None)

        if not completed:
            return HttpResponse(504, {}, "Gateway Timeout: local server did not respond")

        return response_holder[0]

    def receive_response(self, request_id: str, response: HttpResponse):
        """Called when client sends back a response for a pending request."""
        with self._lock:
            entry = self._pending.get(request_id)
        if entry is None:
            return  # request already timed out; discard
        event, response_holder = entry
        response_holder[0] = response
        event.set()

    def close(self):
        self.status = TunnelStatus.CLOSED
        self.connection.close()
        # Fail all pending requests
        with self._lock:
            for request_id, (event, response_holder) in self._pending.items():
                response_holder[0] = HttpResponse(502, {}, "Tunnel closed")
                event.set()
            self._pending.clear()


class SubdomainGenerator:
    BASE_DOMAIN = "tunnel.example.com"

    def generate(self) -> str:
        return uuid.uuid4().hex[:8]   # e.g., "a3f9c012"

    def public_url(self, subdomain: str) -> str:
        return f"https://{subdomain}.{self.BASE_DOMAIN}"


class TunnelRegistry:
    def __init__(self):
        self._by_id: dict = {}         # tunnel_id -> Tunnel
        self._by_subdomain: dict = {}  # subdomain -> tunnel_id
        self._lock = threading.Lock()

    def add(self, tunnel: Tunnel):
        with self._lock:
            self._by_id[tunnel.tunnel_id] = tunnel
            self._by_subdomain[tunnel.subdomain] = tunnel.tunnel_id

    def remove(self, tunnel_id: str):
        with self._lock:
            tunnel = self._by_id.pop(tunnel_id, None)
            if tunnel:
                self._by_subdomain.pop(tunnel.subdomain, None)

    def find_by_subdomain(self, subdomain: str) -> Optional[Tunnel]:
        with self._lock:
            tid = self._by_subdomain.get(subdomain)
            return self._by_id.get(tid) if tid else None

    def find_by_id(self, tunnel_id: str) -> Optional[Tunnel]:
        with self._lock:
            return self._by_id.get(tunnel_id)


class TunnelServer:
    def __init__(self, request_timeout: float = 30.0):
        self.registry = TunnelRegistry()
        self.subdomain_gen = SubdomainGenerator()
        self.request_timeout = request_timeout

    def register_tunnel(self, local_port: int, connection: Connection) -> str:
        """Called when a tunnel client connects. Returns the public URL."""
        subdomain = self.subdomain_gen.generate()
        tunnel_id = str(uuid.uuid4())
        tunnel = Tunnel(tunnel_id, subdomain, local_port, connection)
        self.registry.add(tunnel)
        public_url = self.subdomain_gen.public_url(subdomain)
        print(f"Tunnel created: {public_url} -> localhost:{local_port}")
        return public_url

    def handle_incoming(self, request: HttpRequest) -> HttpResponse:
        """Entry point for all public HTTP traffic."""
        subdomain = self._extract_subdomain(request.host)
        if not subdomain:
            return HttpResponse(400, {}, "Bad Request: missing or invalid Host header")

        tunnel = self.registry.find_by_subdomain(subdomain)
        if tunnel is None:
            return HttpResponse(404, {}, "Tunnel not found")

        if tunnel.status != TunnelStatus.ACTIVE:
            return HttpResponse(503, {}, "Tunnel is not active")

        return tunnel.send_request(request, timeout=self.request_timeout)

    def close_tunnel(self, tunnel_id: str):
        tunnel = self.registry.find_by_id(tunnel_id)
        if tunnel:
            tunnel.close()
            self.registry.remove(tunnel_id)

    def _extract_subdomain(self, host: str) -> Optional[str]:
        # host = "abc123.tunnel.example.com"
        # Returns "abc123"
        base = SubdomainGenerator.BASE_DOMAIN
        if host.endswith(f".{base}"):
            return host[: -(len(base) + 1)]
        return None
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

```python
class TCPTunnel:
    """Forwards raw bytes — no HTTP awareness."""
    def forward_bytes(self, data: bytes) -> bytes:
        # Send raw bytes to client, receive raw bytes back
        request_id = str(uuid.uuid4())
        self.connection.send_raw(data, request_id)
        return self._wait_for_raw_response(request_id)
```

For TCP tunneling, the public server listens on a port (not a subdomain) and the tunnel identifies by port number rather than hostname.

### 2. "How does subdomain routing work?"

The public tunnel server needs a wildcard DNS record: `*.tunnel.example.com -> server_ip`. The server holds a wildcard TLS certificate for `*.tunnel.example.com`. When a request arrives, the `Host` header contains the full hostname. The server parses the subdomain prefix and looks it up in the TunnelRegistry.

```python
def _extract_subdomain(self, host: str) -> Optional[str]:
    # Strip port if present: "abc123.tunnel.example.com:443"
    host = host.split(":")[0]
    base = "tunnel.example.com"
    if host.endswith(f".{base}") and host != base:
        return host[:-(len(base) + 1)]
    return None
```

For collision avoidance, the SubdomainGenerator should check the registry before returning a new subdomain and retry if already taken.

### 3. "How does request/response buffering work for slow clients?"

When the local server is slow, the tunnel server holds the response in memory (buffered in the Future). For very large responses, streaming is better: instead of buffering the full response, send chunks back to the caller as they arrive.

```python
class StreamingTunnel(Tunnel):
    def send_request_streaming(self, request: HttpRequest):
        """Returns an iterator of response chunks."""
        request_id = str(uuid.uuid4())
        chunk_queue = queue.Queue()

        with self._lock:
            self._pending_streams[request_id] = chunk_queue

        self.connection.send({**self._serialize(request), "request_id": request_id, "streaming": True})

        def chunk_iterator():
            while True:
                chunk = chunk_queue.get(timeout=30)
                if chunk is None:  # sentinel
                    break
                yield chunk

        return chunk_iterator()

    def receive_chunk(self, request_id: str, chunk: bytes, done: bool):
        q = self._pending_streams.get(request_id)
        if q:
            q.put(chunk)
            if done:
                q.put(None)  # sentinel
```

### 4. "How do you implement rate limiting per tunnel?"

Each Tunnel maintains a token bucket or sliding window counter. Before forwarding a request, check if the rate limit is exceeded.

```python
class RateLimitedTunnel(Tunnel):
    def __init__(self, *args, requests_per_second: int = 10, **kwargs):
        super().__init__(*args, **kwargs)
        self.rps = requests_per_second
        self._timestamps: list = []   # sliding window
        self._ratelimit_lock = threading.Lock()

    def _check_rate_limit(self) -> bool:
        now = time.time()
        with self._ratelimit_lock:
            # Remove timestamps older than 1 second
            self._timestamps = [t for t in self._timestamps if now - t < 1.0]
            if len(self._timestamps) >= self.rps:
                return False
            self._timestamps.append(now)
            return True

    def send_request(self, request: HttpRequest, timeout: float = 30.0) -> HttpResponse:
        if not self._check_rate_limit():
            return HttpResponse(429, {"Retry-After": "1"}, "Too Many Requests")
        return super().send_request(request, timeout)
```

### 5. "How do you support WebSocket tunneling?"

WebSocket connections are upgraded HTTP connections. The tunnel server needs to:
1. Detect the `Upgrade: websocket` header in the initial HTTP request
2. Complete the WebSocket handshake on behalf of the client
3. Forward all subsequent WebSocket frames bidirectionally through the persistent tunnel connection

```python
def handle_incoming(self, request: HttpRequest) -> HttpResponse:
    if request.headers.get("Upgrade", "").lower() == "websocket":
        return self._handle_websocket_upgrade(request)
    return self._handle_http(request)

def _handle_websocket_upgrade(self, request: HttpRequest):
    tunnel = self.registry.find_by_subdomain(self._extract_subdomain(request.host))
    # Complete WS handshake: send 101 Switching Protocols to caller
    # Then enter bidirectional frame relay mode
    # Each WS frame from public side -> tunnel connection -> local server
    # Each WS frame from local server -> tunnel connection -> public side
    pass
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
