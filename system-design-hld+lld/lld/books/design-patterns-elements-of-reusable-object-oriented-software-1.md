# Design Patterns: Elements of Reusable Object-Oriented Software

## Chapter 1 of _Design Patterns: Elements of Reusable Object-Oriented Software_:

***

#### 1. **Introduction to Design Patterns**

* **Purpose**: To make reusable and flexible object-oriented software by reusing proven design solutions.
* **Challenge**: Designing reusable software involves finding pertinent objects, defining classes and inheritance hierarchies, and establishing relationships between them.
* **Expert Knowledge**: Experienced designers avoid solving problems from scratch and instead reuse solutions that have worked in the past, often based on "design patterns" that capture best practices.

#### 2. **What is a Design Pattern?**

* Defined by four elements:
  1. **Pattern Name**: A label to describe a design solution.
  2. **Problem**: The context and situations where the pattern is applicable.
  3. **Solution**: Abstract relationships, responsibilities, and collaborations.
  4. **Consequences**: Results and trade-offs of using the pattern.
* Design patterns are higher-level abstractions and do not include domain-specific designs, focusing instead on general reusable solutions.

#### 3. **Example: Design Patterns in Smalltalk MVC**

* **MVC** (Model-View-Controller) separates an application’s data (Model), user interface (View), and user interaction (Controller).
  * **Observer Pattern**: Decouples Model and View by notifying views of changes in the model.
  * **Composite Pattern**: Allows views to be nested, treating groups of views uniformly.
  * **Strategy Pattern**: Uses Controller to define different input-response strategies for views.

#### 4. **Describing Design Patterns**

* **Template**: Patterns are documented using a consistent template that includes:
  * **Pattern Name and Classification**
  * **Intent**: The purpose and goals of the pattern.
  * **Motivation**: An example scenario.
  * **Applicability**: When and where the pattern should be used.
  * **Structure**: Diagram of the pattern structure.
  * **Participants and Collaborations**
  * **Consequences and Implementation**
  * **Sample Code, Known Uses, and Related Patterns**

#### 5. **The Catalog of Design Patterns**

* **Three Categories**:
  * **Creational Patterns**: Deal with object creation mechanisms.
  * **Structural Patterns**: Organize classes and objects.
  * **Behavioral Patterns**: Characterize communication between objects.
* **23 Patterns**: Includes patterns such as Abstract Factory, Singleton, Adapter, Composite, Observer, and Strategy.

#### 6. **Organizing the Catalog**

* Patterns are classified by their **purpose** (Creational, Structural, Behavioral) and **scope** (Class vs. Object).
* **Related Patterns**: Relationships between patterns help in selecting combinations.

#### 7. **How Design Patterns Solve Design Problems**

* **Finding Appropriate Objects**: Patterns help identify suitable abstractions.
* **Determining Object Granularity**: Patterns like Facade and Flyweight address the size and number of objects.
* **Specifying Object Interfaces**: Patterns define essential elements of interfaces, such as the Memento and Decorator patterns.
* **Specifying Object Implementations**: Patterns encourage programming to an interface rather than an implementation, reducing dependencies.
* **Object Composition over Inheritance**: Composition promotes flexibility by allowing dynamic object composition.

#### 8. **Programming to an Interface, Not an Implementation**

* This principle emphasizes that clients should depend on abstract interfaces, not specific classes, which promotes flexibility.
* **Creational Patterns** like Abstract Factory and Singleton allow the system to depend on interfaces for instantiation.

#### 9. **Reuse Mechanisms: Inheritance vs. Composition**

* **Inheritance** (white-box reuse): Allows new classes based on existing ones but has limitations in flexibility.
* **Composition** (black-box reuse): Assembles objects to create complex functionality, fostering encapsulation and runtime flexibility.
* **Delegation**: Allows one object to handle a request by passing it to another, promoting reuse without tight coupling.

#### 10. **Designing for Change**

