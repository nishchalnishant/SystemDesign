---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, tetris, game, factory, strategy, command]
---
# Design Tetris

> **Difficulty**: Hard
> **Asked at**: Amazon, Intuit
> **Key Patterns**: Factory (tetromino types), Strategy (rotation), Command (move/rotate/drop)

---

## Understanding the Problem

Design the core game engine for Tetris: spawning falling tetromino pieces, detecting collisions with the board and existing blocks, clearing completed lines, tracking score, and managing the game loop until the board fills up.

---

## Clarifying Questions

**You**: "Should I handle all 7 standard tetromino types — I, O, T, S, Z, L, J?"
**Interviewer**: "Yes, all 7."

**You**: "Does rotation follow the standard Super Rotation System (SRS) with wall kicks, or basic 90-degree rotation?"
**Interviewer**: "Start with basic 90-degree rotation. Mention SRS as an extension."

**You**: "Is scoring standard — points for single/double/triple/tetris line clears?"
**Interviewer**: "Yes, use the standard multiplier system."

**You**: "Do I need to implement gravity speed that increases over time (levels)?"
**Interviewer**: "Yes, each level speeds up the drop interval."

**You**: "Should I implement a 'next piece' preview?"
**Interviewer**: "Yes, maintain a queue of upcoming pieces."

**You**: "Do I need to handle hard drop (instant drop to bottom)?"
**Interviewer**: "Yes, include hard drop and soft drop."

**You**: "Is this single-player only, or multiplayer?"
**Interviewer**: "Single-player only."

---

## Final Requirements

**In scope:**
1. Spawn all 7 tetromino types using a factory
2. Move pieces left, right, down; rotate 90 degrees clockwise/counter-clockwise
3. Collision detection with board edges and locked pieces
4. Lock piece when it can no longer fall
5. Clear completed lines and shift rows down
6. Score based on lines cleared (1/2/3/4 -> 100/300/500/800 x level)
7. Level progression increases gravity speed
8. Next piece preview queue
9. Hard drop and soft drop
10. Game over detection when spawn position is blocked

**Out of scope:**
- SRS wall kicks (mention as extension)
- T-spin detection
- Hold piece
- Multiplayer / garbage lines
- Ghost piece shadow preview (mention as extension)
- Persistent leaderboard

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|---------------|
| Game | Orchestrates game loop, level, score, spawning |
| Board | 2D grid state, collision detection, line clearing |
| Tetromino | Abstract piece with shape, position, rotation logic |
| TetrominoFactory | Creates correct Tetromino subclass by type |
| ScoreTracker | Computes and accumulates score from line clears |
| PieceQueue | Holds current and upcoming pieces |

---

## Class Design

### Tetromino

| Requirement | What Tetromino must track |
|-------------|--------------------------|
| Shape in all 4 rotations | `rotations: list[list[tuple]]` — offset lists per rotation state |
| Current rotation index | `rotation_index: int` |
| Position on board | `x: int, y: int` (top-left anchor) |
| Piece type/color | `piece_type: str` |

```
class Tetromino:
- piece_type: str
- rotations: list[list[tuple[int,int]]]
- rotation_index: int
- x: int
- y: int
+ cells() -> list[tuple[int,int]]
+ rotate_cw() -> None
+ rotate_ccw() -> None
+ move(dx, dy) -> None
```

### Board

| Requirement | What Board must track |
|-------------|----------------------|
| Fixed locked cells | `grid: list[list[str or None]]` |
| Board dimensions | `rows: int, cols: int` |

```
class Board:
- grid: list[list[str | None]]
- rows: int
- cols: int
+ is_valid_position(piece, dx, dy) -> bool
+ lock_piece(piece) -> None
+ clear_lines() -> int
+ is_game_over() -> bool
+ render(piece) -> list[list[str]]
```

### Game

| Requirement | What Game must track |
|-------------|---------------------|
| Active board | `board: Board` |
| Score and level | managed by `score_tracker` |
| Piece queue | `queue: PieceQueue` |
| Game state | `state: GameState` enum |

```
class Game:
- board: Board
- score_tracker: ScoreTracker
- queue: PieceQueue
- state: GameState
- current_piece: Tetromino
+ start() -> None
+ tick() -> None
+ move(dx) -> None
+ rotate_cw() -> None
+ hard_drop() -> None
+ soft_drop() -> None
+ spawn_piece() -> bool
```

---

