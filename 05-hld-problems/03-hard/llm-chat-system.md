# Design a ChatGPT-like LLM Chat System

> **Difficulty**: Hard
> **Topics**: LLM Inference, Token Streaming, KV-Cache, Context Window Management, Multi-tenant GPU Infrastructure
> **Time**: 60-75 minutes
> **Companies**: OpenAI, Anthropic, Google DeepMind, Meta AI, Cohere, Mistral

---

## Problem Statement

Design a production LLM chat service that:
- Accepts multi-turn conversations from users
- Streams responses token-by-token (< 200ms to first token)
- Supports 10M DAU with millions of concurrent conversations
- Manages context window constraints (e.g. 128K tokens)
- Handles multiple model sizes (small fast / large accurate)
- Ensures user conversation history is persisted and searchable

---

## Analogy

A real-time stenographer at a press conference. The journalist asks a question (prompt); the stenographer immediately starts writing and displays each word as they transcribe (streaming tokens). The journalist can see partial answers in real-time rather than waiting for the full response.

At scale: imagine 1 million journalists all asking questions simultaneously, each needing their own stenographer, and the steno pool is GPU clusters costing thousands of dollars per hour. The challenge isn't the intelligence — it's the economics and latency of serving inference at massive concurrency.

---

## What Breaks Without This System?

Without an inference serving layer with request batching and KV-cache management, each user request gets its own dedicated GPU for the full duration of generation — at 10M DAU × 5 requests/day, peak concurrency is ~50K simultaneous requests, each tying up a full A100 GPU ($3/hr). That's $150K/hr in raw GPU cost just for compute allocation, before factoring in model loading latency. Without streaming, users stare at a blank screen for 10–30 seconds waiting for a full response — the product feels broken compared to alternatives that stream incrementally.

---

## Derive the Architecture

**1 GPU, synchronous inference**: User sends prompt → model runs full forward pass → returns complete response. Works for ~5 concurrent users on a 7B parameter model. Time to first token: 10–30 seconds. Breaks when: 50 concurrent users → each waits for 49 others to finish before their request starts. Queue depth at 50K QPS = minutes of wait. Fix: stream tokens as they are generated so users see output immediately, and batch multiple requests together onto one GPU.

**Token streaming + single GPU**: Server-Sent Events (SSE) stream each token as it is generated (~50 tokens/sec on A100 for 7B model). User sees first token in <500ms. Still limited to ~5–10 concurrent requests on one GPU before VRAM fills with KV-cache (one 128K-context request = ~16 GB KV-cache on a 7B model). Breaks when: 10M DAU creates peak demand for thousands of concurrent GPU slots. Fix: horizontal GPU fleet with a load balancer that routes requests to available GPU workers.

**GPU cluster with load balancer**: 100 A100 GPUs, each serving 10 concurrent requests via continuous batching = 1,000 concurrent requests. Handles ~50K QPS at 50 tokens/response average. Breaks when: simple load balancing ignores KV-cache locality — a user's turn 2 request lands on a different GPU than turn 1, so the KV-cache for turn 1 tokens must be recomputed from scratch (adds 200–500ms per turn). Fix: session affinity — route all turns in the same conversation to the same GPU worker so the KV-cache is reused.

**Session-affinity routing**: Consistent hash on conversation_id routes all messages in a session to the same GPU instance. KV-cache reuse cuts per-token latency by 50–80% for multi-turn conversations. Breaks when: the GPU instance for a session crashes — all in-flight KV-cache is lost, session affinity fails, and the re-routed request must cold-start. Fix: store conversation history in a fast external store (Redis); on re-route, replay the last N turns to rebuild KV-cache from the new worker.

**Persistent conversation history + GPU fleet**: Conversation turns stored in Redis (hot, last 24h) and S3/DB (cold, long-term). On GPU reassignment, the new worker fetches the last 10 turns from Redis and re-warms KV-cache in ~200ms. Breaks when: context window fills (128K token limit) for users with very long conversations. Fix: context management service summarizes old turns, evicting the least-relevant earlier turns while keeping the summary + recent turns within the window limit.

