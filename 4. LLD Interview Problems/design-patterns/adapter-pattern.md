# Adapter Pattern

> **Type**: Structural  
> **Purpose**: Allows objects with incompatible interfaces to collaborate. Acts as a wrapper/translator.

## Real-World Analogy
Card Reader adapter. Reads MicroSD card (Adaptee) and fits into USB port (Target) of laptop.

## Problem Statement
You have an existing `CheckoutService` that expects a `PaymentGateway` interface (`pay(amount)`).  
You want to integrate a 3rd party `RazorpayAPI` which has a different method signature (`make_payment(invoice_id, amount)`).

## Implementation

```python
from abc import ABC, abstractmethod

# 1. Target Interface (Expected by Client)
class PaymentGateway(ABC):
    @abstractmethod
    def pay(self, order_id, amount): pass

# 2. Existing valid implementation
class PayUGateway(PaymentGateway):
    def pay(self, order_id, amount):
        print(f"Paid {amount} using PayU for Order {order_id}")

# 3. Adaptee (Third-party / Legacy code)
class RazorpayAPI:
    def make_payment(self, invoice_id, amount):
        print(f"Paid {amount} using Razorpay for Invoice {invoice_id}")

# 4. Adapter (The Wrapper)
class RazorpayAdapter(PaymentGateway):
    def __init__(self):
        self.api = RazorpayAPI()

    def pay(self, order_id, amount):
        # Translate logic: 'order_id' -> 'invoice_id'
        self.api.make_payment(order_id, amount)

# Client
if __name__ == "__main__":
    # Client expects PaymentGateway
    def process_payment(gateway: PaymentGateway):
        gateway.pay("ORD-123", 500)

    process_payment(PayUGateway())       # Old
    process_payment(RazorpayAdapter())   # New (via Adapter)
```

## When to use?
*   Integrating existing classes/libraries that don't match your interface.
*   Legacy code migration.
