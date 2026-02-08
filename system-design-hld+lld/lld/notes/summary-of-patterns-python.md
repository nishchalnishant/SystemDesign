# Summary of patterns (Python)

## Creational



<table data-header-hidden><thead><tr><th width="109.41796875"></th><th width="127.30078125"></th><th width="159.2421875"></th><th></th><th></th></tr></thead><tbody><tr><td><strong>Pattern</strong></td><td><strong>Mnemonic</strong></td><td><strong>Summary</strong></td><td><strong>LLD Case / Question</strong></td><td><strong>How It Is Used in LLD</strong></td></tr><tr><td>Abstract Factory</td><td>AFter Concrete Factory</td><td>Creates families of related or dependent objects without specifying their concrete classes.</td><td>Design a Multi-Database Connector System. The system needs to support connecting to MySQL, Postgres, and MongoDB interchangeably.</td><td>An Abstract Factory interface creates the family of related objects (Connection, Query, Transaction). Concrete factories (<code>MySQLFactory</code>, <code>PostgresFactory</code>) implement this to return concrete objects, ensuring the client works only with abstractions.</td></tr><tr><td>Builder</td><td>Builder Responsibility Construction</td><td>Separates the construction of a complex object from its representation, allowing the same construction process to create different representations.</td><td>Design a complex Report Generator. A report has many optional attributes (header, footer, content) and different formats (PDF, HTML).</td><td>The Builder defines the steps (<code>buildHeader()</code>, <code>buildBody()</code>). Concrete Builders (e.g., <code>PdfReportBuilder</code>) implement these steps to produce the final complex <code>Report</code> object in a fluent, step-by-step manner, managing required and optional parameters.</td></tr><tr><td>Factory Method</td><td>FM for Interface</td><td>Defines an interface for creating an object, but lets subclasses decide which class to instantiate.</td><td>Design a Notification Service that can send messages via Email, SMS, or Push Notification, decided at runtime.</td><td>A <code>NotificationFactory</code> class defines the <code>createNotification()</code> method. Subclasses (<code>EmailFactory</code>, <code>SmsFactory</code>) override this method to return the concrete product, deferring instantiation to subclasses while exposing a uniform creation interface.</td></tr><tr><td>Prototype</td><td>Prototype Clones New objects</td><td>Creates new objects by copying an existing object (the prototype) to avoid coupling the client to the product classes.</td><td>Design a Maze/Game Level Editor where complex, pre-configured game objects (e.g., a specific type of enemy) need to be duplicated rapidly.</td><td>A prototype object is cloned to create new instances. This is more efficient than re-initializing the object from scratch, especially when object creation is resource-intensive (e.g., loading assets or complex data).</td></tr><tr><td>Singleton</td><td>Single Instance Global Access</td><td>Ensures a class has only one instance and provides a global point of access to it.</td><td>Design a Configuration Manager for a distributed application. The configuration must be loaded once and accessed globally by all modules.</td><td>The <code>ConfigurationManager</code> class uses a private constructor and a static method to return its single instance. This prevents multiple instances, ensuring all modules read and potentially write to the same configuration data source.</td></tr><tr><td></td><td></td><td></td><td></td><td></td></tr></tbody></table>

## Structural

