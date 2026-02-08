# Low level design

## EP 1: "TicTacToe&#x20;

**Introduction**

* **Context:** Jordan introduces a new series focusing on low-level design.
* **Personal Touch:** Shares his recent haircut experience and jokes about his appearance.
* **Project:** Building a Tic-Tac-Toe game with a focus on efficient win-checking.
* **Goal:** Demonstrate how to approach a coding problem, focusing on algorithmic efficiency.

**Problem Definition**

* **Standard Tic-Tac-Toe:** Two players (X and O) take turns placing their markers on a 3x3 grid.
* **Extended Scope:** The game will be played on an N x N board, requiring optimization for larger board sizes.
* **Win Condition:**
  * Three in a row horizontally, vertically, or diagonally.
  * Tie condition when the board is full.

**Solution Approach**

* **Data Structures:**
  * **Board:** 2D array to represent the game board.
  * **Player:** Class to store player name and marker (X or O).
  * **Game:** Class to manage game flow and player turns.
* **Win-Checking Algorithm:**
  * **Inefficient Approach:**
    * Iterating through all rows and columns for each move (O(N^2) time complexity).
  * **Optimized Approach:**
    * Maintain counters for each row, column, and diagonal.
    * Increment/decrement counters for each move.
    * Check if any counter reaches the board size (constant time complexity).

**Code Implementation (Python)**

* **Player Class:**
  * Stores player name and marker.
* **Board Class:**
  * `__init__`: Initializes the board with an empty 2D array and resets counters.
  * `place_marker()`:
    * Places the marker on the board.
    * Updates row, column, and diagonal counters.
    * Checks for win condition and returns True/False.
* **Game Class:**
  * Manages player turns and game flow.
  * Handles user input and displays the game board.

**Key Takeaways**

* **Algorithmic Efficiency:** Prioritize efficient algorithms, especially for problems that scale.
* **Data Structures:** Choose appropriate data structures to optimize performance.
* **Code Clarity:** Write clean, well-documented code.
* **Problem Decomposition:** Break down complex problems into smaller, more manageable subproblems.

**Additional Notes**

* **Humor:** Jordan's humor and personality are a key part of his channel.
* **Educational Focus:** The video aims to teach viewers about low-level design principles and best practices.
* **Call to Action:** Encourages viewers to provide feedback on his presentation style.

This summary provides a comprehensive overview of the key concepts discussed in the video. The original video provides more in-depth explanations, code examples, and visual aids.

I hope these notes are helpful! Let me know if you have any other questions.



## Ep 2: Cache

**Introduction**

* **Format Change:** Jordan switches to PowerPoint for this video, citing a preference for investment banking presentation style.
* **Project:** Designing a cache data structure.
* **Objective:** Focus on object-oriented design principles and algorithmic efficiency.

**Functional Requirements**

* **Key-Value Store:**
  * `get(key)`: Retrieve the value associated with the given key.
  * `put(key, value)`: Store the key-value pair in the cache.
  * `delete(key)`: Remove the key-value pair from the cache.
* **Eviction Policy:**
  * Evict keys when the cache reaches capacity.
  * Implement various eviction policies (e.g., LRU, FIFO, LFU).

**Design Philosophy**

* **Interface-Driven Design:**
  * Utilize interfaces for `Storage` and `EvictionPolicy`.
  * Allows for flexibility and easy swapping of implementations.
* **Extensibility:**
  * Design the cache class to be extensible, allowing for different storage backends and eviction policies.

**Cache Class Interface**

* **Constructor:**
  * Takes `Storage` and `EvictionPolicy` interfaces as arguments.
  * Takes `capacity` as an integer.
* **Methods:**
  * `get(key)`:
    * Update eviction policy (e.g., for LRU).
    * Call `get()` on the underlying `Storage`.
  * `put(key, value)`:
    * If capacity is exceeded, evict a key using `EvictionPolicy.getKeyToEvict()`.
    * Delete the evicted key.
    * Update eviction policy.
    * Call `put()` on the underlying `Storage`.
    * Increment cache size.
  * `delete(key)`:
    * Call `delete()` on the underlying `Storage`.
    * Update eviction policy to remove the deleted key.
    * Decrement cache size.

