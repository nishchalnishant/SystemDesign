> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design Chess — a complex OOP modeling problem focusing on inheritance, polymorphism, and validating complex business rules.
>
> **Key concepts:**
> - Core Entities: `Game`, `Board` (8x8 array of `Box`), `Player`, `Move`.
> - Polymorphism: `Piece` is an abstract base class with an abstract method `isValidMove(start, end)`. Concrete classes (`King`, `Queen`, `Knight`, etc.) implement their specific movement logic.
> - The Game Loop: A central `Game` orchestrator manages player turns, gets the proposed move, checks if it's valid for that piece, and executes it.
> - Tricky Rules: 
>   - Castling: requires tracking if the King and Rook have moved yet.
>   - En Passant: requires knowing the exact *previous* move.
>   - Check/Checkmate: requires simulating a move and seeing if the King is still under attack.
>
> **Key takeaway:** Keep the pieces "dumb" regarding the state of the entire game. A `Piece` should only validate its geometric move (e.g., Knight moves in an L-shape). The `Board` or `Game` must validate if the path is blocked by other pieces.

---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, chess, polymorphism, state-machine, abstract-class]
---
# Design Chess

> **Difficulty**: Hard
> **Asked at**: Amazon, Google, Jane Street
> **Key Patterns**: Polymorphism (piece hierarchy), State enum, Template Method

---

## Understanding the Problem

Design a two-player chess game with all piece types, legal move validation (including check), and end-game detection (checkmate and stalemate).

---

## Clarifying Questions

**You**: "Are we building the full rules of chess — all piece types, check, checkmate, stalemate?"
**Interviewer**: "Yes, all standard pieces and win conditions."

**You**: "Do we need special moves like castling, en passant, and pawn promotion?"
**Interviewer**: "Start with standard moves. Castling and en passant are follow-up extensions."

**You**: "Is this backend logic only, or do we need a UI?"
**Interviewer**: "Backend only."

**You**: "Do we need to support saving and loading a game?"
**Interviewer**: "No persistence needed."

**You**: "Should we support an AI opponent?"
**Interviewer**: "Not required, but discuss how you'd add it."

---

## Final Requirements

**In scope:**
1. Two players alternate turns, White moves first
2. Each piece type enforces its own legal moves
3. A move is rejected if it leaves the moving side's king in check
4. Detect check, checkmate (game over — opponent wins), stalemate (draw)
5. Invalid moves are rejected clearly

**Out of scope:**
- Castling, en passant, pawn promotion (follow-up)
- Draw by repetition, 50-move rule (follow-up)
- Persistence
- UI

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|---------------|
| `Game` | Orchestrates turns, validates moves, detects checkmate/stalemate |
| `Board` | 8×8 grid; get/set pieces; does not know rules |
| `Piece` (abstract) | Holds color and position; declares `get_valid_moves(board)` |
| `King/Queen/Rook/Bishop/Knight/Pawn` | Each overrides `get_valid_moves` with piece-specific logic |
| `Player` | Name and color — pure data |
| `Position` | Immutable (row, col) coordinate with bounds check |
| `Color` | Enum: WHITE, BLACK |
| `GameState` | Enum: IN_PROGRESS, WHITE_WINS, BLACK_WINS, DRAW |

`Game` delegates to `Board` for grid state and to each `Piece` for move generation. Check detection works by simulating a move on the board and testing whether the king is attacked afterward.

---

## Class Design

### Piece (abstract base)

| Requirement | What Piece must track |
|-------------|----------------------|
| "Each piece enforces its own moves" | color, position; abstract `get_valid_moves` |
| "Move leaves king in check" | color (to know which king to protect) |

```
class Piece (abstract):
- color: Color
- position: Position

+ get_valid_moves(board: Board) -> list[Position]  # abstract
+ get_color() -> Color
+ get_position() -> Position
+ set_position(pos: Position)
```

### Board

| Requirement | What Board must track |
|-------------|----------------------|
| "8×8 grid" | grid: dict[Position, Piece] (sparse — only occupied cells) |
| "Detect if king is attacked" | ability to query all pieces of a color |

```
class Board:
- grid: dict[Position, Piece]

+ get_piece(pos: Position) -> Optional[Piece]
+ set_piece(pos: Position, piece: Piece)
+ remove_piece(pos: Position)
+ move_piece(from_pos: Position, to_pos: Position)
+ get_pieces(color: Color) -> list[Piece]
+ find_king(color: Color) -> Position
+ is_in_check(color: Color) -> bool
+ clone() -> Board
```

