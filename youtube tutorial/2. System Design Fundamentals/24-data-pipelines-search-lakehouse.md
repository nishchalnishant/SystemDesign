# Data Pipelines, Search & Data Lakehouse

> **Source**: Videos #66, #76, #101 from the playlist
> - What is Data Pipeline? | Why Is It So Popular?
> - How Search Really Works
> - What is a Data Lakehouse?

---

## Data Pipelines

### What is a Data Pipeline?
A set of processes that move and transform data from source(s) to destination(s).

```
Sources → Ingestion → Processing → Storage → Analytics/ML
  (APIs,     (Kafka,    (Spark,     (Data     (BI tools,
   DBs,       Kinesis)   Flink)     Warehouse) ML models)
   files)
```

### Types of Data Pipelines

| Type | Latency | Processing | Tools | Use Case |
|---|---|---|---|---|
| **Batch** | Minutes-hours | Scheduled jobs | Spark, Hadoop | Daily reports, ETL |
| **Stream** | Milliseconds-seconds | Continuous | Kafka, Flink, Spark Streaming | Real-time analytics |
| **Micro-batch** | Seconds-minutes | Small batches | Spark Structured Streaming | Near-real-time |

### ETL vs ELT

| ETL | ELT |
|---|---|
| Extract → Transform → Load | Extract → Load → Transform |
| Transform before loading | Transform after loading |
| Traditional data warehousing | Modern cloud data warehouses |
| Limited by ETL server | Leverages warehouse compute power |

### Key Components
- **Ingestion**: Kafka, Kinesis, Pub/Sub
- **Processing**: Spark, Flink, Beam
- **Orchestration**: Airflow, Dagster, Prefect
- **Storage**: S3, GCS, HDFS, Data Warehouse
- **Quality**: Great Expectations, dbt tests

---

## How Search Works

### Inverted Index (Core Data Structure)
```
Document 1: "The quick brown fox"
Document 2: "The brown dog"

Inverted Index:
  "the"   → [Doc1, Doc2]
  "quick" → [Doc1]
  "brown" → [Doc1, Doc2]
  "fox"   → [Doc1]
  "dog"   → [Doc2]
```

### Search Pipeline
```
1. Crawl/Index   → Discover and index content
2. Query Parse   → Understand user's search intent
3. Retrieval     → Find matching documents (inverted index)
4. Ranking       → Score and rank results (relevance)
5. Return        → Display results to user
```

### Ranking Factors
- **TF-IDF**: Term Frequency × Inverse Document Frequency
- **BM25**: Improved version of TF-IDF (standard in Elasticsearch)
- **PageRank**: Link-based authority score (Google)
- **Semantic search**: Embedding-based similarity (modern)

### Search Technologies
| Tool | Description |
|---|---|
| **Elasticsearch** | Distributed search engine (based on Lucene) |
| **Apache Solr** | Search platform (based on Lucene) |
| **Typesense** | Lightweight, typo-tolerant search |
| **Meilisearch** | Fast, easy-to-deploy search |
| **Algolia** | SaaS search platform |

---

## Data Lakehouse

### Evolution of Data Architectures

```
Data Warehouse → Data Lake → Data Lakehouse
(Structured)    (All data)   (Best of both)
```

### Data Warehouse
- **Structured data** only (schema-on-write)
- Optimized for **SQL queries and BI**
- Examples: Snowflake, BigQuery, Redshift
- **Expensive** for large data volumes

### Data Lake
- **All data types** (structured, semi-structured, unstructured)
- Schema-on-read
- Cheap storage (S3, GCS, ADLS)
- **Problem**: Becomes a "data swamp" without governance
- Examples: S3 + Athena, HDFS

### Data Lakehouse
- **Combines** warehouse features with lake storage
- Open file formats (Parquet, ORC) on cheap storage
- ACID transactions, schema enforcement, time travel
- Technologies:
  - **Delta Lake** (Databricks)
  - **Apache Iceberg** (Netflix)
  - **Apache Hudi** (Uber)

| Feature | Data Warehouse | Data Lake | Data Lakehouse |
|---|---|---|---|
| Data Types | Structured | All | All |
| Schema | On-write | On-read | On-write + on-read |
| ACID | Yes | No | Yes |
| Cost | High | Low | Low |
| SQL | Yes | Limited | Yes |
| ML Support | Limited | Yes | Yes |