* Design patterns make systems more adaptable by anticipating changes.
  * **Creating objects indirectly**: Using patterns like Abstract Factory and Factory Method to avoid committing to a specific class for object creation.
  * **Reducing direct dependency on operations**: Patterns like Strategy and Command allow flexibility by encapsulating specific operations in separate objects.
  * **Managing object interactions and dependencies**: Patterns like Observer and Mediator simplify dependencies and make systems more modular.

***

This summary provides a framework for understanding the fundamental concepts in Chapter 1 and how design patterns support modular, reusable software design.





## Chapter 2, _A Case Study: Designing a Document Editor_, from _Design Patterns: Elements of Reusable Object-Oriented Software_:

***

#### 1. **Overview**

* **Purpose**: Demonstrates design patterns in a case study of a WYSIWYG document editor, Lexi.
* **Key Challenge**: Develop a flexible and efficient design that supports editing, displaying, and analyzing text and graphics.
* **Patterns Explored**: Introduces eight design patterns through solutions to Lexi’s design problems.

#### 2. **Design Problems**

* **Document Structure**: How to represent documents internally for easy manipulation.
* **Formatting**: How Lexi organizes text and graphics into structured layouts.
* **Embellishments**: Implementing UI features like scroll bars, borders, and shadows without major changes.
* **Multiple Look-and-Feel Standards**: Support different user interface styles (e.g., Motif, Presentation Manager).
* **Multiple Window Systems**: Ensure Lexi operates across various OS window systems.
* **User Operations**: Centralize and manage user commands, including undo/redo.
* **Text Analysis**: Integrate features like spell-checking and hyphenation.

#### 3. **Document Structure**

* **Objective**: Build a hierarchical structure to support text, graphics, and layout elements.
* **Solution**: Use **Composite Pattern**.
* **Implementation**:
  * Define a `Glyph` class representing document components (characters, images).
  * Use subclasses for text elements, rows, and columns, enabling a flexible structure.
  * Allow nesting of simple elements (characters) and complex elements (tables, columns) using recursive composition .

#### 4. **Formatting**

* **Objective**: Enable Lexi to organize content based on different line-breaking and layout rules.
* **Solution**: Use **Strategy Pattern** to encapsulate formatting algorithms.
* **Implementation**:
  * Define a `Compositor` class to encapsulate formatting strategies (e.g., simple layout, TeX formatting).
  * Composition objects organize glyphs and format them with a selected compositor, supporting flexible layout strategies .

#### 5. **Embellishing the User Interface**

* **Objective**: Allow easy addition/removal of UI elements (e.g., borders, scroll bars).
* **Solution**: Use **Decorator Pattern** to dynamically add features.
* **Implementation**:
  * Use `Border` and `ScrollBar` as decorators for Lexi's main document interface.
  * Enables the application of multiple embellishments without altering the main UI classes .

#### 6. **Supporting Multiple Look-and-Feel Standards**

* **Objective**: Make Lexi adaptable to various interface standards.
* **Solution**: Use **Abstract Factory Pattern** to create interface elements based on the selected look-and-feel.
* **Implementation**:
  * Define an `AbstractFactory` class to create widgets like `Button`, `ScrollBar`, etc.
  * Configure the factory to generate widgets specific to the chosen UI standard, providing a cohesive look-and-feel .

#### 7. **Supporting Multiple Window Systems**

* **Objective**: Ensure Lexi’s functionality across different OS window systems.
* **Solution**: Use **Bridge Pattern** to separate windowing interface from system-specific implementations.
* **Implementation**:
  * `Window` class provides common operations (draw, resize).
  * `WindowImp` class encapsulates system-dependent details, allowing Lexi to work across platforms .

#### 8. **User Operations (Commands)**

* **Objective**: Provide a centralized mechanism for user actions and enable undo/redo functionality.
* **Solution**: Use **Command Pattern** to encapsulate user actions as objects.
* **Implementation**:
  * Define `Command` class with `Execute` and `Unexecute` for reversible operations.
  * Commands (e.g., `FontChangeCommand`) manage their state, allowing undo/redo by tracking command history .

#### 9. **Spelling Checking and Hyphenation**

