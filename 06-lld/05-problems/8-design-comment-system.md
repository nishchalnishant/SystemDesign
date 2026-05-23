---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, system-design, problems]
---
# Design Comment System (Reddit-Style Nested Comments)

> **Difficulty**: Medium
> **Topics**: Composite Pattern, Strategy Pattern, Recursive Tree Structures
> **Extension**: Pagination, sorting strategies, vote scoring

---

## Opening Analogy

Open any Reddit thread. You see a top-level comment. Under it are replies. Under those are replies to replies. You can collapse an entire sub-thread with one click — that means the system treats a comment and a subtree of comments uniformly. That is exactly the Composite pattern: a `Comment` and a `CommentThread` (a comment with children) share the same interface. You traverse the tree the same way regardless of depth.

The second insight: Reddit's "hot", "new", and "top" sorts produce radically different orderings of the same data. Instead of baking sort logic into `CommentNode`, we inject a `SortStrategy` at render time.

---

## Phase 1: Requirements

### Functional
- Post a root comment on any content item (post/article).
- Reply to any existing comment at any depth (infinite nesting).
- Upvote or downvote any comment.
- Retrieve the full comment tree for a post, sorted by: `NEW` (newest first), `TOP` (highest score), `HOT` (Wilson score or time-decayed score).
- Delete a comment — soft delete (replace content with "[deleted]", keep structure so child replies remain visible).
- Paginate top-level comments; lazy-load child replies.

### Non-Functional
- Read path (fetch thread) must be fast — O(N) tree traversal where N = comment count.
- Write path (post comment) is less frequent; consistency over speed.
- Score updates (votes) are eventually consistent — update in background.

---

## Phase 2: Use Cases

### Actors
- **Authenticated User** — posts, replies, votes.
- **Guest** — reads only.
- **Moderator** — removes comments.

### UC1: Post Root Comment
1. User submits content for `postId`.
2. System creates a `CommentNode` with `parentId = null`, `depth = 0`.
3. System assigns unique ID and timestamp.
4. System appends to root-level list for the post.

### UC2: Reply to Comment
1. User submits content + `parentCommentId`.
2. System fetches parent node.
3. System creates new `CommentNode` with `parentId` set, `depth = parent.depth + 1`.
4. System adds new node as child of parent.

### UC3: Upvote/Downvote
1. User votes on `commentId`.
2. System checks whether user already voted on this comment.
3. If not voted: record vote, increment/decrement score.
4. If already voted same direction: remove vote (toggle off).
5. If voted opposite direction: flip vote, adjust score by 2.

### UC4: Fetch Comment Thread
1. Client requests `getThread(postId, sortStrategy, page)`.
2. System fetches top-level comments for the post (paginated).
3. System sorts top-level by chosen strategy.
4. For each top-level comment, recursively attach children (sorted by same strategy).
5. Return tree structure.

---

## Phase 3: Class Diagram

```
          <<interface>>
       ┌─────────────────┐
       │   CommentComponent│
       │─────────────────│
       │ + getId()       │
       │ + getContent()  │
       │ + getScore()    │
       │ + display(depth)│
       └────────┬────────┘
                │ implemented by
     ┌──────────┴────────────┐
     ▼                       ▼
┌──────────────┐    ┌─────────────────────────┐
│ DeletedComment│    │       CommentNode        │  <<Composite>>
│ (Leaf/Null   │    │─────────────────────────│
│  Object)     │    │ - id: String             │
└──────────────┘    │ - postId: String         │
                    │ - authorId: String       │
                    │ - content: String        │
                    │ - score: int             │
                    │ - depth: int             │
                    │ - createdAt: Instant     │
                    │ - children: List<Comment │
                    │             Component>   │
                    │─────────────────────────│
                    │ + addReply(child)        │
                    │ + upvote() / downvote()  │
                    │ + softDelete()           │
                    │ + display(indent)        │
                    └─────────────────────────┘

┌─────────────────────────────────────┐
│          SortStrategy               │  <<interface>>
│─────────────────────────────────────│
│ + sort(List<CommentComponent>): List│
└──────────┬──────────────────────────┘
           │
  ┌────────┼──────────────┐
  ▼        ▼              ▼
NewSort  TopSort       HotSort
(by time)(by score)  (time-decayed score)

┌───────────────────────────────────────┐
│           CommentService              │
│───────────────────────────────────────│
│ - threads: Map<postId, List<Comment>> │
│ - voteRegistry: VoteRegistry          │
│───────────────────────────────────────│
│ + postComment(postId, authorId, text) │
│ + replyTo(parentId, authorId, text)   │
│ + vote(commentId, userId, direction)  │
│ + getThread(postId, strategy, page)   │
└───────────────────────────────────────┘

┌─────────────────────────────────────┐
│           VoteRegistry              │
│─────────────────────────────────────│
│ - votes: Map<commentId+userId, dir> │
│ + recordVote(commentId, userId, dir)│
│ + getVote(commentId, userId): dir   │
└─────────────────────────────────────┘
```

