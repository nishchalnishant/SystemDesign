> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a Social Network like LinkedIn/Facebook — modeling mutual connections (friend requests), posts, and news feed generation.
>
> **Key concepts:**
> - Core Entities: `User`, `Connection` (state machine: PENDING → ACCEPTED/REJECTED), `Post`, `Like`, `Comment`, `FeedGenerator`.
> - The problem: two users must both agree to connect (unlike one-way "follow"), and each user's feed must aggregate posts from all their connections, ordered chronologically or by engagement.
> - Patterns:
>   - Observer: notify connections when a user creates a post (feed invalidation / notification fan-out).
>   - Strategy: pluggable feed ranking (chronological vs engagement-based).
>   - State: `Connection` request lifecycle (PENDING, ACCEPTED, REJECTED).
> - Concurrency: Two simultaneous connection requests between the same pair of users must resolve to exactly one accepted connection — this is the classic "double friend request" race condition, solved with canonical ordering + locking or a unique-constraint upsert.
>
> **Key takeaway:** The hard part isn't the data model — it's the feed generation tradeoff (fan-out-on-write vs fan-out-on-read) and correctly modeling connection state transitions to prevent duplicate/racing requests.

---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, social-network, observer, strategy, state, concurrency]
---
# Design a Social Network (LinkedIn / Facebook)

> **Difficulty**: Hard  
> **Asked at**: Meta, LinkedIn, Google, Amazon  
> **Key Patterns**: Observer (feed/notification fan-out), Strategy (feed ranking), State (connection lifecycle)

---

## Understanding the Problem

Design a simplified social network where users send and accept mutual connection requests (like Facebook friends, not one-way Twitter/Instagram follows), create posts, and view a news feed aggregating posts from their connections. The system must handle the connection request lifecycle correctly and generate feeds efficiently as the connection graph grows.

---

## Clarifying Questions

**You**: "Should connections be mutual (both parties must accept, like Facebook) or one-way (follow, like Twitter/LinkedIn's 'follow' feature)?"  
**Interviewer**: "Mutual — model it like Facebook friend requests. A sends a request to B; B must accept before they're connected."

**You**: "How is the news feed generated — pulled on read, or pushed to each user's feed as posts are created?"  
**Interviewer**: "Start with pull-based (fan-out-on-read): aggregate posts from all connections at read time. We'll discuss push-based fan-out as a scaling follow-up."

**You**: "What post types do we support — text, images, video?"  
**Interviewer**: "Keep it simple: text content plus an optional media URL. Don't model a full media pipeline."

**You**: "Do posts have a visibility/privacy scope, like public vs connections-only?"  
**Interviewer**: "Yes — a post is visible either to all connections of the author, or publicly. No per-post custom audience lists."

**You**: "Should the feed be strictly reverse-chronological, or do you want engagement-based ranking?"  
**Interviewer**: "Chronological for the base implementation. Make ranking pluggable so we can swap in an engagement-based strategy later."

**You**: "Do we need to support likes and comments?"  
**Interviewer**: "Yes, both — they matter for the engagement-ranking follow-up."

**You**: "What about blocking, or un-friending?"  
**Interviewer**: "Out of scope for the core design. Mention it as an extension if time allows."

---

## Final Requirements

**In scope:**
1. Send, accept, and reject connection requests (mutual, state-machine driven)
2. Prevent duplicate/spam connection requests between the same pair of users
3. Create text/media posts with visibility scope (CONNECTIONS or PUBLIC)
4. Like and comment on posts
5. Generate a news feed for a user: posts from all accepted connections, chronologically ordered
6. Pluggable feed ranking strategy (chronological now, engagement-based later)
7. Thread-safe connection state transitions and feed reads under concurrent writes

**Out of scope:**
- Un-friending / blocking / privacy audience lists beyond CONNECTIONS vs PUBLIC
- Messaging / chat
- Media storage pipeline (URLs only, no upload/transcoding)
- Full-text search, ads, groups/pages

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|----------------|
| User | Identity; holds accepted connection set for fast feed lookups |
| Connection | Mutual relationship request between two users; owns PENDING/ACCEPTED/REJECTED state |
| Post | Author, content, media URL, timestamp, visibility |
| Like | User + Post, unique per (user, post) pair |
| Comment | User + Post + text + timestamp |
| FeedGenerator | Aggregates posts from a user's connections and ranks them via a pluggable strategy |

