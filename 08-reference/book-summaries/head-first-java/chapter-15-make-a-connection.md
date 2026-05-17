# Ch 15: Make a Connection

**Source**: Head First Java, Second Edition | **Pages**: 505-562

## 🎯 Learning Objectives

Networking and concurrency

## 📚 Key Concepts

* Networking basics
* Sockets
* TCP/IP overview
* Threads
* Runnable interface
* Thread class
* Thread safety issues
* synchronized keyword
* Building chat client

***

## 📖 Detailed Notes

### 1. Networking basics

_Essential concept for mastering Java and OOP._

**Example**:

```java
Socket chatSocket = new Socket(“196.164.1.103”, 5000);
```

### 2. Sockets

_Essential concept for mastering Java and OOP._

**Example**:

```java
Socket chatSocket = new Socket(“127.0.0.1”, 5000);
```

### 3. TCP/IP overview

_Essential concept for mastering Java and OOP._

**Example**:

```java
BufferedReader reader = new BufferedReader(stream);
String message = reader.readLine();
```

### 4. Threads

_Essential concept for mastering Java and OOP._

**Example**:

```java
InputStreamReader stream = new InputStreamReader(chatSocket.getInputStream());
```

### 5. Runnable interface

_Essential concept for mastering Java and OOP._

**Example**:

```java
Socket chatSocket = new Socket(“127.0.0.1”, 5000);
```

### 6. Thread class

_Essential concept for mastering Java and OOP._

**Example**:

```java
writer.println(“message to send”);
writer.print(“another message”);
```

### 7. Thread safety issues

_Essential concept for mastering Java and OOP._

**Example**:

```java
PrintWriter writer = new PrintWriter(chatSocket.getOutputStream());
```

### 8. synchronized keyword

_Essential concept for mastering Java and OOP._

**Example**:

```java
import java.io.*;
import java.net.*;
public class DailyAdviceClient {    
   public void go() {
       try {
           Socket s = new Socket(“127.0.0.1”, 4242);
           InputStreamReader streamReader = new InputStreamReader(s.getInputStream());
           BufferedReader reader = new BufferedReader(streamReader);
           String advice = reader.readLine();
           System.out.println(“Today you should: “ + advice);
           reader.close();
        } catch(IOException ex) {
           ex
```

***

## 💡 Important Points to Remember

* a
* the imports
* that
* our earlier example that kept giving us different
* for you physics-savvy readers: yes, the convention of using the word ‘atomic’ here does not reflect

***

## ✅ Self-Check Questions

Test your understanding:

1. Can you explain the main concepts covered in this chapter?
2. Can you write code examples demonstrating these concepts?
3. Do you understand when and why to use these features?
4. Can you explain the benefits and tradeoffs?

## 🔄 Quick Revision Points

* [ ] Networking basics
* [ ] Sockets
* [ ] TCP/IP overview
* [ ] Threads
* [ ] Runnable interface
* [ ] Thread class
* [ ] Thread safety issues
* [ ] synchronized keyword
* [ ] Building chat client

***

## 📝 Practice Exercises

1. Write your own code examples for each key concept
2. Modify existing examples to test edge cases
3. Explain concepts to someone else
4. Create a small project using these concepts

## 🔗 Related Chapters

Review related concepts from other chapters to build comprehensive understanding.

***

_For complete details, diagrams, and all examples, refer to Head First Java Second Edition, pages 505-562._



## Chapter 15: Make a Connection — Study Notes

This chapter moves beyond standalone applications to explore Networking and Multithreading. You will learn how to connect your Java programs to the outside world and how to do multiple things at once (like sending a message while listening for a reply).

***

### 1. Networking with Sockets

Java makes networking simple with the `java.net` package. The key concept is the Socket, which represents a connection between two machines.

#### The Client and Server Relationship

* Client: The machine that initiates the connection (e.g., your chat app).
  * Needs to know the IP Address (who to contact) and the TCP Port (which application to talk to).
* Server: The machine that waits for connections.
  * Runs on a specific port and listens for incoming requests.

#### TCP Ports

A port is a 16-bit number (0–65535) identifying a specific program on a server.

<a class="button secondary">+1</a>

*   0–1023: Reserved for well-known services (HTTP=80, FTP=21, Telnet=23). Don't use these for your own programs!.

    <a class="button secondary">+1</a>
*   1024–65535: Free for you to use (mostly).

    <a class="button secondary">+1</a>

#### Establishing a Connection

To connect to a server, you need a `Socket` object.