---

## Phase 4: Design Patterns Applied

### 1. Composite Pattern — Uniform tree traversal
**Why:** A leaf comment (no replies) and a parent comment (with N replies) must respond to the same interface: `display()`, `getScore()`, `getId()`. The caller — rendering code, sort logic — should not need `instanceof` checks. The Composite pattern ensures `CommentNode` and `DeletedComment` are interchangeable where `CommentComponent` is expected.

**How it works:** `CommentNode.display(depth)` prints its own content indented by `depth`, then calls `child.display(depth + 1)` for each child. The render loop is a single recursive call on the root — it traverses the entire tree.

### 2. Strategy Pattern — Sorting at render time
**Why:** The sort order is a user preference, not a property of the data. `CommentNode` should not know about "hot" vs "new". Instead, `CommentService.getThread()` accepts a `SortStrategy` and applies it at each level of the tree before returning children. Swapping from `TopSort` to `HotSort` is a one-line change to the caller.

### 3. Null Object Pattern — Soft delete
**Why:** When a comment is deleted, its children must remain visible. Instead of setting content to null (causing NullPointerExceptions in render code), we replace the content with `"[deleted]"` and keep the node. Alternatively, a `DeletedComment` null object renders as `"[deleted]"` and delegates `addReply` to its replacement — callers need no null checks.

---

## Phase 5: Key Java Implementation

