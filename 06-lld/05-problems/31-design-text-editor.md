---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, text-editor, gap-buffer, command-pattern, undo-redo]
---
# Design Text Editor

> **Difficulty**: Hard
> **Asked at**: Amazon, Microsoft, Dropbox
> **Key Patterns**: Command Pattern (undo/redo), Gap Buffer (efficient insert/delete at cursor)

---

## Understanding the Problem

Design the backend of a text editor supporting efficient insert, delete, cursor movement, undo/redo, and text search.

---

## Clarifying Questions

**You**: "What operations must the editor support?"
**Interviewer**: "Insert character, delete character (backspace), move cursor, undo, redo."

**You**: "What data structure for storing text?"
**Interviewer**: "Explain your choice — gap buffer, rope, or plain array."

**You**: "What's the maximum document size?"
**Interviewer**: "Assume it fits in memory — no paging required."

**You**: "Do we need multi-line support?"
**Interviewer**: "Yes — newlines are valid characters."

**You**: "Is undo history bounded?"
**Interviewer**: "Yes — max 100 undo levels."

---

## Final Requirements

**In scope:**
1. Insert character / string at cursor
2. Delete character (backspace at cursor)
3. Move cursor (left, right)
4. Undo / redo (up to 100 levels)
5. Get current content as string
6. Cursor position tracking

**Out of scope:**
- Syntax highlighting
- File I/O
- Multiple cursors
- Clipboard — mention as extension

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|---------------|
| `GapBuffer` | Core text storage: O(1) insert/delete near cursor |
| `Cursor` | Tracks absolute offset in the text |
| `Command` | Abstract: execute() + undo() for Command pattern |
| `InsertCommand` | Inserts text; stores position for undo |
| `DeleteCommand` | Deletes char; stores deleted char for undo |
| `Editor` | Orchestrates buffer, cursor, undo/redo stacks |

`Editor` holds one `GapBuffer`, one `Cursor`, and two stacks (undo / redo). Every operation creates a `Command`, executes it, pushes to undo stack, clears redo stack.

---

## Class Design

### GapBuffer

| Requirement | What GapBuffer must track |
|-------------|--------------------------|
| "O(1) insert/delete at cursor" | char array with gap at cursor position |
| "Gap boundaries" | gap_start, gap_end (exclusive) |

```
class GapBuffer:
- buffer: list[str]    # char array with embedded gap
- gap_start: int
- gap_end: int
- GAP_SIZE: int = 128

+ insert(char: str)
+ delete() -> Optional[str]    # removes char before cursor
+ move_cursor(new_pos: int)    # moves gap to absolute position
+ to_string() -> str
+ content_length() -> int      # excludes gap
```

### Command

```
class Command (abstract):
+ execute(buffer: GapBuffer, cursor: Cursor)
+ undo(buffer: GapBuffer, cursor: Cursor)

class InsertCommand(Command):
- text: str
- position: int

class DeleteCommand(Command):
- deleted_char: str      # set during execute
- position: int          # cursor position before delete
```

### Editor

```
class Editor:
- buffer: GapBuffer
- cursor: Cursor
- undo_stack: deque      # maxlen=100
- redo_stack: deque

+ insert(text: str)
+ delete()
+ move_left()
+ move_right()
+ undo()
+ redo()
+ get_content() -> str
```

---

## Implementation

### Core Data Structure: GapBuffer

A gap buffer is a char array with a "gap" (free space) at the cursor position. Insertions at the cursor are O(1) — write into the gap. Moving the cursor shifts the gap (O(k) where k = distance). Growing the gap requires reallocation.

```
Initial: buffer = [_, _, _, _, h, e, l, l, o], gap=[0,4)
Insert 'w': buffer = [w, _, _, _, h, e, l, l, o], gap=[1,4)
Insert 'o': buffer = [w, o, _, _, h, e, l, l, o], gap=[2,4)

Move cursor left by 1 (pos 2 → 1):
  Shift char left of gap (o) to right side of gap
  buffer = [w, _, _, o, h, e, l, l, o], gap=[1,4)
```

