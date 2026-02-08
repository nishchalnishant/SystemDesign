# Intermediate

## Internet Download Manager



An end-to-end Low Level Design (LLD) for an Internet Download Manager (IDM).

This problem tests your understanding of Concurrency (Multi-threading), Network Protocols (HTTP Range), and File I/O.

***

#### 1. Requirements Clarification

* Core Functionality: Download files from a URL.
* Acceleration: Split the file into multiple parts (segments) and download them in parallel.
* Resumability: Ability to pause and resume downloads (persisting state).
* Assembly: Merge segments into the final file upon completion.
* Status Tracking: Pending, Downloading, Paused, Completed, Failed.

***

#### 2. Core Concept: HTTP Range Headers

The magic of IDM lies in the HTTP protocol.

1. Head Request: We first send a `HEAD` request to check the file size (`Content-Length`) and if the server supports parallel downloads (`Accept-Ranges: bytes`).
2. Range Request: Workers send `GET` requests with specific byte ranges.
   * _Worker 1:_ `Range: bytes=0-1000`
   * _Worker 2:_ `Range: bytes=1001-2000`

***

#### 3. Core Entities

1. DownloadTask: Represents the file being downloaded. Holds metadata (URL, file size, status).
2. Segment (Chunk): Represents a specific byte range (Start, End) and its download status.
3. Worker (Thread): A separate thread responsible for downloading one Segment.
4. DownloadManager: The Controller. Starts tasks, manages the thread pool, and handles file assembly.

***

#### 4. Class Diagram

<figure><img src="../../../.gitbook/assets/image (168).png" alt=""><figcaption></figcaption></figure>

***

#### 5. Python Implementation

#### A. The Segment and Task

These classes hold the data state.

Python

```python
import os
import requests
import threading
from enum import Enum

class DownloadStatus(Enum):
    PENDING = 1
    DOWNLOADING = 2
    PAUSED = 3
    COMPLETED = 4
    FAILED = 5

class Segment:
    def __init__(self, id, start, end, temp_path):
        self.id = id
        self.start = start
        self.end = end
        self.downloaded = 0
        self.is_completed = False
        self.temp_path = temp_path

class DownloadTask:
    def __init__(self, url, output_path, num_threads=4):
        self.url = url
        self.output_path = output_path
        self.num_threads = num_threads
        self.segments = []
        self.status = DownloadStatus.PENDING
        self.file_size = 0
        self.lock = threading.Lock() # For safe status updates

    def initialize(self):
        # 1. Head Request to get Size
        response = requests.head(self.url)
        self.file_size = int(response.headers.get('content-length', 0))
        accept_ranges = response.headers.get('accept-ranges', 'none')

        if self.file_size == 0:
            raise Exception("Invalid File Size")

        # 2. Calculate Segment Sizes
        print(f"File Size: {self.file_size} bytes. Ranges Supported: {accept_ranges}")
        
        # If server doesn't support ranges, use 1 segment
        if accept_ranges == 'none':
            self.segments.append(Segment(0, 0, self.file_size, f"{self.output_path}.part0"))
        else:
            chunk_size = self.file_size // self.num_threads
            for i in range(self.num_threads):
                start = i * chunk_size
                # Last chunk takes the remainder
                end = self.file_size if i == self.num_threads - 1 else (start + chunk_size)
                temp_path = f"{self.output_path}.part{i}"
                self.segments.append(Segment(i, start, end, temp_path))

    def merge_files(self):
        print("Merging segments...")
        with open(self.output_path, 'wb') as final_file:
            for segment in self.segments:
                with open(segment.temp_path, 'rb') as part_file:
                    final_file.write(part_file.read())
                # Cleanup temp file
                os.remove(segment.temp_path)
        self.status = DownloadStatus.COMPLETED
        print("Download Complete!")
```

#### B. The Worker (Thread)

This performs the actual HTTP Request with the Range header.

Python

```python
class ChunkDownloader(threading.Thread):
    def __init__(self, url, segment):
        super().__init__()
        self.url = url
        self.segment = segment

    def run(self):
        if self.segment.is_completed:
            return

        # Core Logic: HTTP Range Header
        # We allow resuming by adding 'downloaded' offset to 'start'
        current_start = self.segment.start + self.segment.downloaded
        headers = {'Range': f'bytes={current_start}-{self.segment.end}'}
        
        print(f"Thread-{self.segment.id} requesting bytes {current_start}-{self.segment.end}")
        
        try:
            with requests.get(self.url, headers=headers, stream=True) as r:
                mode = 'ab' if self.segment.downloaded > 0 else 'wb'
                with open(self.segment.temp_path, mode) as f:
                    for chunk in r.iter_content(chunk_size=1024):
                        if chunk:
                            f.write(chunk)
                            self.segment.downloaded += len(chunk)
            
            self.segment.is_completed = True
            print(f"Thread-{self.segment.id} finished.")
            
        except Exception as e:
            print(f"Thread-{self.segment.id} failed: {e}")
```

#### C. The Manager (Orchestrator)

Starts the download, waits for threads, and triggers merging.

Python

```python
class DownloadManager:
    def __init__(self):
        self.tasks = []

    def start_download(self, url, output_file):
        task = DownloadTask(url, output_file)
        self.tasks.append(task)
        
        try:
            task.initialize()
            task.status = DownloadStatus.DOWNLOADING
            
            # Create threads
            workers = []
            for segment in task.segments:
                worker = ChunkDownloader(task.url, segment)
                workers.append(worker)
                worker.start()

            # Wait for all to finish (In real app, this would be async)
            for worker in workers:
                worker.join()

            # Check if all successful
            if all(s.is_completed for s in task.segments):
                task.merge_files()
            else:
                task.status = DownloadStatus.FAILED
                print("Download failed.")

        except Exception as e:
            print(f"Error: {e}")

# --- Driver Code ---
if __name__ == "__main__":
    # Example URL (Placeholder)
    url = "https://example.com/largefile.zip" 
    # Note: This URL needs to be real and support Ranges for the code to split chunks.
    # Otherwise, it defaults to 1 chunk.
    
    manager = DownloadManager()
    
    # In an interview, you don't run against a real URL usually, 
    # but explain the logic.
    print("Starting Manager...")
    # manager.start_download(url, "final_file.zip") 
```

***

#### 6. Key Design Decisions & Interview Talking Points

#### 1. Handling Resume (Persistence)

* Question: How does the system know where to resume after a crash?
* Design: We must serialise the `DownloadTask` object (including its list of `Segments` and their `downloaded` bytes count) to a file (e.g., `.json` or `.idm` file) on disk.
* On Startup: The Manager reads this file, reconstructs the `Segment` objects, and the `ChunkDownloader` calculates the new Range header: `Range: bytes=(start + downloaded)-end`.

#### 2. File I/O Optimization

* Design: I used separate temporary files (`.part0`, `.part1`) for each thread.
* Why? This avoids the complexity of Locking. If all threads wrote to the same big file using `seek()`, we would need a mutex to prevent race conditions, which slows down throughput. Merging at the end is faster than locking on every write.

#### 3. Concurrency Model

* Logic: I used a Thread per Segment.
* Bottleneck: Too many threads can hurt performance due to Context Switching and disk I/O saturation.
* Refinement: A real IDM uses a bounded `ThreadPool` (e.g., max 8 threads) regardless of how many files are in the queue.

#### 4. Network Failure Handling

* Logic: If a specific chunk fails, only that specific `Segment` is marked as `FAILED`. The Manager has a retry policy (Exponential Backoff) to retry only the failed chunks, not the whole file.

Would you like to explore "Design a URL Shortener" (System Design/HLD) or stick to LLD with "Design an Elevator System"?

## Coupon System for Zepto



An end-to-end Low Level Design (LLD) for a Coupon & Discount System (similar to Zepto, Swiggy, or Amazon).

This problem focuses on Flexibility and the Strategy Design Pattern. You need a system that can handle "10% Off", "Flat $50", "BOGO", and "Free Shipping" without rewriting code every time a marketing team invents a new promo.

***

#### 1. Requirements Clarification

* Coupon Types:
  * Percentage: "Get 10% off up to $50".
  * Flat: "Get $100 off".
  * Condition-based: "Free delivery on orders over $500".
* Constraints (Rules):
  * Minimum Order Value: E.g., Cart > $200.
  * Category Specific: Only applies to "Electronics".
  * User Specific: "New Users Only".
  * Expiry: Valid between Date A and Date B.
* Behavior:
  * Validate if a coupon is applicable to the current cart.
  * Calculate the discount amount.

***

#### 2. Core Entities & Patterns

1. Cart: The context object containing items and total value.
2. Coupon: The core entity. It doesn't hardcode logic; instead, it holds Strategies.
3. Constraint (Interface): Defines _conditions_ (e.g., `isValid(cart)`). Uses Chain of Responsibility or a simple List validation.
4. Reward (Interface): Defines _calculation_ (e.g., `calculateDiscount(cart)`). Uses Strategy Pattern.

***

#### 3. Class Diagram

The key takeaway is separating the "Condition" (Can I use it?) from the "Reward" (What do I get?).

<figure><img src="../../../.gitbook/assets/image (169).png" alt=""><figcaption></figcaption></figure>

***

#### 4. Python Implementation

#### A. The Context (Cart & Item)

Simple data holders.

Python

```python
class Item:
    def __init__(self, name, price, category):
        self.name = name
        self.price = price
        self.category = category

class Cart:
    def __init__(self):
        self.items = []
    
    def add_item(self, item):
        self.items.append(item)
        
    def get_total_price(self):
        return sum(item.price for item in self.items)
```

#### B. The Constraints (Validation Strategies)

We define small, reusable rules.

Python

```python
from abc import ABC, abstractmethod
import datetime

class Constraint(ABC):
    @abstractmethod
    def validate(self, cart: Cart) -> bool:
        pass

class MinOrderConstraint(Constraint):
    def __init__(self, min_price):
        self.min_price = min_price
        
    def validate(self, cart: Cart):
        return cart.get_total_price() >= self.min_price

class CategoryConstraint(Constraint):
    def __init__(self, required_category):
        self.required_category = required_category
        
    def validate(self, cart: Cart):
        # Check if ANY item in cart belongs to the category
        return any(item.category == self.required_category for item in cart.items)

class NotExpiredConstraint(Constraint):
    def __init__(self, expiry_date):
        self.expiry_date = expiry_date
        
    def validate(self, cart: Cart):
        return datetime.datetime.now() < self.expiry_date
```

#### C. The Rewards (Calculation Strategies)

This handles the math. Note the logic in `PercentageReward` to handle maximum caps.

Python

```python
class Reward(ABC):
    @abstractmethod
    def calculate(self, cart: Cart) -> float:
        pass

class FlatReward(Reward):
    def __init__(self, amount):
        self.amount = amount
        
    def calculate(self, cart: Cart):
        return self.amount

class PercentageReward(Reward):
    def __init__(self, percentage, max_discount=float('inf')):
        self.percentage = percentage
        self.max_discount = max_discount
        
    def calculate(self, cart: Cart):
        discount = cart.get_total_price() * (self.percentage / 100)
        return min(discount, self.max_discount)
```

#### D. The Coupon (The Composite Entity)

The Coupon itself is lightweight. It simply iterates through its constraints and then delegates calculation to its reward.

Python

```python
class Coupon:
    def __init__(self, code, reward: Reward):
        self.code = code
        self.reward = reward
        self.constraints = []

    def add_constraint(self, constraint: Constraint):
        self.constraints.append(constraint)

    def is_valid(self, cart: Cart):
        # ALL constraints must pass (AND logic)
        for constraint in self.constraints:
            if not constraint.validate(cart):
                return False
        return True

    def get_discount(self, cart: Cart):
        if self.is_valid(cart):
            discount = self.reward.calculate(cart)
            # Ensure discount doesn't exceed total cart value
            return min(discount, cart.get_total_price())
        return 0.0
```

#### E. Orchestration (Driver Code)

Python

```python
if __name__ == "__main__":
    # 1. Create a Cart
    cart = Cart()
    cart.add_item(Item("MacBook", 2000, "Electronics"))
    cart.add_item(Item("Shampoo", 10, "Personal Care"))
    
    print(f"Cart Total: ${cart.get_total_price()}")

    # 2. Create Coupon 1: "SAVE50" (Flat $50 off, Min Order $1000)
    flat_reward = FlatReward(50)
    coupon_1 = Coupon("SAVE50", flat_reward)
    coupon_1.add_constraint(MinOrderConstraint(1000))
    
    # 3. Create Coupon 2: "ELECTRO10" (10% off up to $100, Electronics only)
    percent_reward = PercentageReward(10, max_discount=100)
    coupon_2 = Coupon("ELECTRO10", percent_reward)
    coupon_2.add_constraint(CategoryConstraint("Electronics"))
    coupon_2.add_constraint(MinOrderConstraint(500))

    # 4. Apply
    print(f"Applying SAVE50: ${coupon_1.get_discount(cart)}")       # Returns 50
    print(f"Applying ELECTRO10: ${coupon_2.get_discount(cart)}")    # Returns 100 (Capped)
    
    # Test invalid case
    small_cart = Cart()
    small_cart.add_item(Item("Pen", 5, "Stationery"))
    print(f"Applying SAVE50 on small cart: ${coupon_1.get_discount(small_cart)}") # Returns 0
```

***

#### 5. Key Design Decisions & Interview Talking Points

#### 1. Strategy Pattern vs Inheritance

* Bad Approach: Creating classes like `FlatCoupon`, `PercentageCoupon`, `MinOrderFlatCoupon`. This leads to a class explosion.
* Good Approach (This Design): Composition. A `Coupon` _has_ a list of Constraints and _has_ a Reward strategy. This allows dynamic combinations (e.g., a "Flat Discount" valid only for "Electronics" on "Weekends").

#### 2. Extensibility

* Question: "How would you add a 'Buy One Get One' (BOGO) coupon?"
* Answer: I would create a new Reward Strategy: `BogoReward`.
  * It implements `calculate(cart)`.
  * Logic: Scan items, find pairs, deduct the price of the cheaper item.
  * No changes needed in `Coupon` or `Cart` classes.

#### 3. Concurrency (Database Design HLD hint)

