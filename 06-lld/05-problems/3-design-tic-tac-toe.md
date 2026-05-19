# Design Tic-Tac-Toe

> **Difficulty**: Beginner
> **Topics**: Object-Oriented Design, Board Game Logic, State Pattern, Command Pattern
> **Extension**: N×N Board, AI Player (Minimax), Undo move

---

## What Breaks Without This Design?

```java
class TicTacToe {
    private char[][] board = new char[3][3]; // '\0' = empty
    private char currentPlayer = 'X';
    private boolean gameOver = false;

    public void makeMove(int row, int col) {
        if (gameOver || board[row][col] != '\0') return;

        board[row][col] = currentPlayer;

        // Check win: scan entire board
        for (int r = 0; r < 3; r++) {
            if (board[r][0] == currentPlayer && board[r][1] == currentPlayer
                && board[r][2] == currentPlayer) { gameOver = true; return; }
        }
        for (int c = 0; c < 3; c++) {
            if (board[0][c] == currentPlayer && board[1][c] == currentPlayer
                && board[2][c] == currentPlayer) { gameOver = true; return; }
        }
        if (board[0][0] == currentPlayer && board[1][1] == currentPlayer
            && board[2][2] == currentPlayer) { gameOver = true; return; }

        currentPlayer = (currentPlayer == 'X') ? 'O' : 'X';
    }
}
```

**Concrete failures**:
1. **Hardcoded 3×3**: Changing to N×N requires rewriting every loop bound and the diagonal checks.
2. **O(N²) win check**: Scanning the entire board on every move is unnecessary — only the row, column, and at most 2 diagonals affected by the last move need checking (O(N)).
3. **No undo**: `board[row][col]` is modified in place with no history. Undo requires saving the board state before every move — but there is nowhere to store it.
4. **AI player impossible to swap in**: The game loop is inside the same class. Swapping in an `AIPlayer` for `currentPlayer == 'O'` requires modifying the game controller logic — there is no `Player` abstraction.
5. **`char` encodes piece type**: Using `'X'` and `'O'` as chars couples the display representation to the game logic. A `Player` object with a `piece` field separates these.

---

## Derive the Class Structure

**Force 1 — N×N board**: The board size is a constructor parameter. Win check scans only the affected row (N checks), affected column (N checks), and up to 2 diagonals (N checks each) = O(N). The board is a `char[][]` (or `Player[][]`) with N as the dimension.

**Force 2 — Undo requires history**: Each move must be reversible. Wrap each move in a `MoveCommand(player, row, col)` with `execute()` (place piece) and `undo()` (clear cell). A `Deque<MoveCommand>` stack in the controller stores history. `undo()` pops and reverses.

**Force 3 — Human and AI players need the same interface**: The game controller calls `player.makeMove(board)` without knowing if it is human or AI. Extract `Player` interface. `HumanPlayer` reads from input; `AIPlayer` runs Minimax. The controller's turn loop is unchanged.

**Force 4 — Win detection belongs on the board, not the controller**: The controller should ask `board.checkWinner(lastRow, lastCol)` — not implement the win-scanning logic itself. Extract `Board` with `place(row, col, player)`, `checkWinner(row, col)`, `isFull()`.

**Result** — the class split these forces produce:
```
God class → GameController (turn loop, delegates to Player + Board, holds undo stack)
          → Board (N×N grid, place/checkWinner/isFull — O(N) win check)
          → Player (interface: Piece getPiece(), int[] chooseMove(Board))
             → HumanPlayer, AIPlayer (Minimax)
          → MoveCommand (player, row, col — execute + undo)
          → Piece (enum: X, O)
```

---

## Opening Analogy

Think of a chess tournament referee. The referee needs to: track whose turn it is (turn management), declare a winner when a row/column/diagonal is complete (board evaluator), and support undoing an illegal move that was accidentally placed (Command pattern with undo). Now shrink the board to 3×3 and you have Tic-Tac-Toe. The structural problem is identical — managing state transitions, validating actions, and detecting terminal conditions.

---

## Phase 1: Requirements

### Functional
- Two players take turns placing their piece (X or O) on a 3×3 grid.
- Validate that the chosen cell is empty and within bounds.
- After each move, detect win (row/column/diagonal match) or draw (board full, no winner).
- Support undo of the last move.
- Board size is configurable (N×N).

### Non-Functional
- Move validation must be O(N) — scan only the row/column touched by the move, not the entire board.
- Undo must be O(1).
- Design must support swapping in an AI player without touching game loop logic.

---

## Phase 2: Use Cases

### Actors
- **Human Player** — inputs a cell coordinate.
- **AI Player** — computes the best cell via strategy algorithm.
- **Game Controller** — enforces turn order, delegates move, checks terminal state.