### Game

```
class Game:
- board: Board
- players: dict[Color, Player]
- current_color: Color
- state: GameState

+ make_move(from_pos: Position, to_pos: Position) -> bool
+ get_current_player() -> Player
+ get_state() -> GameState
+ is_checkmate(color: Color) -> bool
+ is_stalemate(color: Color) -> bool
```

---

## Implementation

### Core Method: `make_move`

**Core logic:**
1. Validate it's the right player's turn
2. Get piece at from_pos; confirm it belongs to current player
3. Get valid moves for that piece
4. Confirm to_pos is in valid moves
5. Simulate the move on a cloned board; check if own king is in check → reject if so
6. Apply move to real board
7. Switch turns; check checkmate/stalemate for opponent

**Edge cases:**
- No piece at from_pos
- Piece belongs to opponent
- Move puts own king in check (illegal even if piece can physically go there)
- Moving into checkmate end state

```java
public boolean makeMove(Position fromPos, Position toPos) {
    if (state != GameState.IN_PROGRESS) {
        return false;
    }

    Piece piece = board.getPiece(fromPos);
    if (piece == null || piece.getColor() != currentColor) {
        return false;
    }

    List<Position> validMoves = piece.getValidMoves(board);
    if (!validMoves.contains(toPos)) {
        return false;
    }

    // Simulate move; reject if own king ends up in check
    Board testBoard = board.clone();
    testBoard.movePiece(fromPos, toPos);
    if (testBoard.isInCheck(currentColor)) {
        return false;
    }

    // Apply to real board
    board.movePiece(fromPos, toPos);

    Color opponent = (currentColor == Color.WHITE) ? Color.BLACK : Color.WHITE;
    if (isCheckmate(opponent)) {
        state = (currentColor == Color.WHITE) ? GameState.WHITE_WINS : GameState.BLACK_WINS;
    } else if (isStalemate(opponent)) {
        state = GameState.DRAW;
    } else {
        currentColor = opponent;
    }

    return true;
}
```

### Core Method: `is_in_check`

```java
public boolean isInCheck(Color color) {
    Position kingPos = findKing(color);
    Color opponent = (color == Color.WHITE) ? Color.BLACK : Color.WHITE;
    for (Piece piece : getPieces(opponent)) {
        // getAttackSquares without check-filter to avoid infinite recursion
        if (piece.getAttackSquares(this).contains(kingPos)) {
            return true;
        }
    }
    return false;
}
```

### Core Method: `is_checkmate`

```java
public boolean isCheckmate(Color color) {
    if (!isInCheck(color)) {
        return false;
    }
    return hasNoLegalMoves(color);
}

public boolean isStalemate(Color color) {
    if (isInCheck(color)) {
        return false;
    }
    return hasNoLegalMoves(color);
}

private boolean hasNoLegalMoves(Color color) {
    for (Piece piece : getPieces(color)) {
        for (Position move : piece.getValidMoves(board)) {
            Board testBoard = board.clone();
            testBoard.movePiece(piece.getPosition(), move);
            if (!testBoard.isInCheck(color)) {
                return false;
            }
        }
    }
    return true;
}
```

### Piece: `Knight.get_valid_moves`

```java
@Override
public List<Position> getValidMoves(Board board) {
    int[][] offsets = {{-2,-1},{-2,1},{-1,-2},{-1,2},{1,-2},{1,2},{2,-1},{2,1}};
    List<Position> moves = new ArrayList<>();
    int r = position.getRow();
    int c = position.getCol();
    for (int[] offset : offsets) {
        Position pos = new Position(r + offset[0], c + offset[1]);
        if (pos.isValid()) {
            Piece occupant = board.getPiece(pos);
            if (occupant == null || occupant.getColor() != color) {
                moves.add(pos);
            }
        }
    }
    return moves;
}
```

### Piece: Sliding pieces (Rook, Bishop, Queen)

```java
protected List<Position> getSlidingMoves(Board board, int[][] directions) {
    List<Position> moves = new ArrayList<>();
    for (int[] direction : directions) {
        int dr = direction[0];
        int dc = direction[1];
        int r = position.getRow() + dr;
        int c = position.getCol() + dc;
        while (r >= 0 && r < 8 && c >= 0 && c < 8) {
            Position pos = new Position(r, c);
            Piece occupant = board.getPiece(pos);
            if (occupant != null) {
                if (occupant.getColor() != color) {
                    moves.add(pos); // capture
                }
                break; // blocked
            }
            moves.add(pos);
            r += dr;
            c += dc;
        }
    }
    return moves;
}
```

