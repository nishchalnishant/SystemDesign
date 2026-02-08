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

```java
// Simple but has burst problem at window boundaries
public class FixedWindowRateLimiter {
    private int maxRequests;
    private int windowSeconds;
    private RedisClient redis;
    
    public FixedWindowRateLimiter(int maxRequests, int windowSeconds) {
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
        this.redis = new RedisClient();
    }
    
    public boolean allowRequest(String userId) {
        long currentWindow = System.currentTimeMillis() / 1000 / windowSeconds;
        String key = "rate_limit:" + userId + ":" + currentWindow;
        long count = redis.incr(key);
        
        if (count == 1) {
            redis.expire(key, windowSeconds);
        }
        
        return count <= maxRequests;
    }
}
```

**Problem**: Burst at window boundaries (20 requests in 1 second if 10 at end + 10 at start)

### 2. Sliding Window Log

```java
public class SlidingWindowLogRateLimiter {
    private int maxRequests;
    private int windowSeconds;
    private RedisClient redis;
    
    public boolean allowRequest(String userId) {
        String key = "rate_limit:" + userId;
        long now = System.currentTimeMillis() / 1000;
        long windowStart = now - windowSeconds;
        
        // Remove old requests
        redis.zremrangebyscore(key, 0, windowStart);
        
        // Count requests in window
        long count = redis.zcard(key);
        
        if (count < maxRequests) {
            redis.zadd(key, now, String.valueOf(now));
            redis.expire(key, windowSeconds);
            return true;
        }
        
        return false;
    }
}
```

**Pros**: Accurate  
**Cons**: Memory intensive (stores all request timestamps)

### 3. Token Bucket (Most Common)

```java
public class TokenBucketRateLimiter {
    private int maxRequests;
    private int windowSeconds;
    private RedisClient redis;
    
    public boolean allowRequest(String userId) {
        String key = "rate_limit:" + userId;
        Map<String, String> bucket = redis.hgetall(key);
        
        long now = System.currentTimeMillis() / 1000;
        double lastRefill = Double.parseDouble(bucket.getOrDefault("last_refill", String.valueOf(now)));
        double tokens = Double.parseDouble(bucket.getOrDefault("tokens", String.valueOf(maxRequests)));
        
        // Refill tokens
        double elapsed = now - lastRefill;
        double tokensToAdd = elapsed * ((double) maxRequests / windowSeconds);
        tokens = Math.min(maxRequests, tokens + tokensToAdd);
        
        if (tokens >= 1) {
            tokens -= 1;
            Map<String, String> values = new HashMap<>();
            values.put("tokens", String.valueOf(tokens));
            values.put("last_refill", String.valueOf(now));
            redis.hset(key, values);
            redis.expire(key, windowSeconds);
            return true;
        }
        
        return false;
    }
}
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
