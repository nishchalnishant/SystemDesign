# Design Snake & Ladder

> **Difficulty**: Beginner  
> **Topics**: Board Games, HashMap / 1D Array Logic, Randomness  
> **Entities**: Board, Dice, Player, Jump (Snake/Ladder).

## Problem Statement

Create a 10x10 board game where players race to 100.
- **Snakes**: Move you down.
- **Ladders**: Move you up.

## Core Logic

- **Board**: Don't use 2D array. Use 1D array of size 101.
- **Jump**: Snake and Ladder are the SAME entity `Jump(start, end)`.
  - Snake: start > end.
  - Ladder: start < end.

## Implementation

```python
import random
from collections import deque

class Jump:
    def __init__(self, start, end):
        self.start = start
        self.end = end

class Cell:
    def __init__(self):
        self.jump = None

class Board:
    def __init__(self, size, count_snakes, count_ladders):
        self.size = size
        self.cells = [Cell() for _ in range(size*size + 1)]
        self._add_random_jumps(count_snakes, count_ladders)

class Player:
    def __init__(self, id):
        self.id = id
        self.pos = 0

class Dice:
    def roll(self):
        return random.randint(1, 6)

class Game:
    def __init__(self):
        self.board = Board(10, 5, 5)
        self.dice = Dice()
        self.players = deque([Player("P1"), Player("P2")])
        self.winner = None

    def start(self):
        while not self.winner:
            player = self.players.popleft()
            val = self.dice.roll()
            new_pos = player.pos + val
            
            if new_pos > 100:
                self.players.append(player)
                continue
                
            # Check Jump
            cell = self.board.cells[new_pos]
            if cell.jump:
                new_pos = cell.jump.end
            
            player.pos = new_pos
            
            if new_pos == 100:
                self.winner = player
                print(f"Winner: {player.id}")
                return

            self.players.append(player)
```

## Key Points

1.  **Polymorphism (Not needed)**:
    -   We don't need `class Snake` and `class Ladder`. They have identical behavior (teleport player). A single `Jump` class is cleaner.
2.  **Dice Strategy**:
    -   If interviewer asks for "Rigged Dice", implement `DiceStrategy` interface.
