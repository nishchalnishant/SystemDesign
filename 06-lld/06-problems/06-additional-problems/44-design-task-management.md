> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a Task Management System (like Jira/Trello/Asana) — a Kanban-style board with columns, tasks, assignment, status transitions, comments, and an activity feed.
>
> **Key concepts:**
> - Core Entities: `Board`, `Column` (list, e.g. TODO/IN_PROGRESS/DONE), `Task`, `User`, `Comment`, `ActivityLog`.
> - The problem: moving tasks between columns with valid-transition enforcement, tracking who did what and when, and notifying interested parties.
> - Patterns:
>   - Observer: `Task` publishes change events (status change, assignment, comment) to subscribers — activity logger and notification service.
>   - Command: encapsulate a task move as a `MoveTaskCommand` object so it can be undone/redone.
> - Concurrency: two users dragging the same task card at once must not silently overwrite each other. Use optimistic locking with a `version` field — the second writer gets a conflict instead of clobbering the first.
>
> **Key takeaway:** Keep `Task` state-transition rules centralized (a small state machine), and keep side effects (logging, notifications) decoupled via Observer rather than hard-coded inside `moveTask`.

---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, task-management, jira, trello, observer, command, optimistic-locking]
---
# Design a Task Management System

> **Difficulty**: Medium  
> **Asked at**: Atlassian, Asana, Meta, Microsoft  
> **Key Patterns**: Observer (activity log/notifications), Command (undo/redo task moves), State (status transitions)

---

## Understanding the Problem

Design a task management system like Jira/Trello/Asana: users create boards, boards contain columns (lists) representing workflow stages, and tasks live in columns and move between them. Tasks can be assigned to users, have due dates and priority, support comments, and every change is recorded in an activity log. The system must handle concurrent edits from multiple users safely.

---

## Clarifying Questions

**You**: "Is this closer to Trello (freeform boards with lists) or Jira (structured projects with sprints)?"  
**Interviewer**: "Trello-style — a board has columns, and tasks move between columns. Keep sprints/epics out of scope."

**You**: "Can a task be assigned to multiple users, or just one?"  
**Interviewer**: "One primary assignee for now. Design it so multi-assignee could be added later."

**You**: "Should column names/workflow be fully customizable per board, or a fixed set of statuses?"  
**Interviewer**: "Start with a fixed workflow — TODO, IN_PROGRESS, IN_REVIEW, DONE — with defined valid transitions. Mention how you'd extend it to per-board custom workflows."

**You**: "Do tasks need due dates and priority?"  
**Interviewer**: "Yes — due date, and priority as LOW/MEDIUM/HIGH/URGENT."

**You**: "Do we need comments and an activity/audit log on tasks?"  
**Interviewer**: "Yes to both. Every status change, assignment change, and comment should be recorded with who and when."

**You**: "Does the interviewer want notifications (e.g., email/push when assigned)?"  
**Interviewer**: "Assume a `NotificationService` exists — just show how a task change triggers it, not the delivery mechanism itself."

**You**: "Is concurrent editing a concern — two people moving or editing the same task at once?"  
**Interviewer**: "Yes, assume concurrent access. Show how you'd prevent a lost update."

---

## Final Requirements

**In scope:**
1. Create boards with a fixed set of columns (TODO, IN_PROGRESS, IN_REVIEW, DONE)
2. Create tasks within a column; set title, description, due date, priority
3. Move a task between columns, enforcing valid status transitions
4. Assign a task to a single user
5. Add comments to a task
6. Maintain an activity log per task (status changes, assignment changes, comments)
7. Notify interested parties (assignee) on task changes via Observer
8. Thread-safe concurrent task updates using optimistic locking (version field)

**Out of scope:**
- Sprints, epics, backlogs, story points
- Fully custom per-board workflows (discussed as extension only)
- Real notification delivery (email/push infra)
- Multi-assignee tasks, subtasks, attachments

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|---------------|
| Board | Owns an ordered list of Columns; entry point for task creation |
| Column | Named workflow stage (TODO/IN_PROGRESS/IN_REVIEW/DONE); holds task IDs |
| Task | Core unit of work; title, status, assignee, priority, due date, version |
| User | Task creator/assignee/commenter identity |
| Comment | Text left by a User on a Task, timestamped |
| ActivityLog | Append-only record of TaskEvents per Task |

