# Design Text Editor (Sublime Text / VS Code)

> **Difficulty**: Hard  
> **Topics**: Data Structures (Gap Buffer/Rope), Command Pattern, Flyweight Pattern  
> **Features**: Insert, Delete, Undo/Redo, Syntax Highlighting

## Problem Statement

Design a text editor core.
- **Core Actions**: Insert text, Delete text, Move cursor.
- **Undo/Redo**: Infinite history.
- **Efficiency**: Must handle large files and fast typing.

## Core Data Structure (The "Secret Sauce")

A simple `String` is $O(N)$ for insertion. Real editors use:

1.  **Gap Buffer** (Emacs): Array with a "gap" at cursor. Insert is $O(1)$. Moving cursor is $O(N)$ (shift gap).
    *   `[H, E, L, L, O, _, _, _, W, O, R, L, D]`
2.  **Rope** (Heavyweights): Binary tree of strings. Good for huge files.
3.  **List of Lines** (Our Choice): Linked List or Array of Strings (one per line). Vertical move is $O(1)$.

## Design Patterns

1.  **Command Pattern**: Encapsulate actions (`InsertCommand`, `DeleteCommand`) to support Undo/Redo.
2.  **Flyweight Pattern**: For syntax highlighting. Don't create an object for every 'A' character. Reuse `Glyph('A', font)`.
3.  **Memento Pattern**: Store state snapshots (optional, Command is usually enough).

## Python Implementation (List of Lines + Command Pattern)

### 1. Model (TextBuffer)

```python
class Cursor:
    def __init__(self, row=0, col=0):
        self.row = row
        self.col = col

class TextBuffer:
    def __init__(self):
        self.lines = [""] # Start with empty line

    def insert_char(self, char, row, col):
        line = self.lines[row]
        self.lines[row] = line[:col] + char + line[col:]

    def delete_char(self, row, col):
        line = self.lines[row]
        char = line[col]
        self.lines[row] = line[:col] + line[col+1:]
        return char

    def split_line(self, row, col):
        line = self.lines[row]
        self.lines[row] = line[:col]
        self.lines.insert(row + 1, line[col:])

    def merge_lines(self, row):
        if row == 0: return
        prev = self.lines[row-1]
        curr = self.lines[row]
        new_col = len(prev)
        self.lines[row-1] = prev + curr
        self.lines.pop(row)
        return new_col
```

### 2. Command Pattern (Undo/Redo)

```python
from abc import ABC, abstractmethod

class Command(ABC):
    @abstractmethod
    def execute(self): pass
    @abstractmethod
    def undo(self): pass

class InsertCommand(Command):
    def __init__(self, buffer, cursor, char):
        self.buffer = buffer
        self.cursor = cursor
        self.char = char
        self.row = cursor.row
        self.col = cursor.col

    def execute(self):
        self.cursor.row = self.row
        self.cursor.col = self.col
        if self.char == '\n':
            self.buffer.split_line(self.row, self.col)
            self.cursor.row += 1
            self.cursor.col = 0
        else:
            self.buffer.insert_char(self.char, self.row, self.col)
            self.cursor.col += 1

    def undo(self):
        # Precise inverse of execute
        if self.char == '\n':
            self.buffer.merge_lines(self.row + 1)
            self.cursor.row = self.row
            self.cursor.col = self.col
        else:
            self.buffer.delete_char(self.row, self.col)
            self.cursor.row = self.row
            self.cursor.col = self.col

class CommandHistory:
    def __init__(self):
        self.undo_stack = []
        self.redo_stack = []

    def execute(self, cmd):
        cmd.execute()
        self.undo_stack.append(cmd)
        self.redo_stack.clear()

    def undo(self):
        if self.undo_stack:
            cmd = self.undo_stack.pop()
            cmd.undo()
            self.redo_stack.append(cmd)
```

## Interview Q&A

**Q: "How to search efficiently?"**
- A: "KMP Algorithm inside lines. For massive files, don't load all into RAM (use Memory Mapping / Mmap)."

**Q: "How to handle a 10GB file?"**
- A: "Virtual Proxy. Load only visible lines (Viewport) + buffer. Page in other parts as user scrolls."
