# Prototype Pattern

* used to clone existing objects instead of constructing them from scratch.&#x20;
* It enables efficient object creation, especially when the initialisation process is complex or costly.
* **Formal Definition**:
  * "Prototype pattern creates duplicate objects while keeping performance in mind.&#x20;
  * It provides a mechanism to copy the original object to a new one without making the code dependent on their classes."
* **In simpler terms**:
  * Imagine you already have a perfectly set-up object — like a well-written email template or a configured game character.&#x20;
  * Instead of building a new one every time (which can be repetitive and expensive), you just **copy the existing one** and make small adjustments.&#x20;
  * This is what the Prototype Pattern does. It allows you to create new objects by copying existing ones, saving time and resources.
* Real life analogy (photocopy machine)
  * Think of preparing ten offer letters. Instead of typing the same letter ten times, you write it once, photocopy it, and change just the name on each copy.&#x20;
  * This is how the Prototype Pattern works: start with a base object and produce modified copies with minimal changes.
*   Understanding

    * Let's understand better through a common challenge in software systems.
    * Consider an email notification system where each email instance requires extensive setup—loading templates, configurations, user settings, and formatting.&#x20;
    * Creating every email from scratch introduces redundancy and inefficiency.
    * Now imagine having a **pre-configured prototype email**, and simply cloning it for each user while modifying a few fields (like the name or content).&#x20;
    * That would save time, reduce errors, and simplify the logic.


*   #### Suitable Use Cases

    Apply the Prototype Pattern in these situations:

    * Object creation is resource-intensive or complex.
    * You require many similar objects with slight variations.
    * You want to avoid writing repetitive initialisation logic.
    * You need runtime object creation without tight class coupling.<br>

Real-life example&#x20;

```python
from abc import ABC, abstractmethod

# Target Interface
# Abstract class to represent an Email Template
class EmailTemplate(ABC):
    @abstractmethod
    def set_content(self, content):
        pass

    @abstractmethod
    def send(self, to):
        pass

# A concrete email class, hardcoded
class WelcomeEmail(EmailTemplate):
    def __init__(self):
        self.subject = "Welcome to TUF+"
        self.content = "Hi there! Thanks for joining us."

    def set_content(self, content):
        self.content = content

    def send(self, to):
        print(f"Sending to {to}: [{self.subject}] {self.content}")

if __name__ == "__main__":
    # Create a welcome email
    email1 = WelcomeEmail()
    email1.send("user1@example.com")

    # Suppose we want a similar email with slightly different content
    email2 = WelcomeEmail()
    email2.set_content("Hi there! Welcome to TUF Premium.")
    email2.send("user2@example.com")

    # Yet another variation
    email3 = WelcomeEmail()
    email3.set_content("Thanks for signing up. Let's get started!")
    email3.send("user3@example.com")

```

* **Issues in the Bad design**
  * **Tight Coupling to Concrete Class:**
    * The code uses the WelcomeEmail class directly.
    * No abstraction for cloning—client code is tightly bound to object creation logic (new WelcomeEmail() everywhere).
  * **Repetitive Instantiation:**
    * For every variation, a new instance is created using the constructor—even though most data remains the same.
    * This leads to unnecessary duplication of code and logic.
  * **Violates DRY Principle:** Repeated calls to new WelcomeEmail() and then setContent() for slight modifications break the Don't Repeat Yourself principle.
  * **No Cloning or Copy Mechanism:** There is no concept of cloning or reusing a pre-defined template and just modifying small parts.

Good code&#x20;

```python
import copy

# Defining the Prototype Interface
class EmailTemplate:
    def clone(self):
        return copy.deepcopy(self)  # Recommended to perform deep copy

    def set_content(self, content):
        pass

    def send(self, to):
        pass

# Concrete Class implementing clone logic
class WelcomeEmail(EmailTemplate):
    def __init__(self):
        self.subject = "Welcome to TUF+"
        self.content = "Hi there! Thanks for joining us."

    def set_content(self, content):
        self.content = content

    def send(self, to):
        print(f"Sending to {to}: [{self.subject}] {self.content}")

# Template Registry to store and provide clones
class EmailTemplateRegistry:
    templates = {
        "welcome": WelcomeEmail(),
        # "discount": DiscountEmail(),
        # "feature-update": FeatureUpdateEmail(),
    }

    @staticmethod
    def get_template(type_):
        return EmailTemplateRegistry.templates[type_].clone()  # clone to avoid modifying original

# Driver code
if __name__ == "__main__":
    welcome_email1 = EmailTemplateRegistry.get_template("welcome")
    welcome_email1.set_content("Hi Alice, welcome to TUF Premium!")
    welcome_email1.send("alice@example.com")

    welcome_email2 = EmailTemplateRegistry.get_template("welcome")
    welcome_email2.set_content("Hi Bob, thanks for joining!")
    welcome_email2.send("bob@example.com")

    # Reuse the base WelcomeEmail structure, just changing dynamic content

```

*   **Benefits of Good Design**

    * **Implements clone():** Allows object copying instead of recreation.
    * **Introduces Registry:** Central location (`EmailTemplateRegistry`) holds template prototypes.
    * **Decouples creation from usage:** Client code doesn't depend on how `WelcomeEmail` is constructed.
    * **Improves performance:** Avoids complex re-initialization logic by cloning pre-configured templates.


*   #### Deep Cloning VS Shallow Cloning



    * There are two types of cloning in Java: **Shallow Cloning** and **Deep Cloning**.
    * In the context of the Prototype Pattern, **Deep Cloning** is often preferred.&#x20;
    * This means that when you clone an object, not only the object itself is copied, but also all the objects it references.&#x20;
    * This ensures that changes to the cloned object do not affect the original object or any of its referenced objects.
    * Deep cloning is considered safer as well than shallow cloning because it avoids unintended side effects and ensures each clone is truly independent — especially important when templates contain complex internal structures (like nested configuration objects, lists, etc.).
*   #### Pros of Prototype Pattern

    * **Faster object creation:** No need to reinitialize objects from scratch.
    * **Reduces subclassing:** No need to create multiple subclasses for variations.
    * **Runtime object configuration:** Easy to modify a clone on the fly.
    * **Ideal for UI/UX cloning:** Useful when duplicating component trees or screen states.

    #### Cons of Prototype Pattern

    * **Deep cloning can be hard:** Implementing a true deep copy takes extra effort.
    * **Trouble with circular references:** Cloning objects that refer to each other can lead to complex issues.
    * **Potential for bugs:** If cloning isn't handled carefully, it may introduce unexpected behavior.

Class digram:

<figure><img src="../../../../.gitbook/assets/image (18).png" alt=""><figcaption></figcaption></figure>



<br>
