---
module: 05-hld-problems
topic: Hard
status: unread
tags: [05-hld-problems, system-design, hard]
---
# Design a Code Repository Hosting Service (GitHub)

## Problem Statement

Design a distributed code repository hosting platform that supports:
- Git push/pull/clone for millions of repositories
- Web-based code browsing, diff viewing, pull requests
- CI/CD trigger pipeline on push events
- 100M users, 400M repositories, peak load during business hours

---

## Functional Requirements

- Create/clone/push/pull Git repositories
- Browse files and commit history via web UI
- View diffs between commits and branches
- Create, review, and merge pull requests
- Webhook notifications on push/PR events
- Trigger CI pipelines on push/merge

## Non-Functional Requirements

- **Availability**: 99.95% (git push/pull must work even during incidents)
- **Durability**: Zero data loss — a commit acked must be permanently stored
- **Latency**: git push < 3s for repos < 100MB; web file browse < 100ms
- **Scale**: 400M repos; 100M users; 10M git operations/day; 100K CI triggers/hour (peak)
- **Storage**: ~50TB of repository data (compressed Git objects)

---

## Capacity Estimation

```
Repositories: 400M repos
  Average repo size: 10MB (mostly small; long tail of large repos)
  Total: 400M × 10MB = 4 PB of raw objects
  With compression and deduplication: ~500 TB active, multi-PB archived

Git operations:
  100M users × 10 git operations/day = 1B operations/day = ~11,600 ops/sec
  Peak (9 AM - 6 PM): 3× = ~35,000 ops/sec

Web API requests:
  File browse, PR views, commit history: 100M users × 50 API calls/day
  = 5B calls/day = ~58,000 RPS average; peak ~200,000 RPS

CI triggers:
  Assume 20% of pushes trigger CI → 2M pushes/day → 23 triggers/sec avg
  Peak: ~100,000 triggers/hour = ~28 triggers/sec

Diffs served:
  PR views: 10M PR views/day; avg diff = 500 lines
  = 10M × 500 lines of diff HTML/JSON per day
```

---

## Git Internals (Foundation)

Understanding Git storage is prerequisite to this design.

### Object Types

Git stores everything as content-addressable objects identified by SHA-1/SHA-256 hash:

```
Blob    → file contents (no filename, no path)
Tree    → directory listing: list of (mode, name, SHA) for blobs and sub-trees
Commit  → tree SHA + parent commit SHA(s) + author + message
Tag     → named pointer to a commit
```

```
Commit c1:
  tree: t1
  parent: c0
  message: "Add login"

Tree t1:
  blob b1 "README.md" (SHA: abc123)
  blob b2 "main.go"   (SHA: def456)
  tree t2 "src/"      (SHA: ghi789)
```

**Content-addressable:** the same file content always has the same SHA. If two repos have identical files, they share the same blob SHA — this enables deduplication.

### Packfiles

Git initially stores each object as a loose file: `.git/objects/ab/cdef123...`. For large repos, this creates millions of small files (slow filesystem). Git `gc` packs them into **packfiles**:

```
packfile.pack:   binary; all objects delta-compressed against similar objects
packfile.idx:    sorted index of (SHA → byte offset in pack)

Delta compression: instead of storing full content of v2,
  store: "v2 = v1 + these changes" (binary diff)
  A 10KB file modified by 1 line: delta ≈ 100 bytes instead of 10KB
```

**Implication for the system:**
- Pushing large repos: client sends a packfile (already delta-compressed), server unpacks to object store
- Serving `git clone`: server assembles a packfile from requested objects and streams it

---

## High-Level Architecture

