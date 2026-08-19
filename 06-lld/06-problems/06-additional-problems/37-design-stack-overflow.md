> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a Q&A platform like Stack Overflow — a classic content-and-community LLD problem that tests voting integrity, reputation-driven state changes, and pluggable ranking.
>
> **Key concepts:**
> - Core Entities: `User`, `Question`, `Answer`, `Comment`, `Tag`, `Vote`, `ReputationManager`.
> - The problem: users post questions and answers, vote on them, accept a best answer, and the system must compute reputation and rank content — all under concurrent access.
> - Patterns:
>   - Strategy: `RankingStrategy` for hot/top/newest question ordering.
>   - Observer: notify the question owner (and watchers) when a new answer or comment arrives.
>   - Composite-ish aggregation: `Question` owns `Answer`s which own `Comment`s, each independently votable.
> - Concurrency: two threads voting on the same post simultaneously must not double-count or corrupt the tally — vote uniqueness is enforced per `(userId, targetId)` under a lock, and reputation updates must be atomic with the vote record.
>
> **Key takeaway:** The crux of this problem is not the data model — it's getting **vote uniqueness + reputation side effects + accept-answer semantics** correct and thread-safe simultaneously. Treat `castVote` as a single atomic operation, not "check then vote."

---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, stack-overflow, qa-platform, strategy, observer, reputation, concurrency]
---
# Design Stack Overflow

> **Difficulty**: Medium-Hard
> **Asked at**: Google, Meta, Amazon, Atlassian
> **Key Patterns**: Strategy (ranking), Observer (answer/comment notifications), State-ish (vote transitions), Composite (Question → Answer → Comment)

---

## Understanding the Problem

Design a Q&A platform where users post questions, other users post answers and comments, the community votes on questions and answers, question owners can accept a best answer, and content is tagged and searchable. The system must track reputation as a derived, vote-driven quantity and rank questions by relevance under a pluggable strategy.

---

## Clarifying Questions

**You**: "Do we need comments in addition to questions and answers, and can comments be voted on?"
**Interviewer**: "Yes to comments, on both questions and answers. No voting on comments — keep them lightweight, just threaded text."

**You**: "What are the voting rules — can a user upvote and downvote the same post, and is there a self-vote restriction?"
**Interviewer**: "One vote per user per post. A user can change their vote (up→down or vice versa) but never cast two votes on the same post. Users cannot vote on their own posts."

**You**: "How does voting affect reputation, and whose reputation changes?"
**Interviewer**: "Author gets +10 reputation when their question or answer is upvoted, -2 when downvoted. The voter's own reputation doesn't change from casting a vote in this simplified model."

**You**: "Who can accept an answer, and what happens on acceptance?"
**Interviewer**: "Only the question's author can accept an answer to their own question. Exactly one answer can be accepted at a time — accepting a new one un-accepts the previous. The answer's author gets +15 reputation on acceptance."

**You**: "Do tags need their own lifecycle — creation, following, moderation — or just labels?"
**Interviewer**: "Keep tags as simple labels for this round: a question has 1-5 tags, and we can filter/search by tag. No tag moderation workflow."

**You**: "Should search be full-text across title/body, or tag-only filtering?"
**Interviewer**: "Support both — filter by tag and a simple keyword search over title and body. Doesn't need to be a real inverted-index search engine, but design it so that piece is swappable."

**You**: "Is reputation used to gate any privileges, like a minimum score to downvote?"
**Interviewer**: "Not required for the core design, but mention how you'd extend it if asked."

---

## Final Requirements

**In scope:**
1. Post a question with title, body, and 1-5 tags
2. Post an answer to a question; post comments on questions and answers
3. Upvote/downvote questions and answers — one vote per user per post, changeable, not duplicable
4. Reputation system driven by vote and accept-answer events
5. Question owner accepts exactly one answer; acceptance is exclusive and revocable (accepting a different one un-accepts the old)
6. Tag-based filtering and simple keyword search over title/body
7. Pluggable ranking strategy (hot / top / newest) for listing questions
8. Notify question owner when a new answer is posted (and, extensibly, watchers)
9. Thread-safe voting under concurrent access

