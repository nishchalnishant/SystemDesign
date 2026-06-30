---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, unlock-pattern, dfs, backtracking, constraint-validation]
---
# Design Unlock Pattern

> **Difficulty**: Medium
> **Asked at**: Amazon, Google, LeetCode (Android Unlock Pattern)
> **Key Patterns**: DFS / Backtracking, Adjacency constraint validation, Graph traversal

---

## Understanding the Problem

Design the Android unlock pattern system. A 3×3 grid of dots (numbered 1–9). The user draws a pattern by connecting dots. Count (or validate) all valid patterns of a given length, subject to skip constraints.

---

## Clarifying Questions

**You**: "What makes a pattern invalid?"
**Interviewer**: "If drawing a line between two dots passes through a third dot, that third dot must have already been visited."

**You**: "Do we count all valid patterns of a given length, or validate a specific pattern?"
**Interviewer**: "Both — count all valid patterns of length m to n, and validate a given sequence."

**You**: "Is the grid always 3×3?"
**Interviewer**: "Yes."

**You**: "Does order matter — is 1→2→3 different from 3→2→1?"
**Interviewer**: "Yes, order matters. Different patterns."

**You**: "Minimum pattern length?"
**Interviewer**: "Minimum 4 dots, maximum 9."

---

## Final Requirements

**In scope:**
1. Count all valid unlock patterns of length m to n (1 ≤ m ≤ n ≤ 9)
2. Validate whether a given sequence of dot indices is a valid unlock pattern
3. Precompute the "skip" map: if going from A to B skips through C, C must be visited

**Out of scope:**
- UI / rendering
- Actual Android touch gesture recognition
- Storing or authenticating patterns against a saved password

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|---------------|
| `UnlockPatternGrid` | 3×3 grid; precomputes skip constraints |
| `PatternValidator` | Validates a given dot sequence |
| `PatternCounter` | Counts valid patterns via DFS with backtracking |
| `SkipMap` | `skip[a][b] = c` means going a→b crosses dot c |

The core logic is DFS backtracking constrained by the skip map. The skip map is precomputed once at initialization.

---

## Class Design

### UnlockPatternGrid

```
class UnlockPatternGrid:
- skip: dict[tuple, int]   # (a, b) → c, or empty if no skip

+ __init__()   # precompute skip map
+ get_skip(a, b) -> Optional[int]   # 0-indexed dot numbers
```

### PatternValidator

```
class PatternValidator:
- grid: UnlockPatternGrid

+ is_valid(pattern: list[int]) -> bool
```

### PatternCounter

```
class PatternCounter:
- grid: UnlockPatternGrid

+ count_patterns(min_len: int, max_len: int) -> int
+ _dfs(current: int, visited: set[int], remaining: int) -> int
```

---

## Implementation

### Precomputing the Skip Map

Dots are numbered 1–9, arranged in a 3×3 grid:
```
1 2 3
4 5 6
7 8 9
```

For each pair (a, b), check if the segment passes through a midpoint. A midpoint c exists when a and b are symmetric around c (i.e., `c = (a + b) / 2` is an integer) AND a, b, c share the same row, column, or diagonal.

```python
class UnlockPatternGrid:
    def __init__(self):
        # Dots 1-9, row/col as (row, col) 0-indexed
        self.pos = {
            1: (0,0), 2: (0,1), 3: (0,2),
            4: (1,0), 5: (1,1), 6: (1,2),
            7: (2,0), 8: (2,1), 9: (2,2)
        }
        self.skip = {}
        self._precompute_skips()

    def _precompute_skips(self):
        dots = list(self.pos.keys())
        for a in dots:
            for b in dots:
                if a == b:
                    continue
                ra, ca = self.pos[a]
                rb, cb = self.pos[b]
                # midpoint is an integer only when a+b is even and they are collinear
                mid_r = (ra + rb)
                mid_c = (ca + cb)
                if mid_r % 2 == 0 and mid_c % 2 == 0:
                    mid = (mid_r // 2, mid_c // 2)
                    # find dot at mid position
                    for c, pos in self.pos.items():
                        if pos == mid and c != a and c != b:
                            self.skip[(a, b)] = c
                            break

    def get_skip(self, a, b):
        return self.skip.get((a, b))
```

