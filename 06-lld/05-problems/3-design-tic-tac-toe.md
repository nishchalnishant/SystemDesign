---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, system-design, problems]
---
# Design Tic-Tac-Toe

> **Difficulty**: Beginner
> **Topics**: Object-Oriented Design, Board Game Logic, State Pattern, Command Pattern
> **Extension**: N×N Board, AI Player (Minimax), Undo move

---

## What Breaks Without This Design?

```python
class TicTacToe:
    def __init__(self) -> None:
        self._board: list[list[str | None]] = [[None] * 3 for _ in range(3)]  # None = empty
        self._current_player = "X"
        self._game_over = False

    def make_move(self, row: int, col: int) -> None:
        if self._game_over or self._board[row][col] is not None:
            return

        self._board[row][col] = self._current_player

        # Check win: scan entire board
        for r in range(3):
            if all(self._board[r][c] == self._current_player for c in range(3)):
                self._game_over = True
                return
        for c in range(3):
            if all(self._board[r][c] == self._current_player for r in range(3)):
                self._game_over = True
                return
        if all(self._board[i][i] == self._current_player for i in range(3)):
            self._game_over = True
            return

        self._current_player = "O" if self._current_player == "X" else "X"
```

**Concrete failures**:
1. **Hardcoded 3×3**: Changing to N×N requires rewriting every loop bound and the diagonal checks.
2. **O(N²) win check**: Scanning the entire board on every move is unnecessary — only the row, column, and at most 2 diagonals affected by the last move need checking (O(N)).
3. **No undo**: `board[row][col]` is modified in place with no history. Undo requires saving the board state before every move — but there is nowhere to store it.
4. **AI player impossible to swap in**: The game loop is inside the same class. Swapping in an `AIPlayer` for `currentPlayer == 'O'` requires modifying the game controller logic — there is no `Player` abstraction.
5. **`char` encodes piece type**: Using `'X'` and `'O'` as chars couples the display representation to the game logic. A `Player` object with a `piece` field separates these.

---

## Derive the Class Structure

**Force 1 — N×N board**: The board size is a constructor parameter. Win check scans only the affected row (N checks), affected column (N checks), and up to 2 diagonals (N checks each) = O(N). The board is a `char[][]` (or `Player[][]`) with N as the dimension.

**Force 2 — Undo requires history**: Each move must be reversible. Wrap each move in a `MoveCommand(player, row, col)` with `execute()` (place piece) and `undo()` (clear cell). A `Deque<MoveCommand>` stack in the controller stores history. `undo()` pops and reverses.

**Force 3 — Human and AI players need the same interface**: The game controller calls `player.makeMove(board)` without knowing if it is human or AI. Extract `Player` interface. `HumanPlayer` reads from input; `AIPlayer` runs Minimax. The controller's turn loop is unchanged.

**Force 4 — Win detection belongs on the board, not the controller**: The controller should ask `board.checkWinner(lastRow, lastCol)` — not implement the win-scanning logic itself. Extract `Board` with `place(row, col, player)`, `checkWinner(row, col)`, `isFull()`.

**Result** — the class split these forces produce:
```
God class → GameController (turn loop, delegates to Player + Board, holds undo stack)
          → Board (N×N grid, place/checkWinner/isFull — O(N) win check)
          → Player (interface: Piece getPiece(), int[] chooseMove(Board))
             → HumanPlayer, AIPlayer (Minimax)
          → MoveCommand (player, row, col — execute + undo)
          → Piece (enum: X, O)
```

---

## Opening Analogy

Think of a chess tournament referee. The referee needs to: track whose turn it is (turn management), declare a winner when a row/column/diagonal is complete (board evaluator), and support undoing an illegal move that was accidentally placed (Command pattern with undo). Now shrink the board to 3×3 and you have Tic-Tac-Toe. The structural problem is identical — managing state transitions, validating actions, and detecting terminal conditions.

---

## Phase 1: Requirements

### Functional
- Two players take turns placing their piece (X or O) on a 3×3 grid.
- Validate that the chosen cell is empty and within bounds.
- After each move, detect win (row/column/diagonal match) or draw (board full, no winner).
- Support undo of the last move.
- Board size is configurable (N×N).

### Non-Functional
- Move validation must be O(N) — scan only the row/column touched by the move, not the entire board.
- Undo must be O(1).
- Design must support swapping in an AI player without touching game loop logic.

---

## Phase 2: Use Cases

### Actors
- **Human Player** — inputs a cell coordinate.
- **AI Player** — computes the best cell via strategy algorithm.
- **Game Controller** — enforces turn order, delegates move, checks terminal state.

### UC1: Make Move
1. Active player selects `(row, col)`.
2. Controller validates cell is empty and within bounds.
3. Controller places the piece on the board.
4. Controller runs winner check against the piece just placed (only the affected row, column, and up to two diagonals).
5. If win → announce winner, game over. If draw → announce tie. Otherwise → switch turn.