---

## Why This Is Hard

1. **GPU is the bottleneck**: LLM inference is compute-bound on GPUs. A single A100 GPU can serve ~10-50 concurrent requests depending on model size. With 10M DAU and average 5 requests/user/day, peak QPS = ~50K. You need thousands of GPUs and intelligent request routing.
2. **KV-Cache management**: During generation, the model computes Key-Value tensors for every previous token (the "attention cache"). Storing this cache per request requires tens of GB of GPU VRAM. Managing cache across requests and model instances is the core scaling problem.
3. **Streaming vs batching trade-off**: Batching multiple requests together increases GPU utilization but increases time-to-first-token. Continuous batching (vLLM PagedAttention) solves this but requires sophisticated memory management.
4. **Context window limits**: A 128K token context window model can process ~100K words per request. But if a user has a 200K token conversation history, you must decide what to include, summarize, or evict — without the user noticing.
5. **Cost per request**: GPT-4 costs ~$0.01-$0.06 per 1K tokens. A 10-message conversation with average 500 tokens/message = 5K tokens = $0.05-$0.30 per conversation. At 10M DAU with 5 conversations/day = 50M conversations/day = $2.5M-$15M/day in raw GPU costs. Cost optimization is existential.

---

## Critical Requirements

### Functional
- Multi-turn chat with context continuity
- Streaming responses (token-by-token)
- Conversation history storage and retrieval
- System prompt support (custom assistant personas)
- Tool use / function calling (web search, code execution)
- Multi-model routing (fast small model vs slow large model)

### Non-Functional
- **TTFT (Time to First Token)**: < 500ms P99
- **Token throughput**: > 50 tokens/second per stream
- **Availability**: 99.9% (8.7 hours downtime/year tolerable)
- **Cost efficiency**: Maximize GPU utilization (> 60% target)
- **Privacy**: Conversation data encrypted, isolated per user/tenant

---

## Scale Estimation

```
Users: 10M DAU
Messages per user per day: 10 (5 conversations × 2 turns each)
Total messages/day: 100M
Average prompt tokens: 500
Average completion tokens: 300
Total tokens/day: 100M × 800 = 80B tokens/day

Peak QPS (10× average):
  Avg: 100M / 86,400 = 1,157 req/sec
  Peak: ~12,000 req/sec

GPU requirement (A100 80GB, 30 req/sec per GPU for 7B model):
  12,000 / 30 = 400 GPUs minimum at peak (2,000 with redundancy)

Storage:
  Conversation: 100M messages/day × 1KB = 100GB/day
  1 year: 36 TB (messages) + indexes
  Model weights: 7B model = 14GB (FP16) to 7GB (INT8)
  KV-Cache: ~2GB per concurrent 128K-token context (requires VRAM)
```

---

## Core Concepts

### 1. Transformer Inference & KV-Cache

```
Prompt Processing (Prefill phase):
  Input tokens → Attention layers → KV tensors computed and cached
  This phase is parallel (fast for short prompts, slow for long ones)

Token Generation (Decode phase):
  One token generated per forward pass
  Reuses cached KV tensors from prefill + previously generated tokens
  This is sequential — can't parallelize within one request

KV-Cache size per request:
  = num_layers × 2 (K and V) × num_heads × head_dim × seq_len × bytes_per_element
  For LLaMA-3 70B, 128K context: ~35GB per request (impossible on one GPU!)
  → Must shard across GPUs (tensor parallelism) or limit context
```

### 2. Continuous Batching (vLLM PagedAttention)

```
Naive batching: Group N requests, run together. Problem: one long request
blocks the batch (head-of-line blocking). GPU waits for all N to finish.

Continuous batching: Requests join and leave the batch dynamically.
As one request finishes generating, a new request is immediately inserted.

PagedAttention: KV cache is stored in non-contiguous "pages" (like OS virtual
memory). This eliminates internal fragmentation and allows sharing common
prefixes (system prompts) across thousands of requests.

Prefix caching: If 10,000 users all use the same system prompt (2,000 tokens),
compute and cache those KV tensors once and reuse. 90% VRAM savings on
shared prefixes.
```

