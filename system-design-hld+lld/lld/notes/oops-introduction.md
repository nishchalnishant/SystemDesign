# OOPs introduction



* Why do we need to learn OOPs
  * OOPs helps us to break down the system into smaller, modular components, One can define for cars customers and rental transactions each with its own data and behavior.
  * makes the code maintenance and extensibility  easier. if one needs to make changes to the system we can modify or extend specific classes without affecting others .
  * Algorithms + Data structures =Programs&#x20;
  * A program is combination of two things information (data) and a set of instructions for manipulating that information (algorithms). But we are writing a software which is a collection of programs that work together to perform several tasks.
* Building system: The OOP way
  * An object is logical construct that consists of user-defined data and a set of operations for manipulating that data. It can be thought of as a combination of data and behavior( Data+ operation= Object)
  * So OOP is a way of organizing and structuring a program around objects&#x20;
  * Thinking in OOPs way helps us to break a problem into a smaller, more manageable piece and think about each piece individually, rather than trying to understand the entire problem at once.
  * Just like in the world of algorithms, where we try to divide a problem into smaller parts in order to find an optimal solution using the computer we are dividing our program into smaller parts in order to find an optimal code for developers.



<figure><img src="../../../.gitbook/assets/image (141).png" alt=""><figcaption></figcaption></figure>

## Principles of Object Oriented Programming (OOP)

