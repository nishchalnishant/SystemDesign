> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a Text Editor — a specialized LLD problem focusing on the Command pattern (Undo/Redo) and the Gap Buffer data structure.
>
> **Key concepts:**
> - Core Entities: `Editor`, `Document` (Gap Buffer), `CommandManager`, `Command` (Insert/Delete).
> - The Gap Buffer: Storing text as a single `String` or `ArrayList` is too slow for insertions ($O(N)$). A Gap Buffer allocates a large empty "gap" at the cursor position. Insertions into the gap are $O(1)$. Moving the cursor shifts the gap.
> - Undo/Redo (Command Pattern): Every action (type 'a', hit backspace) is encapsulated in a `Command` object with `execute()` and `undo()` methods.
> - Two Stacks: Maintain an `undoStack` and a `redoStack` (`java.util.Deque<Command>`, used via `push`/`pop`). When you type, push to `undoStack` and clear `redoStack`. When you hit Ctrl+Z, pop from `undoStack`, call `undo()`, and push to `redoStack`.
>
> **Key takeaway:** The Command pattern with two stacks is the standard, expected answer for any Undo/Redo mechanism. Mentioning the Gap Buffer (or a Rope data structure) for the underlying text storage shows deep domain knowledge.

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

```java
public class GapBuffer {
    private static final int GAP_SIZE = 128;

    private char[] buffer;
    private int gapStart;
    private int gapEnd;

    public GapBuffer() {
        this.buffer = new char[GAP_SIZE];
        this.gapStart = 0;
        this.gapEnd = GAP_SIZE;
    }

    public void insert(char c) {
        if (gapStart == gapEnd) {
            growGap();
        }
        buffer[gapStart] = c;
        gapStart++;
    }

    // Removes char before cursor; returns null if nothing to delete
    public Character delete() {
        if (gapStart == 0) {
            return null;
        }
        gapStart--;
        char deleted = buffer[gapStart];
        buffer[gapStart] = '\0';
        return deleted;
    }

    // Moves gap to absolute position
    public void moveCursor(int newPos) {
        int currentPos = gapStart;
        if (newPos < currentPos) {
            // Move gap left: shift chars from left of gap to right
            int steps = currentPos - newPos;
            for (int i = 0; i < steps; i++) {
                gapEnd--;
                gapStart--;
                buffer[gapEnd] = buffer[gapStart];
                buffer[gapStart] = '\0';
            }
        } else if (newPos > currentPos) {
            // Move gap right: shift chars from right of gap to left
            int steps = newPos - currentPos;
            for (int i = 0; i < steps; i++) {
                buffer[gapStart] = buffer[gapEnd];
                buffer[gapEnd] = '\0';
                gapStart++;
                gapEnd++;
            }
        }
    }

    public String toText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < buffer.length; i++) {
            if (i < gapStart || i >= gapEnd) {
                sb.append(buffer[i]);
            }
        }
        return sb.toString();
    }

    // Excludes gap
    public int contentLength() {
        return buffer.length - (gapEnd - gapStart);
    }

    private void growGap() {
        String content = toText();
        int pos = gapStart;
        char[] newBuf = new char[content.length() + GAP_SIZE];
        content.getChars(0, pos, newBuf, 0);
        content.getChars(pos, content.length(), newBuf, pos + GAP_SIZE);
        this.buffer = newBuf;
        this.gapEnd = pos + GAP_SIZE;
    }
}
```

### Command Pattern: Insert / Delete

```java
public interface Command {
    void execute(GapBuffer buffer, Cursor cursor);
    void undo(GapBuffer buffer, Cursor cursor);
}

public class InsertCommand implements Command {
    private final String text;
    private final int position;

    public InsertCommand(String text, int position) {
        this.text = text;
        this.position = position;
    }

    @Override
    public void execute(GapBuffer buffer, Cursor cursor) {
        buffer.moveCursor(position);
        for (char c : text.toCharArray()) {
            buffer.insert(c);
        }
        cursor.setPosition(position + text.length());
    }

    @Override
    public void undo(GapBuffer buffer, Cursor cursor) {
        buffer.moveCursor(position + text.length());
        for (int i = 0; i < text.length(); i++) {
            buffer.delete();
        }
        cursor.setPosition(position);
    }
}

public class DeleteCommand implements Command {
    private final int position;
    private Character deletedChar;   // set during execute

    public DeleteCommand(int position) {
        this.position = position;
    }

    @Override
    public void execute(GapBuffer buffer, Cursor cursor) {
        buffer.moveCursor(position);
        this.deletedChar = buffer.delete();
        cursor.setPosition(position - 1);
    }

    @Override
    public void undo(GapBuffer buffer, Cursor cursor) {
        buffer.moveCursor(position - 1);
        buffer.insert(deletedChar);
        cursor.setPosition(position);
    }
}
```

