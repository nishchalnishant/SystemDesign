---
module: 06-lld
topic: LLD Problems
status: unread
tags: [06-lld, system-design, lld, chess, state-machine, polymorphism]
---
# Design Chess

> **Difficulty**: Medium
> **Topics**: Polymorphism, Abstract Base Class, State Machine, Turn Management
> **Extension**: AI opponent (Strategy), en passant, castling, draw by repetition

---

## Phase 1: Requirements

### Functional
- Two players alternate turns (WHITE moves first).
- Each piece type enforces its own legal move set.
- Move validation rejects moves that leave the moving side's king in check.
- Detect check, checkmate (game over), and stalemate (draw).
- Draw conditions: stalemate only (repetition is a follow-up extension).

### Non-Functional
- All in-memory — no persistence needed for the interview.
- Clean OOP: Board and Game code must be piece-agnostic (Open/Closed).
- Extensible: adding a new piece type requires only a new subclass.

---

## Phase 2: Core Classes

| Class / Enum | Responsibility |
|---|---|
| `Color` | Enum: `WHITE`, `BLACK` |
| `Position(row, col)` | Immutable coordinate; `is_valid()` checks 0–7 bounds |
| `Piece` (abstract) | Holds `color`, `position`; declares `get_valid_moves(board)` |
| `King`, `Queen`, `Rook`, `Bishop`, `Knight`, `Pawn` | Each overrides `get_valid_moves` with piece-specific logic |
| `Board` | 8×8 grid (`dict[Position, Piece]`); `get_piece`, `move_piece`, `is_in_check` |
| `Player(name, color)` | Owns a name and color; no logic beyond data |
| `Game` | Turn state, move dispatch, checkmate/stalemate detection |

---

## Phase 3: Class Diagram

```
┌──────────────────────────────────────┐
│              Game                    │
│──────────────────────────────────────│
│ - board: Board                       │
│ - players: dict[Color, Player]       │
│ - current_color: Color               │
│──────────────────────────────────────│
│ + make_move(from, to) -> bool        │
│ + is_checkmate(color) -> bool        │
│ + is_stalemate(color) -> bool        │
└──────────────────┬───────────────────┘
                   │ owns
        ┌──────────▼──────────────┐
        │          Board          │
        │─────────────────────────│
        │ - _grid: dict[Pos,Piece]│
        │─────────────────────────│
        │ + get_piece(pos)        │
        │ + move_piece(piece, to) │
        │ + is_in_check(color)    │
        │ + find_king(color)      │
        └──────────┬──────────────┘
                   │ contains
        ┌──────────▼──────────────┐
        │     Piece  (abstract)   │
        │─────────────────────────│
        │ # color: Color          │
        │ # position: Position    │
        │─────────────────────────│
        │ + get_valid_moves(board)│  ← abstract
        └──────────┬──────────────┘
     ┌─────────────┼──────────────────────┐
     ▼             ▼                      ▼
  Knight          Pawn              King / Queen
                                    Rook / Bishop

┌────────────────────┐   ┌──────────────────────┐
│      Position      │   │        Player        │
│────────────────────│   │──────────────────────│
│ row: int           │   │ name: str            │
│ col: int           │   │ color: Color         │
│────────────────────│   └──────────────────────┘
│ is_valid() -> bool │
└────────────────────┘
```

**Turn state machine:**
```
WHITE_TURN ──legal move──► BLACK_TURN
BLACK_TURN ──legal move──► WHITE_TURN
Any turn   ──checkmate──►  GAME_OVER
Any turn   ──stalemate──►  DRAW
```

---

## Phase 4: Key Implementation

