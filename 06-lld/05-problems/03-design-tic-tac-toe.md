---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, tic-tac-toe, strategy, state-machine, game]
---
# Design Tic-Tac-Toe

> **Difficulty**: Easy  
> **Asked at**: Amazon, Apple, Airbnb  
> **Key Patterns**: Strategy (win checker), State enum, counter-based O(1) win detection

---

## Understanding the Problem

Design a two-player Tic-Tac-Toe game on an NxN board where players alternate placing marks, and the system detects wins (row, column, diagonal) and draws after each move.

---

## Clarifying Questions

**You**: "Is this always 3x3, or should the board size be configurable?"  
**Interviewer**: "Start with 3x3, but design it so NxN works without major refactoring."

**You**: "Two human players, or do we need an AI opponent?"  
**Interviewer**: "Two human players for now. AI is a potential follow-up."

**You**: "What win condition — exactly N in a row, or connect-K where K can differ from N?"  
**Interviewer**: "Exactly N in a row for now. K == N."

**You**: "What should happen if a player tries to play on an occupied cell?"  
**Interviewer**: "Reject the move and return an error — don't change game state."

**You**: "Do we need to support undo?"  
**Interviewer**: "Not required, but mention how you'd add it."

---

## Final Requirements

**In scope:**
1. NxN board, configurable at construction
2. Two players alternate turns (X goes first)
3. `make_move(player, row, col)` — validates and applies a move
4. Win detection after each move (row, column, two diagonals)
5. Draw detection when board is full with no winner
6. Game state tracking: IN_PROGRESS, X_WINS, O_WINS, DRAW

**Out of scope:**
- AI opponent
- Undo/redo
- Persistence or replays
- Connect-K variant (K != N)

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|---------------|
| Game | Orchestrates turns, enforces order, tracks game state |
| Board | Holds cell state, delegates win checking, tracks move count |
| Player | Holds name and mark (X or O) |
| WinChecker | Detects win condition using row/col/diagonal counters |
| GameState | Enum: IN_PROGRESS, X_WINS, O_WINS, DRAW |

Game owns Board and two Players. After each `make_move`, Game asks WinChecker if the last-moved player has won. WinChecker uses counters (not board scans) for O(1) detection.

---

## Class Design

### Player

```
class Player:
- name: str
- mark: Mark  # enum: X, O

+ get_mark() -> Mark
```

### Board

| Requirement | What Board must track |
|-------------|----------------------|
| Cell state | grid: list[list[Mark | None]] |
| Win detection without scanning | row_counts, col_counts, diag_count, anti_diag_count: dict/list |
| Full-board detection | move_count: int |
| Board size | size: int |

```
class Board:
- size: int
- grid: list[list[Mark | None]]
- row_counts: dict[Mark, list[int]]     # mark -> [count per row]
- col_counts: dict[Mark, list[int]]     # mark -> [count per col]
- diag_count: dict[Mark, int]           # mark -> count on main diagonal
- anti_diag_count: dict[Mark, int]      # mark -> count on anti-diagonal
- move_count: int

+ place(mark: Mark, row: int, col: int) -> None
+ is_cell_empty(row: int, col: int) -> bool
+ is_winner(mark: Mark, row: int, col: int) -> bool
+ is_full() -> bool
```

### WinChecker

```
class WinChecker:
+ check(board: Board, mark: Mark, row: int, col: int) -> bool
```

### Game

```
class Game:
- board: Board
- players: list[Player]    # [player_x, player_o]
- current_player_idx: int
- state: GameState
- win_checker: WinChecker

+ make_move(player: Player, row: int, col: int) -> GameState
+ get_current_player() -> Player
+ get_state() -> GameState
```

---

## Implementation

### Core Method: make_move

**Core logic:**
1. Validate game is still IN_PROGRESS
2. Validate it is this player's turn
3. Validate cell (row, col) is within bounds and empty
4. Place mark on board (updates counters)
5. Check win using counter comparison (O(1))
6. Check draw if board is full
7. Advance turn if game continues

**Edge cases:**
- Move out of bounds — raise IndexError
- Cell already occupied — raise ValueError
- Move by wrong player — raise ValueError
- Move after game ended — raise GameOverError

