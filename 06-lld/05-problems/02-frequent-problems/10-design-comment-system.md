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

```java
import java.util.*;
import java.time.Instant;

class Comment {
    private final String commentId;
    private final String postId;
    private final String userId;
    private String text;
    private final String parentId; // nullable
    private final List<Comment> children = new ArrayList<>();
    private final Set<String> likedBy = new HashSet<>();
    private final Instant createdAt;
    private boolean isDeleted;

    public Comment(String commentId, String postId, String userId,
                    String text, String parentId) {
        this.commentId = commentId;
        this.postId = postId;
        this.userId = userId;
        this.text = text;
        this.parentId = parentId;
        this.createdAt = Instant.now();
        this.isDeleted = false;
    }

    public void toggleLike(String userId) {
        if (likedBy.contains(userId)) {
            likedBy.remove(userId);
        } else {
            likedBy.add(userId);
        }
    }

    public void softDelete() {
        this.isDeleted = true;
        this.text = "[deleted]";
    }

    public int likeCount() {
        return likedBy.size();
    }

    public void addChild(Comment comment) {
        children.add(comment);
    }

    public String getCommentId() { return commentId; }
    public String getPostId() { return postId; }
    public String getUserId() { return userId; }
    public String getText() { return text; }
    public String getParentId() { return parentId; }
    public List<Comment> getChildren() { return children; }
    public void setChildren(List<Comment> children) {
        this.children.clear();
        this.children.addAll(children);
    }
    public Set<String> getLikedBy() { return likedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isDeleted() { return isDeleted; }
}


class CommentTree {
    private final String postId;
    private final Map<String, Comment> commentMap = new HashMap<>();
    private final List<Comment> roots = new ArrayList<>();

    public CommentTree(String postId) {
        this.postId = postId;
    }

    public void insert(Comment comment) {
        commentMap.put(comment.getCommentId(), comment);
        if (comment.getParentId() == null) {
            roots.add(comment);
        } else {
            Comment parent = find(comment.getParentId());
            parent.addChild(comment);
        }
    }

    public Comment find(String commentId) {
        if (!commentMap.containsKey(commentId)) {
            throw new NoSuchElementException("Comment " + commentId + " not found");
        }
        return commentMap.get(commentId);
    }

    // BFS traversal — level-order, preserves nesting context.
    public List<Comment> getFlat() {
        List<Comment> result = new ArrayList<>();
        Deque<Comment> queue = new ArrayDeque<>(roots);
        while (!queue.isEmpty()) {
            Comment node = queue.poll();
            result.add(node);
            queue.addAll(node.getChildren());
        }
        return result;
    }

    public Map<String, Comment> getCommentMap() { return commentMap; }
    public List<Comment> getRoots() { return roots; }
    public String getPostId() { return postId; }
}


interface SortStrategy {
    List<Comment> sort(List<Comment> comments);
}


class ByTimeAscending implements SortStrategy {
    @Override
    public List<Comment> sort(List<Comment> comments) {
        List<Comment> sorted = new ArrayList<>(comments);
        sorted.sort(Comparator.comparing(Comment::getCreatedAt));
        return sorted;
    }
}


class ByTimeDescending implements SortStrategy {
    @Override
    public List<Comment> sort(List<Comment> comments) {
        List<Comment> sorted = new ArrayList<>(comments);
        sorted.sort(Comparator.comparing(Comment::getCreatedAt).reversed());
        return sorted;
    }
}


class ByLikes implements SortStrategy {
    @Override
    public List<Comment> sort(List<Comment> comments) {
        List<Comment> sorted = new ArrayList<>(comments);
        sorted.sort(Comparator.comparingInt(Comment::likeCount).reversed());
        return sorted;
    }
}


class CommentService {
    private final Map<String, CommentTree> trees = new HashMap<>();

    private CommentTree getTree(String postId) {
        return trees.computeIfAbsent(postId, CommentTree::new);
    }

    public Comment addComment(String postId, String userId, String text,
                               String parentId) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Comment text cannot be empty");
        }
        CommentTree tree = getTree(postId);
        if (parentId != null && !tree.getCommentMap().containsKey(parentId)) {
            throw new NoSuchElementException("Parent comment " + parentId + " not found");
        }
        Comment comment = new Comment(
            UUID.randomUUID().toString(),
            postId,
            userId,
            text.trim(),
            parentId
        );
        tree.insert(comment);
        return comment;
    }

    public List<Comment> getComments(String postId, SortStrategy sortStrategy) {
        CommentTree tree = getTree(postId);
        List<Comment> comments = tree.getFlat();
        if (sortStrategy != null) {
            comments = sortStrategy.sort(comments);
        }
        return comments;
    }

    public void likeComment(String postId, String commentId, String userId) {
        CommentTree tree = getTree(postId);
        Comment comment = tree.find(commentId);
        comment.toggleLike(userId);
    }

    public void deleteComment(String postId, String commentId) {
        CommentTree tree = getTree(postId);
        Comment comment = tree.find(commentId);
        comment.softDelete();
    }
}
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

```java
void sortTree(Comment node, SortStrategy strategy) {
    node.setChildren(strategy.sort(node.getChildren()));
    for (Comment child : node.getChildren()) {
        sortTree(child, strategy);
    }
}