---

## Verification

```
Initial: White Rook at (7,0), Black King at (0,4), White King at (7,4)
White Queen at (6,4)

Turn: White makes move Queen (6,4) → (1,4)
  - piece = Queen (White) ✓
  - valid_moves includes (1,4) ✓
  - simulate: White King at (7,4) not in check ✓
  - apply move
  - is_in_check(Black)? Queen at (1,4) attacks (0,4) → YES, Black King in check
  - is_checkmate(Black)? Black King at (0,4):
      can go to (0,3), (0,5), (1,3), (1,5) — test each:
      Queen at (1,4) covers (1,3), (1,5), (0,4), (1,4)
      Rook at (7,0) covers column 0 only
      (0,3): not attacked → King can escape → NOT checkmate
  - state stays IN_PROGRESS
  - current_color = BLACK
```

---

## Deep Dive & Extensibility

### 1. "How does check detection avoid infinite recursion?"

`is_in_check` calls `piece.get_attack_squares(board)` — a separate method from `get_valid_moves`. `get_valid_moves` filters moves that leave the king in check (calls `is_in_check`). If `get_attack_squares` also called `get_valid_moves`, you'd have infinite recursion.

The fix: `get_attack_squares` returns the squares a piece *threatens* (pure geometry + captures), with no legality filter. `get_valid_moves` calls `get_attack_squares` and then filters out moves that leave own king in check.

```java
// Pawn attacks diagonally but moves forward — different squares
public class Pawn extends Piece {

    @Override
    public List<Position> getAttackSquares(Board board) {
        int direction = (color == Color.WHITE) ? -1 : 1;
        List<Position> attacks = new ArrayList<>();
        for (int dc : new int[] {-1, 1}) {
            Position pos = new Position(position.getRow() + direction, position.getCol() + dc);
            if (pos.isValid()) {
                attacks.add(pos);
            }
        }
        return attacks;
    }

    @Override
    public List<Position> getValidMoves(Board board) {
        List<Position> moves = new ArrayList<>();
        // forward move
        int direction = (color == Color.WHITE) ? -1 : 1;
        Position forward = new Position(position.getRow() + direction, position.getCol());
        if (forward.isValid() && board.getPiece(forward) == null) {
            moves.add(forward);
        }
        // diagonal captures
        for (Position attack : getAttackSquares(board)) {
            Piece occupant = board.getPiece(attack);
            if (occupant != null && occupant.getColor() != color) {
                moves.add(attack);
            }
        }
        return moves;
    }
}
```

### 2. "How would you add castling?"

Castling has three preconditions: King and Rook have not moved, no pieces between them, King is not in check and does not pass through check.

```java
public List<Position> getCastlingMoves(Board board) {
    List<Position> moves = new ArrayList<>();
    if (hasMoved || board.isInCheck(color)) {
        return moves;
    }

    // Kingside
    Position rookPos = new Position(position.getRow(), 7);
    Piece rook = board.getPiece(rookPos);
    if (rook != null && !rook.hasMoved()) {
        if (pathClearAndSafe(board, new int[][] {{0, 5}, {0, 6}})) {
            moves.add(new Position(position.getRow(), 6));
        }
    }

    // Queenside similar...
    return moves;
}

private boolean pathClearAndSafe(Board board, int[][] offsets) {
    for (int[] offset : offsets) {
        Position pos = new Position(position.getRow() + offset[0], position.getCol() + offset[1]);
        if (board.getPiece(pos) != null) {
            return false;
        }
        // simulate king moving through square
        Board test = board.clone();
        test.movePiece(position, pos);
        if (test.isInCheck(color)) {
            return false;
        }
    }
    return true;
}
```

Add `has_moved: bool` flag to `Piece`. Set it in `Board.move_piece`.

### 3. "How would you add an AI opponent?"

Keep `Game` and `Board` unchanged. Add a `ChessEngine` that picks a move given the game state.