A `Board` owns `Column`s in a fixed order. A `Task` belongs to exactly one `Column` at a time (tracked via `status`, which maps 1:1 to a column). Moving a task changes its `status` after validating the transition is legal in the state machine. `Task` is the subject in an Observer relationship — `ActivityLogger` and `NotificationService` subscribe to `TaskObserver` and react to `TaskEvent`s (status changed, assigned, commented). Task moves are also wrapped as `Command` objects to support undo/redo.

---

## Class Design

### TaskStatus / Priority

```
enum TaskStatus:
    TODO, IN_PROGRESS, IN_REVIEW, DONE

enum Priority:
    LOW, MEDIUM, HIGH, URGENT
```

### Task

| Requirement | What Task must track |
|-------------|----------------------|
| Identity & content | task_id, title, description |
| Workflow position | status: TaskStatus |
| Ownership | assignee: User \| None, created_by: User |
| Scheduling | due_date, priority |
| Optimistic concurrency | version: int |
| Change notification | observers: list[TaskObserver] |

```
class Task:
- task_id: str
- title: str
- description: str
- status: TaskStatus
- assignee: User | None
- created_by: User
- due_date: date | None
- priority: Priority
- comments: list[Comment]
- version: int                     # optimistic locking
- observers: list[TaskObserver]

+ add_observer(observer: TaskObserver) -> None
+ notify(event: TaskEvent) -> None
+ add_comment(comment: Comment) -> None
```

### TaskObserver / TaskEvent (Observer pattern)

```
interface TaskObserver:
+ on_task_event(event: TaskEvent) -> None

class TaskEvent:
- task_id: str
- event_type: EventType         # STATUS_CHANGED, ASSIGNED, COMMENTED
- actor: User
- timestamp: datetime
- details: dict                 # e.g. {"from": TODO, "to": IN_PROGRESS}

class ActivityLogger implements TaskObserver:
- logs: dict[str, list[TaskEvent]]  # task_id -> events
+ on_task_event(event: TaskEvent) -> None

class NotificationService implements TaskObserver:
+ on_task_event(event: TaskEvent) -> None   # notify event.details.assignee, etc.
```

### Column / Board

```
class Column:
- name: str
- status: TaskStatus
- task_ids: list[str]

class Board:
- board_id: str
- name: str
- columns: list[Column]         # fixed order: TODO -> IN_PROGRESS -> IN_REVIEW -> DONE
- tasks: dict[str, Task]
- _lock: ReentrantLock

+ create_task(...) -> Task
+ move_task(task_id, from_status, to_status, actor) -> Task
+ assign_task(task_id, user, actor) -> Task
+ add_comment(task_id, user, text) -> Comment
```

### Command pattern (undo/redo task moves)

```
interface Command:
+ execute() -> None
+ undo() -> None

class MoveTaskCommand implements Command:
- board: Board
- task_id: str
- from_status: TaskStatus
- to_status: TaskStatus
- actor: User

+ execute() -> None    # board.move_task(task_id, from_status, to_status, actor)
+ undo() -> None       # board.move_task(task_id, to_status, from_status, actor)

class CommandHistory:
- undo_stack: Deque[Command]
- redo_stack: Deque[Command]

+ execute(command: Command) -> None
+ undo() -> None
+ redo() -> None
```

### WorkflowValidator (state machine for valid transitions)

```
class WorkflowValidator:
- allowed_transitions: dict[TaskStatus, set[TaskStatus]]

+ is_valid_transition(from_status, to_status) -> bool
```

---

## Implementation

### Core Method: createTask

**Core logic:**
1. Validate title is non-blank
2. Build Task with status = TODO, version = 0, empty observers replaced with the board's registered observers
3. Add task_id to the TODO column's task list
4. Fire a TASK_CREATED-equivalent event is optional; we log via ActivityLogger on first mutating event

**Edge cases:**
- Blank title — reject
- Board has no TODO column configured — misconfiguration error

