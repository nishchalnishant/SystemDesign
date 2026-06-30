> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a Search Engine (Inverted Index) — tests text processing, efficient data structures (`Map<String, List<Document>>`), and ranking algorithms.
>
> **Key concepts:**
> - Core Entities: `Document`, `SearchEngine`, `InvertedIndex`, `Tokenizer`, `Ranker`.
> - The Inverted Index: A `Map<String, List<DocResult>>`. For every word (token), it stores a list of documents that contain the word, along with the term frequency.
> - Tokenization (Strategy Pattern): Before indexing, text must be processed: lowercase, remove punctuation, remove stop words ("the", "is"), and stem ("running" -> "run").
> - Ranking (Strategy Pattern): When querying, you retrieve the lists for each word. How do you sort them? Usually via TF-IDF (Term Frequency - Inverse Document Frequency) or PageRank.
> - Set Intersection: If a user searches "fast car", you must fetch the list for "fast" and the list for "car", and efficiently find the intersection of the two lists.
>
> **Key takeaway:** The heart of this problem is the Inverted Index data structure and the Set Intersection algorithm for multi-word queries.

---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, search-engine, inverted-index, tfidf, tokenization]
---
# Design Search Engine (Inverted Index)

> **Difficulty**: Hard
> **Asked at**: Amazon, Google, LinkedIn
> **Key Patterns**: Inverted Index, TF-IDF ranking, Strategy (tokenizer), Iterator (result pagination)

---

## Understanding the Problem

Design a simple search engine that indexes text documents, processes queries, and returns ranked results using TF-IDF scoring.

---

## Clarifying Questions

**You**: "What's the primary data structure — inverted index?"
**Interviewer**: "Yes, inverted index mapping term → list of (document_id, positions)."

**You**: "What ranking algorithm — BM25, TF-IDF, or simple term frequency?"
**Interviewer**: "TF-IDF is fine for this exercise."

**You**: "Do we support boolean queries (AND/OR/NOT) or just keyword search?"
**Interviewer**: "Keyword search with relevance ranking."

**You**: "What about phrase matching or stemming?"
**Interviewer**: "Mention them as extensions — basic tokenization (lowercase, strip punctuation) is enough."

**You**: "Do we need to support document updates?"
**Interviewer**: "Yes — re-index on update."

---

## Final Requirements

**In scope:**
1. Add / update / delete documents
2. Index documents using inverted index (term → postings list)
3. Search by keyword(s); return ranked results by TF-IDF score
4. Basic tokenization: lowercase, remove punctuation, split on whitespace

**Out of scope:**
- Stemming / lemmatization (follow-up)
- Boolean query parser
- Persistent index (in-memory only)
- Distributed indexing

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|---------------|
| `Document` | doc_id, title, content, word_count |
| `InvertedIndex` | term → PostingsList (doc_id, positions, tf) |
| `Posting` | doc_id, term_frequency, positions in document |
| `SearchEngine` | Orchestrates index, search, ranking |
| `Tokenizer` | Splits and normalizes text into terms |
| `TFIDFRanker` | Scores documents per query term using TF-IDF |
| `SearchResult` | doc_id, title, score, snippet |

`SearchEngine` owns the `InvertedIndex`. On query, it fetches postings for each term, computes TF-IDF per (term, doc) pair, aggregates score per doc, returns sorted results.

---

## Class Design

### Document

```
class Document:
- doc_id: str
- title: str
- content: str
- word_count: int  # computed on index
```

### Posting

```
class Posting:
- doc_id: str
- term_frequency: int       # count of term in this document
- positions: list[int]      # word offsets for phrase matching
```

### InvertedIndex

```
class InvertedIndex:
- index: dict[str, list[Posting]]   # term → postings
- doc_count: int
- doc_lengths: dict[str, int]       # doc_id → total word count

+ add_posting(term, posting: Posting)
+ get_postings(term) -> list[Posting]
+ remove_doc(doc_id)
+ document_frequency(term) -> int
```

### SearchEngine

```
class SearchEngine:
- index: InvertedIndex
- documents: dict[str, Document]
- tokenizer: Tokenizer

+ index_document(doc: Document)
+ update_document(doc_id, title, content)
+ delete_document(doc_id)
+ search(query: str, top_k=10) -> list[SearchResult]
```

