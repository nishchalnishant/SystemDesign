> [!NOTE]
> ** 5-Minute Summary**
>
> **What this covers:** How websites let you search for "black running shoes" and instantly find the exact product out of 10 million items.
>
> **Key topics:**
> - **The Problem:** Standard databases are terrible at searching paragraphs of text. If you use a SQL `LIKE '%running%'` query, it has to scan every single word in the database. It takes minutes.
> - **The Inverted Index:** Instead of mapping "Document -> Words", Elasticsearch maps "Word -> Documents". (Like the Glossary at the back of a textbook).
> - **Tokenization:** Before saving a document, Elasticsearch rips it apart. It removes punctuation, makes everything lowercase, and converts words to their root (e.g., "Running" becomes "run").
> - **Apache Lucene:** The actual core engine that does the searching. Elasticsearch is just a giant wrapper around Lucene that allows it to scale across 100 servers.
> - **Shards:** Chopping the Inverted Index into pieces so multiple servers can search at the exact same time.
>
> **Key takeaway:** Elasticsearch isn't really a database. It is a highly specialized search engine. You should use a normal database (like PostgreSQL) to store your real data, and copy it to Elasticsearch purely for the search bar.

---
module: 04-advanced-topics
status: unread
tags: [04-advanced-topics, system-design, internals, search, nosql]
---
# Elasticsearch Internals - System Design Guide

> This guide explains the internal magic of Elasticsearch using simple analogies.

---

## Why Should I Care?

Imagine a library with 1 million books. A patron walks in and says, "I need to find a book that mentions a 'Quick Brown Fox' in chapter 3."
If you use a SQL database, the librarian has to open all 1 million books and read every single page. This is called a Full Table Scan. It is unbelievably slow.

If you use **Elasticsearch**, the librarian walks directly to a massive Glossary on the wall.
They look up "Quick" (found in Books 1 and 4).
They look up "Brown" (found in Books 1 and 9).
They look up "Fox" (found in Books 1 and 2).

The librarian does a quick mental intersection: Book 1 is the only book that has all 3 words! They fetch Book 1 instantly.
This is why Elasticsearch is used to power the search bar on almost every major website in the world (Wikipedia, GitHub, Uber).

---

## The Inverted Index (The Glossary)

The magic behind Elasticsearch is the **Inverted Index**.

A normal database maps an ID to the text:
- `ID 1` -> "The quick brown fox"
- `ID 2` -> "The lazy dog"

An Inverted Index flips it backwards:
- `quick` -> [ID 1]
- `brown` -> [ID 1]
- `fox` -> [ID 1]
- `lazy` -> [ID 2]
- `dog` -> [ID 2]

When a user searches for "lazy fox", the computer doesn't read any text. It just looks up the two arrays in the Index, and merges them: `[ID 1, ID 2]`. It is pure, instant math.

---

## Tokenization (The Woodchipper)

If the document says "The foxes are running," and the user types "fox run", how does it match?

Before Elasticsearch saves a document into the Inverted Index, it throws the text into an Analyzer (A Woodchipper).

1. **Character Filter:** Strips out HTML tags. (Turns `<b>Foxes</b>` into `Foxes`).
2. **Tokenizer:** Chops the sentence into individual words based on spaces.
3. **Token Filter (Lowercasing):** Turns `Foxes` into `foxes`.
4. **Token Filter (Stop Words):** Throws away useless words like "the", "and", "is".
5. **Token Filter (Stemming):** Chops words down to their root dictionary form. `foxes` becomes `fox`. `running` becomes `run`.

*After* the woodchipper is finished, it puts the root words (`fox`, `run`) into the Inverted Index. When the user searches, their search query goes through the exact same woodchipper!

---

## Shards & Apache Lucene

Elasticsearch doesn't actually do the searching. The searching is done by a much older, highly optimized Java library called **Apache Lucene**.

The problem with Lucene is that it only works on one single computer.
**Elasticsearch** is essentially a giant wrapper around Lucene that allows it to work on 100 computers at once.

> ** Analogy:**
> - **Lucene** is a really smart librarian who can search an Index incredibly fast.
> - **Elasticsearch** is the Manager who hires 10 Lucene librarians, chops the massive Index into 10 smaller books (called **Shards**), gives one book to each librarian, and coordinates them.

When you search for "Shoes", Elasticsearch asks all 10 librarians to search their specific Shard at the exact same time, and then merges their 10 answers into one final list for the user.

---

## Interview Questions to Practice

1. **"Why use Elasticsearch instead of just using a SQL `LIKE '%keyword%'` query?"**
   *Answer:* A SQL `LIKE` query with a leading wildcard cannot use a standard B-Tree index, forcing the database to perform a Full Table Scan (checking every row, string-matching character by character), which is incredibly slow at scale. Elasticsearch uses an Inverted Index, making full-text search as fast as an `O(1)` dictionary lookup and an array intersection.
2. **"What is the role of an Analyzer (Tokenization/Stemming) in Elasticsearch?"**
   *Answer:* It transforms raw text into standardized tokens before placing them in the Inverted Index. By lowercasing, removing stop words, and stemming words to their root (e.g., turning "jumped" into "jump"), it ensures that a user searching for "jump" will successfully match a document containing the word "jumped."
