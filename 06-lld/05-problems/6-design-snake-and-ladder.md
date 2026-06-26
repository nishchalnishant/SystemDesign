---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, system-design, problems]
---
# Design Snake & Ladder

> **Difficulty**: Beginner
> **Topics**: Board Games, Iterator Pattern, Memento Pattern, HashMap Logic
> **Extension**: Multiple dice, special cells, game save/load

---

## What Breaks Without This Design?

```python
import random

class SnakeAndLadder:
    def __init__(self):
        self.player_positions: list[int] = []  # indexed by player_id
        self.current_player: int = 0
        # Snakes: head → tail. Ladders: foot → top. Hardcoded.
        self.snake_heads = [99, 70, 54, 36]
        self.snake_tails = [2,  32, 19, 6]
        self.ladder_feet = [3,  22, 42, 54]
        self.ladder_tops = [38, 58, 65, 80]

    def play_turn(self) -> None:
        roll = random.randint(1, 6)
        self.player_positions[self.current_player] += roll

        if self.player_positions[self.current_player] > 100:
            self.player_positions[self.current_player] -= roll  # undo overshoot
            return

        # Check snake
        for i, head in enumerate(self.snake_heads):
            if self.player_positions[self.current_player] == head:
                self.player_positions[self.current_player] = self.snake_tails[i]
                break

        # Check ladder
        for i, foot in enumerate(self.ladder_feet):
            if self.player_positions[self.current_player] == foot:
                self.player_positions[self.current_player] = self.ladder_tops[i]
                break

        if self.player_positions[self.current_player] == 100:
            print(f"Player {self.current_player} wins!")

        self.current_player = (self.current_player + 1) % len(self.player_positions)
```

**Concrete failures**:
1. **Board is hardcoded**: Snakes and ladders are in parallel arrays. Changing the board layout requires editing source code. No way to configure different boards.
2. **Two O(S) and O(L) scans per turn**: Linear scans through `snakeHeads[]` and `ladderFeet[]` to find a match. A `HashMap<cell → destination>` gives O(1) lookup.
3. **Snakes and ladders are structurally identical**: Both are "if you land on cell X, jump to cell Y." Two separate arrays encode the same relationship. Unify into a single `jumpMap: Map<Integer, Integer>`.
4. **No player abstraction**: `playerPositions[currentPlayer]` is an index into an array. You cannot store a player's name or implement different turn strategies (e.g., AI player).
5. **No save/restore**: There is no way to snapshot and restore `playerPositions` mid-game. Adding save/load requires restructuring the entire state.
6. **Turn cycling is manual**: `currentPlayer = (currentPlayer + 1) % n` is error-prone. A `Queue<Player>` with `poll()` + `offer()` captures the cycling intent more clearly and supports removing a player who wins.

---

## Derive the Class Structure

**Force 1 — Board as a `Map<Integer, Integer>`**: Snakes and ladders are identical in behavior — both redirect a landing cell to a different cell. One `jumpMap: Map<Integer, Integer>` handles both: positive jumps (ladders), negative jumps (snakes). Lookup is O(1). Board configuration is data, not code.

**Force 2 — Players need identity**: Extract `Player` (name, currentPosition). A `Queue<Player>` cycles through them naturally: `poll()` gets the current player, `offer()` re-enqueues them at the back (or removes them on win).

**Force 3 — Dice roll is a separate concern**: The die value source should be injectable for deterministic testing. Extract `Dice` interface with `roll()`. `StandardDice` uses `Random`; `LoadedDice` (for testing) returns a fixed sequence.

**Force 4 — Game save/load requires state snapshot**: The full game state is: all player positions + current turn order + which players are still active. Extract a `GameState` value object (Memento). `GameController.save()` creates a `GameState`; `GameController.restore(GameState)` restores it.

**Force 5 — Cycle detection on board setup**: A snake tail can be a ladder foot, creating an infinite loop (`A→B→A`). The board setup validator must detect cycles in `jumpMap` via DFS/BFS before the game starts.

**Result** — the class split these forces produce:
```
God class → GameController (turn loop, win detection, save/restore)
          → Board (jumpMap: Map<cell, destination>, size, cycle detection)
          → Player (name, position)
          → Dice (interface: int roll())
             → StandardDice, LoadedDice (testing)
          → GameState (Memento: snapshot of all player positions + turn order)
```

---

## Opening Analogy

Think about a physical board game sitting on a table. The board is simply a printed map where certain squares have pictures: a ladder foot means "if you land here, climb to the top", a snake mouth means "if you land here, slide to the tail". The board itself does not move — it is a static lookup table from `landing_cell → destination`. The die roll drives movement. Multiple players take turns — that is naturally an Iterator. And if someone knocks the board off the table mid-game, you want to restore the saved state — that is Memento.

The key insight: **the board is just a `Map<Integer, Integer>`** — cell number to destination. There is no reason to model snakes and ladders as separate classes unless they have different behavior (they do not — both are "move to another cell").

---

## Phase 1: Requirements

