> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a Search Engine (Inverted Index) — tests text processing, efficient data structures (`Map<String, List<Posting>>`), and ranking algorithms.
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

```java
public void indexDocument(Document doc) {
    if (documents.containsKey(doc.getDocId())) {
        index.removeDoc(doc.getDocId());
    }

    List<String> tokens = tokenizer.tokenize(doc.getContent());
    doc.setWordCount(tokens.size());

    Map<String, List<Integer>> termPositions = new HashMap<>();
    for (int pos = 0; pos < tokens.size(); pos++) {
        termPositions.computeIfAbsent(tokens.get(pos), k -> new ArrayList<>()).add(pos);
    }

    for (Map.Entry<String, List<Integer>> entry : termPositions.entrySet()) {
        String term = entry.getKey();
        List<Integer> positions = entry.getValue();
        Posting posting = new Posting(doc.getDocId(), positions.size(), positions);
        index.addPosting(term, posting);
    }

    documents.put(doc.getDocId(), doc);
    index.setDocCount(documents.size());
}
```

### Tokenizer

```java
public class Tokenizer {
    private static final Set<String> STOP_WORDS = Set.of(
        "the", "is", "a", "an", "in", "of", "and", "or", "to", "for"
    );

    public List<String> tokenize(String text) {
        String lower = text.toLowerCase();
        String stripped = lower.replaceAll("[^\\w\\s]", "");
        String[] tokens = stripped.trim().split("\\s+");

        List<String> result = new ArrayList<>();
        for (String t : tokens) {
            if (!t.isEmpty() && !STOP_WORDS.contains(t)) {
                result.add(t);
            }
        }
        return result;
    }
}
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

```java
public List<SearchResult> search(String query, int topK) {
    List<String> queryTerms = tokenizer.tokenize(query);
    if (queryTerms.isEmpty()) {
        return Collections.emptyList();
    }

    Map<String, Double> scores = new HashMap<>();
    int N = documents.size();

    for (String term : queryTerms) {
        List<Posting> postings = index.getPostings(term);
        if (postings.isEmpty()) {
            continue;
        }
        int df = postings.size();
        double idf = Math.log((double) (N + 1) / (df + 1)) + 1;

        for (Posting posting : postings) {
            Document doc = documents.get(posting.getDocId());
            double tf = (double) posting.getTermFrequency() / doc.getWordCount();
            scores.merge(posting.getDocId(), tf * idf, Double::sum);
        }
    }

    List<Map.Entry<String, Double>> ranked = new ArrayList<>(scores.entrySet());
    ranked.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

    List<SearchResult> results = new ArrayList<>();
    for (Map.Entry<String, Double> entry : ranked.subList(0, Math.min(topK, ranked.size()))) {
        String docId = entry.getKey();
        double score = entry.getValue();
        Document doc = documents.get(docId);
        double rounded = Math.round(score * 10000.0) / 10000.0;
        results.add(new SearchResult(
            docId,
            doc.getTitle(),
            rounded,
            generateSnippet(doc, queryTerms)
        ));
    }
    return results;
}
```

### InvertedIndex internals

```java
public class InvertedIndex {
    private final Map<String, List<Posting>> index = new HashMap<>();
    private int docCount = 0;

    public void addPosting(String term, Posting posting) {
        index.computeIfAbsent(term, k -> new ArrayList<>()).add(posting);
    }

    public List<Posting> getPostings(String term) {
        return index.getOrDefault(term, Collections.emptyList());
    }

    public void removeDoc(String docId) {
        Iterator<Map.Entry<String, List<Posting>>> it = index.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, List<Posting>> entry = it.next();
            entry.getValue().removeIf(p -> p.getDocId().equals(docId));
            if (entry.getValue().isEmpty()) {
                it.remove();
            }
        }
    }

    public int documentFrequency(String term) {
        return index.getOrDefault(term, Collections.emptyList()).size();
    }

    public int getDocCount() {
        return docCount;
    }

    public void setDocCount(int docCount) {
        this.docCount = docCount;
    }
}
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

