> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a Nested Comment System (like Reddit or HackerNews) — tests tree data structures, Composite pattern, and recursive rendering.
>
> **Key concepts:**
> - Core Entities: `User`, `Post`, `Comment`.
> - Tree Structure (Composite Pattern): A `Comment` contains a list of `Comment`s (its children/replies).
> - Database Storage: Storing trees in SQL is hard. 
>   - Adjacency List (storing `parentId`) is simple but requires recursive queries to fetch deep threads.
>   - Materialized Path (storing `path="1/4/7"`) allows fetching an entire thread in one query (`LIKE '1/4/%'`).
> - Sorting (Strategy Pattern): Implement strategies for "Top" (upvotes - downvotes), "New", and "Controversial".
> - Pagination: Fetching the whole tree is too heavy. You must support lazy-loading (e.g., "Load more comments...").
>
> **Key takeaway:** The interviewer will heavily probe how you store and retrieve the nested structure from a database. Be prepared to explain the Materialized Path approach for O(1) thread retrieval.

---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, comment-system, tree, composite, strategy]
---
# Design a Nested Comment System

> **Difficulty**: Medium  
> **Asked at**: Amazon, Meta, Twitter  
> **Key Patterns**: Composite (tree of comments), Strategy (sort), Observer (notifications)

---

## Understanding the Problem

Design a nested comment system like Reddit or YouTube where users can post comments on content, reply to existing comments forming a thread tree, like comments, and retrieve comments sorted by time or popularity.

---

## Clarifying Questions

**You**: "When a user replies to a comment, how deep can nesting go — is there a limit?"  
**Interviewer**: "No hard limit. Assume typical usage won't exceed 10 levels deep."

**You**: "What sort orders should we support — time and likes? Any others?"  
**Interviewer**: "Time ascending, time descending, and likes descending. That covers it."

**You**: "When a comment is deleted, what happens to its replies?"  
**Interviewer**: "Replies should remain. Show the parent as '[deleted]' rather than removing the whole subtree."

**You**: "Should likes be unique per user, and can a user unlike?"  
**Interviewer**: "Yes, unique per user. Liking again should toggle it off."

**You**: "Are we designing the storage layer or the in-memory object model?"  
**Interviewer**: "Focus on class design and core methods. Mention how you'd persist it but don't implement SQL."

**You**: "Should we support pagination for top-level comments?"  
**Interviewer**: "Assume full thread for now. Mention how you'd add it."

---

## Final Requirements

**In scope:**
1. Add a top-level comment to a post
2. Reply to an existing comment (nested, unlimited depth)
3. Like / unlike a comment (unique per user, toggleable)
4. Delete a comment (soft delete — content replaced, replies preserved)
5. Retrieve all comments for a post sorted by time or likes
6. Preserve nested structure in retrieval

**Out of scope:**
- Persistent storage implementation
- Authentication / authorization
- Spam detection
- Real-time push notifications

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|---------------|
| Comment | Holds text, author, timestamps, like set, parent reference, child list |
| CommentTree | Owns root-level comments for a post; builds and traverses the tree |
| CommentService | Orchestrates CRUD; enforces business rules |
| SortStrategy | Interface — ByTime and ByLikes implementations |
| User | Identity for authorship and like uniqueness |

A `CommentService` receives requests and delegates tree mutations to `CommentTree`. Each `Comment` is a node that holds a list of child `Comment` objects (Composite pattern). Sort strategies operate on lists of comments returned from the tree.

---

## Class Design

### Comment

| Requirement | What Comment must track |
|-------------|------------------------|
| Identity | comment_id, post_id |
| Content | text, is_deleted flag |
| Authorship | user_id, created_at |
| Hierarchy | parent_id, children: List[Comment] |
| Engagement | liked_by: Set[str] |

```
class Comment:
- comment_id: str
- post_id: str
- user_id: str
- text: str
- parent_id: Optional[str]
- children: List[Comment]
- liked_by: Set[str]
- created_at: datetime
- is_deleted: bool
+ toggle_like(user_id: str) -> None
+ soft_delete() -> None
+ like_count() -> int
+ add_child(comment: Comment) -> None
```

