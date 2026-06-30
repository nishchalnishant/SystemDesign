---
module: 06-lld
topic: Design Patterns
subtopic: Creational
status: unread
tags: [06-lld, system-design, design-patterns]
---
# Prototype Pattern

## Question

Your system sends marketing emails. Each email has a fixed structure (HTML template, headers, sender info) but a different recipient and a small personalization block. You need to create 10,000 `EmailTemplate` objects per campaign. Write the construction code.

Try it before reading on.

---

## Pattern Mindmap

```
[Prototype Pattern]
├── Problem It Solves
│   ├── 10,000 EmailTemplate objects each loading 50KB HTML from disk + DB
│   ├── Construction is expensive: I/O, parsing, network calls
│   └── Objects differ only in recipient + small personalization block
├── Core Structure
│   ├── Cloneable interface with clone() method
│   ├── EmailTemplate.clone() copies expensive fields from prototype
│   ├── Client mutates only the variable fields on the clone
│   └── Prototype registry: map of named templates to pre-built instances
├── Deep vs Shallow Clone
│   ├── Shallow clone: nested objects are shared (mutable references copied)
│   ├── Deep clone: nested objects are also duplicated (safe mutation)
│   └── Rule: if cloned object will mutate nested state, must deep clone
├── EmailTemplate Registry
│   ├── EmailTemplateRegistry.get("WELCOME") → clone of pre-built prototype
│   ├── Pre-built once at startup; each clone is O(field copy) not O(I/O)
│   └── Register new templates without changing client code
├── Analogy
│   ├── Word document template: create template once, duplicate for each doc
│   └── Cell mitosis: new cell is a copy of parent, then specializes
├── When to Use
│   ├── Object creation is expensive (I/O, network, parsing)
│   ├── Objects are structurally similar with small variations
│   └── Need many copies quickly (bulk campaign, game entities)
├── When NOT to Use
│   ├── Object creation is cheap (simple constructor) — just use new
│   └── Deep clone is complex and error-prone — consider factory instead
├── Trade-offs
│   ├── Deep cloning complex object graphs is tricky
│   ├── Circular references in object graph can break naive clone
│   └── Registry adds indirection but simplifies client code significantly
└── Interview Angles
    ├── When would you choose Prototype over Factory?
    ├── What is the difference between deep and shallow clone?
    └── How does a prototype registry work?
```

## Problem Without the Pattern

The obvious approach: construct each one from scratch.

```python
class EmailTemplate:
    def __init__(self, recipient: str, personalization: str):
        self.html_body       = FileLoader.load("template.html")   # disk I/O
        self.headers         = ConfigParser.parse("email.cfg")    # file parse
        self.sender_info     = DB.query("SELECT sender FROM config")  # DB call
        self.recipient       = recipient
        self.personalization = personalization

# Sending to 10,000 users:
for user in users:
    t = EmailTemplate(user, f"Hi {user}")  # 10,000 disk + DB calls
```

**What breaks**:
1. **Performance**: 10,000 disk reads and DB queries for data that never changes between emails.
2. **Initialization cost is constant but paid every time**: The heavy work (loading HTML, parsing config) is identical for every instance.
3. **No sharing of invariant state**: Each of 10,000 instances holds its own copy of the same 50KB HTML.

---

## Derive the Minimal Fix

The constraint: **do the expensive initialization once, then copy the result**.

Step 1 — do the expensive initialization once and store it as a prototype:
```python
prototype = EmailTemplate()  # one disk read, one DB call
```

Step 2 — for each new instance, clone the prototype and only set what differs:
```python
import copy

for_alice = copy.copy(prototype)
for_alice.recipient       = "alice@example.com"
for_alice.personalization = "Hi Alice"
```

