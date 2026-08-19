> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design Android Unlock Pattern — a graph traversal problem masquerading as LLD. Tests DFS/Backtracking and constraint validation.
>
> **Key concepts:**
> - Core Entities: `PatternValidator`, `Grid` (3x3).
> - The Rules: You can connect any two dots, *unless* there is a dot directly between them. If there is a dot in between, you can only make the jump if the intermediate dot has *already been visited*.
> - The Jump Table: Precompute a `Map<Integer, Integer>` `jumps[start][end]` which stores the intermediate node. E.g., `jumps[1][3] = 2`.
> - Backtracking (DFS): To find all valid patterns of length $N$, use DFS. Keep a `visited` `Set<Integer>` (or boolean array). Before visiting `next`, check if `jumps[current][next]` is non-null. If it is, ensure `visited.contains(jumps[current][next])` is true.
>
> **Key takeaway:** This is a classic LeetCode algorithm problem (Number of Valid Words for Each Puzzle / Android Unlock Patterns) wrapped in an object-oriented shell. Memorize the "Jump Table" concept to handle the "intermediate dot" rule elegantly.

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
- skip: Map<Integer, Integer>   # (a, b) encoded key → c, or absent if no skip

+ UnlockPatternGrid()   # precompute skip map
+ getSkip(a: int, b: int) -> Integer   # 0-indexed dot numbers; null if no skip
```

### PatternValidator

```
class PatternValidator:
- grid: UnlockPatternGrid

+ isValid(pattern: List<Integer>) -> boolean
```

### PatternCounter

```
class PatternCounter:
- grid: UnlockPatternGrid

+ countPatterns(minLen: int, maxLen: int) -> int
- dfs(current: int, visited: Set<Integer>, remaining: int) -> int
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

```java
import java.util.*;

public class UnlockPatternGrid {
    // Dots 1-9, row/col as int[]{row, col}, 0-indexed
    private final Map<Integer, int[]> pos = new HashMap<>();
    // (a, b) encoded as a * 10 + b -> skip dot c
    private final Map<Integer, Integer> skip = new HashMap<>();

    public UnlockPatternGrid() {
        pos.put(1, new int[]{0, 0}); pos.put(2, new int[]{0, 1}); pos.put(3, new int[]{0, 2});
        pos.put(4, new int[]{1, 0}); pos.put(5, new int[]{1, 1}); pos.put(6, new int[]{1, 2});
        pos.put(7, new int[]{2, 0}); pos.put(8, new int[]{2, 1}); pos.put(9, new int[]{2, 2});
        precomputeSkips();
    }

    private void precomputeSkips() {
        for (int a : pos.keySet()) {
            for (int b : pos.keySet()) {
                if (a == b) continue;
                int[] posA = pos.get(a);
                int[] posB = pos.get(b);
                // midpoint is an integer only when a+b is even and they are collinear
                int midR = posA[0] + posB[0];
                int midC = posA[1] + posB[1];
                if (midR % 2 == 0 && midC % 2 == 0) {
                    int[] mid = new int[]{midR / 2, midC / 2};
                    // find dot at mid position
                    for (Map.Entry<Integer, int[]> entry : pos.entrySet()) {
                        int c = entry.getKey();
                        int[] p = entry.getValue();
                        if (p[0] == mid[0] && p[1] == mid[1] && c != a && c != b) {
                            skip.put(a * 10 + b, c);
                            break;
                        }
                    }
                }
            }
        }
    }

    public Integer getSkip(int a, int b) {
        return skip.get(a * 10 + b);
    }
}
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

```java
import java.util.*;

public class PatternCounter {
    private final UnlockPatternGrid grid;

    public PatternCounter(UnlockPatternGrid grid) {
        this.grid = grid;
    }

    public int countPatterns(int minLen, int maxLen) {
        int total = 0;
        for (int start = 1; start <= 9; start++) {
            Set<Integer> visited = new HashSet<>(Set.of(start));
            for (int length = minLen - 1; length < maxLen; length++) {
                total += dfs(start, visited, length);
            }
            // Note: dfs counts patterns where remaining more dots are needed
        }
        return total;
    }

