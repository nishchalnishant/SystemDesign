# Chapter 7 — Design Principles

> What is good software design? How would you measure it? What practices would you need to
> follow to achieve it? How can you make your architecture flexible, stable and easy to
> understand?

These are great questions; but, unfortunately, the answers **differ depending on the type of
application** you're building. Nevertheless, there are several **universal principles** of
software design. **Most of the design patterns in this book are based on these principles.**

---

## 7.1 Encapsulate What Varies

> **Identify the aspects of your application that vary and separate them from what stays
> the same.**

The main goal of this principle is to **minimize the effect caused by changes**.

> **Analogy:** Imagine your program is a ship, and changes are hideous mines lingering under
> water. Struck by a mine, the ship sinks. Knowing this, you divide the ship's hull into
> independent compartments that can be safely sealed to limit damage to a single compartment.
> Now, if the ship hits a mine, the ship as a whole remains afloat.

In the same way, you isolate the parts of the program that vary in independent modules,
protecting the rest of the code from adverse effects. As a result, you spend less time
getting the program back into working shape, implementing and testing the changes.
**The less time you spend making changes, the more time you have for implementing features.**

### Encapsulation on a method level

Say you're making an e-commerce website. Somewhere in your code, there's a `getOrderTotal`
method that calculates a grand total for the order, including taxes.

We can anticipate that tax-related code might need to change: the tax rate depends on the
country, state or even city where the customer resides, and the formula may change over time
due to new laws. As a result, you'll need to change `getOrderTotal` quite often — **even
though the method's name suggests it doesn't care how the tax is calculated.**

**BEFORE:** tax calculation code is mixed with the rest of the method's code.

```
 1  method getOrderTotal(order) is
 2    total = 0
 3    foreach item in order.lineItems
 4      total += item.price * item.quantity
 5
 6    if (order.country == "US")
 7      total += total * 0.07 // US sales tax
 8    else if (order.country == "EU"):
 9      total += total * 0.20 // European VAT
10
11    return total
```

**AFTER:** you can get the tax rate by calling a designated method.

```
 1  method getOrderTotal(order) is
 2    total = 0
 3    foreach item in order.lineItems
 4      total += item.price * item.quantity
 5
 6    total += total * getTaxRate(order.country)
 7
 8    return total
 9
10  method getTaxRate(country) is
11    if (country == "US")
12      return 0.07 // US sales tax
13    else if (country == "EU")
14      return 0.20 // European VAT
15    else
16      return 0
```

Tax-related changes become **isolated inside a single method**. Moreover, if the tax
calculation logic becomes too complicated, it's now easier to move it to a separate class.

### Encapsulation on a class level

Over time you might add more and more responsibilities to a method that used to do a simple
thing. These added behaviors often come with their own **helper fields and methods** that
eventually **blur the primary responsibility** of the containing class. Extracting everything
to a new class might make things much clearer and simpler.

```
BEFORE                                AFTER
┌────────────────────────┐            ┌──────────────────┐      ┌────────────────────┐
│         Order          │            │      Order       │      │    TaxCalculator   │
├────────────────────────┤            ├──────────────────┤      ├────────────────────┤
│ lineItems              │            │ lineItems        │      │ getTaxRate(country,│
│ country                │            │ country          │─────►│   state, product)  │
│ state                  │            │ state            │      │ getUSTax(state)    │
├────────────────────────┤            ├──────────────────┤      │ getEUTax(country)  │
│ getOrderTotal()        │            │ getOrderTotal()  │      │ getChineseTax(..)  │
│ getTaxRate(country,    │            └──────────────────┘      └────────────────────┘
│   state, product)      │
└────────────────────────┘
```

Objects of the `Order` class **delegate all tax-related work** to a special object that does
just that.

---

## 7.2 Program to an Interface, not an Implementation

> **Program to an interface, not an implementation. Depend on abstractions, not on
> concrete classes.**