### 3. Streaming (Server-Sent Events)

```
Client → POST /chat (prompt)
Server → SSE stream:
  data: {"token": "Paris", "index": 0}\n\n
  data: {"token": " is", "index": 1}\n\n
  data: {"token": " the", "index": 2}\n\n
  ...
  data: {"done": true, "total_tokens": 47}\n\n

Protocol choice:
  SSE (Server-Sent Events): HTTP/1.1 compatible, one-directional, simple
  WebSockets: Bidirectional but heavier, use when client also streams input
  gRPC streaming: Best for internal service-to-service streaming
```

### 4. Context Window Management

```
Problem: User conversation grows beyond 128K token limit.

Strategy 1: Sliding Window
  Keep the system prompt + last N messages that fit in the context window.
  Simple but loses early conversation context.

Strategy 2: Summarization
  When context > 80% of limit, ask the model to summarize the oldest 50% of
  the conversation into a compact "memory" block. Replace those turns with
  the summary. User perceives continuity.

Strategy 3: Retrieval-Augmented Memory
  Store ALL conversation turns in a vector DB.
  At each request, retrieve the most relevant past turns (semantic search).
  Include retrieved chunks + recent turns in context.
  Best quality but adds latency (vector search + LLM reranking).

Decision: Use sliding window for most users; summarization for power users
with long conversations (> 50 turns).
```

---

## Database Schema

```sql
-- Users and sessions
CREATE TABLE conversations (
    conversation_id UUID PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    title           VARCHAR(255),
    model_id        VARCHAR(50),       -- 'gpt-4o', 'claude-3', 'llama-3-70b'
    system_prompt   TEXT,
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP,
    token_count     INT DEFAULT 0,     -- Running total for billing
    INDEX idx_user_conv (user_id, updated_at DESC)
);

CREATE TABLE messages (
    message_id      UUID PRIMARY KEY,
    conversation_id UUID REFERENCES conversations(conversation_id),
    role            ENUM('user', 'assistant', 'system', 'tool'),
    content         TEXT,
    token_count     INT,
    model_id        VARCHAR(50),
    latency_ms      INT,               -- P99 tracking
    created_at      TIMESTAMP,
    INDEX idx_conv_msg (conversation_id, created_at ASC)
);

-- For billing and rate limiting
CREATE TABLE usage_events (
    event_id        UUID PRIMARY KEY,
    user_id         BIGINT,
    conversation_id UUID,
    model_id        VARCHAR(50),
    prompt_tokens   INT,
    completion_tokens INT,
    total_cost_usd  DECIMAL(10, 6),
    created_at      TIMESTAMP,
    INDEX idx_user_usage (user_id, created_at DESC)
);
```

---

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                        CLIENT                                │
│   (Browser / Mobile / API client)                            │
└───────────────────┬──────────────────────────────────────────┘
                    │ HTTPS POST /chat (with SSE response)
                    ▼
┌──────────────────────────────────────────────────────────────┐
│               API GATEWAY / EDGE LAYER                       │
│   Rate limiting | Auth (JWT) | TLS termination | WAF         │
│   (Cloudflare Workers / AWS API Gateway + Lambda@Edge)       │
└───────────────────┬──────────────────────────────────────────┘
                    │
                    ▼
┌──────────────────────────────────────────────────────────────┐
│                CHAT ORCHESTRATION SERVICE                    │
│   1. Load conversation history (last N messages)             │
│   2. Apply context window management (sliding window / sum)  │
│   3. Select model + route to inference cluster               │
│   4. Stream response back to client via SSE                  │
│   5. Persist assistant message                               │
│   6. Emit usage event (billing/rate limiting)                │
└────┬──────────────────────┬───────────────────────┬──────────┘
     │                      │                       │
     ▼                      ▼                       ▼
