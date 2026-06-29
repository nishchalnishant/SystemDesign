---
module: 07-interview-templates
status: interview-ready
tags: [07-interview-templates, system-design, lld, cheat-sheet]
---
# LLD Cheat Sheet — Amazon SDE-2

Last-page review before a low-level design interview.

---

## 45-Minute Interview Flow

| Time | What to do | Output |
|---|---|---|
| 0–5 | Clarify scope | actors, use cases, what's in/out |
| 5–10 | Identify classes | entities, value objects, enums, services |
| 10–20 | Sketch class diagram | relationships, interfaces, chosen pattern |
| 20–30 | Walk the main flow | happy path + one failure path |
| 30–45 | Code the critical path | the method that does the core work |
| 45–50 | Edge cases + concurrency | invalid input, race conditions, extensions |

Say out loud at minute 5: "I'm going to identify the main entities, the services that orchestrate them, and the interfaces between them."

---

## Class Types at a Glance

| Type | Role | Example | Real-life anchor |
|---|---|---|---|
| **Entity** | owns domain state, has identity | `Order`, `Booking`, `User` | A parcel in the Amazon warehouse — it has a tracking ID and travels through states |
| **Value Object** | immutable, equality by value | `Money`, `DateRange`, `Address` | A ₹500 note — two notes with the same value are interchangeable; the note itself never changes denomination |
| **Enum** | closed set of states/types | `OrderStatus`, `PaymentMethod` | Traffic light — only RED, AMBER, GREEN; no new colours |
| **Service** | orchestrates use cases | `OrderService`, `BookingService` | A restaurant manager — coordinates kitchen, waiters, and billing without doing the cooking |
| **Repository** | persistence boundary | `OrderRepository` | Warehouse clerk — the only person allowed to touch the shelves; others place requests |
| **Factory** | creates by type/config | `VehicleFactory`, `NotificationFactory` | Car dealership: you say "SUV, diesel" — they hand you the right model without you knowing the assembly details |
| **Strategy** | swappable algorithm | `PricingStrategy`, `DiscountRule` | GPS navigation: same destination, choose Fastest / Shortest / Avoid Tolls — swap the routing strategy |
| **State** | encapsulates state-specific behavior | `IdleState`, `ProcessingState` | Vending machine: inserting a coin does something different depending on whether it's idle or already has money |
| **Adapter** | translates 3rd-party interface | `StripeAdapter`, `FedExAdapter` | Travel power adapter: your Indian plug → UK socket, same electricity, different connector |
| **Facade** | simplifies your own subsystem | `OrderFacade`, `CheckoutFacade` | Hotel concierge: one call to book a cab, restaurant, and spa — you don't call each department separately |

---

## OOP Quick Reference

| Concept | One-liner | Real-life anchor |
|---|---|---|
| Encapsulation | Private fields + public methods that enforce invariants | ATM hides internal cash count — you press "Withdraw ₹2000", not "set cash drawer to N-1" |
| Abstraction | Interface hides implementation — callers don't know which vendor | Pressing the accelerator — you don't know if it's petrol or electric under the hood |
| Polymorphism | `vehicle.start()` works for PetrolCar, ElectricCar, Truck — same call, right behaviour | Traffic light turns green — each driver responds their own way |
| Inheritance | Share code up the hierarchy; composition if behavior differs at runtime | PetrolCar / ElectricCar / Truck all inherit `brake()` from Vehicle — change ABS once, all pick it up |
| Composition (strong HAS-A) | Owner creates the part; part dies with owner | Car creates its own Engine in the factory — the engine has no life outside that car |
| Aggregation (weak HAS-A) | Owner receives the part; part lives independently | Uber Fleet holds Driver references — Alice can drive for Ola too; deleting the fleet doesn't fire Alice |
| Duck typing | Python checks method presence at runtime — implement `__iter__` to be iterable | A boarding pass and a loyalty card both have a QR code — the scanner doesn't care which one it is |
| Tell Don't Ask | `account.debit(amount)` — don't `get_balance()` then `set_balance()` | Tell the bartender "one beer" — don't ask for the inventory count yourself then update it |
| Law of Demeter | `order.getCustomerCity()` — not `order.getCustomer().getAddress().getCity()` | Ask the concierge for a cab, not the hotel owner's personal driver's cousin |
| Immutability | Value objects never mutate — every operation returns a new instance | A ₹500 note doesn't change its value when you spend it — you hand it over, a new balance is computed |
| Fail fast | Validate in `__init__`; never let invalid objects exist | Airport security at the gate, not after the plane lands |