Java

```
// Connect to server at IP 196.164.1.103 on port 5000
Socket chatSocket = new Socket("196.164.1.103", 5000);
```

#### Reading from a Socket

Reading from a network is just like reading from a file. You use a `BufferedReader` chained to an `InputStreamReader`.

<a class="button secondary">+1</a>

1. Get the Stream: Ask the socket for its input stream.
2. Chain It: Wrap it in a reader to handle text.

Java

```
InputStreamReader stream = new InputStreamReader(chatSocket.getInputStream());
BufferedReader reader = new BufferedReader(stream);
String message = reader.readLine();
```

#### Writing to a Socket

Writing is also standard I/O using `PrintWriter`.

<a class="button secondary">+1</a>Java

```
PrintWriter writer = new PrintWriter(chatSocket.getOutputStream());
writer.println("message to send");
writer.print("another message");
```

***

### 2. Multithreading (Doing Two Things at Once)

In a chat program, you need to send messages and receive them at the same time. You can't just wait in a loop for the user to type (sending) because you might miss an incoming message. The solution is Threads.

#### Threads vs. Processes

* Process: Has its own memory space (like a separate program running).
*   Thread: A separate "path of execution" within the _same_ program. Threads share the same heap (objects) but have their own stacks (method calls).

    <a class="button secondary">+1</a>

#### Creating a Thread

To launch a new thread, you need two things:

1.  The Job: A class that implements the `Runnable` interface. This class must have a `run()` method containing the code you want the thread to execute.

    <a class="button secondary">+1</a>
2. The Worker: A `Thread` object. You pass the `Runnable` (the job) to the `Thread` constructor.

Java

```
public class MyJob implements Runnable {
    public void run() {
        // Code to run in the new thread
        System.out.println("Job running");
    }
}

public class ThreadTester {
    public static void main(String[] args) {
        Runnable job = new MyJob();      // 1. Create the job
        Thread worker = new Thread(job); // 2. Create the worker, give it the job
        worker.start();                  // 3. Start the thread!
    }
}
```

#### Thread States

* New: A `Thread` object has been created but not started.
* Runnable: You called `start()`. The thread is ready and waiting for the Scheduler to choose it.
* Running: The Scheduler has selected this thread to execute code.
*   Blocked/Waiting: The thread is waiting for something (like data from a stream or a `sleep()` timeout) and cannot run until that event occurs.

    <a class="button secondary"></a>

***

### 3. The Thread Scheduler

The Scheduler decides which thread gets to run and for how long.

*   Unpredictability: You cannot guarantee the order in which threads will run. Different operating systems and JVMs may schedule threads differently.

    <a class="button secondary"></a>
*   `Thread.sleep()`: A method to force a thread to pause, giving other threads a chance to run.

    Java

    ```
    try {
        Thread.sleep(2000); // Sleep for 2 seconds
    } catch (InterruptedException ex) {
        ex.printStackTrace();
    }
    ```

***

### 4. Concurrency Issues (The "Ryan and Monica" Problem)

When two threads access the same object (shared data) at the same time, data corruption can occur. This is often called a Race Condition.

<a class="button secondary"></a>

* Example: Two threads try to withdraw money from the same bank account simultaneously. Both check the balance (100), see it's enough, and withdraw 100. Result: The account is overdrawn because they "interleaved" their actions.

#### Synchronization

To fix race conditions, you must make the operations atomic (indivisible). You do this with the `synchronized` keyword.

<a class="button secondary"></a>

* Locks: Every object has a "lock" (or monitor). When a method is `synchronized`, a thread must acquire the object's lock before entering the method.
* Behavior: If Thread A has the lock, Thread B must wait outside the method until Thread A finishes and releases the lock.

Java

```
public synchronized void withdraw(int amount) {
    // Only one thread can be in here at a time!
    if (balance >= amount) {
        balance = balance - amount;
    }
}
```

***

### 5. Revision Checklist

* `implements Runnable`: Remember that to make a job for a thread, you implement `Runnable`, not extend `Thread`.
* `start()` vs. `run()`: Always call `worker.start()` to launch a new thread of execution. If you call `run()` directly, the code just runs in the _current_ thread, which defeats the purpose.
* Closing Sockets: Like files, sockets should be closed when you are done to release resources.
*   Lost Update: Be aware that `i++` is not an atomic operation (it's read, increment, write). If multiple threads increment a variable without synchronization, some updates will be lost.

    <a class="button secondary"></a>
