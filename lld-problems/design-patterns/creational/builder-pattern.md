# Builder Pattern

* separates the construction of a complex object from its representation
* allows you to create different types and representations of an object using the same construction process.
* Formal definition:&#x20;
  * Builder pattern builds a complex object step by step. It separates the construction of a complex object from its representation, so that the same construction process can create different representations.
* In-simpler terms:
  * Imagine you're ordering a custom burger. You choose the bun, patty, toppings, sauces, and whether you want it grilled or toasted.&#x20;
  * The chef follows your instructions step by step to build your custom burger.&#x20;
  * This is what the Builder Pattern does — it lets you construct complex objects by specifying their parts one at a time, giving you flexibility and control over the object creation process.
* Real life analogy (custom pizza order)
  * Think of ordering a pizza online. You select the crust type, size, toppings, cheese, and sauce — all step by step.&#x20;
  * The pizza shop then builds your pizza according to your selections.
  * Different customers can use the same process to get entirely different pizzas.&#x20;
  * This is the essence of the Builder Pattern: a structured, step-wise approach to creating customised complex objects.
* Understanding the problem
  * Imagine you're building a **BurgerMeal** in your application.&#x20;
  * A burger must have some mandatory components like: Bun and Patty.&#x20;
  * And it can also include **option components** like: Sides, Toppings, and Cheese.

```python
# Represents a customizable Burger Meal
class BurgerMeal:
    # Constructor trying to handle all combinations
    def __init__(self, bun, patty, sides=None, toppings=None, cheese=False):
        # Mandatory components
        self.bun = bun
        self.patty = patty

        # Optional components
        self.sides = sides
        self.toppings = toppings
        self.cheese = cheese

# Constructing the object with only required details
burger_meal = BurgerMeal("wheat", "veg", None, None, False)

```

* **Issues in Code:** This constructor approach works, but it creates multiple problems:
  * Hard to Read and Maintain:\
    The user has to remember the order of parameters and their types. It becomes difficult to read when more optional parameters are added.
  * Unnecessary null values:\
    Even if the user doesn’t want toppings or sides, they still have to pass null explicitly. This clutters the object creation code.
  * Risk of `NullPointerException`:\
    If we forget to null-check before accessing optional values inside the class, it may lead to runtime exceptions.
  * Too Many Constructor Overloads:\
    To handle various combinations (e.g., with cheese, without sides, only toppings, etc.), you’d need to create multiple overloaded constructors — which is not scalable.
  * Tight Coupling Between Parameters and Construction:\
    There is no flexibility to set values step by step. The entire object must be built in one go, which doesn't match the natural way of ordering or customising a burger.
* **Telescoping Constructor Anti-Pattern**
  * To manage optional parameters, many developers try to solve this by writing multiple overloaded constructors — each with one more optional parameter than the last.&#x20;
  * For example:<br>

```python
class BurgerMeal {
    public BurgerMeal(String bun, String patty) { ... }
    public BurgerMeal(String bun, String patty, boolean cheese) { ... }
    public BurgerMeal(String bun, String patty, boolean cheese, String side) { ... }
    public BurgerMeal(String bun, String patty, boolean cheese, String side, String drink) { ... }
}
```

* This is called Telescoping Constructor Anti-Pattern.
* But this creates a cascade of constructors that become:
  * Hard to read and write
  * Error-prone due to confusing parameter order
  * Difficult to maintain when more fields are added
  * Inflexible, as users must use parameters in a specific order
* It occurs most commonly in Java, which lacks support for optional or default parameters (unlike C++ or Python).&#x20;
* Because of this limitation, developers are forced to create multiple constructor overloads to handle different combinations of parameters.
* Clearly, this approach doesn't scale well. And this is exactly the kind of problem that the Builder Pattern is designed to solve.&#x20;
* It gives the user full control over which parts to build while keeping the construction code clean, readable, and safe.<br>
* The Solution
  * To solve the problems we saw earlier with constructors, we use the Builder Pattern.&#x20;
  *   It separates object construction from its representation, allowing us to build step-by-step while keeping the object immutable and readable.