3. **"What is the relationship between Elasticsearch, Lucene, and Shards?"**
   *Answer:* Apache Lucene is the underlying search library that actually builds the Inverted Indexes and executes the queries on a single machine. Elasticsearch is a distributed wrapper around Lucene. It divides your data into logical 'Shards', where each Shard is a fully functional, independent Lucene instance, allowing the search workload to be distributed across multiple physical servers.

---

# 🎯 SDE-3 Deep Dive

Inverted index + tokenization + shards is the mechanism. Seniors get asked about **segments and the near-real-time refresh model, scoring (BM25), the shard/replica architecture and why you can't reshard, and the operational failure modes that make Elasticsearch notoriously hard to run.**

## Segments, refresh, and "near real time" (not real time)

A Lucene shard isn't one file — it's a set of **immutable segments**. New docs land in an in-memory buffer and only become searchable when a **refresh** flushes the buffer into a new segment (default **every 1 second**). This is why Elasticsearch is **near-real-time**: a just-indexed doc isn't searchable for up to ~1s.

- Because segments are **immutable**, a "delete" only marks a tombstone and an "update" is a **delete + re-index** — the old doc lingers until **merge**. Segment **merging** compacts many small segments into fewer large ones (LSM-like) and reclaims deleted-doc space — a background I/O cost, same family as compaction in [`02-database-internals`](../../03-scaling/02-database-internals.md).
- Durability is the **translog** (write-ahead log): a doc is written to the translog before the next `fsync`/**flush**, so a crash between refreshes doesn't lose data. Refresh ≠ flush: refresh makes docs *searchable*; flush makes them *durable* on disk.
- Tuning lever: bulk-loading? Raise the refresh interval (or set `-1`) to cut segment churn, then refresh once at the end.

## Relevance scoring — it's not a boolean match

Results are **ranked**, and seniors should know the model: modern Elasticsearch uses **BM25** (an improved TF-IDF). Score rises with **term frequency** (how often the term appears in the doc), falls with **document frequency** (common terms across the corpus matter less — the IDF), and normalizes by **field length** (a match in a short title beats one in a long body). See the postings-list/scoring internals in [`01-index-structures.md`](01-index-structures.md).

## Shards, replicas, and the resharding wall

- A **primary shard** count is **fixed at index creation** — you cannot add primaries later, because the routing formula is `hash(routing_key) % number_of_primary_shards`. Changing the divisor would misroute every existing doc. To "reshard" you **reindex into a new index** (or use the split/shrink APIs). This is *the* Elasticsearch capacity-planning trap — same class as the DynamoDB "model up front" and sharding-key problems.
- **Replica shards** are copies for read-scaling + HA; a search hits one copy (primary or replica) of each shard. More replicas → more read throughput and fault tolerance, at storage cost.
- **Oversharding** is the common mistake: thousands of tiny shards each carry fixed heap/segment overhead and crush the cluster. Rule of thumb: keep shards ~10–50GB, size for the future but don't over-split.

## Operational failure modes seniors must name

- **It's not your source of truth.** ES is a derived read model fed from your primary DB via CDC/dual-write ([`../../01-foundations/05-advanced-distributed-theory/03-change-data-capture.md`](../../01-foundations/05-advanced-distributed-theory/03-change-data-capture.md)); index loss should be recoverable by reindexing.
- **Split-brain / quorum:** master-eligible nodes elect a master; with an even count a partition can elect two masters. Modern ES enforces a quorum (`2f+1` voting nodes) automatically — but know *why* odd master-eligible counts matter.
- **JVM heap & GC:** ES is a JVM app; fielddata/aggregations on high-cardinality fields blow the heap and trigger long GC pauses that look like node failures. Cap heap ≤ ~31GB (compressed oops), use `doc_values` for aggregations.
- **Deep pagination** (`from + size` at page 10,000) forces every shard to return `from+size` hits to the coordinator — O(N) memory. Use **`search_after`** / scroll for deep traversal.

## Interview probes you should survive

- *"You indexed a doc but a search doesn't find it — bug?"* → No: near-real-time. It becomes searchable on the next refresh (~1s). Force `?refresh` in tests if needed.
- *"Your index is at capacity — just add shards?"* → Can't add primary shards; routing is `hash % primary_count`. Reindex into a new index with more shards (or split API).
- *"How does ES decide result order?"* → BM25 scoring: term frequency up, document frequency down (IDF), normalized by field length. Not a boolean filter.
- *"Why does an update not immediately free space?"* → Segments are immutable; update = delete-marker + re-index; space is reclaimed only at merge.
- *"A node went unresponsive with no crash — why?"* → Likely a long JVM GC pause from heap pressure (fielddata/aggregations on high-cardinality fields). Cap heap, use doc_values, avoid unbounded aggregations.

---

## Applied In

This concept is used by **5 problems** in this repo:

**High-Level Design**

- [Design Autocomplete / Typeahead Search](../../05-hld-problems/01-easy/autocomplete.md)
- [Design Typeahead Search (Google Search Bar)](../../05-hld-problems/02-medium/typeahead-search.md)
- [Design GitHub (Code Repository Hosting)](../../05-hld-problems/03-hard/github-code-repo.md)
- [Design a RAG System (Retrieval-Augmented Generation)](../../05-hld-problems/03-hard/rag-system.md)
- [Design a Web Search Engine (Google)](../../05-hld-problems/03-hard/search-system.md)

