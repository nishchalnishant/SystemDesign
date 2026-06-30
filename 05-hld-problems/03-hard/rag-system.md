> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a RAG (Retrieval-Augmented Generation) system — grounding LLM responses in a document corpus via semantic search, embedding vectors, and prompt construction.
>
> **Key design decisions:**
> - Ingestion pipeline: documents → chunking (512–1024 tokens, overlap 50 tokens) → embedding model → vector embeddings → vector DB (Pinecone/Weaviate/pgvector)
> - Chunking strategy: fixed-size chunks vs semantic chunks (sentence-boundary aware); overlap prevents context loss at chunk boundaries
> - Retrieval: user query → embed query → ANN search in vector DB (HNSW index) → top-K semantically similar chunks → re-rank with cross-encoder
> - Hybrid search: semantic (dense vector) + keyword (BM25) → combine scores with RRF (Reciprocal Rank Fusion); catches both semantic and exact matches
> - Prompt construction: system prompt + retrieved chunks + user query → sent to LLM; chunk count limited by context window (e.g., 5 chunks × 512 tokens)
> - Citation: each chunk tagged with source document + page; LLM instructed to cite sources; UI displays reference links
> - Freshness: incremental ingestion for new documents; chunk-level deduplication by content hash; re-embed on document update
>
> **Key takeaway:** HNSW vector search + re-ranking (cross-encoder) is the retrieval backbone — ANN gives recall, re-ranking gives precision; hybrid BM25+vector catches cases where semantic search misses exact keyword matches.

---
module: 05-hld-problems
topic: Hard
status: unread
tags: [05-hld-problems, system-design, hard, rag, vector-db, embeddings, hnsw, semantic-search]
---
# Design a RAG System (Retrieval-Augmented Generation)

> **Difficulty**: Hard | **Asked at**: OpenAI, Anthropic, Google, Cohere, Amazon

---

## Problem Statement

Design a Retrieval-Augmented Generation (RAG) system that allows an LLM to answer questions by retrieving relevant context from a large document corpus. Users ask natural language questions; the system retrieves semantically relevant document chunks, injects them into the LLM prompt, and returns a grounded answer with citations.

---

## Functional Requirements

1. **Document ingestion**: Upload documents (PDF, Word, web pages, code); index for retrieval
2. **Semantic search**: Given a query, retrieve top-K most semantically similar document chunks
3. **Answer generation**: Inject retrieved chunks as context into an LLM prompt; return answer with source citations
4. **Multi-tenant**: Different organizations have isolated document corpora
5. **Re-ranking**: Apply a cross-encoder re-ranker to improve precision of retrieved chunks
6. **Hybrid retrieval**: Combine dense (embedding) search with sparse (BM25 keyword) search

---

## Non-Functional Requirements

- **Scale**: 100M document chunks per tenant; 10M queries/day; 1,000 TPS retrieval
- **Latency**: End-to-end (retrieval + LLM generation) < 5s; retrieval alone < 200ms
- **Relevance**: Retrieved chunks must be semantically relevant (measured by MRR, NDCG)
- **Freshness**: New documents indexed and searchable within 30 seconds
- **Isolation**: Cross-tenant data leakage is a critical failure

---

## Core Entities

| Entity | Key Fields |
|--------|-----------|
| `Document` | doc_id, tenant_id, title, source_url, mime_type, created_at, metadata |
| `Chunk` | chunk_id, doc_id, tenant_id, text, chunk_index, token_count, embedding_model |
| `Embedding` | chunk_id, vector (float32[1536]), model_version, created_at |
| `Query` | query_id, tenant_id, user_id, text, retrieved_chunk_ids[], llm_response, latency_ms |

---

## API Design

```http
POST /api/v1/documents
Body: { "title": "Q3 Earnings Report", "content": "<text or base64 PDF>", "metadata": {"source": "internal"} }
Response 202: { "doc_id": "d123", "status": "processing", "chunk_count": 0 }

GET /api/v1/documents/{doc_id}/status
Response 200: { "doc_id": "d123", "status": "indexed", "chunk_count": 48 }

POST /api/v1/query
Body: {
  "question": "What was the revenue growth in Q3 2026?",
  "top_k": 5,
  "hybrid_weight": 0.7  # 0=pure BM25, 1=pure embedding
}
Response 200: {
  "answer": "Revenue grew 23% YoY in Q3 2026...",
  "citations": [{ "chunk_id": "c456", "doc_title": "Q3 Earnings Report", "text": "..." }]
}

DELETE /api/v1/documents/{doc_id}
Response 200: { "deleted_chunks": 48 }
```

---

## High-Level Design

```
User
  │ POST /query
  ▼
Query Service
  │ 1. Embed query: call Embedding Model → query_vector (1536-dim float32)
  │ 2. Dense retrieval: ANN search in Vector DB (top-K by cosine similarity)
  │ 3. Sparse retrieval: BM25 search in Elasticsearch (keyword match)
  │ 4. Fusion: Reciprocal Rank Fusion → merged top-K chunks
  │ 5. Re-rank: cross-encoder model scores each (query, chunk) pair → re-ordered
  │ 6. Build prompt: system_prompt + retrieved chunks + user question
  │ 7. Call LLM API → stream response
  │ 8. Return answer + citations
  ▼
Ingestion Pipeline (async, for POST /documents):
  Document → Text Extraction → Chunking → Embedding Model → Vector DB + Elasticsearch

Storage:
  Vector DB (Pinecone / Weaviate / pgvector): embeddings for ANN search
  Elasticsearch: BM25 full-text index
  PostgreSQL: document metadata, chunk text, query logs
  S3: raw document files
```