┌─────────┐        ┌────────────────┐       ┌──────────────┐
│PostgreSQL│       │  Model Router  │       │  Usage Queue │
│(Conv/Msg)│       │                │       │  (Kafka)     │
└─────────┘        └───────┬────────┘       └──────────────┘
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
      ┌──────────┐  ┌──────────┐  ┌──────────┐
      │Fast Model│  │Full Model│  │Long-Ctx  │
      │Cluster   │  │Cluster   │  │Cluster   │
      │(7B LLaMA)│  │(70B/GPT4)│  │(128K ctx)│
      └────┬─────┘  └────┬─────┘  └────┬─────┘
           └─────────────┴─────────────┘
                         │
                    vLLM Servers
                (Continuous Batching,
                 PagedAttention)
```

---

## API Design

### Send Message (Streaming)

```http
POST /v1/conversations/{conversation_id}/messages
Authorization: Bearer <token>
Content-Type: application/json
Accept: text/event-stream

Request:
{
  "role": "user",
  "content": "Explain quantum entanglement simply",
  "model": "auto",           // auto-selects based on complexity
  "stream": true,
  "tools": ["web_search"]    // optional tool use
}

Response (SSE stream):
HTTP/1.1 200 OK
Content-Type: text/event-stream
Cache-Control: no-cache

data: {"id":"msg_01","delta":{"content":"Quantum"},"model":"llama-3-70b"}

data: {"id":"msg_01","delta":{"content":" entanglement"},"model":"llama-3-70b"}

data: {"id":"msg_01","delta":{"content":" is"},"model":"llama-3-70b"}

data: {"id":"msg_01","done":true,"usage":{"prompt_tokens":15,"completion_tokens":120}}
```

### Create Conversation

```http
POST /v1/conversations
{
  "title": "Physics Q&A",
  "system_prompt": "You are a friendly physics tutor. Use analogies.",
  "model": "claude-3-sonnet"
}

Response: 201 Created
{
  "conversation_id": "conv_abc123",
  "title": "Physics Q&A",
  "model": "claude-3-sonnet",
  "created_at": "2026-05-13T11:00:00Z"
}
```

---

## Model Routing Strategy

```
Request comes in → Model Router evaluates:

1. Complexity scoring (fast heuristic):
   - Short prompt (< 100 tokens) + simple query → route to 7B model
   - Code generation / complex reasoning → route to 70B model
   - Very long context (> 32K tokens) → route to long-context model

2. Load-aware routing:
   - Check GPU utilization across clusters
   - Route to least-loaded cluster that satisfies model requirement

3. Cost-aware routing:
   - Free tier: 7B model only
   - Pro tier: auto-routing, prefers quality
   - API tier: explicit model selection honored

Implementation (simplified):
```python
def route_request(prompt: str, user_tier: str, context_len: int) -> str:
    if context_len > 50_000:
        return "long-context-cluster"
    if user_tier == "free":
        return "fast-cluster"
    complexity = estimate_complexity(prompt)  # Lightweight classifier
    if complexity > 0.7 or is_code_request(prompt):
        return "full-cluster"
    return "fast-cluster"
```

---

## Streaming Implementation

```python
# FastAPI streaming endpoint
from fastapi import FastAPI
from fastapi.responses import StreamingResponse
import asyncio

app = FastAPI()

@app.post("/v1/conversations/{conversation_id}/messages")
async def send_message(conversation_id: str, request: MessageRequest):
    async def generate():
        # 1. Load context
        history = await load_conversation_history(conversation_id)
        context = apply_context_window(history, request.content)

        # 2. Stream from vLLM
        async for token in vllm_client.generate_stream(context):
            yield f"data: {token.to_json()}\n\n"

        # 3. Signal completion
        yield f"data: {{\"done\": true}}\n\n"

        # 4. Persist async (don't block the stream)
        asyncio.create_task(persist_message(conversation_id, response))
        asyncio.create_task(emit_usage_event(conversation_id, usage))

    return StreamingResponse(
        generate(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"}
    )
```

