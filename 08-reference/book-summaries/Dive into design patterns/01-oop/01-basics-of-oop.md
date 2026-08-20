# Chapter 1 — Basics of OOP

> Object-oriented programming is a paradigm based on the concept of wrapping pieces of data,
> and behavior related to that data, into special bundles called **objects**, which are constructed
> from a set of "blueprints", defined by a programmer, called **classes**.

## 1.1 Objects and Classes

- A **class** is a blueprint that defines the structure for objects.
- An **object** is a concrete instance of a class.
- Example: `Oscar` is an object, an instance of the `Cat` class.

| Term | Meaning |
|---|---|
| **Fields** (attributes) | The data a class holds: `name`, `sex`, `age`, `weight`, `color`, `favoriteFood` |
| **Methods** | The behaviors: `breathe()`, `eat()`, `run()`, `sleep()`, `meow()` |
| **Members** | Fields + methods, collectively |
| **State** | Data stored inside the object's fields |
| **Behavior** | All of the object's methods |

```
┌─────────────────────┐
│        Cat          │   ← class (blueprint)
├─────────────────────┤
│ name: string        │
│ sex: enum           │   ← fields
│ age: int            │
│ weight: float       │
│ color: string       │
│ favoriteFood: Food  │
├─────────────────────┤
│ breathe()           │
│ eat(food)           │   ← methods
│ run(destination)    │
│ sleep(hours)        │
│ meow()              │
└─────────────────────┘
        │  instantiates
   ┌────┴─────┐
   ▼          ▼
 Oscar       Luna        ← objects (instances)
 male        female
 3 yrs       2 yrs
 7 kg        4 kg
```

Luna, your friend's cat, is also an instance of the `Cat` class. It has the **same set of
attributes** as Oscar — the difference is only in the **values** of those attributes.

## 1.2 Class Hierarchies

A real program contains more than a single class. Classes may be organized into **hierarchies**.

Dogs and cats have a lot in common: `name`, `sex`, `age`, `color` are attributes of both.
Both can `breathe`, `sleep` and `run`. So we extract a base `Animal` class holding the
common attributes and behaviors.

- A parent class is called a **superclass**.
- Its children are **subclasses**.
- Subclasses **inherit** state and behavior from their parent, defining only what differs.
  - `Cat` adds `meow()`; `Dog` adds `bark()`.

Given a related business requirement, we can go further and extract a more general
`Organism` class, which becomes the superclass for `Animal` and `Plant`.
Such a pyramid of classes is a **hierarchy**. In it, `Cat` inherits everything from
**both** `Animal` and `Organism`.

```
              Organism
              ├─ position
              └─ move()
                  ▲
        ┌─────────┴─────────┐
      Animal              Plant
      ├─ age              ├─ height
      ├─ isAlive          └─ photosynthesize()
      ├─ breathe()
      ├─ eat()
      └─ sleep()
          ▲
    ┌─────┴─────┐
   Cat         Dog
   └─ meow()   └─ bark()
```

> UML tip: classes in a diagram can be **simplified** (contents omitted) if it's more
> important to show their relations than their contents.

Subclasses can **override** the behavior of methods they inherit. A subclass can either
completely replace the default behavior or just enhance it with extra work.

## Key Takeaways

1. Class = blueprint; object = instance with concrete field values.
2. Fields hold *state*, methods define *behavior*; together they are *members*.
3. Hierarchies let you extract common state/behavior into superclasses.
4. Subclasses inherit everything and may override or extend inherited methods.