### CommentTree

| Requirement | What CommentTree must track |
|-------------|----------------------------|
| Fast lookup | comment_map: Dict[str, Comment] |
| Root access | roots: List[Comment] |

```
class CommentTree:
- comment_map: Dict[str, Comment]
- roots: List[Comment]
+ insert(comment: Comment) -> None
+ find(comment_id: str) -> Comment
+ get_flat() -> List[Comment]
```

### CommentService

| Requirement | What CommentService must track |
|-------------|-------------------------------|
| Per-post trees | trees: Dict[str, CommentTree] |

```
class CommentService:
- trees: Dict[str, CommentTree]
+ add_comment(post_id, user_id, text, parent_id=None) -> Comment
+ get_comments(post_id, sort_strategy) -> List[Comment]
+ like_comment(post_id, comment_id, user_id) -> None
+ delete_comment(post_id, comment_id) -> None
```

### SortStrategy

```
class SortStrategy (abstract):
+ sort(comments: List[Comment]) -> List[Comment]

class ByTimeAscending(SortStrategy):
+ sort(comments) -> List[Comment]

class ByTimeDescending(SortStrategy):
+ sort(comments) -> List[Comment]

class ByLikes(SortStrategy):
+ sort(comments) -> List[Comment]
```

---

## Implementation

### Core Method: add_comment

**Core logic:**
1. Get or create `CommentTree` for the post
2. Validate text is non-empty
3. If `parent_id` is provided, verify it exists in `comment_map`
4. Build a new `Comment` with generated UUID
5. If `parent_id` is None, append to tree roots; otherwise call `parent.add_child(comment)`
6. Register in `comment_map`

**Edge cases:**
- `parent_id` not found → raise `KeyError`
- Parent is soft-deleted → still allow replies (replies are independent content)
- Empty or whitespace-only text → raise `ValueError`

```python
import uuid
from datetime import datetime
from typing import Optional, List, Dict, Set
from abc import ABC, abstractmethod


class Comment:
    def __init__(self, comment_id: str, post_id: str, user_id: str,
                 text: str, parent_id: Optional[str] = None):
        self.comment_id = comment_id
        self.post_id = post_id
        self.user_id = user_id
        self.text = text
        self.parent_id = parent_id
        self.children: List['Comment'] = []
        self.liked_by: Set[str] = set()
        self.created_at = datetime.utcnow()
        self.is_deleted = False

    def toggle_like(self, user_id: str) -> None:
        if user_id in self.liked_by:
            self.liked_by.discard(user_id)
        else:
            self.liked_by.add(user_id)

    def soft_delete(self) -> None:
        self.is_deleted = True
        self.text = "[deleted]"

    def like_count(self) -> int:
        return len(self.liked_by)

    def add_child(self, comment: 'Comment') -> None:
        self.children.append(comment)


class CommentTree:
    def __init__(self, post_id: str):
        self.post_id = post_id
        self.comment_map: Dict[str, Comment] = {}
        self.roots: List[Comment] = []

    def insert(self, comment: Comment) -> None:
        self.comment_map[comment.comment_id] = comment
        if comment.parent_id is None:
            self.roots.append(comment)
        else:
            parent = self.find(comment.parent_id)
            parent.add_child(comment)

    def find(self, comment_id: str) -> Comment:
        if comment_id not in self.comment_map:
            raise KeyError(f"Comment {comment_id} not found")
        return self.comment_map[comment_id]

    def get_flat(self) -> List[Comment]:
        """BFS traversal — level-order, preserves nesting context."""
        result = []
        queue = list(self.roots)
        while queue:
            node = queue.pop(0)
            result.append(node)
            queue.extend(node.children)
        return result


class SortStrategy(ABC):
    @abstractmethod
    def sort(self, comments: List[Comment]) -> List[Comment]:
        pass


class ByTimeAscending(SortStrategy):
    def sort(self, comments: List[Comment]) -> List[Comment]:
        return sorted(comments, key=lambda c: c.created_at)


class ByTimeDescending(SortStrategy):
    def sort(self, comments: List[Comment]) -> List[Comment]:
        return sorted(comments, key=lambda c: c.created_at, reverse=True)


class ByLikes(SortStrategy):
    def sort(self, comments: List[Comment]) -> List[Comment]:
        return sorted(comments, key=lambda c: c.like_count(), reverse=True)


class CommentService:
    def __init__(self):
        self.trees: Dict[str, CommentTree] = {}

    def _get_tree(self, post_id: str) -> CommentTree:
        if post_id not in self.trees:
            self.trees[post_id] = CommentTree(post_id)
        return self.trees[post_id]

    def add_comment(self, post_id: str, user_id: str, text: str,
                    parent_id: Optional[str] = None) -> Comment:
        if not text or not text.strip():
            raise ValueError("Comment text cannot be empty")
        tree = self._get_tree(post_id)
        if parent_id and parent_id not in tree.comment_map:
            raise KeyError(f"Parent comment {parent_id} not found")
        comment = Comment(
            comment_id=str(uuid.uuid4()),
            post_id=post_id,
            user_id=user_id,
            text=text.strip(),
            parent_id=parent_id
        )
        tree.insert(comment)
        return comment

    def get_comments(self, post_id: str,
                     sort_strategy: Optional[SortStrategy] = None) -> List[Comment]:
        tree = self._get_tree(post_id)
        comments = tree.get_flat()
        if sort_strategy:
            comments = sort_strategy.sort(comments)
        return comments

    def like_comment(self, post_id: str, comment_id: str, user_id: str) -> None:
        tree = self._get_tree(post_id)
        comment = tree.find(comment_id)
        comment.toggle_like(user_id)

    def delete_comment(self, post_id: str, comment_id: str) -> None:
        tree = self._get_tree(post_id)
        comment = tree.find(comment_id)
        comment.soft_delete()
```

