import java.util.*;
import java.util.function.*;

/**
 * DesignPatterns.java
 * A single runnable reference implementing every pattern from
 * "Foundations of Object-Oriented Programming & Software Design".
 *
 * Each pattern lives in its own static nested container class so names
 * never collide. Run with: java DesignPatterns.java
 */
public class DesignPatterns {

    // =====================================================================
    // SECTION 0 — THE SIX CLASS RELATIONSHIPS (the vocabulary everything uses)
    // =====================================================================
    /**
     * THE SIX CLASS RELATIONSHIPS — The foundation vocabulary for every pattern below.
     *
     * Every design pattern is built from one or more of these six relationships.
     * Understanding them is the prerequisite to understanding WHY a pattern is structured
     * the way it is.
     *
     * Ordered from tightest to loosest coupling:
     *
     *  1. INHERITANCE  (Is-A)        — subclass extends superclass; reuses & overrides
     *  2. REALISATION  (Can-Do)      — class implements interface; declares a capability
     *  3. COMPOSITION  (Has-A, own)  — part is created inside the owner; dies with it
     *  4. AGGREGATION  (Has-A, borrow) — part is injected from outside; outlives the owner
     *  5. ASSOCIATION  (Knows-A)     — peer reference stored as a field; independent lifecycles
     *  6. DEPENDENCY   (Uses-A)      — transient reference via a method parameter; shortest lived
     *
     * UML coupling rule: prefer the loosest relationship that satisfies the requirement.
     */
    static class Relationships {

        // 1. INHERITANCE — "Is-A" — tightest coupling
        //    UML: solid arrow with closed hollow arrowhead  (Car ──▷ Vehicle)
        //    The subclass inherits ALL non-private state and behaviour.
        //    Use ONLY when every instance of the child truly IS a parent (Liskov test).
        //    Pitfall: breaks encapsulation — child depends on parent internals.
        static abstract class Vehicle {
            protected String fuelType = "petrol";

            abstract void move();
        }

        static class Car extends Vehicle { // Car IS-A Vehicle
            @Override
            void move() {
                System.out.println("  [Is-A] Driving on roads.");
            }
        }

        // 2. REALISATION — "Can-Do" — strong, but capability-only
        //    UML: dashed arrow with closed hollow arrowhead  (ElectricCar - - ▷ Chargeable)
        //    Commits the class to a contract (interface) without inheriting any state.
        //    Java best practice: prefer interfaces over abstract classes for shared types.
        //    This is how Strategy, Observer, Command, etc. achieve polymorphism without inheritance.
        interface Chargeable {
            void plugIn();
        }

        static class ElectricCar implements Chargeable { // ElectricCar CAN-DO charging
            @Override
            public void plugIn() {
                System.out.println("  [Can-Do] Charging battery...");
            }
        }

        // 3. COMPOSITION — "Has-A" strong ownership — part dies with the whole
        //    UML: solid diamond at the owner end  (ComposedCar ◆── Engine)
        //    Key signal: the part is created with `new` INSIDE the owner constructor.
        //    The part has no meaning or existence outside the owner.
        //    Used in: Singleton (Holder), Facade, Flyweight pool, Builder.
        static class Engine {
            void fire() {
                System.out.println("  [Has-A strong] Cylinders firing.");
            }
        }

        static class ComposedCar {
            private final Engine engine;

            ComposedCar() {
                this.engine = new Engine();
            } // created INSIDE -> owned

            void start() {
                engine.fire();
            }
        }

        // 4. AGGREGATION — "Has-A" weak ownership — part outlives the whole
        //    UML: hollow diamond at the owner end  (AggregatedCar ◇── Wheel)
        //    Key signal: the part is INJECTED (constructor/setter) from the outside.
        //    This is the basis of Dependency Injection and most GoF patterns (Strategy, Decorator, Bridge).
        //    Prefer this over Composition when the part is shared or has independent meaning.
        static class Wheel {
            void roll() {
                System.out.println("  [Has-A weak] Wheel rolling.");
            }
        }

        static class AggregatedCar {
            private final Wheel wheel;

            AggregatedCar(Wheel external) {
                this.wheel = external;
            } // passed IN -> borrowed

            void drive() {
                wheel.roll();
            }
        }

        // 5. PLAIN ASSOCIATION — "Knows-A" — peers, independent lifecycles
        //    UML: plain arrow  (Doctor ──> Patient)
        //    Both objects are created independently; one stores a reference to the other.
        //    Can be unidirectional (Doctor → Patient) or bidirectional (Doctor ↔ Patient).
        //    Used in: Mediator (users know the mediator), Observer (subject knows observers).
        static class Doctor {
            private final List<Patient> patients = new ArrayList<>();

            void addPatient(Patient p) {
                patients.add(p);
            }

            void greet() {
                System.out.println("  [Knows-A] Doctor has " + patients.size() + " patient(s).");
            }
        }

        static class Patient {
            private Doctor primaryDoctor;

            void setDoctor(Doctor d) {
                this.primaryDoctor = d;
            }
        }

        // 6. DEPENDENCY — "Uses-A" — loosest, exists only for the method call
        //    UML: dashed arrow  (RefuelableCar - -> FuelPump)
        //    The dependency appears only in a method signature (parameter, local variable, return type).
        //    No field is stored — the relationship ends when the method returns.
        //    Golden rule: if you can express a relationship as Dependency instead of Association, do it.
        static class FuelPump {
            void pumpGas() {
                System.out.println("  [Uses-A] Pumping fuel.");
            }
        }

        static class RefuelableCar {
            void refuel(FuelPump pump) {
                pump.pumpGas();
            } // relationship ends with the method
        }

        static void demo() {
            header("0. SIX CLASS RELATIONSHIPS");
            new Car().move();
            new ElectricCar().plugIn();
            new ComposedCar().start();
            new AggregatedCar(new Wheel()).drive();
            Doctor d = new Doctor();
            Patient p = new Patient();
            d.addPatient(p);
            p.setDoctor(d);
            d.greet();
            new RefuelableCar().refuel(new FuelPump());
        }
    }

    // =====================================================================
    // SECTION 1 — CREATIONAL PATTERNS
    // =====================================================================

    /**
     * 1.1 SINGLETON — "There Can Be Only One"
     * Relationship used: Self-referencing Composition (the class owns its own single instance).
     *
     * INTENT: Ensure a class has exactly one instance and provide a global access point to it.
     *
     * IMPLEMENTATION (Initialization-on-demand Holder idiom — Bill Pugh Singleton):
     *   - The outer class `DatabaseConnection` has a PRIVATE constructor, so no one can call `new`.
     *   - A private static inner class `Holder` contains the sole instance as a static final field.
     *   - The JVM guarantees that static fields are initialized exactly once, lazily, and thread-safely
     *     the first time the class is loaded — so we get lazy init WITHOUT synchronization overhead.
     *   - `getInstance()` simply returns `Holder.INSTANCE`.
     *
     * KEY SIGNALS IN CODE:
     *   - `private` constructor
     *   - `static final` instance inside a nested `Holder`
     *   - `static getInstance()` factory method
     *
     * WHEN TO USE:
     *   - Database connection pools, configuration managers, loggers, thread pools.
     *   - When exactly one object must coordinate actions across the system.
     *
     * PITFALLS:
     *   - Hard to unit-test (global state is hard to mock).
     *   - Breaks Single Responsibility if the singleton also controls its own lifecycle.
     *   - Enum-based singleton (`enum DB { INSTANCE; }`) is the most serialization-safe alternative.
     */
    static class SingletonPattern {
        // Eager/holder idiom: thread-safe without synchronization cost.
        static final class DatabaseConnection {
            private static class Holder {
                static final DatabaseConnection INSTANCE = new DatabaseConnection();
            }

            private DatabaseConnection() {
                System.out.println("  Initializing connection pool (happens once)...");
            }

            static DatabaseConnection getInstance() {
                return Holder.INSTANCE;
            }

            void executeQuery(String sql) {
                System.out.println("  Executing: " + sql);
            }
        }

        static void demo() {
            header("1.1 SINGLETON");
            DatabaseConnection a = DatabaseConnection.getInstance();
            DatabaseConnection b = DatabaseConnection.getInstance();
            a.executeQuery("SELECT * FROM users");
            System.out.println("  Same instance? " + (a == b));
        }
    }

    /**
     * 1.2 FACTORY METHOD — "Let Subclasses Choose"
     * Relationships used: Inheritance (App hierarchy) + Realisation (Document interface).
     *
     * INTENT: Define an interface for creating an object, but let subclasses decide which
     * concrete class to instantiate. The factory method defers instantiation to subclasses.
     *
     * PARTICIPANTS:
     *   - Product       : `Document` interface — what is created.
     *   - ConcreteProduct: `PdfDocument`, `WordDocument` — specific implementations.
     *   - Creator       : `DocumentApp` — declares the abstract factory method `createDocument()`.
     *   - ConcreteCreator: `PdfApp`, `WordApp` — override the factory method.
     *
     * IMPLEMENTATION:
     *   - `DocumentApp.openDocument()` is a TEMPLATE METHOD — it calls `createDocument()` but
     *     doesn't know (or care) which concrete type it gets back.
     *   - Subclasses override only `createDocument()` — the rest of the algorithm is inherited.
     *   - Client code works against `DocumentApp`; the concrete type is chosen at construction time.
     *
     * vs ABSTRACT FACTORY: Factory Method creates ONE product; Abstract Factory creates a FAMILY.
     * vs STRATEGY: Strategy swaps the algorithm; Factory Method swaps the object being created.
     *
     * WHEN TO USE:
     *   - When a framework must create objects but should remain open for extension.
     *   - Spring `BeanFactory`, JDBC `DriverManager.getConnection()`, `Collection.iterator()`.
     *
     * PITFALL: Can lead to class explosion — one ConcreteCreator per ConcreteProduct.
     */
    static class FactoryMethodPattern {
        interface Document {
            void open();
        }