---

## SOLID — One Sentence Each

| Principle | Definition | Violation signal | Fix | Real-life anchor |
|---|---|---|---|---|
| **SRP** | A class should have only one reason to change | Class has >1 reason to change | Split `UserService` into `UserValidator`, `EmailSender`, `UserRepo` | A chef cooks; a waiter serves — one person doing both is a bottleneck |
| **OCP** | Open for extension, closed for modification — add new behaviour by adding code, not editing existing code | `if type == "X":` grows for every new type | Add a class; don't modify existing ones | Adding a new payment method (UPI) shouldn't require editing the existing credit-card code |
| **LSP** | A subclass must be substitutable for its parent without breaking the caller | Subclass throws or no-ops a parent method | Flatten hierarchy; use composition | A Square that breaks when you set width ≠ height is not a safe Rectangle substitute |
| **ISP** | No class should be forced to implement methods it does not use | Class implements methods it doesn't use | Split into smaller, role-specific interfaces | A delivery driver shouldn't have to implement `processRefund()` just because it's on the `OrderHandler` interface |
| **DIP** | High-level modules should depend on abstractions, not concrete implementations | `OrderService` instantiates `MySQLRepository` directly | Inject `OrderRepository` interface; swap at construction | A power strip accepts any plug standard — the device doesn't hard-wire itself to the wall socket |

---

## Pattern Picker

### Creational — *how objects are created*

| Problem signal | Pattern | Real-life anchor | Do NOT use when |
|---|---|---|---|
| Create objects by type/config | **Factory Method** | `NotificationFactory.create("SMS")` returns `SMSNotifier` — caller never imports the class | One concrete type only |
| Family of related objects | **Abstract Factory** | AWS UI kit: swap `AWSFactory` → `AzureFactory` and every button/dialog changes consistently | Products don't need to be consistent |
| Many optional constructor fields | **Builder** | `Pizza.builder().size(L).crust(THIN).extra("cheese").build()` | ≤3 fields — use keyword args |
| Exactly one instance | **Singleton** | App-wide logger, config reader shared across all threads | Avoid — use module-level object in Python |

#### Factory Method
```python
from abc import ABC, abstractmethod

class Notifier(ABC):
    @abstractmethod
    def send(self, msg): ...

class EmailNotifier(Notifier):
    def send(self, msg): print(f"Email: {msg}")

class SMSNotifier(Notifier):
    def send(self, msg): print(f"SMS: {msg}")

class NotificationFactory:
    @staticmethod
    def create(type_):
        if type_ == "EMAIL": return EmailNotifier()
        if type_ == "SMS":   return SMSNotifier()
        raise ValueError(f"Unknown type: {type_}")

notifier = NotificationFactory.create("SMS")
notifier.send("Your order is confirmed")
```

#### Abstract Factory
```python
from abc import ABC, abstractmethod

class Button(ABC):
    @abstractmethod
    def render(self): ...

class Dialog(ABC):
    @abstractmethod
    def render(self): ...

class AWSButton(Button):
    def render(self): print("AWS Button")

class AWSDialog(Dialog):
    def render(self): print("AWS Dialog")

class AzureButton(Button):
    def render(self): print("Azure Button")

class AzureDialog(Dialog):
    def render(self): print("Azure Dialog")

class UIFactory(ABC):
    @abstractmethod
    def create_button(self): ...
    @abstractmethod
    def create_dialog(self): ...

class AWSFactory(UIFactory):
    def create_button(self): return AWSButton()
    def create_dialog(self): return AWSDialog()

class AzureFactory(UIFactory):
    def create_button(self): return AzureButton()
    def create_dialog(self): return AzureDialog()

# Swap the factory → entire UI family changes
factory = AWSFactory()
factory.create_button().render()
factory.create_dialog().render()
```

