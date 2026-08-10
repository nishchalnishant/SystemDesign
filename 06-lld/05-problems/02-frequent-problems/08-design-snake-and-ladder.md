> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design Snake and Ladder — a simpler, entity-relationship focused LLD problem that tests basic game loops and random number generation handling.
>
> **Key concepts:**
> - Core Entities: `Game`, `Board`, `Player`, `Dice`, `Jumper` (class representing both Snakes and Ladders).
> - The Board: usually an array or map of size 100.
> - The Jumper: a `Snake` is just a `Jumper` where `start > end`. A `Ladder` is a `Jumper` where `end > start`. Representing both as a single `Jumper(start, end)` class simplifies logic.
> - The Game Loop: Roll dice, calculate new position, check for Jumper at new position, update position, check for win condition (position >= 100).
> - Extensions: Multiple dice, rolling a 6 grants an extra turn (requires a `while` loop inside the player's turn), different board sizes.
>
> **Key takeaway:** Do not create separate `Snake` and `Ladder` classes. Creating a single `Jumper` (or `Entity`) class that maps a `start` position to an `end` position makes the logic incredibly clean.

---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, snake-and-ladder, strategy, observer, factory]
---
# Design Snake and Ladder

> **Difficulty**: Easy
> **Asked at**: Amazon, Intuit
> **Key Patterns**: Strategy (dice), Observer (position events), Factory (board setup)

---

## Understanding the Problem

Design a Snake and Ladder board game for 2 or more players, with a configurable board of snakes and ladders, dice rolling, and win detection.

---

## Clarifying Questions

**You**: "How many players and what board size are we supporting?"
**Interviewer**: "2 to 4 players, standard 100-cell board."

**You**: "Are snakes and ladders hardcoded or configurable?"
**Interviewer**: "Configurable — passed in at setup time."

**You**: "What if a snake head and a ladder base are at the same cell?"
**Interviewer**: "Good catch. Define it as invalid config — reject at setup."

**You**: "Do we need to support multiple dice?"
**Interviewer**: "One dice by default. Multiple dice is a follow-up."

**You**: "Can a player overshoot the last cell to win?"
**Interviewer**: "Exact roll required — if you overshoot, stay in place."

---

## Final Requirements

**In scope:**
1. 2–4 players take turns rolling a dice and moving
2. Landing on a snake head moves the player to the snake's tail (lower cell)
3. Landing on a ladder base moves the player to the ladder's top (higher cell)
4. Exact roll required to land on cell 100 to win
5. Invalid config (snake head == ladder base at same cell) is rejected at setup

**Out of scope:**
- Multiple dice (follow-up)
- Power-ups
- Undo
- Persistence

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|---------------|
| `Game` | Orchestrates turns, rolls dice, moves players, checks win |
| `Board` | Holds snakes and ladders; resolves final position after a move |
| `Player` | Tracks name and current position |
| `Dice` | Strategy — rolls and returns a value |
| `Snake` | Data: head → tail (head > tail) |
| `Ladder` | Data: base → top (top > base) |

`Game` calls `dice.roll()`, updates `player.position`, then asks `board.resolve(position)` to apply any snake/ladder. Game then checks if resolved position == 100.

---

## Class Design

### Board

| Requirement | What Board must track |
|-------------|----------------------|
| "Landing on snake head → tail" | snakes: dict[head, tail] |
| "Landing on ladder base → top" | ladders: dict[base, top] |
| "Exact roll to win" | size (100) |

```
class Board:
- size: int = 100
- snakes: dict[int, int]   # head → tail
- ladders: dict[int, int]  # base → top

+ Board(snakes, ladders)
+ resolve(position: int) -> int
+ is_valid_config() -> bool
```

### Player

```
class Player:
- name: str
- position: int = 0

+ get_position() -> int
+ set_position(pos: int)
+ get_name() -> str
```

### Dice (Strategy)

