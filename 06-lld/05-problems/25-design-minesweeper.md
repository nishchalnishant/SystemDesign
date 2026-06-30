> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design Minesweeper — tests your ability to model a game board, handle cell states, and implement recursive flood-fill algorithms (DFS/BFS).
>
> **Key concepts:**
> - Core Entities: `Game`, `Board`, `Cell`.
> - State Pattern (Cell): A `Cell` can be `HIDDEN`, `REVEALED`, or `FLAGGED`. It also holds its content (`MINE` or `NUMBER_1_TO_8`).
> - The Setup Phase: Placing $M$ mines randomly on an $N \times N$ board, then calculating the adjacent mine count for all non-mine cells.
> - The Game Loop (Flood Fill): When a user clicks a `HIDDEN` cell:
>   - If it's a mine: Game Over.
>   - If it's a number: Reveal just that cell.
>   - If it's empty (0 adjacent mines): Reveal it, then recursively (or using a queue/BFS) reveal all its 8 neighbors. If any neighbor is also a 0, continue the recursion.
>
> **Key takeaway:** The recursion (Flood Fill) is the core algorithmic challenge. Ensure you check boundary conditions (`x < 0 || y >= N`) and only recurse on `HIDDEN` cells to prevent infinite loops.

---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, minesweeper, flood-fill, bfs, dfs, recursion]
---
# Design Minesweeper

> **Difficulty**: Medium
> **Asked at**: Amazon, Google, Meta
> **Key Patterns**: Flood Fill (BFS/DFS), State Machine (cell state), Factory (board setup)

---

## Understanding the Problem

Design the classic Minesweeper game. The player reveals cells on a grid; unrevealed mines explode and end the game; blank cells auto-expand via flood fill; revealed mine counts guide the player.

---

## Clarifying Questions

**You**: "What are the grid dimensions and mine count?"
**Interviewer**: "Configurable: rows, cols, and num_mines."

**You**: "Do we use BFS or DFS for flood fill?"
**Interviewer**: "Either works — explain your choice."

**You**: "Is this backend logic or do we need rendering?"
**Interviewer**: "Backend only."

**You**: "What about first-click safety (never hit a mine on the first reveal)?"
**Interviewer**: "Nice catch — yes, guarantee first click is safe."

**You**: "Do we need a flagging mechanic?"
**Interviewer**: "Yes — player can flag cells they suspect are mines."

---

## Final Requirements

**In scope:**
1. Configurable grid: rows × cols with num_mines
2. Reveal a cell: mine → GAME_OVER; blank → BFS flood fill; numbered → show count
3. First click guaranteed safe (re-place mine if needed)
4. Flag/unflag cells
5. Win detection: all non-mine cells revealed

**Out of scope:**
- UI rendering
- Multiplayer
- Timer / leaderboard
- Hint mode

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|---------------|
| `Board` | Grid of cells; mine placement; flood fill |
| `Cell` | State (HIDDEN, REVEALED, FLAGGED), is_mine, adjacent_mine_count |
| `CellState` | Enum: HIDDEN, REVEALED, FLAGGED |
| `Game` | Orchestrates reveal/flag, tracks state (IN_PROGRESS, WON, LOST) |
| `GameState` | Enum: IN_PROGRESS, WON, LOST |
| `MinesPlacer` | Randomly places mines (excludes first-click cell) |

`Game` delegates grid logic to `Board`. Board computes adjacent counts at setup. Flood fill is iterative BFS on `Board.reveal()`.

---

## Class Design

### Cell

| Requirement | What Cell must track |
|-------------|---------------------|
| "Is mine" | is_mine: bool |
| "Adjacent count" | adjacent_mines: int (0–8) |
| "Reveal/flag" | state: CellState |

```
class Cell:
- is_mine: bool
- adjacent_mines: int
- state: CellState

+ is_hidden() -> bool
+ is_revealed() -> bool
+ is_flagged() -> bool
```

### Board

```
class Board:
- rows: int
- cols: int
- grid: list[list[Cell]]

+ Board(rows, cols, num_mines, safe_cell=None)
+ reveal(row, col) -> RevealResult   # MINE, BLANK, NUMBER
+ flag(row, col) -> bool
+ unflag(row, col) -> bool
+ get_cell(row, col) -> Cell
+ is_all_revealed() -> bool
+ _place_mines(num_mines, exclude: set[tuple])
+ _compute_adjacent_counts()
+ _flood_fill(row, col)
```

### Game

```
class Game:
- board: Board
- state: GameState
- rows: int
- cols: int
- num_mines: int
- first_move: bool

+ Game(rows, cols, num_mines)
+ reveal(row, col) -> GameState
+ flag(row, col) -> bool
+ get_state() -> GameState
```