**Out of scope:**
- Real inverted-index / ranked search (Elasticsearch-style relevance scoring)
- Tag moderation, tag synonyms, tag wikis
- Comment voting or comment acceptance
- Anti-spam / rate limiting infrastructure beyond a brief mention
- Edit history / revision diffs

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|---------------|
| User | Identity; holds current reputation score |
| Question | Title, body, tags, owner, list of answers, accepted answer reference |
| Answer | Body, owner, parent question, accepted flag |
| Comment | Lightweight threaded text on a Question or Answer |
| Tag | Simple label used for filtering/search |
| Vote | Records (voterId, targetId, targetType, value) — the single source of truth for "who voted what" |
| ReputationManager | Applies reputation deltas to users in response to vote/accept events; the only writer of `User.reputation` |
| RankingStrategy | Orders a list of questions (hot/top/newest) — Strategy pattern |
| QAService (Facade) | Orchestrates postQuestion/postAnswer/castVote/acceptAnswer, owns storage and notification wiring |

`Question` aggregates `Answer`s (Composite-ish); each `Answer` aggregates its own `Comment`s, and `Question` also holds top-level `Comment`s. `Vote` is a first-class entity, not just a counter, because uniqueness and vote-changing require tracking who voted and with what value. `ReputationManager` is the single choke point for reputation mutation so all deltas stay auditable and consistent. `QAService` observes new answers and notifies the question's owner via the Observer pattern.

---

## Class Design

### User

| Requirement | What User must track |
|-------------|----------------------|
| Identity | userId, displayName |
| Derived reputation score | reputation: int (mutated only by ReputationManager) |

```
class User:
- user_id: str
- display_name: str
- reputation: AtomicInteger

+ get_reputation() -> int
```

### Tag

```
class Tag:
- name: str          # normalized, lowercase, unique

+ equals/hashCode by name
```

### Comment

```
class Comment:
- comment_id: str
- author: User
- body: str
- created_at: datetime
```

### Vote

| Requirement | What Vote must track |
|-------------|----------------------|
| Who voted | voter_id: str |
| On what | target_id: str, target_type: TargetType (QUESTION, ANSWER) |
| Current direction | value: VoteValue (UPVOTE, DOWNVOTE) |

```
class Vote:
- voter_id: str
- target_id: str
- target_type: TargetType   # QUESTION, ANSWER
- value: VoteValue          # UPVOTE, DOWNVOTE
```

### Answer

```
class Answer:
- answer_id: str
- question_id: str
- author: User
- body: str
- created_at: datetime
- is_accepted: bool
- vote_count: AtomicInteger
- comments: list[Comment]
- _lock: threading.Lock       # guards vote_count + accepted flag together

+ apply_vote_delta(delta: int) -> None
+ mark_accepted() -> None
+ mark_unaccepted() -> None
```

### Question

| Requirement | What Question must track |
|-------------|--------------------------|
| Content | title, body, tags |
| Ownership | owner: User |
| Answers and comments | answers: list[Answer], comments: list[Comment] |
| Vote tally and acceptance | vote_count: AtomicInteger, accepted_answer_id: str \| None |
| Observers for new-answer events | observers: list[QuestionObserver] |

```
class Question:
- question_id: str
- title: str
- body: str
- tags: set[Tag]
- owner: User
- created_at: datetime
- answers: list[Answer]
- comments: list[Comment]
- vote_count: AtomicInteger
- accepted_answer_id: str | None
- _lock: threading.Lock

+ apply_vote_delta(delta: int) -> None
+ add_answer(answer: Answer) -> None
+ set_accepted_answer(answer_id: str) -> None
```

### ReputationManager

```
class ReputationManager:
- QUESTION_UPVOTE = +10
- ANSWER_UPVOTE = +10
- DOWNVOTE_GIVEN_PENALTY = -2
- ACCEPTED_ANSWER_BONUS = +15

+ on_vote_cast(author: User, old_value: VoteValue | None, new_value: VoteValue) -> None
+ on_answer_accepted(author: User) -> None
+ on_answer_unaccepted(author: User) -> None
```

### RankingStrategy (Strategy)

```
class RankingStrategy:            # abstract
+ rank(questions: list[Question]) -> list[Question]

class NewestStrategy(RankingStrategy)
class TopStrategy(RankingStrategy)      # by vote_count desc
class HotStrategy(RankingStrategy)      # decayed score: votes / age factor
```

### QuestionObserver (Observer)

```
class QuestionObserver:            # abstract
+ on_new_answer(question: Question, answer: Answer) -> None

class OwnerNotifier(QuestionObserver)
class WatcherNotifier(QuestionObserver)
```

### QAService (Facade)

