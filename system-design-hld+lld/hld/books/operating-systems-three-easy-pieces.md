# Operating systems three easy pieces

#### Chapter 1: A Dialogue on the Book

**Overview**

The first chapter, "A Dialogue on the Book," introduces the structure and purpose of the book through a conversational format between a "Professor" and a "Student." This approach is used to ease the reader into the subject of operating systems, providing a light and engaging entry point into the more complex topics covered later.

**Key Themes**

1. **Introduction to the Book’s Title**
   * The Student inquires about the book's title, "Operating Systems: Three Easy Pieces."
   * The Professor explains that the title is inspired by Richard Feynman’s physics lectures, specifically "Six Easy Pieces," which distilled complex physics concepts into simpler parts. Similarly, this book aims to simplify operating system concepts into three main areas: virtualization, concurrency, and persistence.
2. **Structure and Content**
   * The Professor outlines the three key pieces the book will cover:
     * **Virtualization:** Understanding how operating systems create the illusion of multiple processes running simultaneously on a single CPU.
     * **Concurrency:** Managing multiple processes running at the same time, ensuring they operate correctly and efficiently.
     * **Persistence:** How data is stored reliably over time, including file systems and storage devices.
3. **Learning Approach**
   * The Professor advises the Student on the best ways to learn from the book:
     * Attend lectures to get initial exposure to concepts.
     * Read the notes at the end of each week to reinforce learning.
     * Review the notes again before exams to solidify understanding.
     * Engage in assigned homework and projects, emphasizing practical coding tasks to apply theoretical concepts.
4. **Dialogues as a Learning Tool**
   * The dialogues are designed to pull the reader out of the narrative to think critically about the material.
   * The interactive format aims to make the learning process more engaging and reflective.

**Notable Quotes**

* **On the Purpose of the Dialogues:**
  * Professor: "I think it is sometimes useful to pull yourself outside of a narrative and think a bit; these dialogues are those times. So you and I are going to work together to make sense of all of these pretty complex ideas."
* **On the Learning Process:**
  * Professor: "Each person needs to figure this out on their own, of course, but here is what I would do: go to class, to hear the professor introduce the material. Then, at the end of every week, read these notes, to help the ideas sink into your head a bit better."

#### Summary

Chapter 1 sets the stage for the reader by introducing the structure, purpose, and methodology of the book. Through the dialogue, it highlights the importance of virtualization, concurrency, and persistence in understanding operating systems. The chapter also emphasizes the interactive and reflective nature of the learning process proposed by the authors.





Chapter 2 of "Operating Systems: Three Easy Pieces" provides an introduction to operating systems, focusing on the key concept of virtualization. Here are the detailed notes on this chapter:

#### 2. Introduction to Operating Systems

**2.1 Virtualizing the CPU**

* **Virtualization of Resources**: The primary role of an operating system (OS) is to virtualize physical resources. This involves transforming physical hardware into more general, powerful, and user-friendly virtual resources.
* **CPU Virtualization**: The OS virtualizes the CPU, allowing multiple programs to run as if they each have their own processor. This involves time-sharing and context-switching to give the illusion of multiple virtual CPUs.

**2.2 Virtualizing Memory**

* **Memory Virtualization**: The OS also virtualizes memory, providing each process with the illusion of having its own private memory space. This involves techniques like paging and segmentation.

**2.3 Concurrency**

* **Concurrency Management**: The OS handles concurrency, managing multiple processes and threads running simultaneously. This includes synchronization mechanisms to avoid conflicts and ensure correct execution.

**2.4 Persistence**

* **Data Persistence**: Unlike CPU and memory, the OS manages disk storage to ensure data persists beyond program execution. This involves managing files and directories on storage devices like hard drives and SSDs.

**2.5 Design Goals**

* **Design Goals of OS**: Key design goals include abstraction, performance, protection, and reliability. The OS aims to provide a user-friendly interface, efficiently manage resources, protect processes from each other, and ensure system reliability.

**2.6 Some History**

* **Historical Context**: The chapter provides a brief history of operating systems, tracing their evolution from simple batch processing systems to complex modern systems. Key developments include time-sharing systems and the introduction of personal computers.

**2.7 Summary**

* **Summary**: The chapter concludes with a summary of the primary roles of an OS: virtualizing the CPU and memory, managing concurrency, and ensuring data persistence. It also reiterates the design goals and historical evolution of operating systems.

This chapter sets the stage for understanding the detailed mechanisms and policies used by operating systems to manage hardware resources efficiently and effectively. It highlights the importance of virtualization and provides a historical perspective to understand the evolution of OS design.