```java
public class ChessEngine {

    public Move chooseMove(Game game, Color color, int depth) {
        return minimax(game.getBoard(), color, depth, true).getMove();
    }

    private ScoredMove minimax(Board board, Color color, int depth, boolean maximizing) {
        if (depth == 0) {
            return new ScoredMove(evaluate(board, color), null);
        }
        Move bestMove = null;
        double bestScore = maximizing ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        for (Piece piece : board.getPieces(color)) {
            for (Position move : piece.getValidMoves(board)) {
                Board test = board.clone();
                test.movePiece(piece.getPosition(), move);
                double score = minimax(test, opponent(color), depth - 1, !maximizing).getScore();
                if (maximizing && score > bestScore) {
                    bestScore = score;
                    bestMove = new Move(piece.getPosition(), move);
                } else if (!maximizing && score < bestScore) {
                    bestScore = score;
                    bestMove = new Move(piece.getPosition(), move);
                }
            }
        }
        return new ScoredMove(bestScore, bestMove);
    }
}
```

Alpha-beta pruning cuts search tree significantly (O(b^d) → O(b^(d/2)) in best case).

### 4. "How would you add draw by repetition?"

Track board state hashes in Game. After each move, hash the board (piece positions + color to move). If the same hash appears 3 times → draw.

```java
public class Game {
    private final Map<String, Integer> positionHistory = new HashMap<>();

    public boolean makeMove(Position fromPos, Position toPos) {
        // ... apply move ...
        String boardHash = hashBoard();
        positionHistory.merge(boardHash, 1, Integer::sum);
        if (positionHistory.get(boardHash) >= 3) {
            state = GameState.DRAW;
        }
        return true;
    }
}
```

---

## Interviewer Questions by Level

**Junior**: Identify the need for a `Piece` hierarchy and a `Board`. Implement move generation for at least 2-3 piece types correctly. Handle turn switching. Win detection is acceptable even if check validation is incomplete.

**Mid-level**: Clean separation — `Board` owns grid state, `Game` orchestrates, `Piece` owns move generation. `make_move` must validate legality including check. Discuss the infinite-recursion problem in check detection. Handle checkmate and stalemate.

**Senior**: Produce check detection via simulation (clone board + test) without recursion bugs. `get_attack_squares` vs `get_valid_moves` distinction is explicit. Proactively discuss castling/en passant extension points. Minimax AI walkthrough. Board cloning efficiency (sparse dict is O(pieces), not O(64)).

---

## Common Interview Questions

- **Q**: Why use an abstract `Piece` class instead of a single class with a `type` field?
  **A**: Open/Closed Principle — adding a new piece type (e.g., Fairy chess pieces) requires only a new subclass, not touching existing code. With a type field, `get_valid_moves` becomes a giant switch statement that grows with every new type.

- **Q**: How do you detect if a move puts the own king in check?
  **A**: Clone the board, apply the move to the clone, then call `is_in_check(own_color)` on the clone. Never modify real board state during validation.

- **Q**: Why does `get_attack_squares` differ from `get_valid_moves`?
  **A**: To avoid infinite recursion. `get_valid_moves` filters moves that expose the king (calls `is_in_check`). `is_in_check` asks opponents what squares they attack. If attack-squares called `get_valid_moves` → infinite loop.

- **Q**: dict vs 2D array for the board — which is better?
  **A**: dict (`Position → Piece`) is better for this problem: only 32 pieces max, so it's more memory-efficient, cloning is O(pieces) not O(64), and `get_piece(pos)` returning `None` cleanly represents empty squares.

- **Q**: How does checkmate differ from stalemate?
  **A**: Checkmate = in check AND no legal moves. Stalemate = NOT in check AND no legal moves. Both end the game, but checkmate is a loss, stalemate is a draw.

- **Q**: How would you make `board.clone()` efficient?
  **A**: With a map, clone is `new HashMap<>(this.grid)` plus reconstructing each Piece. Since there are at most 32 pieces and each is lightweight (color + position), this is O(32) = O(1) effectively.

---

## Related

**Patterns applied here**

- [Factory Pattern](../../03-design-patterns/01-creational/factory-pattern.md)
- [Strategy Pattern](../../03-design-patterns/03-behavioral/strategy-pattern.md)
- [Memento Pattern](../../03-design-patterns/03-behavioral/memento-pattern.md) — capture move history to support undo / takeback

**SOLID focus**: [Open/Closed](../../02-solid-principles/02-open-closed.md) · [Liskov Substitution](../../02-solid-principles/03-liskov-substitution.md)

**Practice next**

- [Design Tic-Tac-Toe](../01-core-problems/03-design-tic-tac-toe.md)
- [Design Tetris](../04-advanced-niche/28-design-tetris.md)

Start with tic-tac-toe for the board and turn model.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