A `Connection` is requested by one `User` (requester) toward another (recipient) and transitions through states; only an `ACCEPTED` connection makes both users appear in each other's connection graph. `Post` belongs to exactly one `User` (author). `FeedGenerator` reads each connection's posts (fan-out-on-read) and merges them using a `FeedRankingStrategy`. `Like` and `Comment` reference a `Post` and contribute engagement signals consumed by ranking strategies.

---

## Class Design

### User

```
class User:
- user_id: str
- name: str
- connections: set[str]          # accepted connection user_ids, for O(1) lookup
- posts: list[Post]               # this user's own posts, newest first

+ add_connection(other_user_id: str) -> None
+ remove_connection(other_user_id: str) -> None
+ is_connected_to(other_user_id: str) -> bool
```

### Connection

```
enum ConnectionStatus: PENDING, ACCEPTED, REJECTED

class Connection:
- connection_id: str
- requester_id: str
- recipient_id: str
- status: ConnectionStatus
- created_at: datetime
- resolved_at: datetime | None

+ accept() -> None      # PENDING -> ACCEPTED, else raise IllegalStateException
+ reject() -> None      # PENDING -> REJECTED, else raise IllegalStateException
+ involves(user_id: str) -> bool
+ other_party(user_id: str) -> str
```

### Post

```
enum Visibility: CONNECTIONS, PUBLIC

class Post:
- post_id: str
- author_id: str
- content: str
- media_url: str | None
- visibility: Visibility
- created_at: datetime
- likes: set[str]                # user_ids who liked
- comments: list[Comment]

+ add_like(user_id: str) -> bool     # returns False if already liked
+ add_comment(comment: Comment) -> None
+ engagement_score() -> int          # likes + comments, weighted
```

### FeedGenerator

```
class FeedRankingStrategy:                 # abstract
+ rank(posts: list[Post]) -> list[Post]

class ChronologicalRanking(FeedRankingStrategy):
+ rank(posts) -> list[Post]                # sort by created_at desc

class EngagementRanking(FeedRankingStrategy):
+ rank(posts) -> list[Post]                # sort by engagement_score desc, tiebreak recency

class FeedGenerator:
- social_network: SocialNetwork
- ranking_strategy: FeedRankingStrategy

+ generate_feed(user_id: str, limit: int) -> list[Post]
```

---

## Implementation

### Core Method: sendConnectionRequest

**Core logic:**
1. Reject self-requests
2. Canonicalize the pair (sorted user-id pair) and lock on that key — prevents A→B and B→A races from creating two live requests
3. Check for an existing non-REJECTED connection between the pair; if PENDING or ACCEPTED, reject as duplicate
4. Create a new `Connection` in PENDING state

**Edge cases:**
- A already sent a request to B, A sends again — duplicate, reject
- A sends to B while B is simultaneously sending to A — must collapse to one connection, not two
- Users already connected — reject as duplicate

```java
public Connection sendConnectionRequest(String requesterId, String recipientId) {
    if (requesterId.equals(recipientId)) {
        throw new IllegalArgumentException("Cannot connect to self");
    }

    String pairKey = canonicalPairKey(requesterId, recipientId);
    Object lock = pairLocks.computeIfAbsent(pairKey, k -> new Object());

    synchronized (lock) {
        Connection existing = findConnectionBetween(requesterId, recipientId);
        if (existing != null && existing.getStatus() != ConnectionStatus.REJECTED) {
            throw new IllegalStateException(
                "Connection already " + existing.getStatus() + " between these users");
        }

        Connection connection = new Connection(
            UUID.randomUUID().toString(),
            requesterId,
            recipientId,
            ConnectionStatus.PENDING,
            LocalDateTime.now()
        );
        connectionsByPair.put(pairKey, connection);
        connectionsById.put(connection.getConnectionId(), connection);
        return connection;
    }
}

private String canonicalPairKey(String a, String b) {
    return a.compareTo(b) < 0 ? a + "#" + b : b + "#" + a;
}

private Connection findConnectionBetween(String userA, String userB) {
    return connectionsByPair.get(canonicalPairKey(userA, userB));
}
```

### Core Method: acceptConnectionRequest

