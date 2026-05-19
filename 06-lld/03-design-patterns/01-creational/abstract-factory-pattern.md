# Abstract Factory Pattern

## Question

Your e-commerce platform is launching in India and the US. India uses UPI and Rupee invoices; the US uses Credit Card and Dollar invoices. A `CheckoutService` needs to create a `PaymentProcessor` and an `InvoiceGenerator`. How do you write `CheckoutService` so it works correctly in both regions?

Try it before reading on.

---

## Problem Without the Pattern

The instinct is to branch on region:

```java
class CheckoutService {
    private String region;

    public void checkout(Order order) {
        PaymentProcessor processor;
        InvoiceGenerator invoice;

        if (region.equals("IN")) {
            processor = new UPIProcessor();
            invoice   = new RupeeInvoice();
        } else if (region.equals("US")) {
            processor = new CreditCardProcessor();
            invoice   = new DollarInvoice();
        }

        processor.process(order);
        invoice.generate(order);
    }
}
```

**What breaks**:
1. **OCP violation**: Adding "EU" (SEPA + Euro invoice) means editing `CheckoutService`.
2. **Mismatched families**: Nothing prevents `new UPIProcessor()` paired with `new DollarInvoice()` — a bug that compiles silently.
3. **Scattered creation**: `CheckoutService` must know every concrete class in every region.
4. **Untestable**: Can't inject a test family without modifying the service.

---

## Derive the Minimal Fix

The constraint: **group the related objects (payment + invoice) behind a single factory, and the service only talks to the factory**.

Step 1 — extract interfaces for the product types:
```java
interface PaymentProcessor { void process(Order o); }
interface InvoiceGenerator  { void generate(Order o); }
```

Step 2 — define the factory interface that creates a matched family:
```java
interface RegionFactory {
    PaymentProcessor createPaymentProcessor();
    InvoiceGenerator createInvoiceGenerator();
}
```

Step 3 — one concrete factory per region, each wiring the correct pair:
```java
class IndiaFactory implements RegionFactory {
    public PaymentProcessor createPaymentProcessor() { return new UPIProcessor(); }
    public InvoiceGenerator createInvoiceGenerator()  { return new RupeeInvoice(); }
}

class USFactory implements RegionFactory {
    public PaymentProcessor createPaymentProcessor() { return new CreditCardProcessor(); }
    public InvoiceGenerator createInvoiceGenerator()  { return new DollarInvoice(); }
}
```

Step 4 — `CheckoutService` depends only on the `RegionFactory` interface:
```java
class CheckoutService {
    private final RegionFactory factory;

    public CheckoutService(RegionFactory factory) { this.factory = factory; }

    public void checkout(Order order) {
        factory.createPaymentProcessor().process(order);
        factory.createInvoiceGenerator().generate(order);
    }
}
```

Adding EU is now: one new `EUFactory` class, zero changes to `CheckoutService`.

---

> **Purpose**: Provides an interface for creating families of related or dependent objects without specifying their concrete classes.

> **Analogy**: IKEA vs Ashley Furniture. Both make chairs, tables, and sofas — but from different families (modern vs traditional). An abstract factory gives you a matched set. You don't mix an IKEA chair with an Ashley table and hope they look right together.

---

## The Core Idea

When you need multiple related objects that must be consistent with each other (e.g., payment gateway + invoice format for a specific country), the Abstract Factory ensures you always get a matched family — never a mismatched set.

**Factory Method**: Creates ONE product. Subclass decides which.
**Abstract Factory**: Creates a FAMILY of related products. Factory object decides which family.

---

## The Problem: Hardcoded Object Creation

```java
// Bad: CheckoutService directly creates objects — tightly coupled
class CheckoutService {
    private String gatewayType;
    
    public CheckoutService(String gatewayType) {
        this.gatewayType = gatewayType;
    }
    
    public void checkOut(double amount) {
        // Hardcoded decision logic — violates OCP
        PaymentGateway paymentGateway;
        if (this.gatewayType.equals("razorpay")) {
            paymentGateway = new RazorpayGateway();
        } else {
            paymentGateway = new PayUGateway();
        }
        
        paymentGateway.processPayment(amount);
        
        // Always uses GSTInvoice — can't support US invoices
        Invoice invoice = new GSTInvoice();
        invoice.generateInvoice();
    }
}
```

**Issues:**
- Tight Coupling: `CheckoutService` directly instantiates concrete classes
- Violates OCP: Adding new gateways requires modifying `CheckoutService`
- No extensibility: Can't support different countries without rewriting

---

## The Solution: Abstract Factory Pattern