**Storage Implementations**

* **HashMap:**
  * Simple and efficient for basic key-value storage.
  * Provides O(1) time complexity for get, put, and delete operations.
* **TreeMap:**
  * Allows for ordered retrieval of keys.
  * Useful for scenarios where key ordering is required.
* **LinkedHashMap:**
  * Maintains insertion order.
  * Can be used to implement FIFO eviction policy.

**Eviction Policies**

* **Random:** Evict a random key when capacity is exceeded.
* **FIFO (First-In, First-Out):** Evict the oldest key in the cache.
* **LIFO (Last-In, First-Out):** Evict the most recently added key.
* **LFU (Least Frequently Used):** Evict the key that has been accessed the least frequently.
* **LRU (Least Recently Used):** Evict the key that has not been accessed for the longest time.

**LRU Eviction Policy Implementation**

* **Data Structures:**
  * **Doubly Linked List:** To maintain the order of key accesses.
  * **HashMap:** To map keys to their corresponding nodes in the doubly linked list.
* **`update(key)`:**
  * Move the node corresponding to the given key to the end of the list (most recently used).
* **`getKeyToEvict()`:**
  * Return the key at the head of the doubly linked list (least recently used).
* **`delete(key)`:**
  * Remove the node corresponding to the key from the doubly linked list.
  * Remove the key from the HashMap.

**Conclusion**

* **Key Takeaways:**
  * Importance of interface-driven design for flexibility and maintainability.
  * Choosing appropriate data structures for efficient implementation.
  * Understanding the trade-offs between different eviction policies.
* **Call to Action:**
  * Encourage viewers to implement the cache class and experiment with different storage and eviction policies.

**Note:** This summary provides a comprehensive overview of the key concepts discussed in the video. The original video provides more in-depth explanations, code examples, and visual aids.

I hope these notes are helpful! Let me know if you have any other questions.





## Ep 3: Elevator system



**Introduction**

* **Jordan introduces low-level design:** He explains the importance of low-level design in software development, focusing on the nitty-gritty details and how they impact the overall system.
* **Elevator control system project:** He introduces the project: designing an elevator control system for a high-rise building. This system manages elevator movement, user requests, and ensures safety.

**Functional Requirements**

* **User requests:** Users can call an elevator from a specific floor using buttons on the designated panels.
* **Door control:** The system manages the opening and closing of elevator doors to ensure passenger safety and efficient operation.
* **Movement control:** The system controls the elevator's movement between floors, optimizing speed and minimizing wait times.
* **Safety features:** The system prioritizes safety by incorporating features like overload protection (detecting exceeding weight limits), emergency stop buttons, and door safety sensors.
* **Efficiency considerations:** The system should optimize elevator movements to minimize wait times for users and reduce energy consumption. This might involve intelligent scheduling algorithms.

**Design Considerations**

* **Number of elevators:** Determining the optimal number of elevators for the building depends on factors like building height, number of floors, and expected traffic (number of users per floor during peak hours).
* **Elevator scheduling:** The system implements an algorithm to efficiently schedule elevator movements and minimize wait times. Common algorithms include Shortest Seek Time (prioritizes closest requests) and Scan Algorithm (moves the elevator up or down, serving all waiting requests on each floor).
* **Floor buttons and call panels:** Designing user-friendly interfaces for requesting elevators, potentially including floor indicators and up/down buttons.
* **Sensor data:** Utilizing sensors for various purposes:
  * Floor occupancy sensors can detect if someone is waiting on a floor.
  * Weight sensors can prevent overloading and ensure safety.
  * Door safety sensors detect if someone or something is blocking the doorway, preventing closure.
* **Communication protocol:** Establishing a communication protocol between elevator cars, the control system, and user interfaces to transmit information and control commands.
* **Error handling and maintenance:** Designing mechanisms for handling system failures (e.g., power outages, mechanical issues) and scheduling preventive maintenance to ensure smooth operation.

**Potential Implementation Details**

* **Data structures:** Using queues to manage elevator requests and floor information. Each floor might have a queue for up and down requests.
* **State machines:** Representing different states of the elevator (idle, moving up, moving down, door opening, door closing).
* **Threads:** Implementing concurrent processes for handling multiple elevator movements and user requests simultaneously. This ensures the system remains responsive even during high traffic.

