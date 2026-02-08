# Dive into design patterns



## Chapter 1 of _Dive Into Design Patterns_&#x20;



introduces the fundamentals of object-oriented programming (OOP) and lays a foundation for understanding design patterns. Here’s a detailed breakdown of the main concepts covered in this chapter:

#### 1. Basics of OOP

*   **Objects and Classes**: OOP is centered on objects, which bundle data and behaviors. Objects are instances of classes, which act as blueprints. For example, a `Cat` class can have attributes like `name` and behaviors like `meow`.



    <figure><img src="../../../.gitbook/assets/image (91).png" alt="" width="375"><figcaption></figcaption></figure>

    * Data stored inside the objects field is often refrenced as state, and all the object's methods define its behaviour.
    * So a class is like a blueprint that defines the structure for the objects, which are concrete instances of that class.
*   **Class Hierarchies**: Objects can be organized into hierarchies. For instance, `Cat` and `Dog` could both be subclasses of an `Animal` superclass, inheriting common properties.

    * A parent class like Animal, is called a superclass, its children are subclass. Subclasses inherit state and behaviour from their parent, defining only attributes or behaviours that differ.
    * THus the class cat would have the meow method and the dog class the bark method.
    * Such a pyramid of classes is called hierarchy. Here the cat class inherits everything from both the animal and organism classes.
    * Subclasses can override the behavior of methods that they inherit from parent classes. A subclass can completly replace the default behavior or just enhance it with some extra stuff.



    <figure><img src="../../../.gitbook/assets/image (92).png" alt="" width="375"><figcaption></figcaption></figure>

    <figure><img src="../../../.gitbook/assets/image (93).png" alt="" width="375"><figcaption></figcaption></figure>

#### 2. Pillars of OOP

* **Abstraction**:&#x20;
  * Objects model real-world entities with attributes relevant to specific contexts, abstracting away unnecessary details.
  * For example: An airplace class could probably exist in both a flight simulator and a flight booking application. but in the former case, it would hold details related to the actual flight, whereas in the latter class one would care only about the seach map and available seat. Here because of abstraction we represent all details relevant to this context with high accuracy and omits all the rest.
*   **Encapsulation**:&#x20;

    * Objects have public interfaces, hiding internal workings. For instance, a car’s start button hides complex processes under the hood.
    * Encapsulation is the ability of an object to hide parts of its state and behaviors from other objects, exposing only a limit- ed interface to the rest of the program.
    * To encapsulate something means to make it private, and thus accessible only from within of the methods of its own class. There's a little bit less restrictive mode called protected that makes a member of a class available to subclasses as well.
    * INterfaces and abstract class/methods of most programming languages are based on the concepts of abstraction and encapsulation
    * Example -- we have a flyingTransport interface with a methods fly(origin, destination, passangers). When designing an airport transportation simulator we can restrict the airport class to work only with the objects that implement FlyingTransport interface. After this one can be sure that any object passed to an airport object, whether it is an airplace, a helicopter would be able to arrive or depart from this type of airport.



    <figure><img src="../../../.gitbook/assets/image (94).png" alt="" width="375"><figcaption></figcaption></figure>
*   **Inheritance**:&#x20;

    * Classes can inherit behaviours and attributes from other classes, promoting code reuse and maintaining a logical hierarchy.
    * benifit -- Code reuse. THe consequece of using inheritance is that subclasses have the same interface as thier parent class. One can't hide a method in a subclass if it was declared in the superclass. One must also implement all abstract methods, even if they don't make sense for our subclass.



    <figure><img src="../../../.gitbook/assets/image (95).png" alt="" width="375"><figcaption></figcaption></figure>
*   **Polymorphism**:&#x20;

    * Objects can take many forms and be treated as instances of their superclass, allowing methods like `makeSound` to be generalized across subclasses like `Cat` and `Dog`.
    * When we anticipate that all subclass will need to override the based makeSound method so each subclass can emit the correct sound, THerefore we can declare it abstract right away. This lets us omit any default implementation of the method in the superclass, but force all subclass to come up with their own.
    * it is the ability of program to detect the real class of an object and call its implementation even when its real type is unknown in the current context.
    * One can think polymorphism as the ability of an object to pretend to be something else, usually a class it extends or an interface it implements. Here, cats and dogs were pretending to be generic animals.



    <figure><img src="../../../.gitbook/assets/image (86).png" alt="" width="375"><figcaption></figcaption></figure>

#### 3. Relations Between Objects

*   **Association**:&#x20;

    * When one object uses or interacts with another, forming a link (e.g., a professor-student relationship).
    * Shown using a simple arrow in UML, can be bidirectional as well.



    <figure><img src="../../../.gitbook/assets/image (87).png" alt="" width="375"><figcaption></figcaption></figure>
*   **Dependency**:&#x20;

    * A weaker association where one object relies on another, usually as a method parameter.
    * NO permanenet link between objects, a dependency exists between two classes if changes to the definaction of one class results in the modifications in another class.



    <figure><img src="../../../.gitbook/assets/image (88).png" alt="" width="375"><figcaption></figcaption></figure>
