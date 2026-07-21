> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design GitHub — code repository hosting with Git operations, pull request workflows, code search, and CI/CD pipeline triggering at millions-of-repos scale.
>
> **Key design decisions:**
> - Repository storage: Git objects (blobs, trees, commits, tags) stored in object store; repositories as bare Git repos on distributed storage (Gitaly at GitLab, network of NFS at GitHub)
> - Repository routing: consistent hashing routes repository operations to specific storage nodes; replica set per shard for HA
> - Clone/fetch: large repos offloaded to CDN (pack-objects for smart HTTP); popular repos cached at CDN edge; partial clone for monorepos
> - Pull request model: PR = branch + metadata; diff computed on PR creation and cached; code review inline comments stored as GitHub Suggestions objects
> - Code search: Elasticsearch full-text index of repository content; incremental indexing on push; indexed by repo, path, language, content
> - Webhooks: push event → fan-out to registered webhooks; at-least-once via retry queue; CI/CD systems (GitHub Actions) triggered via webhook
> - CI/CD: GitHub Actions = YAML workflow definition + runner infrastructure; job queue in PostgreSQL; runners pull jobs; artifact storage in S3
>
> **Key takeaway:** Repository storage routing is the core scalability challenge — consistent hashing with replica sets per shard ensures both load distribution and HA; Git's content-addressable storage (SHA1 objects) naturally enables deduplication.

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

---

## Back-of-Envelope Estimation

**Scale inputs (given in NFRs):**
- 100M repos; 50M active users; 1B git operations/day; 10M CI pipeline runs/day

**Git operation throughput:**
- 1B git ops/day ÷ 86,400 sec = **~11,574 ops/sec** average
- Mix: ~60% reads (clone, fetch, ls-remote), ~30% writes (push), ~10% API (REST/GraphQL)
- Write (push) rate: 11,574 × 30% = **~3,472 pushes/sec**
- Each push triggers webhooks + CI: 3,472 × 1.5 avg webhooks = **~5,208 webhook events/sec**

**Repository storage:**
- 100M repos × average 50 MB per repo (git objects, packfiles) = **~5 PB** raw
- With 3× replication: **~15 PB** physical across the distributed object store
- Large repos (monorepos like chromium, android): up to 50 GB each; ~10K large repos × 50 GB = **500 TB** — top 0.01% of repos use 10% of storage

**Clone/fetch bandwidth:**
- 50M DAU; 10% clone per day (5M clones); average clone 50 MB = **~250 TB/day** outbound for clones
- 250 TB ÷ 86,400 sec = **~2.9 GB/sec** sustained clone bandwidth → served from CDN/edge

**Git pack file generation:**
- On `git clone`, server runs `git pack-objects` to compute a packfile: CPU-bound
- Shallow clone (depth=1, for CI): fetch only the latest commit tree — reduces clone from 50 MB to ~5 MB average
- Full clone CPU: 50 MB packfile generation takes ~200ms server CPU
- 3,472 pushes/sec trigger GC/pack operations: async, queued, not on the critical path

**CI pipeline capacity:**
- 10M CI runs/day ÷ 86,400 sec = **~115 pipeline starts/sec**
- Each pipeline runs for ~5 minutes average
- Concurrently running pipelines: 115/sec × 300 sec = **~34,500 concurrent CI jobs**
- Each job needs ~4 vCPUs + 8 GB RAM (build + test): 34,500 × 4 vCPUs = **138,000 vCPUs** for CI fleet

**Metadata DB (repo, commit, PR, issue):**
- 100M repos × 1,000 commits avg = **100B commit objects** (stored as compressed git objects, not DB rows)
- DB stores: repo metadata, PR records, issue records, user data — not git objects
- PR records: 100M repos × 5 avg open PRs = **500M PR rows** × 2 KB each = **~1 TB** — shardable by repo_id

**Architecture decisions driven by these numbers:**
- **Git object store on distributed blob storage (S3-like), not POSIX filesystem**: 5 PB of git objects across 100M repos cannot fit on one filesystem. Git's content-addressed model (every object is SHA1/SHA256 of its content, stored as a blob) maps naturally to S3's key-value semantics. Repo ID + object SHA = S3 key. No directory structure needed. At 3,472 pushes/sec, S3's parallel PUT rate (~5,500 requests/sec per prefix) handles the load.
- **Shallow clone by default for CI**: CI doesn't need full git history — only the current commit tree. Shallow clone (depth=1) reduces clone size from 50 MB to ~5 MB average — **10× bandwidth reduction**. At 115 CI pipeline starts/sec, full clones would consume 115 × 50 MB = 5.75 GB/sec of bandwidth. Shallow clones: 115 × 5 MB = 575 MB/sec — fits within a reasonable CDN budget.
- **Webhook fan-out via Kafka, not synchronous HTTP**: At 5,208 webhook events/sec, synchronously calling each webhook URL (which can be slow, fail, or timeout) on the critical path of a push would make pushes unreliable. Kafka decouples: push completes, event is written to Kafka (<1ms), a webhook delivery service reads from Kafka and retries failed deliveries with exponential backoff. This also enables the event stream for CI triggers, notifications, and audit logs from a single Kafka topic.

---

## Related

**Concepts used in this design**

- [Storage Fundamentals](../../01-foundations/02-hardware-and-networking/01-storage-fundamentals.md)
- [Sharding](../../02-building-blocks/03-data-partitioning/01-sharding.md)
- [Elasticsearch Internals](../../04-advanced-topics/03-internals/09-elasticsearch-internals.md)
- [Consistency & Conflicts](../../01-foundations/05-advanced-distributed-theory/01-consistency-and-conflicts.md)

**Practice next**

- [Dropbox Sync](../03-hard/dropbox-sync.md)
- [Web Crawler](../01-easy/web-crawler.md)

Code search reuses the crawler and index machinery.

**Frameworks**: [HLD Template](../../07-interview-templates/01-frameworks/01-hld-template.md) · [Capacity Estimation](../../07-interview-templates/02-cheat-sheets/02-capacity-estimation.md) · [Trade-offs Cheat Sheet](../../07-interview-templates/02-cheat-sheets/01-trade-offs-cheat-sheet.md)
