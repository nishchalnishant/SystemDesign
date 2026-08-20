# Command

> **Also known as:** *Action*, *Transaction*
>
> **Intent:** Command is a behavioral design pattern that turns a request into a stand-alone object that contains all information about the request. This transformation lets you pass requests as a method arguments, delay or queue a request's execution, and support undoable operations.

---

## Problem

Imagine that you're working on a new **text-editor app**. Your current task is to create a toolbar with a bunch of buttons for various operations of the editor. You created a very neat `Button` class that can be used for buttons on the toolbar, as well as for generic buttons in various dialogs.

*All buttons of the app are derived from the same class.*

While all of these buttons look similar, they're all supposed to do different things. Where would you put the code for the various click handlers of these buttons? The simplest solution is to create **tons of subclasses** for each place where the button is used. These subclasses would contain the code that would have to be executed on a button click.

```
                    Button
                      ▲
    ┌──────────┬──────┴──────┬──────────────┐
 SaveButton  CopyButton  PasteButton  ... (and dozens more)
```
*Lots of button subclasses. What can go wrong?*

Before long, you realize that this approach is deeply flawed. First, you have an **enormous number of subclasses**, and that would be okay if you weren't risking breaking the code in these subclasses each time you modify the base `Button` class. Put simply, your **GUI code has become awkwardly dependent on the volatile code of the business logic**.

*Several classes implement the same functionality.*

And here's the ugliest part. Some operations, such as copying/pasting text, would need to be **invoked from multiple places**. For example, a user could click a small "Copy" button on the toolbar, or copy something via the context menu, or just hit `Ctrl+C` on the keyboard.

Initially, when our app only had the toolbar, it was okay to place the implementation of various operations into the button subclasses. In other words, having the code for copying text inside the `CopyButton` subclass was fine. But then, when you implement context menus, shortcuts, and other stuff, you have to either **duplicate the operation's code** in many classes or **make menus dependent on buttons**, which is an even worse option.

---

## Solution

Good software design is often based on the principle of **separation of concerns**, which usually results in breaking an app into **layers**. The most common example: a layer for the graphical user interface and another layer for the business logic. The GUI layer is responsible for rendering a beautiful picture on the screen, capturing any input and showing results of what the user and the app are doing. However, when it comes to doing something important, like calculating the trajectory of the moon or composing an annual report, the GUI layer delegates the work to the underlying layer of business logic.

In the code it might look like this: a GUI object calls a method of a business logic object, passing it some arguments. This process is usually described as one object **sending another a request**.

*The GUI objects may access the business logic objects directly.*

The Command pattern suggests that **GUI objects shouldn't send these requests directly**. Instead, you should extract all of the request details — such as the object being called, the name of the method and the list of arguments — into a **separate command class** with a single method that triggers this request.

Command objects serve as **links** between various GUI and business logic objects. From now on, the GUI object doesn't need to know what business logic object will receive the request and how it'll be processed. The GUI object just triggers the command, which handles all the details.

*Accessing the business logic layer via a command.*

The next step is to make your commands **implement the same interface**. Usually it has just a single execution method that takes no parameters. This interface lets you use various commands with the same request sender, without coupling it to concrete classes of commands. As a bonus, now you can **switch command objects linked to the sender**, effectively changing the sender's behavior at runtime.

You might have noticed one missing piece of the puzzle, which is the **request parameters**. A GUI object might have supplied the business-layer object with some parameters. Since the command execution method doesn't have any parameters, how would we pass the request details to the receiver? It turns out the command should be either **pre-configured with this data**, or **capable of getting it on its own**.

*The GUI objects delegate the work to commands.*

Let's get back to our text editor. After we apply the Command pattern, we no longer need all those button subclasses to implement various click behaviors. It's enough to put a **single field into the base `Button` class** that stores a reference to a command object and make the button execute that command on a click.

You'll implement a bunch of command classes for every possible operation and link them with particular buttons, depending on the buttons' intended behavior.

Other GUI elements, such as **menus, shortcuts or entire dialogs**, can be implemented in the same way. They'll be linked to a command which gets executed when a user interacts with the GUI element. The elements related to the same operations will be linked to the **same commands**, preventing any code duplication.

