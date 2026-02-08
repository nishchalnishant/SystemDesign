# Abstract Factory Pattern

* provides an interface for creating **families of related or dependent objects** without specifying their concrete classes.
* **In simpler terms**:\
  You use it when you have multiple factories, each responsible for producing objects that are meant to work together.
*   **When Should You Use It?**

    Use of the Abstract Factory Pattern is appropriate in the following scenarios:

    * When multiple related objects must be created as part of a cohesive set (e.g., a payment gateway and its corresponding invoice generator).
    * When the type of objects to be instantiated depends on a specific context, such as country, theme, or platform.
    * When client code should remain independent of concrete product classes.
    * When consistency across a family of related products must be maintained (e.g., a US payment gateway paired with a US-style invoice).

    <br>
*   Real life example: Imagine building out a checkout service for our platform TUF plus



Bad design: Hardcoded Object Creation in CheckoutService

```python
from abc import ABC, abstractmethod

# Interface representing any payment gateway
class PaymentGateway(ABC):
    @abstractmethod
    def process_payment(self, amount):
        pass

# Concrete implementation: Razorpay
class RazorpayGateway(PaymentGateway):
    def process_payment(self, amount):
        print(f"Processing INR payment via Razorpay: {amount}")

# Concrete implementation: PayU
class PayUGateway(PaymentGateway):
    def process_payment(self, amount):
        print(f"Processing INR payment via PayU: {amount}")

# Interface representing invoice generation
class Invoice(ABC):
    @abstractmethod
    def generate_invoice(self):
        pass

# Concrete invoice implementation for India
class GSTInvoice(Invoice):
    def generate_invoice(self):
        print("Generating GST Invoice for India.")

# CheckoutService that directly handles object creation (bad practice)
class CheckoutService:
    def __init__(self, gateway_type):
        # Constructor accepts a string to determine which gateway to use
        self.gateway_type = gateway_type

    # Checkout process hardcodes logic for gateway and invoice creation
    def check_out(self, amount):
        # Hardcoded decision logic
        if self.gateway_type == "razorpay":
            payment_gateway = RazorpayGateway()
        else:
            payment_gateway = PayUGateway()

        # Process payment using selected gateway
        payment_gateway.process_payment(amount)

        # Always uses GSTInvoice, even though more types may exist later
        invoice = GSTInvoice()
        invoice.generate_invoice()

# Main method
if __name__ == "__main__":
    # Example: Using Razorpay
    razorpay_service = CheckoutService("razorpay")
    razorpay_service.check_out(1500.00)


```

* **Issues with this design**
  * **Tight Coupling:**\
    The **`CheckoutService`** directly creates instances of **`RazorpayGateway`**, **`PayUGateway`**, and **`GSTInvoice`**, making it dependent on specific implementations.
  * **Violation of the Open/Closed Principle:**\
    Any addition of new payment gateways or invoice types will require modifying the **`CheckoutService`** class.
  * **Lack of Extensibility:**\
    Hardcoding limits the ability to support other countries or multiple combinations of payment methods and invoice formats.

Improved design: Abstract Factory pattern for checkoutService