* If this is a "First 100 users only" coupon, the `Constraint` needs to check a global counter. This introduces concurrency issues.
* Solution: The `GlobalLimitConstraint` would need to interact with a Redis atomic counter (decrement) to ensure we don't oversell the coupon.

#### 4. Multiple Coupons

* Question: Can I stack coupons?
* Answer: The current design allows testing coupons individually. To support stacking, the `CouponManager` would take a list of coupons, apply the first one, update the cart's "effective total", and apply the second one.

Would you like to explore "Design a Payment Gateway Integration" (Handling states, retries, and idempotency) next?

## Android Unlock Pattern

An end-to-end Low Level Design (LLD) for the Android Unlock Pattern system.

This problem is a classic example of Graph Theory (DFS/Backtracking) applied to a UI interaction problem. It tests how you model constraints, validate user input, and handle state transitions.

***

#### 1. Requirements Clarification

* Grid: Standard $$ $3 \times 3$ $$ grid (9 dots).
* Input: A sequence of connected points (the "Pattern").
* Constraints:
  1. Minimum Length: Pattern must connect at least 4 dots.
  2. Unique Nodes: A dot can be visited only once in a single pattern (unless passing through an already visited dot, though standard implementations usually strictly forbid re-visiting as a "node endpoint").
  3. Direct Path: If connecting Dot A to Dot C passes through Dot B, Dot B must be part of the pattern (unless B was already visited).
* Operations:
  * Set Pattern: Save a new valid pattern.
  * Verify Pattern: Check if input matches the saved pattern.

***

#### 2. Core Entities

1. Point (Node): Represents a specific dot on the grid $$ $(x, y)$ $$ or ID $$ $(0-8)$ $$.
2. Pattern: A strictly ordered list of Points.
3. GridManager: Knows the geometry. Validates if a move from $$ $A \rightarrow B$ $$ is legal (e.g., is it adjacent? Does it skip a node?).
4. UnlockService: The controller. Stores the "password" (hashed) and manages the `Set` vs `Verify` state.

***

#### 3. Class Diagram

<figure><img src="../../../.gitbook/assets/image (170).png" alt=""><figcaption></figcaption></figure>

***

#### 4. Python Implementation

#### A. The Point Entity

We map the 3x3 grid to coordinates $$ $(0,0)$ $$ to $$ $(2,2)$ $$.

Python

```python
class Point:
    def __init__(self, row, col):
        self.row = row
        self.col = col
        self.index = row * 3 + col # 0 to 8

    def __eq__(self, other):
        return self.row == other.row and self.col == other.col

    def __repr__(self):
        return f"({self.row},{self.col})"
    
    def __hash__(self):
        return hash(self.index)
```

#### B. The Validator (The "Skip" Logic)

This is the hardest part.

* Scenario: Moving from $$ $(0,0)$ $$ to $$ $(0,2)$ $$ skips $$ $(0,1)$ $$.
* Rule: This is only legal if $$ $(0,1)$ $$ has _already_ been visited.
* Math: The midpoint between two points $$ $(r1, c1)$ $$ and $$ $(r2, c2)$ $$ is an integer coordinate only if $$ $r1+r2$ $$ and $$ $c1+c2$ $$ are both even.

Python

```python
class PatternValidator:
    def __init__(self):
        self.MIN_LENGTH = 4

    def is_valid_pattern(self, pattern: list[Point]) -> bool:
        if len(pattern) < self.MIN_LENGTH:
            print("Error: Pattern too short")
            return False
        
        visited = set()
        visited.add(pattern[0])

        for i in range(len(pattern) - 1):
            curr = pattern[i]
            next_node = pattern[i+1]

            # 1. Check if next node is already visited (Standard rule: No repeats)
            if next_node in visited:
                print(f"Error: Node {next_node} revisited")
                return False

            # 2. Check for 'Skips' (The core constraints)
            intermediate = self._get_intermediate_point(curr, next_node)
            
            # If there is a point in between, it MUST be visited already
            if intermediate and intermediate not in visited:
                print(f"Error: Skipped over unvisited node {intermediate}")
                return False

            visited.add(next_node)
            
        return True

    def _get_intermediate_point(self, p1: Point, p2: Point):
        # Check if they are in the same row, col, or diagonal
        # AND if there is a point exactly in the middle
        
        # Math: Midpoint = (x1+x2)/2, (y1+y2)/2
        row_sum = p1.row + p2.row
        col_sum = p1.col + p2.col

        # If sum is odd, midpoint is 0.5 (not a grid point) -> No skip
        if row_sum % 2 != 0 or col_sum % 2 != 0:
            return None

        mid_row = row_sum // 2
        mid_col = col_sum // 2
        
        # Determine the middle point
        return Point(mid_row, mid_col)
```

#### C. The Lock System (Controller)

Python

```python
class PatternLockSystem:
    def __init__(self):
        self.saved_pattern_hash = None
        self.validator = PatternValidator()

    def set_pattern(self, pattern: list[Point]):
        if self.validator.is_valid_pattern(pattern):
            # In real systems, we store a Hash, not the raw points
            self.saved_pattern_hash = self._generate_hash(pattern)
            print("Pattern set successfully!")
        else:
            print("Failed to set pattern.")

    def unlock(self, input_pattern: list[Point]):
        if not self.saved_pattern_hash:
            print("No pattern set yet.")
            return False

        input_hash = self._generate_hash(input_pattern)
        
        if input_hash == self.saved_pattern_hash:
            print("UNLOCK SUCCESSFUL")
            return True
        else:
            print("WRONG PATTERN")
            return False

    def _generate_hash(self, pattern):
        # Simple string representation for this example
        # e.g., "0->1->2->4"
        return "->".join([str(p.index) for p in pattern])

# --- Helper to create points easily ---
def get_points(indices):
    return [Point(i // 3, i % 3) for i in indices]

# --- Driver Code ---
if __name__ == "__main__":
    system = PatternLockSystem()
    
    # Test 1: Valid Pattern (L shape: 0->3->6->7)
    # Grid:
    # 0 1 2
    # 3 4 5
    # 6 7 8
    print("--- Test 1: Set Valid ---")
    valid_seq = get_points([0, 3, 6, 7])
    system.set_pattern(valid_seq)
    
    # Test 2: Unlock Correctly
    print("\n--- Test 2: Unlock ---")
    system.unlock(valid_seq)

    # Test 3: Invalid Move (0 -> 2, skipping 1 unvisited)
    print("\n--- Test 3: Illegal Skip ---")
    illegal_seq = get_points([0, 2, 5, 8]) 
    # Validator should catch that 0->2 skips 1
    system.validator.is_valid_pattern(illegal_seq) 

    # Test 4: Valid Skip (1 visited, then 0 -> 2)
    # Path: 1 -> 0 -> 2 -> 5 (This is valid because 1 is visited before the 0->2 jump)
    print("\n--- Test 4: Valid Skip ---")
    valid_skip_seq = get_points([1, 0, 2, 5])
    if system.validator.is_valid_pattern(valid_skip_seq):
        print("Complex skip pattern is Valid (Correct)")
```

***

#### 5. Key Interview Points

#### 1. The "Skip" Logic (Midpoint Formula)

* Question: How do you detect if `0 -> 8` skips `4`?
* Answer:
  * $$ $P_1=(0,0), P_2=(2,2)$ $$.
  * Sum of coords: $$ $(2, 2)$ $$.
  * Both are even, so Midpoint exists at $$ $(1, 1)$ $$, which is Index 4.
  * If Index 4 is NOT in `visited_set`, the move is illegal.
* Edge Case: Knight's move (like Chess).
  * $$ $0(0,0) \rightarrow 5(1,2)$ $$.
  * Sum: $$ $(1, 2)$ $$. Row sum is odd. No integer midpoint.
  * Move is valid without any visited checks (knights jump over pieces).

#### 2. Security (Hashing)

* Never store the pattern as a plain list `[0, 1, 2, 5]`.
* Convert the sequence to a string or byte array and SHA-256 hash it.
* Salt: Add a user-specific salt to prevent Rainbow Table attacks (though the search space for patterns is small, \~389,000 combinations, so salting is crucial).

#### 3. Maximum Combinations (DFS)

* If the interviewer asks "How many valid patterns exist?", this becomes a DFS / Backtracking counting problem.
  * Min length 4, Max length 9.
  * You write a recursive DFS that attempts every valid move from the current node and counts the paths.

#### 4. Code Extensibility

* Grid Size: The code calculates `row = index // 3`. By changing `3` to `N`, the `Point` and `Validator` logic supports 4x4 or 5x5 grids instantly.

Would you like to move to "Design a Code Deployment System" (like Jenkins/deployment pipelines)?&#x20;

## Scalable Database Schema for Reddit-like Comments



An end-to-end Low Level Design (LLD) and Schema Design for a scalable, threaded comment system like Reddit.

This is a classic system design interview question that tests your knowledge of Hierarchical Data Modeling in relational databases.

***

#### 1. Requirements Clarification

* Structure: Infinite nesting (Comment A $$ $\rightarrow$ $$ Reply B $$ $\rightarrow$ $$ Reply C).
* Scale: Millions of comments per post (potentially).
* Performance:
  * Read-heavy: Users view comments much more than they write.
  * Pagination: Cannot load the entire tree at once; need "Load more replies".
  * Ranking: Comments are sorted by relevance (score) or time.
* Operations: Post comment, Reply, Upvote/Downvote, Edit.

***

#### 2. The Core Problem: Storing Trees in SQL

There are four common ways to store trees. For Reddit, we compare the two best options:

| **Strategy**      | **Concept**                    | **Pros**                                           | **Cons**                                               | **Verdict**     |
| ----------------- | ------------------------------ | -------------------------------------------------- | ------------------------------------------------------ | --------------- |
| Adjacency List    | Column `parent_id`             | Simple, easy inserts.                              | Reading a full tree requires recursive queries (CTEs). | Good (Standard) |
| Materialized Path | Column `path` (e.g., "1/5/12") | Extremely fast subtree retrieval via regex/prefix. | Moving subtrees is hard (rare in Reddit).              | Best for Scale  |

We will use the Materialized Path (Path Enumeration) pattern because it makes fetching and sorting nested threads efficient without complex recursion.

***

#### 3. Database Schema

#### Table 1: `Comments`

This is the heart of the system.

SQL

```python
CREATE TABLE Comments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL,          -- The Thread ID
    user_id BIGINT NOT NULL,
    parent_id BIGINT NULL,            -- Optimization for direct parent lookups
    
    -- THE MAGIC COLUMN
    -- Stores the lineage: "root_id/child_id/grandchild_id"
    -- Example: "0010/0045/0099"
    path VARCHAR(255) NOT NULL,
    
    content TEXT,
    depth INT DEFAULT 0,              -- Indentation level (UI helper)
    
    -- Denormalized Counters (For Read Speed)
    upvote_count INT DEFAULT 0,
    downvote_count INT DEFAULT 0,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_post_path (post_id, path) -- Crucial for "SELECT * ... ORDER BY path"
);
```

#### Table 2: `CommentVotes`

Keeps track of who voted on what to prevent double voting.

SQL

```python
CREATE TABLE CommentVotes (
    user_id BIGINT,
    comment_id BIGINT,
    vote_value TINYINT, -- +1 for Up, -1 for Down
    PRIMARY KEY (user_id, comment_id)
);
```

***

#### 4. Key Operations (SQL Logic)

#### A. Inserting a Comment

When User B replies to User A (Comment ID `100`, Path `0010/0100`):

1. Generate Path: `Parent_Path` + `/` + `New_ID`.
2. Calculate Depth: `Parent_Depth` + 1.

_Note: In a real system, you might generate the ID separately or use a UUID to construct the path before insertion._

SQL

```python
-- 1. Get Parent Info
SELECT path, depth FROM Comments WHERE id = 100;
-- Returns: path="0010/0100", depth=2

-- 2. Insert New Reply
INSERT INTO Comments (post_id, parent_id, path, depth, content)
VALUES (
    55, 
    100, 
    '0010/0100/0101', -- Constructed in app logic
    3, 
    'This is a reply'
);
```

#### B. Fetching the Tree (The "Reddit View")

This is where Materialized Path shines. If you sort by the `path` column string, the comments naturally align in a Depth-First Search (DFS) order—exactly how they appear on a UI.

SQL

```sql
SELECT * FROM Comments 
WHERE post_id = 55 
ORDER BY path ASC 
LIMIT 50; 
```

Result:

1. `0001` (Root Comment)
2. `0001/0002` (Reply to 1)
3. `0001/0002/0003` (Reply to 2)
4. `0005` (New Root Comment)

Pagination: To "Load More" from a specific point, you can simply query `WHERE path > 'last_seen_path'`.

***

#### 5. Python LLD Implementation

<figure><img src="../../../.gitbook/assets/image (171).png" alt=""><figcaption></figcaption></figure>

#### Code Structure

Python

```python
class CommentService:
    def __init__(self, db_conn):
        self.db = db_conn

    def add_top_level_comment(self, post_id, user_id, content):
        # 1. Generate a new ID (assuming auto-increment or UUID)
        new_id = self._generate_id()
        
        # 2. Path is just the ID for root comments
        path = self._format_path_segment(new_id)
        
        # 3. Save
        sql = "INSERT INTO Comments (id, post_id, path, depth...) VALUES (...)"
        self.db.execute(sql, (new_id, post_id, path, 0, content))

    def reply_to_comment(self, parent_id, user_id, content):
        # 1. Fetch Parent Path
        parent = self.db.fetch_one("SELECT path, depth, post_id FROM Comments WHERE id = %s", parent_id)
        if not parent:
            raise ValueError("Parent not found")

        new_id = self._generate_id()
        
        # 2. Construct Child Path
        # e.g., Parent="0001", New="0001/0005"
        child_path = f"{parent['path']}/{self._format_path_segment(new_id)}"
        child_depth = parent['depth'] + 1

        # 3. Save
        sql = "INSERT INTO Comments (id, post_id, path, depth...) VALUES (...)"
        self.db.execute(sql, (new_id, parent['post_id'], child_path, child_depth, content))

    def get_discussion_tree(self, post_id):
        # Fetch flattened tree sorted by path
        # The Frontend will use 'depth' to render indentation
        sql = "SELECT * FROM Comments WHERE post_id = %s ORDER BY path ASC LIMIT 100"
        return self.db.fetch_all(sql, post_id)

    def _format_path_segment(self, id):
        # Formats ID to fixed length for correct string sorting
        # ID 5 -> "0005"
        return str(id).zfill(4)
```

