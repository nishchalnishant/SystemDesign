---
module: 06-lld
topic: Design Patterns
status: unread
tags: [06-lld, system-design, design-patterns, behavioral]
---
# Command Pattern

## Intent

Encapsulate a request as an object. This lets you parameterize operations, queue them, log them, and support undo/redo.

---

## When to Use

- You need undo/redo (each command stores how to reverse itself).
- You need a request queue or job scheduler (commands are objects — enqueue them).
- You need to log or replay operations (commands are serializable actions).
- You want to decouple the object that issues a request from the object that executes it.

**Real examples**: Tic-tac-toe undo, text editor undo/redo, task queue worker, database transaction log, macro recorder.

---

## What Breaks Without It

```python
class TicTacToeGame:
    def make_move(self, row, col, player):
        self._board[row][col] = player
        self._history.append((row, col, player))

    def undo(self):
        if not self._history:
            return
        row, col, _ = self._history.pop()
        self._board[row][col] = ""  # hardcoded reversal logic here
```

Adding a new move type (swap two cells, bomb a 3×3 area) requires editing `undo()` to handle every new case. Undo logic is not colocated with the move it reverses — it drifts out of sync.

---

## Structure

```
Command <<interface>>
  + execute(): void
  + undo(): void

ConcreteCommand(Command)
  - receiver: Receiver    // the object that knows how to do the work
  - state: ...            // saved state needed for undo

Invoker
  - history: list[Command]
  + execute_command(cmd): void
  + undo(): void

Receiver
  + action(): void        // the actual work
```

---

## Python Implementation

### Tic-Tac-Toe with Undo

```python
from abc import ABC, abstractmethod

class Command(ABC):
    @abstractmethod
    def execute(self): ...

    @abstractmethod
    def undo(self): ...


class Board:
    def __init__(self, size=3):
        self._cells = [[""] * size for _ in range(size)]

    def place(self, row, col, player):
        if self._cells[row][col]:
            raise ValueError(f"Cell ({row},{col}) already occupied")
        self._cells[row][col] = player

    def clear(self, row, col):
        self._cells[row][col] = ""

    def display(self):
        for row in self._cells:
            print("|".join(c or "." for c in row))


class PlacePieceCommand(Command):
    def __init__(self, board, row, col, player):
        self._board = board
        self._row = row
        self._col = col
        self._player = player

    def execute(self):
        self._board.place(self._row, self._col, self._player)

    def undo(self):
        self._board.clear(self._row, self._col)


class GameInvoker:
    def __init__(self):
        self._history = []  # list of Command

    def execute(self, command):
        command.execute()
        self._history.append(command)

    def undo(self):
        if not self._history:
            return
        self._history.pop().undo()
```

### Usage

```python
board = Board()
game = GameInvoker()

game.execute(PlacePieceCommand(board, 0, 0, "X"))
game.execute(PlacePieceCommand(board, 1, 1, "O"))
board.display()

game.undo()  # removes O from (1,1)
board.display()
```

---

## Task Queue Variant

Commands work naturally as queue items — the invoker is now a worker thread.

```python
import queue
import threading

class TaskQueue:
    def __init__(self):
        self._queue = queue.Queue()  # queue of Command objects
        self._worker = threading.Thread(target=self._process, daemon=True)
        self._worker.start()

    def submit(self, command):
        self._queue.put(command)

    def _process(self):
        while True:
            cmd = self._queue.get()
            try:
                cmd.execute()
            except Exception as e:
                print(f"Command failed: {e}")
            finally:
                self._queue.task_done()
```

---

## Design Patterns Comparison

| Pattern | Similarity | Difference |
|---------|-----------|------------|
| **Strategy** | Encapsulates behavior in an object | Strategy replaces an algorithm; Command encapsulates a one-time request with state for undo |
| **Memento** | Used with undo | Memento saves/restores whole object state; Command stores the minimal delta needed to reverse |
| **Chain of Responsibility** | Request as object | CoR routes a request to a handler; Command decouples who issues from who executes |

---

## Key Points for Interview

- **Undo is the killer use case**: Every `Command` stores the exact state needed to reverse itself. No central switch statement.
- **execute() + undo() must be symmetric**: If `execute()` places X at (0,0), `undo()` must clear (0,0). Verify this for every concrete command.
- **History stack**: `GameInvoker` pops on undo, pushes on execute. For redo support, add a redo stack — on undo, push to redo; on execute new command, clear redo stack.
- **Used in**: Tic-tac-toe (problem 11 — undo/redo), task queues, transaction logs, macro systems.

---

## Interviewer Follow-Up Questions

- "What does the Command pattern give you that a direct method call doesn't?" → Encapsulates a request as an object, enabling: (1) Undo/redo — store executed commands, call `command.undo()` to reverse. (2) Queuing — commands are serializable objects; put them in a queue, execute later. (3) Logging — each command is an object with a timestamp and parameters; log them for audit trail or replay. (4) Macro recording — compose multiple commands into a `MacroCommand`. Direct method calls give you none of these.
- "How does Command enable undo/redo?" → Each `Command` has `execute()` and `undo()`. `execute()` performs the operation and saves state needed to reverse it. `undo()` reverses the operation. A `CommandHistory` stack holds executed commands. Ctrl+Z: pop the top command, call `undo()`. Ctrl+Y: re-execute. For `DeleteTextCommand`: `execute()` stores the deleted text; `undo()` re-inserts it. The history stack bounds memory usage (keep last N commands).
- "How is Command used in a job queue system?" → Command objects are serialized (JSON/Protobuf), enqueued to SQS/Kafka/Redis. Workers deserialize and call `execute()`. The queue gives async execution, retry on failure, and deferred execution — all enabled by the fact that the command is an object, not an immediate call. Examples: email sending command, report generation command, payment command (retry-safe because Command can include idempotency key).
- "What's a macro command?" → A `MacroCommand` implements the `Command` interface but holds a list of sub-commands. `execute()` calls `execute()` on each in sequence. `undo()` calls `undo()` in reverse order. This is the Composite pattern applied to Command — a command tree. Use case: multi-step transaction (create order + reserve inventory + charge payment as a single undoable macro), or test setup (a sequence of setup commands that can be torn down in reverse).
