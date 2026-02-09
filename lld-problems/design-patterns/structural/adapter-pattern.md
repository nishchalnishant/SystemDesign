# Adapter Pattern

> **Type**: Structural  
> **Purpose**: Allows objects with incompatible interfaces to collaborate. Acts as a wrapper/translator.

## Real-World Analogy
Card Reader adapter. Reads MicroSD card (Adaptee) and fits into USB port (Target) of laptop.

## Problem Statement
You have an existing `CheckoutService` that expects a `PaymentGateway` interface (`pay(amount)`).  
You want to integrate a 3rd party `RazorpayAPI` which has a different method signature (`make_payment(invoice_id, amount)`).

## Implementation

```java
// 1. Target Interface (Expected by Client)
interface PaymentGateway {
    void pay(String orderId, double amount);
}

// 2. Existing valid implementation
class PayUGateway implements PaymentGateway {
    public void pay(String orderId, double amount) {
        System.out.println("Paid " + amount + " using PayU for Order " + orderId);
    }
}

// 3. Adaptee (Third-party / Legacy code)
class RazorpayAPI {
    public void makePayment(String invoiceId, double amount) {
        System.out.println("Paid " + amount + " using Razorpay for Invoice " + invoiceId);
    }
}

// 4. Adapter (The Wrapper)
class RazorpayAdapter implements PaymentGateway {
    private RazorpayAPI api;
    
    public RazorpayAdapter() {
        this.api = new RazorpayAPI();
    }
    
    public void pay(String orderId, double amount) {
        // Translate logic: 'orderId' -> 'invoiceId'
        api.makePayment(orderId, amount);
    }
}

// Client
public class Main {
    public static void processPayment(PaymentGateway gateway) {
        gateway.pay("ORD-123", 500);
    }
    
    public static void main(String[] args) {
        processPayment(new PayUGateway());        // Old
        processPayment(new RazorpayAdapter());    // New (via Adapter)
    }
}
```

## When to use?
*   Integrating existing classes/libraries that don't match your interface.
*   Legacy code migration.