***

#### 6. Optimization & Scaling (Interview Gold)

#### 1. The "Viral Post" Problem (Hot Partition)

* Issue: If one post has 1 million comments, querying `WHERE post_id = X` becomes slow.
* Solution: Database Sharding. Shard comments based on `post_id`. All comments for Post X live on Shard A. This keeps the tree structure intact on one server.

#### 2. Denormalization (Vote Counts)

* Issue: Calculating `SELECT COUNT(*) FROM Votes WHERE comment_id = X` every time a user views a thread is fatal for performance.
* Solution: Add `upvote_count` column to `Comments` table.
  * Write Path: When a user votes, update the `Votes` table AND increment the `Comments.upvote_count`.
  * High Scale: Use Redis to buffer increments. Update the SQL database in batches (e.g., every 10 seconds) to reduce write locking.

#### 3. Caching (Redis)

* Cache the Top 200 comments of hot threads in Redis.
* Structure: `Redis List` or `Sorted Set` serialized as JSON.
* Since the top comments don't change structure often (mostly just vote counts change), cache invalidation is manageable.

#### 4. Why not NoSQL (MongoDB/Cassandra)?

* MongoDB: Actually a good fit. You can store the comment tree as a nested document (up to 16MB limit) or use the same "Materialized Path" pattern.
* Cassandra: Great for writes, but strictly hierarchical fetching (Sorting) is harder. You would need to model the partition key carefully (e.g., `post_id`) and use clustering keys for sorting, but recursive nesting depth is hard to model without application-side logic.
* Verdict: SQL (Postgres) is usually preferred for comments because of strong consistency (ACID) requirements for voting and structural integrity, but MongoDB is a very strong runner-up.

#### Next Step

Would you like to move on to "Design a Leaderboard System" (Redis Sorted Sets) or "Design a Notification Service"?

## Sublime Text IDE

An end-to-end Low Level Design (LLD) for a Text Editor (like Sublime Text, Notepad++, or VS Code).

This problem is unique because it focuses heavily on Data Structures (how to store text efficiently) and the Command Pattern (how to handle Undo/Redo).

***

#### 1. Requirements Clarification

* Core Actions: Insert text, Delete text, Move cursor (Up/Down/Left/Right).
* File Handling: Open, Save.
* Undo/Redo: Infinite undo history.
* Selection: Highlight and modify a range of text.
* Multiple Views: Support editing multiple files (Tabs).
* Search: Find strings within the file.

***

#### 2. The Core Data Structure (The "Secret Sauce")

How do we store the text? A simple `String` is $$ $O(N)$ $$ for insertions. We need better options:

1. Gap Buffer (Used by Emacs): A dynamic array with a "gap" of empty space at the cursor. Insertions at the cursor are $$ $O(1)$ $$. Moving the cursor far away requires shifting the gap ($$ $O(N)$ $$).
2. Rope (Used by Heavyweights): A binary tree where leaves are strings. Efficient for massive files.
3. Piece Table (Used by VS Code): Stores the original file (Read-Only) and an "Add Buffer" (Append-Only). The document is a list of pointers to these buffers.
4. List of Lines (Our Choice): A Linked List or Array of Strings, where each string is a line.
   * _Why?_ It's easy to implement in an interview and efficient enough for most cases (vertical movement is $$ $O(1)$ $$).

***

#### 3. Core Entities & Patterns

1. TextBuffer: The Model. Holds the content (List of Lines).
2. Cursor: Tracks position (`row`, `col`).
3. Command (Pattern): Encapsulates an action (`InsertCommand`, `DeleteCommand`). This is mandatory for Undo/Redo.
4. CommandHistory: A Stack that holds executed commands.
5. Editor: The Controller. Binds inputs to commands.
6. Flyweight (Pattern): (Optional) Used for rendering characters with syntax highlighting to save memory.

***

#### 4. Class Diagram

<figure><img src="../../../.gitbook/assets/image (172).png" alt=""><figcaption></figcaption></figure>

***

#### 5. Python Implementation

#### A. The Model (Buffer & Cursor)

We use a List of Lines approach. This makes handling "Enter" (splitting a line) and "Backspace" (merging lines) the critical logic.

Python

```python
class Cursor:
    def __init__(self, row=0, col=0):
        self.row = row
        self.col = col

    def update(self, row, col):
        self.row = row
        self.col = col

class TextBuffer:
    def __init__(self):
        # Start with one empty line
        self.lines = [""] 

    def get_content(self):
        return "\n".join(self.lines)

    def get_line_length(self, row):
        return len(self.lines[row])

    # --- Core Operations ---
    
    def insert_char(self, char, row, col):
        # Insert character at specific position
        line = self.lines[row]
        self.lines[row] = line[:col] + char + line[col:]

    def delete_char(self, row, col):
        # Delete character at specific position
        line = self.lines[row]
        char = line[col] # Save for undo
        self.lines[row] = line[:col] + line[col+1:]
        return char

    def split_line(self, row, col):
        # Handle 'Enter' key: Split one line into two
        line = self.lines[row]
        first_part = line[:col]
        second_part = line[col:]
        self.lines[row] = first_part
        self.lines.insert(row + 1, second_part)

    def merge_lines(self, row):
        # Handle 'Backspace' at start of line: Join with previous line
        if row == 0: return # Cannot merge first line up
        
        current_line = self.lines[row]
        previous_line = self.lines[row - 1]
        
        new_cursor_col = len(previous_line) # Cursor moves to end of prev line
        
        self.lines[row - 1] = previous_line + current_line
        self.lines.pop(row)
        return new_cursor_col
```

#### B. The Command Pattern (Undo/Redo)

Every action must be reversible.

Python

```python
from abc import ABC, abstractmethod

class Command(ABC):
    @abstractmethod
    def execute(self): pass
    
    @abstractmethod
    def undo(self): pass

class InsertCommand(Command):
    def __init__(self, buffer: TextBuffer, cursor: Cursor, char: str):
        self.buffer = buffer
        self.cursor = cursor
        self.char = char
        # Snapshot state for undo
        self.row = cursor.row
        self.col = cursor.col

    def execute(self):
        self.cursor.update(self.row, self.col)
        if self.char == '\n':
            self.buffer.split_line(self.row, self.col)
            self.cursor.update(self.row + 1, 0)
        else:
            self.buffer.insert_char(self.char, self.row, self.col)
            self.cursor.update(self.row, self.col + 1)

    def undo(self):
        if self.char == '\n':
            self.buffer.merge_lines(self.row + 1)
            self.cursor.update(self.row, self.col)
        else:
            self.buffer.delete_char(self.row, self.col)
            self.cursor.update(self.row, self.col)

class DeleteCommand(Command):
    # Handles Backspace
    def __init__(self, buffer: TextBuffer, cursor: Cursor):
        self.buffer = buffer
        self.cursor = cursor
        self.row = cursor.row
        self.col = cursor.col
        self.deleted_char = None # To restore on Undo

    def execute(self):
        # Case 1: Beginning of line (Merge)
        if self.col == 0:
            if self.row > 0:
                self.deleted_char = '\n'
                new_col = self.buffer.merge_lines(self.row)
                self.cursor.update(self.row - 1, new_col)
        # Case 2: Middle of line (Simple delete)
        else:
            self.cursor.update(self.row, self.col - 1)
            self.deleted_char = self.buffer.delete_char(self.row, self.col - 1)

    def undo(self):
        if self.deleted_char == '\n':
            self.buffer.split_line(self.row - 1, self.cursor.col)
            self.cursor.update(self.row, 0)
        else:
            self.buffer.insert_char(self.deleted_char, self.row, self.col - 1)
            self.cursor.update(self.row, self.col)
```

#### C. The History Manager

Manages the stacks.

Python

```python
class CommandHistory:
    def __init__(self):
        self.undo_stack = []
        self.redo_stack = []

    def execute(self, cmd: Command):
        cmd.execute()
        self.undo_stack.append(cmd)
        self.redo_stack.clear() # New action clears redo history

    def undo(self):
        if not self.undo_stack: return
        cmd = self.undo_stack.pop()
        cmd.undo()
        self.redo_stack.append(cmd)

    def redo(self):
        if not self.redo_stack: return
        cmd = self.redo_stack.pop()
        cmd.execute()
        self.undo_stack.append(cmd)
```

#### D. The Editor (Controller)

Python

```python
class SublimeEditor:
    def __init__(self):
        self.buffer = TextBuffer()
        self.cursor = Cursor()
        self.history = CommandHistory()

    def type_key(self, char):
        cmd = InsertCommand(self.buffer, self.cursor, char)
        self.history.execute(cmd)

    def backspace(self):
        cmd = DeleteCommand(self.buffer, self.cursor)
        self.history.execute(cmd)

    def undo(self):
        print(">> Undo")
        self.history.undo()

    def redo(self):
        print(">> Redo")
        self.history.redo()

    def display(self):
        # Simple CLI visualization
        # In a real UI, this would paint to a Canvas
        print("-" * 20)
        content = self.buffer.lines[:]
        # Visualizing Cursor
        line = content[self.cursor.row]
        content[self.cursor.row] = line[:self.cursor.col] + "|" + line[self.cursor.col:]
        
        for l in content:
            print(l)
        print("-" * 20)

# --- Driver Code ---
if __name__ == "__main__":
    editor = SublimeEditor()
    
    editor.type_key('H')
    editor.type_key('e')
    editor.type_key('l')
    editor.type_key('l')
    editor.type_key('o')
    editor.display() # Hello|
    
    editor.type_key('\n') # Enter
    editor.type_key('W')
    editor.display()
    # Hello
    # W|
    
    editor.undo() # Undo 'W'
    editor.undo() # Undo Enter
    editor.display() # Hello|
    
    editor.redo() # Redo Enter
    editor.display() 
    # Hello
    # |
```

***

#### 6. Key Interview Points

#### 1. Data Structures (The "Gap Buffer")

* Question: Why not use a simple long string?
* Answer: Inserting into a string is $$ $O(N)$ $$ (requires shifting all subsequent characters).
* Optimization: In a real-world editor (Emacs), we use a Gap Buffer.
  * `[H, E, L, L, O, _, _, _, W, O, R, L, D]`
  * The underscores represent empty space (the gap) at the cursor position. Insertion is $$ $O(1)$ $$.
  * If the cursor moves, we shift the gap.

#### 2. Syntax Highlighting (Flyweight Pattern)

* If we have 1 million 'A' characters, creating 1 million objects is heavy.
* Flyweight: We create one `Glyph('A', Font, Color)` object and reuse it, storing only the _position_ externally.
* Strategy Pattern: Syntax highlighting rules (Python vs Java) are different strategies injected into the parser.

#### 3. Large File Support (Proxy/Lazy Loading)

* Question: How to open a 10GB log file?
* Answer: Do not load the whole file into RAM.
  * Use Memory Mapping (mmap).
  * Load only the "viewport" (visible lines) + a small buffer above/below.
  * This is the Virtual Proxy pattern.

#### 4. Search (KMP Algorithm)

* Implementing `Ctrl+F` requires string matching. A naive loop is $$ $O(N*M)$ $$. The Knuth-Morris-Pratt (KMP) algorithm does it in $$ $O(N+M)$ $$, which is preferred for text editors.

#### 5. Composite Pattern

* Used if the editor supports grouping elements, like folding sections of code (blocks) or rendering rich text where a Document contains Paragraphs, which contain Sentences, which contain Characters.

Would you like to move on to "Design a Scheduler / Job Queue" (Concurrency & Heaps) or "Design a Cache (Redis)"?



## Ngrok Tool



Here is a detailed Low Level Design (LLD) for an Ngrok-like Tunneling Tool.

This is a System Design / Networking heavy problem. The core challenge is exposing a service running behind a NAT/Firewall (localhost) to the public internet without port forwarding.

***

#### 1. Requirements Clarification

* Goal: Expose a local port (e.g., `localhost:8080`) to a public URL (e.g., `myapp.tunnel.com`).
* Actors:
  1. Public User: Accesses the URL via browser.
  2. Tunnel Server (Cloud): Accepts public traffic and routes it to the correct agent.
  3. Tunnel Client (Agent): Runs locally, connects to the server, and forwards traffic to the local app.
* Key Features:
  * NAT Traversal: The client must initiate the connection (outbound) because inbound ports are blocked.
  * Multiplexing: Multiple public users should use the same single persistent TCP connection to the agent.
  * Protocol: HTTP/TCP.

***

#### 2. High-Level Architecture

The magic of Ngrok is a Reverse Tunnel.

1. Agent connects to Server (on a control port, e.g., 4443). Connection is kept open.
2. Server listens on a public port (e.g., 80) for internet traffic.
3. User requests `http://abc.tunnel.com`.
4. Server looks up the connection for `abc`, encapsulates the HTTP request, and sends it down the socket to Agent.
5. Agent unwraps it, calls `localhost:8080`, gets response, and sends it back up the tunnel.

***

#### 3. Class Diagram & Entities

<figure><img src="../../../.gitbook/assets/image (173).png" alt=""><figcaption></figcaption></figure>

***

#### 4. Python Implementation (Machine Coding)

We will use Python's `asyncio` because this is an I/O-bound problem requiring high concurrency.

#### A. The Tunnel Server (`server.py`)

This runs on the Cloud. It acts as a switchboard.

Python