* **Objective**: Support document analysis operations (e.g., spell-checking) that traverse document structure.
* **Solution**: Use **Visitor Pattern** to apply operations across the document structure without altering it.
* **Implementation**:
  * Define a `Visitor` class for analysis, e.g., `SpellingVisitor`, `HyphenationVisitor`.
  * Allows Lexi to apply new analyses by adding Visitor subclasses, making the system extensible without changing existing document structures .

#### 10. **Summary**

* **Design Principles**: Encapsulation, separation of concerns, and use of design patterns support flexibility and extensibility.
* **Result**: Lexi’s design allows new features and UI elements to be added with minimal impact on existing code.

***

These notes provide a structured overview of how design patterns address specific issues in the design of Lexi, a flexible and feature-rich document editor.





## Chapter 3, _Creational Patterns_, from _Design Patterns: Elements of Reusable Object-Oriented Software_:

***

#### 1. **Introduction to Creational Patterns**

* **Purpose**: Encapsulate object creation mechanisms to make systems independent of how objects are created, composed, and represented.
* **Types**: Divided into Class and Object creational patterns:
  * **Class Creational**: Use inheritance to alter the instantiation.
  * **Object Creational**: Delegate instantiation to another object.

#### 2. **Themes in Creational Patterns**

* **Encapsulation of Class Knowledge**: Hide concrete class knowledge and focus on abstract classes/interfaces.
* **Flexible Object Creation**: Decouple the creation of objects from their use to allow configuration changes at runtime or compile-time.
* **Dynamic Composition over Static Inheritance**: Prefer creating object behaviors dynamically rather than relying on hard-coded inheritance.

#### 3. **Five Creational Patterns in Focus**

* **Abstract Factory**: Provides an interface for creating families of related objects without specifying their concrete classes.
* **Builder**: Separates the construction of a complex object from its representation, allowing the same construction process to create different representations.
* **Factory Method**: Defines an interface for creating an object, allowing subclasses to decide which class to instantiate.
* **Prototype**: Specifies kinds of objects using a prototypical instance, enabling creation by copying a prototype.
* **Singleton**: Ensures a class has only one instance and provides a global access point to it.

#### 4. **Example Problem: Maze Game Creation**

* **Context**: Illustrates different creational patterns by designing a maze game.
* **Maze Components**: Defines components like `Room`, `Door`, and `Wall`.
* **Challenge**: Ensuring maze parts are flexible and support future modifications without changing the creation code.

#### 5. **Details of Each Creational Pattern**

* **Abstract Factory**:
  * **Intent**: Creates families of related objects (e.g., Room, Door) without requiring the exact classes.
  * **Implementation**: Uses factory objects to generate products for each family (e.g., `MazeFactory`).
  * **Benefit**: Supports different configurations like enchanted or regular mazes by changing the factory.
* **Builder**:
  * **Intent**: Separates the creation of a complex object (e.g., a maze with many rooms and doors) from its representation.
  * **Implementation**: Director class orchestrates construction through a `Builder` interface.
  * **Use Case**: Allows creation of different representations of complex objects, such as a maze with various layouts.
* **Factory Method**:
  * **Intent**: Defines a method for creating objects, but lets subclasses specify the type.
  * **Implementation**: Subclasses override a `createProduct` method to specify the object created.
  * **Example**: `MazeGame` uses a `createMaze` method to instantiate a maze, allowing variations by subclassing `MazeGame`.
* **Prototype**:
  * **Intent**: Create new objects by copying an existing object (prototype).
  * **Implementation**: Objects implement a `clone` method to return a copy.
  * **Benefit**: Simplifies object creation when object configurations are complex or may vary widely.
* **Singleton**:
  * **Intent**: Ensure a single instance of a class exists.
  * **Implementation**: Restricts instantiation, often through a private constructor and a static access method.
  * **Use Case**: Suitable for global resources like configuration settings or connection pools.

#### 6. **Comparison of Creational Patterns**

* **Use Cases**:
  * **Abstract Factory** and **Builder** are suitable for complex structures involving multiple object families.
  * **Prototype** and **Singleton** work well for specific instances and managing state across an application.
