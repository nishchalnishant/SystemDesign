# Day 12: Study Common Design Patterns (e.g., Singleton, Factory)



## **Overview of Design Patterns**

Design patterns are well-established solutions to common software design problems. They provide a standard terminology and are best practices that help developers structure their code in a more efficient and reusable way. Design patterns are typically categorized into three main types:

* **Creational Patterns**: Deal with object creation mechanisms.
* **Structural Patterns**: Focus on class and object composition.
* **Behavioral Patterns**: Concerned with communication between objects.

***

## **1. Creational Design Patterns**

**1.1 Singleton Pattern**

* **Purpose**: Ensures that a class has only one instance and provides a global point of access to it.
* **Use Cases**: When you need to control access to shared resources such as logging, configuration settings, or database connections.
*   **Implementation**:

    * The class itself is responsible for managing its unique instance.
    * The constructor is made private to prevent the creation of new instances.
    * A static method (`getInstance()`) provides access to the single instance.

    ```java
    public class Singleton {
        private static Singleton instance;
        
        private Singleton() { }
        
        public static Singleton getInstance() {
            if (instance == null) {
                instance = new Singleton();
            }
            return instance;
        }
    }
    ```
* **Advantages**:
  * Controlled access to the sole instance.
  * Reduces memory footprint since only one instance is created.
* **Disadvantages**:
  * Singleton can be overused, leading to tightly coupled code.
  * Difficult to unit test due to global state.

**1.2 Factory Method Pattern**

* **Purpose**: Defines an interface for creating objects but allows subclasses to alter the type of objects that will be created.
* **Use Cases**: When a class can't anticipate the type of objects it needs to create beforehand or when the creation process involves complex logic.
*   **Implementation**:

    * Create an interface or abstract class that defines a method for creating objects.
    * Subclasses implement the factory method to create specific objects.

    ```java
    interface Product {
        void create();
    }

    class ConcreteProductA implements Product {
        public void create() {
            System.out.println("Product A created.");
        }
    }

    class ConcreteProductB implements Product {
        public void create() {
            System.out.println("Product B created.");
        }
    }

    abstract class Creator {
        public abstract Product factoryMethod();
    }

    class ConcreteCreatorA extends Creator {
        public Product factoryMethod() {
            return new ConcreteProductA();
        }
    }

    class ConcreteCreatorB extends Creator {
        public Product factoryMethod() {
            return new ConcreteProductB();
        }
    }
    ```
* **Advantages**:
  * Promotes loose coupling between the client code and the object creation process.
  * Allows easy extension of new product types without modifying existing code.
* **Disadvantages**:
  * Adds complexity to the code by introducing additional classes.

**1.3 Abstract Factory Pattern**

* **Purpose**: Provides an interface to create families of related or dependent objects without specifying their concrete classes.
* **Use Cases**: When a system needs to be independent of how its objects are created, or when you want to enforce that objects within a group are related or compatible.
*   **Implementation**:

    * Create abstract factory interfaces that define creation methods for different types of products.
    * Concrete factories implement these interfaces to produce different types of products.

    ```java
    interface GUIFactory {
        Button createButton();
        Checkbox createCheckbox();
    }

    class WindowsFactory implements GUIFactory {
        public Button createButton() {
            return new WindowsButton();
        }

        public Checkbox createCheckbox() {
            return new WindowsCheckbox();
        }
    }

    class MacFactory implements GUIFactory {
        public Button createButton() {
            return new MacButton();
        }

        public Checkbox createCheckbox() {
            return new MacCheckbox();
        }
    }
    ```
* **Advantages**:
  * Encourages consistency among products.
  * Provides flexibility in product creation, allowing the system to be easily switched between different product families.
* **Disadvantages**:
  * Can become complex with the addition of many product families.

***

## **2. Structural Design Patterns**

**2.1 Adapter Pattern**

* **Purpose**: Converts the interface of a class into another interface that the client expects. The adapter allows incompatible classes to work together.
* **Use Cases**: When you want to integrate a new class or external library into an existing system without modifying the existing code.
*   **Implementation**:

    * Create an adapter class that implements the target interface and wraps the adaptee.

    ```java
    interface MediaPlayer {
        void play(String audioType, String fileName);
    }

    class AudioPlayer implements MediaPlayer {
        public void play(String audioType, String fileName) {
            if(audioType.equalsIgnoreCase("mp3")) {
                System.out.println("Playing mp3 file. Name: " + fileName);
            } 
        }
    }

    class MediaAdapter implements MediaPlayer {
        AdvancedMediaPlayer advancedMusicPlayer;
        
        public MediaAdapter(String audioType) {
            if(audioType.equalsIgnoreCase("vlc")) {
                advancedMusicPlayer = new VlcPlayer();
            }
        }

        public void play(String audioType, String fileName) {
            if(audioType.equalsIgnoreCase("vlc")) {
                advancedMusicPlayer.playVlc(fileName);
            }
        }
    }
    ```
