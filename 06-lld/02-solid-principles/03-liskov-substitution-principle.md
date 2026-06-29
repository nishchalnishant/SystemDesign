---
module: 06-lld
topic: Solid Principles
status: unread
tags: [06-lld, system-design, solid-principles]
---
# Liskov Substitution Principle (LSP)

**Question**: You have a `processPayment(PaymentMethod p)` method. It calls `p.refund()`. You add `GiftCardPayment extends PaymentMethod`. Someone calls `processPayment(new GiftCardPayment(...))`. The method throws `UnsupportedOperationException` — gift cards aren't refundable. Who is responsible for catching this? How does the caller know?

**Problem without LSP**: The caller accepted a `PaymentMethod` contract that promised `refund()` would work. The subclass violated that promise. Now every caller must `instanceof`-check before calling `refund()`, defeating the purpose of polymorphism. The subtype is not substitutable for the base type.

**Minimal fix**: If `GiftCardPayment` cannot honor the `refund()` contract, it should not extend a class or implement an interface that requires it. Extract a `Refundable` interface. Only `CreditCardPayment` implements it. `processRefund()` takes `Refundable`, not `PaymentMethod`.

**Full principle**: LSP — subtypes must be substitutable for their base types without altering the correctness of the program. Subclasses should only strengthen postconditions and weaken preconditions, never the reverse. If a subclass cannot honor a method's contract, the inheritance hierarchy is wrong.

> **Analogy**: A square is a rectangle in math, but not in code. If you have a rectangle and call `setWidth(5)`, you expect height to stay the same. A square breaks this expectation — it changes both dimensions. So `Square` should NOT extend `Rectangle`.

---

## Topic Mindmap

```
[Liskov Substitution Principle]
├── Problem It Solves
│   ├── GiftCardPayment extends PaymentMethod but throws on refund()
│   ├── Caller accepted a contract; subclass violated it silently
│   └── Result: instanceof checks everywhere — polymorphism defeated
├── Core Rule
│   ├── Subtype must be substitutable for base type, no surprises
│   ├── Subclass may only strengthen postconditions or weaken preconditions
│   └── Test: can every subclass pass the parent's unit tests unchanged?
├── Classic Violations
│   ├── Square extends Rectangle: setWidth() changes height — breaks contract
│   ├── Penguin extends Bird: fly() throws UnsupportedOperationException
│   └── GiftCard extends PaymentMethod: refund() throws at runtime
├── The Fix Pattern
│   ├── Extract a narrower interface that only promises what the type can do
│   ├── Refundable interface → only CreditCardPayment implements it
│   ├── processRefund() takes Refundable, not PaymentMethod
│   └── FlyingBird vs Bird: only birds that fly implement FlyingBird
├── Behavioral Contract Rules
│   ├── Override must not throw new exception types the base doesn't declare
│   ├── Override must not require stricter input (weaken preconditions)
│   └── Override must at least deliver what base promised (strengthen post)
├── LSP Substitute Test
│   ├── Replace every use of Parent with Child in isolation
│   ├── Run parent's test suite against child — all must pass
│   └── If any test fails, hierarchy is wrong
├── Relation to Other Principles
│   ├── LSP enables OCP: you can only substitute if LSP holds
│   ├── ISP helps LSP: fat interfaces → more opportunities for bad inheritance
│   └── DIP: when depending on abstractions, LSP guarantees they are safe
└── Interview Angles
    ├── Why is Square/Rectangle a classic LSP violation?
    ├── What is the difference between IS-A in math vs in code?
    └── How do you fix an LSP violation without changing callers?
```

## The Core Idea

If class `B` extends class `A`, then wherever you use an `A`, you should be able to drop in a `B` and the program should still behave correctly. LSP is violated when a subclass overrides behavior in a way that surprises the caller.

**Simple test**: Can every subclass pass the parent's tests without modification? If not, LSP is violated.

---

## Bad Design (Violates LSP)

```python
class Rectangle:
    def __init__(self, width, height):
        self._width = width
        self._height = height

    def set_width(self, width):
        self._width = width

    def set_height(self, height):
        self._height = height

    def get_area(self):
        return self._width * self._height

class Square(Rectangle):
    def __init__(self, side):
        super().__init__(side, side)

    def set_width(self, width):
        self._width = width
        self._height = width  # Violates LSP: set_width changes height too

    def set_height(self, height):
        self._width = height
        self._height = height  # Violates LSP: set_height changes width too
```

**Why this breaks:**

```python
# This test passes for Rectangle but FAILS for Square
def test_rectangle(r):
    r.set_width(5)
    r.set_height(10)
    assert r.get_area() == 50  # Passes for Rectangle, FAILS for Square (returns 100)

r = Square(4)
test_rectangle(r)  # Square breaks the caller's valid assumption
```

The caller expected `setWidth` and `setHeight` to be independent. Square violates this contract silently.

---

## Good Design (Follows LSP)

```python
from abc import ABC, abstractmethod

# Abstract Shape — no conflicting contract
class Shape(ABC):
    @abstractmethod
    def get_area(self): ...

class Rectangle(Shape):
    def __init__(self, width, height):
        self._width = width
        self._height = height

    def set_width(self, width):
        self._width = width

    def set_height(self, height):
        self._height = height

    def get_area(self):
        return self._width * self._height

class Square(Shape):
    def __init__(self, side):
        self._side = side

    def set_side(self, side):
        self._side = side

    def get_area(self):
        return self._side * self._side
```