* There are four fundamental principles of OOP:
  * <mark style="color:yellow;">**Abstraction**</mark>
    * Modularity
    * Helps us to program an interface and hide implementation details.
    * Several ways to implement abstraction in JAVA
      * Through abstract classes
        * using abstract keyword in a class.
        * in java marking a class as 'abstract' means that it cannot be instantiated.
        * we are using abstract class to define a contract. A contract about what things the class must do, not how those things are done.&#x20;
        * To use the power about abstract classes, think about extension rather than initialization.
      * Through Interfaces
        * Similar to abstract classes.
        * We can use interface keyword to define an interface.
    * Implementing abstraction can be different using these methods
      * The main reason interfaces exist is to allow an object to implement multiple abstractions since JAVA does not allow a class to extend more than one class due to issues with multiple inheritance
      * Types of methods: Interfaces can only have abstract method, while abstract classes can have both abstract and non-abstract methods.
      * Types of variables: abstract class can have final, non-final, static and non-static variables while interfaces only have static and final variables.
      * Accessibility of data members: Members of jave interface are public by default, while a java abstract class can have private, protected and other access modifiers.
  * <mark style="color:yellow;">**Encapsulation**</mark>
    * Code reusability
    * Ability of a system to hide information in such a way that it cannot be accessed or manipulated directly by other entities.
    * Allows us to have a better control and avid unpredictable state changes to your system. for ex-- in the car example, if we directly expose our engine object, don't you think anyone could call engine.ignote() without the keys?"
    * The concept of getters and setters is a common technique used to protect the data within an object. This is done by marking attributes of a class as private or protected and providing public methods, known as getters and setters.
    * Difference between encapsulation and abstraction&#x20;
      * both are closely related concepts
      * encapsulation bundles data and methods that operate on that data within a single unit or object
      * Abstraction exposes only the necessary information and hides the implementation details
      * Main difference between these is the the intent behind them, Abstraction is used to simplify and make the interface more user friendly, while encapsulation use used to control the access and modification of data within an object.
  * <mark style="color:yellow;">**Inheritance**</mark>
    * extensibility
    * mechanism that allows an object to reuse or extend the functionality of another object.
    * It allows a subclass or derived class to inherit the properties and methods of a superclass or a base class. In this way the subclass can have access to the functionality of the super class.
    * when designing with inheritance one can place common code in a super class and design more specific classes that are subclasses of the superclass. These sub classes will now inherit the members of the superclass and use them as if they were its own members&#x20;
    * This approach makes it easier to reuse and extend code since one can use the inherited members in the subclass without the need to rewrite them.
  * <mark style="color:yellow;">**Polymorphism**</mark>
    * data hiding
    * ability of an object to behave differently based on the context of its invocation.
    * **Compile time polymorphism (method overloading)** -- One can create multiple implementation of a method with different arguments. Due to this the same method will behave differently based on the number and type of arguments provided.
    * **Runtime polymorphism (method overriding) --** decision of which method to invoke is made at runtime, rather than at compile time. In method overriding, a subclass or derived class provide its own implementation of a method that is defined in the superl class or base class. when we call a method on an object of the subclass, the subclass' implementation of the method will be invoked .
    * overloading requires different parameters (number, order, or type) for methods with the same name within the same class, while overriding requires the same parameters and return type (or a covariant
    *

## Introduction of OOPs concept in C++

* The idea behind Class and Object is that we try to group attributes, behaviour and relationships of real-world objects in meaningful fashion. So the idea of class and object helps us define such structures in real life software.
  *   <mark style="color:yellow;">Class</mark>

      * fundamental building block of OOPs in C++.
      * It is a user defined data type that encapsulates data and methods into a single unit and serves as a blueprint for creating objects.
      * so each object contains data and methods that work on that data&#x20;
        * when a class is defined, no memory is allocated
        * Memory is allocated when an object is created


  * <mark style="color:yellow;">Object</mark>
    * It is an instance of a class created with specific data.
    * memory is allotted when an object is created
* <mark style="color:yellow;">Encapsulation</mark>
  * On large scale software projects it is crucial to take care of accessibility and security of our data and encapsulation helps us achieve this goal.
  * it hides the details of objects from the outside world and only exposes a public interface to interact with the object.
  * So it will help us to achieve abstraction because user of the object do not need to know the implementation details to use the object.
  * To achieve encapsulation we use private, protected and public.
  * private member can only be accessed within the class, while the public members can be accessed from anywhere the protected member is similar to private, but its scope is only limited to the same class or derived classes.
  * One of the best practices is to declare class variables as private and provide public getter and setter methods to access and modify . This will ensure that data within an object is accessed and modified in a controlled and consistent way.
  * in addition to data, we can also declare some internal methods as private. These methods will only be accessed by methods defined inside the class.
* <mark style="color:yellow;">Inheritance</mark>
  * helps in reusing existing code or extend existing functions
  * helps us create new classes based on existing classes. It is a process by which derived classes inherit attributes and methods of their base class(super class or parent class)
  * Each derived class can also implement its attributes and methods while still being able to reuse attributes or methods of the parent class.
* <mark style="color:yellow;">Polymorphism</mark>&#x20;
  * presents the common interface to perform a single action in different ways.
  * There are two types of polymorphism in C++
    * Compile time (static binding)
      * methods overloading and operator overloading
      * method overloading multiple methods can have the same name but different parameters list.&#x20;
      * ex-- myFunc(int a, int b) has two parameters int a and int b  while myFunc(float a, int b) has the parameter int and float&#x20;
      * similarly in operator overloading operators such as +,-,\*,/ can be overloaded to work with user-defined data types.
    * Runtime (Dynamic binding)
      * ability of objects of different classes to be treated as if they are object of common parent class.
      * This allows the same method name to be used for different purposes in different classes.
      * We can achieve this using inheritance and abstract class
      * abstract class -- it contains at least one pure virtual function and it cannot be instantiated directly. we use it to provide a common blueprint for its derived classes by specifying a set of virtual functions that must be implemented by the derived classes.
      * pure virtual function it is a function declared by appending =0 to the function declaration in a base class but it has no implementation. It must be overridden by derived classes.
      * The main idea of runtime polymorphism is that we define the abstract base class( which contains pure virtual functions) and derived classes inherit from it. Derived class provide the concrete implementation of the virtual functions defined in the base class .
      * After that we can assign any object of a derived class with the base class pointer, when we call the virtual function using a base class pointer, the appropriate implementation of the virtual functions determined at runtime based on the type of object being pointed to.
      * So this idea will enable different derived classes to have their own implementation of the same function.
* Abstraction
  * process of providing only the essential details to the users, so the user only needs to know what the code does, not how it does it.
  * Ex-- to drive a car, one only needs to know the driving process and not the mechanics of the engine.
  * to achieve abstraction on C++
    * using abstract classes
    * using interfaces&#x20;
    * using encapsulation&#x20;
    * using header files
    * shape class
    * creating derived classes
    * using polymorphism
