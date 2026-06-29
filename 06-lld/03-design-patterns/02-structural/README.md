# Structural Design Patterns

Structural patterns explain how objects are assembled, wrapped, adapted, or exposed through simpler interfaces.

## Patterns in This Folder

| Pattern | File | Use when |
|---------|------|----------|
| Adapter | [adapter-pattern.md](adapter-pattern.md) | your system expects one interface but an external class provides another |
| Composite | [composite-pattern.md](composite-pattern.md) | leaf and group objects should be treated uniformly in a tree |
| Decorator | [decorator-pattern.md](decorator-pattern.md) | behavior combinations would cause subclass explosion |
| Facade | [facade-pattern.md](facade-pattern.md) | a complex subsystem needs one simple entry point |
| Proxy | [proxy-pattern.md](proxy-pattern.md) | access should be controlled, delayed, cached, or logged |

## Interview Tips

- Composite is for trees: comments, folders, coupon rule groups.
- Decorator is for runtime add-ons: toppings, log sinks, rate-limit wrappers.
- Adapter changes an interface; Proxy controls access to the same interface.
- Facade simplifies a subsystem but does not usually add new behavior.