### Functional
- Support 2–6 players on a 1–100 numbered board (10×10).
- Each turn: roll one die (1–6), move forward.
- If the new cell has a snake head or ladder foot, jump to its tail/top.
- Exact 100 wins (overshoot: player stays; optional rule).
- Support saving and restoring game state (Memento).
- Cycle detection: board setup must not contain infinite snake/ladder loops.

### Non-Functional
- Board setup is O(S+L) where S=snakes, L=ladders.
- Each turn is O(1): dictionary lookup for jump.
- Game state serialization must be complete (all player positions + current turn).

---

## Phase 2: Use Cases

### Actors
- **Player** — rolls die, moves piece.
- **Game Controller** — enforces turn order, applies jumps, checks win.
- **Admin** — configures board (snakes, ladders), saves/loads game.

### UC1: Play a Turn
1. Controller dequeues the active player.
2. Player rolls die → gets value `d`.
3. Controller computes `newPos = currentPos + d`.
4. If `newPos > 100`: stay (overshoot rule). Else proceed.
5. Controller looks up `jumpMap.get(newPos)` — if present, apply jump and log type.
6. Update player position.
7. If `newPos == 100`: player wins.
8. Enqueue player at back (or remove if winner).

### UC2: Save Game State
1. Admin calls `saveGame()`.
2. Controller serializes a `GameMemento` containing: list of `(playerId, position)` pairs and `currentTurnIndex`.
3. Memento stored externally (file, DB, session).

### UC3: Load Game State
1. Admin calls `loadGame(memento)`.
2. Controller restores player positions and turn order from memento.
3. Game resumes from saved state.

---

## Phase 3: Class Diagram

```
┌──────────────────────────────────────┐
│               Game                   │  <<Orchestrator>>
│──────────────────────────────────────│
│ - board: Board                       │
│ - dice: Dice                         │
│ - players: Deque<Player>             │
│ - winner: Player                     │
│──────────────────────────────────────│
│ + startGame(): void                  │
│ + saveGame(): GameMemento            │
│ + loadGame(memento): void            │
│ - applyJump(pos): int                │
└──────────────────────────────────────┘
         │ uses            │ uses
         ▼                 ▼
┌──────────────┐   ┌──────────────────┐
│    Board     │   │      Dice        │
│──────────────│   │──────────────────│
│ - size: int  │   │ - diceCount: int │
│ - jumpMap:   │   │──────────────────│
│  Map<Int,Int>│   │ + roll(): int    │
│──────────────│   └──────────────────┘
│+ getJump(pos)│
│+ validateCycles()
└──────────────┘

┌─────────────────────┐
│       Player        │
│─────────────────────│
│ - id: String        │
│ - currentPosition   │
└─────────────────────┘

┌─────────────────────────────────────┐
│           GameMemento               │  <<Memento>>
│─────────────────────────────────────│
│ - playerStates: Map<String, Integer>│
│ - turnOrder: List<String>           │
└─────────────────────────────────────┘
```

**Why `Map<Integer, Integer>` for the board?**
A snake from 99→10 and a ladder from 4→38 are both "if you land on X, go to Y". They differ only in direction (snake: start > end; ladder: start < end). Same data structure, same lookup cost O(1). No need for separate `Snake` and `Ladder` classes.

---

## Phase 4: Design Patterns Applied

### 1. Iterator Pattern — Turn management
**Why:** `Deque<Player>` acts as a circular iterator. `removeFirst()` gets the current player, `addLast()` puts them at the back. Winner is removed from the deque entirely. This naturally handles 2–6 players with no index arithmetic.

### 2. Memento Pattern — Save/Load game
**Why:** Game state is a snapshot of all player positions and whose turn it is. Memento captures this as an immutable value object. The game can be restored to any saved checkpoint without exposing internal implementation details to external code.

### 3. Strategy Pattern — Dice variations
**Why:** A `Dice` interface allows swapping `StandardDice` (1d6), `FastDice` (2d6 sum), or `RiggedDice` (fixed sequence for testing) without touching game logic.

---

## Phase 5: Key Java Implementation