<table data-header-hidden><thead><tr><th width="106.12890625"></th><th width="117.1640625"></th><th width="157.65625"></th><th></th><th></th></tr></thead><tbody><tr><td><strong>Pattern</strong></td><td><strong>Mnemonic</strong></td><td><strong>Summary</strong></td><td><strong>LLD Case / Question</strong></td><td><strong>How It Is Used in LLD</strong></td></tr><tr><td>Adapter</td><td>Adapter Makes Interfaces Compatible</td><td>Allows objects with incompatible interfaces to collaborate. Converts the interface of a class into another interface clients expect.</td><td>Integrate a new, incompatible Third-Party Logging Library into an existing system without modifying hundreds of existing client calls.</td><td>The Adapter object wraps the new logger and implements the legacy logging interface that the clients expect. This bridges the interface gap, allowing the old code to call the new functionality seamlessly.</td></tr><tr><td>Bridge</td><td>Bridge Divides Abstraction Implementation</td><td>Decouples an abstraction from its implementation so that the two can vary independently.</td><td>Design a Drawing Program that can draw different shapes (Circle, Square) on different Rendering Engines (OpenGL, DirectX).</td><td>The <code>Shape</code> (Abstraction) holds a reference to a <code>Renderer</code> (Implementation) object. This allows any shape to be combined with any renderer, letting you add new shapes or new rendering APIs independently.</td></tr><tr><td>Composite</td><td>Composite Treats Objects Uniformly</td><td>Composes objects into tree structures. Clients can treat individual objects and compositions of objects uniformly.</td><td>Design a File System Component. The component needs to represent both individual files and directories, and clients should operate on both uniformly (e.g., <code>getSize()</code>).</td><td>Both <code>File</code> (Leaf) and <code>Directory</code> (Composite) implement the same component interface. The <code>Directory</code> object manages children and recursively delegates operations, forming a self-similar tree structure.</td></tr><tr><td>Decorator</td><td>Decorator Adds Responsibilities Dynamically</td><td>Attaches additional responsibilities to an object dynamically. Provides a flexible alternative to subclassing.</td><td>Design a Data Stream Wrapper that needs to support various runtime features like compression, encryption, and logging, combinable in any order.</td><td>Each feature (e.g., <code>CompressedStream</code>, <code>EncryptedStream</code>) is a Decorator that wraps the original stream and adds its functionality. Decorators can be chained dynamically to create flexible feature combinations.</td></tr><tr><td>Facade</td><td>Facade Simplifies Complex Subsystems</td><td>Provides a unified, high-level interface to a set of interfaces in a subsystem, making it easier to use.</td><td>Design an API Gateway for a legacy service. The service has dozens of methods, but the mobile client needs a few simple, aggregated operations.</td><td>The <code>APIGateway</code> acts as a Facade, providing simple methods (<code>getUserSummary()</code>) that internally orchestrate and delegate calls to the complex, multiple classes of the legacy subsystem.</td></tr><tr><td>Flyweight</td><td>Flyweight Shares Large Amounts</td><td>Shares common state among multiple objects to reduce memory usage.</td><td>Design a Text Editor capable of displaying a document with millions of characters, where each character object is memory-intensive.</td><td>The Flyweight object stores the intrinsic state (e.g., the character code, font name) which is shared. The extrinsic state (e.g., character position) is passed in by the client (the document structure).</td></tr><tr><td>Proxy</td><td>Proxy Controls Access</td><td>Provides a surrogate or placeholder for another object to control access to it.</td><td>Design a Virtual Proxy for a high-resolution image viewer. Loading the image should be delayed until the user clicks on the thumbnail.</td><td>The <code>ImageProxy</code> acts as a surrogate for the real, expensive <code>HiResImage</code>. It implements the same interface but adds lazy loading logic to defer creation and loading of the real object until it is absolutely needed.</td></tr></tbody></table>

## Behavioural