#### Builder
```python
class Pizza:
    def __init__(self, size, crust, toppings):
        self.size     = size
        self.crust    = crust
        self.toppings = toppings

    def __repr__(self):
        return f"Pizza({self.size}, {self.crust}, {self.toppings})"

class PizzaBuilder:
    def __init__(self):
        self._size     = "M"
        self._crust    = "THIN"
        self._toppings = []

    def size(self, s):
        self._size = s
        return self

    def crust(self, c):
        self._crust = c
        return self

    def topping(self, t):
        self._toppings.append(t)
        return self

    def build(self):
        return Pizza(self._size, self._crust, self._toppings)

pizza = PizzaBuilder().size("L").crust("THICK").topping("cheese").topping("olives").build()
```

#### Singleton
```python
# Preferred Python idiom: module-level instance
# config.py
class _Config:
    def __init__(self):
        self.debug = False

config = _Config()   # import config; config.debug

# Thread-safe class-based variant (when needed)
import threading

class Logger:
    _instance = None
    _lock = threading.Lock()

    @classmethod
    def get_instance(cls):
        if cls._instance is None:
            with cls._lock:
                if cls._instance is None:   # double-checked
                    cls._instance = cls()
        return cls._instance

    def log(self, msg):
        print(msg)
```

---

### Structural — *how classes and objects are composed*

| Problem signal | Pattern | Real-life anchor | Do NOT use when |
|---|---|---|---|
| Add features without modifying class | **Decorator** | Coffee + Milk + Sugar — each topping wraps the previous, same `cost()` call | Simple subclassing is enough |
| Tree of objects, uniform operations | **Composite** | File system: `Folder.size()` recurses into files and subfolders transparently | No real hierarchy exists |
| Control access to one object | **Proxy** | ATM PIN check before reaching account; lazy-load heavy config only on first access | You own the object — use Facade instead |
| Simplify complex subsystem | **Facade** | `BookingFacade.book(userId, hotelId, dates)` hides inventory + payment + notification calls | Only one subsystem class involved |
| Translate 3rd-party interface | **Adapter** | `StripeAdapter` maps your `PaymentGateway.charge()` to Stripe's API format | You control both sides |

#### Decorator
```python
from abc import ABC, abstractmethod

class Coffee(ABC):
    @abstractmethod
    def cost(self): ...
    @abstractmethod
    def description(self): ...

class SimpleCoffee(Coffee):
    def cost(self): return 5
    def description(self): return "Coffee"

class CoffeeDecorator(Coffee):
    def __init__(self, coffee):
        self._coffee = coffee   # wrapped component

class MilkDecorator(CoffeeDecorator):
    def cost(self): return self._coffee.cost() + 2
    def description(self): return self._coffee.description() + ", Milk"

class SugarDecorator(CoffeeDecorator):
    def cost(self): return self._coffee.cost() + 1
    def description(self): return self._coffee.description() + ", Sugar"

coffee = SugarDecorator(MilkDecorator(SimpleCoffee()))
print(coffee.description(), coffee.cost())  # Coffee, Milk, Sugar  8
```

#### Composite
```python
from abc import ABC, abstractmethod

class FileSystemItem(ABC):
    @abstractmethod
    def size(self): ...

class File(FileSystemItem):
    def __init__(self, name, size):
        self.name  = name
        self._size = size

    def size(self): return self._size

class Folder(FileSystemItem):
    def __init__(self, name):
        self.name     = name
        self._children = []   # list of FileSystemItem

    def add(self, item):
        self._children.append(item)

    def size(self):
        return sum(c.size() for c in self._children)

root = Folder("root")
root.add(File("a.txt", 10))
sub = Folder("docs")
sub.add(File("b.pdf", 50))
root.add(sub)
print(root.size())  # 60
```

#### Proxy
```python
from abc import ABC, abstractmethod

class BankAccount(ABC):
    @abstractmethod
    def withdraw(self, amount): ...

class RealBankAccount(BankAccount):
    def __init__(self, balance):
        self._balance = balance

    def withdraw(self, amount):
        self._balance -= amount
        print(f"Withdrew {amount}, balance: {self._balance}")

class ATMProxy(BankAccount):
    def __init__(self, account, pin):
        self._account    = account
        self._correct_pin = pin
        self._authenticated = False

    def authenticate(self, pin):
        self._authenticated = (pin == self._correct_pin)

    def withdraw(self, amount):
        if not self._authenticated:
            raise PermissionError("Not authenticated")
        self._account.withdraw(amount)

account = ATMProxy(RealBankAccount(1000), pin="1234")
account.authenticate("1234")
account.withdraw(200)
```

