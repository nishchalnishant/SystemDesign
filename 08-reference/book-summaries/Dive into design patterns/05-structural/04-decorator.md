# Decorator

> **Also known as:** *Wrapper*
>
> **Intent:** Decorator is a structural design pattern that lets you **attach new behaviors to
> objects** by placing these objects inside **special wrapper objects** that contain the behaviors.

---

## Problem

Imagine that you're working on a **notification library** which lets other programs notify their users
about important events.

The initial version of the library was based on the `Notifier` class that had only a few fields, a
constructor and a single `send` method. The method could accept a **message** argument from a client
and send the message to a list of emails that were passed to the notifier via its constructor. A
third-party app which acted as a client was supposed to create and configure the notifier object
once, and then use it each time something important happened.

At some point, you realize that users of the library expect **more than just email notifications**.
Many of them would like to receive an **SMS** about critical issues. Others would like to be notified
on **Facebook** and, of course, the corporate users would love to get **Slack** notifications.

*How hard can that be?* You extended the `Notifier` class and put the additional notification methods
into new subclasses. Now the client was supposed to instantiate the desired notification class and use
it for all further notifications.

But then someone reasonably asked you, **"Why can't you use several notification types at once? If
your house is on fire, you'd probably want to be informed through every channel."**

You tried to address that problem by creating special subclasses which **combined** several
notification methods within one class. However, it quickly became apparent that this approach would
**bloat the code immensely** — not only the library code but the client code as well.

```
                          Notifier
     ┌──────────┬───────────┼───────────┬────────────┐
  SMS      Facebook      Slack    SMS+Facebook   SMS+Slack
                                Facebook+Slack   SMS+Facebook+Slack ...

              Combinatorial explosion of subclasses.
```

You have to find some other way to structure notification classes so that their number won't
accidentally break some Guinness record.

---

## Solution

Extending a class is the first thing that comes to mind when you need to alter an object's behavior.
However, **inheritance has several serious caveats**:

- **Inheritance is static.** You **can't alter the behavior of an existing object at runtime**. You can
  only replace the whole object with another one that's created from a different subclass.
- **Subclasses can have just one parent class.** In most languages, inheritance doesn't let a class
  inherit behaviors of multiple classes at the same time.

One of the ways to overcome these caveats is by using **Aggregation or Composition** instead of
Inheritance. Both alternatives work almost the same way: **one object has a reference to another and
delegates it some work**, whereas with inheritance, the object itself is able to do that work,
inheriting the behavior from its superclass.

With this new approach you can easily **substitute the linked "helper" object with another, changing
the behavior of the container at runtime**. An object can use the behavior of various classes, having
references to multiple objects and delegating them all kinds of work. **Aggregation/composition is the
key principle behind many design patterns**, including Decorator.

> **Inheritance vs. Aggregation**

**"Wrapper"** is the alternative nickname for the Decorator pattern that clearly expresses the main
idea. A wrapper is an object that can be **linked with some target object**. The wrapper contains
**the same set of methods as the target** and delegates to it all requests it receives. However, the
wrapper **may alter the result** by doing something either **before or after** it passes the request
to the target.

**When does a simple wrapper become the real decorator?** The wrapper implements the **same
interface** as the wrapped object. That's why, from the client's perspective, these objects are
**identical**. Make the wrapper's reference field accept **any object that follows that interface**.
This will let you **cover an object in multiple wrappers**, adding the combined behavior of all the
wrappers to it.

In our notifications example, we leave the simple **email** notification behavior inside the base
`Notifier` class, but turn all other notification methods into **decorators**.

The client code would need to wrap a basic notifier object into a set of decorators that match the
client's preferences. The resulting objects will be structured as a **stack**:

```
  Client ──► SlackDecorator ──► FacebookDecorator ──► SMSDecorator ──► Notifier
```

The **last decorator in the stack** is the object the client actually works with. Since all decorators
implement the same interface as the base notifier, the rest of the client code **won't care** whether
it works with the "pure" notifier object or the decorated one.

We could apply the same approach to other behaviors such as **formatting messages** or **composing the
recipient list**. The client can decorate the object with any custom decorators, as long as they follow
the same interface as the others.

---

## Real-World Analogy

**You get a combined effect from wearing multiple pieces of clothing.**

Wearing clothes is an example of using decorators. When you're cold, you wrap yourself in a
**sweater**. If you're still cold with a sweater, you can wear a **jacket** on top. If it's raining,
you can put on a **raincoat**. All of these garments **"extend" your basic behavior but aren't part of
you**, and you can easily take off any piece of clothing whenever you don't need it.