```python
from abc import ABC, abstractmethod
from enum import Enum

# ── Core types ─────────────────────────────────────────────────────────────

class Color(Enum):
    WHITE = "WHITE"
    BLACK = "BLACK"

class Position:
    def __init__(self, row, col):
        self.row = row
        self.col = col

    def is_valid(self):
        return 0 <= self.row < 8 and 0 <= self.col < 8

    def __eq__(self, other):
        return isinstance(other, Position) and self.row == other.row and self.col == other.col

    def __hash__(self):
        return hash((self.row, self.col))

# ── Piece hierarchy ────────────────────────────────────────────────────────

class Piece(ABC):
    def __init__(self, color, position):
        self.color = color
        self.position = position

    @abstractmethod
    def get_valid_moves(self, board):
        pass

class Knight(Piece):
    OFFSETS = [(-2,-1),(-2,1),(-1,-2),(-1,2),(1,-2),(1,2),(2,-1),(2,1)]

    def get_valid_moves(self, board):
        moves = []
        for dr, dc in self.OFFSETS:
            pos = Position(self.position.row + dr, self.position.col + dc)
            if pos.is_valid():
                occupant = board.get_piece(pos)
                if occupant is None or occupant.color != self.color:
                    moves.append(pos)
        return moves

class Pawn(Piece):
    def get_valid_moves(self, board):
        moves = []
        direction = 1 if self.color == Color.WHITE else -1
        r, c = self.position.row, self.position.col

        # Forward one square
        fwd = Position(r + direction, c)
        if fwd.is_valid() and board.get_piece(fwd) is None:
            moves.append(fwd)
            # Double move from starting rank
            start_rank = 1 if self.color == Color.WHITE else 6
            if r == start_rank:
                fwd2 = Position(r + 2 * direction, c)
                if board.get_piece(fwd2) is None:
                    moves.append(fwd2)

        # Diagonal captures
        for dc in (-1, 1):
            cap = Position(r + direction, c + dc)
            if cap.is_valid():
                occupant = board.get_piece(cap)
                if occupant is not None and occupant.color != self.color:
                    moves.append(cap)
        return moves

class King(Piece):
    OFFSETS = [(-1,-1),(-1,0),(-1,1),(0,-1),(0,1),(1,-1),(1,0),(1,1)]

    def get_valid_moves(self, board):
        moves = []
        for dr, dc in self.OFFSETS:
            pos = Position(self.position.row + dr, self.position.col + dc)
            if pos.is_valid():
                occupant = board.get_piece(pos)
                if occupant is None or occupant.color != self.color:
                    moves.append(pos)
        return moves

# ── Board ──────────────────────────────────────────────────────────────────

class Board:
    def __init__(self):
        self._grid = {}  # dict[Position, Piece]

    def place_piece(self, piece):
        self._grid[piece.position] = piece

    def get_piece(self, pos):
        return self._grid.get(pos)

    def move_piece(self, piece, to):
        captured = self._grid.pop(to, None)
        del self._grid[piece.position]
        piece.position = to
        self._grid[to] = piece
        return captured

    def find_king(self, color):
        for piece in self._grid.values():
            if isinstance(piece, King) and piece.color == color:
                return piece
        raise ValueError("King not found")

    def is_in_check(self, color):
        king = self.find_king(color)
        opponent = Color.BLACK if color == Color.WHITE else Color.WHITE
        return any(
            king.position in p.get_valid_moves(self)
            for p in self._grid.values()
            if p.color == opponent
        )

# ── Player ─────────────────────────────────────────────────────────────────

class Player:
    def __init__(self, name, color):
        self.name = name
        self.color = color

# ── Game ───────────────────────────────────────────────────────────────────

class Game:
    def __init__(self, player_white, player_black):
        self.board = Board()
        self.players = {Color.WHITE: player_white, Color.BLACK: player_black}
        self.current_color = Color.WHITE
        self._setup_board()

    def _setup_board(self):
        # Place kings (full setup would place all 32 pieces)
        self.board.place_piece(King(Color.WHITE, Position(0, 4)))
        self.board.place_piece(King(Color.BLACK, Position(7, 4)))
        for c in range(8):
            self.board.place_piece(Pawn(Color.WHITE, Position(1, c)))
            self.board.place_piece(Pawn(Color.BLACK, Position(6, c)))

    def make_move(self, from_pos, to_pos):
        piece = self.board.get_piece(from_pos)
        if piece is None or piece.color != self.current_color:
            return False
        if to_pos not in piece.get_valid_moves(self.board):
            return False

        # Try the move
        original_pos = piece.position
        captured = self.board.move_piece(piece, to_pos)

        # Reject if move exposes own king
        if self.board.is_in_check(self.current_color):
            self.board.move_piece(piece, original_pos)
            if captured:
                self.board.place_piece(captured)
            return False

        self._switch_turn()
        return True

    def _switch_turn(self):
        self.current_color = (
            Color.BLACK if self.current_color == Color.WHITE else Color.WHITE
        )

    def is_checkmate(self, color):
        return self.board.is_in_check(color) and self._has_no_legal_moves(color)

    def is_stalemate(self, color):
        return not self.board.is_in_check(color) and self._has_no_legal_moves(color)

    def _has_no_legal_moves(self, color):
        for piece in list(self.board._grid.values()):
            if piece.color != color:
                continue
            for move in piece.get_valid_moves(self.board):
                original_pos = piece.position
                captured = self.board.move_piece(piece, move)
                in_check = self.board.is_in_check(color)
                # Undo
                self.board.move_piece(piece, original_pos)
                if captured:
                    self.board.place_piece(captured)
                if not in_check:
                    return False
        return True
```

