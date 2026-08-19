> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design Tetris — a very complex game state problem that tests 2D array manipulation, matrix rotation math, and game loops.
>
> **Key concepts:**
> - Core Entities: `Game`, `Board` (2D array, e.g., 20x10), `Tetromino` (the falling piece).
> - Tetromino (Factory): 7 distinct shapes (I, J, L, O, S, T, Z). Each is represented by a small 2D array or a list of relative coordinates.
> - Actions (Command Pattern): Move Left, Move Right, Move Down, Rotate, Drop.
> - Collision Detection: Before applying any command, the system must simulate it. If the new coordinates overlap with the board boundaries or existing settled blocks, the move is invalid.
> - Rotation Logic: Rotating a 2D matrix 90 degrees involves transposing the matrix and reversing the rows (or applying a standard 2D rotation matrix: `x' = -y, y' = x`).
> - Line Clearing: After a piece settles, check all rows. If a row is full, remove it, shift all rows above it down by 1, and increment the score.
>
> **Key takeaway:** Collision detection is the hardest part. Always keep the `Tetromino`'s local coordinates separate from its global position `(x, y)` on the `Board`. Add the local offsets to `(x, y)` to check against the board array.

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

```java
import java.util.*;

enum GameState {
    IDLE, RUNNING, GAME_OVER
}

enum PieceType {
    I, O, T, S, Z, L, J
}

class Shapes {
    // Each piece has 4 rotation states; each state is a list of (row, col) offsets.
    static final Map<PieceType, int[][][]> SHAPES = new EnumMap<>(PieceType.class);
    static final Map<PieceType, String> COLORS = new EnumMap<>(PieceType.class);

    static {
        SHAPES.put(PieceType.I, new int[][][]{
            {{0,0},{0,1},{0,2},{0,3}},
            {{0,2},{1,2},{2,2},{3,2}},
            {{2,0},{2,1},{2,2},{2,3}},
            {{0,1},{1,1},{2,1},{3,1}},
        });
        SHAPES.put(PieceType.O, new int[][][]{
            {{0,0},{0,1},{1,0},{1,1}},
            {{0,0},{0,1},{1,0},{1,1}},
            {{0,0},{0,1},{1,0},{1,1}},
            {{0,0},{0,1},{1,0},{1,1}},
        });
        SHAPES.put(PieceType.T, new int[][][]{
            {{0,1},{1,0},{1,1},{1,2}},
            {{0,1},{1,1},{1,2},{2,1}},
            {{1,0},{1,1},{1,2},{2,1}},
            {{0,1},{1,0},{1,1},{2,1}},
        });
        SHAPES.put(PieceType.S, new int[][][]{
            {{0,1},{0,2},{1,0},{1,1}},
            {{0,1},{1,1},{1,2},{2,2}},
            {{1,1},{1,2},{2,0},{2,1}},
            {{0,0},{1,0},{1,1},{2,1}},
        });
        SHAPES.put(PieceType.Z, new int[][][]{
            {{0,0},{0,1},{1,1},{1,2}},
            {{0,2},{1,1},{1,2},{2,1}},
            {{1,0},{1,1},{2,1},{2,2}},
            {{0,1},{1,0},{1,1},{2,0}},
        });
        SHAPES.put(PieceType.L, new int[][][]{
            {{0,2},{1,0},{1,1},{1,2}},
            {{0,1},{1,1},{2,1},{2,2}},
            {{1,0},{1,1},{1,2},{2,0}},
            {{0,0},{0,1},{1,1},{2,1}},
        });
        SHAPES.put(PieceType.J, new int[][][]{
            {{0,0},{1,0},{1,1},{1,2}},
            {{0,1},{0,2},{1,1},{2,1}},
            {{1,0},{1,1},{1,2},{2,2}},
            {{0,1},{1,1},{2,0},{2,1}},
        });

        COLORS.put(PieceType.I, "cyan");
        COLORS.put(PieceType.O, "yellow");
        COLORS.put(PieceType.T, "purple");
        COLORS.put(PieceType.S, "green");
        COLORS.put(PieceType.Z, "red");
        COLORS.put(PieceType.L, "orange");
        COLORS.put(PieceType.J, "blue");
    }
}

class Tetromino {
    private final PieceType pieceType;
    private final int[][][] rotations;
    private int rotationIndex;
    private int x; // spawn near center column
    private int y; // spawn at top row

    public Tetromino(PieceType pieceType) {
        this.pieceType = pieceType;
        this.rotations = Shapes.SHAPES.get(pieceType);
        this.rotationIndex = 0;
        this.x = 3;
        this.y = 0;
    }

    /** Return absolute (row, col) board positions. */
    public List<int[]> cells() {
        int[][] shape = rotations[rotationIndex];
        List<int[]> result = new ArrayList<>();
        for (int[] offset : shape) {
            result.add(new int[]{y + offset[0], x + offset[1]});
        }
        return result;
    }

    public void rotateCw() {
        rotationIndex = (rotationIndex + 1) % 4;
    }

    public void rotateCcw() {
        rotationIndex = (rotationIndex + 3) % 4;
    }

    public void move(int dx, int dy) {
        x += dx;
        y += dy;
    }

    public PieceType getPieceType() { return pieceType; }
    public int getRotationIndex() { return rotationIndex; }
    public int getX() { return x; }
    public int getY() { return y; }
}

class TetrominoFactory {
    private static final List<PieceType> TYPES = Arrays.asList(PieceType.values());
    private final Random random = new Random();

    public Tetromino create(PieceType pieceType) {
        if (!TYPES.contains(pieceType)) {
            throw new IllegalArgumentException("Unknown piece type: " + pieceType);
        }
        return new Tetromino(pieceType);
    }

    public Tetromino randomPiece() {
        return create(TYPES.get(random.nextInt(TYPES.size())));
    }
}

class PieceQueue {
    private final TetrominoFactory factory;
    private final int previewSize;
    private final Deque<Tetromino> queue = new ArrayDeque<>();

    public PieceQueue(TetrominoFactory factory) {
        this(factory, 3);
    }

    public PieceQueue(TetrominoFactory factory, int previewSize) {
        this.factory = factory;
        this.previewSize = previewSize;
        refill();
    }

    private void refill() {
        while (queue.size() < previewSize + 1) {
            queue.addLast(factory.randomPiece());
        }
    }

    public Tetromino next() {
        Tetromino piece = queue.removeFirst();
        refill();
        return piece;
    }

    public List<Tetromino> preview() {
        List<Tetromino> list = new ArrayList<>(queue);
        return list.subList(0, Math.min(previewSize, list.size()));
    }
}

class Board {
    private final int rows;
    private final int cols;
    private String[][] grid;

    public Board() {
        this(20, 10);
    }

    public Board(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
        this.grid = new String[rows][cols];
    }

    public boolean isValidPosition(Tetromino piece, int dx, int dy) {
        for (int[] cell : piece.cells()) {
            int r = cell[0] + dy;
            int c = cell[1] + dx;
            if (r < 0 || r >= rows || c < 0 || c >= cols) {
                return false;
            }
            if (grid[r][c] != null) {
                return false;
            }
        }
        return true;
    }

    public boolean isValidPosition(Tetromino piece) {
        return isValidPosition(piece, 0, 0);
    }

    public void lockPiece(Tetromino piece) {
        String color = Shapes.COLORS.get(piece.getPieceType());
        for (int[] cell : piece.cells()) {
            grid[cell[0]][cell[1]] = color;
        }
    }

    public int clearLines() {
        List<Integer> fullRows = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            boolean full = true;
            for (int c = 0; c < cols; c++) {
                if (grid[r][c] == null) {
                    full = false;
                    break;
                }
            }
            if (full) fullRows.add(r);
        }
        if (fullRows.isEmpty()) {
            return 0;
        }

        String[][] newGrid = new String[rows][cols];
        int writeRow = fullRows.size();
        for (int r = 0; r < rows; r++) {
            if (!fullRows.contains(r)) {
                newGrid[writeRow++] = grid[r];
            }
        }
        for (int r = 0; r < fullRows.size(); r++) {
            newGrid[r] = new String[cols];
        }
        grid = newGrid;
        return fullRows.size();
    }

    public boolean isGameOver() {
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < cols; c++) {
                if (grid[r][c] != null) {
                    return true;
                }
            }
        }
        return false;
    }

    public char[][] render(Tetromino piece) {
        char[][] display = new char[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                display[r][c] = grid[r][c] != null ? grid[r][c].charAt(0) : '.';
            }
        }
        if (piece != null) {
            char colorChar = Character.toUpperCase(Shapes.COLORS.get(piece.getPieceType()).charAt(0));
            for (int[] cell : piece.cells()) {
                int row = cell[0], col = cell[1];
                if (row >= 0 && row < rows && col >= 0 && col < cols) {
                    display[row][col] = colorChar;
                }
            }
        }
        return display;
    }

    public int getRows() { return rows; }
    public int getCols() { return cols; }
}

class ScoreTracker {
    private static final Map<Integer, Integer> POINTS = Map.of(1, 100, 2, 300, 3, 500, 4, 800);

    private int score = 0;
    private int linesCleared = 0;
    private int level = 1;

    public int addLines(int lines) {
        if (lines == 0) {
            return 0;
        }
        int points = POINTS.getOrDefault(lines, 0) * level;
        score += points;
        linesCleared += lines;
        level = linesCleared / 10 + 1;
        return points;
    }

    /** Seconds between gravity ticks. Decreases with level. */
    public double dropInterval() {
        return Math.max(0.05, 1.0 - (level - 1) * 0.1);
    }

    public int getScore() { return score; }
    public void addScore(int delta) { score += delta; }
    public int getLevel() { return level; }
    public int getLinesCleared() { return linesCleared; }
}

class Game {
    private final Board board = new Board();
    private final TetrominoFactory factory = new TetrominoFactory();
    private final PieceQueue queue = new PieceQueue(factory);
    private final ScoreTracker scoreTracker = new ScoreTracker();
    private GameState state = GameState.IDLE;
    private Tetromino currentPiece;

    public void start() {
        state = GameState.RUNNING;
        spawnPiece();
    }

    public boolean spawnPiece() {
        Tetromino piece = queue.next();
        if (!board.isValidPosition(piece)) {
            state = GameState.GAME_OVER;
            return false;
        }
        currentPiece = piece;
        return true;
    }

    /** Called each gravity interval. */
    public void tick() {
        if (state != GameState.RUNNING || currentPiece == null) {
            return;
        }
        if (board.isValidPosition(currentPiece, 0, 1)) {
            currentPiece.move(0, 1);
        } else {
            lockAndSpawn();
        }
    }

    private void lockAndSpawn() {
        board.lockPiece(currentPiece);
        int lines = board.clearLines();
        scoreTracker.addLines(lines);
        spawnPiece();
    }

    public void move(int dx) {
        if (currentPiece != null && board.isValidPosition(currentPiece, dx, 0)) {
            currentPiece.move(dx, 0);
        }
    }

    public void rotateCw() {
        if (currentPiece == null) {
            return;
        }
        currentPiece.rotateCw();
        if (!board.isValidPosition(currentPiece)) {
            currentPiece.rotateCcw(); // revert on collision
        }
    }

    public void hardDrop() {
        if (currentPiece == null) {
            return;
        }
        int rowsDropped = 0;
        while (board.isValidPosition(currentPiece, 0, 1)) {
            currentPiece.move(0, 1);
            rowsDropped++;
        }
        scoreTracker.addScore(rowsDropped * 2); // hard drop bonus
        lockAndSpawn();
    }

    public void softDrop() {
        if (currentPiece != null && board.isValidPosition(currentPiece, 0, 1)) {
            currentPiece.move(0, 1);
            scoreTracker.addScore(1);
        }
    }
}
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

```java
// Wall kick offsets for J, L, S, T, Z (I-piece has different kicks)
class SrsKicks {
    static final Map<List<Integer>, int[][]> KICKS = new HashMap<>();
    static {
        KICKS.put(List.of(0, 1), new int[][]{{0,0},{-1,0},{-1,1},{0,-2},{-1,-2}});
        KICKS.put(List.of(1, 0), new int[][]{{0,0},{1,0},{1,-1},{0,2},{1,2}});
        KICKS.put(List.of(1, 2), new int[][]{{0,0},{1,0},{1,-1},{0,2},{1,2}});
        KICKS.put(List.of(2, 1), new int[][]{{0,0},{-1,0},{-1,1},{0,-2},{-1,-2}});
        KICKS.put(List.of(2, 3), new int[][]{{0,0},{1,0},{1,1},{0,-2},{1,-2}});
        KICKS.put(List.of(3, 2), new int[][]{{0,0},{-1,0},{-1,-1},{0,2},{-1,2}});
        KICKS.put(List.of(3, 0), new int[][]{{0,0},{-1,0},{-1,-1},{0,2},{-1,2}});
        KICKS.put(List.of(0, 3), new int[][]{{0,0},{1,0},{1,1},{0,-2},{1,-2}});
    }
}