#### Chapter 3: A Dialogue on Virtualization

**Overview:** Chapter 3 of "Operating Systems: Three Easy Pieces" is a dialogue that introduces the concept of virtualization. The chapter explains virtualization in a conversational style between a professor and a student, using analogies and examples to make the concept accessible.

**Key Points:**

1. **Introduction to Virtualization:**
   * Virtualization is described as the process of creating a virtual version of something, such as a CPU, memory, or storage.
   * The professor uses the analogy of sharing a physical peach among many eaters to illustrate the idea of virtualization, where each eater gets the illusion of having their own peach, though there is only one physical peach.
2. **Virtualization of the CPU:**
   * The professor explains that virtualization allows multiple applications to run as if each has its own CPU, even though there may only be one physical CPU.
   * This is achieved by the operating system (OS) creating virtual CPUs for each application, giving the illusion that each application runs on its own dedicated CPU.
3. **The Illusion of Multiple CPUs:**
   * The OS can switch between different processes, giving each a small slice of CPU time, which creates the appearance that all processes are running simultaneously.
   * This technique is known as time-sharing and is fundamental to how modern operating systems manage multiple processes.
4. **Time-Sharing and Performance:**
   * Time-sharing can impact the performance of each process since the CPU's time is divided among many processes.
   * However, it allows for better utilization of the CPU and makes the system more responsive and efficient from the user's perspective.
5. **The Role of the Operating System:**
   * The OS is responsible for managing the hardware resources and providing the virtual environment in which applications run.
   * It creates and maintains the illusion of multiple virtual resources (like CPUs) through various mechanisms, including context switching and scheduling.

**Dialogue Excerpts:**

* **Professor:** "Imagine we have a peach... let us call that the physical peach. But we have many eaters who would like to eat this peach... we create the illusion of many virtual peaches out of the one physical peach."
* **Student:** "Wow! That sounds like magic. Tell me more! How does that work?"
* **Professor:** "By running one process, then stopping it and running another, and so forth, the OS can promote the illusion that many virtual CPUs exist when in fact there is only one physical CPU (or a few)."

**Conclusion:** The chapter effectively uses a simple analogy and dialogue to explain the concept of virtualization, focusing on how the OS can create the illusion of multiple virtual CPUs from a single physical CPU. This foundational concept is crucial for understanding how operating systems manage resources and provide a seamless user experience.

***

For detailed study, refer to Chapter 3 of "Operating Systems: Three Easy Pieces" by Remzi H. Arpaci-Dusseau and Andrea C. Arpaci-Dusseau【6:4†source】.





#### Detailed Notes of Chapter 4: The Abstraction: The Process

Chapter 4 of "Operating Systems: Three Easy Pieces" discusses one of the most fundamental abstractions provided by an operating system: the process. Here are the detailed notes on each section of this chapter:

**4.1 The Abstraction: A Process**

* **Definition**: A process is a running program, a dynamic entity that involves the execution of a program's instructions.
* **Program vs. Process**: A program is static, just a collection of bytes on a disk, whereas a process is dynamic, involving the execution of the program's instructions.
* **Multiprogramming**: Systems typically run multiple programs simultaneously to enhance usability. The OS provides the illusion of multiple CPUs through time-sharing techniques.

**4.2 Process API**

* **API Overview**: Describes the system calls and functions that can be used to create, manipulate, and terminate processes.
* **Key System Calls**:
  * **fork()**: Creates a new process by duplicating the calling process.
  * **exec()**: Replaces the current process memory space with a new program.
  * **wait()**: Waits for a process to change state, typically to become a zombie.
  * **exit()**: Terminates the current process.

**4.3 Process Creation: A Little More Detail**

* **Loading Programs**: The OS loads the program’s code and static data from disk into memory, creating an address space for the process.
* **Memory Allocation**: Allocates memory for the process’s stack (for function calls and local variables) and heap (for dynamically allocated data).
* **Initialization Tasks**: The OS initializes various components like the stack and heap, sets up I/O descriptors, and prepares the environment for execution.

**4.4 Process States**

* **Life Cycle of a Process**: A process goes through various states during its lifetime:
  * **Running**: The process is actively executing instructions.
  * **Ready**: The process is ready to run and is waiting for CPU time.
  * **Blocked**: The process is waiting for some event (e.g., I/O completion).
  * **Terminated**: The process has finished execution.
* **State Transitions**: The OS handles transitions between these states, such as moving a process from ready to running, or from running to blocked.

**4.5 Data Structures**

