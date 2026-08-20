# SDE-3 Interview Relevance

Ranking of all 23 GoF patterns by how often they actually surface in SDE-3-level interviews — LLD rounds, system-design rounds, and "walk me through your design" follow-ups. Tier 1 = come prepared to design and code these cold. Tier 2 = recognize and use when the problem calls for it. Tier 3 = know the definition; rarely the point of the question.

## Tier 1 — Design and code these cold

| Pattern | Why it shows up at SDE-3 |
|---|---|
| [Strategy](06-behavioral/08-strategy.md) | Default answer to "how do you make this pricing/sorting/routing logic pluggable without a giant if/else." Extremely common in LLD problems (parking fee calculators, ride pricing, payment processing). |
| [Factory Method](04-creational/01-factory-method.md) | Comes up any time object creation needs to vary by type — notification systems, shape/vehicle factories, connector creation. Baseline expectation. |
| [Observer](06-behavioral/06-observer.md) | Core of any pub/sub, event system, or "notify dependents on state change" design — stock tickers, chat systems, order-status updates. Also the conceptual basis for webhooks/event buses discussed at the system-design level. |
| [Decorator](05-structural/04-decorator.md) | Classic for "add optional behaviors/pricing add-ons without subclass explosion" (coffee shop, pizza toppings, middleware/interceptor chains). Middleware chains in real backend systems are literally this pattern. |
| [Singleton](04-creational/05-singleton.md) | Comes up constantly (config managers, connection pools, logger) — interviewers also probe here on thread-safety (double-checked locking, enum singleton), which is the real SDE-3 differentiator, not the pattern itself. |
| [Builder](04-creational/03-builder.md) | Expected whenever an object has many optional fields — HTTP request builders, immutable domain objects. Also a strong signal of coding maturity (fluent APIs, immutability). |
| [Facade](05-structural/05-facade.md) | Comes up implicitly in almost every LLD problem when wiring subsystems together (e.g., a `BookingService` facade over payment/inventory/notification). Interviewers rarely name it, but expect you to reach for it. |
| [Adapter](05-structural/01-adapter.md) | Common in "integrate with a third-party/legacy API" framing — payment gateway integration, legacy system wrapping. Frequently asked directly. |

## Tier 2 — Recognize and apply when the problem calls for it

| Pattern | Why it's second-tier |
|---|---|
| [State](06-behavioral/07-state.md) | Shows up in workflow/status-machine LLDs (order lifecycle, vending machine, traffic light, elevator) — common but more niche than Strategy. |
| [Command](06-behavioral/02-command.md) | Useful for undo/redo, job queues, task scheduling designs — comes up when the problem explicitly needs deferred/queued execution. |
| [Chain of Responsibility](06-behavioral/01-chain-of-responsibility.md) | Natural fit for validation pipelines, middleware, approval workflows — asked when a request needs to pass through ordered handlers. |
| [Composite](05-structural/03-composite.md) | Comes up for hierarchical/tree structures (file systems, org charts, UI component trees) — solid but problem-specific. |
| [Proxy](05-structural/07-proxy.md) | Relevant for caching, lazy loading, access control layers — often discussed conceptually in system design (reverse proxies, API gateways) more than coded in LLD. |
| [Template Method](06-behavioral/09-template-method.md) | Comes up in "shared algorithm skeleton, varying steps" designs (report generators, data pipelines, test harnesses) — useful but inheritance-based, so less favored than Strategy in modern interviews. |
| [Abstract Factory](04-creational/02-abstract-factory.md) | Occasionally asked for cross-platform/theme/family-of-related-objects problems (UI toolkits) — real but less common than plain Factory Method. |
| [Mediator](06-behavioral/04-mediator.md) | Comes up in chat rooms, air traffic control, or "many objects need to talk without direct coupling" problems — a recognizable but less-requested design. |

## Tier 3 — Know the definition; rarely the crux of the question

| Pattern | Why it's lower priority |
|---|---|
| [Iterator](06-behavioral/03-iterator.md) | Mostly language-provided (Java's `Iterable`/`Iterator`) — asked about conceptually, almost never designed from scratch. |
| [Bridge](05-structural/02-bridge.md) | Conceptually important (decoupling abstraction from implementation) but rarely the explicit ask; mostly shows up as a "how is this different from Adapter/Strategy" follow-up. |
| [Prototype](04-creational/04-prototype.md) | Comes up for expensive-to-construct object cloning, but infrequent as a primary interview topic. |
| [Flyweight](05-structural/06-flyweight.md) | Niche — memory-optimization framing (text editors, game object pooling); occasionally referenced in system-design answers about caching shared state, rarely a dedicated LLD question. |
| [Memento](06-behavioral/05-memento.md) | Undo/snapshot use cases exist but are usually solved with simpler state-copying in interviews rather than the full pattern. |
| [Visitor](06-behavioral/10-visitor.md) | Conceptually advanced (double dispatch) but rarely expected at SDE-3; more common in senior/staff-level compiler/AST-style discussions. |

## Interviewer's real focus at SDE-3

The pattern name matters less than: (1) recognizing *when* a pattern applies from a vague prompt, (2) justifying the tradeoff against simpler alternatives (why not just an if/else, why not a hardcoded singleton), and (3) combining 2-3 patterns naturally in one design (e.g., Factory + Strategy + Observer in an order-processing system) rather than reciting one in isolation.