```python
import asyncio

# Registry: Maps 'subdomain' -> TunnelSession
# For simplicity, we assume 1 subdomain = 1 active writer
active_tunnels = {}

class TunnelServer:
    def __init__(self, public_port=8080, control_port=9090):
        self.public_port = public_port
        self.control_port = control_port

    async def start(self):
        # 1. Start Control Server (For Agents)
        server_control = await asyncio.start_server(
            self.handle_agent_connection, '0.0.0.0', self.control_port
        )
        print(f"Control Server listening on {self.control_port}...")

        # 2. Start Public Web Server (For Users)
        server_public = await asyncio.start_server(
            self.handle_public_request, '0.0.0.0', self.public_port
        )
        print(f"Public Server listening on {self.public_port}...")

        async with server_control, server_public:
            await asyncio.gather(server_control.serve_forever(), server_public.serve_forever())

    # --- Handling Agents (The Tunnel Creators) ---
    async def handle_agent_connection(self, reader, writer):
        # Simple Handshake: Agent sends "SUBDOMAIN:xyz"
        data = await reader.read(1024)
        msg = data.decode().strip()
        
        if msg.startswith("REGISTER:"):
            subdomain = msg.split(":")[1]
            active_tunnels[subdomain] = (reader, writer)
            print(f"Tunnel registered for: {subdomain}")
            
            # Keep connection alive to write data later
            # In a real app, we would handle heartbeats here
            try:
                await reader.read() # Wait until connection closes
            except:
                pass
            finally:
                del active_tunnels[subdomain]
                writer.close()

    # --- Handling Public Users (The Traffic) ---
    async def handle_public_request(self, reader, writer):
        # 1. Read the HTTP Request to find the Host header
        # Note: We peek at data to route, but we must forward everything.
        data = await reader.read(4096) 
        request_text = data.decode(errors='ignore')
        
        # 2. Extract Subdomain (Simplistic parsing)
        # Request looks like: "GET / HTTP/1.1\r\nHost: xyz.tunnel.com..."
        subdomain = self._parse_subdomain(request_text)
        
        if subdomain and subdomain in active_tunnels:
            agent_reader, agent_writer = active_tunnels[subdomain]
            
            # 3. Forward Public Request -> Agent
            print(f"Forwarding traffic to agent: {subdomain}")
            agent_writer.write(data)
            await agent_writer.drain()
            
            # 4. Read Response from Agent -> Public User
            # We create a bridge loop here
            try:
                while True:
                    # Wait for agent to send back the localhost response
                    response_chunk = await agent_reader.read(4096)
                    if not response_chunk: break
                    writer.write(response_chunk)
                    await writer.drain()
            except Exception as e:
                print(f"Connection closed: {e}")
        else:
            writer.write(b"HTTP/1.1 404 Not Found\r\n\r\nTunnel not found.")
            await writer.drain()

        writer.close()

    def _parse_subdomain(self, request):
        # Mock logic: assume header "Host: xyz.localhost"
        try:
            lines = request.split('\r\n')
            for line in lines:
                if line.startswith("Host:"):
                    host = line.split(":")[1].strip()
                    return host.split(".")[0] # 'xyz.localhost' -> 'xyz'
        except:
            return None
        return "demo" # Default for testing

if __name__ == "__main__":
    server = TunnelServer()
    asyncio.run(server.start())
```

#### B. The Tunnel Client (`agent.py`)

This runs on your laptop. It connects `Server <-> Localhost`.

Python

```python
import asyncio

class TunnelAgent:
    def __init__(self, server_host, control_port, local_port, subdomain):
        self.server_host = server_host
        self.control_port = control_port
        self.local_port = local_port
        self.subdomain = subdomain

    async def start(self):
        # 1. Connect to Public Tunnel Server
        try:
            remote_reader, remote_writer = await asyncio.open_connection(
                self.server_host, self.control_port
            )
        except Exception as e:
            print(f"Could not connect to server: {e}")
            return

        # 2. Register Subdomain
        print(f"Registering subdomain: {self.subdomain}")
        remote_writer.write(f"REGISTER:{self.subdomain}".encode())
        await remote_writer.drain()

        # 3. Listen for incoming forwarded requests
        print("Tunnel established. Waiting for traffic...")
        
        while True:
            # Read Request from Server (originally from Public User)
            data = await remote_reader.read(4096)
            if not data:
                print("Server closed connection.")
                break
            
            print("Received Request -> Forwarding to Localhost")
            
            # 4. Forward to Localhost App (e.g., Flask running on 5000)
            response_data = await self._forward_to_local(data)
            
            # 5. Send Response back to Server
            remote_writer.write(response_data)
            await remote_writer.drain()

    async def _forward_to_local(self, request_data):
        try:
            # Open short-lived connection to local app
            local_reader, local_writer = await asyncio.open_connection(
                '127.0.0.1', self.local_port
            )
            
            local_writer.write(request_data)
            await local_writer.drain()
            
            # Read full response
            # Note: In production, we'd stream this, not buffer all at once
            response = b""
            while True:
                chunk = await local_reader.read(4096)
                if not chunk: break
                response += chunk
            
            local_writer.close()
            return response
        except Exception as e:
            return f"HTTP/1.1 502 Bad Gateway\r\n\r\nLocalhost Error: {e}".encode()

if __name__ == "__main__":
    # Example: Forward traffic from Server to Localhost:5000
    agent = TunnelAgent('127.0.0.1', 9090, 5000, 'demo')
    asyncio.run(agent.start())
```

***

#### 5. Key Design Challenges (Interview Gold)

#### 1. Handling Concurrency (Multiplexing)

* Problem: In the code above, if the Agent is processing one request (waiting for localhost), the tunnel is blocked. Other users hang.
* Solution (Real Ngrok): The tunnel shouldn't just send raw bytes. It should wrap data in Frames.
  * _Frame:_ `[RequestID | Payload_Length | Payload]`
  * The Agent reads the frame, spawns a _new async task_ to talk to localhost, and when done, sends a frame back with the same `RequestID`.
  * This allows asynchronous multiplexing over a single TCP socket.

#### 2. Disconnections & Reconnecting

* Problem: Wi-Fi drops.
* Solution: The Agent needs a `while True` loop with exponential backoff retry logic around the `connect()` function. It should also send `PING` frames (Heartbeats) to keep the NAT mapping alive.

#### 3. Security

* Problem: Anyone can connect to your server and register "https://www.google.com/search?q=google.tunnel.com".
* Solution:
  * Auth Tokens: Agent sends `REGISTER: {token} : {subdomain}`.
  * TLS Termination: The Public Server should handle HTTPS certificates (wildcard cert `*.tunnel.com`), decrypt the traffic, and send plain HTTP (or re-encrypted) over the tunnel.

#### 4. Protocol Support

* Ngrok supports HTTP, TCP, and TLS tunnels.
* For Raw TCP (e.g., exposing an SSH port), the server cannot parse headers. It simply allocates a random public port (e.g., `tunnel.com:12345`) and pipes everything from that port to the agent.

***

#### 6. Execution Flow Summary

1. Start Local App: `python -m http.server 5000` (runs a dummy site).
2. Start Tunnel Server: `python server.py`. It listens on 9090 (control) and 8080 (web).
3. Start Agent: `python agent.py`. It connects to 9090 and says "I am demo".
4. Public Access: Browser opens `http://localhost:8080`.
   * Server sees "Host: localhost". Routes to 'demo' Agent.
   * Agent sends to port 5000.
   * Port 5000 replies.
   * Agent sends back to Server.
   * Browser shows the page.

Would you like to explore "Design a Load Balancer" or "Design a Collaborative Document Editor (Operational Transformation)" next?

## Rate Limiter



An end-to-end Low Level Design (LLD) for a Rate Limiter.

This is a classic Machine Coding problem that tests your knowledge of Concurrency, Design Patterns (Strategy), and Algorithms.

***

#### 1. Requirements Clarification

* Goal: Restrict the number of requests a client (User/IP) can make within a time window.
* Rules:
  * Limit: e.g., "10 requests per second".
  * Granularity: User-based (User ID) or Global.
* Behavior: Return `True` (Allowed) or `False` (Throttled/HTTP 429).
* Environment:
  * Thread-Safe: Must handle concurrent requests correctly.
  * In-Memory: For this problem, we assume a single server instance (though we should discuss distributed scaling).

***

#### 2. Algorithm Choice

There are two primary algorithms suitable for an LLD interview. We must choose one and justify it.

1. Sliding Window Log: Stores the timestamp of _every_ request. Accurate, but memory-intensive ($$ $O(N)$ $$ space per user).
2. Token Bucket (Recommended): A "bucket" holds tokens. Tokens refill at a constant rate. Each request consumes a token.
   * Pros: Memory efficient ($$ $O(1)$ $$ space—just an integer counter), allows short bursts of traffic (good UX), easy to implement.

We will implement the Token Bucket Algorithm.

***

#### 3. Class Diagram

We use the Strategy Pattern to allow swapping algorithms (e.g., switching from Token Bucket to Leaky Bucket) without changing the core service logic.

Code snippet

<figure><img src="../../../.gitbook/assets/image (174).png" alt=""><figcaption></figcaption></figure>

***

#### 4. Python Implementation

#### A. The Bucket Entity (Thread-Safe)

This class holds the state for a single user.

Crucial Concept: We use Lazy Refill. We don't have a background timer adding tokens every second. Instead, when a request comes in, we calculate: tokens\_to\_add = (now - last\_time) \* rate.

Python

```python
import time
import threading

class Bucket:
    def __init__(self, capacity, refill_rate):
        self.capacity = capacity          # Max burst size
        self.tokens = capacity            # Current available tokens
        self.refill_rate = refill_rate    # Tokens per second
        self.last_refill_timestamp = time.time()
        self.lock = threading.Lock()      # Granular lock for this specific user

    def allow_request(self, tokens_needed=1):
        with self.lock:
            self._refill()
            
            if self.tokens >= tokens_needed:
                self.tokens -= tokens_needed
                return True
            return False

    def _refill(self):
        now = time.time()
        delta = now - self.last_refill_timestamp
        
        # Logic: Calculate how many tokens *should* have been added since last visit
        tokens_to_add = delta * self.refill_rate
        
        if tokens_to_add > 0:
            self.tokens = min(self.capacity, self.tokens + tokens_to_add)
            self.last_refill_timestamp = now
```

#### B. The Strategy (Manager)

This manages the collection of buckets for different users.

Python

```python
from abc import ABC, abstractmethod

class RateLimiterStrategy(ABC):
    @abstractmethod
    def allow_request(self, key: str) -> bool:
        pass

class TokenBucketLimiter(RateLimiterStrategy):
    def __init__(self, capacity, refill_rate):
        self.capacity = capacity
        self.refill_rate = refill_rate
        self.buckets = {}
        self.map_lock = threading.Lock() # Protects the dictionary structure

    def allow_request(self, key: str) -> bool:
        # 1. Check if bucket exists
        if key not in self.buckets:
            # 2. Thread-safe creation (Double-Checked Locking)
            with self.map_lock:
                if key not in self.buckets:
                    self.buckets[key] = Bucket(self.capacity, self.refill_rate)
        
        # 3. Delegate to the specific user's bucket
        return self.buckets[key].allow_request()
```

#### C. The Service (Facade) & Driver Code

Python

```python
class RateLimiterService:
    def __init__(self, strategy: RateLimiterStrategy):
        self.strategy = strategy

    def access_resource(self, user_id):
        allowed = self.strategy.allow_request(user_id)
        status = "Allowed" if allowed else "Denied (Throttled)"
        print(f"[{time.strftime('%X')}] User {user_id}: {status}")
        return allowed

# --- Driver Code ---
if __name__ == "__main__":
    # Config: 2 tokens max capacity, refills 1 token every second
    strategy = TokenBucketLimiter(capacity=2, refill_rate=1)
    service = RateLimiterService(strategy)
    
    user = "User_123"

    print("--- Burst 1 (Should pass 2) ---")
    service.access_resource(user) # Allowed (1 left)
    service.access_resource(user) # Allowed (0 left)
    
    print("--- Burst 2 (Should fail) ---")
    service.access_resource(user) # Denied
    
    print("--- Waiting 2 seconds (Refill) ---")
    time.sleep(2.1)
    
    print("--- Burst 3 (Should pass) ---")
    service.access_resource(user) # Allowed
```

***

#### 5. Key Design Decisions & Interview Q\&A

#### 1. Handling Concurrency

* Question: Why did you use locks inside the `Bucket` class?
* Answer: Granularity.
  * If I locked the entire `TokenBucketLimiter` class (the dictionary), User A's request would block User B's request.
  * By locking only the `Bucket` instance, User A only contends with other requests from User A. This allows high throughput for the system overall.

#### 2. Token Bucket vs Leaky Bucket

* Question: Why Token Bucket?
* Answer: Token Bucket allows Bursts. In real-world web usage, a user loading a page might fire 10 simultaneous API calls. Token Bucket allows this (up to capacity). Leaky Bucket enforces a rigid constant rate (queueing or dropping burst requests), which can degrade UX for innocent bursts.

#### 3. Distributed Rate Limiting (Scaling Up)

* Question: How does this change if we have 5 servers behind a Load Balancer?
* Answer: In-memory dictionaries won't work (requests are split across servers).
  * We move the state to a centralized store like Redis.
  * We use Lua Scripts in Redis to perform the "Check -> Decrement" operation atomically.
  * Algorithm: `redis.eval('local curr = redis.call("get", key); if curr > 0 then ...')`.

#### 4. Memory Cleanup

* Question: `self.buckets` grows forever. How do you fix memory leaks?
* Answer: We need a cleanup strategy.
  * TTL (Time To Live): If using Redis, keys expire automatically.
  * Background Worker: A thread runs every few minutes to delete `Bucket` objects where `last_refill_timestamp` is older than 1 hour.
  * LRU Cache: Implement `self.buckets` as an LRU Cache (e.g., Python `functools.lru_cache` or `OrderedDict`) with a max size.

Would you like to move on to "Design a Splitwise (Expense Sharing App)" or "Design a Notification System"?



## Thread Pool



An end-to-end Low Level Design (LLD) for a Custom Thread Pool.

This is a fundamental concurrency problem. It tests your understanding of OS Concepts (Threads vs. Processes), Synchronization, and the Producer-Consumer Pattern.

***

#### 1. Requirements Clarification

* Goal: Create a container that manages a fixed number of threads to execute tasks concurrently.
* Why? Creating and destroying threads is expensive (OS overhead). A pool reuses threads.
* Core Features:
  * Fixed Size: `n` threads running in the background.
  * Task Queue: A buffer to hold tasks when all threads are busy.
  * Non-blocking Submission: `submit(task)` should return immediately, not wait for execution.
  * Shutdown: Ability to stop accepting new tasks and shut down workers gracefully.

