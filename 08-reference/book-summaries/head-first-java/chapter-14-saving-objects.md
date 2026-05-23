---
module: 08-reference
topic: Book Summaries
subtopic: Head First Java
status: unread
tags: [08-reference, system-design, book-summaries]
---
# Ch 14: Saving Objects

**Source**: Head First Java, Second Edition | **Pages**: 463-504

## 🎯 Learning Objectives

Serialization and File I/O

## 📚 Key Concepts

* Serialization concept
* ObjectOutputStream
* ObjectInputStream
* Serializable interface
* transient keyword
* File I/O
* Saving and loading objects
* Version control for serialized classes

***

## 📖 Detailed Notes

### 1. Serialization concept

_Essential concept for mastering Java and OOP._

**Example**:

```java
È{Gxptbowtswordtdustsq~»tTrolluq~tb
```

### 2. ObjectOutputStream

_Essential concept for mastering Java and OOP._

**Example**:

```java
FileOutputStream fileStream = new FileOutputStream(“MyGame.ser”);
```

### 3. ObjectInputStream

_Essential concept for mastering Java and OOP._

**Example**:

```java
os.writeObject(characterOne);
os.writeObject(characterTwo);
os.writeObject(characterThree);
```

### 4. Serializable interface

_Essential concept for mastering Java and OOP._

**Example**:

```java
os.close();
```

### 5. transient keyword

_Essential concept for mastering Java and OOP._

**Example**:

```java
ObjectOutputStream os = new ObjectOutputStream(fileStream);
```

### 6. File I/O

_Essential concept for mastering Java and OOP._

**Example**:

```java
you write objects but underneath converts them to bytes? Think good OO. Each class 
```

### 7. Saving and loading objects

_Essential concept for mastering Java and OOP._

**Example**:

```java
instance of a class different from 
```

### 8. Version control for serialized classes

_Essential concept for mastering Java and OOP._

**Example**:

```java
Foo myFoo = new Foo();
myFoo.setWidth(37);
myFoo.setHeight(70);
FileOutputStream fs = new FileOutputStream(“foo.ser”);
ObjectOutputStream os = new ObjectOutputStream(fs);
os.writeObject(myFoo);
```

***

## 💡 Important Points to Remember

* that the Dog
* those flashcards you used in school? Where you
* split() is FAR more powerful than

***

## ✅ Self-Check Questions

Test your understanding:

1. Can you explain the main concepts covered in this chapter?
2. Can you write code examples demonstrating these concepts?
3. Do you understand when and why to use these features?
4. Can you explain the benefits and tradeoffs?

## 🔄 Quick Revision Points

* [ ] Serialization concept
* [ ] ObjectOutputStream
* [ ] ObjectInputStream
* [ ] Serializable interface
* [ ] transient keyword
* [ ] File I/O
* [ ] Saving and loading objects
* [ ] Version control for serialized classes

***

## 📝 Practice Exercises

1. Write your own code examples for each key concept
2. Modify existing examples to test edge cases
3. Explain concepts to someone else
4. Create a small project using these concepts

## 🔗 Related Chapters

Review related concepts from other chapters to build comprehensive understanding.

***

_For complete details, diagrams, and all examples, refer to Head First Java Second Edition, pages 463-504._



## Chapter 14: Saving Objects — Study Notes

This chapter focuses on data persistence—saving the state of your program so it can be reloaded later. It covers two main approaches: Serialization (saving whole objects) and File I/O (writing data to text files).

***

### 1. Serialization (Saving Objects)

Serialization is the process of "flattening" an object into a stream of bytes so it can be stored in a file or sent over a network. When you serialize an object, you save its state (instance variables).

#### The Serialized File

* What it is: A file containing the serialized bytes of an object.
*   Readability: It is not human-readable text; it's a binary format understood by the Java Virtual Machine (JVM).

    <a class="button secondary"></a>

#### How to Serialize

To make an object serializable, its class must implement the `Serializable` interface.

* Interface: `java.io.Serializable`
* Marker Interface: It has no methods to implement. It serves only as a "stamp" to tell the JVM, "It's okay to serialize objects of this type."

Java

```
import java.io.*;

public class Box implements Serializable { // 1. Implement Serializable
    private int width;
    private int height;

    public static void main(String[] args) {
        Box myBox = new Box();
        myBox.width = 50;
        myBox.height = 20;

        try {
            // 2. Make a FileOutputStream (connection to the file)
            FileOutputStream fs = new FileOutputStream("foo.ser");
            
            // 3. Make an ObjectOutputStream (the chain stream that does the magic)
            ObjectOutputStream os = new ObjectOutputStream(fs);
            
            // 4. Write the object
            os.writeObject(myBox);
            
            // 5. Close the stream
            os.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
```

#### The Object Graph

When an object is serialized, the JVM saves everything that object refers to.