As a result, commands become a **convenient middle layer that reduces coupling between the GUI and business logic layers**. And that's only a fraction of the benefits that the Command pattern can offer!

---

## Real-World Analogy

*Making an order in a restaurant.*

After a long walk through the city, you get to a nice restaurant and sit at the table by the window. A friendly **waiter** approaches you and quickly takes your order, writing it down on a **piece of paper**. The waiter goes to the kitchen and sticks the order on the wall. After a while, the order gets to the **chef**, who reads it and cooks the meal accordingly. The cook places the meal on a tray along with the order. The waiter discovers the tray, checks the order to make sure everything is as you wanted it, and brings everything to your table.

The **paper order serves as a command**. It remains in a **queue** until the chef is ready to serve it. The order contains all the relevant information required to cook the meal. It allows the chef to start cooking right away instead of running around clarifying the order details from you directly.

---

## Structure

```
 ┌────────┐  creates   ┌───────────────────┐
 │ Client │───────────►│ ConcreteCommand   │
 └───┬────┘            ├───────────────────┤
     │ configures      │ - receiver        │──────┐
     ▼                 │ - params          │      │
 ┌──────────────┐      ├───────────────────┤      ▼
 │   Sender     │      │ + execute() is    │  ┌──────────────┐
 │  (Invoker)   │      │   receiver.op(par)│  │  Receiver    │
 ├──────────────┤      └─────────▲─────────┘  ├──────────────┤
 │ - command    │────┐           │            │ + operation()│
 ├──────────────┤    │  ┌────────┴─────────┐  └──────────────┘
 │ + setCommand │    └─►│   «interface»    │
 │ + doSomething│       │     Command      │
 │   command    │       ├──────────────────┤
 │     .execute │       │ + execute()      │
 └──────────────┘       └──────────────────┘
```

1. **The Sender** class (aka **invoker**) is responsible for initiating requests. This class must have a field for storing a reference to a command object. The sender triggers that command instead of sending the request directly to the receiver. Note that the sender **isn't responsible for creating** the command object. Usually, it gets a pre-created command from the client via the constructor.

2. **The Command interface** usually declares just a single method for executing the command.

3. **Concrete Commands** implement various kinds of requests. A concrete command **isn't supposed to perform the work on its own**, but rather to pass the call to one of the business logic objects. However, for the sake of simplifying the code, these classes can be merged.

   Parameters required to execute a method on a receiving object can be declared as **fields** in the concrete command. You can make command objects **immutable** by only allowing the initialization of these fields via the constructor.

4. **The Receiver** class contains some business logic. **Almost any object may act as a receiver.** Most commands only handle the details of *how* a request is passed to the receiver, while the receiver itself does the actual work.

5. **The Client** creates and configures concrete command objects. The client must pass all of the request parameters, including a receiver instance, into the command's constructor. After that, the resulting command may be associated with **one or multiple senders**.

---

## Pseudocode

In this example, the Command pattern helps to **track the history of executed operations** and makes it possible to **revert an operation** if needed.

*Undoable operations in a text editor.*

Commands which result in changing the state of the editor (e.g., cutting and pasting) make a **backup copy of the editor's state** before executing an operation associated with the command. After a command is executed, it's placed into the **command history** (a stack of command objects) along with the backup copy of the editor's state at that point.

Later, if the user needs to revert an operation, the app can take the most recent command from the history, read the associated backup of the editor's state, and restore it.

The client code (GUI elements, command history, etc.) isn't coupled to concrete command classes because it works with commands via the command interface. This approach lets you introduce new commands into the app without breaking any existing code.