You can tell the design is flexible enough if you can easily **extend it without breaking any
existing code**.

> **Cat analogy:** A `Cat` that can eat *any food* is more flexible than one that can eat just
> *sausages*. You can still feed the first cat with sausages because they are a subset of
> "any food"; however, you can extend that cat's menu with any other food.

### The four-step recipe

When you want to make two classes collaborate, you can start by making one dependent on the
other. But there's a more flexible way:

1. **Determine what exactly one object needs from the other:** which methods does it execute?
2. **Describe these methods** in a new interface or abstract class.
3. **Make the class that is a dependency implement this interface.**
4. **Make the second class dependent on this interface** rather than on the concrete class.
   You still can make it work with objects of the original class, but the connection is now
   much more flexible.

> After making this change, you probably won't feel any immediate benefit. On the contrary,
> the code has become **more complicated** than before. However, if you feel this might be a
> good extension point for extra functionality, or that other people using your code might
> want to extend it here, then go for it.

### Example — software development company simulator

You have different classes representing various employee types.

**BEFORE: all classes are tightly coupled.**

```
┌───────────────────────────┐
│         Company           │
├───────────────────────────┤
│ createSoftware()          │
│   d = new Designer()      │
│   d.designArchitecture()  │
│   p = new Programmer()    │
│   p.writeCode()           │
│   t = new Tester()        │
│   t.testSoftware()        │
└───────────────────────────┘
        │      │      │
        ▼      ▼      ▼
   Designer Programmer Tester
```

Despite the difference in their implementations, we can generalize various work-related
methods and extract a **common interface** for all employee classes. After that we can apply
**polymorphism** inside `Company`, treating various employee objects via the `Employee`
interface.

**BETTER: polymorphism simplified the code, but the rest of `Company` still depends on the
concrete employee classes.**

```
┌──────────────────────────────────┐         ┌──────────────────────┐
│           Company                │         │ «interface» Employee │
├──────────────────────────────────┤────────►├──────────────────────┤
│ createSoftware()                 │         │ doWork()             │
│   employees = getEmployees()     │         └──────────────────────┘
│   foreach e in employees         │                    ▲
│     e.doWork()                   │        ┌───────────┼───────────┐
│                                  │   Designer     Programmer    Tester
│ getEmployees(): Employee[]       │   doWork()     doWork()      doWork()
│   return [new Designer(), ...]   │  ← still concrete!
└──────────────────────────────────┘
```

The `Company` class **remains coupled** to the employee classes. This is bad: if we introduce
new types of companies that work with other types of employees, we'll need to override most
of the `Company` class instead of reusing that code.

To solve this, declare the method for getting employees as **abstract**. Each concrete company
implements it differently, creating only those employees that it needs.

**AFTER: the primary method of `Company` is independent from concrete employee classes.
Employee objects are created in concrete company subclasses.**

```
┌──────────────────────────────────────┐
│      Company (abstract)              │
├──────────────────────────────────────┤
│ createSoftware()                     │
│   employees = getEmployees()         │
│   foreach e in employees             │
│     e.doWork()                       │
│                                      │
│ abstract getEmployees(): Employee[]  │
└──────────────────────────────────────┘
                 ▲
     ┌───────────┴────────────┐
┌─────────────────────┐  ┌──────────────────────┐
│ GameDevCompany      │  │ OutsourcingCompany   │
├─────────────────────┤  ├──────────────────────┤
│ getEmployees()      │  │ getEmployees()       │
│  return [Designer,  │  │  return [Programmer, │
│    Artist]          │  │    Tester]           │
└─────────────────────┘  └──────────────────────┘
```

Now you can extend this class and introduce new types of companies and employees **while
still reusing a portion of the base company class**. Extending the base company class doesn't
break any existing code that already relies on it.

> 🎯 **You've just seen a design pattern in action!** That was an example of the
> **Factory Method** pattern.

---