<table data-header-hidden><thead><tr><th width="106.77081298828125"></th><th width="102.046875"></th><th width="155.307373046875"></th><th></th><th></th></tr></thead><tbody><tr><td><strong>Pattern</strong></td><td><strong>Mnemonic</strong></td><td><strong>Summary</strong></td><td><strong>LLD Case / Question</strong></td><td><strong>How It Is Used in LLD</strong></td></tr><tr><td>Chain of Responsibility</td><td>Chain Handles Requests Sequentially</td><td>Avoids coupling the sender of a request to its receiver by giving more than one object a chance to handle the request.</td><td>Design a Workflow Engine where a request must pass through a sequence of processors (Authentication <span class="math">$\rightarrow$</span> Validation <span class="math">$\rightarrow$</span> Authorization).</td><td>Each processor (e.g., <code>AuthHandler</code>, <code>ValidationHandler</code>) is a handler in the chain. If a handler cannot process the request, it passes it to the next handler. This provides flexible ordering and allows handlers to be added/removed dynamically.</td></tr><tr><td>Command</td><td>Command Encapsulates Action</td><td>Encapsulates a request as an object, allowing for parameterization of clients with different requests, queuing, and undoable operations.</td><td>Design an advanced Text Editor with full Undo/Redo functionality.</td><td>Each user action (e.g., <code>InsertTextCommand</code>, <code>DeleteTextCommand</code>) is encapsulated as an object. These objects are stored in a stack. To implement Undo, the system simply executes the <code>undo()</code> method of the last command on the stack.</td></tr><tr><td>Iterator</td><td>Iterator Accesses Collection Sequentially</td><td>Provides a way to access the elements of an aggregate object sequentially without exposing its underlying representation.</td><td>Design a custom Data Structure (like a sparse array or a graph) and provide a standard way for client code to traverse its elements.</td><td>The <code>Iterator</code> interface (<code>hasNext()</code>, <code>next()</code>) is defined, and the data structure provides a method to get a concrete <code>Iterator</code> implementation. The client code uses the standard iterator interface, decoupled from the collection's internal complexity.</td></tr><tr><td>Mediator</td><td>Mediator Reduces Direct Coupling</td><td>Defines an object that encapsulates how a set of objects interact. Promotes loose coupling by keeping objects from referring to each other explicitly.</td><td>Design an Air Traffic Control (ATC) System. Planes should not communicate directly but must coordinate via the Tower.</td><td>The Tower acts as the Mediator. All <code>Plane</code> objects communicate with the Tower, which relays or coordinates the messages. This drastically reduces the number of direct dependencies between the planes (objects).</td></tr><tr><td>Memento</td><td>Memento Saves Internal State</td><td>Captures and externalizes an object's internal state without violating encapsulation, so the object can be restored to this state later.</td><td>Implement a Save-State/Checkpoint Feature in a complex application or game without exposing the internal state of the core object.</td><td>The Originator (the object whose state is saved) creates a Memento object containing its current state. The Caretaker (the saving mechanism) stores the Memento but cannot inspect its contents. The Originator can later restore its state from the Memento.</td></tr><tr><td>Observer</td><td>Observer Notifies Dependent Objects</td><td>Defines a one-to-many dependency between objects so that when one object (the subject) changes state, all its dependents (observers) are notified.</td><td>Design a Logging and Auditing System. When an order status changes, multiple modules (inventory, billing, notification service) need to be updated automatically.</td><td>The <code>Order</code> object is the Subject. The other modules are Observers. When the <code>Order</code> state changes, it notifies all subscribed observers via a standard <code>update()</code> method, completely decoupling the change from the update logic.</td></tr><tr><td>State</td><td>State Alters Behavior Based On state</td><td>Allows an object to alter its behavior when its internal state changes. The object will appear to change its class.</td><td>Design a Vending Machine or an Order Management System where the behavior changes significantly depending on the current state (e.g., <code>Idle</code>, <code>Has Coin</code>, <code>Dispensing</code>).</td><td>The Vending Machine class delegates its behavior to one of the State objects (e.g., <code>IdleState</code>, <code>HasCoinState</code>). When an event occurs (e.g., <code>insertCoin()</code>), the current state object handles it and manages the transition to a new state.</td></tr><tr><td>Strategy</td><td>Strategy Encapsulates Algorithms</td><td>Defines a family of algorithms, encapsulates each one, and makes them interchangeable.</td><td>Design a Checkout Flow with different discount and tax calculation rules (e.g., student discount, seasonal sale, state tax). The choice of rules should be swappable at runtime.</td><td>An <code>IPriceStrategy</code> interface is defined. Concrete strategies (e.g., <code>StudentDiscountStrategy</code>) encapsulate the different algorithms. The <code>CheckoutContext</code> holds a reference to the current strategy and can swap it easily to change the pricing logic.</td></tr><tr><td>Template Method</td><td>Template Defines Skeleton Steps</td><td>Defines the skeleton of an algorithm in the superclass but lets subclasses override specific steps of the algorithm without changing its structure.</td><td>Design a framework for Report Generation where the core process is fixed (Fetch Data <span class="math">$\rightarrow$</span> Process <span class="math">$\rightarrow$</span> Format <span class="math">$\rightarrow$</span> Print), but the <em>Process</em> and <em>Format</em> steps differ for different report types.</td><td>The abstract base class defines the overall Template Method (e.g., <code>generateReport()</code>) which calls abstract "hook" methods for the customizable steps. Subclasses implement these hooks to provide specific logic without altering the workflow structure.</td></tr><tr><td>Visitor</td><td>Visitor Adds Operations Without Modifying Elements</td><td>Represents an operation to be performed on the elements of an object structure. Lets you define a new operation without changing the classes of the elements.</td><td>Design a Compiler/Interpreter. You need to perform operations like type-checking, code generation, and optimization on an Abstract Syntax Tree (AST).</td><td>Each operation (e.g., <code>TypeCheckVisitor</code>, <code>CodeGenerationVisitor</code>) is a Visitor class that implements the <code>visit()</code> method for every element type in the AST. This allows you to add new operations without modifying the AST node classes themselves.</td></tr></tbody></table>

