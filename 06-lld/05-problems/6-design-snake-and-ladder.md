# Design Snake & Ladder

> **Difficulty**: Beginner
> **Topics**: Board Games, Iterator Pattern, Memento Pattern, HashMap Logic
> **Extension**: Multiple dice, special cells, game save/load

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

```java
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

// ── Player ─────────────────────────────────────────────────────────────────

class Player {
    final String id;
    int currentPosition;

    Player(String id) { this.id = id; this.currentPosition = 0; }
}

// ── Dice with Strategy Interface ───────────────────────────────────────────

interface DiceStrategy {
    int roll();
}

class StandardDice implements DiceStrategy {
    private final int count;
    StandardDice(int count) { this.count = count; }

    public int roll() {
        int total = 0;
        for (int i = 0; i < count; i++)
            total += ThreadLocalRandom.current().nextInt(1, 7);
        return total;
    }
}

// ── Board ──────────────────────────────────────────────────────────────────

class Board {
    private final int size;
    // Maps landing cell → destination cell (both snakes and ladders)
    private final Map<Integer, Integer> jumpMap = new HashMap<>();

    Board(int size) {
        this.size = size;
    }

    // Add a snake (start > end) or ladder (start < end)
    public void addJump(int from, int to) {
        if (from <= 0 || from > size || to <= 0 || to > size)
            throw new IllegalArgumentException("Jump out of bounds: " + from + "→" + to);
        if (jumpMap.containsKey(from))
            throw new IllegalArgumentException("Cell " + from + " already has a jump.");
        jumpMap.put(from, to);
    }

    public int getJumpDestination(int position) {
        return jumpMap.getOrDefault(position, position);
    }

    public int getSize() { return size; }

    // Cycle detection: ensure no infinite loop A→B→C→A
    public void validateNoCycles() {
        for (int start : jumpMap.keySet()) {
            Set<Integer> visited = new HashSet<>();
            int curr = start;
            while (jumpMap.containsKey(curr)) {
                if (!visited.add(curr))
                    throw new IllegalStateException("Cycle detected at cell " + curr);
                curr = jumpMap.get(curr);
            }
        }
    }
}

// ── Memento (Game Save) ────────────────────────────────────────────────────

class GameMemento {
    private final Map<String, Integer> playerPositions;
    private final List<String> turnOrder;

    GameMemento(Map<String, Integer> positions, List<String> turnOrder) {
        this.playerPositions = new HashMap<>(positions);
        this.turnOrder       = new ArrayList<>(turnOrder);
    }

    Map<String, Integer> getPositions() { return Collections.unmodifiableMap(playerPositions); }
    List<String> getTurnOrder()          { return Collections.unmodifiableList(turnOrder); }
}

// ── Game Orchestrator ──────────────────────────────────────────────────────

public class Game {
    private final Board board;
    private final DiceStrategy dice;
    private final Deque<Player> players = new ArrayDeque<>();
    private Player winner;

    public Game(Board board, DiceStrategy dice) {
        this.board = board;
        this.dice  = dice;
        board.validateNoCycles();
    }

    public void addPlayer(Player p) { players.addLast(p); }

    public void startGame() {
        while (winner == null) {
            Player active = players.removeFirst();

            int roll        = dice.roll();
            int newPosition = active.currentPosition + roll;

            if (newPosition > board.getSize()) {
                // Overshoot: stay put
                System.out.printf("%s rolled %d → overshoots! Stays at %d%n",
                        active.id, roll, active.currentPosition);
                players.addLast(active);
                continue;
            }

            int finalPosition = board.getJumpDestination(newPosition);

            if (finalPosition != newPosition) {
                String jumpType = finalPosition > newPosition ? "LADDER" : "SNAKE";
                System.out.printf("%s rolled %d → %d, then %s to %d%n",
                        active.id, roll, newPosition, jumpType, finalPosition);
            } else {
                System.out.printf("%s rolled %d → %d%n", active.id, roll, newPosition);
            }

            active.currentPosition = finalPosition;

            if (finalPosition == board.getSize()) {
                winner = active;
                System.out.println("WINNER: " + winner.id);
                return;
            }

            players.addLast(active);
        }
    }

    // ── Memento: Save ─────────────────────────────────────────────────────

    public GameMemento saveGame() {
        Map<String, Integer> positions = new HashMap<>();
        List<String> turnOrder         = new ArrayList<>();
        for (Player p : players) {
            positions.put(p.id, p.currentPosition);
            turnOrder.add(p.id);
        }
        return new GameMemento(positions, turnOrder);
    }

    // ── Memento: Restore ──────────────────────────────────────────────────

    public void loadGame(GameMemento memento, Map<String, Player> playerRegistry) {
        players.clear();
        winner = null;
        Map<String, Integer> positions = memento.getPositions();
        for (String id : memento.getTurnOrder()) {
            Player p = playerRegistry.get(id);
            p.currentPosition = positions.get(id);
            players.addLast(p);
        }
        System.out.println("Game state restored.");
    }
}

// ── Demo ───────────────────────────────────────────────────────────────────

class SnakeLadderDemo {
    public static void main(String[] args) {
        Board board = new Board(100);
        // Ladders
        board.addJump(4,  38);
        board.addJump(8,  30);
        board.addJump(28, 84);
        // Snakes
        board.addJump(99, 10);
        board.addJump(62, 19);
        board.addJump(54, 34);

        Game game = new Game(board, new StandardDice(1));
        Player p1 = new Player("Alice");
        Player p2 = new Player("Bob");
        game.addPlayer(p1);
        game.addPlayer(p2);

        // Save state after a few turns would call: GameMemento m = game.saveGame();
        game.startGame();
    }
}
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
```java
interface CellEffect {
    int apply(Player player, int landedPosition);
}
class FreezeCellEffect implements CellEffect {
    // Skip player's next turn by marking them
}
// Board holds Map<Integer, CellEffect> alongside jumpMap
```
Use Chain of Responsibility: each handler checks if it applies, then passes to next.

**Board variants (NxN, different win cell):**
Constructor takes `size` parameter. Win condition checks `finalPosition == board.getSize()`.

**Game save to file:**
`GameMemento` implements `Serializable`. Serialize to JSON or binary. Load via `ObjectInputStream`.

**Multiple dice:**
`StandardDice(2)` already handles this — `roll()` sums N dice. For separate dice results (e.g., doubles rule): return `int[]` from strategy instead of `int`.
