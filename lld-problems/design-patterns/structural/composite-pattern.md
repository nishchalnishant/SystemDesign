# Composite Pattern

* **Composite Pattern** is a structural design pattern that allows you to **compose objects into tree structures** to represent part-whole hierarchies.&#x20;
* It lets clients treat individual objects and compositions of objects uniformly.
* **Problem It Solves**
  * The Composite Pattern solves the problem of treating **individual objects** and **groups of objects** in the same way. The main problem arises when:
    * You want to work with a hierarchy of objects.
    * You want the client code to be **agnostic to whether it's dealing with a single object or a collection** of them.
* #### Understanding the Problem
  * Consider you are building the checkout service of an e-commerce application, and you take the following approach as shown in the code below.

```python
# Represents a single product
class Product:
    def __init__(self, name, price):
        self.name = name
        self.price = price

    def getPrice(self):
        return self.price

    def display(self, indent):
        print(f"{indent}Product: {self.name} – ₹{self.price}")


# Represents a bundle of products
class ProductBundle:
    def __init__(self, bundleName):
        self.bundleName = bundleName
        self.products = []

    def addProduct(self, product):
        self.products.append(product)

    def getPrice(self):
        return sum(product.getPrice() for product in self.products)

    def display(self, indent):
        print(f"{indent}Bundle: {self.bundleName}")
        for product in self.products:
            product.display(indent + "  ")


# Main logic
if __name__ == "__main__":
    # Individual Items
    book = Product("Book", 500)
    headphones = Product("Headphones", 1500)
    charger = Product("Charger", 800)
    pen = Product("Pen", 20)
    notebook = Product("Notebook", 60)

    # Bundle: iPhone Combo
    iphoneCombo = ProductBundle("iPhone Combo Pack")
    iphoneCombo.addProduct(headphones)
    iphoneCombo.addProduct(charger)

    # Bundle: School Kit
    schoolKit = ProductBundle("School Kit")
    schoolKit.addProduct(pen)
    schoolKit.addProduct(notebook)

    # Add to cart logic
    cart = [book, iphoneCombo, schoolKit]

    # Display Cart
    total = 0
    print("Cart Details:\n")

    for item in cart:
        if isinstance(item, Product):
            item.display("  ")
            total += item.getPrice()
        elif isinstance(item, ProductBundle):
            item.display("  ")
            total += item.getPrice()

    print(f"\nTotal Price: ₹{total}")


```

* **Working of Code**
  * `Product` class represents a simple item with `name` and `price`.
  * `ProductBundle` class represents a group of products bundled together.
  * Both classes have methods to display and return their prices.
  * In `main()`, individual products and bundles are created and added to the cart.
  * The cart is a `List<Object>` that holds both products and bundles.
  * During checkout, the code checks each item's type using `instanceof`.
  * Based on the type, it casts the object and calls its respective methods.
  * Finally, it displays all items and calculates the total price.
* **Problem in above code**
  * code lacks the structure to treat individual and group items uniformly
  * In the current implementation, **individual products** (`Product`) and **product bundles** (`ProductBundle`) are completely separate types with no shared interface or superclass.&#x20;
  * This means we cannot write code that treats both uniformly and the logic always has to check which type we're working with.
  *   Other than these, there are some other problems as well:

      * `instanceof` is used repeatedly, breaking polymorphism.
      * Cart uses `List<Object>`, which is unsafe and violates abstraction.
      * ProductBundle cannot contain another ProductBundle (no recursive structure).
      * Display and price logic are duplicated instead of unified.


* Refactored Code Using Composite Pattern
  * Let's refactor the code using the Composite Pattern. The idea is to create a common interface `CartItem` for both `Product` and `ProductBundle`, allowing us to treat them uniformly.