### UC1: Make Move
1. Active player selects `(row, col)`.
2. Controller validates cell is empty and within bounds.
3. Controller places the piece on the board.
4. Controller runs winner check against the piece just placed (only the affected row, column, and up to two diagonals).
5. If win → announce winner, game over. If draw → announce tie. Otherwise → switch turn.

### UC2: Undo Move
1. Active player requests undo.
2. Controller pops the last `MoveCommand` from the undo stack.
3. Controller sets the cell back to null and restores the previous player's turn.

### UC3: AI Takes Turn
1. Controller calls `player.chooseMove(board)`.
2. `AIPlayer` internally uses a `MoveStrategy` (Random or Minimax) to return `(row, col)`.
3. Same validation and win-check flow as UC1.

---

## Phase 3: Class Diagram

```
┌─────────────────────────────┐
│       TicTacToeGame         │
│─────────────────────────────│
│ - board: Board              │
│ - players: Deque<Player>    │
│ - moveHistory: Deque<Move>  │
│─────────────────────────────│
│ + startGame(): String       │
│ + undoLastMove(): void      │
│ - checkWinner(r,c,t): bool  │
└──────────────┬──────────────┘
               │ uses
     ┌─────────┴──────────┐
     ▼                    ▼
┌─────────┐        ┌────────────┐
│  Board  │        │   Player   │
│─────────│        │────────────│
│ size: N │        │ name       │
│ grid[][]│        │ piece      │
│─────────│        │────────────│
│addPiece │        │chooseMove  │◄── interface
│getCell  │        └─────┬──────┘
│freeCells│              │
└─────────┘    ┌─────────┴────────┐
               ▼                  ▼
         HumanPlayer          AIPlayer
                               - strategy: MoveStrategy
                               
┌──────────────┐   ┌───────────────────┐
│ PlayingPiece │   │  MoveStrategy     │ <<interface>>
│──────────────│   │───────────────────│
│ type: Piece  │   │ chooseMove(Board) │
│   Type enum  │   └──────┬────────────┘
└──────────────┘          │
                 ┌─────────┴──────────┐
                 ▼                    ▼
          RandomStrategy        MinimaxStrategy

┌─────────────────┐
│   MoveCommand   │   <<Command>>
│─────────────────│
│ row, col, piece │
│─────────────────│
│ execute()       │
│ undo()          │
└─────────────────┘
```

**Key relationships:**
- `TicTacToeGame` owns `Board` and a `Deque<Player>` (rotated to manage turns).
- `Player` is an interface — `HumanPlayer` reads stdin; `AIPlayer` delegates to a `MoveStrategy`.
- Each move is wrapped in a `MoveCommand` and pushed to `moveHistory` for undo support.
- `PieceType` is an enum (`X`, `O`); `PlayingPiece` wraps it for polymorphism.

---

## Phase 4: Design Patterns Applied

### 1. Command Pattern — Undo support
**Why:** Every move is a reversible action. Wrapping `(row, col, piece)` as a `MoveCommand` with `execute()` and `undo()` lets us maintain an undo stack at zero extra coupling. The game loop just pushes/pops commands; it never needs to know how undo is implemented.

### 2. Strategy Pattern — Pluggable AI
**Why:** `AIPlayer` should be able to swap between `RandomStrategy` (trivial, for testing) and `MinimaxStrategy` (optimal, for production) without changing the game loop. Open/Closed principle: the game loop calls `player.chooseMove(board)` regardless of implementation.

### 3. State Pattern (implicit) — Turn management
**Why:** The game has three states: `ONGOING`, `WON`, `DRAW`. Encoding these as an enum and checking before each move is clean state-machine thinking. For a more complex game, each state would be a full class that handles its own transitions.

---

## Phase 5: Key Java Implementation