    private int dfs(int current, Set<Integer> visited, int remaining) {
        if (remaining == 0) {
            return 1;
        }
        int count = 0;
        for (int nextDot = 1; nextDot <= 9; nextDot++) {
            if (visited.contains(nextDot)) {
                continue;
            }
            Integer skip = grid.getSkip(current, nextDot);
            if (skip != null && !visited.contains(skip)) {
                continue;   // skip dot not yet visited — invalid move
            }
            visited.add(nextDot);
            count += dfs(nextDot, visited, remaining - 1);
            visited.remove(nextDot);
        }
        return count;
    }

    public int countPatternsRange(int minLen, int maxLen) {
        int total = 0;
        for (int start = 1; start <= 9; start++) {
            for (int targetLen = minLen; targetLen <= maxLen; targetLen++) {
                Set<Integer> visited = new HashSet<>(Set.of(start));
                total += dfs(start, visited, targetLen - 1);
            }
        }
        return total;
    }
}
```

### Core Method: `PatternValidator.is_valid`

```java
import java.util.*;

public class PatternValidator {
    private final UnlockPatternGrid grid;

    public PatternValidator(UnlockPatternGrid grid) {
        this.grid = grid;
    }

    public boolean isValid(List<Integer> pattern) {
        if (pattern.size() < 4 || pattern.size() > 9) {
            return false;
        }
        if (new HashSet<>(pattern).size() != pattern.size()) {
            return false;  // duplicate dots
        }
        for (int d : pattern) {
            if (d < 1 || d > 9) {
                return false;
            }
        }

        Set<Integer> visited = new HashSet<>();
        for (int i = 0; i < pattern.size(); i++) {
            int dot = pattern.get(i);
            if (i > 0) {
                int prev = pattern.get(i - 1);
                Integer skip = grid.getSkip(prev, dot);
                if (skip != null && !visited.contains(skip)) {
                    return false;
                }
            }
            visited.add(dot);
        }

        return true;
    }
}
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

```java
public int countPatternsOptimized(int minLen, int maxLen) {
    int total = 0;
    for (int targetLen = minLen; targetLen <= maxLen; targetLen++) {
        // Corner: 4 symmetric corners (1, 3, 7, 9)
        Set<Integer> visited = new HashSet<>(Set.of(1));
        total += 4 * dfs(1, visited, targetLen - 1);
        // Edge midpoint: 4 symmetric midpoints (2, 4, 6, 8)
        visited = new HashSet<>(Set.of(2));
        total += 4 * dfs(2, visited, targetLen - 1);
        // Center: unique
        visited = new HashSet<>(Set.of(5));
        total += dfs(5, visited, targetLen - 1);
    }
    return total;
}
```

This reduces DFS calls from 9 starting points to 3.

### 2. "How do you count patterns of length 4 to 9?"

Call the DFS for each starting dot, for each target length in [4, 9]:

```java
public int countRange(int minLen, int maxLen) {
    int total = 0;
    for (int start = 1; start <= 9; start++) {
        for (int length = minLen; length <= maxLen; length++) {
            total += dfs(start, new HashSet<>(Set.of(start)), length - 1);
        }
    }
    return total;
}
```

### 3. "What if the grid is n × n instead of 3 × 3?"

Generalize: dots are numbered 1 to n², positions computed from `(i / n, i % n)`. Skip detection still uses midpoint arithmetic. DFS structure is unchanged — only the dot count and position map change.

### 4. "How would you store and verify a user's unlock pattern?"

Store the hashed pattern (SHA256 of the dot sequence as a string) — never the raw sequence:

```java
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public void storePattern(List<Integer> pattern) {
    this.patternHash = sha256(pattern.toString());
}

public boolean verifyPattern(List<Integer> inputPattern) {
    if (!new PatternValidator(grid).isValid(inputPattern)) {
        return false;
    }
    return sha256(inputPattern.toString()).equals(this.patternHash);
}

private String sha256(String input) {
    try {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes());
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    } catch (NoSuchAlgorithmException e) {
        throw new RuntimeException(e);
    }
}
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

---

## Related

**Patterns applied here**

- [Strategy Pattern](../../03-design-patterns/03-behavioral/strategy-pattern.md)

**SOLID focus**: [Single Responsibility](../../02-solid-principles/01-single-responsibility.md)

**Practice next**

- [Design Minesweeper](25-design-minesweeper.md)
- [Design Tic-Tac-Toe](../01-core-problems/03-design-tic-tac-toe.md)

Grid-path validation is the common mechanic.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../01-oop-fundamentals/uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
