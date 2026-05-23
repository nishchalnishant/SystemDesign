---
module: 05-hld-problems
topic: Hard
status: unread
tags: [05-hld-problems, system-design, hard]
---
# Design a RAG (Retrieval-Augmented Generation) System

> **Difficulty**: Hard
> **Topics**: Vector Databases, Embedding Pipelines, Hybrid Search, Re-ranking, Chunking Strategies
> **Time**: 60-75 minutes
> **Companies**: OpenAI, Anthropic, Microsoft (Copilot), Salesforce, Notion AI, Glean

---

## Problem Mindmap

```
RAG System (Retrieval-Augmented Generation)
├── Problem Constraints
│   ├── Scale → 1M documents, 50M chunks, 300GB vector index, 10K QPS retrieval
│   ├── Latency target → retrieval p99 < 100ms; end-to-end (retrieval + LLM) < 3s
│   └── Core hardness → hybrid search (keyword + semantic) + re-ranking accuracy + multi-tenant isolation
├── Architecture Derivation
│   ├── Step 1 → Full-text search only → misses semantic matches ("automobile" vs "car"); poor recall for paraphrased queries
│   ├── Step 2 → Dense vector search only → poor for exact keyword matches (names, IDs, codes); Achilles heel of embeddings
│   ├── Step 3 → Hybrid: BM25 (sparse) + HNSW ANN (dense) → merge via Reciprocal Rank Fusion (RRF); best of both worlds
│   └── Step 4 → Cross-encoder re-ranking on top-50 candidates → full attention on (query, chunk) pair; precise but slow; only run on top-50
├── Core Components
│   ├── Ingestion Pipeline → document → chunker (512 tokens, 50-token overlap) → embedding model → vector DB + BM25 index
│   ├── Vector DB → HNSW index (Qdrant/Weaviate/Pinecone); approximate nearest-neighbor; 300GB in RAM; namespace per tenant
│   ├── BM25 Index → Elasticsearch; keyword matching; sparse retrieval; complements dense ANN
│   ├── RRF Merger → Reciprocal Rank Fusion: score = Σ(1/(k+rank_i)); k=60 standard; merges BM25 and ANN result lists
│   └── Cross-encoder Re-ranker → BERT-based model; scores (query, chunk) pair with full attention; top-50→top-5; runs on GPU
├── Data Model
│   ├── chunks → (chunk_id UUID, doc_id, tenant_id, text, embedding VECTOR(1536), metadata{page, section, created_at})
│   └── documents → PostgreSQL (doc_id, tenant_id, source_url, title, ingestion_status, chunk_count, indexed_at, content_hash)
├── APIs
│   ├── POST /ingest → {tenant_id, document_url, metadata} → {job_id}; async webhook on completion
│   ├── POST /query → {tenant_id, question, top_k, filters{}} → {chunks: [{text, score, source}], answer_context}
│   └── DELETE /documents/{doc_id} → remove chunks from vector DB + BM25 index; tombstone in PostgreSQL
├── Critical Trade-offs
│   ├── BM25 + ANN vs ANN only → hybrid chosen; BM25 handles exact terms; ANN handles semantic; RRF fusion outperforms either alone
│   ├── Cross-encoder vs bi-encoder re-ranking → cross-encoder more accurate (joint attention); bi-encoder faster (independent encoding)
│   └── Chunk size 512 tokens → balances recall (too small = loses context) vs precision (too large = dilutes relevance score)
├── Failure Scenarios
│   ├── Vector DB OOM → HNSW graph evicted to disk; query latency spikes; scale out nodes; index sharded by tenant_id
│   ├── Embedding model unavailable → ingestion queued in Kafka; retrieval falls back to BM25-only (lower quality but available)
│   └── Stale index on document update → webhook triggers re-ingestion on doc change; old chunks tombstoned; incremental re-index
└── Interview Angles
    ├── Microsoft Copilot → "Design an enterprise RAG system" → hybrid search + multi-tenant namespace isolation + re-ranking = core
    ├── Glean → "How do you retrieve from millions of enterprise documents?" → BM25+ANN hybrid; RBAC filter pre-retrieval
    └── Follow-up → "How does chunking strategy affect quality?" → semantic chunking (split at paragraph boundaries) > fixed-size for coherence
```