***

#### 2. Core Architecture: Producer-Consumer

The Thread Pool is a classic implementation of the Producer-Consumer pattern.

* Producers: The main program calling `pool.submit(task)`.
* Buffer: A thread-safe Blocking Queue.
* Consumers: The "Worker Threads" that loop infinitely, pulling tasks from the queue.

***

#### 3. Class Diagram

Code snippet

<figure><img src="../../../.gitbook/assets/image (175).png" alt=""><figcaption></figcaption></figure>

***

#### 4. Python Implementation

We will use Python's `threading` and `queue` modules. The `queue.Queue` class is already thread-safe (it uses locks/semaphores internally), which simplifies the code significantly.

#### A. The Worker (The Consumer)

This is a wrapper around a system thread. It runs an infinite loop.

Python

```python
import threading
import queue
import time

class Worker(threading.Thread):
    def __init__(self, task_queue, id):
        super().__init__()
        self.task_queue = task_queue
        self.id = id
        # Daemon threads die automatically when the main program exits
        # However, for a proper pool, we usually manage shutdown explicitly.
        self.daemon = True 

    def run(self):
        while True:
            try:
                # 1. Block until a task is available
                # timeout is optional, but good for debugging/checking shutdown flags
                func, args = self.task_queue.get(block=True)

                # 2. Check for "Poison Pill" (Shutdown Signal)
                if func is None:
                    # Signal to queue that we are done processing this 'None' task
                    self.task_queue.task_done() 
                    print(f"Worker-{self.id} shutting down.")
                    break 

                # 3. Execute Task
                print(f"Worker-{self.id} starting task.")
                try:
                    func(*args)
                except Exception as e:
                    print(f"Worker-{self.id} error: {e}")
                
                # 4. Mark complete
                self.task_queue.task_done()
                
            except queue.Empty:
                continue
```

#### B. The Thread Pool (The Manager)

This manages the lifecycle of the workers.

Python

```python
class ThreadPool:
    def __init__(self, num_threads):
        self.num_threads = num_threads
        self.task_queue = queue.Queue(maxsize=0) # Infinite queue
        self.workers = []
        self.is_shutdown = False

        # Create and start workers immediately
        for i in range(self.num_threads):
            worker = Worker(self.task_queue, i)
            worker.start()
            self.workers.append(worker)

    def submit(self, func, *args):
        if self.is_shutdown:
            raise RuntimeError("Cannot submit to a shutdown pool")
        
        # Add task to the queue
        self.task_queue.put((func, args))
        print(f"Task submitted. Queue size: {self.task_queue.qsize()}")

    def wait_completion(self):
        """Blocks until all tasks in the queue are processed."""
        self.task_queue.join()

    def shutdown(self):
        """Gracefully shuts down the pool."""
        self.is_shutdown = True
        
        # Method: The Poison Pill
        # We insert 'None' into the queue for EACH worker.
        # When a worker pulls 'None', it knows to break its loop.
        for _ in range(self.num_threads):
            self.task_queue.put((None, None))
        
        # Wait for all threads to finish
        for worker in self.workers:
            worker.join()
        
        print("ThreadPool shutdown complete.")
```

#### C. Driver Code

Python

```python
# A dummy task to simulate work
def heavy_task(name, duration):
    time.sleep(duration)
    print(f"Task '{name}' finished.")

if __name__ == "__main__":
    # Create pool with 3 threads
    pool = ThreadPool(3)

    # Submit 5 tasks
    # Since we have 3 threads, tasks 1-3 start immediately.
    # Tasks 4-5 wait in the queue.
    for i in range(5):
        pool.submit(heavy_task, f"Job-{i}", 1)

    # Wait for completion
    pool.wait_completion()
    
    # Shut down
    pool.shutdown()
```

***

#### 5. Key Design Decisions & Interview Talking Points

#### 1. The "Poison Pill" (Graceful Shutdown)

* Problem: How do you kill a thread that is blocked on `queue.get()`? You can't force-kill a thread safely in most languages (it might leave resources/locks open).
* Solution: Insert a special sentinel value (like `None` or a `StopSignal` object) into the queue.
* Logic: When a worker pulls this value, it realizes "The Manager wants me to stop," breaks its loop, and exits cleanly. You must push exactly $$ $N$ $$ pills for $$ $N$ $$ threads.

#### 2. Blocking Queue

* Why: We use a blocking queue so that if there are no tasks, the worker threads go to sleep (Waiting State).
* Benefit: This saves CPU. If we used a standard list and a `while` loop, the threads would "spin" (busy wait), consuming 100% CPU checking for new work.

#### 3. Future / Promise (Returning Results)

* The implementation above is "Fire and Forget" (void return).
* Interview Question: "How would you return a value?"
* Answer: The `submit()` method should return a `Future` object immediately.
  * The `Future` has a `.get()` method that blocks.
  * The `Worker` calculates the result and sets it inside the `Future`.
  * Any thread waiting on `.get()` is then notified (using a Condition Variable).

#### 4. Ideal Pool Size

* CPU-Bound Tasks: Size = Number of Cores (e.g., 4 or 8). Adding more threads just increases context-switching overhead without performance gain.
* I/O-Bound Tasks (Web scraping, DB calls): Size can be much larger (e.g., 50 or 100). Threads spend most of their time waiting for network responses, so one core can manage many waiting threads efficiently.

#### 5. Rejection Policy

* Question: What if the queue gets full? (If `maxsize` was set).
* Answer: We need a Rejection Policy.
  1. Abort: Throw an exception.
  2. CallerRuns: The main thread (producer) runs the task itself (slowing down submission).
  3. Discard: Throw away the oldest or newest task.

Would you like to move on to "Design a Pub-Sub System (like Kafka)" or "Design a Splitwise"?



## OYO/Airbnb - Part 1: Database Modelling



An end-to-end Low Level Design (LLD) for a Hotel Booking System (like OYO or Airbnb).

This problem focuses heavily on Data Modeling, Date Handling, and most importantly, Concurrency Control (preventing double bookings).

***

#### 1. Requirements Clarification

* Actors:
  * Guest: Searches for hotels, views details, books rooms.
  * Host/Admin: Adds hotels, manages rooms/inventory.
  * System: Manages bookings, payments, and notifications.
* Core Use Cases:
  * Search: Find hotels by City + Date Range.
  * Book: Reserve a specific room type for specific dates.
  * Cancel: Cancel a booking and free up inventory.
* Critical Constraint:
  * No Double Bookings: Two users cannot book the same room for the same date.
  * Scalability: Search should be fast.

***

#### 2. Core Entities & Relationships

1. Hotel: The physical property (Name, Address, Rating).
2. Room: A specific unit (Room 101) or a Category (Deluxe). For Airbnb, it's usually a specific unit. For OYO, it's often Categories. We will design for Room Instances (e.g., Room 101, 102) to make availability logic clear.
3. Booking: Connects a User, a Room, and a Date Range.
4. InventoryManager: The engine that checks if dates are free.

***

#### 3. Class Diagram

Code snippet

<figure><img src="../../../.gitbook/assets/image (176).png" alt=""><figcaption></figcaption></figure>

***

#### 4. Python Implementation

#### A. Basic Models (Hotel & Room)

The core logic for "Is this room free?" lives inside the `Room` class.

Python

```python
from datetime import date, datetime
import threading
from enum import Enum

class RoomType(Enum):
    STANDARD = 1
    DELUXE = 2
    SUITE = 3

class Address:
    def __init__(self, city, street):
        self.city = city
        self.street = street

class Room:
    def __init__(self, room_id, room_type, price):
        self.id = room_id
        self.type = room_type
        self.price = price
        self.bookings = [] # List of confirmed bookings
        self.lock = threading.Lock() # For concurrency

    def is_available(self, start_date: date, end_date: date) -> bool:
        # Check if the requested dates overlap with ANY existing booking
        for booking in self.bookings:
            # Overlap Logic: 
            # (Requested Start < Existing End) AND (Requested End > Existing Start)
            if start_date < booking.end_date and end_date > booking.start_date:
                return False
        return True

    def book(self, booking):
        # This method assumes validation is done beforehand or inside a lock manager
        self.bookings.append(booking)

class Hotel:
    def __init__(self, hotel_id, name, address: Address):
        self.id = hotel_id
        self.name = name
        self.address = address
        self.rooms = []

    def add_room(self, room: Room):
        self.rooms.append(room)

    def get_available_rooms(self, start_date, end_date):
        available = []
        for room in self.rooms:
            if room.is_available(start_date, end_date):
                available.append(room)
        return available
```

#### B. The Booking Manager (The Controller)

This handles the high-level logic and the critical "Check-Then-Act" race condition.

Python

```python
class BookingStatus(Enum):
    CONFIRMED = 1
    CANCELLED = 2

class Booking:
    def __init__(self, booking_id, user, room, start_date, end_date):
        self.id = booking_id
        self.user = user
        self.room = room
        self.start_date = start_date
        self.end_date = end_date
        self.status = BookingStatus.CONFIRMED

class OYOSystem:
    def __init__(self):
        self.hotels = {} # Map id -> Hotel
        self.booking_counter = 1

    def add_hotel(self, hotel):
        self.hotels[hotel.id] = hotel

    def search_hotels(self, city, start_date, end_date):
        results = []
        for hotel in self.hotels.values():
            if hotel.address.city.lower() == city.lower():
                if len(hotel.get_available_rooms(start_date, end_date)) > 0:
                    results.append(hotel)
        return results

    def create_booking(self, user, room, start_date, end_date):
        # CRITICAL SECTION: Preventing Double Booking
        # In a real distributed system, we would use a Redis Lock (Redlock)
        # or a Database Row Lock (SELECT ... FOR UPDATE).
        # Here, we use a Python thread lock on the Room object.
        
        with room.lock:
            if room.is_available(start_date, end_date):
                new_booking = Booking(self.booking_counter, user, room, start_date, end_date)
                room.book(new_booking)
                self.booking_counter += 1
                print(f"Success: Booking confirmed for {user} in Room {room.id}")
                return new_booking
            else:
                print(f"Fail: Room {room.id} is already booked for these dates.")
                return None
```

#### C. Driver Code

Python

```python
if __name__ == "__main__":
    system = OYOSystem()

    # 1. Setup Data
    hotel_bangalore = Hotel(1, "OYO Townhouse", Address("Bangalore", "Indiranagar"))
    r101 = Room(101, RoomType.STANDARD, 2000)
    r102 = Room(102, RoomType.DELUXE, 3000)
    hotel_bangalore.add_room(r101)
    hotel_bangalore.add_room(r102)
    
    system.add_hotel(hotel_bangalore)

    # 2. Dates
    d1 = date(2023, 12, 25) # Xmas
    d2 = date(2023, 12, 27)

    # 3. Search
    print("--- Searching Hotels in Bangalore ---")
    options = system.search_hotels("Bangalore", d1, d2)
    for h in options:
        print(f"Found: {h.name}")

    # 4. Concurrent Booking Test
    # Two users try to book Room 101 at the exact same time
    u1 = "User_A"
    u2 = "User_B"

    t1 = threading.Thread(target=system.create_booking, args=(u1, r101, d1, d2))
    t2 = threading.Thread(target=system.create_booking, args=(u2, r101, d1, d2))

    t1.start()
    t2.start()
    
    t1.join()
    t2.join()
    
    # Result: Only one should succeed.
```

***

#### 5. Key Design Challenges & Interview Answers

#### 1. Handling Concurrency (Double Booking)

* Problem: User A and User B check availability. Both see "Free". Both pay. Both Book.
* LLD Solution (Single Machine): `synchronized` keyword (Java) or `Lock` (Python) on the specific `Room` object.
* System Design Solution (Distributed):
  * Optimistic Locking: Add a `version` column to the Room table. When updating, check `ver = old_ver`. If it fails, retry.
  * Pessimistic Locking (Better for bookings): Use SQL `SELECT ... FOR UPDATE` when reading the room row. This locks the row until the transaction completes.

#### 2. Search Optimization

* Problem: Iterating through all hotels to check availability (`is_available`) is slow ($$ $O(N)$ $$).
* Solution: Invert the index. Maintain an Availability Table.
  * Table: `RoomAvailability (room_id, date, status)`.
  * Query: `SELECT room_id FROM RoomAvailability WHERE date IN (d1, d2) AND status = 'FREE'`.
  * Even better: Use Elasticsearch or Geospatial Indexes (QuadTrees) to filter by Location first, then filter by Availability.

#### 3. Dynamic Pricing

* Add a `PricingStrategy` interface (Strategy Pattern).
  * `BasePriceStrategy`
  * `DemandSurgeStrategy` (if booked % > 80, price \* 1.5)
  * `SeasonalStrategy` (if month == Dec, price \* 2)

#### 4. Scalability (Hotels vs Rooms)

* Airbnb lists unique homes (Room = Hotel essentially).
* OYO lists Hotels with types.
* The design above uses `Room` as a unique unit (Room 101). If OYO has 100 "Standard Rooms" identical to each other, we can model this as `RoomTypeInventory` with a count (e.g., `count=100`) instead of 100 row objects. This saves DB space.

Would you like to continue with "Design a Parking Lot" (classic) or "Design a Tic-Tac-Toe Game"?

## Google Calendar Database Model



An end-to-end Low Level Design (LLD) for a Calendar System (like Google Calendar or Outlook).

This problem is a test of Data Modeling (how to represent time) and Algorithms (how to efficiently detect overlaps and manage recurring events).

***

#### 1. Requirements Clarification

* Core Entities: Users, Events, Calendars.
* Event Properties: Title, Start Time, End Time, Location, Attendees.
* Core Operations:
  * Create, Update, Delete events.
  * Get Events: View a day/week/month.
  * Conflict Detection: Check if a user is free during a specific time slot.
* Advanced Features (Bonus):
  * Recurring Events: (e.g., "Every Monday").
  * Invites: Adding other users to an event.
  * Reminders: Notification logic.

***

#### 2. Core Concept: Handling Time

Time is tricky. To ensure the system works globally:

