---
module: 05-hld-problems
topic: Hard
status: unread
tags: [05-hld-problems, system-design, hard, github, git, code-hosting, pull-requests, ci-cd]
---
# Design GitHub (Code Repository Hosting)

> **Difficulty**: Hard | **Asked at**: Microsoft, GitLab, Atlassian, Amazon

---

## Problem Statement

Design a code repository hosting platform like GitHub. Developers store Git repositories in the cloud, collaborate via pull requests, run code search, and trigger CI/CD pipelines. The system must handle millions of repositories, large binary files, and high-concurrency cloning during popular repository releases.

---

## Functional Requirements

1. **Repository hosting**: Create, clone, push, and pull Git repositories
2. **Pull requests**: Create PRs, review code (comments, suggestions), merge
3. **Code search**: Full-text search across all files in all repositories
4. **Issues and discussions**: Bug tracker and conversation threads
5. **Releases and tags**: Tag commits, create release archives (zip/tarball)
6. **CI/CD**: Trigger webhooks on push/PR; integrate with Actions

---

## Non-Functional Requirements

- **Scale**: 100M repositories, 50M active developers, 10M git operations/day
- **Latency**: `git clone` of a 100 MB repo < 10s; `git push` < 5s; code search < 1s
- **Availability**: 99.99% — a git push must always succeed
- **Storage**: 100M repos × 500 MB avg = 50 PB; with pack files and dedup: ~15 PB
- **Concurrency**: 1,000 concurrent clones of `torvalds/linux` during Linus's release

---

## Core Entities

| Entity | Key Fields |
|--------|-----------|
| `Repository` | repo_id, owner_id, name, is_private, default_branch, disk_path, size_bytes |
| `Commit` | sha (40-char hex), repo_id, tree_sha, parent_shas[], author, message, timestamp |
| `Branch` | repo_id, name, head_commit_sha, is_protected |
| `PullRequest` | pr_id, repo_id, title, author_id, head_branch, base_branch, status, created_at |
| `PRReview` | review_id, pr_id, reviewer_id, body, state (approved/changes_requested) |

---

## API Design

**Git protocol** (SSH/HTTPS Smart HTTP):
```
git clone https://github.com/owner/repo.git
  → HTTPS smart HTTP protocol:
    GET /owner/repo.git/info/refs?service=git-upload-pack
    POST /owner/repo.git/git-upload-pack (negotiation + pack data)

git push origin main
  → POST /owner/repo.git/git-receive-pack (refs + pack data)
```

**REST API**:
```http
POST /api/v1/repos
Body: { "name": "my-project", "private": true }

POST /api/v1/repos/{owner}/{repo}/pulls
Body: { "title": "Add feature X", "head": "feature-x", "base": "main" }

GET /api/v1/repos/{owner}/{repo}/contents/{path}?ref=main
Response 200: { "type": "file", "content": "<base64>", "sha": "..." }

GET /api/v1/search/code?q=def+connect&repo=owner/repo&language=python
Response 200: { "items": [{ "path": "db/connection.py", "line": 42, "text": "..." }] }
```

---

## High-Level Design

```
Developer
  │ git push / git clone (SSH or HTTPS)
  ▼
Git Frontend (nginx + git-http-backend or SSH server)
  │ Auth → Route to correct repository server
  ▼
Repository Server
  │ Git data: stored on local NFS or distributed object store
  │ git-upload-pack (clone/fetch): read from bare repo
  │ git-receive-pack (push): write to bare repo → run hooks
  │
  ├── Post-receive hook:
  │     publish event to Kafka (push-events)
  │     → trigger CI/CD webhooks
  │     → update search index
  │     → update PR status
  │
  └── Repository metadata: PostgreSQL

Storage:
  Git object store: S3 (pack files, loose objects)
  NFS: local fast cache for hot repos
  PostgreSQL: repositories, commits index, PRs, issues
  Elasticsearch: code search index
```

---

## Deep Dive 1: Git Storage at Scale

**Problem**: 100M repositories × 500 MB = 50 PB of Git data. Git stores data as objects (blobs for file content, trees for directory structure, commits). How do you store this efficiently at scale?

**Git's native deduplication**: Git content-addresses all objects by SHA-1 of their content. If two repositories have the same file, they share the same blob object. Popular open-source code (Linux headers, standard libraries) appears in millions of forks → stored once.