```java
// ===== Abstract Product Interfaces =====
interface PaymentGateway {
    void processPayment(double amount);
}

interface Invoice {
    void generateInvoice();
}

// ===== India Product Family =====
class RazorpayGateway implements PaymentGateway {
    @Override
    public void processPayment(double amount) {
        System.out.println("Processing INR payment via Razorpay: " + amount);
    }
}

class PayUGateway implements PaymentGateway {
    @Override
    public void processPayment(double amount) {
        System.out.println("Processing INR payment via PayU: " + amount);
    }
}

class GSTInvoice implements Invoice {
    @Override
    public void generateInvoice() {
        System.out.println("Generating GST Invoice for India.");
    }
}

// ===== US Product Family =====
class PayPalGateway implements PaymentGateway {
    @Override
    public void processPayment(double amount) {
        System.out.println("Processing USD payment via PayPal: " + amount);
    }
}

class StripeGateway implements PaymentGateway {
    @Override
    public void processPayment(double amount) {
        System.out.println("Processing USD payment via Stripe: " + amount);
    }
}

class USInvoice implements Invoice {
    @Override
    public void generateInvoice() {
        System.out.println("Generating Invoice as per US norms.");
    }
}

// ===== Abstract Factory =====
interface RegionFactory {
    PaymentGateway createPaymentGateway(String gatewayType);
    Invoice createInvoice();
}

// ===== Concrete Factories — each produces a matched family =====
class IndiaFactory implements RegionFactory {
    @Override
    public PaymentGateway createPaymentGateway(String gatewayType) {
        if (gatewayType.equals("razorpay")) return new RazorpayGateway();
        if (gatewayType.equals("payu"))     return new PayUGateway();
        throw new IllegalArgumentException("Unsupported gateway for India: " + gatewayType);
    }
    
    @Override
    public Invoice createInvoice() {
        return new GSTInvoice();  // India always gets GST invoice
    }
}

class USFactory implements RegionFactory {
    @Override
    public PaymentGateway createPaymentGateway(String gatewayType) {
        if (gatewayType.equals("paypal")) return new PayPalGateway();
        if (gatewayType.equals("stripe")) return new StripeGateway();
        throw new IllegalArgumentException("Unsupported gateway for US: " + gatewayType);
    }
    
    @Override
    public Invoice createInvoice() {
        return new USInvoice();  // US always gets US-style invoice
    }
}

// ===== CheckoutService — depends on abstraction only =====
class CheckoutService {
    private PaymentGateway paymentGateway;
    private Invoice invoice;
    
    public CheckoutService(RegionFactory factory, String gatewayType) {
        this.paymentGateway = factory.createPaymentGateway(gatewayType);
        this.invoice = factory.createInvoice();
        // Guaranteed matched pair — correct gateway + correct invoice for the region
    }
    
    public void completeOrder(double amount) {
        this.paymentGateway.processPayment(amount);
        this.invoice.generateInvoice();
    }
}

// ===== Usage =====
public class Main {
    public static void main(String[] args) {
        // India checkout with Razorpay — gets GST invoice automatically
        CheckoutService indiaCheckout = new CheckoutService(new IndiaFactory(), "razorpay");
        indiaCheckout.completeOrder(1999.0);
        
        System.out.println("---");
        
        // US checkout with PayPal — gets US invoice automatically
        CheckoutService usCheckout = new CheckoutService(new USFactory(), "paypal");
        usCheckout.completeOrder(49.99);
    }
}
```

### Class Diagram

```mermaid
classDiagram
    class PaymentGateway {
        <<interface>>
        +processPayment(double amount)
    }

    class Invoice {
        <<interface>>
        +generateInvoice()
    }

    class RegionFactory {
        <<interface>>
        +createPaymentGateway(String gatewayType) PaymentGateway
        +createInvoice() Invoice
    }

    class IndiaFactory {
        +createPaymentGateway(String gatewayType) PaymentGateway
        +createInvoice() Invoice
    }

    class USFactory {
        +createPaymentGateway(String gatewayType) PaymentGateway
        +createInvoice() Invoice
    }

    class CheckoutService {
        -PaymentGateway paymentGateway
        -Invoice invoice
        +completeOrder(double amount)
    }

    PaymentGateway <|.. RazorpayGateway
    PaymentGateway <|.. PayUGateway
    PaymentGateway <|.. PayPalGateway
    PaymentGateway <|.. StripeGateway
    Invoice <|.. GSTInvoice
    Invoice <|.. USInvoice
    RegionFactory <|.. IndiaFactory
    RegionFactory <|.. USFactory
    CheckoutService --> PaymentGateway
    CheckoutService --> Invoice
    CheckoutService ..> RegionFactory : uses
```

---

## How This Fixes the Original Issues

| Original Problem | How Abstract Factory Fixes It |
|---|---|
| Object creation mixed with business logic | Creation moved to factory classes |
| Concrete classes hardcoded in service | Service depends on `PaymentGateway` and `Invoice` interfaces |
| Adding new gateway required modifying service | Add new factory class — service untouched |
| Can't scale to new regions | Add `EUFactory`, `AUFactory` — zero changes to `CheckoutService` |

---

## When to Use in Interviews

- Multi-region or multi-platform systems: "I'd use Abstract Factory to encapsulate region-specific object creation. The service never knows if it's dealing with India or US."
- UI theme systems: "A `DarkThemeFactory` and `LightThemeFactory` both produce matched Button, Dialog, and TextField objects."
- Cross-platform rendering: "A `WindowsFactory` and `MacFactory` produce OS-native UI components — guaranteed consistent."

---

## Common Violations

| Violation | Symptom | Fix |
|---|---|---|
| Mixing product families | India gateway + US invoice by accident | Let factory enforce the family |
| Factory with too many products | One factory creates 10 unrelated types | Split into cohesive family factories |
| Client decides product family | `if (region == "IN") new RazorpayGateway()` | Move decision into factory selection |

---

## Pros and Cons

**Pros:**
- Ensures product family consistency (India gateway always paired with GST invoice)
- Follows OCP — add a new region by adding a new factory
- Follows DIP — client depends on `RegionFactory` interface, not concrete factories
- Easy to test — swap in a `MockFactory` for unit tests

**Cons:**
- Increased complexity — many classes and interfaces for simple cases
- Adding a new product type to ALL factories is expensive (e.g., adding `Receipt` means updating every factory)
- More boilerplate code upfront

---

## Interview Tips

**Q: "Factory Method vs Abstract Factory?"**
- "Factory Method creates one product — subclass decides the type. Abstract Factory creates a family of related products — factory object decides the family. Use Abstract Factory when you need consistency across multiple related objects."

**Q: "When would you choose Abstract Factory?"**
- "When multiple objects must be compatible or consistent with each other — like UI components per OS, or payment systems per region. You want to guarantee that you never mix a Windows button with a Mac dialog."
