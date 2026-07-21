> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a Version Control System (like Git) — a highly advanced LLD problem focusing on Directed Acyclic Graphs (DAGs), hashing, and immutability.
>
> **Key concepts:**
> - Core Entities: `Blob` (file content), `Tree` (directory structure), `Commit` (snapshot), `Branch` (pointer to a commit), `Repository`.
> - Content-Addressable Storage: Every object is hashed (e.g., SHA-1). The hash is the ID. If a file's content doesn't change between commits, both commits point to the *exact same* Blob hash. This deduplication saves massive amounts of space.
> - The DAG: Commits point to their parent commit(s). This forms a Directed Acyclic Graph.
> - Branches: A branch is literally just a named pointer (a string -> hash map, e.g., `"main" -> "a1b2c3"`).
> - Commands (Command Pattern): `git add` (creates Blobs in staging), `git commit` (creates a Tree and a Commit object), `git checkout` (updates HEAD and working directory).
>
> **Key takeaway:** Do not store full copies of the project for every commit. Explain how Git uses immutable hashing to share unchanged blobs and trees between commits.

---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, version-control, git, dag, graph, command]
---
# Design Version Control System

> **Difficulty**: Hard
> **Asked at**: Amazon, Google, Atlassian
> **Key Patterns**: Command (commit/checkout), Graph (DAG of commits), Composite (directory tree)

---

## Understanding the Problem

Design a Git-like version control system supporting repository initialization, file staging, committing with SHA-based content addressing, branching, checkout, and diff between two commits.

---

## Clarifying Questions

**You**: "Should commits be content-addressed using a hash, like SHA-1 in Git?"
**Interviewer**: "Yes, hash the content to get the commit identifier."

**You**: "Do I need to support the full object model — blobs, trees, commits — or can I simplify?"
**Interviewer**: "Model blobs and trees at a conceptual level; full binary encoding is not needed."

**You**: "Should I support merging branches?"
**Interviewer**: "Mention fast-forward and 3-way merge as a deep dive. Focus on basic commit/checkout first."

**You**: "Do I need to persist to disk, or is an in-memory representation fine?"
**Interviewer**: "In-memory is fine. Describe how disk persistence would work."

**You**: "Should diff show line-level differences or just which files changed?"
**Interviewer**: "File-level diff is sufficient. Mention line-level as an extension."

**You**: "Do I need to handle remotes (push/pull/clone)?"
**Interviewer**: "No, local operations only."

**You**: "Should I support a staging area (index) like Git, or commit working directory directly?"
**Interviewer**: "Yes, include a staging area."

---

## Final Requirements

**In scope:**
1. `init()` — create a new repository
2. `stage(file_path, content)` — add file to staging area
3. `commit(message)` — snapshot staged files, return commit hash
4. `create_branch(name)` — create branch pointing to current commit
5. `checkout(branch_or_hash)` — switch HEAD
6. `diff(commit1_hash, commit2_hash)` — show added/removed/modified files
7. Content-addressed storage: SHA of content = object key
8. Branching: branch is a pointer to a commit hash

**Out of scope:**
- Merge conflict resolution UI
- Rebase
- Remote operations (push/pull/clone/fetch)
- Binary file handling
- .gitignore

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|---------------|
| Repository | Top-level facade; owns all subsystems |
| WorkingDirectory | In-memory map of file path to content |
| StagingArea | Files staged for next commit |
| Commit | Snapshot: hash, message, parent hash, tree hash, timestamp |
| Tree | Maps file paths to blob hashes (directory snapshot) |
| Blob | Raw file content, addressed by its SHA hash |
| Branch | Named pointer to a commit hash |
| CommitGraph | DAG of commits; parent links enable history traversal |
| ObjectStore | Content-addressed storage for blobs, trees, commits |

---

## Class Design

### Blob and Tree

| Requirement | What Blob must track |
|-------------|---------------------|
| Content | `content: str` |
| Hash | `hash: str` — SHA256 of content |