#### Facade
```python
class InventoryService:
    def reserve(self, item_id): print(f"Reserved {item_id}")

class PaymentService:
    def charge(self, user_id, amount): print(f"Charged {user_id} ${amount}")

class NotificationService:
    def notify(self, user_id, msg): print(f"Notify {user_id}: {msg}")

class BookingFacade:
    def __init__(self):
        self._inventory    = InventoryService()
        self._payment      = PaymentService()
        self._notification = NotificationService()

    def book(self, user_id, item_id, amount):
        self._inventory.reserve(item_id)
        self._payment.charge(user_id, amount)
        self._notification.notify(user_id, "Booking confirmed")

BookingFacade().book("u1", "hotel_101", 5000)
```

#### Adapter
```python
# Third-party payment SDK with a different interface
class StripeSDK:
    def make_charge(self, amount_cents, currency, token):
        print(f"Stripe: {amount_cents}c {currency} token={token}")

# Your internal interface
class PaymentGateway:
    def charge(self, amount, user_token): ...

class StripeAdapter(PaymentGateway):
    def __init__(self):
        self._stripe = StripeSDK()

    def charge(self, amount, user_token):
        self._stripe.make_charge(int(amount * 100), "USD", user_token)

gateway = StripeAdapter()
gateway.charge(49.99, "tok_abc123")
```

---

### Behavioural — *how objects communicate and assign responsibility*

| Problem signal | Pattern | Real-life anchor | Do NOT use when |
|---|---|---|---|
| Many swappable algorithms | **Strategy** | Swiggy: Fastest / Cheapest / Eco delivery — same checkout, different routing logic | Only 2 variants that never change |
| Behavior depends on state | **State** | Vending machine reacts differently to "insert coin" when Idle vs CoinInserted vs OutOfStock | Only 2 states, no transitions |
| One-to-many notification | **Observer** | Amazon order confirmed → email + SMS + inventory reserver all fire automatically | Call order or failure isolation matters — use event bus instead |
| Sequential processing pipeline | **Chain of Responsibility** | Loan approval: credit check → fraud check → manager sign-off — each handler passes or blocks | Steps are tightly coupled / share state |
| Undo / audit / replay | **Command** | Text editor Ctrl-Z: each edit is a `Command` stored on a stack, reversed on undo | No need to queue or reverse operations |
| Algorithm skeleton, vary steps | **Template Method** | ETL pipeline: `extract() → transform() → load()` fixed; subclasses override `transform()` | Need to swap the whole algorithm — use Strategy |

#### Strategy
```python
from abc import ABC, abstractmethod

class DeliveryStrategy(ABC):
    @abstractmethod
    def estimate(self, origin, dest): ...

class FastestRoute(DeliveryStrategy):
    def estimate(self, origin, dest): return f"Fastest: {origin}→{dest}"

class CheapestRoute(DeliveryStrategy):
    def estimate(self, origin, dest): return f"Cheapest: {origin}→{dest}"

class Checkout:
    def __init__(self):
        self._strategy = None   # DeliveryStrategy, set before routing

    def set_strategy(self, strategy):
        self._strategy = strategy

    def route(self, origin, dest):
        return self._strategy.estimate(origin, dest)

c = Checkout()
c.set_strategy(FastestRoute())
print(c.route("Delhi", "Mumbai"))
c.set_strategy(CheapestRoute())
print(c.route("Delhi", "Mumbai"))
```

