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

```python
from __future__ import annotations
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from datetime import datetime, timezone
from enum import Enum
from typing import Callable

# ── Composite Interface ────────────────────────────────────────────────────

class CommentComponent(ABC):
    @abstractmethod
    def get_id(self) -> str: ...
    @abstractmethod
    def get_content(self) -> str: ...
    @abstractmethod
    def get_score(self) -> int: ...
    @abstractmethod
    def get_depth(self) -> int: ...
    @abstractmethod
    def get_children(self) -> list[CommentComponent]: ...
    @abstractmethod
    def display(self, indent: int) -> None: ...

# ── Composite Node ─────────────────────────────────────────────────────────

class CommentNode(CommentComponent):
    def __init__(self, id: str, post_id: str | None, author_id: str, content: str, depth: int):
        self._id        = id
        self._post_id   = post_id
        self._author_id = author_id
        self._content   = content
        self._depth     = depth
        self._score     = 0
        self._created_at = datetime.now(timezone.utc)
        self._children: list[CommentComponent] = []
        self._deleted   = False

    def get_id(self)       -> str:  return self._id
    def get_content(self)  -> str:  return self._content
    def get_score(self)    -> int:  return self._score
    def get_depth(self)    -> int:  return self._depth
    def get_created_at(self) -> datetime: return self._created_at
    def get_children(self) -> list[CommentComponent]: return self._children

    def add_reply(self, child: CommentComponent) -> None:
        self._children.append(child)

    def upvote(self)   -> None: self._score += 1
    def downvote(self) -> None: self._score -= 1

    # Soft delete: keep node so children remain visible
    def soft_delete(self) -> None:
        self._content = "[deleted]"
        self._deleted = True

    def display(self, indent: int) -> None:
        pad = "  " * indent
        print(f"{pad}[{self._depth}] {self._content} | score: {self._score}")
        for child in self._children:
            child.display(indent + 1)

# ── Sort Strategy ──────────────────────────────────────────────────────────

class SortStrategy(ABC):
    @abstractmethod
    def sort(self, comments: list[CommentComponent]) -> list[CommentComponent]: ...

class NewSort(SortStrategy):
    def sort(self, comments: list[CommentComponent]) -> list[CommentComponent]:
        def key(c: CommentComponent):
            return c.get_created_at() if isinstance(c, CommentNode) else datetime.min.replace(tzinfo=timezone.utc)
        return sorted(comments, key=key, reverse=True)

class TopSort(SortStrategy):
    def sort(self, comments: list[CommentComponent]) -> list[CommentComponent]:
        return sorted(comments, key=lambda c: c.get_score(), reverse=True)

class HotSort(SortStrategy):
    # Time-decayed score: score / (age_in_hours + 2)^1.5
    def sort(self, comments: list[CommentComponent]) -> list[CommentComponent]:
        return sorted(comments, key=lambda c: -self._hot_score(c))

    def _hot_score(self, c: CommentComponent) -> float:
        if not isinstance(c, CommentNode):
            return 0.0
        age_hours = (datetime.now(timezone.utc) - c.get_created_at()).total_seconds() / 3600
        return c.get_score() / (age_hours + 2) ** 1.5

# ── Vote Registry ──────────────────────────────────────────────────────────

class VoteDirection(Enum):
    UP   = "UP"
    DOWN = "DOWN"

class VoteRegistry:
    def __init__(self):
        # key: commentId + ":" + userId
        self._votes: dict[str, VoteDirection] = {}

    def record_vote(self, comment_id: str, user_id: str, direction: VoteDirection,
                    comment: CommentNode) -> None:
        key = f"{comment_id}:{user_id}"
        existing = self._votes.get(key)

        if existing is None:
            self._votes[key] = direction
            comment.upvote() if direction == VoteDirection.UP else comment.downvote()
        elif existing == direction:
            # Toggle off
            del self._votes[key]
            comment.downvote() if direction == VoteDirection.UP else comment.upvote()
        else:
            # Flip direction
            self._votes[key] = direction
            if direction == VoteDirection.UP:
                comment.upvote(); comment.upvote()
            else:
                comment.downvote(); comment.downvote()

# ── Comment Service ────────────────────────────────────────────────────────

class CommentService:
    def __init__(self):
        # post_id → root-level comments
        self._threads: dict[str, list[CommentComponent]] = {}
        # comment_id → node (for O(1) lookup when replying)
        self._node_index: dict[str, CommentNode] = {}
        self._vote_registry = VoteRegistry()
        self._id_counter = 0

    def _generate_id(self) -> str:
        self._id_counter += 1
        return f"c{self._id_counter}"

    def post_comment(self, post_id: str, author_id: str, content: str) -> CommentNode:
        id_ = self._generate_id()
        node = CommentNode(id_, post_id, author_id, content, 0)
        self._node_index[id_] = node
        self._threads.setdefault(post_id, []).append(node)
        return node

    def reply_to(self, parent_id: str, author_id: str, content: str) -> CommentNode:
        parent = self._node_index.get(parent_id)
        if parent is None:
            raise ValueError(f"Parent not found: {parent_id}")
        id_ = self._generate_id()
        child = CommentNode(id_, None, author_id, content, parent.get_depth() + 1)
        self._node_index[id_] = child
        parent.add_reply(child)
        return child

    def vote(self, comment_id: str, user_id: str, direction: VoteDirection) -> None:
        node = self._node_index.get(comment_id)
        if node is None:
            raise ValueError(f"Comment not found: {comment_id}")
        self._vote_registry.record_vote(comment_id, user_id, direction, node)

    # Fetch thread with sorting applied at each level
    def get_thread(self, post_id: str, strategy: SortStrategy,
                   page: int, page_size: int) -> list[CommentComponent]:
        roots  = self._threads.get(post_id, [])
        sorted_ = strategy.sort(roots)

        # Pagination on top-level only
        from_ = page * page_size
        to_   = min(from_ + page_size, len(sorted_))
        page_results = sorted_[from_:to_]

        return self._apply_sort_recursively(page_results, strategy)

    def _apply_sort_recursively(self, nodes: list[CommentComponent],
                                strategy: SortStrategy) -> list[CommentComponent]:
        for node in nodes:
            if isinstance(node, CommentNode) and node.get_children():
                sorted_children = strategy.sort(node.get_children())
                node.get_children().clear()
                node.get_children().extend(self._apply_sort_recursively(sorted_children, strategy))
        return nodes

    def display_thread(self, post_id: str, strategy: SortStrategy) -> None:
        thread = self.get_thread(post_id, strategy, 0, 2**31)
        for c in thread:
            c.display(0)

# ── Demo ───────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    service = CommentService()

    c1 = service.post_comment("post1", "alice",   "First top-level comment")
    c2 = service.post_comment("post1", "bob",     "Second top-level comment")
    r1 = service.reply_to(c1.get_id(), "charlie", "Reply to Alice")
    r2 = service.reply_to(r1.get_id(), "alice",   "Reply to Charlie (nested)")

    service.vote(c2.get_id(), "alice", VoteDirection.UP)
    service.vote(c2.get_id(), "dave",  VoteDirection.UP)
    service.vote(c1.get_id(), "eve",   VoteDirection.UP)

    print("=== TOP sort ===")
    service.display_thread("post1", TopSort())

    print("\n=== NEW sort ===")
    service.display_thread("post1", NewSort())
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
```python
import math

def wilson_score(upvotes: int, total: int) -> float:
    """Wilson lower bound — more statistically robust than raw score."""
    if total == 0:
        return 0.0
    z    = 1.96  # 95% confidence
    p_hat = upvotes / total
    return (
        p_hat + z*z / (2*total)
        - z * math.sqrt((p_hat * (1 - p_hat) + z*z / (4*total)) / total)
    ) / (1 + z*z / total)
```
Use as `HotSort` comparator for production-quality ranking.
