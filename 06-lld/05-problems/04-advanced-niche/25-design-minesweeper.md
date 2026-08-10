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

```java
public GameState reveal(int row, int col) {
    if (this.state != GameState.IN_PROGRESS) {
        return this.state;
    }

    Cell cell = this.board.getCell(row, col);
    if (cell.isRevealed() || cell.isFlagged()) {
        return this.state;
    }

    // First-click safety
    if (this.firstMove) {
        this.firstMove = false;
        if (cell.isMine()) {
            this.board.relocateMine(row, col);
        }
    }

    RevealResult result = this.board.reveal(row, col);

    if (result == RevealResult.MINE) {
        this.state = GameState.LOST;
        this.board.revealAllMines();
    } else if (this.board.isAllRevealed()) {
        this.state = GameState.WON;
    }

    return this.state;
}
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

```java
public RevealResult reveal(int row, int col) {
    Cell cell = this.grid[row][col];
    if (cell.isMine()) {
        cell.setState(CellState.REVEALED);
        return RevealResult.MINE;
    }

    if (cell.getAdjacentMines() > 0) {
        cell.setState(CellState.REVEALED);
        return RevealResult.NUMBER;
    }

    // BFS flood fill for blank cells
    floodFill(row, col);
    return RevealResult.BLANK;
}

private static final int[][] DIRECTIONS = {
    {-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}
};

private void floodFill(int startRow, int startCol) {
    Deque<int[]> queue = new ArrayDeque<>();
    queue.add(new int[]{startRow, startCol});
    Set<Long> visited = new HashSet<>();

    while (!queue.isEmpty()) {
        int[] pos = queue.poll();
        int r = pos[0], c = pos[1];
        long key = (long) r * cols + c;
        if (visited.contains(key)) {
            continue;
        }
        visited.add(key);

        if (!(r >= 0 && r < this.rows && c >= 0 && c < this.cols)) {
            continue;
        }

        Cell cell = this.grid[r][c];
        if (cell.getState() == CellState.REVEALED || cell.isMine()) {
            continue;
        }

        cell.setState(CellState.REVEALED);

        if (cell.getAdjacentMines() == 0) {
            for (int[] d : DIRECTIONS) {
                queue.add(new int[]{r + d[0], c + d[1]});
            }
        }
    }
}
```

### Mine placement and adjacent counts

```java
private void placeMines(int numMines, Set<Long> exclude) {
    List<int[]> allCells = new ArrayList<>();
    for (int r = 0; r < this.rows; r++) {
        for (int c = 0; c < this.cols; c++) {
            long key = (long) r * cols + c;
            if (!exclude.contains(key)) {
                allCells.add(new int[]{r, c});
            }
        }
    }
    Collections.shuffle(allCells, new Random());
    List<int[]> minePositions = allCells.subList(0, numMines);
    for (int[] pos : minePositions) {
        this.grid[pos[0]][pos[1]].setMine(true);
    }
}

private void computeAdjacentCounts() {
    for (int r = 0; r < this.rows; r++) {
        for (int c = 0; c < this.cols; c++) {
            if (!this.grid[r][c].isMine()) {
                int count = 0;
                for (int[] d : DIRECTIONS) {
                    int nr = r + d[0], nc = c + d[1];
                    if (nr >= 0 && nr < this.rows && nc >= 0 && nc < this.cols
                            && this.grid[nr][nc].isMine()) {
                        count++;
                    }
                }
                this.grid[r][c].setAdjacentMines(count);
            }
        }
    }
}
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

Both work. BFS (queue) is iterative and avoids deep call-stack growth. DFS (recursive) is simpler to write but risks a `StackOverflowError` on large grids (e.g., 100×100 blank board = 10,000 recursive calls), since the JVM's default thread stack size is finite.

**Recommendation**: BFS for production code.

```java
// DFS (recursive) — simpler but risky for large grids
private void floodFillDfs(int r, int c, Set<Long> visited) {
    if (!inBounds(r, c) || visited.contains((long) r * cols + c)) {
        return;
    }
    Cell cell = this.grid[r][c];
    if (cell.getState() == CellState.REVEALED || cell.isMine()) {
        return;
    }
    visited.add((long) r * cols + c);
    cell.setState(CellState.REVEALED);
    if (cell.getAdjacentMines() == 0) {
        for (int[] d : DIRECTIONS) {
            floodFillDfs(r + d[0], c + d[1], visited);
        }
    }
}
```