```java
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

// ── Composite Interface ────────────────────────────────────────────────────

interface CommentComponent {
    String getId();
    String getContent();
    int getScore();
    int getDepth();
    List<CommentComponent> getChildren();
    void display(int indent);
}

// ── Composite Node ─────────────────────────────────────────────────────────

class CommentNode implements CommentComponent {
    private final String id;
    private final String postId;
    private final String authorId;
    private String content;
    private int score;
    private final int depth;
    private final Instant createdAt;
    private final List<CommentComponent> children = new ArrayList<>();

    CommentNode(String id, String postId, String authorId, String content, int depth) {
        this.id        = id;
        this.postId    = postId;
        this.authorId  = authorId;
        this.content   = content;
        this.depth     = depth;
        this.score     = 0;
        this.createdAt = Instant.now();
    }

    public String getId()      { return id; }
    public String getContent() { return content; }
    public int getScore()      { return score; }
    public int getDepth()      { return depth; }
    public Instant getCreatedAt() { return createdAt; }
    public List<CommentComponent> getChildren() { return children; }

    public void addReply(CommentComponent child) { children.add(child); }

    public void upvote()   { score++; }
    public void downvote() { score--; }

    // Soft delete: keep node so children remain visible
    public void softDelete() { this.content = "[deleted]"; this.authorId_deleted = true; }
    private boolean authorId_deleted = false;

    public void display(int indent) {
        String pad = "  ".repeat(indent);
        System.out.printf("%s[%d] %s | score: %d%n", pad, depth, content, score);
        for (CommentComponent child : children) {
            child.display(indent + 1);
        }
    }
}

// ── Sort Strategy ──────────────────────────────────────────────────────────

interface SortStrategy {
    List<CommentComponent> sort(List<CommentComponent> comments);
}

class NewSort implements SortStrategy {
    public List<CommentComponent> sort(List<CommentComponent> comments) {
        return comments.stream()
                .sorted(Comparator.comparing(c -> {
                    if (c instanceof CommentNode cn) return cn.getCreatedAt();
                    return Instant.MIN;
                }, Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }
}

class TopSort implements SortStrategy {
    public List<CommentComponent> sort(List<CommentComponent> comments) {
        return comments.stream()
                .sorted(Comparator.comparingInt(CommentComponent::getScore).reversed())
                .collect(Collectors.toList());
    }
}

class HotSort implements SortStrategy {
    // Time-decayed score: score / (age_in_hours + 2)^1.5
    public List<CommentComponent> sort(List<CommentComponent> comments) {
        return comments.stream()
                .sorted(Comparator.comparingDouble(c -> -hotScore(c)))
                .collect(Collectors.toList());
    }

    private double hotScore(CommentComponent c) {
        if (!(c instanceof CommentNode cn)) return 0;
        long ageHours = (Instant.now().getEpochSecond() - cn.getCreatedAt().getEpochSecond()) / 3600;
        return cn.getScore() / Math.pow(ageHours + 2, 1.5);
    }
}

// ── Vote Registry ──────────────────────────────────────────────────────────

enum VoteDirection { UP, DOWN }

class VoteRegistry {
    // key: commentId + ":" + userId
    private final Map<String, VoteDirection> votes = new HashMap<>();

    public void recordVote(String commentId, String userId, VoteDirection dir,
                           CommentNode comment) {
        String key = commentId + ":" + userId;
        VoteDirection existing = votes.get(key);

        if (existing == null) {
            votes.put(key, dir);
            if (dir == VoteDirection.UP) comment.upvote(); else comment.downvote();
        } else if (existing == dir) {
            // Toggle off
            votes.remove(key);
            if (dir == VoteDirection.UP) comment.downvote(); else comment.upvote();
        } else {
            // Flip direction
            votes.put(key, dir);
            if (dir == VoteDirection.UP) { comment.upvote(); comment.upvote(); }
            else                         { comment.downvote(); comment.downvote(); }
        }
    }
}

// ── Comment Service ────────────────────────────────────────────────────────

public class CommentService {
    // postId → root-level comments
    private final Map<String, List<CommentComponent>> threads = new HashMap<>();
    // commentId → node (for O(1) lookup when replying)
    private final Map<String, CommentNode> nodeIndex = new HashMap<>();
    private final VoteRegistry voteRegistry = new VoteRegistry();
    private long idCounter = 0;

    private String generateId() { return "c" + (++idCounter); }

    public CommentNode postComment(String postId, String authorId, String content) {
        String id = generateId();
        CommentNode node = new CommentNode(id, postId, authorId, content, 0);
        nodeIndex.put(id, node);
        threads.computeIfAbsent(postId, k -> new ArrayList<>()).add(node);
        return node;
    }

    public CommentNode replyTo(String parentId, String authorId, String content) {
        CommentNode parent = nodeIndex.get(parentId);
        if (parent == null) throw new IllegalArgumentException("Parent not found: " + parentId);

        String id = generateId();
        CommentNode child = new CommentNode(id, null, authorId, content, parent.getDepth() + 1);
        nodeIndex.put(id, child);
        parent.addReply(child);
        return child;
    }

    public void vote(String commentId, String userId, VoteDirection direction) {
        CommentNode node = nodeIndex.get(commentId);
        if (node == null) throw new IllegalArgumentException("Comment not found: " + commentId);
        voteRegistry.recordVote(commentId, userId, direction, node);
    }

    // Fetch thread with sorting applied at each level
    public List<CommentComponent> getThread(String postId, SortStrategy strategy, int page, int pageSize) {
        List<CommentComponent> roots = threads.getOrDefault(postId, Collections.emptyList());
        List<CommentComponent> sorted = strategy.sort(roots);

        // Pagination on top-level only
        int from = page * pageSize;
        int to   = Math.min(from + pageSize, sorted.size());
        List<CommentComponent> page_results = sorted.subList(from, to);

        // Apply sort recursively to children
        return applySortRecursively(page_results, strategy);
    }

    private List<CommentComponent> applySortRecursively(List<CommentComponent> nodes, SortStrategy strategy) {
        for (CommentComponent node : nodes) {
            if (node instanceof CommentNode cn && !cn.getChildren().isEmpty()) {
                List<CommentComponent> sortedChildren = strategy.sort(cn.getChildren());
                cn.getChildren().clear();
                cn.getChildren().addAll(applySortRecursively(sortedChildren, strategy));
            }
        }
        return nodes;
    }

    public void displayThread(String postId, SortStrategy strategy) {
        List<CommentComponent> thread = getThread(postId, strategy, 0, Integer.MAX_VALUE);
        for (CommentComponent c : thread) c.display(0);
    }
}

// ── Demo ───────────────────────────────────────────────────────────────────

class CommentDemo {
    public static void main(String[] args) throws InterruptedException {
        CommentService service = new CommentService();

        CommentNode c1 = service.postComment("post1", "alice", "First top-level comment");
        CommentNode c2 = service.postComment("post1", "bob",   "Second top-level comment");
        CommentNode r1 = service.replyTo(c1.getId(), "charlie", "Reply to Alice");
        CommentNode r2 = service.replyTo(r1.getId(), "alice",   "Reply to Charlie (nested)");

        service.vote(c2.getId(), "alice",   VoteDirection.UP);
        service.vote(c2.getId(), "dave",    VoteDirection.UP);
        service.vote(c1.getId(), "eve",     VoteDirection.UP);

        System.out.println("=== TOP sort ===");
        service.displayThread("post1", new TopSort());

        System.out.println("\n=== NEW sort ===");
        service.displayThread("post1", new NewSort());
    }
}
```