### Editor: undo/redo orchestration

```java
public class Editor {
    private static final int MAX_UNDO = 100;

    private final GapBuffer buffer;
    private final Cursor cursor;
    private final Deque<Command> undoStack;   // bounded to MAX_UNDO
    private final Deque<Command> redoStack;

    public Editor() {
        this.buffer = new GapBuffer();
        this.cursor = new Cursor(0);
        this.undoStack = new ArrayDeque<>();
        this.redoStack = new ArrayDeque<>();
    }

    public void insert(String text) {
        Command cmd = new InsertCommand(text, cursor.getPosition());
        cmd.execute(buffer, cursor);
        pushUndo(cmd);
        redoStack.clear();
    }

    public void delete() {
        if (cursor.getPosition() == 0) {
            return;
        }
        Command cmd = new DeleteCommand(cursor.getPosition());
        cmd.execute(buffer, cursor);
        pushUndo(cmd);
        redoStack.clear();
    }

    public void moveLeft() {
        if (cursor.getPosition() > 0) {
            cursor.setPosition(cursor.getPosition() - 1);
            buffer.moveCursor(cursor.getPosition());
        }
    }

    public void moveRight() {
        if (cursor.getPosition() < buffer.contentLength()) {
            cursor.setPosition(cursor.getPosition() + 1);
            buffer.moveCursor(cursor.getPosition());
        }
    }

    public void undo() {
        if (undoStack.isEmpty()) {
            return;
        }
        Command cmd = undoStack.pop();
        cmd.undo(buffer, cursor);
        redoStack.push(cmd);
    }

    public void redo() {
        if (redoStack.isEmpty()) {
            return;
        }
        Command cmd = redoStack.pop();
        cmd.execute(buffer, cursor);
        pushUndo(cmd);
    }

    public String getContent() {
        return buffer.toText();
    }

    // Deque has no built-in maxlen like Python's deque; evict oldest manually
    private void pushUndo(Command cmd) {
        if (undoStack.size() == MAX_UNDO) {
            undoStack.removeLast();
        }
        undoStack.push(cmd);
    }
}
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

```java
public List<Integer> find(String query) {
    String content = buffer.toText();
    List<Integer> positions = new ArrayList<>();
    int idx = 0;
    while (true) {
        idx = content.indexOf(query, idx);
        if (idx == -1) {
            break;
        }
        positions.add(idx);
        idx += 1;
    }
    return positions;
}

public void replaceAll(String query, String replacement) {
    List<Integer> positions = find(query);
    // Replace right-to-left to preserve earlier positions
    for (int i = positions.size() - 1; i >= 0; i--) {
        int pos = positions.get(i);
        for (int j = 0; j < query.length(); j++) {
            buffer.moveCursor(pos + query.length());
            buffer.delete();
        }
        buffer.moveCursor(pos);
        for (char c : replacement.toCharArray()) {
            buffer.insert(c);
        }
    }
}
```

### 3. "How would you add clipboard (copy/paste)?"

```java
public class Clipboard {
    private String content = "";

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}

public class CopyCommand {
    private final int start;
    private final int end;

    public CopyCommand(int start, int end) {
        this.start = start;
        this.end = end;
    }

    public void execute(GapBuffer buffer, Cursor cursor, Clipboard clipboard) {
        String content = buffer.toText();
        clipboard.setContent(content.substring(start, end));
    }

    public void undo(GapBuffer buffer, Cursor cursor, Clipboard clipboard) {
        // copy is non-destructive — nothing to undo
    }
}
```

Paste = `InsertCommand(clipboard.content, cursor.position)`.

### 4. "How would you track line/column position efficiently?"

Maintain a `LineIndex` — a sorted list of newline positions. `(line, col)` ↔ absolute offset via binary search:

```java
public class LineIndex {
    private final List<Integer> newlines = new ArrayList<>();   // sorted absolute positions of '\n'