## Code examples

Creational design patterns focus on the best ways to instantiate objects. They hide the creation logic rather than having you instantiate objects directly using the `new` operator. This gives the program more flexibility in deciding which objects need to be created for a given use case.

Here is the implementation of the five core creational patterns in Python.

***

#### 1. Singleton Pattern

Intent: Ensures that a class has only one instance and provides a global point of access to it.



`__new__` creates and returns the actual object instance (the "constructor"), while `__init__` initializes that created object's attributes (the "initializer"); `__new__` runs first, returning the object for `__init__` to set up, but you usually only override `__new__` for advanced cases like metaclasses or creating immutable types, rarely needing it for standard object setup.&#x20;





Python

```python
class Singleton:
    _instance = None

    def __new__(cls):
        if cls._instance is None:
            print("Creating new instance")
            cls._instance = super(Singleton, cls).__new__(cls)
        return cls._instance

# Usage
s1 = Singleton()
s2 = Singleton()
print(f"Are they the same? {s1 is s2}")  # True
```

***

#### 2. Factory Method Pattern

Intent: Defines an interface for creating an object but allows subclasses to alter the type of objects that will be created.

Python

```python
from abc import ABC, abstractmethod

# Product Interface
class Animal(ABC):
    @abstractmethod
    def speak(self):
        pass

# Concrete Products
class Dog(Animal):
    def speak(self): return "Woof!"

class Cat(Animal):
    def speak(self): return "Meow!"

# Creator (Factory)
class AnimalFactory:
    def get_animal(self, animal_type):
        if animal_type == "dog":
            return Dog()
        elif animal_type == "cat":
            return Cat()
        return None

# Usage
factory = AnimalFactory()
pet = factory.get_animal("dog")
print(pet.speak())
```

***

#### 3. Abstract Factory Pattern

Intent: Provides an interface for creating families of related or dependent objects without specifying their concrete classes.

Python

```python
# Abstract Products
class Chair(ABC): pass
class Sofa(ABC): pass

# Concrete Product Families (Modern)
class ModernChair(Chair): pass
class ModernSofa(Sofa): pass

# Concrete Product Families (Victorian)
class VictorianChair(Chair): pass
class VictorianSofa(Sofa): pass

# Abstract Factory
class FurnitureFactory(ABC):
    @abstractmethod
    def create_chair(self): pass
    @abstractmethod
    def create_sofa(self): pass

# Concrete Factories
class ModernFactory(FurnitureFactory):
    def create_chair(self): return ModernChair()
    def create_sofa(self): return ModernSofa()

class VictorianFactory(FurnitureFactory):
    def create_chair(self): return VictorianChair()
    def create_sofa(self): return VictorianSofa()
```

***

#### 4. Builder Pattern

Intent: Separates the construction of a complex object from its representation, allowing the same construction process to create different representations.

