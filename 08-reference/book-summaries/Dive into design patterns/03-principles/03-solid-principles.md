# Chapter 8 — SOLID Principles

Five principles introduced by **Robert Martin** in *Agile Software Development, Principles,
Patterns, and Practices*.

> SOLID is a mnemonic for five design principles intended to make software designs more
> **understandable, flexible and maintainable**.

> ⚠️ **Caveat from the author:** As with everything in life, using these principles mindlessly
> can cause more harm than good. The cost of applying these principles into a program's
> architecture might be making it more complicated than it should be. "I doubt that there's a
> successful software product in which all of these principles are applied at the same time.
> Striving for these principles is good, but always try to be pragmatic and don't take
> everything written here as dogma."

| Letter | Principle |
|---|---|
| **S** | Single Responsibility Principle |
| **O** | Open/Closed Principle |
| **L** | Liskov Substitution Principle |
| **I** | Interface Segregation Principle |
| **D** | Dependency Inversion Principle |

---

## 8.1 S — Single Responsibility Principle

> **A class should have just one reason to change.**

Try to make every class responsible for a **single part of the functionality** provided by the
software, and make that responsibility **entirely encapsulated by** (hidden within) the class.

### Why — the main goal is reducing complexity

- You don't need a sophisticated design for a program with ~200 lines of code. Make a dozen
  methods pretty and you'll be fine.
- **The real problems emerge when your program constantly grows and changes.** At some point
  classes become so big you can no longer remember their details. Code navigation slows to a
  crawl; you scan whole classes or the entire program to find specific things. The number of
  entities overflows your brain stack and you feel you're losing control over the code.
- **If a class does too many things, you have to change it every time one of these things
  changes** — risking breaking other parts of the class you didn't even intend to change.

> If you feel that it's becoming hard to focus on specific aspects of the program one at a
> time, remember SRP and check whether it's time to divide some classes into parts.

### Example

The `Employee` class has several reasons to change:
1. Its main job — **managing employee data**.
2. The **format of the timesheet report** may change over time.

```
BEFORE                              AFTER
┌──────────────────────────┐        ┌──────────────────┐   ┌──────────────────────────┐
│        Employee          │        │    Employee      │   │     TimeSheetReport      │
├──────────────────────────┤        ├──────────────────┤   ├──────────────────────────┤
│ getName()                │        │ getName()        │   │ print(employee)          │
│ printTimeSheetReport()   │        └──────────────────┘   └──────────────────────────┘
└──────────────────────────┘
```

Solve the problem by **moving the behavior related to printing timesheet reports into a
separate class**. This change also lets you move other report-related stuff to the new class.

---

## 8.2 O — Open/Closed Principle

> **Classes should be open for extension but closed for modification.**

The main idea: **keep existing code from breaking when you implement new features.**

| Term | Meaning |
|---|---|
| **Open** | You can extend it — produce a subclass and do whatever you want: add new methods or fields, override base behavior. Some languages let you restrict further extension with keywords like `final`; after that the class is no longer open. |
| **Closed** (or *complete*) | It's 100% ready to be used by other classes — its interface is clearly defined and won't be changed in the future. |

> The words *open* & *closed* sound mutually exclusive, but in terms of this principle a class
> can be **both open (for extension) and closed (for modification) at the same time.**

If a class is already developed, tested, reviewed, and included in a framework or otherwise
used in an app, **messing with its code is risky**. Instead, create a subclass and override
the parts you want to behave differently. You achieve your goal without breaking existing
clients.

> **This principle isn't meant to be applied for all changes to a class.** If you know there's
> a **bug** in the class, just go and fix it; don't create a subclass for it.
> **A child class shouldn't be responsible for the parent's issues.**

### Example

An e-commerce app with an `Order` class that calculates shipping costs, with all shipping
methods hardcoded inside the class. Adding a new shipping method means changing `Order`
and risking breaking it.