```java
public Task createTask(String title, String description, User createdBy,
                        Priority priority, LocalDate dueDate) {
    if (title == null || title.isBlank()) {
        throw new IllegalArgumentException("Task title cannot be blank");
    }

    Task task = new Task(
        UUID.randomUUID().toString(),
        title,
        description,
        TaskStatus.TODO,
        createdBy,
        priority,
        dueDate
    );
    for (TaskObserver observer : observers) {
        task.addObserver(observer);
    }

    synchronized (lock) {
        tasks.put(task.getTaskId(), task);
        getColumn(TaskStatus.TODO).getTaskIds().add(task.getTaskId());
    }
    return task;
}
```

### Core Method: moveTask

**Core logic:**
1. Look up task; verify it exists
2. Validate `(fromStatus -> toStatus)` against `WorkflowValidator`
3. Verify task's current status actually equals `fromStatus` (guards against stale UI state)
4. Optimistic lock: caller supplies `expectedVersion`; reject if it doesn't match `task.version`
5. Remove task_id from source column, add to destination column, bump `status` and `version`
6. Fire a STATUS_CHANGED event to observers (ActivityLogger records it, NotificationService notifies assignee)

**Edge cases:**
- Invalid transition (e.g. TODO -> DONE directly) — reject
- Stale version (another writer already moved it) — throw `OptimisticLockException`
- Task not currently in `fromStatus` — reject (someone already moved it)

```java
public Task moveTask(String taskId, TaskStatus fromStatus, TaskStatus toStatus,
                      long expectedVersion, User actor) {
    if (!workflowValidator.isValidTransition(fromStatus, toStatus)) {
        throw new IllegalStateException(
            "Invalid transition: " + fromStatus + " -> " + toStatus);
    }

    synchronized (lock) {
        Task task = tasks.get(taskId);
        if (task == null) {
            throw new NoSuchElementException("No such task: " + taskId);
        }
        if (task.getStatus() != fromStatus) {
            throw new IllegalStateException(
                "Task " + taskId + " is not in " + fromStatus + " (currently " + task.getStatus() + ")");
        }
        if (task.getVersion() != expectedVersion) {
            throw new OptimisticLockException(
                "Task " + taskId + " was modified concurrently (expected v" +
                expectedVersion + ", found v" + task.getVersion() + ")");
        }

        getColumn(fromStatus).getTaskIds().remove(taskId);
        getColumn(toStatus).getTaskIds().add(taskId);
        task.setStatus(toStatus);
        task.setVersion(task.getVersion() + 1);

        task.notify(new TaskEvent(
            taskId, EventType.STATUS_CHANGED, actor, LocalDateTime.now(),
            Map.of("from", fromStatus, "to", toStatus)));

        return task;
    }
}
```

### Core Method: assignTask

**Core logic:**
1. Look up task, optimistic version check
2. Set assignee, bump version
3. Fire ASSIGNED event so NotificationService can notify the new assignee

```java
public Task assignTask(String taskId, User assignee, long expectedVersion, User actor) {
    synchronized (lock) {
        Task task = tasks.get(taskId);
        if (task == null) {
            throw new NoSuchElementException("No such task: " + taskId);
        }
        if (task.getVersion() != expectedVersion) {
            throw new OptimisticLockException(
                "Task " + taskId + " was modified concurrently (expected v" +
                expectedVersion + ", found v" + task.getVersion() + ")");
        }

        User previousAssignee = task.getAssignee();
        task.setAssignee(assignee);
        task.setVersion(task.getVersion() + 1);

        task.notify(new TaskEvent(
            taskId, EventType.ASSIGNED, actor, LocalDateTime.now(),
            Map.of("from", previousAssignee, "to", assignee)));

        return task;
    }
}
```

### Core Method: addComment

**Core logic:**
1. Look up task under lock
2. Append comment to task's comment list (no version bump needed — comments are append-only, not overwritten)
3. Fire COMMENTED event

```java
public Comment addComment(String taskId, User author, String text) {
    if (text == null || text.isBlank()) {
        throw new IllegalArgumentException("Comment text cannot be blank");
    }

    Comment comment = new Comment(
        UUID.randomUUID().toString(), author, text, LocalDateTime.now());

    synchronized (lock) {
        Task task = tasks.get(taskId);
        if (task == null) {
            throw new NoSuchElementException("No such task: " + taskId);
        }
        task.addComment(comment);
        task.notify(new TaskEvent(
            taskId, EventType.COMMENTED, author, LocalDateTime.now(),
            Map.of("commentId", comment.getCommentId())));
    }
    return comment;
}
```