```
  1    // The base command class defines the common interface for all
  2    // concrete commands.
  3    abstract class Command is
  4      protected field app: Application
  5      protected field editor: Editor
  6      protected field backup: text
  7
  8      constructor Command(app: Application, editor: Editor) is
  9        this.app = app
 10       this.editor = editor
 11
 12     // Make a backup of the editor's state.
 13     method saveBackup() is
 14      backup = editor.text
 15
 16     // Restore the editor's state.
 17     method undo() is
 18      editor.text = backup
 19
 20     // The execution method is declared abstract to force all
 21     // concrete commands to provide their own implementations.
 22     // The method must return true or false depending on whether
 23     // the command changes the editor's state.
 24     abstract method execute()
 25
 26
 27   // The concrete commands go here.
 28   class CopyCommand extends Command is
 29     // The copy command isn't saved to the history since it
 30     // doesn't change the editor's state.
 31     method execute() is
 32      app.clipboard = editor.getSelection()
 33       return false
 34
 35   class CutCommand extends Command is
 36     // The cut command does change the editor's state, therefore
 37     // it must be saved to the history. And it'll be saved as
 38     // long as the method returns true.
 39     method execute() is
 40      saveBackup()
 41      app.clipboard = editor.getSelection()
 42      editor.deleteSelection()
 43      return true
 44
 45   class PasteCommand extends Command is
 46     method execute() is
 47       saveBackup()
 48      editor.replaceSelection(app.clipboard)
 49      return true
 50
 51   // The undo operation is also a command.
 52   class UndoCommand extends Command is
 53     method execute() is
 54      app.undo()
 55      return false
 56
 57
 58   // The global command history is just a stack.
 59   class CommandHistory is
 60     private field history: array of Command
 61
 62     // Last in...
 63     method push(c: Command) is
 64       // Push the command to the end of the history array.
 65
 66     // ...first out
 67     method pop():Command is
 68       // Get the most recent command from the history.
 69
 70
 71   // The editor class has actual text editing operations. It plays
 72   // the role of a receiver: all commands end up delegating
 73   // execution to the editor's methods.
 74   class Editor is
 75     field text: string
 76
 77     method getSelection() is
 78       // Return selected text.
 79
 80     method deleteSelection() is
 81       // Delete selected text.
 82
 83      method replaceSelection(text) is
 84        // Insert the clipboard's contents at the current
 85        // position.
 86
 87
 88    // The application class sets up object relations. It acts as a
 89    // sender: when something needs to be done, it creates a command
 90    // object and executes it.
 91    class Application is
 92      field clipboard: string
 93      field editors: array of Editors
 94      field activeEditor: Editor
 95      field history: CommandHistory
 96
 97      // The code which assigns commands to UI objects may look
 98      // like this.
 99     method createUI() is
100       // ...
101      copy = function() { executeCommand(
102        new CopyCommand(this, activeEditor)) }
103      copyButton.setCommand(copy)
104      shortcuts.onKeyPress("Ctrl+C", copy)
105
106      cut = function() { executeCommand(
107        new CutCommand(this, activeEditor)) }
108      cutButton.setCommand(cut)
109      shortcuts.onKeyPress("Ctrl+X", cut)
110
111      paste = function() { executeCommand(
112        new PasteCommand(this, activeEditor)) }
113      pasteButton.setCommand(paste)
114      shortcuts.onKeyPress("Ctrl+V", paste)
115
116      undo = function() { executeCommand(
117        new UndoCommand(this, activeEditor)) }
118      undoButton.setCommand(undo)
119      shortcuts.onKeyPress("Ctrl+Z", undo)
120
121     // Execute a command and check whether it has to be added to
122     // the history.
123     method executeCommand(command) is
124       if (command.execute)
125        history.push(command)
126
127     // Take the most recent command from the history and run its
128     // undo method. Note that we don't know the class of that
129     // command. But we don't have to, since the command knows
130     // how to undo its own action.
131    method undo() is
132      command = history.pop()
133      if (command != null)
134         command.undo()
```

---

## Java Implementation

A complete, compilable translation of the pseudocode above (`CommandDemo.java`), with a
working undo stack.

