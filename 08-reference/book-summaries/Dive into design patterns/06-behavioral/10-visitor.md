# Visitor

> **Intent:** Visitor is a behavioral design pattern that lets you separate algorithms from the objects on which they operate.

---

## Problem

Imagine that your team develops an app which works with geographic information structured as one colossal graph. Each node of the graph may represent a complex entity such as a city, but also more granular things like industries, sightseeing areas, etc. The nodes are connected with others if there's a road between the real objects that they represent. Under the hood, each node type is represented by its own class, while each specific node is an object.

```
                    Exporting the graph into XML

        ┌────────┐        ┌────────────┐        ┌──────────────┐
        │  City  │────────│  Industry  │────────│ SightSeeing  │
        └────────┘        └────────────┘        └──────────────┘
             \                  |                     /
              \                 |                    /
               ▼                ▼                   ▼
                    ┌───────────────────────┐
                    │      XML export       │
                    └───────────────────────┘
```

At some point, you got a task to implement exporting the graph into XML format. At first, the job seemed pretty straightforward. You planned to add an export method to each node class and then leverage recursion to go over each node of the graph, executing the export method. The solution was simple and elegant: thanks to polymorphism, you weren't coupling the code which called the export method to concrete classes of nodes.

Unfortunately, the system architect refused to allow you to alter existing node classes. He said that the code was already in production and he didn't want to risk breaking it because of a potential bug in your changes.

> *The XML export method had to be added into all node classes, which bore the risk of breaking the whole application if any bugs slipped through along with the change.*

Besides, he questioned whether it makes sense to have the XML export code within the node classes. The primary job of these classes was to work with geodata. The XML export behavior would look alien there.

There was another reason for the refusal. It was highly likely that after this feature was implemented, someone from the marketing department would ask you to provide the ability to export into a different format, or request some other weird stuff. This would force you to change those precious and fragile classes again.

---

## Solution

The Visitor pattern suggests that you place the new behavior into a separate class called **visitor**, instead of trying to integrate it into existing classes. The original object that had to perform the behavior is now passed to one of the visitor's methods as an argument, providing the method access to all necessary data contained within the object.

Now, what if that behavior can be executed over objects of different classes? For example, in our case with XML export, the actual implementation will probably be a little bit different across various node classes. Thus, the visitor class may define not one, but a set of methods, each of which could take arguments of different types, like this:

```
  1   class ExportVisitor implements Visitor is
  2       method doForCity(City c) { ... }
  3       method doForIndustry(Industry f) { ... }
  4       method doForSightSeeing(SightSeeing ss) { ... }
  5       // ...
```

But how exactly would we call these methods, especially when dealing with the whole graph? These methods have different signatures, so we can't use polymorphism. To pick a proper visitor method that's able to process a given object, we'd need to check its class. Doesn't this sound like a nightmare?

```
  1   foreach (Node node in graph)
  2     if (node instanceof City)
  3         exportVisitor.doForCity((City) node)
  4       if (node instanceof Industry)
  5         exportVisitor.doForIndustry((Industry) node)
  6       // ...
  7   }
```