* **Composition and Aggregation**:&#x20;
  *   Composition means an object is composed of other objects that can't exist independently, while aggregation means objects can exist independently (e.g., a university with departments).



      <figure><img src="../../../.gitbook/assets/image (90).png" alt="" width="375"><figcaption></figcaption></figure>
  *   Aggregation is a less strict varriant of compposition, where one object marely contains a reference to another. The container doesn't controls the life cycle of componenet. The componenet can exist without the container and can be linked to serveral containers at the same time.



      <figure><img src="../../../.gitbook/assets/image (89).png" alt="" width="375"><figcaption></figcaption></figure>

#### 4. Introduction to Design Patterns

* **Definition**: Design patterns are reusable solutions for common software design problems, like templates or blueprints adaptable to different problems. An algorithm defines a clear set of actions that can achieve some goal, a pattern is more high level description of a solution. The code of the same pattern applied to two different programs may be different.
* **Components of Patterns**:&#x20;
  * Each pattern has a formal description including intent, motivation, and structure. This enables consistency and easier collaboration among developers.
  * Intent: describes both the problem and the solution
  * motivation: explaines the problem and solution the patterns makes possible
  * structure: shows each part of the pattern and how they are related.
  * Code example: makes it easier to grasp the indea behind the pattern.
*   **Pattern Classification**:

    * **architectural patterns:** high level patterns that can be created in any language
    * **Idioms -** basic and low level patterns that apply only to one programming language.
    * **Creational Patterns**: Focus on object creation (e.g., Factory Method).
    * **Structural Patterns**: Organize object composition (e.g., Adapter).
    * **Behavioral Patterns**: Define interactions between objects (e.g., Observer).



    #### Importance of Learning Patterns

    * **Efficiency**: Patterns are reusable solutions that save time and avoid repetitive design.
    * **Common Language**: Design patterns create a shared vocabulary among developers, improving collaboration and reducing the need for detailed explanations when discussing common design approaches.
    * **Software Quality**: Patterns encourage code that is flexible, maintainable, and scalable, embodying the best practices in object-oriented design【10:7†source】.

Design patterns are a toolkit of tried and tested solutions to the common problems that one faces.

Design patterns define a common language that you and your teammates can use to communicate more efficiently. You can say, “Oh, just use a Singleton for that,” and everyone will understand the idea behind your suggestion. No need to explain what a singleton is if you know the pattern and its name

This chapter provides a solid understanding of OOP principles, which are crucial for grasping the structure and purpose of design patterns. Let me know if you’d like a more focused explanation on any specific part for your exam prep!



_Design Patterns: Elements of Reusable Object-Oriented_



## Chapter 2 of _Features of good design_&#x20;



introduces design patterns and explains their purpose, benefits, and classifications. Here’s a summary of the chapter:

#### 1. What is a Design Pattern?

* **Definition**: Design patterns are general solutions to common design problems in software engineering. They are not specific pieces of code but rather concepts that can be adapted to different programming contexts.
* **Analogy**: Unlike algorithms, which provide specific steps for a task (like a recipe), design patterns are more like architectural blueprints. They outline a general solution, allowing flexibility in implementation depending on the specific needs of the program.

#### 2. Components of a Design Pattern

* **Intent**: Summarizes the problem and solution the pattern addresses.
* **Motivation**: Provides a deeper explanation of the problem and the solution the pattern offers.
* **Structure**: Shows the components involved and their relationships.
* **Code Example**: Often includes pseudocode to illustrate the pattern.
* **Additional Sections**: Some descriptions may include applicability, implementation details, and relations to other patterns【10:0†source】.

#### 3. Categories of Design Patterns

* **Creational Patterns**: Deal with object creation, focusing on methods that increase code flexibility and reusability (e.g., Factory Method).
* **Structural Patterns**: Focus on class composition and relationships, making systems more flexible and efficient (e.g., Adapter).
* **Behavioral Patterns**: Manage communication between objects and allocate responsibilities effectively (e.g., Observer)【10:3†source】【10:5†source】.

#### 4. I

This chapter sets up a foundational understanding of why design patterns are valuable, offering a preview of patterns that help organize complex software architectures. Let me know if you’d like more detail on specific sections or examples from this chapter!





## Chapter 3 of _Dive Into Design Patterns_&#x20;



covers essential software design principles that contribute to creating clean, maintainable, and scalable code. Here’s an in-depth look at the concepts introduced:

#### <mark style="color:yellow;">1. Encapsulate What Varies</mark>