## Implementation

### Core Method: clear_lines()

**Core logic:**
1. Scan all rows; collect rows where every cell is non-None
2. Remove those rows from the grid list
3. Prepend the same count of empty rows at the top
4. Return count of cleared rows to the score tracker

**Edge cases:**
- Multiple non-adjacent rows cleared in one lock (e.g., rows 5 and 9)
- Board completely empty after clear — no crash
- Zero lines cleared — return 0, skip score update

```python
from enum import Enum
from typing import Optional
import random

SHAPES = {
    'I': [
        [(0,0),(0,1),(0,2),(0,3)],
        [(0,2),(1,2),(2,2),(3,2)],
        [(2,0),(2,1),(2,2),(2,3)],
        [(0,1),(1,1),(2,1),(3,1)],
    ],
    'O': [
        [(0,0),(0,1),(1,0),(1,1)],
        [(0,0),(0,1),(1,0),(1,1)],
        [(0,0),(0,1),(1,0),(1,1)],
        [(0,0),(0,1),(1,0),(1,1)],
    ],
    'T': [
        [(0,1),(1,0),(1,1),(1,2)],
        [(0,1),(1,1),(1,2),(2,1)],
        [(1,0),(1,1),(1,2),(2,1)],
        [(0,1),(1,0),(1,1),(2,1)],
    ],
    'S': [
        [(0,1),(0,2),(1,0),(1,1)],
        [(0,1),(1,1),(1,2),(2,2)],
        [(1,1),(1,2),(2,0),(2,1)],
        [(0,0),(1,0),(1,1),(2,1)],
    ],
    'Z': [
        [(0,0),(0,1),(1,1),(1,2)],
        [(0,2),(1,1),(1,2),(2,1)],
        [(1,0),(1,1),(2,1),(2,2)],
        [(0,1),(1,0),(1,1),(2,0)],
    ],
    'L': [
        [(0,2),(1,0),(1,1),(1,2)],
        [(0,1),(1,1),(2,1),(2,2)],
        [(1,0),(1,1),(1,2),(2,0)],
        [(0,0),(0,1),(1,1),(2,1)],
    ],
    'J': [
        [(0,0),(1,0),(1,1),(1,2)],
        [(0,1),(0,2),(1,1),(2,1)],
        [(1,0),(1,1),(1,2),(2,2)],
        [(0,1),(1,1),(2,0),(2,1)],
    ],
}

COLORS = {
    'I': 'cyan', 'O': 'yellow', 'T': 'purple',
    'S': 'green', 'Z': 'red', 'L': 'orange', 'J': 'blue'
}


class GameState(Enum):
    IDLE = "idle"
    RUNNING = "running"
    GAME_OVER = "game_over"


class Tetromino:
    def __init__(self, piece_type: str):
        self.piece_type = piece_type
        self.rotations = SHAPES[piece_type]
        self.rotation_index = 0
        self.x = 3   # spawn near center column
        self.y = 0   # spawn at top row

    def cells(self) -> list:
        """Return absolute (row, col) board positions."""
        shape = self.rotations[self.rotation_index]
        return [(self.y + dr, self.x + dc) for dr, dc in shape]

    def rotate_cw(self):
        self.rotation_index = (self.rotation_index + 1) % 4

    def rotate_ccw(self):
        self.rotation_index = (self.rotation_index - 1) % 4

    def move(self, dx: int, dy: int):
        self.x += dx
        self.y += dy


class TetrominoFactory:
    TYPES = list(SHAPES.keys())

    def create(self, piece_type: str) -> Tetromino:
        if piece_type not in self.TYPES:
            raise ValueError(f"Unknown piece type: {piece_type}")
        return Tetromino(piece_type)

    def random_piece(self) -> Tetromino:
        return self.create(random.choice(self.TYPES))


class PieceQueue:
    def __init__(self, factory: TetrominoFactory, preview_size: int = 3):
        self.factory = factory
        self.preview_size = preview_size
        self._queue: list = []
        self._refill()

    def _refill(self):
        while len(self._queue) < self.preview_size + 1:
            self._queue.append(self.factory.random_piece())

    def next(self) -> Tetromino:
        piece = self._queue.pop(0)
        self._refill()
        return piece

    def preview(self) -> list:
        return self._queue[:self.preview_size]


class Board:
    def __init__(self, rows: int = 20, cols: int = 10):
        self.rows = rows
        self.cols = cols
        self.grid: list = [[None] * cols for _ in range(rows)]

    def is_valid_position(self, piece: Tetromino, dx: int = 0, dy: int = 0) -> bool:
        for row, col in piece.cells():
            r, c = row + dy, col + dx
            if not (0 <= r < self.rows and 0 <= c < self.cols):
                return False
            if self.grid[r][c] is not None:
                return False
        return True

    def lock_piece(self, piece: Tetromino):
        color = COLORS[piece.piece_type]
        for row, col in piece.cells():
            self.grid[row][col] = color

    def clear_lines(self) -> int:
        full_rows = [r for r in range(self.rows)
                     if all(cell is not None for cell in self.grid[r])]
        if not full_rows:
            return 0
        new_grid = [row for r, row in enumerate(self.grid) if r not in full_rows]
        empty_rows = [[None] * self.cols for _ in range(len(full_rows))]
        self.grid = empty_rows + new_grid
        return len(full_rows)

    def is_game_over(self) -> bool:
        return any(self.grid[r][c] is not None
                   for r in range(2) for c in range(self.cols))

    def render(self, piece: Optional[Tetromino] = None) -> list:
        display = [[cell or '.' for cell in row] for row in self.grid]
        if piece:
            color_char = COLORS[piece.piece_type][0].upper()
            for row, col in piece.cells():
                if 0 <= row < self.rows and 0 <= col < self.cols:
                    display[row][col] = color_char
        return display


class ScoreTracker:
    POINTS = {1: 100, 2: 300, 3: 500, 4: 800}

    def __init__(self):
        self.score = 0
        self.lines_cleared = 0
        self.level = 1

    def add_lines(self, lines: int) -> int:
        if lines == 0:
            return 0
        points = self.POINTS.get(lines, 0) * self.level
        self.score += points
        self.lines_cleared += lines
        self.level = self.lines_cleared // 10 + 1
        return points

    def drop_interval(self) -> float:
        """Seconds between gravity ticks. Decreases with level."""
        return max(0.05, 1.0 - (self.level - 1) * 0.1)


class Game:
    def __init__(self):
        self.board = Board()
        self.factory = TetrominoFactory()
        self.queue = PieceQueue(self.factory)
        self.score_tracker = ScoreTracker()
        self.state = GameState.IDLE
        self.current_piece: Optional[Tetromino] = None

    def start(self):
        self.state = GameState.RUNNING
        self.spawn_piece()

    def spawn_piece(self) -> bool:
        piece = self.queue.next()
        if not self.board.is_valid_position(piece):
            self.state = GameState.GAME_OVER
            return False
        self.current_piece = piece
        return True

    def tick(self):
        """Called each gravity interval."""
        if self.state != GameState.RUNNING or self.current_piece is None:
            return
        if self.board.is_valid_position(self.current_piece, dy=1):
            self.current_piece.move(0, 1)
        else:
            self._lock_and_spawn()

    def _lock_and_spawn(self):
        self.board.lock_piece(self.current_piece)
        lines = self.board.clear_lines()
        self.score_tracker.add_lines(lines)
        self.spawn_piece()

    def move(self, dx: int):
        if self.current_piece and self.board.is_valid_position(self.current_piece, dx=dx):
            self.current_piece.move(dx, 0)

    def rotate_cw(self):
        if self.current_piece is None:
            return
        self.current_piece.rotate_cw()
        if not self.board.is_valid_position(self.current_piece):
            self.current_piece.rotate_ccw()  # revert on collision

    def hard_drop(self):
        if self.current_piece is None:
            return
        rows_dropped = 0
        while self.board.is_valid_position(self.current_piece, dy=1):
            self.current_piece.move(0, 1)
            rows_dropped += 1
        self.score_tracker.score += rows_dropped * 2  # hard drop bonus
        self._lock_and_spawn()

    def soft_drop(self):
        if self.current_piece and self.board.is_valid_position(self.current_piece, dy=1):
            self.current_piece.move(0, 1)
            self.score_tracker.score += 1
```