```
                        ┌─────────────────────────────────┐
                        │   Git Clients (git push/pull)    │
                        │   Web Clients (browser, API)     │
                        └──────────────────┬──────────────┘
                                           │
                    ┌──────────────────────┼──────────────────────┐
                    │                      │                       │
            ┌───────▼──────┐      ┌────────▼───────┐    ┌─────────▼──────┐
            │  Git Smart   │      │   Web / REST   │    │    Webhook     │
            │  HTTP / SSH  │      │   API Service  │    │    Fanout Svc  │
            │  Gateway     │      │   (file browse,│    │  (CI triggers) │
            └───────┬──────┘      │    PR, search) │    └─────────┬──────┘
                    │             └────────┬───────┘              │
                    │                      │                       │
         ┌──────────▼──────────────────────▼──────────┐          │
         │              Repository Service             │          │
         │  (auth, routing to correct repo shard,      │          │
         │   pack/unpack, ref updates)                 │          │
         └──────────┬──────────────────────────────────┘          │
                    │                                    ┌─────────▼──────┐
         ┌──────────▼─────────┐                         │  Message Queue  │
         │   Object Storage   │                         │  (Kafka/SQS)    │
         │                    │                         └─────────┬───────┘
         │ Git Object Store   │                                   │
         │ (blobs, trees,     │                         ┌─────────▼───────┐
         │  commits, tags)    │                         │  CI Orchestrator │
         │ Sharded by repo_id │                         │  (job dispatch,  │
         │ S3 / GCS / HDFS    │                         │  runner fleet)   │
         └──────────┬─────────┘                         └─────────────────┘
                    │
         ┌──────────▼──────────┐
         │   Metadata DB       │
         │   (PostgreSQL)      │
         │   - repos, users    │
         │   - branches, refs  │
         │   - PRs, comments   │
         │   - permissions     │
         └─────────────────────┘

         ┌─────────────────────┐
         │   Search Index      │
         │   (Elasticsearch)   │
         │   - code search     │
         │   - commit messages │
         └─────────────────────┘
```

---

## Deep Dive: Git Object Storage

### Storage Strategy: Content-Addressable Object Store

Each Git object is stored by its SHA hash. This maps perfectly to an object store (S3/GCS):

```
Key:   {repo_id}/{sha[0:2]}/{sha[2:]}    (mirrors Git's loose object layout)
Value: zlib-compressed object bytes
```

For packfiles (large repos):
```
Key:   {repo_id}/packs/{pack_sha}.pack
Key:   {repo_id}/packs/{pack_sha}.idx
```

**Deduplication at blob level:** Since blobs are addressed by content SHA, two repos with the same file share a single blob if using a global object store. GitHub implements this — a `node_modules` directory shared by 10M repos is stored once. This is why GitHub's storage efficiency is much better than 400M × avg_repo_size.

**Sharding:** Repos are sharded by `repo_id` (consistent hash ring). All objects for a repo land on the same shard → pack/unpack operations are local to one node. Hot repos get dedicated shard instances.

### Push Flow

```
1. Client: git push origin main
   → client sends packfile over HTTPS (smart HTTP protocol)
   → negotiation phase: client sends "have" list (commits it has), server sends "want" list

2. Git Gateway:
   → receives packfile stream
   → authenticates: is user allowed to push to this repo?
   → routes to correct shard (consistent hash on repo_id)

3. Repository Service (on shard):
   → unpack packfile → write loose objects to object store
   → verify connectivity: walk object graph, ensure no dangling references
   → update ref atomically: CAS on refs/heads/main → new commit SHA
   → if ref update conflicts (non-fast-forward), reject push

4. Post-receive hooks (async):
   → publish push.event to Kafka: { repo_id, ref, old_sha, new_sha, pusher }
   → Webhook fanout service consumes → delivers to registered webhook URLs
   → CI trigger service consumes → creates CI job

5. Return success to client
```

**Atomic ref update (preventing concurrent push conflicts):**
```sql
UPDATE refs
SET sha = $new_sha
WHERE repo_id = $repo_id AND ref_name = 'refs/heads/main' AND sha = $old_sha
-- rows_affected = 0 → conflict (another push landed first) → reject with "non-fast-forward"
```

### Clone Flow

```
1. Client: git clone https://github.com/org/repo.git
2. Server: git upload-pack negotiates which objects client needs
3. Server assembles packfile:
   - Walk commit graph from requested refs
   - Collect all reachable objects (commits + trees + blobs)
   - Delta-compress against similar objects
   - Stream packfile to client

Performance: large repos (Linux kernel: 5GB packfile)
  → Pre-computed shallow clones: cache packfiles for HEAD + recent N commits
  → Partial clone: `git clone --filter=blob:none` (server sends tree+commits, not blobs)
  → Client fetches blobs lazily on checkout
```

---

## Deep Dive: Diff Serving

### On-Demand Diff

For a PR with 50 changed files:
```
1. client requests diff: GET /repos/org/repo/compare/main...feature
2. Repository Service:
   a. Fetch base commit tree (main HEAD) and head commit tree (feature HEAD)
   b. Walk both trees recursively, compare blob SHAs
   c. For changed blobs: fetch both blob contents, compute unified diff
   d. Return diff JSON with hunks
```

