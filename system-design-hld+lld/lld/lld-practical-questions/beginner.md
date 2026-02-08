# Beginner

## &#x20;Tic-Tac-Toe

Here is the complete end-to-end Low Level Design (LLD) solution for Tic Tac Toe implemented in Python.

This implementation follows Object-Oriented principles, keeping the design modular, extensible, and easy to maintain.

#### 1. The Setup (Enums and Pieces)

First, we define the types of pieces and the base classes. This allows us to easily add new symbols (like '$' or '&') later without breaking the code.

Python

```python
from enum import Enum

# Enum for piece type (Scalable for future piece types)
class PieceType(Enum):
    X = 1
    O = 2

# Base Class for a Playing Piece
class PlayingPiece:
    def __init__(self, piece_type: PieceType):
        self.piece_type = piece_type

# Concrete Class for X
class PlayingPieceX(PlayingPiece):
    def __init__(self):
        super().__init__(PieceType.X)

# Concrete Class for O
class PlayingPieceO(PlayingPiece):
    def __init__(self):
        super().__init__(PieceType.O)
```

***

#### 2. The Board

The `Board` class manages the grid state. It handles printing the board, checking for free cells, and placing pieces.

Python

```python
class Board:
    def __init__(self, size):
        self.size = size
        # Initialize board with None
        self.grid = [[None for _ in range(size)] for _ in range(size)]

    def add_piece(self, row, col, playing_piece):
        if self.grid[row][col] is not None:
            return False
        self.grid[row][col] = playing_piece
        return True

    def get_free_cells(self):
        free_cells = []
        for i in range(self.size):
            for j in range(self.size):
                if self.grid[i][j] is None:
                    free_cells.append((i, j))
        return free_cells

    def print_board(self):
        for i in range(self.size):
            row_display = []
            for j in range(self.size):
                if self.grid[i][j] is not None:
                    row_display.append(self.grid[i][j].piece_type.name)
                else:
                    row_display.append(" ")
            print(" | ".join(row_display))
            print("-" * (self.size * 4 - 1))
```

***

#### 3. The Player

A simple class to bind a name to a specific piece.

Python

```python
class Player:
    def __init__(self, name, playing_piece):
        self.name = name
        self.playing_piece = playing_piece
    
    def get_name(self):
        return self.name
    
    def get_playing_piece(self):
        return self.playing_piece
```

***

#### 4. The Game Orchestrator (Main Logic)

The `TicTacToeGame` class initializes the game and runs the main loop. It uses a `deque` (double-ended queue) to manage player turns efficiently.

Python

```python
from collections import deque

class TicTacToeGame:
    def __init__(self):
        self.players = deque()
        self.game_board = None
        self.initialize_game()

    def initialize_game(self):
        # Create 2 Players
        cross_piece = PlayingPieceX()
        player1 = Player("Player1", cross_piece)

        noughts_piece = PlayingPieceO()
        player2 = Player("Player2", noughts_piece)

        self.players.append(player1)
        self.players.append(player2)

        # Initialize Board of size 3
        self.game_board = Board(3)

    def start_game(self):
        no_winner = True
        
        while no_winner:
            # 1. Take out the player whose turn it is
            player_turn = self.players.popleft()

            # 2. Check for free space (Game Over check)
            self.game_board.print_board()
            free_spaces = self.game_board.get_free_cells()
            
            if not free_spaces:
                no_winner = False
                continue

            # 3. Read the user input
            print(f"Player: {player_turn.name} Enter row,column: ", end="")
            try:
                s = input()
                values = s.split(",")
                input_row = int(values[0])
                input_col = int(values[1])
            except (ValueError, IndexError):
                print("Invalid input. Please enter row,column (e.g., 0,0)")
                self.players.appendleft(player_turn)
                continue

            # 4. Validate and Add piece
            piece_added_successfully = self.game_board.add_piece(input_row, input_col, player_turn.playing_piece)
            
            if not piece_added_successfully:
                print("Incorrect position chosen, try again")
                self.players.appendleft(player_turn) # Retry same player
                continue
            
            # Put the player back at the end of the queue for next turn
            self.players.append(player_turn)

            # 5. Check for Winner
            if self.is_there_winner(input_row, input_col, player_turn.playing_piece.piece_type):
                return player_turn.name

        return "Tie"

    def is_there_winner(self, row, col, piece_type):
        size = self.game_board.size
        grid = self.game_board.grid
        
        # Check Row
        row_match = True
        for i in range(size):
            if grid[row][i] is None or grid[row][i].piece_type != piece_type:
                row_match = False
                break
        
        # Check Column
        col_match = True
        for i in range(size):
            if grid[i][col] is None or grid[i][col].piece_type != piece_type:
                col_match = False
                break
                
        # Check Main Diagonal
        diag_match = True
        for i in range(size):
            if grid[i][i] is None or grid[i][i].piece_type != piece_type:
                diag_match = False
                break
                
        # Check Anti-Diagonal
        anti_diag_match = True
        for i in range(size):
            if grid[i][size - 1 - i] is None or grid[i][size - 1 - i].piece_type != piece_type:
                anti_diag_match = False
                break
        
        return row_match or col_match or diag_match or anti_diag_match
```

***

#### 5. Running the Game

To run the game, simply instantiate the class and call `start_game`.

Python

```python
if __name__ == "__main__":
    game = TicTacToeGame()
    print("Game winner is: " + game.start_game())
```

#### Key Design Highlights for Interviews:

1. Deque for Players: Using `collections.deque` allows for $$ $O(1)$ $$ rotation of players. This makes it very easy to support 3 or 4 players in the future (just add them to the queue).
2. Validation: The `add_piece` method returns a boolean, allowing the Game class to handle invalid moves gracefully (by asking the user again).
3. Extensibility: If the interviewer asks "Can we add a Triangle piece?", you just create `class PlayingPieceTriangle` and add it to the init logic. No core game logic needs changing.



<figure><img src="../../../.gitbook/assets/image (158).png" alt=""><figcaption></figcaption></figure>



## &#x20;Snake & Ladder Game



Here is a complete Low Level Design (LLD) solution for the Snake and Ladder Game.

***

#### 1. Requirements Clarification

* Board: Standard 10x10 grid (100 cells).
* Dice: A standard 6-sided dice.
* Players: Multiple players (2 or more).
* Snakes & Ladders: Randomly placed. Snakes take you down (start > end), Ladders take you up (start < end).
* Movement: Players start at position 0. They roll the dice and move forward.
* Winning Condition: The first player to reach or exceed position 100 wins.

***

#### 2. Core Entities (Objects)

1. Jump: Represents a connection between two cells. Used for both Snakes and Ladders.
   * Properties: `start`, `end`.
2. Cell: Represents a square on the board. Can hold a `Jump`.
3. Board: Holds the grid of Cells. Responsible for knowing where the snakes and ladders are.
4. Dice: Responsible for generating a random number (1-6).
5. Player: Holds ID and current position.
6. Game: Orchestrator. Manages the flow (rolling dice, checking jumps, turn rotation).

***

#### 3. Class Diagram & Relationships

Here is the UML diagram representing the design.

Code snippet

<figure><img src="../../../.gitbook/assets/image (195).png" alt=""><figcaption></figcaption></figure>

***

#### 4. Python Implementation

#### A. The Jump & Cell Classes

Instead of creating separate classes for Snake and Ladder, we use a generic `Jump` class.

* If `start < end` $$ $\rightarrow$ $$ Ladder.
* If `start > end` $$ $\rightarrow$ $$ Snake.

Python

```python
import random
from collections import deque

class Jump:
    def __init__(self, start, end):
        self.start = start
        self.end = end

class Cell:
    def __init__(self):
        self.jump = None  # Stores a Jump object if a snake or ladder exists here
```

#### B. The Board

The board initializes the grid and randomly places snakes and ladders.

Python

```python
class Board:
    def __init__(self, size, number_of_snakes, number_of_ladders):
        self.size = size
        # A 1D logical representation is often easier for Snake & Ladder than 2D
        # size * size + 1 to handle 1-based indexing easily (0-100)
        self.cells = [Cell() for _ in range(size * size + 1)] 
        self._add_snakes_ladders(self.cells, number_of_snakes, number_of_ladders)

    def _add_snakes_ladders(self, cells, number_of_snakes, number_of_ladders):
        # Add Snakes
        while number_of_snakes > 0:
            start = random.randint(1, len(cells) - 2)
            end = random.randint(1, len(cells) - 2)
            if start <= end: # Snake must go down
                continue
            
            if cells[start].jump is None:
                cells[start].jump = Jump(start, end)
                number_of_snakes -= 1

        # Add Ladders
        while number_of_ladders > 0:
            start = random.randint(1, len(cells) - 2)
            end = random.randint(1, len(cells) - 2)
            if start >= end: # Ladder must go up
                continue
            
            if cells[start].jump is None:
                cells[start].jump = Jump(start, end)
                number_of_ladders -= 1

    def get_cell(self, position):
        return self.cells[position]
```

#### C. The Dice

Supports rolling standard dice. Can be extended to support multiple dice.