        static class PdfDocument implements Document {
            public void open() {
                System.out.println("  Opening PDF.");
            }
        }

        static class WordDocument implements Document {
            public void open() {
                System.out.println("  Opening Word doc.");
            }
        }

        static abstract class DocumentApp {
            protected abstract Document createDocument(); // <-- the factory method

            final void openDocument() {
                createDocument().open();
            } // stable algorithm
        }

        static class PdfApp extends DocumentApp {
            protected Document createDocument() {
                return new PdfDocument();
            }
        }

        static class WordApp extends DocumentApp {
            protected Document createDocument() {
                return new WordDocument();
            }
        }

        static void demo() {
            header("1.2 FACTORY METHOD");
            for (DocumentApp app : List.of(new PdfApp(), new WordApp()))
                app.openDocument();
        }
    }

    /**
     * 1.3 ABSTRACT FACTORY — "Families of Products"
     * Relationships used: Composition (Application owns its widgets) + Dependency Inversion (DIP).
     *
     * INTENT: Provide an interface for creating families of related objects without specifying
     * their concrete classes. Ensures that products from the same family are used together.
     *
     * PARTICIPANTS:
     *   - AbstractFactory : `UIFactory` — declares creation methods for each product type.
     *   - ConcreteFactory : `MacFactory`, `WinFactory` — implement creation for one family.
     *   - AbstractProduct : `Button`, `Checkbox` — interfaces for each product kind.
     *   - ConcreteProduct : `MacButton`, `WinButton`, etc.
     *   - Client          : `Application` — uses only the abstract interfaces; never mentions Mac/Win.
     *
     * IMPLEMENTATION:
     *   - `Application` receives a `UIFactory` via its constructor (Dependency Injection).
     *   - It calls `f.createButton()` and `f.createCheckbox()` — it never uses `new` directly.
     *   - Swapping `MacFactory` for `WinFactory` gives an entirely different UI family.
     *
     * vs FACTORY METHOD: Abstract Factory uses composition; Factory Method uses inheritance.
     *
     * WHEN TO USE:
     *   - Cross-platform UI toolkits (Swing LAF, JDBC drivers for different DBs).
     *   - When the system must be independent of how its products are created.
     *
     * PITFALL: Adding a new product type (e.g., `Scrollbar`) requires changing the factory
     * interface AND all concrete factories — violates OCP for new product kinds.
     */
    static class AbstractFactoryPattern {
        interface Button {
            void paint();
        }

        interface Checkbox {
            void render();
        }

        interface UIFactory {
            Button createButton();

            Checkbox createCheckbox();
        }

        static class MacButton implements Button {
            public void paint() {
                System.out.println("  Mac button");
            }
        }

        static class MacCheckbox implements Checkbox {
            public void render() {
                System.out.println("  Mac checkbox");
            }
        }

        static class MacFactory implements UIFactory {
            public Button createButton() {
                return new MacButton();
            }

            public Checkbox createCheckbox() {
                return new MacCheckbox();
            }
        }

        static class WinButton implements Button {
            public void paint() {
                System.out.println("  Windows button");
            }
        }

        static class WinCheckbox implements Checkbox {
            public void render() {
                System.out.println("  Windows checkbox");
            }
        }

        static class WinFactory implements UIFactory {
            public Button createButton() {
                return new WinButton();
            }

            public Checkbox createCheckbox() {
                return new WinCheckbox();
            }
        }

        static class Application {
            private final Button button;
            private final Checkbox checkbox;

            Application(UIFactory f) {
                this.button = f.createButton();
                this.checkbox = f.createCheckbox();
            }

            void draw() {
                button.paint();
                checkbox.render();
            }
        }

        static void demo() {
            header("1.3 ABSTRACT FACTORY");
            new Application(new MacFactory()).draw();
            new Application(new WinFactory()).draw();
        }
    }

    /**
     * 1.4 BUILDER — "Step-by-Step Construction"
     * Relationship used: Inner Builder aggregates optional parts before handing off to the product.
     *
     * INTENT: Separate the construction of a complex object from its representation so that
     * the same construction process can create different representations.
     *
     * PARTICIPANTS:
     *   - Product  : `Computer` — the immutable result; private constructor enforces use of Builder.
     *   - Builder  : `Computer.Builder` — holds mutable defaults; exposes fluent setters.
     *   - Director : (implicit) — the call-chain in `demo()` plays this role.
     *
     * IMPLEMENTATION:
     *   - `Computer` has a `private` constructor that only accepts a `Builder` instance.
     *   - Each setter in `Builder` returns `this`, enabling a FLUENT API / method chaining.
     *   - `build()` calls `new Computer(this)` — the builder is the only way in.
     *   - Unset fields keep their defaults (`"500GB"`, `"8GB"`, `false`).
     *   - The resulting `Computer` is fully immutable — all fields are `final`.
     *
     * WHEN TO USE:
     *   - Objects with many optional parameters (avoids telescoping constructors).
     *   - When you want an immutable object but construction requires multiple steps.
     *   - `StringBuilder`, `HttpRequest.Builder`, `AlertDialog.Builder` (Android).
     *
     * PITFALL: The builder itself is mutable and can be reused — calling `build()` twice
     * creates two independent objects, which may surprise callers.
     */
    static class BuilderPattern {
        static final class Computer {
            private final String hdd, ram; // immutable result
            private final boolean graphicsCard;

            private Computer(Builder b) {
                this.hdd = b.hdd;
                this.ram = b.ram;
                this.graphicsCard = b.graphicsCard;
            }

            static class Builder {
                private String hdd = "500GB", ram = "8GB";
                private boolean graphicsCard = false;

                Builder setHDD(String hdd) {
                    this.hdd = hdd;
                    return this;
                } // return this -> chaining

                Builder setRAM(String ram) {
                    this.ram = ram;
                    return this;
                }

                Builder setGraphics(boolean on) {
                    this.graphicsCard = on;
                    return this;
                }

                Computer build() {
                    return new Computer(this);
                }
            }

            @Override
            public String toString() {
                return "Computer[hdd=" + hdd + ", ram=" + ram + ", gpu=" + graphicsCard + "]";
            }
        }

        static void demo() {
            header("1.4 BUILDER");
            Computer pc = new Computer.Builder().setHDD("1TB").setRAM("16GB").setGraphics(true).build();
            System.out.println("  " + pc);
            System.out.println("  " + new Computer.Builder().setRAM("32GB").build()); // defaults survive
        }
    }

    /**
     * 1.5 PROTOTYPE — "Clone Yourself"
     * Relationship used: Realisation of a `Prototype<T>` copy contract.
     *
     * INTENT: Specify the kinds of objects to create using a prototypical instance, and
     * create new objects by COPYING (cloning) this prototype.
     *
     * PARTICIPANTS:
     *   - Prototype interface : `Prototype<T>` — declares the `copy()` method.
     *   - ConcretePrototype   : `GameCharacter` and `Loadout` — implement `copy()` with
     *                           deep-copy semantics for nested mutable state.
     *
     * IMPLEMENTATION — DEEP vs SHALLOW:
     *   - `GameCharacter.copy()` creates a new character AND calls `loadout.copy()` recursively.
     *   - `Loadout.copy()` creates a new `ArrayList` seeded with the same items — so mutating
     *     the clone's list does NOT affect the master's list.
     *   - Primitive fields (`int health`) and immutable fields (`String weapon`) are safe to
     *     copy by value; only mutable object references need deep copying.
     *
     * SHALLOW vs DEEP rule of thumb:
     *   - Shallow copy: fast, fine when all fields are primitive or immutable (String, Integer).
     *   - Deep copy: required when any field points to a MUTABLE object.
     *
     * WHEN TO USE:
     *   - When creating objects is expensive (e.g., DB-loaded configuration) and copies are cheap.
     *   - Game characters, template documents, undo/snapshot systems.
     *
     * PITFALL: Java's `Object.clone()` is shallow by default. Prefer an explicit `copy()` method
     * (as done here) over implementing `Cloneable` — the latter is widely considered broken.
     */
    static class PrototypePattern {
        interface Prototype<T> {
            T copy();
        }

        static class Loadout implements Prototype<Loadout> { // nested mutable state -> needs deep copy
            List<String> items;

            Loadout(List<String> items) {
                this.items = new ArrayList<>(items);
            }

            public Loadout copy() {
                return new Loadout(this.items);
            } // new list = deep
        }

        static class GameCharacter implements Prototype<GameCharacter> {
            String weapon;
            int health;
            Loadout loadout;

            GameCharacter(String weapon, int health, Loadout loadout) {
                this.weapon = weapon;
                this.health = health;
                this.loadout = loadout;
            }

            public GameCharacter copy() {
                return new GameCharacter(weapon, health, loadout.copy());
            }

            void show() {
                System.out.println("  " + weapon + " | HP " + health + " | bag " + loadout.items);
            }
        }