```java
import java.util.*;

// ── Enums ──────────────────────────────────────────────────────────────────

enum PieceType { X, O }

enum GameState { ONGOING, WON, DRAW }

// ── Piece & Players ────────────────────────────────────────────────────────

class PlayingPiece {
    final PieceType type;
    PlayingPiece(PieceType type) { this.type = type; }
}

interface Player {
    String getName();
    PlayingPiece getPiece();
    int[] chooseMove(Board board); // returns [row, col]
}

class HumanPlayer implements Player {
    private final String name;
    private final PlayingPiece piece;

    HumanPlayer(String name, PieceType type) {
        this.name = name;
        this.piece = new PlayingPiece(type);
    }

    public String getName() { return name; }
    public PlayingPiece getPiece() { return piece; }

    public int[] chooseMove(Board board) {
        Scanner sc = new Scanner(System.in);
        System.out.print(name + ", enter row,col: ");
        String[] parts = sc.nextLine().split(",");
        return new int[]{ Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) };
    }
}

// ── Command Pattern: Move ──────────────────────────────────────────────────

class MoveCommand {
    final int row, col;
    final PlayingPiece piece;

    MoveCommand(int row, int col, PlayingPiece piece) {
        this.row = row;
        this.col = col;
        this.piece = piece;
    }

    void execute(Board board) { board.setCell(row, col, piece); }
    void undo(Board board)    { board.setCell(row, col, null);  }
}

// ── Board ──────────────────────────────────────────────────────────────────

class Board {
    final int size;
    private final PlayingPiece[][] grid;

    Board(int size) {
        this.size = size;
        this.grid = new PlayingPiece[size][size];
    }

    boolean addPiece(int row, int col, PlayingPiece piece) {
        if (row < 0 || row >= size || col < 0 || col >= size) return false;
        if (grid[row][col] != null) return false;
        grid[row][col] = piece;
        return true;
    }

    void setCell(int row, int col, PlayingPiece piece) {
        grid[row][col] = piece;
    }

    PlayingPiece getCell(int row, int col) { return grid[row][col]; }

    List<int[]> getFreeCells() {
        List<int[]> free = new ArrayList<>();
        for (int i = 0; i < size; i++)
            for (int j = 0; j < size; j++)
                if (grid[i][j] == null) free.add(new int[]{i, j});
        return free;
    }

    void print() {
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                System.out.print(grid[i][j] == null ? " ." : " " + grid[i][j].type);
            }
            System.out.println();
        }
    }
}

// ── Game Controller ────────────────────────────────────────────────────────

public class TicTacToeGame {
    private final Board board;
    private final Deque<Player> players;
    private final Deque<MoveCommand> moveHistory = new ArrayDeque<>();
    private GameState state = GameState.ONGOING;

    public TicTacToeGame(int size, List<Player> playerList) {
        this.board = new Board(size);
        this.players = new ArrayDeque<>(playerList);
    }

    public String startGame() {
        while (state == GameState.ONGOING) {
            board.print();
            Player current = players.peekFirst();

            int[] move = current.chooseMove(board);
            int row = move[0], col = move[1];

            boolean placed = board.addPiece(row, col, current.getPiece());
            if (!placed) {
                System.out.println("Invalid move — try again.");
                continue;
            }

            // Record for undo
            MoveCommand cmd = new MoveCommand(row, col, current.getPiece());
            moveHistory.push(cmd);

            if (isWinner(row, col, current.getPiece().type)) {
                state = GameState.WON;
                return current.getName() + " wins!";
            }
            if (board.getFreeCells().isEmpty()) {
                state = GameState.DRAW;
                return "It's a draw.";
            }

            // Rotate players
            players.addLast(players.removeFirst());
        }
        return "Game over.";
    }

    // O(N) — only checks the row/column/diagonals touched by this move
    boolean isWinner(int row, int col, PieceType type) {
        int n = board.size;
        boolean rowWin = true, colWin = true, diagWin = true, antiWin = true;

        for (int i = 0; i < n; i++) {
            if (board.getCell(row, i) == null || board.getCell(row, i).type != type) rowWin = false;
            if (board.getCell(i, col) == null || board.getCell(i, col).type != type) colWin = false;
            if (board.getCell(i, i) == null || board.getCell(i, i).type != type)     diagWin = false;
            if (board.getCell(i, n-1-i) == null || board.getCell(i, n-1-i).type != type) antiWin = false;
        }
        return rowWin || colWin || diagWin || antiWin;
    }

    // Undo last move and give the turn back to previous player
    public void undoLastMove() {
        if (moveHistory.isEmpty()) { System.out.println("Nothing to undo."); return; }
        MoveCommand last = moveHistory.pop();
        last.undo(board);
        // Rotate players back
        players.addFirst(players.removeLast());
        state = GameState.ONGOING;
        System.out.println("Move undone.");
    }
}
```

---

## Phase 6: Trade-offs and Extensions

### Trade-offs

| Decision | Choice | Alternative | Reason |
|---|---|---|---|
| Win check scope | Only affected row/col/diag | Scan full board | O(N) vs O(N²); move narrows what must be checked |
| Turn management | `Deque` rotation | Index counter | Deque naturally supports undo (re-insert front) |
| Undo storage | Command stack | Board snapshots | Command stack is O(1) per move; snapshots are O(N²) |

### Extensions

**Scale to N×N with K-in-a-row win rule:**
Pass `winLength` to `isWinner` and check only a sliding window of size K instead of full lines.

**AI opponent (Minimax):**
```java
class MinimaxStrategy implements MoveStrategy {
    public int[] chooseMove(Board board, PieceType myType) {
        // For each free cell: simulate move, recurse, score, backtrack
        // Alpha-beta pruning cuts branches where outcome is already determined
        // O(b^d) base, O(b^(d/2)) with alpha-beta
    }
}
```
Wire it into `AIPlayer` via constructor injection — zero changes to the game loop.

**Tournament mode:**
Wrap `TicTacToeGame` in a `Tournament` class. Track wins per player across N games. Strategy pattern already decouples AI difficulty per player.

**Persistence:**
Serialize `moveHistory` (it is a stack of simple value objects) to reconstruct any game state.
