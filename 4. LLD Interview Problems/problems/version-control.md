# Design Version Control System (Git)

> **Difficulty**: Hard  
> **Topics**: Graph Theory (DAG), Hashing, Content Addressable Storage  
> **Features**: Commit, Branch, Merge, Log.

## Problem Statement

Design a Git-like system.
- **Entities**: Blob (Content), Commit (Snapshot), Branch (Pointer).
- **Core Mechanism**: Deduplication via Hashing.

## Comparison: Deltas vs Snapshots

- **SVN**: Stores Deltas (Diffs). Slow to reconstruct.
- **Git**: Stores Snapshots (Full state). Fast to traverse. Uses Blobs to avoid duplicates.

## Implementation

### Commit Node

```python
import hashlib
import datetime

class Commit:
    def __init__(self, message, files_snapshot, parent_id):
        self.message = message
        self.files = files_snapshot # {filename: content_hash}
        self.parent_id = parent_id
        self.id = self._generate_id()

    def _generate_id(self):
        # SHA-1 of content + meta
        data = f"{self.message}{self.parent_id}{str(self.files)}"
        return hashlib.sha1(data.encode()).hexdigest()[:7]
```

### VCS Engine

```python
class MiniGit:
    def __init__(self):
        self.commits = {} 
        self.branches = {"master": None}
        self.current_branch = "master"
        self.staging = {}

    def commit(self, message):
        parent_id = self.branches[self.current_branch]
        
        # 1. Inherit parent state
        new_files = {}
        if parent_id:
            new_files = self.commits[parent_id].files.copy()
            
        # 2. Apply staging
        new_files.update(self.staging)
        
        # 3. Create Commit
        c = Commit(message, new_files, parent_id)
        self.commits[c.id] = c
        
        # 4. Advance Branch Pointer
        self.branches[self.current_branch] = c.id
        self.staging.clear()
        return c.id

    def create_branch(self, name):
        # Point new branch to current HEAD
        self.branches[name] = self.branches[self.current_branch]

    def switch_branch(self, name):
        self.current_branch = name

    def merge(self, source_branch):
        # Simplified Fast-Forward merge
        source_head = self.branches[source_branch]
        # In real Git: Ancestry check & 3-way merge logic
        # Here: Just apply source files on top
        source_files = self.commits[source_head].files
        self.staging.update(source_files)
        self.commit(f"Merge {source_branch}")
```

## Interview Q&A

**Q: "How to handle Merge Conflicts?"**
- A: "Find Common Ancestor. Diff(Ancestor, Current) vs Diff(Ancestor, Source). If both modified same lines, flag conflict."

**Q: "Detached HEAD?"**
- A: "HEAD points to Commit Hash instead of Branch Ref. Commits made here are garbage collected if not tagged/branched."