**Git's built-in diff algorithm:** Myers diff (default) or histogram diff (better for refactors). Both are O(N×M) where N and M are file sizes — fine for most files, slow for auto-generated large files.

### Pre-Computed Diff Cache

For popular PRs (many viewers), computing the diff on every request is wasteful.

```
On PR creation:
  → compute diff asynchronously
  → cache result: Redis key = "diff:{base_sha}:{head_sha}" TTL=24h
  → if cache miss at serve time, compute on-demand and populate cache

Cache strategy:
  - SHA-addressed: same pair of SHAs always produces same diff → eternal cache validity
  - Key: "diff:{repo_id}:{base_sha}:{head_sha}:{context_lines}"
  - Store in Redis (< 1MB diffs) or S3 (> 1MB)
  - Evict LRU; most diffs are viewed only a few times
```

**CDN caching:** Diff content is immutable (SHAs don't change) — can be cached at CDN with very long TTL (7 days). The URL `GET /compare/abc123...def456` always returns the same result.

---

## Deep Dive: Webhook Fanout and CI Trigger Pipeline

### Webhook Fanout

On every push, GitHub delivers webhooks to potentially thousands of registered URLs (CI servers, Slack bots, deployment tools).

```
Push event → Kafka topic: "repo.push.events"
  partition key: repo_id (order matters within a repo)

Webhook Fanout Service (Kafka consumer):
  1. Read push event
  2. Fetch all registered webhooks for this repo + org (from DB, cached in Redis)
  3. For each webhook:
     a. Build payload: JSON with repo info, commits, pusher
     b. Sign payload: HMAC-SHA256 with webhook secret → X-Hub-Signature-256 header
     c. Enqueue HTTP delivery job to per-webhook queue

HTTP Delivery Worker:
  1. POST webhook URL with signed payload + 5-second timeout
  2. On failure (5xx, timeout): retry with exponential backoff (1s, 5s, 30s, 2min, 10min)
  3. After 5 retries: mark delivery as failed; alert repository admin
  4. Log: delivery_id, timestamp, status_code, latency → visible in repo Settings > Webhooks
```

**Delivery ordering:** Webhook deliveries are best-effort, not guaranteed ordered. If a receiver needs ordering, they should use the `after` field (new commit SHA) to reconcile with git history.

**Webhook security:** Receiver validates `X-Hub-Signature-256` header:
```python
expected = hmac.new(secret, payload, sha256).hexdigest()
received = request.headers['X-Hub-Signature-256'].split('=')[1]
if not hmac.compare_digest(expected, received):
    return 403  # reject; may be replay or spoofed
```

### CI Trigger Pipeline

```
CI Trigger flow:
  1. Webhook fanout publishes to "ci.trigger.requests" Kafka topic
     Payload: { repo_id, sha, branch, trigger_type: "push|PR", config_ref }

  2. CI Orchestrator:
     a. Fetch .github/workflows/*.yml from the commit tree (blob lookup by path)
     b. Parse workflow YAML: which events match this trigger?
     c. For matching workflows: create job definitions
     d. Assign jobs to runner fleet (weighted round-robin; priority queue for paid users)

  3. Runner Selection:
     GitHub-hosted runners: ephemeral VMs provisioned per job
     Self-hosted runners: poll "ci.jobs.{runner_label}" Kafka topic
     Job timeout: 6 hours (GitHub default); cancel on timeout

  4. Job Execution:
     a. Checkout code: git clone --depth=1 {repo} --branch {sha}
     b. Set up environment (Docker image or VM)
     c. Execute steps
     d. Upload artifacts to S3 (artifact_id → S3 key)
     e. Report status via REST API: PATCH /repos/{owner}/{repo}/statuses/{sha}

  5. Status propagation:
     Commit status updated → webhook to repo → PR merge gate can check status
     Status API: GET /commits/{sha}/status → { state: "success|failure|pending" }
```

**Artifact storage:**
```
Artifacts: large test results, built binaries, coverage reports
  Store in S3 with TTL (default 90 days)
  Key: {org}/{repo}/{run_id}/{artifact_name}.zip
  Download via signed S3 URL (no streaming through API servers)
  Enforce quota per org to prevent abuse
```

---

## Deep Dive: Code Search

Code search is distinct from commit history search:
- **Code search**: find all files containing `func AuthMiddleware` across all repos
- **Commit search**: find commits with "fix SQL injection" in message

### Code Search Architecture

```
Indexing pipeline:
  On push → extract changed files → parse into tokens (language-aware tokenizer)
  Index: Elasticsearch or custom Codesearch engine (Zoekt — used by Sourcegraph/GitHub)

Zoekt advantages over Elasticsearch for code:
  - Trigram index: every 3-character sequence indexed → fast substring matching
  - Language-aware: `func` in Go vs `function` in JS handled correctly
  - N-gram sharding: indices shard by repo not by content (keeps repo data co-located)

Query example:
  Search: "func AuthMiddleware" language:go repo:myorg/*
  → Trigram index: filter to files containing "Aut", "uth", "thM", "hMi", ...
  → Rank by: exact match score, repo stars, recency
  → Return: file path, line number, match context
```

**Scaling challenge:** Indexing 400M repos is expensive. GitHub prioritizes:
1. Active repos (pushed to in last 30 days) — fully indexed
2. Archived repos — index on demand, not pre-indexed
3. Private repos — indexed in isolated tenant shards (security isolation)

---

## Bottlenecks and Mitigations

| Bottleneck | Mitigation |
|---|---|
| Large repo clones (monorepos, game assets) | Shallow clone, partial clone (blob:none), LFS for large binaries |
| Hot repos (linux kernel: thousands of clones/hour) | Pre-computed packfile cache; dedicated CDN delivery for popular repos |
| CI queue depth during mass push events | Priority queues (paid users first); backpressure to runners; autoscale runner fleet |
| Diff computation for large PRs | Pre-compute on PR creation; cache SHA-addressed diffs indefinitely |
| Ref update conflicts | Optimistic CAS on ref table; reject non-fast-forward pushes with clear error |
| Webhook delivery reliability | Kafka for durability; retry with backoff; dead letter queue; per-endpoint circuit breaker |

---

## API Design

```
Git Protocol:
  git clone https://github.com/{owner}/{repo}.git       ← smart HTTP
  git push  origin main                                  ← pack + ref update
  git fetch origin                                       ← negotiated pack download

REST API:
  GET  /repos/{owner}/{repo}/contents/{path}?ref={sha}  → file content
  GET  /repos/{owner}/{repo}/commits?sha={branch}       → commit list (paginated)
  GET  /repos/{owner}/{repo}/compare/{base}...{head}    → diff
  POST /repos/{owner}/{repo}/pulls                      → create PR
  GET  /repos/{owner}/{repo}/pulls/{pr_number}          → PR details + reviews
  POST /repos/{owner}/{repo}/merges                     → merge PR
  GET  /repos/{owner}/{repo}/statuses/{sha}             → CI status for commit
  POST /repos/{owner}/{repo}/hooks                      → register webhook
```

---

## Trade-offs Summary

| Decision | Choice | Reason |
|---|---|---|
| Object storage | Content-addressable (SHA) in S3/GCS | Immutable objects; natural deduplication; CDN-cacheable |
| Sharding | By repo_id | All repo data co-located; pack/unpack is local |
| Diff serving | On-demand + SHA-addressed cache | Immutable diffs; perfect cache hit rate after first view |
| Webhook delivery | Kafka + async HTTP delivery workers | Decouples write path from delivery; durable; retryable |
| CI triggers | Event-driven via Kafka | Decouples push from CI; CI scale independent of Git scale |
| Code search | Trigram index (Zoekt) | Better substring matching than Elasticsearch for code |

---

## Quick Revision

- **Core challenge**: storing billions of Git objects efficiently (content-addressable → SHA-keyed S3), serving clones without reading entire repo (negotiate + packfile), atomic ref updates (CAS)
- **Diff serving**: SHA-addressed cache (SHAs are immutable → cache forever); pre-compute on PR creation
- **CI triggers**: push → Kafka → CI orchestrator → runner pool; async, decoupled from git write path
- **Webhooks**: Kafka for durability → HTTP delivery workers with retry; HMAC-signed payloads
- **Code search**: trigram index for fast substring matching; sharded by repo for security isolation

---

## See Also

- `05-hld-problems/03-hard/distributed-job-scheduler.md` — CI job scheduling patterns
- `05-hld-problems/03-hard/search-system.md` — search index architecture
- `02-building-blocks/message-brokers.md` — Kafka for webhook/CI event fanout
- `01-foundations/change-data-capture.md` — CDC for ref update events
- `04-advanced-topics/event-driven-architecture.md` — event-driven CI pipeline