public void rotateCwSrs(Board board) {
    int oldIndex = rotationIndex;
    int newIndex = (oldIndex + 1) % 4;
    rotationIndex = newIndex;
    int[][] kicks = SrsKicks.KICKS.getOrDefault(List.of(oldIndex, newIndex), new int[][]{{0, 0}});
    for (int[] kick : kicks) {
        int dx = kick[0], dy = kick[1];
        if (board.isValidPosition(this, dx, dy)) {
            x += dx;
            y += dy;
            return; // kick succeeded
        }
    }
    rotationIndex = oldIndex; // all kicks failed, revert
}
```

The practical impact: with SRS, a T-piece wedged against a wall can still rotate by kicking off the wall. Without it, the rotation silently fails.

### 2. "How does collision detection work without moving the piece?"

`is_valid_position` accepts delta offsets `(dx, dy)` and tests hypothetical positions without mutating state. This avoids the move-then-revert pattern which could leave the piece in an invalid state if code throws mid-revert.

```java
public boolean isValidPosition(Tetromino piece, int dx, int dy) {
    for (int[] cell : piece.cells()) {
        int r = cell[0] + dy, c = cell[1] + dx;
        // Wall check
        if (r < 0 || r >= rows || c < 0 || c >= cols) {
            return false;
        }
        // Occupied cell check
        if (grid[r][c] != null) {
            return false;
        }
    }
    return true;
}
```

Edge case: I-piece at x=7 with horizontal orientation has cells at cols 7,8,9,10. `col=10 >= 10` (board.cols=10) fails, blocking the move right. This is correct — no partial blocking.

### 3. "How do you animate line clearing instead of instant removal?"

Add a `CLEARING` sub-state. When full lines are detected, store them and pause spawning for N frames. The renderer blinks those rows. After N frames, finalize the removal.

```java
class Board {
    static final int CLEAR_FRAMES = 8;