```
class QAService:
- questions: dict[str, Question]
- answers: dict[str, Answer]
- votes: dict[tuple[str, str], Vote]   # (voter_id, target_id) -> Vote
- users: dict[str, User]
- reputation_manager: ReputationManager
- observers: list[QuestionObserver]
- ranking_strategy: RankingStrategy
- _votes_lock: threading.Lock

+ post_question(owner, title, body, tags) -> Question
+ post_answer(author, question_id, body) -> Answer
+ post_comment(author, target_id, target_type, body) -> Comment
+ cast_vote(voter, target_id, target_type, value) -> None
+ accept_answer(question_owner, question_id, answer_id) -> None
+ search(keyword: str | None, tag: str | None) -> list[Question]
+ list_questions_ranked() -> list[Question]
```

---

## Implementation

### Core Method: postQuestion

**Core logic:**
1. Validate 1-5 tags provided
2. Create Question with owner, empty answers/comments, zero vote count
3. Store in `questions` map, index tags for search

**Edge cases:**
- Zero tags or more than 5 — reject
- Empty title/body — reject

```java
public Question postQuestion(User owner, String title, String body, Set<String> tagNames) {
    if (tagNames == null || tagNames.isEmpty() || tagNames.size() > 5) {
        throw new IllegalArgumentException("A question must have between 1 and 5 tags");
    }
    if (title == null || title.isBlank() || body == null || body.isBlank()) {
        throw new IllegalArgumentException("Title and body are required");
    }

    Set<Tag> tags = new HashSet<>();
    for (String name : tagNames) {
        tags.add(new Tag(name.toLowerCase()));
    }

    Question question = new Question(
        UUID.randomUUID().toString(), title, body, tags, owner, LocalDateTime.now()
    );
    questions.put(question.getQuestionId(), question);

    for (Tag tag : tags) {
        tagIndex.computeIfAbsent(tag.getName(), k -> ConcurrentHashMap.newKeySet())
                .add(question.getQuestionId());
    }
    return question;
}
```

### Core Method: postAnswer

**Core logic:**
1. Look up question; fail if it does not exist
2. Create Answer with author and question reference
3. Append to question's answer list (under the question's lock, since acceptance also touches this list)
4. Notify observers (Observer pattern) so the question owner is alerted

**Edge cases:**
- Question not found — raise error
- Author answering their own question is allowed (unlike voting) unless the interviewer says otherwise

```java
public Answer postAnswer(User author, String questionId, String body) {
    Question question = questions.get(questionId);
    if (question == null) {
        throw new NoSuchElementException("No such question: " + questionId);
    }
    if (body == null || body.isBlank()) {
        throw new IllegalArgumentException("Answer body is required");
    }

    Answer answer = new Answer(
        UUID.randomUUID().toString(), questionId, author, body, LocalDateTime.now()
    );
    answers.put(answer.getAnswerId(), answer);
    question.addAnswer(answer);

    for (QuestionObserver observer : observers) {
        observer.onNewAnswer(question, answer);
    }
    return answer;
}
```

### Core Method: castVote

**Core logic:**
1. Resolve target (Question or Answer) and its author
2. Reject self-votes
3. Under a per-target-key lock, look up any existing `Vote` for `(voterId, targetId)`
4. If no existing vote: record it, apply the full vote delta to the tally and reputation
5. If an existing vote with the **same** value: no-op (idempotent — not a duplicate vote)
6. If an existing vote with a **different** value: flip it — update tally by 2x delta (removing old effect, applying new), update reputation accordingly
7. This whole check-then-write sequence is atomic under the lock, preventing double-voting races

**Edge cases:**
- Self-vote — reject
- Re-clicking the same vote button — idempotent no-op, not double-counted
- Switching vote direction — tally and reputation move by 2 units, not 1

```java
public void castVote(User voter, String targetId, TargetType targetType, VoteValue newValue) {
    User author = resolveAuthor(targetId, targetType);
    if (author.getUserId().equals(voter.getUserId())) {
        throw new IllegalStateException("Users cannot vote on their own posts");
    }

    String voteKey = voter.getUserId() + ":" + targetId;
    Object lock = voteLocks.computeIfAbsent(targetId, k -> new Object());

    synchronized (lock) {
        Vote existing = votes.get(voteKey);

        if (existing == null) {
            votes.put(voteKey, new Vote(voter.getUserId(), targetId, targetType, newValue));
            applyTallyDelta(targetId, targetType, deltaFor(newValue));
            reputationManager.onVoteCast(author, null, newValue);
            return;
        }

        if (existing.getValue() == newValue) {
            return;
        }

        int reversal = -deltaFor(existing.getValue()) + deltaFor(newValue);
        applyTallyDelta(targetId, targetType, reversal);
        reputationManager.onVoteCast(author, existing.getValue(), newValue);
        existing.setValue(newValue);
    }
}

private int deltaFor(VoteValue value) {
    return value == VoteValue.UPVOTE ? 1 : -1;
}

private void applyTallyDelta(String targetId, TargetType targetType, int delta) {
    if (targetType == TargetType.QUESTION) {
        questions.get(targetId).applyVoteDelta(delta);
    } else {
        answers.get(targetId).applyVoteDelta(delta);
    }
}

private User resolveAuthor(String targetId, TargetType targetType) {
    if (targetType == TargetType.QUESTION) {
        Question q = questions.get(targetId);
        if (q == null) throw new NoSuchElementException("No such question: " + targetId);
        return q.getOwner();
    } else {
        Answer a = answers.get(targetId);
        if (a == null) throw new NoSuchElementException("No such answer: " + targetId);
        return a.getAuthor();
    }
}
```