* **Process Control Block (PCB)**: The OS maintains a PCB for each process, which contains information like process state, program counter, CPU registers, memory management information, accounting information, and I/O status.
* **Context Switching**: Involves saving the state of the currently running process and restoring the state of the next process to run. This is crucial for the time-sharing mechanism.

**4.6 Summary**

* **Core Concept**: The process is a fundamental abstraction that allows the OS to manage and execute programs effectively.
* **Virtualization of the CPU**: Achieved through time-sharing, allowing multiple processes to appear to run simultaneously on a single CPU.
* **Mechanisms and Policies**: The chapter discusses both the low-level mechanisms (e.g., context switching) and high-level policies (e.g., scheduling) that enable effective process management.

These sections collectively provide a comprehensive overview of what processes are, how they are created and managed, and the key concepts and mechanisms that underpin process management in an operating system.



#### Notes on Chapter 5: Interlude: Process API

**Overview:** Chapter 5 of "Operating Systems: Three Easy Pieces" focuses on the Process API, particularly in UNIX systems. It covers the `fork()`, `wait()`, and `exec()` system calls which are used for process creation and control.

**5.1 The fork() System Call**

* **Purpose:** `fork()` is used to create a new process.
* **Behavior:** When called, it creates a new process (child process) which is a duplicate of the calling process (parent process).
* **Return Values:**
  * **Parent Process:** Receives the PID of the child process.
  * **Child Process:** Receives 0.
  * **On Error:** Returns -1 in the parent process, no child process is created.
*   **Example Code:**

    ```c
    #include <stdio.h>
    #include <stdlib.h>
    #include <unistd.h>

    int main(int argc, char *argv[]) {
        printf("hello world (pid:%d)\n", (int) getpid());
        int rc = fork();
        if (rc < 0) { // fork failed; exit
            fprintf(stderr, "fork failed\n");
            exit(1);
        } else if (rc == 0) { // child (new process)
            printf("hello, I am child (pid:%d)\n", (int) getpid());
        } else { // parent goes down this path (main)
            printf("hello, I am parent of %d (pid:%d)\n", rc, (int) getpid());
        }
        return 0;
    }
    ```
* **Non-deterministic Output:** The order of execution of the parent and child processes is determined by the scheduler, leading to non-deterministic output.

**5.2 The wait() System Call**

* **Purpose:** Allows a parent process to wait for its child process to finish execution.
* **Behavior:** When a parent process calls `wait()`, it blocks until any of its children has finished execution.
*   **Example Code:**

    ```c
    #include <stdio.h>
    #include <stdlib.h>
    #include <unistd.h>
    #include <sys/wait.h>

    int main(int argc, char *argv[]) {
        printf("hello world (pid:%d)\n", (int) getpid());
        int rc = fork();
        if (rc < 0) { // fork failed; exit
            fprintf(stderr, "fork failed\n");
            exit(1);
        } else if (rc == 0) { // child (new process)
            printf("hello, I am child (pid:%d)\n", (int) getpid());
        } else { // parent goes down this path (main)
            int wc = wait(NULL);
            printf("hello, I am parent of %d (wc:%d) (pid:%d)\n", rc, wc, (int) getpid());
        }
        return 0;
    }
    ```

**5.3 The exec() System Call**

* **Purpose:** `exec()` family of functions replaces the current process image with a new process image.
* **Variants:** `execl()`, `execle()`, `execlp()`, `execv()`, `execvp()`.
* **Behavior:**
  * It loads a binary file into memory and starts its execution.
  * The current process image is replaced, and the new process starts execution from its entry point.
  * `exec()` does not create a new process but transforms the current process.
*   **Example Code:**

    ```c
    #include <stdio.h>
    #include <stdlib.h>
    #include <unistd.h>
    #include <string.h>
    #include <sys/wait.h>

    int main(int argc, char *argv[]) {
        printf("hello world (pid:%d)\n", (int) getpid());
        int rc = fork();
        if (rc < 0) { // fork failed; exit
            fprintf(stderr, "fork failed\n");
            exit(1);
        } else if (rc == 0) { // child (new process)
            printf("hello, I am child (pid:%d)\n", (int) getpid());
            char *myargs[3];
            myargs[0] = strdup("wc"); // program: "wc" (word count)
            myargs[1] = strdup("p3.c"); // argument: file to count
            myargs[2] = NULL; // marks end of array
            execvp(myargs[0], myargs); // runs word count
            printf("this shouldn’t print out");
        } else { // parent goes down this path (main)
            int wc = wait(NULL);
            printf("hello, I am parent of %d (wc:%d) (pid:%d)\n", rc, wc, (int) getpid());
        }
        return 0;
    }
    ```

**5.4 Why? Motivating The API**

