# Design Rate Limiter

> **Difficulty**: Easy  
> **Topics**: Token Bucket, Sliding Window, Distributed Systems  
> **Time**: 45 minutes  
> **Companies**: Google, Amazon, Stripe, Cloudflare

## Problem Statement

Design a rate limiter that restricts the number of requests a user can make to an API within a time window.

**Examples:**
- Maximum 10 requests per second per user
- Maximum 1000 requests per hour per API key
- Maximum 100,000 requests per day per IP address

## Requirements

### Functional
- Limit requests per user/API key/IP
- Return 429 (Too Many Requests) when limit exceeded
- Support different time windows (second, minute, hour, day)

### Non-Functional
- Low latency (<1ms per check)
- Highly available (99.99%)
- Distributed (works across multiple servers)
- Memory efficient

## Algorithms

### 1. Fixed Window Counter

```python
# Simple but has burst problem at window boundaries
class FixedWindowRateLimiter:
    def __init__(self, max_requests, window_seconds):
        self.max_requests = max_requests
        self.window_seconds = window_seconds
        self.redis = Redis()
    
    def allow_request(self, user_id):
        key = f"rate_limit:{user_id}:{int(time.time() / self.window_seconds)}"
        count = self.redis.incr(key)
        
        if count == 1:
            self.redis.expire(key, self.window_seconds)
        
        return count <= self.max_requests
```

**Problem**: Burst at window boundaries (20 requests in 1 second if 10 at end + 10 at start)

### 2. Sliding Window Log

```python
class SlidingWindowLogRateLimiter:
    def allow_request(self, user_id):
        key = f"rate_limit:{user_id}"
        now = time.time()
        window_start = now - self.window_seconds
        
        # Remove old requests
        self.redis.zremrangebyscore(key, 0, window_start)
        
        # Count requests in window
        count = self.redis.zcard(key)
        
        if count < self.max_requests:
            self.redis.zadd(key, {str(now): now})
            self.redis.expire(key, self.window_seconds)
            return True
        
        return False
```

**Pros**: Accurate  
**Cons**: Memory intensive (stores all request timestamps)

### 3. Token Bucket (Most Common)

```python
class TokenBucketRateLimiter:
    def allow_request(self, user_id):
        key = f"rate_limit:{user_id}"
        bucket = self.redis.hgetall(key)
        
        now = time.time()
        last_refill = float(bucket.get('last_refill', now))
        tokens = float(bucket.get('tokens', self.max_requests))
        
        # Refill tokens
        elapsed = now - last_refill
        tokens_to_add = elapsed * (self.max_requests / self.window_seconds)
        tokens = min(self.max_requests, tokens + tokens_to_add)
        
        if tokens >= 1:
            tokens -= 1
            self.redis.hset(key, mapping={
                'tokens': tokens,
                'last_refill': now
            })
            self.redis.expire(key, self.window_seconds)
            return True
        
        return False
```

**Pros**: Smooth rate limiting, allows bursts  
**Cons**: Slightly complex

## Architecture

```
Client Request
     ↓
Load Balancer
     ↓
API Gateway (Rate Limiter Check)
     ↓
Redis Cluster (Token Bucket State)
     ↓
Backend Service (if allowed)
```

## API Design

```http
GET /api/resource
Headers:
  X-API-Key: abc123

Response (Success): 200 OK
Headers:
  X-RateLimit-Limit: 1000
  X-RateLimit-Remaining: 999
  X-RateLimit-Reset: 1675843200

Response (Rate Limited): 429 Too Many Requests
Headers:
  X-RateLimit-Limit: 1000
  X-RateLimit-Remaining: 0
  X-RateLimit-Reset: 1675843200
  Retry-After: 3600
```

## Distributed Rate Limiting

**Challenge**: Multiple servers need shared state

**Solution**: Redis Cluster
```
Server 1 → Redis Node 1 (hash slot 0-5460)
Server 2 → Redis Node 2 (hash slot 5461-10922)
Server 3 → Redis Node 3 (hash slot 10923-16383)
```

## Interview Tips

**Common Questions:**
- Q: "How would you rate limit across multiple datacenters?"
- A: Global Redis cluster OR each datacenter has limits (80% of global)

**Decision Matrix:**
| Algorithm | Accuracy | Memory | Use Case |
|-----------|----------|---------|----------|
| Fixed Window | Low | Low | Simple, non-critical |
| Sliding Log | High | High | Financial APIs |
| Token Bucket | Medium | Medium | Most APIs (recommended) |
| Leaky Bucket | High | Medium | Traffic shaping |
