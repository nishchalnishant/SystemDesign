# Design Patterns

Design patterns are reusable fixes for common class-design problems. For SDE-2 LLD, the important part is not naming a pattern; it is explaining what failure mode the pattern avoids.

---

## Categories

| Category | Folder | Use when |
|----------|--------|----------|
| Creational | [01-creational](01-creational/README.md) | object creation varies or is complex |
| Structural | [02-structural](02-structural/README.md) | objects need to be composed, wrapped, adapted, or simplified |
| Behavioral | [03-behavioral](03-behavioral/README.md) | algorithms, state transitions, events, or request handling vary |

---

## SDE-2 Pattern Map

| Pattern | File | Use when | Common problem |
|---------|------|----------|----------------|
| Singleton | [singleton.md](01-creational/singleton.md) | one shared coordinator or logger | Parking Lot, Logger |
| Factory | [factory-pattern.md](01-creational/factory-pattern.md) | create object by type | Parking Lot, Hotel |
| Builder | [builder-pattern.md](01-creational/builder-pattern.md) | many optional construction fields | Coupon System |
| Adapter | [adapter-pattern.md](02-structural/adapter-pattern.md) | integrate incompatible interface | Payment gateway wrapper |
| Composite | [composite-pattern.md](02-structural/composite-pattern.md) | tree structure | Comment System, Coupon System |
| Decorator | [decorator-pattern.md](02-structural/decorator-pattern.md) | add behavior dynamically | Rate Limiter, Logger |
| Facade | [facade-pattern.md](02-structural/facade-pattern.md) | simplify a subsystem | checkout orchestration |
| Proxy | [proxy-pattern.md](02-structural/proxy-pattern.md) | control access/lazy loading | image/file/object access |
| Strategy | [strategy-pattern.md](03-behavioral/strategy-pattern.md) | swap algorithms | pricing, assignment, splitting |
| State | [state-pattern.md](03-behavioral/state-pattern.md) | behavior changes by lifecycle state | Vending Machine, ATM |
| Observer | [observer-pattern.md](03-behavioral/observer-pattern.md) | one event triggers many consumers | notifications |
| Command | [command-pattern.md](03-behavioral/command-pattern.md) | operation as object | undo/replay/game move |
| Chain of Responsibility | [chain-of-responsibility-pattern.md](03-behavioral/chain-of-responsibility-pattern.md) | ordered handler pipeline | Logger, ATM, coupon validation |
| Template Method | [template-method-pattern.md](03-behavioral/template-method-pattern.md) | fixed algorithm skeleton, variable steps | parsers, notification templates |

---

## Interview Rule

For every pattern you use, say:

```
Without this pattern, X breaks or becomes hard to extend.
With this pattern, adding Y means adding a class, not rewriting core logic.
```

Example:

```
I use Strategy for pricing because adding weekend pricing should add WeekendPricingStrategy, not modify a switch inside BookingService.
```