    private List<Integer> clearingRows = new ArrayList<>();
    private int clearFrame = 0;

    public List<Integer> detectFullLines() {
        clearingRows = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            boolean full = true;
            for (int c = 0; c < cols; c++) {
                if (grid[r][c] == null) {
                    full = false;
                    break;
                }
            }
            if (full) clearingRows.add(r);
        }
        clearFrame = 0;
        return clearingRows;
    }

    /** Returns true when animation is done. */
    public boolean tickClearAnimation() {
        clearFrame++;
        return clearFrame >= CLEAR_FRAMES;
    }

    public int finalizeClear() {
        int count = clearingRows.size();
        String[][] newGrid = new String[rows][cols];
        int writeRow = count;
        for (int r = 0; r < rows; r++) {
            if (!clearingRows.contains(r)) {
                newGrid[writeRow++] = grid[r];
            }
        }
        for (int r = 0; r < count; r++) {
            newGrid[r] = new String[cols];
        }
        grid = newGrid;
        clearingRows = new ArrayList<>();
        return count;
    }
}
```

Game loop: lock piece -> `detect_full_lines()` -> enter CLEARING state -> call `tick_clear_animation()` each frame -> on True, call `finalize_clear()` -> spawn next piece.

### 4. "How do you implement the next piece preview?"

`PieceQueue` maintains a list of pre-generated pieces. `next()` pops from the front and `_refill()` appends new random pieces to keep the queue at `preview_size + 1`. The UI reads `queue.preview()` which returns a view without consuming pieces.

For fair distribution, use a 7-bag randomizer: shuffle all 7 types, deal in order, reshuffle when exhausted. This guarantees at most 12 pieces between any two of the same type.

```java
class SevenBagQueue {
    private final TetrominoFactory factory;
    private final int previewSize;
    private final List<PieceType> bag = new ArrayList<>();
    private final Deque<Tetromino> queue = new ArrayDeque<>();
    private final Random random = new Random();