---

## Implementation

### Core Method: `index_document`

**Core logic:**
1. Tokenize document content
2. For each token, track position and count (term frequency)
3. Update inverted index: add/update posting for this doc_id
4. Store document in doc registry

**Edge cases:**
- Re-indexing existing doc: remove old postings first
- Empty content → index with no terms (still stored)

```python
def index_document(self, doc):
    if doc.doc_id in self.documents:
        self.index.remove_doc(doc.doc_id)

    tokens = self.tokenizer.tokenize(doc.content)
    doc.word_count = len(tokens)

    term_positions = defaultdict(list)
    for pos, token in enumerate(tokens):
        term_positions[token].append(pos)

    for term, positions in term_positions.items():
        posting = Posting(
            doc_id=doc.doc_id,
            term_frequency=len(positions),
            positions=positions
        )
        self.index.add_posting(term, posting)

    self.documents[doc.doc_id] = doc
    self.index.doc_count = len(self.documents)
```

### Tokenizer

```python
class Tokenizer:
    STOP_WORDS = {'the', 'is', 'a', 'an', 'in', 'of', 'and', 'or', 'to', 'for'}

    def tokenize(self, text):
        text = text.lower()
        text = re.sub(r'[^\w\s]', '', text)
        tokens = text.split()
        return [t for t in tokens if t not in self.STOP_WORDS]
```

### Core Method: `search` with TF-IDF

**Core logic:**
1. Tokenize query
2. For each query term, get postings list
3. For each document in postings, compute TF-IDF contribution
4. Aggregate scores per document
5. Return top-K sorted by score

**TF-IDF formula:**
- `TF(t, d)` = term_frequency / doc_word_count  (normalized)
- `IDF(t)` = log((N + 1) / (df + 1)) + 1  (smoothed)
- `score(d, q)` = sum of TF × IDF for each query term in d

```python
def search(self, query, top_k=10):
    query_terms = self.tokenizer.tokenize(query)
    if not query_terms:
        return []

    scores = defaultdict(float)
    N = len(self.documents)

    for term in query_terms:
        postings = self.index.get_postings(term)
        if not postings:
            continue
        df = len(postings)
        idf = math.log((N + 1) / (df + 1)) + 1

        for posting in postings:
            doc = self.documents[posting.doc_id]
            tf = posting.term_frequency / doc.word_count
            scores[posting.doc_id] += tf * idf

    ranked = sorted(scores.items(), key=lambda x: x[1], reverse=True)
    results = []
    for doc_id, score in ranked[:top_k]:
        doc = self.documents[doc_id]
        results.append(SearchResult(
            doc_id=doc_id,
            title=doc.title,
            score=round(score, 4),
            snippet=self._generate_snippet(doc, query_terms)
        ))
    return results
```

### InvertedIndex internals

```python
class InvertedIndex:
    def __init__(self):
        self.index = defaultdict(list)
        self.doc_count = 0

    def add_posting(self, term, posting):
        self.index[term].append(posting)

    def get_postings(self, term):
        return self.index.get(term, [])

    def remove_doc(self, doc_id):
        for term in list(self.index.keys()):
            self.index[term] = [
                p for p in self.index[term] if p.doc_id != doc_id
            ]
            if not self.index[term]:
                del self.index[term]

    def document_frequency(self, term):
        return len(self.index.get(term, []))
```

---

## Verification

```
Documents:
  D1: "Python is great for data science"  → tokens: [python, great, data, science]
  D2: "Data science with Python"          → tokens: [data, science, python]
  D3: "Java is great for backend"         → tokens: [java, great, backend]

Inverted index:
  "python"  → [Posting(D1, tf=1), Posting(D2, tf=1)]
  "great"   → [Posting(D1, tf=1), Posting(D3, tf=1)]
  "data"    → [Posting(D1, tf=1), Posting(D2, tf=1)]
  "science" → [Posting(D1, tf=1), Posting(D2, tf=1)]

Query: "python data science" → terms: [python, data, science]
N=3

  term="python": df=2, idf=log(4/3)+1≈1.29
    D1: tf=1/4=0.25, score+=0.32
    D2: tf=1/3=0.33, score+=0.43

  term="data": df=2, idf≈1.29
    D1: score→0.64, D2: score→0.86

  term="science": df=2, idf≈1.29
    D1: score→0.96, D2: score→1.29

Ranking: D2(1.29) > D1(0.96) > D3(0)
```