* **Complementary Patterns**: Can be used together; e.g., **Builder** with **Prototype** for complex objects with predefined templates.

#### 7. **Key Takeaways**

* **Flexibility and Extensibility**: Creational patterns increase flexibility by enabling dynamic configuration.
* **Decoupling**: Helps reduce dependencies by focusing on interfaces rather than concrete classes.
* **Customization**: Each pattern addresses specific customization needs, from shared resources to complex object configurations.

***

This summary captures the main principles and implementations of the creational patterns, with examples from the maze game case study to illustrate their application【14:0†source】.



## Chapter 4, _Structural Patterns_, from _Design Patterns: Elements of Reusable Object-Oriented Software_:

***

#### 1. **Introduction to Structural Patterns**

* **Purpose**: Focuses on the composition of classes and objects to form larger structures.
* **Categories**:
  * **Class Structural Patterns**: Use inheritance to combine interfaces and implementations.
  * **Object Structural Patterns**: Use object composition to define new functionality dynamically at runtime.

#### 2. **Overview of Structural Patterns**

* **Examples**:
  * **Adapter**: Makes interfaces compatible.
  * **Bridge**: Separates an object’s abstraction from its implementation.
  * **Composite**: Creates tree structures to represent part-whole hierarchies.
  * **Decorator**: Adds responsibilities to objects dynamically.
  * **Facade**: Provides a simplified interface to a subsystem.
  * **Flyweight**: Shares objects to support large numbers of fine-grained instances efficiently.
  * **Proxy**: Controls access to another object.

#### 3. **Adapter Pattern**

* **Intent**: Converts the interface of a class into another interface clients expect, allowing classes with incompatible interfaces to work together.
* **Types**:
  * **Class Adapter**: Uses multiple inheritance to adapt interfaces.
  * **Object Adapter**: Uses composition to achieve adaptation.
* **Use Case**: Integrating legacy code with new interfaces.

#### 4. **Bridge Pattern**

* **Intent**: Decouples an abstraction from its implementation, allowing both to vary independently.
* **Structure**:
  * Separates the abstraction (e.g., a shape) from the implementation details (e.g., drawing API).
* **Use Case**: Use when you want to avoid a permanent binding between an abstraction and its implementation.

#### 5. **Composite Pattern**

* **Intent**: Composes objects into tree structures to represent part-whole hierarchies.
* **Implementation**:
  * Defines a `Component` interface, with `Leaf` and `Composite` implementing it to allow uniform treatment of individual and grouped objects.
* **Use Case**: Suitable for representing hierarchies such as files and directories.

#### 6. **Decorator Pattern**

* **Intent**: Attaches additional responsibilities to an object dynamically.
* **Implementation**:
  * Uses composition instead of inheritance to add behavior to objects at runtime.
  * `Decorator` class wraps a `Component` to extend functionality without modifying the base class.
* **Use Case**: Use to add features incrementally without creating subclasses for every possible feature combination.

#### 7. **Facade Pattern**

* **Intent**: Provides a unified, simplified interface to a complex subsystem.
* **Implementation**:
  * The `Facade` object represents the subsystem by exposing high-level methods that encapsulate subsystem complexity.
* **Use Case**: Useful in simplifying complex APIs for ease of use by clients.

#### 8. **Flyweight Pattern**

* **Intent**: Uses sharing to support efficient use of large numbers of fine-grained objects.
* **Implementation**:
  * Separates intrinsic state (shared) from extrinsic state (unshared).
  * Stores shared intrinsic state in a `Flyweight` object.
* **Use Case**: Efficiently manage many small objects with shared data, such as in a graphical application.

#### 9. **Proxy Pattern**

* **Intent**: Provides a surrogate or placeholder for another object to control access to it.
* **Types**:
  * **Virtual Proxy**: Manages expensive object instantiation.
  * **Protection Proxy**: Controls access to sensitive resources.
  * **Remote Proxy**: Represents objects in different address spaces.