**Skip examples:**
- 1 → 3: passes through 2 (same row, midpoint)
- 1 → 7: passes through 4 (same column)
- 1 → 9: passes through 5 (main diagonal)
- 3 → 7: passes through 5 (anti-diagonal)
- 1 → 6: NO skip (knight move — no dot on the path)

### Core Method: `PatternCounter._dfs`

**Core logic:**
1. If remaining == 0: found a valid pattern of the target length → return 1
2. For each unvisited dot next_dot:
   a. Check if going from current → next_dot requires a skip dot
   b. If skip dot exists and is NOT visited → invalid move, skip
   c. Otherwise: mark next_dot visited, recurse, unmark (backtrack)

```python
class PatternCounter:
    def __init__(self, grid):
        self.grid = grid

    def count_patterns(self, min_len, max_len):
        total = 0
        for start in range(1, 10):
            visited = {start}
            for length in range(min_len - 1, max_len):
                total += self._dfs(start, visited, length)
            # Note: _dfs counts patterns where remaining more dots are needed
        return total

    def _dfs(self, current, visited, remaining):
        if remaining == 0:
            return 1
        count = 0
        for next_dot in range(1, 10):
            if next_dot in visited:
                continue
            skip = self.grid.get_skip(current, next_dot)
            if skip and skip not in visited:
                continue   # skip dot not yet visited — invalid move
            visited.add(next_dot)
            count += self._dfs(next_dot, visited, remaining - 1)
            visited.remove(next_dot)
        return count

    def count_patterns_range(self, min_len, max_len):
        total = 0
        for start in range(1, 10):
            for target_len in range(min_len, max_len + 1):
                visited = {start}
                total += self._dfs(start, visited, target_len - 1)
        return total
```

### Core Method: `PatternValidator.is_valid`

```python
class PatternValidator:
    def __init__(self, grid):
        self.grid = grid

    def is_valid(self, pattern):
        if len(pattern) < 4 or len(pattern) > 9:
            return False
        if len(set(pattern)) != len(pattern):
            return False  # duplicate dots
        if any(d < 1 or d > 9 for d in pattern):
            return False

        visited = set()
        for i, dot in enumerate(pattern):
            if i > 0:
                prev = pattern[i - 1]
                skip = self.grid.get_skip(prev, dot)
                if skip and skip not in visited:
                    return False
            visited.add(dot)

        return True
```

---

## Verification

```
Grid dot positions:
  1(0,0) 2(0,1) 3(0,2)
  4(1,0) 5(1,1) 6(1,2)
  7(2,0) 8(2,1) 9(2,2)

Skip precomputation:
  (1,3): mid=(0,1)=dot2 → skip[1,3]=2
  (1,9): mid=(1,1)=dot5 → skip[1,9]=5
  (3,7): mid=(1,1)=dot5 → skip[3,7]=5
  (2,8): mid=(1,1)=dot5 → skip[2,8]=5
  (4,6): mid=(1,1)=dot5 → skip[4,6]=5
  (1,7): mid=(1,0)=dot4 → skip[1,7]=4

Validate pattern [1, 2, 3, 6]:
  1→2: skip? (1,2)→mid=(0,0.5) not integer → no skip. visited={1}. add 2
  2→3: skip? (2,3)→mid=(0,1.5) not integer → no skip. visited={1,2}. add 3
  3→6: skip? (3,6)→mid=(0.5,2) not integer → no skip. visited={1,2,3}. add 6
  → valid ✓

Validate pattern [1, 3, 9, 7]:
  1→3: skip dot=2, visited={}? 2 not visited → INVALID ✗

Validate pattern [2, 1, 3, 9]:
  2→1: no skip. visited={2}. add 1
  1→3: skip dot=2, visited={2}? 2 IS visited → allowed. add 3
  3→9: no skip (3,9)→mid=(1,2.5) not integer. add 9
  → valid ✓
```

---

## Deep Dive & Extensibility