To make DFS safe on large grids: increase the thread's stack size (`-Xss` JVM flag or `new Thread(runnable, name, stackSize)`), or convert to iterative with an explicit `Deque` used as a stack.

### 2. "How would you implement first-click safety?"

Two approaches:

**A — Lazy placement**: Don't place mines until after the first click. Place mines excluding the clicked cell and its neighbors.

```java
public GameState reveal(int row, int col) {
    if (this.firstMove) {
        this.firstMove = false;
        Set<Long> exclude = new HashSet<>(neighbors(row, col));
        exclude.add((long) row * cols + col);
        this.board.placeMinesExcluding(exclude);
        this.board.computeAdjacentCounts();
    }
    // ... rest of reveal ...
}
```

**B — Relocate**: Place mines upfront. If first click hits a mine, move that mine to a random non-mine cell.

Approach A is cleaner — board setup is deferred, no re-computation needed.

### 3. "How would you add a hint system?"

Hint = reveal the cell with the highest probability of being safe. Use constraint propagation: for each numbered cell, if `adjacent_mines == count_of_flagged_adjacent`, all other adjacent hidden cells are safe.

```java
public Optional<int[]> getHint() {
    for (int r = 0; r < this.rows; r++) {
        for (int c = 0; c < this.cols; c++) {
            Cell cell = this.grid[r][c];
            if (cell.isRevealed() && cell.getAdjacentMines() > 0) {
                List<int[]> neighbors = neighbors(r, c);
                List<int[]> hidden = new ArrayList<>();
                int flaggedCount = 0;
                for (int[] n : neighbors) {
                    Cell nCell = this.grid[n[0]][n[1]];
                    if (nCell.isHidden()) {
                        hidden.add(n);
                    }
                    if (nCell.isFlagged()) {
                        flaggedCount++;
                    }
                }
                if (flaggedCount == cell.getAdjacentMines() && !hidden.isEmpty()) {
                    return Optional.of(hidden.get(0));  // safe to reveal
                }
            }
        }
    }
    return Optional.empty();  // no deterministic hint
}
```

### 4. "How would you validate the win condition efficiently?"

Rather than scanning all cells after every reveal, track a counter:

```java
public class Board {
    private int unrevealedNonMineCount;

    public Board(int rows, int cols, int numMines) {
        this.unrevealedNonMineCount = rows * cols - numMines;
    }

    private void revealCell(Cell cell) {
        cell.setState(CellState.REVEALED);
        this.unrevealedNonMineCount--;
    }

    public boolean isAllRevealed() {
        return this.unrevealedNonMineCount == 0;
    }
}
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
  **A**: The JVM's default thread stack allows roughly a few thousand frames before `StackOverflowError`, depending on frame size. A 30×30 all-blank board could generate 900 recursive calls — within range, but not future-proof. BFS uses an explicit queue — no stack overflow risk regardless of grid size.

- **Q**: How do you compute adjacent mine counts?
  **A**: After placing all mines, iterate every cell. For each non-mine cell, check its 8 neighbors and count how many are mines. Store the count in `cell.adjacent_mines`. O(rows × cols × 8) = O(n).

- **Q**: How does flood fill know when to stop?
  **A**: Stop conditions in BFS: (1) out of bounds, (2) cell is already revealed, (3) cell is a mine, (4) cell has adjacent_mines > 0 (reveal it but don't enqueue its neighbors).

- **Q**: How do you detect a win?
  **A**: Maintain a counter `unrevealed_non_mine_count = total_cells - num_mines`. Decrement on each non-mine cell reveal. Win when counter reaches 0. O(1) check.

- **Q**: What happens if a player flags a mine — does it count toward win?
  **A**: Standard rules: flagged cells are not revealed. Win requires all non-mine cells to be REVEALED (flagged cells don't count). The win check only counts REVEALED state.

---

## Related

**Patterns applied here**

- [Factory Pattern](../../03-design-patterns/01-creational/factory-pattern.md)
- [State Pattern](../../03-design-patterns/03-behavioral/state-pattern.md)
- [Strategy Pattern](../../03-design-patterns/03-behavioral/strategy-pattern.md)

**SOLID focus**: [Single Responsibility](../../02-solid-principles/01-single-responsibility.md) · [Open/Closed](../../02-solid-principles/02-open-closed.md)

**Practice next**

- [Design Snake and Ladder](../02-frequent-problems/08-design-snake-and-ladder.md)
- [Design Unlock Pattern](33-design-unlock-pattern.md)

Grid adjacency logic is the shared mechanic.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