```python
from abc import ABC, abstractmethod

# ========== Interfaces ==========
class PaymentGateway(ABC):
    @abstractmethod
    def process_payment(self, amount):
        pass

class Invoice(ABC):
    @abstractmethod
    def generate_invoice(self):
        pass

# ========== India Implementations ==========
class RazorpayGateway(PaymentGateway):
    def process_payment(self, amount):
        print(f"Processing INR payment via Razorpay: {amount}")

class PayUGateway(PaymentGateway):
    def process_payment(self, amount):
        print(f"Processing INR payment via PayU: {amount}")

class GSTInvoice(Invoice):
    def generate_invoice(self):
        print("Generating GST Invoice for India.")

# ========== US Implementations ==========
class PayPalGateway(PaymentGateway):
    def process_payment(self, amount):
        print(f"Processing USD payment via PayPal: {amount}")

class StripeGateway(PaymentGateway):
    def process_payment(self, amount):
        print(f"Processing USD payment via Stripe: {amount}")

class USInvoice(Invoice):
    def generate_invoice(self):
        print("Generating Invoice as per US norms.")

# ========== Abstract Factory ==========
class RegionFactory(ABC):
    @abstractmethod
    def create_payment_gateway(self, gateway_type):
        pass

    @abstractmethod
    def create_invoice(self):
        pass

# ========== Concrete Factories ==========
class IndiaFactory(RegionFactory):
    def create_payment_gateway(self, gateway_type):
        if gateway_type == "razorpay":
            return RazorpayGateway()
        elif gateway_type == "payu":
            return PayUGateway()
        raise ValueError(f"Unsupported gateway for India: {gateway_type}")

    def create_invoice(self):
        return GSTInvoice()

class USFactory(RegionFactory):
    def create_payment_gateway(self, gateway_type):
        if gateway_type == "paypal":
            return PayPalGateway()
        elif gateway_type == "stripe":
            return StripeGateway()
        raise ValueError(f"Unsupported gateway for US: {gateway_type}")

    def create_invoice(self):
        return USInvoice()

# ========== Checkout Service ==========
class CheckoutService:
    def __init__(self, factory, gateway_type):
        self.payment_gateway = factory.create_payment_gateway(gateway_type)
        self.invoice = factory.create_invoice()

    def complete_order(self, amount):
        self.payment_gateway.process_payment(amount)
        self.invoice.generate_invoice()

# ========== Main Method ==========
if __name__ == "__main__":
    # Using Razorpay in India
    india_checkout = CheckoutService(IndiaFactory(), "razorpay")
    india_checkout.complete_order(1999.0)

    print("---")

    # Using PayPal in US
    us_checkout = CheckoutService(USFactory(), "paypal")
    us_checkout.complete_order(49.99)


```

*   **How This Code Fixes the Original Issues**

    * **Object creation logic was mixed with business logic:**\
      Now moved to separate factory classes like `IndiaFactory` and `USFactory`.
    * **Concrete classes like Razorpay and PayU were hardcoded in the service:**\
      Replaced with abstractions (`PaymentGateway`, `Invoice`) and created via interfaces.
    * **Adding a new gateway or invoice type required modifying CheckoutService:**\
      Now, new gateways or invoices can be added by updating/adding a new factory — no changes required in the service class.
    * **The code was difficult to maintain and scale across regions:**\
      Now easy to maintain and scale by plugging in region-specific factories (e.g., USFactory, IndiaFactory, etc.).


* **Key Benefits of this design**
  * **Scalable:** Add new countries or payment systems by simply creating new factories.
  * **Clean and Maintainable:** `CheckoutService` doesn’t care what kind of gateway or invoice it's using.
  * **Easy to Test:** Each factory can be tested independently with its own unit tests.
  * **Follows SOLID Principles:** Especially the **Open/Closed Principle** and **Dependency Inversion Principle**.



*   ### Pros and Cons

    **Pros of the Abstract Factory Pattern**

    * **Encapsulates Object Creation:** Centralizes and abstracts the instantiation logic for related objects, making client code cleaner and more focused on behavior.
    * **Promotes Consistency Across Products:** Ensures that related objects (e.g., UI components or payment modules) are used together correctly and consistently.
    * **Enhances Scalability:** Adding new product families or regions can be done by introducing new factory classes, without modifying existing logic.
    * **Supports Open/Closed Principle:** Code is open for extension (new factories/products) but closed for modification, improving long-term maintainability.
    * **Improves Code Maintainability:** Reduces tight coupling between components and specific implementations, making it easier to modify, test, and debug individual parts.
    * **Provides a Layer of Abstraction:** Abstracts away platform-specific or environment-specific details from the client, enhancing code portability.

    <br>

    **Cons of the Abstract Factory Pattern**

    * **Increased Complexity:** Adds additional layers (interfaces, factories, families of products) which might be overkill for small or simple projects.
    * **Difficult to Extend Product Families:** Adding a new product to an existing family requires updating all factory implementations.
    * **More Boilerplate Code:** Requires writing multiple classes and interfaces even for basic use cases.
    * **Reduced Flexibility in Runtime Decisions:** Factories are often chosen at compile-time, making dynamic switching at runtime more complex.

    <br>

<figure><img src="../../../../.gitbook/assets/image (16).png" alt=""><figcaption></figcaption></figure>