---

## Phase 5: Follow-up Questions

**Q1: How do you add a new piece type?**
Subclass `Piece`, implement `get_valid_moves`. `Board` and `Game` are piece-agnostic — no existing code changes (Open/Closed Principle).

**Q2: How do you handle en passant and castling?**
Special move flags on `Pawn`/`King` (e.g., `has_moved: bool`). `Game.make_move` checks preconditions before delegating: king must not have moved, must not currently be in check, squares between must be unoccupied and unattacked.

**Q3: How would you add an AI opponent?**
Strategy pattern: give `Player` a `choose_move(board) -> tuple[Position, Position]` method. `HumanPlayer` reads from input; `MinimaxPlayer` implements minimax with alpha-beta pruning. `Game` calls `player.choose_move()` each turn — no other changes.

**Q4: How do you detect draw by repetition?**
`Game` maintains a `list[frozenset]` (or Zobrist hash) of board states after each move. If the same state appears three times, declare draw. A `frozenset` of `(position, type(piece).__name__, piece.color)` tuples uniquely identifies a board position.

**Q5: What design pattern is central here?**
Polymorphism / Template Method: `Piece` defines the interface contract; each subclass encodes its own movement rules. `Board.is_in_check` and `Game._has_no_legal_moves` iterate pieces and call `get_valid_moves` without knowing which piece type they hold — the subclass decides.

---

## Interviewer Follow-Up Questions

- "What are your core classes for chess?" → `Board` (8×8 grid of `Cell`s), `Cell` (position + optional `Piece`), `Piece` (abstract: `color`, `position`, abstract `getValidMoves(Board)`), concrete pieces (`King`, `Queen`, `Rook`, `Bishop`, `Knight`, `Pawn`), `Game` (players, turn management, move validation, game state), `Player`, `Move` (source + target cell + piece).
- "How do you implement `getValidMoves()` for a Rook?" → Iterate in 4 directions (up, down, left, right): advance one step at a time. For each cell: if empty → valid move, continue. If occupied by opponent → valid move (capture), stop. If occupied by own piece → stop. Return all collected valid cells. The Rook's movement is direction-based iteration with early termination — the same pattern applies to Bishop (diagonals) and Queen (all 8 directions).
- "How does the game detect check?" → After any move: check if the moving player's king is now attacked. "Attacked" = any opponent piece's `getValidMoves()` includes the king's cell. If yes: the move was illegal (either move into check, or failed to escape check). A move is only valid if it doesn't leave the player in check. This means every move candidate must be simulated and checked — computationally expensive for complex boards.
- "How do you implement castling and en passant?" → Special moves that can't be derived from piece position alone — they depend on history. Castling: valid only if the king and rook haven't moved, no pieces between them, king doesn't pass through or end in check. Track `hasMoved` flag on `King` and `Rook`. En passant: valid only immediately after the opponent's pawn moves two squares. Track the last move on the `Game` object; the `Pawn.getValidMoves()` checks if the last move was a double-step by an adjacent enemy pawn. These are valid reasons to add state to `Game` beyond just the board.
- "How do you detect checkmate vs stalemate?" → Both: the current player has no legal moves. Checkmate: the player is in check AND has no legal moves (loses). Stalemate: the player is NOT in check but has no legal moves (draw). Algorithm: generate all moves for all pieces; simulate each move; check if it leaves the king in check; collect all moves that don't. If the resulting list is empty: check for check → checkmate or stalemate.