---

## Phase 6: Trade-offs and Extensions

### Trade-offs

| Decision | Choice | Alternative | Reason |
|---|---|---|---|
| Tree storage | In-memory nested objects | Materialized path in DB | LLD context; DB variant uses `path` string + `ORDER BY path` for DFS ordering |
| Sort application | Applied at render time (service layer) | Stored sorted order | Render-time sort allows dynamic strategy; stored order only works for one strategy |
| Vote storage | `VoteRegistry` Map | `Set<VoteRecord>` per comment | Map keyed by `commentId:userId` gives O(1) lookup; set would need filtering |
| Soft delete | Replace content string | Separate `isDeleted` flag | Content replacement is simpler; flag needs null checks everywhere in render |

### Extensions

**Persistence (DB schema):**
```sql
CREATE TABLE comments (
  id         BIGINT PRIMARY KEY,
  post_id    BIGINT NOT NULL,
  parent_id  BIGINT REFERENCES comments(id),
  author_id  BIGINT NOT NULL,
  content    TEXT,
  score      INT DEFAULT 0,
  depth      INT DEFAULT 0,
  path       VARCHAR(500),   -- materialized path: "0001/0034/0091"
  created_at TIMESTAMP,
  deleted_at TIMESTAMP       -- soft delete
);
CREATE INDEX idx_thread ON comments (post_id, path);
```
`ORDER BY path` gives depth-first traversal (full thread) in one query. Sorting by score or time requires application-layer reordering per level, or separate queries.

**Pagination deep replies:**
Return `hasMore: true` flag per comment node. Client fetches `GET /comments/{id}/replies?page=2` lazily when user clicks "load more replies".

**Score computation (Wilson score for ranking):**
```java
// Wilson lower bound — more statistically robust than raw score
double wilsonScore(int upvotes, int total) {
    if (total == 0) return 0;
    double z = 1.96; // 95% confidence
    double pHat = (double) upvotes / total;
    return (pHat + z*z/(2*total) - z * Math.sqrt((pHat*(1-pHat) + z*z/(4*total))/total))
           / (1 + z*z/total);
}
```
Use as `HotSort` comparator for production-quality ranking.