![Image of Builder design pattern diagram](https://encrypted-tbn0.gstatic.com/licensed-image?q=tbn:ANd9GcTwQrh6rBPpDKnazzLhqXhIaSB3jXu8_KKvPHfqXRKUcLN6mFtQHhJGeh8ZbEEqTI6iMk4QvnBUdFUHGpkBdA9281MBDr1yuBxjyLxDWwaMaEPe4Xw)ShutterstockPython

```python
class House:
    def __init__(self):
        self.walls = None
        self.roof = None
        self.has_pool = False

class HouseBuilder:
    def __init__(self):
        self.house = House()
    
    def build_walls(self):
        self.house.walls = "Concrete"
        return self
    
    def build_roof(self):
        self.house.roof = "Tiles"
        return self
    
    def add_pool(self):
        self.house.has_pool = True
        return self

    def get_result(self):
        return self.house

# Usage (Fluent Interface)
my_house = HouseBuilder().build_walls().build_roof().add_pool().get_result()
```

***

#### 5. Prototype Pattern

Intent: Creates new objects by copying an existing object (the prototype) rather than creating from scratch.

Python

```python
import copy

class Shape:
    def __init__(self, color):
        self.color = color

    def clone(self):
        return copy.deepcopy(self)

# Usage
red_circle = Shape("Red")
another_circle = red_circle.clone()
another_circle.color = "Blue"

print(f"Original: {red_circle.color}, Clone: {another_circle.color}")
```

***

#### Summary Table

| **Pattern**      | **Purpose**                     | **Use Case**                                |
| ---------------- | ------------------------------- | ------------------------------------------- |
| Singleton        | Limit to 1 instance             | Database connections, Loggers.              |
| Factory Method   | Delegate creation to subclasses | Creating different types of documents.      |
| Abstract Factory | Create families of objects      | UI toolkits (Windows vs Mac buttons).       |
| Builder          | Step-by-step complex creation   | Building a custom computer or Pizza.        |
| Prototype        | Clone existing objects          | Large objects that are expensive to create. |



Structural design patterns are all about how classes and objects are composed to form larger structures. They help ensure that if one part of a system changes, the entire structure doesn't need to be rebuilt.

Here is the implementation of the seven core structural patterns in Python.

***

#### 1. Adapter Pattern

Intent: Allows objects with incompatible interfaces to collaborate. It acts as a wrapper that translates calls from one interface to another.

Python

```python
# The "Old" interface (Incompatible)
class OldSystem:
    def legacy_request(self):
        return "Legacy response"

# The Target interface (What we want)
class NewInterface:
    def request(self):
        pass

# The Adapter
class Adapter(NewInterface):
    def __init__(self, old_system):
        self.old_system = old_system

    def request(self):
        # Translating the request
        return f"Adapter: (TRANSLATED) {self.old_system.legacy_request()}"

# Usage
old = OldSystem()
adapter = Adapter(old)
print(adapter.request())
```

***

#### 2. Bridge Pattern

Intent: Splits a large class or a set of closely related classes into two separate hierarchies—abstraction and implementation—which can be developed independently.

Python

```python
# Implementation Interface
class Device:
    def set_volume(self, percent): pass

# Concrete Implementation
class TV(Device):
    def set_volume(self, percent): print(f"TV volume set to {percent}%")

class Radio(Device):
    def set_volume(self, percent): print(f"Radio volume set to {percent}%")

# Abstraction
class RemoteControl:
    def __init__(self, device):
        self.device = device

    def volume_up(self):
        self.device.set_volume(50)

# Usage
tv_remote = RemoteControl(TV())
tv_remote.volume_up()
```

***

#### 3. Composite Pattern

Intent: Lets you compose objects into tree structures and then work with these structures as if they were individual objects. Great for "Part-Whole" hierarchies.

Python

```python
from abc import ABC, abstractmethod

# Component
class FileSystemItem(ABC):
    @abstractmethod
    def show_details(self): pass

# Leaf
class File(FileSystemItem):
    def __init__(self, name): self.name = name
    def show_details(self): print(f"File: {self.name}")

# Composite
class Directory(FileSystemItem):
    def __init__(self, name):
        self.name = name
        self.children = []

    def add(self, item): self.children.append(item)

    def show_details(self):
        print(f"Directory: {self.name}")
        for child in self.children:
            child.show_details()

# Usage
root = Directory("root")
root.add(File("config.txt"))
sub_dir = Directory("bin")
sub_dir.add(File("app.exe"))
root.add(sub_dir)
root.show_details()
```

***

#### 4. Decorator Pattern

Intent: Lets you attach new behaviors to objects by placing these objects inside special wrapper objects that contain the behaviors.

Python

```python
# Component
class Coffee:
    def get_cost(self): return 10

# Decorator
class MilkDecorator:
    def __init__(self, coffee):
        self._coffee = coffee

    def get_cost(self):
        return self._coffee.get_cost() + 5

# Usage
basic_coffee = Coffee()
milk_coffee = MilkDecorator(basic_coffee)
print(f"Total: ${milk_coffee.get_cost()}") # 15
```

***

#### 5. Facade Pattern

Intent: Provides a simplified interface to a library, a framework, or any other complex set of classes.

Python

```python
class VideoConverter:
    def convert(self, filename):
        print("Complex Subsystem: Reading file...")
        print("Complex Subsystem: Applying codecs...")
        print("Complex Subsystem: Saving file...")
        return "Video.mp4"

# Facade
class VideoApp:
    def start_conversion(self):
        converter = VideoConverter()
        return converter.convert("raw_footage.avi")

# Usage
app = VideoApp()
app.start_conversion()
```

***

#### 6. Flyweight Pattern

Intent: Lets you fit more objects into the available amount of RAM by sharing common parts of state between multiple objects instead of keeping all of the data in each object.

Python

```python
class TreeType:
    """The Flyweight: contains shared state."""
    def __init__(self, name, color):
        self.name = name
        self.color = color

class Forest:
    """The Context: creates/manages Flyweights."""
    _tree_types = {}

    @classmethod
    def get_tree_type(cls, name, color):
        if name not in cls._tree_types:
            cls._tree_types[name] = TreeType(name, color)
        return cls._tree_types[name]

# Usage
tree1 = Forest.get_tree_type("Oak", "Green")
tree2 = Forest.get_tree_type("Oak", "Green")
print(f"Same instance? {tree1 is tree2}") # True
```

***

#### 7. Proxy Pattern

Intent: Provides a substitute or placeholder for another object. A proxy controls access to the original object, allowing you to perform something either before or after the request reaches the original object.

Python

```python
class RealDatabase:
    def query(self, sql):
        print(f"Executing: {sql}")

class DatabaseProxy:
    def __init__(self, user_role):
        self.db = RealDatabase()
        self.user_role = user_role

    def query(self, sql):
        if self.user_role != "admin":
            print("Access Denied: Only admins can run queries.")
        else:
            self.db.query(sql)

# Usage
proxy = DatabaseProxy("guest")
proxy.query("DELETE FROM users") # Denied
```

***

#### Summary Table

| **Pattern** | **Focus**     | **Real-World Analogy**                                    |
| ----------- | ------------- | --------------------------------------------------------- |
| Adapter     | Compatibility | Travel power plug adapters.                               |
| Bridge      | Independence  | A remote control that works with any brand of TV.         |
| Composite   | Hierarchy     | A folder containing files and other folders.              |
| Decorator   | Extension     | Adding toppings to a pizza.                               |
| Facade      | Simplicity    | A "Start" button on a microwave that hides complex logic. |
| Flyweight   | Efficiency    | Using the same textures for 10,000 trees in a video game. |
| Proxy       | Control       | A credit card acting as a proxy for your bank account.    |

Behavioral design patterns are concerned with algorithms and the assignment of responsibilities between objects. They describe not just patterns of objects or classes but also the patterns of communication between them.

Here is the implementation of the most common behavioral patterns in Python.

***

#### 1. Chain of Responsibility

Intent: Passes requests along a chain of handlers. Upon receiving a request, each handler decides either to process the request or to pass it to the next handler in the chain.

Python

```python
class Handler:
    def __init__(self, next_handler=None):
        self._next_handler = next_handler

    def handle(self, request):
        if self._next_handler:
            return self._next_handler.handle(request)
        return None

class AuthHandler(Handler):
    def handle(self, request):
        if request.get("user") == "admin":
            print("Auth passed.")
            return super().handle(request)
        print("Auth failed.")
        return False

class LogHandler(Handler):
    def handle(self, request):
        print(f"Logging request: {request}")
        return super().handle(request)

# Usage
chain = AuthHandler(LogHandler())
chain.handle({"user": "admin", "data": "payload"})
```

***

#### 2. Command Pattern

Intent: Turns a request into a stand-alone object that contains all information about the request. This transformation lets you pass requests as a method arguments, delay or queue a request’s execution, and support undoable operations.

Python

```python
class Command:
    def execute(self): pass

class Light:
    def turn_on(self): print("Light is ON")
    def turn_off(self): print("Light is OFF")

class OnCommand(Command):
    def __init__(self, light): self.light = light
    def execute(self): self.light.turn_on()

class RemoteControl:
    def submit(self, command): command.execute()

# Usage
light = Light()
remote = RemoteControl()
remote.submit(OnCommand(light))
```

***

#### 3. Observer Pattern (Pub/Sub)

Intent: Lets you define a subscription mechanism to notify multiple objects about any events that happen to the object they’re observing.

Python

```python
class Subject:
    def __init__(self):
        self._observers = []

    def subscribe(self, observer):
        self._observers.append(observer)

    def notify(self, message):
        for observer in self._observers:
            observer.update(message)

class EmailUser:
    def update(self, message):
        print(f"Email sent with message: {message}")

# Usage
news_feed = Subject()
news_feed.subscribe(EmailUser())
news_feed.notify("New Design Pattern Post!")
```

***

#### 4. Strategy Pattern<sup>1</sup>

Intent: Defines a family <sup>2</sup>of algorithms, puts each of them into a separate class, and makes their objects interchangeable.

Python

```python
class PaymentStrategy:
    def pay(self, amount): pass

class CreditCard(PaymentStrategy):
    def pay(self, amount): print(f"Paid ${amount} via Credit Card")

class Bitcoin(PaymentStrategy):
    def pay(self, amount): print(f"Paid ${amount} via Bitcoin")

class ShoppingCart:
    def __init__(self, strategy):
        self.strategy = strategy

    def checkout(self, amount):
        self.strategy.pay(amount)

# Usage
cart = ShoppingCart(Bitcoin())
cart.checkout(100)
```

***

#### 5. State Pattern<sup>3</sup>

Intent: Lets an object alter its beh<sup>4</sup>avior when its internal state changes. It appears as if the object changed its class.

Python

```python
class State:
    def handle(self): pass

class PlayingState(State):
    def handle(self): print("The player is currently playing.")

class PausedState(State):
    def handle(self): print("The player is paused.")

class MusicPlayer:
    def __init__(self, state):
        self.state = state

    def set_state(self, state):
        self.state = state

    def press_button(self):
        self.state.handle()

# Usage
player = MusicPlayer(PlayingState())
player.press_button()
player.set_state(PausedState())
player.press_button()
```

***

#### 6. Template Method Pattern

Intent: Defines the skeleton of an algorithm in the superclass but lets subclasses override specific steps of the algorithm without changing its structure.

Python

```python
class DocumentGenerator:
    def generate(self):
        self.open_file()
        self.extract_data()
        self.close_file()

    def open_file(self): print("Opening file...")
    def close_file(self): print("Closing file...")
    
    # Subclasses must implement this
    def extract_data(self): pass

class PDFGenerator(DocumentGenerator):
    def extract_data(self): print("Extracting PDF data...")

# Usage
pdf = PDFGenerator()
pdf.generate()
```

***

#### 7. Mediator Pattern

Intent: Reduces chaotic dependencies between objects. The pattern restricts direct communications between the objects and forces them to collaborate only via a mediator object.

Python

```python
class ChatRoom:
    def display_message(self, user, message):
        print(f"[{user.name}]: {message}")

class User:
    def __init__(self, name, mediator):
        self.name = name
        self.mediator = mediator

    def send(self, message):
        self.mediator.display_message(self, message)

# Usage
room = ChatRoom()
bob = User("Bob", room)
alice = User("Alice", room)
bob.send("Hi Alice!")
```

***

#### 8. Memento Pattern

Intent: Lets you save and restore the previous state of an object without revealing the details of its implementation.

Python

```python
class Memento:
    def __init__(self, state): self._state = state
    def get_state(self): return self._state

class Editor:
    def __init__(self): self._state = ""
    def type(self, text): self._state += text
    def save(self): return Memento(self._state)
    def restore(self, memento): self._state = memento.get_state()

# Usage
editor = Editor()
editor.type("Hello ")
saved_state = editor.save()
editor.type("World")
editor.restore(saved_state)
print(editor._state) # Hello 
```

***

#### Summary Table

| **Pattern**             | **Use Case**                                                     |
| ----------------------- | ---------------------------------------------------------------- |
| Command                 | Undo/Redo operations, transactional logging, job queues.         |
| Strategy                | Switching sorting algorithms, different tax calculation methods. |
| Observer                | Event handling systems, real-time dashboards (Pub/Sub).          |
| State                   | Game state management (Menu -> Playing -> Game Over).            |
| Mediator                | Decoupling complex components (e.g., Air Traffic Control).       |
| Memento                 | History tracking, "Save" points in applications.                 |
| Chain of Responsibility | Middleware, event bubbling, multi-stage validation.              |
| Template Method         | Standardizing a workflow with custom steps (e.g., CI/CD).        |

***

This is a very common problem — and the good news is:

you don’t need to _memorize_ design patterns, you need to anchor them to problems your brain already understands.

\
It is perfectly normal to feel overwhelmed. There are 23 "classic" GoF (Gang of Four) patterns, and they often sound similar. The secret to remembering them isn't rote memorization—it's Visual Categorization and Problem-Pattern Mapping.

Here is a system to help you memorize them, followed by the interview probability ranking.

***

#### 🧠 1. The "Memory Palace" Strategy

Instead of remembering names, remember the Purpose of the category.

* Creational (Construction): How do I make the object? (Think of a Factory)
* Structural (Assembly): How do I glue objects together? (Think of a Circuit Board)
* Behavioral (Interaction): How do objects talk to each other? (Think of a Team of People)

***

#### 🏆 2. Interview Probability Ranking (Top 12)

If you are short on time, focus on the "S-Tier" patterns. These cover 80% of LLD interview questions.

<table data-header-hidden><thead><tr><th width="105.54046630859375"></th><th width="117.80328369140625"></th><th width="162.81317138671875"></th><th></th></tr></thead><tbody><tr><td><strong>Pattern</strong></td><td><strong>Category</strong></td><td><strong>Probability</strong></td><td><strong>Common Interview Prompt</strong></td></tr><tr><td>Strategy</td><td>Behavioral</td><td>🟢 Very High</td><td>"Design a Payment system with multiple methods."</td></tr><tr><td>Observer</td><td>Behavioral</td><td>🟢 Very High</td><td>"Design a Notification system or Stock Ticker."</td></tr><tr><td>Factory</td><td>Creational</td><td>🟢 Very High</td><td>"How would you create different types of UI buttons?"</td></tr><tr><td>Decorator</td><td>Structural</td><td>🟡 High</td><td>"Design a Pizza or Coffee shop with many toppings."</td></tr><tr><td>Singleton</td><td>Creational</td><td>🟡 High</td><td>"How do you ensure only one Database connection?"</td></tr><tr><td>State</td><td>Behavioral</td><td>🟡 High</td><td>"Design a Vending Machine or an ATM."</td></tr><tr><td>Builder</td><td>Creational</td><td>🟡 High</td><td>"How to create a complex 'User' object with 20 fields?"</td></tr><tr><td>Adapter</td><td>Structural</td><td>⚪ Medium</td><td>"How to integrate a 3rd party API with a different format?"</td></tr><tr><td>Chain of Resp.</td><td>Behavioral</td><td>⚪ Medium</td><td>"Design a Logger or a Middleware filter."</td></tr><tr><td>Facade</td><td>Structural</td><td>⚪ Medium</td><td>"How do you simplify a complex internal library?"</td></tr><tr><td>Composite</td><td>Structural</td><td>🔴 Low</td><td>"Design a File System (Folders within Folders)."</td></tr><tr><td>Proxy</td><td>Structural</td><td>🔴 Low</td><td>"How to add caching or security before a DB call?"</td></tr></tbody></table>

***

#### 💡 3. How to Remember: The "Trigger" Method

Associate each pattern with a single real-world object that triggers the memory.

**Creational Patterns**

* Singleton: The Sun (There is only one).
* Factory: A Pizza Store (You ask for a "Pepperoni" and it handles the dough/sauce).
* Builder: LEGOs (You build a complex structure piece by piece).
* Prototype: A Photocopy Machine (You copy an existing document).

**Structural Patterns**

* Adapter: A Travel Plug (Converts US plug to EU socket).
* Decorator: A Christmas Tree (You start with a tree and add ornaments/lights to it).
* Facade: A Remote Control (One button handles the complex wiring of the TV/Audio).
* Proxy: A Credit Card (It represents the money in your bank but adds a security layer).

**Behavioral Patterns**

* Strategy: A GPS/Maps App (Choose "Walk," "Drive," or "Bike" — the route algorithm changes).
* Observer: YouTube/Instagram (When a creator uploads, all subscribers get a notification).
* State: A Traffic Light (Red, Yellow, Green — behavior changes based on color).
* Command: A Restaurant Order (The waiter writes the order on a slip; it can be queued or canceled).

***

#### 🛠️ 4. The "Cheat Sheet" for LLD Coding Rounds

When you get a problem, ask yourself these three questions to identify the pattern:

1. "Does the object have a complex lifecycle or many states?" → Use State Pattern.
2. "Am I trying to support multiple algorithms/behaviors for one task?" → Use Strategy Pattern.
3. "Do I have multiple objects that need to stay in sync?" → Use Observer Pattern.
4. "Am I adding features to an object without changing its class?" → Use Decorator Pattern.

***

Would you like me to take one of these "S-Tier" patterns (like Strategy or Observer) and show you how to write it in a professional, "Interview-Perfect" style?

***