---

## Structure

1. **The Component** declares the common interface for both wrappers and wrapped objects.

2. **Concrete Component** is a class of objects being wrapped. It defines the **basic behavior**, which
   can be altered by decorators.

3. **The Base Decorator** class has a field for referencing a wrapped object. The field's type should
   be declared as the **component interface** so it can contain both concrete components and
   decorators. The base decorator **delegates all operations** to the wrapped object.

4. **Concrete Decorators** define **extra behaviors** that can be added to components dynamically.
   Concrete decorators override methods of the base decorator and execute their behavior **either
   before or after** calling the parent method.

5. **The Client** can wrap components in **multiple layers** of decorators, as long as it works with
   all objects via the component interface.

```
┌────────┐      ┌────────────────────────────┐
│ Client │─────►│ «interface» Component      │◄───────────────┐
└────────┘      ├────────────────────────────┤                │ wrappee
                │ execute()                  │                │
                └────────────────────────────┘                │
                      ▲                  ▲                    │
      ┌───────────────┴────┐   ┌─────────┴────────────────────┴──┐
      │ ConcreteComponent  │   │      BaseDecorator              │
      ├────────────────────┤   ├─────────────────────────────────┤
      │ execute()          │   │ wrappee: Component              │
      └────────────────────┘   │ BaseDecorator(c: Component)     │
                               │ execute() { wrappee.execute() } │
                               └─────────────────────────────────┘
                                      ▲                  ▲
                       ┌──────────────┴───┐   ┌──────────┴────────┐
                       │ ConcreteDecoratorA│  │ConcreteDecoratorB │
                       ├───────────────────┤  ├───────────────────┤
                       │ execute()         │  │ execute()         │
                       │ extra()           │  │ extra()           │
                       └───────────────────┘  └───────────────────┘
```

---

## Pseudocode

In this example, the Decorator pattern lets you **compress and encrypt sensitive data** independently
from the code that actually uses this data.

The application wraps the data source object with a **pair of decorators**. Both wrappers change the
way the data is written to and read from the disk:

- Just **before** the data is written to disk, the decorators **encrypt and compress** it. The
  original class writes the encrypted and protected data to the file **without knowing about the
  change**.
- Right **after** the data is read from disk, it goes through the same decorators, which
  **decompress and decode** it.

The decorators and the data source class implement the **same interface**, which makes them all
interchangeable in the client code.

```
  1    // The component interface defines operations that can be
  2    // altered by decorators.
  3    interface DataSource is
  4      method writeData(data)
  5      method readData():data
  6
  7    // Concrete components provide default implementations for the
  8    // operations. There might be several variations of these
  9    // classes in a program.
 10    class FileDataSource implements DataSource is
 11      constructor FileDataSource(filename) { ... }
 12
 13      method writeData(data) is
 14        // Write data to file.
 15
 16      method readData():data is
 17        // Read data from file.
 18
 19    // The base decorator class follows the same interface as the
 20    // other components. The primary purpose of this class is to
 21    // define the wrapping interface for all concrete decorators.
 22    // The default implementation of the wrapping code might include
 23    // a field for storing a wrapped component and the means to
 24    // initialize it.
 25    class DataSourceDecorator implements DataSource is
 26      protected field wrappee: DataSource
 27
 28      constructor DataSourceDecorator(source: DataSource) is
 29        wrappee = source
 30
 31      // The base decorator simply delegates all work to the
 32      // wrapped component. Extra behaviors can be added in
 33      // concrete decorators.
 34      method writeData(data) is
 35        wrappee.writeData(data)
 36
 37      // Concrete decorators may call the parent implementation of
 38      // the operation instead of calling the wrapped object
 39      // directly. This approach simplifies extension of decorator
 40      // classes.
 41      method readData():data is
 42        return wrappee.readData()
 43
 44    // Concrete decorators must call methods on the wrapped object,
 45    // but may add something of their own to the result. Decorators
 46    // can execute the added behavior either before or after the
 47    // call to a wrapped object.
 48    class EncryptionDecorator extends DataSourceDecorator is
 49      method writeData(data) is
 50        // 1. Encrypt passed data.
 51        // 2. Pass encrypted data to the wrappee's writeData
 52        // method.
 53
 54      method readData():data is
 55        // 1. Get data from the wrappee's readData method.
 56        // 2. Try to decrypt it if it's encrypted.
 57        // 3. Return the result.
 58
 59    // You can wrap objects in several layers of decorators.
 60    class CompressionDecorator extends DataSourceDecorator is
 61      method writeData(data) is
 62        // 1. Compress passed data.
 63        // 2. Pass compressed data to the wrappee's writeData
 64        // method.
 65
 66      method readData():data is
 67        // 1. Get data from the wrappee's readData method.
 68        // 2. Try to decompress it if it's compressed.
 69        // 3. Return the result.
 70
 71
 72    // Option 1. A simple example of a decorator assembly.
 73    class Application is
 74      method dumbUsageExample() is
 75       source = new FileDataSource("somefile.dat")
 76       source.writeData(salaryRecords)
 77        // The target file has been written with plain data.
 78
 79       source = new CompressionDecorator(source)
 80       source.writeData(salaryRecords)
 81        // The target file has been written with compressed
 82        // data.
 83
 84       source = new EncryptionDecorator(source)
 85        // The source variable now contains this:
 86        // Encryption > Compression > FileDataSource
 87       source.writeData(salaryRecords)
 88        // The file has been written with compressed and
 89        // encrypted data.
 90
 91
 92    // Option 2. Client code that uses an external data source.
 93    // SalaryManager objects neither know nor care about data
 94    // storage specifics. They work with a pre-configured data
 95    // source received from the app configurator.
 96    class SalaryManager is
 97      field source: DataSource
 98
 99      constructor SalaryManager(source: DataSource) { ... }
100
101      method load() is
102        return source.readData()
103
104      method save() is
105        source.writeData(salaryRecords)
106      // ...Other useful methods...
107
108
109    // The app can assemble different stacks of decorators at
110    // runtime, depending on the configuration or environment.
111    class ApplicationConfigurator is
112      method configurationExample() is
113        source = new FileDataSource("salary.dat")
114        if (enabledEncryption)
115          source = new EncryptionDecorator(source)
116        if (enabledCompression)
117          source = new CompressionDecorator(source)
118
119       logger = new SalaryManager(source)
120        salary = logger.load()
121      // ...
```

