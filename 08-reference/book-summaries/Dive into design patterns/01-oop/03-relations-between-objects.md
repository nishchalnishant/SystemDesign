# Chapter 3 — Relations Between Objects

In addition to **inheritance** and **implementation** already seen, there are other types of
relations between objects.

---

## 3.1 Dependency

> *UML Dependency: Professor depends on the course materials.*
> Notation: dashed arrow with an open arrowhead.

**Dependency** is the most basic and the **weakest** type of relation between classes.
There is a dependency between two classes if some changes to the definition of one class
might result in modifications to another class.

**When it occurs:** typically when you use **concrete class names** in your code —
specifying types in method signatures, instantiating objects via constructor calls, etc.

**How to weaken it:** make your code depend on **interfaces or abstract classes** instead of
concrete classes.

> Usually a UML diagram doesn't show every dependency — there are far too many of them in
> any real code. Instead of polluting the diagram, be very selective and show only those
> important to whatever you are communicating.

---

## 3.2 Association

> *UML Association: Professor communicates with students.*
> Notation: a simple solid arrow drawn from an object and pointing to the object it uses.
> Bi-directional association is completely normal — then the arrow has a point at each end.

**Association** is a relationship in which one object **uses or interacts with** another.

Association can be seen as a **specialized kind of dependency**, where an object *always*
has access to the objects with which it interacts, whereas simple dependency doesn't
establish a permanent link between objects.

In general, you use an association to represent something like a **field in a class**.
The link is always there — you can always ask an order for its customer. But it doesn't
*have* to be a field: if you are modeling your classes from an interface perspective, it can
just indicate the presence of a method that will return the order's customer.

### Combined example — association vs. dependency

```
1  class Professor is
2    field Student student
3    // ...
4    method teach(Course c) is
5      // ...
6      this.student.remember(c.getKnowledge())
```

- **`Course` is a dependency.** The `teach` method takes an argument of the `Course` class,
  used in the body. If someone changes `Course.getKnowledge()` (alters its name, adds
  required parameters, etc.) our code will break.
- **`Student` is a dependency *and* an association.** If the `remember` method changes,
  `Professor`'s code will break (dependency). But since the `student` field is always
  accessible to any method of `Professor`, the link is permanent — so it's also an association.

---

## 3.3 Aggregation

> *UML Aggregation: Department contains professors.*
> Notation: a line with an **empty diamond** at the container end and an arrow at the end
> pointing toward the component.

**Aggregation** is a specialized type of association that represents **"one-to-many"**,
**"many-to-many"** or **"whole-part"** relations between multiple objects.

Usually, under aggregation, an object "has" a set of other objects and serves as a
**container or collection**. Crucially:

- The component **can exist without** the container.
- The component **can be linked to several containers** at the same time.

> While we talk about relations between *objects*, keep in mind that UML represents relations
> between *classes*. A university object might consist of multiple departments even though you
> see just one "block" per entity in the diagram. UML can represent quantities on both sides
> of relationships, but it's okay to omit them if the quantities are clear from context.

---

## 3.4 Composition

> *UML Composition: University consists of departments.*
> Notation: drawn the same as aggregation, but with a **filled diamond** at the arrow's base.

**Composition** is a specific kind of aggregation, where one object is **composed of** one or
more instances of the other. The distinction: **the component can only exist as a part of the
container**.

> **Terminology note:** many people use the term "composition" when they really mean both
> aggregation and composition. The most notorious example is the famous principle
> *"choose composition over inheritance."* It's not because people are ignorant about the
> difference, but rather because the word "composition" (e.g. "object composition") sounds
> more natural in English.

---

## 3.5 The Big Picture — from weakest to strongest

| Relation | Meaning |
|---|---|
| **Dependency** | Class A can be affected by changes in class B. |
| **Association** | Object A **knows about** object B. Class A depends on B. |
| **Aggregation** | Object A knows about object B, **and consists of** B. Class A depends on B. |
| **Composition** | Object A knows about object B, consists of B, **and manages B's life cycle**. Class A depends on B. |
| **Implementation** | Class A **defines methods declared in interface B**. Objects A can be treated as B. Class A depends on B. |
| **Inheritance** | Class A **inherits interface and implementation** of class B but can extend it. Objects A can be treated as B. Class A depends on B. |

```
weakest ──────────────────────────────────────────────────────► strongest

Dependency → Association → Aggregation → Composition → Implementation → Inheritance
   ┄┄►          ───►          ◇───►         ◆───►          ┄┄▷            ───▷
```

This ordering answers the classic questions:
- *"What's the difference between aggregation and composition?"* → life-cycle management.
- *"Is inheritance a type of dependency?"* → yes, the strongest one.

---

## UML Notation Cheat Sheet

| Relation | Line style | Head / base marker |
|---|---|---|
| Dependency | dashed | open arrowhead |
| Association | solid | open arrowhead (one or both ends) |
| Aggregation | solid | **empty** diamond at container, arrow at component |
| Composition | solid | **filled** diamond at container, arrow at component |
| Implementation | dashed | hollow triangle at interface |
| Inheritance | solid | hollow triangle at superclass |