        static void demo() {
            header("1.5 PROTOTYPE");
            GameCharacter master = new GameCharacter("Sword", 100, new Loadout(List.of("potion")));
            GameCharacter clone = master.copy();
            clone.weapon = "Axe";
            clone.loadout.items.add("bomb"); // deep copy -> master is untouched
            master.show();
            clone.show();
        }
    }

    // =====================================================================
    // SECTION 2 — STRUCTURAL PATTERNS
    // =====================================================================

    /**
     * 2.1 ADAPTER — "The Translator"
     * Relationship used: Aggregation (the adapter wraps/holds the adaptee).
     *
     * INTENT: Convert the interface of a class into another interface clients expect.
     * Adapter lets classes work together that couldn't otherwise because of incompatible interfaces.
     *
     * PARTICIPANTS:
     *   - Target    : `ModernCharging` — the interface the client knows and uses.
     *   - Adaptee   : `OldPhone` — the existing class with an incompatible interface.
     *   - Adapter   : `CableAdapter` — implements Target and internally delegates to Adaptee.
     *   - Client    : code that only sees `ModernCharging`, unaware of `OldPhone`.
     *
     * IMPLEMENTATION:
     *   - `CableAdapter` implements `ModernCharging` (the expected interface).
     *   - It holds an `OldPhone` reference (aggregation — injected via constructor).
     *   - `chargeWithUsbC()` translates the call to `chargeWithMicroUsb()` internally.
     *   - This is an OBJECT ADAPTER (delegation). A CLASS ADAPTER would use multiple inheritance
     *     (not possible in Java — only feasible in C++ or via Kotlin extension functions).
     *
     * vs DECORATOR: Decorator adds behaviour, keeps the same interface.
     *               Adapter changes the interface, no new behaviour.
     * vs PROXY:     Proxy keeps the same interface and controls access.
     *
     * WHEN TO USE:
     *   - Integrating third-party or legacy code without modifying it.
     *   - `Arrays.asList()`, `InputStreamReader(InputStream)`, Java Streams over Iterators.
     *
     * PITFALL: Over-adapting creates layers of indirection; prefer redesigning if you own both sides.
     */
    static class AdapterPattern {
        interface ModernCharging {
            void chargeWithUsbC();
        } // what the client expects

        static class OldPhone {
            void chargeWithMicroUsb() {
                System.out.println("  Charging via micro-USB...");
            }
        }

        static class CableAdapter implements ModernCharging {
            private final OldPhone adaptee;

            CableAdapter(OldPhone adaptee) {
                this.adaptee = adaptee;
            }

            public void chargeWithUsbC() {
                System.out.println("  Translating USB-C -> micro-USB");
                adaptee.chargeWithMicroUsb(); // delegation
            }
        }

        static void demo() {
            header("2.1 ADAPTER");
            ModernCharging port = new CableAdapter(new OldPhone());
            port.chargeWithUsbC();
        }
    }

    /**
     * 2.2 BRIDGE — "Independent Axes"
     * Relationship used: Aggregation across two separate inheritance hierarchies.
     *
     * INTENT: Decouple an abstraction from its implementation so that the two can vary independently.
     * Avoids the multiplicative class explosion that would result from sub-classing every combination.
     *
     * PARTICIPANTS:
     *   - Abstraction         : `Shape` — the high-level control layer; holds a reference to Implementor.
     *   - RefinedAbstraction  : `Circle`, `Square` — extend Abstraction with specific behaviour.
     *   - Implementor         : `Color` interface — the low-level operations.
     *   - ConcreteImplementor : `Red`, `Blue` — provide the actual implementations.
     *
     * IMPLEMENTATION:
     *   - `Shape` has a `protected final Color color` field — THIS is the bridge.
     *   - The `Color` is injected via `Shape`'s constructor (aggregation / DI).
     *   - `Circle.draw()` calls `color.applyColor()` — delegating to whichever implementor was injected.
     *   - Result: 2 shapes × 2 colors = 4 combos from just 4 classes, not 4 subclasses.
     *   - Adding a new color (e.g., `Green`) doesn't touch any `Shape` code, and vice versa.
     *
     * vs STRATEGY: Strategy swaps the whole algorithm at runtime; Bridge separates two
     *              orthogonal hierarchies at design time.
     * vs ADAPTER:  Adapter makes incompatible interfaces work together;
     *              Bridge anticipates variability from the start.
     *
     * WHEN TO USE:
     *   - GUI toolkits (shape hierarchy vs. rendering backend: OpenGL, DirectX, SVG).
     *   - JDBC Driver API: `Connection` (abstraction) vs. driver implementation (implementor).
     *
     * PITFALL: Over-engineering if the two axes never actually vary independently in practice.
     */
    static class BridgePattern {
        interface Color {
            void applyColor();
        } // implementation axis

        static class Red implements Color {
            public void applyColor() {
                System.out.println("red.");
            }
        }

        static class Blue implements Color {
            public void applyColor() {
                System.out.println("blue.");
            }
        }

        static abstract class Shape { // abstraction axis
            protected final Color color; // <-- the bridge

            protected Shape(Color color) {
                this.color = color;
            }

            abstract void draw();
        }

        static class Circle extends Shape {
            Circle(Color c) {
                super(c);
            }

            void draw() {
                System.out.print("  Circle painted ");
                color.applyColor();
            }
        }

        static class Square extends Shape {
            Square(Color c) {
                super(c);
            }

            void draw() {
                System.out.print("  Square painted ");
                color.applyColor();
            }
        }

        static void demo() {
            header("2.2 BRIDGE");
            // 2 shapes x 2 colors = 4 combos from 4 classes, not 4 subclasses
            for (Shape s : List.of(new Circle(new Red()), new Circle(new Blue()),
                    new Square(new Red()), new Square(new Blue())))
                s.draw();
        }
    }

    /**
     * 2.3 COMPOSITE — "Tree Hierarchy"
     * Relationship used: Recursive Aggregation (a container holds a list of the same interface).
     *
     * INTENT: Compose objects into tree structures to represent part-whole hierarchies.
     * Composite lets clients treat individual objects (leaves) and compositions of objects
     * (composites) uniformly through a single interface.
     *
     * PARTICIPANTS:
     *   - Component : `SystemComponent` interface — the uniform interface for both leaf and composite.
     *   - Leaf      : `FileItem` — has no children; performs the operation directly.
     *   - Composite : `DirectoryFolder` — stores children and delegates `showDetails()` to them recursively.
     *   - Client    : calls `showDetails()` on any node, not knowing if it's a file or a directory.
     *
     * IMPLEMENTATION:
     *   - `DirectoryFolder` contains a `List<SystemComponent>` — it can hold either `FileItem` or
     *     another `DirectoryFolder` (self-referential structure).
     *   - `add()` returns `this` for fluent chaining (see `demo()`).
     *   - `showDetails()` is recursive: composite calls the method on each child, increasing indent.
     *   - The client only calls `tree.showDetails("  ")` on the root; the tree handles the rest.
     *
     * vs DECORATOR: Decorator has one child (wraps a single component);
     *               Composite has multiple children (tree branching).
     *
     * WHEN TO USE:
     *   - File-system hierarchies, UI widget trees, parse trees, org charts.
     *   - Anywhere you need "treat a group like a single item" uniformly.
     *
     * PITFALL: The common interface forces Leaf classes to expose `add/remove` methods
     * they don't support — handle by throwing `UnsupportedOperationException` or by
     * using a separate `Composite` interface (transparency vs safety trade-off).
     */
    static class CompositePattern {
        interface SystemComponent {
            void showDetails(String indent);
        }

        static class FileItem implements SystemComponent { // leaf
            private final String name;

            FileItem(String name) {
                this.name = name;
            }

            public void showDetails(String indent) {
                System.out.println(indent + "File: " + name);
            }
        }

        static class DirectoryFolder implements SystemComponent { // composite
            private final String name;
            private final List<SystemComponent> children = new ArrayList<>();

            DirectoryFolder(String name) {
                this.name = name;
            }

            DirectoryFolder add(SystemComponent c) {
                children.add(c);
                return this;
            }

            public void showDetails(String indent) {
                System.out.println(indent + "Dir : " + name);
                for (SystemComponent c : children)
                    c.showDetails(indent + "   "); // recursion
            }
        }

        static void demo() {
            header("2.3 COMPOSITE");
            SystemComponent tree = new DirectoryFolder("src")
                    .add(new FileItem("Main.java"))
                    .add(new DirectoryFolder("util")
                            .add(new FileItem("Strings.java"))
                            .add(new FileItem("Dates.java")));
            tree.showDetails("  ");
        }
    }