Python

```python
class Dice:
    def __init__(self, dice_count=1):
        self.dice_count = dice_count
        self.min = 1
        self.max = 6

    def roll_dice(self):
        total_sum = 0
        dice_used = 0
        while dice_used < self.dice_count:
            total_sum += random.randint(self.min, self.max)
            dice_used += 1
        return total_sum
```

#### D. The Player

Python

```python
class Player:
    def __init__(self, player_id, current_position=0):
        self.id = player_id
        self.current_position = current_position
```

#### E. The Game Orchestrator

This manages the core loop: Pop player $$ $\rightarrow$ $$ Roll Dice $$ $\rightarrow$ $$ Calculate Move $$ $\rightarrow$ $$ Check Win $$ $\rightarrow$ $$ Push player back.

Python

```python
class SnakeAndLadderGame:
    def __init__(self):
        self.board = None
        self.dice = None
        self.players = deque()
        self.winner = None

    def initialize_game(self):
        # 1. Create Board (10x10) with 5 snakes and 5 ladders
        self.board = Board(10, 5, 5)
        
        # 2. Create Dice
        self.dice = Dice(1)
        
        # 3. Add Players
        player1 = Player("Player1", 0)
        player2 = Player("Player2", 0)
        self.players.append(player1)
        self.players.append(player2)

    def start_game(self):
        self.initialize_game()
        
        while self.winner is None:
            # 1. Get current player
            player_turn = self.players.popleft()
            
            # 2. Roll Dice
            dice_value = self.dice.roll_dice()
            
            # 3. Calculate new position
            current_pos = player_turn.current_position
            new_pos = current_pos + dice_value
            
            # Check for jump (Snake or Ladder)
            if new_pos <= self.board.size * self.board.size:
                cell = self.board.get_cell(new_pos)
                if cell.jump is not None:
                    jump_name = "Snake" if cell.jump.start > cell.jump.end else "Ladder"
                    print(f"{player_turn.id} hit a {jump_name} at {cell.jump.start}! Moving to {cell.jump.end}")
                    new_pos = cell.jump.end
            
            # 4. Check Win Condition
            board_limit = self.board.size * self.board.size
            if new_pos >= board_limit:
                self.winner = player_turn
                print(f"WINNER: {player_turn.id} reached {new_pos}!")
                return 

            player_turn.current_position = new_pos
            print(f"{player_turn.id} rolled a {dice_value} and moved to {new_pos}")
            
            # 5. Add player back to queue
            self.players.append(player_turn)

if __name__ == "__main__":
    game = SnakeAndLadderGame()
    game.start_game()
```

***

#### 5. Design Considerations for Interview

#### 1. Why use a `Jump` class instead of Snake/Ladder subclasses?

This simplifies the `Board` logic. The board only needs to know "Is there a jump here?". It doesn't need to know the physics of snakes vs ladders. The logic is purely `current_pos = jump.end`.

#### 2. Design Pattern: Strategy Pattern (Dice)

If the interviewer asks: "What if we have a rigged dice? Or a 20-sided dice?"

You can use the Strategy Pattern for the Dice.

* Interface: `DiceStrategy` (`roll()`)
*   Concrete: NormalDice, LoadedDice, DoubleDice.

    The Game class would just call dice.roll() regardless of the implementation.

#### 3. Concurrency (Advanced)

If multiple games run on a server:

* Make the `Game` class a unique session instance.
* The `Board` can be a Singleton if the snakes/ladders are static (same for everyone). If every game has random snakes, the Board must be instantiated per game.

#### 4. Code Extensibility (Adding "Skip Turn" or "Extra Turn")

We can extend `Cell` to have a `CellEffect`.

* `CellEffect` could be an Enum or Interface: `JUMP`, `EXTRA_TURN`, `SKIP_TURN`.
* The `Game` loop would check the effect at the `new_pos` and adjust the `players` deque accordingly (e.g., adding the player to the front again for "Extra Turn").



## &#x20;Tetris Game&#x20;



An end-to-end Low Level Design (LLD) solution for the Tetris Game.

***

#### 1. Requirements Clarification

* Board: Standard grid (usually 10x20).
* Pieces (Tetrominoes): 7 standard shapes (I, O, T, S, Z, J, L), each consisting of 4 blocks.
* Movement: Pieces fall automatically. Players can move them Left, Right, Down (soft drop), or Rotate.
* Collision: Pieces stop when they hit the bottom or another piece.
* Line Clearing: When a row is full, it is cleared, and blocks above move down.
* Game Over: If a new piece cannot be placed at the top, the game ends.

***

#### 2. Core Entities (Objects)

1. Block (Cell): The smallest unit. Has a state (occupied/empty) and color.
2. Point: A helper class for coordinates (row, col).
3. Tetromino (Piece): The falling shape. It has a list of `Points` representing its shape relative to a center or pivot.
4. Board: The grid. Stores the state of "landed" blocks. Checks collisions and clears lines.
5. Game: The controller. Handles the game loop, input, and score.

***

#### 3. Class Diagram

Code snippet

<figure><img src="../../../.gitbook/assets/image (196).png" alt=""><figcaption></figcaption></figure>

***

#### 4. Python Implementation

#### A. Helper Class (Point)

Used to manage coordinates easily.

Python

```python
class Point:
    def __init__(self, row, col):
        self.row = row
        self.col = col

    def add(self, other_point):
        return Point(self.row + other_point.row, self.col + other_point.col)
```

#### B. The Tetromino (Piece) Base Class & Factory

This manages the shape and rotation logic.

* Rotation Logic: Standard matrix rotation. $$ $(x, y) \rightarrow (y, -x)$ $$.

Python

```python
import random

class Tetromino:
    def __init__(self, shape_coords, color_id):
        # Shape is a list of Points relative to (0,0)
        self.shape = shape_coords 
        self.position = Point(0, 4) # Spawns at top middle
        self.color_id = color_id

    def rotate(self):
        # 90 degree clockwise rotation: (r, c) -> (c, -r)
        new_shape = []
        for p in self.shape:
            new_shape.append(Point(p.col, -p.row))
        self.shape = new_shape

    def get_absolute_positions(self):
        # Returns actual board coordinates of the piece
        return [self.position.add(p) for p in self.shape]

    def move(self, row_offset, col_offset):
        self.position = self.position.add(Point(row_offset, col_offset))

# Concrete Pieces
# Example: T-Shape
class TShape(Tetromino):
    def __init__(self):
        # Center, Left, Right, Top
        coords = [Point(0,0), Point(0,-1), Point(0,1), Point(-1,0)]
        super().__init__(coords, 1)

class IShape(Tetromino):
    def __init__(self):
        coords = [Point(0,0), Point(0,-1), Point(0,1), Point(0,2)]
        super().__init__(coords, 2)

# ... (Similarly for O, S, Z, J, L)

# Factory Pattern to get random pieces
class TetrominoFactory:
    @staticmethod
    def get_random_piece():
        shapes = [TShape(), IShape()] # Add others here
        return random.choice(shapes)
```

#### C. The Board

Handles the grid state, collision detection, and line clearing.

Python

```python
class Board:
    def __init__(self, height=20, width=10):
        self.height = height
        self.width = width
        # 0 = Empty, >0 = Color ID of block
        self.grid = [[0 for _ in range(width)] for _ in range(height)]

    def is_valid_position(self, tetromino):
        for p in tetromino.get_absolute_positions():
            # Check boundaries
            if not (0 <= p.row < self.height and 0 <= p.col < self.width):
                return False
            # Check overlap with existing blocks
            if self.grid[p.row][p.col] != 0:
                return False
        return True

    def place_piece(self, tetromino):
        for p in tetromino.get_absolute_positions():
            self.grid[p.row][p.col] = tetromino.color_id

    def clear_full_lines(self):
        lines_cleared = 0
        new_grid = [row for row in self.grid if any(x == 0 for x in row)]
        lines_cleared = self.height - len(new_grid)
        
        # Add empty rows at the top
        for _ in range(lines_cleared):
            new_grid.insert(0, [0] * self.width)
            
        self.grid = new_grid
        return lines_cleared

    def print_board(self, current_piece=None):
        # Create a temporary view including the moving piece
        display_grid = [row[:] for row in self.grid]
        
        if current_piece:
            for p in current_piece.get_absolute_positions():
                if 0 <= p.row < self.height and 0 <= p.col < self.width:
                    display_grid[p.row][p.col] = 8 # 8 represents moving piece

        print("\nBoard State:")
        for row in display_grid:
            print(" ".join(str(x) if x != 0 else '.' for x in row))
```

#### D. The Game Orchestrator

This manages the input and game loop (tick).

Python