* **Use Case**: Applicable when there’s a need to add a layer of control before accessing an object.

#### 10. **Comparison of Structural Patterns**

* **Adapter vs. Bridge**:
  * **Adapter** resolves compatibility issues post-design, while **Bridge** separates abstraction and implementation at design time.
* **Composite vs. Decorator**:
  * **Composite** represents part-whole hierarchies, while **Decorator** is for adding responsibilities without altering structure.
* **Decorator vs. Proxy**:
  * **Decorator** adds functionality, while **Proxy** manages access.
* **Facade vs. Adapter**:
  * **Facade** provides a new interface, while **Adapter** matches existing interfaces.

***

## Chapter 5, _Behavioral Patterns_, from _Design Patterns: Elements of Reusable Object-Oriented Software_:

***

#### 1. **Introduction to Behavioral Patterns**

* **Purpose**: Focus on algorithms and communication between objects.
* **Types**:
  * **Class Behavioral Patterns**: Use inheritance to distribute behavior across classes.
  * **Object Behavioral Patterns**: Use composition to assign behavior and coordinate between objects.
* **Main Goal**: Reduce coupling by defining how objects interact, making systems more manageable and extensible.

#### 2. **Chain of Responsibility Pattern**

* **Intent**: Avoid coupling request senders to receivers by passing the request along a chain until an object handles it.
* **Application**: Useful when multiple objects may handle a request, but the specific handler is not known in advance.
* **Example**: Context-sensitive help systems, where help requests move through a chain of UI elements until handled.

#### 3. **Command Pattern**

* **Intent**: Encapsulate a request as an object, allowing for parameterization, queuing, and logging of requests.
* **Application**: Useful in scenarios like menu actions, where actions (commands) can be created, stored, and executed at different times.
* **Example**: Undo operations in applications, where each command object maintains a history of operations.

#### 4. **Interpreter Pattern**

* **Intent**: Given a language, define a representation for its grammar and an interpreter to evaluate sentences in the language.
* **Application**: Suitable for implementing languages or protocols.
* **Example**: Expression parsing and evaluation in calculators.

#### 5. **Iterator Pattern**

* **Intent**: Access elements of a collection sequentially without exposing its underlying representation.
* **Application**: Used in collections to allow traversal without exposing the structure of the collection.
* **Example**: Iterating over lists, arrays, or custom data structures like trees.

#### 6. **Mediator Pattern**

* **Intent**: Define an object that encapsulates how a set of objects interact, promoting loose coupling.
* **Application**: Useful when many objects communicate in complex ways, making direct interactions hard to manage.
* **Example**: A dialog box that coordinates interactions among UI controls without each control knowing about others.

#### 7. **Memento Pattern**

* **Intent**: Capture and externalize an object’s internal state to restore it later without violating encapsulation.
* **Application**: Ideal for implementing undo or rollback features.
* **Example**: Save and restore editor states in word processors.

#### 8. **Observer Pattern**

* **Intent**: Define a one-to-many dependency so that when one object changes state, all its dependents are notified.
* **Application**: Useful for data binding and event-handling systems.
* **Example**: Model-view synchronization in GUIs, where views update in response to changes in the model.

#### 9. **State Pattern**

* **Intent**: Allow an object to alter its behavior when its internal state changes, appearing to change its class.
* **Application**: Suitable for objects that change behavior depending on their state.
* **Example**: State-based behavior in TCP connections, where each state has different behaviors for sending/receiving.

#### 10. **Strategy Pattern**

* **Intent**: Define a family of algorithms, encapsulate each one, and make them interchangeable.
* **Application**: Useful when different algorithms are needed at runtime.
* **Example**: Layout strategies in UI components, where different algorithms arrange UI elements in different layouts.

#### 11. **Template Method Pattern**

* **Intent**: Define the skeleton of an algorithm in a method, deferring some steps to subclasses.
* **Application**: Useful for invariant algorithms where certain steps can vary.
* **Example**: Frameworks that define common workflow but allow specific steps to be customized.

#### 12. **Visitor Pattern**

