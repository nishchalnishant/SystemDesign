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

```java
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public final class Hashing {
    private Hashing() {}

    public static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}

public final class Blob {
    private final String content;
    private final String hash;

    private Blob(String content, String hash) {
        this.content = content;
        this.hash = hash;
    }

    public static Blob fromContent(String content) {
        return new Blob(content, Hashing.sha256(content));
    }

    public String getContent() { return content; }
    public String getHash() { return hash; }
}

public final class Tree {
    private final Map<String, String> files; // path -> blob_hash
    private final String hash;

    private Tree(Map<String, String> files, String hash) {
        this.files = files;
        this.hash = hash;
    }

    public static Tree fromFiles(Map<String, String> files) {
        // Deterministic hash: sort by path
        List<String> entries = new ArrayList<>();
        List<String> sortedKeys = new ArrayList<>(files.keySet());
        Collections.sort(sortedKeys);
        for (String key : sortedKeys) {
            entries.add("(" + key + ", " + files.get(key) + ")");
        }
        String serialized = "[" + String.join(", ", entries) + "]";
        return new Tree(new HashMap<>(files), Hashing.sha256(serialized));
    }

    public Map<String, String> getFiles() { return files; }
    public String getHash() { return hash; }
}

public final class Commit {
    private final String hash;
    private final String parentHash; // nullable
    private final String treeHash;
    private final String message;
    private final String author;
    private final double timestamp;

    private Commit(String hash, String parentHash, String treeHash,
                    String message, String author, double timestamp) {
        this.hash = hash;
        this.parentHash = parentHash;
        this.treeHash = treeHash;
        this.message = message;
        this.author = author;
        this.timestamp = timestamp;
    }

    public static Commit create(String parentHash, String treeHash, String message, String author) {
        double ts = System.currentTimeMillis() / 1000.0;
        String content = String.format("%s|%s|%s|%s|%s", parentHash, treeHash, message, author, ts);
        String commitHash = Hashing.sha256(content);
        return new Commit(commitHash, parentHash, treeHash, message, author, ts);
    }

    public String getHash() { return hash; }
    public String getParentHash() { return parentHash; }
    public String getTreeHash() { return treeHash; }
    public String getMessage() { return message; }
    public String getAuthor() { return author; }
    public double getTimestamp() { return timestamp; }
}

/** Content-addressed storage for blobs, trees, and commits. */
public class ObjectStore {
    private final Map<String, Object> store = new HashMap<>(); // hash -> object

    public String put(Blob obj) { store.put(obj.getHash(), obj); return obj.getHash(); }
    public String put(Tree obj) { store.put(obj.getHash(), obj); return obj.getHash(); }
    public String put(Commit obj) { store.put(obj.getHash(), obj); return obj.getHash(); }

    @SuppressWarnings("unchecked")
    public <T> T get(String objHash) {
        return (T) store.get(objHash);
    }

    public boolean exists(String objHash) {
        return store.containsKey(objHash);
    }

    public Map<String, Object> getStore() { return store; }
}

public final class DiffResult {
    private final List<String> added;    // paths added in commit2 not in commit1
    private final List<String> removed;  // paths removed
    private final List<String> modified; // paths present in both but different blob hash

    public DiffResult(List<String> added, List<String> removed, List<String> modified) {
        this.added = added;
        this.removed = removed;
        this.modified = modified;
    }

    public List<String> getAdded() { return added; }
    public List<String> getRemoved() { return removed; }
    public List<String> getModified() { return modified; }
}

public class Repository {
    private final ObjectStore objectStore = new ObjectStore();
    private final Map<String, String> branches = new HashMap<>(); // name -> commit_hash
    private String head = "main"; // branch name or detached commit hash
    private boolean isDetached = false;
    private final Map<String, String> stagingArea = new LinkedHashMap<>(); // path -> content
    private Map<String, String> workingDir = new LinkedHashMap<>();        // path -> content

    public void init() {
        branches.put("main", null); // main branch with no commits yet
        head = "main";
    }

    public void stage(String filePath, String content) {
        stagingArea.put(filePath, content);
        workingDir.put(filePath, content);
    }

    public String commit(String message, String author) {
        if (stagingArea.isEmpty()) {
            throw new IllegalStateException("Nothing to commit — staging area is empty");
        }

        // Create blobs for each staged file
        Map<String, String> treeFiles = new HashMap<>();
        for (Map.Entry<String, String> entry : stagingArea.entrySet()) {
            Blob blob = Blob.fromContent(entry.getValue());
            objectStore.put(blob);
            treeFiles.put(entry.getKey(), blob.getHash());
        }

        // Carry forward files from previous commit not overwritten
        String parentHash = currentCommitHash();
        if (parentHash != null) {
            Commit prevCommit = objectStore.get(parentHash);
            Tree prevTree = objectStore.get(prevCommit.getTreeHash());
            for (Map.Entry<String, String> entry : prevTree.getFiles().entrySet()) {
                treeFiles.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }

        // Create tree and commit
        Tree tree = Tree.fromFiles(treeFiles);
        objectStore.put(tree);

        Commit newCommit = Commit.create(parentHash, tree.getHash(), message, author);
        objectStore.put(newCommit);

        // Advance branch or detached HEAD
        if (isDetached) {
            head = newCommit.getHash();
        } else {
            branches.put(head, newCommit.getHash());
        }

        stagingArea.clear();
        return newCommit.getHash();
    }

    public String currentCommitHash() {
        if (isDetached) {
            return head;
        }
        return branches.get(head);
    }

    public void createBranch(String name) {
        String current = currentCommitHash();
        if (current == null) {
            throw new IllegalStateException("Cannot create branch — no commits yet");
        }
        branches.put(name, current);
    }

    /** Checkout a branch name or commit hash. */
    public void checkout(String ref) {
        String commitHash;
        if (branches.containsKey(ref)) {
            head = ref;
            isDetached = false;
            commitHash = branches.get(ref);
        } else if (objectStore.exists(ref)) {
            head = ref;
            isDetached = true;
            commitHash = ref;
        } else {
            throw new IllegalArgumentException("Unknown ref: " + ref);
        }

        // Restore working directory from the commit's tree
        if (commitHash != null) {
            Commit commit = objectStore.get(commitHash);
            Tree tree = objectStore.get(commit.getTreeHash());
            workingDir = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : tree.getFiles().entrySet()) {
                Blob blob = objectStore.get(entry.getValue());
                workingDir.put(entry.getKey(), blob.getContent());
            }
        }
    }

    public DiffResult diff(String hash1, String hash2) {
        Commit c1 = objectStore.get(hash1);
        Commit c2 = objectStore.get(hash2);
        Tree tree1 = c1 != null ? (Tree) objectStore.get(c1.getTreeHash()) : null;
        Tree tree2 = c2 != null ? (Tree) objectStore.get(c2.getTreeHash()) : null;

        Map<String, String> files1 = tree1 != null ? tree1.getFiles() : Collections.emptyMap();
        Map<String, String> files2 = tree2 != null ? tree2.getFiles() : Collections.emptyMap();

        List<String> added = new ArrayList<>();
        for (String p : files2.keySet()) {
            if (!files1.containsKey(p)) added.add(p);
        }
        List<String> removed = new ArrayList<>();
        for (String p : files1.keySet()) {
            if (!files2.containsKey(p)) removed.add(p);
        }
        List<String> modified = new ArrayList<>();
        for (String p : files1.keySet()) {
            if (files2.containsKey(p) && !files1.get(p).equals(files2.get(p))) modified.add(p);
        }

        return new DiffResult(added, removed, modified);
    }

    /** Walk commit history from HEAD backwards. */
    public List<Commit> log() {
        List<Commit> commits = new ArrayList<>();
        String currentHash = currentCommitHash();
        while (currentHash != null) {
            Commit commit = objectStore.get(currentHash);
            if (commit == null) break;
            commits.add(commit);
            currentHash = commit.getParentHash();
        }
        return commits;
    }
}
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

```java
// If two files have the same content, they share one blob
Blob blobA = Blob.fromContent("same content");
Blob blobB = Blob.fromContent("same content");
assert blobA.getHash().equals(blobB.getHash()); // only stored once in ObjectStore
```

Git uses SHA-1 for its object store (`.git/objects`). The first 2 hex chars become a directory name, the remaining 38 become the filename — a simple sharding strategy for the filesystem.

### 2. "How does the commit DAG work and why a DAG?"

Each commit has a `parent_hash` pointer. This forms a directed acyclic graph where:
- Edges point from child to parent (time flows against edge direction)
- A merge commit has two parents
- A branch is just a named pointer to a commit node

```java
/** BFS to collect all ancestor hashes. */
public Set<String> getAncestors(String commitHash) {
    Set<String> visited = new HashSet<>();
    Deque<String> queue = new ArrayDeque<>();
    queue.push(commitHash);
    while (!queue.isEmpty()) {
        String h = queue.pop();
        if (h == null || visited.contains(h)) continue;
        visited.add(h);
        Commit commit = objectStore.get(h);
        if (commit != null && commit.getParentHash() != null) {
            queue.push(commit.getParentHash());
        }
    }
    return visited;
}