| Requirement | What Tree must track |
|-------------|---------------------|
| File map | `files: dict[str, str]` — path -> blob_hash |
| Hash | `hash: str` — SHA256 of sorted serialized file map |

```
class Blob:
- content: str
- hash: str
+ from_content(content) -> Blob

class Tree:
- files: dict[str, str]   # path -> blob_hash
- hash: str
+ from_files(files) -> Tree
```

### Commit

| Requirement | What Commit must track |
|-------------|----------------------|
| Identity | `hash: str` |
| Parent | `parent_hash: str or None` |
| Tree snapshot | `tree_hash: str` |
| Metadata | `message: str, author: str, timestamp: float` |

```
class Commit:
- hash: str
- parent_hash: str | None
- tree_hash: str
- message: str
- author: str
- timestamp: float
```

### Repository

```
class Repository:
- object_store: ObjectStore
- branches: dict[str, str]    # branch_name -> commit_hash
- HEAD: str                    # branch name or commit hash (detached)
- staging_area: dict[str, str] # path -> content
- working_dir: dict[str, str]  # path -> content
+ init() -> None
+ stage(file_path, content) -> None
+ commit(message, author) -> str
+ create_branch(name) -> None
+ checkout(ref) -> None
+ diff(hash1, hash2) -> DiffResult
+ log() -> list[Commit]
+ current_commit_hash() -> str
```

---

## Implementation

### Core Method: commit()

**Core logic:**
1. For each staged file, create a Blob and store it in ObjectStore
2. Create a Tree from (path -> blob_hash) mapping, store it
3. Create a Commit object with parent=current HEAD commit hash, tree=tree hash
4. Hash the commit content to get the commit hash; store it
5. Advance the current branch pointer to the new commit hash
6. Clear the staging area

**Edge cases:**
- Empty staging area — raise error or no-op
- First commit — parent_hash is None
- Detached HEAD — update HEAD directly to new commit hash, not a branch

