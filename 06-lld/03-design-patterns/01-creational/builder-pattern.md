# Builder Pattern

## Question

You are building a `User` object that has: `name` (required), `email` (required), `age` (optional), `address` (optional), `phoneNumber` (optional), `profilePicture` (optional). Write the constructor for this class.

Try it before reading on.

---

## Pattern Mindmap

```
[Builder Pattern]
├── Problem It Solves
│   ├── Telescoping constructor: User(name, email, age, address, phone, pic)
│   ├── Caller must pass null for optional fields — unreadable, error-prone
│   ├── Setter approach: object is invalid between first and last setter call
│   └── Need to create immutable objects with many optional fields
├── Core Structure
│   ├── Outer class: final fields, private constructor taking Builder
│   ├── Static inner Builder class with same fields
│   ├── Builder.name(), Builder.email() — fluent setters returning this
│   ├── Builder.build() validates and constructs the outer object
│   └── Client: new User.Builder("name","email").age(30).build()
├── BurgerMeal Example
│   ├── BurgerMeal.BurgerBuilder with required + optional fields
│   ├── size(s), sauce(s), cheese(b), lettuce(b) fluent methods
│   └── build() creates immutable BurgerMeal
├── Real-World Usage
│   ├── OkHttp: Request.Builder().url().method().build()
│   ├── Lombok @Builder annotation generates builder automatically
│   └── StringBuilder is a mutable builder for String
├── When to Use
│   ├── Object has 4+ fields, several optional
│   ├── Object must be immutable (final fields, no setters)
│   └── Construction requires validation of field combinations
├── When NOT to Use
│   ├── Object has 2–3 fields — constructor or factory is simpler
│   └── Mutability is acceptable and setters work fine
├── Trade-offs
│   ├── Verbose: Builder has same fields as the object (duplication)
│   ├── Lombok @Builder eliminates boilerplate automatically
│   └── Harder to extend: subclass needs its own builder
└── Interview Angles
    ├── What is the telescoping constructor anti-pattern?
    ├── How does Builder enforce immutability?
    └── Difference between Builder and Factory?
```

## Problem Without the Pattern

The first approach — a constructor with all fields:

```java
class User {
    public User(String name, String email, int age, 
                String address, String phone, String picture) { ... }
}

// Caller:
User u = new User("Alice", "alice@x.com", 0, null, null, null);
//                                         ^^  ^^^^  ^^^^  ^^^^
//                  what do these nulls mean? which is phone vs address?
```

Or the telescoping constructor approach:
```java
User u1 = new User("Alice", "alice@x.com");
User u2 = new User("Alice", "alice@x.com", 30);
User u3 = new User("Alice", "alice@x.com", 30, "123 Main St");
// Need a separate constructor for every combination of optional fields
```

**What breaks**:
1. **Unreadable callsites**: `new User("Alice", "x@x.com", 0, null, null, null)` — what is the 5th `null`?
2. **Constructor explosion**: N optional fields → up to 2^N combinations you might need to support.
3. **Invalid state**: Nothing stops `new User(null, null, -5, ...)` — the object is invalid from birth.
4. **No immutability**: Setters let callers mutate after construction.

---

## Derive the Minimal Fix

The constraint: **separate the step-by-step configuration from the final construction**.

Step 1 — make the outer class constructor private, require only mandatory fields:
```java
class User {
    private final String name;   // required
    private final String email;  // required
    private final int age;       // optional
    private final String phone;  // optional

    private User(Builder b) {
        this.name  = b.name;
        this.email = b.email;
        this.age   = b.age;
        this.phone = b.phone;
    }
}
```

Step 2 — inner `Builder` class holds the state during configuration:
```java
static class Builder {
    private final String name;   // required → constructor param
    private final String email;  // required → constructor param
    private int age;
    private String phone;

    public Builder(String name, String email) {
        this.name = name; this.email = email;
    }

    public Builder age(int a)      { this.age = a; return this; }
    public Builder phone(String p) { this.phone = p; return this; }

    public User build() { return new User(this); }
}
```

Step 3 — callsite is now readable and validated:
```java
User u = new User.Builder("Alice", "alice@x.com")
    .age(30)
    .phone("555-1234")
    .build();
```

That's the entire pattern — a fluent inner builder that returns `this` for chaining, and a `build()` that calls the private outer constructor.

---

> **Purpose**: Separates the construction of a complex object from its representation, allowing step-by-step creation with full control over which parts are set.

> **Analogy**: Building a custom PC. You specify: CPU=i9, RAM=32GB, Storage=1TB NVMe. The builder assembles it step by step. You don't call `new Computer(i9, 32, 1000, true, false, null, ...)` and try to remember what the 7th argument means.

---

## The Problem: Telescoping Constructors

When an object has many optional fields, you end up with either:
1. A constructor with too many parameters (unreadable, error-prone)
2. Multiple overloaded constructors that grow out of control

```java
// Telescoping Constructor Anti-Pattern
class BurgerMeal {
    public BurgerMeal(String bun, String patty) { ... }
    public BurgerMeal(String bun, String patty, boolean cheese) { ... }
    public BurgerMeal(String bun, String patty, boolean cheese, String side) { ... }
    public BurgerMeal(String bun, String patty, boolean cheese, String side, String drink) { ... }
}

// Usage is confusing — what does 'true' mean here?
BurgerMeal meal = new BurgerMeal("wheat", "veg", null, null, false);
```

**Issues:**
- Hard to read: you must remember parameter order and types
- Unnecessary `null` values for optional fields
- Risk of `NullPointerException` if internals don't null-check
- Adding a new optional field means adding more constructor overloads
- No flexibility to set values step by step

---

## The Solution: Builder Pattern

