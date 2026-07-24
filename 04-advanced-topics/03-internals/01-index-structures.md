> [!NOTE]
> ** 5-Minute Summary**
>
> **What this covers:** How databases search through billions of rows of data instantly.
>
> **Key topics:**
> - **The Problem:** If you ask a database for "User #450", and it has to check every single row one by one (A Full Table Scan), it will take 10 minutes.
> - **B-Tree Index (The Phonebook):** The standard database index. It sorts data into a massive tree. Perfect for searching ranges ("Find all users between ages 20 and 30").
> - **Hash Index (The Coat Check):** A math trick that jumps instantly to the exact location. Blazing fast, but completely useless for searching ranges.
> - **Inverted Index (The Book Glossary):** How Google and Elasticsearch work. Instead of mapping "Document -> Words", it maps "Word -> Documents".
>
> **Key takeaway:** Indexes make reading data 100x faster, but they make writing data slightly slower (because you have to update the index every time you add data). You must choose the right type of index for your specific search queries.

---
module: 04-advanced-topics
status: unread
tags: [04-advanced-topics, system-design, databases, internals]
---
# Index Structures - System Design Guide

> This guide explains how database indexes work using simple analogies.

---

## Why Should I Care?

Imagine a library with 1 million books, just thrown randomly into a giant pile in the center of the room.
If someone asks you, "Do you have the book *Harry Potter*?", you have to pick up every single book, one by one, until you find it. If it's the very last book in the pile, it will take you 3 years to answer the question.
In a database, this is called a **Full Table Scan**. It is the enemy of performance.

To fix this, you build an **Index**.
You buy a Rolodex (A tiny box of alphabetized cards). On the 'H' card, you write: *"Harry Potter is located on Shelf 4, Row 2."*
Now, when someone asks for the book, you look at the tiny Rolodex (takes 2 seconds), and walk directly to Shelf 4.

This is what a Database Index does. It trades a tiny amount of storage space (the Rolodex) for a massive increase in read speed.

---

## 1. The B-Tree Index (The Phonebook)

This is the default index used by 99% of relational databases (PostgreSQL, MySQL).

> ** Analogy:** A Phonebook. Everything is sorted alphabetically or numerically.

- **How it works:** The database creates a tree structure. To find the number 45, it starts at the top: "Is 45 greater or less than 50? Less. Go left. Is 45 greater or less than 25? Greater. Go right." In a database with 1 Billion rows, it only takes 4 "jumps" down the tree to find any number!
- **The Superpower:** It is amazing at **Range Queries**. If a user asks, "Find all products priced between $10 and $20", the B-Tree just finds the $10 mark, and reads everything next to it until it hits $20.
- **The Downside:** Writing new data is slightly slow, because the database has to physically shift data around to keep the tree perfectly balanced.

## 2. The Hash Index (The Coat Check)

This is used heavily by in-memory caches like Redis.

> ** Analogy:** A Coat Check at a fancy restaurant. You hand the clerk your coat, and they hand you ticket #42. When you come back, you hand them #42, and they turn around and instantly grab the coat sitting on hook #42. They don't have to search alphabetically.

- **How it works:** The database runs the search key (e.g., "John") through a math formula (A Hash Function). The math formula spits out an exact memory address (e.g., `Memory Slot 105`). The database jumps instantly to Slot 105.
- **The Superpower:** It is `O(1)` speed. It is the fastest possible way to look up a single, exact piece of data.
- **The Downside:** It is completely useless for Range Queries. If you ask a Hash Index for "All users between ages 20 and 30", it will crash, because age 20 and age 21 are stored in completely random, unrelated memory slots!

## 3. The Inverted Index (The Google Search)

This is how Search Engines (Elasticsearch, Lucene) work.

> ** Analogy:** The Glossary at the back of a textbook.

If you want to find every page in a textbook that mentions the word "Photosynthesis", you don't read the whole book. You go to the back of the book, find the word "Photosynthesis", and it says: *"Pages 12, 45, 99"*.

- **How it works:** Normal databases map a Document ID to its contents (e.g., `Doc 1: "The quick brown fox"`). An Inverted Index flips this upside down. It maps individual words to Document IDs.
  - `The -> [Doc 1]`
  - `quick -> [Doc 1, Doc 4]`
  - `fox -> [Doc 1, Doc 2, Doc 9]`
- **The Superpower:** Full-text search. If a user types "quick fox" into a search bar, Elasticsearch just looks up the two words in the index, finds the intersection (Doc 1), and returns it instantly.

---

## Interview Questions to Practice

1. **"What is the difference between a B-Tree Index and a Hash Index?"**
   *Answer:* A B-Tree stores data in a sorted, balanced tree, which makes it perfect for range queries (e.g., `price > 10 AND price < 50`) and sorting (`ORDER BY`). A Hash index maps keys to exact locations using a hash function; it is extremely fast for exact-match lookups (`WHERE id = 5`), but cannot be used for range queries or sorting.
2. **"Why shouldn't you just put an index on every single column in your database table?"**
   *Answer:* Because every time you `INSERT`, `UPDATE`, or `DELETE` a row, the database must also update every single index associated with that table. Having too many indexes will severely degrade write performance and consume a massive amount of disk space and RAM.
3. **"How does Elasticsearch perform full-text search so quickly?"**
   *Answer:* By using an Inverted Index. Instead of scanning documents for a word, it tokenizes the text upon ingestion and creates a mapping from every unique word to a list of Document IDs that contain that word. Searching is as fast as a single index lookup and an array intersection.

