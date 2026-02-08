# #18 tic tac toe game

Here’s a complete, time-boxed, 1-hour interview-ready answer for designing a Tic-Tac-Toe game system (can be extended to online multiplayer). It follows your usual system design interview structure, including functional & non-functional requirements, APIs/data model, architecture, deep dive, and trade-offs.

***

## 0 – 5 min — Problem recap, scope & assumptions

<br>

Goal: Design a Tic-Tac-Toe game system that allows two players to play a match in real-time or asynchronously. The system should handle multiple concurrent games, maintain game state, enforce game rules, and allow players to view history and statistics.

<br>

Scope for interview:

* Game logic and rule enforcement.
* Multiplayer support (real-time vs turn-based).
* Player matchmaking and session management.
* Persistence of game history and leaderboard.
* Optional: scalability for large number of concurrent games.

<br>

Assumptions:

* Board size: 3x3.
* Two players per game.
* Real-time gameplay via web or mobile client.
* Target concurrent users: 10k–100k (small for scaling exercise).
* Support asynchronous games where players take turns without being online simultaneously.

***

## 5 – 15 min — Functional & Non-Functional Requirements

<br>

#### Functional Requirements

<br>

Must

1. Game creation: Player can create a new game session.
2. Player matchmaking: Invite friend or match with random opponent.
3. Turn-based moves: Enforce alternating moves; prevent illegal moves.
4. Win/Draw detection: Check for win conditions or draw.
5. Game state management: Maintain current board, moves history, current turn.
6. Player notifications: Notify opponent when a move is made.
7. Game history: Persist past games and results.

<br>

Should

* Allow spectators to watch games.
* Support rematch requests.
* Provide in-game chat or emojis.

<br>

Nice-to-have

* Leaderboard and ranking based on wins/losses.
* Achievements or statistics (win streaks, fastest wins).
* Support multiple board sizes (NxN).

***

#### Non-Functional Requirements

* Availability: System should be highly available; games shouldn’t be interrupted.
* Latency: Real-time moves latency < 200ms.
* Scalability: Handle thousands of concurrent games.
* Durability: Game state and history must be reliably persisted.
* Consistency: Enforce strong consistency for moves (no two moves on same cell).
* Security: Authenticate players; prevent cheating.
* Monitoring: Track active games, errors, latency.

***

## 15 – 25 min — API / Data Model

<br>

#### APIs

<br>

Game Management

```
create_game(player1_id, player2_id=None) -> game_id
join_game(game_id, player_id) -> status
make_move(game_id, player_id, x, y) -> {status, next_turn, winner}
get_game_state(game_id) -> {board, moves, current_turn, status}
get_game_history(player_id) -> [game_summary]
```

Player Management

```
register_player(name, credentials)
get_leaderboard() -> [player_rankings]
```

#### Data Models

<br>

Game

```
{
  "game_id": "string",
  "players": ["player1_id", "player2_id"],
  "board": [[null,null,null],[null,null,null],[null,null,null]],
  "moves": [{"player_id": "x", "position": [0,1], "timestamp": "ts"}],
  "current_turn": "player_id",
  "status": "ONGOING/WON/DRAW",
  "winner": "player_id/null"
}
```

Player

```
{
  "player_id": "string",
  "name": "string",
  "stats": {"wins": int, "losses": int, "draws": int},
  "games": [game_id]
}
```

Leaderboard

```
{
  "player_id": "string",
  "rank": int,
  "wins": int
}
```

***

## 25 – 40 min — High-level architecture & data flow

```
[Client 1]             [Client 2]
   |                        |
   | WebSocket/HTTP API     |
   v                        v
+------------------------+
| Game Service           |
| - Game Logic           |
| - Move Validation      |
| - Winner/Draw Check    |
+------------------------+
          |
          v
+------------------------+
| Storage / DB           |
| - Game State           |
| - Move History         |
| - Player Stats         |
+------------------------+
          |
          v
+------------------------+
| Notification Service   |
| - WebSocket / Push     |
| - Opponent updates     |
+------------------------+
```

Components

1. Client: Web or mobile interface; displays board, sends moves, receives updates.
2. Game Service: Core game logic, rule enforcement, winner/draw detection, turn validation.
3. Storage / DB: Persist game state, history, and player stats (can use SQL or NoSQL).
4. Notification Service: Push updates to opponent or spectator in real-time (WebSocket, Pub/Sub).
5. Matchmaking Service: Optional service to pair players automatically.

<br>

Data flow example

1. Player1 creates game → Game Service creates new Game in DB.
2. Player2 joins → Game status updated to ONGOING.
3. Player1 makes a move → validated → board updated → move stored in DB → Notification Service informs Player2.
4. Game continues until WIN or DRAW → status updated, stats updated.

***

## 40 – 50 min — Deep dive — consistency, real-time updates, scaling

<br>

#### Consistency

* Use strong consistency for game state (DB transaction or in-memory lock per game).
* Prevent race conditions: only current\_turn player can make a move.

<br>

#### Real-time updates

* Use WebSocket or server-sent events to push moves to opponent instantly.
* For asynchronous games, store notifications and deliver when opponent comes online.

<br>

#### Scaling

* Horizontal scaling by sharding games by game\_id or player\_id.
* Cache active games in memory (Redis / in-memory store) for fast access.
* Background workers for updating stats, leaderboard, and archiving old games.

<br>

#### Fault tolerance

* Persist every move immediately.
* On service crash, reload active games from DB.
* Optional checkpointing for long-running matches.

***

## 50 – 55 min — Back-of-the-envelope calculations

<br>

Assumptions

* 10k concurrent games, each sending 1 move/min → \~167 moves/sec.
* Each move: \~200 bytes → 33 KB/sec → \~2.85 GB/day if stored long-term.
* Active games cache: \~1 KB per move x 10k games → 10 MB in memory (very small).
* DB: SQL or NoSQL sufficient for this scale.

<br>

Latency

* Real-time move delivery: < 200 ms achievable with WebSocket and in-memory caching.

<br>

Storage

* Persist all moves in DB for history, archive old games to long-term storage if needed.

***

## 55 – 58 min — Monitoring & ops

<br>

Monitoring

* Active games count.
* Move processing latency.
* DB errors / failed writes.
* Notification failures.

<br>

Operational playbooks

* Recover in-progress games after service crash.
* Handle network disconnects (reconnect client & update board state).
* Manage leaderboard and stats update failures.

***

## 58 – 60 min — Trade-offs, evolution & summary

<br>

#### Trade-offs

* Real-time vs async: Real-time requires WebSockets, faster updates; async simpler but may delay moves.
* In-memory vs DB-first: In-memory cache improves latency; DB persistence ensures durability.
* SQL vs NoSQL: SQL easier for joins (stats, leaderboard); NoSQL simpler for sharding and high write throughput.

<br>

#### Evolution

1. MVP: simple 3x3 game, two players, real-time moves, basic stats.
2. Phase 2: add matchmaking, spectator mode, chat, rematch.
3. Phase 3: leaderboard, achievements, multi-board sizes, AI opponent.

<br>

#### Summary

<br>

Build a scalable, real-time Tic-Tac-Toe system:

* Game Service enforces rules and stores moves.
* DB persists state and stats.
* Notification service updates players in real-time.
* System handles thousands of concurrent games with low latency and ensures consistency and durability.

***

If you want, I can next produce a sequence diagram for a single Tic-Tac-Toe game flow, showing game creation → move → state update → notification.

<br>

Do you want me to create that?