### WorkflowValidator

```java
public class WorkflowValidator {
    private final Map<TaskStatus, Set<TaskStatus>> allowedTransitions;

    public WorkflowValidator() {
        this.allowedTransitions = new EnumMap<>(TaskStatus.class);
        allowedTransitions.put(TaskStatus.TODO, EnumSet.of(TaskStatus.IN_PROGRESS));
        allowedTransitions.put(TaskStatus.IN_PROGRESS,
            EnumSet.of(TaskStatus.IN_REVIEW, TaskStatus.TODO));
        allowedTransitions.put(TaskStatus.IN_REVIEW,
            EnumSet.of(TaskStatus.DONE, TaskStatus.IN_PROGRESS));
        allowedTransitions.put(TaskStatus.DONE, EnumSet.of(TaskStatus.IN_PROGRESS));
    }

    public boolean isValidTransition(TaskStatus from, TaskStatus to) {
        return allowedTransitions.getOrDefault(from, Set.of()).contains(to);
    }
}
```

---

## Verification

**Scenario**: Alice creates a task, Bob starts work on it, then moves it to review, then it's approved to done.

1. `createTask("Fix login bug", ..., createdBy=Alice, HIGH, dueDate)` — Task T1, status=TODO, version=0, placed in TODO column
2. `assignTask(T1, Bob, expectedVersion=0, actor=Alice)` — assignee=Bob, version=1; ASSIGNED event fires; NotificationService notifies Bob; ActivityLogger records `{actor: Alice, event: ASSIGNED, to: Bob}`
3. `moveTask(T1, TODO, IN_PROGRESS, expectedVersion=1, actor=Bob)` — valid transition (TODO -> IN_PROGRESS); T1 removed from TODO column, added to IN_PROGRESS column; status=IN_PROGRESS, version=2; STATUS_CHANGED event logged
4. `addComment(T1, Bob, "Root cause found, fix incoming")` — comment appended; COMMENTED event logged; version unchanged (still 2)
5. `moveTask(T1, IN_PROGRESS, IN_REVIEW, expectedVersion=2, actor=Bob)` — valid; status=IN_REVIEW, version=3
6. `moveTask(T1, IN_REVIEW, DONE, expectedVersion=3, actor=Alice)` — valid; status=DONE, version=4

**Activity log for T1** (chronological): ASSIGNED(Alice→Bob), STATUS_CHANGED(TODO→IN_PROGRESS), COMMENTED, STATUS_CHANGED(IN_PROGRESS→IN_REVIEW), STATUS_CHANGED(IN_REVIEW→DONE).

**Rejected case**: A stale client attempts `moveTask(T1, TODO, IN_PROGRESS, expectedVersion=0, ...)` after step 3 already advanced it to version 2 — throws `OptimisticLockException`, forcing the client to refetch and retry.

---

## Deep Dive & Extensibility

### 1. "How would you support custom workflows per board (configurable columns/states)?"

Replace the hardcoded `TaskStatus` enum + `WorkflowValidator` with a data-driven model owned by each `Board`. Statuses become board-scoped `WorkflowState` objects, and transitions become a per-board edge list instead of a static enum map.

```java
public class WorkflowState {
    private final String stateId;
    private final String name;
    private final int order;

    public WorkflowState(String stateId, String name, int order) {
        this.stateId = stateId;
        this.name = name;
        this.order = order;
    }

    public String getStateId() { return stateId; }
}

public class BoardWorkflow {
    private final List<WorkflowState> states;
    private final Map<String, Set<String>> transitions; // stateId -> allowed next stateIds

    public BoardWorkflow(List<WorkflowState> states, Map<String, Set<String>> transitions) {
        this.states = states;
        this.transitions = transitions;
    }

    public boolean isValidTransition(String fromStateId, String toStateId) {
        return transitions.getOrDefault(fromStateId, Set.of()).contains(toStateId);
    }
}
```

`Task.status` becomes a `stateId: String` referencing a `WorkflowState` on the owning board rather than a fixed enum value. `Board.createBoard(templateId)` can seed common presets (simple Kanban: To Do/Doing/Done; software team: Backlog/Todo/In Progress/In Review/Done). The rest of `moveTask` is unchanged — it just calls `board.getWorkflow().isValidTransition(...)` instead of consulting a static enum map.