---

## Verification

**Scenario: Spawn I-piece, move right twice, rotate CW, hard drop, clear a line**

1. `game.start()` -> I-piece spawns at x=3, y=0, rotation=0 (horizontal cells at row=0, cols 3-6)
2. `game.move(1)` -> x=4; `game.move(1)` -> x=5
3. `game.rotate_cw()` -> rotation_index=1 (vertical I-piece, cells at col 7, rows 0-3)
4. `board.is_valid_position(piece)` checks pass; piece is at column 7
5. `game.hard_drop()` -> loop moves piece down until row=16 (board nearly full at bottom); rows_dropped=16; score += 32
6. `board.lock_piece(piece)` -> columns 7, rows 16-19 filled
7. `board.clear_lines()` -> if row 19 is now full, returns 1; `new_grid` removes row 19, prepends empty row
8. `score_tracker.add_lines(1)` -> score += 100; lines_cleared=1; level still 1
9. `spawn_piece()` -> next piece from queue, if spawn position is valid, game continues

---

## Deep Dive & Extensibility

### 1. "How does the Super Rotation System (SRS) differ from basic rotation?"

Basic rotation reverts immediately on collision. SRS tries up to 4 additional "wall kick" offset positions before reverting. Each piece type has a lookup table of kick offsets indexed by (from_rotation, to_rotation).

