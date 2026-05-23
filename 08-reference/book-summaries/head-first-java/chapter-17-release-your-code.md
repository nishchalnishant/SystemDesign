---
module: 08-reference
topic: Book Summaries
subtopic: Head First Java
status: unread
tags: [08-reference, system-design, book-summaries]
---
# Ch 17: Release Your Code

**Source**: Head First Java, Second Edition | **Pages**: 615-640

## 🎯 Learning Objectives

Packages, JARs, and deployment

## 📚 Key Concepts

* Package naming
* Creating packages
* Compiling with packages
* JAR files
* Creating JARs
* Manifest file
* Executable JARs
* Java Web Start

***

## 📖 Detailed Notes

### 1. Package naming

_Essential concept for mastering Java and OOP._

**Example**:

```java
out the class files to give them to an end-user. What’s really 
```

### 2. Creating packages

_Essential concept for mastering Java and OOP._

**Example**:

```java
nine class files, since the client already has the 
```

### 3. Compiling with packages

_Essential concept for mastering Java and OOP._

**Example**:

```java
files (nine source code files and nine compiled class 
```

### 4. JAR files

_Essential concept for mastering Java and OOP._

**Example**:

```java
that the compiler has to generate class files 
for all those inner class GUI event listeners 
```

### 5. Creating JARs

_Essential concept for mastering Java and OOP._

**Example**:

```java
 Now he has to carefully extract all the class 
```

### 6. Manifest file

_Essential concept for mastering Java and OOP._

**Example**:

```java
Separate source code and class files
A single directory with a pile of source code and class files is a 
```

### 7. Executable JARs

_Essential concept for mastering Java and OOP._

**Example**:

```java
code separate. In other words, making sure his compiled class 
```

### 8. Java Web Start

_Essential concept for mastering Java and OOP._

**Example**:

```java
a choice about putting the class 
```

***

## 💡 Important Points to Remember

* way back in chapter 6 when we discussed how
* everything in this chapter assumes that the
* when we say JAR in all caps, we’re referring

***

## ✅ Self-Check Questions

Test your understanding:

1. Can you explain the main concepts covered in this chapter?
2. Can you write code examples demonstrating these concepts?
3. Do you understand when and why to use these features?
4. Can you explain the benefits and tradeoffs?

## 🔄 Quick Revision Points

* [ ] Package naming
* [ ] Creating packages
* [ ] Compiling with packages
* [ ] JAR files
* [ ] Creating JARs
* [ ] Manifest file
* [ ] Executable JARs
* [ ] Java Web Start

***

## 📝 Practice Exercises

1. Write your own code examples for each key concept
2. Modify existing examples to test edge cases
3. Explain concepts to someone else
4. Create a small project using these concepts

## 🔗 Related Chapters

Review related concepts from other chapters to build comprehensive understanding.

***

_For complete details, diagrams, and all examples, refer to Head First Java Second Edition, pages 615-640._



## Chapter 17: Release Your Code — Study Notes

This chapter focuses on the final stage of development: Deployment. It covers how to organize your files, package them into a single executable file (JAR), and distribute them to users, either locally or over the web using Java Web Start.

***

### 1. Deployment Options

Once your code is finished, you need to get it into the user's hands. There are two primary ways to deploy a Java application:

*   Local Deployment (Executable JAR): The user runs the application solely on their local machine.

    <a class="button secondary"></a>
*   Remote Deployment (Java Web Start): The application is launched from a web browser, but it runs as a standalone application on the user's machine.

    <a class="button secondary"></a>

***

### 2. Organizing Your Code

Before deployment, you should separate your source code (`.java` files) from your compiled code (`.class` files) to keep your project clean.

<a class="button secondary"></a>

* Source Directory: Create a folder (e.g., `source`) for your `.java` files.
* Classes Directory: Create a folder (e.g., `classes`) for your compiled `.class` files.
*   Compiling: Use the `-d` (directory) flag to tell the compiler where to put the output.

    Bash

    ```
    javac -d ../classes MyApp.java
    ```

    (This command tells the compiler to put the resulting class files into the `classes` directory) .

    <a class="button secondary">+1</a>

***

### 3. JAR Files (Java ARchives)