* **Ease of Use:** The combination of `fork()` and `exec()` allows for straightforward process creation and program execution.
* **Flexibility:** These system calls provide powerful mechanisms to control and manage processes, which are essential for implementing complex functionalities in operating systems.

**5.5 Other Parts Of The API**

* **Additional System Calls:** The chapter also briefly touches on other process-related system calls and their importance in the process control and management.

**5.6 Summary**

* The process API in UNIX systems, particularly the `fork()`, `wait()`, and `exec()` calls, are fundamental tools for process creation and control.
* These system calls enable the execution of new programs, synchronization between processes, and provide mechanisms to manage the lifecycle of processes effectively.

This chapter provides practical insights into the use of these system calls, with examples illustrating their usage and highlighting their significance in operating systems .



Here are detailed notes from Chapter 6 of "Operating Systems: Three Easy Pieces" titled "Mechanism: Limited Direct Execution":

#### 6. Mechanism: Limited Direct Execution

**Objective:** To virtualize the CPU, the OS needs to share the CPU among multiple jobs efficiently and with control. This is achieved through a technique called **Limited Direct Execution (LDE)**.

**6.1 Basic Technique: Limited Direct Execution**

* **Direct Execution:** The OS runs the program directly on the CPU. It involves:
  1. Creating a process entry in the process list.
  2. Allocating memory for the process.
  3. Loading the program code into memory.
  4. Setting up the stack with arguments.
  5. Clearing registers and jumping to the program's entry point (e.g., main()).
* **Challenge:** How to ensure the program doesn't perform unauthorized actions and how to manage time-sharing between processes without losing control.

**6.2 Problem #1: Restricted Operations**

* **Restricted Operations:** Processes need to perform certain privileged operations like I/O, which they cannot be allowed to do directly.
* **Solution:** **Protected Control Transfer**
  * **User Mode vs. Kernel Mode:** The CPU operates in user mode for normal processes and kernel mode for the OS to access full hardware resources.
  * **Trap Instructions:** Special CPU instructions to transition from user mode to kernel mode (trap) and back (return-from-trap).
* **Trap Table:**
  * Configured by the OS at boot time to define which code to execute on various exceptional events (e.g., system calls, interrupts).
  * **Privileged Instruction:** Only the OS can set the trap table, preventing user programs from altering it.

**6.3 Problem #2: Switching Between Processes**

* **Context Switching:** When the OS needs to switch between processes, it must regain control of the CPU.
* **Techniques:**
  * **Cooperative Approach:** Relies on processes making system calls (voluntary yielding) which is not reliable for non-cooperative processes.
  * **Preemptive Multitasking:** Uses a timer interrupt to regain control periodically, allowing the OS to decide process switching.
* **Steps in Context Switching:**
  1. **Save State:** Save the state (registers, program counter) of the currently running process.
  2. **Load State:** Load the state of the next process to run.
  3. **Switch Context:** Update the process control block (PCB) and process state information.

**6.4 Worried About Concurrency?**

* **Concurrency:** Handling multiple processes running seemingly simultaneously can lead to race conditions.
* **Synchronization Mechanisms:** Ensuring processes do not interfere with each other while accessing shared resources.
  * **Locks, Semaphores, Monitors:** Tools to manage access to shared resources.

#### Summary

* **Limited Direct Execution:** A technique to run processes efficiently while keeping control with the OS.
* **Problems Addressed:**
  * Handling restricted operations by using protected control transfers.
  * Achieving process switching through context switching mechanisms.
  * Managing concurrency using synchronization tools to prevent race conditions.

**References and Homework:**

* The chapter concludes with references for further reading and practical homework exercises to reinforce the concepts discussed.

By following these mechanisms and addressing the inherent challenges, the OS can effectively virtualize the CPU, providing a smooth and controlled multi-processing environment .



#### Detailed Notes on Chapter 7: Scheduling: Introduction

**7.1 Workload Assumptions**

* **Key Concept**: Before delving into scheduling policies, it is crucial to understand the workload, which consists of the processes running in the system.
* **Simplifying Assumptions**:
  1. Jobs are independent and arrive at the same time.
  2. Jobs run for a known amount of time.
  3. The system only runs one job at a time.
  4. All jobs arrive simultaneously.
  5. There are no dependencies between jobs.

**7.2 Scheduling Metrics**

* **Primary Metric**: Turnaround Time
  * Defined as ( T\_{turnaround} = T\_{completion} - T\_{arrival} )
  * Simplified for simultaneous job arrival: ( T\_{arrival} = 0 ), thus ( T\_{turnaround} = T\_{completion} ).