```
class Dice (abstract):
+ roll() -> int

class StandardDice(Dice):
+ roll() -> int   # random 1-6

class LoadedDice(Dice):   # for testing
- values: list[int]
+ roll() -> int   # pops from list
```

### Game

```
class Game:
- board: Board
- players: list[Player]
- dice: Dice
- current_index: int
- winner: Optional[Player]

+ Game(players, board, dice)
+ take_turn() -> TurnResult
+ get_current_player() -> Player
+ get_winner() -> Optional[Player]
+ is_over() -> bool
```

---

## Implementation

### Core Method: `take_turn`

**Core logic:**
1. Roll dice
2. Compute new position = current + roll
3. If new position > 100: stay (overshoot)
4. Resolve snakes/ladders: `board.resolve(new_position)`
5. Update player position
6. Check if position == 100 → winner
7. Advance to next player

**Edge cases:**
- Overshoot: position + roll > 100 → no move
- Snake on 100: impossible if config is valid
- Multiple snakes/ladders chained: resolve only once

```java
public TurnResult takeTurn() {
    if (isOver()) {
        return null;
    }

    Player player = getCurrentPlayer();
    int roll = dice.roll();
    int newPos = player.getPosition() + roll;

    if (newPos > board.getSize()) {
        newPos = player.getPosition(); // overshoot — stay
    } else {
        newPos = board.resolve(newPos);
    }

    player.setPosition(newPos);

    if (newPos == board.getSize()) {
        winner = player;
    }

    currentIndex = (currentIndex + 1) % players.size();

    return new TurnResult(player, roll, newPos);
}
```

### Core Method: `board.resolve`

```java
public int resolve(int position) {
    if (snakes.containsKey(position)) {
        return snakes.get(position);   // slide down
    }
    if (ladders.containsKey(position)) {
        return ladders.get(position);  // climb up
    }
    return position;
}
```

### Board validation

```java
public boolean isValidConfig() {
    Set<Integer> snakeHeads = snakes.keySet();
    Set<Integer> ladderBases = ladders.keySet();
    for (Integer head : snakeHeads) {
        if (ladderBases.contains(head)) {
            return false;
        }
    }
    for (Map.Entry<Integer, Integer> entry : snakes.entrySet()) {
        if (entry.getValue() >= entry.getKey()) {
            return false;
        }
    }
    for (Map.Entry<Integer, Integer> entry : ladders.entrySet()) {
        if (entry.getValue() <= entry.getKey()) {
            return false;
        }
    }
    return true;
}
```

---

## Verification

```
Board: snake at 17→7, ladder at 4→14
Players: Alice (pos=0), Bob (pos=0)

Turn 1 — Alice rolls 4:
  new_pos = 0 + 4 = 4
  board.resolve(4) → 14 (ladder!)
  Alice.position = 14

Turn 2 — Bob rolls 6:
  new_pos = 0 + 6 = 6
  board.resolve(6) → 6 (no snake/ladder)
  Bob.position = 6

Turn 3 — Alice rolls 3:
  new_pos = 14 + 3 = 17
  board.resolve(17) → 7 (snake!)
  Alice.position = 7

Turn N — Bob at 97, rolls 3:
  new_pos = 97 + 3 = 100
  board.resolve(100) → 100
  Bob.position = 100 == board.size → Bob wins!
```

---

## Deep Dive & Extensibility

### 1. "How would you support multiple dice?"

Replace `Dice` with `DiceSet`:

```java
public class DiceSet implements Dice {
    private final List<Dice> dice;

    public DiceSet(List<Dice> dice) {
        this.dice = dice;
    }

    @Override
    public int roll() {
        int total = 0;
        for (Dice d : dice) {
            total += d.roll();
        }
        return total;
    }
}
```

`Game` accepts any object with a `roll()` method — no other changes needed. This is the Strategy pattern paying off.

### 2. "How would you add power-ups (skip a turn, roll again)?"

**Option A** — Cell type enum: After resolving position, check `board.get_effect(new_pos)` → CellEffect (NONE, SKIP_TURN, ROLL_AGAIN). `take_turn` handles each case.

