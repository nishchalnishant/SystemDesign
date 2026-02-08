# Design patterns\[refactoring guru]

CBS \[22]

* <mark style="color:$success;">Creational</mark> \[5] —&#x20;
  * provides various object creation mechanism which increase flexibility and reuse of existing code
  * Types&#x20;
    * singleton -&#x20;
      * Ensure that a class has only one instance while providing global access point to this instance.
    * factory -
      * Provides an interface (using abstract class) for creating object in superclass but allows subclasses to alter the type of objects that will be created.
    * abstract factory -&#x20;
      * Produce family of related objects without specifying their concrete classes.
    * builder -&#x20;
      * Construct complex objects step by step.&#x20;
      * this allows us to produce different types and representations of a object using the same construction code.
    * prototype -&#x20;
      * lets us copy existing object without making code dependent on their classes&#x20;
* <mark style="color:$success;">Behavioural</mark> \[11]
  * Patterns concerned with algorithms and the assignments of responsibilities between objects.
    * Observer
      * strategy -
        * lets us define a familty of alorithms, put each of them into a seperate class, and make their objects interchangable.
      * command —&#x20;
        * Turns a request into a stand alone object that contains all information about the request.&#x20;
        * this transformation lets us pass requests as a method arguments, delay or queue a requests execution and support undoable operations.
      * state--
        * lets an object alter its behaviour when its internal state changes
        * it appears if the object changed its class
      * visitor --
        * lets us separate algorithms from the objects on which they operate
      * momento
        * lets us save and restore the previous state of an object without revealing the details of its implementations
      * iterator --
        * Lets us traverse elements of a collection without exposing its underlying representation (list, stack, tree etc)
      * mediator
        * lets us reduce chaotic dependencies between objects/
        * the patterns restricts direct communications between the objects and forces them to collaborate only via a mediator object&#x20;
      * chain of responsibility --&#x20;
        * Lets us pass request along a chain of handlers, upon receiving a request, each handler decides either to process the request or to pass it to the next handler in the chain.
* <mark style="color:$success;">structural</mark> \[7]
  * These patterns explain how to assemble objects and classes into larger structures while keeping these structures flexible and efficient.
    * adapter
      * bridge
        * lets us split a large class or a set of closely related classes into two seperate hierarchies- abstraction and implementation which can be developed independently of each other.
      * composite
        * lets us compose objects into tree structure and then work with these structures as if they were individual objects.
      * decorator
        * lets us attach new behaviours to objects by placing these objects inside special wrapper objects that contain the behaviour
      * facade
        * provides a simplified interface to a library, a framework or any other complex set of classes.
      * flywheel
        * lets us fit more object into the same available amount of ram by sharing common parts of state between multiple objects instead of keeping all of the data in each object.
      * proxy
        * lets us provide a substitute or placeholder for another object.
        * A proxy controls access to the original object, allowing us to perform something either before or after the request gets through to the original object.



## Creational&#x20;

### Singleton

* Intent&#x20;
  * lets us ensure that a class has only one instance whule providing a global access point to this instance.
* problem it solves&#x20;
  * Ensure that a class has just a single instance
    * One might need a single instance of a class to control access to some shared resource like a database or a file
    * ex — If a object is already created, instead of creating a fresh object we will get the one we already created.
  * Provide a global access point to that instance&#x20;
    * Global variables with are used to store som essential values can be unsage as they can be overwritten.
    * just like a global variable the singleton patterns lets us access the object from anywhere in the program but it also protects that instance from being overwritten by other code
* Solution it provides&#x20;
  * Make the default constructor private, to prevent other objects from using the new operator with the singleton class
  * Create a static creation method that acts as a constructor, under the hood this method calls the private constructor to create an object and saves it in a static fiels.
  * All following calls to this method returns the cached objects.
* real world analogy --
  * govt is an excellent example of the singleton pattern
  * a county can have only one official govt.
  * Regardless of the personal iddentities of the individual who for the govt.
* Structure&#x20;
  *

      <figure><img src="../../../.gitbook/assets/image (32).png" alt=""><figcaption></figcaption></figure>


* applicability&#x20;
  * use the singleton pattern when a class in the program should have just a single instance available to all clients
  * ex — a single database object shared by different parts of the program
  * the singleton pattern disables all other means of creating objects of a class except for the spl creation method.
  * This method either creates a new object or returns an existing one if it has already been created.
  * use the singleton patterns when one needd strictier control over global variables&#x20;
  * unlike global variables the singleton pattern guaranttes that there is just one instance of the class.
  * nothing except the singleton class itself can replace the cashed instance
* How to implement&#x20;
  * add a private static filed to the class for storing singleton instance&#x20;
  * declare a public static creation method for creating the singleton instance&#x20;
  * implement "lazy initialization" inside the static method. it should create a new object on its first call and put it into the static field
  * The method should always return that instance on all subsequent calls.
  * Make the constructor of the class private, the static method of the class with stil be able to call the constructor but not the other objects
  * Go over the client code and replace all direct calls to the singleton constructor with calls to its static creation method
* Pros&#x20;
  * one can be sure that a class has only a sinlge instance&#x20;
  * one gain a global access point to that instance
  * the singleton object is initialized only when its requested for the first time
* Cons
  * violates the sinlge responsibility principle, the patterns solves two problems at a time
  * can mask bad design, for instance when componenets of the program know too much about each other&#x20;
  * pattern requires special tratment in a multithreaded environment so that multiple threads won't create a singleton object several times.
  * It may be difficult to unit test the client code of the singleton because many test frameworks rely on inheritance when producing mock objects
  * Since the constructor of the singleton class is provate and overriding static methods is impossible in most languages one will need to think of a creative way to mock the singleton or just don't write the tests