```python
class TetrisGame:
    def __init__(self):
        self.board = Board()
        self.current_piece = TetrominoFactory.get_random_piece()
        self.score = 0
        self.game_over = False

    def handle_input(self, command):
        # command: 'L' (Left), 'R' (Right), 'D' (Down), 'U' (Rotate)
        if self.game_over: return

        # Backup current state for rollback if move is invalid
        original_pos = Point(self.current_piece.position.row, self.current_piece.position.col)
        original_shape = list(self.current_piece.shape)

        if command == 'L':
            self.current_piece.move(0, -1)
        elif command == 'R':
            self.current_piece.move(0, 1)
        elif command == 'D':
            self.current_piece.move(1, 0)
        elif command == 'U':
            self.current_piece.rotate()

        if not self.board.is_valid_position(self.current_piece):
            # Revert move
            self.current_piece.position = original_pos
            self.current_piece.shape = original_shape
            
            # Special case: If moving down failed, the piece lands
            if command == 'D':
                self.lock_piece()

    def update(self):
        # Called automatically every "tick" (e.g., every 1 second)
        if self.game_over: return

        original_pos = Point(self.current_piece.position.row, self.current_piece.position.col)
        self.current_piece.move(1, 0) # Gravity

        if not self.board.is_valid_position(self.current_piece):
            self.current_piece.position = original_pos
            self.lock_piece()

    def lock_piece(self):
        self.board.place_piece(self.current_piece)
        lines = self.board.clear_full_lines()
        self.score += lines * 100
        
        # Spawn new piece
        self.current_piece = TetrominoFactory.get_random_piece()
        
        # Check immediate game over
        if not self.board.is_valid_position(self.current_piece):
            self.game_over = True
            print("GAME OVER")

# Driver Code Simulation
if __name__ == "__main__":
    game = TetrisGame()
    
    # Simulate a few moves
    game.board.print_board(game.current_piece)
    
    print("Moving Down...")
    game.handle_input('D')
    game.handle_input('D')
    game.board.print_board(game.current_piece)
    
    print("Rotating...")
    game.handle_input('U')
    game.board.print_board(game.current_piece)
    
    print("Simulating Tick (Gravity)...")
    game.update()
    game.board.print_board(game.current_piece)
```

***

#### 5. Key Design Decisions (Interview Talking Points)

1. Tetromino Factory:
   * Why: Decouples the `Game` class from specific shapes. If you want to add a "Bomb" piece or a "Single Block" piece later, you only modify the Factory, not the Game logic.
2. Board as the source of truth:
   * Why: The Board knows boundaries and existing blocks. The Piece shouldn't check its own collision; the Board should check if a Piece fits.
3. Rotation Logic:
   * Detail: Implementing rotation can be tricky. Using relative coordinates (Points relative to a center `0,0`) makes the math $$ $(x, y) \rightarrow (y, -x)$ $$ very clean compared to rotating absolute indices.
4. Game Loop vs Input:
   * Design: I separated `handle_input` (user action) from `update` (automatic gravity). This mimics real game engine architecture (Input Loop vs Update Loop).

## &#x20;Minesweeper



Here is the complete Low Level Design (LLD) solution for Minesweeper.

***

#### 1. Requirements Clarification

* Board: Grid of size $$ $N \times M$ $$.
* Components:
  * Mines: Hidden randomly.
  * Numbers: Represents the count of mines in the 8 neighboring cells.
  * Empty: No neighboring mines (Value 0).
* Actions:
  * Reveal (Click): Shows content.
    * If Mine $$ $\rightarrow$ $$ Game Over (Loss).
    * If Number $$ $\rightarrow$ $$ Reveal just that cell.
    * If Empty $$ $\rightarrow$ $$ Auto-reveal neighbors recursively (Flood Fill).
  * Flag: Mark a cell as a potential mine.
* Win Condition: All non-mine cells are revealed.

***

#### 2. Core Entities

1. Cell: The atomic unit. Needs to know if it's a mine, its adjacent mine count, and its current state (Hidden, Revealed, Flagged).
2. Board: The grid. Handles mine placement, adjacency calculations, and the "Flood Fill" algorithm.
3. Game: The controller. Manages input parsing, game status (Won/Lost/Playing), and time tracking.

***

#### 3. Class Diagram & Relationships

Code snippet

<figure><img src="../../../.gitbook/assets/image (197).png" alt=""><figcaption></figcaption></figure>

***

#### 4. Python Implementation

#### A. The Cell Class

We separate the _data_ (mine/number) from the _state_ (revealed/flagged).

Python

```python
class Cell:
    def __init__(self):
        # Configuration
        self.is_mine = False
        self.adjacent_mines_count = 0
        
        # Player State
        self.is_revealed = False
        self.is_flagged = False

    def reveal(self):
        self.is_revealed = True
    
    def toggle_flag(self):
        self.is_flagged = not self.is_flagged
```

#### B. The Board Class (The Heavy Lifter)

This class handles the core complexity: Mine Placement and Flood Fill.

Python

```python
import random
from collections import deque

class Board:
    def __init__(self, rows, cols, num_mines):
        self.rows = rows
        self.cols = cols
        self.num_mines = num_mines
        self.grid = [[Cell() for _ in range(cols)] for _ in range(rows)]
        self.cells_revealed_count = 0
        
        self._place_mines()
        self._calculate_adjacency()

    def _place_mines(self):
        # Randomly place mines
        mines_placed = 0
        while mines_placed < self.num_mines:
            r = random.randint(0, self.rows - 1)
            c = random.randint(0, self.cols - 1)
            
            if not self.grid[r][c].is_mine:
                self.grid[r][c].is_mine = True
                mines_placed += 1

    def _calculate_adjacency(self):
        # Pre-calculate numbers for all cells
        directions = [(-1, -1), (-1, 0), (-1, 1), (0, -1), 
                      (0, 1), (1, -1), (1, 0), (1, 1)]
        
        for r in range(self.rows):
            for c in range(self.cols):
                if self.grid[r][c].is_mine:
                    continue
                
                # Count mines in neighbors
                count = 0
                for dr, dc in directions:
                    nr, nc = r + dr, c + dc
                    if 0 <= nr < self.rows and 0 <= nc < self.cols:
                        if self.grid[nr][nc].is_mine:
                            count += 1
                self.grid[r][c].adjacent_mines_count = count

    def reveal_cell(self, row, col):
        cell = self.grid[row][col]
        
        if cell.is_revealed or cell.is_flagged:
            return False # No-op

        cell.reveal()
        self.cells_revealed_count += 1

        if cell.is_mine:
            return True # Explosion! (Return True indicating Game Over condition hit)

        # Optimization: Flood Fill (BFS)
        # Only if the cell is empty (0 adjacent mines), we auto-reveal neighbors
        if cell.adjacent_mines_count == 0:
            self._flood_fill(row, col)
            
        return False # Safe move

    def _flood_fill(self, row, col):
        queue = deque([(row, col)])
        directions = [(-1, -1), (-1, 0), (-1, 1), (0, -1), 
                      (0, 1), (1, -1), (1, 0), (1, 1)]
        
        while queue:
            r, c = queue.popleft()
            
            for dr, dc in directions:
                nr, nc = r + dr, c + dc
                if 0 <= nr < self.rows and 0 <= nc < self.cols:
                    neighbor = self.grid[nr][nc]
                    
                    if not neighbor.is_revealed and not neighbor.is_mine:
                        neighbor.reveal()
                        self.cells_revealed_count += 1
                        
                        # If the neighbor is ALSO a zero, add to queue to continue expansion
                        if neighbor.adjacent_mines_count == 0:
                            queue.append((nr, nc))

    def print_board(self, reveal_all=False):
        print("  " + " ".join(str(i) for i in range(self.cols)))
        for r in range(self.rows):
            row_str = []
            for c in range(self.cols):
                cell = self.grid[r][c]
                if reveal_all:
                    val = "*" if cell.is_mine else str(cell.adjacent_mines_count)
                    row_str.append(val)
                elif cell.is_flagged:
                    row_str.append("F")
                elif not cell.is_revealed:
                    row_str.append("-")
                elif cell.is_mine:
                    row_str.append("*")
                else:
                    row_str.append(str(cell.adjacent_mines_count))
            print(f"{r} " + " ".join(row_str))
```

#### C. The Game Orchestrator

Handles the winning logic and user input.

Python

```python
class MinesweeperGame:
    def __init__(self, rows=10, cols=10, mines=10):
        self.rows = rows
        self.cols = cols
        self.mines = mines
        self.board = Board(rows, cols, mines)
        self.game_over = False
        self.win = False

    def play(self):
        while not self.game_over:
            self.board.print_board()
            try:
                user_input = input("Enter row, col, action (r=reveal, f=flag): ").split()
                r, c = int(user_input[0]), int(user_input[1])
                action = user_input[2]
            except:
                print("Invalid Input.")
                continue

            if action == 'f':
                self.board.grid[r][c].toggle_flag()
            elif action == 'r':
                hit_mine = self.board.reveal_cell(r, c)
                if hit_mine:
                    self.game_over = True
                    self.win = False
                    print("BOOM! Game Over.")
                    self.board.print_board(reveal_all=True)
            
            if self._check_win():
                self.game_over = True
                self.win = True
                print("Congratulations! You cleared the minefield.")
                self.board.print_board(reveal_all=True)

    def _check_win(self):
        # Win if all non-mine cells are revealed
        total_cells = self.rows * self.cols
        non_mine_cells = total_cells - self.mines
        return self.board.cells_revealed_count == non_mine_cells

if __name__ == "__main__":
    game = MinesweeperGame(5, 5, 3) # Small board for testing
    game.play()
```