```java
CellEffect effect = board.getEffect(newPos);
if (effect == CellEffect.ROLL_AGAIN) {
    return takeTurn();   // same player rolls again
}
if (effect == CellEffect.SKIP_TURN) {
    skipFlags.put(currentIndex, true);
}
```

**Option B** — Observer: `Game` fires `PlayerLanded(player, pos)`. Power-up handlers subscribe. More extensible when effect types grow beyond 2-3.

### 3. "How would you add undo?"

Track move history as a stack of `(player_index, old_position, roll)`. Undo pops the last entry.

```java
public void undo() {
    if (history.isEmpty()) {
        return;
    }
    MoveRecord last = history.pop();
    players.get(last.getPlayerIndex()).setPosition(last.getOldPosition());
    currentIndex = last.getPlayerIndex();
    winner = null;
}
```

### 4. "What if we want to load board config from JSON?"

Factory static method on `Board`:

```java
public static Board fromConfig(Map<String, Object> config) {
    Map<Integer, Integer> snakes = new HashMap<>();
    for (Map<String, Integer> s : (List<Map<String, Integer>>) config.getOrDefault("snakes", new ArrayList<>())) {
        snakes.put(s.get("head"), s.get("tail"));
    }
    Map<Integer, Integer> ladders = new HashMap<>();
    for (Map<String, Integer> l : (List<Map<String, Integer>>) config.getOrDefault("ladders", new ArrayList<>())) {
        ladders.put(l.get("base"), l.get("top"));
    }
    Board board = new Board(snakes, ladders);
    if (!board.isValidConfig()) {
        throw new IllegalArgumentException("Invalid board configuration");
    }
    return board;
}
```

`Game` never sees the config format — open for new formats (YAML, DB) without touching game logic.

---

## Interviewer Questions by Level

**Junior**: Identify Board, Player, Game. Implement dice roll and position update. Handle snakes and ladders with a dict. Detect winner. May miss overshoot edge case.

**Mid-level**: Strategy for Dice (injectable for testing). `board.resolve()` as a clean abstraction. Board config validation. Overshoot handled. Discuss chaining vs single-resolve.

**Senior**: Proactively introduce `LoadedDice` for deterministic testing. Discuss cell effects as Observer vs enum. Config validation rejects ambiguous setups. Design supports 2–N players without if/else branching.

---

## Common Interview Questions

- **Q**: Why store snakes/ladders as dicts rather than a list of objects?
  **A**: Dicts give O(1) lookup by position. A list would require O(n) scan on every cell landing. The data (head→tail) is inherently key-value shaped.

- **Q**: Should snakes and ladders chain?
  **A**: Standard rules say no — resolve once. Chaining requires a while loop in `resolve()` and can loop infinitely on bad config. State the assumption and validate config against it.

- **Q**: Why does overshoot mean "stay" instead of "bounce back"?
  **A**: Standard rules say exact roll required. Bounce-back is a variant — clarify with interviewer. The current design requires changing exactly one condition in `take_turn`.

- **Q**: How do you test this without random dice?
  **A**: `LoadedDice` takes a predetermined sequence and pops values on each `roll()`. Inject via constructor — the game never knows it's not random.

- **Q**: What if two players are on the same cell?
  **A**: No conflict — position is per-player and independent. Multiple players can share a cell.

---

## Related

**Patterns applied here**

- [Factory Pattern](../../03-design-patterns/01-creational/factory-pattern.md)
- [Strategy Pattern](../../03-design-patterns/03-behavioral/strategy-pattern.md)

**SOLID focus**: [Open/Closed](../../02-solid-principles/02-open-closed.md)

**Practice next**

- [Design Tic-Tac-Toe](../01-core-problems/03-design-tic-tac-toe.md)
- [Design Minesweeper](../04-advanced-niche/25-design-minesweeper.md)

Grid traversal and cell-effect modelling recur in both.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