```python
# Wall kick offsets for J, L, S, T, Z (I-piece has different kicks)
SRS_KICKS = {
    (0, 1): [(0,0),(-1,0),(-1,1),(0,-2),(-1,-2)],
    (1, 0): [(0,0),(1,0),(1,-1),(0,2),(1,2)],
    (1, 2): [(0,0),(1,0),(1,-1),(0,2),(1,2)],
    (2, 1): [(0,0),(-1,0),(-1,1),(0,-2),(-1,-2)],
    (2, 3): [(0,0),(1,0),(1,1),(0,-2),(1,-2)],
    (3, 2): [(0,0),(-1,0),(-1,-1),(0,2),(-1,2)],
    (3, 0): [(0,0),(-1,0),(-1,-1),(0,2),(-1,2)],
    (0, 3): [(0,0),(1,0),(1,1),(0,-2),(1,-2)],
}

def rotate_cw_srs(self, board: Board):
    old_index = self.rotation_index
    new_index = (old_index + 1) % 4
    self.rotation_index = new_index
    kicks = SRS_KICKS.get((old_index, new_index), [(0, 0)])
    for dx, dy in kicks:
        if board.is_valid_position(self, dx=dx, dy=dy):
            self.x += dx
            self.y += dy
            return  # kick succeeded
    self.rotation_index = old_index  # all kicks failed, revert
```

The practical impact: with SRS, a T-piece wedged against a wall can still rotate by kicking off the wall. Without it, the rotation silently fails.

### 2. "How does collision detection work without moving the piece?"

`is_valid_position` accepts delta offsets `(dx, dy)` and tests hypothetical positions without mutating state. This avoids the move-then-revert pattern which could leave the piece in an invalid state if code throws mid-revert.

```python
def is_valid_position(self, piece: Tetromino, dx: int = 0, dy: int = 0) -> bool:
    for row, col in piece.cells():
        r, c = row + dy, col + dx
        # Wall check
        if not (0 <= r < self.rows and 0 <= c < self.cols):
            return False
        # Occupied cell check
        if self.grid[r][c] is not None:
            return False
    return True
```

Edge case: I-piece at x=7 with horizontal orientation has cells at cols 7,8,9,10. `col=10 >= 10` (board.cols=10) fails, blocking the move right. This is correct — no partial blocking.

### 3. "How do you animate line clearing instead of instant removal?"

Add a `CLEARING` sub-state. When full lines are detected, store them and pause spawning for N frames. The renderer blinks those rows. After N frames, finalize the removal.

```python
class Board:
    CLEAR_FRAMES = 8

    def __init__(self, ...):
        self.clearing_rows: list = []
        self.clear_frame: int = 0

    def detect_full_lines(self) -> list:
        self.clearing_rows = [
            r for r in range(self.rows)
            if all(cell is not None for cell in self.grid[r])
        ]
        self.clear_frame = 0
        return self.clearing_rows

    def tick_clear_animation(self) -> bool:
        """Returns True when animation is done."""
        self.clear_frame += 1
        return self.clear_frame >= self.CLEAR_FRAMES

    def finalize_clear(self) -> int:
        count = len(self.clearing_rows)
        new_grid = [row for r, row in enumerate(self.grid)
                    if r not in self.clearing_rows]
        self.grid = [[None] * self.cols for _ in range(count)] + new_grid
        self.clearing_rows = []
        return count
```