1. Storage: Always store time in UTC (Epoch timestamps).
2. Display: Convert to the User's Local Timezone only at the UI layer.
3. Intervals: We need a robust class to handle time ranges and overlap logic.

***

#### 3. Class Diagram

We will use a Service-Oriented Architecture. The `CalendarService` acts as the facade.

Code snippet

<figure><img src="../../../.gitbook/assets/image (177).png" alt=""><figcaption></figcaption></figure>

***

#### 4. Python Implementation

#### A. The TimeSlot (Interval Logic)

This helper class encapsulates the math for checking if two events clash.

Python

```python
from datetime import datetime, timedelta

class TimeSlot:
    def __init__(self, start: datetime, end: datetime):
        if start >= end:
            raise ValueError("Start time must be before end time")
        self.start = start
        self.end = end

    def overlaps(self, other: 'TimeSlot') -> bool:
        # Overlap logic: StartA < EndB AND EndA > StartB
        return self.start < other.end and self.end > other.start

    def __repr__(self):
        return f"[{self.start.strftime('%H:%M')} - {self.end.strftime('%H:%M')}]"
```

#### B. The Event & User

Basic entities.

Python

```python
import uuid

class User:
    def __init__(self, email, name):
        self.id = email  # using email as ID for simplicity
        self.name = name
    
    def __repr__(self):
        return self.name

class Event:
    def __init__(self, title, start, end, owner, attendees=None):
        self.id = str(uuid.uuid4())
        self.title = title
        self.slot = TimeSlot(start, end)
        self.owner = owner
        self.attendees = attendees if attendees else []
    
    def __repr__(self):
        return f"'{self.title}' {self.slot}"
```

#### C. The Calendar

Represents one user's schedule. Note the `check_conflict` method.

Python

```python
class Calendar:
    def __init__(self, owner: User):
        self.owner = owner
        # In a real DB, this would be indexed. 
        # In memory, we keep it sorted by start time.
        self.events = [] 

    def add_event(self, event: Event):
        self.events.append(event)
        # Keep sorted for efficient viewing
        self.events.sort(key=lambda x: x.slot.start)

    def check_conflict(self, new_slot: TimeSlot) -> bool:
        for event in self.events:
            if event.slot.overlaps(new_slot):
                return True
        return False

    def get_events_in_range(self, start: datetime, end: datetime):
        query_slot = TimeSlot(start, end)
        return [e for e in self.events if e.slot.overlaps(query_slot)]
```

#### D. The Service (Controller)

Handles the logic of inviting people (adding events to multiple calendars).

Python

```python
class CalendarService:
    def __init__(self):
        self.calendars = {} # Map UserID -> Calendar

    def get_calendar(self, user: User):
        if user.id not in self.calendars:
            self.calendars[user.id] = Calendar(user)
        return self.calendars[user.id]

    def create_event(self, owner: User, title, start, end, attendees=None):
        attendees = attendees or []
        
        # 1. Basic validation: Check if owner is free
        owner_cal = self.get_calendar(owner)
        new_slot = TimeSlot(start, end)
        
        if owner_cal.check_conflict(new_slot):
            print(f"Failed to create '{title}': Owner {owner.name} has a conflict.")
            return None

        # 2. Check attendees conflicts (Optional: depending on requirements, might allow overlap)
        for attendee in attendees:
            att_cal = self.get_calendar(attendee)
            if att_cal.check_conflict(new_slot):
                print(f"Warning: Attendee {attendee.name} has a conflict for '{title}'.")

        # 3. Create the Event
        event = Event(title, start, end, owner, attendees)
        
        # 4. Add to Owner's Calendar
        owner_cal.add_event(event)

        # 5. Add to Attendees' Calendars (Shared Reference)
        for attendee in attendees:
            self.get_calendar(attendee).add_event(event)

        print(f"Event '{title}' created successfully.")
        return event

    def show_schedule(self, user, date):
        # Define the range for the "Day View"
        start_of_day = datetime(date.year, date.month, date.day, 0, 0)
        end_of_day = start_of_day + timedelta(days=1)
        
        cal = self.get_calendar(user)
        events = cal.get_events_in_range(start_of_day, end_of_day)
        
        print(f"Schedule for {user.name} on {date.date()}:")
        if not events:
            print("  No events.")
        for e in events:
            print(f"  {e}")
```

#### E. Driver Code

Python

```python
if __name__ == "__main__":
    service = CalendarService()
    
    # 1. Create Users
    u1 = User("alice@google.com", "Alice")
    u2 = User("bob@google.com", "Bob")

    # 2. Define Times
    # Today 10:00 AM to 11:00 AM
    now = datetime.now()
    start1 = now.replace(hour=10, minute=0, second=0, microsecond=0)
    end1 = start1 + timedelta(hours=1)
    
    # Today 10:30 AM to 11:30 AM (Overlaps with above)
    start2 = start1 + timedelta(minutes=30)
    end2 = start2 + timedelta(hours=1)

    # 3. Create Event 1
    service.create_event(u1, "Team Standup", start1, end1, attendees=[u2])
    
    # 4. Try creating overlapping event for Alice
    service.create_event(u1, "Client Call", start2, end2) # Should fail

    # 5. Show Schedule
    service.show_schedule(u1, now)
    service.show_schedule(u2, now)
```

***

#### 5. Key Interview Design Challenges

#### 1. Recurring Events ("Every Monday")

* Naive Approach: Create 52 separate event objects for the next year.
  * _Problem:_ Waste of space. If user changes the title, you have to update 52 objects.
* Smart Approach (RRULE): Store the event once with a Recurrence Rule (RFC 5545 standard).
  * Example Rule: `FREQ=WEEKLY;BYDAY=MO`
  * Retrieval: When `get_events(Jan 1, Jan 31)` is called, the system dynamically calculates the "Virtual Events" generated by the rule that fall in that window.

#### 2. Timezone Management

* Problem: Alice (New York) creates an event at 9 AM EST. Bob (London) views it.
* Solution: Store `9 AM EST` as `14:00 UTC` in the database. When Bob views it, convert `14:00 UTC` + `0 hours` (London/GMT) = `2 PM`.

#### 3. Efficient Search (Data Structures)

* Problem: `get_events` using a simple list is $$ $O(N)$ $$.
* Solution: Use an Interval Tree or Segment Tree.
  * This allows finding all intervals overlapping a specific range in $$ $O(\log N + K)$ $$ time (where K is the number of results).
  *   In a relational DB (SQL), we use:

      SQL

      ```
      SELECT * FROM events 
      WHERE start_time < query_end 
      AND end_time > query_start;
      ```

      (Composite index on `start_time` helps, but interval queries are notoriously hard to optimize perfectly in B-Trees).

#### 4. Meeting Scheduler (Find Free Slots)

* Feature: "Find a time where Alice, Bob, and Charlie are all free."
* Algorithm:
  1. Fetch sorted event intervals for all 3 users.
  2. Merge intervals to find "Busy Blocks" (like the "Merge Intervals" LeetCode problem).
  3. Invert the "Busy Blocks" to find "Free Blocks".
  4. Return blocks where `duration >= requested_duration`.

Would you like to explore "Design an Elevator System" (State Machine) or "Design a Chat Application (WhatsApp)"?



## Google Authenticator



An end-to-end Low Level Design (LLD) for a Google Authenticator system.

This is a unique Machine Coding problem because it is less about "Architecture" and almost entirely about Cryptography and the RFC 6238 (TOTP) standard. The "magic" is that the Client (App) and Server generate the same 6-digit code without talking to each other.

***

#### 1. Requirements Clarification

* Goal: Generate and Validate 2-Factor Authentication (2FA) codes.
* Core Mechanism: TOTP (Time-based One-Time Password).
* Setup: The server generates a Secret Key, shares it with the User (usually via QR Code), and both save it.
* Operation:
  * App: Uses `Secret + Current Time` $$ $\rightarrow$ $$ Generates 6-digit code.
  * Server: Uses `Stored Secret + Current Time` $$ $\rightarrow$ $$ Generates code $$ $\rightarrow$ $$ Compares with User Input.
* Constraints:
  * Code changes every 30 seconds.
  * Must handle slight time differences (clock drift) between phone and server.

***

#### 2. The Algorithm (The "Secret Sauce")

You cannot design this without knowing the math. Google Authenticator uses HMAC-SHA1.

1. Time Step ($$ $T$ $$): $$ $\lfloor \frac{\text{Current Unix Time}}{30} \rfloor$ $$. This ensures the input only changes every 30 seconds.
2. Hashing: Calculate `HMAC-SHA1(Secret_Key, T)`. This gives a 20-byte hash.
3. Dynamic Truncation:
   * Take the _last byte_ of the hash.
   * Extract the last 4 bits to get an `offset` (0 to 15).
   * Take 4 bytes from the hash starting at `offset`.
4. Modulus: Convert those 4 bytes to an integer, remove the sign bit, and do `% 1,000,000` to get the 6 digits.

***

#### 3. Class Diagram

Code snippet

<figure><img src="../../../.gitbook/assets/image (178).png" alt=""><figcaption></figcaption></figure>

***

#### 4. Python Implementation

We need `hmac`, `struct` (for binary manipulation), and `base64` (Google Auth uses Base32 encoding for secrets).

**A. The Math Core (RFC 6238)**

Python

```python
import hmac
import hashlib
import struct
import time
import base64

class TOTPAlgorithm:
    def __init__(self, interval=30, digits=6):
        self.interval = interval
        self.digits = digits

    def generate_code(self, secret_base32: str, timestamp: float = None) -> str:
        if timestamp is None:
            timestamp = time.time()
            
        # 1. Calculate Time Counter (T)
        # Allows inputs to be stable for 'interval' seconds
        time_counter = int(timestamp // self.interval)
        
        # 2. Decode the Base32 Secret to bytes
        # Padding correction is often needed for Base32
        secret_bytes = self._decode_base32(secret_base32)
        
        # 3. Pack Time Counter into 8 bytes (Big Endian)
        # RFC requirement: The time must be an 8-byte bytearray
        time_bytes = struct.pack(">Q", time_counter)
        
        # 4. HMAC-SHA1 Calculation
        hmac_hash = hmac.new(secret_bytes, time_bytes, hashlib.sha1).digest()
        
        # 5. Dynamic Truncation
        # Get the last byte
        last_byte = hmac_hash[-1]
        # Get the last 4 bits (0x0F) to determine offset
        offset = last_byte & 0x0F 
        
        # Read 4 bytes starting at offset
        four_bytes = hmac_hash[offset : offset + 4]
        
        # Convert to integer (Big Endian)
        large_int = struct.unpack(">I", four_bytes)[0]
        
        # Mask the most significant bit (to ensure 31-bit positive integer)
        large_int = large_int & 0x7FFFFFFF
        
        # 6. Modulo to get N digits
        code = large_int % (10 ** self.digits)
        
        # Pad with zeros (e.g., "123" -> "000123")
        return str(code).zfill(self.digits)

    def _decode_base32(self, secret):
        # Helper to handle padding issues
        missing_padding = len(secret) % 8
        if missing_padding != 0:
            secret += '=' * (8 - missing_padding)
        return base64.b32decode(secret, casefold=True)
```

**B. The Service (Server-Side Logic)**

This handles User management and the "Window Verification" (Clock Drift).

Python

```python
class AuthenticatorService:
    def __init__(self):
        self.user_secrets = {} # Mock DB: {user_id: secret}
        self.totp = TOTPAlgorithm()

    def generate_new_secret(self, user_id):
        # Generate a random 16-byte secret encoded in Base32
        # This is what goes into the QR Code
        random_bytes = base64.b32encode(struct.pack(">Q", int(time.time() * 1000))) # simplified randomness
        secret = random_bytes.decode('utf-8').replace('=', '')
        
        self.user_secrets[user_id] = secret
        print(f"Assigning Secret to {user_id}: {secret}")
        return secret

    def verify_code(self, user_id, input_code):
        if user_id not in self.user_secrets:
            print("User not found.")
            return False

        secret = self.user_secrets[user_id]
        
        # WINDOW VERIFICATION (Crucial for Distributed Systems)
        # The user's phone might be 20 seconds behind or ahead.
        # We check: Current Time (T), T-1 (30s ago), and T+1 (30s future)
        
        current_time = time.time()
        
        # Check T, T-1, T+1
        for i in range(-1, 2):
            test_time = current_time + (i * 30)
            calculated_code = self.totp.generate_code(secret, test_time)
            
            if calculated_code == input_code:
                print(f"Success: Code matched (Window: {i})")
                return True
                
        print(f"Fail: Expected {self.totp.generate_code(secret, current_time)}, got {input_code}")
        return False
```

**C. Driver Code (Simulation)**

Python

```python
if __name__ == "__main__":
    service = AuthenticatorService()
    
    # 1. Setup
    user = "alice"
    # User scans QR code containing this secret
    shared_secret = service.generate_new_secret(user) 
    
    # 2. Client Side (App) generates code
    # Let's use the same logic class to simulate the phone app
    app_simulator = TOTPAlgorithm()
    
    print("\n--- Normal Case ---")
    otp = app_simulator.generate_code(shared_secret)
    print(f"App Generated: {otp}")
    service.verify_code(user, otp)
    
    print("\n--- Clock Drift Case ---")
    # Simulate phone being 25 seconds BEHIND server
    # The server is at 'now', phone is at 'now - 25'
    drifted_time = time.time() - 25
    drifted_otp = app_simulator.generate_code(shared_secret, drifted_time)
    
    print(f"Phone (Lagging) Generated: {drifted_otp}")
    # Verify should still pass because of the +/- 1 window in verify_code
    service.verify_code(user, drifted_otp)
```

***

#### 5. Key Design Details (Interview Gold)

**1. Why Base32?**

You will be asked: "Why not Base64?"

* Answer: Base32 only uses `A-Z` and `2-7`. It avoids easily confused characters (like `1`, `I`, `l` or `0`, `O`) which is crucial if a user has to manually type the secret key (Backup Key) instead of scanning a QR code. It is also case-insensitive.

**2. Clock Drift (Synchronization)**

* Problem: The user's phone is in Airplane mode and the clock drifted by 1 minute.
* Solution: The `verify_code` loop (seen in the code above). Standard servers check a window of `[-1, 0, +1]` intervals. If the user is successfully verified at window `-1`, the server can store this "drift" and apply it to future requests for that user to improve UX.