```python
def make_move(self, player: Player, row: int, col: int) -> GameState:
    if self.state != GameState.IN_PROGRESS:
        raise GameOverError("Game is already over")

    if player != self.players[self.current_player_idx]:
        raise ValueError(f"It's not {player.name}'s turn")

    if not (0 <= row < self.board.size and 0 <= col < self.board.size):
        raise IndexError(f"Position ({row},{col}) is out of bounds")

    if not self.board.is_cell_empty(row, col):
        raise ValueError(f"Cell ({row},{col}) is already occupied")

    self.board.place(player.mark, row, col)

    if self.win_checker.check(self.board, player.mark, row, col):
        self.state = GameState.X_WINS if player.mark == Mark.X else GameState.O_WINS
        return self.state

    if self.board.is_full():
        self.state = GameState.DRAW
        return self.state

    self.current_player_idx = 1 - self.current_player_idx
    return self.state
```

### Core Method: Board.place + O(1) win detection

**Core logic (place):**
1. Set `grid[row][col] = mark`
2. Increment `row_counts[mark][row]` and `col_counts[mark][col]`
3. If on main diagonal (row == col), increment `diag_count[mark]`
4. If on anti-diagonal (row + col == size - 1), increment `anti_diag_count[mark]`
5. Increment `move_count`

**Win check:**
- A win occurs if any counter reaches `size`

```python
def place(self, mark: Mark, row: int, col: int) -> None:
    self.grid[row][col] = mark
    self.row_counts[mark][row] += 1
    self.col_counts[mark][col] += 1
    if row == col:
        self.diag_count[mark] += 1
    if row + col == self.size - 1:
        self.anti_diag_count[mark] += 1
    self.move_count += 1

def is_winner(self, mark: Mark, row: int, col: int) -> bool:
    n = self.size
    return (
        self.row_counts[mark][row] == n or
        self.col_counts[mark][col] == n or
        self.diag_count[mark] == n or
        self.anti_diag_count[mark] == n
    )

def is_full(self) -> bool:
    return self.move_count == self.size * self.size
```

### WinChecker (delegates to Board)

```python
class WinChecker:
    def check(self, board: Board, mark: Mark, row: int, col: int) -> bool:
        return board.is_winner(mark, row, col)
```

---

## Verification

**Scenario**: 3x3 board, X wins on top row.

Initial state: all counters = 0, move_count = 0

1. X plays (0,0): `row_counts[X][0]=1`, `col_counts[X][0]=1`, `diag_count[X]=1`. No win (need 3). O's turn.
2. O plays (1,0): `row_counts[O][1]=1`, `col_counts[O][0]=1`. No win. X's turn.
3. X plays (0,1): `row_counts[X][0]=2`, `col_counts[X][1]=1`. No win. O's turn.
4. O plays (2,2): `row_counts[O][2]=1`, `col_counts[O][2]=1`, `diag_count[O]=1`. No win. X's turn.
5. X plays (0,2): `row_counts[X][0]=3` → **equals size(3)** → `is_winner` returns True
6. `state = X_WINS`. Game over.

Total win check cost: O(1) — just compare counter to N.

---

## Deep Dive & Extensibility

### 1. "Why is the counter approach O(1) instead of scanning the board?"

Naive win detection scans the row, column, and both diagonals of the last move — O(N) per move, O(N²) total. With counters:

- Each `place()` updates at most 4 counters (row, col, diag, anti-diag) in O(1)
- `is_winner()` checks at most 4 counter values in O(1)

The key insight: we only need to know if the *last move's* row/col/diagonal is complete. We don't need to know the whole board state. Counters track the running total per line, per mark.

For an N=1000 board this difference is critical — O(1) vs O(1000) per move.

### 2. "How would you generalize this to NxN with connect-K (K != N)?"

Current counters track consecutive counts along a full line. For connect-K, you need to track **consecutive** runs, not total marks per line (a line could have alternating X and O).

Use a different approach — sliding window or DFS from the last move:

```python
def check_k_consecutive(board, mark, row, col, k):
    directions = [(0,1), (1,0), (1,1), (1,-1)]  # right, down, diag, anti-diag
    for dr, dc in directions:
        count = 1
        # extend in positive direction
        r, c = row + dr, col + dc
        while board.in_bounds(r, c) and board.grid[r][c] == mark:
            count += 1
            r, c = r + dr, c + dc
        # extend in negative direction
        r, c = row - dr, col - dc
        while board.in_bounds(r, c) and board.grid[r][c] == mark:
            count += 1
            r, c = r - dr, c - dc
        if count >= k:
            return True
    return False
```