```python
class GapBuffer:
    GAP_SIZE = 128

    def __init__(self):
        self.buffer = [''] * self.GAP_SIZE
        self.gap_start = 0
        self.gap_end = self.GAP_SIZE

    def insert(self, char):
        if self.gap_start == self.gap_end:
            self._grow_gap()
        self.buffer[self.gap_start] = char
        self.gap_start += 1

    def delete(self):
        if self.gap_start == 0:
            return None
        self.gap_start -= 1
        deleted = self.buffer[self.gap_start]
        self.buffer[self.gap_start] = ''
        return deleted

    def move_cursor(self, new_pos):
        current_pos = self.gap_start
        if new_pos < current_pos:
            # Move gap left: shift chars from left of gap to right
            steps = current_pos - new_pos
            for _ in range(steps):
                self.gap_end -= 1
                self.gap_start -= 1
                self.buffer[self.gap_end] = self.buffer[self.gap_start]
                self.buffer[self.gap_start] = ''
        elif new_pos > current_pos:
            # Move gap right: shift chars from right of gap to left
            steps = new_pos - current_pos
            for _ in range(steps):
                self.buffer[self.gap_start] = self.buffer[self.gap_end]
                self.buffer[self.gap_end] = ''
                self.gap_start += 1
                self.gap_end += 1

    def to_string(self):
        return ''.join(
            c for i, c in enumerate(self.buffer)
            if not (self.gap_start <= i < self.gap_end)
        )

    def content_length(self):
        return len(self.buffer) - (self.gap_end - self.gap_start)

    def _grow_gap(self):
        content = self.to_string()
        pos = self.gap_start
        new_buf = (
            list(content[:pos]) +
            [''] * self.GAP_SIZE +
            list(content[pos:])
        )
        self.buffer = new_buf
        self.gap_end = pos + self.GAP_SIZE
```

### Command Pattern: Insert / Delete

```python
class InsertCommand:
    def __init__(self, text, position):
        self.text = text
        self.position = position

    def execute(self, buffer, cursor):
        buffer.move_cursor(self.position)
        for char in self.text:
            buffer.insert(char)
        cursor.position = self.position + len(self.text)

    def undo(self, buffer, cursor):
        buffer.move_cursor(self.position + len(self.text))
        for _ in self.text:
            buffer.delete()
        cursor.position = self.position

class DeleteCommand:
    def __init__(self, position):
        self.position = position
        self.deleted_char = None

    def execute(self, buffer, cursor):
        buffer.move_cursor(self.position)
        self.deleted_char = buffer.delete()
        cursor.position = self.position - 1

    def undo(self, buffer, cursor):
        buffer.move_cursor(self.position - 1)
        buffer.insert(self.deleted_char)
        cursor.position = self.position
```

### Editor: undo/redo orchestration

```python
class Editor:
    MAX_UNDO = 100

    def __init__(self):
        self.buffer = GapBuffer()
        self.cursor = Cursor(position=0)
        self.undo_stack = deque(maxlen=self.MAX_UNDO)
        self.redo_stack = deque()

    def insert(self, text):
        cmd = InsertCommand(text, self.cursor.position)
        cmd.execute(self.buffer, self.cursor)
        self.undo_stack.append(cmd)
        self.redo_stack.clear()

    def delete(self):
        if self.cursor.position == 0:
            return
        cmd = DeleteCommand(self.cursor.position)
        cmd.execute(self.buffer, self.cursor)
        self.undo_stack.append(cmd)
        self.redo_stack.clear()

    def move_left(self):
        if self.cursor.position > 0:
            self.cursor.position -= 1
            self.buffer.move_cursor(self.cursor.position)

    def move_right(self):
        if self.cursor.position < self.buffer.content_length():
            self.cursor.position += 1
            self.buffer.move_cursor(self.cursor.position)

    def undo(self):
        if not self.undo_stack:
            return
        cmd = self.undo_stack.pop()
        cmd.undo(self.buffer, self.cursor)
        self.redo_stack.append(cmd)

    def redo(self):
        if not self.redo_stack:
            return
        cmd = self.redo_stack.pop()
        cmd.execute(self.buffer, self.cursor)
        self.undo_stack.append(cmd)

    def get_content(self):
        return self.buffer.to_string()
```

---

## Verification

```
editor = Editor()   # content="", cursor=0

editor.insert("hello")
  InsertCommand(text="hello", position=0).execute()
  buffer: [h,e,l,l,o], cursor=5
  undo_stack: [Insert("hello", 0)]

editor.delete()
  cursor=5, DeleteCommand(position=5).execute()
  deleted_char='o', cursor=4
  undo_stack: [Insert("hello",0), Delete(5)]

editor.undo()
  Delete(5).undo() → insert 'o' at position 4
  buffer: [h,e,l,l,o], cursor=5
  undo_stack: [Insert("hello",0)]
  redo_stack: [Delete(5)]

editor.undo()
  Insert("hello",0).undo() → delete 5 chars
  buffer: [], cursor=0
  undo_stack: []
  redo_stack: [Delete(5), Insert("hello",0)]

editor.redo()
  Insert("hello",0).execute() → insert "hello"
  buffer: [h,e,l,l,o], cursor=5
```

