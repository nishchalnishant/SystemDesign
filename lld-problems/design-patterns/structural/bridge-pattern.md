# Bridge Pattern

* **Bridge Pattern** is a structural design pattern that is used to **decouple an abstraction from its implementation** so that the two can vary independently.
*   **Problem It Solves**



    * When you have **multiple dimensions of variability**, such as different types of features (abstractions) and multiple implementations of those features, you might end up with a combinatorial explosion of subclasses if you try to use inheritance to handle all combinations.&#x20;
    *   Thus bridge pattern:

        * **Avoids tight coupling** between abstraction and implementation.
          * **Eliminates code duplication** that would occur if every combination of abstraction and implementation had its own class.
          * **Promotes composition over inheritance**, allowing more flexible code evolution.

        \
        <br>
*   **Real-Life Analogy**



    * Think of a TV remote and a TV:
      * The remote is the abstraction (interface the user interacts with).
      * The TV is the implementation (actual functionality).
    * You can have different types of remotes (basic, advanced) and different brands of TVs (Samsung, Sony). Bridge Pattern allows any remote to work with any TV without creating a separate class for each combination.



    * #### Real-Life Coding Example
      *   Assume we are building a video player that aims to model different video players (like Web, Mobile, Smart TV) each with different quality types (HD, Ultra HD, 4K).

          <br>

```python
from abc import ABC, abstractmethod

# ======= Interface for video quality =======
class PlayQuality(ABC):
    @abstractmethod
    def play(self, title):
        pass

# Each class here represents a combination of platform and quality
class WebHDPlayer(PlayQuality):
    def play(self, title):
        # Web player plays in HD
        print(f"Web Player: Playing {title} in HD")

class MobileHDPlayer(PlayQuality):
    def play(self, title):
        # Mobile player plays in HD
        print(f"Mobile Player: Playing {title} in HD")

class SmartTVUltraHDPlayer(PlayQuality):
    def play(self, title):
        # Smart TV plays in Ultra HD
        print(f"Smart TV: Playing {title} in ultra HD")

class Web4KPlayer(PlayQuality):
    def play(self, title):
        # Web player plays in 4K
        print(f"Web Player: Playing {title} in 4K")

# ============ Main logic ================
if __name__ == "__main__":
    player = WebHDPlayer()
    player.play("Interstellar")

```

*   **Understanding the Issue**

    * In the given design, platform types (like Web, Mobile, Smart TV) are tightly coupled with video quality types (like HD, Ultra HD, 4K). This results in a rigid system where every combination requires a separate class — for example, WebHDPlayer, MobileHDPlayer, SmartTVUltraHDPlayer, and so on.
    * As new platforms or quality types are introduced, the number of classes grows rapidly. Adding just one new platform or one new quality level leads to multiple new classes. If you have 5 platforms and 5 quality types, you end up with 25 distinct classes — most of which share very similar code.
    * Such tightly coupled designs are hard to test, extend, and manage over time. This is where the Bridge Pattern proves valuable — by decoupling the abstraction (platform) from its implementation (quality), it allows both to evolve independently, eliminating unnecessary class combinations.



```python
from abc import ABC, abstractmethod

# ======== Implementor Interface =========
class VideoQuality(ABC):
    @abstractmethod
    def load(self, title):
        pass

# ============ Concrete Implementors ==============
class SDQuality(VideoQuality):
    def load(self, title):
        print(f"Streaming {title} in SD Quality")

class HDQuality(VideoQuality):
    def load(self, title):
        print(f"Streaming {title} in HD Quality")

class UltraHDQuality(VideoQuality):
    def load(self, title):
        print(f"Streaming {title} in 4K Ultra HD Quality")

# ========== Abstraction ==========
class VideoPlayer(ABC):
    def __init__(self, quality: VideoQuality):
        self.quality = quality

    @abstractmethod
    def play(self, title):
        pass

# =========== Refined Abstractions ==============
class WebPlayer(VideoPlayer):
    def play(self, title):
        print("Web Platform:")
        self.quality.load(title)

class MobilePlayer(VideoPlayer):
    def play(self, title):
        print("Mobile Platform:")
        self.quality.load(title)

# Client Code
if __name__ == "__main__":
    # Playing on Web with HD Quality
    player1 = WebPlayer(HDQuality())
    player1.play("Interstellar")

    # Playing on Mobile with Ultra HD Quality
    player2 = MobilePlayer(UltraHDQuality())
    player2.play("Inception")

```



* **How Bridge Pattern Solves the Issue**
  * **Separation of Concerns:** `VideoPlayer` (abstraction) focuses on the **platform-specific behavior**, while `VideoQuality` (implementor) handles **quality-specific streaming logic**.
  * **Flexible Combinations:** You can mix and match any platform with any quality at runtime without creating new classes.
  * **Easier to Extend:** Adding a new platform or a new quality only requires one new class, not multiple combinations:
    * Add SmartTVPlayer → works with all existing qualities.
    * Add FullHDQuality → works with all existing players.
  * **Cleaner Code Structure:** Each class has a single responsibility. This promotes maintainability, scalability, and adheres to the Open/Closed Principle.

***

* When to use Bridge Pattern
  * You have multiple dimensions of variation
  * You want to decouple abstraction from implementation
  * You anticipate frequent changes or additions
  * You want to follow SOLID principles
  * You want runtime flexibility

***

* Advantages
  * **Decouples abstraction and implementation:** Changes in one side (abstraction or implementation) do not affect the other.
  * **Avoids class explosion:** You don't need to create a separate class for every combination of abstraction and implementation.
  * **Supports the Open/Closed Principle (OCP):** You can extend functionalities without modifying existing code.
  * **Ideal for cross-platform development:** Useful when developing for multiple platforms that share similar features.
  * **Improves maintainability and testing:** Easier to manage and test each part independently.

***

* Disadvantages
  * **Increased complexity:** Might be overkill if your application is simple or has limited variations.
  * **Can be confused with other patterns:** Especially with patterns like Strategy or Adapter, due to structural similarities.
  * **Coordination needed between teams:** If abstraction and implementation are developed separately, good communication is essential.



<figure><img src="../../../../.gitbook/assets/image (156).png" alt=""><figcaption></figcaption></figure>

<br>