---

## Scaling: GPU Fleet Management

### Horizontal Scaling of Inference

```
Each vLLM server runs one model replica:
  - 7B model: 1× A100 GPU (16GB VRAM)
  - 70B model: 4× A100 GPUs (tensor parallelism across 4 GPUs)
  - 405B model: 8+ A100 GPUs (pipeline parallelism)

Auto-scaling triggers:
  - Queue depth > 10 pending requests per server → scale out
  - GPU utilization < 20% for 5 min → scale in
  - Predictive scaling based on time-of-day patterns

Cold start problem:
  - Warm pool: Keep minimum 10% extra capacity always warm
  - Model loading from S3/EFS: ~2-5 minutes (too slow for auto-scale)
  - Solution: Pre-loaded model weights on NVMe SSDs per node
  - Load from NVMe: ~30-60 seconds (acceptable for warm pool expansion)
```

### Cost Optimization

```
1. Spot Instances for batch/non-latency-sensitive workloads
   - Background summarization → spot GPUs
   - Embedding generation → spot GPUs
   - Live serving → on-demand GPUs (no interruption risk)

2. Speculative decoding
   - Small "draft" model generates 4 tokens speculatively
   - Large model verifies all 4 in one forward pass
   - 2-3× speedup when draft is often correct
   - Cost same as large model alone, throughput improved

3. Quantization
   - FP16 (default): Full quality
   - INT8: 2× smaller, ~1% quality drop, 1.5× faster
   - INT4 (GPTQ/AWQ): 4× smaller, ~3% quality drop, 2× faster
   - Use INT4 for free-tier users, FP16 for pro-tier

4. Prefix caching
   - System prompts shared across users (e.g. "You are a helpful assistant")
   - Cache KV tensors for common prefixes → 50-80% VRAM savings for that portion
```

---

## Failure Scenarios

### vLLM Server OOM (Out of Memory)

```
Cause: KV cache grows faster than PagedAttention can reclaim.
Symptom: CUDA OOM error during generation.

Response:
1. Immediately return error to client: {"error": "generation_failed", "retry": true}
2. Reduce max_concurrent_sequences on that server
3. Alert ops team; server may need restart
4. Client retries with exponential backoff + jitter (ideally hitting different server)
5. Implement context length limits per user tier to bound memory usage
```

### Conversation History DB Overload

```
Cause: 10M users × 5 conversations × 10 messages = 500M messages.
At 1KB each = 500GB. Read QPS for context loading = 12,000/sec.

Mitigation:
- Redis cache: Cache last 20 messages per active conversation (LRU eviction)
- PostgreSQL read replicas: Route history reads to replicas
- Partition by user_id: Messages partitioned across shards
- Archive old conversations to S3 after 90 days of inactivity
```

### Model Service Degradation

```
Primary model (70B) latency P99 > 5 seconds:
1. Circuit breaker trips on inference client
2. Model router falls back to 7B fast model
3. Response quality degrades but service stays up
4. Alert: "Model degradation — serving reduced quality"
5. Full model cluster auto-scales; once recovered, circuit half-opens, then closes
```

---

## Monitoring

```
Key Metrics:
  TTFT (Time to First Token) — P50, P99 (target: P99 < 500ms)
  Tokens per second (TPS) — per model, per cluster
  GPU utilization — target 60-80% (below = waste, above = queue buildup)
  KV cache hit rate — target > 50% (prefix caching effectiveness)
  Error rate — < 0.1% (non-retryable errors)
  Cost per request — track by model, user tier

Alerts:
  P0: TTFT P99 > 5s for > 2 minutes
  P0: Error rate > 1%
  P1: GPU utilization > 95% (scaling lag risk)
  P1: KV cache memory > 90% on any server
  P2: Spot instance interruption (auto-mitigated but track frequency)
```

---

## Interview Talking Points