Now `Rectangle` and `Square` implement the same `Shape` contract without inheriting incompatible behavior from each other. Both can be used interchangeably where a `Shape` is expected.

---

## Another Classic Violation: The "Do Nothing" or "Exception" Override

```python
class Bird:
    def fly(self):
        print("Flying...")

class Penguin(Bird):
    def fly(self):
        raise NotImplementedError("Penguins can't fly!")
        # LSP violated — caller expected Bird to fly, Penguin breaks that
```

**Fix**: Separate the `Flyable` behavior into an interface.

```python
from abc import ABC, abstractmethod

class Flyable(ABC):
    @abstractmethod
    def fly(self): ...

class Sparrow(Flyable):
    def fly(self):
        print("Sparrow flying...")

class Penguin:
    # No fly() — Penguin never claimed to fly
    def swim(self):
        print("Penguin swimming...")
```

---

## LSP in Practice: Payment Processors

```python
from abc import ABC, abstractmethod

# Base type
class PaymentProcessor(ABC):
    @abstractmethod
    def process_payment(self, amount): ...

    @abstractmethod
    def refund(self, transaction_id): ...

# This is fine
class StripeProcessor(PaymentProcessor):
    def process_payment(self, amount): return True   # Stripe API
    def refund(self, transaction_id): return True    # Stripe refund

# LSP VIOLATION: GiftCardProcessor can't refund
class GiftCardProcessor(PaymentProcessor):
    def process_payment(self, amount): return True   # Deduct from card
    def refund(self, transaction_id):
        raise NotImplementedError("Gift cards are non-refundable")
        # Breaks substitutability — any code using PaymentProcessor expects refund to work

# Fix: Separate the contract
class Refundable(ABC):
    @abstractmethod
    def refund(self, transaction_id): ...

class PaymentProcessor(ABC):
    @abstractmethod
    def process_payment(self, amount): ...

class StripeProcessor(PaymentProcessor, Refundable): ...   # implements both
class GiftCardProcessor(PaymentProcessor): ...             # No refund
```

---

## When to Use in Interviews

- Classic interview trap: Square/Rectangle. Mention it proactively and explain WHY it violates LSP.
- When designing inheritance hierarchies: "Before extending, I ask — does the subclass truly fulfill the parent's entire contract? If any method has to throw `UnsupportedOperationException`, that's a red flag."
- When reviewing existing code: "I look for overrides that weaken preconditions or strengthen postconditions — both are LSP violations."

---

## Common Violations

| Violation | Symptom | Fix |
|---|---|---|
| `UnsupportedOperationException` in override | Subclass can't honor parent's method | Separate into distinct interface |
| Override changes behavior silently | Square's `setWidth` also changes height | Don't extend; use composition or common interface |
| Subclass narrows accepted input | Override only accepts positive numbers when parent accepts all | Keep preconditions the same or weaker |
| Subclass weakens output guarantee | Override can return null when parent promised non-null | Keep postconditions the same or stronger |

---

## Interview Tips

**Q: "Give an example of LSP violation"**
- "Square extending Rectangle. Mathematically, a square is a rectangle. But in code, if you call `setWidth(5)` on a Rectangle, you expect height to be unchanged. A Square overrides this and changes both — breaking the caller's valid assumption."

**Q: "How do you detect LSP violations in code reviews?"**
- "Look for `UnsupportedOperationException` in overrides, or overrides that throw when the parent wouldn't. Also look for precondition strengthening — a subclass that rejects inputs the parent would accept."

---

## Interviewer Follow-Up Questions

- "What does LSP actually say? Give the formal definition, then give an intuitive one." → Formal: if S is a subtype of T, then objects of type T may be replaced with objects of type S without altering the correctness of the program. Intuitive: a subclass must be usable wherever the parent class is expected, without surprises. Code that accepts a `Bird` and calls `.fly()` must work correctly with any `Bird` subclass — including `Penguin`. If `Penguin.fly()` throws an exception, LSP is violated.
- "How is LSP different from just 'method overriding'?" → Method overriding is a mechanism; LSP is a correctness constraint on how you use it. You can override without violating LSP (e.g., `Dog.speak()` barks, `Cat.speak()` meows — both make sound, just different sounds). You can violate LSP without overriding (by strengthening preconditions — requiring more than the parent, or weakening postconditions — guaranteeing less than the parent). LSP is about behavioral compatibility, not syntactic compatibility.
- "Give a real-world example of an LSP violation you might encounter." → `ReadOnlyList extends ArrayList`. ArrayList has `add()`, `remove()` methods. `ReadOnlyList` overrides them to throw `UnsupportedOperationException`. Code that accepts a `List` and calls `list.add(item)` — valid for `ArrayList`, throws for `ReadOnlyList`. The type contract is broken. Fix: `ReadOnlyList` should implement a `ReadableList` interface (no `add`/`remove`), not extend `ArrayList`. This is also why Java's `Collections.unmodifiableList()` returns a `List` that throws on mutation — a known LSP violation accepted as a pragmatic trade-off.
- "How does LSP relate to preconditions and postconditions?" → Contravariance rule: subclasses may only weaken preconditions (accept more inputs, never fewer). Covariance rule: subclasses may only strengthen postconditions (guarantee more, never less). A parent method `void save(File f)` that accepts any non-null File — a subclass that rejects read-only files has strengthened the precondition (violation). A parent method that guarantees returning a non-null list — a subclass that may return null has weakened the postcondition (violation).
