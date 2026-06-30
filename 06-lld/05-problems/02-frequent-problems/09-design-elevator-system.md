> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design Elevator System — a classic problem testing State machines, scheduling algorithms, and concurrent request handling.
>
> **Key concepts:**
> - Core Entities: `ElevatorSystem` (orchestrator), `ElevatorCar`, `Button` (Internal/External), `Display`.
> - State Pattern: `ElevatorState` (MovingUp, MovingDown, Idle, DoorOpen).
> - The Algorithm: SCAN (or LOOK) algorithm. The elevator maintains two min/max heaps or sorted sets: one for upward requests, one for downward requests. It sweeps fully up, then fully down.
> - Strategy Pattern: the `ElevatorDispatchStrategy` decides *which* car gets a request (e.g., shortest wait time, nearest car moving in the same direction).
> - Concurrency: Requests arrive asynchronously from different floors. The request queues must be thread-safe (e.g., `PriorityBlockingQueue`).
>
> **Key takeaway:** Do not use a basic FIFO queue for elevator requests, or the elevator will bounce erratically. You must mention the SCAN/LOOK directional sweep algorithm and use sorted data structures.

---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, elevator, state-machine, strategy, observer]
---
# Design Elevator System

> **Difficulty**: Hard
> **Asked at**: Amazon, Microsoft, Uber
> **Key Patterns**: State (elevator state), Strategy (scheduling), Observer (floor requests)

---

## Understanding the Problem

Design an elevator control system for a building with multiple elevators, handling internal (cabin) and external (floor) button requests, and scheduling elevators efficiently.

---

## Clarifying Questions

**You**: "How many elevators and floors are we targeting?"
**Interviewer**: "Configurable — let's say up to 10 elevators and 20 floors."

**You**: "Are there two types of requests — pressing a button inside the elevator, and pressing up/down on a floor?"
**Interviewer**: "Yes, both."

**You**: "Should we implement a specific scheduling algorithm?"
**Interviewer**: "Implement SCAN (elevator sweeps up then down). Discuss alternatives."

**You**: "Does the system need to be real-time or step-based?"
**Interviewer**: "Step-based — each call to move_step() advances all elevators by one floor."

**You**: "Do we need emergency mode or maintenance?"
**Interviewer**: "Discuss as a follow-up."

---

## Final Requirements

**In scope:**
1. Multiple elevators, each with an independent state (IDLE, MOVING_UP, MOVING_DOWN)
2. External requests: floor + direction (UP/DOWN)
3. Internal requests: destination floor from inside elevator
4. SCAN scheduling: elevator serves floors in current direction, then reverses
5. Step-based simulation: `move_step()` advances each elevator one floor

**Out of scope:**
- Real-time async movement
- Emergency/VIP floors (follow-up)
- Maintenance mode (follow-up)
- Weight sensors

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|---------------|
| `ElevatorController` | Accepts requests, assigns to best elevator, drives simulation |
| `Elevator` | Tracks floor, state, and destination queue; moves one step at a time |
| `ElevatorState` | Enum: IDLE, MOVING_UP, MOVING_DOWN |
| `FloorRequest` | External request: (floor, direction) |
| `SchedulingStrategy` | Abstract: assigns a request to the best elevator |
| `SCANStrategy` | Concrete: nearest elevator moving in same direction or idle |
| `Direction` | Enum: UP, DOWN |

`ElevatorController` holds all elevators and the scheduling strategy. On a floor button press, it calls `strategy.assign(request, elevators)` to pick the best elevator and adds the destination to that elevator's queue.

---

## Class Design

### Elevator

| Requirement | What Elevator must track |
|-------------|-------------------------|
| "Independent state per elevator" | current_floor, state: ElevatorState |
| "SCAN — serve in direction then reverse" | destinations: sorted list; direction |
| "Internal button press" | destinations (same set — just a floor number) |

```
class Elevator:
- id: int
- current_floor: int
- state: ElevatorState
- destinations: SortedList[int]

+ add_destination(floor: int)
+ move_step()
+ get_next_destination() -> Optional[int]
+ is_idle() -> bool
+ get_current_floor() -> int
+ get_state() -> ElevatorState
```