---

## Java Implementation

A complete, compilable translation of the pseudocode above (`DecoratorDemo.java`). The
encryption is a toy Base64 round-trip and the "compression" a run-length encoder, so both
transformations are genuinely reversible and you can watch the layers unwrap.

```java
import java.util.Base64;

// ─── Component ────────────────────────────────────────────────────────
interface DataSource {
    void writeData(String data);
    String readData();
}

// ─── Concrete component ───────────────────────────────────────────────
// Stands in for a real file; keeps the bytes in memory so the demo runs
// anywhere. It has no idea it will ever be wrapped.
class FileDataSource implements DataSource {
    private final String filename;
    private String storage = "";

    FileDataSource(String filename) {
        this.filename = filename;
    }

    @Override
    public void writeData(String data) {
        storage = data;
        System.out.println("  [disk] wrote to " + filename + ": " + data);
    }

    @Override
    public String readData() {
        return storage;
    }
}

// ─── Base decorator ───────────────────────────────────────────────────
// Implements the SAME interface and holds a reference to a wrappee.
// By default it does nothing but delegate.
class DataSourceDecorator implements DataSource {
    protected final DataSource wrappee;

    DataSourceDecorator(DataSource source) {
        this.wrappee = source;
    }

    @Override
    public void writeData(String data) {
        wrappee.writeData(data);
    }

    @Override
    public String readData() {
        return wrappee.readData();
    }
}

// ─── Concrete decorators ──────────────────────────────────────────────
class EncryptionDecorator extends DataSourceDecorator {

    EncryptionDecorator(DataSource source) {
        super(source);
    }

    @Override
    public void writeData(String data) {
        super.writeData(encode(data));       // transform, THEN delegate
    }

    @Override
    public String readData() {
        return decode(super.readData());     // delegate, THEN transform
    }

    private String encode(String data) {
        return Base64.getEncoder().encodeToString(data.getBytes());
    }

    private String decode(String data) {
        return new String(Base64.getDecoder().decode(data));
    }
}

class CompressionDecorator extends DataSourceDecorator {

    CompressionDecorator(DataSource source) {
        super(source);
    }

    @Override
    public void writeData(String data) {
        super.writeData(compress(data));
    }

    @Override
    public String readData() {
        return decompress(super.readData());
    }

    /** Toy run-length encoding: "aaab" -> "3a1b". */
    private String compress(String data) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < data.length()) {
            char c = data.charAt(i);
            int run = 1;
            while (i + run < data.length() && data.charAt(i + run) == c) run++;
            out.append(run).append(c);
            i += run;
        }
        return out.toString();
    }

    private String decompress(String data) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < data.length()) {
            int j = i;
            while (Character.isDigit(data.charAt(j))) j++;
            int run = Integer.parseInt(data.substring(i, j));
            char c = data.charAt(j);
            out.append(String.valueOf(c).repeat(run));
            i = j + 1;
        }
        return out.toString();
    }
}

// ─── Client that never knows the stack shape ──────────────────────────
class SalaryManager {
    private final DataSource source;

    SalaryManager(DataSource source) {
        this.source = source;
    }

    void save(String records) {
        source.writeData(records);
    }

    String load() {
        return source.readData();
    }
}

// ─── Demo ─────────────────────────────────────────────────────────────
public class DecoratorDemo {
    public static void main(String[] args) {
        String salaryRecords = "aaabbbccd";

        System.out.println("Option 1 — building the stack step by step:");

        DataSource source = new FileDataSource("somefile.dat");
        source.writeData(salaryRecords);

        source = new CompressionDecorator(new FileDataSource("compressed.dat"));
        source.writeData(salaryRecords);

        // Compression > Encryption > FileDataSource
        // (outermost first: compress the plain text, THEN encrypt it)
        source = new CompressionDecorator(
                     new EncryptionDecorator(
                         new FileDataSource("secure.dat")));
        source.writeData(salaryRecords);
        System.out.println("  read back through the stack: " + source.readData());

        System.out.println();
        System.out.println("Option 2 — the app configurator assembles the stack:");

        boolean enabledEncryption = true;
        boolean enabledCompression = true;

        DataSource configured = new FileDataSource("salary.dat");
        if (enabledEncryption)  configured = new EncryptionDecorator(configured);
        if (enabledCompression) configured = new CompressionDecorator(configured);

        SalaryManager manager = new SalaryManager(configured);
        manager.save(salaryRecords);
        System.out.println("  SalaryManager.load(): " + manager.load());
        System.out.println("  round-trip intact?    "
                + salaryRecords.equals(manager.load()));
    }
}
```

