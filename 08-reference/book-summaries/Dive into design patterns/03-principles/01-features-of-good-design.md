# Chapter 6 — Features of Good Design

Before proceeding to the actual patterns: the process of designing software architecture —
things to aim for and things you'd better avoid.

---

## 6.1 Code Reuse

**Cost and time** are two of the most valuable metrics when developing any software product.

- Less time in development → entering the market earlier than competitors.
- Lower development costs → more money left for marketing and a broader reach to
  potential customers.

**Code reuse** is one of the most common ways to reduce development costs. The intent is
obvious: instead of developing something over and over from scratch, reuse existing code in
new projects.

The idea looks great on paper, but making existing code work in a **new context** usually
takes extra effort. What kills reusability:

- Tight coupling between components
- Dependencies on **concrete classes** instead of interfaces
- Hardcoded operations

All of this reduces flexibility of the code and makes it harder to reuse.

> Using design patterns is one way to increase flexibility of software components and make
> them easier to reuse. However, this sometimes comes at the price of **making the components
> more complicated**.

### Erich Gamma on the three levels of reuse

> I see three levels of reuse.
>
> **At the lowest level, you reuse classes**: class libraries, containers, maybe some class
> "teams" like container/iterator.
>
> **Frameworks are at the highest level.** They really try to distill your design decisions.
> They identify the key abstractions for solving a problem, represent them by classes and
> define relationships between them. JUnit is a small framework, for example. It is the
> "Hello, world" of frameworks. It has `Test`, `TestCase`, `TestSuite` and relationships
> defined.
>
> A framework is typically larger-grained than just a single class. Also, you hook into
> frameworks by subclassing somewhere. They use the so-called **Hollywood principle** of
> *"don't call us, we'll call you."* The framework lets you define your custom behavior, and
> it will call you when it's your turn to do something. Same with JUnit, right? It calls you
> when it wants to execute a test for you, but the rest happens in the framework.
>
> **There also is a middle level. This is where I see patterns.** Design patterns are both
> smaller and more abstract than frameworks. They're really a description about how a couple
> of classes can relate to and interact with each other. The level of reuse increases when
> you move from classes to patterns and finally frameworks.
>
> What is nice about this middle layer is that **patterns offer reuse in a way that is less
> risky than frameworks**. Building a framework is high-risk and a significant investment.
> Patterns let you reuse design ideas and concepts independently of concrete code.

| Level | Unit of reuse | Risk / investment |
|---|---|---|
| Low | **Classes** — libraries, containers, class "teams" (container/iterator) | Low |
| Middle | **Patterns** — descriptions of how a couple of classes relate and interact | Low risk, reuse of *design ideas* independent of concrete code |
| High | **Frameworks** — distilled design decisions, key abstractions + relationships; Hollywood principle | High risk, significant investment |

---

## 6.2 Extensibility

> **Change is the only constant thing in a programmer's life.**

Examples:

- You released a video game for Windows, but now people ask for a macOS version.
- You created a GUI framework with square buttons, but several months later round buttons
  become a trend.
- You designed a brilliant e-commerce website architecture, but a month later customers ask
  for a feature that would let them accept phone orders.

### Three reasons why this happens

1. **We understand the problem better once we start to solve it.** Often by the time you
   finish the first version of an app, you're ready to rewrite it from scratch because you
   now understand many aspects of the problem much better. You have also grown
   professionally, and your own code now looks like crap.
2. **Something beyond your control has changed.** This is why so many dev teams pivot from
   their original ideas into something new. Everyone who relied on Flash in an online
   application has been reworking or migrating their code as browser after browser drops
   support for Flash.
3. **The goalposts move.** Your client was delighted with the current version, but now sees
   eleven "little" changes he'd like so it can do other things he never mentioned in the
   original planning sessions. These aren't frivolous changes: your excellent first version
   has shown him that even more is possible.

> 🌟 There's a bright side: **if someone asks you to change something in your app, that means
> someone still cares about it.**

That's why all seasoned developers **try to provide for possible future changes** when
designing an application's architecture.
