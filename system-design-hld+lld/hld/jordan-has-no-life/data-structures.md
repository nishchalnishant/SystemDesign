# Data structures

## EP1: Arrays & Lists:

**Introduction**

* The video starts with Jordan explaining his plan to focus more on data structures and algorithms, particularly complex ones and competitive programming.
* He acknowledges that this might not be everyone's cup of tea but assures viewers that he will continue to post other types of content as well.
* He emphasizes the importance of starting with the basics before diving into complex topics.

**Arrays**

* Arrays are fixed-size blocks of memory used to store data.
* The type of data stored can vary depending on the programming language.
* Key operations on arrays:
  * **Appending:** Adding an element to the end of the array. This is efficient (constant time) as long as there's space.
  * **Accessing:** Retrieving an element at a specific index. Also very efficient (constant time).
  * **Deleting:** Removing an element from the array. This is less efficient (linear time) because it requires shifting elements to fill the gap.
  * **Inserting:** Adding an element at a specific index. Similar to deleting, this also requires shifting elements and is linear time.

**Lists**

* Lists are dynamically sized arrays.
* They use an underlying array to store data but can grow or shrink as needed.
* To accommodate growth, lists reallocate a new array with double the capacity when the current array is full.
* This reallocation operation is expensive (linear time), but its impact is amortized over multiple append operations, making the average time complexity of appending to a list constant.

**Time Complexity**

* **Accessing:** O(1) for both arrays and lists.
* **Appending:** O(1) for arrays (if space is available) and O(1) amortized for lists.
* **Deleting/Inserting:** O(n) for both arrays and lists due to the need to shift elements.
* **Searching:** O(n) for unsorted arrays and lists.

**Conclusion**

* Jordan emphasizes the importance of understanding these basic data structures before moving on to more complex topics.
* He encourages viewers to provide feedback and suggests that this type of content could be helpful for many.

**Additional Notes**

* The video uses a humorous and engaging style, including a self-deprecating joke about his own "list."
* It includes a simple PowerPoint presentation to illustrate the concepts.
* The video provides a good overview of arrays and lists, covering their key characteristics and operations.

I hope these notes are helpful! Let me know if you have any other questions.



## EP 2: Sorting algorithms&#x20;

* **Sorting Algorithms**
  * **Selection Sort**
    * Splits the array into sorted and unsorted portions.
    * Finds the minimum element in the unsorted portion and swaps it with the first element of the unsorted portion.
    * Time complexity: O(n^2)
    * Space complexity: O(1)
    * Not stable
  * **Insertion Sort**
    * Splits the array into sorted and unsorted portions.
    * Takes the first element of the unsorted portion and inserts it into the correct position in the sorted portion.
    * Time complexity: O(n^2)
    * Space complexity: O(1)
    * Stable
  * **Merge Sort**
    * Recursively divides the array into two halves.
    * Sorts each half.
    * Merges the two sorted halves into a single sorted array.
    * Time complexity: O(n log n)
    * Space complexity: O(n)
    * Stable
  * **Quick Sort**
    * Picks a pivot element.
    * Partitions the array such that all elements less than the pivot are to the left of the pivot and all elements greater than or equal to the pivot are to the right of the pivot.
    * Recursively sorts1 the left and right subarrays.
    * Time complexity: O(n log n) on average, O(n^2) in the worst case
    * Space complexity: O(log n) on average, O(n) in the worst case
    * Not stable
* **Other Sorting Algorithms**
  * Bubble Sort
  * Non-comparative Sorting Algorithms (e.g., bucket sort, radix sort)
* **Why Sorting is Important**
  * Many algorithms rely on sorted data.
  * Sorting is a fundamental building block of many other algorithms.
* **How to Choose a Sorting Algorithm**
  * Consider the time and space complexity of the algorithm.
  * Consider whether the algorithm is stable.
  * Consider the size of the data set.