* Cascading Save: If a `Dog` object has a `Collar` instance variable, serializing the `Dog` automatically serializes the `Collar`. If the `Collar` has a `Color` object, that gets saved too.
* Requirement: All objects in the graph must be `Serializable`. If any object in the chain is not `Serializable`, a `NotSerializableException` is thrown at runtime.

#### Skipping Variables: `transient`

If a variable should not (or cannot) be saved, mark it as `transient`. The serialization process will skip it, and when the object is restored, that variable will return as `null` (or the default primitive value).

Java

```
transient String currentID; // This won't be saved
```

***

### 2. Deserialization (Restoring Objects)

Deserialization is the reverse process: reading the bytes from a file and inflating them back into a living Java object on the heap.

#### Steps to Restore

1. Open File: `FileInputStream fileStream = new FileInputStream("MyGame.ser");`
2. Make ObjectStream: `ObjectInputStream os = new ObjectInputStream(fileStream);`
3. Read Object: `Object one = os.readObject();`
4. Cast: The return type is `Object`, so you must cast it back to your specific type (e.g., `GameCharacter elf = (GameCharacter) one;`).
5.  Close: `os.close();`.

    <a class="button secondary"></a>

#### Key Rules of Restoration

* Class Found: The JVM must be able to find the class file for the object. If the class is missing, deserialization fails.
* Constructors:
  * The constructor for the serialized object is NOT run. (This preserves the object's saved state rather than resetting it).
  * However, if a superclass in the hierarchy is _not_ serializable, its constructor _will_ run.
* Static Variables: Static variables are not saved because they belong to the class, not the object. When an object is deserialized, it sees whatever static value the class currently has.

***

### 3. Writing to Text Files

Sometimes you need to write data that other programs (like Notepad or Excel) can read. For this, you write plain text files.

#### The `java.io.File` Class

* Purpose: Represents a file on the disk, but _not_ the content of the file. It’s an abstract path representation.
* Capabilities:
  * Make a new directory: `dir.mkdir()`
  * List files in a directory: `if (dir.isDirectory()) { String[] dirContents = dir.list(); }`
  * Get absolute path: `dir.getAbsolutePath()`
  * Delete a file or directory: `didItWork = myFile.delete()`

#### Writers and Readers

*   Writing: Use `FileWriter` to write characters to a file.

    Java

    ```
    FileWriter writer = new FileWriter("Foo.txt");
    writer.write("hello world!");
    writer.close();
    ```
*   Buffers: For efficiency, wrap your `FileWriter` in a `BufferedWriter`. This reduces the number of trips to the disk by holding data in memory until the buffer is full.

    Java

    ```
    BufferedWriter writer = new BufferedWriter(new FileWriter(file));
    ```

***

### 4. Reading from Text Files

To read a text file, the standard approach involves chaining streams to parse the text line-by-line.

#### The Chain

1. `File`: Represents the file on disk.
2. `FileReader`: Connects to the text file.
3. `BufferedReader`: Reads text efficiently and allows reading one line at a time.

Java

```
try {
    File myFile = new File("MyText.txt");
    FileReader fileReader = new FileReader(myFile);
    BufferedReader reader = new BufferedReader(fileReader);

    String line = null;
    while ((line = reader.readLine()) != null) {
        System.out.println(line);
    }
    reader.close();
} catch (Exception ex) {
    ex.printStackTrace();
}
```

#### Parsing Strings

*   `split()`: A method in the `String` class that breaks a string into an array of pieces (tokens) based on a delimiter.

    Java

    ```
    String toTest = "What is blue + yellow?/green";
    String[] result = toTest.split("/"); 
    // result[0] is "What is blue + yellow?"
    // result[1] is "green"
    ```

***

### 5. `serialVersionUID`

* Versioning: Every time you change a class (e.g., add a method), the class gets a new unique "version ID".
* The Conflict: If you serialize an object (ID: 100), change the class (ID: 101), and then try to deserialize the old object, the JVM will throw an exception because the IDs don't match.
* The Fix: You can manually define a `static final long serialVersionUID` in your class. This tells the JVM, "Trust me, this version of the class is compatible with the old serialized objects," preventing the crash even if you've made changes to the class methods.

***

### 6. Revision Checklist

* Connection vs. Chain Streams: Remember that "Connection" streams (like `FileOutputStream`) connect to a source/destination, while "Chain" streams (like `ObjectOutputStream`) perform high-level processing (like serialization).
*   Closing: Always close your streams! Closing the top-level chain stream automatically closes the underlying connection stream.

    <a class="button secondary"></a>
* Buffers: Use `BufferedWriter` and `BufferedReader` for better performance when working with text files.
* Transient: Use `transient` for sensitive data (like passwords) or nonserializable object references (like network sockets) that shouldn't be saved.