---

## Implementation

### Core Method: `Game.reveal`

**Core logic:**
1. First click: ensure safe — if cell is mine, re-place mines excluding this cell
2. Call `board.reveal(row, col)`
3. If result is MINE → set state = LOST, reveal all mines
4. If WIN → set state = WON
5. Return current game state

**Edge cases:**
- Already revealed cell → no-op
- Flagged cell → no-op (player must unflag first)
- Out of bounds → raise error

```python
def reveal(self, row, col):
    if self.state != GameState.IN_PROGRESS:
        return self.state

    cell = self.board.get_cell(row, col)
    if cell.is_revealed() or cell.is_flagged():
        return self.state

    # First-click safety
    if self.first_move:
        self.first_move = False
        if cell.is_mine:
            self.board.relocate_mine(row, col)

    result = self.board.reveal(row, col)

    if result == RevealResult.MINE:
        self.state = GameState.LOST
        self.board.reveal_all_mines()
    elif self.board.is_all_revealed():
        self.state = GameState.WON

    return self.state
```

### Core Method: `Board.reveal` with BFS flood fill

**Core logic:**
- If mine: return MINE
- If already revealed: return NUMBER (no-op)
- Mark cell as REVEALED
- If adjacent_mines > 0: return NUMBER (stop here)
- If adjacent_mines == 0: BFS to reveal all connected blank cells

**Edge cases:**
- Guard against revisiting cells in BFS (use visited set or check cell.is_revealed())

```python
def reveal(self, row, col):
    cell = self.grid[row][col]
    if cell.is_mine:
        cell.state = CellState.REVEALED
        return RevealResult.MINE

    if cell.adjacent_mines > 0:
        cell.state = CellState.REVEALED
        return RevealResult.NUMBER

    # BFS flood fill for blank cells
    self._flood_fill(row, col)
    return RevealResult.BLANK

def _flood_fill(self, start_row, start_col):
    queue = deque([(start_row, start_col)])
    visited = set()

    while queue:
        r, c = queue.popleft()
        if (r, c) in visited:
            continue
        visited.add((r, c))

        if not (0 <= r < self.rows and 0 <= c < self.cols):
            continue

        cell = self.grid[r][c]
        if cell.state == CellState.REVEALED or cell.is_mine:
            continue

        cell.state = CellState.REVEALED

        if cell.adjacent_mines == 0:
            for dr, dc in [(-1,-1),(-1,0),(-1,1),(0,-1),(0,1),(1,-1),(1,0),(1,1)]:
                queue.append((r + dr, c + dc))
```

### Mine placement and adjacent counts

```python
def _place_mines(self, num_mines, exclude):
    all_cells = [
        (r, c)
        for r in range(self.rows)
        for c in range(self.cols)
        if (r, c) not in exclude
    ]
    mine_positions = random.sample(all_cells, num_mines)
    for r, c in mine_positions:
        self.grid[r][c].is_mine = True

def _compute_adjacent_counts(self):
    for r in range(self.rows):
        for c in range(self.cols):
            if not self.grid[r][c].is_mine:
                count = sum(
                    1
                    for dr, dc in [(-1,-1),(-1,0),(-1,1),(0,-1),(0,1),(1,-1),(1,0),(1,1)]
                    if 0 <= r+dr < self.rows
                    and 0 <= c+dc < self.cols
                    and self.grid[r+dr][c+dc].is_mine
                )
                self.grid[r][c].adjacent_mines = count
```

---

## Verification

```
3×3 board, 1 mine at (0,0)
Adjacent counts after setup:
  (0,0)=mine  (0,1)=1     (0,2)=0
  (1,0)=1     (1,1)=1     (1,2)=0
  (2,0)=0     (2,1)=0     (2,2)=0

Game.reveal(2, 2):
  Not a mine, adjacent_mines=0 → flood fill
  BFS queue: [(2,2)]
    (2,2): adjacent=0 → reveal, enqueue neighbors
    (2,1): adjacent=0 → reveal, enqueue neighbors
    (2,0): adjacent=0 → reveal, enqueue neighbors
    (1,2): adjacent=0 → reveal, enqueue neighbors
    (1,1): adjacent=1 → reveal, stop (no further enqueue)
    (1,0): adjacent=1 → reveal, stop
    (0,2): adjacent=0 → reveal, enqueue neighbors
    (0,1): adjacent=1 → reveal, stop
    (0,0): is_mine → skip (never revealed)
  
  Revealed: all cells except (0,0)
  is_all_revealed(): 8/9 non-mine cells revealed = True
  state = GameState.WON
```

---

## Deep Dive & Extensibility