```python
import hashlib
import time
from dataclasses import dataclass, field
from typing import Optional


def sha256(content: str) -> str:
    return hashlib.sha256(content.encode()).hexdigest()


@dataclass
class Blob:
    content: str
    hash: str

    @classmethod
    def from_content(cls, content: str) -> 'Blob':
        return cls(content=content, hash=sha256(content))


@dataclass
class Tree:
    files: dict  # path -> blob_hash
    hash: str

    @classmethod
    def from_files(cls, files: dict) -> 'Tree':
        # Deterministic hash: sort by path
        serialized = str(sorted(files.items()))
        return cls(files=dict(files), hash=sha256(serialized))


@dataclass
class Commit:
    hash: str
    parent_hash: Optional[str]
    tree_hash: str
    message: str
    author: str
    timestamp: float

    @classmethod
    def create(cls, parent_hash, tree_hash, message, author) -> 'Commit':
        ts = time.time()
        content = f"{parent_hash}|{tree_hash}|{message}|{author}|{ts}"
        commit_hash = sha256(content)
        return cls(
            hash=commit_hash,
            parent_hash=parent_hash,
            tree_hash=tree_hash,
            message=message,
            author=author,
            timestamp=ts,
        )


class ObjectStore:
    """Content-addressed storage for blobs, trees, and commits."""
    def __init__(self):
        self._store: dict = {}  # hash -> object

    def put(self, obj) -> str:
        self._store[obj.hash] = obj
        return obj.hash

    def get(self, obj_hash: str):
        return self._store.get(obj_hash)

    def exists(self, obj_hash: str) -> bool:
        return obj_hash in self._store


@dataclass
class DiffResult:
    added: list      # paths added in commit2 not in commit1
    removed: list    # paths removed
    modified: list   # paths present in both but different blob hash


class Repository:
    def __init__(self):
        self.object_store = ObjectStore()
        self.branches: dict = {}           # name -> commit_hash
        self.HEAD: str = 'main'            # branch name or detached commit hash
        self._is_detached: bool = False
        self.staging_area: dict = {}       # path -> content
        self.working_dir: dict = {}        # path -> content

    def init(self):
        self.branches['main'] = None       # main branch with no commits yet
        self.HEAD = 'main'

    def stage(self, file_path: str, content: str):
        self.staging_area[file_path] = content
        self.working_dir[file_path] = content

    def commit(self, message: str, author: str = "user") -> str:
        if not self.staging_area:
            raise ValueError("Nothing to commit — staging area is empty")

        # Create blobs for each staged file
        tree_files = {}
        for path, content in self.staging_area.items():
            blob = Blob.from_content(content)
            self.object_store.put(blob)
            tree_files[path] = blob.hash

        # Carry forward files from previous commit not overwritten
        parent_hash = self.current_commit_hash()
        if parent_hash:
            prev_commit = self.object_store.get(parent_hash)
            prev_tree = self.object_store.get(prev_commit.tree_hash)
            for path, blob_hash in prev_tree.files.items():
                if path not in tree_files:
                    tree_files[path] = blob_hash

        # Create tree and commit
        tree = Tree.from_files(tree_files)
        self.object_store.put(tree)

        commit = Commit.create(parent_hash, tree.hash, message, author)
        self.object_store.put(commit)

        # Advance branch or detached HEAD
        if self._is_detached:
            self.HEAD = commit.hash
        else:
            self.branches[self.HEAD] = commit.hash

        self.staging_area.clear()
        return commit.hash

    def current_commit_hash(self) -> Optional[str]:
        if self._is_detached:
            return self.HEAD
        return self.branches.get(self.HEAD)

    def create_branch(self, name: str):
        current = self.current_commit_hash()
        if current is None:
            raise ValueError("Cannot create branch — no commits yet")
        self.branches[name] = current

    def checkout(self, ref: str):
        """Checkout a branch name or commit hash."""
        if ref in self.branches:
            self.HEAD = ref
            self._is_detached = False
            commit_hash = self.branches[ref]
        elif self.object_store.exists(ref):
            self.HEAD = ref
            self._is_detached = True
            commit_hash = ref
        else:
            raise ValueError(f"Unknown ref: {ref}")

        # Restore working directory from the commit's tree
        if commit_hash:
            commit = self.object_store.get(commit_hash)
            tree = self.object_store.get(commit.tree_hash)
            self.working_dir = {}
            for path, blob_hash in tree.files.items():
                blob = self.object_store.get(blob_hash)
                self.working_dir[path] = blob.content

    def diff(self, hash1: str, hash2: str) -> DiffResult:
        c1 = self.object_store.get(hash1)
        c2 = self.object_store.get(hash2)
        tree1 = self.object_store.get(c1.tree_hash) if c1 else None
        tree2 = self.object_store.get(c2.tree_hash) if c2 else None

        files1 = tree1.files if tree1 else {}
        files2 = tree2.files if tree2 else {}

        added = [p for p in files2 if p not in files1]
        removed = [p for p in files1 if p not in files2]
        modified = [p for p in files1
                    if p in files2 and files1[p] != files2[p]]

        return DiffResult(added=added, removed=removed, modified=modified)

    def log(self) -> list:
        """Walk commit history from HEAD backwards."""
        commits = []
        current_hash = self.current_commit_hash()
        while current_hash:
            commit = self.object_store.get(current_hash)
            if commit is None:
                break
            commits.append(commit)
            current_hash = commit.parent_hash
        return commits
```

---

## Verification

**Scenario: Init, two commits, create branch, checkout, diff**