---

## Verification

Trace: User A comments on post_1, User B replies, User C likes B's reply, then unlikes it.

1. `add_comment("post_1", "user_a", "Great post!")` → Comment(id="c1") in roots
2. `add_comment("post_1", "user_b", "I agree!", parent_id="c1")` → Comment(id="c2") in c1.children
3. `like_comment("post_1", "c2", "user_c")` → c2.liked_by = {"user_c"}, like_count=1
4. `like_comment("post_1", "c2", "user_c")` → toggle removes it, c2.liked_by = {}, like_count=0
5. `get_comments("post_1", ByLikes())` → BFS yields [c1, c2], sorted by likes (both 0, stable order preserved)
6. `delete_comment("post_1", "c1")` → c1.text="[deleted]", c1.is_deleted=True; c2 unaffected

---

## Deep Dive & Extensibility

### 1. "How would you store nested comments in a relational database?"

Three main approaches:

**Adjacency List**: Each row stores `parent_id`. Simple writes; fetching an entire thread needs a recursive CTE.

```sql
WITH RECURSIVE thread AS (
  SELECT * FROM comments WHERE parent_id IS NULL AND post_id = ?
  UNION ALL
  SELECT c.* FROM comments c JOIN thread t ON c.parent_id = t.comment_id
)
SELECT * FROM thread ORDER BY created_at;
```

**Materialized Path**: Store full path as string `"c1/c2/c5"`. Subtree fetch is a single `LIKE 'c1/%'` query. Re-parenting requires rewriting all descendant paths.

**Nested Sets**: Each node stores `lft` and `rgt` integers. Subtree is a single range query. Inserts require updating half the table — poor write performance.

Recommendation: Adjacency list for most systems (simple, works with recursive CTE). Materialized path if fast subtree reads and rare re-parenting are the priority.

### 2. "How do you sort nested comments — should it affect the whole tree or just siblings?"

Sorting applies within siblings at each level, not across levels. Apply the strategy recursively on each node's `children` list before serialization.

```python
def sort_tree(node: Comment, strategy: SortStrategy) -> None:
    node.children = strategy.sort(node.children)
    for child in node.children:
        sort_tree(child, strategy)

# Apply to roots first, then recurse
def get_sorted_tree(post_id: str, strategy: SortStrategy) -> List[Comment]:
    tree = self._get_tree(post_id)
    sorted_roots = strategy.sort(tree.roots)
    for root in sorted_roots:
        sort_tree(root, strategy)
    return sorted_roots
```