```java
import java.util.List;
import java.util.ArrayList;

// Represents a customizable Burger Meal
public class BurgerMeal {
    // Required components
    private final String bunType;
    private final String patty;

    // Optional components
    private final boolean hasCheese;
    private final List<String> toppings;
    private final String side;
    private final String drink;

    // Private constructor to force use of Builder
    private BurgerMeal(BurgerBuilder builder) {
        this.bunType = builder.bunType;
        this.patty = builder.patty;
        this.hasCheese = builder.hasCheese;
        this.toppings = builder.toppings;
        this.side = builder.side;
        this.drink = builder.drink;
    }

    // Static nested Builder class
    public static class BurgerBuilder {
        // Required fields
        private final String bunType;
        private final String patty;

        // Optional fields (with defaults)
        private boolean hasCheese = false;
        private List<String> toppings = new ArrayList<>();
        private String side = null;
        private String drink = null;

        // Builder constructor with required fields
        public BurgerBuilder(String bunType, String patty) {
            this.bunType = bunType;
            this.patty = patty;
        }

        // Method to set cheese
        public BurgerBuilder withCheese(boolean hasCheese) {
            this.hasCheese = hasCheese;
            return this;
        }

        // Method to set toppings
        public BurgerBuilder withToppings(List<String> toppings) {
            this.toppings = toppings;
            return this;
        }

        // Method to set side
        public BurgerBuilder withSide(String side) {
            this.side = side;
            return this;
        }

        // Method to set drink
        public BurgerBuilder withDrink(String drink) {
            this.drink = drink;
            return this;
        }

        // Final build method
        public BurgerMeal build() {
            return new BurgerMeal(this);
        }
    }
}

// Usage examples:
// Creating burger with only required fields
BurgerMeal plainBurger = new BurgerMeal.BurgerBuilder("wheat", "veg").build();

// Burger with cheese only
BurgerMeal burgerWithCheese = new BurgerMeal.BurgerBuilder("wheat", "veg")
    .withCheese(true)
    .build();

// Fully loaded burger
List<String> toppings = List.of("lettuce", "onion", "jalapeno");
BurgerMeal loadedBurger = new BurgerMeal.BurgerBuilder("multigrain", "chicken")
    .withCheese(true)
    .withToppings(toppings)
    .withSide("fries")
    .withDrink("coke")
    .build();
```

* **Understanding the Code**
  * Private Constructor The constructor of `BurgerMeal` is made private so that object creation is restricted to the `Builder` only.
  * Nested Static `BurgerBuilder` Class\
    This builder class holds the same fields as `BurgerMeal`. It ensures immutability and keeps construction controlled.
  * Fluent API Style\
    Each method (like `withCheese`, `withSide`) returns the builder itself, enabling method chaining in a fluent and readable manner.
  * Selective Configuration\
    Only required fields (`bunType`, `patty`) are passed to the builder's constructor. Everything else is optional and set via `withXYZ()` methods.
  * Final Step: build()\
    Once all desired fields are set, calling `.build()` finalizes the object construction and returns the `BurgerMeal` instance.
  *
*   **Why This is Better**

    | Aspect                 | Constructor Approach             | Builder Pattern                     |
    | ---------------------- | -------------------------------- | ----------------------------------- |
    | **Object readability** | Poor (nulls, long argument list) | Excellent (fluent and expressive)   |
    | **Flexibility**        | Low (all-or-nothing setup)       | High (configure only what’s needed) |
    | **Maintainability**    | Hard to scale with more fields   | Easy to extend with more options    |
    | **Safety**             | High chance of errors with nulls | Controlled and safe instantiation   |
*   ### When to Use and When to Avoid the Builder Pattern

    **When to Use?**

    You should consider using the Builder Pattern in the following scenarios:

    * An object has multiple fields, especially when many of them are optional. Managing such objects using constructors becomes messy and error-prone.
    * Immutability is preferred - Builder lets you construct an object step by step and then make it immutable once built.
    * You want readable, maintainable object creation, especially when dealing with domain models or configuration objects. The fluent interface style improves clarity and flexibility.



    **When to Avoid?**

    The Builder Pattern can be overkill in simpler use cases. Avoid it when:

    * Your class has only 1-2 fields: Using a constructor or setter methods is simpler and more concise.
    * You don’t need object customization or immutability: If the object is small, mutable, or built only in one place, a builder adds unnecessary complexity.

    <br>
*   ### Pros and Cons of Builder Pattern

    Understanding both the advantages and limitations of the Builder Pattern helps in deciding when to use it effectively.

    **Pros**

    * Avoids constructor telescoping: You no longer need to write multiple overloaded constructors for different configurations.
    * Ensures immutability: The final object can be made immutable once built, which improves safety and thread-safety.
    * Clean, readable object creation: The fluent API makes object construction expressive and easy to follow.
    * Great for complex configurations: If your object has many optional parameters or conditional setup, the builder pattern keeps it organized.

    <br>

    **Cons**

    * Slightly tough to set up: Initial setup requires writing a separate builder class, which adds to boilerplate.
    * Overkill for small classes: If a class only has one or two fields, using a builder adds unnecessary complexity.
    * Separate builder class needed: You need to maintain a second class or static inner class just to construct the main object, increasing maintenance.

    <br>
*   ### Real World Products Using Builder Pattern

    \
    \- Amazon Cart configuration&#x20;

    *   Think about Amazon's shopping cart system. When you add an item to your cart, you're not just storing an item ID. You're building a complex object with fields like:

        * Quantity
        * Size or color (for apparel)
        * Delivery option
        * Gift wrap
        * Save for later status
        * Discounted price or offer tag

        \
        Each user may customize these options differently. Internally, such cart items are likely created using a Builder Pattern to allow step-by-step configuration while ensuring data consistency and immutability.<br>