---

## Problem Statement

Design a RAG system that:
- Ingests and indexes enterprise documents (PDFs, Confluence, Slack, GitHub)
- Answers user questions grounded in those documents
- Returns accurate answers with source citations
- Handles 1M+ documents, 100K DAU, 10K QPS queries
- Stays fresh as documents are updated/added
- Supports multi-tenant isolation (different companies' data stays separate)

---

## Analogy

A research librarian with a photographic memory. When you ask a question, the librarian doesn't read every book (that would take too long) — they first consult the index cards (retrieval) to find the 5-10 most relevant book passages, bring those to you, and then synthesize an answer from just those passages. They cite their sources.

Without the library index: the librarian would have to read every book for every question — O(N) per query, unusable at scale. The index is what makes sub-second answers possible over millions of documents.

---

## What Breaks Without This System?

Without retrieval augmentation, the LLM answers entirely from its training weights — it hallucates facts not in its training data, cannot reference documents updated after its cutoff, and cannot cite sources. For enterprise use cases (internal knowledge bases, legal documents, medical records), this is not just unhelpful but actively dangerous. Without a vector index, finding relevant passages from 1M documents means feeding all 1M documents into the LLM context per query — at 500 tokens/doc that's 500M tokens per query, costing hundreds of dollars and taking hours per answer.

---

## Derive the Architecture

**No retrieval, pure LLM**: Send the user's question directly to the LLM. Works when the answer is in the model's training data. Breaks when: the question requires knowledge from private enterprise documents. The model has no access to those documents and hallucates a plausible-sounding answer. Fix: embed documents into a vector index so relevant passages can be retrieved and injected into the prompt.

**1 server, vector DB (1M documents)**: Chunk each document into ~500-token passages, embed each with a text embedding model (1536-dim vector), store in a vector DB (Pinecone/Weaviate). At query time: embed the question → approximate nearest neighbor (ANN) search → retrieve top-10 chunks → send them as context to LLM. Handles 1M documents at ~10K QPS. Breaks when: pure semantic search misses exact term matches — "GDPR Article 17" is not semantically close to its embedding unless those exact terms appear repeatedly. Fix: hybrid search — run BM25 keyword search in parallel with vector search, merge results with RRF (Reciprocal Rank Fusion).

**Hybrid search (BM25 + vector)**: Run both searches in parallel; RRF merges ranked lists from each. Recall improves significantly for exact-term queries while semantic matching is preserved. Handles 10K QPS at <200ms retrieval latency. Breaks when: 1M documents from multiple tenants share one vector index — a query from Tenant A could return Tenant B's confidential documents if tenant isolation is not enforced at the query layer. Fix: add tenant_id as a metadata filter on every ANN query; the vector DB evaluates the filter before returning results.

**Multi-tenant isolation with metadata filters**: Each document indexed with `{tenant_id, doc_id, chunk_id}`. Every query filtered by `tenant_id == caller`. Prevents cross-tenant data leakage. Handles 1,000 tenants sharing one index. Breaks when: retrieval quality degrades as the corpus grows to 10M+ documents — top-10 retrieved chunks may be superficially related but not the most useful passage for the specific question. Fix: add a re-ranker (cross-encoder model) that scores each candidate chunk against the query directly and reorders the top-10 by relevance.

**Re-ranking layer**: Retrieve top-50 candidates from hybrid search (<100ms), then cross-encoder re-ranks to top-10 (~150ms for 50 candidates on a small model). Total retrieval latency: ~250ms. Answer quality improves substantially for ambiguous queries. Breaks when: documents are updated or added — the old embeddings in the vector DB are stale, and users get answers from outdated content. Fix: document change events (via webhook, Confluence sync, file watcher) trigger an incremental re-ingestion pipeline: chunk → embed → upsert into vector DB with version metadata, replacing the old embedding within 5 minutes.

---

## Why This Is Hard

1. **Retrieval quality determines answer quality**: The LLM can only answer from what you retrieve. If the wrong chunks are retrieved, even a perfect LLM gives a wrong answer. Getting retrieval right (precision AND recall) at millisecond latency is the core engineering problem.
2. **Chunking strategy is non-obvious**: Split too small → individual chunks lose context ("the answer" without "the question"). Split too large → embedding captures too many topics, retrieval becomes unfocused. The optimal chunk size depends on document type.
3. **Semantic vs keyword search**: "What is the refund policy?" should match "Customers may return products within 30 days for a full refund." Keyword search misses the semantic match. But pure semantic search can miss exact terms (product codes, names). Hybrid search (BM25 + dense embeddings) wins in practice.
4. **Freshness vs indexing cost**: New documents must be indexed before they're queryable. Indexing a 1M-document corpus takes hours. Incremental indexing introduces complexity (partial updates, deletion, versioning).
5. **Multi-tenancy**: Tenant A's confidential documents must never appear in Tenant B's queries, even when they share the same vector index infrastructure. Row-level security at the vector DB layer is non-trivial at scale.

---

## Critical Requirements

### Functional
- Ingest documents from multiple sources (PDF upload, Confluence sync, Slack export, GitHub)
- Answer questions in natural language with source citations
- Real-time query answering (< 3 seconds end-to-end)
- Document freshness: new/updated docs queryable within 5 minutes
- Multi-tenant data isolation
- Feedback loop: thumbs up/down on answers → improve retrieval

### Non-Functional
- **Query latency**: P99 < 3 seconds (including LLM generation)
- **Retrieval precision@5**: > 85% (correct chunk in top 5)
- **Index freshness**: < 5 minutes from upload to queryable
- **Scale**: 1M documents, 100K DAU, 10K QPS peak

---

## Scale Estimation

```
Documents: 1M total, avg 10 pages each = 10M pages
Chunks per page: ~5 (avg 200 words/chunk at 500-word chunks with overlap)
Total chunks: 50M chunks

Embedding dimensions: 1536 (text-embedding-ada-002) or 768 (BGE-large)
Storage per embedding: 1536 × 4 bytes (float32) = 6KB per chunk
Total embedding storage: 50M × 6KB = 300GB (vector index)
Plus metadata + document store: ~500GB total

Query QPS: 10K
Embedding API calls: 10K × 1 (query embedding) = 10K/sec
Vector search: 10K × 50M vectors → HNSW: < 10ms
LLM calls: 10K × (3 retrieved chunks × 500 tokens + 50-token query) = 10K × 1,550 tokens
At 70B model, 1,550 tokens: ~200ms generation → feasible

Ingestion rate:
  New docs: 10K/day, avg 10 pages each = 100K pages/day
  100K × 5 chunks = 500K new chunks/day
  500K embeddings/day → ~6 embedding API calls/sec (easily handled)
```

---

## Core Concepts

### 1. Chunking Strategies

```
Fixed-size chunking (naive):
  Split every 500 tokens, 50-token overlap.
  Problem: May split mid-sentence or mid-concept.
  Use when: Structured data, consistent format.

Semantic chunking:
  Detect natural paragraph/section boundaries.
  Split at semantic breaks (headings, blank lines, topic shifts).
  Better context coherence.
  Use when: Prose documents, wikis, PDFs.

Recursive chunking (LangChain RecursiveCharacterTextSplitter):
  Try larger splits first; recursively split oversized chunks.
  Respects document structure (headers → paragraphs → sentences).
  Most practical general-purpose approach.

Document-structure-aware chunking:
  PDFs: Split by page + maintain header context
  Code: Split by function/class (AST-aware)
  Tables: Keep entire table as one chunk (tables don't chunk well)
  Markdown/HTML: Split by heading hierarchy

Chunk metadata (critical for retrieval quality):
  {
    "chunk_id": "doc123_chunk_7",
    "document_id": "doc123",
    "document_title": "Q4 Refund Policy Update",
    "page_number": 3,
    "chunk_text": "Customers may return products within 30 days...",
    "section_header": "Return Policy",
    "created_at": "2026-05-01",
    "tenant_id": "acme_corp"
  }
```

### 2. Embedding Models

```
Proprietary:
  OpenAI text-embedding-ada-002: 1536 dims, excellent quality
  OpenAI text-embedding-3-large: 3072 dims, best quality, $0.13/1M tokens

Open-source (self-hosted):
  BGE-large-en-v1.5 (BAAI): 1024 dims, near-proprietary quality, free
  E5-mistral-7b: Best open-source, 4096 dims, expensive to run
  all-MiniLM-L6-v2: 384 dims, fast/cheap, lower quality

Selection criteria:
  MTEB benchmark score (Massive Text Embedding Benchmark)
  Dimension size (larger = better quality, more storage + latency)
  Inference cost (OpenAI API vs self-hosted GPU)
  License (commercial use allowed?)
```

### 3. Hybrid Search (BM25 + Dense Vectors)

```
BM25 (Sparse, keyword-based):
  "Find me docs with 'GDPR' and 'data retention'"
  Exact term matching, handles jargon and product names
  TF-IDF based, fast (<1ms), in Elasticsearch/OpenSearch

Dense (Semantic, vector-based):
  "What are the rules about keeping customer data?"
  Semantic understanding, handles paraphrase
  HNSW index, 10-50ms, in Pinecone/Weaviate/pgvector

Hybrid Fusion (Reciprocal Rank Fusion):
  Score_final = α × Score_BM25_normalized + (1-α) × Score_dense_normalized
  α tuned per domain (code search → higher BM25 weight; FAQ → higher dense weight)
  
Typical improvement over either alone: 10-15% precision@5
```

### 4. Re-ranking

```
Problem: Top-K retrieval returns the right chunks but in the wrong order.
         Re-ranking uses a more expensive cross-encoder to get precise ordering.

Pipeline:
  1. Retrieve top-50 candidates (fast, ANN search)
  2. Re-rank with cross-encoder → top-5 (slow but runs on 50, not 50M)
  3. Send top-5 to LLM for generation

Cross-encoder models:
  ms-marco-MiniLM-L-6-v2: Fast, good for English
  Cohere Rerank API: Best quality, managed service
  BGE-reranker-large: Open-source, excellent

Latency budget:
  Retrieval (HNSW + BM25): 20-50ms
  Re-ranking (50 chunks): 50-100ms
  LLM generation: 500-2000ms
  Total: ~700ms-2200ms → feasible
```

---

## Database Schema

```sql
-- Tenant and document management
CREATE TABLE tenants (
    tenant_id   VARCHAR(64) PRIMARY KEY,
    name        VARCHAR(255),
    plan        ENUM('starter', 'pro', 'enterprise'),
    created_at  TIMESTAMP
);

CREATE TABLE documents (
    document_id  UUID PRIMARY KEY,
    tenant_id    VARCHAR(64) REFERENCES tenants(tenant_id),
    title        VARCHAR(512),
    source_type  ENUM('pdf', 'confluence', 'slack', 'github', 'web'),
    source_url   TEXT,
    content_hash VARCHAR(64),        -- SHA-256 for dedup and change detection
    status       ENUM('pending', 'processing', 'indexed', 'failed'),
    chunk_count  INT,
    created_at   TIMESTAMP,
    indexed_at   TIMESTAMP,
    INDEX idx_tenant_docs (tenant_id, indexed_at DESC)
);

CREATE TABLE chunks (
    chunk_id     UUID PRIMARY KEY,
    document_id  UUID REFERENCES documents(document_id),
    tenant_id    VARCHAR(64),
    chunk_index  INT,
    content      TEXT,
    token_count  INT,
    page_number  INT,
    section      VARCHAR(255),
    -- embedding_vector stored in vector DB, not here
    created_at   TIMESTAMP,
    INDEX idx_doc_chunks (document_id, chunk_index)
);

-- Query logs for feedback and improvement
CREATE TABLE queries (
    query_id     UUID PRIMARY KEY,
    tenant_id    VARCHAR(64),
    user_id      BIGINT,
    query_text   TEXT,
    retrieved_chunks  JSONB,   -- chunk_ids + scores
    response     TEXT,
    latency_ms   INT,
    feedback     TINYINT,      -- 1=positive, -1=negative, 0=none
    created_at   TIMESTAMP
);
```

---

## Architecture

```
                    INGESTION PIPELINE
┌──────────┐    ┌──────────────────────────────────────────────┐
│ Sources  │    │           Document Processor                  │
│ (PDF/    │───▶│  1. Extract text (PDFPlumber, Tika)           │
│  Conf/   │    │  2. Chunk (recursive, structure-aware)        │
│  Slack)  │    │  3. Generate embeddings (batch API call)      │
└──────────┘    │  4. Upsert to vector DB + metadata DB         │
                │  5. Mark document as indexed                  │
                └──────────────────────────────────────────────┘
                              │
                   ┌──────────▼──────────┐
                   │    Ingestion Queue  │
                   │    (Kafka / SQS)    │
                   └──────────┬──────────┘
                              │
            ┌─────────────────┼─────────────────┐
            ▼                 ▼                 ▼
   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
   │ Doc Worker 1 │  │ Doc Worker 2 │  │ Doc Worker N │
   │ (chunking +  │  │ (chunking +  │  │ (chunking +  │
   │  embedding)  │  │  embedding)  │  │  embedding)  │
   └──────┬───────┘  └──────┬───────┘  └──────┬───────┘
          └─────────────────┼─────────────────┘
                            │
              ┌─────────────┼─────────────┐
              ▼             ▼             ▼
       ┌────────────┐ ┌──────────┐ ┌───────────┐
       │ Vector DB  │ │PostgreSQL│ │Elasticsearch│
       │ (Pinecone  │ │(chunks + │ │(BM25 full- │
       │  /Weaviate │ │ metadata)│ │ text index)│
       │  /pgvector)│ └──────────┘ └───────────┘
       └────────────┘

                    QUERY PIPELINE
┌───────┐   ┌────────────────────────────────────────────────┐
│ User  │──▶│              Query Orchestrator                 │
│       │   │  1. Embed query (same model as ingestion!)      │
└───────┘   │  2. Hybrid search: vector + BM25               │
            │  3. Apply tenant_id filter (data isolation)     │
            │  4. Re-rank top-50 → top-5                     │
            │  5. Build prompt: [context chunks] + [query]    │
            │  6. LLM generation (stream response)            │
            │  7. Log query + retrieved chunks (feedback)     │
            └────────────────────────────────────────────────┘
```

---

## Query Flow (Step-by-Step)

```
User: "What is our company's parental leave policy?"

Step 1: Query Embedding
  embed("What is our company's parental leave policy?")
  → [0.12, -0.45, 0.78, ...] (1536-dimensional vector)

Step 2: Hybrid Search (parallel)
  Vector search: HNSW nearest neighbor in tenant's namespace
    → [chunk_87 (score: 0.92), chunk_134 (score: 0.89), ..., top 50]
  BM25 search: "parental leave policy"
    → [chunk_87 (rank:1), chunk_203 (rank:2), ..., top 50]

Step 3: Reciprocal Rank Fusion
  Merge and score: chunk_87 appears in both → boosted rank
  Final candidates: [chunk_87, chunk_134, chunk_203, ..., top 50 unique]

Step 4: Re-ranking
  Cross-encoder scores: pair(query, each_chunk)
  Final top-5: [chunk_87, chunk_203, chunk_91, chunk_44, chunk_155]

Step 5: Prompt Construction
  System: "Answer based only on the provided context. Cite sources."
  Context:
    [Source: HR Policy Handbook, p.12]
    "Employees are eligible for 16 weeks of paid parental leave after..."
    [Source: Benefits Guide 2026, p.3]
    "Parental leave applies to all full-time employees with > 6 months tenure..."
  User: "What is our company's parental leave policy?"

Step 6: LLM Generation (streaming)
  "Based on the HR Policy Handbook, your company offers **16 weeks of paid
  parental leave** for all full-time employees with more than 6 months of
  tenure [Source: HR Policy Handbook, p.12]. The leave applies to both
  primary and secondary caregivers [Source: Benefits Guide 2026, p.3]."

Step 7: Response with citations
  Return answer + source documents + confidence score
```

---

## Multi-Tenant Data Isolation

```
Problem: 1,000 enterprise tenants share the same vector index infrastructure.
         Tenant A's legal docs must never surface in Tenant B's queries.

Solution 1: Namespace Isolation (Pinecone)
  Each tenant gets a separate vector namespace.
  Queries automatically scoped to tenant's namespace.
  Pros: Simple, native support. Cons: Namespace count limits.

Solution 2: Metadata Filtering
  All chunks stored in shared index with tenant_id metadata field.
  All queries include filter: {tenant_id: "acme_corp"}
  Vector DB filters before ANN search.
  Pros: Simpler infra. Cons: Filter applied post-ANN, some leaked computation.

Solution 3: Dedicated Index per Enterprise (recommended for sensitive data)
  Each enterprise tenant gets a dedicated vector DB cluster.
  Used for customers with strict compliance (finance, healthcare, government).
  Pros: True isolation, independent scaling. Cons: Cost, operational complexity.

Implementation with Weaviate:
  Tenant class with tenant_id field.
  Multi-tenancy mode: each tenant's data physically isolated.
  Query: always include tenant_id in where filter.

Security:
  Application layer: Validate user's tenant_id from JWT before every query
  DB layer: Row-level security on PostgreSQL chunks table (tenant_id = current_tenant)
  Audit log: Every query logged with user_id + tenant_id for compliance
```

---

## Keeping the Index Fresh

```
Challenge: Documents are updated. Chunks must be re-indexed within 5 minutes.

Event-driven indexing pipeline:
  1. Document uploaded / Confluence webhook fires / GitHub push hook
  2. Ingestion Queue receives event
  3. Worker fetches document, checks content_hash against stored hash
     - If hash unchanged: skip (no-op, already indexed)
     - If hash changed: delete old chunks, re-chunk, re-embed, upsert
  4. Document status updated to 'indexed'

Deletion handling:
  - Document deleted → cascade delete all chunks (vector DB + PostgreSQL + Elasticsearch)
  - Soft delete first (mark as deleted), hard delete after propagation confirmed
  - Eventual consistency: < 5 min to disappear from query results

Tombstone race condition:
  Rare: user queries while re-indexing in progress
  Mitigation: Mark document as 'reindexing'; old chunks still served (stale but consistent)
              After new chunks upserted, atomically swap version pointer
```

---

## Failure Scenarios

### Vector DB Overload

```
Symptom: ANN search latency > 500ms
Cause: Too many concurrent searches (10K QPS at 50M vectors is demanding)

Mitigation:
  - Read replicas of vector index (horizontal scaling)
  - Cache frequent queries (semantic cache: same question → cached answer)
  - Reduce top-K from 50 to 20 at high load (slightly lower re-rank quality)
  - Circuit breaker: fall back to BM25-only (no vector search) if vector DB > 1s
```

### Embedding Model API Down

```
Symptom: Ingestion stalled, queries degraded
Cause: OpenAI API outage or rate limits

Mitigation for ingestion:
  - Queue all pending embedding jobs (Kafka retention: 24h)
  - Resume when API recovers
  - Switch to self-hosted backup embedding model (BGE-large) for < 5% quality loss

Mitigation for queries:
  - Use BM25-only retrieval (no semantic understanding, but keeps system up)
  - Alert: "RAG operating in degraded mode — keyword search only"
```

---

## Monitoring

```
Ingestion Pipeline:
  Documents indexed per minute (target: < 5 min lag on new uploads)
  Chunk embedding latency (P99)
  Ingestion error rate (chunking failures, embedding failures)
  Vector DB upsert latency

Query Pipeline:
  End-to-end query latency P50, P99, P999
  Retrieval precision (sampled ground truth evaluation — weekly)
  Re-ranker latency
  LLM TTFT and token throughput
  Cache hit rate (semantic cache)

Business Metrics:
  User feedback score (thumbs up/down ratio — target > 80% positive)
  Zero-result rate (query returns no relevant chunks — target < 5%)
  Hallucination rate (answer not supported by retrieved context — evaluated by LLM judge)
```

---

## Interview Talking Points

**Q: "Why do you need RAG at all? Why not just fine-tune the LLM?"**
> "Fine-tuning bakes knowledge into weights — great for teaching style and behavior, but poor for facts that change (policy updates, new products). You'd need to retrain every time a document changes. RAG keeps knowledge external and updateable in minutes. Fine-tuning and RAG are complementary: fine-tune for response style and behavior, RAG for factual grounding."

**Q: "How do you handle the 'context length problem' where retrieved chunks together exceed the LLM's limit?"**
> "Re-ranking is the first lever — only send the top 5-7 chunks, not top 50. Second, limit chunk size to 300-500 tokens so 5 chunks = 1,500-2,500 tokens, well within any modern context window. For very long documents (legal contracts, technical manuals), hierarchical retrieval: first find the relevant section (coarse retrieval), then retrieve specific passages within that section (fine retrieval). This two-stage approach keeps retrieved context precise and compact."

**Q: "How do you measure and improve retrieval quality?"**
> "We maintain a 'golden dataset' — 500 question-answer pairs with known source chunks. We measure precision@5 (is the right chunk in the top 5?) and nDCG weekly. When precision drops, we investigate: is it chunking (chunks are too large/small?), embedding model (domain mismatch?), or BM25 weight (alpha tuning?). We also collect implicit feedback from user thumbs up/down and use that to fine-tune the re-ranker on domain-specific examples."

---

## Interview Questions Asked

### OpenAI
1. **"Design a RAG pipeline for an enterprise knowledge base with 10M documents"** → Probe: chunking strategy, embedding at scale, retrieval quality, freshness. Hint: chunk with overlap (300-500 tokens, 50-token overlap); embed with domain-adapted model; store in vector DB (Pinecone/Weaviate) + BM25 index; hybrid retrieval + cross-encoder re-ranker; re-index incrementally on document updates.

### Anthropic
1. **"How do you evaluate retrieval quality in a RAG system?"** → Probe: offline metrics vs online feedback, golden dataset, continuous monitoring. Hint: maintain golden Q&A dataset with known source chunks; measure precision@5 and nDCG weekly; online: collect thumbs up/down; use LLM-as-judge to score whether answer is grounded in retrieved context.

### Common Follow-ups
1. **"How do you tune chunk size — what are the trade-offs?"** → Larger chunks: more context per retrieved chunk, fewer chunks to retrieve, but dilutes embedding signal and wastes context window; smaller chunks: precise retrieval, but may lack surrounding context needed to answer; sweet spot 300-500 tokens with 50-token overlap; use recursive character splitting to respect document structure.
2. **"How do you choose an embedding model?"** → Trade-offs: general-purpose (text-embedding-3-large) vs domain-adapted (fine-tuned on your corpus); evaluate on MTEB benchmark for retrieval tasks; domain-specific corpora (legal, medical, code) benefit significantly from fine-tuned embedders; also consider latency and cost — smaller models (384-dim) are much faster at query time.
3. **"How do you re-index when documents are updated?"** → Incremental re-indexing: detect changed documents via webhook or polling; delete old chunks for that doc_id from vector DB; re-chunk and re-embed; upsert new vectors; avoid full re-index; for large-scale updates, batch and throttle embedding calls to avoid rate limits; version embeddings if model changes.
4. **"How do you reduce hallucinations in RAG?"** → Constrain LLM with explicit prompt: "answer only using the provided context; say 'I don't know' if not found"; citation grounding: require model to cite the chunk ID for each claim; post-generation verification: second LLM call checks if answer is supported by retrieved text; low retrieval confidence → fallback to "I couldn't find reliable information."