***

#### 5. Key Interview Points (The "Why")

#### 1. Algorithm: Flood Fill (BFS vs DFS)

* Question: How do you handle revealing an empty area?
* Answer: I used BFS (`deque`) in the `_flood_fill` method.
  * Why BFS? It reveals the area layer-by-layer, which feels more natural in UI rendering, though DFS works fine too.
  * Logic: If I reveal a cell with `0`, I add all its neighbors to the queue. If I reveal a cell with `1-8`, I stop there (it's the border of the empty region).

#### 2. Initialization Strategy (Advanced)

* Question: What if the user clicks a mine on the very first turn? That's bad UX.
* Optimization: In a production system, `_place_mines()` should be called _after_ the first click.
  * Capture user's first `(r, c)`.
  * Place mines randomly, ensuring `(r, c)` and its immediate neighbors are skipped.
  * Calculate adjacency numbers.
  * Proceed with the reveal.

#### 3. Separation of Cell State vs Configuration

* I separated `is_mine` (Configuration) from `is_revealed` (State).
* This prevents "Cheating" logic where the UI might accidentally peek at the mine status before the user reveals it. The UI should only render based on `is_revealed`.

## Chess Game&#x20;



An end-to-end Low Level Design (LLD) solution for the Chess Game.

***

#### 1. Requirements Clarification

* Board: Standard 8x8 grid.
* Pieces: King, Queen, Bishop, Knight, Rook, Pawn (White & Black sets).
* Movement Rules: Unique logic for each piece type.
* Game Flow: Turn-based (White moves first).
* Special Moves: Castling, En Passant, Promotion.
* Win Condition: Checkmate.
* End Condition: Stalemate, Draw, Resignation.

***

#### 2. Core Entities

1. Block (Spot): A coordinate $$ $(x, y)$ $$ that may contain a piece.
2. Piece: Abstract class. Contains color and logic for `can_move`.
3. Board: 8x8 grid of Blocks.
4. Player: Human or Engine.
5. Move: Represents a start and end block, plus piece moved.
6. Game: Orchestrator. Validates turns, checks for checkmate/stalemate.

***

#### 3. Class Diagram & Design

Code snippet

<figure><img src="../../../.gitbook/assets/image (198).png" alt=""><figcaption></figcaption></figure>

***

#### 4. Python Implementation

#### A. The Piece Hierarchy

We use an abstract base class `Piece` and implement movement logic in subclasses.

Python

```python
from abc import ABC, abstractmethod

class Piece(ABC):
    def __init__(self, white: bool):
        self.white = white
        self.killed = False

    @abstractmethod
    def can_move(self, board, start_block, end_block):
        pass

class King(Piece):
    def can_move(self, board, start, end):
        # Basic logic: King moves 1 step in any direction
        x = abs(start.x - end.x)
        y = abs(start.y - end.y)
        if x + y <= 2 and x <= 1 and y <= 1:
            # Check if destination is not occupied by friendly piece
            # (Note: Full validation requires checking for 'Check')
            return end.piece is None or end.piece.white != self.white
        return False

class Knight(Piece):
    def can_move(self, board, start, end):
        x = abs(start.x - end.x)
        y = abs(start.y - end.y)
        return x * y == 2 # 2x1 or 1x2 L-shape jump

# ... Implement Queen, Bishop, Rook, Pawn similarly
```

#### B. The Board and Block

The Board initializes the grid and places pieces.

Python

```python
class Block:
    def __init__(self, x, y, piece=None):
        self.x = x
        self.y = y
        self.piece = piece

class Board:
    def __init__(self):
        self.boxes = [[None for _ in range(8)] for _ in range(8)]
        self.reset_board()

    def reset_board(self):
        # Initialize 8x8 blocks
        # Place Pieces (simplified for brevity)
        # White Pieces
        self.boxes[0][0] = Block(0, 0, Rook(True))
        self.boxes[0][1] = Block(0, 1, Knight(True))
        # ... setup other pieces ...
        
        # Empty Blocks
        for i in range(2, 6):
            for j in range(8):
                self.boxes[i][j] = Block(i, j, None)

    def get_block(self, x, y):
        if 0 <= x < 8 and 0 <= y < 8:
            return self.boxes[x][y]
        raise Exception("Index out of bound")
```

#### C. The Move and Game Logic

This is where the complex rules (turns, checkmate) reside.

Python

```python
class Move:
    def __init__(self, player, start_block, end_block):
        self.player = player
        self.start = start_block
        self.end = end_block
        self.piece_moved = start_block.piece
        self.piece_killed = end_block.piece
        self.castling_move = False

class Game:
    def __init__(self):
        self.board = Board()
        self.players = [Player(white_side=True), Player(white_side=False)]
        self.current_turn = self.players[0] # White starts
        self.moves_played = []
        self.game_status = "ACTIVE"

    def player_move(self, start_x, start_y, end_x, end_y):
        start_box = self.board.get_block(start_x, start_y)
        end_box = self.board.get_block(end_x, end_y)
        move = Move(self.current_turn, start_box, end_box)
        
        return self.make_move(move, self.current_turn)

    def make_move(self, move, player):
        source_piece = move.start.piece
        
        # 1. Validation: Is there a piece? Is it your turn?
        if source_piece is None:
            return False
        if player != self.current_turn:
            return False
        if source_piece.white != player.white_side:
            return False

        # 2. Validation: Can piece physically move there?
        if not source_piece.can_move(self.board, move.start, move.end):
            return False

        # 3. Execution: Kill piece if exists
        killed_piece = move.end.piece
        if killed_piece is not None:
            killed_piece.killed = True
            
        # Move piece
        move.end.piece = source_piece
        move.start.piece = None

        # 4. Post-Move Check: Did this move leave current player in Check?
        # (This is expensive: Requires scanning board to see if King is under attack)
        # if self.is_check(player):
        #     revert_move(move)
        #     return False

        self.moves_played.append(move)

        # 5. Switch Turn
        if self.current_turn == self.players[0]:
            self.current_turn = self.players[1]
        else:
            self.current_turn = self.players[0]

        return True
```

#### D. The Player

Simple wrapper for player identity.

Python

```python
class Player:
    def __init__(self, white_side, human_player=True):
        self.white_side = white_side
        self.human_player = human_player
```

***

#### 5. Key Complexity Handling (Interview "Gotchas")

#### 1. "Check" and "Checkmate" Validation

This is the hardest part of Chess LLD.

* The Problem: A piece might _physically_ be able to move to a spot, but if that move exposes the King to capture, the move is illegal.
* The Solution: You implement a `is_move_valid()` method in the Game class that:
  1. Temporarily executes the move.
  2. Scans the board to see if the opponent can capture the King.
  3. If yes, revert the move and return `False`.

#### 2. Special Moves (Castling, En Passant)

These violate standard movement rules.

* Design: Add flags to the `King` and `Rook` classes: `has_moved`.
* Logic: Inside `King.can_move()`, check:
  * Has King moved?
  * Has target Rook moved?
  * Is path clear?
  * Is path under attack? (King cannot castle _through_ check).

#### 3. Pawn Promotion

* Design: When a Pawn reaches the last rank ($$ $x=0$ $$ or $$ $x=7$ $$), the Game class must pause and ask for user input (Queen, Rook, etc.), then replace the Pawn object with the new Piece object.

## &#x20;Alarm/Alert Service for AWS

An end-to-end Low Level Design (LLD) for an Alarm/Alert Service (similar to AWS CloudWatch Alarms).

***

#### 1. Requirements Clarification

* Input: The system receives metrics (e.g., CPU Usage, Latency) from resources.
* Rules (Alarms): Users can define rules (e.g., `If CPU > 80% for 3 consecutive data points`).
* State Management: Alarms have states: `OK` $$ $\leftrightarrow$ $$ `ALARM`.
  * We only notify on state transitions (to prevent spamming every second the CPU is high).
* Notifications: Support multiple channels (Email, SMS, Webhook, PagerDuty).
* Scalability: Must handle high throughput of incoming metrics.

***

#### 2. Core Entities

1. Metric: A data point consisting of `{MetricName, Value, Timestamp, Dimensions}`.
2. Condition (Rule): Logic to evaluate the metric (e.g., `GreaterThan`, `LessThan`).
3. Alarm: The core object binding a Metric to a Condition. It maintains the current State.
4. Action: What to do when the state changes (e.g., `SendEmail`).
5. AlertService: The Orchestrator. Ingests metrics and routes them to the correct alarms.

***

#### 3. Class Diagram & Relationships

We use the Strategy Pattern for notification channels and the State Pattern (simplified) for alarm status.

Code snippet

<figure><img src="../../../.gitbook/assets/image (199).png" alt=""><figcaption></figcaption></figure>

***

#### 4. Python Implementation

#### A. The Basic Data Structures

Enums and Metric class to standardize inputs.

Python

```python
from enum import Enum
import time

class ComparisonOperator(Enum):
    GREATER_THAN = 1
    LESS_THAN = 2
    EQUALS = 3

class AlarmState(Enum):
    OK = 1
    ALARM = 2
    INSUFFICIENT_DATA = 3

class Metric:
    def __init__(self, name, value, dimensions=None):
        self.name = name
        self.value = value
        self.dimensions = dimensions or {}
        self.timestamp = time.time()
```

#### B. Notification Strategy (Strategy Pattern)

This allows us to easily add "Slack" or "PagerDuty" later without changing the Alarm class.

Python

```python
from abc import ABC, abstractmethod

class NotificationStrategy(ABC):
    @abstractmethod
    def send(self, message):
        pass

class EmailNotification(NotificationStrategy):
    def __init__(self, email_address):
        self.email_address = email_address

    def send(self, message):
        print(f"[Email to {self.email_address}]: {message}")

class SMSNotification(NotificationStrategy):
    def __init__(self, phone_number):
        self.phone_number = phone_number

    def send(self, message):
        print(f"[SMS to {self.phone_number}]: {message}")
```

#### C. The Alarm Logic (State Management)

This is the heart of the system. It checks the rule and manages the `OK` -> `ALARM` transition.

Python

```python
class Condition:
    def __init__(self, metric_name, operator, threshold):
        self.metric_name = metric_name
        self.operator = operator
        self.threshold = threshold

    def evaluate(self, value):
        if self.operator == ComparisonOperator.GREATER_THAN:
            return value > self.threshold
        elif self.operator == ComparisonOperator.LESS_THAN:
            return value < self.threshold
        elif self.operator == ComparisonOperator.EQUALS:
            return value == self.threshold
        return False

class Alarm:
    def __init__(self, name, condition):
        self.name = name
        self.condition = condition
        self.state = AlarmState.OK
        self.actions = [] # List of NotificationStrategies
        
        # Simple windowing: track last N violations 
        # (In a real system, this would be a sliding window)
        self.consecutive_breaches = 0
        self.required_breaches = 1 # simplified for LLD

    def add_action(self, action: NotificationStrategy):
        self.actions.append(action)

    def evaluate_metric(self, metric: Metric):
        # 1. Check if metric matches this alarm
        if metric.name != self.condition.metric_name:
            return

        # 2. Check Logic
        is_breach = self.condition.evaluate(metric.value)

        # 3. Handle State Transition
        if is_breach:
            self.consecutive_breaches += 1
            if self.consecutive_breaches >= self.required_breaches:
                self._transition_state(AlarmState.ALARM, metric.value)
        else:
            self.consecutive_breaches = 0
            self._transition_state(AlarmState.OK, metric.value)

    def _transition_state(self, new_state, metric_value):
        if self.state != new_state:
            # State Changed! Trigger Actions
            old_state = self.state
            self.state = new_state
            
            # Only alert if moving TO Alarm (or optionally OK for resolution)
            if new_state == AlarmState.ALARM:
                msg = f"ALARM: {self.name} transitioned from {old_state.name} to {new_state.name}. Value: {metric_value}"
                self._trigger_actions(msg)
            elif new_state == AlarmState.OK and old_state == AlarmState.ALARM:
                 msg = f"RESOLVED: {self.name} is back to normal."
                 self._trigger_actions(msg)

    def _trigger_actions(self, message):
        for action in self.actions:
            action.send(message)
```

#### D. The Orchestrator (Service)

Simulates the ingestion of metrics.

Python

```python
class AlertService:
    def __init__(self):
        self.alarms = []

    def add_alarm(self, alarm):
        self.alarms.append(alarm)

    def ingest_metric(self, metric):
        # In a distributed system, you would use a HashMap or DB to find relevant alarms
        # For LLD, iteration is fine.
        for alarm in self.alarms:
            alarm.evaluate_metric(metric)

# --- Driver Code ---
if __name__ == "__main__":
    service = AlertService()

    # 1. Define Rule: CPU > 80%
    cpu_condition = Condition("CPU_Usage", ComparisonOperator.GREATER_THAN, 80)
    cpu_alarm = Alarm("High_CPU_Alarm", cpu_condition)

    # 2. Add Actions
    cpu_alarm.add_action(EmailNotification("admin@company.com"))
    cpu_alarm.add_action(SMSNotification("+1234567890"))

    service.add_alarm(cpu_alarm)

    # 3. Simulate Metric Stream
    print("--- Stream Started ---")
    service.ingest_metric(Metric("CPU_Usage", 50)) # OK
    service.ingest_metric(Metric("CPU_Usage", 85)) # Trigger ALARM
    service.ingest_metric(Metric("CPU_Usage", 90)) # Still ALARM (No Notification - State didn't change)
    service.ingest_metric(Metric("CPU_Usage", 40)) # Trigger RESOLVED
```

***

#### 5. Key Design Decisions (Interview Talking Points)

#### 1. Handling Flapping (State Oscillation)

* Problem: If CPU hovers at 80% (79, 81, 79, 81), the user gets spammed with "Alarm", "Resolved", "Alarm".
* Solution: In `Alarm` class, implement M out of N logic.
  * Change `self.required_breaches` to 3.
  * The state only transitions to `ALARM` if we see 3 bad metrics in a row.

#### 2. Strategy Pattern for Notifications

* Why? Allows us to follow the Open/Closed Principle. We can add `WebhookNotification` or `SlackNotification` without modifying the `Alarm` class code.

#### 3. Metric Matching (Scalability)

* In the python code, I iterated all alarms (`for alarm in self.alarms`).
* Optimization: In a real system, use a HashMap or Inverted Index.
  * Map: `MetricName -> List[AlarmID]`.
  * When `CPU_Usage` comes in, we directly fetch only the relevant alarms.

#### 4. Push vs Pull

* This Design (Push): Metrics are pushed into the evaluator. Best for real-time.
* Alternative (Pull): The evaluator queries a database periodically ("Select avg(cpu) from DB"). Best for complex aggregations.

## Logging Library like log4j



An end-to-end Low Level Design (LLD) for a Logging Framework similar to Log4j or Python's `logging` module.

***

#### 1. Requirements Clarification

* Log Levels: Support standard levels (DEBUG, INFO, ERROR, etc.) with priority order.
* Multiple Sinks (Appenders): Support writing to Console, Files, or Databases.
* Formatters (Layouts): Support different output formats (Simple Text, JSON, XML).
* Configuration: Ability to set different logging levels and appenders for different loggers.
* Singleton/Factory: Central management of Logger instances.
* Thread Safety: Writing to shared resources (like a file) must be thread-safe.

***

#### 2. Core Entities

1. LogLevel: Enum defining severity (DEBUG < INFO < ERROR).
2. LogMessage: Encapsulates the data (message, timestamp, thread ID).
3. Layout (Formatter): Strategy interface for converting a `LogMessage` into a string.
4. Appender (Sink): Strategy interface for outputting the formatted string (Console, File).
5. Logger: The main component used by clients. Filters messages based on Level and delegates to Appenders.
6. LoggerManager: A centralized factory/singleton to manage logger instances.

***

#### 3. Class Diagram & Relationships

We rely heavily on the Strategy Pattern (for Layouts and Appenders) and the Factory Pattern (for Logger creation).

Code snippet

<figure><img src="../../../.gitbook/assets/image (200).png" alt=""><figcaption></figcaption></figure>

***

#### 4. Python Implementation

#### A. Basic Setup (Enum & Message)

We define the priority levels.

Python

```python
import time
import threading
from enum import IntEnum
from abc import ABC, abstractmethod

class LogLevel(IntEnum):
    DEBUG = 1
    INFO = 2
    WARNING = 3
    ERROR = 4
    FATAL = 5

class LogMessage:
    def __init__(self, level, message):
        self.level = level
        self.message = message
        self.timestamp = time.time()
        self.thread_name = threading.current_thread().name
```

#### B. Layouts (Strategy Pattern)

This handles _how_ the log looks.

Python

```python
class Layout(ABC):
    @abstractmethod
    def format(self, log_message: LogMessage) -> str:
        pass

class SimpleLayout(Layout):
    def format(self, msg: LogMessage) -> str:
        return f"[{msg.timestamp}] [{msg.thread_name}] {msg.level.name}: {msg.message}"

class JsonLayout(Layout):
    def format(self, msg: LogMessage) -> str:
        import json
        return json.dumps({
            "time": msg.timestamp,
            "level": msg.level.name,
            "msg": msg.message
        })
```

#### C. Appenders (Strategy Pattern)

This handles _where_ the log goes. Note the thread locking in `FileAppender`.

Python

```python
class Appender(ABC):
    def __init__(self, layout: Layout):
        self.layout = layout

    @abstractmethod
    def append(self, log_message: LogMessage):
        pass

class ConsoleAppender(Appender):
    def append(self, msg: LogMessage):
        # Print is generally thread-safe in Python, but good practice to lock if complex
        print(self.layout.format(msg))

class FileAppender(Appender):
    def __init__(self, layout: Layout, file_path: str):
        super().__init__(layout)
        self.file_path = file_path
        self.lock = threading.Lock() # Essential for thread safety

    def append(self, msg: LogMessage):
        formatted_string = self.layout.format(msg)
        with self.lock:
            with open(self.file_path, "a") as f:
                f.write(formatted_string + "\n")
```

#### D. The Logger

This component filters messages based on severity and delegates to all attached appenders.

Python

```python
class Logger:
    def __init__(self, name):
        self.name = name
        self.level = LogLevel.INFO # Default level
        self.appenders = []

    def set_level(self, level: LogLevel):
        self.level = level

    def add_appender(self, appender: Appender):
        self.appenders.append(appender)

    def log(self, level: LogLevel, message: str):
        # 1. Filter Check (Performance Optimization)
        if level < self.level:
            return

        # 2. Create Message Object
        log_msg = LogMessage(level, message)

        # 3. Delegate to all Appenders
        for appender in self.appenders:
            try:
                appender.append(log_msg)
            except Exception as e:
                # Fail-safe: Logging shouldn't crash the application
                print(f"Failed to log: {e}")

    # Convenience Methods
    def debug(self, msg): self.log(LogLevel.DEBUG, msg)
    def info(self, msg): self.log(LogLevel.INFO, msg)
    def error(self, msg): self.log(LogLevel.ERROR, msg)
```

#### E. Logger Manager (Singleton/Factory)

Ensures we don't create duplicate logger instances for the same name.

Python

```python
class LoggerManager:
    _loggers = {}
    _lock = threading.Lock()

    @staticmethod
    def get_logger(name):
        with LoggerManager._lock:
            if name not in LoggerManager._loggers:
                LoggerManager._loggers[name] = Logger(name)
            return LoggerManager._loggers[name]

# --- Driver Code ---
if __name__ == "__main__":
    # 1. Configuration
    logger = LoggerManager.get_logger("PaymentService")
    logger.set_level(LogLevel.DEBUG)

    # 2. Add Strategies
    console = ConsoleAppender(SimpleLayout())
    file_out = FileAppender(JsonLayout(), "app.log")
    
    logger.add_appender(console)
    logger.add_appender(file_out)

    # 3. Usage
    logger.info("Application started")       # Printed (INFO >= DEBUG)
    logger.debug("Debugging transaction...") # Printed (DEBUG >= DEBUG)
    logger.set_level(LogLevel.ERROR)
    logger.info("This will be ignored")      # Ignored (INFO < ERROR)
    logger.error("Critical Failure!")        # Printed
```

***

#### 5. Key Interview Points

#### 1. Why use the Strategy Pattern?

We separate Formatting (`Layout`) and Output (`Appender`).

* This allows combinatorial freedom. I can have `Console + JSON`, `File + JSON`, or `Console + SimpleText` without creating classes like `JsonConsoleAppender`.

#### 2. Thread Safety

* Problem: If two threads write to `app.log` at the exact same time, lines might get interleaved/garbled.
* Solution: The `FileAppender` holds a `threading.Lock`. It acquires the lock before writing and releases it after.

#### 3. Performance (Lazy Evaluation)

* Question: What if `layout.format()` takes 1 second, but the log level is filtered out?
* Answer: In the `Logger.log()` method, we check `if level < self.level` before creating the `LogMessage` or calling any formatters. This prevents wasting CPU on logs that nobody will see.

#### 4. Extensibility

* Question: How to add sending logs to Slack?
* Answer: Create a new class `SlackAppender(Appender)`. Implement `append()`. Add it to the logger. No existing code needs to change (Open/Closed Principle).

## JSON Parser&#x20;



Here is the complete Low Level Design (LLD) solution for a JSON Parser.

Designing a parser usually involves two distinct stages:

1. Lexical Analysis (Tokenizer): Converts the raw string into a list of meaningful tokens (e.g., `"{", "key", ":", 123, "}"`).
2. Syntactic Analysis (Parser): Processes the tokens to build the final data structure (Object/Array). We will use a Recursive Descent Parser.

***

#### 1. Requirements Clarification

* Input: A valid JSON string.
* Output: A native object (Dictionary for `{}`, List for `[]`, etc.).
* Supported Types:
  * Objects: `{ "key": value }`
  * Arrays: `[ value1, value2 ]`
  * Strings: `"hello"`
  * Numbers: `123`, `4.56`
  * Booleans/Null: `true`, `false`, `null`
* Constraints: Must handle nested structures (e.g., Object inside Array inside Object).

***

#### 2. Core Architecture & Entities

1. TokenType: Enum representing the type of token (L\_BRACE, R\_BRACE, STRING, NUMBER, etc.).
2. Token: A data class holding the `type` and the raw `value`.
3. Lexer (Tokenizer): Scans the input string character by character and produces a stream of Tokens.
4. Parser: Consumes the stream of Tokens. It has specific methods for parsing Objects (`parse_object`) and Arrays (`parse_array`).

***

#### 3. Class Diagram



<figure><img src="../../../.gitbook/assets/image (165).png" alt=""><figcaption></figcaption></figure>

***

#### 4. Python Implementation

#### A. The Setup (Tokens)

We define the building blocks of the JSON language.

Python

```python
from enum import Enum, auto

class TokenType(Enum):
    L_BRACE = auto()    # {
    R_BRACE = auto()    # }
    L_BRACKET = auto()  # [
    R_BRACKET = auto()  # ]
    COLON = auto()      # :
    COMMA = auto()      # ,
    STRING = auto()     # "foo"
    NUMBER = auto()     # 123
    TRUE = auto()       # true
    FALSE = auto()      # false
    NULL = auto()       # null

class Token:
    def __init__(self, type_: TokenType, value: str = None):
        self.type = type_
        self.value = value
    
    def __repr__(self):
        return f"Token({self.type.name}, {self.value})"
```

#### B. The Lexer (Tokenizer)

This class handles the dirty work of reading characters. It skips whitespace and groups characters into tokens.

Python

```python
class Lexer:
    def __init__(self, text):
        self.text = text
        self.pos = 0
        self.length = len(text)

    def tokenize(self):
        tokens = []
        while self.pos < self.length:
            char = self.text[self.pos]

            if char.isspace():
                self.pos += 1
                continue
            
            if char == '{': tokens.append(Token(TokenType.L_BRACE))
            elif char == '}': tokens.append(Token(TokenType.R_BRACE))
            elif char == '[': tokens.append(Token(TokenType.L_BRACKET))
            elif char == ']': tokens.append(Token(TokenType.R_BRACKET))
            elif char == ':': tokens.append(Token(TokenType.COLON))
            elif char == ',': tokens.append(Token(TokenType.COMMA))
            elif char == '"': tokens.append(self._read_string())
            elif char.isdigit() or char == '-': tokens.append(self._read_number())
            elif char.isalpha(): tokens.append(self._read_keyword())
            else:
                raise ValueError(f"Unexpected character: {char}")
            
            # For single-char tokens, advance manually if not handled by helper methods
            if char in "{}[],:":
                self.pos += 1
                
        return tokens

    def _read_string(self):
        # Skip starting quote
        self.pos += 1
        start = self.pos
        while self.pos < self.length and self.text[self.pos] != '"':
            self.pos += 1
        value = self.text[start:self.pos]
        self.pos += 1 # Skip closing quote
        return Token(TokenType.STRING, value)

    def _read_number(self):
        start = self.pos
        while self.pos < self.length and (self.text[self.pos].isdigit() or self.text[self.pos] in '.-'):
            self.pos += 1
        value = self.text[start:self.pos]
        return Token(TokenType.NUMBER, float(value) if '.' in value else int(value))

    def _read_keyword(self):
        start = self.pos
        while self.pos < self.length and self.text[self.pos].isalpha():
            self.pos += 1
        value = self.text[start:self.pos]
        
        if value == 'true': return Token(TokenType.TRUE, True)
        if value == 'false': return Token(TokenType.FALSE, False)
        if value == 'null': return Token(TokenType.NULL, None)
        raise ValueError(f"Unknown keyword: {value}")
```

#### C. The Parser (Recursive Descent)

This uses the tokens to build the structure. The core idea is `parse_value` determines the type, and delegates to `parse_object` or `parse_array` which recursively call `parse_value`.

Python

```python
class Parser:
    def __init__(self, tokens):
        self.tokens = tokens
        self.pos = 0

    def parse(self):
        result = self._parse_value()
        if self.pos < len(self.tokens):
            raise ValueError("Unexpected tokens after JSON data")
        return result

    def _current_token(self):
        if self.pos < len(self.tokens):
            return self.tokens[self.pos]
        raise ValueError("Unexpected End of Input")

    def _consume(self, expected_type=None):
        token = self._current_token()
        if expected_type and token.type != expected_type:
            raise ValueError(f"Expected {expected_type} but found {token.type}")
        self.pos += 1
        return token

    def _parse_value(self):
        token = self._current_token()

        if token.type == TokenType.L_BRACE:
            return self._parse_object()
        elif token.type == TokenType.L_BRACKET:
            return self._parse_array()
        elif token.type == TokenType.STRING:
            return self._consume().value
        elif token.type == TokenType.NUMBER:
            return self._consume().value
        elif token.type == TokenType.TRUE:
            self._consume()
            return True
        elif token.type == TokenType.FALSE:
            self._consume()
            return False
        elif token.type == TokenType.NULL:
            self._consume()
            return None
        else:
            raise ValueError(f"Unexpected token: {token}")

    def _parse_object(self):
        self._consume(TokenType.L_BRACE)
        obj = {}
        
        # Check for empty object
        if self._current_token().type == TokenType.R_BRACE:
            self._consume(TokenType.R_BRACE)
            return obj

        while True:
            # 1. Parse Key
            key_token = self._consume(TokenType.STRING)
            key = key_token.value
            
            # 2. Parse Colon
            self._consume(TokenType.COLON)
            
            # 3. Parse Value (Recursive)
            value = self._parse_value()
            obj[key] = value
            
            # 4. Check for Comma or End
            next_token = self._current_token()
            if next_token.type == TokenType.COMMA:
                self._consume(TokenType.COMMA)
            elif next_token.type == TokenType.R_BRACE:
                self._consume(TokenType.R_BRACE)
                break
            else:
                raise ValueError("Expected comma or }")
        
        return obj

    def _parse_array(self):
        self._consume(TokenType.L_BRACKET)
        arr = []
        
        # Check for empty array
        if self._current_token().type == TokenType.R_BRACKET:
            self._consume(TokenType.R_BRACKET)
            return arr

        while True:
            # 1. Parse Value (Recursive)
            arr.append(self._parse_value())
            
            # 2. Check for Comma or End
            next_token = self._current_token()
            if next_token.type == TokenType.COMMA:
                self._consume(TokenType.COMMA)
            elif next_token.type == TokenType.R_BRACKET:
                self._consume(TokenType.R_BRACKET)
                break
            else:
                raise ValueError("Expected comma or ]")
        
        return arr

# --- Driver Code ---
if __name__ == "__main__":
    json_string = '{"name": "Gemini", "is_ai": true, "versions": [1, 1.5], "meta": {"created": 2023}}'
    
    print(f"Raw String: {json_string}")
    
    # 1. Tokenize
    lexer = Lexer(json_string)
    tokens = lexer.tokenize()
    # print(tokens) # Debug: View tokens
    
    # 2. Parse
    parser = Parser(tokens)
    result = parser.parse()
    
    print(f"Parsed Object: {result}")
    print(f"Accessing Key 'meta': {result['meta']}")
```

***

#### 5. Key Interview Points

#### 1. Why Recursive Descent?

Recursive Descent is chosen because the JSON structure is naturally recursive:

* An Object contains Values.
*   A Value can be an Object.

    This mutual recursion (parse\_object calls parse\_value calls parse\_object) makes the code mirror the grammar definitions almost 1:1, making it readable and easy to debug.

#### 2. Handling Ambiguity

JSON is unambiguous (LL(1) grammar).

* We only need to look at one token to know what to do next.
* If we see `{`, we _know_ it's an object. If we see `[`, we _know_ it's an array. This makes the parser very fast as no backtracking is required.

#### 3. Error Handling

Notice the `_consume(expected_type)` helper.

* This enforces structure. If we are inside an object and see a Key, we must see a Colon next. If not, `consume` raises a clear error immediately.

#### 4. Extensions

* Streaming: For huge JSON files (gigabytes), you cannot load all tokens into memory. You would modify the `Lexer` to be a generator (`yield` tokens one by one) and the `Parser` to consume them lazily.

Would you like to explore "Design a Rate Limiter" (Token Bucket Algorithm) or "Design a URL Shortener" next?

## &#x20;File System&#x20;

Here is the complete Low Level Design (LLD) solution for a File System.

This problem is the textbook example of the Composite Design Pattern, where individual objects (Files) and compositions of objects (Directories) are treated uniformly.

***

#### 1. Requirements Clarification

* Entities:
  * File: Has a name, content (binary/text), size, and metadata.
  * Directory: Has a name and can contain _multiple_ Files or sub-Directories.
* Operations:
  * Traversal: `cd`, `ls` (list files).
  * Manipulation: `mkdir` (create dir), `touch` (create file), `rm` (delete).
  * IO: `write` to file, `read` from file.
* Properties:
  * Hierarchy: Must support nested structures ($$ $Root \rightarrow Folder \rightarrow File$ $$).
  * Metadata: Size, Creation Time, Last Updated Time.

***

#### 2. Core Entities

1. FileSystemEntry (Abstract Class): The common parent. Defines shared attributes (`name`, `created_at`) and abstract methods (`get_size()`).
2. File (Leaf): Represents a concrete file. Implements `get_size()` based on content length.
3. Directory (Composite): Represents a folder. Holds a list of children (`List<FileSystemEntry>`). Implements `get_size()` by recursively summing children's sizes.
4. FileSystem: The orchestrator. Holds the reference to the `Root` directory and manages path resolution (converting `/home/user/docs` into actual objects).

***

#### 3. Class Diagram & Relationships

We use the Composite Pattern: A `Directory` _is an_ `Entry`, but it also _contains_ `Entry` objects.

<figure><img src="../../../.gitbook/assets/image (166).png" alt=""><figcaption></figcaption></figure>

***

#### 4. Python Implementation

#### A. The Base Component (Abstract Class)

This ensures both Files and Directories can be stored in the same list and accessed via the same interface.

Python

```python
from abc import ABC, abstractmethod
import time

class FileSystemEntry(ABC):
    def __init__(self, name):
        self.name = name
        self.created_at = time.time()
        self.updated_at = time.time()
        self.parent = None # Reference to parent directory

    @abstractmethod
    def get_size(self):
        pass

    def get_path(self):
        # Recursive backtracking to build path: /home/user/file.txt
        if self.parent is None:
            return "/" + self.name
        return self.parent.get_path() + "/" + self.name
```

#### B. The Leaf (File)

Implements size based on data content.

Python

```python
class File(FileSystemEntry):
    def __init__(self, name, content=""):
        super().__init__(name)
        self.content = content

    def get_size(self):
        # Size is the length of the content
        return len(self.content)

    def read(self):
        return self.content

    def write(self, new_content):
        self.content = new_content
        self.updated_at = time.time()
```

#### C. The Composite (Directory)

Implements size based on recursion. Holds a collection of children.

Python

```python
class Directory(FileSystemEntry):
    def __init__(self, name):
        super().__init__(name)
        self.children = [] # List of FileSystemEntry (can be File or Directory)

    def add_entry(self, entry):
        entry.parent = self
        self.children.append(entry)

    def remove_entry(self, entry):
        if entry in self.children:
            self.children.remove(entry)

    def get_size(self):
        # Recursively calculate size of all children
        total_size = 0
        for entry in self.children:
            total_size += entry.get_size()
        return total_size

    def get_child(self, name):
        for entry in self.children:
            if entry.name == name:
                return entry
        return None
    
    def list_contents(self):
        print(f"Contents of {self.name}:")
        for entry in self.children:
            type_ = "DIR" if isinstance(entry, Directory) else "FILE"
            print(f"  [{type_}] {entry.name} - {entry.get_size()} bytes")
```

#### D. The Orchestrator (File System)

Handles path parsing (string $$ $\rightarrow$ $$ object).

Python

```python
class FileSystem:
    def __init__(self):
        self.root = Directory("root")

    def _resolve_path(self, path):
        # Helper: Converts "/home/user" -> Directory Object
        if path == "/":
            return self.root
        
        parts = path.strip("/").split("/")
        current = self.root
        
        for part in parts:
            next_node = current.get_child(part)
            if next_node is None or not isinstance(next_node, Directory):
                raise Exception(f"Invalid path: directory '{part}' not found")
            current = next_node
        return current

    def mkdir(self, path):
        # Create directory at path. e.g. path="/home", name="user"
        parent_path, new_dir_name = path.rsplit("/", 1)
        parent_path = parent_path or "/" # Handle root case
        
        parent_dir = self._resolve_path(parent_path)
        new_dir = Directory(new_dir_name)
        parent_dir.add_entry(new_dir)
        print(f"Directory created: {path}")

    def touch(self, path, content=""):
        # Create file
        parent_path, file_name = path.rsplit("/", 1)
        parent_path = parent_path or "/"
        
        parent_dir = self._resolve_path(parent_path)
        new_file = File(file_name, content)
        parent_dir.add_entry(new_file)
        print(f"File created: {path}")

    def ls(self, path):
        target_dir = self._resolve_path(path)
        target_dir.list_contents()

# --- Driver Code ---
if __name__ == "__main__":
    fs = FileSystem()
    
    # 1. Setup Structure
    fs.mkdir("/home")
    fs.mkdir("/home/user")
    
    # 2. Add Files
    fs.touch("/home/user/notes.txt", "Hello World") # Size 11
    fs.touch("/home/user/photo.png", "binarydata")  # Size 10
    
    # 3. Operations
    fs.ls("/home/user")
    
    # 4. Recursive Size Check
    root = fs._resolve_path("/")
    print(f"Total Disk Usage: {root.get_size()} bytes") # Should be 11 + 10 = 21
```

***

#### 5. Key Interview Points

#### 1. The Composite Pattern

* Why? The interviewer is testing if you know how to treat a collection of objects (`Directory`) the same way as a single object (`File`).
* Proof: Both inherit from `FileSystemEntry`. You can call `get_size()` on the root folder, and it cascades down gracefully without knowing whether the children are files or folders.

#### 2. Path Resolution

* The logic inside `_resolve_path` (splitting string by `/` and traversing nodes) is critical. It shows you understand tree traversal.

#### 3. Circular Dependencies

* Question: What if I create a symlink (shortcut) that points to a parent directory, creating an infinite loop for `get_size()`?
* Answer: In a simple LLD, we assume a Tree structure (DAG). In a real OS (Graph structure), `get_size()` would need a `Set<VisitedNode>` to prevent infinite recursion.

#### 4. Search Functionality

*   You can easily add a recursive search method to `Directory`:

    Python

    ```python
    def search(self, keyword):
        results = []
        if keyword in self.name: results.append(self.get_path())
        if isinstance(self, Directory):
            for child in self.children:
                results.extend(child.search(keyword))
        return results
    ```

Would you like to explore "Design a Cache System (LRU/LFU)" next?

## 2048 &#x20;

An end-to-end Low Level Design (LLD) solution for the 2048 Game.

***

#### 1. Requirements Clarification

* Board: A 4x4 grid.
* Tiles: Numbered tiles (starting with 2 or 4).
* Movement:
  * Players swipe Left, Right, Up, or Down.
  * All tiles slide as far as possible in that direction.
  * Merge Logic: If two tiles of the same number collide while moving, they merge into one tile with double the value (e.g., $$ $2+2 \rightarrow 4$ $$).
  * A merge can only happen once per tile per move.
* Random Spawn: After every valid move, a new tile (2 or 4) spawns in a random empty spot.
* Game Over: No empty spots and no adjacent tiles with the same value (no moves possible).
* Win Condition: A tile with value 2048 is created.

***

#### 2. Core Entities

1. Tile: Represents a single cell. Holds the `value` (int). Can check if it is empty.
2. Board: The 4x4 grid. Handles the complex logic of Sliding and Merging.
3. Game: The controller. Manages input, score, game status (Won/Lost), and the game loop.
4. Direction (Enum): UP, DOWN, LEFT, RIGHT.

***

#### 3. Class Diagram

<figure><img src="../../../.gitbook/assets/image (167).png" alt=""><figcaption></figcaption></figure>

***

#### 4. Python Implementation

#### A. The Tile Class

Simple wrapper for the number.

Python

```python
class Tile:
    def __init__(self, value=0):
        self.value = value

    def is_empty(self):
        return self.value == 0
    
    def __repr__(self):
        return str(self.value) if self.value > 0 else "."
```

#### B. The Board (The Core Algorithm)

This is where the interview difficulty lies: implementing the Shift & Merge logic correctly.

Strategy:

Instead of writing 4 different functions for Up/Down/Left/Right, we can normalize the problem:

1. Left/Right: Process rows.
2. Up/Down: Transpose the grid (swap rows and cols), process as rows, then transpose back.
3. Reverse: For Right/Down, reverse the row, process as Left, then reverse back.

This reduces the problem to writing one robust function: `merge_left(row)`.

Python

```python
import random

class Direction:
    UP = 'UP'
    DOWN = 'DOWN'
    LEFT = 'LEFT'
    RIGHT = 'RIGHT'

class Board:
    def __init__(self, size=4):
        self.size = size
        self.grid = [[Tile(0) for _ in range(size)] for _ in range(size)]
        self.spawn_random_tile()
        self.spawn_random_tile()

    def spawn_random_tile(self):
        empty_cells = [(r, c) for r in range(self.size) for c in range(self.size) if self.grid[r][c].is_empty()]
        if not empty_cells:
            return
        r, c = random.choice(empty_cells)
        # 90% chance of 2, 10% chance of 4
        self.grid[r][c].value = 4 if random.random() > 0.9 else 2

    def move(self, direction):
        # 1. Orient the board so we always slide LEFT
        if direction == Direction.UP:
            self._transpose()
            self._slide_left() # Effectively sliding Up
            self._transpose()
        elif direction == Direction.DOWN:
            self._transpose()
            self._reverse_rows()
            self._slide_left() # Effectively sliding Down
            self._reverse_rows()
            self._transpose()
        elif direction == Direction.LEFT:
            self._slide_left()
        elif direction == Direction.RIGHT:
            self._reverse_rows()
            self._slide_left() # Effectively sliding Right
            self._reverse_rows()
            
        # Note: A real implementation tracks if the board actually *changed*
        # before spawning a new tile.
        self.spawn_random_tile()

    def _slide_left(self):
        # Apply shift-merge logic to every row
        for r in range(self.size):
            new_row = self._merge_row(self.grid[r])
            self.grid[r] = new_row

    def _merge_row(self, row):
        # 1. Filter out empty tiles
        non_empty = [t for t in row if not t.is_empty()]
        
        merged = []
        skip = False
        
        # 2. Merge logic
        for i in range(len(non_empty)):
            if skip:
                skip = False
                continue
                
            # If current matches next, merge
            if i + 1 < len(non_empty) and non_empty[i].value == non_empty[i+1].value:
                merged_val = non_empty[i].value * 2
                merged.append(Tile(merged_val))
                skip = True # Skip next tile as it got merged
            else:
                merged.append(non_empty[i])
        
        # 3. Fill the rest with empty tiles
        while len(merged) < self.size:
            merged.append(Tile(0))
            
        return merged

    # Helper: Swap rows and columns (Matrix Transpose)
    def _transpose(self):
        self.grid = [list(row) for row in zip(*self.grid)]

    # Helper: Reverse each row
    def _reverse_rows(self):
        for r in range(self.size):
            self.grid[r] = self.grid[r][::-1]

    def is_game_over(self):
        # Check for empty cells
        for r in range(self.size):
            for c in range(self.size):
                if self.grid[r][c].is_empty():
                    return False
        
        # Check for adjacent matches (Horizontal)
        for r in range(self.size):
            for c in range(self.size - 1):
                if self.grid[r][c].value == self.grid[r][c+1].value:
                    return False
                    
        # Check for adjacent matches (Vertical)
        for r in range(self.size - 1):
            for c in range(self.size):
                if self.grid[r][c].value == self.grid[r+1][c].value:
                    return False
                    
        return True

    def print_board(self):
        print("-" * 20)
        for row in self.grid:
            print("\t".join(str(t) for t in row))
        print("-" * 20)
```

#### C. The Game Controller

Python

```python
class Game2048:
    def __init__(self):
        self.board = Board()
        self.game_over = False
        self.score = 0

    def play(self):
        print("Use commands: w(Up), s(Down), a(Left), d(Right), q(Quit)")
        self.board.print_board()
        
        while not self.game_over:
            cmd = input("Move: ").strip().lower()
            
            if cmd == 'q':
                break
                
            direction = None
            if cmd == 'w': direction = Direction.UP
            elif cmd == 's': direction = Direction.DOWN
            elif cmd == 'a': direction = Direction.LEFT
            elif cmd == 'd': direction = Direction.RIGHT
            
            if direction:
                self.board.move(direction)
                self.board.print_board()
                
                if self.board.is_game_over():
                    print("Game Over!")
                    self.game_over = True
```

***

#### 5. Key Design Decisions (Interview Talking Points)

#### 1. Matrix Manipulation Strategy

* Problem: Writing 4 separate loops for Up/Down/Left/Right is prone to bugs and code duplication.
* Solution: I used a "Canonical" approach.
  * I wrote one logic: `slide_left`.
  * To slide RIGHT: I reverse the row, slide left, and reverse back.
  * To slide UP: I transpose the matrix, slide left, and transpose back.
  * This is a cleaner, more mathematical approach that impresses interviewers.

#### 2. Merge Logic (Greedy Approach)

* Detail: When merging `[2, 2, 2, 2]`, does it become `[4, 4, 0, 0]` or `[8, 0, 0, 0]`?
* Rule: Standard 2048 rules say `[4, 4]`. Each tile can only merge once per turn.
* Implementation: I used a `skip` flag in the loop. If index `i` merges with `i+1`, I append the merged result and force the loop to skip `i+1`.

#### 3. Check Game Over

* Edge Case: The board is full, but the game isn't over if adjacent tiles are equal (e.g., `[2, 2]`).
* Logic: The `is_game_over` function checks:
  1. Are there any zeros? (If yes, continue).
  2. Are there any horizontal neighbors equal?
  3. Are there any vertical neighbors equal?

#### 4. Separation of Concerns

* Board vs Tile: The `Tile` is dumb; it just holds a number. The `Board` is smart; it knows grid geometry.
* Board vs Game: The `Board` handles matrix math. The `Game` handles input parsing and the `while` loop.

Would you like to move on to "Design a Parking Lot" or "Design a Vending Machine"?