```java
import java.util.ArrayDeque;
import java.util.Deque;

// ─── Receiver: where the real work happens ────────────────────────────
class Editor {
    String text = "";
    int selectionStart = 0;
    int selectionEnd = 0;

    String getSelection() {
        return text.substring(selectionStart, Math.min(selectionEnd, text.length()));
    }

    void deleteSelection() {
        text = text.substring(0, selectionStart)
             + text.substring(Math.min(selectionEnd, text.length()));
        selectionEnd = selectionStart;
    }

    void replaceSelection(String replacement) {
        String tail = text.substring(Math.min(selectionEnd, text.length()));
        text = text.substring(0, selectionStart) + replacement + tail;
        selectionStart = selectionStart + replacement.length();
        selectionEnd = selectionStart;
    }

    void select(int start, int end) {
        selectionStart = start;
        selectionEnd = end;
    }
}

// ─── Command: the base class ──────────────────────────────────────────
abstract class Command {
    protected final Application app;
    protected final Editor editor;
    private String backup;

    Command(Application app, Editor editor) {
        this.app = app;
        this.editor = editor;
    }

    void saveBackup() {
        backup = editor.text;
    }

    void undo() {
        editor.text = backup;
    }

    /** @return true if the command changed state and belongs in history. */
    abstract boolean execute();
}

// ─── Concrete commands ────────────────────────────────────────────────
class CopyCommand extends Command {
    CopyCommand(Application app, Editor editor) {
        super(app, editor);
    }

    @Override
    boolean execute() {
        app.clipboard = editor.getSelection();
        return false;                       // read-only: not undoable
    }
}

class CutCommand extends Command {
    CutCommand(Application app, Editor editor) {
        super(app, editor);
    }

    @Override
    boolean execute() {
        if (editor.getSelection().isEmpty()) {
            return false;
        }
        saveBackup();
        app.clipboard = editor.getSelection();
        editor.deleteSelection();
        return true;                        // mutating: push to history
    }
}

class PasteCommand extends Command {
    PasteCommand(Application app, Editor editor) {
        super(app, editor);
    }

    @Override
    boolean execute() {
        if (app.clipboard == null || app.clipboard.isEmpty()) {
            return false;
        }
        saveBackup();
        editor.replaceSelection(app.clipboard);
        return true;
    }
}

/** Undo is itself a command — it just isn't recorded. */
class UndoCommand extends Command {
    UndoCommand(Application app, Editor editor) {
        super(app, editor);
    }

    @Override
    boolean execute() {
        app.undo();
        return false;
    }
}

// ─── The history is just a stack ──────────────────────────────────────
class CommandHistory {
    private final Deque<Command> history = new ArrayDeque<>();

    void push(Command c) {
        history.push(c);
    }

    Command pop() {
        return history.isEmpty() ? null : history.pop();
    }

    int size() {
        return history.size();
    }
}

// ─── Invoker / sender ─────────────────────────────────────────────────
class Application {
    String clipboard = "";
    final Editor activeEditor = new Editor();
    final CommandHistory history = new CommandHistory();

    /** Execute, then record only if the command reports a state change. */
    void executeCommand(Command command) {
        if (command.execute()) {
            history.push(command);
        }
    }

    /**
     * Pop the most recent command and let it undo itself. Note we do
     * NOT know its concrete class — and don't need to.
     */
    void undo() {
        Command command = history.pop();
        if (command != null) {
            command.undo();
        }
    }
}

// ─── Demo ─────────────────────────────────────────────────────────────
public class CommandDemo {
    public static void main(String[] args) {
        Application app = new Application();
        Editor editor = app.activeEditor;
        editor.text = "Hello wonderful world";

        show(app, "initial");

        // Ctrl+X over "wonderful "
        editor.select(6, 16);
        app.executeCommand(new CutCommand(app, editor));
        show(app, "after Cut of 'wonderful '");

        // Ctrl+V at the end
        editor.select(editor.text.length(), editor.text.length());
        app.executeCommand(new PasteCommand(app, editor));
        show(app, "after Paste at end");

        // Ctrl+C — changes nothing, so it must NOT enter the history
        editor.select(0, 5);
        app.executeCommand(new CopyCommand(app, editor));
        show(app, "after Copy of 'Hello' (history unchanged)");

        // Ctrl+Z, Ctrl+Z
        app.executeCommand(new UndoCommand(app, editor));
        show(app, "after Undo");
        app.executeCommand(new UndoCommand(app, editor));
        show(app, "after Undo");
    }

    static void show(Application app, String label) {
        System.out.printf("%-42s text=\"%s\" | clipboard=\"%s\" | history=%d%n",
                label, app.activeEditor.text, app.clipboard, app.history.size());
    }
}
```

