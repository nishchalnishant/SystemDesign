# Design Search Engine (Mini Google)

> **Difficulty**: Hard  
> **Topics**: Inverted Index, TF-IDF, Ranking, Tries  
> **Scope**: Indexing and Search only (Crawler is distinct).

## Problem Statement

Ingest documents and allow keyword search.
- **Input**: "Apple banana", "Banana cherry".
- **Query**: "banana".
- **Output**: Ranked list of documents.

## Core Concept: Inverted Index

Map: `Word -> List[DocID]`.
- "apple" -> [1]
- "banana" -> [1, 2]

## Implementation

### Data Structures

```python
from collections import defaultdict
import re

class Posting:
    def __init__(self, doc_id):
        self.doc_id = doc_id
        self.frequency = 0
    
    def increment(self): self.frequency += 1
```

### Indexer

```python
class InvertedIndex:
    def __init__(self):
        # Word -> { DocID -> Posting }
        self.index = defaultdict(dict)

    def add_document(self, doc_id, content):
        words = re.findall(r'\w+', content.lower())
        for word in words:
            if doc_id not in self.index[word]:
                self.index[word][doc_id] = Posting(doc_id)
            self.index[word][doc_id].increment()

    def search(self, word):
        return list(self.index[word.lower()].values())
```

### Search Service (Ranking)

```python
class MiniGoogle:
    def __init__(self):
        self.index = InvertedIndex()
        self.docs = {}

    def index_page(self, id, content):
        self.docs[id] = content
        self.index.add_document(id, content)

    def search(self, query):
        keywords = re.findall(r'\w+', query.lower())
        
        # 1. Fetch Postings
        # word -> [Posting, Posting]
        matches = {w: self.index.search(w) for w in keywords}
        
        # 2. Example: OR Logic (Any keyword match)
        doc_scores = defaultdict(int)
        for word, postings in matches.items():
            for p in postings:
                # Simple Score: Sum of frequencies
                doc_scores[p.doc_id] += p.frequency

        # 3. Sort
        ranked = sorted(doc_scores.items(), key=lambda x: x[1], reverse=True)
        return ranked
```

## Key Topics

1.  **TF-IDF**: Replace simple frequency count with `TF * IDF` to penalize common words like "the".
2.  **Concurrency**: Use Read-Write locks or Double Buffering (Shadow Index) so writes don't block reads.
3.  **Sharding**: Document Partitioning (Shard by DocID) is better than Term Partitioning (Shard by Word) for multi-term queries.