**Output**

```
Option 1 — building the stack step by step:
  [disk] wrote to somefile.dat: aaabbbccd
  [disk] wrote to compressed.dat: 3a3b2c1d
  [disk] wrote to secure.dat: M2EzYjJjMWQ=
  read back through the stack: aaabbbccd

Option 2 — the app configurator assembles the stack:
  [disk] wrote to salary.dat: M2EzYjJjMWQ=
  SalaryManager.load(): aaabbbccd
  round-trip intact?    true
```

Both stacks compress first and encrypt second, so both land the same bytes on disk — and
`SalaryManager` never learns which stack it was handed.

### Notes on the Java translation

- **Order of layering matters.** The *outermost* decorator runs first on the way in.
  `new Compression(new Encryption(file))` compresses the plain text and then encrypts the result;
  flipping it to `new Encryption(new Compression(file))` would try to compress ciphertext, which in
  reality achieves nothing (encrypted bytes have no redundancy left) and in this toy demo actually
  corrupts the round trip, because the RLE codec assumes a small alphabet. Compress-then-encrypt is
  the correct real-world order.
- `writeData` transforms *before* delegating; `readData` delegates *first*, then transforms. Every
  decorator pair must be symmetric or the round trip breaks.
- Concrete decorators call `super.writeData(...)`, not `wrappee.writeData(...)` — as the book notes,
  going through the base class keeps concrete decorators extensible.
- Because `DataSourceDecorator` **is a** `DataSource`, a decorator can wrap another decorator to
  unlimited depth. That is the ability inheritance can't give you.

### Decorator vs. subclassing

Suppose you need plain / compressed / encrypted / compressed+encrypted. With inheritance:
`FileDataSource`, `CompressedFileDataSource`, `EncryptedFileDataSource`,
`CompressedEncryptedFileDataSource` — **2ⁿ classes** for n features, and you must pick at compile
time. With Decorator: **n classes**, combined at runtime, in any order, any number of times.

| | Inheritance | Decorator |
|---|---|---|
| When chosen | Compile time | Runtime |
| Combinations | One class per combination | Compose freely |
| Applies to | The whole class | One object |
| Can be undone | No | Yes — just don't wrap |

### Where this appears in the JDK

**`java.io` is the largest decorator family in the standard library** — it is *why* the stream API
looks the way it does:

```java
InputStream in = new GZIPInputStream(
                     new BufferedInputStream(
                         new FileInputStream("data.gz")));
```