### UC2: Undo Move
1. Active player requests undo.
2. Controller pops the last `MoveCommand` from the undo stack.
3. Controller sets the cell back to null and restores the previous player's turn.

### UC3: AI Takes Turn
1. Controller calls `player.chooseMove(board)`.
2. `AIPlayer` internally uses a `MoveStrategy` (Random or Minimax) to return `(row, col)`.
3. Same validation and win-check flow as UC1.

---

## Phase 3: Class Diagram

```
┌─────────────────────────────┐
│       TicTacToeGame         │
│─────────────────────────────│
│ - board: Board              │
│ - players: Deque<Player>    │
│ - moveHistory: Deque<Move>  │
│─────────────────────────────│
│ + startGame(): String       │
│ + undoLastMove(): void      │
│ - checkWinner(r,c,t): bool  │
└──────────────┬──────────────┘
               │ uses
     ┌─────────┴──────────┐
     ▼                    ▼
┌─────────┐        ┌────────────┐
│  Board  │        │   Player   │
│─────────│        │────────────│
│ size: N │        │ name       │
│ grid[][]│        │ piece      │
│─────────│        │────────────│
│addPiece │        │chooseMove  │◄── interface
│getCell  │        └─────┬──────┘
│freeCells│              │
└─────────┘    ┌─────────┴────────┐
               ▼                  ▼
         HumanPlayer          AIPlayer
                               - strategy: MoveStrategy
                               
┌──────────────┐   ┌───────────────────┐
│ PlayingPiece │   │  MoveStrategy     │ <<interface>>
│──────────────│   │───────────────────│
│ type: Piece  │   │ chooseMove(Board) │
│   Type enum  │   └──────┬────────────┘
└──────────────┘          │
                 ┌─────────┴──────────┐
                 ▼                    ▼
          RandomStrategy        MinimaxStrategy

┌─────────────────┐
│   MoveCommand   │   <<Command>>
│─────────────────│
│ row, col, piece │
│─────────────────│
│ execute()       │
│ undo()          │
└─────────────────┘
```

**Key relationships:**
- `TicTacToeGame` owns `Board` and a `Deque<Player>` (rotated to manage turns).
- `Player` is an interface — `HumanPlayer` reads stdin; `AIPlayer` delegates to a `MoveStrategy`.
- Each move is wrapped in a `MoveCommand` and pushed to `moveHistory` for undo support.
- `PieceType` is an enum (`X`, `O`); `PlayingPiece` wraps it for polymorphism.

---

## Phase 4: Design Patterns Applied

### 1. Command Pattern — Undo support
**Why:** Every move is a reversible action. Wrapping `(row, col, piece)` as a `MoveCommand` with `execute()` and `undo()` lets us maintain an undo stack at zero extra coupling. The game loop just pushes/pops commands; it never needs to know how undo is implemented.

### 2. Strategy Pattern — Pluggable AI
**Why:** `AIPlayer` should be able to swap between `RandomStrategy` (trivial, for testing) and `MinimaxStrategy` (optimal, for production) without changing the game loop. Open/Closed principle: the game loop calls `player.chooseMove(board)` regardless of implementation.

### 3. State Pattern (implicit) — Turn management
**Why:** The game has three states: `ONGOING`, `WON`, `DRAW`. Encoding these as an enum and checking before each move is clean state-machine thinking. For a more complex game, each state would be a full class that handles its own transitions.

---

## Phase 5: Key Python Implementation