public String findCommonAncestor(String hash1, String hash2) {
    Set<String> ancestors1 = getAncestors(hash1);
    String h = hash2;
    while (h != null) {
        if (ancestors1.contains(h)) {
            return h;
        }
        Commit commit = objectStore.get(h);
        h = commit != null ? commit.getParentHash() : null;
    }
    return null;
}
```

### 3. "How does fast-forward vs 3-way merge work?"

Fast-forward: branch B is a direct ancestor of branch A. Moving B's pointer to A's tip is sufficient — no new commit needed.

```java
public String merge(String sourceBranch) {
    String sourceHash = branches.get(sourceBranch);
    String targetHash = currentCommitHash();
    Set<String> ancestorsOfSource = getAncestors(sourceHash);
    if (ancestorsOfSource.contains(targetHash)) {
        // Fast-forward: target is ancestor of source
        branches.put(head, sourceHash);
        return "fast-forward";
    }
    // Otherwise: 3-way merge needed
    String lca = findCommonAncestor(sourceHash, targetHash);
    // Merge tree(lca), tree(source), tree(target)
    return threeWayMerge(lca, sourceHash, targetHash);
}
```

3-way merge: find the least common ancestor (LCA). For each file:
- Changed in source but not target: take source version
- Changed in target but not source: take target version
- Changed in both: conflict — require human resolution

### 4. "How does rebase work?"

Rebase replays commits from the current branch on top of a new base, creating new commit objects with new hashes. This rewrites history.

```java
public void rebase(String ontoBranch) {
    String ontoHash = branches.get(ontoBranch);
    String currentHash = currentCommitHash();
    String lca = findCommonAncestor(ontoHash, currentHash);

    // Collect commits to replay (from LCA to current, oldest first)
    List<Commit> commitsToReplay = new ArrayList<>();
    String h = currentHash;
    while (!Objects.equals(h, lca)) {
        Commit commit = objectStore.get(h);
        commitsToReplay.add(commit);
        h = commit.getParentHash();
    }
    Collections.reverse(commitsToReplay);

    // Replay each commit on top of onto
    String newBase = ontoHash;
    for (Commit oldCommit : commitsToReplay) {
        // Apply the diff from oldCommit's parent to oldCommit onto newBase
        // Create a new commit with parent=newBase
        Commit newCommit = Commit.create(newBase, oldCommit.getTreeHash(),
                oldCommit.getMessage(), oldCommit.getAuthor());
        objectStore.put(newCommit);
        newBase = newCommit.getHash();
    }

    branches.put(head, newBase);
}
```

Because rebasing creates new hashes, the old commits become unreachable (garbage collectable).

### 5. "How does garbage collection work?"

Objects that are no longer reachable from any branch or tag can be deleted. GC does a graph traversal from all branch tips and tags, collecting all reachable hashes, then deletes everything else from the ObjectStore.

```java
public int garbageCollect() {
    Set<String> reachable = new HashSet<>();
    // Start from all branch tips
    for (String commitHash : branches.values()) {
        if (commitHash != null) {
            reachable.addAll(getAncestors(commitHash));
            // Also mark the trees and blobs reachable from each commit
            for (String h : new ArrayList<>(reachable)) {
                Commit commit = objectStore.get(h);
                if (commit != null) {
                    Tree tree = objectStore.get(commit.getTreeHash());
                    if (tree != null) {
                        reachable.add(commit.getTreeHash());
                        reachable.addAll(tree.getFiles().values());
                    }
                }
            }
        }
    }

    // Delete unreachable objects
    List<String> allHashes = new ArrayList<>(objectStore.getStore().keySet());
    int deleted = 0;
    for (String h : allHashes) {
        if (!reachable.contains(h)) {
            objectStore.getStore().remove(h);
            deleted++;
        }
    }
    return deleted;
}
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
- [Memento Pattern](../../03-design-patterns/03-behavioral/memento-pattern.md) — commit snapshots as restorable working-tree state

**SOLID focus**: [Single Responsibility](../../02-solid-principles/01-single-responsibility.md) · [Open/Closed](../../02-solid-principles/02-open-closed.md)

**Practice next**

- [Design S3 Object Storage](26-design-s3-object-storage.md)
- [Design Text Editor](31-design-text-editor.md)

The text editor uses the same undo/snapshot machinery.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../01-oop-fundamentals/uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