**Evaluation and Testing**

* The video might discuss strategies for testing and evaluating the elevator control system's performance and safety. This could involve simulations, test cases, and potentially deploying the system in a controlled environment before real-world use.

**Conclusion**

* Jordan summarizes the key takeaways from the design process, emphasizing the importance of considering various factors and trade-offs when designing a complex system like an elevator control system.
* He might suggest additional resources or challenges for viewers to explore and solidify their understanding of low-level design principles in this context.

**Note:** This outline is based on common design problems for elevator systems and might not reflect the exact structure of the video.

Please let me know if you'd like me to elaborate on any specific aspects of elevator system design or low-level design in general.

## Ep 4:&#x20;

This video is about designing a parking garage. The author starts by explaining the functional requirements of a parking garage, which are to be able to enter the lot, exit the lot, and see how many available spots there are. He then discusses the different types of parking spots, which are big, medium, and small. He also discusses the different ways to implement a parking garage, such as using a queue, a priority queue, or sharding the parking lot. Finally, he implements a priority queue parking lot, which is a parking lot that assigns the closest spot to each entrance.

Here are some of the key points from the video:

* The author starts by explaining the functional requirements of a parking garage, which are to be able to enter the lot, exit the lot, and see how many available spots there are.
* He then discusses the different types of parking spots, which are big, medium, and small.
* He also discusses the different ways to implement a parking garage, such as using a queue, a priority queue, or sharding the parking lot.
* Finally, he implements a priority queue parking lot, which is a parking lot that assigns the closest spot to each entrance.

I hope these notes are helpful!



## designing a limit order book,&#x20;



a data structure used in stock exchanges to track open orders for buying and selling stocks. The author starts by explaining the basic concepts of a limit order book, such as the bid and ask sides, the spread, and the rules for matching orders. He then discusses the design of the order book interface, including methods for placing orders, canceling orders, and getting the volume at a given price.

The author then delves into the implementation details of the order book, focusing on the use of heaps and queues to efficiently store and retrieve orders. He explains how to use a Min Heap for the ask side and a Max Heap for the bid side, and how to use queues within each heap to handle orders at the same price. He also discusses the importance of maintaining volume information and the trade-offs between active and lazy cancellation methods.

Finally, the author explores some extensions to the basic limit order book problem, such as handling market orders and trigger orders. He provides a high-level overview of how to implement these extensions, but does not go into as much detail as he does for the basic limit order book.

Overall, this video provides a comprehensive overview of the design and implementation of a limit order book. The author covers a wide range of topics, from the basic concepts to advanced implementation details. The video is well-organized and easy to follow, and the author's explanations are clear and concise.

Here are some of the key points from the video:

* **Basic concepts of a limit order book:** The bid and ask sides, the spread, and the rules for matching orders.
* **Design of the order book interface:** Methods for placing orders, canceling orders, and getting the volume at a given price.
* **Implementation details:** Use of heaps and queues to store and retrieve orders, maintaining volume information, and trade-offs between active and lazy cancellation methods.
* **Extensions to the basic limit order book problem:** Handling market orders and trigger orders.

I hope these notes are helpful!



## designing an air traffic control system.&#x20;

The author starts by explaining the functional requirements of an air traffic control system, which are to be able to enter the lot, exit the lot, and see how many available spots there are. He then discusses the different types of parking spots, which are big, medium, and small. He also discusses the different ways to implement a parking garage, such as using a queue, a priority queue, or sharding the parking lot. Finally, he implements a priority queue parking lot, which is a parking lot that assigns the closest spot to each entrance.

Here are some of the key points from the video:

* The author starts by explaining the functional requirements of a parking garage, which are to be able to enter the lot, exit the lot, and see how many available spots there are.
* He then discusses the different types of parking spots, which are big, medium, and small.
* He also discusses the different ways to implement a parking garage, such as using a queue, a priority queue, or sharding the parking lot.
* Finally, he implements a priority queue parking lot, which is a parking lot that assigns the closest spot to each entrance.

I hope these notes are helpful!
