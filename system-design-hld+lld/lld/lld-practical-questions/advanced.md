# Advanced

## AWS S3 Service



An end-to-end Low Level Design (LLD) for a simplified Object Storage Service (like AWS S3).

This problem is distinct from designing a standard file system because S3 is a Key-Value Store for Blobs. It decouples the Metadata (File info) from the Block Storage (Actual bytes).

***

#### 1. Requirements Clarification

* Core Entities:
  * Bucket: A container for objects. Names must be globally unique.
  * Object: The file itself (immutable content). Identified by a Key.
* Operations:
  * `create_bucket(name)`
  * `put_object(bucket, key, data)`
  * `get_object(bucket, key)`
  * `delete_object(bucket, key)`
* Key Behavior:
  * Flat Structure: S3 does not have real folders. "photos/2023/vacation.jpg" is just a long string key.
  * Immutability: You cannot edit a byte in the middle of a file. You must re-upload the whole object.

***

#### 2. Core Architecture: Metadata vs. Data

To design this correctly, we must separate concerns:

1. Metadata Store: A database (e.g., DynamoDB/Cassandra) holding attributes: `{Key: "abc.jpg", Size: 2MB, StoragePath: "/disk1/block_99"}`.
2. Blob Store: The physical storage engine (e.g., HDD/SSD) that saves the raw bytes.

***

#### 3. Class Diagram

We use the Strategy Pattern for the Storage Engine to allow swapping implementations (e.g., Local Disk vs. Distributed File System).

Code snippet

<figure><img src="../../../.gitbook/assets/image (182).png" alt=""><figcaption></figcaption></figure>

***

#### 4. Python Implementation

#### A. Storage Backend (The Strategy)

This handles the raw bytes. In a real system, this would talk to a block server. Here, we simulate it with a local directory.

Python

```python
import os
import uuid
from abc import ABC, abstractmethod

class StorageBackend(ABC):
    @abstractmethod
    def save(self, data_bytes) -> str:
        pass

    @abstractmethod
    def load(self, path_id) -> bytes:
        pass
        
    @abstractmethod
    def delete(self, path_id):
        pass

class LocalFileSystemStorage(StorageBackend):
    def __init__(self, root_dir="./s3_data"):
        self.root = root_dir
        if not os.path.exists(self.root):
            os.makedirs(self.root)

    def save(self, data_bytes) -> str:
        # Generate a unique physical ID (Content Addressable Storage)
        # We decouple the 'Logical Key' (user's filename) from 'Physical ID'
        path_id = str(uuid.uuid4())
        full_path = os.path.join(self.root, path_id)
        
        with open(full_path, "wb") as f:
            f.write(data_bytes)
        return path_id

    def load(self, path_id) -> bytes:
        full_path = os.path.join(self.root, path_id)
        if not os.path.exists(full_path):
            raise FileNotFoundError("Physical block not found")
        
        with open(full_path, "rb") as f:
            return f.read()

    def delete(self, path_id):
        full_path = os.path.join(self.root, path_id)
        if os.path.exists(full_path):
            os.remove(full_path)
```

#### B. The Entities (Bucket & Object)

Python

```python
from datetime import datetime

class S3Object:
    def __init__(self, key, size, storage_path, content_type="application/octet-stream"):
        self.key = key
        self.size = size
        self.storage_path = storage_path # The UUID pointer to disk
        self.created_at = datetime.now()
        self.content_type = content_type
        self.custom_metadata = {}

class Bucket:
    def __init__(self, name):
        self.name = name
        self.created_at = datetime.now()
        # The Metadata Index: Key -> S3Object Metadata
        self.objects = {} 

    def add_object_metadata(self, obj: S3Object):
        self.objects[obj.key] = obj

    def get_object_metadata(self, key):
        return self.objects.get(key)
    
    def remove_object_metadata(self, key):
        if key in self.objects:
            del self.objects[key]
```

#### C. The Service (Facade)

This coordinates the Metadata Store and the Blob Store.

Python

```python
class S3Service:
    def __init__(self, storage_strategy: StorageBackend):
        self.storage = storage_strategy
        self.buckets = {} # Global namespace for buckets

    def create_bucket(self, bucket_name):
        if bucket_name in self.buckets:
            raise ValueError("Bucket name already exists globally.")
        
        self.buckets[bucket_name] = Bucket(bucket_name)
        print(f"Bucket '{bucket_name}' created.")

    def put_object(self, bucket_name, key, data_bytes):
        bucket = self.buckets.get(bucket_name)
        if not bucket:
            raise ValueError("Bucket not found")

        # 1. Save Bytes to Disk (Blob Store)
        # Return the physical path (UUID)
        physical_path = self.storage.save(data_bytes)

        # 2. Create Metadata
        size = len(data_bytes)
        new_obj = S3Object(key, size, physical_path)

        # 3. Update Index (Metadata Store)
        # Note: If object exists, we overwrite the metadata pointer.
        # Ideally, we should delete the OLD physical file to save space (Garbage Collection).
        bucket.add_object_metadata(new_obj)
        
        print(f"Uploaded '{key}' to '{bucket_name}' (Size: {size} bytes)")

    def get_object(self, bucket_name, key):
        bucket = self.buckets.get(bucket_name)
        if not bucket: raise ValueError("Bucket not found")

        # 1. Retrieve Metadata
        metadata = bucket.get_object_metadata(key)
        if not metadata:
            raise FileNotFoundError("Object key not found")

        # 2. Retrieve Bytes using the physical path
        data = self.storage.load(metadata.storage_path)
        return data

    def delete_object(self, bucket_name, key):
        bucket = self.buckets.get(bucket_name)
        if not bucket: return

        metadata = bucket.get_object_metadata(key)
        if metadata:
            # 1. Delete actual bytes
            self.storage.delete(metadata.storage_path)
            # 2. Delete metadata
            bucket.remove_object_metadata(key)
            print(f"Deleted '{key}'")
```

#### D. Driver Code

Python

```python
if __name__ == "__main__":
    # Use Local Disk simulation
    storage = LocalFileSystemStorage()
    aws = S3Service(storage)

    # 1. Setup
    aws.create_bucket("my-photos")

    # 2. Upload
    # S3 treats paths like "2023/jan/img.jpg" as a single string key
    image_data = b"\x89PNG\r\n..." # Fake binary data
    aws.put_object("my-photos", "2023/vacation/beach.png", image_data)

    # 3. Download
    retrieved_data = aws.get_object("my-photos", "2023/vacation/beach.png")
    print(f"Retrieved {len(retrieved_data)} bytes.")

    # 4. Overwrite (Immutability concept)
    # The system will generate a NEW UUID for storage, and update the pointer.
    new_data = b"New Image Content"
    aws.put_object("my-photos", "2023/vacation/beach.png", new_data)
```

***

#### 5. Key Design Decisions & Interview Questions

#### 1. Handling Large Files (Multipart Upload)

* Question: How do you upload a 50GB file? You can't hold it all in RAM.
* Design: Break the file into chunks (e.g., 10MB parts).
  * API: `initiate_multipart_upload()`, `upload_part(part_id, data)`, `complete_upload()`.
  * The `S3Object` metadata will store a list of chunk IDs: `[chunk1_uuid, chunk2_uuid, ...]`.
  * `get_object` reconstructs the file by reading chunks in order.

#### 2. Logical vs Physical Deletion