    public SevenBagQueue(TetrominoFactory factory) {
        this(factory, 3);
    }

    public SevenBagQueue(TetrominoFactory factory, int previewSize) {
        this.factory = factory;
        this.previewSize = previewSize;
        refill();
    }

    private Tetromino drawFromBag() {
        if (bag.isEmpty()) {
            bag.addAll(Arrays.asList(PieceType.values()));
            Collections.shuffle(bag, random);
        }
        return factory.create(bag.remove(bag.size() - 1));
    }

    private void refill() {
        while (queue.size() < previewSize + 1) {
            queue.addLast(drawFromBag());
        }
    }

    public Tetromino next() {
        Tetromino piece = queue.removeFirst();
        refill();
        return piece;
    }

    public List<Tetromino> preview() {
        List<Tetromino> list = new ArrayList<>(queue);
        return list.subList(0, Math.min(previewSize, list.size()));
    }
}
```

### 5. "Explain the scoring system and multipliers"

Standard Tetris scoring: points = base[lines_cleared] * level. The base values jump non-linearly to reward clearing 4 lines (Tetris) at once.

```java
class ScoreTracker {
    static final Map<Integer, Integer> POINTS = Map.of(1, 100, 2, 300, 3, 500, 4, 800);

    private int score;
    private int linesCleared;
    private int level;

