---
module: 05-hld-problems
topic: Hard
status: unread
tags: [05-hld-problems, system-design, hard, llm, chatgpt, streaming, context-window, rag]
---
# Design an LLM Chat System (ChatGPT)

> **Difficulty**: Hard | **Asked at**: OpenAI, Anthropic, Google, Microsoft

---

## Problem Statement

Design a conversational AI chat system like ChatGPT. Users send messages to an LLM and receive streamed responses. The system maintains conversation history as context, supports multiple concurrent conversations per user, and provides low-latency streaming token delivery. At scale, the system must route requests to GPU servers efficiently and manage context window limits.

---

## Functional Requirements

1. **Chat**: User sends a message; system streams back an LLM response token by token
2. **Conversation history**: Previous turns are included as context for each new request
3. **Multiple conversations**: Users can have many independent conversation threads
4. **Model selection**: Users can select which model (GPT-4, GPT-4o, etc.) per conversation
5. **RAG (optional)**: Retrieve relevant documents to augment the LLM's context before sending
6. **File upload**: User can upload files (PDF, code) for the LLM to analyze

---

## Non-Functional Requirements

- **Scale**: 10M daily active users, 100M messages/day, 1M concurrent streaming responses
- **Latency**: Time-to-first-token < 500ms; token streaming at > 20 tokens/sec
- **GPU utilization**: GPU inference servers are expensive; must maintain > 80% utilization
- **Context**: Support up to 128K token context windows
- **Availability**: 99.9% — inference failures should retry automatically

---

## Core Entities

| Entity | Key Fields |
|--------|-----------|
| `User` | user_id, email, plan (free/plus), token_quota_daily |
| `Conversation` | conversation_id, user_id, model, title, created_at, system_prompt |
| `Message` | message_id, conversation_id, role (user/assistant/system), content, token_count, created_at |
| `Attachment` | attachment_id, conversation_id, message_id, s3_key, mime_type, extracted_text |

---

## API Design

```http
POST /api/v1/chat/completions
Body: {
  "conversation_id": "conv123",
  "message": "Explain quantum entanglement simply.",
  "model": "gpt-4o",
  "stream": true
}
Response: text/event-stream (SSE)
  data: {"delta": "Quantum", "token_id": 1}
  data: {"delta": " entanglement", "token_id": 2}
  ...
  data: {"delta": "", "finish_reason": "stop", "total_tokens": 142}

POST /api/v1/conversations
Body: { "model": "gpt-4o", "system_prompt": "You are a helpful coding assistant." }
Response 201: { "conversation_id": "conv123" }

GET /api/v1/conversations/{conv_id}/messages?limit=50
Response 200: { "messages": [{ "role": "user", "content": "..." }, { "role": "assistant", "content": "..." }] }
```

---

## High-Level Design

```
User (browser / mobile)
  │ SSE (Server-Sent Events) streaming connection
  ▼
API Gateway
  │ Auth, rate limit, quota check
  ▼
Chat Service
  │ 1. Load conversation history from PostgreSQL
  │ 2. Build prompt: [system] + [history messages] + [new user message]
  │ 3. Truncate if > context limit (summarization or sliding window)
  │ 4. Send to LLM Inference Router
  ▼
LLM Inference Router
  │ Select GPU server with capacity (least-loaded or consistent hash by conversation_id)
  │ Forward prompt; stream tokens back
  ▼
GPU Inference Cluster (vLLM / TensorRT-LLM)
  │ Load model, run inference
  │ Stream tokens back to router → API → client (SSE)
  ▼
After completion:
  Chat Service: save assistant response to PostgreSQL
  Token accounting: update user's daily quota
  Analytics: log completion (model, tokens, latency)
```

---

## Deep Dive 1: Streaming Token Delivery (SSE)

**Problem**: LLM inference generates tokens one at a time. Users expect to see text appearing progressively (like ChatGPT). HTTP is request-response; how do you stream partial responses?

**Server-Sent Events (SSE)**:
- Client opens a persistent HTTP connection: `Accept: text/event-stream`
- Server sends `data:` frames as tokens are generated, one per line
- Connection stays open until inference completes; server sends `data: [DONE]`
- Client-side: `EventSource` API handles reconnection automatically

