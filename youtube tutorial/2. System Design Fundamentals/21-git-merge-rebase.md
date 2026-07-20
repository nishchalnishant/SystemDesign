# Git: Merge vs Rebase & How Git Works

> **Source**: Videos #39, #47 from the playlist
> - Git MERGE vs REBASE: Everything You Need to Know
> - How Git Works: Explained in 4 Minutes

---

## How Git Works

### Core Concepts
- **Repository**: Project history stored as directed acyclic graph (DAG) of commits
- **Commit**: Snapshot of files + metadata (author, timestamp, parent commit)
- **Branch**: Lightweight pointer to a commit
- **HEAD**: Pointer to current branch/commit

### Git Object Model
```
Blob     → File contents (hashed)
Tree     → Directory listing (points to blobs and other trees)
Commit   → Snapshot (points to tree + parent commits + metadata)
Tag      → Named reference to a commit
```

### The Three Areas
```
Working Directory → Staging Area (Index) → Repository (.git)
       ↓ git add         ↓ git commit
```

---

## Git Merge vs Rebase

### Git Merge
```
Before:
main:    A → B → C
feature:      └→ D → E

After merge:
main:    A → B → C → M (merge commit)
feature:      └→ D → E ↗
```
- Creates a **merge commit** that combines both branches
- **Preserves complete history** including branch structure
- Non-destructive — no existing commits are changed

### Git Rebase
```
Before:
main:    A → B → C
feature:      └→ D → E

After rebase:
main:    A → B → C
feature:              → D' → E' (new commits with same changes)
```
- **Replays commits** on top of the target branch
- Creates a **linear history** (no merge commits)
- Rewrites commit hashes (D→D', E→E')

### When to Use Which

| Use Merge When | Use Rebase When |
|---|---|
| Working on shared/public branches | Cleaning up local feature branch before PR |
| Want to preserve branch history | Want a clean, linear history |
| Multiple people on same branch | Only you are working on the branch |
| Default safe option | Before merging feature → main |

### Golden Rule
> **Never rebase a public/shared branch**
> 
> Rebasing rewrites history. If others have based work on those commits, it causes chaos.

### Interactive Rebase (`git rebase -i`)
- **squash**: Combine commits into one
- **edit**: Modify a commit
- **reorder**: Change commit order
- **drop**: Remove a commit
- Great for cleaning up commit history before a PR

---

## Common Git Workflow

```
1. git checkout -b feature/my-feature    # Create feature branch
2. # ... make changes ...
3. git add . && git commit -m "msg"      # Commit changes
4. git fetch origin main                 # Get latest main
5. git rebase origin/main                # Rebase on main (clean history)
6. git push origin feature/my-feature    # Push feature branch
7. # Create Pull Request
8. # After review: merge PR into main
```