**Output**

```
initial                                    text="Hello wonderful world" | clipboard="" | history=0
after Cut of 'wonderful '                  text="Hello world" | clipboard="wonderful " | history=1
after Paste at end                         text="Hello worldwonderful " | clipboard="wonderful " | history=2
after Copy of 'Hello' (history unchanged)  text="Hello worldwonderful " | clipboard="Hello" | history=2
after Undo                                 text="Hello world" | clipboard="Hello" | history=1
after Undo                                 text="Hello wonderful world" | clipboard="Hello" | history=0
```

The history goes 0 → 1 → 2 → **2** → 1 → 0. Copy executed but was never recorded, because
`execute()` returned `false`. Two undos walk the text back to exactly its original value.

### Notes on the Java translation

- **The boolean return from `execute()` is the whole history policy.** Mutating commands say
  `true`; read-only ones say `false`. The invoker needs no knowledge of what any command does.
- `undo()` lives on the base class because every command undoes the same way here — restore the
  text snapshot. This is the **Memento** pattern quietly embedded inside Command; see
  [05-memento.md](05-memento.md).
- `app.undo()` pops a `Command` and calls `undo()` on it with **no type check and no cast**. Adding
  a new command type requires zero changes to `Application`.
- Snapshotting the entire document is fine for a demo but doesn't scale. Real editors store an
  *inverse operation* per command (`InsertCommand.undo()` = delete that range) instead of a full
  copy.

### The lambda form: what you'll actually write in modern Java

A command with one method is a functional interface, so most Java code skips the class hierarchy:

```java
@FunctionalInterface
interface Action {
    void run();
}

Map<String, Action> shortcuts = Map.of(
    "Ctrl+C", () -> app.executeCommand(new CopyCommand(app, editor)),
    "Ctrl+X", () -> app.executeCommand(new CutCommand(app, editor)),
    "Ctrl+Z", app::undo
);

shortcuts.get("Ctrl+X").run();
```

`Runnable`, `Callable<V>`, `Supplier<T>`, and `Consumer<T>` are all pre-built command interfaces.
Use a **class** when the command needs state (a backup for undo, parameters, a name for logging);
use a **lambda** when it's a one-shot call.

### What else the pattern buys you

Because an operation is now an object, you can do things to it that you cannot do to a method call:

- **Queue it** — put commands on a work queue and run them on a thread pool
- **Serialize it** — write the command to disk or send it over the network (this is how
  event-sourcing and CQRS work; a command log *is* the database)
- **Retry it** — keep the object around and call `execute()` again after a failure
- **Schedule it** — run it later, or at a fixed rate
- **Macro-record it** — a `MacroCommand` holding a `List<Command>` replays a whole sequence:

```java
class MacroCommand extends Command {
    private final List<Command> commands;

    MacroCommand(Application app, Editor editor, List<Command> commands) {
        super(app, editor);
        this.commands = commands;
    }

    @Override
    boolean execute() {
        boolean changed = false;
        for (Command c : commands) changed |= c.execute();
        return changed;
    }

    @Override
    void undo() {
        // Undo in REVERSE order — this is essential.
        for (int i = commands.size() - 1; i >= 0; i--) commands.get(i).undo();
    }
}
```

Note the reverse-order undo: it is the single most commonly missed detail in Command implementations.

### Where this appears in the JDK and ecosystem

- `java.lang.Runnable` — the archetypal command interface
- `java.util.concurrent.Callable<V>` and everything `ExecutorService.submit()` accepts
- `javax.swing.Action` / `AbstractAction` — Swing's explicit Command implementation, shared between
  a menu item, a toolbar button, and a keystroke
- `java.util.concurrent.ThreadPoolExecutor` — the queue holds commands
- `Thread(Runnable)` itself
- Event sourcing, CQRS, database transaction logs, and every "undo" feature ever shipped

---

## Applicability

