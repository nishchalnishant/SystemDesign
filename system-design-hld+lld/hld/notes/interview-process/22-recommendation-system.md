# #22 recommendation system

Here’s a complete, time-boxed, 1-hour interview-ready answer for designing a Recommender System (like Netflix, Amazon, or YouTube). It follows your system design interview structure, including functional & non-functional requirements, APIs/data model, architecture, deep dive, and trade-offs.

***

## 0 – 5 min — Problem recap, scope & assumptions

<br>

Goal: Design a scalable system that provides personalized recommendations to users based on their behavior, preferences, and/or item metadata.

<br>

Scope for interview:

* Recommend items (movies, products, videos, etc.) to users in real-time.
* Support millions of users and items.
* Track user interactions to update recommendations.
* Include both online (real-time) and offline (batch) recommendation pipelines.
* Metrics: engagement (clicks, purchases, watch time).

<br>

Assumptions:

* 10M users, 1M items.
* Recommendations updated daily offline; top suggestions cached for real-time serving.
* Users may interact via web or mobile app.
* System supports filtering (e.g., categories, content restrictions).

***

## 5 – 15 min — Functional & Non-Functional Requirements

<br>

#### Functional Requirements

<br>

Must

1. Personalized recommendations: Recommend items per user.
2. Top-k retrieval: Return top N recommended items quickly.
3. User activity tracking: Capture clicks, views, purchases, ratings.
4. Filtering: Ability to filter recommendations by categories, availability, or other business rules.
5. Real-time serving: Fetch recommendations with low latency.

<br>

Should

* Support hybrid recommendation: collaborative filtering + content-based.
* Recommendations for anonymous users (popular/trending items).
* Track cold-start users/items.

<br>

Nice-to-have

* A/B testing of recommendation algorithms.
* Trending or seasonal recommendations.
* Multi-language/multi-region support.

***

#### Non-Functional Requirements

* Latency: Recommendations served in <100 ms.
* Availability: 99.99% uptime, globally.
* Scalability: Millions of users, millions of items, real-time updates.
* Durability: Persist user activity and recommendation model outputs.
* Consistency: Eventual consistency acceptable for real-time updates; strong consistency for critical metrics.
* Monitoring: Track recommendation quality, system latency, errors.

***

## 15 – 25 min — API / Data Model

<br>

#### APIs

```
# Real-time recommendation
get_recommendations(user_id: str, top_k: int=10) -> List[item_id]

# Track user activity
log_user_activity(user_id: str, item_id: str, action: str, timestamp: datetime)

# Manage recommendation models (admin)
update_model(model_id: str, model_data: json)
```

#### Data Models

<br>

User

```
{
  "user_id": "string",
  "profile": { "age": int, "preferences": ["genre1", "genre2"] },
  "activity_history": [{"item_id": "string", "action": "click/view", "timestamp": "ts"}]
}
```

Item

```
{
  "item_id": "string",
  "metadata": {"category": "string", "tags": ["tag1","tag2"], "popularity": int},
  "ratings": {"avg": float, "count": int}
}
```

Recommendation

```
{
  "user_id": "string",
  "recommended_items": [{"item_id": "string", "score": float}],
  "last_updated": "timestamp"
}
```

***

## 25 – 40 min — High-level architecture & data flow

```
[Client] --> [API Gateway] --> [Recommendation Service] --> [Cache / Redis]
                                         |
                                         v
                              [User Activity Store / Event Bus]
                                         |
                                         v
                              [Offline Model Training / ML Pipeline]
                                         |
                                         v
                                 [Feature Store / Model Store]
```

Components

1. API Gateway: Routes recommendation requests from clients.
2. Recommendation Service: Fetches precomputed recommendations from cache; falls back to online computation if needed.
3. Cache / Redis: Stores top-k recommendations per user for fast retrieval.
4. User Activity Store: Logs interactions; could be Kafka, Kinesis, or DB.
5. Offline Model Training: Batch process ML models (collaborative filtering, content-based, hybrid).
6. Feature Store / Model Store: Store item features, embeddings, model outputs.

<br>

Data Flow

1. User activity logged in event bus → stored for batch training.
2. Offline model generates recommendations → stored in cache/DB.
3. User requests recommendations → API fetches from cache → returned to client.
4. Optional online adjustments: use recent activity to re-rank recommendations in real-time.

***

## 40 – 50 min — Deep dive — recommendation algorithms & scaling

<br>

#### Algorithms

* Collaborative Filtering: Matrix factorization / embeddings.
* Content-Based Filtering: Item metadata similarity.
* Hybrid: Combine both approaches, weighted scoring.
* Real-time updates: Re-rank cached recommendations based on recent activity.

<br>

#### Scaling

* Sharding: Split users across multiple nodes.
* Caching: Serve top recommendations from Redis or in-memory cache.
* Batch vs Streaming: Offline batch for model training; streaming for near-real-time personalization.
* Feature Store: Store embeddings for items and users for fast retrieval.

<br>

#### Fault Tolerance

* Cache replication across regions.
* Event bus for durable logging.
* Retry mechanisms for model updates and user activity logging.

***

## 50 – 55 min — Back-of-the-envelope calculations

<br>

Assumptions

* 10M users, 1M items → each user gets top 50 recommendations → \~500M entries.
* Each recommendation entry \~64 bytes → \~32 GB (fits in memory with sharding).
* 1M user activity events/day → manageable with Kafka or similar.
* Latency: Cached recommendations <10 ms; online computation fallback <100 ms.

<br>

Storage

* Cache: Redis clusters for fast retrieval.
* Persistent DB: NoSQL for activity logs, relational DB for metadata.
* Analytics warehouse: For offline training.

***

## 55 – 58 min — Monitoring & ops

<br>

Monitoring

* Cache hit/miss ratio.
* Recommendation latency.
* Click-through rate (CTR) as recommendation quality metric.
* System health: API errors, queue lag in event bus.

<br>

Operational concerns

* Update models periodically (nightly or weekly).
* Handle cold-start users/items (popular or content-based suggestions).
* Rolling deployment of new models for A/B testing.

***

## 58 – 60 min — Trade-offs, evolution & summary

<br>

#### Trade-offs

* Batch vs Real-time: Batch cheaper and simpler but less fresh; real-time personalization better UX but more complex.
* Memory vs Accuracy: Caching top recommendations improves latency; recomputing online increases freshness but higher load.
* Complexity vs Speed: Hybrid models improve recommendation quality but increase system complexity.

<br>

#### Evolution

1. MVP: Popular items or collaborative filtering recommendations, batch offline updates.
2. Phase 2: Real-time re-ranking using recent activity; caching per user.
3. Phase 3: Personalization with hybrid ML models, embeddings, trending items, multi-language support.

<br>

#### Summary

* Build a scalable, low-latency recommender system:
  * Event-driven logging of user interactions.
  * Offline batch ML models to precompute recommendations.
  * Cache top-k recommendations for fast serving.
  * Support real-time personalization and re-ranking.
  * Horizontally scalable, fault-tolerant, and monitorable system.

***

If you want, I can next create a sequence diagram showing a user interacting with the recommender system, logging activity, fetching cached recommendations, and updating models, which is very helpful in interviews.

<br>

Do you want me to create that diagram next?