    /**
     * 2.4 DECORATOR — "Dynamic Wrapper"
     * Relationships used: Recursive Aggregation (wraps same interface) + Realisation.
     *
     * INTENT: Attach additional responsibilities to an object dynamically. Decorators provide
     * a flexible alternative to subclassing for extending functionality.
     *
     * PARTICIPANTS:
     *   - Component          : `Coffee` interface — defines the common interface.
     *   - ConcreteComponent  : `PlainCoffee` — the base object being decorated.
     *   - Decorator (abstract): `CoffeeDecorator` — implements `Coffee` AND holds a `Coffee` reference.
     *   - ConcreteDecorator  : `Milk`, `Cinnamon` — add cost and description on top of the inner item.
     *
     * IMPLEMENTATION — the recursive wrapping:
     *   - `CoffeeDecorator` holds `protected final Coffee inner` — this IS the item being wrapped.
     *   - `Milk.getCost()` calls `super.getCost()` which calls `inner.getCost()`, and adds its own delta.
     *   - Stacking: `new Cinnamon(new Milk(new PlainCoffee()))` builds a call chain:
     *       Cinnamon.getCost() → Milk.getCost() → PlainCoffee.getCost() = 10 + 3.5 + 1.5 = 15.0
     *   - Decorators are COMPOSABLE at runtime without changing any existing class.
     *
     * vs COMPOSITE: Composite has MANY children; Decorator has exactly ONE.
     * vs INHERITANCE: Inheritance is static (compile-time); Decorator is dynamic (runtime).
     * vs PROXY: Proxy controls access; Decorator adds behaviour.
     *
     * WHEN TO USE:
     *   - `java.io` streams: `new BufferedReader(new InputStreamReader(new FileInputStream(f)))`
     *   - Logging, compression, encryption added around a core service.
     *   - When sub-classing every combination would cause class explosion.
     *
     * PITFALL: Many small wrapper objects can be hard to debug — unwrapping the stack to find
     * which decorator caused an issue requires careful logging or a visitor.
     */
    static class DecoratorPattern {
        interface Coffee {
            double getCost();

            String getDescription();
        }

        static class PlainCoffee implements Coffee {
            public double getCost() {
                return 10.0;
            }

            public String getDescription() {
                return "coffee";
            }
        }

        static abstract class CoffeeDecorator implements Coffee {
            protected final Coffee inner; // wraps the SAME interface

            protected CoffeeDecorator(Coffee inner) {
                this.inner = inner;
            }

            public double getCost() {
                return inner.getCost();
            }

            public String getDescription() {
                return inner.getDescription();
            }
        }

        static class Milk extends CoffeeDecorator {
            Milk(Coffee c) {
                super(c);
            }

            public double getCost() {
                return super.getCost() + 3.5;
            }

            public String getDescription() {
                return super.getDescription() + " + milk";
            }
        }

        static class Cinnamon extends CoffeeDecorator {
            Cinnamon(Coffee c) {
                super(c);
            }

            public double getCost() {
                return super.getCost() + 1.5;
            }

            public String getDescription() {
                return super.getDescription() + " + cinnamon";
            }
        }

        static void demo() {
            header("2.4 DECORATOR");
            Coffee order = new Cinnamon(new Milk(new PlainCoffee())); // stack wrappers at runtime
            System.out.println("  " + order.getDescription() + " = " + order.getCost());
        }
    }

    /**
     * 2.5 FLYWEIGHT — "Shared Optimisation"
     * Relationship used: Factory pool (Composition of a static Map) + shared references.
     *
     * INTENT: Use sharing to efficiently support a large number of fine-grained objects.
     * Separates state into INTRINSIC (shared, immutable) and EXTRINSIC (unique, passed in).
     *
     * KEY CONCEPTS:
     *   - INTRINSIC state  : data that is the SAME across many objects (texture, color of a tree type).
     *                        Stored inside the flyweight object; never changes.
     *   - EXTRINSIC state  : data that DIFFERS per use (x, y position of a specific tree).
     *                        NOT stored in the flyweight; passed in at the time of use.
     *
     * PARTICIPANTS:
     *   - Flyweight        : `TreeType` — holds intrinsic state; `display(x, y)` accepts extrinsic state.
     *   - FlyweightFactory : `TreeFactory` — manages the pool; creates new flyweights only when needed.
     *   - Client           : calls `TreeFactory.get(...)` and passes extrinsic state to `display()`.
     *
     * IMPLEMENTATION:
     *   - `POOL` is a `static final Map<String, TreeType>` — the cache of shared instances.
     *   - `computeIfAbsent` creates a new `TreeType` only on first request for that key;
     *     subsequent calls for the same texture-color return the SAME object from the pool.
     *   - 4 trees drawn, but only 1 `TreeType` object exists in memory (if all are "oak-green").
     *
     * WHEN TO USE:
     *   - Rendering engines with millions of similar objects (game trees, particles, characters).
     *   - Java's `String.intern()`, `Integer.valueOf()` cache (-128 to 127), `Boolean.TRUE/FALSE`.
     *
     * PITFALL: Flyweights must be IMMUTABLE — sharing a mutable object would corrupt all users.
     *          The pool itself can be a memory leak if references are never evicted.
     */
    static class FlyweightPattern {
        static class TreeType { // INTRINSIC: shared, immutable
            private final String texture, color;

            TreeType(String texture, String color) {
                this.texture = texture;
                this.color = color;
            }

            void display(int x, int y) { // EXTRINSIC: passed in
                System.out.println("  " + color + " " + texture + " tree at (" + x + "," + y + ")");
            }
        }

        static class TreeFactory {
            private static final Map<String, TreeType> POOL = new HashMap<>();

            static TreeType get(String texture, String color) {
                return POOL.computeIfAbsent(texture + "-" + color, k -> new TreeType(texture, color));
            }

            static int poolSize() {
                return POOL.size();
            }
        }

        static void demo() {
            header("2.5 FLYWEIGHT");
            int[][] positions = { { 1, 1 }, { 5, 3 }, { 9, 7 }, { 2, 8 } };
            for (int[] p : positions)
                TreeFactory.get("oak", "green").display(p[0], p[1]);
            System.out.println("  4 trees drawn, distinct objects in pool: " + TreeFactory.poolSize());
        }
    }

    /**
     * 2.6 FACADE — "Simplified Gateway"
     * Relationship used: Composition/Aggregation of multiple subsystem objects.
     *
     * INTENT: Provide a unified, simplified interface to a set of interfaces in a subsystem.
     * Facade defines a higher-level interface that makes the subsystem easier to use.
     *
     * PARTICIPANTS:
     *   - Facade     : `HomeTheaterFacade` — the single entry point; hides the subsystem.
     *   - Subsystems : `SoundSystem`, `Projector`, `Lights` — the complex parts being wrapped.
     *   - Client     : only calls `watchMovie()` and knows nothing about the subsystem steps.
     *
     * IMPLEMENTATION:
     *   - `HomeTheaterFacade` creates and owns the subsystem objects as private fields (composition).
     *   - `watchMovie()` orchestrates the correct sequence: dim lights → start projector → audio on.
     *   - The client is completely decoupled from the order of operations and the subsystem APIs.
     *   - Subsystems can still be used directly by advanced clients who need fine-grained control.
     *
     * vs ADAPTER: Adapter makes an existing interface compatible; Facade creates a NEW simpler interface.
     * vs MEDIATOR: Mediator coordinates PEERS that know each other; Facade shields clients from SUBSYSTEMS.
     *
     * WHEN TO USE:
     *   - Layered architecture entry points (Service layer over DAO/Repository layer).
     *   - SDK or library wrappers: `Hibernate.getSession()`, `SLF4J LoggerFactory`.
     *   - When you want to structure a subsystem into layers (reduce coupling between layers).
     *
     * PITFALL: The Facade can become a "God Class" if it grows too large.
     *          Keep it thin — it should DELEGATE, not IMPLEMENT logic.
     */
    static class FacadePattern {
        static class SoundSystem {
            void turnOn() {
                System.out.println("  Amplifier on.");
            }
        }

        static class Projector {
            void start() {
                System.out.println("  Projector warming up.");
            }
        }

        static class Lights {
            void dim() {
                System.out.println("  Lights dimmed to 10%.");
            }
        }

        static class HomeTheaterFacade {
            private final SoundSystem audio = new SoundSystem();
            private final Projector video = new Projector();
            private final Lights lights = new Lights();

            void watchMovie() { // one call hides the sequence
                System.out.println("  -- watchMovie() --");
                lights.dim();
                video.start();
                audio.turnOn();
            }
        }

        static void demo() {
            header("2.6 FACADE");
            new HomeTheaterFacade().watchMovie();
        }
    }

    /**
     * 2.7 PROXY — "The Gatekeeper"
     * Relationship used: Same-interface Realisation + lazy Composition of the real subject.
     *
     * INTENT: Provide a surrogate or placeholder for another object to control access to it.
     *
     * PROXY TYPES — all share the same interface as the real object:
     *   - PROTECTION PROXY  : controls access based on permissions (this example — ban list).
     *   - VIRTUAL PROXY     : delays expensive object creation until it's actually needed (lazy init).
     *   - REMOTE PROXY      : represents an object in a different address space (RMI, gRPC stub).
     *   - CACHING PROXY     : stores results of expensive calls and returns cached data.
     * (Java's `java.lang.reflect.Proxy` and Spring AOP are runtime proxy examples.)
     *
     * PARTICIPANTS:
     *   - Subject      : `InternetAccess` — the interface both real and proxy implement.
     *   - RealSubject  : `RealInternet` — the actual implementation that does the real work.
     *   - Proxy        : `ProtectedInternetProxy` — intercepts calls, applies logic, then delegates.
     *
     * IMPLEMENTATION:
     *   - `ProtectedInternetProxy` implements `InternetAccess` — exactly the same contract.
     *   - `RealInternet real` field starts as `null` (VIRTUAL PROXY — lazy init).
     *   - On first allowed access, `real = new RealInternet()` is created and reused thereafter.
     *   - Banned URLs are rejected before the real object is even touched.
     *
     * vs DECORATOR: Decorator adds behaviour; Proxy controls access.
     * vs ADAPTER:   Adapter changes the interface; Proxy keeps the same interface.
     * vs FACADE:    Facade simplifies a group of interfaces; Proxy wraps a single object.
     *
     * WHEN TO USE:
     *   - Security / access control, lazy loading, logging, caching, rate limiting.
     *   - Spring `@Transactional`, Hibernate lazy-loaded entities, CDN request routing.
     */
    static class ProxyPattern {
        interface InternetAccess {
            void browseSite(String url);
        }

