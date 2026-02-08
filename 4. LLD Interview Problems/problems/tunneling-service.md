# Design Tunneling Tool (Ngrok)

> **Difficulty**: Hard  
> **Topics**: Networking, AsyncIO, Multiplexing, Reverse Tunneling  
> **Similar**: Ngrok, Cloudflare Tunnel

## Problem Statement

Expose a local web server (localhost:8080) to the public internet (myapp.tunnel.com) without port forwarding.
- **Challenges**: NAT traversal (Inbound blocked), Multiplexing multiple requests over one connection.

## Architecture: Reverse Tunnel

1.  **Agent (Client)** connects OUTBOUND to **Tunnel Server** (Cloud).
    *   Since it's outbound, NAT/Firewall allows it.
    *   Connection is persistent (TCP or WebSocket).
2.  **Public User** hits `myapp.tunnel.com`.
3.  **Tunnel Server** receives request, wraps it in a packet, and sends it *down* the persistent connection to Agent.
4.  **Agent** unwraps, hits `localhost:8080`, gets response, and sends it back *up*.

## Python Implementation (AsyncIO)

We use `asyncio` for high concurrency.

### Tunnel Server (Cloud)

```python
import asyncio

active_tunnels = {} # subdomain -> (reader, writer)

async def handle_agent(reader, writer):
    # Agent Handshake: "REGISTER:demo"
    data = await reader.read(1024)
    msg = data.decode()
    if msg.startswith("REGISTER:"):
        subdomain = msg.split(":")[1]
        active_tunnels[subdomain] = (reader, writer)
        # Keep alive...

async def handle_public_request(reader, writer):
    # 1. Parse Subdomain (e.g., demo.tunnel.com)
    data = await reader.read(4096)
    subdomain = parse_subdomain(data)
    
    if subdomain in active_tunnels:
        agent_reader, agent_writer = active_tunnels[subdomain]
        # 2. Forward Request Down Tunnel
        agent_writer.write(data)
        await agent_writer.drain()
        
        # 3. Read Response from Agent
        response = await agent_reader.read(4096)
        writer.write(response) # Send to public user
```

### Tunnel Agent (Localhost)

```python
async def start_agent():
    # 1. Connect to Tunnel Server
    remote_reader, remote_writer = await asyncio.open_connection('server.com', 9090)
    remote_writer.write(b"REGISTER:demo")
    
    while True:
        # 2. Receive Request from Cloud
        req = await remote_reader.read(4096)
        
        # 3. Forward to Localhost:8080
        local_reader, local_writer = await asyncio.open_connection('127.0.0.1', 8080)
        local_writer.write(req)
        resp = await local_reader.read(4096)
        
        # 4. Send Response back to Cloud
        remote_writer.write(resp)
```

## Key Design Challenges

1.  **Multiplexing (HOL Blocking)**: 
    *   In simple TCP, one request blocks the pipe.
    *   **Solution**: Use a framing protocol. `[RequestID | Length | Payload]`. Agent matches Response ID to Request ID. This allows async processing of multiple requests over one TCP socket.
2.  **Disconnection**:
    *   Agent needs a `while True` loop with exponential backoff to reconnect.
    *   Heartbeats (Ping/Pong) to keep NAT mapping alive.
3.  **Security**:
    *   Public Server does TLS termination (HTTPS).
    *   Tunnel sends plain HTTP (or re-encrypted) to Agent.