* Question: When I overwrite a file, what happens to the old data?
* Answer: In the code above, the old data is orphaned on the disk (if we didn't explicitly delete it).
* Real S3 Approach: S3 often uses a Garbage Collector process. It marks metadata as "Deleted" instantly, but a background process scans for physical blobs that have no metadata pointing to them and deletes them later (Async deletion).

#### 3. Folder Structure

* Question: Does S3 have folders?
* Answer: No. It's a flat key-value store.
* UI Illusion: The console shows folders by splitting keys by the `/` delimiter.
* Implication: Renaming a "folder" is expensive. To rename `photos/` to `images/`, S3 must copy _every single object_ to the new key prefix and delete the old ones. It's an $$ $O(N)$ $$ operation.

#### 4. Versioning

* Feature: S3 allows keeping history.
* Implementation: The `objects` map in `Bucket` changes from `Key -> Object` to `Key -> List[Object]`.
  * `put_object` appends to the list instead of overwriting.
  * `get_object` returns the last item in the list (Current Version).

#### 5. Distributed Scalability (System Design touch)

* Metadata: Because bucket listings can be huge, metadata is usually sharded in a distributed DB (like DynamoDB or a specialized LSM-Tree engine).
* Data: The `storage_path` uses Consistent Hashing to distribute bytes across thousands of servers.

Would you like to move on to "Design a Traffic Control Signal System" or "Design a Cache (LRU)"?

## Google Drive

An end-to-end Low Level Design (LLD) for a Search Engine (like a mini-Google).

Disclaimer: Designing "Google" is typically a System Design (HLD) question due to its scale. For Machine Coding (LLD), the interviewer expects you to build the core engine: The Inverted Index and the Ranking Algorithm.

***

#### 1. Requirements Clarification

* Goal: Ingest documents (web pages) and allow users to search for them using keywords.
* Input: A list of "Web Pages" (Strings with ID and Content).
* Output: A list of relevant page IDs sorted by relevance (Ranking).
* Core Components:
  * Crawler: (Simulated) Adds documents to the system.
  * Indexer: Processing text to make it searchable.
  * Query Processor: Parsing user input.
  * Ranker: Sorting results (e.g., using Frequency).

***

#### 2. Core Concept: The Inverted Index

This is the heart of any search engine.

* Forward Index: Document ID $$ $\rightarrow$ $$ List of words. (Great for storage, bad for search).
* Inverted Index: Word $$ $\rightarrow$ $$ List of Document IDs. (Great for search).

Example:

* Doc1: "Apple banana"
* Doc2: "Banana cherry"

Inverted Index:

* "apple" $$ $\rightarrow$ $$ \[Doc1]
* "banana" $$ $\rightarrow$ $$ \[Doc1, Doc2]
* "cherry" $$ $\rightarrow$ $$ \[Doc2]

***

#### 3. Class Diagram

We will use a Pipeline Architecture for ingestion and a Service for retrieval.

Code snippet

<figure><img src="../../../.gitbook/assets/image (183).png" alt=""><figcaption></figcaption></figure>

***

#### 4. Python Implementation

#### A. The Data Structures (Document & Posting)

The `Posting` class is crucial. It doesn't just store "Doc ID", it stores metadata (frequency, position) needed for ranking.

Python

```python
from collections import defaultdict
import re

class Document:
    def __init__(self, doc_id, url, content):
        self.id = doc_id
        self.url = url
        self.content = content

class Posting:
    """
    Represents a specific word inside a specific document.
    Stores how many times it appears (frequency) to help with Ranking.
    """
    def __init__(self, doc_id):
        self.doc_id = doc_id
        self.frequency = 0
    
    def increment(self):
        self.frequency += 1
    
    def __repr__(self):
        return f"[Doc:{self.doc_id} Freq:{self.frequency}]"
```

#### B. The Indexer (The Engine)

This builds the Inverted Index.

Python

```python
class InvertedIndex:
    def __init__(self):
        # Map: Word -> Map: DocID -> Posting
        # We use a nested map for O(1) lookup during construction
        self.index = defaultdict(dict)

    def add_document(self, doc: Document):
        # 1. Tokenize (Simple split by non-alphanumeric)
        # In real life: Stemming (running->run), Stop words removal (the, is)
        words = re.findall(r'\w+', doc.content.lower())
        
        for word in words:
            # If word/doc pair doesn't exist, create it
            if doc.id not in self.index[word]:
                self.index[word][doc.id] = Posting(doc.id)
            
            # Increment frequency
            self.index[word][doc.id].increment()

    def search(self, word):
        """Returns list of Postings for a word"""
        word = word.lower()
        if word in self.index:
            return list(self.index[word].values())
        return []
```

#### C. The Search Service (Orchestrator)

This handles the user query, intersection of results, and ranking.

Python

```python
class MiniGoogle:
    def __init__(self):
        self.db = {} # Mock DB: DocID -> Document
        self.inverted_index = InvertedIndex()
        self.doc_counter = 1

    def crawl_page(self, url, content):
        """Simulates the Crawler + Indexer pipeline"""
        doc = Document(self.doc_counter, url, content)
        self.db[self.doc_counter] = doc
        self.doc_counter += 1
        
        self.inverted_index.add_document(doc)
        print(f"Indexed: {url}")

    def search(self, query):
        print(f"\n--- Searching for: '{query}' ---")
        keywords = re.findall(r'\w+', query.lower())
        if not keywords:
            return []

        # 1. Retrieve Postings for each keyword
        # results_per_word = { "apple": [Posting(Doc1), Posting(Doc2)], "pie": [Posting(Doc1)] }
        results_per_word = {}
        for word in keywords:
            results_per_word[word] = self.inverted_index.search(word)

        # 2. Identify Relevant Documents (OR Logic vs AND Logic)
        # Here we use OR logic: A document is relevant if it contains ANY keyword.
        relevant_doc_ids = set()
        for postings in results_per_word.values():
            for p in postings:
                relevant_doc_ids.add(p.doc_id)

        # 3. Ranking (Simple TF-IDF style logic)
        # Score = Sum of frequencies of matched keywords
        scored_results = []
        for doc_id in relevant_doc_ids:
            score = 0
            for word in keywords:
                # Find the posting for this word & doc
                postings = results_per_word.get(word, [])
                for p in postings:
                    if p.doc_id == doc_id:
                        score += p.frequency
            
            scored_results.append((doc_id, score))

        # 4. Sort by Score (Desc)
        scored_results.sort(key=lambda x: x[1], reverse=True)

        # 5. Fetch actual content
        final_output = []
        for doc_id, score in scored_results:
            doc = self.db[doc_id]
            final_output.append(f"{doc.url} (Score: {score})")
            
        return final_output
```

#### D. Driver Code

Python

```python
if __name__ == "__main__":
    google = MiniGoogle()

    # 1. Ingest Data
    google.crawl_page("google.com/apple", "Apple is a fruit. Apple pie is good.")
    google.crawl_page("google.com/tech", "Apple released a new phone. Tech is booming.")
    google.crawl_page("google.com/banana", "Banana is yellow. Monkey likes banana.")

    # 2. Search Scenarios
    
    # Query 1: Single keyword
    # Should return 2 docs. 'apple' appears twice in doc 1, once in doc 2.
    # Doc 1 should be ranked higher.
    results = google.search("apple")
    for r in results: print(r)

    # Query 2: Multiple keywords
    # "Apple" (in 1 & 2) and "Banana" (in 3)
    results = google.search("apple banana")
    for r in results: print(r)
```

***

#### 5. Key Design Challenges & Interview Answers

#### 1. AND vs OR Search

* Question: How do you handle "Apple AND Banana"?
* Answer: Instead of taking the Union (`set.add`) of document IDs found, you take the Intersection.
  * List 1 (Apple): \[Doc1, Doc2]
  * List 2 (Banana): \[Doc3]
  * Intersection: \[] (No results).

#### 2. Ranking Algorithm (TF-IDF)

* Question: Counting frequency is too simple. If I search "the apple", "the" appears 100 times, "apple" appears once. A doc with 100 "the"s will outrank a doc with 5 "apple"s.
* Solution: Use TF-IDF (Term Frequency - Inverse Document Frequency).
  * TF: How often word appears in _this_ doc. (High for "the" and "apple").
  * IDF: How rare is the word across _all_ docs?
    * "The" is in every doc $$ $\rightarrow$ $$ Low Score.
    * "Apple" is rare $$ $\rightarrow$ $$ High Score.
  * We multiply TF \* IDF. "The" gets penalized effectively.

#### 3. Typeahead / Autocomplete

* Question: How do you design the dropdown suggestions?
* Structure: Use a Trie (Prefix Tree).
* Optimization: Store the "Top 5 searched queries" at each node of the Trie so you don't have to traverse to the bottom to find suggestions.

#### 4. Concurrency

* Question: What if thousands of crawlers are adding documents while users are searching?
* Design:
  * The Indexer shouldn't block the Searcher.
  * Use a Read-Write Lock.
  * Or, use Double Buffering (Shadow Indexing): Search queries go to `Index_A` (ReadOnly). The Crawler updates `Index_B`. Once `Index_B` is ready, we atomically swap them.

#### 5. Scalability (Sharding)

* The index is too big for one machine.
* Document Partitioning (Sharding by DocID): Each machine holds a subset of documents (e.g., Doc 1-1M).
  * _Search:_ Broadcast query to ALL shards, merge results. (Parallel processing).
* Term Partitioning (Sharding by Word): Each machine holds a subset of words (e.g., A-C).
  * _Search:_ Query "Apple" goes to Shard 1.
  * _Problem:_ Multi-word queries ("Apple Banana") require hitting multiple shards and transferring large lists over the network. Document partitioning is usually preferred.

Would you like to move on to "Design a Rate Limiter" or "Design a Task Scheduler"?

## Version Control (GitHub) - Database Modelling

An end-to-end Low Level Design (LLD) for a Version Control System (like Git).

This problem tests your understanding of Graph Theory (Directed Acyclic Graph - DAG), Hashing, and Immutable Data Structures.

***

#### 1. Requirements Clarification

* Core Entities: Files, Commits, Branches.
* Key Operations:
  * `commit(message)`: Save the current state of files.
  * `create_branch(name)`: Create a new pointer to the current commit.
  * `switch_branch(name)`: Switch the working context.
  * `merge(branch_name)`: Merge changes from another branch (simplified).
  * `log()`: Show history.
* Constraints:
  * Store file contents efficiently (deduplication is a bonus).
  * Handle the history as a graph (DAG).

***

#### 2. Core Architecture: The Git Object Model

To design this correctly, you must think like Git. Git is not a file system; it's a Content-Addressable File System.

1. Blob: Represents the _content_ of a file. If two files have the same text, they point to the same Blob.
2. Commit: A snapshot. It points to the file state and a parent commit.
3. Ref (Branch): A mutable pointer to a specific Commit Hash (e.g., `master` points to `Commit A`).
4. HEAD: A pointer to the _current_ Branch (or Commit).

***

#### 3. Class Diagram

Code snippet

<figure><img src="../../../.gitbook/assets/image (184).png" alt=""><figcaption></figcaption></figure>

***

#### 4. Python Implementation

We will use `hashlib` to generate SHA-1 hashes, mimicking Git's ID generation.

#### A. The Commit Node (The Snapshot)

In a real system, we would have `Tree` objects (folders) and `Blob` objects. For an interview, we can simplify: A commit stores a flat map of `{filename: content_hash}`.

Python

```python
import hashlib
import uuid
import datetime
import copy

class Commit:
    def __init__(self, message, files_snapshot, parent_id=None):
        self.message = message
        # Snapshot: {filename: content}
        # In real Git, this maps filename -> blob_hash. 
        # Here we store content directly for simplicity, or we can hash it.
        self.files = copy.deepcopy(files_snapshot) 
        self.parent_id = parent_id
        self.timestamp = datetime.datetime.now()
        self.id = self._generate_hash()

    def _generate_hash(self):
        # Create a unique string based on content + time + parent
        unique_str = f"{self.message}{self.timestamp}{self.parent_id}{str(self.files)}"
        return hashlib.sha1(unique_str.encode("utf-8")).hexdigest()[:7] # Short hash

    def __repr__(self):
        return f"[{self.id}] {self.message}"
```

#### B. The Version Control System (The Engine)

This manages the graph pointers (`HEAD`, `master`, etc.).

Python

```python
class MiniGit:
    def __init__(self):
        # 1. Storage
        self.commits = {} # Map: commit_id -> Commit Object
        self.branches = {} # Map: branch_name -> commit_id (String)
        
        # 2. State
        self.staging = {} # Map: filename -> content (The "Index")
        
        # 3. Initialization
        self.current_branch = "master"
        self.branches["master"] = None # No commit yet
        
    def add(self, filename, content):
        """Stage a file change."""
        self.staging[filename] = content
        print(f"Staged '{filename}'")

    def commit(self, message):
        """Create a snapshot."""
        # 1. Determine Parent
        parent_commit_id = self.branches[self.current_branch]
        
        # 2. Calculate State
        # Start with parent's files (if any), then apply staging on top
        new_files_snapshot = {}
        if parent_commit_id:
            parent_commit = self.commits[parent_commit_id]
            new_files_snapshot = copy.deepcopy(parent_commit.files)
        
        # Apply Staged changes
        new_files_snapshot.update(self.staging)
        
        # 3. Create Commit Object
        new_commit = Commit(message, new_files_snapshot, parent_commit_id)
        
        # 4. Save to Storage
        self.commits[new_commit.id] = new_commit
        
        # 5. Move Branch Pointer
        self.branches[self.current_branch] = new_commit.id
        
        # 6. Clear Staging
        self.staging.clear()
        print(f"Committed: {new_commit}")
        return new_commit.id

    def create_branch(self, branch_name):
        if branch_name in self.branches:
            print(f"Branch '{branch_name}' already exists.")
            return
        
        # New branch points to the SAME commit as current branch
        current_commit_id = self.branches[self.current_branch]
        self.branches[branch_name] = current_commit_id
        print(f"Created branch '{branch_name}' at {current_commit_id}")

    def switch_branch(self, branch_name):
        if branch_name not in self.branches:
            print(f"Branch '{branch_name}' does not exist.")
            return
            
        self.current_branch = branch_name
        
        # Update working directory (simulated)
        head_commit_id = self.branches[branch_name]
        print(f"Switched to branch '{branch_name}'")
        if head_commit_id:
            print(f"Head is now at: {self.commits[head_commit_id].message}")
        else:
            print("Branch is empty.")

    def show_log(self):
        """Traverse the graph backwards from HEAD."""
        curr_id = self.branches[self.current_branch]
        print(f"\n--- Log for {self.current_branch} ---")
        while curr_id:
            commit = self.commits[curr_id]
            print(f"{commit.id} | {commit.message} | Files: {list(commit.files.keys())}")
            curr_id = commit.parent_id
        print("--- End of Log ---\n")

    def merge(self, source_branch):
        """Naive Merge: Applies source branch changes on top of current."""
        # Real git uses '3-way merge' finding a common ancestor.
        # This is a simplified 'Fast-Forward' or 'Squash' style merge for LLD.
        
        source_commit_id = self.branches.get(source_branch)
        if not source_commit_id:
            print("Source branch invalid.")
            return

        print(f"Merging {source_branch} into {self.current_branch}...")
        
        # Get the files from the source branch head
        source_commit = self.commits[source_commit_id]
        
        # Stage them all (Simplification: Overwrite conflict resolution)
        self.staging.update(source_commit.files)
        
        # Auto commit
        self.commit(f"Merge branch '{source_branch}'")
```

#### C. Driver Code

Python

```python
if __name__ == "__main__":
    git = MiniGit()

    # 1. Work on Master
    git.add("file1.txt", "Hello World")
    git.commit("Initial Commit")

    git.add("file2.txt", "Second File")
    git.commit("Added file2")

    # 2. Create Feature Branch
    git.create_branch("feature-login")
    git.switch_branch("feature-login")

    # 3. Modify in Feature Branch
    git.add("login.py", "def login(): pass")
    git.commit("Added login logic")
    
    git.show_log()

    # 4. Switch back and Merge
    git.switch_branch("master")
    # Master doesn't have login.py yet
    
    git.merge("feature-login")
    # Now master has login.py
    
    git.show_log()
```

***

#### 5. Key Design Challenges & Interview Answers

#### 1. Storage Efficiency (Deduplication)

* Problem: If I have a 100MB file and I change 1 character, `Commit A` has the old file and `Commit B` has the new file. Storing full copies is wasteful.
* Git's Solution (The Blob): Git stores files by the _Hash of their content_.
  * File1: "Hello" $$ $\rightarrow$ $$ Hash `a1b2`. Store Object `a1b2`.
  * Commit A points to `a1b2`.
  * If Commit B uses the same file "Hello", it also points to `a1b2`. No duplicate storage.
  * Interview Implementation: Use a separate `BlobStore` dictionary `{content_hash: content}`. The Commit object only stores `{filename: content_hash}`.

#### 2. Merge Conflicts

* Question: What if both branches modified line 10 of `file1.txt`?
* Logic:
  1. Find the Common Ancestor (The latest commit reachable from both branches).
  2. Diff `Ancestor` vs `Current` $$ $\rightarrow$ $$ `MyChanges`.
  3. Diff `Ancestor` vs `Incoming` $$ $\rightarrow$ $$ `TheirChanges`.
  4. If both touched the same line with different content $$ $\rightarrow$ $$ Raise `ConflictException`.
  5. The system stops and asks the user to manually edit the file and run `add` again.

#### 3. Detached HEAD

* Concept: Usually `HEAD` points to a Branch Name (`master`).
* Detached: `HEAD` points directly to a Commit Hash (`a1b2c`).
* Implication: If you commit now, the new commit has no branch pointing to it. If you switch away, you lose this commit (it becomes "garbage" and will be collected).

#### 4. Differencing (Diffs vs Snapshots)

* SVN/Old VCS: Stored "Deltas" (Change from V1 to V2). Slow to reconstruct the full file.
* Git: Stores "Snapshots" (The full state). Fast to checkout, but requires smart compression (Packfiles) to save space. _Stick to the Snapshot model for LLD interviews._

#### 5. `git revert` vs `git reset`

* Revert: Creates a _new_ commit that undoes changes. (Safe for public branches).
* Reset: Moves the branch pointer backward. (Destructive, dangerous for shared history).
* Implementation: `reset` is just `branches[current] = target_commit_id`. `revert` is applying the reverse patch and calling `commit()`.

## Mentorship Platform like Preplaced

An end-to-end Low Level Design (LLD) for a Mentorship Platform (like Preplaced, Topmate, or ADPList).

This problem focuses on Two-Sided Marketplace Dynamics (matching Supply and Demand), Calendar/Availability Management, and Booking State Machines.

***

#### 1. Requirements Clarification

* Actors:
  * Mentor: Sets availability, sets price/free sessions, approves requests.
  * Mentee: Searches for mentors (by skill/company), books slots.
  * System: Handles payments, scheduling, and notifications.
* Core Use Cases:
  * Onboarding: Mentors create profiles with specific tags (e.g., "Google", "System Design", "Python").
  * Availability: Mentors define "Slots" (e.g., Saturday 10 AM - 12 PM).
  * Booking: Mentee selects a slot $$ $\rightarrow$ $$ Block Slot $$ $\rightarrow$ $$ Payment $$ $\rightarrow$ $$ Confirmation.
  * Search: Filter mentors by Domain, Company, or Price.

***

#### 2. Core Architecture: The Booking Flow

The most critical part is handling Availability. We cannot allow two mentees to book the same mentor at the same time.

1. Slot Definition: A granular unit of time (e.g., 30 mins).
2. Booking Lifecycle: `CREATED` $$ $\rightarrow$ $$ `PAYMENT_PENDING` $$ $\rightarrow$ $$ `CONFIRMED` $$ $\rightarrow$ $$ `COMPLETED`.

***

#### 3. Class Diagram

Code snippet

<figure><img src="../../../.gitbook/assets/image (185).png" alt=""><figcaption></figcaption></figure>

***

#### 4. Python Implementation

#### A. The Models (Users & Slots)

We treat `Slot` as a resource that must be locked during booking.

Python

```python
from enum import Enum
from datetime import datetime, timedelta
import uuid
import threading

class Skill(Enum):
    PYTHON = 1
    JAVA = 2
    SYSTEM_DESIGN = 3
    PRODUCT_MANAGEMENT = 4

class BookingStatus(Enum):
    PENDING = 1
    CONFIRMED = 2
    CANCELLED = 3

class User:
    def __init__(self, uid, name, email):
        self.id = uid
        self.name = name
        self.email = email

class Slot:
    def __init__(self, start_time, duration_mins=30):
        self.id = str(uuid.uuid4())
        self.start_time = start_time
        self.end_time = start_time + timedelta(minutes=duration_mins)
        self.is_booked = False
        self.lock = threading.Lock() # For concurrency control

    def overlaps(self, other_start, other_end):
        return self.start_time < other_end and self.end_time > other_start

    def __repr__(self):
        status = "Booked" if self.is_booked else "Free"
        return f"[{self.start_time.strftime('%H:%M')} - {self.end_time.strftime('%H:%M')} : {status}]"

class Mentor(User):
    def __init__(self, uid, name, email, company, rate):
        super().__init__(uid, name, email)
        self.company = company
        self.rate = rate
        self.skills = set()
        self.slots = [] # List of Slot objects

    def add_skill(self, skill: Skill):
        self.skills.add(skill)

    def add_availability(self, start_time):
        # In a real system, we would check for overlaps with existing slots here
        new_slot = Slot(start_time)
        self.slots.append(new_slot)
        print(f"Mentor {self.name} added availability: {new_slot}")

    def get_available_slots(self):
        return [s for s in self.slots if not self.is_booked]
```

#### B. The Search Engine (Discovery)

A simple in-memory filter. In production, this would be an Elasticsearch query.

Python

```python
class SearchService:
    def __init__(self):
        self.mentors = []

    def register_mentor(self, mentor):
        self.mentors.append(mentor)

    def search(self, skill=None, company=None, max_price=None):
        results = []
        for mentor in self.mentors:
            if skill and skill not in mentor.skills:
                continue
            if company and mentor.company.lower() != company.lower():
                continue
            if max_price and mentor.rate > max_price:
                continue
            results.append(mentor)
        return results
```

#### C. The Booking System (Transaction Management)

This handles the critical "Check-Lock-Book" flow.

Python

```python
class Booking:
    def __init__(self, mentee, mentor, slot):
        self.id = str(uuid.uuid4())
        self.mentee = mentee
        self.mentor = mentor
        self.slot = slot
        self.status = BookingStatus.PENDING
        self.timestamp = datetime.now()

class Platform:
    def __init__(self):
        self.search_service = SearchService()
        self.bookings = {} # id -> Booking

    def book_session(self, mentee, mentor, slot_id):
        # 1. Find the slot
        target_slot = None
        for slot in mentor.slots:
            if slot.id == slot_id:
                target_slot = slot
                break
        
        if not target_slot:
            print("Error: Slot not found.")
            return None

        # 2. Concurrency Control (The Critical Section)
        # We acquire the lock on the specific slot object
        with target_slot.lock:
            if target_slot.is_booked:
                print(f"Failed: Slot {target_slot} is already booked.")
                return None
            
            # 3. Reserve the slot (Optimistic Booking)
            target_slot.is_booked = True
            
            # 4. Create Booking Record
            booking = Booking(mentee, mentor, target_slot)
            
            # 5. Simulate Payment...
            if self._process_payment(mentee, mentor.rate):
                booking.status = BookingStatus.CONFIRMED
                self.bookings[booking.id] = booking
                print(f"Success: Booking Confirmed for {mentee.name} with {mentor.name}")
                return booking
            else:
                # Rollback
                target_slot.is_booked = False
                print("Payment Failed. Slot released.")
                return None

    def _process_payment(self, user, amount):
        # Mock payment gateway
        return True
```

#### D. Driver Code

Python

```python
if __name__ == "__main__":
    platform = Platform()

    # 1. Setup Mentor
    alice = Mentor("m1", "Alice", "alice@google.com", "Google", 50.0)
    alice.add_skill(Skill.SYSTEM_DESIGN)
    alice.add_skill(Skill.PYTHON)
    
    # Add slots for today
    now = datetime.now()
    slot_time = now.replace(hour=10, minute=0, second=0, microsecond=0)
    alice.add_availability(slot_time) # 10:00 - 10:30
    
    platform.search_service.register_mentor(alice)

    # 2. Setup Mentee
    bob = User("u1", "Bob", "bob@college.edu")

    # 3. Search
    results = platform.search_service.search(skill=Skill.SYSTEM_DESIGN, company="Google")
    print(f"Found {len(results)} mentors.")
    
    if results:
        mentor = results[0]
        # Bob views slots
        available = [s for s in mentor.slots if not s.is_booked]
        if available:
            chosen_slot = available[0]
            # Bob books
            platform.book_session(bob, mentor, chosen_slot.id)
            
            # Another user tries to book same slot
            charlie = User("u2", "Charlie", "c@c.com")
            platform.book_session(charlie, mentor, chosen_slot.id) # Should fail
```

***

#### 5. Key Design Challenges & Interview Answers

#### 1. Handling Timezones

* Problem: Alice (Mentor) is in New York (EST). Bob (Mentee) is in India (IST).
* Solution:
  * Backend: ALWAYS store times in UTC in the database (e.g., `2023-10-01T14:00:00Z`).
  * Frontend: Convert UTC to the user's browser locale (`Intl.DateTimeFormat`).
  * Notification: Emails should say "Your session is at \[Local Time]".

#### 2. Integration with Google Calendar

* Use Case: When a booking is confirmed, it should appear on both users' Google Calendars with a Meet link.
* Design:
  * Use OAuth 2.0 to get write access to the user's calendar.
  * Upon `BookingStatus.CONFIRMED`, trigger an async job (Kafka/Celery).
  * Call Google Calendar API `events.insert` with the attendees' emails.
  * Save the `conferenceData` (Google Meet Link) back to the Booking entity.

#### 3. Slot Generation (User Experience)

* Problem: Mentors don't want to create 100 slots manually.
* Solution: Implement Recurrence Rules.
  * UI: "I am available Every Saturday 10 AM - 2 PM".
  * Backend: Don't create infinite rows in the DB. Create "Availability Templates".
  * When a user views the profile for "Next Week", the system dynamically generates the "Virtual Slots" based on the template minus any existing bookings.

#### 4. Ranking (Search Relevance)

* Question: How do you decide which mentor appears first?
* Factors:
  * Availability: Mentors with slots in the next 24h rank higher.
  * Response Rate: Mentors who reject requests rank lower.
  * Rating: Weighted average of last 20 reviews.

#### 5. Dispute Resolution

* Scenario: Mentee says "Mentor didn't show up". Mentor says "I was there".
* Solution:
  * If using an integrated video platform (like internal WebRTC or Zoom API), log "Join Events".
  * Check logs: Did `MentorID` join room `BookingID` between `Start` and `End`?
  * If no logs (e.g., WhatsApp call), it requires manual Admin intervention (He said/She said). This is why platforms enforce on-platform calls.



## Tinder Dating App

An end-to-end Low Level Design (LLD) for a Dating Application (like Tinder or Bumble).

This problem tests your ability to handle Geospatial Data (finding people near you) and Event Driven Actions (User A likes B + User B likes A $$ $\rightarrow$ $$ Trigger Match).

***

#### 1. Requirements Clarification

* Actors: User.
* Core Entities: Profile (Bio, Photos), Location (Lat/Long), Preferences (Gender, Age Range, Distance Radius).
* Core Operations:
  * `create_profile()`
  * `update_location(lat, long)`
  * `get_recommendations()`: Fetch users who match my preferences and I haven't swiped on yet.
  * `swipe(user_id, direction)`: Right (Like) or Left (Pass).
* The "Match" Condition: A match occurs if and only if Mutual Likes exist (A likes B AND B likes A).

***

#### 2. Core Architecture: The "Double Opt-In"

The system relies on checking a "Reverse Lookup" upon every Like action.

1. Alice Likes Bob. System saves `Swipe(Alice -> Bob)`.
2. System checks: Did Bob already like Alice?
   * No $$ $\rightarrow$ $$ Do nothing (Silent).
   * Yes $$ $\rightarrow$ $$ It's a Match! Create Match object and notify both.

***

#### 3. Class Diagram

Code snippet

<figure><img src="../../../.gitbook/assets/image (186).png" alt=""><figcaption></figcaption></figure>

***

#### 4. Python Implementation

#### A. Core Models (User, Location)

We need a robust way to calculate distance.

Python

```python
import math
from enum import Enum
import uuid

class Gender(Enum):
    MALE = 1
    FEMALE = 2

class SwipeType(Enum):
    LIKE = 1
    PASS = 2

class Location:
    def __init__(self, lat, long):
        self.lat = lat
        self.long = long

    def distance_to(self, other):
        # Simplified Euclidean distance for Interview (Pythagoras)
        # In production, use Haversine formula
        return math.sqrt(
            (self.lat - other.lat)**2 + (self.long - other.long)**2
        )

class User:
    def __init__(self, name, gender, age, location):
        self.id = str(uuid.uuid4())
        self.name = name
        self.gender = gender
        self.age = age
        self.location = location
        # Preferences
        self.radius_filter = 100 # units
        self.seen_users = set() # Set of IDs already swiped on

    def __repr__(self):
        return f"{self.name} ({self.age})"
```

#### B. The Recommendation Engine

This filters the "Feed". In a real system, this uses a QuadTree or Geohash. For LLD, iterating with filters is acceptable logic, provided you explain the scalability limits.

Python

```python
class RecommendationEngine:
    def get_matches(self, active_user: User, all_users: list):
        potential_matches = []
        
        for user in all_users:
            # 1. Self Check
            if user.id == active_user.id:
                continue
            
            # 2. Already Swiped Check
            if user.id in active_user.seen_users:
                continue

            # 3. Gender Filter (Simplified: strict opposite or same)
            # In reality, this is a complex preference object
            if active_user.gender == user.gender:
                continue 

            # 4. Location Filter
            dist = active_user.location.distance_to(user.location)
            if dist <= active_user.radius_filter:
                potential_matches.append(user)
        
        return potential_matches
```

#### C. The Service (Swipe Logic)

This is the brain of the application.

Python

```python
class TinderService:
    def __init__(self):
        self.users = {} # id -> User
        # Storage: Map who liked whom. 
        # Key: user_id, Value: Set of user_ids they LIKED
        self.likes = {} 
        self.matches = []
        self.recommendation_engine = RecommendationEngine()

    def add_user(self, user):
        self.users[user.id] = user
        self.likes[user.id] = set()

    def get_feed(self, user_id):
        user = self.users.get(user_id)
        if not user: return []
        
        # Pass the global list (Mocking DB access)
        return self.recommendation_engine.get_matches(user, list(self.users.values()))

    def swipe(self, actor_id, target_id, swipe_type):
        actor = self.users.get(actor_id)
        target = self.users.get(target_id)
        
        if not actor or not target: return

        # 1. Mark as Seen
        actor.seen_users.add(target_id)

        if swipe_type == SwipeType.PASS:
            print(f"{actor.name} passed on {target.name}")
            return

        # 2. Handle LIKE
        print(f"{actor.name} LIKED {target.name}")
        self.likes[actor_id].add(target_id)

        # 3. Check for Mutual Match (The "It's a Match" Logic)
        if self._check_match(actor_id, target_id):
            self._create_match(actor, target)

    def _check_match(self, actor_id, target_id):
        # Does Target already like Actor?
        if actor_id in self.likes.get(target_id, set()):
            return True
        return False

    def _create_match(self, user1, user2):
        match_id = str(uuid.uuid4())
        # In a real app, this triggers a Push Notification and creates a Chat Room
        print(f"\n💕 IT'S A MATCH! {user1.name} and {user2.name} like each other! 💕\n")
        self.matches.append({
            "id": match_id,
            "participants": {user1.name, user2.name}
        })
```

#### D. Driver Code

Python

```python
if __name__ == "__main__":
    app = TinderService()

    # 1. Create Users (Locations are simplified x,y coordinates)
    alice = User("Alice", Gender.FEMALE, 24, Location(0, 0))
    bob = User("Bob", Gender.MALE, 26, Location(10, 10)) # Distance ~14
    charlie = User("Charlie", Gender.MALE, 28, Location(500, 500)) # Too far

    app.add_user(alice)
    app.add_user(bob)
    app.add_user(charlie)

    # 2. Alice opens app
    feed = app.get_feed(alice.id)
    print(f"Alice's Feed: {feed}") 
    # Should see Bob (close). Charlie is excluded (too far).

    # 3. Alice Likes Bob
    app.swipe(alice.id, bob.id, SwipeType.LIKE)
    # No match yet, because Bob hasn't swiped Alice.

    # 4. Bob opens app and sees Alice
    feed_bob = app.get_feed(bob.id)
    print(f"Bob's Feed: {feed_bob}")

    # 5. Bob Likes Alice -> TRIGGER MATCH
    app.swipe(bob.id, alice.id, SwipeType.LIKE)
```

***

#### 5. Key Design Challenges & Interview Answers

#### 1. Spatial Indexing (The "Near Me" Query)

* Problem: Iterating through `all_users` to calculate distance is $$ $O(N)$ $$. If you have 10 million users, the feed fails.
* Solution: Use Geohashing or a QuadTree.
  * Geohash: Divides the world into a grid of strings (e.g., "u4pru..."). Users sharing the same prefix are close to each other.
  * DB Query: `SELECT * FROM Users WHERE geohash LIKE 'u4p%'`. This reduces search space from 10M to 1000.

#### 2. Sharding Strategies

* Question: How do you shard the database?
* Answer: Sharding by Location (Geosharding) is intuitive but risky (NYC is overloaded, Kansas is empty).
  * Better Approach: Shard by User ID for profile data. Store the "Location Index" (Geohash -> UserIDs) in a separate, specialized service (like Redis Geo or Elasticsearch).

#### 3. "Super Like" Feature

* Design: A "Super Like" is just a `SwipeType.SUPER_LIKE`.
* Logic: It bypasses the "Mutual" requirement for visibility. If Alice Super Likes Bob, when Bob opens the app, the engine explicitly _injects_ Alice at the top of Bob's feed with a special flag `is_super_like=True`.

#### 4. Unlimited vs Free Swipes

* Design: Decorator Pattern or Proxy Pattern on the `swipe` method.
  * Check `user.subscription_tier`.
  * If `Free`, check `daily_swipe_count < 10`.
  * If `Gold`, allow infinite.

#### 5. Profile Consistency (The "Ghost" Problem)

* Problem: Alice swipes right on Bob. 1 second later, Bob deletes his account. Alice matches with a ghost.
* Solution: Strong consistency is hard. Eventual consistency is better.
  * When Bob deletes account, publish `UserDeletedEvent`.
  * Swipe Service listens and removes Bob from all `likes` sets.
  * During the `_create_match` step, perform a final "Live Check" (`if user.is_active`) before confirming.

#### Next Steps

Would you like to explore "Design Splitwise" (Expense Sharing) or "Design a Stock Brokerage (Zerodha)"?

## WhatsApp Messenger

An end-to-end Low Level Design (LLD) for a Chat Application (like WhatsApp/Telegram).

This problem focuses on Real-Time Communication (WebSockets), Data Persistence (Chat History), and the State Management of messages (Sent $$ $\rightarrow$ $$ Delivered $$ $\rightarrow$ $$ Read).

***

#### 1. Requirements Clarification

* Core Features:
  * 1:1 Chat: Send text messages between two users.
  * Group Chat: Broadcast messages to multiple users.
  * Receipts: Manage status updates: `SENT` (1 tick), `DELIVERED` (2 ticks), `READ` (Blue ticks).
  * User Status: Online/Last Seen.
* Constraints:
  * Real-time: Low latency is crucial.
  * Offline Handling: If a user is offline, store the message and deliver when they reconnect.

***

#### 2. Core Architecture: The WebSocket Gateway

Unlike standard REST APIs (Request-Response), chat apps use Full-Duplex Communication (WebSockets or MQTT). The server pushes messages to the client without the client asking.

* Connection Map: The server must maintain a mapping: `UserID -> WebSocketConnection`.
* Pub/Sub (For Scale): If User A is connected to Server 1 and User B is connected to Server 2, we need a Pub/Sub mechanism (Redis/Kafka) to route the message between servers.

***

#### 3. Class Diagram

Code snippet

<figure><img src="../../../.gitbook/assets/image (187).png" alt=""><figcaption></figcaption></figure>



***

#### 4. Python Implementation

#### A. The Models

We need a robust Message class that tracks _who_ sent _what_ to _whom_.

Python

```python
import uuid
from enum import Enum
from datetime import datetime
import threading

class MessageStatus(Enum):
    SENT = 1      # Arrived at Server
    DELIVERED = 2 # Arrived at Recipient's Device
    READ = 3      # Opened by Recipient

class User:
    def __init__(self, uid, name):
        self.id = uid
        self.name = name
        self.is_online = False

class Message:
    def __init__(self, sender_id, content, receiver_id=None, group_id=None):
        self.id = str(uuid.uuid4())
        self.sender_id = sender_id
        self.receiver_id = receiver_id # Null if Group Chat
        self.group_id = group_id       # Null if 1:1 Chat
        self.content = content
        self.timestamp = datetime.now()
        self.status = MessageStatus.SENT

    def __repr__(self):
        target = f"Group({self.group_id})" if self.group_id else f"User({self.receiver_id})"
        return f"[Msg {self.id[:4]}] {self.sender_id} -> {target}: {self.content} ({self.status.name})"
```

#### B. The Connection Manager (The Socket Layer)

This mimics the WebSocket server. In a real implementation (e.g., using `socket.io` or `websockets` library), this holds the actual TCP socket objects.

Python

```python
class ConnectionManager:
    def __init__(self):
        # Map: UserID -> "Connection Object" (Mocked here as a simple callback/print)
        self.connections = {} 
        self.lock = threading.Lock()

    def connect(self, user_id):
        with self.lock:
            self.connections[user_id] = True
            print(f"User {user_id} connected.")

    def disconnect(self, user_id):
        with self.lock:
            if user_id in self.connections:
                del self.connections[user_id]
            print(f"User {user_id} disconnected.")

    def is_online(self, user_id):
        return user_id in self.connections

    def push_message(self, target_user_id, message):
        """
        Simulates pushing data via WebSocket.
        """
        if self.is_online(target_user_id):
            print(f"PUSH to {target_user_id}: {message}")
            return True # Delivery Successful
        else:
            print(f"User {target_user_id} is OFFLINE. Message queued.")
            return False
```

#### C. The Chat Service (Business Logic)

This handles 1:1 and Group logic, plus the "Store and Forward" mechanism for offline users.

Python

```python
class ChatService:
    def __init__(self):
        self.users = {}
        self.groups = {} # group_id -> List[user_ids]
        self.message_store = [] # Database
        self.connection_mgr = ConnectionManager()
        self.offline_queue = {} # user_id -> List[Message]

    def register_user(self, user):
        self.users[user.id] = user

    def create_group(self, group_name, creator_id):
        gid = str(uuid.uuid4())
        self.groups[gid] = [creator_id]
        print(f"Group '{group_name}' created (ID: {gid})")
        return gid

    def add_member_to_group(self, group_id, user_id):
        if group_id in self.groups:
            self.groups[group_id].append(user_id)

    # --- Messaging Logic ---

    def send_p2p_message(self, sender_id, receiver_id, content):
        msg = Message(sender_id, content, receiver_id=receiver_id)
        self.message_store.append(msg)
        
        # Try to deliver immediately
        delivered = self.connection_mgr.push_message(receiver_id, msg)
        
        if delivered:
            msg.status = MessageStatus.DELIVERED
            # Notify Sender that it was delivered (Double Tick)
            self.connection_mgr.push_message(sender_id, f"Receipt: Msg {msg.id[:4]} DELIVERED")
        else:
            # Queue for later
            if receiver_id not in self.offline_queue:
                self.offline_queue[receiver_id] = []
            self.offline_queue[receiver_id].append(msg)

    def send_group_message(self, sender_id, group_id, content):
        if group_id not in self.groups: return
        
        msg = Message(sender_id, content, group_id=group_id)
        self.message_store.append(msg)
        
        members = self.groups[group_id]
        for member_id in members:
            if member_id == sender_id: continue # Don't echo to self
            
            delivered = self.connection_mgr.push_message(member_id, msg)
            if not delivered:
                if member_id not in self.offline_queue:
                    self.offline_queue[member_id] = []
                self.offline_queue[member_id].append(msg)

    # --- Reconection Logic ---
    
    def user_comes_online(self, user_id):
        self.connection_mgr.connect(user_id)
        
        # Flush offline queue
        pending_msgs = self.offline_queue.get(user_id, [])
        if pending_msgs:
            print(f"Flushing {len(pending_msgs)} pending messages to {user_id}...")
            for msg in pending_msgs:
                self.connection_mgr.push_message(user_id, msg)
                msg.status = MessageStatus.DELIVERED
                # Notify original Sender
                self.connection_mgr.push_message(msg.sender_id, f"Receipt: Msg {msg.id[:4]} DELIVERED")
            
            # Clear queue
            self.offline_queue[user_id] = []
```

#### D. Driver Code

Python

```python
if __name__ == "__main__":
    app = ChatService()
    
    # 1. Setup Users
    u1 = User("Alice", "Alice")
    u2 = User("Bob", "Bob")
    u3 = User("Charlie", "Charlie")
    
    app.register_user(u1)
    app.register_user(u2)
    app.register_user(u3)
    
    # 2. Online/Offline Simulation
    app.connection_mgr.connect(u1.id) # Alice Online
    # Bob is Offline
    
    # 3. P2P Chat (Alice -> Bob)
    print("\n--- Alice sends msg to Bob (Offline) ---")
    app.send_p2p_message(u1.id, u2.id, "Hi Bob, are you there?")
    # Output should show "User Bob is OFFLINE. Message queued."
    
    # 4. Bob Connects
    print("\n--- Bob comes online ---")
    app.user_comes_online(u2.id)
    # Bob should receive the pending message.
    # Alice should receive the DELIVERED receipt.
    
    # 5. Group Chat
    print("\n--- Group Chat Simulation ---")
    gid = app.create_group("Tech Talk", u1.id)
    app.add_member_to_group(gid, u2.id)
    app.add_member_to_group(gid, u3.id)
    
    app.send_group_message(u2.id, gid, "Hello Everyone!")
    # Alice (Online) gets it.
    # Charlie (Offline) gets queued.
```

***

#### 5. Key Design Challenges & Interview Answers

#### 1. Protocol: HTTP vs WebSocket

* Question: Why can't we just use HTTP POST?
* Answer: HTTP is request-initiated. To get new messages, the client would have to Poll (`setInterval` every 1s). This kills battery and increases server load.
* Solution: WebSockets provide a persistent connection. The server can push data anytime.

#### 2. Message Ordering (The "Clock" Problem)

* Problem: In a distributed system, Alice might send "Msg A" then "Msg B", but Bob receives "Msg B" first due to network lag.
* Solution: Use Sequence Numbers (Logical Clocks) or timestamps. The Client App is responsible for re-ordering the messages in the UI buffer based on `timestamp` before displaying them.

#### 3. End-to-End Encryption (E2EE)

* Question: Does the server read the messages?
* Answer: In WhatsApp, no.
* Design: The LLD `send_message` method doesn't send plain text.
  1. Alice generates a Symmetric Key.
  2. Encrypts: `EncryptedMsg = Encrypt(Message, Key)`.
  3. Encrypts Key: `EncryptedKey = Encrypt(Key, Bob_Public_Key)`.
  4. Server routes the encrypted blob. It cannot decrypt it.

#### 4. "Last Seen" Scalability

* Problem: If I have 500 friends, and I go online, do I notify all 500 immediately? That's a "Thundering Herd".
* Solution: Lazy Fetching or Subscription.
  * I only subscribe to the presence updates of people _whose chat window I currently have open_.
  * For the contact list, we poll infrequently (e.g., every 30s) or only update when scrolling into view.

#### 5. Media Handling (Images/Video)

* Do not send binary image data over the WebSocket. It blocks the chat channel.
* Flow:
  1. Client uploads Image to Object Storage (S3) via HTTP API.
  2. Gets URL: `https://s3.aws.com/img_123.jpg`.
  3. Sends the _URL_ (Text) via WebSocket as a normal message.
  4. Receiver downloads the image from the URL.

#### Next Step

Would you like to solve "Design a Code Deployment System (CI/CD Pipeline)" or "Design a Splitwise (Expense Sharing)" system next?



## Gmail&#x20;



An end-to-end Low Level Design (LLD) for an Email Service (like Gmail).

This problem tests your understanding of Data Modeling (Labels vs. Folders), Search (indexing text), and State Management (Draft $$ $\rightarrow$ $$ Sent $$ $\rightarrow$ $$ Inbox).

***

#### 1. Requirements Clarification

* Core Features:
  * Compose/Send: Send emails to one or multiple recipients (To, CC, BCC).
  * Inbox management: Receive emails.
  * Organization: Apply Labels (Work, Personal) and standard folders (Inbox, Sent, Trash).
  * Search: Find emails by subject or sender.
  * Drafts: Save unfinished emails automatically.
* Key Distinction (Gmail Specific):
  * Gmail uses Labels, not Folders.
  * In a "Folder" system (Outlook), an email exists in _one_ location.
  * In a "Label" system (Gmail), an email is a single entity that can have _multiple_ tags (e.g., "Inbox", "Work", "Urgent" all at once).

***

#### 2. Core Architecture: The Label System

We need to decouple the Email Content from its Organization.

1. Email Storage: A global collection of unique Email objects.
2. User View: Users don't "hold" the email object; they hold a reference to it associated with a Label.
3. Sending Flow:
   * Create Email Object.
   * Add to Sender's "Sent" Label.
   * Add to Receiver's "Inbox" Label.

***

#### 3. Class Diagram

Code snippet

<figure><img src="../../../.gitbook/assets/image (188).png" alt=""><figcaption></figcaption></figure>

***

#### 4. Python Implementation

#### A. The Models

We need to handle the nuances of `BCC` (Blind Carbon Copy) — the recipients list stored in the email object usually hides BCC, but for this LLD, we store it to ensure delivery, then mask it during display.

Python

```python
import uuid
from datetime import datetime
from enum import Enum
from collections import defaultdict

class LabelType(Enum):
    SYSTEM = 1 # Inbox, Sent, Trash, Spam
    CUSTOM = 2 # Work, Receipts, Travel

class User:
    def __init__(self, email_addr, name):
        self.email = email_addr
        self.name = name
    
    def __repr__(self):
        return f"{self.name} <{self.email}>"

class Email:
    def __init__(self, sender, subject, body, to_list, cc_list=None, bcc_list=None):
        self.id = str(uuid.uuid4())
        self.sender = sender
        self.subject = subject
        self.body = body
        self.to_list = to_list # List[User]
        self.cc_list = cc_list if cc_list else []
        self.bcc_list = bcc_list if bcc_list else []
        self.timestamp = datetime.now()
        # In a real system, attachments would be links to Object Storage (S3)
        self.attachments = [] 

    def __repr__(self):
        return f"[Subject: {self.subject}] From: {self.sender.name}"
```

#### B. The Mailbox (User's View)

This class manages how a specific user sees their emails. It maps `Label -> List[Email]`.

Python

```python
class Mailbox:
    def __init__(self, user):
        self.user = user
        # Map: LabelName -> List[Email]
        self.emails_by_label = defaultdict(list)
        self._init_system_labels()

    def _init_system_labels(self):
        self.create_label("Inbox", LabelType.SYSTEM)
        self.create_label("Sent", LabelType.SYSTEM)
        self.create_label("Trash", LabelType.SYSTEM)
        self.create_label("Drafts", LabelType.SYSTEM)

    def create_label(self, name, ltype=LabelType.CUSTOM):
        if name not in self.emails_by_label:
            self.emails_by_label[name] = []

    def add_email(self, email, label="Inbox"):
        if label in self.emails_by_label:
            self.emails_by_label[label].append(email)

    def remove_email(self, email, label):
        # In Gmail, 'Deleting' usually just moves to Trash.
        # Removing a label (e.g., 'Work') keeps it in 'Inbox' if it was there.
        if label in self.emails_by_label:
            if email in self.emails_by_label[label]:
                self.emails_by_label[label].remove(email)

    def get_emails(self, label="Inbox"):
        # Sort by latest first
        return sorted(
            self.emails_by_label.get(label, []), 
            key=lambda x: x.timestamp, 
            reverse=True
        )
```

#### C. The Service (Controller)

This handles the transaction of moving an email from Sender to Receiver.

Python

```python
class GmailSystem:
    def __init__(self):
        self.users = {} # email_str -> User Object
        self.mailboxes = {} # email_str -> Mailbox Object

    def create_user(self, email, name):
        user = User(email, name)
        self.users[email] = user
        self.mailboxes[email] = Mailbox(user)
        return user

    def save_draft(self, email_obj):
        sender_email = email_obj.sender.email
        mailbox = self.mailboxes.get(sender_email)
        mailbox.add_email(email_obj, "Drafts")
        print(f"Saved Draft for {sender_email}")

    def send_email(self, email_obj):
        sender_email = email_obj.sender.email
        sender_mailbox = self.mailboxes.get(sender_email)

        # 1. Move from Drafts (if exists) to Sent
        # Note: We must check if it was in drafts first to avoid errors
        sender_mailbox.remove_email(email_obj, "Drafts")
        sender_mailbox.add_email(email_obj, "Sent")

        # 2. Distribute to Recipients (Inbox)
        all_recipients = email_obj.to_list + email_obj.cc_list + email_obj.bcc_list
        
        for recipient in all_recipients:
            if recipient.email in self.mailboxes:
                recipient_box = self.mailboxes[recipient.email]
                # Filter Logic (Spam/Rules) would go here
                recipient_box.add_email(email_obj, "Inbox")
        
        print(f"Email sent from {sender_email} to {len(all_recipients)} recipients.")

    def search(self, user_email, query):
        """Simple Keyword Search across all labels"""
        mailbox = self.mailboxes.get(user_email)
        results = set()
        
        # Search all lists
        for label, emails in mailbox.emails_by_label.items():
            if label == "Trash": continue # Usually don't search trash
            for email in emails:
                if (query.lower() in email.subject.lower() or 
                    query.lower() in email.body.lower()):
                    results.add(email)
        
        return list(results)
    
    def delete_email(self, user_email, email_obj):
        """Moves email to Trash"""
        mailbox = self.mailboxes.get(user_email)
        
        # Remove from all current labels
        # (This is a simplified logic. In reality, you might just archive it)
        for label in list(mailbox.emails_by_label.keys()):
            if email_obj in mailbox.emails_by_label[label]:
                mailbox.remove_email(email_obj, label)
        
        # Add to Trash
        mailbox.add_email(email_obj, "Trash")
        print(f"Moved email '{email_obj.subject}' to Trash for {user_email}")
```

#### D. Driver Code

Python

```python
if __name__ == "__main__":
    gmail = GmailSystem()

    # 1. Create Users
    alice = gmail.create_user("alice@gmail.com", "Alice")
    bob = gmail.create_user("bob@gmail.com", "Bob")
    charlie = gmail.create_user("charlie@gmail.com", "Charlie")

    # 2. Alice drafts an email
    draft_email = Email(
        sender=alice,
        subject="Project Update",
        body="Hey team, the project is live!",
        to_list=[bob],
        cc_list=[charlie]
    )
    gmail.save_draft(draft_email)

    # Verify Draft
    print(f"Alice's Drafts: {gmail.mailboxes['alice@gmail.com'].get_emails('Drafts')}")

    # 3. Alice Sends it
    gmail.send_email(draft_email)

    # 4. Verification
    print("\n--- After Sending ---")
    print(f"Alice's Sent: {gmail.mailboxes['alice@gmail.com'].get_emails('Sent')}")
    print(f"Bob's Inbox: {gmail.mailboxes['bob@gmail.com'].get_emails('Inbox')}")
    print(f"Charlie's Inbox: {gmail.mailboxes['charlie@gmail.com'].get_emails('Inbox')}")

    # 5. Search
    print("\n--- Search Results ---")
    results = gmail.search("bob@gmail.com", "Project")
    print(f"Bob searched 'Project': {results}")
```

***

#### 5. Key Design Challenges & Interview Answers

#### 1. Protocols (SMTP vs IMAP)

* Question: How does the email actually travel?
* Answer:
  * SMTP (Simple Mail Transfer Protocol): Used for Sending. It pushes data from Alice's Server $$ $\rightarrow$ $$ Bob's Server.
  * IMAP/POP3: Used for Retrieving. It pulls data from Bob's Server $$ $\rightarrow$ $$ Bob's Phone/Laptop.
  * _LLD Context:_ Our `GmailService.send_email` mimics the SMTP delivery agent, and `get_emails` mimics the IMAP fetch command.

#### 2. Spam Filters (Chain of Responsibility)

* Design: Before adding an email to the `Inbox`, pass it through a filter pipeline.
* Pattern: Chain of Responsibility.
  * `VirusScanner` $$ $\rightarrow$ $$ `SpamKeywordFilter` $$ $\rightarrow$ $$ `BlacklistFilter` $$ $\rightarrow$ $$ `Inbox`.
  * If any filter flags it, divert to `Spam` label instead of `Inbox`.

#### 3. Attachment Handling

* Constraint: You cannot store 25MB files in a MySQL/Postgres row.
* Solution:
  * DB: Stores Metadata (filename, size, S3\_Key).
  * Object Store (S3): Stores the actual binary data.
  * When an email is sent, the attachment is uploaded to S3 first, and the URL is embedded in the email object.

#### 4. Search Scalability (Inverted Index)

* Question: `query in body.lower()` is $$ $O(N)$ $$. It's too slow for 10GB of text.
* Solution: Use an Inverted Index (Lucene/Elasticsearch).
  * When email arrives: Tokenize body ("Project", "Live", "Team").
  * Update Index: `"project" -> [Email_ID_1, Email_ID_5]`.
  * Search is now $$ $O(1)$ $$.

#### 5. Threading (Conversation View)

* Feature: Gmail groups replies together.
* LLD: The `Email` object needs a `parent_email_id` or `thread_id`.
* Logic: When Bob replies to Alice:
  * `ReplyEmail.thread_id = OriginalEmail.thread_id`.
  * The UI groups all emails with the same `thread_id`.

Would you like to try "Design a Logger Library" or "Design an Airline Reservation System" next?

## Game Engine like Unreal&#x20;



An end-to-end Low Level Design (LLD) for a Game Engine (like a simplified Unity or Unreal Engine).

Designing a full engine is massive. For Machine Coding, the focus is strictly on the Core Architecture Pattern: The Entity-Component-System (ECS) or the Composition Pattern.

Traditional OOP (Inheritance) fails in games (e.g., `class FlyingEnemy extends Enemy` works, but what about `FlyingSwimmingEnemy`?). ECS solves this by using Composition over Inheritance.

***

#### 1. Requirements Clarification

* Core Entities:
  * GameObject (Entity): A generic container (e.g., Player, Tree, Camera).
  * Component: Data or Logic attached to an object (e.g., `Transform`, `RigidBody`, `MeshRenderer`).
* Core Systems:
  * Scene Management: Holding the hierarchy of objects.
  * Game Loop: The heartbeat (Input $$ $\rightarrow$ $$ Update $$ $\rightarrow$ $$ Render).
  * Physics/Rendering: Subsystems that act on specific components.
* Goal: Create a Mario-like object that has position, can move, and can be drawn.

***

#### 2. Core Architecture: Entity-Component (EC)

1. Entity: Just an ID or a list of Components. It doesn't know _what_ it is.
2. Component: Pure data (or logic in simpler Unity-style designs).
3. System: Iterates over entities that have specific components and performs actions.

***

#### 3. Class Diagram

Code snippet

<figure><img src="../../../.gitbook/assets/image (189).png" alt=""><figcaption></figcaption></figure>

***

#### 4. Python Implementation

#### A. The Core (Entity & Component)

This is the foundation. Every object in the game will inherit from or use these classes.

Python

```python
from abc import ABC, abstractmethod
import time
import uuid

# 1. The Abstract Component
class Component(ABC):
    def __init__(self):
        self.gameObject = None # Reference to container

    @abstractmethod
    def update(self, delta_time):
        """Logic loop (Physics, AI)"""
        pass

    def render(self):
        """Draw loop"""
        pass

# 2. The Entity (GameObject)
class GameObject:
    def __init__(self, name):
        self.id = str(uuid.uuid4())
        self.name = name
        self.components = []
        self.is_active = True

        # Every object needs a Transform by default
        self.transform = Transform() 
        self.add_component(self.transform)

    def add_component(self, component: Component):
        component.gameObject = self
        self.components.append(component)
        return component # Return for chaining

    def get_component(self, component_type):
        for c in self.components:
            if isinstance(c, component_type):
                return c
        return None

    def update(self, delta_time):
        if not self.is_active: return
        for c in self.components:
            c.update(delta_time)

    def render(self):
        if not self.is_active: return
        for c in self.components:
            c.render()
```

#### B. Standard Components

These are the building blocks developers would use to make a game.

Python

```python
# 1. Transform (Position/Rotation/Scale)
class Transform(Component):
    def __init__(self, x=0, y=0):
        super().__init__()
        self.x = x
        self.y = y

    def translate(self, dx, dy):
        self.x += dx
        self.y += dy
    
    def update(self, delta_time):
        pass # Transform usually doesn't update itself, Physics updates it

    def __repr__(self):
        return f"({self.x:.2f}, {self.y:.2f})"


# 2. RigidBody (Physics)
class RigidBody(Component):
    def __init__(self, mass=1.0):
        super().__init__()
        self.mass = mass
        self.velocity_x = 0
        self.velocity_y = 0

    def apply_force(self, fx, fy):
        # F = ma -> a = F/m
        ax = fx / self.mass
        ay = fy / self.mass
        self.velocity_x += ax
        self.velocity_y += ay

    def update(self, delta_time):
        # Update Transform based on Velocity
        # x = x + v * dt
        transform = self.gameObject.transform
        transform.x += self.velocity_x * delta_time
        transform.y += self.velocity_y * delta_time
        
        # Simulate simple Gravity
        if transform.y > 0: # If in air
            self.velocity_y -= 9.8 * delta_time 
            if transform.y < 0: transform.y = 0 # Ground collision


# 3. SpriteRenderer (Visuals)
class SpriteRenderer(Component):
    def __init__(self, sprite_char):
        super().__init__()
        self.sprite = sprite_char

    def update(self, delta_time):
        pass

    def render(self):
        pos = self.gameObject.transform
        print(f"Rendering '{self.sprite}' at {pos}")
```

#### C. The Game Engine (Loop & Scene)

This orchestrates the lifecycle.

Python

```python
class Scene:
    def __init__(self):
        self.objects = []

    def add_object(self, obj):
        self.objects.append(obj)

    def update(self, delta_time):
        for obj in self.objects:
            obj.update(delta_time)

    def render(self):
        print("\n--- Frame Start ---")
        for obj in self.objects:
            obj.render()
        print("--- Frame End ---")

class GameEngine:
    def __init__(self):
        self.scene = Scene()
        self.is_running = False

    def start(self):
        self.is_running = True
        self.loop()

    def loop(self):
        last_time = time.time()
        
        # Simulation: Run for 5 frames
        frames = 0
        while frames < 5:
            current_time = time.time()
            delta_time = current_time - last_time
            last_time = current_time

            # 1. Process Input (Mocked)
            self.process_input()

            # 2. Update Physics/Logic
            self.scene.update(delta_time)

            # 3. Render
            self.scene.render()
            
            # Sleep to simulate 60 FPS (approx 0.016s)
            time.sleep(0.5) # Slowed down for demo visualization
            frames += 1

    def process_input(self):
        # In real engine, this checks keyboard/mouse
        pass
```

#### D. Driver Code (User creating a Game)

Python

```python
class PlayerController(Component):
    """Custom script written by a Game Developer"""
    def update(self, delta_time):
        # Simulate pressing "Right Arrow"
        rb = self.gameObject.get_component(RigidBody)
        if rb:
            rb.apply_force(10.0 * delta_time, 50.0 * delta_time) # Jump forward

if __name__ == "__main__":
    engine = GameEngine()

    # 1. Create Player "Mario"
    mario = GameObject("Mario")
    
    # Add Physics
    mario.add_component(RigidBody(mass=10)) 
    
    # Add Visuals
    mario.add_component(SpriteRenderer("M"))
    
    # Add Custom Logic
    mario.add_component(PlayerController())

    engine.scene.add_object(mario)

    # 2. Create Static Object "Tree"
    tree = GameObject("Tree")
    tree.transform.translate(5, 0)
    tree.add_component(SpriteRenderer("T"))
    engine.scene.add_object(tree)

    # 3. Run
    engine.start()
```

***

#### 5. Key Design Challenges & Interview Answers

#### 1. ECS vs OOP

* Question: Why not just make a `Player` class?
* Answer:
  * OOP: If `Player` inherits from `Character`, and `Boat` inherits from `Vehicle`, how do you make a `Player` drive a `Boat`? You end up with deep, messy inheritance trees.
  * ECS: A `Player` is just an ID with a `ControllerComponent`. A `Boat` is an ID with a `BuoyancyComponent`. To drive, you simply parent the Player Entity to the Boat Entity. It's flexible and modular.

#### 2. Update Order (The Dependency Problem)

* Problem: The Camera follows the Player. If `Camera.update()` runs _before_ `Player.update()`, the camera will jitter because it's tracking the _old_ position.
* Solution: Introduce Phases.
  * `Update()`: For Logic/Input.
  * `LateUpdate()`: For Camera/UI (Runs after all Updates are done).
  * `FixedUpdate()`: For Physics (Runs at constant time intervals, e.g., 0.02s).

#### 3. Resource Management (Flyweight Pattern)

* Problem: If I have 1,000 trees, and each loads "tree.png" (5MB) into RAM, I crash memory.
* Solution: ResourceManager.
  * The `SpriteRenderer` asks `ResourceManager.get_texture("tree.png")`.
  * The Manager checks a Map. If loaded, return reference. If not, load from disk.
  * All 1,000 trees share the same single Texture object in memory.

#### 4. Collision Detection (Spatial Partitioning)

* Problem: Checking if Mario collides with every object in the world is $$ $O(N^2)$ $$.
* Solution: Use a QuadTree (2D) or Octree (3D).
  * Only check collisions with objects in the same grid cell/node.

#### 5. Serialization (Save/Load)

* Because we used Components (Data), saving is easy.
* Loop through all GameObjects $$ $\rightarrow$ $$ Loop through all Components $$ $\rightarrow$ $$ Serialize to JSON.
* `{ "id": "mario", "components": [ {"type": "Transform", "x": 10}, {"type": "RigidBody", "mass": 10} ] }`.



## Real-Time Chat System with Millions of Users



Here is a detailed Low Level Design (LLD) for a Real-Time Chat System optimized for High Scalability (Millions of Users).

While a basic chat app can run on one server, a system for millions of users requires a distributed architecture. The core challenge is that WebSockets are stateful. If User A is connected to Server 1 and User B is connected to Server 2, Server 1 cannot directly send a message to User B.

***

#### 1. Requirements Clarification

* Scale: Support millions of concurrent connections.
* Latency: Real-time delivery (< 100ms).
* Features:
  * 1:1 Chat & Group Chat.
  * Cross-Server Communication (User A on Node 1 $$ $\rightarrow$ $$ User B on Node 2).
  * Persistent History (scalable storage).
  * Presence (Online/Offline status).

***

#### 2. Core Architecture: The "Gateway + Pub/Sub" Pattern

To solve the stateful server problem, we use a Pub/Sub (Publish-Subscribe) mechanism (like Redis or Kafka).

1. WebSocket Gateway: A cluster of servers holding active TCP connections.
2. Session Store (Redis): Maps `UserID` $$ $\rightarrow$ $$ `GatewayID`. (Where is User A connected?).
3. Pub/Sub: Used to route messages between Gateways.

The Flow:

1. User A sends message to User B.
2. Gateway 1 looks up User B in Session Store. Result: "User B is on Gateway 5".
3. Gateway 1 publishes message to Topic: Gateway\_5.
4. Gateway 5 receives the message and pushes it down the WebSocket to User B.

***

#### 3. Class Diagram

Code snippet

<figure><img src="../../../.gitbook/assets/image (190).png" alt=""><figcaption></figcaption></figure>

***

#### 4. Python Implementation

We will simulate a distributed system by creating multiple instances of `WebSocketGateway` and a mock `RedisPubSub`.

#### A. Infrastructure Mocks (Redis & Session Store)

Python

```python
import uuid
import threading
from collections import defaultdict

# 1. Mock Redis Pub/Sub
class MockRedisPubSub:
    def __init__(self):
        self.channels = defaultdict(list) # ChannelID -> List of Callbacks
        self.lock = threading.Lock()

    def subscribe(self, channel, callback):
        with self.lock:
            self.channels[channel].append(callback)
            print(f"[Redis] Subscribed to channel: {channel}")

    def publish(self, channel, message):
        # Asynchronously trigger callbacks
        with self.lock:
            if channel in self.channels:
                for callback in self.channels[channel]:
                    callback(message)

# 2. Mock Global Session Store (Redis Key-Value)
class SessionStore:
    def __init__(self):
        self.user_gateway_map = {} # UserID -> GatewayID

    def set_user_gateway(self, user_id, gateway_id):
        self.user_gateway_map[user_id] = gateway_id

    def get_gateway(self, user_id):
        return self.user_gateway_map.get(user_id)
```

#### B. The Gateway Node (The Server)

This represents one physical server in the fleet. It handles connections and listens to the Pub/Sub for messages destined for its connected users.

Python

```python
class WebSocketGateway:
    def __init__(self, gateway_id, pub_sub, session_store):
        self.id = gateway_id
        self.pub_sub = pub_sub
        self.session_store = session_store
        
        # Local connections: UserID -> "Socket" (Mock)
        self.local_connections = {}
        
        # Subscribe to my own channel to receive messages from other servers
        self.pub_sub.subscribe(self.id, self.on_inter_server_message)

    def client_connect(self, user_id):
        # 1. Store local connection
        self.local_connections[user_id] = f"SOCKET_TO_{user_id}"
        
        # 2. Update Global Session Store (Distributed Registry)
        self.session_store.set_user_gateway(user_id, self.id)
        print(f"[{self.id}] User {user_id} connected.")

    def client_send_msg(self, sender_id, receiver_id, text):
        """
        User A sends a message. We must find where User B is.
        """
        print(f"[{self.id}] Received msg from {sender_id} to {receiver_id}")
        
        # 1. Check if recipient is local (Optimization)
        if receiver_id in self.local_connections:
            self.push_to_client(receiver_id, text)
            return

        # 2. If not local, find which gateway they are on
        target_gateway = self.session_store.get_gateway(receiver_id)
        
        if target_gateway:
            # 3. Publish to that Gateway's channel
            msg_payload = {"to": receiver_id, "from": sender_id, "text": text}
            print(f"[{self.id}] Routing msg to {target_gateway} via Redis")
            self.pub_sub.publish(target_gateway, msg_payload)
        else:
            print(f"[{self.id}] User {receiver_id} is OFFLINE. Storing in DB...")

    def on_inter_server_message(self, message):
        """
        Callback when Redis pushes a message to this Gateway
        """
        recipient = message["to"]
        if recipient in self.local_connections:
            print(f"[{self.id}] Handling Inter-Server Msg -> Pushing to {recipient}")
            self.push_to_client(recipient, f"{message['from']}: {message['text']}")

    def push_to_client(self, user_id, text):
        # Simulate WebSocket Push
        print(f"    --> 🟢 PUSH SUCCESS to Device({user_id}): '{text}'")
```

#### C. Driver Code (Simulating Distributed Cluster)

Python

```python
if __name__ == "__main__":
    # Shared Infrastructure
    redis = MockRedisPubSub()
    sessions = SessionStore()

    # Spin up 2 simulated Servers
    server_us_east = WebSocketGateway("Gateway_US_1", redis, sessions)
    server_eu_west = WebSocketGateway("Gateway_EU_1", redis, sessions)

    print("--- Simulation Start ---\n")

    # 1. User A connects to US Server
    server_us_east.client_connect("User_A")

    # 2. User B connects to EU Server
    server_eu_west.client_connect("User_B")

    print("\n--- Scenario: Cross-Server Chat ---\n")
    
    # 3. User A (US) sends message to User B (EU)
    # Flow: US Server -> Redis -> EU Server -> User B
    server_us_east.client_send_msg("User_A", "User_B", "Hello from America!")

    print("\n--- Scenario: Local Chat ---\n")
    
    # 4. User C connects to US Server (Same as A)
    server_us_east.client_connect("User_C")
    
    # 5. User A sends to User C
    # Flow: US Server -> User C (No Redis needed)
    server_us_east.client_send_msg("User_A", "User_C", "Hello Neighbor!")
```

***

#### 5. Key Design Decisions for Scale

#### 1. Database Schema (NoSQL is mandatory)

RDBMS (MySQL) cannot handle the write throughput of millions of messages. We use Cassandra or DynamoDB.

Schema Design (Cassandra):

We prioritize Reading messages for a specific chat.

* Partition Key: `channel_id` (or `chat_id`) -> Keeps all messages for a chat on one node.
* Clustering Key: `message_id` (TimeUUID) -> Sorts messages by time.

SQL

```sql
CREATE TABLE chat_history (
    channel_id uuid,
    message_id timeuuid,
    sender_id text,
    content text,
    PRIMARY KEY ((channel_id), message_id)
) WITH CLUSTERING ORDER BY (message_id DESC);
```

#### 2. Group Chat "Fan-out" (The Instagram Problem)

When a celebrity sends a message to a group with 1 million members:

* Simple Loop: Iterating 1 million times to publish 1 million WebSocket frames will choke the server.
* Solution: Batched Fan-out Service.
  * The Gateway sends the message to a Kafka topic `GroupMessages`.
  * A fleet of "Push Workers" consumes the topic.
  * Worker 1 handles users A-M, Worker 2 handles N-Z, etc.

#### 3. Presence System (Heartbeats)

* Problem: Updating the DB every time a user disconnects is expensive.
* Solution: Redis with TTL (Time To Live).
  * Client sends a "Heartbeat" packet every 30 seconds.
  * Server does `Redis.set(user_id, "Online", TTL=40s)`.
  * If the heartbeat stops (app crash, internet loss), the key expires automatically after 40s. User appears "Offline".

#### 4. Media Handling

* Chat servers never handle binary files (Images/Videos).
* Flow:
  1. Client uploads image to S3 (Object Storage).
  2. Client gets URL.
  3. Client sends _text message_ containing the URL.
  4. Receiver downloads from S3.

#### 5. ID Generation

* We cannot use auto-increment IDs (MySQL) in a distributed system.
* Use Snowflake ID (Twitter's approach) or UUID v1 (Time-based) to generate sortable, unique 64-bit integers across distributed nodes.



## Design (LLD) Video Conferencing App like Zoom

An end-to-end Low Level Design (LLD) for a Video Conferencing Application (like Zoom, Google Meet, or Teams).

Disclaimer: In a Machine Coding round, you are not expected to write video compression algorithms (H.264) or implement raw socket programming for video streams. You are expected to design the Control Plane (Signaling, Meeting Management) and explain the Data Plane (how video flows).

***

#### 1. Requirements Clarification

* Core Entities:
  * Meeting (Room): A virtual space with a unique ID.
  * Participant: A user with permissions (Host/Guest).
  * Stream: Audio/Video tracks.
* Operations:
  * `create_meeting()`
  * `join_meeting(meeting_id)`
  * `leave_meeting()`
  * `mute_audio()` / `stop_video()`
* Technical Constraint:
  * Real-time: Latency must be minimal (< 200ms).
  * Topology: How do 10 people see each other? (We will discuss Mesh vs. SFU).

***

#### 2. Core Architecture: WebRTC & Signaling

Video chat relies on WebRTC. However, WebRTC needs a Signaling Server to set up the connection.

1. Signaling (The Handshake): Before Alice and Bob can send video, they must exchange metadata via a server (HTTP/WebSocket):
   * "I am Alice, here is my IP address" (ICE Candidates).
   * "I support HD video" (SDP - Session Description Protocol).
2. Media (The Stream): Once connected, video packets usually flow via UDP.
   * P2P (Mesh): Every client connects to every other client. (Bad for >3 users).
   * SFU (Selective Forwarding Unit): Everyone sends video to a central server; the server forwards it to others. (Standard for Zoom).

***

#### 3. Class Diagram

We will focus on the Signaling and Meeting Management layer.

Code snippet

<figure><img src="../../../.gitbook/assets/image (191).png" alt=""><figcaption></figcaption></figure>

***

#### 4. Python Implementation

#### A. The Models (Meeting & Participant)

This manages the _state_ of the conference.

Python

```python
import uuid
from enum import Enum
import threading

class EventType(Enum):
    OFFER = 1           # "I want to connect"
    ANSWER = 2          # "I accept"
    ICE_CANDIDATE = 3   # "Here is my network path"
    USER_JOINED = 4
    USER_LEFT = 5

class User:
    def __init__(self, name):
        self.id = str(uuid.uuid4())[:8]
        self.name = name

class Participant:
    def __init__(self, user, role="GUEST"):
        self.user = user
        self.role = role # HOST or GUEST
        self.audio_on = True
        self.video_on = True
        # In real WebRTC, this holds the RTCPeerConnection object
        self.connection_id = str(uuid.uuid4()) 

    def update_media(self, audio, video):
        self.audio_on = audio
        self.video_on = video

class Meeting:
    def __init__(self, host: User):
        self.id = str(uuid.uuid4())[:8]
        self.participants = {} # Map: user_id -> Participant
        
        # Add Host automatically
        host_participant = Participant(host, role="HOST")
        self.participants[host.id] = host_participant
        self.is_active = True
        print(f"Meeting {self.id} started by {host.name}")

    def add_participant(self, user):
        if not self.is_active:
            raise Exception("Meeting Ended")
        
        p = Participant(user)
        self.participants[user.id] = p
        return p

    def remove_participant(self, user_id):
        if user_id in self.participants:
            del self.participants[user_id]
            # Logic: If host leaves, verify if meeting should end or reassign host
            
    def get_participants(self):
        return list(self.participants.values())
```

#### B. The Signaling Service (The Traffic Cop)

This simulates the WebSocket server that passes messages between clients.

Python

```python
class SignalingService:
    def __init__(self):
        self.meetings = {} # meeting_id -> Meeting

    def create_meeting(self, host_user):
        meeting = Meeting(host_user)
        self.meetings[meeting.id] = meeting
        return meeting.id

    def join_meeting(self, user, meeting_id):
        if meeting_id not in self.meetings:
            print("Meeting not found.")
            return False

        meeting = self.meetings[meeting_id]
        new_participant = meeting.add_participant(user)
        
        # 1. Notify others that X joined
        self._broadcast(meeting_id, {
            "type": EventType.USER_JOINED,
            "user_id": user.id,
            "name": user.name
        }, exclude_user=user.id)
        
        print(f"{user.name} joined meeting {meeting_id}")
        return True

    def leave_meeting(self, user_id, meeting_id):
        if meeting_id in self.meetings:
            meeting = self.meetings[meeting_id]
            meeting.remove_participant(user_id)
            self._broadcast(meeting_id, {
                "type": EventType.USER_LEFT,
                "user_id": user_id
            })

    def send_signal(self, meeting_id, from_user_id, to_user_id, signal_type, payload):
        """
        WebRTC Signaling: 
        Passes opaque data (SDP/ICE) from Client A to Client B.
        The server does not read the payload, just routes it.
        """
        if meeting_id not in self.meetings: return

        # Simulate routing (In reality, this sends a WebSocket message)
        print(f"[Signal] {signal_type.name} from {from_user_id} -> {to_user_id} : {payload}")

    def _broadcast(self, meeting_id, message, exclude_user=None):
        """
        Simulate sending a message to everyone in the room.
        """
        meeting = self.meetings[meeting_id]
        recipients = [p.user.name for p in meeting.get_participants() if p.user.id != exclude_user]
        print(f"--> [Broadcast to {recipients}]: {message}")
```

#### C. Driver Code (Simulating the Flow)

Python

```python
if __name__ == "__main__":
    zoom = SignalingService()

    # 1. Alice creates a meeting
    alice = User("Alice")
    meeting_id = zoom.create_meeting(alice)

    # 2. Bob joins
    bob = User("Bob")
    zoom.join_meeting(bob, meeting_id)

    # 3. WebRTC Handshake Simulation
    # Alice (Host) offers a connection to Bob
    # In a mesh network, every new user initiates a connection to existing users
    
    sdp_offer = "SDP_OFFER_DATA_XYZ" # Technical metadata about Alice's camera
    zoom.send_signal(meeting_id, from_user_id=alice.id, to_user_id=bob.id, 
                     signal_type=EventType.OFFER, payload=sdp_offer)

    # Bob accepts
    sdp_answer = "SDP_ANSWER_DATA_ABC"
    zoom.send_signal(meeting_id, from_user_id=bob.id, to_user_id=alice.id, 
                     signal_type=EventType.ANSWER, payload=sdp_answer)

    # 4. Meeting Controls
    print("\n--- Meeting State ---")
    mtg = zoom.meetings[meeting_id]
    for p in mtg.get_participants():
        print(f"{p.user.name} | Audio: {p.audio_on}")

    # 5. Bob leaves
    zoom.leave_meeting(bob.id, meeting_id)
```

***

#### 5. Key Design Challenges & Interview Answers

#### 1. TCP vs. UDP

* Question: Why does video lag if we use TCP?
* Answer: TCP guarantees delivery (reliability). If Packet 5 is lost, TCP pauses Packet 6, 7, 8 until 5 is re-sent. In a live call, this causes buffering (freezing).
* Solution: Use UDP. If Packet 5 is lost, ignore it and show Packet 6. It's better to have a slight glitch in the image than to freeze the entire stream.

#### 2. Architecture: Mesh vs. MCU vs. SFU

* Mesh (Peer-to-Peer): \* Everyone connects to everyone.
  * _N_ users = $$ $N \times (N-1)$ $$ connections.
  * Pros: Cheap (no media server). Cons: Clients crash if > 4 users (Upload bandwidth saturation).
* MCU (Multipoint Control Unit): \* Server decodes everyone's video, mixes it into one video stream, and sends it out.
  * Pros: Client is happy (receives only 1 stream). Cons: Server CPU is on fire (Decoding/Encoding is expensive).
* SFU (Selective Forwarding Unit) - _The Winner_:
  * Server acts as a smart router. It receives Alice's stream and forwards it to Bob, Charlie, and Dave without decoding it.
  * Pros: Scalable (Zoom uses this).

#### 3. Adaptive Bitrate (Simulcast)

* Problem: Alice has fast internet (4K video), Bob has slow 3G internet. If Alice sends 4K, Bob chokes.
* Solution: Alice sends Simulcast (Three versions of her video: High, Med, Low quality) to the SFU.
  * The SFU checks Bob's bandwidth and forwards the Low quality packet to Bob.
  * The SFU sends the High quality packet to Charlie (who has fiber).

#### 4. Handling Jitter

* Problem: Packets arrive out of order (1, 3, 2, 4) due to UDP.
* Solution: Jitter Buffer. The client waits a small amount of time (e.g., 50ms) to collect packets and reorders them before playing the video frame.

#### 5. Security (E2EE)

* WebRTC is encrypted by default (DTLS/SRTP).
* However, in an SFU/MCU, the server usually decrypts the header to route packets. True End-to-End Encryption (where the server sees nothing) requires advanced key management (Insertable Streams).

## Cryptocurrency Exchange Platform



An end-to-end Low Level Design (LLD) for a Cryptocurrency Exchange (like Binance, Coinbase, or Luno).

This problem is a classic example of designing a High-Frequency Trading Engine. The core complexity lies in the Matching Algorithm (matching Buy orders with Sell orders) and managing Concurrency (ensuring two people don't sell the same coin).

***

#### 1. Requirements Clarification

* Core Entities: Users, Wallets, Assets (BTC, ETH, USD).
* Core Operations:
  * Limit Order: Buy/Sell at a specific price.
  * Market Order: Buy/Sell immediately at the best available price.
  * Cancel Order: Remove an active order.
* The Matching Engine:
  * Price-Time Priority: Orders with better prices match first. If prices are equal, the earlier order matches first.
  * Partial Fills: If I want 10 BTC but only 2 are available, I buy 2 and keep waiting for 8.

***

#### 2. Core Architecture: The Order Book

The heart of an exchange is the Order Book. It maintains two lists for every trading pair (e.g., BTC/USD):

1. Bids (Buys): People wanting to buy. Sorted by Price DESC (Highest bidder first).
2. Asks (Sells): People wanting to sell. Sorted by Price ASC (Lowest seller first).

Data Structure Choice:

* Priority Queue (Heap): ideal for matching because we always need the "Best" price ($$ $O(1)$ $$ access).
* Tree Map (Balanced BST): Better if we need random access cancellation.
* _For this interview, we use Heaps for the Matching Engine efficiency._

***

#### 3. Class Diagram

Code snippet

<figure><img src="../../../.gitbook/assets/image (192).png" alt=""><figcaption></figcaption></figure>

***

#### 4. Python Implementation

#### A. The Order Model

We implement comparison methods (`__lt__`) so the Heap knows how to sort orders automatically.

Crucial Logic:

* Sell Orders: Lower price is better. (Sort Ascending).
* Buy Orders: Higher price is better. (Sort Descending).

Python

```python
import heapq
import time
import uuid
from enum import Enum
from decimal import Decimal

class OrderSide(Enum):
    BUY = 1
    SELL = 2

class Order:
    def __init__(self, user_id, side, price, quantity):
        self.id = str(uuid.uuid4())
        self.user_id = user_id
        self.side = side
        self.price = Decimal(str(price)) # Use Decimal for money!
        self.quantity = Decimal(str(quantity))
        self.filled_qty = Decimal("0.0")
        self.timestamp = time.time()
        
    @property
    def remaining_qty(self):
        return self.quantity - self.filled_qty

    def is_filled(self):
        return self.remaining_qty <= 0

    # Logic for Heap Sorting (Price-Time Priority)
    def __lt__(self, other):
        if self.price != other.price:
            if self.side == OrderSide.BUY:
                return self.price > other.price # Descending for BUY (High is top)
            else:
                return self.price < other.price # Ascending for SELL (Low is top)
        return self.timestamp < other.timestamp # FIFO if prices equal

    def __repr__(self):
        return f"[{self.side.name} {self.remaining_qty} @ {self.price}]"
```

#### B. The Matching Engine (Order Book)

This is the complex algorithmic part.

Python

```python
class OrderBook:
    def __init__(self, symbol):
        self.symbol = symbol
        self.bids = [] # Max Heap (Buy)
        self.asks = [] # Min Heap (Sell)
        self.trade_history = []

    def add_order(self, order):
        # 1. Try to match immediately (Taker)
        self._match_order(order)
        
        # 2. If not fully filled, add to book (Maker)
        if not order.is_filled():
            if order.side == OrderSide.BUY:
                heapq.heappush(self.bids, order)
            else:
                heapq.heappush(self.asks, order)
            print(f"Order queued: {order}")

    def _match_order(self, incoming_order):
        """
        Core Matching Logic:
        If Incoming is BUY, check against lowest ASK.
        If Incoming is SELL, check against highest BID.
        """
        opposite_book = self.asks if incoming_order.side == OrderSide.BUY else self.bids
        
        while opposite_book and not incoming_order.is_filled():
            best_match = opposite_book[0] # Peek top
            
            # Check Price Condition
            # Buy Order Price >= Sell Ask Price?
            price_matches = (incoming_order.side == OrderSide.BUY and incoming_order.price >= best_match.price) or \
                            (incoming_order.side == OrderSide.SELL and incoming_order.price <= best_match.price)

            if not price_matches:
                break # No match possible, stop looking

            # Execute Trade
            trade_qty = min(incoming_order.remaining_qty, best_match.remaining_qty)
            trade_price = best_match.price # Trade happens at the MAKER's price
            
            self._execute_trade(incoming_order, best_match, trade_qty, trade_price)

            # Cleanup
            if best_match.is_filled():
                heapq.heappop(opposite_book) # Remove from book

    def _execute_trade(self, taker, maker, qty, price):
        taker.filled_qty += qty
        maker.filled_qty += qty
        
        trade_record = {
            "symbol": self.symbol,
            "price": price,
            "qty": qty,
            "taker_id": taker.id,
            "maker_id": maker.id
        }
        self.trade_history.append(trade_record)
        print(f"⚡ TRADE EXECUTED: {qty} {self.symbol} @ ${price}")
```

#### C. The Exchange Service (Facade)

This manages multiple trading pairs and user validation.

Python

```python
class CryptoExchange:
    def __init__(self):
        # Map: "BTC/USD" -> OrderBook
        self.order_books = {} 

    def get_order_book(self, symbol):
        if symbol not in self.order_books:
            self.order_books[symbol] = OrderBook(symbol)
        return self.order_books[symbol]

    def place_limit_order(self, user_id, symbol, side, price, qty):
        # 1. Validation (Does user have balance? Skipped for brevity)
        
        # 2. Create Order
        order = Order(user_id, side, price, qty)
        
        # 3. Process
        book = self.get_order_book(symbol)
        book.add_order(order)
        return order.id
```

#### D. Driver Code

Python

```python
if __name__ == "__main__":
    exchange = CryptoExchange()

    # Scenario:
    # 1. Alice wants to Sell 1 BTC for $50,000 (Maker)
    # 2. Bob wants to Sell 1 BTC for $51,000 (Maker)
    # 3. Charlie wants to Buy 2 BTC for $52,000 (Taker)
    
    print("--- Initializing Order Book ---")
    exchange.place_limit_order("Alice", "BTC/USD", OrderSide.SELL, 50000, 1.0)
    exchange.place_limit_order("Bob", "BTC/USD", OrderSide.SELL, 51000, 1.0)
    
    print("\n--- Charlie Enters (Aggressive Buy) ---")
    # Charlie is willing to pay up to 52k. 
    # He should buy Alice's cheap 50k BTC first, then Bob's 51k BTC.
    exchange.place_limit_order("Charlie", "BTC/USD", OrderSide.BUY, 52000, 2.0)
```

***

#### 5. Key Design Challenges & Interview Answers

#### 1. Floating Point Errors (The "Superman 3" Problem)

* Problem: If you calculate `0.1 + 0.2` in Python/Java floats, you often get `0.30000000000000004`. In finance, this creates phantom money or prevents trade matching.
* Solution: ALWAYS use `Decimal` types (Python `decimal`, Java `BigDecimal`). Never use `float` or `double` for currency.

#### 2. Concurrency (Race Conditions)

* Problem: What if two threads try to match the same "Best Sell Order" at the same time?
* Solution:
  * Single-Threaded Matching Engine: Most high-performance exchanges (like LMAX Disruptor) use a Single Thread for the matching core to avoid locking overhead. It's faster to queue inputs into a single processor than to manage locks.
  * Sharding: One thread per Trading Pair. (Thread A handles BTC/USD, Thread B handles ETH/USD). They don't overlap.

#### 3. Market Orders

* Question: How to handle "Buy 1 BTC at _Any_ Price"?
* Logic: A Market Order is treated as a Limit Order with a price of `Infinity` (for Buy) or `0` (for Sell). It crosses the spread immediately until the quantity is filled.

#### 4. Cancellation Efficiency

* Problem: `heapq` is good for popping the top, but deleting an item from the middle (user cancels order) is $$ $O(N)$ $$.
* Solution: Lazy Deletion.
  * Don't remove it from the heap immediately.
  * Mark the order object as `status = CANCELLED`.
  * When the heap pops the top during matching, if it sees a `CANCELLED` order, it discards it and pops again.

#### 5. Auditability (Event Sourcing)

* An exchange cannot lose data.
* Instead of just updating `UserBalance = 50`, we record a ledger of events:
  1. `OrderPlaced(ID: 1)`
  2. `TradeExecuted(ID: 1, Price: 50k)`
  3. `BalanceDeducted(User: A, Amount: 1 BTC)`
* This allows replaying history to fix bugs or audit fraud.

## Collaborative Document Editing (Google Docs)



An end-to-end Low Level Design (LLD) for a Collaborative Document Editor (like Google Docs or Etherpad).

This is widely considered one of the hardest LLD problems because it requires handling Concurrency Conflicts in real-time. The standard solution involves Operational Transformation (OT) or CRDTs (Conflict-Free Replicated Data Types).

***

#### 1. Requirements Clarification

* Core Entities: Document, User, Session.
* Core Operations:
  * `insert(index, char)`: User types a character.
  * `delete(index)`: User deletes a character.
* The Conflict Problem:
  * Initial Text: "AB"
  * User 1: Inserts 'X' at index 1 ("AXB").
  * User 2: Deletes index 1 ('B') at the same time ("A").
  * Result: Without special logic, User 1's "X" might overwrite User 2's deletion, or the document becomes corrupted.
* Goal: Eventual Consistency (Everyone sees the same text).

***

#### 2. Core Architecture: Operational Transformation (OT)

We cannot send the whole document text on every keypress (too slow). We send Operations.

The Server acts as the Single Source of Truth.

1. Optimistic UI: When you type, the character appears immediately on your screen.
2. Transformation: If the server receives an operation from User B while you were typing, the server transforms User B's operation so it makes sense in the context of _your_ changes, and vice versa.

The "Transform" Logic:

If OpA inserts at index 5, and OpB inserts at index 10:

*   OpA does not affect OpB.

    If OpA inserts at index 5, and OpB inserts at index 2:
* `OpA` must now insert at index 6 (because `OpB` added a char before it).

***

#### 3. Class Diagram

Code snippet

<figure><img src="../../../.gitbook/assets/image (193).png" alt=""><figcaption></figcaption></figure>

***

#### 4. Python Implementation (Simplified OT)

We will implement a simplified OT Engine that handles `INSERT` vs `INSERT` conflicts.

#### A. The Operation Model

Python

```python
from enum import Enum
import copy

class OpType(Enum):
    INSERT = 1
    DELETE = 2

class Operation:
    def __init__(self, type, position, char=None, user_id=None):
        self.type = type
        self.position = position
        self.char = char # Only for INSERT
        self.user_id = user_id

    def __repr__(self):
        op_code = "INS" if self.type == OpType.INSERT else "DEL"
        char_str = f"'{self.char}'" if self.char else ""
        return f"[{self.user_id} {op_code} @ {self.position} {char_str}]"
```

#### B. The Transformation Engine

This is the most critical function. It takes two concurrent operations (`op_new` and `op_previous`) and adjusts `op_new` as if `op_previous` had happened first.

Python

```python
class OTEngine:
    @staticmethod
    def transform(new_op, previous_op):
        """
        Adjusts 'new_op' assuming 'previous_op' has already been applied.
        """
        # Create a copy so we don't mutate the original input
        transformed = copy.deepcopy(new_op)

        if previous_op.type == OpType.INSERT:
            # Case 1: Previous User inserted text BEFORE our position
            if previous_op.position <= new_op.position:
                # Our target index has shifted right by 1
                transformed.position += 1
                
                # Tie-breaker: If both insert at SAME position
                # We arbitrate based on user_id (e.g., lower ID goes first)
                if previous_op.position == new_op.position:
                     if previous_op.user_id < new_op.user_id:
                         pass # Previous wins spot, we shift
                     else:
                         transformed.position -= 1 # We win spot, don't shift (logic varies)

        elif previous_op.type == OpType.DELETE:
            # Case 2: Previous User deleted text BEFORE our position
            if previous_op.position < new_op.position:
                # Our target index has shifted left by 1
                transformed.position -= 1
            elif previous_op.position == new_op.position:
                # They deleted the spot we wanted to write/delete.
                # Complex case: For simplicity, we might say the insert happens at the same spot.
                pass 
                
        return transformed
```

#### C. The Document Server

The server applies operations strictly in order and keeps a history to transform incoming "stale" operations.

Python

```python
class DocumentServer:
    def __init__(self, initial_text=""):
        self.content = list(initial_text) # Using list for mutability
        self.history = [] # Log of all applied operations
        self.lock = False # Simplified concurrency control

    def apply_operation(self, op):
        """
        1. Transform incoming OP against all operations that happened 
           since the client created this OP.
        2. Apply to local state.
        3. Add to history.
        """
        # In a real system, the Client sends 'revision_id' (state version).
        # We transform 'op' against all ops in history[revision_id:].
        # For this LLD, we simulate a single transformation step.
        
        # Apply Logic
        if op.type == OpType.INSERT:
            self.content.insert(op.position, op.char)
        elif op.type == OpType.DELETE:
            if 0 <= op.position < len(self.content):
                self.content.pop(op.position)
        
        self.history.append(op)
        print(f"Server State: {''.join(self.content)}")

    def get_content(self):
        return "".join(self.content)
```

#### D. Driver Code (Simulation)

We simulate a race condition where Alice and Bob act on the same starting state _before_ seeing each other's changes.

Python

```python
if __name__ == "__main__":
    # Initial State: "ABC"
    server = DocumentServer("ABC")
    engine = OTEngine()

    # 1. Setup Concurrent Operations
    # Alice wants to insert 'X' at index 1 -> "AXBC"
    op_alice = Operation(OpType.INSERT, 1, 'X', user_id=1)
    
    # Bob wants to insert 'Y' at index 1 -> "AYBC"
    op_bob = Operation(OpType.INSERT, 1, 'Y', user_id=2)

    print(f"Initial: {server.get_content()}")
    print(f"Alice sends: {op_alice}")
    print(f"Bob sends:   {op_bob}")
    print("--- Processing ---")

    # 2. Server receives Alice first
    server.apply_operation(op_alice) 
    # Server Text is now "AXBC". Alice is happy.
    
    # 3. Server receives Bob next
    # BUT Bob created his operation when text was "ABC".
    # If we apply Bob's op (Insert at 1) blindly to "AXBC":
    # Result would be "AYXBC". (Bob jumps in front of Alice?)
    # OT Logic: Since Alice (User 1) inserted at 1, and Bob (User 2) wants to insert at 1,
    # we must shift Bob to index 2 so 'X' stays before 'Y'.

    # Transform Bob against Alice
    transformed_bob = engine.transform(op_bob, op_alice)
    print(f"Transformed Bob: {transformed_bob}") # Should be Insert 'Y' at 2
    
    server.apply_operation(transformed_bob)
    
    print(f"Final Doc: {server.get_content()}") 
    # Expected: "AXYBC" (Consistent)
```

***

#### 5. Key Design Challenges & Interview Answers

#### 1. OT vs. CRDT (The Big Debate)

* Operational Transformation (OT): Used by Google Docs.
  * Pros: History-based, intuitive for text.
  * Cons: Extremely complex to implement correctly (requires a transformation function for every pair of operations: Insert vs Delete, Delete vs Insert, etc.). Needs a central server.
* CRDT (Conflict-Free Replicated Data Types): Used by modern editors (Zed, Atom Teletype).
  * Concept: Give every character a unique, immutable ID (e.g., `Start`, `0.1`, `0.5`, `End`). To insert between A and B, generate ID `0.3`.
  * Pros: Commutative (order doesn't matter). Works P2P (Peer-to-Peer).
  * Cons: IDs can grow large (memory overhead).

#### 2. Handling Network Latency

* Optimistic UI: The client applies the change locally _immediately_ so the user feels no lag.
* Rollback/Rebase: If the server rejects the change (rare) or transforms it drastically, the client might momentarily "jump" or undo/redo the text to match the server's truth.

#### 3. Deleting Ranges

* Deleting a range `(start=2, end=5)` is tricky with concurrency.
* Solution: Treat it as 3 individual `Delete(index)` operations or handle `RangeDelete` as a specific atomic operation type in the OT Engine.

#### 4. Garbage Collection

* Problem: The history of operations grows infinitely.
* Solution: When all active clients have acknowledged version 100, the server can "squash" history from 0-100 into a snapshot and delete the individual operation logs.

#### 5. Collaborative Cursor (Presence)

* Do not store cursor positions in the `Document` object.
* Use a separate Ephemeral State channel (Redis Pub/Sub or WebSocket broadcast).
* Cursor positions are transient; if they are lost, it doesn't corrupt the document.

#### Next Step?

This concludes the document editing design. Would you like to proceed with "Design a Rate Limiter" or "Design a Parking Lot System"?

## Payment Recommendation System



An end-to-end Low Level Design (LLD) for a Payment Recommendation System (like the ones used by Razorpay, Stripe, or PhonePe/GPay Smart Routing).

This problem focuses on Rule Engines, Strategy Patterns, and Ranking Algorithms. The goal is not just to list payment methods, but to order them intelligently to maximize Success Rate (SR), User Rewards, or minimize Transaction Costs.

***

#### 1. Requirements Clarification

* Context: A user is on the checkout page of an E-commerce app (e.g., Swiggy/Amazon).
* Inputs:
  * User: Contains stored payment methods (HDFC Credit Card, UPI ID, Paytm Wallet).
  * Transaction: Amount (e.g., ₹500), Merchant (e.g., Starbucks).
* Business Goals (Priorities):
  1. Reliability: Don't show banks that are currently down.
  2. Rewards: Promote cards that give max cashback for this specific merchant.
  3. Cost: Prefer low-cost methods (UPI) over high-cost (Amex) for the merchant (optional B2B toggle).
* Output: A ranked list of payment instruments.

***

#### 2. Core Architecture: The Scoring Pipeline

We use a Pipe & Filter or Chain of Responsibility pattern. Every payment instrument starts with a base score. It passes through a series of "Rule Filters" that modify its score.

The Pipeline:

1. Candidate Retrieval: Fetch all user instruments.
2. Eligibility Filter: Remove invalid options (e.g., Wallet balance < transaction amount).
3. Scoring Engine:
   * _Health Check Rule:_ Is the bank API up?
   * _Reward Rule:_ Is there a 5% cashback offer?
   * _Affordability Rule:_ Is BNPL applicable?
4. Ranking: Sort by Score DESC.

***

#### 3. Class Diagram

Code snippet

<figure><img src="../../../.gitbook/assets/image (194).png" alt=""><figcaption></figcaption></figure>

***

#### 4. Python Implementation

#### A. The Models

We define the Payment Instruments and the Context (Cart).

Python

```python
from abc import ABC, abstractmethod
from enum import Enum
import uuid

class InstrumentType(Enum):
    CREDIT_CARD = 1
    UPI = 2
    WALLET = 3

class PaymentInstrument:
    def __init__(self, name, type, issuer, relevance_score=0):
        self.id = str(uuid.uuid4())
        self.name = name # e.g., "HDFC Regalia"
        self.type = type
        self.issuer = issuer # e.g., "HDFC"
        self.relevance_score = relevance_score # Transient field for sorting

    def __repr__(self):
        return f"[{self.name} | Score: {self.relevance_score}]"

class User:
    def __init__(self, name):
        self.name = name
        self.instruments = []

class CartContext:
    def __init__(self, amount, merchant, category):
        self.amount = amount
        self.merchant = merchant # e.g., "Uber"
        self.category = category # e.g., "Travel"
```

#### B. The Rules Engine (Strategy Pattern)

This is the core logic. Instead of hardcoding `if-else` blocks, we create pluggable `Rule` classes. This allows the business to add/remove rules without changing core code (Open-Closed Principle).

Python

```python
class ScoringRule(ABC):
    @abstractmethod
    def apply(self, instrument, context):
        """
        Returns an integer score delta. 
        Positive boosts ranking, Negative demotes it.
        """
        pass

# --- Concrete Rules ---

class HealthRule(ScoringRule):
    """
    Checks if the Bank is currently experiencing downtime.
    """
    def __init__(self, bank_health_service):
        self.health_service = bank_health_service

    def apply(self, instrument, context):
        # Mocking the external service call
        uptime = self.health_service.get_uptime(instrument.issuer)
        
        if uptime < 80:
            return -1000 # Critical failure, push to bottom
        elif uptime < 95:
            return -50   # Risky, demote slightly
        return 0

class RewardRule(ScoringRule):
    """
    Boosts cards that have specific offers for this merchant.
    """
    def apply(self, instrument, context):
        score = 0
        # Logic: If Merchant is 'Amazon' and Card is 'Amazon Pay', boost heavy
        if context.merchant == "Amazon" and "Amazon" in instrument.name:
            score += 100
        
        # Logic: Credit Cards usually give better rewards than UPI
        if instrument.type == InstrumentType.CREDIT_CARD:
            score += 10
            
        return score

class FeeRule(ScoringRule):
    """
    Business logic: Prefer UPI (Low Cost) over Credit Card (High MDR)
    if the transaction amount is small.
    """
    def apply(self, instrument, context):
        if context.amount < 200:
            if instrument.type == InstrumentType.UPI:
                return 20 # Boost UPI for small amounts
            elif instrument.type == InstrumentType.CREDIT_CARD:
                return -10 # Demote Cards for small amounts
        return 0
```

#### C. The Recommendation Service

This orchestrates the flow.

Python

```python
class BankHealthService:
    """Mock Service to simulate bank uptimes"""
    def get_uptime(self, bank_name):
        # Simulate: HDFC is having a bad day
        if bank_name == "HDFC": return 70 
        return 99

class RecommendationEngine:
    def __init__(self):
        self.rules = []

    def add_rule(self, rule):
        self.rules.append(rule)

    def recommend(self, user, context):
        candidates = user.instruments
        
        print(f"--- Calculating for {context.merchant} (${context.amount}) ---")

        for instrument in candidates:
            # Reset score for fresh calculation
            instrument.relevance_score = 100 # Base Score
            
            for rule in self.rules:
                delta = rule.apply(instrument, context)
                instrument.relevance_score += delta
                # Optional: Log logic for debugging
                if delta != 0:
                    print(f"   > Rule {type(rule).__name__} applied {delta} to {instrument.name}")

        # Sort: Descending Score
        ranked_list = sorted(candidates, key=lambda x: x.relevance_score, reverse=True)
        return ranked_list
```

#### D. Driver Code

Python

```python
if __name__ == "__main__":
    # 1. Setup Infrastructure
    health_svc = BankHealthService()
    engine = RecommendationEngine()
    
    # 2. Add Rules (Pluggable logic)
    engine.add_rule(HealthRule(health_svc))
    engine.add_rule(RewardRule())
    engine.add_rule(FeeRule())

    # 3. Setup User
    alice = User("Alice")
    alice.instruments = [
        PaymentInstrument("HDFC Regalia", InstrumentType.CREDIT_CARD, "HDFC"),
        PaymentInstrument("SBI UPI", InstrumentType.UPI, "SBI"),
        PaymentInstrument("Amazon Pay ICICI", InstrumentType.CREDIT_CARD, "ICICI")
    ]

    # 4. Scenario A: Buying on Amazon (Should prefer Amazon Card)
    ctx_amazon = CartContext(5000, "Amazon", "Shopping")
    recommendations = engine.recommend(alice, ctx_amazon)
    
    print("\nResult:")
    for i, inst in enumerate(recommendations, 1):
        print(f"{i}. {inst.name} (Score: {inst.relevance_score})")
        # Expected: Amazon ICICI (Top due to Reward), HDFC (Bottom due to Downtime -1000)

    # 5. Scenario B: Buying Tea for Rs 50 (Should prefer UPI)
    print("\n")
    ctx_tea = CartContext(50, "ChaiPoint", "Food")
    rec_tea = engine.recommend(alice, ctx_tea)
    
    print("\nResult:")
    for i, inst in enumerate(rec_tea, 1):
        print(f"{i}. {inst.name} (Score: {inst.relevance_score})")
        # Expected: UPI (Top due to FeeRule boosting small txns), HDFC (Bottom)
```

***

#### 5. Key Design Challenges & Interview Answers

#### 1. Handling Latency (The 50ms Constraint)

* Problem: If you make an external API call to "Check Bank Health" for every transaction, the checkout page will load slowly.
* Solution: Caching & Asynchronous Health Checks.
  * Do not call the bank in real-time.
  * Have a background worker ("Heartbeat Service") that checks Bank Health every 10 seconds and updates a Redis Cache.
  * The `HealthRule` simply reads from Redis ($$ $O(1)$ $$).

#### 2. A/B Testing

* Problem: Marketing wants to test if "Boosting UPI" increases conversion rates for 10% of users.
* Solution: The `RecommendationEngine` should accept an `ExperimentConfig`.
  * Before adding rules, check `if user_id % 10 == 0: engine.add_rule(BoostUPIRule())`.

#### 3. Dynamic Filtering (Constraint Satisfaction)

* Scenario: A merchant says "I do not accept AMEX".
* Design: Add a Hard Filter Phase before the Scoring Phase.
  * `Filter.filter(instruments)` removes invalid items.
  * `Scorer.score(instruments)` ranks the remaining ones.

#### 4. Cold Start / Guest Users

* Question: What if the user has no saved cards?
* Answer: Recommend based on global popularity ("Most people on Swiggy use GPay").
  * Return a generic list: `[Google Pay, PhonePe, Credit/Debit Card]`.

#### 5. ML-Based Personalization (Advanced)

* Instead of static rules (`+10 points`), use a Logistic Regression Model.
* Inputs: Time of day, User History (Alice prefers Credit Cards 90% of the time).
* Output: Probability of Success.
* The `RecommendationEngine` calls the ML Model to get the final score.

Would you like to explore "Design an Inventory Management System" or "Design a Rate Limiter" next?

## Alexa



An end-to-end Low Level Design (LLD) for a Voice Assistant (like Amazon Alexa or Google Home).

This problem is unique because it bridges Hardware (Device) and Software (Cloud). The design must handle the Pipeline of Processing: Audio $$ $\rightarrow$ $$ Text $$ $\rightarrow$ $$ Intent $$ $\rightarrow$ $$ Action $$ $\rightarrow$ $$ Response.

***

#### 1. Requirements Clarification

* Actors: User, Alexa Device, Cloud Service.
* Core Flow:
  1. Wake Word: Device listens locally for "Alexa".
  2. ASR (Automatic Speech Recognition): Convert Audio to Text.
  3. NLU (Natural Language Understanding): Extract Intent (What to do) and Slots (Variables).
  4. Execution: Trigger the correct "Skill" (Music, Smart Home, Weather).
  5. TTS (Text to Speech): Convert response back to audio.
* Key Design Goal: Extensibility. We must be able to add new capabilities (e.g., "Order Pizza") without rewriting the core engine.

***

#### 2. Core Architecture: The Pipeline & Command Pattern

We need a Pipeline Pattern to process the data in stages, and a Command/Strategy Pattern to handle the diverse capabilities (Skills).

1. The "Device" (Client): Dumb terminal. It only processes the "Wake Word" locally (to save bandwidth/privacy) and streams audio to the cloud.
2. The "Orchestrator" (Cloud): The brain. It routes the text to the correct Skill.
3. The "Skill" (Plugin): A modular unit of logic (e.g., SpotifySkill, HueLightSkill).

***

#### 3. Class Diagram

Code snippet

<figure><img src="../../../.gitbook/assets/image (4).png" alt=""><figcaption></figcaption></figure>

***

#### 4. Python Implementation

#### A. The Data Models (Intent & Slots)

We need a structured way to pass user desires around.

* Utterance: "Play Taylor Swift"
* Intent: `PlayMusic`
* Slots: `{artist: "Taylor Swift"}`

Python

```python
import abc

class Intent:
    def __init__(self, name, slots=None):
        self.name = name
        self.slots = slots if slots else {}

    def __repr__(self):
        return f"Intent(Name={self.name}, Slots={self.slots})"

# Simulating the complex NLP Layer
class NLUService:
    def parse_text(self, text):
        text = text.lower()
        if "weather" in text:
            return Intent("GetWeather", {"location": "Bengaluru"}) # Simplified extraction
        elif "play" in text:
            # Extract artist "play [artist]"
            artist = text.replace("play ", "").strip()
            return Intent("PlayMusic", {"artist": artist})
        elif "turn on" in text:
            device = text.replace("turn on ", "").strip()
            return Intent("TurnOnDevice", {"device": device})
        else:
            return Intent("Unknown")
```

#### B. The Skill Interface (Strategy Pattern)

This is the most critical part for extensibility. Every capability is a `Skill`.

Python

```python
class AlexaSkill(abc.ABC):
    @abc.abstractmethod
    def get_intent_name(self):
        """Returns the Intent this skill handles"""
        pass

    @abc.abstractmethod
    def execute(self, intent):
        """Performs the logic and returns a text response"""
        pass

# --- Concrete Skills ---

class WeatherSkill(AlexaSkill):
    def get_intent_name(self):
        return "GetWeather"

    def execute(self, intent):
        # In reality, calls Weather API
        city = intent.slots.get("location", "Unknown City")
        return f"The weather in {city} is 28 degrees and sunny."

class MusicSkill(AlexaSkill):
    def get_intent_name(self):
        return "PlayMusic"

    def execute(self, intent):
        artist = intent.slots.get("artist")
        # In reality, calls Spotify API
        return f"Playing top hits by {artist} on Spotify."

class SmartHomeSkill(AlexaSkill):
    def get_intent_name(self):
        return "TurnOnDevice"

    def execute(self, intent):
        device = intent.slots.get("device")
        # In reality, calls IoT Gateway
        return f"Okay, turning on the {device}."
```

#### C. The Orchestrator (The Cloud Brain)

This component wires everything together. It holds a registry of skills and routes the intent.

Python

```python
class AlexaCloudService:
    def __init__(self):
        self.nlu_service = NLUService()
        self.skills = {} # Map: IntentName -> Skill Object
        
        # Register core skills
        self.register_skill(WeatherSkill())
        self.register_skill(MusicSkill())
        self.register_skill(SmartHomeSkill())

    def register_skill(self, skill: AlexaSkill):
        self.skills[skill.get_intent_name()] = skill

    def process_command(self, audio_text_input):
        """
        In LLD, we often mock the Audio->Text part (ASR) 
        and start from the text input.
        """
        print(f"\n[Cloud] Received Audio/Text: '{audio_text_input}'")

        # 1. NLU Parsing
        intent = self.nlu_service.parse_text(audio_text_input)
        print(f"[Cloud] Parsed Intent: {intent}")

        # 2. Routing
        if intent.name in self.skills:
            skill = self.skills[intent.name]
            response_text = skill.execute(intent)
        else:
            response_text = "Sorry, I don't know that one."

        # 3. TTS (Text to Speech) - Mocked
        return self._generate_audio(response_text)

    def _generate_audio(self, text):
        return f"AUDIO_STREAM('{text}')"
```

#### D. The Device (Client)

The device handles the "Wake Word" loop.

Python

```python
import time

class AlexaDevice:
    def __init__(self, cloud_service):
        self.cloud = cloud_service
        self.is_listening = False

    def on_wake_word_detected(self):
        print("[Device] Wake word 'Alexa' detected! 🔵 (Ring turns blue)")
        self.is_listening = True

    def capture_audio(self, mock_input_text):
        if not self.is_listening:
            return
        
        # Simulate streaming audio to cloud
        audio_response = self.cloud.process_command(mock_input_text)
        
        self.speak(audio_response)
        self.is_listening = False # Go back to sleep

    def speak(self, audio_blob):
        # Simulate playing audio
        print(f"[Device] Alexa says: 🔊 {audio_blob}")
```

#### E. Driver Code

Python

```python
if __name__ == "__main__":
    # 1. Setup
    cloud = AlexaCloudService()
    echo_dot = AlexaDevice(cloud)

    # 2. Simulation: User says "Alexa, play Taylor Swift"
    
    # Step A: Hardware detects wake word
    echo_dot.on_wake_word_detected()
    
    # Step B: User speaks command
    echo_dot.capture_audio("Play Taylor Swift")

    # 3. Simulation: Smart Home
    print("-" * 30)
    echo_dot.on_wake_word_detected()
    echo_dot.capture_audio("Turn on Living Room Lights")
    
    # 4. Simulation: Unknown command
    print("-" * 30)
    echo_dot.on_wake_word_detected()
    echo_dot.capture_audio("Order a Pizza") 
    # Logic: Should fail as we didn't register a PizzaSkill
```

***

#### 5. Key Design Challenges & Interview Answers

#### 1. Handling State & Context

* Scenario:
  * User: "Who is the President of the US?" $$ $\rightarrow$ $$ Alexa: "Joe Biden."
  * User: "How old is he?"
* Problem: The second query has no context. "He" is ambiguous.
* Design: The `AlexaCloudService` must maintain a `SessionContext` object.
  * Store the last `Intent` and `Entity`.
  * Pass `SessionContext` into the `NLUService` to resolve references (Coreference Resolution).

#### 2. Latency vs. Throughput (Streaming)

* Question: Do we wait for the user to finish the whole sentence before sending to Cloud?
* Answer: No. That feels slow.
* Design: Use Bi-directional Streaming (gRPC or WebSockets).
  * Device streams chunks of audio bytes.
  * Cloud ASR processes chunks in real-time (Intermediate Transcription).
  * This allows Alexa to determine "End of Sentence" (Silence Detection) dynamically.

#### 3. Third-Party Skills (Isolation)

* Problem: If the "Uber Skill" crashes, it shouldn't crash the whole Alexa server.
* Design: Run Skills as Serverless Functions (AWS Lambda).
  * The `AlexaCloudService` doesn't execute the skill code directly. It calls an external API/Lambda.
  * This ensures sandboxing and security.

#### 4. Privacy (Local vs. Cloud)

* Design:
  * Wake Word Engine: Must run On-Device (using a tiny ML model). No audio leaves the house until this trigger fires.
  * Cloud: Only receives audio _after_ the wake word.

#### 5. Multi-Turn Conversations

* Scenario: "Set an alarm." $$ $\rightarrow$ $$ "For what time?" $$ $\rightarrow$ $$ "7 AM."
* Design:
  * The `Intent` result can be a `DialogDelegate`.
  * If a required slot (Time) is missing, the NLU returns `SlotElicitation` state, prompting the orchestrator to ask a clarifying question instead of executing the action.