        static class RealInternet implements InternetAccess {
            public void browseSite(String url) {
                System.out.println("  Connecting to: " + url);
            }
        }

        static class ProtectedInternetProxy implements InternetAccess { // SAME interface as the real thing
            private RealInternet real; // lazily created
            private static final Set<String> BANNED = Set.of("darkweb.com", "malicious.io");

            public void browseSite(String url) {
                if (BANNED.contains(url.toLowerCase())) {
                    System.out.println("  ACCESS DENIED: " + url);
                    return;
                }
                if (real == null)
                    real = new RealInternet(); // lazy initialization
                real.browseSite(url);
            }
        }

        static void demo() {
            header("2.7 PROXY");
            InternetAccess net = new ProtectedInternetProxy();
            net.browseSite("wikipedia.org");
            net.browseSite("darkweb.com");
        }
    }

    // =====================================================================
    // SECTION 3 — BEHAVIORAL PATTERNS
    // =====================================================================

    /**
     * 3.1 OBSERVER — "Publisher / Subscriber"
     * Relationship used: Dynamic Aggregation (subject holds a list of observers).
     *
     * INTENT: Define a one-to-many dependency between objects so that when one object
     * (subject/publisher) changes state, all its dependents (observers/subscribers) are
     * notified and updated automatically.
     *
     * PARTICIPANTS:
     *   - Observer  : `Observer` interface — declares the `update()` callback.
     *   - Subject   : `NewsAgency` — maintains the list, provides attach/detach, fires notifications.
     *   - ConcreteObserver : `NewsChannel` — reacts to updates from the subject.
     *
     * IMPLEMENTATION:
     *   - `NewsAgency` holds a `List<Observer>` — the subscription list (aggregation, not composition,
     *     because observers outlive the agency).
     *   - `attach()` / `detach()` manage subscriptions at runtime — observers can come and go.
     *   - `setNews()` iterates the list and calls `update()` on each observer (push model).
     *   - After CNN is detached, it no longer receives the second news item.
     *
     * PUSH vs PULL:
     *   - PUSH (this example): subject sends the changed data directly in `update(String news)`.
     *   - PULL: subject sends itself as the argument; observer queries what it needs.
     *
     * WHEN TO USE:
     *   - Event systems: Java `EventListener`, `PropertyChangeSupport`, Android `LiveData`.
     *   - MVC: Model (Subject) notifies View (Observer) of data changes.
     *   - Reactive streams: RxJava `Observable`, Project Reactor `Flux`.
     *
     * PITFALLS:
     *   - Memory leaks: failing to `detach()` keeps a reference alive (use `WeakReference` or explicit cleanup).
     *   - Unexpected update order if observers modify the subject during notification.
     */
    static class ObserverPattern {
        interface Observer {
            void update(String news);
        }

        static class NewsChannel implements Observer {
            private final String name;

            NewsChannel(String name) {
                this.name = name;
            }

            public void update(String news) {
                System.out.println("  " + name + " -> " + news);
            }
        }

        static class NewsAgency {
            private final List<Observer> observers = new ArrayList<>(); // the subscription list

            void attach(Observer o) {
                observers.add(o);
            }

            void detach(Observer o) {
                observers.remove(o);
            }

            void setNews(String news) {
                for (Observer o : observers)
                    o.update(news);
            }
        }

        static void demo() {
            header("3.1 OBSERVER");
            NewsAgency agency = new NewsAgency();
            Observer cnn = new NewsChannel("CNN");
            agency.attach(cnn);
            agency.attach(new NewsChannel("BBC"));
            agency.setNews("Market rallies");
            agency.detach(cnn);
            agency.setNews("(after CNN unsubscribes) Rain expected");
        }
    }

    /**
     * 3.2 STATE — "Contextual Behaviour"
     * Relationships used: Composition (Context holds a State) + Polymorphism.
     *
     * INTENT: Allow an object to alter its behaviour when its internal state changes.
     * The object will appear to change its class. Replaces complex if/else or switch chains
     * with a polymorphic dispatch.
     *
     * PARTICIPANTS:
     *   - Context         : `Player` — delegates behaviour to the current state object.
     *   - State interface : `State` — declares the event methods (here: `pressPlay`).
     *   - ConcreteState   : `PlayingState`, `PausedState` — implement the behaviour for their state.
     *
     * IMPLEMENTATION:
     *   - `Player` holds `private State current` — this IS the current state (composition).
     *   - `pushButton()` simply calls `current.pressPlay(this)` — NO if/else inside Player.
     *   - Each state transitions the context itself: `p.setState(new PausedState())`.
     *     The state object decides the NEXT state — transitions are distributed across states.
     *   - Adding a new state (e.g., `BufferingState`) requires only a new class, not touching `Player`.
     *
     * vs STRATEGY: Both delegate behaviour via an interface field.
     *   - Strategy: the algorithm is swapped by the CLIENT; the strategies don't know each other.
     *   - State: the STATE itself decides the transition; states know each other (or the context does).
     *
     * WHEN TO USE:
     *   - Finite state machines: vending machines, TCP connections, UI workflows.
     *   - When an object's behaviour depends on its state and must change at runtime.
     *   - To eliminate large conditional blocks gated on an "status" field.
     *
     * PITFALL: If there are many states and transitions, the state classes proliferate.
     *          Consider a State Table / transition map for complex FSMs.
     */
    static class StatePattern {
        interface State {
            void pressPlay(Player player);
        }

        static class PlayingState implements State {
            public void pressPlay(Player p) {
                System.out.println("  Pausing...");
                p.setState(new PausedState());
            }
        }

        static class PausedState implements State {
            public void pressPlay(Player p) {
                System.out.println("  Resuming...");
                p.setState(new PlayingState());
            }
        }

        static class Player {
            private State current = new PausedState(); // context holds a state

            void setState(State s) {
                this.current = s;
            }

            void pushButton() {
                current.pressPlay(this);
            } // delegate, no if/else
        }

        static void demo() {
            header("3.2 STATE");
            Player p = new Player();
            p.pushButton();
            p.pushButton();
            p.pushButton();
        }
    }

    /**
     * 3.3 COMMAND — "Action as an Object"
     * Relationship used: Realisation of the `Command` interface; Aggregation of receivers in commands.
     *
     * INTENT: Encapsulate a request as an object, thereby letting you parameterise clients
     * with different requests, queue or log requests, and support undoable operations.
     *
     * PARTICIPANTS:
     *   - Command interface  : `Command` — declares `execute()` and `undo()`.
     *   - ConcreteCommand    : `LightOnCommand` — binds a receiver to an action.
     *   - Receiver           : `Light` — knows how to perform the operation (`turnOn`, `turnOff`).
     *   - Invoker            : `RemoteControl` — holds and fires commands; maintains history for undo.
     *   - Client             : `demo()` — assembles the commands and wires them to the invoker.
     *
     * IMPLEMENTATION:
     *   - `LightOnCommand` stores a `Light` reference (aggregation) and maps `execute()` → `turnOn()`
     *     and `undo()` → `turnOff()`.
     *   - `RemoteControl` keeps a `Deque<Command>` as a history stack.
     *   - `press()` executes AND pushes to history; `pressUndo()` pops and calls `undo()`.
     *   - The invoker (`RemoteControl`) is DECOUPLED from what the command actually does.
     *   - Modern Java: simple commands with no undo can be expressed as lambdas (`Runnable`).
     *
     * ENABLES:
     *   - Undo / redo stacks (text editors, IDEs).
     *   - Transaction queues: commands can be serialized and replayed.
     *   - Macro recording: store a sequence of commands and replay as a batch.
     *
     * WHEN TO USE:
     *   - GUI buttons/menu items, job schedulers, task queues, transactional operations.
     *   - `java.util.concurrent.Callable`, thread pool `Runnable` tasks.
     *
     * PITFALL: Command can explode into many small classes. Use lambdas for simple cases;
     *          use full Command classes only when you need undo, serialization, or history.
     */
    static class CommandPattern {
        interface Command {
            void execute();

            void undo();
        }

        static class Light { // receiver
            private final String room;

            Light(String room) {
                this.room = room;
            }

            void turnOn() {
                System.out.println("  " + room + " light ON");
            }

            void turnOff() {
                System.out.println("  " + room + " light OFF");
            }
        }

        static class LightOnCommand implements Command { // concrete command
            private final Light light;

            LightOnCommand(Light light) {
                this.light = light;
            }

            public void execute() {
                light.turnOn();
            }

            public void undo() {
                light.turnOff();
            }
        }

        static class RemoteControl { // invoker
            private final Deque<Command> history = new ArrayDeque<>();

            void press(Command c) {
                c.execute();
                history.push(c);
            }

            void pressUndo() {
                if (!history.isEmpty())
                    history.pop().undo();
            }
        }

        static void demo() {
            header("3.3 COMMAND");
            RemoteControl remote = new RemoteControl();
            remote.press(new LightOnCommand(new Light("Kitchen")));
            remote.press(new LightOnCommand(new Light("Garage")));
            System.out.println("  undo x2:");
            remote.pressUndo();
            remote.pressUndo();
        }
    }