## 7.3 Favor Composition Over Inheritance

Inheritance is probably the most obvious and easy way of reusing code between classes:
two classes with the same code → create a common base class and move the similar code into it.

Unfortunately, inheritance comes with **caveats that often become apparent only after your
program already has tons of classes** and changing anything is pretty hard.

### The five problems with inheritance

1. **A subclass can't reduce the interface of the superclass.** You have to implement all
   abstract methods of the parent class even if you won't be using them.
2. **When overriding methods you need to ensure the new behavior is compatible with the base
   one.** Objects of the subclass may be passed to any code that expects objects of the
   superclass, and you don't want that code to break.
3. **Inheritance breaks encapsulation of the superclass** because the internal details of the
   parent become available to the subclass. There might be an opposite situation where a
   programmer makes a superclass aware of some details of subclasses for the sake of making
   further extension easier.
4. **Subclasses are tightly coupled to superclasses.** Any change in a superclass may break
   the functionality of subclasses.
5. **Reusing code through inheritance can lead to parallel inheritance hierarchies.**
   Inheritance usually takes place in a *single dimension*. But whenever there are two or more
   dimensions, you have to create lots of class combinations, **bloating the class hierarchy
   to a ridiculous size**.

### Composition — the alternative

| Relationship | Meaning | Example |
|---|---|---|
| **Inheritance** | "**is a**" | A car *is a* transport |
| **Composition** | "**has a**" | A car *has an* engine |

> This principle also applies to **aggregation** — a more relaxed variant of composition where
> one object may have a reference to the other but doesn't manage its lifecycle. Example:
> a car *has* a driver, but he or she may use another car or just walk without the car.

### Example — car manufacturer catalog app

The company makes both **cars and trucks**; they can be either **electric or gas**;
all models have either **manual controls or an autopilot**.

**INHERITANCE:** extending a class in several dimensions
(cargo type × engine type × navigation type) leads to a **combinatorial explosion** of
subclasses.

```
                          Transport
                              ▲
              ┌───────────────┴───────────────┐
             Car                            Truck
              ▲                               ▲
        ┌─────┴─────┐                   ┌─────┴─────┐
   ElectricCar   CombustionCar    ElectricTruck  CombustionTruck
        ▲             ▲                  ▲             ▲
    ┌───┴───┐     ┌───┴───┐          ┌───┴───┐     ┌───┴───┐
 Autopilot Manual Autopilot Manual  Autopilot Manual Autopilot Manual
  ElectricCar ...  (8 leaf classes and growing)
```

Each additional parameter **multiplies** the number of subclasses. There's a lot of duplicate
code between subclasses because a subclass can't extend two classes at the same time.

**COMPOSITION:** different "dimensions" of functionality extracted to their own class
hierarchies.

```
┌────────────────────┐
│     Transport      │
├────────────────────┤       ┌──────────────────┐
│ engine: Engine     │──────►│«interface» Engine│◄── CombustionEngine
│ driver: Driver     │──┐    │  move()          │◄── ElectricEngine
├────────────────────┤  │    └──────────────────┘
│ deliver(dest,cargo)│  │    ┌──────────────────┐
└────────────────────┘  └───►│«interface» Driver│◄── Robot
                             │  navigate()      │◄── Human
                             └──────────────────┘
```

**Added benefit: you can replace a behavior at runtime.** For instance, you can replace the
engine object linked to a car object just by assigning a different engine object to the car.

> This structure of classes resembles the **Strategy** pattern.

---

## Summary

| Principle | Core idea |
|---|---|
| **Encapsulate What Varies** | Isolate the parts that change from the parts that stay the same — at method level and at class level. |
| **Program to an Interface** | Depend on abstractions, not concrete classes; extract the interface for what one object needs from another. |
| **Favor Composition Over Inheritance** | "has a" beats "is a" when you have multiple dimensions of variation; behavior becomes swappable at runtime. |