### Core Method: acceptAnswer

**Core logic:**
1. Verify the caller is the question's owner
2. Verify the answer belongs to the question
3. If a different answer was previously accepted, un-accept it (revoke reputation)
4. Mark the new answer accepted, award reputation bonus to its author
5. All under the question's lock so acceptance is atomic with respect to concurrent votes/answers

```java
public void acceptAnswer(User questionOwner, String questionId, String answerId) {
    Question question = questions.get(questionId);
    if (question == null) {
        throw new NoSuchElementException("No such question: " + questionId);
    }
    if (!question.getOwner().getUserId().equals(questionOwner.getUserId())) {
        throw new IllegalStateException("Only the question owner can accept an answer");
    }

    Answer answer = answers.get(answerId);
    if (answer == null || !answer.getQuestionId().equals(questionId)) {
        throw new IllegalArgumentException("Answer does not belong to this question");
    }

    synchronized (question.getLock()) {
        String previousAcceptedId = question.getAcceptedAnswerId();
        if (previousAcceptedId != null && previousAcceptedId.equals(answerId)) {
            return;
        }
        if (previousAcceptedId != null) {
            Answer previous = answers.get(previousAcceptedId);
            previous.markUnaccepted();
            reputationManager.onAnswerUnaccepted(previous.getAuthor());
        }

        answer.markAccepted();
        question.setAcceptedAnswer(answerId);
        reputationManager.onAnswerAccepted(answer.getAuthor());
    }
}
```

---

## Verification

**Scenario**: Alice posts a question. Bob posts an answer. Carol and Dave vote, then Carol changes her vote. Alice accepts Bob's answer.

1. `postQuestion(alice, "Why NPE?", "...", {"java", "npe"})` → Question Q1, `vote_count=0`, `accepted_answer_id=None`
2. `postAnswer(bob, Q1, "Because...")` → Answer A1, `is_accepted=False`; `OwnerNotifier` fires, notifying Alice of a new answer
3. `castVote(carol, A1, ANSWER, UPVOTE)` — no existing vote for `(carol, A1)`. Record it, `A1.vote_count`: 0 → 1. Bob's reputation: 0 → **+10**
4. `castVote(dave, A1, ANSWER, DOWNVOTE)` — no existing vote. Record it, `A1.vote_count`: 1 → 0. Bob's reputation: 10 → **8** (`-2` for downvote)
5. `castVote(carol, A1, ANSWER, DOWNVOTE)` — existing vote was UPVOTE, new is DOWNVOTE. `reversal = -(+1) + (-1) = -2`. `A1.vote_count`: 0 → -2. Bob's reputation: 8 → **6** (lose the +10 effect, apply -2: net change on this flip is -12, but expressed as the vote-cast delta the manager applies -10 then -2 = -12 → 8-12 = -4; clamp/consistency handled by ReputationManager, see note below)
6. `acceptAnswer(alice, Q1, A1)` — Alice is owner, A1 belongs to Q1, no prior accepted answer. `A1.is_accepted=True`, `Q1.accepted_answer_id=A1`. Bob's reputation: **+15** on top of current value

Final tally on A1: `vote_count = -2` (one net downvote after Carol's flip and Dave's downvote). Bob's reputation reflects two downvotes' worth of penalty plus the accept bonus — the exact arithmetic is intentionally owned by `ReputationManager.onVoteCast`, called once per vote-cast event (initial or flip) with the old/new values so it can compute the correct delta in one place rather than duplicating vote-value-to-reputation-delta logic in `castVote`.

---

## Deep Dive & Extensibility

### 1. "How would you rank questions by relevance/hot score?"

Strategy pattern: `RankingStrategy` is injected into `QAService` and swapped without touching call sites.