---

# 🎯 SDE-3 Deep Dive

B-Tree / Hash / Inverted is the taxonomy. Seniors get asked about **composite-index ordering and the leftmost-prefix rule, covering/index-only scans, when the planner *ignores* an index (selectivity), the LSM-vs-B-tree write trade, and the specialized indexes (GIN/GiST/partial) that solve real problems.**

## Composite indexes and the leftmost-prefix rule

An index on `(a, b, c)` is a *single* sorted structure keyed by `a`, then `b`, then `c`. This dictates what it can serve:

- It can satisfy `WHERE a=?`, `WHERE a=? AND b=?`, `WHERE a=? AND b=? AND c=?`, and range on the *last* used column.
- It **cannot** satisfy `WHERE b=?` alone — you skipped the leftmost column, so the index is unusable (like searching a phonebook by first name). This is the **leftmost-prefix rule** and the single most common "why isn't my index used?" bug.
- **Column order matters:** put equality predicates before range predicates. `(status, created_at)` serves `status='X' AND created_at > T`; `(created_at, status)` does not efficiently.

## Covering / index-only scans

If an index contains *every column the query needs* (via the key columns or an `INCLUDE` clause), the DB answers **from the index alone** — no heap/clustered-index trip. This eliminates the MySQL "double lookup" ([`07-mysql-internals.md`](07-mysql-internals.md)) and Postgres's heap visibility check (when the page is all-visible). Deliberately designing a **covering index** for a hot query is a core senior optimization.

## When the planner ignores your index — selectivity & cardinality

An index isn't free to use; the planner weighs it against a scan:

- **Low selectivity kills it.** An index on a boolean or `gender` column (few distinct values) returns a huge fraction of rows; the planner correctly chooses a **sequential scan** because random index I/O per row is slower than a bulk sequential read. Index columns with **high cardinality**.
- **Stale statistics** cause bad plans — the planner estimates row counts from `ANALYZE`/histogram stats; if they're stale it mis-picks. `ANALYZE` after big data changes.
- Functions on the indexed column (`WHERE lower(email)=...`) defeat a plain index — you need an **expression/functional index** on `lower(email)`.

## LSM-tree vs B-tree — the write-path trade

The deepest index question is the storage-engine trade (ties to [`02-database-internals`](../../03-scaling/02-database-internals.md)):

| | **B-Tree** (Postgres, InnoDB) | **LSM-Tree** (Cassandra, RocksDB, LevelDB) |
|---|---|---|
| Writes | In-place update → random I/O | Append to memtable → sequential flush |
| Write amplification | Lower per write, but random | Higher (compaction rewrites) but sequential |
| Reads | One tree traversal | May check memtable + several SSTables (Bloom filters mitigate) |
| Best for | Read-heavy, range-heavy, OLTP | Write-heavy ingest |

The one-liner: **B-trees optimize reads with in-place random writes; LSM-trees optimize writes by turning them sequential, paying it back at read/compaction time.**

## Specialized indexes worth naming

- **Partial index** — index only rows matching a predicate (`WHERE status='active'`); tiny index for a hot subset.
- **GIN** — inverted index for composite values (arrays, JSONB, full-text `tsvector`) — the Postgres cousin of Elasticsearch's inverted index.
- **GiST / SP-GiST** — geospatial / range / nearest-neighbor (R-tree-like).
- **Bitmap index scan** — the planner combines several medium-selectivity indexes by bitmapping matching rows, then fetching once in physical order.

## Inverted index internals (beyond "word → docs")

Real inverted indexes carry more than a doc list: **postings lists** store `(docID, term-frequency, positions)`, are **delta-encoded + compressed** and carry **skip pointers** for fast intersection; ranking uses **TF-IDF / BM25** over term/document frequencies; positions enable **phrase queries**. See [`09-elasticsearch-internals.md`](09-elasticsearch-internals.md).

## Interview probes you should survive

- *"You have an index on `(a,b)` but the query filters only on `b` — is it used?"* → No: leftmost-prefix rule. The index is sorted by `a` first, so `b`-only lookups can't use it. Reorder or add an index leading with `b`.
- *"You added an index but the query got no faster — why?"* → Low selectivity (planner prefers a seq scan), stale stats, a function wrapping the column, or the query needs columns not in the index (heap trips). Check `EXPLAIN`.
- *"Why is Cassandra fast at writes but can need multiple reads per lookup?"* → LSM-tree: writes append to a memtable (sequential); a read may probe the memtable plus several SSTables, mitigated by Bloom filters and compaction.
- *"How would you make this hot query avoid touching the table?"* → Covering index: include every selected column so it's an index-only scan.
- *"How does full-text ranking actually order results?"* → Postings lists with term frequencies feed TF-IDF/BM25 scoring; positions enable phrase matching.

---

## Applied In

This concept is used by **4 problems** in this repo:

**High-Level Design**

- [Design Autocomplete / Typeahead Search](../../05-hld-problems/01-easy/autocomplete.md)
- [Design Typeahead Search (Google Search Bar)](../../05-hld-problems/02-medium/typeahead-search.md)
- [Design a RAG System (Retrieval-Augmented Generation)](../../05-hld-problems/03-hard/rag-system.md)
- [Design a Web Search Engine (Google)](../../05-hld-problems/03-hard/search-system.md)

