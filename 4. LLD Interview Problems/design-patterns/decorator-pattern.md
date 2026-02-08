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

```python
from abc import ABC, abstractmethod

# 1. Component Interface
class Pizza(ABC):
    @abstractmethod
    def get_desc(self): pass
    @abstractmethod
    def get_cost(self): pass

# 2. Concrete Component (Base)
class Margherita(Pizza):
    def get_desc(self): return "Margherita"
    def get_cost(self): return 100

class VegDelight(Pizza):
    def get_desc(self): return "Veg Delight"
    def get_cost(self): return 150

# 3. Decorator (Base Wrapper)
class PizzaDecorator(Pizza):
    def __init__(self, pizza: Pizza):
        self.pizza = pizza
    
    def get_desc(self): return self.pizza.get_desc()
    def get_cost(self): return self.pizza.get_cost()

# 4. Concrete Decorators (Toppings)
class ExtraCheese(PizzaDecorator):
    def get_desc(self): return self.pizza.get_desc() + ", Extra Cheese"
    def get_cost(self): return self.pizza.get_cost() + 50

class Olives(PizzaDecorator):
    def get_desc(self): return self.pizza.get_desc() + ", Olives"
    def get_cost(self): return self.pizza.get_cost() + 20

# Client
if __name__ == "__main__":
    my_pizza = Margherita()                    # Cost: 100
    my_pizza = ExtraCheese(my_pizza)           # Cost: 150
    my_pizza = Olives(my_pizza)                # Cost: 170
    
    print(f"{my_pizza.get_desc()} = ${my_pizza.get_cost()}")
    # Output: Margherita, Extra Cheese, Olives = $170
```

## Why not Inheritance?
Inheritance is static. You'd need `MargheritaWithCheese`, `MargheritaWithOlives`, `MargheritaWithCheeseAndOlives`... ($2^N$ classes).
Decorator is dynamic composition.