* **Additional Notes**
  * The video does not cover all sorting algorithms.
  * The video does not prove that it is impossible to achieve better than O(n log n) time complexity for comparative sorting algorithms.
  * The video does not go into detail about non-comparative sorting algorithms.

## EP 3: Binary search

This video provides a good overview of some of the most common sorting algorithms. It covers the basic concepts of each algorithm, as well as their time and space complexity. The video also discusses the importance of sorting and how to choose a sorting algorithm for a given problem.



These are the notes from the video:

* **Binary Search**
  * A search algorithm that works on sorted arrays.
  * Divides the array in half at each step.
  * Compares the middle element to the target value.
  * If the middle element is greater than the target value, the search continues in the left half of the array.
  * If the middle element is less than the target value, the search continues in the right half of the array.1
  * The process repeats until the target value is found or the search space is exhausted.
  * Time complexity: O(log n)
  * Space complexity: O(1)
* **Pseudocode**
  * `binary_search(nums, left, right, target)`
    * If `left` is greater than `right`, return `-1` (not found)
    * `mid = (left + right) // 2`
    * If `nums[mid]` is equal to `target`, return `mid`
    * If `nums[mid]` is greater than `target`, return `binary_search(nums, left, mid - 1, target)`
    * If `nums[mid]` is less than `target`, return `binary_search(nums, mid + 1, right, target)`
* **Why Binary Search is Important**
  * It is a very efficient search algorithm.
  * It is used in many other algorithms, such as merge sort and quick sort.
  * It is a common interview question.
* **Additional Notes**
  * The video does not cover all of the possible variations of binary search.
  * The video does not discuss how to handle duplicate values in the array.
  * The video does not prove that binary search has a time complexity of O(log n).

This video provides a good overview of binary search. It covers the basic concepts of the algorithm, as well as its pseudocode and time complexity. The video also discusses the importance of binary search and why it is a useful algorithm to know.





## EP 4: Hashmaps

* **Hashmaps**
  * A specific implementation of an associative array or map.
  * Stores key-value pairs.
  * Uses a hash function to map keys to indices in an array (buckets).
  * Hash function should distribute keys evenly across buckets.
  * Example hash function: `hash(key) = ascii(key) % length_of_array`
* **Hashmap Operations**
  * **Put:**
    * Hash the key to get the bucket index.
    * If the bucket is empty, insert the key-value pair.
    * If the bucket is not empty, handle collisions:
      * **Chaining:** Create a linked list of key-value pairs in the bucket.
      * **Probing:** Find the next available empty slot in the array.
  * **Get:**
    * Hash the key to get the bucket index.
    * If the bucket is empty, return `null`.
    * If the bucket is not empty, search the bucket for the key:
      * If using chaining, search the linked list.
      * If using probing, search the array until the key is found or an empty slot is encountered.
  * **Delete:**
    * Hash the key to get the bucket index.
    * If the bucket is empty, do nothing.
    * If the bucket is not empty, remove the key-value pair from the bucket:
      * If using chaining, remove the node from the linked list.
      * If using probing, remove the key-value pair from the array.
* **Collisions**
  * Occur when two or more keys hash to the same bucket.
  * Can degrade hashmap performance.
  * Strategies for handling collisions:
    * Chaining
    * Probing
* **Load Factor**
  * Ratio of the number of elements in the hashmap to the number of buckets.
  * As load factor approaches 1, collisions become more frequent and performance degrades.
* **Resizing**
  * To maintain good performance, resize the hashmap when the load factor exceeds a certain threshold.
  * Create a new array with a larger size.
  * Rehash all elements into the new array.
  * Resizing is an O(n) operation, but amortized time complexity is O(1).
* **Time Complexity**
  * In the absence of collisions, all operations (put, get, delete) have O(1) time complexity.
  * With collisions, time complexity depends on the collision resolution strategy:
    * Chaining: O(1) average time, O(n) worst-case time (if all elements hash to the same bucket).
    * Probing: Can degrade to O(n) in the worst case.