```python
from __future__ import annotations
from abc import ABC, abstractmethod
from collections import deque
from enum import Enum, auto
from dataclasses import dataclass

# ── Enums ──────────────────────────────────────────────────────────────────

class PieceType(Enum):
    X = auto()
    O = auto()

class GameState(Enum):
    ONGOING = auto()
    WON = auto()
    DRAW = auto()

# ── Piece & Players ────────────────────────────────────────────────────────

@dataclass
class PlayingPiece:
    piece_type: PieceType

class Player(ABC):
    @property
    @abstractmethod
    def name(self) -> str: ...

    @property
    @abstractmethod
    def piece(self) -> PlayingPiece: ...

    @abstractmethod
    def choose_move(self, board: "Board") -> tuple[int, int]: ...

class HumanPlayer(Player):
    def __init__(self, name: str, piece_type: PieceType) -> None:
        self._name = name
        self._piece = PlayingPiece(piece_type)

    @property
    def name(self) -> str:
        return self._name

    @property
    def piece(self) -> PlayingPiece:
        return self._piece

    def choose_move(self, board: "Board") -> tuple[int, int]:
        raw = input(f"{self._name}, enter row,col: ")
        r, c = raw.split(",")
        return int(r), int(c)

# ── Command Pattern: Move ──────────────────────────────────────────────────

@dataclass
class MoveCommand:
    row: int
    col: int
    piece: PlayingPiece

    def execute(self, board: "Board") -> None:
        board.set_cell(self.row, self.col, self.piece)

    def undo(self, board: "Board") -> None:
        board.set_cell(self.row, self.col, None)

# ── Board ──────────────────────────────────────────────────────────────────

class Board:
    def __init__(self, size: int) -> None:
        self.size = size
        self._grid: list[list[PlayingPiece | None]] = [[None] * size for _ in range(size)]

    def add_piece(self, row: int, col: int, piece: PlayingPiece) -> bool:
        if not (0 <= row < self.size and 0 <= col < self.size):
            return False
        if self._grid[row][col] is not None:
            return False
        self._grid[row][col] = piece
        return True

    def set_cell(self, row: int, col: int, piece: PlayingPiece | None) -> None:
        self._grid[row][col] = piece

    def get_cell(self, row: int, col: int) -> PlayingPiece | None:
        return self._grid[row][col]

    def get_free_cells(self) -> list[tuple[int, int]]:
        return [
            (i, j)
            for i in range(self.size)
            for j in range(self.size)
            if self._grid[i][j] is None
        ]

    def print(self) -> None:
        for row in self._grid:
            print(" ".join("." if cell is None else cell.piece_type.name for cell in row))

# ── Game Controller ────────────────────────────────────────────────────────

class TicTacToeGame:
    def __init__(self, size: int, player_list: list[Player]) -> None:
        self._board = Board(size)
        self._players: deque[Player] = deque(player_list)
        self._move_history: deque[MoveCommand] = deque()
        self._state = GameState.ONGOING

    def start_game(self) -> str:
        while self._state == GameState.ONGOING:
            self._board.print()
            current = self._players[0]

            row, col = current.choose_move(self._board)

            if not self._board.add_piece(row, col, current.piece):
                print("Invalid move — try again.")
                continue

            # Record for undo
            cmd = MoveCommand(row, col, current.piece)
            self._move_history.append(cmd)

            if self._is_winner(row, col, current.piece.piece_type):
                self._state = GameState.WON
                return f"{current.name} wins!"
            if not self._board.get_free_cells():
                self._state = GameState.DRAW
                return "It's a draw."

            # Rotate players
            self._players.rotate(-1)

        return "Game over."

    # O(N) — only checks the row/column/diagonals touched by this move
    def _is_winner(self, row: int, col: int, piece_type: PieceType) -> bool:
        n = self._board.size

        def cell_matches(r: int, c: int) -> bool:
            cell = self._board.get_cell(r, c)
            return cell is not None and cell.piece_type == piece_type

        row_win  = all(cell_matches(row, i) for i in range(n))
        col_win  = all(cell_matches(i, col) for i in range(n))
        diag_win = all(cell_matches(i, i) for i in range(n))
        anti_win = all(cell_matches(i, n - 1 - i) for i in range(n))
        return row_win or col_win or diag_win or anti_win

    # Undo last move and give the turn back to the previous player
    def undo_last_move(self) -> None:
        if not self._move_history:
            print("Nothing to undo.")
            return
        last = self._move_history.pop()
        last.undo(self._board)
        # Rotate players back
        self._players.rotate(1)
        self._state = GameState.ONGOING
        print("Move undone.")
```

---

## Phase 6: Trade-offs and Extensions

### Trade-offs

| Decision | Choice | Alternative | Reason |
|---|---|---|---|
| Win check scope | Only affected row/col/diag | Scan full board | O(N) vs O(N²); move narrows what must be checked |
| Turn management | `Deque` rotation | Index counter | Deque naturally supports undo (re-insert front) |
| Undo storage | Command stack | Board snapshots | Command stack is O(1) per move; snapshots are O(N²) |

### Extensions

**Scale to N×N with K-in-a-row win rule:**
Pass `winLength` to `isWinner` and check only a sliding window of size K instead of full lines.

**AI opponent (Minimax):**
```python
from abc import ABC, abstractmethod

class MoveStrategy(ABC):
    @abstractmethod
    def choose_move(self, board: Board, my_type: PieceType) -> tuple[int, int]: ...

class MinimaxStrategy(MoveStrategy):
    def choose_move(self, board: Board, my_type: PieceType) -> tuple[int, int]:
        # For each free cell: simulate move, recurse, score, backtrack
        # Alpha-beta pruning cuts branches where outcome is already determined
        # O(b^d) base, O(b^(d/2)) with alpha-beta
        ...
```
Wire it into `AIPlayer` via constructor injection — zero changes to the game loop.

**Tournament mode:**
Wrap `TicTacToeGame` in a `Tournament` class. Track wins per player across N games. Strategy pattern already decouples AI difficulty per player.

**Persistence:**
Serialize `moveHistory` (it is a stack of simple value objects) to reconstruct any game state.
