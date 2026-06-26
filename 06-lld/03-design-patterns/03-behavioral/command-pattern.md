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
    def make_move(self, row: int, col: int, player: str) -> None:
        self._board[row][col] = player
        self._history.append((row, col, player))

    def undo(self) -> None:
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
    def execute(self) -> None: ...

    @abstractmethod
    def undo(self) -> None: ...


class Board:
    def __init__(self, size: int = 3) -> None:
        self._cells: list[list[str]] = [[""] * size for _ in range(size)]

    def place(self, row: int, col: int, player: str) -> None:
        if self._cells[row][col]:
            raise ValueError(f"Cell ({row},{col}) already occupied")
        self._cells[row][col] = player

    def clear(self, row: int, col: int) -> None:
        self._cells[row][col] = ""

    def display(self) -> None:
        for row in self._cells:
            print("|".join(c or "." for c in row))


class PlacePieceCommand(Command):
    def __init__(self, board: Board, row: int, col: int, player: str) -> None:
        self._board = board
        self._row = row
        self._col = col
        self._player = player

    def execute(self) -> None:
        self._board.place(self._row, self._col, self._player)

    def undo(self) -> None:
        self._board.clear(self._row, self._col)


class GameInvoker:
    def __init__(self) -> None:
        self._history: list[Command] = []

    def execute(self, command: Command) -> None:
        command.execute()
        self._history.append(command)

    def undo(self) -> None:
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
    def __init__(self) -> None:
        self._queue: queue.Queue[Command] = queue.Queue()
        self._worker = threading.Thread(target=self._process, daemon=True)
        self._worker.start()

    def submit(self, command: Command) -> None:
        self._queue.put(command)

    def _process(self) -> None:
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
