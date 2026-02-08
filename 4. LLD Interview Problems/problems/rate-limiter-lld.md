# Design Rate Limiter (Low Level)

> **Difficulty**: Medium  
> **Topics**: Concurrency, Design Patterns (Strategy), Token Bucket Algorithm  
> **Context**: Designing the internal class implementation (Thread-Safe), not the distributed system.

## Problem Statement

Implement a thread-safe Rate Limiter library.
- **Rules**: Limit requests per UserID (e.g., 10 req/sec).
- **Behavior**: Return `True` (Allow) or `False` (Drop).
- **Constraint**: Must handle multi-threaded environment.

## Algorithm: Token Bucket

We use **Token Bucket** (Lazy Refill).
- **Why?**: Memory efficient (store just timestamps), handles bursts better than sliding log.
- **Lazy Refill**: Don't use a background timer. On each request, calculate `tokens_to_add = (now - last_refill) * rate`.

## Implementation

```python
import time
import threading

class TokenBucket:
    def __init__(self, capacity, refill_rate):
        self.capacity = capacity          # Max burst
        self.tokens = capacity            # Current tokens
        self.refill_rate = refill_rate    # Tokens/sec
        self.last_refill = time.time()
        self.lock = threading.Lock()      # Thread safety

    def allow_request(self, tokens_needed=1):
        with self.lock:
            now = time.time()
            # 1. Refill
            elapsed = now - self.last_refill
            added = elapsed * self.refill_rate
            self.tokens = min(self.capacity, self.tokens + added)
            self.last_refill = now
            
            # 2. Consume
            if self.tokens >= tokens_needed:
                self.tokens -= tokens_needed
                return True
            return False
```

## Strategy Pattern for Extensibility

To support multiple algorithms (Leaky Bucket, Sliding Window), use Strategy Pattern.

```python
class RateLimiterStrategy(ABC):
    @abstractmethod
    def allow_request(self, user_id): pass

class TokenBucketStrategy(RateLimiterStrategy):
    def __init__(self):
        self.user_buckets = {} # UserID -> Bucket
        self.lock = threading.Lock()

    def allow_request(self, user_id):
        with self.lock:
            if user_id not in self.user_buckets:
                self.user_buckets[user_id] = TokenBucket(10, 1)
            bucket = self.user_buckets[user_id]
        
        return bucket.allow_request()
```

## Interview Q&A

**Q: "How to scale to distributed system?"**
- A: "This code works for single node. For distributed, replace local `TokenBucket` with Redis + Lua Script. Lua ensures atomicity of `get -> calculate -> set` operations."

**Q: "Memory Usage?"**
- A: "Cleanup old users. Use a background thread to remove keys in `user_buckets` that haven't been accessed for > 1 hour."