```python
from __future__ import annotations
from abc import ABC, abstractmethod
from collections import deque
from dataclasses import dataclass, field
import random

# ── Player ─────────────────────────────────────────────────────────────────

@dataclass
class Player:
    id: str
    current_position: int = 0

# ── Dice with Strategy Interface ───────────────────────────────────────────

class DiceStrategy(ABC):
    @abstractmethod
    def roll(self) -> int: ...

class StandardDice(DiceStrategy):
    def __init__(self, count: int):
        self._count = count

    def roll(self) -> int:
        return sum(random.randint(1, 6) for _ in range(self._count))

# ── Board ──────────────────────────────────────────────────────────────────

class Board:
    def __init__(self, size: int):
        self._size = size
        # Maps landing cell → destination cell (both snakes and ladders)
        self._jump_map: dict[int, int] = {}

    # Add a snake (start > end) or ladder (start < end)
    def add_jump(self, from_: int, to: int) -> None:
        if not (0 < from_ <= self._size and 0 < to <= self._size):
            raise ValueError(f"Jump out of bounds: {from_}→{to}")
        if from_ in self._jump_map:
            raise ValueError(f"Cell {from_} already has a jump.")
        self._jump_map[from_] = to

    def get_jump_destination(self, position: int) -> int:
        return self._jump_map.get(position, position)

    def get_size(self) -> int:
        return self._size

    # Cycle detection: ensure no infinite loop A→B→C→A
    def validate_no_cycles(self) -> None:
        for start in self._jump_map:
            visited: set[int] = set()
            curr = start
            while curr in self._jump_map:
                if curr in visited:
                    raise RuntimeError(f"Cycle detected at cell {curr}")
                visited.add(curr)
                curr = self._jump_map[curr]

# ── Memento (Game Save) ────────────────────────────────────────────────────

@dataclass(frozen=True)
class GameMemento:
    player_positions: dict[str, int]   # immutable snapshot
    turn_order: tuple[str, ...]

# ── Game Orchestrator ──────────────────────────────────────────────────────

class Game:
    def __init__(self, board: Board, dice: DiceStrategy):
        self._board  = board
        self._dice   = dice
        self._players: deque[Player] = deque()
        self._winner: Player | None  = None
        board.validate_no_cycles()

    def add_player(self, p: Player) -> None:
        self._players.append(p)

    def start_game(self) -> None:
        while self._winner is None:
            active = self._players.popleft()

            roll         = self._dice.roll()
            new_position = active.current_position + roll

            if new_position > self._board.get_size():
                # Overshoot: stay put
                print(f"{active.id} rolled {roll} → overshoots! Stays at {active.current_position}")
                self._players.append(active)
                continue

            final_position = self._board.get_jump_destination(new_position)

            if final_position != new_position:
                jump_type = "LADDER" if final_position > new_position else "SNAKE"
                print(f"{active.id} rolled {roll} → {new_position}, then {jump_type} to {final_position}")
            else:
                print(f"{active.id} rolled {roll} → {new_position}")

            active.current_position = final_position

            if final_position == self._board.get_size():
                self._winner = active
                print(f"WINNER: {self._winner.id}")
                return

            self._players.append(active)

    # ── Memento: Save ─────────────────────────────────────────────────────

    def save_game(self) -> GameMemento:
        positions  = {p.id: p.current_position for p in self._players}
        turn_order = tuple(p.id for p in self._players)
        return GameMemento(player_positions=positions, turn_order=turn_order)

    # ── Memento: Restore ──────────────────────────────────────────────────

    def load_game(self, memento: GameMemento, player_registry: dict[str, Player]) -> None:
        self._players.clear()
        self._winner = None
        for id_ in memento.turn_order:
            p = player_registry[id_]
            p.current_position = memento.player_positions[id_]
            self._players.append(p)
        print("Game state restored.")

# ── Demo ───────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    board = Board(100)
    # Ladders
    board.add_jump(4,  38)
    board.add_jump(8,  30)
    board.add_jump(28, 84)
    # Snakes
    board.add_jump(99, 10)
    board.add_jump(62, 19)
    board.add_jump(54, 34)

    game = Game(board, StandardDice(1))
    p1 = Player("Alice")
    p2 = Player("Bob")
    game.add_player(p1)
    game.add_player(p2)

    # Save state after a few turns would call: memento = game.save_game()
    game.start_game()
```

---

## Phase 6: Trade-offs and Extensions

### Trade-offs

| Decision | Choice | Alternative | Reason |
|---|---|---|---|
| Snake + Ladder representation | Single `Map<Int,Int>` | Separate `Snake`/`Ladder` classes | Behavior is identical; data differs. Single class is simpler. |
| Overshoot rule | Stay put | Bounce back (100-overshoot) | Stay-put is the most common rule; easily configurable |
| Cycle detection | DFS at setup | Runtime check per jump | Fail-fast at board construction; O(S+L) one-time cost |
| Turn management | `Deque` rotation | Index counter + modulo | Deque is cleaner; easier to remove a winner mid-game |

### Extensions

**Special cells (freeze, double roll):**
```python
from abc import ABC, abstractmethod

class CellEffect(ABC):
    @abstractmethod
    def apply(self, player: Player, landed_position: int) -> int: ...

class FreezeCellEffect(CellEffect):
    def apply(self, player: Player, landed_position: int) -> int:
        player.frozen = True   # Skip player's next turn by marking them
        return landed_position

# Board holds dict[int, CellEffect] alongside jump_map
```
Use Chain of Responsibility: each handler checks if it applies, then passes to next.

**Board variants (NxN, different win cell):**
Constructor takes `size` parameter. Win condition checks `finalPosition == board.getSize()`.

**Game save to file:**
`GameMemento` implements `Serializable`. Serialize to JSON or binary. Load via `ObjectInputStream`.

**Multiple dice:**
`StandardDice(2)` already handles this — `roll()` sums N dice. For separate dice results (e.g., doubles rule): return `int[]` from strategy instead of `int`.
