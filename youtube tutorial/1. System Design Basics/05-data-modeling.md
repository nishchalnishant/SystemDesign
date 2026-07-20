# Data Modeling in System Design Interviews

> **Source**: [Data Modeling in System Design Interviews w/ Meta Staff Engineer](https://www.youtube.com/watch?v=1HOVtQ-_fcE&list=PL5q3E8eRUieVFeK1oLahJ8KONkAxDpqk2&index=5)

---

## What is Data Modeling?

**Data modeling** is the process of defining how data is stored, organized, and related in your system. It directly impacts performance, scalability, and query patterns.

---

## Why Data Modeling Matters in Interviews

- Shows you think about **trade-offs** (normalization vs denormalization)
- Demonstrates understanding of **access patterns** driving schema design
- Reveals awareness of **database selection** (SQL vs NoSQL)
- Core building block that influences caching, sharding, and API design

---

## Step-by-Step Approach

### 1. Identify Entities
- What are the main objects/nouns in the system?
- Example (Twitter): Users, Tweets, Followers, Likes, Retweets

### 2. Define Relationships
- One-to-one (User → Profile)
- One-to-many (User → Tweets)
- Many-to-many (User ↔ Followers)

### 3. Determine Access Patterns
- What queries will be most frequent?
- What are the read/write ratios?
- What latency requirements exist?

### 4. Choose Storage
- SQL vs NoSQL based on requirements
- Optimize schema for the dominant access pattern

---

## SQL vs NoSQL Decision

| Factor | Choose SQL | Choose NoSQL |
|---|---|---|
| **Data Structure** | Structured, well-defined schema | Flexible, evolving schema |
| **Relationships** | Complex relationships, joins needed | Denormalized, self-contained docs |
| **Consistency** | Strong consistency required (ACID) | Eventual consistency acceptable |
| **Scale** | Moderate scale, vertical scaling | Massive scale, horizontal scaling |
| **Queries** | Complex queries, aggregations | Simple key-value or document lookups |
| **Examples** | Financial systems, user accounts | Social feeds, product catalogs, logs |

### SQL Databases
- PostgreSQL, MySQL, Oracle
- ACID transactions
- Schema enforcement
- Powerful query language (SQL)

### NoSQL Categories
| Type | Example | Use Case |
|---|---|---|
| **Key-Value** | Redis, DynamoDB | Session store, cache, simple lookups |
| **Document** | MongoDB, CouchDB | Product catalog, user profiles |
| **Wide-Column** | Cassandra, HBase | Time-series, IoT data, analytics |
| **Graph** | Neo4j, Amazon Neptune | Social networks, recommendations |

---

## Normalization vs Denormalization

### Normalization
- Eliminate data redundancy
- Split data into related tables
- Use foreign keys to maintain relationships
- **Pros**: Data consistency, less storage, easy updates
- **Cons**: Complex joins, slower reads

### Denormalization
- Duplicate data for faster reads
- Embed related data in the same document/row
- **Pros**: Faster reads, simpler queries, fewer joins
- **Cons**: Data inconsistency risk, more storage, complex updates

### When to Normalize
- Write-heavy systems
- Data integrity is critical
- Data changes frequently
- Storage is a concern

### When to Denormalize
- Read-heavy systems (read >> write)
- Low-latency reads required
- Acceptable eventual consistency
- Data rarely changes

---

## Data Modeling Patterns

### 1. Embedding (Denormalized)
```json
// User document with embedded posts
{
  "user_id": "123",
  "name": "Alice",
  "posts": [
    { "post_id": "p1", "content": "Hello world", "timestamp": "..." },
    { "post_id": "p2", "content": "Another post", "timestamp": "..." }
  ]
}
```
- Good when: Data accessed together, 1:few relationship
- Bad when: Embedded data grows unbounded, updated frequently

### 2. Referencing (Normalized)
```json
// User document
{ "user_id": "123", "name": "Alice" }

// Post documents
{ "post_id": "p1", "user_id": "123", "content": "Hello world" }
{ "post_id": "p2", "user_id": "123", "content": "Another post" }
```
- Good when: Many-to-many, data changes independently, unbounded growth
- Bad when: Frequent joins needed (adds latency)

### 3. Hybrid Approach
- Embed **frequently read** fields, reference the rest
- Example: Embed author name in post (avoids join for display), reference full user profile
```json
{
  "post_id": "p1",
  "content": "Hello world",
  "author": { "user_id": "123", "name": "Alice" },  // embedded summary
  "user_id": "123"  // reference for full profile
}
```

---

## Common Data Modeling Examples

### Social Media (Twitter-like)
```
Users table:       user_id (PK), username, email, bio, created_at
Tweets table:      tweet_id (PK), user_id (FK), content, timestamp
Follows table:     follower_id, followee_id (composite PK)
Likes table:       user_id, tweet_id, timestamp (composite PK)
```

### E-Commerce
```
Users table:       user_id (PK), email, name
Products table:    product_id (PK), name, price, description, seller_id
Orders table:      order_id (PK), user_id (FK), total, status, created_at
Order_Items table: order_id (FK), product_id (FK), quantity, price
```

### Chat System
```
Users table:         user_id (PK), username
Conversations table: conversation_id (PK), type (1:1, group)
Participants table:  conversation_id (FK), user_id (FK)
Messages table:      message_id (PK), conversation_id (FK), sender_id, content, timestamp
```

---

## Indexing Strategy

### Primary Index
- Automatically created on the primary key
- Used for direct lookups

### Secondary Indexes
- Created on frequently queried columns
- Example: Index on `user_id` in tweets table for "get all tweets by user"

### Composite Indexes
- Index on multiple columns
- Order matters: (user_id, timestamp) ≠ (timestamp, user_id)
- Follow the **leftmost prefix** rule

### When to Index
- Columns used in WHERE, JOIN, ORDER BY
- High cardinality columns
- Read-heavy columns

### When NOT to Index
- Write-heavy columns (indexes slow down writes)
- Low cardinality (boolean, status with few values)
- Rarely queried columns

---

## Time-Series Data Considerations

- Append-heavy, rarely updated
- Queries are time-range based
- Consider **wide-column stores** (Cassandra) or **time-series DBs** (InfluxDB)
- Partition by time (daily/weekly tables)
- Use TTL for automatic data expiration

---

## Interview Tips

1. **Start with entities and relationships** — draw an ER diagram
2. **Ask about access patterns** before choosing schema
3. **Discuss trade-offs** of normalization vs denormalization
4. **Choose the right database type** based on requirements
5. **Mention indexing strategy** for query optimization
6. Don't over-design — **model for your top 3 query patterns**
7. Consider **data growth** — will the model scale?
