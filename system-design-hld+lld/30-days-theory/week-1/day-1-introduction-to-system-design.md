# Day 1 Introduction to System Design



## **What is System Design?**

System design is the process of defining the architecture, components, and interfaces of a system to satisfy specific requirements. It involves making high-level choices about how a system should function and how its components will interact with one another.

**Key Concepts in System Design:**

1. **Scalability:**
   * **Scalability** refers to a system's ability to handle growing amounts of work or its potential to expand to accommodate that growth.
   * Types of scalability:
     * **Vertical scaling (scaling up):** Increasing the capacity of a single machine (e.g., adding more CPU or memory).
     * **Horizontal scaling (scaling out):** Adding more machines to distribute the load.
2. **Reliability:**
   * Reliability is the system’s ability to operate continuously without failure.
   * Strategies to improve reliability:
     * **Redundancy:** Having backup components in case of failure.
     * **Replication:** Duplicating services or data across multiple servers.
3. **Availability:**
   * Availability is the percentage of time a system is operational. It depends on the system’s ability to remain functional even in the face of failures.
   * **High availability (HA):** A system designed to operate continuously without failure for a long time.
4. **Performance:**
   * Performance refers to how quickly a system responds to requests.
   * Factors that affect performance:
     * **Latency:** Time taken to process a request.
     * **Throughput:** Number of requests a system can handle in a given period.
5. **Maintainability:**
   * Maintainability is how easy it is to update and improve a system over time. Clean code, modular architecture, and clear documentation all contribute to maintainability.
6. **Consistency, Availability, and Partition Tolerance (CAP Theorem):**
   * **CAP theorem** states that it is impossible for a distributed system to simultaneously provide all three guarantees:
     * **Consistency:** Every read receives the most recent write.
     * **Availability:** Every request receives a response, even if it's not the most recent one.
     * **Partition Tolerance:** The system continues to operate despite network partitioning (communication failures between servers).
7. **Trade-offs in System Design:**
   * System design involves making trade-offs between various properties (e.g., scalability vs. consistency, performance vs. reliability).

***

## **Steps to Approach System Design Problems:**

1. **Understand the Requirements:**
   * Clarify functional and non-functional requirements (e.g., performance, scalability, reliability).
   * Identify constraints (e.g., latency, throughput).
2. **High-Level Architecture:**
   * Break down the system into components (e.g., client, server, database).
   * Identify the communication between components.
3. **Key Design Decisions:**
   * Decide on data storage (SQL or NoSQL, distributed database).
   * Choose communication protocols (e.g., REST, gRPC, WebSocket).
   * Think about caching, load balancing, and replication strategies.
4. **Component Design:**
   * Detail the internals of each major component (e.g., how the database or API layer works).
   * Focus on data flow and interactions between components.
5. **Scale the Design:**
   * Plan for how the system will scale as traffic increases (e.g., through sharding or load balancing).
   * Consider strategies for failure recovery and replication.

***

## **Common System Design Patterns:**

1. **Microservices Architecture:**
   * Breaking down a system into small, independently deployable services that communicate over a network.
2. **Monolithic Architecture:**
   * A single unified codebase where all components of the system are packaged and deployed together.
3. **Client-Server Architecture:**
   * A structure where the server provides services, and the client makes requests.
4. **Master-Slave Architecture:**
   * A design where one node (the master) directs the operations, and other nodes (slaves) follow its instructions.
5. **Event-Driven Architecture:**
   * A system where components react to events, promoting loose coupling and scalability.

***