Game loop: lock piece -> `detect_full_lines()` -> enter CLEARING state -> call `tick_clear_animation()` each frame -> on True, call `finalize_clear()` -> spawn next piece.

### 4. "How do you implement the next piece preview?"

`PieceQueue` maintains a list of pre-generated pieces. `next()` pops from the front and `_refill()` appends new random pieces to keep the queue at `preview_size + 1`. The UI reads `queue.preview()` which returns a view without consuming pieces.

For fair distribution, use a 7-bag randomizer: shuffle all 7 types, deal in order, reshuffle when exhausted. This guarantees at most 12 pieces between any two of the same type.

```python
from collections import deque

class SevenBagQueue:
    def __init__(self, factory: TetrominoFactory, preview_size: int = 3):
        self.factory = factory
        self.preview_size = preview_size
        self._bag: list = []
        self._queue: deque = deque()
        self._refill()

    def _draw_from_bag(self) -> Tetromino:
        if not self._bag:
            self._bag = list(SHAPES.keys())
            random.shuffle(self._bag)
        return self.factory.create(self._bag.pop())

    def _refill(self):
        while len(self._queue) < self.preview_size + 1:
            self._queue.append(self._draw_from_bag())

    def next(self) -> Tetromino:
        piece = self._queue.popleft()
        self._refill()
        return piece

    def preview(self) -> list:
        return list(self._queue)[:self.preview_size]
```

### 5. "Explain the scoring system and multipliers"

Standard Tetris scoring: points = base[lines_cleared] * level. The base values jump non-linearly to reward clearing 4 lines (Tetris) at once.

```python
class ScoreTracker:
    POINTS = {1: 100, 2: 300, 3: 500, 4: 800}

    def add_lines(self, lines: int) -> int:
        # Example: 4 lines at level 5 = 800 * 5 = 4000 points
        points = self.POINTS.get(lines, 0) * self.level
        self.score += points
        self.lines_cleared += lines
        # Level up every 10 lines
        self.level = self.lines_cleared // 10 + 1
        return points

    def soft_drop_bonus(self, rows: int):
        self.score += rows * 1   # 1 point per row

    def hard_drop_bonus(self, rows: int):
        self.score += rows * 2   # 2 points per row (Tetris guideline)
```

The 4:1 ratio between double and tetris (300 vs 800 * 2 = 1600 relative) makes stacking for tetrises worth the risk.

---

## Interviewer Questions by Level

**Junior**: What data structure represents a tetromino piece on the board?

**Mid-level**: How does `is_valid_position` check collisions without actually moving the piece?

**Senior**: How would you implement SRS wall kicks? What is the trade-off between pre-computed rotation tables vs computing rotations with a 2D rotation matrix at runtime?

---

## Common Interview Questions

- **Q: How do you represent a tetromino?**
  A: As a list of 4 rotation states, each a list of (row, col) offset tuples from an anchor point. This avoids matrix math at runtime and keeps rotation O(1).

- **Q: Why store 4 pre-computed rotations instead of computing them with a rotation matrix?**
  A: Pre-computation is simpler and faster. Matrix rotation introduces floating-point precision issues for integer grids. With only 7 pieces x 4 rotations, the lookup table is trivially small.

- **Q: How does line clearing shift rows down efficiently?**
  A: Filter full rows from the grid list, then prepend the same number of empty rows at the top. This is O(rows) with simple Python list operations.

- **Q: How do you detect game over?**
  A: When `spawn_piece()` places a new piece and `is_valid_position()` returns False immediately — the spawn zone (top 2 rows) is blocked by locked pieces.

- **Q: How does the game loop timing work for gravity?**
  A: A timer fires `game.tick()` every `drop_interval` seconds. The interval shortens as level increases (e.g., 1.0s at level 1, 0.1s at level 10). Player inputs are processed synchronously between ticks.

- **Q: How does hard drop differ from soft drop?**
  A: Hard drop instantly moves the piece to the lowest valid position and locks it, awarding 2 points per row. Soft drop moves down one row per input, awarding 1 point per row.

- **Q: How would you add a ghost piece?**
  A: Clone the current piece, simulate `move(0, 1)` in a loop until invalid, render those cells translucently. Cost is O(rows) per frame — negligible.

- **Q: What is the 7-bag randomizer and why use it?**
  A: Shuffle all 7 piece types and deal sequentially; reshuffle when depleted. Guarantees no drought of any piece type longer than 12 consecutive pieces, making the game fairer than pure random.