**3. Replay Attacks**

* Problem: An attacker intercepts a valid code and uses it immediately.
* Solution: The server should store `last_used_interval` for the user. If `current_interval == last_used_interval`, reject the request. A TOTP code should be single-use.

**4. QR Code Format**

*   The "QR Code" is simply a URI scheme:

    otpauth://totp/Google:Alice?secret=JBSWY3DPEHPK3PXP\&issuer=Google

    * Secret: The Base32 key.
    * Issuer: Who is asking for the code (e.g., Gmail).

**5. Backup Codes**

* If the user loses the phone, they lose the Secret.
* Design: When generating the secret, also generate 10 random 8-digit "Scratch Codes", store them in the DB (hashed), and display them to the user once. These are one-time use overrides.

#### Next Step

Would you like to solve "Design a URL Shortener (TinyURL)" or move to a complex game design like "Design Chess"?



## Amazon Prime Video



An end-to-end Low Level Design (LLD) for a Video Streaming Service (Amazon Prime Video / Netflix).

This problem tests your ability to model Hierarchical Data (Series $$ $\rightarrow$ $$ Season $$ $\rightarrow$ $$ Episode), manage Access Control (Prime vs. Rent), and understand how modern video delivery works (Adaptive Bitrate Streaming).

***

#### 1. Requirements Clarification

* Content Types: Movies (Standalone) and TV Shows (Nested: Show $$ $\rightarrow$ $$ Seasons $$ $\rightarrow$ $$ Episodes).
* Playback: Users select a resolution (or Auto) and stream video.
* Entitlement (Monetization):
  * SVOD (Subscription): Free for Prime members.
  * TVOD (Transactional): Rent or Buy specific movies.
* User Experience:
  * Watch History: Resume playback from where you left off.
  * Search: Find content by title/genre.

***

#### 2. Core Concept: Adaptive Streaming (HLS/DASH)

In a real system, you do not stream one big MP4 file. You stream a Manifest File (.m3u8) which points to thousands of tiny chunks (.ts files) of video.

* The LLD Implication: Your `Video` object doesn't hold `byte[] data`. It holds a `Map<Resolution, URL>` pointing to a CDN (Content Delivery Network).

***

#### 3. Class Diagram

We use the Composite Pattern concept for the Movie/Series hierarchy and the Strategy Pattern (implicit) for Entitlement checks.

Code snippet

<figure><img src="../../../.gitbook/assets/image (179).png" alt=""><figcaption></figcaption></figure>

***

#### 4. Python Implementation

#### A. The Data Models (Content Hierarchy)

We need a flexible structure. `Movie` and `Episode` are "Playable", but `TVShow` is just a container.

Python

```python
from abc import ABC
from enum import Enum
from datetime import datetime

class Resolution(Enum):
    SD_480 = "480p"
    HD_720 = "720p"
    FHD_1080 = "1080p"

class VideoType(Enum):
    MOVIE = 1
    SERIES = 2

# Abstract Base Class for common metadata
class Content(ABC):
    def __init__(self, id, title, genre):
        self.id = id
        self.title = title
        self.genre = genre

# A generic "Playable" item (Leaf node)
class PlayableItem(Content):
    def __init__(self, id, title, genre, duration):
        super().__init__(id, title, genre)
        self.duration = duration
        self.stream_urls = {} # Map[Resolution, URL]
        self.is_prime_exclusive = True # Default to Prime
        self.rental_price = 0.0

    def add_stream_source(self, resolution: Resolution, url: str):
        self.stream_urls[resolution] = url

# 1. Movie (Standalone)
class Movie(PlayableItem):
    pass

# 2. TV Series Structure (Composite)
class Episode(PlayableItem):
    def __init__(self, id, title, genre, duration, episode_num):
        super().__init__(id, title, genre, duration)
        self.episode_num = episode_num

class Season:
    def __init__(self, season_num):
        self.season_num = season_num
        self.episodes = [] # List[Episode]

    def add_episode(self, episode: Episode):
        self.episodes.append(episode)

class TVShow(Content):
    def __init__(self, id, title, genre):
        super().__init__(id, title, genre)
        self.seasons = [] # List[Season]

    def add_season(self, season: Season):
        self.seasons.append(season)
```

#### B. User & Watch History

Python

```python
class User:
    def __init__(self, user_id, name, is_prime=False):
        self.id = user_id
        self.name = name
        self.is_prime = is_prime
        self.rentals = set() # Set of video_ids

    def rent_video(self, video_id):
        self.rentals.add(video_id)

class WatchHistoryManager:
    def __init__(self):
        # Map: user_id -> { video_id: timestamp_seconds }
        self.history = {}

    def update_progress(self, user_id, video_id, timestamp):
        if user_id not in self.history:
            self.history[user_id] = {}
        self.history[user_id][video_id] = timestamp
        print(f"Saved progress for User {user_id}: {timestamp}s on Video {video_id}")

    def get_progress(self, user_id, video_id):
        return self.history.get(user_id, {}).get(video_id, 0)
```

#### C. The Service (Controller)

This handles the critical "Can I watch this?" logic and URL fetching.

Python

```python
class PrimeVideoService:
    def __init__(self):
        self.catalog = {} # id -> Content
        self.history_mgr = WatchHistoryManager()

    def add_content(self, content):
        self.catalog[content.id] = content

    def get_stream(self, user: User, video_id: str, quality: Resolution = Resolution.HD_720):
        # 1. Fetch Content
        video = self.catalog.get(video_id)
        if not video or not isinstance(video, PlayableItem):
            raise ValueError("Video not found or not playable")

        # 2. Entitlement Check (The 'Guard')
        if not self._check_entitlement(user, video):
            raise PermissionError(f"User {user.name} is not entitled to watch '{video.title}'")

        # 3. Resume Logic
        start_time = self.history_mgr.get_progress(user.id, video_id)
        
        # 4. Return the CDN Link
        # In reality, this returns a signed URL (e.g., CloudFront with expiration)
        url = video.stream_urls.get(quality, "default_stream.m3u8")
        
        print(f"Starting playback: '{video.title}' at {start_time}s [{quality.value}]")
        return {"url": url, "start_at": start_time}

    def _check_entitlement(self, user: User, video: PlayableItem):
        # Logic: 
        # 1. If user rented it -> OK
        # 2. If it's Free (Prime) AND User is Prime -> OK
        # 3. Otherwise -> Block
        
        if video.id in user.rentals:
            return True
        
        if video.is_prime_exclusive and user.is_prime:
            return True
            
        if not video.is_prime_exclusive and video.rental_price == 0:
            return True # Free for everyone
            
        return False
```

#### D. Driver Code

Python

```python
if __name__ == "__main__":
    service = PrimeVideoService()

    # 1. Content Creation
    inception = Movie("mv_001", "Inception", "Sci-Fi", 148)
    inception.add_stream_source(Resolution.HD_720, "http://cdn.prime.com/inception_720.m3u8")
    inception.is_prime_exclusive = True 
    
    # A generic TV Show
    friends = TVShow("tv_001", "Friends", "Comedy")
    s1 = Season(1)
    ep1 = Episode("ep_001", "The One Where it Started", "Comedy", 22, 1)
    ep1.add_stream_source(Resolution.HD_720, "http://cdn.prime.com/friends_s1e1.m3u8")
    s1.add_episode(ep1)
    friends.add_season(s1)

    service.add_content(inception)
    service.add_content(ep1) # Add individual episode to catalog for direct access

    # 2. User Scenarios
    alice = User("u1", "Alice", is_prime=False)
    bob = User("u2", "Bob", is_prime=True)

    # 3. Playback Tests
    print("--- Scenario 1: Alice (Non-Prime) tries to watch Inception (Prime) ---")
    try:
        service.get_stream(alice, "mv_001")
    except PermissionError as e:
        print(f"Block: {e}")

    print("\n--- Scenario 2: Bob (Prime) watches Inception ---")
    stream_info = service.get_stream(bob, "mv_001")
    # Simulate watching for 10 minutes
    service.history_mgr.update_progress(bob.id, "mv_001", 600)

    print("\n--- Scenario 3: Bob Resumes Inception ---")
    stream_info_2 = service.get_stream(bob, "mv_001")
    # Should show start_at: 600
```

***

#### 5. Key Design Challenges & Interview Answers

#### 1. Handling "Resume Watching" at Scale

* Problem: If 10 million people pause every second, writing to the DB (`UPDATE history SET time=...`) is too heavy.
* Solution: Write-Back Cache (Redis).
  * The frontend sends a "heartbeat" every 10 seconds with current progress.
  * Server updates Redis: `SET user:video:timestamp`.
  * A background worker flushes Redis to the permanent DB (Cassandra/DynamoDB) every few minutes or on session close.

#### 2. Search (Elasticsearch)

* You cannot search efficiently in a standard SQL DB with queries like "Action movies with Tom Cruise".
* Use an Inverted Index (Elasticsearch/Solr).
  * Document: `{ "id": "mv_001", "title": "Mission Impossible", "cast": ["Tom Cruise"], "genre": "Action" }`
  * Search is fuzzy and extremely fast.

#### 3. CDN & Geo-Location

* Question: Why does the video load fast in India but the server is in the US?
* Answer: The `get_stream_url` method doesn't return a file on the app server. It returns a URL to a CDN (like CloudFront or Akamai) edge server. The CDN replicates the video file to a server physically close to the user (e.g., Mumbai).

#### 4. DRM (Digital Rights Management)

* To prevent users from downloading the `.m3u8` stream and converting it to MP4, we use Encrypted Streams (Widevine, FairPlay).
* The Player needs a License Key to decrypt the video chunks. The `EntitlementService` issues this token only if the user has rights.

#### 5. Recommendation System

* This is usually a separate Microservice ("Personalization Service").
* It consumes the `WatchHistory` events via a message queue (Kafka) and updates a User-Item Matrix to generate suggestions (Collaborative Filtering).

Would you like to try "Design a Food Delivery App (Swiggy/Zomato)" or "Design a Payment Gateway" next?



## Online Book Management System



An end-to-end Low Level Design (LLD) for an Online Book Management System (Library Management System).

This is a classic Object-Oriented Design (OOD) question. The main challenge is distinguishing between the Metadata (The abstract concept of the book "Harry Potter") and the Inventory (The 5 physical copies of "Harry Potter" on the shelf).

***

#### 1. Requirements Clarification

* Actors:
  * Librarian (Admin): Add/Remove books, Block members.
  * Member: Search, Borrow, Return, Reserve books.
  * System: Notifications, Fine calculation.
* Core Use Cases:
  * Search: By Title, Author, or Category.
  * Check-out (Borrow): Validate eligibility (max limit, unpaid fines), decrement inventory.
  * Return: Update inventory, calculate overdue fines.
* Constraints:
  * Each book can have multiple copies (Book Items).
  * Max 5 books per member.
  * Max lending duration: 10 days.

***

#### 2. Core Entities & Relationships

1. Book (Metadata): Details like ISBN, Title, Author. Shared by all copies.
2. BookItem (Physical Copy): Specific instance with a unique Barcode and Status (Available, Loaned, Lost).
3. Account/Member: User details, list of checked-out items.
4. Catalog (Search Index): Efficiently finds books.
5. LibraryService (Facade): The main controller handling transactions.

***

#### 3. Class Diagram

Code snippet

<figure><img src="../../../.gitbook/assets/image (180).png" alt=""><figcaption></figcaption></figure>

***

#### 4. Python Implementation

#### A. The Models (Book vs BookItem)

This separation is the most important part of the design.

Python

```python
from abc import ABC
from enum import Enum
from datetime import datetime, timedelta
import uuid

class BookFormat(Enum):
    HARDCOVER = 1
    PAPERBACK = 2
    EBOOK = 3

class BookStatus(Enum):
    AVAILABLE = 1
    LOANED = 2
    RESERVED = 3
    LOST = 4

# 1. The Metadata (Flyweight Pattern context)
class Book:
    def __init__(self, isbn, title, author, subject, publisher):
        self.isbn = isbn
        self.title = title
        self.author = author
        self.subject = subject
        self.publisher = publisher

# 2. The Physical Copy
class BookItem(Book):
    def __init__(self, isbn, title, author, subject, publisher, barcode, is_reference_only=False):
        super().__init__(isbn, title, author, subject, publisher)
        self.barcode = barcode
        self.is_reference_only = is_reference_only
        self.status = BookStatus.AVAILABLE
        self.due_date = None
        self.borrowed_date = None
        self.price = 0.0  # Replacement cost

    def checkout(self):
        if self.is_reference_only:
            raise ValueError("This book is Reference Only and cannot be borrowed.")
        self.status = BookStatus.LOANED
        self.borrowed_date = datetime.now()
        # Default policy: 10 days
        self.due_date = datetime.now() + timedelta(days=10)
        return True
```

#### B. The User (Account & Member)

Python

```python
class Account(ABC):
    def __init__(self, id, password, person):
        self.id = id
        self.password = password
        self.person = person
        self.status = "Active"

class Constants:
    MAX_BOOKS_ISSUED_TO_A_USER = 5
    MAX_LENDING_DAYS = 10

class Member(Account):
    def __init__(self, id, password, person):
        super().__init__(id, password, person)
        self.total_books_checked_out = 0
        self.checked_out_items = {} # Map: barcode -> BookItem

    def get_total_books_checked_out(self):
        return self.total_books_checked_out

    def reserve_book_item(self, book_item):
        # Reservation logic
        pass

    def increment_checkout(self, book_item):
        self.checked_out_items[book_item.barcode] = book_item
        self.total_books_checked_out += 1

    def decrement_checkout(self, book_item):
        if book_item.barcode in self.checked_out_items:
            del self.checked_out_items[book_item.barcode]
            self.total_books_checked_out -= 1
```

#### C. Search Catalog (Index)

A simple list search is $$ $O(N)$ $$. A proper design uses HashMaps for $$ $O(1)$ $$ lookup.

Python