#### State
```python
from abc import ABC, abstractmethod

class State(ABC):
    @abstractmethod
    def insert_coin(self): ...
    @abstractmethod
    def dispense(self): ...

class VendingMachine:
    def __init__(self):
        self.idle_state     = IdleState(self)
        self.has_coin_state = HasCoinState(self)
        self._state         = self.idle_state   # start idle

    def set_state(self, state): self._state = state
    def insert_coin(self):      self._state.insert_coin()
    def dispense(self):         self._state.dispense()

class IdleState(State):
    def __init__(self, machine): self._m = machine
    def insert_coin(self):
        print("Coin inserted")
        self._m.set_state(self._m.has_coin_state)
    def dispense(self): print("Insert coin first")

class HasCoinState(State):
    def __init__(self, machine): self._m = machine
    def insert_coin(self): print("Coin already inserted")
    def dispense(self):
        print("Dispensing...")
        self._m.set_state(self._m.idle_state)

vm = VendingMachine()
vm.insert_coin()   # → HasCoinState
vm.dispense()      # → back to IdleState
```

#### Observer
```python
from abc import ABC, abstractmethod

class Observer(ABC):
    @abstractmethod
    def update(self, event, data): ...

class Subject:
    def __init__(self):
        self._observers = []   # list of Observer

    def subscribe(self, obs):   self._observers.append(obs)
    def unsubscribe(self, obs): self._observers.remove(obs)

    def notify(self, event, data):
        for obs in self._observers:
            obs.update(event, data)

class Order(Subject):
    def confirm(self):
        self.notify("ORDER_CONFIRMED", {"order_id": 42})

class EmailService(Observer):
    def update(self, event, data): print(f"Email for {event}: {data}")

class InventoryService(Observer):
    def update(self, event, data): print(f"Reserve stock for {data}")

order = Order()
order.subscribe(EmailService())
order.subscribe(InventoryService())
order.confirm()
```

#### Chain of Responsibility
```python
from abc import ABC, abstractmethod

class Handler(ABC):
    def __init__(self):
        self._next = None   # next Handler in chain

    def set_next(self, handler):
        self._next = handler
        return handler   # enables chaining: a.set_next(b).set_next(c)

    def handle(self, request):
        if self._next:
            return self._next.handle(request)

class CreditCheckHandler(Handler):
    def handle(self, request):
        if request["credit_score"] < 600:
            print("Rejected: low credit score")
            return False
        print("Credit check passed")
        return super().handle(request)

class FraudCheckHandler(Handler):
    def handle(self, request):
        if request.get("flagged"):
            print("Rejected: fraud flag")
            return False
        print("Fraud check passed")
        return super().handle(request)

class ApprovalHandler(Handler):
    def handle(self, request):
        print("Loan approved")
        return True

credit = CreditCheckHandler()
fraud  = FraudCheckHandler()
approval = ApprovalHandler()
credit.set_next(fraud).set_next(approval)

credit.handle({"credit_score": 720, "flagged": False})
```

#### Command
```python
from abc import ABC, abstractmethod

class Command(ABC):
    @abstractmethod
    def execute(self): ...
    @abstractmethod
    def undo(self): ...

class Board:
    def __init__(self):
        self._cells = [[""] * 3 for _ in range(3)]

    def place(self, row, col, player):
        self._cells[row][col] = player

    def clear(self, row, col):
        self._cells[row][col] = ""

class PlacePieceCommand(Command):
    def __init__(self, board, row, col, player):
        self._board, self._row, self._col, self._player = board, row, col, player

    def execute(self): self._board.place(self._row, self._col, self._player)
    def undo(self):    self._board.clear(self._row, self._col)

class GameInvoker:
    def __init__(self):
        self._history = []   # list of Command

    def execute(self, cmd):
        cmd.execute()
        self._history.append(cmd)

    def undo(self):
        if self._history:
            self._history.pop().undo()

board = Board()
game  = GameInvoker()
game.execute(PlacePieceCommand(board, 0, 0, "X"))
game.execute(PlacePieceCommand(board, 1, 1, "O"))
game.undo()   # removes O
```

#### Template Method
```python
from abc import ABC, abstractmethod

class DataProcessor(ABC):
    # Template method — sequence is fixed
    def process(self, data):
        raw       = self.extract(data)
        cleaned   = self.transform(raw)
        self.load(cleaned)

    @abstractmethod
    def extract(self, data): ...

    @abstractmethod
    def transform(self, raw): ...

    def load(self, cleaned):   # default — subclass can override
        print(f"Loaded: {cleaned}")

class CSVProcessor(DataProcessor):
    def extract(self, data):    return data.split(",")
    def transform(self, raw):   return [x.strip().upper() for x in raw]

class JSONProcessor(DataProcessor):
    def extract(self, data):    return data   # already parsed
    def transform(self, raw):   return {k: str(v) for k, v in raw.items()}

CSVProcessor().process("alice, bob, carol")
JSONProcessor().process({"name": "alice", "age": 30})
```