**Why SSE over WebSocket for LLM chat**:
- LLM chat is unidirectional during response (server→client only)
- SSE is simpler: HTTP/2 compatible, works through standard proxies, automatic reconnect
- WebSocket adds overhead for bidirectional protocol when only one direction is needed

**Token buffer**: GPU server generates tokens faster than the network can deliver them. A token buffer (ring buffer, 1,000 tokens max) prevents the GPU from blocking on network I/O. The buffer is drained by the SSE writer on a separate thread.

**Backpressure**: If the client's connection is slow (mobile on poor network), the buffer fills. The SSE writer applies backpressure: pauses accepting new tokens from the GPU. The GPU's KV cache (key-value cache for attention layers) holds the state. When the client catches up, streaming resumes.

---

## Deep Dive 2: Context Window Management

**Problem**: A conversation has 200 turns. The cumulative token count is 150,000 tokens, but GPT-4 has a 128K context window. How do you handle the overflow?

**Strategy 1: Sliding window** — Drop the oldest turns:
```python
def build_prompt(conversation_id, new_message, max_tokens=120_000):
    messages = db.get_messages(conversation_id, order="asc")
    messages.append({"role": "user", "content": new_message})
    total_tokens = sum(count_tokens(m["content"]) for m in messages)
    while total_tokens > max_tokens and len(messages) > 2:
        # Drop oldest user+assistant pair (keep system prompt = messages[0])
        messages.pop(1)  # oldest assistant
        messages.pop(1)  # oldest user
        total_tokens = sum(count_tokens(m["content"]) for m in messages)
    return messages
```
Simple, fast. Downside: loses context from early in conversation.

**Strategy 2: Hierarchical summarization** — When history exceeds threshold:
1. Take the N oldest message pairs
2. Ask the LLM: `Summarize this conversation history in 200 words: {old_messages}`
3. Replace the old messages with the summary
4. Continue with summary + recent messages

Better coherence for long conversations. Costs an extra LLM call, adds ~500ms.

**Strategy 3: RAG** — Store all messages in a vector DB (embeddings). On each turn, retrieve the top-K most relevant past messages using semantic search and inject them into the context.

---

## Deep Dive 3: GPU Inference Routing and Batching

**Problem**: GPU servers are expensive ($3/hr each). 1M concurrent users generate 1M inference requests. Each request takes 2-10 seconds. How do you maximize GPU utilization?

**Continuous batching** (vLLM):
- Traditional inference: GPU processes one request at a time. Utilization < 30%.
- Continuous batching: GPU processes up to 64 requests simultaneously (batch). When one completes, another joins immediately. GPU utilization > 85%.
- Each request in the batch generates tokens at its own pace; finished sequences leave the batch without waiting for others.

**KV cache management**: Attention layers compute and cache (K, V) tensors for each input token. For a 128K context, the KV cache is 10-40 GB per sequence. With 64 concurrent sequences, the GPU needs 640 GB of KV cache — more than a single A100 (80 GB).
- **PagedAttention** (vLLM): KV cache is managed in pages (like virtual memory). Pages are allocated/freed dynamically. Long sequences share memory pages for common prefixes (e.g., system prompts shared across users).

**Routing**: The LLM Inference Router uses consistent hashing by `conversation_id` to send the same conversation to the same GPU server when possible (warm KV cache). On server failure, reroute to another server (cold start — small latency hit).

**Auto-scaling**: Queue depth metric triggers GPU server scaling. If average queue depth > 5 requests → launch new GPU instances (30-second warmup). If queue depth < 1 for 10 minutes → terminate idle instances.

---

## Interviewer Questions by Level

**Junior**:
- What is a context window in an LLM? Why does it matter for a chat system?
- What is SSE (Server-Sent Events)? How does it differ from regular HTTP?
- What is a "token" in LLM terminology?

**Mid-level**:
- How do you handle a conversation that exceeds the context window limit?
- Why is GPU inference expensive? How does batching improve GPU utilization?
- How do you implement rate limiting for a free-tier user who can send 50 messages/day?

**Senior**:
- Design the token streaming pipeline end-to-end — from GPU inference to client display.
- Explain continuous batching (vLLM) and PagedAttention. How do they improve GPU utilization vs naive inference?
- Design the RAG pipeline for a ChatGPT-like system that can reference uploaded documents.
- How do you handle GPU server failure mid-stream? What does the user experience, and how do you recover?