### 2. "How would you implement undo/redo for task moves using the Command pattern?"

Wrap each move in a `MoveTaskCommand`; a `CommandHistory` keeps an undo stack and a redo stack. Executing a new command clears the redo stack (standard undo/redo semantics).

```java
public class MoveTaskCommand implements Command {
    private final Board board;
    private final String taskId;
    private final TaskStatus fromStatus;
    private final TaskStatus toStatus;
    private final User actor;
    private long versionUsed;

    public MoveTaskCommand(Board board, String taskId, TaskStatus fromStatus,
                            TaskStatus toStatus, User actor) {
        this.board = board;
        this.taskId = taskId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actor = actor;
    }

    @Override
    public void execute() {
        Task current = board.getTask(taskId);
        this.versionUsed = current.getVersion();
        board.moveTask(taskId, fromStatus, toStatus, versionUsed, actor);
    }

    @Override
    public void undo() {
        Task current = board.getTask(taskId);
        board.moveTask(taskId, toStatus, fromStatus, current.getVersion(), actor);
    }
}

public class CommandHistory {
    private final Deque<Command> undoStack = new ArrayDeque<>();
    private final Deque<Command> redoStack = new ArrayDeque<>();

    public void execute(Command command) {
        command.execute();
        undoStack.push(command);
        redoStack.clear();
    }

    public void undo() {
        if (undoStack.isEmpty()) {
            throw new IllegalStateException("Nothing to undo");
        }
        Command command = undoStack.pop();
        command.undo();
        redoStack.push(command);
    }

    public void redo() {
        if (redoStack.isEmpty()) {
            throw new IllegalStateException("Nothing to redo");
        }
        Command command = redoStack.pop();
        command.execute();
        undoStack.push(command);
    }
}
```

Because `moveTask` re-reads `task.getVersion()` at undo time rather than reusing `versionUsed`, undo remains correct even if other events (comments) happened in between — it only fails if another *status change* raced it, which correctly surfaces as an `OptimisticLockException`.

### 3. "How would you scale the activity feed for a board with thousands of tasks (pagination/cursor)?"

An in-memory `List<TaskEvent>` per task doesn't scale to a board-wide feed with millions of events. Move to a cursor-paginated, append-only log keyed by `(boardId, timestamp, eventId)` so pages can be fetched without offset-based `OFFSET/LIMIT` scans (which degrade as the table grows).

```java
public class ActivityFeedPage {
    private final List<TaskEvent> events;
    private final String nextCursor; // opaque, e.g. base64(timestamp + eventId)

    public ActivityFeedPage(List<TaskEvent> events, String nextCursor) {
        this.events = events;
        this.nextCursor = nextCursor;
    }

    public List<TaskEvent> getEvents() { return events; }
    public String getNextCursor() { return nextCursor; }
}

public class ActivityFeedService {
    private static final int PAGE_SIZE = 50;

    public ActivityFeedPage getFeed(String boardId, String cursor) {
        Instant afterTimestamp = cursor == null ? Instant.MAX : decodeCursor(cursor).timestamp();
        String afterEventId = cursor == null ? "" : decodeCursor(cursor).eventId();

        // SELECT * FROM task_events
        // WHERE board_id = ? AND (timestamp, event_id) < (?, ?)
        // ORDER BY timestamp DESC, event_id DESC
        // LIMIT PAGE_SIZE + 1
        List<TaskEvent> fetched = queryEventsBefore(boardId, afterTimestamp, afterEventId, PAGE_SIZE + 1);

        boolean hasMore = fetched.size() > PAGE_SIZE;
        List<TaskEvent> page = hasMore ? fetched.subList(0, PAGE_SIZE) : fetched;
        String next = hasMore ? encodeCursor(page.get(page.size() - 1)) : null;
        return new ActivityFeedPage(page, next);
    }

    private List<TaskEvent> queryEventsBefore(String boardId, Instant ts, String eventId, int limit) {
        throw new UnsupportedOperationException("backed by DB index on (board_id, timestamp, event_id)");
    }

    private String encodeCursor(TaskEvent event) { return event.getTimestamp() + "|" + event.getEventId(); }
    private CursorParts decodeCursor(String cursor) {
        String[] parts = cursor.split("\\|");
        return new CursorParts(Instant.parse(parts[0]), parts[1]);
    }

    private record CursorParts(Instant timestamp, String eventId) {}
}
```

