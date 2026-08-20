# Chapter 2 — Pillars of OOP

Object-oriented programming is based on **four pillars** — concepts that differentiate it
from other programming paradigms:

1. Abstraction
2. Encapsulation
3. Inheritance
4. Polymorphism

---

## 2.1 Abstraction

Most of the time when creating a program with OOP, you shape objects based on real-world
objects. However, program objects **don't represent the originals with 100% accuracy**
(and it's rarely required that they do). Your objects only model attributes and behaviors
of real objects **in a specific context**, ignoring the rest.

**Example — the same real-world object, two models:**

| Context | `Airplane` class models |
|---|---|
| Flight simulator | Details related to the actual flight (speed, altitude, fuel, controls) |
| Flight booking application | Only the seat map and which seats are available |

> **Definition:** *Abstraction* is a model of a real-world object or phenomenon, limited to a
> specific context, which represents all details relevant to this context with high accuracy
> and omits all the rest.

---

## 2.2 Encapsulation

To start a car engine, you only need to turn a key or press a button. You don't need to
connect wires under the hood, rotate the crankshaft and cylinders, and initiate the power
cycle of the engine. Those details are hidden **under the hood**. You have only a simple
interface: a start switch, a steering wheel and some pedals.

This illustrates that each object has an **interface** — a public part of an object, open to
interactions with other objects.

> **Definition:** *Encapsulation* is the ability of an object to hide parts of its state and
> behaviors from other objects, exposing only a limited interface to the rest of the program.

**Access levels:**

| Modifier | Visibility |
|---|---|
| `private` | Accessible only from within the methods of its own class |
| `protected` | Less restrictive — also available to subclasses |
| *public* | Part of the object's interface, open to everyone |

Interfaces and abstract classes/methods of most programming languages are based on the
concepts of **abstraction and encapsulation**. In modern OO languages, the interface
mechanism (usually declared with the `interface` or `protocol` keyword) lets you define
**contracts of interaction** between objects. That's one of the reasons why interfaces only
care about the *behaviors* of objects, and why **you can't declare a field in an interface**.

> ⚠️ The fact that the word *interface* stands for the public part of an object, while
> there's also the `interface` **type** in most programming languages, is very confusing.
> The author acknowledges this.

**Example:**

Imagine a `FlyingTransport` interface with a method `fly(origin, destination, passengers)`.
When designing an air-transportation simulator, you could restrict the `Airport` class to
work **only** with objects that implement `FlyingTransport`. After this, you can be sure
that any object passed to an airport object — whether it's an `Airplane`, a `Helicopter`
or a freaking `DomesticatedGryphon` — would be able to arrive at or depart from this
type of airport.

```
        ┌───────────────────────────────────────────┐
        │      «interface» FlyingTransport          │
        ├───────────────────────────────────────────┤
        │ fly(origin, destination, passengers)      │
        └───────────────────────────────────────────┘
                          ▲ implements
         ┌────────────────┼────────────────┐
    ┌─────────┐    ┌────────────┐   ┌────────────────────┐
    │ Airplane│    │ Helicopter │   │ DomesticatedGryphon│
    └─────────┘    └────────────┘   └────────────────────┘

    ┌─────────┐  works only with FlyingTransport
    │ Airport │───────────────────────────────►
    └─────────┘
```

You could change the implementation of the `fly` method in these classes in any way you
want. **As long as the signature of the method remains the same** as declared in the
interface, all instances of `Airport` can work with your flying objects just fine.

---

## 2.3 Inheritance

> **Definition:** *Inheritance* is the ability to build new classes on top of existing ones.
> The main benefit of inheritance is **code reuse**.

If you want to create a class that's slightly different from an existing one, there's no need
to duplicate code. Instead, you **extend** the existing class and put the extra
functionality into the resulting subclass, which inherits fields and methods of the superclass.

**Consequences / costs of inheritance:**

- Subclasses have the **same interface** as their parent class.
- You **can't hide** a method in a subclass if it was declared in the superclass.
- You **must implement all abstract methods**, even if they don't make sense for your subclass.
- In most languages, a subclass can **extend only one superclass**.
- Any class can **implement several interfaces** at the same time.
- If a superclass implements an interface, **all of its subclasses must also implement it**.

```
   single inheritance             multiple interface implementation

     ┌───────────┐            ┌─────────────┐   ┌─────────────┐
     │  Animal   │            │«interface»  │   │«interface»  │
     └─────┬─────┘            │ Swimmer     │   │ Runner      │
           │ extends          └──────┬──────┘   └──────┬──────┘
     ┌─────▼─────┐                   └────────┬────────┘
     │    Cat    │                     ┌──────▼──────┐
     └───────────┘                     │     Dog     │
                                       └─────────────┘
```

---

## 2.4 Polymorphism

Most `Animal`s can make sounds. We can anticipate that all subclasses will need to override
the base `makeSound` method so each subclass emits the correct sound; therefore we can
declare it **abstract** right away. This lets us omit any default implementation in the
superclass, but **forces** all subclasses to come up with their own.

Imagine we've put several cats and dogs into a large bag. Then, with closed eyes, we take
the animals out one-by-one. After taking an animal from the bag, we don't know for sure what
it is. However, if we cuddle it hard enough, the animal will emit a specific sound of joy,
depending on its **concrete class**.

```
1  bag = [new Cat(), new Dog()];
2
3  foreach (Animal a : bag)
4    a.makeSound()
5
6  // Meow!
7  // Woof!
```

The program doesn't know the concrete type of the object contained inside the `a` variable;
but, thanks to the special mechanism called **polymorphism**, the program can trace down the
subclass of the object whose method is being executed and run the appropriate behavior.

> **Definition:** *Polymorphism* is the ability of a program to detect the real class of an
> object and call its implementation even when its real type is unknown in the current context.

You can also think of polymorphism as the ability of an object to **"pretend" to be
something else** — usually a class it extends or an interface it implements. In our example,
the dogs and cats in the bag were pretending to be generic animals.

---

## Key Takeaways

| Pillar | One-liner |
|---|---|
| **Abstraction** | Model only what matters *in this context*; omit the rest. |
| **Encapsulation** | Hide internal state/behavior; expose a limited interface. |
| **Inheritance** | Build new classes on existing ones for code reuse — at the cost of a fixed interface and single-parent limitation. |
| **Polymorphism** | The program resolves the real class at run time and calls its implementation. |