* **Principle**:&#x20;
  * aim of this principle is to minimize the effect caused by changes. One can isolate the parts of program that vary in independent modules, protecting the rest of the code from adverse effects.
  * Identify parts of the application likely to change and separate them from those that remain constant. This approach minimizes disruption in other parts of the code when updates are necessary.
  * <mark style="color:blue;">Encapsulation on a method level --</mark>
    * e-commerce website. there is a method that calculates  the grand total including the texes. One might anticipate the tax related changes in future.
    * To enable encapsulation at the method level one can extract the tax calculation logic into a seperate method hiding it from the original method. So that tax related changes becom isolated inside a single method. But if the tax calculation becomes too complicated one can move it to a seperate class.
  *   <mark style="color:blue;">Encapsulation on a class level --</mark>

      * Over time one might add more and more responsibilities to a method which used to do a simple thing, this added behaviours often come with their own helper fields and methods that eventually blur the primary responsibility of the class.
      * Extracting everything to a new class might make things much more clear and simple.



      <figure><img src="../../../.gitbook/assets/image (109).png" alt="" width="375"><figcaption></figcaption></figure>
* **Example**: If tax calculations vary by location on an e-commerce site, separating tax calculation logic into a separate method or class ensures changes affect only that part of the code.

#### <mark style="color:yellow;">2. Program to an Interface, Not an Implementation</mark>

*   **Principle**:&#x20;

    * Depend on abstractions rather than concrete classes, enhancing flexibility. By programming to an interface, code can work with any class that implements the interface, promoting polymorphism and reducing dependencies.
    * make design flexible so that one can easily extend it without breaking any existing code.
    * When we want to make two calsses collaborate, one can start by making one of them dependent on the other.
    * After making these changes, we won;t probably feel any imediate benifts. one the contrary the code has become more complicated than it was before



    <figure><img src="../../../.gitbook/assets/image (110).png" alt="" width="375"><figcaption></figcaption></figure>
* **Example**: A Company class can depend on an Employee interface rather than specific employee types, simplifying future extensions.
  * Before:&#x20;
    * Here the company calss is tightly coupled to concrete class of employees. But despite the different in their implementation, we can generalize the various work-related methods and then extract a common interface for all employee classes.
    *

        <figure><img src="../../../.gitbook/assets/image (111).png" alt="" width="375"><figcaption></figcaption></figure>
  *   Better:

      * After doing that we can apply polymorphism inside the company class, treating various employees object via the employee interface.



      <figure><img src="../../../.gitbook/assets/image (112).png" alt="" width="375"><figcaption></figcaption></figure>
  *   After:

      * The company class remains coupled to the employee classes. THis is bad because if we introduce new types of companies that work with other types of employees, we will need to override most of the company class instead of reusing that code.
      * To solve this problem, we could declare the methods for getting employees as abstract. Each concrete company will implement this methods differencly , creating only those employees that it needs.
      * After this Change the company class has become independent from various employees class. One can extend this class and introduce new types of companies and employees while stil reusing a portion of the base company class. Extending the base company class doesn't breaks any existing code that already relies on it.



      <figure><img src="../../../.gitbook/assets/image (113).png" alt="" width="375"><figcaption></figcaption></figure>

#### <mark style="color:yellow;">3. Favor Composition Over Inheritance</mark>

* **Principle**:&#x20;
  * Composition provides greater flexibility by allowing classes to achieve polymorphic behavior through delegation rather than inheritance. This approach avoids tight coupling and makes code more modular.
  * Although inheritance comes with caveats that often become apparent only after the program has already tons of classes and changing anything is pretty hard. these are:
    * A subclass can't reduce the interface of the superclass
    * When overriding methods one needs to make sure that the new behavior is compatible with the base one.
    * Inheritance breaks encapsulation of the superclass
    * Subclasses are tightly coupled to the superclass
    * Trying to reuse code through inheritance can lead to creating parallel inheritance hierarchies.
  * One can use an alternative to inheritance called composition which is has a relationship( a car has an engine) instead of is a relationship ( a car is a transport)
* **Example**: Rather than subclassing a vehicle class for each feature (e.g., fuel type, navigation), a vehicle object could contain an engine component. This modular structure is also the basis for the Strategy design pattern【14:0†source】.
  * before:
    * catalog app for a car manufacturer.&#x20;
    * Here each additional parameters results in multiplying the number of subclasses. THere is a lot of duplicate subclasses as a subclass can't extend two classes at the same time.
    *   To solve this we can use composition, instead of car objects implementing a behaviour on their own, then can deltegate it to other objects .

        <figure><img src="../../../.gitbook/assets/image (115).png" alt=""><figcaption></figcaption></figure>
  * after:
    *   THe added benifit is that one can replace a behavior at runtime. For instance one can replace an enginge object linked to a car object just by assigning engine object to the car.

        <figure><img src="../../../.gitbook/assets/image (116).png" alt=""><figcaption></figcaption></figure>

#### <mark style="color:yellow;">4. SOLID Principles</mark>

The chapter introduces the SOLID principles, a set of five guidelines that enhance modularity, flexibility, and maintainability.

The cose of applying these principles into a program architecture might be making it more complicated than it should be. There are not many sicessful software product in which all of these principles are applied at teh same. One can strive for these principles but always try to be pragmatic and don't take eveything written here as dogma.



