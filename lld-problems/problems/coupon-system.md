# Design Coupon & Discount System

> **Difficulty**: Medium  
> **Topics**: Strategy Pattern, Chain of Responsibility, Composite Pattern  
> **Similar**: Zepto, Amazon, Swiggy Discounts

## Problem Statement

Design a flexible coupon system that supports:
1.  **Discount Types**: Percentage ("10% Off"), Flat ("$50 Off"), Free Shipping.
2.  **Rules/Constraints**: Min Order Value, Specific Category, User Specific, Expiry Date.
3.  **Extensibility**: Marketing team introduces new rules (e.g., BOGO) without rewriting core logic.

## Core Design Pattern: Strategy & Composite

*   **Strategy Pattern**: Use for `Reward` calculation. `PercentageReward`, `FlatReward`.
*   **Composite Pattern**: A `Coupon` *has* a list of `Constraints`. It passes only if ALL constraints pass.

## Class Design

### 1. Context (Cart)

```python
class Item:
    def __init__(self, name, price, category):
        self.name = name
        self.price = price
        self.category = category

class Cart:
    def __init__(self):
        self.items = []
    
    def add_item(self, item):
        self.items.append(item)
        
    def get_total_price(self):
        return sum(item.price for item in self.items)
```

### 2. Constraints (Validation Logic)

```python
from abc import ABC, abstractmethod
import datetime

class Constraint(ABC):
    @abstractmethod
    def validate(self, cart: Cart) -> bool:
        pass

class MinOrderConstraint(Constraint):
    def __init__(self, min_price):
        self.min_price = min_price
        
    def validate(self, cart: Cart):
        return cart.get_total_price() >= self.min_price

class CategoryConstraint(Constraint):
    def __init__(self, required_category):
        self.required_category = required_category
        
    def validate(self, cart: Cart):
        # Check if ANY item in cart belongs to the category
        return any(item.category == self.required_category for item in cart.items)
```

### 3. Rewards (Calculation Logic)

```python
class Reward(ABC):
    @abstractmethod
    def calculate(self, cart: Cart) -> float:
        pass

class FlatReward(Reward):
    def __init__(self, amount):
        self.amount = amount
        
    def calculate(self, cart: Cart):
        return self.amount

class PercentageReward(Reward):
    def __init__(self, percentage, max_discount=float('inf')):
        self.percentage = percentage
        self.max_discount = max_discount
        
    def calculate(self, cart: Cart):
        discount = cart.get_total_price() * (self.percentage / 100)
        return min(discount, self.max_discount)
```

### 4. Coupon (Composite)

```python
class Coupon:
    def __init__(self, code, reward: Reward):
        self.code = code
        self.reward = reward
        self.constraints = []

    def add_constraint(self, constraint: Constraint):
        self.constraints.append(constraint)

    def is_valid(self, cart: Cart):
        for constraint in self.constraints:
            if not constraint.validate(cart):
                return False
        return True

    def get_discount(self, cart: Cart):
        if self.is_valid(cart):
            discount = self.reward.calculate(cart)
            return min(discount, cart.get_total_price())
        return 0.0
```

## Usage Example

```python
if __name__ == "__main__":
    cart = Cart()
    cart.add_item(Item("MacBook", 2000, "Electronics"))
    
    # Coupon: "ELECTRO10" (10% off up to $100 if cart > $500 & contains Electronics)
    coupon = Coupon("ELECTRO10", PercentageReward(10, max_discount=100))
    coupon.add_constraint(CategoryConstraint("Electronics"))
    coupon.add_constraint(MinOrderConstraint(500))
    
    print(f"Discount: ${coupon.get_discount(cart)}") # Output: 100
```

## Interview Talking Points

1.  **Why Strategy Pattern?**: Avoids `if-else` blocks for coupon types (`if type == 'PERCENT'`). Adding a new type just means adding a new `Reward` subclass.
2.  **Concurrency**: If a coupon is "First 100 users only", the `GlobalLimitConstraint` needs to interact with Redis `DECR` to prevent overselling.
3.  **Stacking Coupons**: `CouponManager` could take a list of coupons, apply one, update the effective price, and try applying the next.