    public void onInsert(int pos, char c) {
        // Shift existing newline positions at/after pos before inserting the new one
        for (int i = 0; i < newlines.size(); i++) {
            if (newlines.get(i) >= pos) {
                newlines.set(i, newlines.get(i) + 1);
            }
        }
        if (c == '\n') {
            int insertAt = Collections.binarySearch(newlines, pos);
            if (insertAt < 0) {
                insertAt = -(insertAt + 1);
            }
            newlines.add(insertAt, pos);
        }
    }

    public int[] lineCol(int offset) {
        int line = lowerBound(newlines, offset);
        int prevNewline = line > 0 ? newlines.get(line - 1) : -1;
        int col = offset - prevNewline - 1;
        return new int[] { line, col };
    }

    // Equivalent of bisect.bisect_left
    private int lowerBound(List<Integer> sorted, int target) {
        int lo = 0, hi = sorted.size();
        while (lo < hi) {
            int mid = (lo + hi) / 2;
            if (sorted.get(mid) < target) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }
}
```

### 5. "What's the memory cost of storing 100 undo commands?"

Each InsertCommand stores the inserted text (typically 1-N chars) and position (int). DeleteCommand stores one char and position. For typical edits (single keystrokes), undo history is O(100 × constant) ≈ negligible. Batch-insert large pastes as one command to avoid N commands for N chars.

---

## Interviewer Questions by Level

**Junior**: Array-based text buffer. Insert/delete characters. Move cursor. Get content as string.

**Mid-level**: Gap Buffer for O(1) insert/delete at cursor. Command pattern for undo/redo. Bounded undo stack via a size-capped `Deque`. Redo clears on new edit.

**Senior**: Gap Buffer growth strategy. Rope vs Gap Buffer trade-off. Line index for O(log n) line/col lookup. Clipboard extension. Replace right-to-left to preserve earlier positions.

---

## Common Interview Questions

- **Q**: Why is a gap buffer better than a plain array for text editing?
  **A**: Plain array insert at position i requires shifting O(n) elements right. Gap buffer keeps free space at the cursor — insert fills the gap in O(1). The shifting cost is paid only when the cursor moves, which matches the typical access pattern of real editing.

- **Q**: How does the Command pattern enable undo/redo?
  **A**: Every mutating operation creates a Command with enough state to reverse itself. `execute()` applies the change; `undo()` reverses it using stored state (deleted char, insertion position). Commands are pushed to a bounded undo stack; undo pops and reverses.

- **Q**: What happens to the redo stack when the user makes a new edit?
  **A**: It's cleared. Redo only applies to commands undone from the current branch. A new edit starts a new branch — the undone commands are no longer reachable.

- **Q**: What is the time complexity of `toText()` on a gap buffer?
  **A**: O(n) — must iterate all non-gap positions and join them. This is acceptable because `toText()` is called infrequently (save/display), while insert/delete (O(1)) are called constantly during editing.

- **Q**: How would you limit memory usage for undo history?
  **A**: A `Deque<Command>` with a manual size check on push — when the stack reaches `MAX_UNDO`, evict the oldest entry (`removeLast()`) in O(1) before pushing the new one. Java's `Deque` has no built-in bounded/`maxlen` variant, so the cap is enforced by hand, as shown in `Editor.pushUndo`.

---

## Related

**Patterns applied here**

- [Command Pattern](../../03-design-patterns/03-behavioral/command-pattern.md)
- [Iterator Pattern](../../03-design-patterns/03-behavioral/iterator-pattern.md)
- [Memento Pattern](../../03-design-patterns/03-behavioral/memento-pattern.md) — checkpoint document state for undo / redo stack

**SOLID focus**: [Single Responsibility](../../02-solid-principles/01-single-responsibility.md) · [Open/Closed](../../02-solid-principles/02-open-closed.md)

**Practice next**

- [Design Version Control](29-design-version-control.md)
- [Design Search Engine](27-design-search-engine.md)

Undo stacks and buffer traversal are shared concerns.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