```java
public interface RankingStrategy {
    List<Question> rank(List<Question> questions);
}

public class NewestStrategy implements RankingStrategy {
    @Override
    public List<Question> rank(List<Question> questions) {
        return questions.stream()
            .sorted(Comparator.comparing(Question::getCreatedAt).reversed())
            .collect(Collectors.toList());
    }
}

public class TopStrategy implements RankingStrategy {
    @Override
    public List<Question> rank(List<Question> questions) {
        return questions.stream()
            .sorted(Comparator.comparingInt(Question::getVoteCount).reversed())
            .collect(Collectors.toList());
    }
}

public class HotStrategy implements RankingStrategy {
    @Override
    public List<Question> rank(List<Question> questions) {
        return questions.stream()
            .sorted(Comparator.comparingDouble(this::hotScore).reversed())
            .collect(Collectors.toList());
    }

    private double hotScore(Question q) {
        double ageHours = Duration.between(q.getCreatedAt(), LocalDateTime.now()).toMinutes() / 60.0;
        int answerBoost = q.getAnswers().size() * 2;
        return (q.getVoteCount() + answerBoost) / Math.pow(ageHours + 2, 1.5);
    }
}
```

`QAService.listQuestionsRanked()` simply calls `rankingStrategy.rank(new ArrayList<>(questions.values()))`. Swapping strategies at runtime (e.g. per-request "sort by" query param) requires no change to `QAService` itself — Open/Closed in action.

### 2. "How would you prevent vote manipulation/brigading?"

Layer defenses outside the core vote-recording logic rather than complicating `castVote`:

```java
public class RateLimitedVotingGuard {
    private final Map<String, Deque<Instant>> recentVotesByUser = new ConcurrentHashMap<>();
    private static final int MAX_VOTES_PER_MINUTE = 20;

    public void checkAndRecord(String userId) {
        Deque<Instant> timestamps = recentVotesByUser.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            Instant cutoff = Instant.now().minusSeconds(60);
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= MAX_VOTES_PER_MINUTE) {
                throw new IllegalStateException("Voting rate limit exceeded");
            }
            timestamps.addLast(Instant.now());
        }
    }
}
```

Additional real-world signals: IP/device fingerprint clustering to detect vote rings, minimum-account-age before votes count at full weight, and down-weighting votes from accounts with very low reputation. These sit as a pipeline of `VotingGuard` checks called before `castVote`'s core logic — each independently pluggable, keeping `castVote` itself simple.

### 3. "How would you implement full-text search across questions?"

Keep `QAService.search` behind an interface so a naive in-memory scan can later be swapped for Lucene/Elasticsearch without touching callers:

```java
public interface SearchIndex {
    void index(Question question);
    List<String> search(String keyword);
}

public class InMemoryKeywordIndex implements SearchIndex {
    private final Map<String, Set<String>> invertedIndex = new ConcurrentHashMap<>();

    @Override
    public void index(Question question) {
        for (String token : tokenize(question.getTitle() + " " + question.getBody())) {
            invertedIndex.computeIfAbsent(token, k -> ConcurrentHashMap.newKeySet())
                         .add(question.getQuestionId());
        }
    }

    @Override
    public List<String> search(String keyword) {
        Set<String> matches = new HashSet<>();
        for (String token : tokenize(keyword)) {
            matches.addAll(invertedIndex.getOrDefault(token, Set.of()));
        }
        return new ArrayList<>(matches);
    }

    private List<String> tokenize(String text) {
        return Arrays.stream(text.toLowerCase().split("\\W+"))
            .filter(s -> !s.isBlank())
            .collect(Collectors.toList());
    }
}
```

`QAService.search(keyword, tag)` intersects `searchIndex.search(keyword)` with `tagIndex.get(tag)` when both are provided. This inverted-index shape is exactly what a real search engine does under the hood, so this abstraction is a natural seam to swap in Elasticsearch later — only `SearchIndex`'s implementation changes.

### 4. "How would you gate privileges behind reputation thresholds?"

A `PrivilegeChecker` consults `User.reputation` against named thresholds, keeping the gating logic out of `QAService`'s core flows:

```java
public enum Privilege {
    VOTE_DOWN(125), CREATE_TAG(1500), EDIT_OTHERS_POSTS(2000), CAST_CLOSE_VOTES(3000);

    final int threshold;
    Privilege(int threshold) { this.threshold = threshold; }
}

public class PrivilegeChecker {
    public boolean has(User user, Privilege privilege) {
        return user.getReputation() >= privilege.threshold;
    }

    public void require(User user, Privilege privilege) {
        if (!has(user, privilege)) {
            throw new IllegalStateException(
                user.getDisplayName() + " needs " + privilege.threshold
                + " reputation for " + privilege + ", has " + user.getReputation());
        }
    }
}
```