* **Intent**: Represent an operation on elements of an object structure without changing their classes.
* **Application**: Useful for operations on complex structures like hierarchical or recursive data.
* **Example**: Applying operations to each element in a tree-like data structure, such as ASTs in compilers.

***

These notes cover the main behavioral patterns and provide examples to illustrate their use cases and practical applications【22:3†source】.



## Chapter 6, _Conclusion_

***

#### 1. **Purpose and Impact of Design Patterns**

* **Cataloging Knowledge**: Design patterns create a standardized vocabulary and catalog techniques used in software development.
* **Learning Aid**: By documenting design patterns, the book aims to make object-oriented design principles accessible and easier to learn, benefiting both novice and experienced developers.
* **Improvement through Pattern Documentation**: Standardizing patterns allows for continuous improvement, discovery of new patterns, and refinement of existing ones.

#### 2. **What to Expect from Design Patterns**

* **Common Vocabulary**: Design patterns enable designers to communicate complex structures succinctly. Terms like "Observer" and "Strategy" simplify discussions about design choices.
* **Understanding Existing Systems**: Knowledge of design patterns facilitates comprehension of object-oriented systems that use these patterns extensively.
* **Documentation Tool**: Patterns offer a means to document designs concisely, which aids both understanding and maintenance.
* **Design Framework**: Patterns provide solutions to recurring problems, offering designers a starting point and enhancing creativity by freeing them from routine issues.

#### 3. **Design Patterns as an Adjunct to Design Methods**

* **Flexible Application**: Design patterns complement traditional methods rather than replace them, providing designers with options to address specific design challenges.
* **Encouraging Reusable Solutions**: Patterns encourage reuse of proven solutions, promoting better organization and reducing redundancy in software development.

#### 4. **Lifecycle and Evolution of Software Systems**

* **Phases of Development**:
  * **Prototyping Phase**: Initial phase involving rapid prototyping and adjustments until the software achieves basic functionality.
  * **Expansion Phase**: As new requirements are added, the software structure expands, often resulting in complex and rigid designs.
  * **Consolidation/Refactoring Phase**: Software is refactored to accommodate new requirements more flexibly, usually through object composition.
* **Role of Patterns in Refactoring**: Design patterns offer structures that can reduce the need for refactoring by anticipating change and providing robust, reusable designs from the start.

#### 5. **A Brief History of Design Patterns**

* **Origin**: The catalog originated from Erich Gamma's Ph.D. thesis and was later expanded by the authors to include more patterns.
* **Naming Evolution**: Terms like "Wrapper" became "Decorator," and "Walker" became "Visitor" as the patterns evolved to better represent their function.
* **Collaboration**: Through workshops and feedback, the authors refined patterns to make them more accessible and usable.

#### 6. **The Pattern Community**

* **Influence of Christopher Alexander**: Inspired by the architect Christopher Alexander, whose work on building design patterns inspired the software pattern movement.
* **Growth of the Community**: The software pattern community has grown through conferences, books, and shared efforts to document software expertise.
* **Related Works**: Various authors and researchers have contributed to the pattern community by documenting patterns in specific areas, like C++ programming and organizational roles.

#### 7. **Invitation to Developers**

* **Applying Patterns**: Encourages readers to actively use patterns in their designs, document them, and share insights to foster a broader pattern-based design culture.
* **Critique and Collaboration**: Emphasizes the importance of constructive feedback to refine patterns, as well as documenting and sharing new patterns found in practical development.
* **Building a Community**: Urges designers to contribute to a growing pattern repository, aiding both individual understanding and community knowledge.

#### 8. **Final Thought on Pattern Integration**

* **Dovetailing Patterns**: The best designs often combine multiple patterns seamlessly, resulting in complex, layered, and flexible solutions.
* **Christopher Alexander's Insight**: Just as buildings can achieve depth and complexity through overlapping patterns, software can achieve greater robustness and adaptability through carefully combined design patterns.

***

These notes cover the main points from the conclusion, highlighting the significance, applications, and community impact of design patterns in software development .