### ElevatorController

```
class ElevatorController:
- elevators: list[Elevator]
- strategy: SchedulingStrategy

+ request_floor(floor: int, direction: Direction)   # external button
+ select_floor(elevator_id: int, floor: int)        # internal button
+ move_step()                                        # advance all elevators one floor
+ get_status() -> list[ElevatorStatus]
```

### SchedulingStrategy

```
class SchedulingStrategy (abstract):
+ assign(request: FloorRequest, elevators: list[Elevator]) -> Elevator

class SCANStrategy(SchedulingStrategy):
+ assign(request, elevators) -> Elevator
```

---

## Implementation

### Core Method: `Elevator.move_step`

**Core logic:**
1. If no destinations → stay IDLE
2. Get next destination based on current direction
3. Move one floor toward it
4. If arrived → remove destination, update state based on remaining queue

**Edge cases:**
- Empty destination queue → set IDLE
- Arrive at destination → remove it, check remaining direction

```python
def move_step(self):
    if not self.destinations:
        self.state = ElevatorState.IDLE
        return

    next_dest = self.get_next_destination()

    if self.current_floor < next_dest:
        self.current_floor += 1
        self.state = ElevatorState.MOVING_UP
    elif self.current_floor > next_dest:
        self.current_floor -= 1
        self.state = ElevatorState.MOVING_DOWN

    if self.current_floor == next_dest:
        self.destinations.remove(next_dest)
        if not self.destinations:
            self.state = ElevatorState.IDLE
        elif min(self.destinations) > self.current_floor:
            self.state = ElevatorState.MOVING_UP
        else:
            self.state = ElevatorState.MOVING_DOWN

def get_next_destination(self):
    if self.state in (ElevatorState.MOVING_UP, ElevatorState.IDLE):
        above = [d for d in self.destinations if d >= self.current_floor]
        return min(above) if above else max(self.destinations)
    else:
        below = [d for d in self.destinations if d <= self.current_floor]
        return max(below) if below else min(self.destinations)
```

### Core Method: `SCANStrategy.assign`

```python
def assign(self, request, elevators):
    best = None
    best_score = float('inf')
    for elevator in elevators:
        score = self._score(elevator, request)
        if score < best_score:
            best_score = score
            best = elevator
    return best

def _score(self, elevator, request):
    distance = abs(elevator.current_floor - request.floor)
    # Same direction, elevator will pass through request floor
    if (elevator.state == ElevatorState.MOVING_UP
            and request.direction == Direction.UP
            and elevator.current_floor <= request.floor):
        return distance
    if (elevator.state == ElevatorState.MOVING_DOWN
            and request.direction == Direction.DOWN
            and elevator.current_floor >= request.floor):
        return distance
    if elevator.state == ElevatorState.IDLE:
        return distance + 1
    return distance + 100  # wrong direction — last resort
```

---

## Verification

```
Building: 2 elevators (E1 at floor 1, E2 at floor 5), 10 floors

Request 1: Floor 3, UP (external)
  SCANStrategy: E1 IDLE distance=2 score=3, E2 distance=2 wrong direction score=102
  → E1 assigned. E1.destinations = [3]

Request 2: Inside E1, press floor 7
  E1.destinations = [3, 7]

move_step() ×2:
  E1: floor 1→2→3 (arrives at 3, remove)
  E1.destinations = [7], state = MOVING_UP

move_step() ×4:
  E1: 3→4→5→6→7 (arrives at 7)
  E1.destinations = [], state = IDLE

Request 3: Floor 9, DOWN
  E2 IDLE at 5, distance=4, score=5
  E1 IDLE at 7, distance=2, score=3
  → E1 assigned (lower score)
```

---

## Deep Dive & Extensibility

### 1. "SCAN vs LOOK — what's the difference?"

**SCAN**: Goes all the way to the top/bottom floor before reversing, even if no requests there.
**LOOK**: Reverses as soon as there are no more requests in the current direction.