    /**
     * 3.4 VISITOR — "Separate the Operation"
     * Mechanism: Double Dispatch (two runtime polymorphic calls resolve to the right method).
     *
     * INTENT: Represent an operation to be performed on elements of an object structure.
     * Visitor lets you define a new operation without changing the classes of the elements
     * on which it operates. Solves the Expression Problem — adding operations without modifying types.
     *
     * PARTICIPANTS:
     *   - Visitor interface  : `Visitor` — one `visit(ConcreteElement)` overload per element type.
     *   - ConcreteVisitor    : `DiscountVisitor` — implements discount logic for each element type.
     *   - Element interface  : `Element` — declares `accept(Visitor)`.
     *   - ConcreteElement    : `Book`, `Fruit` — implement `accept()` by calling `v.visit(this)`.
     *
     * HOW DOUBLE DISPATCH WORKS:
     *   1. `element.accept(visitor)` — first dispatch: resolves to `Book.accept()` or `Fruit.accept()`
     *      based on the runtime type of `element`.
     *   2. Inside `accept()`: `v.visit(this)` — second dispatch: resolves to `visit(Book b)` or
     *      `visit(Fruit f)` based on the compile-time type of `this` (which is now known to be exact).
     *   Together these two calls route to the correct combination of (element type, visitor type).
     *
     * IMPLEMENTATION:
     *   - `Book.accept(v)` calls `v.visit(this)` where `this` is a `Book` — picks `visit(Book)`.
     *   - `Fruit.accept(v)` calls `v.visit(this)` where `this` is a `Fruit` — picks `visit(Fruit)`.
     *   - To add a NEW operation (e.g., `TaxVisitor`), create a new Visitor class — no element changes.
     *   - To add a NEW element type, you must update ALL existing visitor interfaces — this is the trade-off.
     *
     * WHEN TO USE:
     *   - Compiler AST traversal (type checking, code generation, pretty printing).
     *   - Report generation over a fixed set of domain objects.
     *   - When elements rarely change but new operations are added frequently.
     *
     * PITFALL: Breaks encapsulation — visitors often need access to element internals.
     *          Adding a new element type requires updating all visitor interfaces (OCP violation for types).
     */
    static class VisitorPattern {
        interface Visitor {
            void visit(Book b);

            void visit(Fruit f);
        }

        interface Element {
            void accept(Visitor v);
        }

        static class Book implements Element {
            final double price = 50;
            final String title = "SICP";

            public void accept(Visitor v) {
                v.visit(this);
            } // dispatch #2 picks the overload
        }

        static class Fruit implements Element {
            final double pricePerKg = 40;
            final double kg = 2;

            public void accept(Visitor v) {
                v.visit(this);
            }
        }

        static class DiscountVisitor implements Visitor {
            public void visit(Book b) {
                System.out.println("  " + b.title + " discounted: " + b.price * 0.8);
            }

            public void visit(Fruit f) {
                System.out.println("  Fruit total: " + f.pricePerKg * f.kg);
            }
        }

        static void demo() {
            header("3.4 VISITOR");
            // Add a new operation without touching Book or Fruit at all.
            for (Element e : List.of(new Book(), new Fruit()))
                e.accept(new DiscountVisitor());
        }
    }

    /**
     * 3.5 MEDIATOR — "Hub Communication"
     * Relationship used: Bidirectional Plain Association (users know the mediator; mediator knows users).
     *
     * INTENT: Define an object that encapsulates how a set of objects interact. Mediator promotes
     * loose coupling by keeping objects from referring to each other explicitly, letting you vary
     * their interaction independently.
     *
     * PARTICIPANTS:
     *   - Mediator interface : `ChatMediator` — declares `sendMessage()` and `register()`.
     *   - ConcreteMediator   : `ChatRoom` — holds the list of users; routes messages.
     *   - Colleague          : `User` abstract class — knows its mediator; sends/receives through it.
     *   - ConcreteColleague  : `ChatUser` — delegates sending to `mediator.sendMessage()`.
     *
     * IMPLEMENTATION:
     *   - `User` stores a `ChatMediator mediator` field (association — passed at construction).
     *   - `ChatUser.send()` calls `mediator.sendMessage(msg, this)` instead of messaging peers directly.
     *   - `ChatRoom.sendMessage()` iterates users and calls `u.receive()` on everyone except the sender.
     *   - Users have NO references to each other — all coupling goes through the mediator (the hub).
     *   - Without Mediator: N users × (N-1) direct connections = O(N²) coupling.
     *     With Mediator: N users × 1 mediator connection = O(N) coupling.
     *
     * vs OBSERVER: Observer is one-to-many (one subject, many observers); Mediator is many-to-many
     *              (any peer can initiate communication with any other).
     * vs FACADE:   Facade shields clients from a subsystem; Mediator coordinates peers.
     *
     * WHEN TO USE:
     *   - Chat rooms, air traffic controllers, UI form components that react to each other.
     *   - `javax.swing.ButtonGroup`, Java MVC controllers, message brokers (Kafka, RabbitMQ).
     *
     * PITFALL: The mediator itself can become a God Object if too much logic is centralised in it.
     */
    static class MediatorPattern {
        interface ChatMediator {
            void sendMessage(String msg, User sender);

            void register(User u);
        }

        static abstract class User {
            protected final ChatMediator mediator;
            protected final String name;

            User(ChatMediator m, String name) {
                this.mediator = m;
                this.name = name;
            }

            abstract void send(String msg);

            abstract void receive(String msg);
        }

        static class ChatUser extends User {
            ChatUser(ChatMediator m, String name) {
                super(m, name);
            }

            void send(String msg) {
                System.out.println("  " + name + " sends: " + msg);
                mediator.sendMessage(msg, this);
            }

            void receive(String msg) {
                System.out.println("    " + name + " received: " + msg);
            }
        }

        static class ChatRoom implements ChatMediator {
            private final List<User> users = new ArrayList<>();

            public void register(User u) {
                users.add(u);
            }

            public void sendMessage(String msg, User sender) {
                for (User u : users)
                    if (u != sender)
                        u.receive(msg); // hub does the routing
            }
        }

        static void demo() {
            header("3.5 MEDIATOR");
            ChatRoom room = new ChatRoom();
            User alice = new ChatUser(room, "Alice"), bob = new ChatUser(room, "Bob"), eve = new ChatUser(room, "Eve");
            room.register(alice);
            room.register(bob);
            room.register(eve);
            alice.send("standup in 5");
        }
    }

    /**
     * 3.6 ITERATOR — "Sequential Access"
     * Relationship used: Realisation of iterator interfaces; inner class hides traversal state.
     *
     * INTENT: Provide a way to access elements of a collection sequentially without exposing
     * the underlying representation (array, list, tree, etc.).
     *
     * PARTICIPANTS:
     *   - Iterator interface : `SimpleIterator<T>` — `hasNext()` and `next()`.
     *   - ConcreteIterator   : `NameIterator` (private inner class) — tracks `index`, accesses `names[]`.
     *   - Aggregate          : `NameCollection` — provides `getIterator()` and `iterator()` (Java's built-in).
     *
     * IMPLEMENTATION:
     *   - `NameIterator` is a PRIVATE inner class: it captures `names[]` from the enclosing
     *     `NameCollection` without the collection needing to expose its internals.
     *   - `hasNext()` checks `index < names.length`; `next()` returns `names[index++]`.
     *   - The second iterator (`iterator()`) implements `java.util.Iterator` — making
     *     `NameCollection` usable with Java's enhanced for-each (`for (String n : c)`).
     *   - Two distinct iterators can traverse the same collection independently.
     *
     * WHEN TO USE:
     *   - Anytime you need uniform traversal over different collection types.
     *   - All Java `Collection` types implement `Iterable<E>` and return `Iterator<E>`.
     *   - `ResultSet` (JDBC), `Scanner` lines, `Files.lines()` are all iterator-like.
     *
     * PITFALL: `ConcurrentModificationException` — modifying a collection while iterating
     *           it with a fail-fast iterator throws. Use `Iterator.remove()` or `CopyOnWriteArrayList`.
     */
    static class IteratorPattern {
        interface SimpleIterator<T> {
            boolean hasNext();

            T next();
        }

        static class NameCollection implements Iterable<String> {
            private final String[] names = { "Alice", "Bob", "Charlie" };

            SimpleIterator<String> getIterator() {
                return new NameIterator();
            }

            private class NameIterator implements SimpleIterator<String> { // inner class hides the index
                private int index = 0;

                public boolean hasNext() {
                    return index < names.length;
                }

                public String next() {
                    return hasNext() ? names[index++] : null;
                }
            }

            // Implementing java.lang.Iterable gives you for-each for free.
            @Override
            public Iterator<String> iterator() {
                return new Iterator<>() {
                    private int i = 0;

                    public boolean hasNext() {
                        return i < names.length;
                    }

                    public String next() {
                        return names[i++];
                    }
                };
            }
        }

        static void demo() {
            header("3.6 ITERATOR");
            NameCollection c = new NameCollection();
            SimpleIterator<String> it = c.getIterator();
            while (it.hasNext())
                System.out.println("  custom: " + it.next());
            for (String n : c)
                System.out.println("  for-each: " + n);
        }
    }