* **Single Responsibility Principle (SRP)**:&#x20;
  * A class should have just one reason to change.
  * Each class should address only one aspect of functionality. This reduces complexity, making code easier to test and modify without unintended side effects.
  * main goal is to reduce complexity.
  * The main problem is if the codebase is too big then to make a specific change one has to scroll and might break other parts of the class which one didn't even intend to change.
  * If at anytime one feels it is becoming hard to focus on a specific aspects of the program one at a time, remember the single responsibility principle and check wheather it is time to divice some classes into parts
  * EX--&#x20;
    * before
      * Employe class has several reasons to change. First might be related to the main job of the class: managing employee data. second the format of the timesheet report may change over time, requireing one to change the code within the class.
      *

          <figure><img src="../../../.gitbook/assets/image (117).png" alt=""><figcaption></figcaption></figure>
    * After
      * move the behavior related to printing timesheet reports into a seperate class. This changes lets one to move other report related stuff into the new class
      *

          <figure><img src="../../../.gitbook/assets/image (118).png" alt=""><figcaption></figcaption></figure>
* **Open/Closed Principle (OCP)**:&#x20;
  * Classes should be open for extension but closed for modification, preventing existing code from breaking when adding new features.&#x20;
  * A class is open if one can extend it, produce a subclass and do whatever one want with it-  add methods or fields, override base behavior etc.
  *   If a class is already developed, tested, reviewed, and includ-

      ed in some framework or otherwise used in an app, trying to

      messwithitscodeisrisky.Insteadofchangingthecodeofthe

      class directly, you can create a subclass and override parts of the original calss that you want to behave differently
  * One would achieve the goal but also won't break any existing clients of the original class.
  * For example, creating subclasses to add new behaviors without changing the original class.
    * Before:
      * we have an e-commerce application with an order class that calculates shipping costs and all shipping methods are hard coded inside the class.&#x20;
      * If one needs to add a new shipping method, one have to change the code of the order class and risk breaking it.
      *

          <figure><img src="../../../.gitbook/assets/image (119).png" alt=""><figcaption></figcaption></figure>
    * After&#x20;
      * One can solve the problem by applying the strategy pattern. We can start by extracting shipping methods into seperate classes with a common interface.
      * Now when we need to implement a new shipping method, one can derive a new class from the shipping interface without touching any of the order class code.
      *   The client code fo the order class will link orders with a shiping object of the new class whenver the user selects this shipping methods in the UI.

          <figure><img src="../../../.gitbook/assets/image (120).png" alt=""><figcaption></figcaption></figure>