The composite `(timestamp, event_id)` cursor avoids duplicate/skipped rows when multiple events share a millisecond. `ActivityLogger` (the Observer) writes to this same append-only store instead of an in-memory map, decoupling write path (Observer callback) from read path (paginated feed query).

---

## Interviewer Questions by Level

**Junior**: Define the `Task` class and its fields. Explain how a task moves from one column to another and which classes are involved. Sketch the entity relationships between Board, Column, and Task.

**Mid-level**: Implement `moveTask` with transition validation. Explain why Observer decouples activity logging and notifications from `Task` mutation logic. Implement the `WorkflowValidator` state machine and justify the allowed-transitions design.

**Senior**: Identify the lost-update race when two users move/edit the same task concurrently and justify optimistic locking with a version field over pessimistic locking. Design the Command-based undo/redo stack and explain why undo re-reads the current version instead of caching it. Extend the fixed workflow to per-board configurable states without breaking `moveTask`'s API. Discuss cursor pagination for the activity feed vs. offset pagination at scale.

---

## Common Interview Questions

- Q: Why Observer for activity logging and notifications instead of calling them directly inside `moveTask`? A: `Task` shouldn't know about logging or notification concerns (Single Responsibility). Observer lets you add/remove subscribers (e.g. a Slack integration) without touching `Task` or `Board` code — Open/Closed in practice.
- Q: Why optimistic locking instead of a mutex per task? A: Most reads/writes on a task don't conflict — pessimistic per-task locks would serialize unrelated operations (e.g. two different users commenting) unnecessarily. Optimistic locking only rejects the rare case of an actual concurrent conflicting write, and clients simply retry.
- Q: What happens on a version mismatch — does the system merge the changes? A: No. The system throws `OptimisticLockException`; the client refetches the latest task state and either reapplies the change or shows the user a conflict. Merging is a UX decision, not something the backend does silently.
- Q: Why validate `task.status == fromStatus` in addition to the version check? A: It's a defense-in-depth guard against a stale UI passing the wrong `fromStatus` — the version check alone would still catch it, but the explicit check gives a clearer error message ("task already moved") instead of a generic version conflict.
- Q: How would you support multiple assignees? A: Change `assignee: User` to `assignees: Set<User>`, and change the ASSIGNED event's `details` map to carry the full set delta (`added`, `removed`) rather than a single `from`/`to` pair. `NotificationService` iterates the set.
- Q: Why is `addComment` not gated by the version field? A: Comments are append-only — two users commenting concurrently don't conflict, unlike a status/assignee overwrite. Gating it would cause spurious conflict errors for unrelated concurrent comments.
- Q: How would you test that concurrent moves don't corrupt state? A: Spin up two threads both fetching the same task's current version, then both trying to move it; assert exactly one succeeds and the other gets `OptimisticLockException`, and that the task ends in a single consistent column.

---

## Concurrency Test Harness

Runnable tests verifying optimistic-locking invariants and no-lost-update comment appends. No external deps — uses `java.util.concurrent` only.

