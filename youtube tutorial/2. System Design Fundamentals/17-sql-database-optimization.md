# SQL & Database Optimization

> **Source**: [Secret To Optimizing SQL Queries - Understand The SQL Execution Order](https://www.youtube.com/playlist?list=PLCRMIe5FDPsd0gVs500xeOewfySTsmEjf) — Video #27
> Also: [7 Must-know Strategies to Scale Your Database](https://www.youtube.com/playlist?list=PLCRMIe5FDPsd0gVs500xeOewfySTsmEjf) — Video #68

---

## SQL Execution Order

The SQL engine processes clauses in a specific order (different from how you write it):

```
Written Order:               Execution Order:
SELECT                  1.   FROM / JOIN
FROM                    2.   WHERE
WHERE                   3.   GROUP BY
GROUP BY                4.   HAVING
HAVING                  5.   SELECT
ORDER BY                6.   DISTINCT
LIMIT                   7.   ORDER BY
                        8.   LIMIT / OFFSET
```

### Why This Matters
- **WHERE** is processed before **SELECT** → can't use aliases in WHERE
- **GROUP BY** before **HAVING** → HAVING filters groups, WHERE filters rows
- **ORDER BY** is near-last → expensive on large result sets
- Understanding order helps write **optimized queries**

---

## 7 Strategies to Scale Your Database

### 1. Indexing
- Create indexes on frequently queried columns
- **B-Tree index**: Range queries, ordered data
- **Hash index**: Exact match lookups
- **Composite index**: Multiple columns (leftmost prefix rule)
- ⚠️ Too many indexes slow down writes

### 2. Read Replicas
```
Primary (writes) → Replica 1 (reads)
                 → Replica 2 (reads)
                 → Replica 3 (reads)
```
- Scale reads horizontally
- Replication lag = eventual consistency

### 3. Sharding (Horizontal Partitioning)
- Distribute data across multiple databases
- Choose shard key carefully (high cardinality, even distribution)
- Types: Range-based, Hash-based, Directory-based

### 4. Vertical Partitioning
- Split table columns across tables/databases
- Frequently accessed columns together
- Large BLOBs in separate storage

### 5. Caching
- Cache hot query results (Redis/Memcached)
- Reduces database load dramatically
- Cache-aside is the most common pattern

### 6. Materialized Views
- Pre-computed query results stored as a table
- Updated periodically or on change
- Great for complex aggregations

### 7. Connection Pooling
- Reuse database connections instead of creating new ones
- Tools: PgBouncer (PostgreSQL), HikariCP (Java)
- Reduces connection overhead dramatically

---

## Query Optimization Tips

1. **Use EXPLAIN/EXPLAIN ANALYZE** to understand query plans
2. **Avoid SELECT *** — only select needed columns
3. **Use indexes** on WHERE, JOIN, ORDER BY columns
4. **Avoid N+1 queries** — use JOINs or batch loading
5. **Limit result sets** — use pagination
6. **Avoid functions on indexed columns** in WHERE
7. **Use covering indexes** — index includes all needed columns
8. **Partition large tables** — time-based partitioning for time-series