### 1. "BFS vs DFS for flood fill — which is better?"

Both work. BFS (queue) is iterative and avoids Python's recursion stack limit. DFS (recursive) is simpler to write but risks `RecursionError` on large grids (e.g., 100×100 blank board = 10,000 recursive calls).

**Recommendation**: BFS for production code.

```python
# DFS (recursive) — simpler but risky for large grids
def _flood_fill_dfs(self, r, c, visited):
    if (r, c) in visited or not self._in_bounds(r, c):
        return
    cell = self.grid[r][c]
    if cell.state == CellState.REVEALED or cell.is_mine:
        return
    visited.add((r, c))
    cell.state = CellState.REVEALED
    if cell.adjacent_mines == 0:
        for dr, dc in [(-1,-1),(-1,0),(-1,1),(0,-1),(0,1),(1,-1),(1,0),(1,1)]:
            self._flood_fill_dfs(r+dr, c+dc, visited)
```

To make DFS safe on large grids: use `sys.setrecursionlimit()` or convert to iterative with an explicit stack.

### 2. "How would you implement first-click safety?"

Two approaches:

**A — Lazy placement**: Don't place mines until after the first click. Place mines excluding the clicked cell and its neighbors.

```python
def reveal(self, row, col):
    if self.first_move:
        self.first_move = False
        exclude = {(row, col)} | self._neighbors(row, col)
        self.board.place_mines_excluding(exclude)
        self.board._compute_adjacent_counts()
    # ... rest of reveal ...
```

**B — Relocate**: Place mines upfront. If first click hits a mine, move that mine to a random non-mine cell.

Approach A is cleaner — board setup is deferred, no re-computation needed.

### 3. "How would you add a hint system?"

Hint = reveal the cell with the highest probability of being safe. Use constraint propagation: for each numbered cell, if `adjacent_mines == count_of_flagged_adjacent`, all other adjacent hidden cells are safe.

```python
def get_hint(self):
    for r in range(self.rows):
        for c in range(self.cols):
            cell = self.grid[r][c]
            if cell.is_revealed() and cell.adjacent_mines > 0:
                hidden = [n for n in self._neighbors(r,c) if self.grid[n[0]][n[1]].is_hidden()]
                flagged = [n for n in self._neighbors(r,c) if self.grid[n[0]][n[1]].is_flagged()]
                if len(flagged) == cell.adjacent_mines and hidden:
                    return hidden[0]  # safe to reveal
    return None  # no deterministic hint
```

### 4. "How would you validate the win condition efficiently?"

Rather than scanning all cells after every reveal, track a counter:

```python
class Board:
    def __init__(self):
        self.unrevealed_non_mine_count = rows * cols - num_mines

    def reveal_cell(self, cell):
        cell.state = CellState.REVEALED
        self.unrevealed_non_mine_count -= 1

    def is_all_revealed(self):
        return self.unrevealed_non_mine_count == 0
```

`is_all_revealed()` is O(1) instead of O(rows × cols).

---

## Interviewer Questions by Level

**Junior**: Board with Cell grid. Reveal a cell — return mine or number. Basic win check (all non-mines revealed).

**Mid-level**: BFS flood fill for blank cells. Adjacent mine count computation. Flag/unflag. Win counter (O(1) check). First-click safety.

**Senior**: BFS vs DFS trade-off justified (recursion depth). Lazy mine placement for first-click safety. Constraint-based hint system. Concurrent access (flag + reveal) thread-safety.

---

## Common Interview Questions

- **Q**: Why is BFS preferred over recursive DFS for flood fill?
  **A**: Python's default recursion limit is 1000. A 30×30 all-blank board could generate 900 recursive calls. BFS uses an explicit queue — no stack overflow risk.

- **Q**: How do you compute adjacent mine counts?
  **A**: After placing all mines, iterate every cell. For each non-mine cell, check its 8 neighbors and count how many are mines. Store the count in `cell.adjacent_mines`. O(rows × cols × 8) = O(n).

- **Q**: How does flood fill know when to stop?
  **A**: Stop conditions in BFS: (1) out of bounds, (2) cell is already revealed, (3) cell is a mine, (4) cell has adjacent_mines > 0 (reveal it but don't enqueue its neighbors).

- **Q**: How do you detect a win?
  **A**: Maintain a counter `unrevealed_non_mine_count = total_cells - num_mines`. Decrement on each non-mine cell reveal. Win when counter reaches 0. O(1) check.

- **Q**: What happens if a player flags a mine — does it count toward win?
  **A**: Standard rules: flagged cells are not revealed. Win requires all non-mine cells to be REVEALED (flagged cells don't count). The win check only counts REVEALED state.