### ▸ Use the Command pattern when you want to parametrize objects with operations.

The Command pattern can turn a specific method call into a stand-alone object. This change opens up a lot of interesting uses: you can pass commands as method arguments, store them inside other objects, switch linked commands at runtime, etc.

Here's an example: you're developing a GUI component such as a context menu, and you want your users to be able to configure menu items that trigger operations when an end user clicks an item.

### ▸ Use the Command pattern when you want to queue operations, schedule their execution, or execute them remotely.

As with any other object, a command can be **serialized**, which means converting it to a string that can be easily written to a file or a database. Later, the string can be restored as the initial command object. Thus, you can delay and schedule command execution. But there's even more! In the same way, you can **queue, log or send commands over the network**.

### ▸ Use the Command pattern when you want to implement reversible operations.

Although there are many ways to implement undo/redo, the Command pattern is perhaps the most popular of all.

To be able to revert operations, you need to implement the **history of performed operations**. The command history is a stack that contains all executed command objects along with related backups of the application's state.

This method has **two drawbacks**. First, it isn't that easy to save an application's state because some of it can be private. This problem can be mitigated with the **Memento** pattern.

Second, the **state backups may consume quite a lot of RAM**. Therefore, sometimes you can resort to an alternative implementation: instead of restoring the past state, the command performs the **inverse operation**. The reverse operation also has a price: it may turn out to be hard or even impossible to implement.

---

## How to Implement

1. **Declare the command interface** with a single execution method.

2. Start **extracting requests into concrete command classes** that implement the command interface. Each class must have a set of fields for storing the request arguments along with a reference to the actual receiver object. All these values must be initialized via the command's constructor.

3. **Identify classes that will act as senders.** Add the fields for storing commands into these classes. Senders should communicate with their commands only via the command interface. Senders usually don't create command objects on their own, but rather get them from the client code.

4. **Change the senders** so they execute the command instead of sending a request to the receiver directly.

5. The client should **initialize objects in the following order**:
   - Create **receivers**.
   - Create **commands**, and associate them with receivers if needed.
   - Create **senders**, and associate them with specific commands.

---

## Pros and Cons

**✅ Pros**
- **Single Responsibility Principle.** You can decouple classes that invoke operations from classes that perform these operations.
- **Open/Closed Principle.** You can introduce new commands into the app without breaking existing client code.
- You can implement **undo/redo**.
- You can implement **deferred execution** of operations.
- You can **assemble a set of simple commands into a complex one**.

**❌ Cons**
- The code may become more complicated since you're introducing a whole new layer between senders and receivers.

---

## Relations with Other Patterns

- **Chain of Responsibility, Command, Mediator and Observer** address various ways of connecting senders and receivers of requests:
  - **Chain of Responsibility** passes a request sequentially along a dynamic chain of potential receivers until one of them handles it.
  - **Command** establishes unidirectional connections between senders and receivers.
  - **Mediator** eliminates direct connections between senders and receivers, forcing them to communicate indirectly via a mediator object.
  - **Observer** lets receivers dynamically subscribe to and unsubscribe from receiving requests.

- Handlers in **Chain of Responsibility** can be implemented as **Commands**. In this case, you can execute a lot of different operations over the same context object, represented by a request.

  However, there's another approach, where the **request itself is a Command object**. In this case, you can execute the same operation in a series of different contexts linked into a chain.

- You can use **Command** and **Memento** together when implementing "undo". In this case, commands are responsible for performing various operations over a target object, while mementos save the state of that object just before a command gets executed.

- **Command** and **Strategy** may look similar because you can use both to parameterize an object with some action. However, they have very different intents.
  - You can use **Command** to convert any operation into an object. The operation's parameters become fields of that object. The conversion lets you defer execution of the operation, queue it, store the history of commands, send commands to remote services, etc.
  - On the other hand, **Strategy** usually describes different ways of doing **the same thing**, letting you swap these algorithms within a single context class.

- **Prototype** can help when you need to save copies of Commands into history.

- You can treat **Visitor** as a **powerful version of the Command pattern**. Its objects can execute operations over various objects of different classes.