You might ask, why don't we use **method overloading**? That's when you give all methods the same name, even if they support different sets of parameters. Unfortunately, even assuming that our programming language supports it at all (as Java and C# do), it won't help us. Since the exact class of a node object is unknown in advance, the overloading mechanism won't be able to determine the correct method to execute. It'll default to the method that takes an object of the base `Node` class.

However, the Visitor pattern addresses this problem. It uses a technique called **Double Dispatch**, which helps to execute the proper method on an object without cumbersome conditionals. Instead of letting the client select a proper version of the method to call, how about we delegate this choice to objects we're passing to the visitor as an argument? Since the objects know their own classes, they'll be able to pick a proper method on the visitor less awkwardly. They "accept" a visitor and tell it what visiting method should be executed.

```
  1   // Client code
  2   foreach (Node node in graph)
  3     node.accept(exportVisitor)
  4
  5   // City
  6   class City is
  7     method accept(Visitor v) is
  8       v.doForCity(this)
  9     // ...
 10
 11   // Industry
 12   class Industry is
 13     method accept(Visitor v) is
 14       v.doForIndustry(this)
 15     // ...
```

I confess. We had to change the node classes after all. But at least the change is trivial and it lets us add further behaviors without altering the code once again.

Now, if we extract a common interface for all visitors, all existing nodes can work with any visitor you introduce into the app. If you find yourself introducing a new behavior related to nodes, all you have to do is implement a new visitor class.

---

## Real-World Analogy

> *A good insurance agent is always ready to offer different policies to various types of organizations.*

Imagine a seasoned insurance agent who's eager to get new customers. He can visit every building in a neighborhood, trying to sell insurance to everyone he meets. Depending on the type of organization that occupies the building, he can offer specialized insurance policies:

- If it's a **residential building**, he sells medical insurance.
- If it's a **bank**, he sells theft insurance.
- If it's a **coffee shop**, he sells fire and flood insurance.

---

## Structure

1. The **Visitor** interface declares a set of visiting methods that can take concrete elements of an object structure as arguments. These methods may have the same names if the program is written in a language that supports overloading, but the type of their parameters must be different.

2. Each **Concrete Visitor** implements several versions of the same behaviors, tailored for different concrete element classes.

3. The **Element** interface declares a method for "accepting" visitors. This method should have one parameter declared with the type of the visitor interface.

4. Each **Concrete Element** must implement the acceptance method. The purpose of this method is to redirect the call to the proper visitor's method corresponding to the current element class. Be aware that even if a base element class implements this method, all subclasses must still override this method in their own classes and call the appropriate method on the visitor object.

5. The **Client** usually represents a collection or some other complex object (for example, a Composite tree). Usually, clients aren't aware of all the concrete element classes because they work with objects from that collection via some abstract interface.

```
  ┌────────────┐
  │   Client   │
  └─────┬──────┘
        │
        │ works with elements via the element interface
        ▼
  ┌────────────────────────┐          ┌────────────────────────────────┐
  │   «interface»          │          │   «interface»                  │
  │   Element              │          │   Visitor                      │
  ├────────────────────────┤          ├────────────────────────────────┤
  │ + accept(v: Visitor)   │─────────▶│ + visitElementA(e: ElementA)   │
  └───────────┬────────────┘          │ + visitElementB(e: ElementB)   │
              │                       └───────────────┬────────────────┘
      ┌───────┴────────┐                              │
      │                │                    ┌─────────┴─────────┐
      ▼                ▼                    ▼                   ▼
┌──────────────┐ ┌──────────────┐  ┌──────────────────┐ ┌──────────────────┐
│  ElementA    │ │  ElementB    │  │ ConcreteVisitor1 │ │ ConcreteVisitor2 │
├──────────────┤ ├──────────────┤  ├──────────────────┤ ├──────────────────┤
│ accept(v) is │ │ accept(v) is │  │ visitElementA()  │ │ visitElementA()  │
│  v.visit-    │ │  v.visit-    │  │ visitElementB()  │ │ visitElementB()  │
│  ElementA(   │ │  ElementB(   │  └──────────────────┘ └──────────────────┘
│    this)     │ │    this)     │
│ featureA()   │ │ featureB()   │
└──────────────┘ └──────────────┘
```

---

## Pseudocode

In this example, the Visitor pattern adds XML export support to the class hierarchy of geometric shapes.

> *Exporting various types of objects into XML format via a visitor object.*

```
1    // The element interface declares an `accept` method that takes
2    // the base visitor interface as an argument.
3    interface Shape is
4      method move(x, y)
5      method draw()
6      method accept(v: Visitor)
7
8    // Each concrete element class must implement the `accept`
 9   // method in such a way that it calls the visitor's method that
10   // corresponds to the element's class.
11   class Dot implements Shape is
12     // ...
13
14     // Note that we're calling `visitDot`, which matches the
15     // current class name. This way we let the visitor know the
16     // class of the element it works with.
17     method accept(v: Visitor) is
18      v.visitDot(this)
19
20   class Circle implements Shape is
21     // ...
22     method accept(v: Visitor) is
23       v.visitCircle(this)
24
25   class Rectangle implements Shape is
26     // ...
27     method accept(v: Visitor) is
28      v.visitRectangle(this)
29
30   class CompoundShape implements Shape is
31     // ...
32     method accept(v: Visitor) is
33       v.visitCompoundShape(this)
34
35
36   // The Visitor interface declares a set of visiting methods that
37   // correspond to element classes. The signature of a visiting
38   // method lets the visitor identify the exact class of the
39   // element that it's dealing with.
40   interface Visitor is
41     method visitDot(d: Dot)
42     method visitCircle(c: Circle)
43     method visitRectangle(r: Rectangle)
44     method visitCompoundShape(cs: CompoundShape)
45
46   // Concrete visitors implement several versions of the same
47   // algorithm, which can work with all concrete element classes.
48   //
49   // You can experience the biggest benefit of the Visitor pattern
50   // when using it with a complex object structure such as a
51   // Composite tree. In this case, it might be helpful to store
52   // some intermediate state of the algorithm while executing the
53   // visitor's methods over various objects of the structure.
54   class XMLExportVisitor implements Visitor is
55     method visitDot(d: Dot) is
56       // Export the dot's ID and center coordinates.
57
58     method visitCircle(c: Circle) is
59       // Export the circle's ID, center coordinates and
60       // radius.
61
62     method visitRectangle(r: Rectangle) is
63       // Export the rectangle's ID, left-top coordinates,
64       // width and height.
65
66     method visitCompoundShape(cs: CompoundShape) is
67       // Export the shape's ID as well as the list of its
68       // children's IDs.
69
70
71   // The client code can run visitor operations over any set of
72   // elements without figuring out their concrete classes. The
73   // accept operation directs a call to the appropriate operation
74   // in the visitor object.
75   class Application is
76     field allShapes: array of Shapes
77
78     method export() is
79      exportVisitor = new XMLExportVisitor()
80
81       foreach (shape in allShapes) do
82         shape.accept(exportVisitor)
```

> If you wonder why we need the `accept` method in this example, the author's article *Visitor and Double Dispatch* addresses this question in detail.

---

## Java Implementation

A complete, compilable translation of the pseudocode above (`VisitorDemo.java`).

```java
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

// ─── Element interface ────────────────────────────────────────────────
interface Shape {
    void move(int x, int y);
    String draw();
    String accept(Visitor visitor);      // the double-dispatch hook
}

// ─── Concrete elements ────────────────────────────────────────────────
class Dot implements Shape {
    protected final int id;
    protected int x, y;

    Dot(int id, int x, int y) { this.id = id; this.x = x; this.y = y; }

    int getId() { return id; }
    int getX()  { return x; }
    int getY()  { return y; }

    @Override public void move(int dx, int dy) { x += dx; y += dy; }
    @Override public String draw()             { return "dot"; }

    // Calls visitDot — matching THIS class. That's how the visitor
    // learns the element's concrete type.
    @Override public String accept(Visitor visitor) { return visitor.visitDot(this); }
}

class Circle extends Dot {
    private final int radius;

    Circle(int id, int x, int y, int radius) {
        super(id, x, y);
        this.radius = radius;
    }

    int getRadius() { return radius; }

    @Override public String draw() { return "circle"; }

    // Overriding accept is MANDATORY. Inherit Dot's and every circle
    // silently exports as a dot.
    @Override public String accept(Visitor visitor) { return visitor.visitCircle(this); }
}

class Rectangle implements Shape {
    private final int id;
    private int x, y;
    private final int width, height;

    Rectangle(int id, int x, int y, int width, int height) {
        this.id = id; this.x = x; this.y = y;
        this.width = width; this.height = height;
    }

    int getId()     { return id; }
    int getX()      { return x; }
    int getY()      { return y; }
    int getWidth()  { return width; }
    int getHeight() { return height; }

    @Override public void move(int dx, int dy) { x += dx; y += dy; }
    @Override public String draw()             { return "rectangle"; }
    @Override public String accept(Visitor visitor) { return visitor.visitRectangle(this); }
}

class CompoundShape implements Shape {
    private final int id;
    private final List<Shape> children = new ArrayList<>();

    CompoundShape(int id) { this.id = id; }

    int getId()               { return id; }
    List<Shape> getChildren() { return children; }
    void add(Shape shape)     { children.add(shape); }

    @Override public void move(int dx, int dy) { children.forEach(c -> c.move(dx, dy)); }
    @Override public String draw()             { return "compound"; }
    @Override public String accept(Visitor visitor) { return visitor.visitCompoundShape(this); }
}

// ─── Visitor interface: one method per element class ──────────────────
interface Visitor {
    String visitDot(Dot dot);
    String visitCircle(Circle circle);
    String visitRectangle(Rectangle rectangle);
    String visitCompoundShape(CompoundShape compound);
}

// ─── Concrete visitor #1: XML export ──────────────────────────────────
class XMLExportVisitor implements Visitor {

    String export(Shape... shapes) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\"?>\n");
        for (Shape shape : shapes) {
            sb.append(shape.accept(this));       // dispatch happens here
        }
        return sb.toString();
    }

    @Override
    public String visitDot(Dot dot) {
        return "<dot>\n  <id>" + dot.getId() + "</id>\n"
             + "  <x>" + dot.getX() + "</x>\n"
             + "  <y>" + dot.getY() + "</y>\n</dot>\n";
    }

    @Override
    public String visitCircle(Circle circle) {
        return "<circle>\n  <id>" + circle.getId() + "</id>\n"
             + "  <x>" + circle.getX() + "</x>\n"
             + "  <y>" + circle.getY() + "</y>\n"
             + "  <radius>" + circle.getRadius() + "</radius>\n</circle>\n";
    }

    @Override
    public String visitRectangle(Rectangle r) {
        return "<rectangle>\n  <id>" + r.getId() + "</id>\n"
             + "  <x>" + r.getX() + "</x>\n"
             + "  <y>" + r.getY() + "</y>\n"
             + "  <width>" + r.getWidth() + "</width>\n"
             + "  <height>" + r.getHeight() + "</height>\n</rectangle>\n";
    }

    @Override
    public String visitCompoundShape(CompoundShape compound) {
        StringBuilder sb = new StringBuilder("<compound_graphic>\n  <id>"
                + compound.getId() + "</id>\n");
        for (Shape child : compound.getChildren()) {
            // Recurse: the visitor drives the Composite traversal.
            sb.append(child.accept(this).indent(2));
        }
        return sb.append("</compound_graphic>\n").toString();
    }
}

// ─── Concrete visitor #2: added WITHOUT touching any Shape class ──────
class AreaVisitor implements Visitor {
    @Override public String visitDot(Dot dot)             { return "0.00"; }

    @Override
    public String visitCircle(Circle circle) {
        return String.format("%.2f", Math.PI * circle.getRadius() * circle.getRadius());
    }

    @Override
    public String visitRectangle(Rectangle r) {
        return String.format("%.2f", (double) r.getWidth() * r.getHeight());
    }

    @Override
    public String visitCompoundShape(CompoundShape compound) {
        double total = compound.getChildren().stream()
                .mapToDouble(child -> Double.parseDouble(child.accept(this)))
                .sum();
        return String.format("%.2f", total);
    }
}

// ─── Client ───────────────────────────────────────────────────────────
public class VisitorDemo {
    public static void main(String[] args) {
        List<Shape> allShapes = new ArrayList<>();
        allShapes.add(new Dot(1, 10, 55));
        allShapes.add(new Circle(2, 23, 15, 10));
        allShapes.add(new Rectangle(3, 10, 17, 20, 30));

        CompoundShape compound = new CompoundShape(4);
        compound.add(new Dot(5, 30, 45));
        compound.add(new Circle(6, 40, 18, 20));
        allShapes.add(compound);

        System.out.println(new XMLExportVisitor()
                .export(allShapes.toArray(new Shape[0])));

        Visitor area = new AreaVisitor();
        System.out.println("Areas (a second visitor, zero changes to Shape):");
        for (Shape shape : allShapes) {
            System.out.println("  " + shape.draw() + " -> " + shape.accept(area));
        }
    }
}
```

**Output**

```
<?xml version="1.0"?>
<dot>
  <id>1</id>
  <x>10</x>
  <y>55</y>
</dot>
<circle>
  <id>2</id>
  <x>23</x>
  <y>15</y>
  <radius>10</radius>
</circle>
<rectangle>
  <id>3</id>
  <x>10</x>
  <y>17</y>
  <width>20</width>
  <height>30</height>
</rectangle>
<compound_graphic>
  <id>4</id>
  <dot>
    <id>5</id>
    <x>30</x>
    <y>45</y>
  </dot>
  <circle>
    <id>6</id>
    <x>40</x>
    <y>18</y>
    <radius>20</radius>
  </circle>
</compound_graphic>

Areas (a second visitor, zero changes to Shape):
  dot -> 0.00
  circle -> 314.16
  rectangle -> 600.00
  compound -> 1256.64
```

`AreaVisitor` is an entirely new operation over the whole hierarchy, and not one line of `Dot`,
`Circle`, `Rectangle`, or `CompoundShape` changed to support it.

### Why `accept` exists: double dispatch

This is the question the book flags, and it's the whole pattern. Java dispatches on the **runtime**
type of the receiver but only the **compile-time** type of the arguments. So this does not work:

```java
class BrokenVisitor {
    String visit(Dot d)       { return "dot"; }
    String visit(Circle c)    { return "circle"; }
    String visit(Shape s)     { return "??? unknown"; }
}

Shape shape = new Circle(1, 0, 0, 5);
new BrokenVisitor().visit(shape);   // -> "??? unknown"
```

The variable is declared `Shape`, so the compiler picks `visit(Shape)` at compile time. Overload
resolution is static.

`accept` fixes it with two virtual calls in a row:

1. `shape.accept(visitor)` — dispatches on the shape's **runtime** type, landing in `Circle.accept`.
2. Inside `Circle.accept`, `this` is statically known to be `Circle`, so `visitor.visitCircle(this)`
   picks the right overload — and dispatches on the visitor's runtime type.

Two dispatches, so the correct method is chosen from *both* type hierarchies. Hence "double
dispatch."

The `instanceof` alternative works but centralises a growing chain that must be edited for every new
shape:

```java
if (shape instanceof Circle c)         { /* ... */ }
else if (shape instanceof Rectangle r) { /* ... */ }
else if (shape instanceof Dot d)       { /* ... */ }
```

### The trade-off: which axis is stable?

Visitor makes exactly one direction cheap:

| Change | Visitor | `instanceof` / methods-on-elements |
|---|---|---|
| add an **operation** | one new visitor class, zero element edits | edit every element class |
| add an **element type** | edit the `Visitor` interface **and every existing visitor** | one new class |

So use Visitor when the element hierarchy is **stable** and operations keep arriving — compilers
(AST nodes fixed; type-check, optimise, generate code keep being added) are the canonical case.
Avoid it when new element types appear regularly.

Two more costs:
- **Encapsulation leaks.** `AreaVisitor` needs `getRadius()`, `getWidth()`, `getHeight()`. Elements
  must expose enough state for every visitor, which pushes their internals public.
- **`accept` is easy to forget on a subclass.** `Circle extends Dot` above *must* override `accept`;
  inherit it and circles silently export as dots. This is a genuinely nasty bug class.

### The modern Java alternative: sealed types + pattern matching

Java 21's sealed interfaces and record patterns give you exhaustive dispatch without `accept` — the
compiler checks you handled every case, and adding a variant produces a compile error in every
switch, which is exactly the safety Visitor provides:

```java
sealed interface Shape permits Dot, Circle, Rectangle, CompoundShape {}

record Dot(int id, int x, int y) implements Shape {}
record Circle(int id, int x, int y, int radius) implements Shape {}
record Rectangle(int id, int x, int y, int width, int height) implements Shape {}
record CompoundShape(int id, List<Shape> children) implements Shape {}

static double area(Shape shape) {
    return switch (shape) {                       // no default needed — exhaustive
        case Dot d                        -> 0;
        case Circle c                     -> Math.PI * c.radius() * c.radius();
        case Rectangle r                  -> (double) r.width() * r.height();
        case CompoundShape cs             -> cs.children().stream()
                                                .mapToDouble(VisitorDemo::area).sum();
    };
}
```

For new Java code over a closed hierarchy, prefer this. Understand Visitor anyway — you will meet it
constantly in older codebases, in generated parser code, and in libraries targeting older language
levels.

### Where this appears in the JDK and frameworks

- `java.nio.file.FileVisitor` + `Files.walkFileTree` — the JDK's clearest Visitor
- `javax.lang.model.element.ElementVisitor` and `TypeVisitor` — annotation processing
- `javax.lang.model.util.SimpleElementVisitor`, and `com.sun.source.tree.TreeVisitor` (javac's AST)
- ANTLR's generated `BaseVisitor` classes; ASM's `ClassVisitor`/`MethodVisitor` for bytecode
- `javax.faces.component.visit.VisitCallback`; Jackson's `JsonNode` visitors
- Compilers and static analysers generally — the pattern's natural home

---

## Applicability

### ▸ Use the Visitor when you need to perform an operation on all elements of a complex object structure (for example, an object tree).

The Visitor pattern lets you execute an operation over a set of objects with different classes by having a visitor object implement several variants of the same operation, which correspond to all target classes.

### ▸ Use the Visitor to clean up the business logic of auxiliary behaviors.

The pattern lets you make the primary classes of your app more focused on their main jobs by extracting all other behaviors into a set of visitor classes.

### ▸ Use the pattern when a behavior makes sense only in some classes of a class hierarchy, but not in others.

You can extract this behavior into a separate visitor class and implement only those visiting methods that accept objects of relevant classes, leaving the rest empty.

---

## How to Implement

1. Declare the visitor interface with a set of "visiting" methods, one per each concrete element class that exists in the program.

2. Declare the element interface. If you're working with an existing element class hierarchy, add the abstract "acceptance" method to the base class of the hierarchy. This method should accept a visitor object as an argument.

3. Implement the acceptance methods in all concrete element classes. These methods must simply redirect the call to a visiting method on the incoming visitor object which matches the class of the current element.

4. The element classes should only work with visitors via the visitor interface. Visitors, however, must be aware of all concrete element classes, referenced as parameter types of the visiting methods.

5. For each behavior that can't be implemented inside the element hierarchy, create a new concrete visitor class and implement all of the visiting methods.

   You might encounter a situation where the visitor will need access to some private members of the element class. In this case, you can either make these fields or methods public, violating the element's encapsulation, or nest the visitor class in the element class. The latter is only possible if you're lucky to work with a programming language that supports nested classes.

6. The client must create visitor objects and pass them into elements via "acceptance" methods.

---

## Pros and Cons

**✅ Pros**

- *Open/Closed Principle.* You can introduce a new behavior that can work with objects of different classes without changing these classes.
- *Single Responsibility Principle.* You can move multiple versions of the same behavior into the same class.
- A visitor object can accumulate some useful information while working with various objects. This might be handy when you want to traverse some complex object structure, such as an object tree, and apply the visitor to each object of this structure.

**❌ Cons**

- You need to update all visitors each time a class gets added to or removed from the element hierarchy.
- Visitors might lack the necessary access to the private fields and methods of the elements that they're supposed to work with.

---

## Relations with Other Patterns

- You can treat **Visitor** as a powerful version of the **Command** pattern. Its objects can execute operations over various objects of different classes.

- You can use **Visitor** to execute an operation over an entire **Composite** tree.

- You can use **Visitor** along with **Iterator** to traverse a complex data structure and execute some operation over its elements, even if they all have different classes.
