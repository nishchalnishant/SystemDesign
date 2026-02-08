# Strategy Pattern

> **Category**: Behavioral Pattern  
> **Purpose**: Define a family of algorithms, encapsulate each one, and make them interchangeable. Strategy lets the algorithm vary independently from clients that use it.

## When to Use

✅ You have multiple ways to do a specific task (e.g., sorting, compression, payment).  
✅ You want to switch algorithms at runtime.  
✅ You have a class with a massive `if-else` or `switch` statement based on algorithm type.

## Implementation

### Example: Payment Processing

Without Strategy (violates Open/Closed Principle):
```java
// Bad!
class PaymentProcessor {
    public void pay(String type, int amount) {
        if (type.equals("credit_card")) {
            // Validate card, charge...
        } else if (type.equals("paypal")) {
            // Login paypal, charge...
        }
    }
}
```

With Strategy:

```java
// 1. Context
class ShoppingCart {
    private PaymentStrategy paymentStrategy;
    
    public void setPaymentStrategy(PaymentStrategy strategy) {
        this.paymentStrategy = strategy;
    }
    
    public void checkout(int amount) {
        paymentStrategy.pay(amount);
    }
}

// 2. Strategy Interface
interface PaymentStrategy {
    void pay(int amount);
}

// 3. Concrete Strategies
class CreditCardStrategy implements PaymentStrategy {
    private String cardNumber;
    
    public CreditCardStrategy(String cardNumber) {
        this.cardNumber = cardNumber;
    }
    
    @Override
    public void pay(int amount) {
        System.out.println("Paid " + amount + " using Credit Card " + cardNumber);
    }
}

class PayPalStrategy implements PaymentStrategy {
    private String email;
    
    public PayPalStrategy(String email) {
        this.email = email;
    }
    
    @Override
    public void pay(int amount) {
        System.out.println("Paid " + amount + " using PayPal " + email);
    }
}

class CryptoStrategy implements PaymentStrategy {
    @Override
    public void pay(int amount) {
        System.out.println("Paid " + amount + " using Bitcoin");
    }
}

// Usage
ShoppingCart cart = new ShoppingCart();

// Use Credit Card
cart.setPaymentStrategy(new CreditCardStrategy("1234-5678"));
cart.checkout(100);

// Switch to PayPal at runtime
cart.setPaymentStrategy(new PayPalStrategy("user@example.com"));
cart.checkout(200);
```

---

## Real-World Examples

### 1. Sorting Algorithms (Java Collections)
```java
List<String> names = Arrays.asList("John", "Alice", "Bob");

// Strategy 1: Natural Order
Collections.sort(names); 

// Strategy 2: Custom Comparator (Strategy)
Collections.sort(names, (a, b) -> b.compareTo(a)); // Reverse order
```

### 2. Navigation Apps (Route Planning)
- `RoadStrategy`: Optimize for cars
- `PublicTransportStrategy`: Optimize for trains/buses
- `WalkingStrategy`: Optimize for pedestrians

### 3. Compression
- `ZipCompressionStrategy`
- `RarCompressionStrategy`

---

## Pros & Cons

**Pros:**
✅ **Open/Closed Principle**: Add new strategies without changing Context.  
✅ **Runtime Switching**: Change behavior dynamically.  
✅ **Eliminates Conditionals**: Removes massive switch statements.

**Cons:**
❌ **Complexity**: Increases number of classes.  
❌ **Client Awareness**: Client must know which strategy to choose.

---

## Interview Tips

**Q: Difference between Strategy and State pattern?**
- **Strategy**: Creating different ways to solve a problem (External control). Client usually chooses the strategy.
- **State**: Managing state transitions (Internal control). Object changes its own state/behavior internally.

**Q: How does dependency injection relate to Strategy?**
- DI is often used to inject the specific Strategy implementation into the Context, making it easy to mock/test.