This video provides a comprehensive overview of hashmaps, including their implementation, operations, collision handling, and resizing. It also discusses the importance of load factor and the time complexity of hashmap operations.



## EP 5: linked list

* **Introduction**
  * The video is about linked lists, a data structure for dynamically sized lists.
  * Linked lists differ from arrays in how they store elements in memory.
  * The video will cover the basics of linked lists, including their implementation, time complexities of operations, and use cases.
* **Background**
  * Linked lists are an alternative to arrays for storing lists of elements.
  * They allow for dynamic resizing, meaning you can add or remove elements without needing to resize the entire data structure.
  * Linked lists are implemented using nodes, where each node contains a value and a pointer to the next node in the list.
* **Implementation**
  * A linked list is typically implemented with a `Node` class and a `LinkedList` class.
  * The `Node` class contains a `value` and a `next` pointer.
  * The `LinkedList` class contains a `head` pointer, which points to the first node in the list.
* **Time Complexities**
  * **Finding an element:** O(n) - You need to traverse the list from the beginning until you find the element.
  * **Inserting an element:** O(1) - If you have a pointer to the node before the insertion point, you can insert in constant time.
  * **Deleting an element:** O(1) - If you have a pointer to the node before the element to be deleted, you can delete in constant time. However, in a singly linked list, you need to keep track of the previous node while traversing, which can increase the time complexity.
  * **Doubly Linked Lists:**
    * Doubly linked lists have pointers to both the next and previous nodes.
    * This allows for constant-time deletion even without keeping track of the previous node.
* **Use Cases**
  * Linked lists are useful when you need to frequently insert or delete elements.
  * They are also useful when you don't know the size of the list in advance.
  * Doubly linked lists are used in data structures like caches (e.g., Least Recently Used cache).
* **Conclusion**
  * Linked lists are a fundamental data structure with various applications.
  * Understanding their implementation and time complexities is crucial for algorithm design and problem-solving.

This video provides a good overview of linked lists, covering their basic concepts, implementation, time complexities, and use cases. It also briefly discusses doubly linked lists and their advantages.



## Ep 6: Stack and queues

* **Introduction**
  * The video introduces stacks and queues, two fundamental data structures.
  * Stacks and queues are interfaces for interacting with lists of items.
  * They offer efficient operations for adding and removing elements.
* **Stacks**
  * **Analogy:** A stack of pancakes.
  * **Operations:**
    * **Push:** Adds an item to the top of the stack (like adding a pancake).
    * **Pop:** Removes and returns the item at the top of the stack (like eating the top pancake).
    * **Peek:** Views the item at the top of the stack without removing it.
  * **Implementations:**
    * **Linked List:**
      * The head of the linked list represents the top of the stack.
      * Push: Create a new node and make it the new head.
      * Pop: Set the head to the next node.
      * Peek: Return the value of the head node.
    * **Array:**
      * The end of the array represents the top of the stack.
      * Push: Append the item to the end of the array.
      * Pop: Remove and return the last item in the array.
      * Peek: Return the last item in the array.
* **Queues**
  * **Analogy:** A waiting line.
  * **Operations:**
    * **Enqueue:** Adds an item to the back of the queue (like joining the end of the line).
    * **Dequeue:** Removes and returns the item at the front of the queue (like being served first).
    * **Peek:** Views the item at the front of the queue without removing it.
  * **Implementations:**
    * **Linked List:**
      * Maintain pointers to both the head and tail of the list.
      * Enqueue: Add a new node to the tail.
      * Dequeue: Remove the head node.
      * Peek: Return the value of the head node.
    * **Array:**
      * Use two pointers: one for the front and one for the back.
      * Enqueue: Add the item to the back of the array.
      * Dequeue: Remove the item at the front of the array and shift the remaining elements.
      * Peek: Return the item at the front of the array.
* **Conclusion**
  * Stacks and queues are essential data structures with various applications in algorithms and data structures.
  * Understanding their implementations and operations is crucial for further study in computer science.

