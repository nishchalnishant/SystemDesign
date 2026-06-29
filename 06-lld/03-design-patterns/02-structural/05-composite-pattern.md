---
module: 06-lld
topic: Design Patterns
subtopic: Structural
status: unread
tags: [06-lld, system-design, design-patterns]
---
# Composite Pattern

## Question

You are designing a file system. A `Directory` can contain `File` objects and other `Directory` objects. The client wants to call `get_size()` on either a single file or an entire directory tree. How do you avoid separate code paths for leaf and group objects?

---

## Pattern Mindmap

```
[Composite Pattern]
├── Problem It Solves
│   ├── Client has to treat File and Directory differently
│   ├── Recursive tree logic leaks into client code
│   └── Adding a new node type forces many if/else checks
├── Core Structure
│   ├── Component interface: common operations
│   ├── Leaf: single object with no children
│   ├── Composite: object that contains children
│   └── Client depends only on Component
├── Key Property
│   ├── Leaf and Composite share the same interface
│   ├── Composite delegates operation to children recursively
│   └── Client can call the same method on a part or whole tree
├── Real Interview Uses
│   ├── File system: File and Directory
│   ├── Comment tree: Comment with nested replies
│   ├── Coupon rules: single rule and AND/OR rule group
│   ├── Organization chart: Employee and Manager
│   └── UI tree: Button, Panel, Window
└── Interview Angles
    ├── Composite vs Decorator
    ├── How do you avoid cycles?
    └── How do you support add/remove only on composite nodes?
```

---

## Problem Without the Pattern

```python
def total_size(node):
    if isinstance(node, File):
        return node.size
    if isinstance(node, Directory):
        total = 0
        for child in node.children:
            total += total_size(child)
        return total
```

This works, but the client owns the tree traversal and type checks. Every new operation repeats the same recursion. Every new node type risks touching every traversal.

---

## Derive the Fix

Define a shared component contract. A `File` answers directly. A `Directory` asks its children and combines the result.

```python
from abc import ABC, abstractmethod

class FileSystemNode(ABC):
    @abstractmethod
    def get_name(self): ...

    @abstractmethod
    def get_size(self): ...

class File(FileSystemNode):
    def __init__(self, name, size):
        self._name = name
        self._size = size

    def get_name(self):
        return self._name

    def get_size(self):
        return self._size

class Directory(FileSystemNode):
    def __init__(self, name):
        self._name = name
        self._children = []  # list of FileSystemNode

    def add(self, child):
        self._children.append(child)

    def remove(self, child):
        self._children.remove(child)

    def get_name(self):
        return self._name

    def get_size(self):
        # recursively sum size of all children
        return sum(child.get_size() for child in self._children)
```

Client code:

```python
root = Directory("root")
root.add(File("resume.pdf", 100))

photos = Directory("photos")
photos.add(File("a.jpg", 200))
photos.add(File("b.jpg", 300))

root.add(photos)

print(root.get_size())  # 600
```

The client does not care whether `root` contains files, directories, or future node types. It calls `get_size()` on the component.

---

## Coupon System Example

Composite is especially useful when a rule can be simple or nested.

```python
class CouponRule(ABC):
    @abstractmethod
    def is_satisfied(self, cart): ...

class MinCartValueRule(CouponRule):
    def __init__(self, min_value):
        self._min_value = min_value

    def is_satisfied(self, cart):
        return cart.total >= self._min_value

class AndRule(CouponRule):
    def __init__(self, rules):
        self._rules = rules  # list of CouponRule

    def is_satisfied(self, cart):
        return all(rule.is_satisfied(cart) for rule in self._rules)

class OrRule(CouponRule):
    def __init__(self, rules):
        self._rules = rules  # list of CouponRule

    def is_satisfied(self, cart):
        return any(rule.is_satisfied(cart) for rule in self._rules)
```

Now a coupon can have one condition or a nested expression:

```
AND(
  min_cart_value >= 1000,
  OR(user_is_prime, category == "electronics")
)
```

---

## When to Use

Use Composite when:
- the domain is naturally a tree;
- leaf and group objects should support the same operation;
- client code should not know whether it is dealing with one object or many;
- recursive behavior should live inside the object model.

Avoid Composite when:
- the structure is flat;
- leaf and group behavior are genuinely different;
- exposing child management methods on the base interface would be misleading.

---

## Interview Tips

**Q: Composite vs Decorator?**  
Composite represents part-whole tree structure. Decorator wraps one object to add behavior. Composite is about hierarchy; Decorator is about dynamic extension.

**Q: Should `add()` be on the component interface?**  
Usually no. Keep `add()` only on composite classes like `Directory`. A `File.add()` method would violate Interface Segregation because files cannot have children.

**Q: How do you prevent cycles?**  
Track parent references or visited node IDs. Reject adding an ancestor as a child.


---

## Interviewer Follow-Up Questions

- "What problem does Composite solve? Give a use case." → Treating individual objects and collections of objects uniformly. Use case: a file system. `File` and `Directory` both implement `FileSystemNode`. `Directory.size()` sums the sizes of all children recursively; `File.size()` returns its own size. The caller does `root.size()` without knowing if root is a file or directory — the recursion is handled by the nodes themselves. Other examples: UI component trees (widget contains widgets), DOM tree, arithmetic expression trees.
- "How does Composite use recursion? What's the risk?" → `Directory.size() = sum(child.size() for child in children)`. Each child may itself be a `Directory` that recurses further. This is tree traversal via polymorphism — clean and elegant. Risk: infinite recursion if the tree has cycles (a directory containing itself, which is possible in filesystems via symlinks). Protection: visited-node set to detect cycles, or depth limit.
- "What's the difference between Composite and Decorator?" → Both wrap objects of the same interface. Composite: aggregates multiple children to form a tree structure. The composite's behavior is derived from all its children (e.g., `size = sum(children.size())`). Decorator: wraps exactly one object to add behavior. The decorator doesn't aggregate — it augments. Tree vs. chain.
- "How would you design a discount system where a cart can have item-level discounts and cart-level discounts?" → Composite: `Discount` interface with `apply(price)`. `PercentDiscount` and `FixedDiscount` are leaf nodes. `CompositeDiscount` holds a list of `Discount` objects and applies all of them (sum or compound). A `CartDiscount` is a `CompositeDiscount` that contains item discounts + a cart-level discount. Callers just call `discount.apply(total)` — don't need to know the structure.
