# Adapter pattern

* concerned with the **composition of classes and objects**
* focus on how to assemble classes and objects into larger structures while keeping these structures flexible and efficient.&#x20;
* Adapter Pattern is one of the most important structural design patterns. Let's understand in depth.
* Adapter pattern
  * allows incompatible interfaces to work together by acting as a **translator** or **wrapper** around an existing class.&#x20;
  * It converts the interface of a class into another interface that a client expects.
  * acts as a bridge between the **Target** interface (expected by the client) and the **Adaptee** (an existing class with a different interface).&#x20;
  * This structural wrapping enables integration and compatibility across diverse systems.
* Real-life analogy
  * Imagine traveling from India to Europe. Your mobile charger doesn't fit into European sockets.&#x20;
  * Instead of buying a new charger, you use a plug adapter.&#x20;
  * The adapter allows your charger (with its Indian plug) to fit the European socket, enabling charging without modifying either the socket or the charger.
* Problem it solves
  * Interface incompatibility between classes.
  * Reusability of existing classes without modifying their source code.
  * Enables systems to communicate that otherwise couldn't due to differing method signatures.
* Real-life coding example: Payment gateway system
  * we have two different payment methods: PayU and Razorpay. While the PayU gateway already conforms to this interface, Razorpay follows a different structure as shown in the code below.

```python
from abc import ABC, abstractmethod

# Target Interface: 
# Standard interface expected by the CheckoutService
class PaymentGateway(ABC):
    @abstractmethod
    def pay(self, order_id, amount):
        pass

# Concrete implementation of PaymentGateway for PayU
class PayUGateway(PaymentGateway):
    def pay(self, order_id, amount):
        print(f"Paid Rs. {amount} using PayU for order: {order_id}")

# Adaptee: 
# An existing class with an incompatible interface
class RazorpayAPI:
    def make_payment(self, invoice_id, amount_in_rupees):
        print(f"Paid Rs. {amount_in_rupees} using Razorpay for invoice: {invoice_id}")

# Client Class:
# Uses PaymentGateway interface to process payments
class CheckoutService:
    # Constructor injection for dependency inversion
    def __init__(self, payment_gateway):
        self.payment_gateway = payment_gateway

    # Business logic to perform checkout
    def checkout(self, order_id, amount):
        self.payment_gateway.pay(order_id, amount)

# Driver Code
if __name__ == "__main__":
    # Using PayU payment gateway to process payment
    checkout_service = CheckoutService(PayUGateway())
    checkout_service.checkout("12", 1780)

```

* Understanding the issues
  * **`CheckoutService`** expects any payment provider to implement the **`PaymentGateway`** interface.
  * **`PayUGateway`** fits this requirement and works correctly.
  * **`RazorpayAPI`**, however, uses a different method (**`makePayment`**) and does not implement **`PaymentGateway`**.
  * Due to this mismatch, **`RazorpayAPI`** cannot be used directly with **`CheckoutService`**.
* This is a case of interface incompatibility, where similar functionalities can't work together because of structural differences.&#x20;
* To solve this without modifying existing code, we use the Adapter Pattern to make **`RazorpayAPI`** compatible with the expected interface.

```python
from abc import ABC, abstractmethod

# Target Interface: 
# Standard interface expected by the CheckoutService
class PaymentGateway(ABC):
    @abstractmethod
    def pay(self, order_id, amount):
        pass

# Concrete implementation of PaymentGateway for PayU
class PayUGateway(PaymentGateway):
    def pay(self, order_id, amount):
        print(f"Paid Rs.{amount} using PayU for order: {order_id}")

# Adaptee: 
# An existing class with an incompatible interface
class RazorpayAPI:
    def make_payment(self, invoice_id, amount_in_rupees):
        print(f"Paid Rs.{amount_in_rupees} using Razorpay for invoice: {invoice_id}")

# Adapter Class:
# Allows RazorpayAPI to be used where PaymentGateway is expected
class RazorpayAdapter(PaymentGateway):
    def __init__(self):
        self.razorpay_api = RazorpayAPI()
    
    # Translates the pay() call to RazorpayAPI's make_payment() method
    def pay(self, order_id, amount):
        self.razorpay_api.make_payment(order_id, amount)

# Client Class:
# Uses PaymentGateway interface to process payments
class CheckoutService:
    # Constructor injection for dependency inversion
    def __init__(self, payment_gateway):
        self.payment_gateway = payment_gateway

    # Business logic to perform checkout
    def checkout(self, order_id, amount):
        self.payment_gateway.pay(order_id, amount)

# Main method
if __name__ == "__main__":
    # Using razorpay payment gateway adapter to process payment
    checkout_service = CheckoutService(RazorpayAdapter())
    checkout_service.checkout("12", 1780)

```

