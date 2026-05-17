# ML System Design

## The Unique Challenges of ML Systems

ML systems are fundamentally different from traditional software systems in three ways:

1. **Non-deterministic**: given the same input, the model may not always give the same output (stochastic sampling, model updates, hardware differences)
2. **Data-dependent**: the system's behavior is determined by training data, not just code — a "bug" might be a data problem, not a code problem
3. **Needs retraining**: the world changes; a model trained on last year's data degrades over time (concept drift)

**Analogy:** Traditional software is like a precise recipe — same ingredients, same steps, same dish every time. An ML system is like a restaurant that changes its menu based on what customers ordered last month. The chef needs new training data (menu feedback from customers), a training pipeline (recipe development and testing), and a serving pipeline (kitchen service). If customers' tastes change and you don't update the menu, sales drop — even though the kitchen is working perfectly.

This is why ML systems need:
- **Data pipelines** alongside code pipelines
- **Model monitoring** alongside system monitoring
- **Retraining infrastructure** not just serving infrastructure

---

## Feature Store

**What is it?**

A feature store is a centralized repository for pre-computed, reusable features used to train and serve ML models.

**Analogy:** A shared pantry where pre-processed ingredients (features) are stored for all chefs (models) to use. Without a feature store, every chef preps from scratch — the pasta chef boils water, the pizza chef boils water, the soup chef boils water. With a feature store, you boil it once and everyone uses it. The pantry has two sections: a fresh section (online store, low-latency) for real-time use, and a bulk section (offline store) for training.

**Two components:**

| Component | Purpose | Latency | Storage |
|-----------|---------|---------|---------|
| Online Store | Serve features at inference time | < 10ms | Redis, DynamoDB, Cassandra |
| Offline Store | Provide features for training | Seconds | S3, HDFS, BigQuery |

**Training-serving skew (critical problem):**
If the feature computation logic differs between training (offline) and serving (online), the model's performance in production won't match its evaluation metrics. Feature stores solve this by using the same feature definitions for both.

**Examples:** Feast (open source), Tecton, AWS SageMaker Feature Store, Vertex AI Feature Store

**Feature types:**
- **Batch features** (computed daily/hourly): `user_avg_spend_last_30_days`, `item_purchase_count`
- **Real-time features** (computed on the fly): `time_since_last_click`, `cart_value_at_checkout`
- **Pre-computed streaming features** (Kafka + Flink): `user_click_rate_last_5_minutes`

---

## Training Pipeline

```
Data Collection → Data Validation → Feature Engineering → Model Training → Evaluation → Model Registry → Serving
```

### 1. Data Collection

- Batch data from data warehouse (Snowflake, BigQuery, Redshift)
- Streaming data from event logs (Kafka → S3)
- Labels from annotation pipelines or implicit feedback (clicks, purchases, ratings)

**Data quality gates:** enforce schema, null rate thresholds, distribution checks before training proceeds.

### 2. Data Validation (often skipped, always regretted)

- Schema validation: expected columns, data types
- Statistical validation: check for distribution shift vs. previous training run
- Label quality: check label balance, label accuracy

Tools: TFX Data Validation, Great Expectations, Deequ (AWS)

### 3. Feature Engineering

- Normalization, encoding, embedding
- Time-based features: rolling averages, lag features
- Cross features: `user_category_affinity × item_category`

