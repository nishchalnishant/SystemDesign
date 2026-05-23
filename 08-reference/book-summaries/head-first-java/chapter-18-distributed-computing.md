---
module: 08-reference
topic: Book Summaries
subtopic: Head First Java
status: unread
tags: [08-reference, system-design, book-summaries]
---
# Ch 18: Distributed Computing

**Source**: Head First Java, Second Edition | **Pages**: 641-682

## 🎯 Learning Objectives

Remote Method Invocation

## 📚 Key Concepts

* RMI concept
* Stubs and skeletons
* Remote interface
* RMI registry
* Distributed computing
* Servlets intro
* EJB overview

***

## 📖 Detailed Notes

### 1. RMI concept

_Essential concept for mastering Java and OOP._

**Example**:

```java
class Foo {
    void go() {
         Bar b = new Bar();
         b.doStuff();
    }
    public static void main (String[] args) {
       Foo f = new Foo();
       f.go();
    }
}
```

### 2. Stubs and skeletons

_Essential concept for mastering Java and OOP._

**Example**:

```java
the JVMs are on the same or different physical machines; 
```

### 3. Remote interface

_Essential concept for mastering Java and OOP._

**Example**:

```java
public interface 
```

### 4. RMI registry

_Essential concept for mastering Java and OOP._

**Example**:

```java
Remote { }
```

### 5. Distributed computing

_Essential concept for mastering Java and OOP._

**Example**:

```java
public interface 
```

### 6. Servlets intro

_Essential concept for mastering Java and OOP._

**Example**:

```java
Remote { }
```

### 7. EJB overview

_Essential concept for mastering Java and OOP._

**Example**:

```java
the client will use as the polymorphic class 
```

***

## 💡 Important Points to Remember

* thing is to find out exactly where your servlet class files
* method! The method of the
* that even though
* that fabulous little ‘music video’ program from the first
* the Servlet library

***

## ✅ Self-Check Questions

Test your understanding:

1. Can you explain the main concepts covered in this chapter?
2. Can you write code examples demonstrating these concepts?
3. Do you understand when and why to use these features?
4. Can you explain the benefits and tradeoffs?

## 🔄 Quick Revision Points

* [ ] RMI concept
* [ ] Stubs and skeletons
* [ ] Remote interface
* [ ] RMI registry
* [ ] Distributed computing
* [ ] Servlets intro
* [ ] EJB overview

***

## 📝 Practice Exercises

1. Write your own code examples for each key concept
2. Modify existing examples to test edge cases
3. Explain concepts to someone else
4. Create a small project using these concepts

## 🔗 Related Chapters

Review related concepts from other chapters to build comprehensive understanding.

***

_For complete details, diagrams, and all examples, refer to Head First Java Second Edition, pages 641-682._

## Chapter 18: Distributed Computing — Study Notes

This chapter explores how to break your application into parts that run on different machines but work together as a single system. It covers RMI (Remote Method Invocation), Servlets, and Jini, focusing on how Java objects can talk to each other across a network.

### 1. Remote Method Invocation (RMI)

RMI allows a Java object on one machine (the client) to call a method on a Java object on a different machine (the server) just as if it were a local object.

#### The Big Picture

* The Goal: You want to call a method on a remote object (e.g., `serverObject.doStuff()`).
* The Problem: The `serverObject` is on a different heap in a different memory space. You can't get a direct reference to it.
* The Solution: You use a proxy (called a Stub) on the client side. The client calls the method on the stub, and the stub handles the networking to talk to the real service.

#### The RMI Architecture

1. Client: Calls a method on the `Stub`.
2. Stub (Proxy): Takes the call, packs the arguments (marshalling), and sends them over the network.
3. Skeleton: Receives the call on the server side, unpacks the arguments, and calls the actual method on the real service object.
4. Service Object: Does the work and returns the result to the Skeleton.
5. Skeleton: Packs the result and sends it back to the Stub.
6. Stub: Unpacks the result and returns it to the client.

#### Making an RMI Service (5 Steps)

1. Make a Remote Interface: Define the methods that the client can call.
   * Must extend `java.rmi.Remote`.
   * All methods must declare `throws RemoteException`.
2. Make a Remote Implementation: Implement the interface.
   * Typically extends `UnicastRemoteObject`.
   * Must write a constructor that declares `throws RemoteException`.
3. Generate Stub and Skeleton: Run `rmic` on your implementation class (e.g., `rmic MyServiceImpl`).
4. Start the RMI Registry: Run `rmiregistry` from the command line. This is like the phone book for RMI services.
5. Start the Service: Run your service class. It must register itself with the RMI registry using `Naming.rebind("ServiceName", serviceObject)`.

***

### 2. Servlets (Web Applications)

Servlets are Java programs that run on a web server (like Apache Tomcat) and handle requests from web browsers.

#### How it Works

1. User: Types a URL into a browser (e.g., `http://myserver.com/servlets/MyServlet`).
2. Web Server: Receives the request and realizes it's for a servlet, not a static HTML file.
3. Servlet: The server loads the servlet class and calls its `doGet()` or `doPost()` method.
4. Response: The servlet writes an HTML response (using `out.println()`) which the server sends back to the user's browser.

#### Simple Servlet Code

Java

```
public class MyServlet extends HttpServlet {
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        out.println("<html><body>");
        out.println("<h1>Hello from a Servlet!</h1>");
        out.println("</body></html>");
    }
}
```

***

### 3. Jini (Universal Network Plug-and-Play)

Jini (now Apache River) is a technology for building distributed systems that are dynamic and adaptive. It allows devices and services to find each other on a network without prior configuration.

#### Key Concepts

* Discovery: A service searches the network for a "Lookup Service" (like an RMI registry, but smarter).
* Join: The service registers itself with the Lookup Service, uploading its own proxy (serialized object).
* Lookup: A client asks the Lookup Service for a specific type of service (e.g., "I need a printer").
* Service Object: The Lookup Service sends the service's proxy to the client. The client now talks directly to the service.

#### Self-Healing Networks

Jini uses Leasing. When a service registers, it gets a "lease" for a fixed time. The service must periodically renew the lease. If the service crashes (or is unplugged), the lease expires, and the Lookup Service automatically removes it from the list.

***

### 4. Revision Checklist

*   RMI Exceptions: Remember that _every_ remote method call is risky (network down, server crash), so they all throw `RemoteException`.

    <a class="button secondary"></a>
* Serialization in RMI: Arguments and return values in RMI are passed by value (copies) using serialization, not by reference (unless they are remote objects themselves).
* Interface Rule: The client always uses the Remote Interface type (e.g., `MyService`), never the implementation class type (e.g., `MyServiceImpl`).
* Servlet Output: Servlets create dynamic web pages by printing HTML tags as Strings to the response stream.
