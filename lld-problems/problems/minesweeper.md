# Design Minesweeper

> **Difficulty**: Medium  
> **Topics**: Flood Fill (DFS/BFS), 2D Grid, Recursion  
> **Actions**: Click, Flag.

## Problem Statement

Grid of hidden cells. Some have Mines.
- **Click Mine**: Game Over.
- **Click Empty**: Reveal it. If 0 adjacent mines, recursively reveal neighbors (Flood Fill).
- **Click Number**: Reveal just that cell.

## Implementation

### Cell

```python
class Cell:
    def __init__(self):
        self.is_mine = False
        self.adj_mines = 0
        self.is_revealed = False
        self.is_flagged = False
```

### Board (Flood Fill)

```python
def click_cell(self, r, c):
    cell = self.grid[r][c]
    if cell.is_revealed: return
    
    cell.is_revealed = True
    
    if cell.is_mine:
        self.game_over = True
        return
    
    if cell.adj_mines == 0:
        # Recursively click neighbors
        for nr, nc in self.get_neighbors(r, c):
            self.click_cell(nr, nc)
```

## Algorithms Used

1.  **Initialization**:
    -   Randomly place M mines.
    -   Iterate all cells, count adjacent mines for each.
2.  **Flood Fill**:
    -   Used when clicking a '0' cell. It ripples out until it hits numbered cells.