**Pack files**: Git groups objects into pack files (`.pack`), compressed with zlib. A pack file contains delta-compressed objects — each object is stored as a delta against a similar object. A 500 MB repo may fit in a 50 MB pack file.

**Storage backend**:
- Actively cloned repos (hot): stored on NFS on the repository server. Direct filesystem access for fast read.
- Inactive repos (cold): stored as pack files in S3. On first clone after cold tier, warm to NFS.
- Very large repos (monorepos, Linux kernel): stored on dedicated high-capacity servers with SSD.

**Forking**: When a repo is forked, GitHub does not copy the objects. The fork shares a "storage network" with the parent — both repos point to the same object store. `git push` to the fork writes only the new objects; shared objects are never duplicated. This is why GitHub forks are nearly instant regardless of repo size.

---

## Deep Dive 2: Code Search

**Problem**: GitHub Code Search must find every file containing `def connect` across 100M repositories (~500B files, ~50 TB of source code). Return results in < 1 second.

**Elasticsearch for code search**:
- Index: one document per file, with fields: `repo_id, path, language, content`
- Analyzer: code-specific tokenizer (splits on non-alphanumeric, preserves identifiers like `camelCase`, `snake_case`)
- Search query: `{ "match": { "content": "def connect" } }` filtered by `repo_id` or `language`

**Incremental indexing**: On every push, only re-index changed files. Kafka push-events → indexer consumer → Elasticsearch upsert.

**Index size**: 50 TB of source code, compressed with Elasticsearch: ~10 TB. Sharded across 500 ES nodes (20 GB per node), searchable across all shards in parallel.

**Trigram index** (GitHub's actual approach for regex search):
- For regex-capable search (`/def\s+connect.*/`), Elasticsearch's inverted index is insufficient
- Precompute all trigrams (3-char substrings) of all files
- Store: trigram → list of (file_id, position)
- Query `/def\s+connect/`: extract trigrams `def`, `ef `, `f c`, `con`, `onn`, `nne`, `nec`, `ect` → intersect file lists → verify regex on candidate files
- Result: regex search on 50 TB code in < 2 seconds

---

## Deep Dive 3: Pull Request Merge and Branch Protection

**Problem**: A PR has passed review and CI. Two developers simultaneously click "Merge" — only one merge should succeed.

**Merge atomicity** (PostgreSQL):
```sql
UPDATE pull_requests
SET status = 'merging',
    merge_started_at = now()
WHERE pr_id = :pr_id
  AND status = 'open'  -- optimistic lock
RETURNING pr_id;
-- 0 rows: another request already started the merge
```

**Merge types**:
- **Merge commit**: Preserves branch history; creates a merge commit. `git merge --no-ff`
- **Squash and merge**: Squashes all PR commits into one. Clean linear history.
- **Rebase and merge**: Replays PR commits on top of base branch. Linear history, no merge commit.

**Branch protection rules** (enforced server-side in git-receive-pack hook):
- Require status checks (CI must pass)
- Require PR reviews (minimum N approved reviews)
- Require signed commits
- Prevent force push (reject non-fast-forward pushes to protected branches)

**Post-merge actions** (Kafka push-event → downstream services):
- Deploy: trigger deployment pipeline
- Notification: notify PR author + reviewers of merge
- Issue close: if PR body contains "Fixes #456", close issue 456
- Code search: reindex changed files

---

## Interviewer Questions by Level

**Junior**:
- What is a Git repository? What is the difference between `git clone` and `git pull`?
- What is a pull request? What is the typical PR review flow?
- What happens when two people push to the same branch at the same time?

**Mid-level**:
- How does GitHub store 100M repositories efficiently? What is Git's native deduplication?
- How does fork work at the storage level? Why is forking a large repo nearly instant?
- How do you prevent two people from merging the same PR simultaneously?

**Senior**:
- Design GitHub Code Search — how do you index 50 TB of source code for sub-second full-text and regex search?
- How do you handle 1,000 concurrent clones of a hot repository (like the Linux kernel at release time)?
- Design GitHub Actions — how do you trigger and orchestrate CI/CD pipelines on git push events?
- How do you implement pull request code review with inline comments that survive rebases and force pushes?