```python
class Catalog:
    def __init__(self):
        # Multiple indices for fast search
        self.book_titles = {}   # Map: Title -> List[BookItem]
        self.book_authors = {}  # Map: Author -> List[BookItem]
        self.book_subjects = {} # Map: Subject -> List[BookItem]
        self.barcodes = {}      # Map: Barcode -> BookItem (Primary Key)

    def add_book_item(self, item: BookItem):
        self.barcodes[item.barcode] = item
        
        # Add to title index
        self.book_titles.setdefault(item.title, []).append(item)
        # Add to author index
        self.book_authors.setdefault(item.author, []).append(item)

    def search_by_title(self, query):
        return self.book_titles.get(query, [])

    def search_by_author(self, query):
        return self.book_authors.get(query, [])
```

#### D. The System (Facade & Fine Logic)

This manages the business rules.

Python

```python
class LibrarySystem:
    def __init__(self):
        self.catalog = Catalog()
        self.members = {} # Map: member_id -> Member

    def add_member(self, member):
        self.members[member.id] = member

    def borrow_book(self, member_id, barcode):
        member = self.members.get(member_id)
        book_item = self.catalog.barcodes.get(barcode)

        # 1. Validations
        if not member or not book_item:
            print("Invalid member or book.")
            return False

        if book_item.status != BookStatus.AVAILABLE:
            print("Book is not available.")
            return False

        if member.get_total_books_checked_out() >= Constants.MAX_BOOKS_ISSUED_TO_A_USER:
            print("User has reached max limit.")
            return False

        # 2. Execute Transaction
        book_item.checkout()
        member.increment_checkout(book_item)
        print(f"Success: '{book_item.title}' checked out to {member.id}. Due: {book_item.due_date.date()}")
        return True

    def return_book(self, member_id, barcode):
        member = self.members.get(member_id)
        book_item = self.catalog.barcodes.get(barcode)

        if not member or not book_item: return False
        if barcode not in member.checked_out_items:
            print("This book does not belong to this user.")
            return False

        # 1. Calculate Fine
        fine = self._calculate_fine(book_item)
        if fine > 0:
            print(f"Book is overdue! Fine amount: ${fine}")
            # In real system, we would create a FineTransaction here
        else:
            print("Book returned on time. No fine.")

        # 2. Update State
        book_item.status = BookStatus.AVAILABLE
        book_item.due_date = None
        book_item.borrowed_date = None
        member.decrement_checkout(book_item)
        return True

    def _calculate_fine(self, book_item):
        if not book_item.due_date: return 0
        
        now = datetime.now()
        # Ensure we are comparing compatible types (naive vs aware). 
        # For simplicity assuming naive datetimes here.
        if now > book_item.due_date:
            delta = now - book_item.due_date
            days_overdue = delta.days
            if days_overdue > 0:
                return days_overdue * 2.0 # $2 per day
        return 0.0
```

#### E. Driver Code

Python

```python
if __name__ == "__main__":
    library = LibrarySystem()

    # 1. Setup Data
    item1 = BookItem("123", "The Alchemist", "Paulo Coelho", "Fiction", "Pub1", "BAR_001")
    item2 = BookItem("123", "The Alchemist", "Paulo Coelho", "Fiction", "Pub1", "BAR_002")
    item3 = BookItem("456", "Clean Code", "Uncle Bob", "Tech", "Pub2", "BAR_003")

    library.catalog.add_book_item(item1)
    library.catalog.add_book_item(item2)
    library.catalog.add_book_item(item3)

    user = Member("user1", "pass123", "John Doe")
    library.add_member(user)

    # 2. Search
    results = library.catalog.search_by_title("The Alchemist")
    print(f"Found {len(results)} copies of 'The Alchemist'")

    # 3. Borrow
    library.borrow_book("user1", "BAR_001")
    
    # 4. Try borrowing same copy again (Should fail)
    library.borrow_book("user1", "BAR_001") 

    # 5. Return (Simulate delay manually if needed, here it's instant)
    library.return_book("user1", "BAR_001")
```

***

#### 5. Key Interview Nuances

#### 1. Fine Calculation (Strategy Pattern)

* Question: What if books have different fine rates (e.g., Rare books = $10/day, Magazines = $0.50/day)?
* Design: Don't hardcode `_calculate_fine`. Use the Strategy Pattern.
  * Interface `FineStrategy` with method `calculate(days)`.
  * Strategies: `StandardFine`, `PremiumFine`.
  * The `BookItem` holds a reference to its specific strategy.

#### 2. Concurrency

* Question: Two users scan the QR code of the _same_ book item at the exact same millisecond via the mobile app.
* Solution: You need locking.
  * DB Level: `SELECT * FROM book_item WHERE barcode = 'xyz' FOR UPDATE;` (Row-level locking).
  * App Level: Python `threading.Lock()` inside the `borrow_book` method for that specific barcode.

#### 3. Notification System (Observer Pattern)

* Question: Notify user when a reserved book becomes available.
* Design: Implement Observer Pattern.
  * User subscribes to `BookMetadata`.
  * When `BookItem` status changes from `LOANED` to `AVAILABLE`, the System checks the Reservation Queue and notifies the next observer (User).

#### 4. Barcode Reading

* In a machine coding round, mentioning that `barcode` is the unique identifier (Primary Key) for `BookItem` while `ISBN` is the identifier for `Book` is a strong signal of domain knowledge.

Would you like to explore "Design a Vending Machine" (State Pattern) or "Design a Snake & Ladder Game" next?



## Lift



Here is a detailed Low Level Design (LLD) for an Elevator (Lift) System.

This is a very popular Machine Coding problem because it tests State Management, Scheduling Algorithms, and Concurrency.

***

#### 1. Requirements Clarification

* Setup: Building with $$ $N$ $$ Floors (0 to $$ $N$ $$). $$ $M$ $$ Elevators.
* Capacity: Each elevator has a max weight/person limit.
* Buttons:
  * Inside: Panel to select specific floors (1, 2, 5...).
  * Outside (Hall): Up/Down buttons on each floor.
* Behavior:
  * The elevator should not change direction violently. It should finish requests in the current direction before switching (The SCAN Algorithm).
  * Example: If going `UP` from 1 to 5, and a user at 3 presses `UP`, stop at 3. If a user at 4 presses `DOWN`, ignore them until the elevator reaches the top and starts coming down.

***

#### 2. Core Algorithm: The SCAN (Look) Algorithm

Naive First-Come-First-Serve (FCFS) is bad for elevators (it moves like a yo-yo).

We use the SCAN Algorithm:

1. Move in `current_direction`.
2. Stop at all requested floors in that direction.
3. If no more requests in that direction, check pending requests in the opposite direction.
4. Reverse direction.

Data Structure: Two Priority Queues.

* Up Queue (Min-Heap): Sorts stops in ascending order (1 $$ $\rightarrow$ $$ 2 $$ $\rightarrow$ $$ 5).
* Down Queue (Max-Heap): Sorts stops in descending order (10 $$ $\rightarrow$ $$ 8 $$ $\rightarrow$ $$ 3).

***

#### 3. Class Diagram

Code snippet

<figure><img src="../../../.gitbook/assets/image (181).png" alt=""><figcaption></figcaption></figure>

***

#### 4. Python Implementation

#### A. Enums and Request

Python

```python
from enum import Enum
import heapq
import time
import threading

class Direction(Enum):
    UP = 1
    DOWN = 2
    IDLE = 3

class Request:
    def __init__(self, floor):
        self.floor = floor

    def __repr__(self):
        return f"Req({self.floor})"
```

#### B. The Elevator (The Worker)

This is where the complex state logic lives. We use two heaps to manage the path.

Python

```python
class Elevator:
    def __init__(self, id, current_floor=0):
        self.id = id
        self.current_floor = current_floor
        self.direction = Direction.IDLE
        
        # Priority Queues
        # Python's heapq is a Min-Heap by default.
        # For 'up_stops', we store (floor). Min-Heap works perfectly.
        # For 'down_stops', we store (-floor) to simulate Max-Heap behavior.
        self.up_stops = [] 
        self.down_stops = [] 

    def add_request(self, floor):
        if floor == self.current_floor:
            return # Already there

        if floor > self.current_floor:
            heapq.heappush(self.up_stops, floor)
            if self.direction == Direction.IDLE:
                self.direction = Direction.UP
        else:
            heapq.heappush(self.down_stops, -floor) # Invert for Max-Heap
            if self.direction == Direction.IDLE:
                self.direction = Direction.DOWN

        print(f"Elevator {self.id} added floor {floor}. Current Dir: {self.direction.name}")

    def move(self):
        """
        Simulates one step of movement. 
        In a real system, this runs in a continuous 'while True' loop on a thread.
        """
        if self.direction == Direction.IDLE:
            print(f"Elevator {self.id} is IDLE at {self.current_floor}")
            return

        if self.direction == Direction.UP:
            self._move_up()
        elif self.direction == Direction.DOWN:
            self._move_down()

    def _move_up(self):
        if not self.up_stops:
            # No more UP requests. Check if we need to go DOWN.
            if self.down_stops:
                self.direction = Direction.DOWN
                self._move_down()
            else:
                self.direction = Direction.IDLE
            return

        # Get next stop (peek)
        next_stop = self.up_stops[0]

        if self.current_floor == next_stop:
            print(f"Elevator {self.id} STOPPED at floor {self.current_floor} (Open Doors)")
            heapq.heappop(self.up_stops) # Remove from queue
            # In real life: wait 2 seconds here
        else:
            self.current_floor += 1
            print(f"Elevator {self.id} moving UP -> {self.current_floor}")

    def _move_down(self):
        if not self.down_stops:
            # No more DOWN requests. Check if we need to go UP.
            if self.up_stops:
                self.direction = Direction.UP
                self._move_up()
            else:
                self.direction = Direction.IDLE
            return

        # Get next stop (peek) - remember to invert value back
        next_stop = -self.down_stops[0]

        if self.current_floor == next_stop:
            print(f"Elevator {self.id} STOPPED at floor {self.current_floor} (Open Doors)")
            heapq.heappop(self.down_stops) 
        else:
            self.current_floor -= 1
            print(f"Elevator {self.id} moving DOWN -> {self.current_floor}")
```

#### C. The Controller (Dispatcher)

This decides _which_ elevator gets the external "Hall Call".

Python

```python
class ElevatorSystem:
    def __init__(self, num_elevators):
        self.elevators = [Elevator(i) for i in range(num_elevators)]

    def request_elevator(self, floor, direction):
        # Dispatch Algorithm:
        # Simple strategy: Choose the closest elevator moving in the correct direction 
        # or an IDLE elevator.
        
        best_elevator = None
        min_distance = float('inf')

        for elevator in self.elevators:
            distance = abs(elevator.current_floor - floor)
            
            # 1. Perfectly valid elevator
            # (Moving UP and floor is above it) OR (Moving DOWN and floor is below it) OR (IDLE)
            is_valid_dir = (
                (elevator.direction == Direction.IDLE) or
                (direction == Direction.UP and elevator.direction == Direction.UP and floor >= elevator.current_floor) or
                (direction == Direction.DOWN and elevator.direction == Direction.DOWN and floor <= elevator.current_floor)
            )

            if is_valid_dir:
                if distance < min_distance:
                    min_distance = distance
                    best_elevator = elevator

        # Fallback: If no ideal elevator, just pick the closest one (naive)
        # In a real system, we might queue the request if all are busy moving away.
        if not best_elevator:
            best_elevator = min(self.elevators, key=lambda e: abs(e.current_floor - floor))

        print(f"Controller: Assigned Floor {floor} to Elevator {best_elevator.id}")
        best_elevator.add_request(floor)
        return best_elevator
```

#### D. Driver Code

Python

```python
if __name__ == "__main__":
    system = ElevatorSystem(num_elevators=2)

    # 1. External Call: User at Floor 5 wants to go UP
    e1 = system.request_elevator(5, Direction.UP)
    
    # 2. External Call: User at Floor 2 wants to go DOWN
    e2 = system.request_elevator(2, Direction.DOWN)

    # Simulate Movement Loop manually for demonstration
    print("\n--- Simulation Step 1 ---")
    system.elevators[0].move() # e0 is moving to 5
    system.elevators[1].move() # e1 is moving to 2

    print("\n--- Simulation Step 2 ---")
    # User enters Elevator 0 at Floor 5 and presses 10 (Internal Request)
    system.elevators[0].add_request(10)
    
    # Continue moving
    for _ in range(6):
        system.elevators[0].move()
```

***

#### 5. Key Design Challenges & Interview Answers

#### 1. Dispatch Algorithm Strategy

* Question: How do you decide which elevator to send?
* Approaches:
  * Nearest Idle: Simple but inefficient during rush hour.
  * Sectoring: Divide building (Elevator A serves 0-10, B serves 11-20). Good for skyscrapers.
  * Destination Dispatch (Modern): Users type destination floor _in the lobby_ before entering. The system groups people going to the same floor into the same elevator. This drastically reduces stops.

#### 2. State Pattern

* Question: The `move()` method has many `if-else` checks. Can we improve this?
* Answer: Use the State Design Pattern.
  * Classes: `IdleState`, `MovingUpState`, `MovingDownState`.
  * Each state implements `move()` and `handle_request()` differently.
  * Example: `MovingUpState.move()` simply increments floor, while `IdleState.handle_request()` transitions the state to Moving.

#### 3. Thread Safety

* Question: `add_request` can happen while `move` is running.
* Answer: Access to `up_stops` and `down_stops` (the priority queues) must be synchronized.
  * In Java: `synchronized` methods.
  * In Python: `with self.lock:` around the heap operations.

#### 4. Handling "Emergency Stop"

* We would add a boolean `is_operational`. If `False`, the dispatcher ignores this elevator.
* If `FireAlarm` triggers, `ElevatorSystem` overrides all queues: clear `up_stops`, push `0` (Lobby) to `down_stops`, and force direction DOWN.

#### 5. Why Priority Queues?

* Using a simple List/Array requires sorting every time a button is pressed ($$ $O(N \log N)$ $$).
* A Priority Queue (Heap) allows insertion in $$ $O(\log N)$ $$ and always gives us the _next closest floor_ in $$ $O(1)$ $$.

Would you like to move on to "Design a Snake and Ladder Game" or "Design a Parking Lot"?