* **Secondary Metric**: Fairness
  * Example metric: Jain's Fairness Index.
  * Balance between performance and fairness is critical; optimizing one often affects the other negatively.

**7.3 First In, First Out (FIFO)**

* **Definition**: FIFO or First Come, First Served (FCFS) schedules jobs in the order they arrive.
* **Example**:
  * Jobs A, B, and C arrive at the same time, each taking 10 seconds.
  * A finishes at 10s, B at 20s, and C at 30s.
  * Average turnaround time = ( \frac{10+20+30}{3} = 20 ) seconds.
* **Drawbacks**:
  * Poor performance with jobs of different lengths due to the "convoy effect".
  * Example: Job A takes 100s, B and C take 10s each.
  * A finishes at 100s, B at 110s, C at 120s.
  * Average turnaround time = ( \frac{100+110+120}{3} = 110 ) seconds.

**7.4 Shortest Job First (SJF)**

* **Definition**: Prioritizes jobs with the shortest duration first.
* **Optimality**: Minimizes average turnaround time when all jobs arrive simultaneously.
* **Example**:
  * Jobs A, B, and C with durations 100s, 10s, and 10s respectively.
  * B and C run first, followed by A.
  * Turnaround times: B=10s, C=20s, A=120s.
  * Average turnaround time = ( \frac{10+20+120}{3} = 50 ) seconds.

**7.5 Shortest Time-to-Completion First (STCF)**

* **Definition**: Preemptive version of SJF, also known as Preemptive Shortest Job First (PSJF).
* **Mechanism**: Switches to the job with the least remaining time whenever a new job arrives.
* **Example**:
  * Job A arrives at 0s (100s duration), B and C at 10s (10s each).
  * A preempted by B and C, runs after both complete.
  * Turnaround times: A=120s, B=10s, C=20s.
  * Average turnaround time = ( \frac{120+10+20}{3} = 50 ) seconds.
* **Optimality**: Minimizes average turnaround time with jobs arriving at different times.

**7.6 A New Metric: Response Time**

* **Definition**: Time from job arrival to first execution.
  * Formula: ( T\_{response} = T\_{firstrun} - T\_{arrival} )
* **Example**:
  * Job A arrives at 0s, B and C at 10s.
  * Response times: A=0s, B=0s, C=10s.
  * Average response time = ( \frac{0+0+10}{3} = 3.33 ) seconds.
* **Observation**: STCF is not ideal for response time, as it can delay the first execution of some jobs significantly.

**7.7 Round Robin (RR)**

* **Definition**: Each job runs for a time slice (scheduling quantum) before switching to the next job.
* **Mechanism**: Repeats until all jobs complete.
* **Example**:
  * Jobs A, B, and C, each needing 5s, arrive at the same time.
  * Time slice = 1s.
  * Jobs are interleaved, reducing wait time.
  * Average response time: RR = ( \frac{0+1+2}{3} = 1 ) second, SJF = ( \frac{0+5+10}{3} = 5 ) seconds.
* **Trade-off**: Shorter time slices improve response time but increase context switching overhead. Balance is crucial.

**7.8 Incorporating I/O**

* **Challenge**: Scheduling must also consider I/O operations.
* **Approach**: Effective scheduling of CPU and I/O bound jobs can optimize system performance.

**7.9 No More Oracle**

* **Reality**: Assumptions about job characteristics (arrival time, duration) are often unknown.
* **Solution**: Develop adaptive and predictive scheduling algorithms.

**7.10 Summary**

* **Core Concepts**: Various scheduling algorithms (FIFO, SJF, STCF, RR) each have strengths and weaknesses.
* **Metrics**: Turnaround time, response time, and fairness are critical in evaluating schedulers.
* **Trade-offs**: Balance between performance metrics and practical system constraints is necessary.

These detailed notes cover the fundamental aspects and examples from Chapter 7 of "Operating Systems: Three Easy Pieces".





#### Chapter 8: Scheduling - The Multi-Level Feedback Queue (MLFQ)

**Introduction**

* The Multi-Level Feedback Queue (MLFQ) scheduler aims to optimize system responsiveness and turnaround time without prior knowledge of job lengths.
* MLFQ dynamically adjusts job priorities based on their behavior to improve scheduling decisions.

**8.1 MLFQ: Basic Rules**

* MLFQ consists of multiple queues, each with a different priority level.
* Jobs are initially placed in the highest-priority queue and their priorities are adjusted based on their execution behavior.
* **Rules:**
  * **Rule 1:** If Priority(A) > Priority(B), A runs.
  * **Rule 2:** If Priority(A) = Priority(B), A and B run in Round Robin (RR).