```java
public interface Stemmer {
    String stem(String word);
}

public class NullStemmer implements Stemmer {
    @Override
    public String stem(String word) {
        return word;
    }
}

public class Tokenizer {
    private final Stemmer stemmer;

    public Tokenizer(Stemmer stemmer) {
        this.stemmer = stemmer != null ? stemmer : new NullStemmer();
    }

    public List<String> tokenize(String text) {
        List<String> tokens = basicTokenize(text);
        List<String> stemmed = new ArrayList<>();
        for (String t : tokens) {
            stemmed.add(stemmer.stem(t));
        }
        return stemmed;
    }

    private List<String> basicTokenize(String text) {
        // lowercase, strip punctuation, split on whitespace
        return Arrays.asList(text.toLowerCase().replaceAll("[^\\w\\s]", "").trim().split("\\s+"));
    }
}

public class PorterStemmer implements Stemmer {
    @Override
    public String stem(String word) {
        // Porter algorithm strips suffixes: "running" -> "run"
        return stemmedWord;
    }
}
```

Index and query use the same stemmer — "running" and "run" map to the same posting.

### 2. "How would you support boolean AND queries?"

Parse query into terms, then intersect postings lists:

```java
public List<SearchResult> searchAnd(String query) {
    List<String> terms = tokenizer.tokenize(query);
    if (terms.isEmpty()) {
        return Collections.emptyList();
    }

    List<Set<String>> postingSets = new ArrayList<>();
    for (String t : terms) {
        Set<String> docIds = new HashSet<>();
        for (Posting p : index.getPostings(t)) {
            docIds.add(p.getDocId());
        }
        postingSets.add(docIds);
    }
    postingSets.sort(Comparator.comparingInt(Set::size)); // intersect smallest first

    Set<String> resultDocs = new HashSet<>(postingSets.get(0));
    for (Set<String> s : postingSets.subList(1, postingSets.size())) {
        resultDocs.retainAll(s);
    }
    return rankAndReturn(resultDocs, terms);
}
```

Intersecting from the shortest list minimizes the work.

### 3. "How would you generate snippets?"

Find the text window around the first query term occurrence:

```java
private String generateSnippet(Document doc, List<String> queryTerms) {
    return generateSnippet(doc, queryTerms, 50);
}

private String generateSnippet(Document doc, List<String> queryTerms, int window) {
    String content = doc.getContent().toLowerCase();
    for (String term : queryTerms) {
        int idx = content.indexOf(term);
        if (idx != -1) {
            int start = Math.max(0, idx - window);
            int end = Math.min(doc.getContent().length(), idx + term.length() + window);
            return "..." + doc.getContent().substring(start, end) + "...";
        }
    }
    int end = Math.min(100, doc.getContent().length());
    return doc.getContent().substring(0, end) + "...";
}
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

---

## Related

**Patterns applied here**

- [Iterator Pattern](../../03-design-patterns/03-behavioral/iterator-pattern.md)
- [Strategy Pattern](../../03-design-patterns/03-behavioral/strategy-pattern.md)
- [Interpreter Pattern](../../03-design-patterns/03-behavioral/interpreter-pattern.md) — parse boolean query grammar (`term AND (a OR b)`) into an evaluable tree

**SOLID focus**: [Interface Segregation](../../02-solid-principles/04-interface-segregation.md) · [Dependency Inversion](../../02-solid-principles/05-dependency-inversion.md)

**Concurrency**: [Producer-Consumer](../../04-concurrency/producer-consumer.md)

**Practice next**

- [Design S3 Object Storage](26-design-s3-object-storage.md)
- [Design Text Editor](31-design-text-editor.md)

Tokenisation and indexing appear in both.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../01-oop-fundamentals/uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