```java
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

enum TaskStatus { TODO, IN_PROGRESS, IN_REVIEW, DONE }
enum Priority { LOW, MEDIUM, HIGH, URGENT }
enum EventType { STATUS_CHANGED, ASSIGNED, COMMENTED }

class OptimisticLockException extends RuntimeException {
    public OptimisticLockException(String message) { super(message); }
}

class User {
    private final String userId;
    private final String name;

    public User(String userId, String name) {
        this.userId = userId;
        this.name = name;
    }

    public String getUserId() { return userId; }
    public String getName() { return name; }
}

class Comment {
    private final String commentId;
    private final User author;
    private final String text;
    private final LocalDateTime createdAt;

    public Comment(String commentId, User author, String text, LocalDateTime createdAt) {
        this.commentId = commentId;
        this.author = author;
        this.text = text;
        this.createdAt = createdAt;
    }

    public String getCommentId() { return commentId; }
}

class TaskEvent {
    private final String taskId;
    private final EventType eventType;
    private final User actor;
    private final LocalDateTime timestamp;
    private final Map<String, Object> details;

    public TaskEvent(String taskId, EventType eventType, User actor,
                      LocalDateTime timestamp, Map<String, Object> details) {
        this.taskId = taskId;
        this.eventType = eventType;
        this.actor = actor;
        this.timestamp = timestamp;
        this.details = details;
    }

    public String getTaskId() { return taskId; }
    public EventType getEventType() { return eventType; }
}

interface TaskObserver {
    void onTaskEvent(TaskEvent event);
}

class ActivityLogger implements TaskObserver {
    final Map<String, List<TaskEvent>> logs = new ConcurrentHashMap<>();

    @Override
    public void onTaskEvent(TaskEvent event) {
        logs.computeIfAbsent(event.getTaskId(), k -> Collections.synchronizedList(new ArrayList<>()))
            .add(event);
    }
}

class Task {
    private final String taskId;
    private String title;
    private TaskStatus status;
    private User assignee;
    private final List<Comment> comments = Collections.synchronizedList(new ArrayList<>());
    private volatile long version = 0;
    private final List<TaskObserver> observers = new CopyOnWriteArrayList<>();

    public Task(String taskId, String title, TaskStatus status) {
        this.taskId = taskId;
        this.title = title;
        this.status = status;
    }

    public void addObserver(TaskObserver observer) { observers.add(observer); }
    public void notifyObservers(TaskEvent event) {
        for (TaskObserver o : observers) o.onTaskEvent(event);
    }

    public void addComment(Comment comment) { comments.add(comment); }
    public List<Comment> getComments() { return comments; }

    public String getTaskId() { return taskId; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public User getAssignee() { return assignee; }
    public void setAssignee(User assignee) { this.assignee = assignee; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}

class Board {
    private final Map<String, Task> tasks = new HashMap<>();
    private final Map<TaskStatus, List<String>> columns = new EnumMap<>(TaskStatus.class);
    private final Object lock = new Object();
    private final List<TaskObserver> observers = new ArrayList<>();

    public Board() {
        for (TaskStatus s : TaskStatus.values()) {
            columns.put(s, Collections.synchronizedList(new ArrayList<>()));
        }
    }

    public void registerObserver(TaskObserver observer) { observers.add(observer); }

    public Task createTask(String title) {
        Task task = new Task(UUID.randomUUID().toString(), title, TaskStatus.TODO);
        for (TaskObserver o : observers) task.addObserver(o);
        synchronized (lock) {
            tasks.put(task.getTaskId(), task);
            columns.get(TaskStatus.TODO).add(task.getTaskId());
        }
        return task;
    }

    public Task getTask(String taskId) {
        synchronized (lock) {
            return tasks.get(taskId);
        }
    }

    public Task moveTask(String taskId, TaskStatus from, TaskStatus to, long expectedVersion, User actor) {
        synchronized (lock) {
            Task task = tasks.get(taskId);
            if (task == null) throw new NoSuchElementException("No such task: " + taskId);
            if (task.getStatus() != from) {
                throw new IllegalStateException("Task not in expected source status");
            }
            if (task.getVersion() != expectedVersion) {
                throw new OptimisticLockException(
                    "Conflict on " + taskId + ": expected v" + expectedVersion + ", found v" + task.getVersion());
            }
            columns.get(from).remove(taskId);
            columns.get(to).add(taskId);
            task.setStatus(to);
            task.setVersion(task.getVersion() + 1);
            return task;
        }
    }

    public Comment addComment(String taskId, User author, String text) {
        Comment comment = new Comment(UUID.randomUUID().toString(), author, text, LocalDateTime.now());
        Task task = getTask(taskId);
        if (task == null) throw new NoSuchElementException("No such task: " + taskId);
        task.addComment(comment);
        return comment;
    }
}

class TaskManagementConcurrencyTest {

    // ─────────────────────────────────────────────────────────────
    // TEST 1: Concurrent moves of the SAME task by two users.
    // Both read version=0 and race to move TODO -> IN_PROGRESS.
    // Exactly one must succeed; the other must get OptimisticLockException.
    // ─────────────────────────────────────────────────────────────
    static void testConcurrentMoveConflictDetected() throws InterruptedException {
        Board board = new Board();
        Task task = board.createTask("Race condition bug");
        User alice = new User("u1", "Alice");
        User bob = new User("u2", "Bob");

        long snapshotVersion = task.getVersion(); // both threads read the same stale version

        List<String> results = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Runnable moveByAlice = () -> {
            try {
                startLatch.await();
                board.moveTask(task.getTaskId(), TaskStatus.TODO, TaskStatus.IN_PROGRESS, snapshotVersion, alice);
                results.add("alice-ok");
            } catch (OptimisticLockException e) {
                results.add("alice-conflict");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        Runnable moveByBob = () -> {
            try {
                startLatch.await();
                board.moveTask(task.getTaskId(), TaskStatus.TODO, TaskStatus.IN_PROGRESS, snapshotVersion, bob);
                results.add("bob-ok");
            } catch (OptimisticLockException e) {
                results.add("bob-conflict");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        pool.submit(moveByAlice);
        pool.submit(moveByBob);
        startLatch.countDown();
        pool.shutdown();
        if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Test timed out");
        }

        long oks = results.stream().filter(r -> r.endsWith("ok")).count();
        long conflicts = results.stream().filter(r -> r.endsWith("conflict")).count();
        if (oks != 1) throw new AssertionError("Expected exactly 1 success, got " + oks + " (" + results + ")");
        if (conflicts != 1) throw new AssertionError("Expected exactly 1 conflict, got " + conflicts);
        if (task.getStatus() != TaskStatus.IN_PROGRESS) {
            throw new AssertionError("Task should have ended in IN_PROGRESS");
        }
        if (task.getVersion() != snapshotVersion + 1) {
            throw new AssertionError("Version should have advanced exactly once");
        }

        System.out.println("PASS: testConcurrentMoveConflictDetected");
    }

    // ─────────────────────────────────────────────────────────────
    // TEST 2: Concurrent comment additions must not lose any comment.
    // 50 threads each add one comment concurrently; all 50 must be present.
    // ─────────────────────────────────────────────────────────────
    static void testConcurrentCommentsNoLostUpdate() throws InterruptedException {
        Board board = new Board();
        Task task = board.createTask("Discuss design");

        int threadCount = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    startLatch.await();
                    User author = new User("u" + idx, "User" + idx);
                    board.addComment(task.getTaskId(), author, "Comment #" + idx);
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        if (!doneLatch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Test timed out");
        }
        pool.shutdown();

        if (errors.get() != 0) throw new AssertionError("Unexpected errors: " + errors.get());
        if (task.getComments().size() != threadCount) {
            throw new AssertionError("Expected " + threadCount + " comments, got " + task.getComments().size());
        }

        Set<String> uniqueIds = new HashSet<>();
        for (Comment c : task.getComments()) {
            if (!uniqueIds.add(c.getCommentId())) {
                throw new AssertionError("Duplicate/corrupted comment id: " + c.getCommentId());
            }
        }

        System.out.println("PASS: testConcurrentCommentsNoLostUpdate");
    }

    public static void main(String[] args) throws InterruptedException {
        testConcurrentMoveConflictDetected();
        testConcurrentCommentsNoLostUpdate();
        System.out.println("All concurrency tests passed.");
    }
}
```

