# Design Android Unlock Pattern

> **Difficulty**: Medium  
> **Topics**: Graph Theory, DFS/Backtracking, Validation Logic  
> **Key Concept**: Midpoint Formula for Skip Logic

## Problem Statement

Design the Android 3x3 Grid Unlock Pattern system.
- **Grid**: 9 dots (0-8).
- **Rules**:
  1.  Min length 4 nodes.
  2.  Cannot visit same node twice (unless hopping over it, but that node must be visited).
  3.  **Skip Rule**: Connecting a non-adjacent node (e.g., 0 to 2) is ONLY valid if the middle node (1) has completely been visited.

## Implementation

### Point Entity

```python
class Point:
    def __init__(self, row, col):
        self.row = row
        self.col = col
        self.index = row * 3 + col

    def __eq__(self, other):
        return self.row == other.row and self.col == other.col
    
    def __hash__(self):
        return hash(self.index)
```

### Pattern Validator (The Hard Part)

The core logic handles the "Skip" rule using the midpoint formula.
- Midpoint of `(r1, c1)` and `(r2, c2)` = `((r1+r2)/2, (c1+c2)/2)`.
- If midpoint coordinates are integers, there is a node in between.

```python
class PatternValidator:
    def __init__(self):
        self.MIN_LENGTH = 4

    def is_valid_pattern(self, pattern: list[Point]) -> bool:
        if len(pattern) < self.MIN_LENGTH:
            return False
        
        visited = set()
        visited.add(pattern[0])

        for i in range(len(pattern) - 1):
            curr = pattern[i]
            next_node = pattern[i+1]

            # 1. Check revisits
            if next_node in visited:
                return False

            # 2. Check illegal skips
            intermediate = self._get_intermediate_point(curr, next_node)
            if intermediate and intermediate not in visited:
                return False # Skipped over unvisited node!

            visited.add(next_node)
            
        return True

    def _get_intermediate_point(self, p1: Point, p2: Point):
        row_sum = p1.row + p2.row
        col_sum = p1.col + p2.col

        # If sum is odd, midpoint is 0.5 -> No integer node in between
        if row_sum % 2 != 0 or col_sum % 2 != 0:
            return None

        return Point(row_sum // 2, col_sum // 2)
```

### Unlock System

```python
class PatternLockSystem:
    def __init__(self):
        self.saved_hash = None
        self.validator = PatternValidator()

    def set_pattern(self, pattern):
        if self.validator.is_valid_pattern(pattern):
            self.saved_hash = self._hash(pattern)
            return True
        return False

    def unlock(self, input_pattern):
        if not self.saved_hash: return False
        return self._hash(input_pattern) == self.saved_hash
    
    def _hash(self, pattern):
        # In production, use SHA-256 with Salt
        return "->".join([str(p.index) for p in pattern])
```

## Interview Q&A

**Q: "How to count total valid patterns?"**
- A: "Use DFS / Backtracking. Recursively traverse from each starting node, keeping track of `visited` set. Total is ~389,000 valid patterns."

**Q: "Security?"**
- A: "Salt and Hash the pattern sequence. Don't store plain lists."

**Q: "Knight's Move (0 -> 5)?"**
- A: "Valid. The midpoint logic `(0+1)/2 = 0.5` correctly returns `None`, meaning no node is skipped over."