**8.2 Attempt #1: How To Change Priority**

* Adjusting priorities based on job behavior:
  * **Rule 3:** A job entering the system is placed in the highest priority queue.
  * **Rule 4a:** If a job uses up its entire time slice, its priority is reduced.
  * **Rule 4b:** If a job relinquishes the CPU before its time slice is up, it retains its priority.
* Example with a long-running job:
  * The job starts at the highest priority queue and gradually moves to lower-priority queues as it uses up its time slices.

**8.3 Attempt #2: The Priority Boost**

* To prevent starvation, periodically boost the priority of all jobs.
* **Rule 5:** After a fixed period S, reset the priority of all jobs.
* Priority boosting ensures that long-running jobs get a chance to run, preventing starvation.

**8.4 Attempt #3: Better Accounting**

* To prevent gaming the scheduler by frequently relinquishing the CPU, track CPU usage more accurately.
* **Revised Rule 4:** Once a job uses up its time allotment at a given level, it is demoted regardless of how it used its CPU time.
* Example of preventing gaming:
  * A job that tries to relinquish the CPU just before its time slice ends to avoid priority reduction will still be demoted once it uses its allotted time.

**8.5 Tuning MLFQ and Other Issues**

* Various parameters need to be tuned for MLFQ, including the number of queues, time slice sizes, and priority boosting intervals.
* Time slices in higher-priority queues are typically shorter to accommodate interactive jobs, while lower-priority queues have longer time slices for CPU-bound jobs.
* Example of time slice variation:
  * Jobs may run for 10 ms in the highest queue, 20 ms in the middle, and 40 ms in the lowest queue.

**8.6 MLFQ: Summary**

* MLFQ is a flexible and adaptive scheduler that dynamically adjusts job priorities based on their behavior.
* Proper tuning of MLFQ parameters is essential for balancing responsiveness and throughput.
* Key strategies include periodic priority boosting and accurate accounting of CPU usage to prevent gaming and starvation.

These notes provide an overview of the key concepts and rules governing the Multi-Level Feedback Queue scheduler, illustrating how it manages job priorities to achieve efficient and fair scheduling in operating systems.



#### Detailed Notes on Chapter 9: "Scheduling: Proportional Share"

**Introduction**

* **Proportional-share scheduling** is also known as **fair-share scheduling**.
* Instead of optimizing for turnaround or response time, it ensures each job gets a certain percentage of CPU time.
* **Lottery scheduling** is a modern example, introduced by Waldspurger and Weihl, although the idea is older.

**9.1 Basic Concept: Tickets Represent Your Share**

* **Tickets** represent the share of a resource a process should receive.
* The percentage of tickets a process has equates to its share of the CPU.
* Example: If process A has 75 tickets and B has 25, A should get 75% of the CPU and B 25%.
* **Lottery Scheduling**: Every time slice, a lottery determines which process runs next. More tickets increase chances of winning.

**9.2 Ticket Mechanisms**

* **Use of randomness** in decision-making is a key feature.
* Advantages of randomness:
  1. Avoids corner-case behaviors that traditional algorithms struggle with.
  2. Lightweight, requiring minimal per-process state.
  3. Fast, as generating random numbers is quick.

**9.3 Implementation**

* To hold a lottery, the scheduler must know the total number of tickets.
* A random number is generated within the ticket range, and the process holding the corresponding ticket wins the CPU time.

**9.4 An Example**

* Example with two jobs, each having 100 tickets and the same runtime.
* **Unfairness metric (U)**: Ratio of the completion time of the first job to the second.
* Ideal outcome is U = 1 (both jobs finish at the same time).
* Randomness can cause significant unfairness, especially for short job lengths. Unfairness decreases as job length increases.

**9.5 How To Assign Tickets?**

* Ticket assignment is crucial and challenging.
* One approach is to let users assign tickets, assuming they know best.
* This approach is a "non-solution" because it doesn’t provide concrete guidelines for ticket distribution among jobs.

**9.6 Why Not Deterministic?**

* Deterministic scheduling (e.g., **stride scheduling**) can ensure exact proportions.
* **Stride Scheduling**: Each process has a stride inversely proportional to its tickets. A pass value tracks progress, ensuring exact share over time.
* Drawbacks of stride scheduling:
  * Requires global state management.
  * Introducing new jobs requires careful handling to avoid monopolization.
* Lottery scheduling, with no global state per process, easily accommodates new processes.

**9.7 Summary**

* Proportional-share scheduling uses either randomness (lottery scheduling) or deterministic methods (stride scheduling) to ensure fair CPU distribution.
* Both methods are conceptually interesting but have not been widely adopted.