### 1. "Can you use symmetry to speed up the count?"

Yes. The 3×3 grid has 8 symmetries (4 rotations × 2 reflections). Corners (1,3,7,9) are symmetric, edge midpoints (2,4,6,8) are symmetric, and center (5) is unique. Count patterns starting from one corner, multiply by 4; one edge midpoint, multiply by 4; center, multiply by 1:

```python
def count_patterns_optimized(self, min_len, max_len):
    total = 0
    for target_len in range(min_len, max_len + 1):
        # Corner: 4 symmetric corners (1, 3, 7, 9)
        visited = {1}
        total += 4 * self._dfs(1, visited, target_len - 1)
        # Edge midpoint: 4 symmetric midpoints (2, 4, 6, 8)
        visited = {2}
        total += 4 * self._dfs(2, visited, target_len - 1)
        # Center: unique
        visited = {5}
        total += self._dfs(5, visited, target_len - 1)
    return total
```

This reduces DFS calls from 9 starting points to 3.

### 2. "How do you count patterns of length 4 to 9?"

Call the DFS for each starting dot, for each target length in [4, 9]:

```python
def count_range(self, min_len, max_len):
    return sum(
        self._dfs(start, {start}, length - 1)
        for start in range(1, 10)
        for length in range(min_len, max_len + 1)
    )
```

### 3. "What if the grid is n × n instead of 3 × 3?"

Generalize: dots are numbered 1 to n², positions computed from `(i // n, i % n)`. Skip detection still uses midpoint arithmetic. DFS structure is unchanged — only the dot count and position map change.

### 4. "How would you store and verify a user's unlock pattern?"

Store the hashed pattern (SHA256 of the dot sequence as a string) — never the raw sequence:

```python
def store_pattern(self, pattern):
    self.pattern_hash = hashlib.sha256(str(pattern).encode()).hexdigest()

def verify_pattern(self, input_pattern):
    if not PatternValidator(self.grid).is_valid(input_pattern):
        return False
    return hashlib.sha256(str(input_pattern).encode()).hexdigest() == self.pattern_hash
```

---

## Interviewer Questions by Level

**Junior**: Build the 3×3 grid. Validate a given pattern (no duplicate dots, length 4–9). Skip constraint check for same-row/column/diagonal pairs.

**Mid-level**: Precompute skip map. DFS backtracking with visited set. Count all valid patterns of a given length. Symmetry optimization (3 starting types instead of 9).

**Senior**: Generalize to n×n grid. Hashing for secure pattern storage. DFS time complexity analysis: O(9! / (9-k)!) patterns = O(9!) worst case. Pruning via skip constraints.

---

## Common Interview Questions

- **Q**: What is the skip constraint in the unlock pattern problem?
  **A**: If drawing a line from dot A to dot B passes through dot C (A, B, C are collinear with C at the midpoint), then C must have been visited before this move. Otherwise the move is invalid.

- **Q**: How do you detect if going from A to B skips through C?
  **A**: C is the midpoint of A and B if `(row_A + row_B) / 2 == row_C` and `(col_A + col_B) / 2 == col_C`. Since dot positions are integers, the midpoint is only a dot when both sums are even. Precompute all such (A, B) → C mappings once.

- **Q**: Why backtracking and not BFS for counting?
  **A**: BFS enumerates level by level — hard to track the visited set per path. DFS with backtracking naturally maintains a per-path visited set: add a dot, recurse, then remove it. Counting all paths of length k is inherently a DFS problem.

- **Q**: What is the time complexity of counting valid patterns?
  **A**: O(9!) in the worst case (9 starting dots, each exploring all permutations of remaining 8). In practice, skip constraints prune many branches. With symmetry optimization: 3 DFS calls × O(8!) each ≈ 3 × 40320.

- **Q**: Does 1→5→9 skip anything?
  **A**: No skip beyond 5, but going 1→9 would skip 5. If the pattern is [1, 5, 9], going 1→5 is fine (no skip), then 5→9 is fine (no dot at midpoint between 5 and 9 since (1+2)/2=1.5, not integer). But skipping 1→9 directly without having visited 5 would be invalid.
