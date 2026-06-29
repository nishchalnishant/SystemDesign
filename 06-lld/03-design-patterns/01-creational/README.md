# Creational Design Patterns

Creational patterns control object creation so clients do not depend on concrete constructors.

## Patterns in This Folder

| Pattern | File | Use when |
|---------|------|----------|
| Singleton | [singleton.md](singleton.md) | one shared instance is needed, such as a logger or coordinator |
| Factory | [factory-pattern.md](factory-pattern.md) | object type is chosen from input or configuration |
| Builder | [builder-pattern.md](builder-pattern.md) | object construction has many optional fields or validation steps |

## Interview Tips

- Use Factory when a `switch` or `if/else` chain creates different subclasses.
- Use Builder when constructors become unreadable because of many optional parameters.
- Use Singleton sparingly; mention testability and dependency-injection trade-offs.