This is O(n log n) total across all levels.

### 3. "Soft delete vs hard delete — trade-offs?"

**Soft delete** (chosen): Set `is_deleted=True`, replace text with `[deleted]`. Children survive intact. Enables audit trail, undo, moderation review. Display layer filters, not the data layer.

**Cascade hard delete**: Remove comment and all descendants. Loses context — a viral reply thread under a deleted comment disappears entirely. Almost never correct for public platforms.

**Tombstone row**: Delete content, keep the row as a placeholder. Children still have a valid `parent_id`. Cleaner DB than orphaned rows but same logical result as soft delete.

### 4. "How do you paginate deeply nested threads?"

**Top-level pagination**: Page over root comments only. Fetch children eagerly per root, or lazily on expand. Works when threads are shallow.

```python
def get_top_level_comments(self, post_id: str, page: int, size: int,
                            strategy: SortStrategy) -> List[Comment]:
    tree = self._get_tree(post_id)
    sorted_roots = strategy.sort(tree.roots)
    start = page * size
    return sorted_roots[start:start + size]
```

**Cursor-based**: Encode cursor as `(parent_id, offset)`. The client calls `GET /comments?post_id=X&parent_id=c1&cursor=Y` to load more children. This is Reddit's "load more comments" pattern.

### 5. "How would you add comment moderation / flagging?"

Add `flags: Dict[str, str]` to `Comment` (user_id → reason). Auto-hide when flag count exceeds threshold. A `ModerationService` processes the queue.

```python
FLAG_THRESHOLD = 5

def flag_comment(self, post_id: str, comment_id: str,
                 reporter_id: str, reason: str) -> None:
    comment = self._get_tree(post_id).find(comment_id)
    if reporter_id == comment.user_id:
        raise ValueError("Cannot flag your own comment")
    comment.flags[reporter_id] = reason
    if len(comment.flags) >= FLAG_THRESHOLD:
        comment.auto_hidden = True
```

Moderators see a priority queue of flagged comments sorted by flag count descending.

---

## Interviewer Questions by Level

**Junior**: How does the children list in Comment relate to the parent_id field? Why do you need both?

**Mid-level**: Your `get_comments` returns a flat list. How does the client reconstruct the tree for rendering? What would you add to each Comment's serialized form?

**Senior**: The recursive CTE works at 1,000 comments. At 10M comments across all posts, what breaks and how do you fix it? Walk through a sharding and caching strategy.

---

## Common Interview Questions

- **Q: Why keep parent_id on Comment when you already have the children list?**  
  A: The children list is an in-memory construct. parent_id is what persists to the DB. Without it you cannot reconstruct the tree on load.

- **Q: Can a user like their own comment?**  
  A: Business decision — add `if user_id == comment.user_id: raise ValueError` in toggle_like to block it.

- **Q: How do you get the full thread in one DB query?**  
  A: Recursive CTE (WITH RECURSIVE) on the adjacency list. Supported in PostgreSQL, MySQL 8+, SQLite 3.35+.

- **Q: Adjacency list vs materialized path for comments?**  
  A: Adjacency list: simple writes, recursive reads. Materialized path: fast subtree reads via LIKE prefix, expensive re-parenting. For comments (rarely re-parented), adjacency list is simpler.

- **Q: If you soft-delete a parent, should you hide its children too?**  
  A: No — children are independent user content. Show parent as "[deleted]" but render children normally (Reddit's approach).

- **Q: How would you pin the top comment regardless of sort?**  
  A: Maintain a sorted set in Redis keyed by post_id, scored by like count. Pull the top comment_id, look it up, render it pinned above the sorted list.

- **Q: Your toggle_like is not thread-safe in a distributed system — what do you do?**  
  A: Use atomic DB operations: `INSERT INTO likes(comment_id, user_id) ON CONFLICT DO NOTHING` for like, `DELETE FROM likes WHERE comment_id=? AND user_id=?` for unlike. Or Redis SADD/SREM which are atomic.