1. `repo.init()` -> branches={'main': None}, HEAD='main'
2. `repo.stage('README.md', '# Hello')` -> staging_area={'README.md': '# Hello'}
3. `repo.commit('initial commit')` -> blob created, tree created, commit C1 created; branches={'main': C1.hash}; staging_area cleared
4. `repo.stage('main.py', 'print("hi")')` -> staging_area={'main.py': '...'}
5. `repo.commit('add main.py')` -> C2 created; C2.parent_hash = C1.hash; tree2 has both README.md and main.py
6. `repo.create_branch('feature')` -> branches={'main': C2.hash, 'feature': C2.hash}
7. `repo.checkout('feature')` -> HEAD='feature'; working_dir restored from C2's tree
8. `repo.stage('main.py', 'print("world")')` -> modified content
9. `repo.commit('modify main.py')` -> C3 on feature branch
10. `repo.diff(C2.hash, C3.hash)` -> DiffResult(added=[], removed=[], modified=['main.py'])

---

## Deep Dive & Extensibility

### 1. "Why is content-addressed storage (CAS) better than file-path-based storage?"

In CAS, the key is the SHA hash of the content. This gives three properties:
- **Deduplication**: if two files have identical content, only one blob is stored
- **Integrity**: retrieving a blob and re-hashing it verifies it hasn't been corrupted
- **Immutability**: you cannot update a blob in-place; any change produces a new hash

```python
# If two files have the same content, they share one blob
blob_a = Blob.from_content("same content")
blob_b = Blob.from_content("same content")
assert blob_a.hash == blob_b.hash  # only stored once in ObjectStore
```

Git uses SHA-1 for its object store (`.git/objects`). The first 2 hex chars become a directory name, the remaining 38 become the filename — a simple sharding strategy for the filesystem.

### 2. "How does the commit DAG work and why a DAG?"

Each commit has a `parent_hash` pointer. This forms a directed acyclic graph where:
- Edges point from child to parent (time flows against edge direction)
- A merge commit has two parents
- A branch is just a named pointer to a commit node

```python
def get_ancestors(self, commit_hash: str) -> set:
    """BFS to collect all ancestor hashes."""
    visited = set()
    queue = [commit_hash]
    while queue:
        h = queue.pop()
        if h in visited or h is None:
            continue
        visited.add(h)
        commit = self.object_store.get(h)
        if commit and commit.parent_hash:
            queue.append(commit.parent_hash)
    return visited

def find_common_ancestor(self, hash1: str, hash2: str) -> Optional[str]:
    ancestors1 = self.get_ancestors(hash1)
    h = hash2
    while h:
        if h in ancestors1:
            return h
        commit = self.object_store.get(h)
        h = commit.parent_hash if commit else None
    return None
```

### 3. "How does fast-forward vs 3-way merge work?"

Fast-forward: branch B is a direct ancestor of branch A. Moving B's pointer to A's tip is sufficient — no new commit needed.

```python
def merge(self, source_branch: str):
    source_hash = self.branches[source_branch]
    target_hash = self.current_commit_hash()
    ancestors_of_source = self.get_ancestors(source_hash)
    if target_hash in ancestors_of_source:
        # Fast-forward: target is ancestor of source
        self.branches[self.HEAD] = source_hash
        return "fast-forward"
    # Otherwise: 3-way merge needed
    lca = self.find_common_ancestor(source_hash, target_hash)
    # Merge tree(lca), tree(source), tree(target)
    return self._three_way_merge(lca, source_hash, target_hash)
```

3-way merge: find the least common ancestor (LCA). For each file:
- Changed in source but not target: take source version
- Changed in target but not source: take target version
- Changed in both: conflict — require human resolution

### 4. "How does rebase work?"

Rebase replays commits from the current branch on top of a new base, creating new commit objects with new hashes. This rewrites history.

```python
def rebase(self, onto_branch: str):
    onto_hash = self.branches[onto_branch]
    current_hash = self.current_commit_hash()
    lca = self.find_common_ancestor(onto_hash, current_hash)

    # Collect commits to replay (from LCA to current, oldest first)
    commits_to_replay = []
    h = current_hash
    while h != lca:
        commits_to_replay.append(self.object_store.get(h))
        h = self.object_store.get(h).parent_hash
    commits_to_replay.reverse()

    # Replay each commit on top of onto
    new_base = onto_hash
    for old_commit in commits_to_replay:
        # Apply the diff from old_commit's parent to old_commit onto new_base
        # Create a new commit with parent=new_base
        new_commit = Commit.create(new_base, old_commit.tree_hash,
                                   old_commit.message, old_commit.author)
        self.object_store.put(new_commit)
        new_base = new_commit.hash

    self.branches[self.HEAD] = new_base
```