LOOK is more efficient — no wasted travel to extreme floors.

```python
# LOOK: reverse when no more destinations in current direction
def get_next_destination_look(self):
    if self.state == ElevatorState.MOVING_UP:
        above = [d for d in self.destinations if d > self.current_floor]
        return min(above) if above else max(self.destinations)  # reverse
    else:
        below = [d for d in self.destinations if d < self.current_floor]
        return max(below) if below else min(self.destinations)  # reverse
```

Since `SchedulingStrategy` is injectable, swapping from SCAN to LOOK requires zero changes to `ElevatorController` or `Elevator`.

### 2. "How would you add emergency mode?"

Add `EMERGENCY` to `ElevatorState`. In `ElevatorController`:

```python
def trigger_emergency(self):
    for elevator in self.elevators:
        elevator.state = ElevatorState.EMERGENCY
        elevator.destinations.clear()
        elevator.add_destination(0)  # ground floor
```

In `move_step`, when state is EMERGENCY, always move toward floor 0. External requests are rejected during emergency.

### 3. "How would you handle maintenance mode?"

Add `MAINTENANCE` flag. `assign()` skips maintenance elevators. After the current destination queue drains, the elevator parks and awaits service.

```python
def set_maintenance(self, elevator_id):
    elevator = self._get_elevator(elevator_id)
    elevator.maintenance_pending = True
    # assign() checks: if elevator.maintenance_pending → skip
    # After destinations drain, move_step sets state = MAINTENANCE
```

### 4. "What about starvation?"

SCAN can starve a floor if traffic is continuously heavy in one direction. Solutions:

- **Age-based priority boost**: requests older than threshold T get score = 0 (always served next)
- **C-SCAN**: elevator always moves one direction only, jumps to bottom on reversal — uniform wait times

```python
def _score(self, elevator, request):
    base_score = self._base_score(elevator, request)
    age = time.time() - request.timestamp
    urgency_bonus = max(0, age - STARVATION_THRESHOLD) * 10
    return base_score - urgency_bonus
```

### 5. "How do you make this thread-safe?"

One lock per elevator — no global lock needed:

```python
class Elevator:
    def __init__(self):
        self._lock = threading.Lock()

    def add_destination(self, floor):
        with self._lock:
            if floor not in self.destinations:
                self.destinations.add(floor)

    def move_step(self):
        with self._lock:
            # ... existing logic ...
```

`ElevatorController.assign()` reads elevator snapshots (floor + state) under each elevator's lock. Avoids a single global bottleneck.

---

## Interviewer Questions by Level

**Junior**: Single elevator with destination list. Move step by step. Handle internal button presses. May hardcode direction logic.

**Mid-level**: Multiple elevators. `SchedulingStrategy` abstraction. SCAN logic correct (serves in direction, then reverses). External vs internal requests distinguished.

**Senior**: SCAN vs LOOK trade-off stated. `_score()` formula justified. Starvation discussed with concrete fix. Thread-safety per-elevator lock explained. Emergency mode extension requires only state addition.

---

## Common Interview Questions

- **Q**: What is the SCAN algorithm?
  **A**: The elevator sweeps bottom to top serving all requests, then reverses and sweeps top to bottom. Similar to a disk arm. Prevents indefinite waits but can cause uneven wait times near reversal points.

- **Q**: How do you assign a request to the best elevator?
  **A**: Score each elevator: same-direction passing through request floor → score = distance. Idle → distance + 1. Wrong direction → distance + 100. Pick lowest score.

- **Q**: What is starvation and how do you prevent it?
  **A**: A floor button press never served because elevators continuously get new requests in the other direction before completing a sweep. Fix: age-based priority boost after threshold T.

- **Q**: Why step-based simulation rather than real-time threads?
  **A**: Simpler, deterministic, testable. Real systems use timers; for an interview, step-based is the right scope.

- **Q**: How do you prevent duplicate assignment of the same request?
  **A**: `assign()` picks exactly one elevator and adds the destination only to that one. The request is marked assigned before returning.