**Q: "How do you handle the context window limit?"**
> "First, I'd use a sliding window — keep the system prompt plus the last N turns that fit. For power users with long conversations, I'd add background summarization: when the conversation hits 80% of the context limit, an async job asks the model to compress the oldest 50% into a dense summary paragraph. We swap those turns with the summary. Users experience continuity without noticing the truncation. For enterprise customers, I'd offer retrieval-augmented memory where we store all turns in a vector DB and semantically retrieve the most relevant past turns for each new message."

**Q: "How do you achieve sub-500ms time-to-first-token?"**
> "Three things: (1) Continuous batching via vLLM so new requests don't wait for previous ones to complete — they join mid-batch. (2) Prefix caching for common system prompts — those KV tensors are precomputed, so prefill only needs to process the new user message. (3) Speculative decoding — a small draft model generates 4 tokens in parallel, the large model verifies them in one pass. Combined, these bring TTFT from ~2s to ~200ms for typical requests."

**Q: "How do you control costs at scale?"**
> "GPU cost is the dominant factor. I optimize via: (1) Tiered model routing — free users get the 7B INT4 model, pro users get 70B FP16. (2) Prefix caching — shared system prompts compute once, KV cache reused thousands of times. (3) Speculative decoding — same quality, 2-3× throughput. (4) Spot instances for non-latency-sensitive work. (5) Smart scaling — predict load from historical patterns and pre-scale rather than reactive scaling which wastes capacity."

**Q: "What's the hardest part of this system?"**
> "GPU memory management. The KV cache for a 128K-token context at 70B parameters is ~35GB — more than one GPU. PagedAttention solves this with virtual memory-style paging, but you need to tune page size vs fragmentation. And when demand spikes, you have a 30-60 second cold start for new GPU instances. The warm pool strategy is essential but expensive — you're paying for standby GPUs. The economic optimization never ends."

---

## Interview Questions Asked

### OpenAI
1. **"Design the ChatGPT infrastructure — focus on inference serving at scale"** → Probe: GPU memory management, batching strategy, KV cache, cost optimization. Hint: vLLM with PagedAttention for KV cache paging; continuous batching so new requests join mid-batch; prefix caching for shared system prompts; tiered model routing (7B for free tier, 70B for pro).

### Anthropic
1. **"How do you implement streaming token delivery end-to-end?"** → Probe: from GPU output to user browser with low TTFT and smooth streaming. Hint: model server streams tokens via gRPC server-side streaming → inference gateway → SSE (Server-Sent Events) to browser; each token flushed immediately; connection pinned to same inference server via sticky routing for session affinity.

### Common Follow-ups
1. **"What is KV cache and how does prefix caching exploit it?"** → KV cache stores key-value tensors for each token in context, avoiding recomputation on each decode step; prefix caching: if two requests share the same system prompt prefix, the KV tensors for that prefix are computed once and reused — reducing prefill cost from O(prefix_tokens) to O(0) for cached prefix.
2. **"How do you handle context window overflow gracefully?"** → Sliding window keeps system prompt + last N turns; at 80% context limit, async job summarizes oldest 50% of conversation into a dense paragraph; swap original turns with summary — user experiences continuity; for enterprise, vector DB stores all turns and semantically retrieves relevant history per new message.
3. **"How do you route requests between small and large models?"** → Complexity classifier (fast, cheap model) scores incoming request; simple factual queries → 7B model (low cost, fast); complex reasoning, code → 70B model; can also use token budget as signal — if user's message is short and straightforward, small model; measure quality via A/B test on user thumbs-up rate.
4. **"How do you rate-limit tokens-per-minute vs requests-per-minute?"** → TPM is more meaningful for GPU cost control: count tokens in request + estimated response tokens; use sliding-window counter in Redis; TPM limit prevents one user with 100K-token context from consuming all GPU capacity; RPM limit prevents API abuse; pro tier gets higher TPM, not just more RPM.
5. **"How do you defend against prompt injection?"** → Separate system prompt from user content at the API boundary (never concatenate as plain strings); use structured message format (role: system / user / assistant); output scanning for instruction-following patterns in user turns; privilege separation — user prompt cannot override system-level instructions in API design.