**What each test verifies:**
- `testConcurrentMoveConflictDetected`: Two threads snapshot the same `version`, then race to apply a move. The `synchronized (lock)` block in `moveTask` makes the version-check-then-update sequence atomic, so exactly one thread's version check passes; the other observes the already-bumped version and throws `OptimisticLockException` instead of silently overwriting the winner's move.
- `testConcurrentCommentsNoLostUpdate`: A `Collections.synchronizedList` backing `comments` plus the append-only nature of comments (no version gating) means 50 concurrent appends all land — verified by exact count and unique comment IDs, proving no lost updates and no corruption.

---

## Related

**Patterns applied here**

- [Observer Pattern](../../03-design-patterns/03-behavioral/observer-pattern.md)
- [Command Pattern](../../03-design-patterns/03-behavioral/command-pattern.md)

**SOLID focus**: [Single Responsibility](../../02-solid-principles/01-single-responsibility.md)

**Concurrency**: [Concurrency Patterns](../../04-concurrency/concurrency-patterns.md)

**Practice next**

- [Design Order Management](../03-domain-specific/21-design-order-management.md)
- [Design Comment System](../02-frequent-problems/10-design-comment-system.md)

Both reuse the state-transition and activity-log/notification shape.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../01-oop-fundamentals/uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