**Core logic:**
1. Look up the `Connection` by id
2. Only the recipient may accept
3. Lock on the pair key; verify status is still PENDING (guards against a concurrent reject/accept race)
4. Transition to ACCEPTED, then update both `User.connections` sets

**Edge cases:**
- Non-existent connection id — raise NoSuchElementException
- Requester tries to accept their own outgoing request — reject
- Double-accept (already ACCEPTED) — raise IllegalStateException, no-op on user sets

```java
public void acceptConnectionRequest(String connectionId, String acceptingUserId) {
    Connection connection = connectionsById.get(connectionId);
    if (connection == null) {
        throw new NoSuchElementException("No such connection request: " + connectionId);
    }
    if (!connection.getRecipientId().equals(acceptingUserId)) {
        throw new IllegalArgumentException("Only the recipient can accept this request");
    }

    String pairKey = canonicalPairKey(connection.getRequesterId(), connection.getRecipientId());
    Object lock = pairLocks.computeIfAbsent(pairKey, k -> new Object());

    synchronized (lock) {
        connection.accept();  // throws IllegalStateException if not PENDING

        User requester = users.get(connection.getRequesterId());
        User recipient = users.get(connection.getRecipientId());
        requester.addConnection(recipient.getUserId());
        recipient.addConnection(requester.getUserId());
    }
}
```

### Core Method: createPost

**Core logic:**
1. Validate author exists
2. Construct `Post` with timestamp and default empty engagement sets
3. Prepend to the author's post list (newest-first) under a per-user lock
4. (Observer hook) notify feed/notification listeners — see Deep Dive

```java
public Post createPost(String authorId, String content, String mediaUrl, Visibility visibility) {
    User author = users.get(authorId);
    if (author == null) {
        throw new NoSuchElementException("No such user: " + authorId);
    }

    Post post = new Post(
        UUID.randomUUID().toString(),
        authorId,
        content,
        mediaUrl,
        visibility,
        LocalDateTime.now()
    );

    Object userLock = userLocks.computeIfAbsent(authorId, k -> new Object());
    synchronized (userLock) {
        author.getPosts().add(0, post);
    }
    postsById.put(post.getPostId(), post);

    for (PostObserver observer : postObservers) {
        observer.onPostCreated(post);
    }
    return post;
}
```

### Core Method: generateFeed (fan-out-on-read)

