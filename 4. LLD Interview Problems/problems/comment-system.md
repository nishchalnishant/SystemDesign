# Design Reddit Comments System (LLD & Schema)

> **Difficulty**: Medium  
> **Topics**: Database Schema, Hierarchical Data, Materialized Path, Recursion  
> **Extension**: Pagination, Vote Counts

## Problem Statement

Design a scalable comment system like Reddit.
- **Features**: Threaded comments (infinite nesting), Upvotes/Downvotes, Pagination.
- **Scale**: Millions of comments. Read-heavy.
- **Challenge**: Storing and fetching trees efficiently in SQL.

## Core Problem: Storing Trees in SQL

| Strategy | Concept | Pros | Cons | Verdict |
| :--- | :--- | :--- | :--- | :--- |
| **Adjacency List** | Column `parent_id` | Simple, easy inserts. | Fetching tree needs recursive queries (CTEs). Slow for deep trees. | Standard |
| **Materialized Path** | Column `path` (e.g., "001/005/012") | Fast subtree retrieval via prefix scan. Sorting by string path gives DFS order! | Moving subtrees is hard. | **Best for Reddit** |

## Database Schema

### Table 1: `Comments`

```sql
CREATE TABLE Comments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL,          -- The Thread ID
    user_id BIGINT NOT NULL,
    parent_id BIGINT NULL,            -- Optimization for direct parent lookups
    
    -- THE MAGIC COLUMN
    -- Stores the lineage: "root_id/child_id/grandchild_id"
    -- Example ID 101 nested under 50 nested under 1: "0001/0050/0101"
    path VARCHAR(255) NOT NULL,
    
    content TEXT,
    depth INT DEFAULT 0,              -- Indentation level (UI helper)
    
    -- Denormalized Counters (For Read Speed)
    upvote_count INT DEFAULT 0,
    downvote_count INT DEFAULT 0,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_post_path (post_id, path) -- Crucial for "ORDER BY path"
);
```

### Table 2: `Votes` (Prevent double voting)

```sql
CREATE TABLE CommentVotes (
    user_id BIGINT,
    comment_id BIGINT,
    vote_value TINYINT, -- +1 for Up, -1 for Down
    PRIMARY KEY (user_id, comment_id)
);
```

## Key Operations

### 1. Fetching the Tree (Depth-First Order)
Sorting by `path` naturally groups comments in the correct threaded order for UI.

```sql
SELECT * FROM Comments 
WHERE post_id = 55 
ORDER BY path ASC 
LIMIT 50; 
```
**Result**:
1. `0001` (Root)
2. `0001/0002` (Child of 1)
3. `0001/0002/0003` (Grandchild of 1)
4. `0005` (New Root)

### 2. Inserting a Reply
Logic: `New_Path = Parent_Path + "/" + New_ID`.

```python
class CommentService:
    def reply_to_comment(self, parent_id, user_id, content):
        parent = self.db.fetch("SELECT path, depth, post_id FROM Comments WHERE id=%s", parent_id)
        
        new_id = self.generate_id() 
        # Format ID to fixed length (e.g., 4 chars) for string sorting
        child_path = f"{parent['path']}/{str(new_id).zfill(4)}"
        child_depth = parent['depth'] + 1

        sql = "INSERT INTO Comments (id, post_id, path, depth, content) VALUES (...)"
        self.db.execute(sql, (new_id, parent['post_id'], child_path, child_depth, content))
```

## Optimization & Scaling

1.  **Viral Post (Hot Partition)**: Shard DB by `post_id`. All comments for a post stay on one shard to maintain the tree.
2.  **Denormalization**: `upvote_count` in `Comments` table avoids `COUNT(*)` on huge `Votes` table every read.
3.  **Caching**: Cache top 200 comments of hot threads in Redis (List/SortedSet).