---

## Encapsulation Rules (say these out loud)

- **Validate in `__init__`** — impossible to construct in invalid state
- **Private fields, public methods** — never expose mutable internals
- **Value objects are immutable** — use `@dataclass(frozen=True)`
- **No `get_X()` + `set_X()` pair** — that's just a public field with extra steps; merge into a behavior method
- **Tell, Don't Ask** — move the conditional into the object, don't leak the decision to the caller

---

## Concurrency Reference

| Issue | Fix | Real-life anchor | Python tool |
|---|---|---|---|
| Check-then-act race | Lock around check + write, or atomic DB operation | Two people booking the last flight seat simultaneously — only one can win | `threading.Lock()` |
| Many readers, rare writers | Read-write lock | Library: many people read the same book simultaneously; only one can update the catalog | Custom `RWLock` |
| Limit N concurrent users | Counting semaphore | Parking lot with N spots — barrier arm blocks entry when full | `threading.Semaphore(N)` |
| Producer faster than consumer | Bounded blocking queue | Amazon warehouse: conveyor belt stops when the packing station is full | `queue.Queue(maxsize=N)` |
| Double-init singleton | Double-checked locking | One CEO per company — second election check confirms seat is still vacant | Module-level object preferred |
| Deadlock | Acquire locks in global order | Two people each holding one chopstick, each waiting for the other's — agree on a pickup order | Never hold lock while waiting for another |
| Graceful shutdown signal | One-shot broadcast event | Fire alarm: single bell rings once, everyone in every room hears and evacuates | `threading.Event` |
| Resource cleanup guarantee | Context manager | Hotel key card: room is guaranteed to be released on checkout regardless of how the guest left | `with lock:`, `@contextmanager` |
| I/O-bound parallelism | Threads release GIL during I/O | Call centre: agent puts customer on hold (waiting for file) and picks up another call | `threading` |
| CPU-bound parallelism | Separate GIL per process | Assembly line: multiple workers each doing heavy lifting on separate stations | `multiprocessing` |
| 10K+ concurrent connections | Cooperative single-thread | Traffic controller: one person manages 10K cars by only giving attention when a car signals | `asyncio` |

**Lock scope rule:**
```
validate inputs          (outside lock — can be slow)
acquire lock
re-check shared state    (inside lock — condition may have changed)
mutate state             (inside lock — fast, no I/O)
release lock
perform side effects     (outside lock — DB write, network call, email)
```

---

## Error Handling Signals

| Situation | Do |
|---|---|
| Invalid argument | Raise at construction time (`__init__`) — fail fast |
| Expected failure (user not found) | Return `None` or `Optional` — not an exception |
| Unexpected failure (DB down) | Raise specific exception — never swallow silently |
| Adding a new failure mode | Define a custom exception class — don't raise `ValueError` with a message |
| Translating a library exception | `raise DomainError("...") from original_exception` — preserve traceback |
| Cleanup on any exit | Context manager (`with` block) — not `finally` in caller |
| Resource that must be returned | `try/finally` or `@contextmanager` — pool connections, file handles |

---

## Code Smells → Fix