Step 3 — the `clone()` implementation must decide: shallow copy (share the 50KB HTML reference — fine if it's immutable) or deep copy (create independent mutable copies):
```python
import copy

class EmailTemplate:
    def clone(self):
        new_obj = copy.copy(self)           # shallow copy primitives + strings
        new_obj.headers = dict(self.headers)  # deep copy mutable field
        return new_obj
```

Now 10,000 emails = 1 expensive initialization + 10,000 cheap clones. That's the pattern.

---

> **Purpose**: Create new objects by cloning an existing object (the prototype) instead of constructing from scratch. Efficient when initialization is complex or costly.

> **Analogy**: A Word document template. Instead of creating every new quarterly report from scratch — formatting, headers, fonts, structure — you clone the template and fill in the specific numbers. Same structure, different content.

---

## The Core Idea

When object creation is expensive (heavy DB lookups, complex configuration, deep initialization), creating from scratch every time is wasteful. Instead, keep a well-configured prototype and clone it. Modify the clone, not the original.

**Key question**: Is this object expensive to create, and do many instances share the same base structure with small variations? If yes, Prototype fits.

---

## The Problem: Repetitive Instantiation

```python
import copy
from abc import ABC, abstractmethod

# Bad: Every email is created from scratch
class EmailTemplate(ABC):
    @abstractmethod
    def set_content(self, content: str): ...

    @abstractmethod
    def send(self, to: str): ...

class WelcomeEmail(EmailTemplate):
    def __init__(self):
        self.subject = "Welcome to TUF+"
        self.content = "Hi there! Thanks for joining us."
        # Imagine this also loads config, validates templates, sets up formatting — expensive

    def set_content(self, content: str):
        self.content = content

    def send(self, to: str):
        print(f"Sending to {to}: [{self.subject}] {self.content}")

if __name__ == "__main__":
    # Creating a new instance every time — redundant re-initialization
    email1 = WelcomeEmail()
    email1.send("user1@example.com")

    email2 = WelcomeEmail()  # Same expensive constructor again
    email2.set_content("Hi there! Welcome to TUF Premium.")
    email2.send("user2@example.com")

    email3 = WelcomeEmail()  # And again
    email3.set_content("Thanks for signing up. Let's get started!")
    email3.send("user3@example.com")
```

**Issues:**
- Tight coupling to concrete class — `WelcomeEmail()` everywhere
- Repetitive re-initialization for mostly-identical objects
- Violates DRY — same base setup repeated for every instance
- No reuse of a pre-configured base object

---

## The Solution: Prototype Pattern

```python
import copy
from abc import ABC, abstractmethod
from typing import Dict

# 1. Prototype interface
class EmailTemplateProto(ABC):
    @abstractmethod
    def clone(self) -> "EmailTemplateProto": ...

# 2. Base class with clone support
class EmailTemplate(EmailTemplateProto):
    def __init__(self):
        self.subject: str = ""
        self.content: str = ""

    def clone(self) -> "EmailTemplate":
        return copy.copy(self)  # Shallow copy

    def set_content(self, content: str):
        self.content = content

    def send(self, to: str):
        print(f"Sending to {to}: [{self.subject}] {self.content}")

# 3. Concrete prototypes — expensive setup happens ONCE in the constructor
class WelcomeEmail(EmailTemplate):
    def __init__(self):
        super().__init__()
        self.subject = "Welcome to TUF+"
        self.content = "Hi there! Thanks for joining us."
        # Expensive initialization happens only once here

class DiscountEmail(EmailTemplate):
    def __init__(self):
        super().__init__()
        self.subject = "Special Offer — 30% Off!"
        self.content = "Hi! Use code SAVE30 at checkout."

# 4. Registry — stores and serves clones of pre-configured prototypes
class EmailTemplateRegistry:
    _templates: Dict[str, EmailTemplate] = {
        "welcome":  WelcomeEmail(),   # Created ONCE
        "discount": DiscountEmail(),  # Created ONCE
    }

    @classmethod
    def get_template(cls, type: str) -> EmailTemplate:
        template = cls._templates.get(type)
        if template is None:
            raise ValueError(f"Unknown template: {type}")
        return template.clone()  # Return a clone, never the original

# 5. Client — clones templates, modifies only what's needed
if __name__ == "__main__":
    email1 = EmailTemplateRegistry.get_template("welcome")
    email1.set_content("Hi Alice, welcome to TUF Premium!")
    email1.send("alice@example.com")

    email2 = EmailTemplateRegistry.get_template("welcome")
    email2.set_content("Hi Bob, thanks for joining!")
    email2.send("bob@example.com")

    # Original prototype is unchanged — both emails were cloned independently
    discount = EmailTemplateRegistry.get_template("discount")
    discount.send("carol@example.com")  # Use as-is
```

### Class Diagram

```mermaid
classDiagram
    class Cloneable {
        <<interface>>
        +clone() EmailTemplate
    }

    class EmailTemplate {
        #String subject
        #String content
        +clone() EmailTemplate
        +setContent(String content)
        +send(String to)
    }

    class WelcomeEmail {
        +WelcomeEmail()
    }

    class DiscountEmail {
        +DiscountEmail()
    }

    class EmailTemplateRegistry {
        -Map~String, EmailTemplate~ templates
        +getTemplate(String type)$ EmailTemplate
    }

    class Main {
        +main(String[] args)
    }

    Cloneable <|.. EmailTemplate
    EmailTemplate <|-- WelcomeEmail
    EmailTemplate <|-- DiscountEmail
    EmailTemplateRegistry o-- EmailTemplate : stores
    Main ..> EmailTemplateRegistry : uses
    Main ..> EmailTemplate : uses
```

---

## Deep Cloning vs Shallow Cloning

**Shallow Clone**: Copies primitive fields by value; copies object references (not the objects themselves). Changes to nested objects in the clone affect the original.

**Deep Clone**: Copies everything recursively. Each clone is fully independent.

```python
import copy

# Deep clone example — for objects with nested mutable state
class EmailTemplate:
    def __init__(self):
        self.subject     = ""
        self.attachments = []  # Mutable nested object

    def clone(self) -> "EmailTemplate":
        new_obj = copy.copy(self)
        new_obj.attachments = list(self.attachments)  # Deep copy the list
        return new_obj

    # For arbitrarily nested mutable objects, use copy.deepcopy:
    def deep_clone(self) -> "EmailTemplate":
        return copy.deepcopy(self)
```

Use deep cloning when the prototype contains mutable nested objects. Otherwise, clones share state and modifying one affects all others.

---

## When to Use in Interviews

- When object initialization is expensive and many instances share the same structure: "I'd use Prototype — initialize once, clone many times."
- When building a game with many similar entities (enemies, tiles): "Each enemy type is a prototype; spawning clones the prototype instead of re-creating from scratch."
- When describing configuration objects: "Configuration is loaded once from file/DB and stored as a prototype. New service instances clone the config."

---

## Common Violations

| Violation | Symptom | Fix |
|---|---|---|
| Modifying original prototype | `get_template()` returns original, not clone | Always return a clone from the registry |
| Shallow copy of mutable fields | Clone shares `list` with original; modifications bleed through | Deep copy all mutable nested objects |
| No registry | `WelcomeEmail()` everywhere despite expensive init | Centralize in a `TemplateRegistry` |
| Using Prototype for cheap objects | Simple data class with 2 fields uses clone | Just use a constructor — no need for Prototype |

---

## Pros and Cons

**Pros:**
- Faster object creation — no need to reinitialize from scratch
- Reduces subclassing — no need to create subclasses for each variation
- Runtime flexibility — modify a clone on the fly without touching the prototype
- Clean decoupling — client doesn't depend on how `WelcomeEmail` is constructed

**Cons:**
- Deep cloning can be complex — circular references are hard to handle
- Easy to forget to deep copy mutable fields — subtle bugs
- Potential confusion if the clone/original distinction isn't clear

---

## Interview Tips

**Q: "When would you use Prototype over just calling the constructor?"**
- "When object creation is expensive — like loading a template from DB, parsing a config file, or setting up a complex graph. You pay that cost once, then clone cheaply. Prototypes are also useful when you need many similar objects with slight variations."

**Q: "What's the risk of Prototype Pattern?"**
- "Shallow cloning. If the prototype has mutable nested objects (lists, dicts, other entities), the clone shares those references. Modifying the clone's nested state corrupts the prototype. Always deep copy mutable fields."