**Core logic:**
1. Resolve the viewer's accepted connection ids
2. For each connection, pull their recent posts (public or connections-visible)
3. Merge all candidate posts (k-way merge by timestamp, since each user's own post list is already sorted newest-first)
4. Apply the ranking strategy, then truncate to `limit`

**Edge cases:**
- User with zero connections — empty feed
- A connection's post list is empty — simply contributes nothing to the merge

```java
public List<Post> generateFeed(String userId, int limit) {
    User viewer = users.get(userId);
    if (viewer == null) {
        throw new NoSuchElementException("No such user: " + userId);
    }

    List<List<Post>> sources = new ArrayList<>();
    for (String connectionId : viewer.getConnections()) {
        User connectedUser = users.get(connectionId);
        if (connectedUser == null) continue;
        List<Post> visible = new ArrayList<>();
        for (Post post : connectedUser.getPosts()) {
            if (post.getVisibility() == Visibility.PUBLIC
                    || post.getVisibility() == Visibility.CONNECTIONS) {
                visible.add(post);
            }
        }
        sources.add(visible);  // already newest-first per user
    }

    List<Post> merged = kWayMergeByTimestampDesc(sources);
    List<Post> ranked = rankingStrategy.rank(merged);
    return ranked.size() > limit ? ranked.subList(0, limit) : ranked;
}

private List<Post> kWayMergeByTimestampDesc(List<List<Post>> sources) {
    PriorityQueue<int[]> heap = new PriorityQueue<>(
        (a, b) -> sources.get(b[0]).get(b[1]).getCreatedAt()
                       .compareTo(sources.get(a[0]).get(a[1]).getCreatedAt()));
    // heap holds [sourceIndex, itemIndex]; using a comparator over actual timestamps
    List<Post> result = new ArrayList<>();
    for (int i = 0; i < sources.size(); i++) {
        if (!sources.get(i).isEmpty()) heap.add(new int[]{i, 0});
    }
    while (!heap.isEmpty()) {
        int[] top = heap.poll();
        result.add(sources.get(top[0]).get(top[1]));
        int nextIdx = top[1] + 1;
        if (nextIdx < sources.get(top[0]).size()) {
            heap.add(new int[]{top[0], nextIdx});
        }
    }
    return result;
}
```

---

## Verification

**Scenario**: Alice and Bob connect, then each posts; Carol views Alice's feed.

1. `sendConnectionRequest("alice", "bob")` → Connection C1, status `PENDING`
2. `acceptConnectionRequest(C1.id, "bob")` → C1 status `ACCEPTED`; `alice.connections = {bob}`, `bob.connections = {alice}`
3. `createPost("bob", "Just shipped a feature!", null, CONNECTIONS)` at `10:00` → Post P1 prepended to bob's posts
4. `createPost("bob", "Follow-up thoughts", null, CONNECTIONS)` at `10:05` → Post P2 prepended; `bob.posts = [P2, P1]`
5. `generateFeed("alice", 10)`:
   - `alice.connections = {bob}` → one source list: `[P2, P1]`
   - k-way merge of a single source is itself: `[P2, P1]`
   - `ChronologicalRanking.rank([P2, P1])` → already sorted desc by `created_at` → `[P2, P1]`
   - Returned feed: **[P2 (10:05), P1 (10:00)]**
6. Carol, not connected to Alice or Bob, calls `generateFeed("carol", 10)` → `carol.connections = {}` → **empty feed**

**Duplicate-request check**: Bob calls `sendConnectionRequest("bob", "alice")` again after the accept in step 2 → `findConnectionBetween` finds C1 with status `ACCEPTED` → `IllegalStateException("Connection already ACCEPTED between these users")`.

---

## Deep Dive & Extensibility

### 1. "How would you scale feed generation for a user with 5000 connections?" (fan-out-on-write vs fan-out-on-read)

**Fan-out-on-read (current design)**: cheap writes (a post is just appended to the author's list), but reads are expensive — a user with 5000 connections triggers 5000 lookups and a large merge on every feed load. This is what the pull-based implementation above does.

**Fan-out-on-write**: at post-creation time, push the post into every connection's *precomputed feed* (a per-user feed inbox). Reads become O(1) — just read the precomputed list. Writes get expensive for high-degree nodes (a celebrity with 1M followers triggers 1M feed insertions per post).

The standard production answer is a **hybrid**: fan-out-on-write for most users (bounded connection count), fan-out-on-read (merged at request time) for celebrity/high-degree accounts, to avoid the "thundering herd" write amplification. Implemented via a `Strategy`/`Observer` combo — `PostObserver` decides per-author whether to push or leave for pull:

```java
interface PostObserver {
    void onPostCreated(Post post);
}

class FanOutOnWriteObserver implements PostObserver {
    private static final int CELEBRITY_THRESHOLD = 1000;
    private final Map<String, User> users;
    private final Map<String, Deque<Post>> precomputedFeeds; // per-user feed inbox

    @Override
    public void onPostCreated(Post post) {
        User author = users.get(post.getAuthorId());
        if (author.getConnections().size() > CELEBRITY_THRESHOLD) {
            return; // too many fan-out writes; let readers pull for this author
        }
        for (String connectionId : author.getConnections()) {
            Deque<Post> feed = precomputedFeeds.computeIfAbsent(
                connectionId, k -> new ArrayDeque<>());
            synchronized (feed) {
                feed.addFirst(post);
                if (feed.size() > 1000) feed.removeLast(); // cap feed cache size
            }
        }
    }
}
```

At read time, `generateFeed` merges the precomputed inbox with a live pull from any "celebrity" connections that were skipped during fan-out — best of both.

### 2. "How would you rank feed by engagement instead of chronological?"

Swap the injected `FeedRankingStrategy` — no changes needed to `FeedGenerator` or the merge logic (classic Strategy pattern / Open-Closed).

```java
class EngagementRanking implements FeedRankingStrategy {
    private static final double RECENCY_HALF_LIFE_HOURS = 24.0;

    @Override
    public List<Post> rank(List<Post> posts) {
        List<Post> copy = new ArrayList<>(posts);
        copy.sort((a, b) -> Double.compare(score(b), score(a)));
        return copy;
    }

    private double score(Post post) {
        long likeCount = post.getLikes().size();
        long commentCount = post.getComments().size();
        double engagement = likeCount * 1.0 + commentCount * 2.0; // comments weigh more
        double hoursOld = Duration.between(post.getCreatedAt(), LocalDateTime.now()).toMinutes() / 60.0;
        double recencyDecay = Math.pow(0.5, hoursOld / RECENCY_HALF_LIFE_HOURS);
        return engagement * recencyDecay + 0.01; // small floor so brand-new posts aren't buried
    }
}
```

`FeedGenerator` is constructed with whichever strategy is active (`new FeedGenerator(network, new EngagementRanking())`); this can even be per-request (A/B testing ranking algorithms) rather than a fixed field.

### 3. "How would you prevent duplicate/spam connection requests?"

Three layers, already partly present in the implementation:

1. **Canonical pair locking** — `sendConnectionRequest` locks on `canonicalPairKey(a, b)`, so A→B and B→A racing requests serialize on the same lock and the second one sees the first's PENDING/ACCEPTED connection.
2. **State check before create** — any existing non-REJECTED connection blocks a new request (see `sendConnectionRequest` above).
3. **Rate limiting for spam** — cap requests per user per time window, independent of the per-pair duplicate check:

```java
class RateLimitedConnectionService {
    private static final int MAX_REQUESTS_PER_HOUR = 50;
    private final Map<String, Deque<Instant>> requestTimestamps = new ConcurrentHashMap<>();
    private final SocialNetwork network;

    public Connection sendConnectionRequest(String requesterId, String recipientId) {
        Deque<Instant> timestamps = requestTimestamps.computeIfAbsent(
            requesterId, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            Instant cutoff = Instant.now().minus(Duration.ofHours(1));
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= MAX_REQUESTS_PER_HOUR) {
                throw new IllegalStateException("Rate limit exceeded for connection requests");
            }
            timestamps.addLast(Instant.now());
        }
        return network.sendConnectionRequest(requesterId, recipientId);
    }
}
```

A REJECTED connection intentionally still allows a fresh request (people reconsider), but repeated reject-then-reapply cycles are caught by the rate limiter rather than being permanently blocked.

---

## Interviewer Questions by Level

**Junior**: Define the `Connection` class and its states. Explain why a connection needs PENDING/ACCEPTED/REJECTED rather than a boolean. Sketch how `User.connections` is used to build a feed.

**Mid-level**: Implement `sendConnectionRequest` and `acceptConnectionRequest` with correct state transitions. Explain why locking on a canonical pair key (not per-user) is necessary. Implement chronological feed generation via k-way merge.

**Senior**: Discuss fan-out-on-write vs fan-out-on-read tradeoffs and design the hybrid approach for celebrity accounts. Explain how ranking strategy swaps without touching feed-generation code (Strategy + Open/Closed). Identify the double-friend-request race and prove the fix is correct under concurrency, not just "seems to work."

---

## Common Interview Questions

- Q: Why can't `Connection` just be a boolean "is_friends" flag on each `User`? A: You lose the PENDING/REJECTED states and can't represent an outstanding request, or distinguish "never requested" from "rejected." A state machine makes invalid transitions (e.g., accepting an already-rejected request) explicit errors instead of silent bugs.
- Q: Why lock on a canonical pair key instead of locking each `User` individually? A: Locking per-user risks deadlock if two threads lock A then B and B then A in opposite order. A single canonical key (e.g., sorted `"alice#bob"`) for the pair guarantees only one lock object per relationship, so there is nothing to deadlock on.
- Q: What's the complexity of `generateFeed` for a user with N connections and M posts each? A: The k-way merge is O(total_posts × log N) using a heap of size N. Without the heap (naive concatenate + sort) it's O(total_posts × log(total_posts)) — the heap approach is better only if you need early termination (e.g., top-K without materializing everything).
- Q: How would you support un-friending? A: Add a `TERMINATED` (or reuse `REJECTED`) status reachable from `ACCEPTED`, remove both users from each other's `connections` set under the same pair lock used for accept, and consider whether historical interactions (likes/comments) should be retained.
- Q: Why use the Observer pattern for post creation instead of calling fan-out logic directly in `createPost`? A: Decouples `SocialNetwork` from feed/notification concerns — new listeners (push notifications, analytics, search indexing) register without modifying `createPost`. This is Open/Closed applied to side effects of an event.
- Q: How do you avoid returning private/blocked users' posts in a PUBLIC-visibility feed context? A: Visibility is enforced at read time in `generateFeed`/wherever posts are surfaced — CONNECTIONS-visibility posts are only pulled from the viewer's own connection list to begin with, and PUBLIC posts would additionally need a separate public-timeline path that doesn't rely on the connection graph at all.
- Q: How would you test that accept is idempotent-safe under concurrency? A: Two threads both call `acceptConnectionRequest` for the same connection id concurrently; assert exactly one succeeds, the other raises `IllegalStateException` (already ACCEPTED), and both users' `connections` sets contain the other exactly once (not duplicated).

---

## Concurrency Test Harness

```java
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

// --- Minimal stubs to make the harness self-contained ---

enum ConnectionStatus { PENDING, ACCEPTED, REJECTED }

class Connection {
    private final String connectionId;
    private final String requesterId;
    private final String recipientId;
    private volatile ConnectionStatus status;
    private final LocalDateTime createdAt;

    public Connection(String connectionId, String requesterId, String recipientId,
                       ConnectionStatus status, LocalDateTime createdAt) {
        this.connectionId = connectionId;
        this.requesterId = requesterId;
        this.recipientId = recipientId;
        this.status = status;
        this.createdAt = createdAt;
    }

    public void accept() {
        if (status != ConnectionStatus.PENDING) {
            throw new IllegalStateException("Cannot accept a " + status + " connection");
        }
        status = ConnectionStatus.ACCEPTED;
    }

    public String getConnectionId() { return connectionId; }
    public String getRequesterId() { return requesterId; }
    public String getRecipientId() { return recipientId; }
    public ConnectionStatus getStatus() { return status; }
}

class User {
    private final String userId;
    private final Set<String> connections = ConcurrentHashMap.newKeySet();

    public User(String userId) { this.userId = userId; }

    public void addConnection(String otherUserId) { connections.add(otherUserId); }
    public String getUserId() { return userId; }
    public Set<String> getConnections() { return connections; }
}

class SocialNetwork {
    final Map<String, User> users = new ConcurrentHashMap<>();
    final Map<String, Connection> connectionsById = new ConcurrentHashMap<>();
    final Map<String, Connection> connectionsByPair = new ConcurrentHashMap<>();
    final Map<String, Object> pairLocks = new ConcurrentHashMap<>();

    public void registerUser(String userId) {
        users.put(userId, new User(userId));
    }

    private String canonicalPairKey(String a, String b) {
        return a.compareTo(b) < 0 ? a + "#" + b : b + "#" + a;
    }

    private Connection findConnectionBetween(String userA, String userB) {
        return connectionsByPair.get(canonicalPairKey(userA, userB));
    }

    public Connection sendConnectionRequest(String requesterId, String recipientId) {
        if (requesterId.equals(recipientId)) {
            throw new IllegalArgumentException("Cannot connect to self");
        }
        String pairKey = canonicalPairKey(requesterId, recipientId);
        Object lock = pairLocks.computeIfAbsent(pairKey, k -> new Object());

        synchronized (lock) {
            Connection existing = findConnectionBetween(requesterId, recipientId);
            if (existing != null && existing.getStatus() != ConnectionStatus.REJECTED) {
                throw new IllegalStateException(
                    "Connection already " + existing.getStatus() + " between these users");
            }
            Connection connection = new Connection(
                UUID.randomUUID().toString(), requesterId, recipientId,
                ConnectionStatus.PENDING, LocalDateTime.now());
            connectionsByPair.put(pairKey, connection);
            connectionsById.put(connection.getConnectionId(), connection);
            return connection;
        }
    }

    public void acceptConnectionRequest(String connectionId, String acceptingUserId) {
        Connection connection = connectionsById.get(connectionId);
        if (connection == null) {
            throw new NoSuchElementException("No such connection request: " + connectionId);
        }
        if (!connection.getRecipientId().equals(acceptingUserId)) {
            throw new IllegalArgumentException("Only the recipient can accept this request");
        }
        String pairKey = canonicalPairKey(connection.getRequesterId(), connection.getRecipientId());
        Object lock = pairLocks.computeIfAbsent(pairKey, k -> new Object());

        synchronized (lock) {
            connection.accept();
            User requester = users.get(connection.getRequesterId());
            User recipient = users.get(connection.getRecipientId());
            requester.addConnection(recipient.getUserId());
            recipient.addConnection(requester.getUserId());
        }
    }
}

class SocialNetworkConcurrencyTest {

    // ─────────────────────────────────────────────────────────────
    // TEST 1: Concurrent connection requests between the same two
    // users (A->B and B->A fired simultaneously) must resolve to
    // exactly one live (non-REJECTED) connection between the pair.
    // ─────────────────────────────────────────────────────────────
    static void testNoDuplicateConnectionUnderRace() throws InterruptedException {
        SocialNetwork network = new SocialNetwork();
        network.registerUser("alice");
        network.registerUser("bob");

        List<Connection> succeeded = Collections.synchronizedList(new ArrayList<>());
        List<Exception> failed = Collections.synchronizedList(new ArrayList<>());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);

        Runnable requestAtoB = () -> {
            try {
                startGate.await();
                succeeded.add(network.sendConnectionRequest("alice", "bob"));
            } catch (Exception e) {
                failed.add(e);
            }
        };
        Runnable requestBtoA = () -> {
            try {
                startGate.await();
                succeeded.add(network.sendConnectionRequest("bob", "alice"));
            } catch (Exception e) {
                failed.add(e);
            }
        };

        Future<?> f1 = pool.submit(requestAtoB);
        Future<?> f2 = pool.submit(requestBtoA);
        startGate.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        if (succeeded.size() != 1) {
            throw new AssertionError("Expected exactly 1 successful request, got " + succeeded.size());
        }
        if (failed.size() != 1) {
            throw new AssertionError("Expected exactly 1 rejected duplicate, got " + failed.size());
        }
        if (network.connectionsById.size() != 1) {
            throw new AssertionError("Expected exactly 1 stored connection, got " + network.connectionsById.size());
        }

        System.out.println("PASS: testNoDuplicateConnectionUnderRace");
    }

    // ─────────────────────────────────────────────────────────────
    // TEST 2: Concurrent accept attempts on the same connection —
    // exactly one thread must succeed, the other must see
    // IllegalStateException, and both users end up connected exactly once.
    // ─────────────────────────────────────────────────────────────
    static void testConcurrentAcceptIsIdempotentSafe() throws InterruptedException {
        SocialNetwork network = new SocialNetwork();
        network.registerUser("alice");
        network.registerUser("bob");
        Connection connection = network.sendConnectionRequest("alice", "bob");

        AtomicIntegerLike successCount = new AtomicIntegerLike();
        AtomicIntegerLike failureCount = new AtomicIntegerLike();

        int threadCount = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(pool.submit(() -> {
                try {
                    startGate.await();
                    network.acceptConnectionRequest(connection.getConnectionId(), "bob");
                    successCount.increment();
                } catch (IllegalStateException e) {
                    failureCount.increment();
                } catch (InterruptedException ignored) {
                }
            }));
        }
        startGate.countDown();
        for (Future<?> f : futures) {
            try { f.get(5, TimeUnit.SECONDS); } catch (Exception ignored) { }
        }
        pool.shutdown();

        if (successCount.get() != 1) {
            throw new AssertionError("Expected exactly 1 successful accept, got " + successCount.get());
        }
        if (failureCount.get() != threadCount - 1) {
            throw new AssertionError("Expected " + (threadCount - 1) + " failed accepts, got " + failureCount.get());
        }

        User alice = network.users.get("alice");
        User bob = network.users.get("bob");
        if (!alice.getConnections().contains("bob") || !bob.getConnections().contains("alice")) {
            throw new AssertionError("Users not mutually connected after accept");
        }
        if (alice.getConnections().size() != 1 || bob.getConnections().size() != 1) {
            throw new AssertionError("Connection set should contain exactly one entry each");
        }

        System.out.println("PASS: testConcurrentAcceptIsIdempotentSafe");
    }

    // ─────────────────────────────────────────────────────────────
    // TEST 3: Concurrent feed reads while posts are being written
    // must not throw ConcurrentModificationException or corrupt data.
    // Uses a simple per-user post list guarded by a lock, simulating
    // the createPost/generateFeed pattern from the main design.
    // ─────────────────────────────────────────────────────────────
    static void testConcurrentFeedReadsWhileWriting() throws InterruptedException {
        SocialNetwork network = new SocialNetwork();
        network.registerUser("alice");
        network.registerUser("bob");
        Connection connection = network.sendConnectionRequest("alice", "bob");
        network.acceptConnectionRequest(connection.getConnectionId(), "bob");

        Map<String, List<String>> postsByUser = new ConcurrentHashMap<>();
        postsByUser.put("bob", Collections.synchronizedList(new ArrayList<>()));
        Object bobPostsLock = new Object();

        AtomicIntegerLike readErrors = new AtomicIntegerLike();
        int writerCount = 50;
        int readerCount = 50;

        ExecutorService pool = Executors.newFixedThreadPool(writerCount + readerCount);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < writerCount; i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                synchronized (bobPostsLock) {
                    postsByUser.get("bob").add(0, "post-" + idx);
                }
            }));
        }
        for (int i = 0; i < readerCount; i++) {
            futures.add(pool.submit(() -> {
                try {
                    List<String> snapshot;
                    synchronized (bobPostsLock) {
                        snapshot = new ArrayList<>(postsByUser.get("bob"));
                    }
                    // simulate feed merge/read work
                    int size = snapshot.size();
                    if (size < 0) throw new IllegalStateException("impossible");
                } catch (Exception e) {
                    readErrors.increment();
                }
            }));
        }

        for (Future<?> f : futures) {
            try { f.get(5, TimeUnit.SECONDS); } catch (Exception e) { readErrors.increment(); }
        }
        pool.shutdown();

        if (readErrors.get() != 0) {
            throw new AssertionError("Expected 0 read errors, got " + readErrors.get());
        }
        if (postsByUser.get("bob").size() != writerCount) {
            throw new AssertionError("Expected " + writerCount + " posts, got " + postsByUser.get("bob").size());
        }

        System.out.println("PASS: testConcurrentFeedReadsWhileWriting");
    }

    // Minimal thread-safe counter (avoids importing java.util.concurrent.atomic.AtomicInteger
    // just to keep the diff self-documenting; behaves identically).
    static class AtomicIntegerLike {
        private int value = 0;
        synchronized void increment() { value++; }
        synchronized int get() { return value; }
    }

    public static void main(String[] args) throws InterruptedException {
        testNoDuplicateConnectionUnderRace();
        testConcurrentAcceptIsIdempotentSafe();
        testConcurrentFeedReadsWhileWriting();
        System.out.println("All concurrency tests passed.");
    }
}
```

**What each test verifies:**
- `testNoDuplicateConnectionUnderRace`: The canonical-pair-key lock in `sendConnectionRequest` ensures that simultaneous A→B and B→A requests serialize; the second request to acquire the lock sees the first's PENDING connection and is rejected as a duplicate, leaving exactly one stored `Connection`.
- `testConcurrentAcceptIsIdempotentSafe`: The pair lock in `acceptConnectionRequest` makes the "check status is PENDING, then transition" sequence atomic. Without it, multiple threads could all read `PENDING` before any of them writes `ACCEPTED`, double-adding both users to each other's connection sets.
- `testConcurrentFeedReadsWhileWriting`: Readers taking a locked snapshot of a user's post list before merging/ranking never observe a torn or concurrently-modified list, and writers never lose an insert — final post count matches writer count exactly.

---

## Related

**Patterns applied here**

- [Observer Pattern](../../03-design-patterns/03-behavioral/observer-pattern.md)
- [Strategy Pattern](../../03-design-patterns/03-behavioral/strategy-pattern.md)

**SOLID focus**: [Single Responsibility](../../02-solid-principles/01-single-responsibility.md)

**Concurrency**: [Concurrency Patterns](../../04-concurrency/concurrency-patterns.md)

**Practice next**

- [Design Pub-Sub](../03-domain-specific/23-design-pub-sub.md)
- [Design Notification System](../02-frequent-problems/16-design-notification-system.md)

Both reuse the observer/fan-out shape used for feed and notification delivery.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../01-oop-fundamentals/uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
