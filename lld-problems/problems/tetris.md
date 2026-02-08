# Design Tetris Game

> **Difficulty**: Medium  
> **Topics**: Matrix Manipulation, Game Loop, Factory Pattern  
> **Key Logic**: Rotation Matrix, Collision Detection.

## Problem Statement

Blocks fall. Move them to clear lines.
- **Tetrominoes**: Shapes (I, O, T, S, Z, J, L).
- **Rotation**: 90-degree turn.

## Core Concepts

1.  **Shapes**: Defined as list of `Point(row, col)` relative to a pivot `(0,0)`.
2.  **Rotation Formula**: To rotate $(x, y)$ 90-deg clockwise: $(y, -x)$.
3.  **Collision**: Handled by Board. "Does this Point overlap with existing Block?"

## Implementation (Snippet)

### Tetromino

```python
class Point:
    def __init__(self, r, c): self.r, self.c = r, c

class Tetromino:
    def __init__(self, shape_coords):
        self.shape = shape_coords # List[Point]
        self.pos = Point(0, 4) # Top-Middle

    def rotate(self):
        # (r, c) -> (c, -r)
        self.shape = [Point(p.c, -p.r) for p in self.shape]
```

### Board

```python
class Board:
    def is_valid(self, piece):
        for p in piece.get_absolute_positions():
            if not self.in_bounds(p) or self.grid[p.r][p.c] != 0:
                return False
        return True

    def clear_lines(self):
        # Remove full rows, prepend empty rows
        pass
```

### Game Loop

```python
class Game:
    def update(self):
        # Gravity
        self.piece.move_down()
        if not self.board.is_valid(self.piece):
            self.piece.move_up() # Undo
            self.board.place(self.piece)
            self.board.clear_lines()
            self.spawn_next()
```

## Interview Q&A

**Q: "How to decouple Shapes from Game?"**
- A: "Tetromino Factory. `Factory.get_random_piece()` returns a Shape subclass. Game doesn't know about specific shapes."