```
BEFORE                                     AFTER (Strategy pattern)
┌────────────────────────────────┐   ┌──────────────────────┐   ┌────────────────────────┐
│           Order                │   │       Order          │   │ «interface» Shipping   │
├────────────────────────────────┤   ├──────────────────────┤   ├────────────────────────┤
│ lineItems                      │   │ lineItems            │──►│ getCost(order)         │
│ shipping: string               │   │ shipping: Shipping   │   │ getDate(order)         │
├────────────────────────────────┤   ├──────────────────────┤   └────────────────────────┘
│ getTotal()                     │   │ getTotal()           │              ▲
│ getShippingCost()              │   │ getShippingCost()    │   ┌──────────┼──────────┐
│   if shipping == "ground" ...  │   │   return shipping    │  Ground   Air      NextDay
│   if shipping == "air" ...     │   │     .getCost(this)   │
└────────────────────────────────┘   └──────────────────────┘
```

Now when you need a new shipping method, derive a new class from the `Shipping` interface
**without touching any of `Order`'s code**. The client code links orders with a shipping
object of the new class whenever the user selects that shipping method in the UI.

> **Bonus:** this solution lets you move the delivery-time calculation to more relevant
> classes, according to the **single responsibility principle**.

---

## 8.3 L — Liskov Substitution Principle

> **When extending a class, remember that you should be able to pass objects of the subclass
> in place of objects of the parent class without breaking the client code.**

The subclass should remain **compatible with the behavior of the superclass**. When overriding
a method, **extend** the base behavior rather than replacing it with something else entirely.

This concept is critical when developing **libraries and frameworks**, because your classes
will be used by other people whose code you can't directly access and change.

Unlike other design principles which are wide open for interpretation, the substitution
principle has a **set of formal requirements** for subclasses and their methods.

### The LSP checklist

#### 1. Parameter types in a subclass method should **match or be more abstract** than in the superclass method

Say there's a class with a method supposed to feed cats: `feed(Cat c)`. Client code always
passes cat objects into this method.

- ✅ **Good:** a subclass overrides the method so it can feed **any animal** (a superclass of
  cats): `feed(Animal c)`. If you pass an object of this subclass instead of the superclass,
  everything still works. The method can feed all animals, so it can still feed any cat passed
  by the client.
- ❌ **Bad:** a subclass restricts the feeding method to only accept **Bengal cats** (a
  subclass of cats): `feed(BengalCat c)`. Since the method can only feed a specific breed,
  it won't serve generic cats passed by the client, breaking all related functionality.

#### 2. The return type in a subclass method should **match or be a subtype** of the superclass return type

> Requirements for a return type are **inverse** to requirements for parameter types.

Say you have `buyCat(): Cat`. The client expects to receive any cat.

- ✅ **Good:** a subclass overrides as `buyCat(): BengalCat`. The client gets a Bengal cat,
  which is still a cat — everything is okay.
- ❌ **Bad:** a subclass overrides as `buyCat(): Animal`. The client code breaks since it
  receives an unknown generic animal (an alligator? a bear?) that doesn't fit a structure
  designed for a cat.

> Another anti-example from dynamically typed languages: the base method returns a string, but
> the overridden method returns a number.

#### 3. A subclass method shouldn't throw types of exceptions the base method isn't expected to throw

Exception types should **match or be subtypes** of those the base method already throws.
This rule comes from the fact that `try-catch` blocks in the client code target specific
exception types the base method is likely to throw. An unexpected exception might **slip
through the defensive lines of the client code and crash the entire application**.

