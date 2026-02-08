# Design Tic-Tac-Toe

> **Difficulty**: Beginner  
> **Topics**: Object-Oriented Design, Board Game Logic, 2D Arrays  
> **Extension**: NxN Board, AI Player (Minimax).

## Problem Statement

Design a simple 2-player Tic-Tac-Toe game.
- **Board**: 3x3 Grid.
- **Pieces**: X and O.
- **Rules**: Take turns. First to get row, col, or diagonal wins.

## Class Design

### 1. Piece (Enum & Class)

```python
from enum import Enum

class PieceType(Enum):
    X = 1
    O = 2

class PlayingPiece:
    def __init__(self, piece_type):
        self.piece_type = piece_type

class PlayingPieceX(PlayingPiece):
    def __init__(self): super().__init__(PieceType.X)

class PlayingPieceO(PlayingPiece):
    def __init__(self): super().__init__(PieceType.O)
```

### 2. Board

```python
class Board:
    def __init__(self, size):
        self.size = size
        self.grid = [[None for _ in range(size)] for _ in range(size)]

    def add_piece(self, row, col, piece):
        if self.grid[row][col] is not None:
            return False
        self.grid[row][col] = piece
        return True

    def print_board(self):
        # Print grid logic...
        pass
```

### 3. Game Orchestrator (Controller)

```python
from collections import deque

class TicTacToeGame:
    def __init__(self, size=3):
        self.board = Board(size)
        self.players = deque()
        self.players.append(Player("P1", PlayingPieceX()))
        self.players.append(Player("P2", PlayingPieceO()))

    def start_game(self):
        no_winner = True
        while no_winner:
            player = self.players.popleft()
            
            # Input handling (row, col)
            row, col = map(int, input(f"{player.name}'s Turn: ").split(','))
            
            if not self.board.add_piece(row, col, player.playing_piece):
                print("Invalid Move")
                self.players.appendleft(player)
                continue
            
            if self.check_winner(row, col, player.playing_piece.piece_type):
                print(f"{player.name} Wins!")
                return
            
            self.players.append(player)

    def check_winner(self, row, col, piece_type):
        # Check Row, Col, Diagonals for piece_type match
        pass
```

## Interview Q&A

**Q: "How to scale to NxN board?"**
- A: "Logic remains same. The `check_winner` function iterates `range(N)` instead of hardcoded 3."

**Q: "How to add Undo feature?"**
- A: "Use Command Pattern or store moves in a Stack `[(row, col, player)]`. `Undo` pops stack and sets `grid[row][col] = None`."