* **Liskov Substitution Principle (LSP)**:&#x20;
  * When extending a class one should be able to pass objects of the subclass in place of objects of the parent class without breaking the client code.
  * Subtypes must be substitutable for their base types. A derived class should not alter expected behavior of the base class to maintain reliability.
  * This substtitution principle is a set of checks that helps predict whether a subclass remains compatible with the code that was able to work with objects of the superclass. (Useful while developing libraries and frameworks as the classes are going to be used by other people whose code one can't directly access and change)
  * This principle has a set of formal requirements for subclass and specifically for their methods.
    *   Parameter types in a method of a subclass should _match_ or be

        _more abstract_ than parameter types in the method of the super-

        class
    *   The return type in a method of a subclass should _match_ or be

        a _subtype_ of the return type in the method of the superclass
    *   A method in a subclass shouldn’t throw types of exceptions

        which the base method isn’t expected to throw.
    * A subclass shouldn’t strengthen pre-conditions
    * A subclass shouldn’t weaken post-conditions
    * Invariants of a superclass must be preserved
    *   A subclass shouldn’t change values of private fields of the

        superclass
  * Example:
    * Before
      * The save method in the ReadOnlyDocuments subclass throws an exception if someone trues to call it.
      * The base method doesn't have this restriction. This means that the client code will break if we don't check the document type before saving it.
      *   The resulting code also violates the open/closed principle, since the client code becomes dependent on concrete classes of documents. if we introducce a new docuemnt subclass one will need to change the client code to supprot it.

          <figure><img src="../../../.gitbook/assets/image (122).png" alt=""><figcaption></figcaption></figure>


    * After
      *   One can solve this be redesigning the class hierarchy, a subclass should extend the behaviour of a superclass thus the read-only document becomes the base class of the hierarchy. The writable document is now a subclass which extends the base class and adds the saving behavior.

          <figure><img src="../../../.gitbook/assets/image (121).png" alt=""><figcaption></figcaption></figure>
* **Interface Segregation Principle (ISP)**:&#x20;
  * Clients shouldn't be forced to depend on methods they do not use.
  * Try to make the interfaces narrow enough that client classes don't have to implement behaviours they don't need.
  * Avoid forcing classes to implement interfaces they don’t use. Narrow interfaces tailored to specific client needs prevent dependency on irrelevant methods.
  * Clients should implement only those methods that they reallt need. otherwise a change to a fat interface would break even clients that don't use the changed methods.
  * Class inheritance lets a class have just one superclass, but it doesn't limit the number of interfaces that the class can implement at the same time. So one doesn't need to cram tons of unrelated methods to a single interface. Break it down to several more refined interfaces-- one can implement them all in a singel class if needed.
  * Example
    *   Before&#x20;

        * there is a library class that makes it easy to integerate apps with various cloud computing providers. While in the intial version it only supported amazon cloud.
        * But later you foind out that most of the interfaces of the library are too wide. Some methods describe features that other cloud providers just don't have.



        <figure><img src="../../../.gitbook/assets/image (123).png" alt="" width="375"><figcaption></figcaption></figure>
    * After&#x20;
      * The approach should be to break down the interface into parts. Classes that are able to implement the original ineterface can now just implement several refined interfaces. Other classes can implement only those interfaces which have methods that make sense for them.
      *   Also one needs to keep the balance, between teh complexity and the interfaces we create.

          <figure><img src="../../../.gitbook/assets/image (124).png" alt="" width="375"><figcaption></figcaption></figure>
* **Dependency Inversion Principle (DIP)**:&#x20;
  * High-level modules should not depend on low-level modules; **both should rely on abstractions**. This principle keeps high-level logic independent of implementation specifics.
  * Usually while designing software one needs to make a distinciton between two level of classes:
    * low-level classes: implement basic operation such as working with a disk, transferring data over a network, connecting to a database etc.
    * High level classes: These have complex business logic that directs low-level classes to do something.
  * Example:
    * The high level budget reporting class uses a low level database class for reading and persisitng its data.
    *   Before:

        * we have a high lkevel budget reporting class that uses a low level database class for reading and persisting its data.
        * This means that any change in the low level class, such as when a new version of the database server gets released , may affect the high level class, which is not supposed to care about the data storage details.



        <figure><img src="../../../.gitbook/assets/image (126).png" alt="" width="375"><figcaption></figcaption></figure>
    *   After:

        * One can fix this by creating a high level interface that describes read/write operations and making the reporting class use that interface instead of the low level class
        * then we can change or extend the original low level class to implement the new read/write interface declared by the business logic.



        <figure><img src="../../../.gitbook/assets/image (127).png" alt="" width="375"><figcaption></figcaption></figure>

These principles form the basis for building robust, scalable software that can adapt to changes with minimal disruption. Let me know if you’d like more examples or clarification on specific sections!











<table><thead><tr><th width="147">Group</th><th>Types</th><th>About</th></tr></thead><tbody><tr><td><mark style="color:red;"><strong>Creational</strong></mark></td><td></td><td>Creational patterns provide various object creation mecha- nisms, which increase flexibility and reuse of existing Code.</td></tr><tr><td></td><td><em><mark style="color:purple;">Factory</mark></em></td><td><p>provides</p><p>an interface for creating objects in a superclass, but allows</p><p>subclasses to alter the type of objects that will be created.</p></td></tr><tr><td></td><td><em><mark style="color:purple;">Abstract factory</mark></em></td><td><p>lets you</p><p>produce families of related objects without specifying their</p><p>concreteclasses.</p></td></tr><tr><td></td><td><em><mark style="color:purple;">Builder</mark></em></td><td><p>lets you construct</p><p>complex objects step by step. The pattern allows you to</p><p>producedifferent types andrepresentations ofanobjectusing</p><p>the same construction code.</p></td></tr><tr><td></td><td><em><mark style="color:purple;">Prototype</mark></em></td><td><p>lets you copy</p><p>existing objects without making your code dependent on</p><p>their classes.</p></td></tr><tr><td></td><td><em><mark style="color:purple;">Singelton</mark></em></td><td><p>lets you ensure</p><p>that a class has only one instance, while providing a global</p><p>access point to this instance.</p></td></tr><tr><td><mark style="color:red;"><strong>Structural</strong></mark></td><td></td><td>Structural patterns explain how to assemble objects and class- es into larger structures, while keeping this structures flexible and efficient.</td></tr><tr><td></td><td><em><mark style="color:purple;">Adaptor</mark></em></td><td><p>allows objects with</p><p>incompatible interfaces tocollaborate.</p></td></tr><tr><td></td><td><em><mark style="color:purple;">Bridge</mark></em></td><td><p>lets you split a large</p><p>class or a set of closely related classes into two separate</p><p>hierarchies—abstraction and implementation—which can be</p><p>developed independently of each other.</p></td></tr><tr><td></td><td><em><mark style="color:purple;">Composite</mark></em></td><td><p>letsyoucompose</p><p>objects into tree structures and then work with these</p><p>structures as if they were individual objects.</p></td></tr><tr><td></td><td><em><mark style="color:purple;">Decorator</mark></em></td><td><p>lets you attach</p><p>new behaviors to objects by placing these objects inside</p><p>special wrapper objects that contain the behaviors.</p></td></tr><tr><td></td><td><em><mark style="color:purple;">Facade</mark></em></td><td><p>providesasimplified</p><p>interfacetoalibrary,aframework, or any other complexset of</p><p>classes.</p></td></tr><tr><td></td><td><em><mark style="color:purple;">Flyweight</mark></em></td><td><p>lets you fit more</p><p>objects into the available amount of RAM by sharing common</p><p>parts of state between multiple objects instead of keeping all</p><p>of the data in each object.</p></td></tr><tr><td></td><td><em><mark style="color:purple;">Proxy</mark></em></td><td><p>lets you provide a</p><p>substitute or placeholder for another object. A proxy controls</p><p>access to the original object, allowing you to perform</p><p>something either before or after the request gets through to</p><p>the original object.</p></td></tr><tr><td><mark style="color:red;"><strong>Behavarioul</strong></mark></td><td></td><td>Behavioral patterns are concerned with algorithms and the assignment of responsibilities between objects.</td></tr><tr><td></td><td><em><mark style="color:purple;">Chain of Responsibility</mark></em></td><td><p>lets</p><p>you pass requests along a chain of handlers. Upon receiving a</p><p>request, each handler decides either to process the request or</p><p>to pass it to the next handler in the chain.</p></td></tr><tr><td></td><td><em><mark style="color:purple;">Iterator</mark></em></td><td><p>lets you traverse</p><p>elements of a collection without exposing its underlying</p><p>representation (list, stack, tree, etc.).</p></td></tr><tr><td></td><td><em><mark style="color:purple;">Command</mark></em></td><td><p>turns a request</p><p>into a stand-alone object that contains all information about</p><p>the request. This transformation lets you parameterize</p><p>methods with different requests, delay or queue a request’s</p><p>execution, and support undoable operations.</p></td></tr><tr><td></td><td><em><mark style="color:purple;">mediator</mark></em></td><td><p>lets you reduce</p><p>chaotic dependencies between objects. The pattern restricts</p><p>directcommunicationsbetweentheobjectsandforcesthemto</p><p>collaborate only via a mediator object.</p></td></tr><tr><td></td><td><em><mark style="color:purple;">Momento</mark></em></td><td><p>letsyousaveand</p><p>restore the previous state of an object without revealing the</p><p>details of its implementation.</p></td></tr><tr><td></td><td><em><mark style="color:purple;">Observer</mark></em></td><td><p>lets you define a</p><p>subscription mechanism to notify multiple objects about any</p><p>events that happen to the object they’re observing.</p></td></tr><tr><td></td><td><em><mark style="color:purple;">State</mark></em></td><td><p>lets an object alter its</p><p>behavior when its internal state changes. It appears as if the</p><p>object changed its class.</p></td></tr><tr><td></td><td><em><mark style="color:purple;">Strategy</mark></em></td><td><p>lets you define a</p><p>family of algorithms, put each of them into a separate class,</p><p>and make their objects interchangeable</p></td></tr><tr><td></td><td><em><mark style="color:purple;">Template method</mark></em></td><td><p>defines</p><p>the skeleton of an algorithm in the superclass but lets</p><p>subclasses override specific steps of the algorithm without</p><p>changing its structure.</p></td></tr><tr><td></td><td><em><mark style="color:purple;">Visitor</mark></em></td><td><p>lets you separate</p><p>algorithms from the objects on which theyoperate.</p></td></tr></tbody></table>

## Chapter 4 **Creational Design Patterns**

which focus on flexible object creation while reducing dependencies. Here’s an overview of the key creational patterns covered:

#### 1. Factory Method

* **Purpose**: Provides an interface for creating objects in a superclass, allowing subclasses to alter the type of objects created.
* **Problem**: Direct object creation can create tight coupling and reduce flexibility, especially if a class depends on a specific subclass.
* **Solution**: Replace direct calls to constructors with calls to a factory method that subclasses can override to create specific object types, supporting the Open/Closed Principle.
* **Example**: A `Logistics` base class uses a `createTransport` method to return transport objects (`Truck`, `Ship`, etc.). Subclasses define this method to specify which transport to create, reducing direct dependencies【18:0†source】.

#### 2. Abstract Factory

* **Purpose**: Creates families of related objects without specifying their concrete classes, helping ensure consistency among related objects.
* **Problem**: Directly instantiating related objects can lead to mismatched components.
* **Solution**: Define interfaces for each product (e.g., `Chair`, `Sofa`). An abstract factory interface then declares methods to create these products, with concrete factories implementing specific variants.
* **Example**: A `FurnitureFactory` with `createChair` and `createSofa` methods can produce matching Victorian or Modern furniture without directly coupling to specific types【18:1†source】【18:9†source】.

#### 3. Builder

* **Purpose**: Constructs complex objects step-by-step, allowing different configurations with the same construction process.
* **Problem**: Complex constructors or numerous subclass combinations can lead to bloated, difficult-to-maintain code.
* **Solution**: Move construction steps to a `Builder` class with methods like `buildWalls` and `buildDoor`. Each method configures a part of the object, which can be customized by calling different builders.
* **Example**: A `HouseBuilder` constructs houses with various features, such as walls and roofs. The builder handles the construction steps without needing to create subclasses for each house type【18:3†source】【18:6†source】.

#### 4. Prototype

* **Purpose**: Creates objects by cloning existing instances, which serves as prototypes.
* **Problem**: Instantiating complex objects can be costly or impractical, especially when the object has multiple configurations.
* **Solution**: Implement a `clone` method in a `Prototype` interface. Each concrete class implements this method, allowing cloning of objects with desired configurations.
* **Example**: A `Tree` object with different characteristics (height, leaf type) can be cloned, creating similar trees without reconstructing each one from scratch【18:13†source】.

#### 5. Singleton

* **Purpose**: Ensures a class has only one instance, providing a global access point to it.
* **Problem**: Global variables are risky, and multiple instances can waste resources or lead to conflicts.
* **Solution**: Make the constructor private and provide a static method to access the single instance.
* **Example**: A `DatabaseConnection` class might be a singleton to prevent opening multiple database connections. The instance can be accessed from anywhere in the code but only instantiated once【18:14†source】.

These patterns are crucial for developing modular, flexible systems that adhere to OOP principles, supporting better structure and easier maintenance. Let me know if you’d like examples or further details on any specific pattern!







## Chapter 5 **Structural Design Patterns**

which help arrange objects and classes into larger structures while keeping the system flexible and efficient. Here’s an overview of the main structural patterns covered:

#### 1. Adapter

* **Purpose**: Allows objects with incompatible interfaces to collaborate.
* **Problem**: Integrating a class with an incompatible interface in a client system.
* **Solution**: The adapter acts as a wrapper, converting calls from the client into a format the wrapped object understands.
* **Example**: In a stock app, an adapter could convert XML data to JSON for compatibility with a JSON-based analytics library【18:0†source】.

#### 2. Bridge

* **Purpose**: Decouples an abstraction from its implementation, allowing them to vary independently.
* **Problem**: When multiple dimensions of a class hierarchy (e.g., shape types and colors) grow, leading to an explosion of subclasses.
* **Solution**: Separate the class hierarchies. For instance, instead of multiple shape/color combinations, a `Shape` class references a `Color` class, with each hierarchy expanded independently.
* **Example**: A `Shape` class can reference `Color` classes, avoiding the need for separate classes like `RedCircle` or `BlueSquare`【18:8†source】.

#### 3. Composite

* **Purpose**: Composes objects into tree structures, enabling clients to treat individual objects and compositions uniformly.
* **Problem**: Managing nested structures like a file directory, where folders may contain files or other folders.
* **Solution**: Define a common interface for both single objects and composites, allowing recursion through the tree structure.
* **Example**: A graphic editor where compound shapes can contain other shapes, allowing a consistent way to manipulate them all【18:15†source】.

#### 4. Decorator

* **Purpose**: Adds behaviors to objects dynamically by wrapping them in decorator classes.
* **Problem**: Extending functionality without subclassing, especially when multiple combinations of features are needed.
* **Solution**: Use decorator classes that wrap the original object, adding functionality while maintaining the original object's interface.
* **Example**: A notification system where additional notification channels (SMS, email) are added via decorators instead of creating a subclass for each combination【18:17†source】.

#### 5. Facade

* **Purpose**: Provides a simplified interface to a complex subsystem.
* **Problem**: Working with a large system of classes can be overwhelming and lead to tightly coupled code.
* **Solution**: A facade class provides methods for core functionalities, simplifying interactions and hiding the complexity of the subsystem.
* **Example**: A video conversion library where a `VideoConverter` facade class encapsulates multiple conversion steps into a single method call【18:19†source】.

#### 6. Flyweight

* **Purpose**: Reduces memory usage by sharing common parts of objects.
* **Problem**: Systems with many similar objects (e.g., displaying thousands of tree objects) consume excessive memory.
* **Solution**: Store common data externally and reuse shared states, creating lightweight objects for unique data.
* **Example**: A graphics application where each tree shares common properties like texture and color while maintaining unique position data【18:11†source】.

#### 7. Proxy

* **Purpose**: Provides a substitute for another object, controlling access to it.
* **Problem**: Controlling access to resources, particularly in cases of expensive operations (e.g., database access).
* **Solution**: The proxy holds a reference to the real object and manages its lifecycle, performing actions like lazy loading or access control.
* **Example**: A credit card acting as a proxy for a bank account, managing transactions on behalf of the account【18:18†source】.

These patterns help organize and optimize object structures, leading to efficient and flexible designs. Let me know if you need more specific details on any of these!





## Chapter 6 **Behavioral Design Patterns**



which focus on how objects interact, communicate, and assign responsibilities. Here’s an overview of the patterns covered:

#### 1. Chain of Responsibility

* **Purpose**: Passes a request along a chain of potential handlers until one of them handles it.
* **Problem**: Directly linking senders and receivers creates a rigid structure.
* **Solution**: Each handler can decide to process the request or pass it to the next handler, making the chain flexible.
* **Example**: An authentication system where requests are checked sequentially (authentication, authorization, etc.)【26:0†source】.

#### 2. Command

* **Purpose**: Turns requests into standalone objects that can be parameterized, queued, and logged.
* **Problem**: GUI elements need to execute various actions depending on the context.
* **Solution**: Encapsulate requests in command objects to decouple the sender and receiver.
* **Example**: Undo functionality in a text editor where each action (cut, paste) is a command that can be reversed【26:8†source】【26:10†source】.

#### 3. Iterator

* **Purpose**: Provides a way to traverse a collection without exposing its underlying structure.
* **Problem**: Directly iterating over a collection reveals its internal structure.
* **Solution**: The iterator object encapsulates traversal algorithms, supporting multiple iteration types without modifying the collection.
* **Example**: Navigating nodes in a tree where multiple traversal methods (depth-first, breadth-first) are needed【26:17†source】.

#### 4. Mediator

* **Purpose**: Reduces chaotic dependencies by centralizing communication through a mediator.
* **Problem**: Overlapping responsibilities among components lead to excessive dependencies.
* **Solution**: Components interact only through a mediator, reducing coupling.
* **Example**: In a GUI, a mediator manages interactions between buttons and text fields without direct component references【26:13†source】.

#### 5. Memento

* **Purpose**: Saves and restores an object’s state without revealing its internal details.
* **Problem**: Exposing an object's state for saving makes it vulnerable to changes.
* **Solution**: Use a memento object to capture and store the state, which can be restored later.
* **Example**: An undo history in a text editor that lets users revert changes【26:19†source】.

#### 6. Observer

* **Purpose**: Allows objects to notify dependent objects about changes without tightly coupling them.
* **Problem**: Dependencies between objects create complex updates.
* **Solution**: Observers subscribe to subjects, receiving notifications about state changes.
* **Example**: A stock monitoring system where observers update when stock prices change【26:6†source】.

#### 7. State

* **Purpose**: Lets an object alter its behavior when its internal state changes.
* **Problem**: Managing behavior based on numerous states in one class can lead to complex conditionals.
* **Solution**: Move state-specific behaviors into separate classes, allowing transitions between states.
* **Example**: Document status in a CMS, where the behavior of publish, edit, or delete actions depends on its state (Draft, Published, Archived)【26:5†source】.

#### 8. Strategy

* **Purpose**: Defines a family of algorithms, encapsulating them for interchangeable use.
* **Problem**: Managing multiple variations of an algorithm in one class leads to bloated code.
* **Solution**: Encapsulate algorithms in separate strategy classes, making them interchangeable.
* **Example**: Route planning in an app, where users can choose driving, walking, or cycling routes【26:4†source】.

#### 9. Template Method

* **Purpose**: Defines the skeleton of an algorithm, allowing subclasses to implement specific steps.
* **Problem**: Code duplication across similar methods with slight variations.
* **Solution**: A template method defines common steps, while subclasses override specific steps.
* **Example**: Data processing where different file formats require similar processing but different parsing steps【26:15†source】.

#### 10. Visitor

* **Purpose**: Separates algorithms from object structures, allowing new operations to be added without modifying the objects.
* **Problem**: Adding new behaviors to a class hierarchy leads to tight coupling.
* **Solution**: The visitor class implements actions, iterating over elements to perform operations.
* **Example**: Applying multiple analytics on various data types in an object structure【26:14†source】.

Behavioral patterns facilitate communication and responsibility-sharing among objects, which helps keep code adaptable and maintainable. Let me know if you need more details on specific patterns!





## Chapter 7 the **Conclusion**&#x20;



and offers guidance on how to continue studying and applying design patterns. Here are the main points:

#### 1. Understanding Patterns in Context

* **Purpose of Patterns**: The book emphasizes that design patterns are not just isolated solutions but part of a larger body of knowledge in software design. They represent refined, reusable solutions to recurring design problems and embody the best practices in software engineering.
* **Choosing Patterns Wisely**: It's important to select patterns based on actual needs and not to overuse them. Applying the right patterns makes code more adaptable and scalable, while overuse can lead to unnecessary complexity .

#### 2. Applying Patterns in Real-World Projects

* **Incremental Learning**: Start with a few key patterns and gradually incorporate more as you encounter relevant scenarios. Working with familiar problems and applying patterns incrementally helps solidify understanding.
* **Refactoring to Patterns**: Often, design patterns are not introduced at the beginning of a project but are incorporated through refactoring. This approach ensures patterns fit the specific needs of the codebase .

#### 3. Expanding Knowledge

* **Further Reading**: The book recommends works like _Refactoring to Patterns_ by Joshua Kerievsky for deeper insight into integrating patterns through refactoring. Continuing to explore advanced books and online resources will also help in gaining a broader view.
* **Practical Resources**: To reinforce learning, the book suggests accessing code samples, downloadable resources, and pattern cheat sheets. These resources can be valuable in both review and real-time project reference .

#### 4. Next Steps

* **Feedback and Practice**: Engaging with the community through feedback, discussions, and practice is essential. Exploring more patterns and experimenting with different ways to implement them further enhances one's skills in software design .

Chapter 7 wraps up the book by stressing the importance of practical experience, ongoing learning, and applying patterns where they bring clear value to the project. Let me know if you need any more details!