    public int addLines(int lines) {
        // Example: 4 lines at level 5 = 800 * 5 = 4000 points
        int points = POINTS.getOrDefault(lines, 0) * level;
        score += points;
        linesCleared += lines;
        // Level up every 10 lines
        level = linesCleared / 10 + 1;
        return points;
    }

    public void softDropBonus(int rows) {
        score += rows * 1; // 1 point per row
    }

    public void hardDropBonus(int rows) {
        score += rows * 2; // 2 points per row (Tetris guideline)
    }
}
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

- **Q: Why does `rotate_ccw` use `(rotationIndex + 3) % 4` instead of subtraction?**
  A: Java's `%` operator can return negative results for negative operands (unlike Python's `%`, which always returns a non-negative result for a positive divisor). Adding 3 before taking mod 4 keeps the result non-negative without needing a branch or `Math.floorMod`.

- **Q: How does line clearing shift rows down efficiently?**
  A: Filter full rows from the grid array, then prepend the same number of empty rows at the top. This is O(rows) with simple array copy operations.

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

---

## Related

**Patterns applied here**

- [Factory Pattern](../../03-design-patterns/01-creational/factory-pattern.md)
- [Command Pattern](../../03-design-patterns/03-behavioral/command-pattern.md)
- [State Pattern](../../03-design-patterns/03-behavioral/state-pattern.md)

**SOLID focus**: [Open/Closed](../../02-solid-principles/02-open-closed.md)

**Practice next**

- [Design Minesweeper](25-design-minesweeper.md)
- [Design Chess](../02-frequent-problems/07-design-chess.md)

Grid state plus undoable moves is the shared model.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../01-oop-fundamentals/uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