**Key Points**

* **Tickets**: Fundamental unit representing a process’s share of CPU time.
* **Randomness**: Used in lottery scheduling to probabilistically achieve fair share.
* **Unfairness metric**: Measures how close the scheduling is to ideal fairness.
* **Stride Scheduling**: Deterministic alternative to achieve exact proportions.
* **Implementation Challenges**: Assigning tickets and handling new jobs in stride scheduling.

These notes cover the essential aspects and mechanisms of proportional-share scheduling as discussed in Chapter 9 of "Operating Systems: Three Easy Pieces"





#### Detailed Notes on Chapter 10: Multiprocessor Scheduling (Advanced)

**Introduction**

* Multiprocessor scheduling has become crucial with the advent of multicore processors, now common in desktops, laptops, and mobile devices.
* Traditional single-CPU scheduling principles need to be extended and adapted for multiple CPUs.
* The chapter covers the architecture, challenges, and strategies for multiprocessor scheduling.

**10.1 Background: Multiprocessor Architecture**

* **Single-CPU Systems**: Utilize a hierarchy of hardware caches to speed up access to frequently used data, exploiting temporal and spatial locality.
* **Multiprocessor Systems**: Have multiple CPUs sharing a single main memory. Each CPU typically has its own cache, leading to complexities in data consistency and cache coherence.
* **Cache Coherence**: A key challenge where multiple caches may hold copies of the same data, and changes in one cache must be reflected across all caches.
* **Snooping and Directory-Based Protocols**: Mechanisms to maintain cache coherence, ensuring data consistency across caches.

**10.2 Don’t Forget Synchronization**

* **Synchronization**: Crucial for ensuring that multiple threads/processes can operate correctly without interfering with each other.
* **Locks and Semaphores**: Common synchronization mechanisms that help coordinate access to shared resources.
* **False Sharing**: A performance issue where processors unnecessarily invalidate cache lines, leading to degraded performance.

**10.3 One Final Issue: Cache Affinity**

* **Cache Affinity**: Keeping a process on the same CPU to benefit from the data already loaded in the cache, improving performance.
* **Migrating Processes**: Can negate cache affinity benefits, so scheduling strategies often aim to minimize unnecessary migrations.

**10.4 Single-Queue Scheduling**

* **Centralized Queue**: All processes are kept in a single queue, and any available CPU can pick the next process to execute.
* **Advantages**: Simplicity and inherent load balancing.
* **Disadvantages**: Potential bottlenecks and scalability issues as the number of CPUs increases.

**10.5 Multi-Queue Scheduling**

* **Separate Queues**: Each CPU has its own queue of processes.
* **Advantages**: Reduced contention and better scalability.
* **Disadvantages**: Potential load imbalance where some CPUs are idle while others are overloaded.
* **Load Balancing**: Achieved through process migration between queues, often using techniques like work stealing.
* **Work Stealing**: An approach where idle CPUs (with empty queues) "steal" work from busy CPUs (with full queues).

**10.6 Linux Multiprocessor Schedulers**

* **Linux Approach**: Combines both single-queue and multi-queue strategies. Modern Linux schedulers use a multi-level feedback queue for each CPU and periodically balance loads across CPUs.
* **Completely Fair Scheduler (CFS)**: Default scheduler in Linux, aims to distribute CPU time fairly among all processes.

**10.7 Summary**

* Multiprocessor scheduling addresses unique challenges not present in single-CPU systems.
* Effective multiprocessor scheduling strategies must consider cache affinity, synchronization, and load balancing to optimize performance.
* Various strategies and mechanisms, such as centralized and distributed queues, work stealing, and specific Linux schedulers, help manage these challenges.

**References and Further Reading**

* The chapter references additional resources for those interested in deeper technical details and advanced topics related to multiprocessor scheduling.

This summary captures the core concepts and mechanisms discussed in Chapter 10, providing a comprehensive overview of multiprocessor scheduling techniques and considerations.



#### Summary of Chapter 11: Summary Dialogue on CPU Virtualization

**Introduction**

In Chapter 11 of "Operating Systems: Three Easy Pieces," the dialogue format features a conversation between a professor and a student discussing the key concepts and mechanisms related to CPU virtualization. This conversational approach reinforces the learning points covered in previous chapters.

**Key Points Discussed**

1. **Understanding CPU Virtualization:**
   * The student recaps that CPU virtualization involves creating the illusion of multiple CPUs using mechanisms such as traps, trap handlers, timer interrupts, and state-saving techniques during process switching.
   * These mechanisms allow the operating system (OS) to manage the CPU efficiently and securely, ensuring that each process behaves as if it has dedicated access to the CPU, even though it is shared among multiple processes.