---

## Deep Dive & Extensibility

### 1. "How would you add stemming?"

Add a `Stemmer` stage to the Tokenizer pipeline (Strategy):

```python
class Tokenizer:
    def __init__(self, stemmer=None):
        self.stemmer = stemmer or NullStemmer()

    def tokenize(self, text):
        tokens = self._basic_tokenize(text)
        return [self.stemmer.stem(t) for t in tokens]

class PorterStemmer:
    def stem(self, word):
        # Porter algorithm strips suffixes: "running" → "run"
        return stemmed_word
```

Index and query use the same stemmer — "running" and "run" map to the same posting.

### 2. "How would you support boolean AND queries?"

Parse query into terms, then intersect postings lists:

```python
def search_and(self, query):
    terms = self.tokenizer.tokenize(query)
    if not terms:
        return []
    postings = [set(p.doc_id for p in self.index.get_postings(t)) for t in terms]
    postings.sort(key=len)  # intersect smallest first
    result_docs = postings[0]
    for p in postings[1:]:
        result_docs &= p
    return self._rank_and_return(result_docs, terms)
```

Intersecting from the shortest list minimizes the work.

### 3. "How would you generate snippets?"

Find the text window around the first query term occurrence:

```python
def _generate_snippet(self, doc, query_terms, window=50):
    content = doc.content.lower()
    for term in query_terms:
        idx = content.find(term)
        if idx != -1:
            start = max(0, idx - window)
            end = min(len(content), idx + len(term) + window)
            return f"...{doc.content[start:end]}..."
    return doc.content[:100] + "..."
```

### 4. "How would you handle document updates efficiently?"

Current approach: remove all old postings for doc_id, then re-index (O(terms in index)). For large indices, use tombstone markers — mark old postings as deleted, compact periodically (like Lucene segment merges). This allows O(1) "delete" and deferred cleanup.

### 5. "How would you scale to millions of documents?"

Shard the inverted index by term across multiple nodes (hash(term) % num_shards). Each query fans out to all shards in parallel, results are merged and re-ranked. Use compressed postings lists (delta-encoded doc IDs) to reduce memory. Cache hot query results in Redis.

---

## Interviewer Questions by Level

**Junior**: Build inverted index from list of documents. Return list of doc_ids containing a given word.

**Mid-level**: TF-IDF scoring. Multi-term query aggregation. Basic tokenization (lowercase, punctuation removal, stop words). Document update/delete.

**Senior**: Stemming as pluggable Strategy. Boolean AND via postings intersection (shortest-first). Snippet generation. Tombstone-based incremental updates. Distributed sharding by term.

---

## Common Interview Questions

- **Q**: What is an inverted index?
  **A**: A map from each term to the list of documents (postings) containing it. Core data structure of every search engine — enables O(1) lookup of which documents contain a term, instead of scanning all document content linearly.

- **Q**: What does TF-IDF measure?
  **A**: TF (Term Frequency) = how often a term appears in this document (normalized by length). IDF (Inverse Document Frequency) = how rare the term is across all documents. Their product rewards documents with frequent, rare terms — unique terms signal strong relevance.

- **Q**: Why normalize TF by document length?
  **A**: Without normalization, longer documents get artificially higher scores just by having more words. Dividing term count by total word count levels the playing field.

- **Q**: How do you handle stop words?
  **A**: Remove them during tokenization. Words like "the", "is", "a" have IDF ≈ 0 (appear in nearly all documents), contributing nothing to ranking — filtering saves space and speeds up lookup.

- **Q**: What's the time complexity of search?
  **A**: O(Q × D) where Q = query terms and D = average postings list length per term. For top-K, use a min-heap of size K → O(Q × D × log K). In practice, postings lists are sorted by score so top-K emerges early.