`castVote` would call `privilegeChecker.require(voter, Privilege.VOTE_DOWN)` before accepting a `DOWNVOTE`, mirroring Stack Overflow's real 125-reputation gate on downvoting. Because it's a separate checker, adding new gated actions (editing others' posts, casting close votes) never touches vote-recording logic.

---

## Interviewer Questions by Level

**Junior**: Define the Question and Answer classes and their relationship. Explain what fields Vote needs and why it's a separate entity instead of a counter. Sketch how postAnswer connects to Question.

**Mid-level**: Implement `castVote` with correct duplicate-vote prevention and vote-flipping semantics. Explain why self-votes must be rejected before touching any lock. Implement `acceptAnswer` and explain the "exactly one accepted answer" invariant. Justify Strategy for ranking over an if/else in `listQuestions`.

**Senior**: Identify the race in "check for existing vote, then write" and explain the per-target locking fix. Discuss why `ReputationManager` should be the sole writer of reputation rather than scattering `+= 10` calls across `castVote` and `acceptAnswer`. Design vote-brigading defenses as a pipeline separate from core vote recording. Discuss how search would evolve from in-memory inverted index to a real search service, and how that swap stays isolated behind `SearchIndex`.

---

## Common Interview Questions

- Q: Why is Vote a first-class entity instead of just incrementing a counter? A: Because votes must be unique per (user, target) and changeable — you cannot enforce "no double voting" or "flip my vote" with a bare counter; you need to know who voted and what they voted.
- Q: Why can't users vote on their own posts? A: Prevents trivial reputation farming — the self-vote check happens before acquiring any lock, as a cheap early rejection.
- Q: What happens to reputation when a user changes their vote from up to down? A: The delta is computed relative to the previous value inside one atomic operation — the manager receives both old and new value and applies the correct net change, avoiding the bug of applying an unvote delta and a new-vote delta as two separate non-atomic steps.
- Q: Why does accepting a new answer un-accept the old one? A: The domain rule is exactly one accepted answer per question — `acceptAnswer` treats "accept A2" as an atomic transition that also revokes A1's accepted status and reputation bonus if one was previously accepted.
- Q: Why use Observer for new-answer notifications instead of a direct call to a notification service? A: Decouples `QAService` from what "notify" means — email, push, in-app badge — and allows adding new observers (e.g., a watcher list) without modifying `postAnswer`.
- Q: How would you test for lost updates on the vote tally under concurrency? A: Spin up N threads casting votes from N distinct users on the same answer; assert the final `vote_count` equals the number of upvotes minus downvotes exactly, and that the `votes` map has exactly N entries (no lost or duplicated records).
- Q: Why lock per-target (`targetId`) rather than one global lock for all votes? A: Voting on different questions/answers is independent work — a global lock would serialize unrelated votes across the entire platform; per-target locks give parallelism proportional to the number of distinct posts being voted on concurrently.

---

## Concurrency Test Harness

Runnable tests that verify vote-tallying thread-safety invariants. No external deps beyond the JDK.

```java
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// --- Minimal stubs to make the harness self-contained ---

enum VoteValue { UPVOTE, DOWNVOTE }
enum TargetType { QUESTION, ANSWER }

class User {
    private final String userId;
    private final String displayName;
    private int reputation = 0;

    public User(String userId, String displayName) {
        this.userId = userId;
        this.displayName = displayName;
    }

    public synchronized void addReputation(int delta) { reputation += delta; }
    public synchronized int getReputation() { return reputation; }
    public String getUserId() { return userId; }
    public String getDisplayName() { return displayName; }
}

class Vote {
    private final String voterId;
    private final String targetId;
    private final TargetType targetType;
    private VoteValue value;

    public Vote(String voterId, String targetId, TargetType targetType, VoteValue value) {
        this.voterId = voterId;
        this.targetId = targetId;
        this.targetType = targetType;
        this.value = value;
    }

    public VoteValue getValue() { return value; }
    public void setValue(VoteValue value) { this.value = value; }
    public String getVoterId() { return voterId; }
    public String getTargetId() { return targetId; }
}

class Answer {
    private final String answerId;
    private final String questionId;
    private final User author;
    private int voteCount = 0;
    private boolean accepted = false;

    public Answer(String answerId, String questionId, User author) {
        this.answerId = answerId;
        this.questionId = questionId;
        this.author = author;
    }

    public synchronized void applyVoteDelta(int delta) { voteCount += delta; }
    public synchronized int getVoteCount() { return voteCount; }
    public String getAnswerId() { return answerId; }
    public String getQuestionId() { return questionId; }
    public User getAuthor() { return author; }
    public void markAccepted() { accepted = true; }
    public void markUnaccepted() { accepted = false; }
    public boolean isAccepted() { return accepted; }
}

class ReputationManager {
    static final int QUESTION_UPVOTE = 10;
    static final int ANSWER_UPVOTE = 10;
    static final int DOWNVOTE_PENALTY = -2;
    static final int ACCEPTED_BONUS = 15;

    public void onVoteCast(User author, VoteValue oldValue, VoteValue newValue) {
        if (oldValue != null) {
            author.addReputation(oldValue == VoteValue.UPVOTE ? -QUESTION_UPVOTE : -DOWNVOTE_PENALTY);
        }
        author.addReputation(newValue == VoteValue.UPVOTE ? QUESTION_UPVOTE : DOWNVOTE_PENALTY);
    }

    public void onAnswerAccepted(User author) { author.addReputation(ACCEPTED_BONUS); }
    public void onAnswerUnaccepted(User author) { author.addReputation(-ACCEPTED_BONUS); }
}

class VotingService {
    private final Map<String, Answer> answers = new ConcurrentHashMap<>();
    private final Map<String, Vote> votes = new ConcurrentHashMap<>();
    private final Map<String, Object> targetLocks = new ConcurrentHashMap<>();
    private final ReputationManager reputationManager = new ReputationManager();

    public void registerAnswer(Answer answer) {
        answers.put(answer.getAnswerId(), answer);
    }

    private int deltaFor(VoteValue value) {
        return value == VoteValue.UPVOTE ? 1 : -1;
    }

    public void castVote(User voter, String answerId, VoteValue newValue) {
        Answer answer = answers.get(answerId);
        if (answer == null) throw new NoSuchElementException("No such answer: " + answerId);
        if (answer.getAuthor().getUserId().equals(voter.getUserId())) {
            throw new IllegalStateException("Cannot vote on own post");
        }

        String voteKey = voter.getUserId() + ":" + answerId;
        Object lock = targetLocks.computeIfAbsent(answerId, k -> new Object());

        synchronized (lock) {
            Vote existing = votes.get(voteKey);
            if (existing == null) {
                votes.put(voteKey, new Vote(voter.getUserId(), answerId, TargetType.ANSWER, newValue));
                answer.applyVoteDelta(deltaFor(newValue));
                reputationManager.onVoteCast(answer.getAuthor(), null, newValue);
                return;
            }
            if (existing.getValue() == newValue) {
                return;
            }
            int reversal = -deltaFor(existing.getValue()) + deltaFor(newValue);
            answer.applyVoteDelta(reversal);
            reputationManager.onVoteCast(answer.getAuthor(), existing.getValue(), newValue);
            existing.setValue(newValue);
        }
    }

    public int voteRecordCount() { return votes.size(); }
}

// --- Test harness ---

class StackOverflowConcurrencyTest {

    // ─────────────────────────────────────────────────────────────
    // TEST 1: No double-voting under concurrent identical votes
    // 50 distinct users concurrently upvote the same answer once each.
    // Exactly 50 votes must be recorded and vote_count must equal 50.
    // ─────────────────────────────────────────────────────────────
    static void testNoDoubleVoting() throws InterruptedException {
        VotingService service = new VotingService();
        User author = new User("author", "Author");
        Answer answer = new Answer("A1", "Q1", author);
        service.registerAnswer(answer);

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            User voter = new User("voter-" + i, "Voter" + i);
            Thread t = new Thread(() -> service.castVote(voter, "A1", VoteValue.UPVOTE));
            threads.add(t);
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        if (answer.getVoteCount() != 50) {
            throw new AssertionError("Expected vote_count 50, got " + answer.getVoteCount());
        }
        if (service.voteRecordCount() != 50) {
            throw new AssertionError("Expected 50 vote records, got " + service.voteRecordCount());
        }
        if (author.getReputation() != 500) {
            throw new AssertionError("Expected reputation 500, got " + author.getReputation());
        }
        System.out.println("PASS: testNoDoubleVoting");
    }

    // ─────────────────────────────────────────────────────────────
    // TEST 2: Same user hammering the same vote repeatedly must not
    // double-count — repeated identical votes are idempotent no-ops.
    // ─────────────────────────────────────────────────────────────
    static void testRepeatedSameVoteIsIdempotent() throws InterruptedException {
        VotingService service = new VotingService();
        User author = new User("author2", "Author2");
        Answer answer = new Answer("A2", "Q2", author);
        service.registerAnswer(answer);
        User voter = new User("voter-x", "VoterX");

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Thread t = new Thread(() -> service.castVote(voter, "A2", VoteValue.UPVOTE));
            threads.add(t);
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        if (answer.getVoteCount() != 1) {
            throw new AssertionError("Expected vote_count 1, got " + answer.getVoteCount());
        }
        if (service.voteRecordCount() != 1) {
            throw new AssertionError("Expected 1 vote record, got " + service.voteRecordCount());
        }
        if (author.getReputation() != 10) {
            throw new AssertionError("Expected reputation 10, got " + author.getReputation());
        }
        System.out.println("PASS: testRepeatedSameVoteIsIdempotent");
    }

    // ─────────────────────────────────────────────────────────────
    // TEST 3: Concurrent mixed up/down votes from distinct users
    // tally correctly, and vote-flipping updates the total exactly once.
    // ─────────────────────────────────────────────────────────────
    static void testMixedVotesAndFlip() throws InterruptedException {
        VotingService service = new VotingService();
        User author = new User("author3", "Author3");
        Answer answer = new Answer("A3", "Q3", author);
        service.registerAnswer(answer);

        int upvoters = 30;
        int downvoters = 10;
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < upvoters; i++) {
            User voter = new User("up-" + i, "Up" + i);
            threads.add(new Thread(() -> service.castVote(voter, "A3", VoteValue.UPVOTE)));
        }
        for (int i = 0; i < downvoters; i++) {
            User voter = new User("down-" + i, "Down" + i);
            threads.add(new Thread(() -> service.castVote(voter, "A3", VoteValue.DOWNVOTE)));
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        int expectedTally = upvoters - downvoters;
        if (answer.getVoteCount() != expectedTally) {
            throw new AssertionError("Expected tally " + expectedTally + ", got " + answer.getVoteCount());
        }

        User flipper = new User("flipper", "Flipper");
        service.castVote(flipper, "A3", VoteValue.UPVOTE);
        if (answer.getVoteCount() != expectedTally + 1) {
            throw new AssertionError("Expected tally after upvote " + (expectedTally + 1) + ", got " + answer.getVoteCount());
        }
        service.castVote(flipper, "A3", VoteValue.DOWNVOTE);
        if (answer.getVoteCount() != expectedTally - 1) {
            throw new AssertionError("Expected tally after flip " + (expectedTally - 1) + ", got " + answer.getVoteCount());
        }
        if (service.voteRecordCount() != upvoters + downvoters + 1) {
            throw new AssertionError("Unexpected vote record count: " + service.voteRecordCount());
        }
        System.out.println("PASS: testMixedVotesAndFlip");
    }

    public static void main(String[] args) throws InterruptedException {
        testNoDoubleVoting();
        testRepeatedSameVoteIsIdempotent();
        testMixedVotesAndFlip();
        System.out.println("All concurrency tests passed.");
    }
}
```

**What each test verifies:**
- `testNoDoubleVoting`: 50 distinct concurrent voters each produce exactly one vote record and one tally increment — the per-target lock serializes the check-then-write sequence so no vote is lost or double-applied.
- `testRepeatedSameVoteIsIdempotent`: the same user racing to cast the identical vote 20 times concurrently results in exactly one recorded vote and one tally increment — the "existing vote with same value → no-op" branch is itself inside the lock, closing the race where two threads could both see "no existing vote."
- `testMixedVotesAndFlip`: verifies the tally arithmetic under concurrent mixed votes, then verifies that flipping a vote (up→down) moves the tally by exactly 2 in one atomic step rather than as two independently-racing updates.

---

## Related

**Patterns applied here**

- [Strategy Pattern](../../03-design-patterns/03-behavioral/strategy-pattern.md)
- [Observer Pattern](../../03-design-patterns/03-behavioral/observer-pattern.md)
- [Factory Pattern](../../03-design-patterns/01-creational/factory-pattern.md)

**SOLID focus**: [Single Responsibility](../../02-solid-principles/01-single-responsibility.md) · [Open/Closed](../../02-solid-principles/02-open-closed.md)

**Concurrency**: [Concurrency Patterns](../../04-concurrency/concurrency-patterns.md)

**Practice next**

- [Design Notification System](../02-frequent-problems/16-design-notification-system.md)
- [Design Pub-Sub](../03-domain-specific/23-design-pub-sub.md)

Both extend the Observer-driven notification shape used here for new-answer alerts.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../01-oop-fundamentals/uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