| Smell | Signal | Fix | Real-life anchor |
|---|---|---|---|
| **Long Method** | Can't give it a single accurate name | Extract cohesive sub-operations into named private methods | A kitchen recipe that tries to also describe plating, billing, and cleaning — split it |
| **Large Class** | >1 reason to change | Split into collaborating classes | A Swiss Army knife is useful at a campsite, not in a professional kitchen |
| **Primitive Obsession** | `str` for email, `int` for money | Extract `EmailAddress`, `Money` value objects | Storing a phone number as `int` — you can't format it, add country code, or validate it |
| **Feature Envy** | Method uses another class's data more than its own | Move the method to that class | A cashier who walks into the kitchen to count ingredients — that logic belongs in the kitchen |
| **Type Code** | `if role == "admin":` repeated everywhere | Replace with polymorphism — `role.can_edit()` | A bouncer checking a list of 50 VIP rules every time — give each person a badge that answers "can you enter?" |
| **God Object** | `OrderManager` does everything | Split into `OrderService`, `OrderRepository`, `OrderValidator` | One employee who takes orders, cooks, delivers, and does accounting — single point of failure |
| **Shotgun Surgery** | One change requires edits in 10 files | Consolidate related behavior into one class | Changing a restaurant's opening hours requires updating the sign, website, Google Maps, Zomato, and the phone recording separately |

---

## Data Structure Picker

| Scenario | Structure | Real-life anchor | Why |
|---|---|---|---|
| LRU Cache | HashMap + doubly linked list | Browser cache: most recently visited pages stay, oldest evicted when full | O(1) lookup, O(1) move-to-front, O(1) evict-tail |
| Priority queue / task scheduler | Min-heap (`heapq`) | Hospital triage: most critical patient always at the front, not arrival order | O(log N) insert, O(1) peek-min |
| Sliding window / BFS queue | `deque` | Airport security conveyor belt: add bags at one end, screen and remove from the other | O(1) append/pop from both ends |
| Membership test only | `set` | Spam filter: "have we seen this sender before?" — yes/no, no details needed | O(1) lookup, clear intent |
| Membership test + metadata | `dict` | Flight manifest: passenger name → seat, meal preference, check-in status | O(1) lookup + associated value |
| Min-stack (min in O(1)) | Two stacks | Stock tracker: current prices on main stack; shadow stack tracks the lowest price seen so far | Shadow `min_stack` tracks current min |
| Access order tracking | `collections.OrderedDict` | Customer service queue: serve in order, but VIPs can be moved to the back or front instantly | `move_to_end()`, `popitem(last=False)` |
| Bounded history buffer | `deque(maxlen=N)` | Black box flight recorder: always keeps the last N seconds; oldest auto-discarded | Auto-evicts oldest on append |

---

## Python LLD Signals

| Tool | When to use |
|---|---|
| `@dataclass(frozen=True)` | Value object — immutable, auto `__eq__` + `__hash__` |
| `@dataclass` | DTO / config struct with many fields |
| `@property` | Computed attribute with no side effects, cheap to call |
| `@classmethod` | Alternative constructor — `Date.from_string(...)` |
| `@staticmethod` | Utility function namespaced to class, no `self`/`cls` needed |
| `__slots__` | Millions of small instances — eliminates `__dict__` overhead |
| `__repr__` | Always define — `Point(x=1, y=2)` for REPL |
| `ABC + @abstractmethod` | Define interface — subclasses must implement |
| `Protocol` | Duck-typing interface — no inheritance required |
| `contextlib.contextmanager` | Simple context manager via generator |
| `threading.RLock` | Lock that the same thread can acquire multiple times |
| `queue.Queue` | Thread-safe producer-consumer; `maxsize` for backpressure |

---

## Amazon-Specific Problems — What to Code First

