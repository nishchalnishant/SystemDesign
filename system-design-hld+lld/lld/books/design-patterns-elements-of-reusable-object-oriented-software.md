# Design patterns  elements of reusable object oriented software

## Chapter 1 **Introduction**

**Design Challenges in Object-Oriented Software**

* Designing object-oriented software is inherently difficult.
* Creating reusable software adds another layer of complexity.
* Key tasks include identifying pertinent objects, defining class interfaces, establishing inheritance hierarchies, and determining relationships among classes.
* The goal is to design solutions specific to current problems but general enough to handle future requirements.

**Expert vs. Novice Designers**

* Expert designers avoid solving every problem from scratch by reusing proven solutions.
* These solutions, referred to as "design patterns," streamline the design process.
* Novice designers often lack this experience and may resort to non-object-oriented techniques, hindering the potential of object-oriented design.

**The Role of Design Patterns**

* Design patterns encapsulate best practices and recurring solutions in object-oriented design.
* They help in achieving flexible, elegant, and reusable designs.
* Patterns provide a shorthand for experienced designers to communicate solutions effectively and to apply tried-and-true methods to new problems.

**Analogies and Importance**

* The book draws an analogy between patterns in software design and plot patterns in literature (e.g., "Tragically Flawed Hero" in Shakespeare's plays).
* Just as literary patterns help in crafting stories, design patterns assist in creating robust software architectures.

**Capturing Design Experience**

* One of the main purposes of the book is to document these design patterns for easy reference and application.
* Each pattern is systematically named, explained, and evaluated, making it accessible and useful for software developers.

**Conclusion**

* Understanding and utilizing design patterns is crucial for creating efficient and reusable object-oriented software.
* This book aims to bridge the knowledge gap between novice and expert designers by providing a comprehensive catalog of design patterns .

These notes cover the main points from the first chapter, highlighting the challenges, the role of design patterns, and the book's goal of documenting these patterns for practical use.





## Chapter 2 In depth study



provides an in-depth case study on designing a "What-You-See-Is-What-You-Get" (WYSIWYG) document editor called Lexi. The chapter is divided into several sections, each addressing a specific design problem and demonstrating how various design patterns can be applied to solve these problems effectively.

#### 2.1 Design Problems

This section outlines seven primary design challenges in developing Lexi:

1. **Document Structure**: The internal representation of the document impacts editing, formatting, displaying, and text analysis. The design must allow efficient traversal of the document structure.
2. **Formatting**: Determining how text and graphics are arranged into lines and columns involves deciding which objects handle different formatting policies and how these policies interact with the document's structure.
3. **Embellishing the User Interface**: Adding and removing embellishments like scroll bars, borders, and drop shadows should be easy without affecting the rest of the application.
4. **Supporting Multiple Look-and-Feel Standards**: Lexi should be adaptable to various look-and-feel standards (e.g., Motif, Presentation Manager) without significant modifications.
5. **Supporting Multiple Window Systems**: The design should be as independent of the window system as possible to accommodate different look-and-feel standards implemented on different window systems.
6. **User Operations**: Providing a uniform mechanism for accessing and undoing functionality scattered throughout the application is crucial. This involves user interfaces like buttons and pull-down menus.
7. **Spelling Checking and Hyphenation**: Supporting analytical operations such as spell checking and hyphenation requires minimizing the number of class modifications needed to add new operations.

#### 2.2 Document Structure

A document in Lexi is an arrangement of basic graphical elements (characters, lines, polygons, etc.). Authors view these elements in terms of the document's physical structure (lines, columns, figures, tables), and these substructures have their own substructures. The challenge is to design a document structure that supports this hierarchical view, allowing users to manipulate these substructures directly. This section introduces the **Composite Pattern** to handle the recursive composition of graphical elements into complex documents.

#### 2.3 Formatting

Formatting involves arranging text and graphics into a layout suitable for display. Different formatting policies (e.g., justification, pagination) require different objects to manage these policies. The **Strategy Pattern** is applied to encapsulate these formatting algorithms, allowing them to be interchanged easily without affecting the document's structure.

#### 2.4 Embellishing the User Interface

User interface embellishments should be added or removed dynamically. The **Decorator Pattern** is ideal for this purpose as it allows behavior to be added to individual objects, making it easy to add embellishments like borders and scroll bars without altering the underlying object structure.

#### 2.5 Supporting Multiple Look-and-Feel Standards

To support multiple look-and-feel standards, the **Abstract Factory Pattern** is employed. This pattern provides an interface for creating families of related or dependent objects without specifying their concrete classes, making it easier to adapt Lexi to different look-and-feel standards.

#### 2.6 Supporting Multiple Window Systems

Similar to look-and-feel standards, the **Bridge Pattern** is used to decouple the abstraction from its implementation, allowing the two to vary independently. This makes Lexi's design more flexible and portable across different window systems.

#### 2.7 User Operations

The **Command Pattern** is introduced to encapsulate user requests as objects, providing a way to parameterize clients with operations, queue operations, and support undoable operations. This pattern helps in managing user operations uniformly and enables undo functionality.

#### 2.8 Spelling Checking and Hyphenation

For operations like spell checking and hyphenation, the **Visitor Pattern** is useful. This pattern allows you to add further operations to objects without having to modify them, making it easier to extend the functionalities related to document analysis.

#### Summary

By the end of the chapter, the reader is familiarized with eight design patterns through practical application in designing Lexi. These patterns include Composite, Strategy, Decorator, Abstract Factory, Bridge, Command, and Visitor, each solving a specific problem in the document editor's design.

These design patterns collectively contribute to a flexible, maintainable, and scalable architecture for the document editor, demonstrating the power and utility of design patterns in software engineering.



## Chapter 3 **Introduction to Creational Patterns**

**Creational Design Patterns** focus on the process of object creation. They abstract the instantiation process, making a system independent of how its objects are created, composed, and represented. They offer significant flexibility in what gets created, who creates it, how it gets created, and when it gets created.

**Themes**:

1. **Encapsulation of Knowledge**: Creational patterns encapsulate knowledge about which concrete classes the system uses.
2. **Hiding Creation Logic**: They hide how instances of these classes are created and put together, exposing only interfaces defined by abstract classes.

**Comparison**:

* Creational patterns vary in whether they use class inheritance (e.g., Factory Method) or delegate instantiation to another object (e.g., Abstract Factory, Builder, Prototype, Singleton).

**Creational Patterns Overview**

The chapter discusses five creational patterns:

1. **Abstract Factory**
2. **Builder**
3. **Factory Method**
4. **Prototype**
5. **Singleton**

Each pattern is explored using the example of a maze game, which helps illustrate their implementations and differences.

**1. Abstract Factory**

**Intent**: Provide an interface for creating families of related or dependent objects without specifying their concrete classes.

**Motivation**: Useful in a user interface toolkit supporting multiple look-and-feel standards (e.g., Motif, Presentation Manager). By abstracting widget creation, the application can switch between different look-and-feel standards easily.

**Implementation**:

* Define an abstract `WidgetFactory` class with operations for creating each kind of widget.
* Concrete subclasses implement the operations to create widgets for a specific look-and-feel.

**Example**:

* `WidgetFactory` interface creates widgets (scroll bars, windows, buttons).
* Concrete factories (`MotifWidgetFactory`, `PMWidgetFactory`) implement the creation methods for specific styles.

**2. Builder**

**Intent**: Separate the construction of a complex object from its representation, allowing the same construction process to create different representations.

**Motivation**: Useful when creating complex objects with numerous optional components or configurations.

**Implementation**:

* A `Builder` abstract class defines the construction steps.
* Concrete builders implement these steps to construct and assemble parts of the product.
* A `Director` object controls the construction process.

**Example**:

* `MazeBuilder` abstract class defines steps to build rooms, doors, walls.
* Concrete builders (`StandardMazeBuilder`, `ComplexMazeBuilder`) provide specific implementations.
* `MazeGame` class (Director) orchestrates the construction process.

**3. Factory Method**

**Intent**: Define an interface for creating an object, but let subclasses alter the type of objects that will be created.

**Motivation**: Useful for frameworks where the core library needs to instantiate classes but cannot anticipate which class to instantiate.

**Implementation**:

* The `Creator` class declares the factory method.
* Concrete subclasses override the factory method to return an instance of a `Product` subclass.

**Example**:

* `MazeGame` class defines a factory method for creating `Room`, `Wall`, `Door`.
* Subclasses (`BombedMazeGame`, `EnchantedMazeGame`) override this method to create specific room types.

**4. Prototype**

**Intent**: Specify the kinds of objects to create using a prototypical instance, and create new objects by copying this prototype.

**Motivation**: Useful when the classes to instantiate are specified at runtime.

**Implementation**:

* An abstract `Prototype` class declares a `Clone` method.
* Concrete prototypes implement the `Clone` method.

**Example**:

* A `GraphicTool` can be parameterized by prototypes of `Graphic` subclasses.
* When a new `Graphic` is needed, the tool clones the prototype.

**5. Singleton**

**Intent**: Ensure a class has only one instance, and provide a global point of access to it.

**Motivation**: Useful when exactly one object is needed to coordinate actions across the system.

**Implementation**:

* A class method ensures only one instance of the class is created.
* Provides a global point of access to the instance.

**Example**:

* `MazeFactory` could be a singleton to ensure all maze components are created through a single factory instance.

#### Key Takeaways

* Creational patterns provide a variety of ways to handle object creation, promoting flexibility and reuse.
* They emphasize encapsulating the knowledge about which concrete classes the system uses.
* By hiding the creation process, they make it easier to manage and extend the system over time.

These notes cover the essential aspects and comparisons of the creational patterns discussed in Chapter 3, providing a comprehensive overview of their intentions, motivations, and implementations.





## Chapter 4 Structural patterns

**Overview**

Chapter 4 discusses **Structural Patterns**, which focus on how classes and objects can be combined to form larger structures. These patterns simplify the design by identifying a simple way to realize relationships between entities.

**Key Concepts**

1. **Structural Class Patterns**:
   * **Inheritance**: Used to compose interfaces or implementations.
   * **Multiple Inheritance**: Combines properties of parent classes, useful for integrating independently developed class libraries.
   * **Adapter Pattern (Class Form)**: Makes one interface conform to another. A class adapter achieves this by privately inheriting from the adaptee class and defining its interface in terms of the adaptee's interface.
2. **Structural Object Patterns**:
   * **Object Composition**: Describes ways to combine objects to create new functionality. Offers flexibility as the composition can be altered at runtime, unlike static class composition.
   * **Composite Pattern**: Builds a class hierarchy consisting of primitive and composite objects, enabling the creation of complex structures from simpler ones.
   * **Proxy Pattern**: Provides a surrogate or placeholder for another object. Useful in scenarios like remote proxies, virtual proxies (loading large objects on demand), and protection proxies (access control).
   * **Flyweight Pattern**: Shares objects to enhance efficiency and consistency. Objects must be context-independent to be shared effectively.
   * **Facade Pattern**: Simplifies interactions with a subsystem by providing a single interface.
   * **Bridge Pattern**: Decouples an abstraction from its implementation, allowing them to vary independently.

**Detailed Patterns**

1. **Composite Pattern**:
   * **Intent**: Compose objects into tree structures to represent part-whole hierarchies.
   * **Applicability**:
     * To represent hierarchies of objects.
     * When clients should treat individual objects and compositions uniformly.
   * **Participants**:
     * **Component**: Declares the interface for objects in the composition and implements default behavior.
     * **Leaf**: Represents leaf objects in the composition.
     * **Composite**: Defines behavior for components having children and stores child components.
   * **Collaborations**: Clients interact with objects through the Component interface, treating both Leaf and Composite uniformly.
2. **Adapter Pattern**:
   * **Intent**: Convert the interface of a class into another interface clients expect.
   * **Class Adapter**: Uses multiple inheritance to adapt one interface to another.
     * Example: A `TextShape` class inherits both `Shape` and `TextView` to provide a uniform interface.
   * **Object Adapter**: Uses composition to adapt interfaces. The adapter holds an instance of the class it wraps.
     * Example: `TextShape` containing a `TextView` instance to adapt its interface to `Shape`.
3. **Bridge Pattern**:
   * **Intent**: Separate an object’s abstraction from its implementation so that they can be varied independently.
   * **Participants**:
     * **Abstraction**: Defines the abstraction's interface.
     * **Implementor**: Defines the interface for implementation classes.
     * **ConcreteImplementor**: Implements the Implementor interface.
     * **RefinedAbstraction**: Extends the Abstraction interface.
   * **Collaborations**: Abstraction forwards client requests to the Implementor object.
4. **Facade Pattern**:
   * **Intent**: Provide a unified interface to a set of interfaces in a subsystem, making the subsystem easier to use.
   * **Participants**:
     * **Facade**: Knows which subsystem classes are responsible for a request.
     * **Subsystem Classes**: Implement subsystem functionality, handling work assigned by the Facade.
5. **Flyweight Pattern**:
   * **Intent**: Use sharing to support large numbers of fine-grained objects efficiently.
   * **Participants**:
     * **Flyweight**: Declares an interface through which flyweights can receive and act on extrinsic state.
     * **ConcreteFlyweight**: Implements the Flyweight interface and stores intrinsic state.
     * **FlyweightFactory**: Creates and manages flyweight objects.
     * **Client**: Maintains references to flyweights and computes or stores extrinsic state.
6. **Proxy Pattern**:
   * **Intent**: Provide a surrogate or placeholder for another object to control access to it.
   * **Participants**:
     * **Proxy**: Maintains a reference to the real subject and controls access to it.
     * **Subject**: Defines the common interface for RealSubject and Proxy.
     * **RealSubject**: Implements the Subject interface and defines the real object.

**Summary**

Structural patterns help in designing flexible and reusable object-oriented software by focusing on how classes and objects can be composed to form larger structures. These patterns decouple the abstract aspects of the design from the implementation details, promoting code reusability and flexibility.

***

These notes are a high-level summary and analysis of the key points from Chapter 4 on Structural Patterns in "Design Patterns: Elements of Reusable Object-Oriented Software". They cover the intent, applicability, participants, and collaborations of each discussed pattern【6:0†source】【6:1†source】【6:2†source】【6:3†source】【6:4†source】【6:5†source】.



## Chapter 5 Behavioral patterns

**Overview:** Behavioral patterns focus on the interaction and responsibility among objects, describing not only patterns of objects or classes but also the patterns of communication between them. They help manage complex control flows and are classified into two types: behavioral class patterns and behavioral object patterns.

**Behavioral Class Patterns**

* **Template Method:** This pattern defines the skeleton of an algorithm in a method, deferring some steps to subclasses. It allows subclasses to redefine certain steps without changing the algorithm's structure.
* **Interpreter:** Represents a grammar as a class hierarchy and implements an interpreter as an operation on instances of these classes.

**Behavioral Object Patterns**

* **Chain of Responsibility:** Avoids coupling the sender of a request to its receiver by giving multiple objects a chance to handle the request. The request is passed along a chain until an object handles it.
* **Command:** Encapsulates a request as an object, thereby allowing users to parameterize clients with queues, requests, and operations.
* **Iterator:** Provides a way to access the elements of an aggregate object sequentially without exposing its underlying representation.
* **Mediator:** Defines an object that encapsulates how a set of objects interact, promoting loose coupling by preventing objects from referring to each other explicitly.
* **Memento:** Without violating encapsulation, captures and externalizes an object's internal state so that the object can be restored to this state later.
* **Observer:** Defines a one-to-many dependency between objects so that when one object changes state, all its dependents are notified and updated automatically.
* **State:** Allows an object to alter its behavior when its internal state changes, appearing to change its class.
* **Strategy:** Defines a family of algorithms, encapsulates each one, and makes them interchangeable. It allows the algorithm to vary independently from clients that use it.
* **Visitor:** Represents an operation to be performed on the elements of an object structure. It allows one to define a new operation without changing the classes of the elements on which it operates.

#### Detailed Pattern Descriptions:

1. **Chain of Responsibility**
   * **Intent:** Decouple the sender of a request from its receiver by giving multiple objects a chance to handle the request. Chain the receiving objects and pass the request along the chain until an object handles it.
   * **Structure:**
     * Handler: Defines an interface for handling requests and optionally implements the successor link.
     * ConcreteHandler: Handles requests it is responsible for and can access its successor. It forwards the request if it does not handle it.
     * Client: Initiates the request to a ConcreteHandler object in the chain.
   * **Consequences:**
     * Reduces coupling between sender and receiver.
     * Adds flexibility in assigning responsibilities to objects.
     * Receivers are not guaranteed to handle the request.
2. **Command**
   * **Intent:** Encapsulate a request as an object, thereby allowing for parameterization of clients with queues, requests, and operations.
   * **Structure:**
     * Command: Declares an interface for executing an operation.
     * ConcreteCommand: Defines a binding between a Receiver object and an action, implementing Execute by invoking the corresponding operation(s) on Receiver.
     * Client: Creates a ConcreteCommand object and sets its receiver.
     * Invoker: Asks the command to carry out the request.
     * Receiver: Knows how to perform the operations associated with carrying out a request.
   * **Consequences:**
     * Decouples the object that invokes the operation from the one that knows how to perform it.
     * Allows for the creation of command queues, and logging changes can support undo/redo operations.
3. **Iterator**
   * **Intent:** Provide a way to access the elements of an aggregate object sequentially without exposing its underlying representation.
   * **Structure:**
     * Iterator: Defines an interface for accessing and traversing elements.
     * ConcreteIterator: Implements the Iterator interface and keeps track of the current position in the traversal.
     * Aggregate: Defines an interface for creating an Iterator object.
     * ConcreteAggregate: Implements the Iterator creation interface and returns an instance of the appropriate ConcreteIterator.
   * **Consequences:**
     * Supports variations in the traversal of an aggregate.
     * Simplifies the aggregate interface.
4. **Mediator**
   * **Intent:** Define an object that encapsulates how a set of objects interact, promoting loose coupling by keeping objects from referring to each other explicitly.
   * **Structure:**
     * Mediator: Defines an interface for communicating with Colleague objects.
     * ConcreteMediator: Implements cooperative behavior by coordinating Colleague objects and knows and maintains its colleagues.
     * Colleague classes: Each Colleague class knows its Mediator object and communicates with it whenever it would have otherwise communicated with another Colleague.
   * **Consequences:**
     * Limits subclassing.
     * Decouples colleagues.
     * Simplifies object protocols.
5. **Memento**
   * **Intent:** Without violating encapsulation, capture and externalize an object's internal state so that the object can be restored to this state later.
   * **Structure:**
     * Memento: Stores the internal state of the Originator object.
     * Originator: Creates a memento containing a snapshot of its current internal state and uses the memento to restore its internal state.
     * Caretaker: Responsible for the memento's safekeeping.
   * **Consequences:**
     * Preserves encapsulation boundaries.
     * Simplifies the Originator.
6. **Observer**
   * **Intent:** Define a one-to-many dependency between objects so that when one object changes state, all its dependents are notified and updated automatically.
   * **Structure:**
     * Subject: Knows its observers and provides an interface for attaching and detaching observer objects.
     * Observer: Defines an updating interface for objects that should be notified of changes in a subject.
     * ConcreteSubject: Stores state of interest to ConcreteObserver objects and sends a notification to its observers when its state changes.
     * ConcreteObserver: Maintains a reference to a ConcreteSubject object and implements the Observer updating interface to keep its state consistent with the subject's.
   * **Consequences:**
     * Abstracts the coupling between Subject and Observer.
     * Supports broadcast communication.
7. **State**
   * **Intent:** Allow an object to alter its behavior when its internal state changes. The object will appear to change its class.
   * **Structure:**
     * Context: Maintains an instance of a ConcreteState subclass that defines the current state.
     * State: Defines an interface for encapsulating the behavior associated with a particular state of the Context.
     * ConcreteState subclasses: Each subclass implements a behavior associated with a state of the Context.
   * **Consequences:**
     * Localizes state-specific behavior and partitions behavior for different states.
8. **Strategy**
   * **Intent:** Define a family of algorithms, encapsulate each one, and make them interchangeable. Strategy lets the algorithm vary independently from clients that use it.
   * **Structure:**
     * Strategy: Declares an interface common to all supported algorithms.
     * ConcreteStrategy: Implements the algorithm using the Strategy interface.
     * Context: Maintains a reference to a Strategy object and configures itself with a ConcreteStrategy object.
   * **Consequences:**
     * Families of related algorithms.
     * Simplifies unit testing.
9. **Visitor**
   * **Intent:** Represent an operation to be performed on elements of an object structure. Visitor lets you define a new operation without changing the classes of the elements on which it operates.
   * **Structure:**
     * Visitor: Declares a Visit operation for each class of ConcreteElement in the object structure.
     * ConcreteVisitor: Implements each operation declared by Visitor.
     * Element: Defines an Accept operation that takes a visitor as an argument.
     * ConcreteElement: Implements an Accept operation that takes a visitor as an argument.
     * ObjectStructure: Can enumerate its elements and may provide a high-level interface to allow the visitor to visit its elements.
   * **Consequences:**
     * Makes adding new operations easy.
     * Gathers related operations and separates unrelated ones.

Chapter 5 of "Design Patterns: Elements of Reusable Object-Oriented Software" delves deeply into the behavioral patterns that provide solutions to common problems of object-oriented design related to responsibilities and communication among objects



## Chapter 6 **Conclusion**

**Overview**

* The authors acknowledge that the book does not introduce new algorithms or programming techniques, nor does it offer a rigorous method for designing systems or develop a new theory of design.
* The primary goal is to document existing design patterns, giving standard names and definitions for techniques used in object-oriented design.
* This documentation is important for improving design patterns and facilitating the development of new ones.

**The Importance of Cataloging Design Patterns**

* Design patterns provide a common vocabulary for designers, making it easier to communicate, document, and explore design alternatives.
* A standard terminology for design patterns helps in reducing complexity by allowing discussion at a higher level of abstraction.
* Once designers become familiar with these patterns, their design vocabulary changes, enabling more efficient communication.

**Impact on Design Practices**

1. **Common Design Vocabulary**
   * Design patterns are organized around larger conceptual structures like algorithms, data structures, and idioms rather than just syntax.
   * They facilitate easier communication among designers by raising the level of abstraction.
   * Familiarity with these patterns enables designers to discuss designs more effectively and intuitively.
2. **Documentation and Learning Aid**
   * Understanding design patterns makes it easier to grasp existing object-oriented systems, especially large ones.
   * Patterns help demystify the use of inheritance and flow of control in complex systems.
   * Learning these patterns accelerates the process of becoming proficient in object-oriented design, bridging the gap between novice and expert designers.
   * Describing a system in terms of design patterns used can simplify the understanding of its architecture.
3. **Adjunct to Existing Methods**
   * Design patterns complement object-oriented design methods by providing solutions to common problems.
   * They aid in transitioning from an analysis model to an implementation model, addressing practical issues not covered by analysis models alone.
   * The inclusion of design patterns helps in capturing more of the "why" behind design decisions.
4. **A Target for Refactoring**
   * Reusable software often requires reorganization or refactoring, and design patterns guide this process.
   * The software lifecycle involves phases of prototyping, expansion, and consolidation.
   * During the expansionary phase, software evolves to meet new requirements but can become inflexible.
   * The consolidation phase involves refactoring to produce reusable frameworks, moving from inheritance-based reuse to composition-based reuse.

**Future of Design Patterns**

* The authors hope this book will initiate a movement to document the expertise of software practitioners.
* They emphasize the need for more types of patterns beyond design patterns, such as analysis patterns, user interface design patterns, and performance-tuning patterns.
* The ultimate goal is to foster a community around the documentation and evolution of design patterns.

**Invitation and Final Thoughts**

* The authors invite readers to contribute to the pattern community by finding and cataloging new patterns.
* They express hope that the book will serve as a foundational reference for both novice and experienced designers, promoting a deeper understanding and better design practices in software engineering.

These notes summarize the key points and insights provided in Chapter 6 of "Design Patterns: Elements of Reusable Object-Oriented Software"