This is O(K) per move worst case — acceptable for games, and unavoidable when K < N since a partial line must be checked.

### 3. "How would you add an AI opponent using minimax?"

Add a `MinimaxPlayer` that implements the same `Player` interface but computes its move:

```python
class MinimaxPlayer(Player):
    def get_move(self, board: Board) -> tuple[int, int]:
        best_score = float('-inf')
        best_move = None
        for row, col in board.empty_cells():
            board.place(self.mark, row, col)
            score = self._minimax(board, depth=0, is_maximizing=False)
            board.undo(row, col)
            if score > best_score:
                best_score, best_move = score, (row, col)
        return best_move

    def _minimax(self, board, depth, is_maximizing):
        winner = board.get_winner()
        if winner == self.mark:
            return 10 - depth
        if winner is not None:
            return depth - 10
        if board.is_full():
            return 0
        if is_maximizing:
            return max(
                self._minimax(board_after_move, depth+1, False)
                for move in board.empty_cells()
            )
        else:
            return min(
                self._minimax(board_after_move, depth+1, True)
                for move in board.empty_cells()
            )
```

Add alpha-beta pruning to reduce the search tree from O(b^d) to O(b^(d/2)). For 3x3 this is overkill (only 9! = 362,880 states), but for N=5 it matters significantly.

### 4. "How would you add undo functionality?"

Replace grid mutations with a move stack:

```python
class Board:
    def __init__(self):
        ...
        self.move_stack: list[tuple[Mark, int, int]] = []

    def place(self, mark, row, col):
        # same counter updates
        self.move_stack.append((mark, row, col))

    def undo(self):
        if not self.move_stack:
            raise ValueError("No moves to undo")
        mark, row, col = self.move_stack.pop()
        self.grid[row][col] = None
        self.row_counts[mark][row] -= 1
        self.col_counts[mark][col] -= 1
        if row == col:
            self.diag_count[mark] -= 1
        if row + col == self.size - 1:
            self.anti_diag_count[mark] -= 1
        self.move_count -= 1
```

In Game, `undo_move()` pops the move stack, reverts the board, and switches `current_player_idx` back. Counters unwind cleanly since they were incremented atomically.

---

## Interviewer Questions by Level

**Junior**: Draw a 3x3 board and trace through a winning game. Define the GameState enum. Explain why you need to check rows, columns, and two diagonals.

**Mid-level**: Implement `make_move` with all validations. Explain and implement the counter-based O(1) win detection. Correctly handle the anti-diagonal condition (`row + col == size - 1`).

**Senior**: Generalize to connect-K. Add undo with correct counter reversal. Sketch minimax with alpha-beta pruning. Discuss how NxN with large N changes the win check strategy.

---

## Common Interview Questions

- Q: How do you detect a win in O(1) instead of O(N²)? A: Maintain four counters per mark per player (row count, col count, diagonal count, anti-diagonal count). After each move, increment the relevant counters and check if any equals N — no board scan needed.
- Q: What is the anti-diagonal condition? A: A cell (row, col) is on the anti-diagonal if `row + col == size - 1`. For a 3x3 board: (0,2), (1,1), (2,0).
- Q: What happens on an NxN board — does the algorithm still work? A: Yes. The counter approach scales to any N. Initialize `row_counts` and `col_counts` as lists of size N. The win threshold is always N. O(1) per move, O(N²) space for the grid.
- Q: How do you distinguish a draw from a game still in progress? A: Track `move_count`. When `move_count == N*N` and no winner has been detected, state is DRAW. Check win first — a move that fills the last cell and wins is X_WINS or O_WINS, not DRAW.
- Q: How would you add undo? A: Push each move to a stack. On undo, pop and reverse: set grid cell to None, decrement the four counters, decrement move_count, switch turns back.
- Q: What's wrong with storing who won as a boolean? A: Three outcomes exist: X wins, O wins, draw. A boolean can't represent all three. Use a GameState enum with at least 4 values: IN_PROGRESS, X_WINS, O_WINS, DRAW.
