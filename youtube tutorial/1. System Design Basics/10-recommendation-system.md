# Recommendation System Infra Basics

> **Source**: [Recommendation System Infra Basics 1](https://www.youtube.com/watch?v=1HOVtQ-_fcE&list=PL5q3E8eRUieVFeK1oLahJ8KONkAxDpqk2&index=10)

---

## What is a Recommendation System?

A recommendation system predicts user preferences and surfaces relevant items (products, content, connections) from a large catalog. It's the backbone of personalized experiences at companies like Netflix, YouTube, Amazon, TikTok, and Spotify.

---

## High-Level Architecture

```
                     ┌─────────────────┐
                     │   User Request   │
                     └────────┬────────┘
                              ▼
                     ┌─────────────────┐
                     │  Candidate       │
                     │  Generation      │  (Retrieve ~1000 candidates from millions)
                     └────────┬────────┘
                              ▼
                     ┌─────────────────┐
                     │   Filtering      │  (Remove ineligible items)
                     └────────┬────────┘
                              ▼
                     ┌─────────────────┐
                     │   Ranking        │  (Score and rank ~1000 → top ~50)
                     └────────┬────────┘
                              ▼
                     ┌─────────────────┐
                     │   Re-Ranking     │  (Business rules, diversity, freshness)
                     └────────┬────────┘
                              ▼
                     ┌─────────────────┐
                     │   Response       │  (Return top ~10-20 items)
                     └─────────────────┘
```

---

## Stage 1: Candidate Generation

### Purpose
- Quickly narrow down from **millions of items** to **hundreds/thousands** of candidates
- Must be **fast** (< 50ms) even at scale
- Trades precision for recall

### Common Approaches

#### 1. Collaborative Filtering
- **User-User**: Find similar users → recommend what they liked
- **Item-Item**: Find items similar to what user has interacted with
- **Matrix Factorization**: Decompose user-item interaction matrix (e.g., ALS, SVD)

#### 2. Content-Based Filtering
- Recommend items similar to what the user liked before
- Based on item features (genre, category, tags, description)
- Example: If user likes action movies → recommend other action movies

#### 3. Embedding-Based Retrieval (Modern)
- Represent users and items as **dense vectors** (embeddings)
- Use **Approximate Nearest Neighbor (ANN)** search
- Tools: FAISS, Annoy, ScaNN, Pinecone, Milvus
```
User embedding: [0.2, 0.8, -0.3, 0.5, ...]
Item embedding: [0.1, 0.7, -0.2, 0.6, ...]
Similarity = cosine(user_emb, item_emb)
```

#### 4. Graph-Based Retrieval
- Model user-item interactions as a graph
- Use graph traversal (BFS/random walks) for candidates
- GraphSAGE, PinSage (Pinterest)

### Multiple Candidate Sources
Typically combine multiple sources for diversity:
```
Source 1: ANN-based retrieval         → 300 candidates
Source 2: Item-item collaborative     → 200 candidates
Source 3: Trending/popular items      → 100 candidates
Source 4: Content-based similar items → 200 candidates
Source 5: Contextual (location, time) → 100 candidates
                                Total:  ~900 candidates
```

---

## Stage 2: Filtering

### Remove Ineligible Items
- Items the user has already seen/interacted with
- Items that violate business rules (age restrictions, geo-restrictions)
- Out-of-stock items (e-commerce)
- Blocked/reported content
- Items outside user's language preference

### Bloom Filters for "Already Seen"
- Use a **bloom filter** per user to efficiently check if an item was already shown
- Space-efficient probabilistic data structure
- False positives OK (skip a few good items), no false negatives

---

## Stage 3: Ranking

### Purpose
- Apply a **more expensive, more precise model** to score the candidates
- Typically a **deep learning model** trained on user engagement data

### Features Used for Ranking
| Category | Examples |
|---|---|
| **User features** | Age, location, watch history, interests |
| **Item features** | Category, creation date, popularity, quality score |
| **Context features** | Time of day, device type, session position |
| **Interaction features** | Past interactions between user and item type |
| **Cross features** | User × Item feature combinations |

### Ranking Models
- **Logistic Regression**: Simple, fast baseline
- **GBDT** (Gradient Boosted Decision Trees): XGBoost, LightGBM
- **Deep Learning**: Wide & Deep, DeepFM, DIN (Deep Interest Network)
- **Multi-task Learning**: Predict multiple objectives (click, watch time, like)

### Prediction Targets
```
P(click)        × weight_click +
P(watch_time)   × weight_watch +
P(like)         × weight_like  +
P(share)        × weight_share -
P(not_interest) × weight_negative
= Final Score
```

---

## Stage 4: Re-Ranking

### Business Logic Layer
- **Diversity**: Ensure variety (not all from same category)
- **Freshness**: Boost newer content
- **Fairness**: Ensure representation across creators/sellers
- **Ad insertion**: Interleave sponsored content
- **Sequential logic**: Don't show Part 2 before Part 1
- **Deduplication**: Remove near-duplicate content

---

## Infrastructure Components

### Feature Store
- Centralized repository for ML features
- Serves features with low latency for online inference
- Stores historical features for training
- Examples: Feast, Tecton, Redis as feature cache

```
Online Feature Store (Redis/DynamoDB):
  user:123 → { watch_history: [...], avg_session_time: 15min }
  item:456 → { category: "tech", views: 50000, avg_rating: 4.5 }

Offline Feature Store (Hive/S3):
  Historical interaction logs for model training
```

### Model Serving
- Serve ML models with low latency (< 20ms per prediction)
- Support batch and real-time inference
- Tools: TensorFlow Serving, TorchServe, Triton Inference Server

### Embedding Index
- Store and query item/user embeddings
- ANN (Approximate Nearest Neighbor) index for fast retrieval
- Tools: FAISS, ScaNN, Annoy, Milvus, Pinecone

### Event Processing Pipeline
```
User Action → Event Queue (Kafka) → Stream Processing (Flink/Spark)
                                          ↓
                                   Feature Computation
                                          ↓
                                   Feature Store Update
                                          ↓
                                   Model Retraining (periodic)
```

---

## Key Metrics

| Metric | Description |
|---|---|
| **CTR** (Click-Through Rate) | % of impressions that get clicked |
| **Watch Time** | Total time users spend watching recommended content |
| **Engagement Rate** | Likes, shares, comments per impression |
| **Diversity** | Variety in recommendations |
| **Coverage** | % of catalog that gets recommended |
| **Freshness** | Recency of recommended items |
| **Latency** | Time to generate recommendations (< 100ms target) |

---

## Handling Cold Start

| Problem | Solution |
|---|---|
| **New User** | Use demographics, popular items, onboarding quiz |
| **New Item** | Content-based features, boost in explore pool |
| **New System** | Start with rules-based, popularity-based recommendations |

---

## Online vs Offline

| Aspect | Offline (Batch) | Online (Real-time) |
|---|---|---|
| **When** | Periodic (daily, hourly) | Per-request |
| **Latency** | Minutes to hours | Milliseconds |
| **Use** | Pre-compute recommendations, model training | Serve personalized results |
| **Tools** | Spark, Hive, Airflow | Flink, Kafka, feature store |

### Hybrid Approach (Common)
- **Offline**: Pre-compute candidate pools, train models, compute features
- **Online**: Rank candidates in real-time with fresh context features

---

## Interview Tips

1. **Draw the funnel**: Candidate generation → Filtering → Ranking → Re-ranking
2. **Discuss scale**: Millions of items → thousands of candidates → top 10-20 shown
3. **Mention embedding-based retrieval** — shows modern ML awareness
4. **Discuss feature stores** — shows production ML awareness
5. **Address cold start** — what happens for new users/items?
6. **Talk about metrics** — CTR, engagement, diversity
7. **Mention offline + online hybrid** — most production systems use both
8. Don't over-index on the ML model — **infrastructure and data pipeline** matter more