- `BufferedInputStream`, `DataInputStream`, `GZIPInputStream`, `CipherInputStream`,
  `PushbackInputStream` all **wrap** an `InputStream` and **are** an `InputStream`
- `BufferedWriter`, `PrintWriter`, `FilterReader` on the character side
- `java.util.Collections.unmodifiableList/synchronizedList/checkedList` — wrappers that add
  behaviour to any `List` without touching its class
- `javax.servlet.http.HttpServletRequestWrapper` / `HttpServletResponseWrapper`
- Spring's transactional and caching proxies decorate your beans

The `java.io` design is also the standard criticism of the pattern: the stack is verbose to build
and a debugger shows you five nested wrappers instead of one object.

---

## Applicability

### ▸ Use the Decorator pattern when you need to be able to assign extra behaviors to objects at runtime without breaking the code that uses these objects.

The Decorator lets you structure your business logic into **layers**, create a decorator for each
layer and **compose objects with various combinations** of this logic at runtime. The client code can
treat all these objects in the same way, since they all follow a common interface.

### ▸ Use the pattern when it's awkward or not possible to extend an object's behavior using inheritance.

Many programming languages have the `final` keyword that can be used to **prevent further extension**
of a class. For a final class, the only way to reuse the existing behavior would be to **wrap the
class with your own wrapper**, using the Decorator pattern.

---

## How to Implement

1. **Make sure your business domain can be represented as a primary component with multiple optional
   layers over it.**

2. **Figure out what methods are common** to both the primary component and the optional layers.
   Create a **component interface** and declare those methods there.

3. **Create a concrete component class** and define the base behavior in it.

4. **Create a base decorator class.** It should have a field for storing a reference to a wrapped
   object. The field should be declared with the **component interface type** to allow linking to
   concrete components as well as decorators. The base decorator **must delegate all work** to the
   wrapped object.

5. **Make sure all classes implement the component interface.**

6. **Create concrete decorators** by extending them from the base decorator. A concrete decorator must
   execute its behavior **before or after** the call to the parent method (which always delegates to
   the wrapped object).

7. **The client code must be responsible for creating decorators** and composing them in the way the
   client needs.

---

## Pros and Cons

**✅ Pros**

- You can extend an object's behavior **without making a new subclass**.
- You can **add or remove responsibilities** from an object **at runtime**.
- You can **combine several behaviors** by wrapping an object into multiple decorators.
- **Single Responsibility Principle.** You can divide a monolithic class that implements many possible
  variants of behavior into several smaller classes.

**❌ Cons**

- It's hard to **remove a specific wrapper** from the wrappers stack.
- It's hard to implement a decorator in such a way that its behavior **doesn't depend on the order**
  in the decorators stack.
- The **initial configuration code** of layers might look pretty ugly.

---

## Relations with Other Patterns

- **Adapter** changes the interface of an existing object, while **Decorator** enhances an object
  **without changing its interface**. In addition, Decorator supports **recursive composition**, which
  isn't possible when you use Adapter.
- **Adapter** provides a **different** interface to the wrapped object, **Proxy** provides it with the
  **same** interface, and **Decorator** provides it with an **enhanced** interface.
- **Chain of Responsibility** and **Decorator** have very similar class structures. Both rely on
  recursive composition to pass the execution through a series of objects. However, there are several
  crucial differences:
  - CoR handlers can execute **arbitrary operations independently** of each other. They can also
    **stop passing the request further** at any point. Various Decorators can extend the object's
    behavior **while keeping it consistent with the base interface**, and decorators **aren't allowed
    to break the flow** of the request.
- **Composite** and **Decorator** have similar structure diagrams since both rely on recursive
  composition to organize an open-ended number of objects.
  - A Decorator is like a Composite but only has **one child component**. Decorator **adds additional
    responsibilities** to the wrapped object, while Composite just **"sums up"** its children's
    results.
  - However, the patterns can also cooperate: you can use Decorator to extend the behavior of a
    specific object in the Composite tree.
- Designs that make heavy use of **Composite and Decorator** can often benefit from using
  **Prototype** — cloning complex structures instead of re-constructing them from scratch.
- **Decorator** lets you change the **skin** of an object, while **Strategy** lets you change the
  **guts**.
- **Decorator** and **Proxy** have similar structures, but very different intents. Both are built on
  composition, where one object delegates some of the work to another. The difference is that a
  **Proxy usually manages the life cycle of its service object on its own**, whereas the composition
  of Decorators is **always controlled by the client**.
