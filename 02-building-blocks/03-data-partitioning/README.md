# Data Partitioning

Splitting and copying data across nodes — the heart of most hard scaling questions.

| File | What it covers |
|------|----------------|
| [Sharding](01-sharding.md) | Range, hash, and directory partitioning; shard key choice, hot partitions, resharding. |
| [Database Replication](02-replication.md) | Leader-follower, multi-leader, leaderless; replication lag and read-after-write. |
| [Consistent Hashing](03-consistent-hashing.md) | Virtual nodes and minimal reshuffling when the cluster changes size. |