* **Advantages**:
  * Enables integration of legacy code with new systems.
  * Promotes code reuse by allowing incompatible classes to work together.
* **Disadvantages**:
  * Can add unnecessary complexity if overused.

**2.2 Decorator Pattern**

* **Purpose**: Allows behavior to be added to an object dynamically by wrapping it with additional "decorator" classes.
* **Use Cases**: When you need to extend the functionality of objects at runtime without modifying their class.
*   **Implementation**:

    * Create a base component interface.
    * Implement concrete decorators that add new functionality.

    ```java
    interface Coffee {
        String getDescription();
        double cost();
    }

    class SimpleCoffee implements Coffee {
        public String getDescription() {
            return "Simple Coffee";
        }

        public double cost() {
            return 5.00;
        }
    }

    class MilkDecorator implements Coffee {
        private Coffee coffee;

        public MilkDecorator(Coffee coffee) {
            this.coffee = coffee;
        }

        public String getDescription() {
            return coffee.getDescription() + ", Milk";
        }

        public double cost() {
            return coffee.cost() + 1.00;
        }
    }
    ```
* **Advantages**:
  * Promotes code flexibility and extendability.
  * Avoids inheritance explosion by combining behaviors dynamically.
* **Disadvantages**:
  * Can lead to complex code with many small classes.

***

## **3. Behavioral Design Patterns**

**3.1 Observer Pattern**

* **Purpose**: Defines a one-to-many relationship where one object (the subject) notifies multiple dependent objects (observers) of state changes.
* **Use Cases**: Used in event-driven systems or when multiple objects need to be updated in response to a change in one object.
*   **Implementation**:

    * Define a `Subject` class with methods to add, remove, and notify observers.
    * Observers implement an interface and get updated when the subject’s state changes.

    ```java
    interface Observer {
        void update(String message);
    }

    class ConcreteObserver implements Observer {
        public void update(String message) {
            System.out.println("Received update: " + message);
        }
    }

    class Subject {
        private List<Observer> observers = new ArrayList<>();
        
        public void addObserver(Observer observer) {
            observers.add(observer);
        }

        public void notifyObservers(String message) {
            for (Observer observer : observers) {
                observer.update(message);
            }
        }
    }
    ```
* **Advantages**:
  * Promotes loose coupling between the subject and observers.
  * Supports event-driven programming.
* **Disadvantages**:
  * Can become difficult to manage when dealing with many observers.

**3.2 Strategy Pattern**

* **Purpose**: Allows the definition of a family of algorithms, encapsulates each one, and makes them interchangeable. The client can switch between strategies dynamically.
* **Use Cases**: When you want to define a set of related algorithms and allow clients to choose which one to use at runtime.
*   **Implementation**:

    * Create a strategy interface and implement multiple concrete strategies.

    ```java
    interface PaymentStrategy {
        void pay(int amount);
    }

    class CreditCardPayment implements PaymentStrategy {
        public void pay(int amount) {
            System.out.println("Paid " + amount + " using credit card.");
        }
    }

    class PayPalPayment implements PaymentStrategy {
        public void pay(int amount) {
            System.out.println("Paid " + amount + " using PayPal.");
        }
    }

    class ShoppingCart {
        private PaymentStrategy paymentStrategy;

        public void setPaymentStrategy(PaymentStrategy paymentStrategy) {
            this.paymentStrategy = paymentStrategy;
        }

        public void checkout(int amount) {
            paymentStrategy.pay(amount);
        }
    }
    ```
* **Advantages**:
  * Promotes code reusability by encapsulating algorithms.
  * Makes algorithms interchangeable without modifying the client.
* **Disadvantages**:
  * Clients must be aware of the different strategies available.

***

## **4. Best Practices for Design Patterns**

* **Understand the problem domain**: Apply design patterns

when they solve specific problems rather than using them unnecessarily.

* **Favor composition over inheritance**: Most design patterns promote composition, making systems more flexible and extensible.
* **Refactor for patterns**: Don't try to design your system with patterns from the beginning. Identify patterns as you refactor for better maintainability.

***

By mastering these design patterns, you'll be able to demonstrate your architectural knowledge and problem-solving skills in system design interviews. These patterns are foundational and frequently used in real-world software development.