```java
import java.util.List;
import java.util.ArrayList;

public class BurgerMeal {
    // Required components
    private final String bunType;
    private final String patty;

    // Optional components
    private final boolean hasCheese;
    private final List<String> toppings;
    private final String side;
    private final String drink;

    // Private constructor — only the Builder can create BurgerMeal
    private BurgerMeal(BurgerBuilder builder) {
        this.bunType   = builder.bunType;
        this.patty     = builder.patty;
        this.hasCheese = builder.hasCheese;
        this.toppings  = builder.toppings;
        this.side      = builder.side;
        this.drink     = builder.drink;
    }

    // Static nested Builder class
    public static class BurgerBuilder {
        // Required fields
        private final String bunType;
        private final String patty;

        // Optional fields (with sane defaults)
        private boolean hasCheese = false;
        private List<String> toppings = new ArrayList<>();
        private String side = null;
        private String drink = null;

        // Builder constructor requires only mandatory fields
        public BurgerBuilder(String bunType, String patty) {
            this.bunType = bunType;
            this.patty   = patty;
        }

        public BurgerBuilder withCheese(boolean hasCheese) {
            this.hasCheese = hasCheese;
            return this;  // Returns builder for chaining
        }

        public BurgerBuilder withToppings(List<String> toppings) {
            this.toppings = toppings;
            return this;
        }

        public BurgerBuilder withSide(String side) {
            this.side = side;
            return this;
        }

        public BurgerBuilder withDrink(String drink) {
            this.drink = drink;
            return this;
        }

        public BurgerMeal build() {
            return new BurgerMeal(this);
        }
    }
}

// Usage — readable, flexible, no nulls
BurgerMeal plainBurger = new BurgerMeal.BurgerBuilder("wheat", "veg")
    .build();

BurgerMeal burgerWithCheese = new BurgerMeal.BurgerBuilder("wheat", "veg")
    .withCheese(true)
    .build();

List<String> toppings = List.of("lettuce", "onion", "jalapeno");
BurgerMeal loadedBurger = new BurgerMeal.BurgerBuilder("multigrain", "chicken")
    .withCheese(true)
    .withToppings(toppings)
    .withSide("fries")
    .withDrink("coke")
    .build();
```

### Class Diagram

```mermaid
classDiagram
    class BurgerMeal {
        -String bunType
        -String patty
        -boolean hasCheese
        -List~String~ toppings
        -String side
        -String drink
        -BurgerMeal(BurgerBuilder builder)
    }

    class BurgerBuilder {
        -String bunType
        -String patty
        -boolean hasCheese
        -List~String~ toppings
        -String side
        -String drink
        +BurgerBuilder(String bunType, String patty)
        +withCheese(boolean hasCheese) BurgerBuilder
        +withToppings(List~String~ toppings) BurgerBuilder
        +withSide(String side) BurgerBuilder
        +withDrink(String drink) BurgerBuilder
        +build() BurgerMeal
    }

    BurgerMeal *-- BurgerBuilder : contains
    BurgerBuilder ..> BurgerMeal : builds
```

---

## Why This is Better

| Aspect | Constructor Approach | Builder Pattern |
|---|---|---|
| Readability | Poor (nulls, long argument list) | Excellent (fluent, expressive) |
| Flexibility | Low (all-or-nothing setup) | High (configure only what's needed) |
| Maintainability | Hard to scale with more fields | Easy to extend with new options |
| Safety | High chance of errors with nulls | Controlled, safe instantiation |
| Immutability | Hard to enforce | Natural — build once, then freeze |

---

## Real-World Products Using Builder Pattern

**HTTP Request Building** (OkHttp, Retrofit):
```java
Request request = new Request.Builder()
    .url("https://api.example.com/users")
    .addHeader("Authorization", "Bearer " + token)
    .post(requestBody)
    .build();
```

**Amazon Cart Configuration**: Items in a cart have quantity, size, color, delivery option, gift wrap, discount tags — all optional. Builder lets each combination be expressed clearly without constructor explosion.

**StringBuilder** (Java standard library): Technically a builder — you chain `append()` calls and call `toString()` at the end.

---

## When to Use in Interviews

- When designing any object with 4+ optional fields: "I'd use Builder to avoid telescoping constructors and make creation readable."
- When the interviewer asks about immutable objects: "Builder lets you set fields step by step and then produce an immutable final object via `build()`."
- When discussing API design: "Fluent builder APIs (like OkHttp's `Request.Builder`) are the gold standard for developer experience when constructing complex objects."

---

## Common Violations

| Violation | Symptom | Fix |
|---|---|---|
| Builder for 2-field class | Over-engineering a simple object | Just use a constructor |
| Mutable `build()` target | Final object's fields can change after build | Make fields `final` in the target class |
| Builder without validation in `build()` | Invalid objects created (e.g., negative price) | Add validation inside `build()` before constructing |
| No required fields in builder constructor | Required fields are optional — allows incomplete objects | Put mandatory fields in the Builder constructor |

---

## When to Use and When to Avoid

**Use when:**
- Object has 4+ fields, especially many optional ones
- You want immutability — build once, don't mutate
- Readable object creation matters (public APIs, domain models)

**Avoid when:**
- Class has only 1-2 fields — a constructor is cleaner
- Object is mutable and simple — setters are fine
- The overhead of a separate Builder class isn't justified

---

## Interview Tips

**Q: "Builder vs Constructor — when do you choose Builder?"**
- "When a class has many optional parameters, constructors become unreadable and error-prone. Builder gives a fluent API and enforces which fields are required vs optional. I'd use Builder for any object with 4+ optional configuration fields."

**Q: "How do you enforce required fields in Builder?"**
- "Put required fields in the Builder's constructor. Optional fields have `with*()` methods. `build()` can also validate that constraints are met before constructing the final object."