2. **Complex Interactions and Learning:**
   * The professor emphasizes that while understanding the theory is important, hands-on practice through class projects is crucial for truly grasping the complexity of CPU virtualization. Engaging in practical work helps solidify theoretical concepts and reveals the intricacies of real-world OS behavior.
3. **Philosophy of the OS:**
   * The student notes that the OS operates with a degree of paranoia, aiming to maintain control over the system while allowing programs to run efficiently. This balance is necessary to prevent errant or malicious processes from disrupting the system.
   * The OS's role as a resource manager is highlighted, ensuring fair and efficient distribution of the CPU among competing processes.
4. **Scheduling Policies:**
   * The dialogue touches upon the importance of scheduling policies, with a particular focus on the Multi-Level Feedback Queue (MLFQ) scheduler. MLFQ attempts to combine the best features of Shortest Job First (SJF) and Round Robin (RR) scheduling to optimize process management.
   * The complexity of designing an effective scheduler is acknowledged, with references to ongoing debates and developments in scheduling algorithms, such as the Linux scheduler battles between Completely Fair Scheduler (CFS), Brain Fuck Scheduler (BFS), and the O(1) scheduler.

**Conclusion**

The chapter concludes with an emphasis on the student's growing understanding of OS mechanisms and policies. The professor encourages continued exploration and practical application of these concepts to deepen the student's knowledge and proficiency in operating systems.

This dialogue serves as a summary and reinforcement of the key ideas related to CPU virtualization, providing a conversational and reflective approach to the subject matter.

#### Key Takeaways

* **CPU Virtualization Mechanisms:** Traps, trap handlers, timer interrupts, and state-saving techniques.
* **Hands-On Learning:** Practical projects are essential for understanding OS concepts.
* **OS Philosophy:** The OS must balance efficiency with control to manage resources effectively.
* **Scheduling Policies:** MLFQ combines SJF and RR, highlighting the complexity of scheduler design.
* **Continued Exploration:** Encouragement to delve deeper into OS mechanisms through practical work.

This chapter effectively summarizes the critical aspects of CPU virtualization and reinforces learning through a conversational review.





#### Detailed Notes on Chapter 12: "A Dialogue on Memory Virtualization" from _Operating Systems: Three Easy Pieces_

**Introduction to Memory Virtualization**

* **Concept Overview:**
  * Virtualization of the CPU was discussed earlier, but now the focus shifts to the complexity of memory virtualization.
  * Memory virtualization involves intricate details about hardware and OS interactions.

**Basic Principles**

* **Virtual Addresses:**
  * All addresses generated by a user program are virtual addresses.
  * The OS provides an illusion to each process that it has its own large, private memory.
  * The OS and hardware collaborate to translate these virtual addresses into real physical addresses.

**Importance and Challenges**

* **Ease of Use:**
  * Virtual memory simplifies programming by providing a large, contiguous address space for code and data.
  * Programmers do not need to worry about the physical location of data.
* **Isolation and Protection:**
  * Virtual memory ensures that processes are isolated from one another.
  * It prevents one program from accessing or modifying another program's memory.

**Techniques for Memory Virtualization**

* **Base and Bounds:**
  * Basic technique for implementing memory virtualization.
  * Provides a simple method to ensure each process runs in its own memory space.
* **Translation Lookaside Buffers (TLBs):**
  * TLBs are small hardware caches that store recent address translations.
  * They are crucial for performance, preventing frequent slow lookups in larger page tables.

**Advanced Structures**

* **Page Tables:**
  * Page tables are data structures used to manage virtual to physical address translations.
  * Various forms of page tables exist, including linear page tables and multi-level page tables.
* **Multi-Level Page Tables:**
  * These structures reduce memory usage by allocating space for address translations only when necessary.
  * They efficiently manage sparse address spaces.

**Practical Considerations**

* **Swapping to Disk:**
  * When physical memory is insufficient, pages can be swapped to disk.
  * Page replacement policies (like LRU) determine which pages to swap out.
* **Real-World Implications:**
  * Mechanisms of virtual memory systems are crucial for understanding performance impacts and limitations.
  * Policies and mechanisms must balance complexity and efficiency to meet user needs.

**Summary**

* **Key Takeaways:**
  * Virtual memory provides a simplified and protected memory environment for each process.
  * Understanding the details of how virtual addresses are translated to physical addresses is critical.
  * Advanced data structures and hardware support (like TLBs) are essential for efficient memory virtualization.
  * Practical implementation involves balancing various trade-offs to achieve optimal system performance.

