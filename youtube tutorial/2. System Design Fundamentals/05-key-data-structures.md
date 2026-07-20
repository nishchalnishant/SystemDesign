# Key Data Structures We Use Every Day

> **Source**: [10 Key Data Structures We Use Every Day](https://www.youtube.com/playlist?list=PLCRMIe5FDPsd0gVs500xeOewfySTsmEjf) — Video #5

---

## 1. Array / List
- **Use**: Ordered collection, random access by index
- **Time**: Access O(1), Search O(n), Insert O(n)
- **Where**: Everywhere — buffers, queues, matrix operations

## 2. Hash Map / Hash Table
- **Use**: Key-value lookups
- **Time**: Average O(1) for get/put/delete
- **Where**: Caches, database indexes, deduplication, counting

## 3. Stack
- **Use**: LIFO (Last In, First Out)
- **Time**: Push/Pop O(1)
- **Where**: Undo operations, browser history, expression parsing, DFS

## 4. Queue
- **Use**: FIFO (First In, First Out)
- **Time**: Enqueue/Dequeue O(1)
- **Where**: Message queues, BFS, task scheduling, rate limiting

## 5. Linked List
- **Use**: Dynamic collection with efficient insertions/deletions
- **Time**: Insert/Delete O(1) at known position, Search O(n)
- **Where**: LRU cache implementation, memory allocation

## 6. Tree / Binary Search Tree
- **Use**: Hierarchical data, sorted data
- **Time**: Balanced BST — O(log n) search/insert/delete
- **Where**: File systems, databases (B-Trees), DOM, routing tables

## 7. Heap / Priority Queue
- **Use**: Quick access to min/max element
- **Time**: Insert O(log n), Extract-min/max O(log n)
- **Where**: Task scheduling, Dijkstra's algorithm, top-K problems

## 8. Graph
- **Use**: Relationships between entities
- **Representations**: Adjacency list, adjacency matrix
- **Where**: Social networks, maps/routing, dependency resolution

## 9. Trie (Prefix Tree)
- **Use**: Prefix-based lookups
- **Time**: O(L) where L = length of key
- **Where**: Autocomplete, spell checking, IP routing, search suggestions

## 10. Bloom Filter
- **Use**: Probabilistic set membership test
- **Properties**: No false negatives, possible false positives
- **Where**: Duplicate detection, cache filtering, database query optimization

---

## Data Structures in System Design

| System Design Component | Key Data Structures Used |
|---|---|
| **Database Index** | B-Tree, B+ Tree, Hash Index |
| **Cache (LRU)** | Hash Map + Doubly Linked List |
| **Message Queue** | Queue, Ring Buffer |
| **Rate Limiter** | Queue, Hash Map (sliding window) |
| **Search Autocomplete** | Trie |
| **Social Graph** | Graph (adjacency list) |
| **Load Balancer** | Consistent Hash Ring |
| **Duplicate Detection** | Bloom Filter, Hash Set |