    /**
     * 3.7 INTERPRETER — "Grammar Evaluator"
     * Relationship used: Composite inheritance of rules (non-terminals contain terminals recursively).
     *
     * INTENT: Given a language, define a representation for its grammar along with an interpreter
     * that uses the representation to interpret sentences in the language.
     *
     * PARTICIPANTS:
     *   - AbstractExpression : `Expression` interface — declares `interpret(context)`.
     *   - TerminalExpression  : `Terminal` — a leaf rule; checks if one word is present in context.
     *   - NonTerminalExpression: `Or`, `And` — composite rules; combine sub-expressions.
     *   - Context            : the `String` passed to `interpret()` — the sentence to evaluate.
     *
     * IMPLEMENTATION:
     *   - This is a COMPOSITE pattern applied to grammar: `Or` holds two `Expression` references
     *     and delegates `interpret()` to both, combining results with `||`.
     *   - `Terminal.interpret()` splits the context string and checks for membership.
     *   - Building a grammar tree: `new Or(new Terminal("John"), new Terminal("Robert"))` creates
     *     a rule that matches either word.
     *   - To evaluate: call `expression.interpret("John")` — the tree walks itself.
     *
     * WHEN TO USE:
     *   - Simple scripting languages, configuration DSLs, rule engines, regular expression engines.
     *   - SQL parsers, mathematical expression evaluators.
     *   - When grammar is simple and performance is not critical.
     *
     * PITFALL: For complex grammars, the class count explodes and performance degrades.
     *           Use a proper parser generator (ANTLR, JavaCC) for non-trivial grammars.
     */
    static class InterpreterPattern {
        interface Expression {
            boolean interpret(String context);
        }

        static class Terminal implements Expression { // leaf rule
            private final String data;

            Terminal(String data) {
                this.data = data;
            }

            public boolean interpret(String ctx) {
                return Arrays.asList(ctx.split(" ")).contains(data);
            }
        }

        static class Or implements Expression { // non-terminal rule
            private final Expression a, b;

            Or(Expression a, Expression b) {
                this.a = a;
                this.b = b;
            }

            public boolean interpret(String ctx) {
                return a.interpret(ctx) || b.interpret(ctx);
            }
        }

        static class And implements Expression {
            private final Expression a, b;

            And(Expression a, Expression b) {
                this.a = a;
                this.b = b;
            }

            public boolean interpret(String ctx) {
                return a.interpret(ctx) && b.interpret(ctx);
            }
        }

        static void demo() {
            header("3.7 INTERPRETER");
            Expression isMale = new Or(new Terminal("John"), new Terminal("Robert"));
            Expression married = new And(new Terminal("Julie"), new Terminal("Married"));
            System.out.println("  'John' is male?           " + isMale.interpret("John"));
            System.out.println("  'Julie Married' married?  " + married.interpret("Julie Married"));
        }
    }

    /**
     * 3.8 MEMENTO — "Undo / Snapshot"
     * Mechanism: Strict inner-class isolation (only the Originator can create/read its Memento).
     *
     * INTENT: Without violating encapsulation, capture and externalise an object's internal state
     * so that the object can be restored to this state later.
     *
     * PARTICIPANTS:
     *   - Originator : `Editor` — creates Mementos from its state; restores state from them.
     *   - Memento    : `Editor.Memento` — opaque token that stores state; private to Editor.
     *   - Caretaker  : `History` — stores and returns Mementos but NEVER reads their contents.
     *
     * IMPLEMENTATION — How encapsulation is preserved:
     *   - `Memento` is a `static final` nested class INSIDE `Editor`.
     *   - Its `state` field is `private`, and its constructor is `private` — ONLY `Editor` can
     *     construct or read a `Memento`. `History` (the caretaker) can store and return them,
     *     but cannot inspect their contents.
     *   - `Editor.save()` creates `new Memento(content)` and returns it.
     *   - `Editor.restore(m)` reads `m.state` — allowed because `Editor` is the enclosing class.
     *   - `History` uses a `Deque<Editor.Memento>` as a stack: `push()` to save, `pop()` to undo.
     *
     * UNDO FLOW in demo():
     *   write("draft one") → save → write("draft two") → save → write("bad edit")
     *   undo → "draft two", undo → "draft one".
     *
     * WHEN TO USE:
     *   - Text editor undo/redo, game save states, transaction rollback.
     *   - Anywhere you need to restore an object to a prior state.
     *
     * PITFALL: Storing many large mementos is expensive. Apply size limits or incremental
     *           (diff-based) mementos for memory efficiency.
     */
    static class MementoPattern {
        static class Editor {
            private String content = "";

            void write(String text) {
                this.content = text;
            }

            String read() {
                return content;
            }

            Memento save() {
                return new Memento(content);
            }

            void restore(Memento m) {
                this.content = m.state;
            }

            static final class Memento { // opaque token
                private final String state; // immutable

                private Memento(String state) {
                    this.state = state;
                } // only Editor can build one
            }
        }

        static class History { // caretaker: stores, never reads
            private final Deque<Editor.Memento> stack = new ArrayDeque<>();

            void push(Editor.Memento m) {
                stack.push(m);
            }

            Editor.Memento pop() {
                return stack.pop();
            }

            boolean isEmpty() {
                return stack.isEmpty();
            }
        }

        static void demo() {
            header("3.8 MEMENTO");
            Editor e = new Editor();
            History h = new History();
            e.write("draft one");
            h.push(e.save());
            e.write("draft two");
            h.push(e.save());
            e.write("bad edit");
            System.out.println("  now:  " + e.read());
            e.restore(h.pop());
            System.out.println("  undo: " + e.read());
            e.restore(h.pop());
            System.out.println("  undo: " + e.read());
        }
    }

    /**
     * 3.9 CHAIN OF RESPONSIBILITY — "Pass the Request"
     * Relationship used: Self-referencing Aggregation (handler holds a reference to its `next` handler).
     *
     * INTENT: Avoid coupling the sender of a request to its receiver by giving more than one object
     * a chance to handle the request. Chain the receiving objects and pass the request along the
     * chain until an object handles it.
     *
     * PARTICIPANTS:
     *   - Handler (abstract) : `SupportHandler` — declares `handle()` and `setNext()`; provides `passOn()`.
     *   - ConcreteHandler    : `Level1`, `Level2`, `Level3` — each handles one severity or forwards.
     *   - Client             : links the chain with `l1.setNext(new Level2()).setNext(new Level3())`
     *                         and then just calls `l1.handle(severity)`.
     *
     * IMPLEMENTATION:
     *   - `SupportHandler` holds a `protected SupportHandler next` field (self-referential aggregation).
     *   - `setNext()` stores the next handler AND returns it, enabling fluent chain building:
     *     `l1.setNext(l2).setNext(l3)` sets l2 as l1's next AND l3 as l2's next.
     *   - Each `handle()` either processes the request (`LOW` → L1) or calls `passOn()` to forward.
     *   - `passOn()` in the base class: if `next != null`, forward; else print "Unhandled" (safety net).
     *   - The sender (`demo()`) doesn't know WHICH handler will process the request.
     *
     * vs DECORATOR: Decorator always processes AND passes on; CoR either handles OR passes on.
     *
     * WHEN TO USE:
     *   - Exception handler chains, middleware pipelines (Servlet filters, Spring interceptors).
     *   - Logging level filters, approval workflows (manager → director → VP).
     *   - `java.util.logging` Logger hierarchy, Netty pipeline.
     *
     * PITFALL: No guarantee any handler will process the request — always add a terminal
     *          default handler or the `passOn()` safety net shown here.
     */
    static class ChainPattern {
        static abstract class SupportHandler {
            protected SupportHandler next; // points at its own type

            SupportHandler setNext(SupportHandler next) {
                this.next = next;
                return next;
            }

            abstract void handle(String severity);

            protected void passOn(String severity) {
                if (next != null)
                    next.handle(severity);
                else
                    System.out.println("  Unhandled: " + severity);
            }
        }

        static class Level1 extends SupportHandler {
            void handle(String s) {
                if (s.equals("LOW"))
                    System.out.println("  L1 fixed it.");
                else
                    passOn(s);
            }
        }

        static class Level2 extends SupportHandler {
            void handle(String s) {
                if (s.equals("MEDIUM"))
                    System.out.println("  L2 fixed it.");
                else
                    passOn(s);
            }
        }

        static class Level3 extends SupportHandler {
            void handle(String s) {
                if (s.equals("HIGH"))
                    System.out.println("  L3 escalated to engineering.");
                else
                    passOn(s);
            }
        }

        static void demo() {
            header("3.9 CHAIN OF RESPONSIBILITY");
            SupportHandler l1 = new Level1();
            l1.setNext(new Level2()).setNext(new Level3());
            for (String s : List.of("LOW", "MEDIUM", "HIGH", "COSMIC"))
                l1.handle(s);
        }
    }