---

## Deep Dive 1: Chunking Strategies

**Problem**: A 100-page PDF must be split into chunks for embedding. The chunk boundaries determine retrieval quality — too small and a chunk lacks context; too large and it dilutes the embedding's specificity.

**Fixed-size chunking** (naive):
```python
def fixed_chunks(text, size=512, overlap=50):
    words = text.split()
    chunks = []
    for i in range(0, len(words), size - overlap):
        chunks.append(" ".join(words[i:i+size]))
    return chunks
```
Simple. `overlap=50` words prevents context loss at boundaries. Problem: splits mid-sentence, mid-paragraph.

**Sentence/paragraph chunking** (better):
- Split on paragraph boundaries (`\n\n`)
- If a paragraph > max_tokens, split at sentence boundaries
- Merge short paragraphs up to max_tokens

**Recursive character text splitting** (LangChain default):
- Try to split on `\n\n` first → then `\n` → then `.` → then ` ` → characters
- Ensures chunks respect semantic boundaries as much as possible

**Semantic chunking** (state of the art):
- Embed each sentence
- Compute cosine similarity between consecutive sentences
- Create chunk boundary where similarity drops sharply (topic shift)
- Most expensive but best quality

**Chunk metadata enrichment**: Prepend document title, section heading, and page number to each chunk text before embedding. Retrieval is more precise when the chunk contains its own context.

---

## Deep Dive 2: ANN Search with HNSW

**Problem**: 100M embeddings, 1,536 dimensions each. Exact nearest-neighbor search requires computing cosine similarity against all 100M vectors — 153.6B multiplications per query. At 1,000 TPS, infeasible.

**Approximate Nearest Neighbor (ANN) with HNSW** (Hierarchical Navigable Small World):

**Structure**: A multi-layer graph. Bottom layer contains all 100M nodes. Each higher layer is a random subset (~1/e fewer nodes). Edges connect nodes that are close in embedding space.

**Search**:
1. Enter at the top layer; greedily traverse edges toward the query vector
2. Found nearest neighbor at top layer → use as entry point for next layer down
3. Repeat until bottom layer; at bottom layer, return top-K closest nodes

**Build**:
```
For each new node:
  1. Assign to layers up to level = floor(-ln(random()) * mL)
  2. At each layer: greedily navigate to nearest existing node → connect (max efConstruction=200 candidates checked)
  3. Prune long-range edges if node has too many connections (max_M=32 per node)
```

**Parameters**:
- `M=32`: max edges per node. Higher → better recall, more memory
- `ef_construction=200`: candidates during build. Higher → better graph quality, slower build
- `ef_search=100`: candidates during query. Higher → better recall, more latency

**Performance**: 100M vectors, 1536-dim, HNSW → ~5ms query latency, 95% recall@10. Compare to brute-force: 100s per query.

**Tenant isolation**: Each tenant gets a separate HNSW index namespace. Cross-tenant ANN search never occurs.

---

## Deep Dive 3: Hybrid Retrieval and Re-ranking

**Problem**: Pure embedding search misses exact keyword matches ("GPT-4o" matches "GPT4o" semantically but not via BM25). Pure BM25 misses semantic matches ("car" vs "automobile"). How do you combine both?

**BM25 (sparse retrieval)**: Term frequency + inverse document frequency scoring. Finds exact and near-exact keyword matches. Implemented in Elasticsearch.

**Dense retrieval**: Embedding cosine similarity. Finds semantically similar chunks even with different vocabulary.

**Reciprocal Rank Fusion (RRF)**:
```python
def rrf_score(rank, k=60):
    return 1.0 / (k + rank)

def fuse(dense_results, sparse_results, top_k=20):
    scores = defaultdict(float)
    for rank, chunk_id in enumerate(dense_results):
        scores[chunk_id] += rrf_score(rank)
    for rank, chunk_id in enumerate(sparse_results):
        scores[chunk_id] += rrf_score(rank)
    return sorted(scores.keys(), key=lambda c: -scores[c])[:top_k]
```
No tuning of relative weights needed; ranks are combined directly.

**Cross-encoder re-ranking**: After fusing to 20 candidates, a cross-encoder model scores each (query, chunk) pair jointly:
- Bi-encoder (used for retrieval): query and chunk embedded separately → fast but approximate
- Cross-encoder (used for re-ranking): query and chunk concatenated as one input to a transformer → slow but accurate
- Re-rank top-5 from 20 candidates: 20 cross-encoder calls × 50ms each = 1s (acceptable within 5s budget)

---

## Interviewer Questions by Level

**Junior**:
- What is RAG? Why do we retrieve documents instead of just asking the LLM directly?
- What is an embedding? Why do similar sentences have similar embeddings?
- What is chunking? Why can't you just embed entire documents?

**Mid-level**:
- What is ANN search? Why is exact nearest-neighbor search not feasible for 100M vectors?
- What is the difference between BM25 and embedding-based retrieval? When does each fail?
- How does Reciprocal Rank Fusion combine results from sparse and dense retrieval?

**Senior**:
- Explain HNSW — how is the graph built, how does search work, and what trade-offs do `M` and `ef_search` control?
- Design the ingestion pipeline for real-time document updates — a document is edited; how do you re-index only the changed chunks without re-embedding the entire document?
- Design multi-tenant RAG — how do you ensure tenant A can never retrieve documents from tenant B's corpus?
- How do you evaluate RAG system quality? What metrics (MRR, NDCG, faithfulness, groundedness) do you use?