```python
from abc import ABC, abstractmethod

# Interface for items that can be added to the cart
class CartItem(ABC):
    @abstractmethod
    def getPrice(self):
        pass

    @abstractmethod
    def display(self, indent):
        pass

# Product class implementing CartItem
class Product(CartItem):
    def __init__(self, name, price):
        self.name = name
        self.price = price

    def getPrice(self):
        return self.price

    def display(self, indent):
        print(f"{indent}Product: {self.name} – ₹{self.price}")

# ProductBundle class implementing CartItem
class ProductBundle(CartItem):
    def __init__(self, bundleName):
        self.bundleName = bundleName
        self.items = []

    def addItem(self, item):
        self.items.append(item)

    def getPrice(self):
        return sum(item.getPrice() for item in self.items)

    def display(self, indent):
        print(f"{indent}Bundle: {self.bundleName}")
        for item in self.items:
            item.display(indent + "  ")

# Main logic
if __name__ == "__main__":
    # Individual Products
    book = Product("Atomic Habits", 499)
    phone = Product("iPhone 15", 79999)
    earbuds = Product("AirPods", 15999)
    charger = Product("20W Charger", 1999)

    # Combo Deal
    iphoneCombo = ProductBundle("iPhone Essentials Combo")
    iphoneCombo.addItem(phone)
    iphoneCombo.addItem(earbuds)
    iphoneCombo.addItem(charger)

    # Back to School Kit
    schoolKit = ProductBundle("Back to School Kit")
    schoolKit.addItem(Product("Notebook Pack", 249))
    schoolKit.addItem(Product("Pen Set", 99))
    schoolKit.addItem(Product("Highlighter", 149))

    # Add everything to cart
    cart = [book, iphoneCombo, schoolKit]

    # Display cart
    print("Your Amazon Cart:")
    total = 0
    for item in cart:
        item.display("  ")
        total += item.getPrice()

    print(f"\nTotal: ₹{total}")


```

* **Working of Refactored Code**
  * `CartItem` interface defines the common methods for both products and bundles.
  * `Product` and `ProductBundle` classes implement the `CartItem` interface.
  * The cart now holds a list of `CartItem`, allowing us to treat both products and bundles uniformly.
  * The display and price calculation logic is simplified, as we no longer need to check types.
* **Understanding Leaf and Composite in the Composite Pattern**
  * **Leaf (Individual Object):** A Leaf is a simple, atomic object in the structure. It does not contain any child components. In our example:
    * `Product` is a Leaf.
    * It represents individual purchasable items like books, phones, pens, etc.
    * Implements `CartItem` and provides its own `getPrice()` and `display()` logic.
  * **Composite (Container of Components):** A Composite is a complex object that can hold multiple CartItem objects, including both Leaf and other Composite objects. In our example:
    * `ProductBundle` is a Composite.
    * It can contain `Products` (leaves) and even other `ProductBundles` (nested composites).
    * Implements `CartItem` and delegates actions (`getPrice()` and `display())` to its children.<br>
*   **How it Solves the Issues**

    * **Uniform Treatment via Shared Interface (CartItem):** Now, both `Product` and `ProductBundle` implement `CartItem`, so the cart can contain any of them without special handling.\
      This eliminates the need for type checking (`instanceof`).
    * **Enables Polymorphism:** All operations like `getPrice()` and `display()` are defined in the `CartItem` interface, so they can be called uniformly on both products and bundles.\
      This simplifies logic and improves code extensibility.
    * **Recursive Composition Made Easy:** Bundles can now include other bundles or products seamlessly. This supports deeply nested combos or kits which is a common real-world scenario.
    * **No Code Duplication:** The cart-handling logic like computing total and displaying items is written once and works for any `CartItem`.\
      This promotes cleaner, DRY (Don't Repeat Yourself) code.


*   #### When to Use Composite Pattern



    * **You have a hierarchical structure:** Use the composite pattern when your objects form a tree-like structure (e.g., folders inside folders, or products inside bundles).
    * **You want to treat individual and groups in the same way:** When operations on single items and collections of items should be uniform (e.g., calculating total price, displaying structure).
    * **You want to avoid client-side logic to differentiate leaf and composite:** Let polymorphism handle the differences between simple and composite objects, keeping client code clean and maintainable.


*   #### Advantages and Disadvantages



    * **Pros:**
      * **Uniformity:** Treats individual and composite objects in the same way.
        * **Extensible:** Easy to add new item types or structures.
        * **Cleaner client code:** Reduces complexity for the user of the structure.
        * **Supports OCP (Open/Closed Principle):** Add new components without modifying existing code.
    * **Cons:**
      * **Violates SRP on scale:** Components manage both hierarchy and business logic.
      * **Overkill for flat and simple structures:** Adds unnecessary complexity.
      * **Can hide important distinctions:** In regulated or sensitive systems, uniform treatment might blur critical differences between types.<br>

<figure><img src="../../../../.gitbook/assets/image (154).png" alt=""><figcaption></figcaption></figure>

<br>