    /**
     * 3.10 TEMPLATE METHOD — "Define the Skeleton"
     * Relationship used: Abstract Class Inheritance (subclasses fill in the blanks).
     *
     * INTENT: Define the skeleton of an algorithm in an operation, deferring some steps to
     * subclasses. Template Method lets subclasses redefine certain steps of an algorithm
     * without changing the algorithm's structure.
     *
     * PARTICIPANTS:
     *   - AbstractClass : `GameLoader` — declares the template method (`runPipeline()`) and hooks.
     *   - ConcreteClass : `MarioGame`, `RacingGame` — override the abstract/hook steps.
     *
     * IMPLEMENTATION:
     *   - `runPipeline()` is marked `final` — the algorithm sequence is LOCKED; subclasses cannot
     *     reorder initialize → loadAssets → cleanup.
     *   - `initialize()` and `cleanup()` are `protected` concrete methods — HOOKS: subclasses MAY
     *     override them (optional extension points). `RacingGame` overrides `cleanup()`.
     *   - `loadAssets()` is `abstract` — a REQUIRED hook: subclasses MUST provide their own version.
     *   - The invariant part (memory allocation, server endpoints) lives in the superclass.
     *     The variant part (asset type) lives in subclasses.
     *
     * vs STRATEGY: Template Method uses INHERITANCE to vary the algorithm steps;
     *              Strategy uses COMPOSITION (swaps the whole algorithm object).
     *   - Prefer Strategy when you need runtime switching or want to avoid inheritance.
     *   - Template Method is simpler when the skeleton is fixed and variation is minor.
     *
     * WHEN TO USE:
     *   - Frameworks: `HttpServlet.service()`, JUnit `setUp/tearDown`, Spring `JdbcTemplate`.
     *   - Build pipelines, test lifecycles, data-processing ETL stages.
     *
     * PITFALL: The Hollywood Principle — "Don't call us, we'll call you."
     *          Subclasses must not call the template method themselves to avoid infinite loops.
     */
    static class TemplateMethodPattern {
        static abstract class GameLoader {
            final void runPipeline() { // final = sequence is locked
                initialize();
                loadAssets(); // the hook subclasses fill
                cleanup();
            }

            protected void initialize() {
                System.out.println("  Allocating memory blocks...");
            }

            protected abstract void loadAssets();

            protected void cleanup() {
                System.out.println("  Opening server endpoints.");
            }
        }

        static class MarioGame extends GameLoader {
            protected void loadAssets() {
                System.out.println("  Extracting 2D sprite sheets...");
            }
        }

        static class RacingGame extends GameLoader {
            protected void loadAssets() {
                System.out.println("  Streaming 3D track meshes...");
            }

            @Override
            protected void cleanup() {
                System.out.println("  Calibrating steering input.");
            }
        }

        static void demo() {
            header("3.10 TEMPLATE METHOD");
            new MarioGame().runPipeline();
            new RacingGame().runPipeline();
        }
    }

    /**
     * 3.11 STRATEGY — "Swap the Algorithm"
     * Relationship used: Composition (Context holds a Strategy via an interface field).
     *
     * INTENT: Define a family of algorithms, encapsulate each one, and make them interchangeable.
     * Strategy lets the algorithm vary independently from clients that use it.
     *
     * PARTICIPANTS:
     *   - Strategy interface  : `PaymentStrategy` — declares `pay(double amount)`.
     *   - ConcreteStrategy    : `CardPayment`, `UpiPayment` (and the lambda `cash`) — algorithms.
     *   - Context             : `Checkout` — holds a `PaymentStrategy`; delegates to it via `confirm()`.
     *
     * IMPLEMENTATION:
     *   - `Checkout` holds `private PaymentStrategy strategy` — a swappable field.
     *   - `setStrategy()` lets the client change the algorithm at RUNTIME — no `if/else` inside Context.
     *   - `confirm(amount)` simply calls `strategy.pay(amount)` — fully decoupled from the algorithm.
     *   - In modern Java: `PaymentStrategy` is a `@FunctionalInterface`, so any lambda can be a strategy:
     *       `c.setStrategy(a -> System.out.println("Paid " + a + " in cash."));`
     *
     * vs TEMPLATE METHOD: Template Method varies steps via inheritance (static);
     *                     Strategy varies the whole algorithm via composition (dynamic, runtime-swappable).
     * vs STATE: State transitions itself; Strategy is swapped by the CLIENT.
     * vs COMMAND: Command encapsulates a request with undo/history; Strategy encapsulates an algorithm.
     *
     * WHEN TO USE:
     *   - Multiple sorting algorithms (bubble, quick, merge) selectable at runtime.
     *   - Payment gateways, compression codecs, authentication methods.
     *   - `java.util.Comparator` is the canonical Strategy in Java.
     *   - Anywhere you want to replace a conditional with polymorphism.
     *
     * PITFALL: Clients must know which strategies exist to choose one.
     *           Combine with Factory or Abstract Factory to hide strategy selection.
     */
    static class StrategyPattern {
        interface PaymentStrategy {
            void pay(double amount);
        }

        static class CardPayment implements PaymentStrategy {
            public void pay(double a) {
                System.out.println("  Paid " + a + " by card.");
            }
        }

        static class UpiPayment implements PaymentStrategy {
            public void pay(double a) {
                System.out.println("  Paid " + a + " via UPI.");
            }
        }

        static class Checkout {
            private PaymentStrategy strategy; // swappable at runtime

            void setStrategy(PaymentStrategy s) {
                this.strategy = s;
            }

            void confirm(double amount) {
                strategy.pay(amount);
            }
        }

        static void demo() {
            header("3.11 STRATEGY  (not in the original notes - add it)");
            Checkout c = new Checkout();
            c.setStrategy(new CardPayment());
            c.confirm(999.0);
            c.setStrategy(new UpiPayment());
            c.confirm(250.0);
            // In modern Java a strategy is often just a lambda:
            PaymentStrategy cash = a -> System.out.println("  Paid " + a + " in cash.");
            c.setStrategy(cash);
            c.confirm(100.0);
        }
    }

    // =====================================================================
    // SECTION 4 — SHALLOW vs DEEP COPY (the cloning trap)
    // =====================================================================
    /**
     * SHALLOW vs DEEP COPY — The Cloning Trap
     *
     * This section is a focused companion to the Prototype pattern (1.5).
     * It isolates the single most common mistake when copying objects: assuming a copy is independent
     * when it actually shares mutable state with the original.
     *
     * DEFINITIONS:
     *   - SHALLOW COPY : Creates a new object but copies field values as-is.
     *     - Primitive fields (int, double): copied by VALUE — independent. ✓
     *     - Object reference fields (Wallet wallet): copied by REFERENCE — SHARED! ✗
     *     - Result: original and clone share the same `Wallet` instance.
     *
     *   - DEEP COPY : Creates a new object AND recursively creates new instances of all mutable
     *     object fields.
     *     - The clone owns a brand-new `Wallet` with the same cash value.
     *     - Mutating the clone's wallet does NOT affect the original. ✓
     *
     * IMPLEMENTATION IN CODE:
     *   - `shallowCopy()`: `return new Person(name, this.wallet)` — passes the SAME `Wallet` ref.
     *   - `deepCopy()`: creates `new Wallet()`, copies `cash` into it, returns a Person with the new wallet.
     *   - After `shallow.wallet.cash = 0`, `original.wallet.cash` is also 0 (proof of sharing).
     *   - After `deep.wallet.cash = 0`, `original.wallet.cash` is still 50 (proof of independence).
     *
     * RULE OF THUMB:
     *   - Immutable fields (String, int, Integer): safe to shallow copy.
     *   - Mutable reference fields (List, Map, custom objects): MUST be deep copied.
     *   - Nested mutable objects: deep copy must be RECURSIVE (see Prototype — `loadout.copy()`).
     *
     * JAVA NOTES:
     *   - `Object.clone()` performs a SHALLOW copy. Always override it carefully or avoid `Cloneable`.
     *   - Serialise then deserialise (“serialization trick”) achieves deep copy but is slow.
     *   - Modern approach: write an explicit `copy()` constructor as shown here.
     */
    static class CopySemantics {
        static class Wallet {
            int cash = 50;
        }

        static class Person {
            String name;
            Wallet wallet;

            Person(String name, Wallet w) {
                this.name = name;
                this.wallet = w;
            }

            Person shallowCopy() {
                return new Person(name, this.wallet);
            } // shares the wallet

            Person deepCopy() {
                Wallet w = new Wallet();
                w.cash = this.wallet.cash;
                return new Person(name, w);
            } // owns a new wallet
        }

        static void demo() {
            header("4. SHALLOW vs DEEP COPY");
            Person original = new Person("Nis", new Wallet());

            Person shallow = original.shallowCopy();
            shallow.wallet.cash = 0;
            System.out.println("  after shallow-copy edit, original cash = " + original.wallet.cash + "  <-- drained!");

            original.wallet.cash = 50;
            Person deep = original.deepCopy();
            deep.wallet.cash = 0;
            System.out.println("  after deep-copy edit,    original cash = " + original.wallet.cash + " <-- safe");
        }
    }

    // =====================================================================
    static void header(String title) {
        System.out.println("\n" + "=".repeat(64));
        System.out.println(title);
        System.out.println("=".repeat(64));
    }

    public static void main(String[] args) {
        Relationships.demo();

        System.out.println("\n\n##################  CREATIONAL  ##################");
        SingletonPattern.demo();
        FactoryMethodPattern.demo();
        AbstractFactoryPattern.demo();
        BuilderPattern.demo();
        PrototypePattern.demo();

        System.out.println("\n\n##################  STRUCTURAL  ##################");
        AdapterPattern.demo();
        BridgePattern.demo();
        CompositePattern.demo();
        DecoratorPattern.demo();
        FlyweightPattern.demo();
        FacadePattern.demo();
        ProxyPattern.demo();

        System.out.println("\n\n##################  BEHAVIORAL  ##################");
        ObserverPattern.demo();
        StatePattern.demo();
        CommandPattern.demo();
        VisitorPattern.demo();
        MediatorPattern.demo();
        IteratorPattern.demo();
        InterpreterPattern.demo();
        MementoPattern.demo();
        ChainPattern.demo();
        TemplateMethodPattern.demo();
        StrategyPattern.demo();

        System.out.println("\n\n##################  BONUS  ##################");
        CopySemantics.demo();
    }
}