* Here, we created an adapter class **`RazorpayAdapter`** that implements the **`PaymentGateway`** interface.&#x20;
* The adapter internally uses the **`RazorpayAPI`** class and translates the method calls from the expected interface to the actual implementation.
* This allows us to use **`RazorpayAPI`** seamlessly with the **`CheckoutService`** without modifying either class.
* When to Use Adapter Pattern
  * The Adapter Pattern is ideal in scenarios where you're trying to integrate components that were not originally designed to work together.&#x20;
  * It proves especially useful when:
    * You need to use an existing class, but its interface does not match the one your system expects.
      * You want to reuse legacy code without modifying its internal structure.
      * You're integrating third-party APIs or external services into your application.
  * In such cases, the Adapter Pattern serves as a bridge, allowing seamless compatibility without altering existing codebases.
* Advantages and Disadvantages
  *   Like any design pattern, the Adapter Pattern comes with its own set of pros and cons:

      **Pros:**

      * **Code Reusability:** Encourages the reuse of existing classes without changing their implementation.
        * **Code Extensibility:** Makes systems more flexible and adaptable to change.
        * **Minimal Changes to Client Code:** Enables integration without requiring modifications to existing client logic.
        * **Simplifies Third-party Integration:** Makes it easier to incorporate external services and APIs.

      **Cons:**

      * **Adds an Extra Layer of Abstraction:** Can introduce unnecessary complexity if not used judiciously.
      * **Overuse Can Obscure System Design:** Excessive use of adapters might make the architecture harder to understand and maintain.
* Real Product Use Cases
  * The Adapter Pattern is not just a theoretical concept — it plays a crucial role in real-world software products and systems.&#x20;
  * Many enterprise-level applications rely on this pattern to integrate with third-party tools, legacy systems, and platform-specific APIs.&#x20;
  * Below are some common and impactful use cases:
    *   **1. Payment Gateways**

        **Scenario:** Different payment providers (e.g., PayPal, Stripe, Razorpay, PayU) expose their own APIs with varying method names, parameters, and response formats.
    * **Adapter Use:** By implementing a common **`PaymentGateway`** interface and creating adapters for each provider, businesses can switch or support multiple gateways without rewriting business logic. This decouples the checkout flow from provider-specific implementations.
    * **2. Logging Frameworks**
      * **Scenario:** Enterprise applications often need to support different logging libraries like Log4j, SLF4J, or custom logging solutions.
      * **Adapter Use:** An adapter can unify the logging interface so developers can write log.debug(...), regardless of whether the underlying implementation is Log4j or java.util.logging. This makes it easier to switch or support multiple logging backends with minimal changes.
    * **3. Cloud Providers and SDKs**
      * **Scenario:** Cloud platforms like AWS, Azure, and Google Cloud offer similar functionalities (storage, compute, database) but expose them through different SDKs and APIs.
      * **Adapter Use:** Using an adapter layer, developers can abstract cloud operations behind a common interface, enabling them to change providers (e.g., from AWS S3 to Google Cloud Storage) without impacting the rest of the application. This is particularly useful for hybrid-cloud or multi-cloud strategies.<br>

<figure><img src="../../../../.gitbook/assets/image (150).png" alt=""><figcaption></figcaption></figure>

\
<br>

<br>
