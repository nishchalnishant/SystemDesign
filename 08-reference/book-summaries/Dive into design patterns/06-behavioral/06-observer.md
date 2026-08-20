# Observer

> **Also known as:** *Event-Subscriber*, *Listener*
>
> **Intent:** Observer is a behavioral design pattern that lets you define a subscription mechanism to notify multiple objects about any events that happen to the object they're observing.

---

## Problem

Imagine that you have two types of objects: a `Customer` and a `Store`. The customer is very interested in a particular brand of product (say, it's a new model of the iPhone) which should become available in the store very soon.

The customer could **visit the store every day** and check product availability. But while the product is still en route, most of these trips would be pointless.

```
   Customer ──► Store ──► "not yet"     (wasted trips)
   Store ──► ✉✉✉✉✉ ──► ALL customers    (spam)
```
*Visiting the store vs. sending spam*

On the other hand, the store could **send tons of emails** (which might be considered spam) to all customers each time a new product becomes available. This would save some customers from endless trips to the store. At the same time, it'd upset other customers who aren't interested in new products.

It looks like we've got a **conflict**. Either the customer wastes time checking product availability or the store wastes resources notifying the wrong customers.

---

## Solution

The object that has some interesting state is often called **subject**, but since it's also going to notify other objects about the changes to its state, we'll call it **publisher**. All other objects that want to track changes to the publisher's state are called **subscribers**.

The Observer pattern suggests that you add a **subscription mechanism** to the publisher class so individual objects can subscribe to or unsubscribe from a stream of events coming from that publisher. Fear not! Everything isn't as complicated as it sounds. In reality, this mechanism consists of:

1. an **array field** for storing a list of references to subscriber objects, and
2. several **public methods** which allow adding subscribers to and removing them from that list.

*A subscription mechanism lets individual objects subscribe to event notifications.*

Now, whenever an important event happens to the publisher, it goes over its subscribers and calls the specific **notification method** on their objects.

Real apps might have dozens of different subscriber classes that are interested in tracking events of the same publisher class. You wouldn't want to couple the publisher to all of those classes. Besides, you might not even know about some of them beforehand if your publisher class is supposed to be used by other people.

That's why it's crucial that **all subscribers implement the same interface** and that the publisher communicates with them only via that interface. This interface should declare the notification method along with a set of parameters that the publisher can use to pass some **contextual data** along with the notification.

```
   ┌────────────┐  notify()  ┌──────────────┐
   │ Publisher  │───────────►│ Subscriber A │
   │ subs[]     │───────────►│ Subscriber B │
   │            │───────────►│ Subscriber C │
   └────────────┘            └──────────────┘
```
*Publisher notifies subscribers by calling the specific notification method on their objects.*

If your app has several different types of publishers and you want to make your subscribers compatible with all of them, you can go even further and **make all publishers follow the same interface**. This interface would only need to describe a few subscription methods. The interface would allow subscribers to observe publishers' states without coupling to their concrete classes.

---

## Real-World Analogy

*Magazine and newspaper subscriptions.*

If you **subscribe to a newspaper or magazine**, you no longer need to go to the store to check if the next issue is available. Instead, the publisher sends new issues directly to your mailbox right after publication or even in advance.

The publisher maintains a **list of subscribers** and knows which magazines they're interested in. Subscribers can **leave the list at any time** when they wish to stop the publisher sending new magazine issues to them.

---

## Structure

```
   ┌────────────────────────────┐            ┌──────────────────────────┐
   │        Publisher           │            │     «interface»          │
   ├────────────────────────────┤  notifies  │      Subscriber          │
   │ - subscribers: Subscriber[]│───────────►├──────────────────────────┤
   │ - mainState                │            │ + update(context)        │
   ├────────────────────────────┤            └──────────────────────────┘
   │ + subscribe(s: Subscriber) │                        △
   │ + unsubscribe(s)           │            ┌───────────┴────────────┐
   │ + notifySubscribers()      │            │                        │
   │ + mainBusinessLogic()      │  ┌──────────────────┐  ┌──────────────────┐
   └────────────────────────────┘  │ConcreteSubscriberA│ │ConcreteSubscriberB│
                 ▲                 ├──────────────────┤  ├──────────────────┤
                 │                 │ + update(context)│  │ + update(context)│
                 │                 └──────────────────┘  └──────────────────┘
            ┌─────────┐                     ▲                    ▲
            │ Client  │─────────────────────┴────────────────────┘
            └─────────┘   (creates and registers)
```

1. The **Publisher** issues events of interest to other objects. These events occur when the publisher changes its state or executes some behaviors. Publishers contain a **subscription infrastructure** that lets new subscribers join and current subscribers leave the list.

2. When a new event happens, the publisher goes over the subscription list and calls the **notification method** declared in the subscriber interface on each subscriber object.

3. The **Subscriber** interface declares the notification interface. In most cases, it consists of a single `update` method. The method may have several parameters that let the publisher pass some event details along with the update.

4. **Concrete Subscribers** perform some actions in response to notifications issued by the publisher. All of these classes must implement the same interface so the publisher isn't coupled to concrete classes.

5. Usually, subscribers need some **contextual information** to handle the update correctly. For this reason, publishers often pass some context data as arguments of the notification method. The publisher can **pass itself** as an argument, letting subscriber fetch any required data directly.

6. The **Client** creates publisher and subscriber objects separately and then registers subscribers for publisher updates.

---

## Pseudocode

In this example, the Observer pattern lets the **text editor object notify other service objects** about changes in its state.

*Notifying objects about events that happen to other objects.*

The list of subscribers is compiled **dynamically**: objects can start or stop listening to notifications at runtime, depending on the desired behavior of your app.

In this implementation, the editor class **doesn't maintain the subscription list by itself**. It delegates this job to the special helper object devoted to just that. You could upgrade that object to serve as a **centralized event dispatcher**, letting any object act as a publisher.

Adding new subscribers to the program doesn't require changes to existing publisher classes, as long as they work with all subscribers through the same interface.

```
1    // The base publisher class includes subscription management
2    // code and notification methods.
3    class EventManager is
4      private field listeners: hash map of event types and listeners
5
6      method subscribe(eventType, listener) is
7        listeners.add(eventType, listener)
8
9      method unsubscribe(eventType, listener) is
10      listeners.remove(eventType, listener)
11
12     method notify(eventType, data) is
13       foreach (listener in listeners.of(eventType)) do
14        listener.update(data)
15
16   // The concrete publisher contains real business logic that's
17   // interesting for some subscribers. We could derive this class
18   // from the base publisher, but that isn't always possible in
19   // real life because the concrete publisher might already be a
20   // subclass. In this case, you can patch the subscription logic
21   // in with composition, as we did here.
22   class Editor is
23     public field events: EventManager
24     private field file: File
25
26     constructor Editor() is
27       events = new EventManager()
28
29     // Methods of business logic can notify subscribers about
30     // changes.
31     method openFile(path) is
32      this.file = new File(path)
33      events.notify("open", file.name)
34
35     method saveFile() is
36      file.write()
37      events.notify("save", file.name)
38
39     // ...
40
41
42   // Here's the subscriber interface. If your programming language
43   // supports functional types, you can replace the whole
44   // subscriber hierarchy with a set of functions.
45   interface EventListener is
46     method update(filename)
47
48   // Concrete subscribers react to updates issued by the publisher
49   // they are attached to.
50   class LoggingListener implements EventListener is
51     private field log: File
52     private field message: string
53
54     constructor LoggingListener(log_filename, message) is
55       this.log = new File(log_filename)
56       this.message = message
57
58     method update(filename) is
59       log.write(replace('%s',filename,message))
60
61   class EmailAlertsListener implements EventListener is
62     private field email: string
63     private field message: string
64
65     constructor EmailAlertsListener(email, message) is
66       this.email = email
67       this.message = message
68
69     method update(filename) is
70      system.email(email, replace('%s',filename,message))
71
72
73   // An application can configure publishers and subscribers at
74   // runtime.
75   class Application is
76     method config() is
77       editor = new Editor()
78
79      logger = new LoggingListener(
80         "/path/to/log.txt",
81         "Someone has opened the file: %s")
82      editor.events.subscribe("open", logger)
83
84      emailAlerts = new EmailAlertsListener(
85        "admin@example.com",
86        "Someone has changed the file: %s")
87      editor.events.subscribe("save", emailAlerts)
```

---

## Java Implementation

A complete, compilable translation of the pseudocode above (`ObserverDemo.java`). Note that the
publisher **delegates** subscription management to an `EventManager` rather than inheriting it —
exactly the composition-over-inheritance point the book makes.

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// ─── Subscriber interface ─────────────────────────────────────────────
interface EventListener {
    void update(String filename);
}

// ─── The reusable subscription machinery ──────────────────────────────
class EventManager {
    private final Map<String, List<EventListener>> listeners = new HashMap<>();

    EventManager(String... operations) {
        for (String operation : operations) {
            listeners.put(operation, new ArrayList<>());
        }
    }

    void subscribe(String eventType, EventListener listener) {
        listeners.get(eventType).add(listener);
    }

    void unsubscribe(String eventType, EventListener listener) {
        listeners.get(eventType).remove(listener);
    }

    void notify(String eventType, String data) {
        // Iterate a COPY: a listener may unsubscribe itself during the
        // callback, which would otherwise throw ConcurrentModificationException.
        for (EventListener listener : List.copyOf(listeners.get(eventType))) {
            listener.update(data);
        }
    }
}

// ─── Concrete publisher ───────────────────────────────────────────────
// Editor is NOT a subclass of EventManager — it holds one. That matters:
// a real publisher usually already has a superclass.
class Editor {
    public final EventManager events = new EventManager("open", "save");
    private String file;

    void openFile(String path) {
        this.file = path;
        events.notify("open", file);
    }

    void saveFile() {
        if (file == null) {
            throw new IllegalStateException("no file opened");
        }
        events.notify("save", file);
    }
}

// ─── Concrete subscribers ─────────────────────────────────────────────
class LoggingListener implements EventListener {
    private final String logFile;
    private final String message;

    LoggingListener(String logFile, String message) {
        this.logFile = logFile;
        this.message = message;
    }

    @Override
    public void update(String filename) {
        System.out.println("  [" + logFile + "] " + message.replace("%s", filename));
    }
}

class EmailAlertsListener implements EventListener {
    private final String email;
    private final String message;

    EmailAlertsListener(String email, String message) {
        this.email = email;
        this.message = message;
    }

    @Override
    public void update(String filename) {
        System.out.println("  [mail -> " + email + "] " + message.replace("%s", filename));
    }
}

public class ObserverDemo {
    public static void main(String[] args) {
        Editor editor = new Editor();

        EventListener logger = new LoggingListener(
                "/path/to/log.txt", "Someone has opened the file: %s");
        editor.events.subscribe("open", logger);

        EventListener emailAlerts = new EmailAlertsListener(
                "admin@example.com", "Someone has changed the file: %s");
        editor.events.subscribe("save", emailAlerts);

        System.out.println("open:");
        editor.openFile("test.txt");
        System.out.println("save:");
        editor.saveFile();

        // Subscribers come and go at RUNTIME — the publisher is untouched.
        System.out.println("\nAdding a second save-listener (a lambda):");
        EventListener backup = filename ->
                System.out.println("  [backup] copied " + filename + " to s3://bucket/");
        editor.events.subscribe("save", backup);
        editor.saveFile();

        System.out.println("\nUnsubscribing the email alerts:");
        editor.events.unsubscribe("save", emailAlerts);
        editor.saveFile();
    }
}
```

**Output**

```
open:
  [/path/to/log.txt] Someone has opened the file: test.txt
save:
  [mail -> admin@example.com] Someone has changed the file: test.txt

Adding a second save-listener (a lambda):
  [mail -> admin@example.com] Someone has changed the file: test.txt
  [backup] copied test.txt to s3://bucket/

Unsubscribing the email alerts:
  [backup] copied test.txt to s3://bucket/
```

`Editor` was never modified to add the backup listener. That is the Open/Closed Principle in its
most literal form.

### Notes on the Java translation

- **`EventManager` is composed, not inherited.** The book flags this explicitly: a real publisher is
  often already a subclass of something, so subscription management goes in a helper object. It also
  makes the machinery reusable across unrelated publishers.
- **Keyed by event type.** A single publisher can broadcast several distinct events; a subscriber
  picks the ones it cares about. Without this you'd need one publisher per event.
- **`List.copyOf` in `notify()`.** A listener that unsubscribes itself inside `update()` would
  otherwise throw `ConcurrentModificationException`. This is a real production bug, not a theoretical
  one.
- **`EventListener` is a functional interface**, so any subscriber can be a lambda — the book notes
  that in a language with functional types the whole subscriber hierarchy collapses into a set of
  functions.

### Push vs. pull

The pseudocode **pushes** the data (`update(filename)`). The alternative is to **pull**:

```java
interface EventListener {
    void update(Editor source);      // subscriber asks the publisher for what it needs
}
```

| | Push | Pull |
|---|---|---|
| Publisher sends | exactly the changed data | just "something changed" + itself |
| Coupling | subscribers depend on the payload's shape | subscribers depend on the publisher's API |
| Efficiency | good — no extra calls | subscriber may fetch data it didn't need |

Push is usually the better default; pull is handy when different subscribers need very different
slices of a large state.

### The pitfalls

- **Notification order is unspecified.** If one subscriber depends on another having run first, the
  design is already broken. Don't rely on registration order.
- **Lapsed listeners leak memory.** A publisher that outlives its subscribers holds strong
  references to them forever — the classic Swing/Android leak. Fixes: always `unsubscribe()` in a
  teardown/`close()`, or hold `WeakReference`s.
- **A throwing subscriber can kill the broadcast.** Wrap each `update()` in a try/catch if one bad
  listener mustn't starve the rest.
- **Cascading updates** — subscriber A's `update()` mutates the publisher, triggering another
  notification — can loop forever. Guard with a re-entrancy flag.

### Observer vs. Mediator vs. Chain of Responsibility

| | Shape | Publisher knows |
|---|---|---|
| **Observer** | one → many broadcast | a list it never inspects |
| **Mediator** | many ↔ many through a hub | the mediator knows every component |
| **Chain of Responsibility** | one → one → one, sequential | only the next handler |

Observer broadcasts to *everyone* subscribed; Chain of Responsibility passes along until *someone*
handles it. See [`04-mediator.md`](04-mediator.md) and
[`01-chain-of-responsibility.md`](01-chain-of-responsibility.md).

### Where this appears in the JDK and frameworks

- `java.util.EventListener` and all its descendants — `ActionListener`, `MouseListener`, …
- `java.beans.PropertyChangeListener` / `PropertyChangeSupport` — literally an `EventManager`
- `java.util.Observer` / `Observable` — **deprecated since Java 9**: not serialisable, no event
  types, and unordered notifications. Don't use it.
- `java.util.concurrent.Flow` (Java 9+) — Reactive Streams: Observer plus *backpressure*
- Spring's `ApplicationListener` / `@EventListener`; RxJava and Project Reactor; the DOM's
  `addEventListener`; Kafka consumer groups

---

## Applicability

### ▸ Use the Observer pattern when changes to the state of one object may require changing other objects, and the actual set of objects is unknown beforehand or changes dynamically.

You can often experience this problem when working with classes of the **graphical user interface**. For example, you created custom button classes, and you want to let the clients hook some custom code to your buttons so that it fires whenever a user presses a button.

The Observer pattern lets any object that implements the subscriber interface subscribe for event notifications in publisher objects. You can add the subscription mechanism to your buttons, letting the clients hook up their custom code via custom subscriber classes.

### ▸ Use the pattern when some objects in your app must observe others, but only for a limited time or in specific cases.

The subscription list is **dynamic**, so subscribers can join or leave the list whenever they need to.

---

## How to Implement

1. Look over your business logic and try to break it down into two parts: the **core functionality**, independent from other code, will act as the publisher; the rest will turn into a set of **subscriber classes**.

2. **Declare the subscriber interface.** At a bare minimum, it should declare a single `update` method.

3. **Declare the publisher interface** and describe a pair of methods for adding a subscriber object to and removing it from the list. Remember that publishers must work with subscribers **only via the subscriber interface**.

4. **Decide where to put the actual subscription list** and the implementation of subscription methods. Usually, this code looks the same for all types of publishers, so the obvious place to put it is in an **abstract class** derived directly from the publisher interface. Concrete publishers extend that class, inheriting the subscription behavior.

   However, if you're applying the pattern to an existing class hierarchy, consider an approach based on **composition**: put the subscription logic into a separate object, and make all real publishers use it.

5. **Create concrete publisher classes.** Each time something important happens inside a publisher, it must notify all its subscribers.

6. **Implement the update notification methods** in concrete subscriber classes. Most subscribers would need some context data about the event. It can be passed as an argument of the notification method.

   But there's another option. Upon receiving a notification, the subscriber can fetch any data **directly from the notification**. In this case, the publisher must pass itself via the update method. The less flexible option is to link a publisher to the subscriber permanently via the constructor.

7. **The client must create all necessary subscribers** and register them with proper publishers.

---

## Pros and Cons

**✅ Pros**

- *Open/Closed Principle.* You can introduce new subscriber classes without having to change the publisher's code (and vice versa if there's a publisher interface).
- You can establish relations between objects at runtime.

**❌ Cons**

- Subscribers are notified in **random order**.

---

## Relations with Other Patterns

- **Chain of Responsibility**, **Command**, **Mediator** and **Observer** address various ways of connecting senders and receivers of requests:
  - *Chain of Responsibility* passes a request sequentially along a dynamic chain of potential receivers until one of them handles it.
  - *Command* establishes unidirectional connections between senders and receivers.
  - *Mediator* eliminates direct connections between senders and receivers, forcing them to communicate indirectly via a mediator object.
  - *Observer* lets receivers dynamically subscribe to and unsubscribe from receiving requests.

- The difference between **Mediator** and **Observer** is often **elusive**. In most cases, you can implement either of these patterns; but sometimes you can apply both simultaneously. Let's see how we can do that.

  The primary goal of *Mediator* is to **eliminate mutual dependencies** among a set of system components. Instead, these components become dependent on a single mediator object. The goal of *Observer* is to establish **dynamic one-way connections** between objects, where some objects act as subordinates of others.

  There's a popular implementation of the Mediator pattern that **relies on Observer**. The mediator object plays the role of publisher, and the components act as subscribers which subscribe to and unsubscribe from the mediator's events. When Mediator is implemented this way, it may look very similar to Observer.

  When you're confused, remember that you can implement the Mediator pattern in other ways. For example, you can **permanently link** all the components to the same mediator object. This implementation won't resemble Observer but will still be an instance of the Mediator pattern.

  Now imagine a program where **all components have become publishers**, allowing dynamic connections between each other. There won't be a centralized mediator object, only a **distributed set of observers**.