Because rebasing creates new hashes, the old commits become unreachable (garbage collectable).

### 5. "How does garbage collection work?"

Objects that are no longer reachable from any branch or tag can be deleted. GC does a graph traversal from all branch tips and tags, collecting all reachable hashes, then deletes everything else from the ObjectStore.

```python
def garbage_collect(self):
    reachable = set()
    # Start from all branch tips
    for commit_hash in self.branches.values():
        if commit_hash:
            reachable.update(self.get_ancestors(commit_hash))
            # Also mark the trees and blobs reachable from each commit
            for h in list(reachable):
                commit = self.object_store.get(h)
                if commit:
                    tree = self.object_store.get(commit.tree_hash)
                    if tree:
                        reachable.add(commit.tree_hash)
                        reachable.update(tree.files.values())

    # Delete unreachable objects
    all_hashes = list(self.object_store._store.keys())
    deleted = 0
    for h in all_hashes:
        if h not in reachable:
            del self.object_store._store[h]
            deleted += 1
    return deleted
```

---

## Interviewer Questions by Level

**Junior**: What is a commit? What information does it contain?

**Mid-level**: Why is content-addressable storage useful? What property does it give you for free?

**Senior**: Walk me through a 3-way merge. What is the role of the LCA? What happens when both branches modify the same file?

---

## Common Interview Questions

- **Q: Why use content-addressable storage instead of storing files by name?**
  A: It gives automatic deduplication, integrity verification (re-hash the content), and immutability. Two identical files share one blob; any corruption changes the hash.

- **Q: How do you compute a diff between two commits?**
  A: Get each commit's tree. Compare the (path -> blob_hash) maps: paths in tree2 not in tree1 are added; paths in tree1 not in tree2 are removed; paths in both with different hashes are modified.

- **Q: What is fast-forward merge?**
  A: When one branch is a direct ancestor of the other, you just move the branch pointer forward — no new merge commit is needed and history stays linear.

- **Q: What is detached HEAD state?**
  A: HEAD points to a commit hash directly instead of a branch name. New commits advance HEAD but no branch tracks them. Checking out a branch re-attaches HEAD. Commits made in detached HEAD become unreachable after checkout.

- **Q: How is a branch implemented internally?**
  A: A branch is just a named pointer (string) to a commit hash stored in a dictionary. Moving a branch is O(1) — just update the pointer value.

- **Q: How do you recover from a bad commit?**
  A: In Git terms, `git reset --hard <prev_hash>` moves the branch pointer back. The "bad" commit object still exists in the object store until garbage collection runs, so it is recoverable via `git reflog`.

- **Q: What is the difference between merge and rebase?**
  A: Merge creates a new commit with two parents, preserving the original branch history. Rebase replays commits on a new base, creating new commit objects and producing linear history but rewriting hashes.

- **Q: What is a tree object?**
  A: A tree maps file paths to blob hashes. It represents the state of the working directory at a commit. Trees can reference sub-trees (directories), forming a Composite pattern.

---

## Related

**Patterns applied here**

- [Composite Pattern](../../03-design-patterns/02-structural/composite-pattern.md)
- [Command Pattern](../../03-design-patterns/03-behavioral/command-pattern.md)

**SOLID focus**: [Single Responsibility](../../02-solid-principles/01-single-responsibility.md) · [Open/Closed](../../02-solid-principles/02-open-closed.md)

**Practice next**

- [Design S3 Object Storage](26-design-s3-object-storage.md)
- [Design Text Editor](31-design-text-editor.md)

The text editor uses the same undo/snapshot machinery.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