> In most modern programming languages, especially statically typed ones (Java, C#, others),
> rules 1–3 are **built into the language**. You won't be able to compile a program that
> violates them.

#### 4. A subclass shouldn't **strengthen pre-conditions**

Example: the base method has a parameter of type `int`. If a subclass overrides it and
requires that the argument be **positive** (throwing an exception if negative), this
strengthens the pre-conditions. Client code that used to work fine passing negative numbers
now breaks with an object of this subclass.

#### 5. A subclass shouldn't **weaken post-conditions**

Say a class has a method that works with a database and is supposed to **always close all
opened database connections** upon returning a value.

You create a subclass and change it so connections **remain open** so you can reuse them.
But the client might not know your intentions. Because it expects the method to close all
connections, it may simply terminate the program right after calling the method,
**polluting the system with ghost database connections**.

#### 6. **Invariants of a superclass must be preserved**

> This is probably the least formal rule of all.

**Invariants** are conditions in which an object makes sense. For example, invariants of a cat
are having four legs, a tail, ability to meow, etc.

The confusing part: while invariants can be defined **explicitly** in the form of interface
contracts or a set of assertions within methods, they could also be **implied** by certain
unit tests and expectations of the client code.

> This rule is the easiest to violate because you might misunderstand or not realize all
> invariants of a complex class. Therefore **the safest way to extend a class is to introduce
> new fields and methods, and not mess with any existing members of the superclass.**
> Of course, that's not always doable in real life.

#### 7. A subclass shouldn't change values of **private fields** of the superclass

*"What? How's that even possible?"* — some programming languages let you access private
members via **reflection** mechanisms. Other languages (Python, JavaScript) don't have any
protection for private members at all.

### Example — document class hierarchy

```
BEFORE (violates LSP)                    AFTER (fixed by redesigning the hierarchy)
┌────────────────────────┐               ┌────────────────────────────┐
│       Document         │               │      ReadOnlyDocument      │
├────────────────────────┤               ├────────────────────────────┤
│ data, filename         │               │ data, filename             │
│ open()                 │               │ open()                     │
│ save()                 │               └────────────────────────────┘
└────────────────────────┘                            ▲
            ▲                            ┌────────────────────────────┐
┌────────────────────────┐               │      WritableDocument      │
│   ReadOnlyDocument     │               ├────────────────────────────┤
├────────────────────────┤               │ save()   ← adds behavior   │
│ save()                 │               └────────────────────────────┘
│   throw Exception ✗    │
└────────────────────────┘
```

**The problem:** the `save` method in the `ReadOnlyDocuments` subclass **throws an exception**
if someone tries to call it. The base method doesn't have this restriction. This means client
code will break unless it **checks the document type before saving**.

The resulting code **also violates the open/closed principle**, since the client code becomes
dependent on concrete document classes. If you introduce a new document subclass, you'll need
to change the client code to support it.

**The fix:** redesign the class hierarchy. A subclass should **extend** the behavior of a
superclass — therefore the **read-only document becomes the base class**. The writable
document is now a subclass that extends the base class and **adds** the saving behavior.

---

## 8.4 I — Interface Segregation Principle

> **Clients shouldn't be forced to depend on methods they do not use.**

Make your interfaces **narrow enough** that client classes don't have to implement behaviors
they don't need.

Break down **"fat" interfaces** into more granular and specific ones. Clients should implement
only those methods they really need. Otherwise, **a change to a "fat" interface would break
even clients that don't use the changed methods.**

> Class inheritance lets a class have just **one superclass**, but it doesn't limit the number
> of **interfaces** a class can implement at the same time. Hence there's no need to cram tons
> of unrelated methods into a single interface. Break it into several refined interfaces — you
> can implement them all in a single class if needed. However, some classes may be fine with
> implementing just one of them.

### Example — cloud provider library

You created a library that makes it easy to integrate apps with various cloud computing
providers. The initial version only supported **Amazon Cloud**, but covered the full set of
cloud services and features.

You assumed all cloud providers have the same broad spectrum of features as Amazon. But when
implementing support for another provider, it turned out **most of the library's interfaces
are too wide** — some methods describe features other cloud providers just don't have.

```
BEFORE                                      AFTER
┌────────────────────────────┐   ┌──────────────────┐┌──────────────────┐┌────────────────┐
│ «interface» CloudProvider  │   │«interface»       ││«interface»       ││«interface»     │
├────────────────────────────┤   │ CloudHostingProv ││ CDNProvider      ││ CloudStorage   │
│ storeFile(name)            │   ├──────────────────┤├──────────────────┤├────────────────┤
│ getFile(name)              │   │ createServer(reg)││ getCDNAddress()  ││ storeFile(name)│
│ createServer(region)       │   │ listServers(reg) ││                  ││ getFile(name)  │
│ listServers(region)        │   └──────────────────┘└──────────────────┘└────────────────┘
│ getCDNAddress()            │           ▲   ▲              ▲                 ▲   ▲
└────────────────────────────┘           │   └──────┬───────┘                 │   │
        ▲            ▲                   │    ┌─────┴────────┐                │   │
   Amazon        Dropbox ✗        Amazon─┴────┤              ├────────────────┘   │
   (fine)     (must stub methods           (implements all three)    Dropbox──────┘
              it doesn't have)                                       (only storage)
```

While you *can* implement these methods and put some **stubs** there, it wouldn't be a pretty
solution. The better approach is to **break down the interface into parts**. Classes able to
implement the original interface now just implement several refined interfaces. Other classes
implement only those interfaces whose methods make sense for them.

> ⚠️ As with the other principles, **you can go too far with this one.** Don't further divide
> an interface which is already quite specific. Remember that **the more interfaces you create,
> the more complex your code becomes. Keep the balance.**

---

## 8.5 D — Dependency Inversion Principle

> **High-level classes shouldn't depend on low-level classes. Both should depend on
> abstractions. Abstractions shouldn't depend on details. Details should depend on
> abstractions.**

Usually when designing software you can distinguish two levels of classes:

| Level | Contains |
|---|---|
| **Low-level classes** | Basic operations: working with a disk, transferring data over a network, connecting to a database, etc. |
| **High-level classes** | Complex **business logic** that directs low-level classes to do something. |

Sometimes people design low-level classes first and only then start working on high-level
ones. This is very common when developing a prototype on a new system, where you're not even
sure what's possible at the higher level because the low-level stuff isn't yet implemented or
clear. **With such an approach, business logic classes tend to become dependent on primitive
low-level classes.**

DIP suggests **changing the direction of this dependency**.

### The three steps

1. **Describe interfaces for low-level operations that high-level classes rely on, preferably
   in business terms.** For instance, business logic should call a method `openReport(file)`
   rather than a series of methods `openFile(x)`, `readBytes(n)`, `closeFile(x)`.
   **These interfaces count as high-level ones.**
2. **Make high-level classes dependent on those interfaces**, instead of on concrete low-level
   classes. This dependency will be much softer than the original one.
3. **Once low-level classes implement these interfaces, they become dependent on the business
   logic level**, reversing the direction of the original dependency.

> DIP often goes along with the **open/closed principle**: you can extend low-level classes to
> use with different business logic classes without breaking existing classes.

### Example — budget reporting

The high-level budget reporting class uses a low-level database class for reading and
persisting data. Any change in the low-level class — e.g., a new version of the database
server — may affect the high-level class, which **isn't supposed to care about data storage
details**.

```
BEFORE                                 AFTER
┌────────────────────┐                 ┌──────────────────────┐
│  BudgetReport      │                 │    BudgetReport      │
│  (high level)      │                 │    (high level)      │
├────────────────────┤                 ├──────────────────────┤
│ db: MySQLDatabase  │                 │ db: Database         │
│ open(date)         │                 │ open(date)           │
│ save()             │                 │ save()               │
└─────────┬──────────┘                 └──────────┬───────────┘
          │ depends on                            │ depends on
          ▼                                       ▼
┌────────────────────┐                 ┌──────────────────────┐
│  MySQLDatabase     │                 │ «interface» Database │
│  (low level)       │                 ├──────────────────────┤
├────────────────────┤                 │ insert() update()    │
│ insert() update()  │                 │ delete()             │
│ delete()           │                 └──────────────────────┘
└────────────────────┘                       ▲            ▲
                                     MySQLDatabase   MongoDB
                                     (low level, now depends on
                                      the high-level abstraction)
```

Fix: create a **high-level interface** describing read/write operations and make the reporting
class use that interface instead of the low-level class. Then change or extend the original
low-level class to implement the new read/write interface declared by the business logic.

**Result: the direction of the original dependency has been inverted — low-level classes are
now dependent on high-level abstractions.**

---

## SOLID Quick Reference

| Principle | Statement | Typical fix |
|---|---|---|
| **SRP** | A class should have just one reason to change. | Extract the extra behavior into its own class. |
| **OCP** | Open for extension, closed for modification. | Extract the varying behavior behind an interface (e.g. Strategy). |
| **LSP** | Subclass objects must be substitutable for superclass objects. | Redesign the hierarchy so subclasses only *extend* behavior. |
| **ISP** | Clients shouldn't depend on methods they don't use. | Split fat interfaces into granular ones. |
| **DIP** | Depend on abstractions, not details. | Declare business-level interfaces; make low-level classes implement them. |