| Problem | Real-life frame | Code first | Key invariant |
|---|---|---|---|
| **Vending Machine** | Snack machine in office lobby — take money, give snack, handle cancel | State transition methods (`insert_coin`, `select_item`, `cancel`) | Never dispense without atomically decrementing inventory |
| **ATM** | Bank ATM withdrawal — the machine can crash after debiting but before dispensing | Withdrawal saga: log intent → debit DB → dispense → confirm | Crash between debit and dispense → refund on recovery |
| **Amazon Locker** | Hub locker at apartment complex — one OTP, one package, one slot | `assign_locker(package)` smallest-fit + atomic `status='OCCUPIED'` | One locker assigned to at most one active delivery |
| **Order State Machine** | Amazon order lifecycle: Placed → Confirmed → Shipped → Delivered / Cancelled | `transition_to(new_status)` with `VALID_TRANSITIONS` dict | Illegal transitions raise; state changes publish events |
| **Coupon Engine** | Flipkart checkout: apply SAVE10 + BANKOFF5 but not two exclusive codes | `apply_coupons(cart, codes, user)` with exclusivity check | Total discount ≤ cart total; exclusive coupons block stacking |
| **Parking Lot** | Mall parking: barrier assigns a spot, ticket released on exit | `assign_spot(vehicle)` + `release_spot(ticket)` | One spot assigned to at most one active ticket |
| **BookMyShow** | Cinema seat booking: two users race for the last aisle seat | `lock_seat(show_id, seat_id, user_id)` NX + TTL | One seat confirmed by at most one booking per show |
| **LRU Cache** | Browser tab history: most recently visited stays, oldest dropped when memory full | `get(key)` + `put(key, value)` with HashMap + DLL | `get` must move node to head; evict tail on capacity |
| **Rate Limiter** | OTP API: allow max 5 OTP requests per phone number per minute | `allow(user_id)` with atomic check-and-increment | Count resets on window boundary; never allow > limit |
| **Logger / Notification** | Order confirmation: email may be slow — don't block SMS because email is down | Handler chain + async queue for slow channels | One bad handler must not block others |
| **Splitwise** | Group trip expense split: Rahul paid ₹3000, Alice and Bob each owe ₹1000 | `add_expense(amount, paid_by, split_with)` | `sum(paid) == sum(owed)` always |

---

## State Machine Template

Use this structure for any status-driven entity (Order, Booking, ATM, Vending Machine):

```python
from enum import Enum

class Status(Enum):
    PLACED = "PLACED"
    CONFIRMED = "CONFIRMED"
    SHIPPED = "SHIPPED"
    CANCELLED = "CANCELLED"

VALID_TRANSITIONS: dict[Status, set[Status]] = {
    Status.PLACED:     {Status.CONFIRMED, Status.CANCELLED},
    Status.CONFIRMED:  {Status.SHIPPED, Status.CANCELLED},
    Status.SHIPPED:    set(),    # terminal
    Status.CANCELLED:  set(),    # terminal
}

class Entity:
    def transition_to(self, new_status: Status):
        if new_status not in VALID_TRANSITIONS[self.status]:
            raise InvalidTransitionError(f"{self.status} → {new_status}")
        self.status = new_status
```

Never allow `entity.status = X` directly. Force all changes through `transition_to()`.

---

## Dependency Injection Template

```python
# Wrong — hard to test, can't swap implementation
class OrderService:
    def __init__(self):
        self._repo = MySQLOrderRepository()   # concrete dependency

# Right — inject through constructor
class OrderService:
    def __init__(self, repo: OrderRepository):  # depend on interface
        self._repo = repo

# In production
service = OrderService(repo=MySQLOrderRepository())

# In tests
service = OrderService(repo=FakeOrderRepository())
```

---

## Must-State Invariants (say these out loud)

```
A parking spot holds at most one active ticket at any time.
A seat can be confirmed by at most one booking per show.
An LRU cache get and put must update the map and linked list atomically.
A vending machine dispenses only when balance >= price AND inventory > 0.
A Splitwise expense must keep sum(paid) == sum(owed).
An ATM withdrawal must debit the DB before dispensing cash; crash mid-way → refund.
An Amazon Locker assigns to exactly one delivery; releases on pickup or expiry.
An order status transition must be validated before persisting; publish event after.
Total applied discounts must never exceed the cart total.
```

---

## Interview Red Flags

Avoid these — they signal weak design to the interviewer:

- **God class**: one `OrderManager` or `SystemController` that does everything
- **String statuses**: `if status == "confirmed":` — use enums
- **Type switch**: `if isinstance(obj, SubClassA):` — replace with polymorphism
- **Deep inheritance**: 4+ levels — flatten with composition
- **No interfaces**: concrete classes talking to concrete classes — no swap point
- **Lock held during I/O**: DB write, HTTP call, or sleep inside a `with lock:` block
- **Mutable value objects**: `Money` or `Address` with setters
- **Float for money**: always use `Decimal`
- **Constructor that can leave object invalid**: field can be `None` after `__init__`
- **Silent exception swallowing**: `except Exception: pass`
- **Hardcoded dependency**: `MySQLRepo()` inside a service constructor
- **No edge cases mentioned**: skipping "what if quantity = 0" or "what if concurrent callers"