**Point-in-time correct joins:** when creating training data from time-series, join features at the timestamp of the label event, not the current time. Getting this wrong causes target leakage (model learns from future data it won't have at inference time).

### 4. Model Training

- Distributed training for large models: parameter servers, data parallelism (PyTorch DDP, Horovod)
- Hyperparameter tuning: grid search, random search, Bayesian optimization (Optuna)
- Experiment tracking: log metrics, parameters, artifacts per run (MLflow, Weights & Biases)

### 5. Evaluation

Never deploy a model without evaluation gates:

| Metric type | Examples |
|-------------|---------|
| Offline metrics | AUC, precision@k, NDCG, RMSE |
| Business metrics (shadow mode) | CTR, conversion rate, revenue per user |
| Fairness metrics | Performance across demographic slices |
| Regression tests | Performance on known edge-case test sets |

**Critical principle:** offline metrics don't always predict online performance. Always A/B test before full rollout.

### 6. Model Registry

Central catalog of trained models with metadata:
- Training dataset version + time range
- Evaluation metrics
- Feature schema version
- Artifact location (S3 path)
- Deployment stage (staging, production, archived)

Tools: MLflow Model Registry, SageMaker Model Registry, Vertex AI Model Registry

---

## Serving: Batch vs Real-Time Inference

### Batch Inference (Offline)

Pre-compute predictions for all users/items on a schedule, store results.

```
Daily job: load model → iterate over all users → compute score → write to DB/cache
User request at runtime: DB lookup → return pre-computed score
```

**Use when:** predictions don't need to be fresh at query time, or computation is too expensive to do in real-time.

**Examples:** pre-compute weekly recommendations, email targeting lists, fraud risk scores for overnight review

**Trade-offs:**
- Pros: simple architecture, can use expensive models, batch GPU utilization
- Cons: predictions are stale, can't personalize on real-time signals (user just bought something)

### Real-Time Inference (Online)

Model is called at request time, returns a fresh prediction.

```
User request → Feature Store (online) → Model Server → Prediction → Response
                                          ↓
                                     p99 latency SLA: 50ms
```

**Use when:** freshness matters (real-time recommendations, fraud detection during transaction, content ranking)

**Architecture:**
```
Client → API Gateway → Feature Service (online feature store) → Model Server (TF Serving, TorchServe, Triton)
```

**Latency budget management:**
- Feature retrieval: < 5ms (Redis / in-process cache)
- Model inference: < 20ms (optimized model: ONNX, TensorRT, quantization)
- Network overhead: < 10ms
- Total: < 50ms for most real-time systems

**Model optimization for serving:**
- **Quantization**: reduce float32 → int8 (4x smaller, faster, minimal accuracy loss)
- **ONNX export**: framework-agnostic optimized inference
- **TensorRT**: GPU-optimized inference for NVIDIA hardware
- **Distillation**: train a small student model to mimic a large teacher model

### Hybrid: Two-Stage Architecture

Common pattern for recommendation systems:

```
Stage 1 — Candidate Generation (fast, cheap model):
  All items (millions) → Embedding similarity / ANN search → Top 100 candidates

Stage 2 — Ranking (expensive, accurate model):
  Top 100 candidates → Full feature computation → Ranking model → Top 10 results
```

Stage 1 uses approximate nearest neighbor (FAISS, ScaNN) for speed.
Stage 2 uses a full neural network with rich features for quality.

---

## A/B Testing for Models

Never deploy a new model to 100% of traffic at once.

**Traffic splitting:**
```
New model request → A/B router:
  90% → Model A (current production)
  10% → Model B (new challenger)
```

**Metrics to track per variant:**
- Business metrics: CTR, conversion rate, revenue per session
- Latency metrics: p50, p99 inference latency
- Model metrics: prediction confidence distribution

**Statistical significance:** run the test long enough (minimum detectable effect × sample size calculation). Typical: 1-2 weeks, 95% confidence interval.

**Ramp-up strategy:**
- 5% → 20% → 50% → 100% with evaluation at each stage
- Automated rollback if metrics degrade beyond threshold

**Shadow mode (before A/B):** run the new model on all traffic but don't serve its results. Compare predictions offline. Catches data/logic bugs before exposing to users.

---

## Model Monitoring: Drift Detection

Models degrade silently. Monitoring is non-negotiable.

### Data Drift (Input drift)

The statistical distribution of input features changes compared to training data.

Example: a fraud model trained on pre-COVID purchase patterns receives COVID-era data (people buying more online, fewer physical stores). The model's input distribution has shifted.

**Detection:**
- Track feature distributions in production (mean, std, percentiles)
- Compare against training data distribution using statistical tests (KS test, PSI)
- Alert when drift score exceeds threshold

### Concept Drift (Label drift)

The relationship between inputs and the label changes.

Example: fraudster tactics change. The same device fingerprints that used to signal fraud now belong to legitimate users (fraudsters moved on).

**Harder to detect** because you don't have immediate ground truth labels.

**Techniques:**
- **Delayed feedback**: wait for chargebacks/returns to arrive (days/weeks lag) then compare predicted vs actual
- **Proxy metrics**: upstream signals that correlate with label quality (e.g., fraud dispute rate)
- **Periodic retraining**: retrain on fresh data every N days regardless of drift detection

### Prediction Drift

The distribution of model outputs changes.

Example: recommendation model used to predict CTR 0.05-0.15; now predicting 0.01-0.04. Something is wrong even without ground truth.

**Detection:** monitor prediction distribution (mean, variance, histogram) over time. Alert on significant shifts.

### Infrastructure Metrics

Beyond model quality — monitor the serving infrastructure:
- Inference latency (p50, p99, p999)
- Error rate (model server errors, feature store timeouts)
- Model server CPU/GPU utilization
- Request throughput vs. baseline

---

## Key Systems to Know

### Recommendation System

**Scale challenges:** Amazon recommends from 350M products. Netflix from 15K titles. The challenge isn't accuracy — it's latency at scale.

**Architecture:**
1. **Candidate generation**: user embedding × item embeddings → ANN search (FAISS) → 100-500 candidates
2. **Ranking**: per-candidate feature retrieval + ranking model → ordered list
3. **Filtering**: business rules (already purchased, out of stock, not eligible)

**Key design decisions:**
- Embedding freshness: how often to recompute user/item embeddings?
- Cold start: new user/item has no history → use content-based features, popularity-based fallback
- Diversity vs relevance: pure relevance ranking can result in filter bubbles (add diversity constraints)

### Fraud Detection

**Unique constraints:** must be real-time (decision at transaction time), high precision (false positives are costly — blocked legitimate users), labels are delayed (chargebacks take days).

**Architecture:**
- Real-time scoring: rule engine (fast, interpretable) + ML model (high recall)
- Rules: block known fraud patterns instantly
- ML model: catch novel patterns rules miss
- Human review queue: high-risk predictions go to analysts

**Features:** transaction amount, merchant category, velocity (N transactions in last 1h), device fingerprint, location consistency (are you transacting from two countries at once?)

**Class imbalance:** fraud is 0.1-1% of transactions. Techniques: SMOTE, class weights, threshold tuning, precision-recall optimization instead of accuracy.

### Search Ranking

**Two-stage:**
1. **Retrieval**: BM25 (keyword matching) or dense retrieval (BERT embeddings + ANN) → top 1000 documents
2. **Ranking**: learning-to-rank model (LambdaMART, LightGBM, neural ranker) → ordered results

**Learning to rank**: train on implicit feedback (clicks, dwell time, purchases). Challenge: position bias — items ranked #1 get more clicks regardless of quality. Correct with inverse propensity weighting.

**Evaluation metrics:**
- NDCG@K (Normalized Discounted Cumulative Gain): accounts for position
- MRR (Mean Reciprocal Rank): position of first relevant result
- Online: click-through rate, zero-result rate

---

## Interview Talking Points

**"How would you design a recommendation system for 100M users and 10M items?"**
- Two-stage: candidate generation (ANN on embeddings) + ranking (feature-rich model)
- Feature store for pre-computed user/item features with < 10ms retrieval
- Batch-update item embeddings nightly, user embeddings hourly
- A/B test model changes with CTR and revenue-per-session as primary metrics

**"How do you handle training-serving skew?"**
- Feature store with shared feature definitions for both training and serving
- Integration tests that compare offline and online feature values for the same entity
- Shadow mode: run new model, log predictions, compare offline

**"How do you detect that your model is degrading in production?"**
- Monitor prediction distribution drift (immediate)
- Monitor proxy metrics (near-immediate)
- Monitor delayed ground truth labels (hours to days delay)
- Set up automated alerts and retraining triggers on drift thresholds

**"What's the hardest part of ML systems?"**
- Data quality — garbage in, garbage out
- Training-serving skew — offline metrics don't match online
- Concept drift — the world changes but the model doesn't know
- Operational complexity — it's a software system AND a data system AND a model management system

---

## Quick Reference

| Component | Tools |
|-----------|-------|
| Feature store | Feast, Tecton, SageMaker Feature Store |
| Experiment tracking | MLflow, Weights & Biases |
| Model registry | MLflow, SageMaker, Vertex AI |
| Model serving | TF Serving, TorchServe, Triton, BentoML |
| ANN search | FAISS, ScaNN, Pinecone, Weaviate |
| Pipeline orchestration | Airflow, Kubeflow, Metaflow, Vertex Pipelines |
| Monitoring | Evidently, Arize, WhyLabs, custom Grafana dashboards |
| Distributed training | PyTorch DDP, Horovod, SageMaker Training |
