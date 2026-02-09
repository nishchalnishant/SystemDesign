# Decorator Pattern

> **Type**: Structural  
> **Purpose**: Dynamically adds behavior to an object without altering its structure. Avoids "Class Explosion".

## Real-World Analogy
Ordering Coffee with toppings.  
`Coffee` + `Milk` + `Sugar`.  
You don't create classes like `CoffeeWithMilkAndSugar`. You wrap the object.

## Problem Statement
Pizza pricing.
Base: `Margherita`.  
Toppings: `Cheese`, `Olives`, `Mushroom`.  
Calculate total cost allowing any combination.

## Implementation

```java
// 1. Component Interface
interface Pizza {
    String getDesc();
    double getCost();
}

// 2. Concrete Component (Base)
class Margherita implements Pizza {
    public String getDesc() { return "Margherita"; }
    public double getCost() { return 100; }
}

class VegDelight implements Pizza {
    public String getDesc() { return "Veg Delight"; }
    public double getCost() { return 150; }
}

// 3. Decorator (Base Wrapper)
abstract class PizzaDecorator implements Pizza {
    protected Pizza pizza;
    
    public PizzaDecorator(Pizza pizza) {
        this.pizza = pizza;
    }
    
    public String getDesc() { return pizza.getDesc(); }
    public double getCost() { return pizza.getCost(); }
}

// 4. Concrete Decorators (Toppings)
class ExtraCheese extends PizzaDecorator {
    public ExtraCheese(Pizza pizza) { super(pizza); }
    
    public String getDesc() { return pizza.getDesc() + ", Extra Cheese"; }
    public double getCost() { return pizza.getCost() + 50; }
}

class Olives extends PizzaDecorator {
    public Olives(Pizza pizza) { super(pizza); }
    
    public String getDesc() { return pizza.getDesc() + ", Olives"; }
    public double getCost() { return pizza.getCost() + 20; }
}

// Client
public class Main {
    public static void main(String[] args) {
        Pizza myPizza = new Margherita();              // Cost: 100
        myPizza = new ExtraCheese(myPizza);            // Cost: 150
        myPizza = new Olives(myPizza);                 // Cost: 170
        
        System.out.println(myPizza.getDesc() + " = $" + myPizza.getCost());
        // Output: Margherita, Extra Cheese, Olives = $170.0
    }
}
```

## Why not Inheritance?
Inheritance is static. You'd need `MargheritaWithCheese`, `MargheritaWithOlives`, `MargheritaWithCheeseAndOlives`... ($2^N$ classes).
Decorator is dynamic composition.
