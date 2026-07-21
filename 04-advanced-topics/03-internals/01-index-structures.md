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

## Applied In

This concept is used by **4 problems** in this repo:

**High-Level Design**

- [Design Autocomplete / Typeahead Search](../../05-hld-problems/01-easy/autocomplete.md)
- [Design Typeahead Search (Google Search Bar)](../../05-hld-problems/02-medium/typeahead-search.md)
- [Design a RAG System (Retrieval-Augmented Generation)](../../05-hld-problems/03-hard/rag-system.md)
- [Design a Web Search Engine (Google)](../../05-hld-problems/03-hard/search-system.md)