* Relation with other patterns&#x20;
  * A [Facade](https://refactoring.guru/design-patterns/facade) class can often be transformed into a [Singleton](https://refactoring.guru/design-patterns/singleton) since a single facade object is sufficient in most cases.
  * [Flyweight](https://refactoring.guru/design-patterns/flyweight) would resemble [Singleton](https://refactoring.guru/design-patterns/singleton) if you somehow managed to reduce all shared states of the objects to just one flyweight object. But there are two fundamental differences between these patterns:
    1. There should be only one Singleton instance, whereas a _Flyweight_ class can have multiple instances with different intrinsic states.
    2. The _Singleton_ object can be mutable. Flyweight objects are immutable.
  * [Abstract Factories](https://refactoring.guru/design-patterns/abstract-factory), [Builders](https://refactoring.guru/design-patterns/builder) and [Prototypes](https://refactoring.guru/design-patterns/prototype) can all be implemented as [Singletons](https://refactoring.guru/design-patterns/singleton).

{% code title="Thread safe singleton pattern" overflow="wrap" fullWidth="false" %}
```python
from threading import Lock, Thread


class SingletonMeta(type):
    """
    This is a thread-safe implementation of Singleton.
    """

    _instances = {}

    _lock: Lock = Lock()
    """
    We now have a lock object that will be used to synchronize threads during
    first access to the Singleton.
    """

    def __call__(cls, *args, **kwargs):
        """
        Possible changes to the value of the `__init__` argument do not affect
        the returned instance.
        """
        # Now, imagine that the program has just been launched. Since there's no
        # Singleton instance yet, multiple threads can simultaneously pass the
        # previous conditional and reach this point almost at the same time. The
        # first of them will acquire lock and will proceed further, while the
        # rest will wait here.
        with cls._lock:
            # The first thread to acquire the lock, reaches this conditional,
            # goes inside and creates the Singleton instance. Once it leaves the
            # lock block, a thread that might have been waiting for the lock
            # release may then enter this section. But since the Singleton field
            # is already initialized, the thread won't create a new object.
            if cls not in cls._instances:
                instance = super().__call__(*args, **kwargs)
                cls._instances[cls] = instance
        return cls._instances[cls]


class Singleton(metaclass=SingletonMeta):
    value: str = None
    """
    We'll use this property to prove that our Singleton really works.
    """

    def __init__(self, value: str) -> None:
        self.value = value

    def some_business_logic(self):
        """
        Finally, any singleton should define some business logic, which can be
        executed on its instance.
        """


def test_singleton(value: str) -> None:
    singleton = Singleton(value)
    print(singleton.value)


if __name__ == "__main__":
    # The client code.

    print("If you see the same value, then singleton was reused (yay!)\n"
          "If you see different values, "
          "then 2 singletons were created (booo!!)\n\n"
          "RESULT:\n")

    process1 = Thread(target=test_singleton, args=("FOO",))
    process2 = Thread(target=test_singleton, args=("BAR",))
    process1.start()
    process2.start()
```
{% endcode %}



### Factory

* Intent&#x20;
  * provides an interface for creating objects in a superclass, but allows subclasses to alter the type of objects that will be created.
* Problem
  * lets say we have a transportation code where most of the code is for trucks now we want to add ships as well, but this would require making changes to the entire codebase.
  * This would lead to a pretty nasty code over time.
* Solution&#x20;
  * Factory Method pattern suggests that you replace direct object construction calls (using the `new` operator) with calls to a special _factory_ method.&#x20;
  * Don’t worry: the objects are still created via the `new` operator, but it’s being called from within the factory method. Objects returned by a factory method are often referred to as _products._
  * At first glance, this change may look pointless: we just moved the constructor call from one part of the program to another.&#x20;
  * However, consider this: now you can override the factory method in a subclass and change the class of products being created by the method.
  *

      <figure><img src="../../../.gitbook/assets/image (33).png" alt=""><figcaption></figcaption></figure>

      <br>
  * For example, both `Truck` and `Ship` classes should implement the `Transport` interface, which declares a method called `deliver`.&#x20;
  * Each class implements this method differently: trucks deliver cargo by land, ships deliver cargo by sea. The factory method in the `RoadLogistics` class returns truck objects, whereas the factory method in the `SeaLogistics` class returns ships.
  * The code that uses the factory method (often called the _client_ code) doesn’t see a difference between the actual products returned by various subclasses.&#x20;
  * The client treats all the products as abstract `Transport`. The client knows that all transport objects are supposed to have the `deliver` method, but exactly how it works isn’t important to the client.
* Structure&#x20;
  *

      <figure><img src="../../../.gitbook/assets/image (34).png" alt=""><figcaption></figcaption></figure>


  * The **Product** declares the interface, which is common to all objects that can be produced by the creator and its subclasses.
  * **Concrete Products** are different implementations of the product interface
  * The **Creator** class declares the factory method that returns new product objects. It’s important that the return type of this method matches the product interface
  * **Concrete Creators** override the base factory method so it returns a different type of product
* Apllicability&#x20;
  * Use the Factory Method when you don’t know beforehand the exact types and dependencies of the objects your code should work with.
  * The Factory Method separates product construction code from the code that actually uses the product. Therefore it’s easier to extend the product construction code independently from the rest of the code.
  * For example, to add a new product type to the app, you’ll only need to create a new creator subclass and override the factory method in it.
  * Use the Factory Method when you want to provide users of your library or framework with a way to extend its internal components.
  * Inheritance is probably the easiest way to extend the default behavior of a library or framework. But how would the framework recognize that your subclass should be used instead of a standard component?
  * The solution is to reduce the code that constructs components across the framework into a single factory method and let anyone override this method in addition to extending the component itself.
  * Let’s see how that would work. Imagine that you write an app using an open source UI framework. Your app should have round buttons, but the framework only provides square ones.
  * You extend the standard `Button` class with a glorious `RoundButton` subclass. But now you need to tell the main `UIFramework` class to use the new button subclass instead of a default one.
  * To achieve this, you create a subclass `UIWithRoundButtons` from a base framework class and override its `createButton` method.&#x20;
  * While this method returns `Button` objects in the base class, you make your subclass return `RoundButton` objects.&#x20;
  * Now use the `UIWithRoundButtons` class instead of `UIFramework`. And that’s about it!
  * Use the Factory Method when you want to save system resources by reusing existing objects instead of rebuilding them each time.
  * You often experience this need when dealing with large, resource-intensive objects such as database connections, file systems, and network resources.
  *   Let’s think about what has to be done to reuse an existing object:

      1. First, you need to create some storage to keep track of all of the created objects.
      2. When someone requests an object, the program should look for a free object inside that pool.
      3. … and then return it to the client code.
      4. If there are no free objects, the program should create a new one (and add it to the pool).


  * That’s a lot of code! And it must all be put into a single place so that you don’t pollute the program with duplicate code.
  * Probably the most obvious and convenient place where this code could be placed is the constructor of the class whose objects we’re trying to reuse.&#x20;
  * However, a constructor must always return **new objects** by definition. It can’t return existing instances.
  * Therefore, you need to have a regular method capable of creating new objects as well as reusing existing ones. That sounds very much like a factory method.
* How to implement&#x20;
  * Make all products follow the same interface. This interface should declare methods that make sense in every product
  * Add an empty factory method inside the creator class. The return type of the method should match the common product interface
  * In the creator’s code find all references to product constructors. One by one, replace them with calls to the factory method, while extracting the product creation code into the factory method
  * You might need to add a temporary parameter to the factory method to control the type of returned product
  * At this point, the code of the factory method may look pretty ugly. It may have a large `switch` statement that picks which product class to instantiate. But don’t worry, we’ll fix it soon enough
  * Now, create a set of creator subclasses for each type of product listed in the factory method. Override the factory method in the subclasses and extract the appropriate bits of construction code from the base method
  * If there are too many product types and it doesn’t make sense to create subclasses for all of them, you can reuse the control parameter from the base class in subclasses
  * If, after all of the extractions, the base factory method has become empty, you can make it abstract. If there’s something left, you can make it a default behavior of the method
* Pros
  * You avoid tight coupling between the creator and the concrete products
  * &#x20;_Single Responsibility Principle_. You can move the product creation code into one place in the program, making the code easier to support
  * _Open/Closed Principle_. You can introduce new types of products into the program without breaking existing client code
* Cons&#x20;
  * The code may become more complicated since you need to introduce a lot of new subclasses to implement the pattern. The best case scenario is when you’re introducing the pattern into an existing hierarchy of creator classes
*   Relation with other pattern

    * Many designs start by using [Factory Method](https://refactoring.guru/design-patterns/factory-method) (less complicated and more customizable via subclasses) and evolve toward [Abstract Factory](https://refactoring.guru/design-patterns/abstract-factory), [Prototype](https://refactoring.guru/design-patterns/prototype), or [Builder](https://refactoring.guru/design-patterns/builder) (more flexible, but more complicated).
    * [Abstract Factory](https://refactoring.guru/design-patterns/abstract-factory) classes are often based on a set of [Factory Methods](https://refactoring.guru/design-patterns/factory-method), but you can also use [Prototype](https://refactoring.guru/design-patterns/prototype) to compose the methods on these classes.
    * You can use [Factory Method](https://refactoring.guru/design-patterns/factory-method) along with [Iterator](https://refactoring.guru/design-patterns/iterator) to let collection subclasses return different types of iterators that are compatible with the collections.
    * [Prototype](https://refactoring.guru/design-patterns/prototype) isn’t based on inheritance, so it doesn’t have its drawbacks. On the other hand, _Prototype_ requires a complicated initialization of the cloned object. [Factory Method](https://refactoring.guru/design-patterns/factory-method) is based on inheritance but doesn’t require an initialization step.
    * [Factory Method](https://refactoring.guru/design-patterns/factory-method) is a specialization of [Template Method](https://refactoring.guru/design-patterns/template-method). At the same time, a _Factory Method_ may serve as a step in a large _Template Method_.



{% code overflow="wrap" fullWidth="false" %}
```python
from __future__ import annotations
from abc import ABC, abstractmethod


class Creator(ABC):
    """
    The Creator class declares the factory method that is supposed to return an
    object of a Product class. The Creator's subclasses usually provide the
    implementation of this method.
    """

    @abstractmethod
    def factory_method(self):
        """
        Note that the Creator may also provide some default implementation of
        the factory method.
        """
        pass

    def some_operation(self) -> str:
        """
        Also note that, despite its name, the Creator's primary responsibility
        is not creating products. Usually, it contains some core business logic
        that relies on Product objects, returned by the factory method.
        Subclasses can indirectly change that business logic by overriding the
        factory method and returning a different type of product from it.
        """

        # Call the factory method to create a Product object.
        product = self.factory_method()

        # Now, use the product.
        result = f"Creator: The same creator's code has just worked with {product.operation()}"

        return result


"""
Concrete Creators override the factory method in order to change the resulting
product's type.
"""


class ConcreteCreator1(Creator):
    """
    Note that the signature of the method still uses the abstract product type,
    even though the concrete product is actually returned from the method. This
    way the Creator can stay independent of concrete product classes.
    """

    def factory_method(self) -> Product:
        return ConcreteProduct1()


class ConcreteCreator2(Creator):
    def factory_method(self) -> Product:
        return ConcreteProduct2()


class Product(ABC):
    """
    The Product interface declares the operations that all concrete products
    must implement.
    """

    @abstractmethod
    def operation(self) -> str:
        pass


"""
Concrete Products provide various implementations of the Product interface.
"""


class ConcreteProduct1(Product):
    def operation(self) -> str:
        return "{Result of the ConcreteProduct1}"


class ConcreteProduct2(Product):
    def operation(self) -> str:
        return "{Result of the ConcreteProduct2}"


def client_code(creator: Creator) -> None:
    """
    The client code works with an instance of a concrete creator, albeit through
    its base interface. As long as the client keeps working with the creator via
    the base interface, you can pass it any creator's subclass.
    """

    print(f"Client: I'm not aware of the creator's class, but it still works.\n"
          f"{creator.some_operation()}", end="")


if __name__ == "__main__":
    print("App: Launched with the ConcreteCreator1.")
    client_code(ConcreteCreator1())
    print("\n")

    print("App: Launched with the ConcreteCreator2.")
    client_code(ConcreteCreator2())

```
{% endcode %}

### Abstract Factory&#x20;

* intent --
  * lets us produce family of related object without specifying their concrete classes
* Problem
  * lets say we have a furniture shop simulator, where&#x20;
    * A family of related products, say: `Chair` + `Sofa` + `CoffeeTable`
    * Several variants of this family. For example, products `Chair` + `Sofa` + `CoffeeTable` are available in these variants: `Modern`, `Victorian`, `ArtDeco`
    * You need a way to create individual furniture objects so that they match other objects of the same family. Customers get quite mad when they receive non-matching furniture
    * Also, you don’t want to change existing code when adding new products or families of products to the program. Furniture vendors update their catalogs very often, and you wouldn’t want to change the core code each time it happens.
*   Solution --

    * explicitly declare interfaces for each distinct product of the product family (e.g., chair, sofa or coffee table).
    * Then you can make all variants of products follow those interfaces.
    * For example, all chair variants can implement the `Chair` interface; all coffee table variants can implement the `CoffeeTable` interface, and so on.
    * The next move is to declare the _Abstract Factory_—an interface with a list of creation methods for all products that are part of the product family (for example, `createChair`, `createSofa` and `createCoffeeTable`).&#x20;
    * These methods must return **abstract** product types represented by the interfaces we extracted previously: `Chair`, `Sofa`, `CoffeeTable` and so on.
    * Now, how about the product variants? For each variant of a product family, we create a separate factory class based on the `AbstractFactory` interface. A factory is a class that returns products of a particular kind.&#x20;
    * For example, the `ModernFurnitureFactory` can only create `ModernChair`, `ModernSofa` and `ModernCoffeeTable` objects.
    *

        <figure><img src="../../../.gitbook/assets/image (35).png" alt=""><figcaption></figcaption></figure>
    * The client code has to work with both factories and products via their respective abstract interfaces.&#x20;
    * This lets you change the type of a factory that you pass to the client code, as well as the product variant that the client code receives, without breaking the actual client code.
    * Say the client wants a factory to produce a chair. The client doesn’t have to be aware of the factory’s class, nor does it matter what kind of chair it gets.&#x20;
    * Whether it’s a Modern model or a Victorian-style chair, the client must treat all chairs in the same manner, using the abstract `Chair` interface.&#x20;
    * With this approach, the only thing that the client knows about the chair is that it implements the `sitOn` method in some way.&#x20;
    * Also, whichever variant of the chair is returned, it’ll always match the type of sofa or coffee table produced by the same factory object.
    * There’s one more thing left to clarify: if the client is only exposed to the abstract interfaces, what creates the actual factory objects?&#x20;
    * Usually, the application creates a concrete factory object at the initialization stage. Just before that, the app must select the factory type depending on the configuration or the environment settings.



    <br>
* Structure&#x20;
  *

      <figure><img src="../../../.gitbook/assets/image (36).png" alt=""><figcaption></figcaption></figure>
  * **Abstract Products** declare interfaces for a set of distinct but related products which make up a product family
  * **Concrete Products** are various implementations of abstract products, grouped by variants. Each abstract product (chair/sofa) must be implemented in all given variants (Victorian/Modern
  * The **Abstract Factory** interface declares a set of methods for creating each of the abstract products.
  * **Concrete Factories** implement creation methods of the abstract factory. Each concrete factory corresponds to a specific variant of products and creates only those product variants.
  * Although concrete factories instantiate concrete products, signatures of their creation methods must return corresponding _abstract_ products.&#x20;
  * This way the client code that uses a factory doesn’t get coupled to the specific variant of the product it gets from a factory. The **Client** can work with any concrete factory/product variant, as long as it communicates with their objects via abstract interfaces.
* Applicability --
  * Use the Abstract Factory when your code needs to work with various families of related products, but you don’t want it to depend on the concrete classes of those products—they might be unknown beforehand or you simply want to allow for future extensibility
  * The Abstract Factory provides you with an interface for creating objects from each class of the product family. As long as your code creates objects via this interface, you don’t have to worry about creating the wrong variant of a product which doesn’t match the products already created by your app.
  * Consider implementing the Abstract Factory when you have a class with a set of [Factory Methods](https://refactoring.guru/design-patterns/factory-method) that blur its primary responsibility
  * In a well-designed program _each class is responsible only for one thing_. When a class deals with multiple product types, it may be worth extracting its factory methods into a stand-alone factory class or a full-blown Abstract Factory implementation
* How to implement --
  * Map out a matrix of distinct product types versus variants of these products.
  * Declare abstract product interfaces for all product types. Then make all concrete product classes implement these interfaces.
  * Declare the abstract factory interface with a set of creation methods for all abstract products.
  * Implement a set of concrete factory classes, one for each product variant.
  * Create factory initialization code somewhere in the app. It should instantiate one of the concrete factory classes, depending on the application configuration or the current environment. Pass this factory object to all classes that construct products.
  * Scan through the code and find all direct calls to product constructors. Replace them with calls to the appropriate creation method on the factory object.
* Pros&#x20;
  * You can be sure that the products you’re getting from a factory are compatible with each other
  * You avoid tight coupling between concrete products and client code.
  * _Single Responsibility Principle_. You can extract the product creation code into one place, making the code easier to support
  * _Open/Closed Principle_. You can introduce new variants of products without breaking existing client code.
* Cons
  * The code may become more complicated than it should be, since a lot of new interfaces and classes are introduced along with the pattern.
* Relation with other patterns&#x20;
  * Many designs start by using [Factory Method](https://refactoring.guru/design-patterns/factory-method) (less complicated and more customizable via subclasses) and evolve toward [Abstract Factory](https://refactoring.guru/design-patterns/abstract-factory), [Prototype](https://refactoring.guru/design-patterns/prototype), or [Builder](https://refactoring.guru/design-patterns/builder) (more flexible, but more complicated).
  * [Builder](https://refactoring.guru/design-patterns/builder) focuses on constructing complex objects step by step. [Abstract Factory](https://refactoring.guru/design-patterns/abstract-factory) specializes in creating families of related objects. _Abstract Factory_ returns the product immediately, whereas _Builder_ lets you run some additional construction steps before fetching the product.
  * [Abstract Factory](https://refactoring.guru/design-patterns/abstract-factory) classes are often based on a set of [Factory Methods](https://refactoring.guru/design-patterns/factory-method), but you can also use [Prototype](https://refactoring.guru/design-patterns/prototype) to compose the methods on these classes.
  * [Abstract Factory](https://refactoring.guru/design-patterns/abstract-factory) can serve as an alternative to [Facade](https://refactoring.guru/design-patterns/facade) when you only want to hide the way the subsystem objects are created from the client code.
  * You can use [Abstract Factory](https://refactoring.guru/design-patterns/abstract-factory) along with [Bridge](https://refactoring.guru/design-patterns/bridge). This pairing is useful when some abstractions defined by _Bridge_ can only work with specific implementations. In this case, _Abstract Factory_ can encapsulate these relations and hide the complexity from the client code.
  * [Abstract Factories](https://refactoring.guru/design-patterns/abstract-factory), [Builders](https://refactoring.guru/design-patterns/builder) and [Prototypes](https://refactoring.guru/design-patterns/prototype) can all be implemented as [Singletons](https://refactoring.guru/design-patterns/singleton).
* Code&#x20;

```python
from __future__ import annotations
from abc import ABC, abstractmethod


class AbstractFactory(ABC):
    """
    The Abstract Factory interface declares a set of methods that return
    different abstract products. These products are called a family and are
    related by a high-level theme or concept. Products of one family are usually
    able to collaborate among themselves. A family of products may have several
    variants, but the products of one variant are incompatible with products of
    another.
    """
    @abstractmethod
    def create_product_a(self) -> AbstractProductA:
        pass

    @abstractmethod
    def create_product_b(self) -> AbstractProductB:
        pass


class ConcreteFactory1(AbstractFactory):
    """
    Concrete Factories produce a family of products that belong to a single
    variant. The factory guarantees that resulting products are compatible. Note
    that signatures of the Concrete Factory's methods return an abstract
    product, while inside the method a concrete product is instantiated.
    """

    def create_product_a(self) -> AbstractProductA:
        return ConcreteProductA1()

    def create_product_b(self) -> AbstractProductB:
        return ConcreteProductB1()


class ConcreteFactory2(AbstractFactory):
    """
    Each Concrete Factory has a corresponding product variant.
    """

    def create_product_a(self) -> AbstractProductA:
        return ConcreteProductA2()

    def create_product_b(self) -> AbstractProductB:
        return ConcreteProductB2()


class AbstractProductA(ABC):
    """
    Each distinct product of a product family should have a base interface. All
    variants of the product must implement this interface.
    """

    @abstractmethod
    def useful_function_a(self) -> str:
        pass


"""
Concrete Products are created by corresponding Concrete Factories.
"""


class ConcreteProductA1(AbstractProductA):
    def useful_function_a(self) -> str:
        return "The result of the product A1."


class ConcreteProductA2(AbstractProductA):
    def useful_function_a(self) -> str:
        return "The result of the product A2."


class AbstractProductB(ABC):
    """
    Here's the the base interface of another product. All products can interact
    with each other, but proper interaction is possible only between products of
    the same concrete variant.
    """
    @abstractmethod
    def useful_function_b(self) -> None:
        """
        Product B is able to do its own thing...
        """
        pass

    @abstractmethod
    def another_useful_function_b(self, collaborator: AbstractProductA) -> None:
        """
        ...but it also can collaborate with the ProductA.

        The Abstract Factory makes sure that all products it creates are of the
        same variant and thus, compatible.
        """
        pass


"""
Concrete Products are created by corresponding Concrete Factories.
"""


class ConcreteProductB1(AbstractProductB):
    def useful_function_b(self) -> str:
        return "The result of the product B1."

    """
    The variant, Product B1, is only able to work correctly with the variant,
    Product A1. Nevertheless, it accepts any instance of AbstractProductA as an
    argument.
    """

    def another_useful_function_b(self, collaborator: AbstractProductA) -> str:
        result = collaborator.useful_function_a()
        return f"The result of the B1 collaborating with the ({result})"


class ConcreteProductB2(AbstractProductB):
    def useful_function_b(self) -> str:
        return "The result of the product B2."

    def another_useful_function_b(self, collaborator: AbstractProductA):
        """
        The variant, Product B2, is only able to work correctly with the
        variant, Product A2. Nevertheless, it accepts any instance of
        AbstractProductA as an argument.
        """
        result = collaborator.useful_function_a()
        return f"The result of the B2 collaborating with the ({result})"


def client_code(factory: AbstractFactory) -> None:
    """
    The client code works with factories and products only through abstract
    types: AbstractFactory and AbstractProduct. This lets you pass any factory
    or product subclass to the client code without breaking it.
    """
    product_a = factory.create_product_a()
    product_b = factory.create_product_b()

    print(f"{product_b.useful_function_b()}")
    print(f"{product_b.another_useful_function_b(product_a)}", end="")


if __name__ == "__main__":
    """
    The client code can work with any concrete factory class.
    """
    print("Client: Testing client code with the first factory type:")
    client_code(ConcreteFactory1())

    print("\n")

    print("Client: Testing the same client code with the second factory type:")
    client_code(ConcreteFactory2())

```



### Builder&#x20;

* Intent&#x20;
  * construct complex objects step by step.&#x20;
  * The pattern allows you to produce different types and representations of an object using the same construction code.
* Problem&#x20;
  * Imagine a complex object that requires laborious, step-by-step initialisation of many fields and nested objects.&#x20;
  * Such initialisation code is usually buried inside a monstrous constructor with lots of parameters. Or even worse: scattered all over the client code.
    *

        <figure><img src="../../../.gitbook/assets/image (37).png" alt=""><figcaption></figcaption></figure>
  * For example, let’s think about how to create a `House` object. To build a simple house, you need to construct four walls and a floor, install a door, fit a pair of windows, and build a roof. But what if you want a bigger, brighter house, with a backyard and other goodies (like a heating system, plumbing, and electrical wiring)?
  * The simplest solution is to extend the base `House` class and create a set of subclasses to cover all combinations of the parameters.&#x20;
  * But eventually you’ll end up with a considerable number of subclasses. Any new parameter, such as the porch style, will require growing this hierarchy even more.
  * There’s another approach that doesn’t involve breeding subclasses.&#x20;
  * You can create a giant constructor right in the base `House` class with all possible parameters that control the house object. While this approach indeed eliminates the need for subclasses, it creates another problem.
  * In most cases most of the parameters will be unused, making [the constructor calls pretty ugly](https://refactoring.guru/smells/long-parameter-list). For instance, only a fraction of houses have swimming pools, so the parameters related to swimming pools will be useless nine times out of ten.
* Solution&#x20;
  * Builder pattern suggests that you extract the object construction code out of its own class and move it to separate objects called _builders_
  * The pattern organizes object construction into a set of steps (`buildWalls`, `buildDoor`, etc.). To create an object, you execute a series of these steps on a builder object.&#x20;
  * The important part is that you don’t need to call all of the steps. You can call only those steps that are necessary for producing a particular configuration of an object.
  * Some of the construction steps might require different implementation when you need to build various representations of the product.&#x20;
  * For example, walls of a cabin may be built of wood, but the castle walls must be built with stone.
  * In this case, you can create several different builder classes that implement the same set of building steps, but in a different manner.&#x20;
  * Then you can use these builders in the construction process (i.e., an ordered set of calls to the building steps) to produce different kinds of objects.
  * For example, imagine a builder that builds everything from wood and glass, a second one that builds everything with stone and iron and a third one that uses gold and diamonds.&#x20;
  * By calling the same set of steps, you get a regular house from the first builder, a small castle from the second and a palace from the third.&#x20;
  * However, this would only work if the client code that calls the building steps is able to interact with builders using a common interface.
* Director&#x20;
  * You can go further and extract a series of calls to the builder steps you use to construct a product into a separate class called _director_.&#x20;
  * The director class defines the order in which to execute the building steps, while the builder provides the implementation for those steps.
  * Having a director class in your program isn’t strictly necessary. You can always call the building steps in a specific order directly from the client code.&#x20;
  * However, the director class might be a good place to put various construction routines so you can reuse them across your program.
  * In addition, the director class completely hides the details of product construction from the client code.&#x20;
  * The client only needs to associate a builder with a director, launch the construction with the director, and get the result from the builder.
* Structure&#x20;
  *

      <figure><img src="../../../.gitbook/assets/image (38).png" alt=""><figcaption></figcaption></figure>
  * The **Builder** interface declares product construction steps that are common to all types of builders.
  * **Concrete Builders** provide different implementations of the construction steps. Concrete builders may produce products that don’t follow the common interface.
  * **Products** are resulting objects. Products constructed by different builders don’t have to belong to the same class hierarchy or interface.
  * The **Director** class defines the order in which to call construction steps, so you can create and reuse specific configurations of products.
  * The **Client** must associate one of the builder objects with the director. Usually, it’s done just once, via parameters of the director’s constructor. Then the director uses that builder object for all further construction.&#x20;
  * However, there’s an alternative approach for when the client passes the builder object to the production method of the director. In this case, you can use a different builder each time you produce something with the director.
* Applicability --
  * Use the Builder pattern to get rid of a “telescoping constructor”.
  * Say you have a constructor with ten optional parameters. Calling such a beast is very inconvenient; therefore, you overload the constructor and create several shorter versions with fewer parameters. These constructors still refer to the main one, passing some default values into any omitted parameters
  * The Builder pattern lets you build objects step by step, using only those steps that you really need. After implementing the pattern, you don’t have to cram dozens of parameters into your constructors anymore.
  * Use the Builder pattern when you want your code to be able to create different representations of some product (for example, stone and wooden houses).
  * The Builder pattern can be applied when construction of various representations of the product involves similar steps that differ only in the details.
  * The base builder interface defines all possible construction steps, and concrete builders implement these steps to construct particular representations of the product. Meanwhile, the director class guides the order of construction.
  * Use the Builder to construct [Composite](https://refactoring.guru/design-patterns/composite) trees or other complex objects.
  * &#x20;The Builder pattern lets you construct products step-by-step. You could defer execution of some steps without breaking the final product. You can even call steps recursively, which comes in handy when you need to build an object tree.
  * A builder doesn’t expose the unfinished product while running construction steps. This prevents the client code from fetching an incomplete result.
* How to implement --
  * Make sure that you can clearly define the common construction steps for building all available product representations. Otherwise, you won’t be able to proceed with implementing the pattern.
  * Declare these steps in the base builder interface.
  * Create a concrete builder class for each of the product representations and implement their construction steps.
  * Don’t forget about implementing a method for fetching the result of the construction. The reason why this method can’t be declared inside the builder interface is that various builders may construct products that don’t have a common interface.&#x20;
  * Therefore, you don’t know what would be the return type for such a method. However, if you’re dealing with products from a single hierarchy, the fetching method can be safely added to the base interface.
  * Think about creating a director class. It may encapsulate various ways to construct a product using the same builder object.
  * The client code creates both the builder and the director objects. Before construction starts, the client must pass a builder object to the director. Usually, the client does this only once, via parameters of the director’s class constructor. The director uses the builder object in all further construction. There’s an alternative approach, where the builder is passed to a specific product construction method of the director.
  * The construction result can be obtained directly from the director only if all products follow the same interface. Otherwise, the client should fetch the result from the builder.
* Pros&#x20;
  * You can construct objects step-by-step, defer construction steps or run steps recursively.
  * You can reuse the same construction code when building various representations of products.
  * _Single Responsibility Principle_. You can isolate complex construction code from the business logic of the product.
* Cons&#x20;
  * The overall complexity of the code increases since the pattern requires creating multiple new classes.
* Relations with other patterns&#x20;
  * Many designs start by using [Factory Method](https://refactoring.guru/design-patterns/factory-method) (less complicated and more customizable via subclasses) and evolve toward [Abstract Factory](https://refactoring.guru/design-patterns/abstract-factory), [Prototype](https://refactoring.guru/design-patterns/prototype), or [Builder](https://refactoring.guru/design-patterns/builder) (more flexible, but more complicated).
  * [Builder](https://refactoring.guru/design-patterns/builder) focuses on constructing complex objects step by step. [Abstract Factory](https://refactoring.guru/design-patterns/abstract-factory) specializes in creating families of related objects. _Abstract Factory_ returns the product immediately, whereas _Builder_ lets you run some additional construction steps before fetching the product.
  * You can use [Builder](https://refactoring.guru/design-patterns/builder) when creating complex [Composite](https://refactoring.guru/design-patterns/composite) trees because you can program its construction steps to work recursively.
  * You can combine [Builder](https://refactoring.guru/design-patterns/builder) with [Bridge](https://refactoring.guru/design-patterns/bridge): the director class plays the role of the abstraction, while different builders act as implementations.
  * [Abstract Factories](https://refactoring.guru/design-patterns/abstract-factory), [Builders](https://refactoring.guru/design-patterns/builder) and [Prototypes](https://refactoring.guru/design-patterns/prototype) can all be implemented as [Singletons](https://refactoring.guru/design-patterns/singleton).
* Code

```python
from __future__ import annotations
from abc import ABC, abstractmethod
from typing import Any


class Builder(ABC):
    """
    The Builder interface specifies methods for creating the different parts of
    the Product objects.
    """

    @property
    @abstractmethod
    def product(self) -> None:
        pass

    @abstractmethod
    def produce_part_a(self) -> None:
        pass

    @abstractmethod
    def produce_part_b(self) -> None:
        pass

    @abstractmethod
    def produce_part_c(self) -> None:
        pass


class ConcreteBuilder1(Builder):
    """
    The Concrete Builder classes follow the Builder interface and provide
    specific implementations of the building steps. Your program may have
    several variations of Builders, implemented differently.
    """

    def __init__(self) -> None:
        """
        A fresh builder instance should contain a blank product object, which is
        used in further assembly.
        """
        self.reset()

    def reset(self) -> None:
        self._product = Product1()

    @property
    def product(self) -> Product1:
        """
        Concrete Builders are supposed to provide their own methods for
        retrieving results. That's because various types of builders may create
        entirely different products that don't follow the same interface.
        Therefore, such methods cannot be declared in the base Builder interface
        (at least in a statically typed programming language).

        Usually, after returning the end result to the client, a builder
        instance is expected to be ready to start producing another product.
        That's why it's a usual practice to call the reset method at the end of
        the `getProduct` method body. However, this behavior is not mandatory,
        and you can make your builders wait for an explicit reset call from the
        client code before disposing of the previous result.
        """
        product = self._product
        self.reset()
        return product

    def produce_part_a(self) -> None:
        self._product.add("PartA1")

    def produce_part_b(self) -> None:
        self._product.add("PartB1")

    def produce_part_c(self) -> None:
        self._product.add("PartC1")


class Product1():
    """
    It makes sense to use the Builder pattern only when your products are quite
    complex and require extensive configuration.

    Unlike in other creational patterns, different concrete builders can produce
    unrelated products. In other words, results of various builders may not
    always follow the same interface.
    """

    def __init__(self) -> None:
        self.parts = []

    def add(self, part: Any) -> None:
        self.parts.append(part)

    def list_parts(self) -> None:
        print(f"Product parts: {', '.join(self.parts)}", end="")


class Director:
    """
    The Director is only responsible for executing the building steps in a
    particular sequence. It is helpful when producing products according to a
    specific order or configuration. Strictly speaking, the Director class is
    optional, since the client can control builders directly.
    """

    def __init__(self) -> None:
        self._builder = None

    @property
    def builder(self) -> Builder:
        return self._builder

    @builder.setter
    def builder(self, builder: Builder) -> None:
        """
        The Director works with any builder instance that the client code passes
        to it. This way, the client code may alter the final type of the newly
        assembled product.
        """
        self._builder = builder

    """
    The Director can construct several product variations using the same
    building steps.
    """

    def build_minimal_viable_product(self) -> None:
        self.builder.produce_part_a()

    def build_full_featured_product(self) -> None:
        self.builder.produce_part_a()
        self.builder.produce_part_b()
        self.builder.produce_part_c()


if __name__ == "__main__":
    """
    The client code creates a builder object, passes it to the director and then
    initiates the construction process. The end result is retrieved from the
    builder object.
    """

    director = Director()
    builder = ConcreteBuilder1()
    director.builder = builder

    print("Standard basic product: ")
    director.build_minimal_viable_product()
    builder.product.list_parts()

    print("\n")

    print("Standard full featured product: ")
    director.build_full_featured_product()
    builder.product.list_parts()

    print("\n")

    # Remember, the Builder pattern can be used without a Director class.
    print("Custom product: ")
    builder.produce_part_a()
    builder.produce_part_b()
    builder.product.list_parts()
```



### Prototype&#x20;

* Intent&#x20;
  * lets you copy existing objects without making your code dependent on their classes.
* Problem&#x20;
  * Say you have an object, and you want to create an exact copy of it. How would you do it? First, you have to create a new object of the same class.&#x20;
  * Then you have to go through all the fields of the original object and copy their values over to the new object.
  * Nice! But there’s a catch. Not all objects can be copied that way because some of the object’s fields may be private and not visible from outside of the object itself.
  * There’s one more problem with the direct approach. Since you have to know the object’s class to create a duplicate, your code becomes dependent on that class.&#x20;
  * If the extra dependency doesn’t scare you, there’s another catch.&#x20;
  * Sometimes you only know the interface that the object follows, but not its concrete class, when, for example, a parameter in a method accepts any objects that follow some interface.
* Solution&#x20;
  * delegates the cloning process to the actual objects that are being cloned.
  * The pattern declares a common interface for all objects that support cloning.&#x20;
  * This interface lets you clone an object without coupling your code to the class of that object. Usually, such an interface contains just a single `clone` method.
  * The implementation of the `clone` method is very similar in all classes.&#x20;
  * The method creates an object of the current class and carries over all of the field values of the old object into the new one.&#x20;
  * You can even copy private fields because most programming languages let objects access private fields of other objects that belong to the same class.
  * An object that supports cloning is called a _prototype_. When your objects have dozens of fields and hundreds of possible configurations, cloning them might serve as an alternative to subclassing.
  * Here’s how it works: you create a set of objects, configured in various ways. When you need an object like the one you’ve configured, you just clone a prototype instead of constructing a new object from scratch.
* Real world analogy&#x20;
  * real life, prototypes are used for performing various tests before starting mass production of a product.&#x20;
  * However, in this case, prototypes don’t participate in any actual production, playing a passive role instead.
  * Since industrial prototypes don’t really copy themselves, a much closer analogy to the pattern is the process of mitotic cell division (biology, remember?).&#x20;
  * After mitotic division, a pair of identical cells is formed. The original cell acts as a prototype and takes an active role in creating the copy.
* Structure&#x20;
  * The **Prototype** interface declares the cloning methods. In most cases, it’s a single `clone` method.
  * The **Concrete Prototype** class implements the cloning method. In addition to copying the original object’s data to the clone, this method may also handle some edge cases of the cloning process related to cloning linked objects, untangling recursive dependencies, etc.
  * The **Client** can produce a copy of any object that follows the prototype interface
  * Prototype registry implementation&#x20;
    *

        <figure><img src="../../../.gitbook/assets/image (39).png" alt=""><figcaption></figcaption></figure>
    * **Prototype Registry** provides an easy way to access frequently-used prototypes. It stores a set of pre-built objects that are ready to be copied.&#x20;
    * The simplest prototype registry is a `name → prototype` hash map. However, if you need better search criteria than a simple name, you can build a much more robust version of the registry
* Aplicability&#x20;
  * Use the Prototype pattern when your code shouldn’t depend on the concrete classes of objects that you need to copy
  * This happens a lot when your code works with objects passed to you from 3rd-party code via some interface. The concrete classes of these objects are unknown, and you couldn’t depend on them even if you wanted to.
  * The Prototype pattern provides the client code with a general interface for working with all objects that support cloning. This interface makes the client code independent from the concrete classes of objects that it clones.
  * Use the pattern when you want to reduce the number of subclasses that only differ in the way they initialize their respective objects
  * Suppose you have a complex class that requires a laborious configuration before it can be used.
  * There are several common ways to configure this class, and this code is scattered through your app.&#x20;
  * To reduce the duplication, you create several subclasses and put every common configuration code into their constructors. You solved the duplication problem, but now you have lots of dummy subclasses.
  * The Prototype pattern lets you use a set of pre-built objects configured in various ways as prototypes. Instead of instantiating a subclass that matches some configuration, the client can simply look for an appropriate prototype and clone it.
* How to implement&#x20;
  * Create the prototype interface and declare the `clone` method in it. Or just add the method to all classes of an existing class hierarchy, if you have one.
  * A prototype class must define the alternative constructor that accepts an object of that class as an argument. The constructor must copy the values of all fields defined in the class from the passed object into the newly created instance. If you’re changing a subclass, you must call the parent constructor to let the superclass handle the cloning of its private fields.
  * If your programming language doesn’t support method overloading, you won’t be able to create a separate “prototype” constructor. Thus, copying the object’s data into the newly created clone will have to be performed within the `clone` method. Still, having this code in a regular constructor is safer because the resulting object is returned fully configured right after you call the `new` operator.
  * The cloning method usually consists of just one line: running a `new` operator with the prototypical version of the constructor. Note, that every class must explicitly override the cloning method and use its own class name along with the `new` operator. Otherwise, the cloning method may produce an object of a parent class.
  * Optionally, create a centralized prototype registry to store a catalog of frequently used prototypes.
  * You can implement the registry as a new factory class or put it in the base prototype class with a static method for fetching the prototype. This method should search for a prototype based on search criteria that the client code passes to the method. The criteria might either be a simple string tag or a complex set of search parameters. After the appropriate prototype is found, the registry should clone it and return the copy to the client.
* Pros&#x20;
  * You can clone objects without coupling to their concrete classes.
  * You can get rid of repeated initialization code in favor of cloning pre-built prototypes.
  * You can produce complex objects more conveniently.
  * You get an alternative to inheritance when dealing with configuration presets for complex objects.
* Cons&#x20;
  * Cloning complex objects that have circular references might be very tricky
* Relations with other patterns&#x20;
  * Many designs start by using [Factory Method](https://refactoring.guru/design-patterns/factory-method) (less complicated and more customizable via subclasses) and evolve toward [Abstract Factory](https://refactoring.guru/design-patterns/abstract-factory), [Prototype](https://refactoring.guru/design-patterns/prototype), or [Builder](https://refactoring.guru/design-patterns/builder) (more flexible, but more complicated).
  * [Abstract Factory](https://refactoring.guru/design-patterns/abstract-factory) classes are often based on a set of [Factory Methods](https://refactoring.guru/design-patterns/factory-method), but you can also use [Prototype](https://refactoring.guru/design-patterns/prototype) to compose the methods on these classes.
  * [Prototype](https://refactoring.guru/design-patterns/prototype) can help when you need to save copies of [Commands](https://refactoring.guru/design-patterns/command) into history.
  * Designs that make heavy use of [Composite](https://refactoring.guru/design-patterns/composite) and [Decorator](https://refactoring.guru/design-patterns/decorator) can often benefit from using [Prototype](https://refactoring.guru/design-patterns/prototype). Applying the pattern lets you clone complex structures instead of re-constructing them from scratch.
  * [Prototype](https://refactoring.guru/design-patterns/prototype) isn’t based on inheritance, so it doesn’t have its drawbacks. On the other hand, _Prototype_ requires a complicated initialization of the cloned object. [Factory Method](https://refactoring.guru/design-patterns/factory-method) is based on inheritance but doesn’t require an initialization step.
  * Sometimes [Prototype](https://refactoring.guru/design-patterns/prototype) can be a simpler alternative to [Memento](https://refactoring.guru/design-patterns/memento). This works if the object, the state of which you want to store in the history, is fairly straightforward and doesn’t have links to external resources, or the links are easy to re-establish.
  * [Abstract Factories](https://refactoring.guru/design-patterns/abstract-factory), [Builders](https://refactoring.guru/design-patterns/builder) and [Prototypes](https://refactoring.guru/design-patterns/prototype) can all be implemented as [Singletons](https://refactoring.guru/design-patterns/singleton).
* Code&#x20;

```python
import copy


class SelfReferencingEntity:
    def __init__(self):
        self.parent = None

    def set_parent(self, parent):
        self.parent = parent


class SomeComponent:
    """
    Python provides its own interface of Prototype via `copy.copy` and
    `copy.deepcopy` functions. And any class that wants to implement custom
    implementations have to override `__copy__` and `__deepcopy__` member
    functions.
    """

    def __init__(self, some_int, some_list_of_objects, some_circular_ref):
        self.some_int = some_int
        self.some_list_of_objects = some_list_of_objects
        self.some_circular_ref = some_circular_ref

    def __copy__(self):
        """
        Create a shallow copy. This method will be called whenever someone calls
        `copy.copy` with this object and the returned value is returned as the
        new shallow copy.
        """

        # First, let's create copies of the nested objects.
        some_list_of_objects = copy.copy(self.some_list_of_objects)
        some_circular_ref = copy.copy(self.some_circular_ref)

        # Then, let's clone the object itself, using the prepared clones of the
        # nested objects.
        new = self.__class__(
            self.some_int, some_list_of_objects, some_circular_ref
        )
        new.__dict__.update(self.__dict__)

        return new

    def __deepcopy__(self, memo=None):
        """
        Create a deep copy. This method will be called whenever someone calls
        `copy.deepcopy` with this object and the returned value is returned as
        the new deep copy.

        What is the use of the argument `memo`? Memo is the dictionary that is
        used by the `deepcopy` library to prevent infinite recursive copies in
        instances of circular references. Pass it to all the `deepcopy` calls
        you make in the `__deepcopy__` implementation to prevent infinite
        recursions.
        """
        if memo is None:
            memo = {}

        # First, let's create copies of the nested objects.
        some_list_of_objects = copy.deepcopy(self.some_list_of_objects, memo)
        some_circular_ref = copy.deepcopy(self.some_circular_ref, memo)

        # Then, let's clone the object itself, using the prepared clones of the
        # nested objects.
        new = self.__class__(
            self.some_int, some_list_of_objects, some_circular_ref
        )
        new.__dict__ = copy.deepcopy(self.__dict__, memo)

        return new


if __name__ == "__main__":

    list_of_objects = [1, {1, 2, 3}, [1, 2, 3]]
    circular_ref = SelfReferencingEntity()
    component = SomeComponent(23, list_of_objects, circular_ref)
    circular_ref.set_parent(component)

    shallow_copied_component = copy.copy(component)

    # Let's change the list in shallow_copied_component and see if it changes in
    # component.
    shallow_copied_component.some_list_of_objects.append("another object")
    if component.some_list_of_objects[-1] == "another object":
        print(
            "Adding elements to `shallow_copied_component`'s "
            "some_list_of_objects adds it to `component`'s "
            "some_list_of_objects."
        )
    else:
        print(
            "Adding elements to `shallow_copied_component`'s "
            "some_list_of_objects doesn't add it to `component`'s "
            "some_list_of_objects."
        )

    # Let's change the set in the list of objects.
    component.some_list_of_objects[1].add(4)
    if 4 in shallow_copied_component.some_list_of_objects[1]:
        print(
            "Changing objects in the `component`'s some_list_of_objects "
            "changes that object in `shallow_copied_component`'s "
            "some_list_of_objects."
        )
    else:
        print(
            "Changing objects in the `component`'s some_list_of_objects "
            "doesn't change that object in `shallow_copied_component`'s "
            "some_list_of_objects."
        )

    deep_copied_component = copy.deepcopy(component)

    # Let's change the list in deep_copied_component and see if it changes in
    # component.
    deep_copied_component.some_list_of_objects.append("one more object")
    if component.some_list_of_objects[-1] == "one more object":
        print(
            "Adding elements to `deep_copied_component`'s "
            "some_list_of_objects adds it to `component`'s "
            "some_list_of_objects."
        )
    else:
        print(
            "Adding elements to `deep_copied_component`'s "
            "some_list_of_objects doesn't add it to `component`'s "
            "some_list_of_objects."
        )

    # Let's change the set in the list of objects.
    component.some_list_of_objects[1].add(10)
    if 10 in deep_copied_component.some_list_of_objects[1]:
        print(
            "Changing objects in the `component`'s some_list_of_objects "
            "changes that object in `deep_copied_component`'s "
            "some_list_of_objects."
        )
    else:
        print(
            "Changing objects in the `component`'s some_list_of_objects "
            "doesn't change that object in `deep_copied_component`'s "
            "some_list_of_objects."
        )

    print(
        f"id(deep_copied_component.some_circular_ref.parent): "
        f"{id(deep_copied_component.some_circular_ref.parent)}"
    )
    print(
        f"id(deep_copied_component.some_circular_ref.parent.some_circular_ref.parent): "
        f"{id(deep_copied_component.some_circular_ref.parent.some_circular_ref.parent)}"
    )
    print(
        "^^ This shows that deepcopied objects contain same reference, they "
        "are not cloned repeatedly."
    )

```

## Structural Patterns

### Adapter

* structural design pattern that allows objects with incompatible interfaces to collablorate
* Problem
  * creating a stock market monitoring app
  * app downloads the stock data from multiple sources in XML format and then displays nice-looking charts and diagrams for the user.
  * At some point, you decide to improve the app by integrating a smart 3rd-party analytics library.&#x20;
  * But there’s a catch: the analytics library only works with data in JSON format.
  * One might think to change the library to work with XML, but this might break some existing code that relies on the library or worse we might not have access to the library source code in the first place making this approach impossible
* Solution
  * We can create an adapter, this spl object converts the interface of one object so that another object can understand it.
  * An adapter wraps one of the objects to hide the complexity of conversion happening behind the scenes.&#x20;
  * The wrapped object isn’t even aware of the adapter. For example, you can wrap an object that operates in meters and kilometers with an adapter that converts all of the data to imperial units such as feet and miles.
  * Adapters can not only convert data into various formats but can also help objects with different interfaces collaborate. Here’s how it works:
    1. The adapter gets an interface, compatible with one of the existing objects.
    2. Using this interface, the existing object can safely call the adapter’s methods.
    3. Upon receiving a call, the adapter passes the request to the second object, but in a format and order that the second object expects.
  * Sometimes it’s even possible to create a two-way adapter that can convert the calls in both directions.
  *

      <figure><img src="../../../.gitbook/assets/image (19).png" alt=""><figcaption></figcaption></figure>
  * To solve the dilemma of incompatible formats, you can create XML-to-JSON adapters for every class of the analytics library that your code works with directly.&#x20;
  * Then you adjust your code to communicate with the library only via these adapters. When an adapter receives a call, it translates the incoming XML data into a JSON structure and passes the call to the appropriate methods of a wrapped analytics object.
* Real world analogy
  * Power adapter plug
* Structure
  * Object adapter&#x20;
    * This implementation uses the object composition principle: the adapter implements the interface of one object and wraps the other one.&#x20;
    * It can be implemented in all popular programming languages.
    *

        <figure><img src="../../../.gitbook/assets/image (20).png" alt=""><figcaption></figcaption></figure>

        1. The **Client** is a class that contains the existing business logic of the program.
        2. The **Client Interface** describes a protocol that other classes must follow to be able to collaborate with the client code.
        3. The **Service** is some useful class (usually 3rd-party or legacy). The client can’t use this class directly because it has an incompatible interface.
        4. The **Adapter** is a class that’s able to work with both the client and the service: it implements the client interface, while wrapping the service object. The adapter receives calls from the client via the client interface and translates them into calls to the wrapped service object in a format it can understand.
        5. The client code doesn’t get coupled to the concrete adapter class as long as it works with the adapter via the client interface. Thanks to this, you can introduce new types of adapters into the program without breaking the existing client code. This can be useful when the interface of the service class gets changed or replaced: you can just create a new adapter class without changing the client code.

        <br>

        <br>
  * Class adapter&#x20;
    * This implementation uses inheritance: the adapter inherits interfaces from both objects at the same time.&#x20;
    * Note that this approach can only be implemented in programming languages that support multiple inheritance, such as C++
    *

        <figure><img src="../../../.gitbook/assets/image (21).png" alt=""><figcaption></figcaption></figure>

        1. The **Class Adapter** doesn’t need to wrap any objects because it inherits behaviors from both the client and the service. The adaptation happens within the overridden methods. The resulting adapter can be used in place of an existing client class.

        <br>
  *   Applicability&#x20;

      * Use the Adapter class when you want to use some existing class, but its interface isn’t compatible with the rest of your code.
      * The Adapter pattern lets you create a middle-layer class that serves as a translator between your code and a legacy class, a 3rd-party class or any other class with a weird interface.
      * &#x20;Use the pattern when you want to reuse several existing subclasses that lack some common functionality that can’t be added to the superclass.
      * &#x20;You could extend each subclass and put the missing functionality into new child classes. However, you’ll need to duplicate the code across all of these new classes, which [smells really bad](https://refactoring.guru/smells/duplicate-code).
      * The much more elegant solution would be to put the missing functionality into an adapter class. Then you would wrap objects with missing features inside the adapter, gaining needed features dynamically. For this to work, the target classes must have a common interface, and the adapter’s field should follow that interface. This approach looks very similar to the [Decorator](https://refactoring.guru/design-patterns/decorator) pattern.


  *   How to implement&#x20;

      * Make sure that you have at least two classes with incompatible interfaces:
        * A useful _service_ class, which you can’t change (often 3rd-party, legacy or with lots of existing dependencies).
        * One or several _client_ classes that would benefit from using the service class.
      * Create the adapter class and make it follow the client interface. Leave all the methods empty for now.
      * Add a field to the adapter class to store a reference to the service object. The common practice is to initialize this field via the constructor, but sometimes it’s more convenient to pass it to the adapter when calling its methods.
      * One by one, implement all methods of the client interface in the adapter class. The adapter should delegate most of the real work to the service object, handling only the interface or data format conversion.
      * Clients should use the adapter via the client interface. This will let you change or extend the adapters without affecting the client code.
      * Declare the client interface and describe how clients communicate with the service.


  *   Pros

      * _Single Responsibility Principle_. You can separate the interface or data conversion code from the primary business logic of the program.
      * _Open/Closed Principle_. You can introduce new types of adapters into the program without breaking the existing client code, as long as they work with the adapters through the client interface.


  *   Cons

      * The overall complexity of the code increases because you need to introduce a set of new interfaces and classes. Sometimes it’s simpler just to change the service class so that it matches the rest of your code.<i class="fa-fw">:fw:</i>


  *   Relations with other patterns&#x20;

      * [Bridge](https://refactoring.guru/design-patterns/bridge) is usually designed up-front, letting you develop parts of an application independently of each other. On the other hand, [Adapter](https://refactoring.guru/design-patterns/adapter) is commonly used with an existing app to make some otherwise-incompatible classes work together nicely.
      * [Adapter](https://refactoring.guru/design-patterns/adapter) provides a completely different interface for accessing an existing object. On the other hand, with the [Decorator](https://refactoring.guru/design-patterns/decorator) pattern the interface either stays the same or gets extended. In addition, _Decorator_ supports recursive composition, which isn’t possible when you use _Adapter_.
      * With [Adapter](https://refactoring.guru/design-patterns/adapter) you access an existing object via different interface. With [Proxy](https://refactoring.guru/design-patterns/proxy), the interface stays the same. With [Decorator](https://refactoring.guru/design-patterns/decorator) you access the object via an enhanced interface.
      * [Facade](https://refactoring.guru/design-patterns/facade) defines a new interface for existing objects, whereas [Adapter](https://refactoring.guru/design-patterns/adapter) tries to make the existing interface usable. _Adapter_ usually wraps just one object, while _Facade_ works with an entire subsystem of objects.
      * [Bridge](https://refactoring.guru/design-patterns/bridge), [State](https://refactoring.guru/design-patterns/state), [Strategy](https://refactoring.guru/design-patterns/strategy) (and to some degree [Adapter](https://refactoring.guru/design-patterns/adapter)) have very similar structures. Indeed, all of these patterns are based on composition, which is delegating work to other objects. However, they all solve different problems. A pattern isn’t just a recipe for structuring your code in a specific way. It can also communicate to other developers the problem the pattern solves.


  * Code&#x20;

```python
class Target:
    """
    The Target defines the domain-specific interface used by the client code.
    """

    def request(self) -> str:
        return "Target: The default target's behavior."


class Adaptee:
    """
    The Adaptee contains some useful behavior, but its interface is incompatible
    with the existing client code. The Adaptee needs some adaptation before the
    client code can use it.
    """

    def specific_request(self) -> str:
        return ".eetpadA eht fo roivaheb laicepS"


class Adapter(Target, Adaptee):
    """
    The Adapter makes the Adaptee's interface compatible with the Target's
    interface via multiple inheritance.
    """

    def request(self) -> str:
        return f"Adapter: (TRANSLATED) {self.specific_request()[::-1]}"


def client_code(target: "Target") -> None:
    """
    The client code supports all classes that follow the Target interface.
    """

    print(target.request(), end="")


if __name__ == "__main__":
    print("Client: I can work just fine with the Target objects:")
    target = Target()
    client_code(target)
    print("\n")

    adaptee = Adaptee()
    print("Client: The Adaptee class has a weird interface. "
          "See, I don't understand it:")
    print(f"Adaptee: {adaptee.specific_request()}", end="\n\n")

    print("Client: But I can work with it via the Adapter:")
    adapter = Adapter()
    client_code(adapter)
```



### Bridge&#x20;

* lets you split a large class or a set of closely related classes into two separate hierarchies—abstraction and implementation—which can be developed independently of each other.
*   Problem

    * Say you have a geometric `Shape` class with a pair of subclasses: `Circle` and `Square`. You want to extend this class hierarchy to incorporate colors, so you plan to create `Red` and `Blue` shape subclasses. However, since you already have two subclasses, you’ll need to create four class combinations such as `BlueCircle` and `RedSquare`
    * Adding new shape types and colors to the hierarchy will grow it exponentially. For example, to add a triangle shape you’d need to introduce two subclasses, one for each color. And after that, adding a new color would require creating three subclasses, one for each shape type. The further we go, the worse it becomes.


* Solution&#x20;
  * occurs because we’re trying to extend the shape classes in two independent dimensions: by form and by color. That’s a very common issue with class inheritance.
  * Bridge pattern attempts to solve this problem by switching from inheritance to the object composition.&#x20;
  * What this means is that you extract one of the dimensions into a separate class hierarchy, so that the original classes will reference an object of the new hierarchy, instead of having all of its state and behaviors within one class.
  * Following this approach, we can extract the color-related code into its own class with two subclasses: `Red` and `Blue`.&#x20;
  *

      <figure><img src="../../../.gitbook/assets/image (22).png" alt=""><figcaption></figcaption></figure>
  * The `Shape` class then gets a reference field pointing to one of the color objects. Now the shape can delegate any color-related work to the linked color object.&#x20;
  * That reference will act as a bridge between the `Shape` and `Color` classes. From now on, adding new colors won’t require changing the shape hierarchy, and vice versa.
  * Abstraction and implementation&#x20;
    * The GoF book <i class="fa-info-circle">:info-circle:</i> introduces the terms _Abstraction_ and _Implementation_ as part of the Bridge definition. In my opinion, the terms sound too academic and make the pattern seem more complicated than it really is. Having read the simple example with shapes and colors, let’s decipher the meaning behind the GoF book’s scary words.
    * _Abstraction_ (also called _interface_) is a high-level control layer for some entity. This layer isn’t supposed to do any real work on its own. It should delegate the work to the _implementation_ layer (also called _platform_).
    * Note that we’re not talking about _interfaces_ or _abstract classes_ from your programming language. These aren’t the same things.
    * When talking about real applications, the abstraction can be represented by a graphical user interface (GUI), and the implementation could be the underlying operating system code (API) which the GUI layer calls in response to user interactions.
    * Generally speaking, you can extend such an app in two independent directions:
      * Have several different GUIs (for instance, tailored for regular customers or admins).
      * Support several different APIs (for example, to be able to launch the app under Windows, Linux, and macOS).
    * In a worst-case scenario, this app might look like a giant spaghetti bowl, where hundreds of conditionals connect different types of GUI with various APIs all over the code.
    * You can bring order to this chaos by extracting the code related to specific interface-platform combinations into separate classes. However, soon you’ll discover that there are _lots_ of these classes.&#x20;
    * The class hierarchy will grow exponentially because adding a new GUI or supporting a different API would require creating more and more classes.
    * Let’s try to solve this issue with the Bridge pattern. It suggests that we divide the classes into two hierarchies:
      * Abstraction: the GUI layer of the app.
      * Implementation: the operating systems’ APIs.
    * The abstraction object controls the appearance of the app, delegating the actual work to the linked implementation object.&#x20;
    * Different implementations are interchangeable as long as they follow a common interface, enabling the same GUI to work under Windows and Linux.
    * As a result, you can change the GUI classes without touching the API-related classes. Moreover, adding support for another operating system only requires creating a subclass in the implementation hierarchy.
* Structure&#x20;
  *

      <figure><img src="../../../.gitbook/assets/image (24).png" alt=""><figcaption></figcaption></figure>

      1. The **Abstraction** provides high-level control logic. It relies on the implementation object to do the actual low-level work.
      2.  The **Implementation** declares the interface that’s common for all concrete implementations. An abstraction can only communicate with an implementation object via methods that are declared here.

          The abstraction may list the same methods as the implementation, but usually the abstraction declares some complex behaviors that rely on a wide variety of primitive operations declared by the implementation.
      3. **Concrete Implementations** contain platform-specific code.
      4. **Refined Abstractions** provide variants of control logic. Like their parent, they work with different implementations via the general implementation interface.
      5. Usually, the **Client** is only interested in working with the abstraction. However, it’s the client’s job to link the abstraction object with one of the implementation objects.<br>
*   Applicability&#x20;

    * Use the Bridge pattern when you want to divide and organize a monolithic class that has several variants of some functionality (for example, if the class can work with various database servers).
    * The bigger a class becomes, the harder it is to figure out how it works, and the longer it takes to make a change. The changes made to one of the variations of functionality may require making changes across the whole class, which often results in making errors or not addressing some critical side effects.
    * The Bridge pattern lets you split the monolithic class into several class hierarchies. After this, you can change the classes in each hierarchy independently of the classes in the others. This approach simplifies code maintenance and minimizes the risk of breaking existing code.
    * &#x20;Use the pattern when you need to extend a class in several orthogonal (independent) dimensions.
    * &#x20;The Bridge suggests that you extract a separate class hierarchy for each of the dimensions. The original class delegates the related work to the objects belonging to those hierarchies instead of doing everything on its own.
    * Use the Bridge if you need to be able to switch implementations at runtime.
    * Although it’s optional, the Bridge pattern lets you replace the implementation object inside the abstraction. It’s as easy as assigning a new value to a field.
    * By the way, this last item is the main reason why so many people confuse the Bridge with the [Strategy](https://refactoring.guru/design-patterns/strategy) pattern. Remember that a pattern is more than just a certain way to structure your classes. It may also communicate intent and a problem being addressed.


*   How to implement&#x20;



    1. Identify the orthogonal dimensions in your classes. These independent concepts could be: abstraction/platform, domain/infrastructure, front-end/back-end, or interface/implementation.
    2. See what operations the client needs and define them in the base abstraction class.
    3. Determine the operations available on all platforms. Declare the ones that the abstraction needs in the general implementation interface.
    4. For all platforms in your domain create concrete implementation classes, but make sure they all follow the implementation interface.
    5. Inside the abstraction class, add a reference field for the implementation type. The abstraction delegates most of the work to the implementation object that’s referenced in that field.
    6. If you have several variants of high-level logic, create refined abstractions for each variant by extending the base abstraction class.
    7. The client code should pass an implementation object to the abstraction’s constructor to associate one with the other. After that, the client can forget about the implementation and work only with the abstraction object.<br>
* Pros&#x20;
  * You can create platform-independent classes and apps.
  * The client code works with high-level abstractions. It isn’t exposed to the platform details.
  * &#x20;_Open/Closed Principle_. You can introduce new abstractions and implementations independently from each other.
  * &#x20;_Single Responsibility Principle_. You can focus on high-level logic in the abstraction and on platform details in the implementation.<br>
* Cons&#x20;
  * You might make the code more complicated by applying the pattern to a highly cohesive class.
*   Relation with other patterns&#x20;

    * [Bridge](https://refactoring.guru/design-patterns/bridge) is usually designed up-front, letting you develop parts of an application independently of each other. On the other hand, [Adapter](https://refactoring.guru/design-patterns/adapter) is commonly used with an existing app to make some otherwise-incompatible classes work together nicely.
    * [Bridge](https://refactoring.guru/design-patterns/bridge), [State](https://refactoring.guru/design-patterns/state), [Strategy](https://refactoring.guru/design-patterns/strategy) (and to some degree [Adapter](https://refactoring.guru/design-patterns/adapter)) have very similar structures. Indeed, all of these patterns are based on composition, which is delegating work to other objects. However, they all solve different problems. A pattern isn’t just a recipe for structuring your code in a specific way. It can also communicate to other developers the problem the pattern solves.
    * You can use [Abstract Factory](https://refactoring.guru/design-patterns/abstract-factory) along with [Bridge](https://refactoring.guru/design-patterns/bridge). This pairing is useful when some abstractions defined by _Bridge_ can only work with specific implementations. In this case, _Abstract Factory_ can encapsulate these relations and hide the complexity from the client code.
    * You can combine [Builder](https://refactoring.guru/design-patterns/builder) with [Bridge](https://refactoring.guru/design-patterns/bridge): the director class plays the role of the abstraction, while different builders act as implementations.


* Code

```python

from __future__ import annotations
from abc import ABC, abstractmethod


class Abstraction:
    """
    The Abstraction defines the interface for the "control" part of the two
    class hierarchies. It maintains a reference to an object of the
    Implementation hierarchy and delegates all of the real work to this object.
    """

    def __init__(self, implementation: Implementation) -> None:
        self.implementation = implementation

    def operation(self) -> str:
        return (f"Abstraction: Base operation with:\n"
                f"{self.implementation.operation_implementation()}")


class ExtendedAbstraction(Abstraction):
    """
    You can extend the Abstraction without changing the Implementation classes.
    """

    def operation(self) -> str:
        return (f"ExtendedAbstraction: Extended operation with:\n"
                f"{self.implementation.operation_implementation()}")


class Implementation(ABC):
    """
    The Implementation defines the interface for all implementation classes. It
    doesn't have to match the Abstraction's interface. In fact, the two
    interfaces can be entirely different. Typically the Implementation interface
    provides only primitive operations, while the Abstraction defines higher-
    level operations based on those primitives.
    """

    @abstractmethod
    def operation_implementation(self) -> str:
        pass


"""
Each Concrete Implementation corresponds to a specific platform and implements
the Implementation interface using that platform's API.
"""


class ConcreteImplementationA(Implementation):
    def operation_implementation(self) -> str:
        return "ConcreteImplementationA: Here's the result on the platform A."


class ConcreteImplementationB(Implementation):
    def operation_implementation(self) -> str:
        return "ConcreteImplementationB: Here's the result on the platform B."


def client_code(abstraction: Abstraction) -> None:
    """
    Except for the initialization phase, where an Abstraction object gets linked
    with a specific Implementation object, the client code should only depend on
    the Abstraction class. This way the client code can support any abstraction-
    implementation combination.
    """

    # ...

    print(abstraction.operation(), end="")

    # ...


if __name__ == "__main__":
    """
    The client code should be able to work with any pre-configured abstraction-
    implementation combination.
    """

    implementation = ConcreteImplementationA()
    abstraction = Abstraction(implementation)
    client_code(abstraction)

    print("\n")

    implementation = ConcreteImplementationB()
    abstraction = ExtendedAbstraction(implementation)
    client_code(abstraction)

```



### Composite

* lets you compose objects into tree structures and then work with these structures as if they were individual objects.
* Problem
  * imagine that you have two types of objects: `Products` and `Boxes`. A `Box` can contain several `Products` as well as a number of smaller `Boxes`. These little `Boxes` can also hold some `Products` or even smaller `Boxes`, and so on.
  * Say you decide to create an ordering system that uses these classes. Orders could contain simple products without any wrapping, as well as boxes stuffed with products...and other boxes. How would you determine the total price of such an order?
  * You could try the direct approach: unwrap all the boxes, go over all the products and then calculate the total.&#x20;
  *

      <figure><img src="../../../.gitbook/assets/image (25).png" alt=""><figcaption></figcaption></figure>
  * That would be doable in the real world; but in a program, it’s not as simple as running a loop.&#x20;
  * You have to know the classes of `Products` and `Boxes` you’re going through, the nesting level of the boxes and other nasty details beforehand. All of this makes the direct approach either too awkward or even impossible.
* Solution
  * The Composite pattern suggests that you work with `Products` and `Boxes` through a common interface which declares a method for calculating the total price.
  * How would this method work? For a product, it’d simply return the product’s price.&#x20;
  * For a box, it’d go over each item the box contains, ask its price and then return a total for this box.&#x20;
  * If one of these items were a smaller box, that box would also start going over its contents and so on, until the prices of all inner components were calculated.&#x20;
  * A box could even add some extra cost to the final price, such as packaging cost.
  * The greatest benefit of this approach is that you don’t need to care about the concrete classes of objects that compose the tree.&#x20;
  * You don’t need to know whether an object is a simple product or a sophisticated box. You can treat them all the same via the common interface.&#x20;
  * When you call a method, the objects themselves pass the request down the tree.
* Real world analogy
  * Armies of most countries are structured as hierarchies. An army consists of several divisions; a division is a set of brigades, and a brigade consists of platoons, which can be broken down into squads.&#x20;
  * Finally, a squad is a small group of real soldiers. Orders are given at the top of the hierarchy and passed down onto each level until every soldier knows what needs to be done.
*   Structure&#x20;


*

    <figure><img src="../../../.gitbook/assets/image (26).png" alt=""><figcaption></figcaption></figure>

    1. The **Component** interface describes operations that are common to both simple and complex elements of the tree.
    2.  The **Leaf** is a basic element of a tree that doesn’t have sub-elements.

        Usually, leaf components end up doing most of the real work, since they don’t have anyone to delegate the work to.
    3.  The **Container** (aka _composite_) is an element that has sub-elements: leaves or other containers. A container doesn’t know the concrete classes of its children. It works with all sub-elements only via the component interface.

        Upon receiving a request, a container delegates the work to its sub-elements, processes intermediate results and then returns the final result to the client.
    4. The **Client** works with all elements through the component interface. As a result, the client can work in the same way with both simple or complex elements of the tree.


* Applicability&#x20;
  * Use the Composite pattern when you have to implement a tree-like object structure.
  * The Composite pattern provides you with two basic element types that share a common interface: simple leaves and complex containers. A container can be composed of both leaves and other containers. This lets you construct a nested recursive object structure that resembles a tree.
  * &#x20;Use the pattern when you want the client code to treat both simple and complex elements uniformly.
  * All elements defined by the Composite pattern share a common interface. Using this interface, the client doesn’t have to worry about the concrete class of the objects it works with.
* How to implement&#x20;
  1. Make sure that the core model of your app can be represented as a tree structure. Try to break it down into simple elements and containers. Remember that containers must be able to contain both simple elements and other containers.
  2. Declare the component interface with a list of methods that make sense for both simple and complex components.
  3. Create a leaf class to represent simple elements. A program may have multiple different leaf classes.
  4.  Create a container class to represent complex elements. In this class, provide an array field for storing references to sub-elements. The array must be able to store both leaves and containers, so make sure it’s declared with the component interface type.

      While implementing the methods of the component interface, remember that a container is supposed to be delegating most of the work to sub-elements.
  5.  Finally, define the methods for adding and removal of child elements in the container.

      Keep in mind that these operations can be declared in the component interface. This would violate the _Interface Segregation Principle_ because the methods will be empty in the leaf class. However, the client will be able to treat all the elements equally, even when composing the tree.<br>
* Pros
  * You can work with complex tree structures more conveniently: use polymorphism and recursion to your advantage.
  * _Open/Closed Principle_. You can introduce new element types into the app without breaking the existing code, which now works with the object tree.
* Cons
  * It might be difficult to provide a common interface for classes whose functionality differs too much.&#x20;
  * In certain scenarios, you’d need to over generalize the component interface, making it harder to comprehend.
* Relations with other patterns
  * You can use [Builder](https://refactoring.guru/design-patterns/builder) when creating complex [Composite](https://refactoring.guru/design-patterns/composite) trees because you can program its construction steps to work recursively.
  * [Chain of Responsibility](https://refactoring.guru/design-patterns/chain-of-responsibility) is often used in conjunction with [Composite](https://refactoring.guru/design-patterns/composite). In this case, when a leaf component gets a request, it may pass it through the chain of all of the parent components down to the root of the object tree.
  * You can use [Iterators](https://refactoring.guru/design-patterns/iterator) to traverse [Composite](https://refactoring.guru/design-patterns/composite) trees.
  * You can use [Visitor](https://refactoring.guru/design-patterns/visitor) to execute an operation over an entire [Composite](https://refactoring.guru/design-patterns/composite) tree.
  * You can implement shared leaf nodes of the [Composite](https://refactoring.guru/design-patterns/composite) tree as [Flyweights](https://refactoring.guru/design-patterns/flyweight) to save some RAM.
  *   [Composite](https://refactoring.guru/design-patterns/composite) and [Decorator](https://refactoring.guru/design-patterns/decorator) have similar structure diagrams since both rely on recursive composition to organize an open-ended number of objects.

      A _Decorator_ is like a _Composite_ but only has one child component. There’s another significant difference: _Decorator_ adds additional responsibilities to the wrapped object, while _Composite_ just “sums up” its children’s results.

      However, the patterns can also cooperate: you can use _Decorator_ to extend the behavior of a specific object in the _Composite_ tree.
  * Designs that make heavy use of [Composite](https://refactoring.guru/design-patterns/composite) and [Decorator](https://refactoring.guru/design-patterns/decorator) can often benefit from using [Prototype](https://refactoring.guru/design-patterns/prototype). Applying the pattern lets you clone complex structures instead of re-constructing them from scratch.
* Code&#x20;

```python
from __future__ import annotations
from abc import ABC, abstractmethod
from typing import List


class Component(ABC):
    """
    The base Component class declares common operations for both simple and
    complex objects of a composition.
    """

    @property
    def parent(self) -> Component:
        return self._parent

    @parent.setter
    def parent(self, parent: Component):
        """
        Optionally, the base Component can declare an interface for setting and
        accessing a parent of the component in a tree structure. It can also
        provide some default implementation for these methods.
        """

        self._parent = parent

    """
    In some cases, it would be beneficial to define the child-management
    operations right in the base Component class. This way, you won't need to
    expose any concrete component classes to the client code, even during the
    object tree assembly. The downside is that these methods will be empty for
    the leaf-level components.
    """

    def add(self, component: Component) -> None:
        pass

    def remove(self, component: Component) -> None:
        pass

    def is_composite(self) -> bool:
        """
        You can provide a method that lets the client code figure out whether a
        component can bear children.
        """

        return False

    @abstractmethod
    def operation(self) -> str:
        """
        The base Component may implement some default behavior or leave it to
        concrete classes (by declaring the method containing the behavior as
        "abstract").
        """

        pass


class Leaf(Component):
    """
    The Leaf class represents the end objects of a composition. A leaf can't
    have any children.

    Usually, it's the Leaf objects that do the actual work, whereas Composite
    objects only delegate to their sub-components.
    """

    def operation(self) -> str:
        return "Leaf"


class Composite(Component):
    """
    The Composite class represents the complex components that may have
    children. Usually, the Composite objects delegate the actual work to their
    children and then "sum-up" the result.
    """

    def __init__(self) -> None:
        self._children: List[Component] = []

    """
    A composite object can add or remove other components (both simple or
    complex) to or from its child list.
    """

    def add(self, component: Component) -> None:
        self._children.append(component)
        component.parent = self

    def remove(self, component: Component) -> None:
        self._children.remove(component)
        component.parent = None

    def is_composite(self) -> bool:
        return True

    def operation(self) -> str:
        """
        The Composite executes its primary logic in a particular way. It
        traverses recursively through all its children, collecting and summing
        their results. Since the composite's children pass these calls to their
        children and so forth, the whole object tree is traversed as a result.
        """

        results = []
        for child in self._children:
            results.append(child.operation())
        return f"Branch({'+'.join(results)})"


def client_code(component: Component) -> None:
    """
    The client code works with all of the components via the base interface.
    """

    print(f"RESULT: {component.operation()}", end="")


def client_code2(component1: Component, component2: Component) -> None:
    """
    Thanks to the fact that the child-management operations are declared in the
    base Component class, the client code can work with any component, simple or
    complex, without depending on their concrete classes.
    """

    if component1.is_composite():
        component1.add(component2)

    print(f"RESULT: {component1.operation()}", end="")


if __name__ == "__main__":
    # This way the client code can support the simple leaf components...
    simple = Leaf()
    print("Client: I've got a simple component:")
    client_code(simple)
    print("\n")

    # ...as well as the complex composites.
    tree = Composite()

    branch1 = Composite()
    branch1.add(Leaf())
    branch1.add(Leaf())

    branch2 = Composite()
    branch2.add(Leaf())

    tree.add(branch1)
    tree.add(branch2)

    print("Client: Now I've got a composite tree:")
    client_code(tree)
    print("\n")

    print("Client: I don't need to check the components classes even when managing the tree:")
    client_code2(tree, simple)
```



### Decorator&#x20;

* lets you attach new behaviors to objects by placing these objects inside special wrapper objects that contain the behaviors.
* Problem&#x20;
  * Imagine that you’re working on a notification library which lets other programs notify their users about important events.
  * The initial version of the library was based on the `Notifier` class that had only a few fields, a constructor and a single `send` method.&#x20;
  * The method could accept a message argument from a client and send the message to a list of emails that were passed to the notifier via its constructor.&#x20;
  * A third-party app which acted as a client was supposed to create and configure the notifier object once, and then use it each time something important happened.
  * At some point, you realize that users of the library expect more than just email notifications.
  * Many of them would like to receive an SMS about critical issues. Others would like to be notified on Facebook and, of course, the corporate users would love to get Slack notifications.
  *

      <figure><img src="../../../.gitbook/assets/image (27).png" alt=""><figcaption></figcaption></figure>
  * How hard can that be? You extended the `Notifier` class and put the additional notification methods into new subclasses. Now the client was supposed to instantiate the desired notification class and use it for all further notifications.
  * But then someone reasonably asked you, “Why can’t you use several notification types at once? If your house is on fire, you’d probably want to be informed through every channel.”
  * You tried to address that problem by creating special subclasses which combined several notification methods within one class. However, it quickly became apparent that this approach would bloat the code immensely, not only the library code but the client code as well.
  *

      <figure><img src="../../../.gitbook/assets/image (28).png" alt=""><figcaption></figcaption></figure>


  * You have to find some other way to structure notifications classes so that their number won’t accidentally break some Guinness record.
* Solution
  * Extending a class is the first thing that comes to mind when you need to alter an object’s behavior. However, inheritance has several serious caveats that you need to be aware of.
    * Inheritance is static. You can’t alter the behavior of an existing object at runtime. You can only replace the whole object with another one that’s created from a different subclass.
    * Subclasses can have just one parent class. In most languages, inheritance doesn’t let a class inherit behaviors of multiple classes at the same time.
  * With this new approach you can easily substitute the linked “helper” object with another, changing the behavior of the container at runtime. An object can use the behavior of various classes, having references to multiple objects and delegating them all kinds of work. Aggregation/composition is the key principle behind many design patterns, including Decorator. On that note, let’s return to the pattern discussion.
  * One of the ways to overcome these caveats is by using _Aggregation_ or _Composition_ <i class="fa-info-circle">:info-circle:</i> instead of _Inheritance_. Both of the alternatives work almost the same way: one object _has a_ reference to another and delegates it some work, whereas with inheritance, the object itself _is_ able to do that work, inheriting the behavior from its superclass.
  * Wrapper” is the alternative nickname for the Decorator pattern that clearly expresses the main idea of the pattern. A _wrapper_ is an object that can be linked with some _target_ object. The wrapper contains the same set of methods as the target and delegates to it all requests it receives. However, the wrapper may alter the result by doing something either before or after it passes the request to the target.
  * When does a simple wrapper become the real decorator? As I mentioned, the wrapper implements the same interface as the wrapped object. That’s why from the client’s perspective these objects are identical. Make the wrapper’s reference field accept any object that follows that interface. This will let you cover an object in multiple wrappers, adding the combined behavior of all the wrappers to it.
  *

      <figure><img src="../../../.gitbook/assets/image (29).png" alt=""><figcaption></figcaption></figure>
  * The client code would need to wrap a basic notifier object into a set of decorators that match the client’s preferences. The resulting objects will be structured as a stack.
  *

      <figure><img src="../../../.gitbook/assets/image (30).png" alt=""><figcaption></figcaption></figure>
  * The last decorator in the stack would be the object that the client actually works with. Since all decorators implement the same interface as the base notifier, the rest of the client code won’t care whether it works with the “pure” notifier object or the decorated one.
* Real world analogy
  * Wearing clothes is an example of using decorators. When you’re cold, you wrap yourself in a sweater.&#x20;
  * If you’re still cold with a sweater, you can wear a jacket on top. If it’s raining, you can put on a raincoat.&#x20;
  * All of these garments “extend” your basic behavior but aren’t part of you, and you can easily take off any piece of clothing whenever you don’t need it.
* Structure&#x20;
  *

      <figure><img src="../../../.gitbook/assets/image (31).png" alt=""><figcaption></figcaption></figure>

      1. The **Component** declares the common interface for both wrappers and wrapped objects.
      2. **Concrete Component** is a class of objects being wrapped. It defines the basic behavior, which can be altered by decorators.
      3. The **Base Decorator** class has a field for referencing a wrapped object. The field’s type should be declared as the component interface so it can contain both concrete components and decorators. The base decorator delegates all operations to the wrapped object.
      4. **Concrete Decorators** define extra behaviors that can be added to components dynamically. Concrete decorators override methods of the base decorator and execute their behavior either before or after calling the parent method.
      5. The **Client** can wrap components in multiple layers of decorators, as long as it works with all objects via the component interface.

      <br>
* Applicability
  * Use the Decorator pattern when you need to be able to assign extra behaviors to objects at runtime without breaking the code that uses these objects.
  * The Decorator lets you structure your business logic into layers, create a decorator for each layer and compose objects with various combinations of this logic at runtime. The client code can treat all these objects in the same way, since they all follow a common interface.
  * &#x20;Use the pattern when it’s awkward or not possible to extend an object’s behavior using inheritance.
  * &#x20;Many programming languages have the `final` keyword that can be used to prevent further extension of a class. For a final class, the only way to reuse the existing behavior would be to wrap the class with your own wrapper, using the Decorator pattern.
* How to implement&#x20;
  1. Make sure your business domain can be represented as a primary component with multiple optional layers over it.
  2. Figure out what methods are common to both the primary component and the optional layers. Create a component interface and declare those methods there.
  3. Create a concrete component class and define the base behavior in it.
  4. Create a base decorator class. It should have a field for storing a reference to a wrapped object. The field should be declared with the component interface type to allow linking to concrete components as well as decorators. The base decorator must delegate all work to the wrapped object.
  5. Make sure all classes implement the component interface.
  6. Create concrete decorators by extending them from the base decorator. A concrete decorator must execute its behavior before or after the call to the parent method (which always delegates to the wrapped object).
  7. The client code must be responsible for creating decorators and composing them in the way the client needs.<br>
*   Pros&#x20;



    * You can extend an object’s behavior without making a new subclass.
    * You can add or remove responsibilities from an object at runtime.
    * &#x20;You can combine several behaviors by wrapping an object into multiple decorators.
    * &#x20;_Single Responsibility Principle_. You can divide a monolithic class that implements many possible variants of behavior into several smaller classes.<br>
* Cons&#x20;
  * It’s hard to remove a specific wrapper from the wrappers stack.
  * <i class="fa-fw">:fw:</i> It’s hard to implement a decorator in such a way that its behavior doesn’t depend on the order in the decorators stack.
  * <i class="fa-fw">:fw:</i> The initial configuration code of layers might look pretty ugly.<br>
* Relations with other patterns&#x20;
  * [Adapter](https://refactoring.guru/design-patterns/adapter) provides a completely different interface for accessing an existing object. On the other hand, with the [Decorator](https://refactoring.guru/design-patterns/decorator) pattern the interface either stays the same or gets extended. In addition, _Decorator_ supports recursive composition, which isn’t possible when you use _Adapter_.
  * With [Adapter](https://refactoring.guru/design-patterns/adapter) you access an existing object via different interface. With [Proxy](https://refactoring.guru/design-patterns/proxy), the interface stays the same. With [Decorator](https://refactoring.guru/design-patterns/decorator) you access the object via an enhanced interface.
  *   [Chain of Responsibility](https://refactoring.guru/design-patterns/chain-of-responsibility) and [Decorator](https://refactoring.guru/design-patterns/decorator) have very similar class structures. Both patterns rely on recursive composition to pass the execution through a series of objects. However, there are several crucial differences.

      The _CoR_ handlers can execute arbitrary operations independently of each other. They can also stop passing the request further at any point. On the other hand, various _Decorators_ can extend the object’s behavior while keeping it consistent with the base interface. In addition, decorators aren’t allowed to break the flow of the request.
  *   [Composite](https://refactoring.guru/design-patterns/composite) and [Decorator](https://refactoring.guru/design-patterns/decorator) have similar structure diagrams since both rely on recursive composition to organize an open-ended number of objects.

      A _Decorator_ is like a _Composite_ but only has one child component. There’s another significant difference: _Decorator_ adds additional responsibilities to the wrapped object, while _Composite_ just “sums up” its children’s results.

      However, the patterns can also cooperate: you can use _Decorator_ to extend the behavior of a specific object in the _Composite_ tree.
  * Designs that make heavy use of [Composite](https://refactoring.guru/design-patterns/composite) and [Decorator](https://refactoring.guru/design-patterns/decorator) can often benefit from using [Prototype](https://refactoring.guru/design-patterns/prototype). Applying the pattern lets you clone complex structures instead of re-constructing them from scratch.
  * [Decorator](https://refactoring.guru/design-patterns/decorator) lets you change the skin of an object, while [Strategy](https://refactoring.guru/design-patterns/strategy) lets you change the guts.
  * [Decorator](https://refactoring.guru/design-patterns/decorator) and [Proxy](https://refactoring.guru/design-patterns/proxy) have similar structures, but very different intents. Both patterns are built on the composition principle, where one object is supposed to delegate some of the work to another. The difference is that a _Proxy_ usually manages the life cycle of its service object on its own, whereas the composition of _Decorators_ is always controlled by the client.
* Code&#x20;



```python

class Component():
    """
    The base Component interface defines operations that can be altered by
    decorators.
    """

    def operation(self) -> str:
        pass


class ConcreteComponent(Component):
    """
    Concrete Components provide default implementations of the operations. There
    might be several variations of these classes.
    """

    def operation(self) -> str:
        return "ConcreteComponent"


class Decorator(Component):
    """
    The base Decorator class follows the same interface as the other components.
    The primary purpose of this class is to define the wrapping interface for
    all concrete decorators. The default implementation of the wrapping code
    might include a field for storing a wrapped component and the means to
    initialize it.
    """

    _component: Component = None

    def __init__(self, component: Component) -> None:
        self._component = component

    @property
    def component(self) -> Component:
        """
        The Decorator delegates all work to the wrapped component.
        """

        return self._component

    def operation(self) -> str:
        return self._component.operation()


class ConcreteDecoratorA(Decorator):
    """
    Concrete Decorators call the wrapped object and alter its result in some
    way.
    """

    def operation(self) -> str:
        """
        Decorators may call parent implementation of the operation, instead of
        calling the wrapped object directly. This approach simplifies extension
        of decorator classes.
        """
        return f"ConcreteDecoratorA({self.component.operation()})"


class ConcreteDecoratorB(Decorator):
    """
    Decorators can execute their behavior either before or after the call to a
    wrapped object.
    """

    def operation(self) -> str:
        return f"ConcreteDecoratorB({self.component.operation()})"


def client_code(component: Component) -> None:
    """
    The client code works with all objects using the Component interface. This
    way it can stay independent of the concrete classes of components it works
    with.
    """

    # ...

    print(f"RESULT: {component.operation()}", end="")

    # ...


if __name__ == "__main__":
    # This way the client code can support both simple components...
    simple = ConcreteComponent()
    print("Client: I've got a simple component:")
    client_code(simple)
    print("\n")

    # ...as well as decorated ones.
    #
    # Note how decorators can wrap not only simple components but the other
    # decorators as well.
    decorator1 = ConcreteDecoratorA(simple)
    decorator2 = ConcreteDecoratorB(decorator1)
    print("Client: Now I've got a decorated component:")
    client_code(decorator2)

```



### Flywheel/ Flyweight&#x20;

* Structural pattern that lets us fit more objects into the available amount of RAM by sharing common parts of the state between multiple objects instead of keeping all of the data in each object.
* Problem
  * lets suppose we built a game which is working fine in our system but due to ram constraints it is not working properly in other systems
  * The problem that we found out that each particle in the game was represented by a separate object containing plenty of data.
  * At some point the carnage on a player screen reached its climax, newly created particles no longer fit into the ram crashing the program.
* Solution&#x20;
  * On closer inspection we found out that the color and sprite fields consume a lot more memory than other fields, the worse part is that these two fields store almost identical data across all particles.
  * other parts of a particle state such as coordinates, movement vector and speed are unique to each particle.
  * After all the values of these fields change over time, the data represents the always changing context in which the particle exists, while the color and sprite remain constant for each particle&#x20;
  * The Flyweight patterns suggests that we stop storing the extrensic state inside the object, instead one could pass this state to specific methods which rely on it
  * Only the intrinsic state stays within the object, letting us reuse it in different contexts. As a result we would need fewer of thest objects since they only differ in the intrinsic state which has much fewer variations than the extrinsic.
  *

      <figure><img src="../../../.gitbook/assets/image (146).png" alt=""><figcaption></figcaption></figure>
  * Extrinsic state storage
    * Where does the extrinsic state move to, some class should still store it right, in most cases it gets moved to the container object, which aggregates objects before we apply the pattern.
    * in our case that the main game object that stores all particles in the particles field, to move the extrisnic state into this class, we need to create several array fields for storing references to a specific flyweight that represents a particle
    * These arrays must be in sync so that one can access all data of a particle using the same index.
    * A more elegant solution is to create a seperate context class that would store the extrinsic state along with reference to the flyweight object.
    * This approach would require having just a single array in the container class
    * Wait a second! Won’t we need to have as many of these contextual objects as we had at the very beginning? Technically, yes. But the thing is, these objects are much smaller than before.&#x20;
    * The most memory-consuming fields have been moved to just a few flyweight objects. Now, a thousand small contextual objects can reuse a single heavy flyweight object instead of storing a thousand copies of its data.
  * Flyweight and immutability&#x20;
    * since the same flywegiht object can be used in different contexts one have to make sure that its state can't be modified.
    * A flywegiht shoudl initialize its state just one via a constructor parameter, it shouldn't expose any setters or publuc fields to other object
  * Flyweight factory&#x20;
    * For more convenient access to various flyweights, you can create a factory method that manages a pool of existing flyweight objects.&#x20;
    * The method accepts the intrinsic state of the desired flyweight from a client, looks for an existing flyweight object matching this state, and returns it if it was found. If not, it creates a new flyweight and adds it to the pool.
    *   There are several options where this method could be placed. The most obvious place is a flyweight container. Alternatively, you could create a new factory class. Or you could make the factory method static and put it inside an actual flyweight class.


* Structure&#x20;
*

    <figure><img src="../../../.gitbook/assets/image (147).png" alt=""><figcaption></figcaption></figure>

    * The Flyweight pattern is merely an optimization. Before applying it, make sure your program does have the RAM consumption problem related to having a massive number of similar objects in memory at the same time. Make sure that this problem can’t be solved in any other meaningful way.
    * The **Flyweight** class contains the portion of the original object’s state that can be shared between multiple objects. The same flyweight object can be used in many different contexts. The state stored inside a flyweight is called _intrinsic._ The state passed to the flyweight’s methods is called _extrinsic._
    * The **Context** class contains the extrinsic state, unique across all original objects. When a context is paired with one of the flyweight objects, it represents the full state of the original object.
    * Usually, the behavior of the original object remains in the flyweight class. In this case, whoever calls a flyweight’s method must also pass appropriate bits of the extrinsic state into the method’s parameters. On the other hand, the behavior can be moved to the context class, which would use the linked flyweight merely as a data object.
    * The **Client** calculates or stores the extrinsic state of flyweights. From the client’s perspective, a flyweight is a template object which can be configured at runtime by passing some contextual data into parameters of its methods.
    * The **Flyweight Factory** manages a pool of existing flyweights. With the factory, clients don’t create flyweights directly. Instead, they call the factory, passing it bits of the intrinsic state of the desired flyweight. The factory looks over previously created flyweights and either returns an existing one that matches search criteria or creates a new one if nothing is found.
    *


* Applicability&#x20;
  * Use the Flyweight pattern only when your program must support a huge number of objects which barely fit into available RAM.
  *   The benefit of applying the pattern depends heavily on how and where it’s used. It’s most useful when:

      * an application needs to spawn a huge number of similar objects
      * this drains all available RAM on a target device
      * the objects contain duplicate states which can be extracted and shared between multiple objects

      <br>
*   How to implement&#x20;



    1. Divide fields of a class that will become a flyweight into two parts:
       * the intrinsic state: the fields that contain unchanging data duplicated across many objects
       * the extrinsic state: the fields that contain contextual data unique to each object
    2. Leave the fields that represent the intrinsic state in the class, but make sure they’re immutable. They should take their initial values only inside the constructor.
    3. Go over methods that use fields of the extrinsic state. For each field used in the method, introduce a new parameter and use it instead of the field.
    4. Optionally, create a factory class to manage the pool of flyweights. It should check for an existing flyweight before creating a new one. Once the factory is in place, clients must only request flyweights through it. They should describe the desired flyweight by passing its intrinsic state to the factory.
    5. The client must store or calculate values of the extrinsic state (context) to be able to call methods of flyweight objects. For the sake of convenience, the extrinsic state along with the flyweight-referencing field may be moved to a separate context class.
* Pros
  * You can save lots of RAM, assuming your program has tons of similar objects.
*   Cons&#x20;

    * You might be trading RAM over CPU cycles when some of the context data needs to be recalculated each time somebody calls a flyweight method.
    * The code becomes much more complicated. New team members will always be wondering why the state of an entity was separated in such a way.

    <i class="fa-fw">:fw:</i>

    <br>
* Relations with other patterns&#x20;
  * You can implement shared leaf nodes of the [Composite](https://refactoring.guru/design-patterns/composite) tree as [Flyweights](https://refactoring.guru/design-patterns/flyweight) to save some RAM.
  * [Flyweight](https://refactoring.guru/design-patterns/flyweight) shows how to make lots of little objects, whereas [Facade](https://refactoring.guru/design-patterns/facade) shows how to make a single object that represents an entire subsystem.
  * [Flyweight](https://refactoring.guru/design-patterns/flyweight) would resemble [Singleton](https://refactoring.guru/design-patterns/singleton) if you somehow managed to reduce all shared states of the objects to just one flyweight object. But there are two fundamental differences between these patterns:
    1. There should be only one Singleton instance, whereas a _Flyweight_ class can have multiple instances with different intrinsic states.
    2. The _Singleton_ object can be mutable. Flyweight objects are immutable.
* Code

```python
import json
from typing import Dict


class Flyweight():
    """
    The Flyweight stores a common portion of the state (also called intrinsic
    state) that belongs to multiple real business entities. The Flyweight
    accepts the rest of the state (extrinsic state, unique for each entity) via
    its method parameters.
    """

    def __init__(self, shared_state: str) -> None:
        self._shared_state = shared_state

    def operation(self, unique_state: str) -> None:
        s = json.dumps(self._shared_state)
        u = json.dumps(unique_state)
        print(f"Flyweight: Displaying shared ({s}) and unique ({u}) state.", end="")


class FlyweightFactory():
    """
    The Flyweight Factory creates and manages the Flyweight objects. It ensures
    that flyweights are shared correctly. When the client requests a flyweight,
    the factory either returns an existing instance or creates a new one, if it
    doesn't exist yet.
    """

    _flyweights: Dict[str, Flyweight] = {}

    def __init__(self, initial_flyweights: Dict) -> None:
        for state in initial_flyweights:
            self._flyweights[self.get_key(state)] = Flyweight(state)

    def get_key(self, state: Dict) -> str:
        """
        Returns a Flyweight's string hash for a given state.
        """

        return "_".join(sorted(state))

    def get_flyweight(self, shared_state: Dict) -> Flyweight:
        """
        Returns an existing Flyweight with a given state or creates a new one.
        """

        key = self.get_key(shared_state)

        if not self._flyweights.get(key):
            print("FlyweightFactory: Can't find a flyweight, creating new one.")
            self._flyweights[key] = Flyweight(shared_state)
        else:
            print("FlyweightFactory: Reusing existing flyweight.")

        return self._flyweights[key]

    def list_flyweights(self) -> None:
        count = len(self._flyweights)
        print(f"FlyweightFactory: I have {count} flyweights:")
        print("\n".join(map(str, self._flyweights.keys())), end="")


def add_car_to_police_database(
    factory: FlyweightFactory, plates: str, owner: str,
    brand: str, model: str, color: str
) -> None:
    print("\n\nClient: Adding a car to database.")
    flyweight = factory.get_flyweight([brand, model, color])
    # The client code either stores or calculates extrinsic state and passes it
    # to the flyweight's methods.
    flyweight.operation([plates, owner])


if __name__ == "__main__":
    """
    The client code usually creates a bunch of pre-populated flyweights in the
    initialization stage of the application.
    """

    factory = FlyweightFactory([
        ["Chevrolet", "Camaro2018", "pink"],
        ["Mercedes Benz", "C300", "black"],
        ["Mercedes Benz", "C500", "red"],
        ["BMW", "M5", "red"],
        ["BMW", "X6", "white"],
    ])

    factory.list_flyweights()

    add_car_to_police_database(
        factory, "CL234IR", "James Doe", "BMW", "M5", "red")

    add_car_to_police_database(
        factory, "CL234IR", "James Doe", "BMW", "X1", "red")

    print("\n")

    factory.list_flyweights()

```



### Facade

* structural design pattern that provides a simplified interface to a library, a framework, or any other complex set of classes.
* Problem&#x20;
  * Imagine that you must make your code work with a broad set of objects that belong to a sophisticated library or framework. Ordinarily, you’d need to initialize all of those objects, keep track of dependencies, execute methods in the correct order, and so on.
  *   As a result, the business logic of your classes would become tightly coupled to the implementation details of 3rd-party classes, making it hard to comprehend and maintain.


  *
* Solution&#x20;
  * A facade is a class that provides a simple interface to a complex subsystem which contains lots of moving parts. A facade might provide limited functionality in comparison to working with the subsystem directly.&#x20;
  * However, it includes only those features that clients really care about.
  * Having a facade is handy when you need to integrate your app with a sophisticated library that has dozens of features, but you just need a tiny bit of its functionality.
  * For instance, an app that uploads short funny videos with cats to social media could potentially use a professional video conversion library.&#x20;
  * However, all that it really needs is a class with the single method `encode(filename, format)`. After creating such a class and connecting it with the video conversion library, you’ll have your first facade.
*   Real world analogy&#x20;

    *

        <figure><img src="../../../.gitbook/assets/image (148).png" alt=""><figcaption></figcaption></figure>
    * When you call a shop to place a phone order, an operator is your facade to all services and departments of the shop.&#x20;
    * The operator provides you with a simple voice interface to the ordering system, payment gateways, and various delivery services.


*   Structure

    <figure><img src="../../../.gitbook/assets/image (149).png" alt=""><figcaption></figcaption></figure>

    1. The **Facade** provides convenient access to a particular part of the subsystem’s functionality. It knows where to direct the client’s request and how to operate all the moving parts.
    2. An **Additional Facade** class can be created to prevent polluting a single facade with unrelated features that might make it yet another complex structure. Additional facades can be used by both clients and other facades.
    3.  The **Complex Subsystem** consists of dozens of various objects. To make them all do something meaningful, you have to dive deep into the subsystem’s implementation details, such as initialising objects in the correct order and supplying them with data in the proper format.

        Subsystem classes aren’t aware of the facade’s existence. They operate within the system and work with each other directly.
    4. The **Client** uses the facade instead of calling the subsystem objects directly.


*   Applicability&#x20;

    * Use the Facade pattern when you need to have a limited but straightforward interface to a complex subsystem.
    * Often, subsystems get more complex over time. Even applying design patterns typically leads to creating more classes. A
    * &#x20;subsystem may become more flexible and easier to reuse in various contexts, but the amount of configuration and boilerplate code it demands from a client grows ever larger. The Facade attempts to fix this problem by providing a shortcut to the most-used features of the subsystem which fit most client requirements.
    * &#x20;Use the Facade when you want to structure a subsystem into layers.
    * &#x20;Create facades to define entry points to each level of a subsystem. You can reduce coupling between multiple subsystems by requiring them to communicate only through facades.
    * For example, let’s return to our video conversion framework. It can be broken down into two layers: video- and audio-related. For each layer, you can create a facade and then make the classes of each layer communicate with each other via those facades. This approach looks very similar to the [Mediator](https://refactoring.guru/design-patterns/mediator) pattern.


*   How to implement&#x20;

    1. Check whether it’s possible to provide a simpler interface than what an existing subsystem already provides. You’re on the right track if this interface makes the client code independent from many of the subsystem’s classes.
    2. Declare and implement this interface in a new facade class. The facade should redirect the calls from the client code to appropriate objects of the subsystem. The facade should be responsible for initializing the subsystem and managing its further life cycle unless the client code already does this.
    3. To get the full benefit from the pattern, make all the client code communicate with the subsystem only via the facade. Now the client code is protected from any changes in the subsystem code. For example, when a subsystem gets upgraded to a new version, you will only need to modify the code in the facade.
    4. If the facade becomes [too big](https://refactoring.guru/smells/large-class), consider extracting part of its behavior to a new, refined facade class.


* Pros
  * You can isolate your code from the complexity of a subsystem.
*   <i class="fa-fw">:fw:</i>Cons

    * &#x20;A facade can become [a god object](https://refactoring.guru/antipatterns/god-object) coupled to all classes of an app.


* Relations with other patterns&#x20;
  * [Facade](https://refactoring.guru/design-patterns/facade) defines a new interface for existing objects, whereas [Adapter](https://refactoring.guru/design-patterns/adapter) tries to make the existing interface usable. _Adapter_ usually wraps just one object, while _Facade_ works with an entire subsystem of objects.
  * [Abstract Factory](https://refactoring.guru/design-patterns/abstract-factory) can serve as an alternative to [Facade](https://refactoring.guru/design-patterns/facade) when you only want to hide the way the subsystem objects are created from the client code.
  * [Flyweight](https://refactoring.guru/design-patterns/flyweight) shows how to make lots of little objects, whereas [Facade](https://refactoring.guru/design-patterns/facade) shows how to make a single object that represents an entire subsystem.
  * [Facade](https://refactoring.guru/design-patterns/facade) and [Mediator](https://refactoring.guru/design-patterns/mediator) have similar jobs: they try to organize collaboration between lots of tightly coupled classes.
    * _Facade_ defines a simplified interface to a subsystem of objects, but it doesn’t introduce any new functionality. The subsystem itself is unaware of the facade. Objects within the subsystem can communicate directly.
    * _Mediator_ centralizes communication between components of the system. The components only know about the mediator object and don’t communicate directly.
  * A [Facade](https://refactoring.guru/design-patterns/facade) class can often be transformed into a [Singleton](https://refactoring.guru/design-patterns/singleton) since a single facade object is sufficient in most cases.
  * [Facade](https://refactoring.guru/design-patterns/facade) is similar to [Proxy](https://refactoring.guru/design-patterns/proxy) in that both buffer a complex entity and initialize it on its own. Unlike _Facade_, _Proxy_ has the same interface as its service object, which makes them interchangeable.
* Code&#x20;

```python
from __future__ import annotations


class Facade:
    """
    The Facade class provides a simple interface to the complex logic of one or
    several subsystems. The Facade delegates the client requests to the
    appropriate objects within the subsystem. The Facade is also responsible for
    managing their lifecycle. All of this shields the client from the undesired
    complexity of the subsystem.
    """

    def __init__(self, subsystem1: Subsystem1, subsystem2: Subsystem2) -> None:
        """
        Depending on your application's needs, you can provide the Facade with
        existing subsystem objects or force the Facade to create them on its
        own.
        """

        self._subsystem1 = subsystem1 or Subsystem1()
        self._subsystem2 = subsystem2 or Subsystem2()

    def operation(self) -> str:
        """
        The Facade's methods are convenient shortcuts to the sophisticated
        functionality of the subsystems. However, clients get only to a fraction
        of a subsystem's capabilities.
        """

        results = []
        results.append("Facade initializes subsystems:")
        results.append(self._subsystem1.operation1())
        results.append(self._subsystem2.operation1())
        results.append("Facade orders subsystems to perform the action:")
        results.append(self._subsystem1.operation_n())
        results.append(self._subsystem2.operation_z())
        return "\n".join(results)


class Subsystem1:
    """
    The Subsystem can accept requests either from the facade or client directly.
    In any case, to the Subsystem, the Facade is yet another client, and it's
    not a part of the Subsystem.
    """

    def operation1(self) -> str:
        return "Subsystem1: Ready!"

    # ...

    def operation_n(self) -> str:
        return "Subsystem1: Go!"


class Subsystem2:
    """
    Some facades can work with multiple subsystems at the same time.
    """

    def operation1(self) -> str:
        return "Subsystem2: Get ready!"

    # ...

    def operation_z(self) -> str:
        return "Subsystem2: Fire!"


def client_code(facade: Facade) -> None:
    """
    The client code works with complex subsystems through a simple interface
    provided by the Facade. When a facade manages the lifecycle of the
    subsystem, the client might not even know about the existence of the
    subsystem. This approach lets you keep the complexity under control.
    """

    print(facade.operation(), end="")


if __name__ == "__main__":
    # The client code may have some of the subsystem's objects already created.
    # In this case, it might be worthwhile to initialize the Facade with these
    # objects instead of letting the Facade create new instances.
    subsystem1 = Subsystem1()
    subsystem2 = Subsystem2()
    facade = Facade(subsystem1, subsystem2)
    client_code(facade)

```



### Proxy

* provide a substitute or placeholder for another object.&#x20;
* A proxy controls access to the original object, allowing you to perform something either before or after the request gets through to the original object.
* Problem&#x20;
  * Why would you want to control access to an object? &#x20;
    * you have a massive object that consumes a vast amount of system resources. You need it from time to time, but not always.
    * You could implement lazy initialization: create this object only when it’s actually needed. All of the object’s clients would need to execute some deferred initialisation code. Unfortunately, this would probably cause a lot of code duplication.
    * In an ideal world, we’d want to put this code directly into our object’s class, but that isn’t always possible. For instance, the class may be part of a closed 3rd-party library.
* SOlution&#x20;
  * Proxy pattern suggests that you create a new proxy class with the same interface as an original service object.&#x20;
  * Then you update your app so that it passes the proxy object to all of the original object’s clients.
  *   Upon receiving a request from a client, the proxy creates a real service object and delegates all the work to it.

      \
      <br>