// Apply to roots first, then recurse
List<Comment> getSortedTree(String postId, SortStrategy strategy) {
    CommentTree tree = getTree(postId);
    List<Comment> sortedRoots = strategy.sort(tree.getRoots());
    for (Comment root : sortedRoots) {
        sortTree(root, strategy);
    }
    return sortedRoots;
}
```

This is O(n log n) total across all levels.

### 3. "Soft delete vs hard delete — trade-offs?"

**Soft delete** (chosen): Set `is_deleted=True`, replace text with `[deleted]`. Children survive intact. Enables audit trail, undo, moderation review. Display layer filters, not the data layer.

**Cascade hard delete**: Remove comment and all descendants. Loses context — a viral reply thread under a deleted comment disappears entirely. Almost never correct for public platforms.

**Tombstone row**: Delete content, keep the row as a placeholder. Children still have a valid `parent_id`. Cleaner DB than orphaned rows but same logical result as soft delete.

### 4. "How do you paginate deeply nested threads?"

**Top-level pagination**: Page over root comments only. Fetch children eagerly per root, or lazily on expand. Works when threads are shallow.

```java
List<Comment> getTopLevelComments(String postId, int page, int size,
                                   SortStrategy strategy) {
    CommentTree tree = getTree(postId);
    List<Comment> sortedRoots = strategy.sort(tree.getRoots());
    int start = page * size;
    int end = Math.min(start + size, sortedRoots.size());
    if (start >= sortedRoots.size()) {
        return new ArrayList<>();
    }
    return sortedRoots.subList(start, end);
}
```

**Cursor-based**: Encode cursor as `(parent_id, offset)`. The client calls `GET /comments?post_id=X&parent_id=c1&cursor=Y` to load more children. This is Reddit's "load more comments" pattern.

### 5. "How would you add comment moderation / flagging?"

Add `flags: Map<String, String>` to `Comment` (user_id → reason). Auto-hide when flag count exceeds threshold. A `ModerationService` processes the queue.

```java
static final int FLAG_THRESHOLD = 5;

void flagComment(String postId, String commentId,
                  String reporterId, String reason) {
    Comment comment = getTree(postId).find(commentId);
    if (reporterId.equals(comment.getUserId())) {
        throw new IllegalArgumentException("Cannot flag your own comment");
    }
    comment.getFlags().put(reporterId, reason);
    if (comment.getFlags().size() >= FLAG_THRESHOLD) {
        comment.setAutoHidden(true);
    }
}
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

---

## Related

**Patterns applied here**

- [Composite Pattern](../../03-design-patterns/02-structural/composite-pattern.md)
- [Observer Pattern](../../03-design-patterns/03-behavioral/observer-pattern.md)
- [Strategy Pattern](../../03-design-patterns/03-behavioral/strategy-pattern.md)

**SOLID focus**: [Single Responsibility](../../02-solid-principles/01-single-responsibility.md) · [Open/Closed](../../02-solid-principles/02-open-closed.md)

**Practice next**

- [Design Pub/Sub](../03-domain-specific/23-design-pub-sub.md)
- [Design Inventory Management](../03-domain-specific/24-design-inventory-management.md)

Pub-sub generalises the notification fan-out used here.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