A JAR file is a single file that holds all your application's classes and resources (like images and sounds). It effectively "zips" your project into one convenient package.

<a class="button secondary"></a>

#### Making an Executable JAR

To make a JAR "executable" (so the user can double-click it to run), you must tell the JVM which class contains the `main()` method.

1.  Create a Manifest File: Make a text file (e.g., `manifest.txt`) containing the following line:

    Plaintext

    ```
    Main-Class: MyApp
    ```

    (Note: You must hit return after this line so the file doesn't end on the text) .

    <a class="button secondary"></a>
2.  Run the `jar` Tool: Use the command line to create the JAR.

    Bash

    ```
    jar -cvmf manifest.txt app1.jar *.class
    ```

    * `-c`: Create a new archive.
    * `-v`: Verbose (print details to screen).
    * `-m`: Include manifest information.
    * `-f`: Output to a file (named `app1.jar`).

#### Running a JAR

*   Command Line: `java -jar app1.jar`.

    <a class="button secondary"></a>
* GUI: On most systems, you can simply double-click the `.jar` file.

***

### 4. Packages

Packages are used to prevent naming collisions. If you write a class named `Account` and someone else writes a class named `Account`, packages distinguish them (e.g., `com.mybank.Account` vs. `com.otherbank.Account`).

<a class="button secondary"></a>

#### Rules for Packages

*   Reverse Domain Name: The standard convention is to use your domain name in reverse (e.g., `com.headfirstjava.projects`).

    <a class="button secondary"></a>
* Directory Structure: The directory structure MUST match the package structure.
  *   If your class is in `package com.headfirstjava`, the source file must be in a directory named `com/headfirstjava`.

      <a class="button secondary">+1</a>
*   Declaration: Put the `package` statement at the very top of your source file.

    Java

    ```
    package com.headfirstjava;
    import javax.swing.*;
    public class MyApp { ... }
    ```

#### Compiling and Running with Packages

*   Compiling: You must be in the directory _above_ the package root.

    Bash

    ```
    javac -d ../classes com/headfirstjava/MyApp.java
    ```
*   Running: You must use the fully qualified name.

    Bash

    ```
    java com.headfirstjava.MyApp
    ```

    (The JVM looks inside the current directory for a folder named `com`, then `headfirstjava`, then the `MyApp.class` file) .

    <a class="button secondary"></a>

***

### 5. Java Web Start (JWS)

JWS allows users to launch your application by clicking a link on a web page. Once downloaded, the app runs outside the browser, just like a standard application.

<a class="button secondary"></a>

#### How it Works

1.  The Client: The user clicks a link to a `.jnlp` file.

    <a class="button secondary"></a>
2.  The Browser: The browser downloads the `.jnlp` file and starts the JWS Helper App (which comes installed with Java).

    <a class="button secondary"></a>
3.  The Helper App: It reads the `.jnlp` file to find the executable JAR, downloads it, and runs the `main()` method.

    <a class="button secondary"></a>

#### The `.jnlp` File

The Java Network Launch Protocol file is an XML document that describes your application. It includes:

* The location of the JAR file on the web server.
*   The name of the class with the `main()` method.

    <a class="button secondary"></a>

#### Steps to Deploy JWS

1.  JAR: Create an executable JAR of your application.

    <a class="button secondary"></a>
2. JNLP: Write the `.jnlp` file describing the app.
3.  Server: Upload both the JAR and the `.jnlp` file to your web server.

    <a class="button secondary"></a>
4.  MIME Type: Configure your web server to handle the `application/x-java-jnlp-file` MIME type so the browser knows what to do with it.

    <a class="button secondary"></a>
5.  HTML: Create a web page with a link to the `.jnlp` file.

    <a class="button secondary"></a>

***

### 6. Revision Checklist

* `java -d`: Remember that this flag builds the directory structure for you if it doesn't exist.
* Manifest newline: The most common mistake when making a JAR is forgetting the newline at the end of the manifest file.
* Fully Qualified Names: Once a class is in a package, you can no longer refer to it as just `MyApp`. You must use `com.domain.MyApp`.
*   Separation: Always keep source code and compiled code separate to prevent version control and deployment headaches.

    <a class="button secondary"></a>