---

## Deep Dive & Extensibility

### 1. "Gap Buffer vs Rope — when does each win?"

**Gap Buffer**: O(1) insert/delete at the cursor gap. O(k) to move cursor k positions (shifts the gap). Ideal for typical editing — cursor usually stays near recent edits (spatial locality).

**Rope**: Balanced binary tree of string chunks. O(log n) for all operations regardless of cursor position. Better for very large files or random-access edits. More complex to implement.

For an interview, Gap Buffer is the right choice: simpler, optimal for typical use.

### 2. "How would you add find/replace?"

```python
def find(self, query):
    content = self.buffer.to_string()
    positions = []
    idx = 0
    while True:
        idx = content.find(query, idx)
        if idx == -1:
            break
        positions.append(idx)
        idx += 1
    return positions

def replace_all(self, query, replacement):
    positions = self.find(query)
    # Replace right-to-left to preserve earlier positions
    for pos in reversed(positions):
        for _ in range(len(query)):
            self.buffer.move_cursor(pos + len(query))
            self.buffer.delete()
        self.buffer.move_cursor(pos)
        for char in replacement:
            self.buffer.insert(char)
```

### 3. "How would you add clipboard (copy/paste)?"

```python
class Clipboard:
    content: str = ""

class CopyCommand(Command):
    def __init__(self, start, end):
        self.start = start
        self.end = end

    def execute(self, buffer, cursor, clipboard):
        content = buffer.to_string()
        clipboard.content = content[self.start:self.end]

    def undo(self, buffer, cursor, clipboard):
        pass   # copy is non-destructive — nothing to undo
```

Paste = `InsertCommand(clipboard.content, cursor.position)`.

### 4. "How would you track line/column position efficiently?"

Maintain a `LineIndex` — a sorted list of newline positions. `(line, col)` ↔ absolute offset via binary search:

```python
class LineIndex:
    def __init__(self):
        self.newlines = []   # sorted absolute positions of '\n'

    def on_insert(self, pos, char):
        if char == '\n':
            bisect.insort(self.newlines, pos)
        self.newlines = [n + 1 if n >= pos else n for n in self.newlines]

    def line_col(self, offset):
        line = bisect.bisect_left(self.newlines, offset)
        prev_newline = self.newlines[line - 1] if line > 0 else -1
        col = offset - prev_newline - 1
        return line, col
```

### 5. "What's the memory cost of storing 100 undo commands?"

Each InsertCommand stores the inserted text (typically 1-N chars) and position (int). DeleteCommand stores one char and position. For typical edits (single keystrokes), undo history is O(100 × constant) ≈ negligible. Batch-insert large pastes as one command to avoid N commands for N chars.

---

## Interviewer Questions by Level

**Junior**: Array-based text buffer. Insert/delete characters. Move cursor. Get content as string.

**Mid-level**: Gap Buffer for O(1) insert/delete at cursor. Command pattern for undo/redo. Bounded undo stack via deque maxlen. Redo clears on new edit.

**Senior**: Gap Buffer growth strategy. Rope vs Gap Buffer trade-off. Line index for O(log n) line/col lookup. Clipboard extension. Replace right-to-left to preserve earlier positions.

---

## Common Interview Questions

- **Q**: Why is a gap buffer better than a plain array for text editing?
  **A**: Plain array insert at position i requires shifting O(n) elements right. Gap buffer keeps free space at the cursor — insert fills the gap in O(1). The shifting cost is paid only when the cursor moves, which matches the typical access pattern of real editing.

- **Q**: How does the Command pattern enable undo/redo?
  **A**: Every mutating operation creates a Command with enough state to reverse itself. `execute()` applies the change; `undo()` reverses it using stored state (deleted char, insertion position). Commands are pushed to a bounded undo stack; undo pops and reverses.

- **Q**: What happens to the redo stack when the user makes a new edit?
  **A**: It's cleared. Redo only applies to commands undone from the current branch. A new edit starts a new branch — the undone commands are no longer reachable.

- **Q**: What is the time complexity of `to_string()` on a gap buffer?
  **A**: O(n) — must iterate all non-gap positions and join them. This is acceptable because `to_string()` is called infrequently (save/display), while insert/delete (O(1)) are called constantly during editing.

- **Q**: How would you limit memory usage for undo history?
  **A**: `deque(maxlen=100)` — when full, appending a new command evicts the oldest one automatically in O(1). No manual management needed.
