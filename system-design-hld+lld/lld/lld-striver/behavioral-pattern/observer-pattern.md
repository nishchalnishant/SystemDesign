# Observer Pattern

* Imagine a notification system where multiple users get alerts when a new blog post is published. The publisher shouldn't have to worry about who all are subscribed or how they get notified.&#x20;
* This kind of automatic, event-driven update mechanism is exactly what behavioural patterns help us achieve.
* Observer Pattern is a behavioural design pattern that defines a one-to-many dependency between objects so that when one object (the subject) changes its state, all its dependents (called observers) are notified and updated automatically.
*   **Formal Definition**



    * The Observer Pattern is a behavioral design pattern where an object, known as the subject, maintains a list of dependents (observers) and notifies them of any state changes, usually by calling one of their methods.
    * This means if multiple objects are watching another object for updates, they don’t need to keep checking repeatedly. Instead, they get notified as soon as something changes — making the system more efficient and loosely coupled.
*   **Real-Life Analogy**



    * Think of subscribing to a YouTube channel. Once you hit the Subscribe button and turn on notifications, you don’t have to keep visiting the channel to check for new videos.&#x20;
    * As soon as a new video is uploaded, you get notified instantly.
    * In this case:
      * The channel is the subject.
      * The subscribers are the observers.
      * The notification is the automatic update mechanism triggered by the subject.
*   ### Understanding the Problem



    * Let’s say we’re building a simple YouTube-like Notification System.&#x20;
    * Whenever a creator uploads a new video, all their subscribers should get notified.

```python
class YouTubeChannel:
    def upload_new_video(self, video_title):
        # Upload the video
        print(f"Uploading: {video_title}\n")

        # Manually notify users
        print("Sending email to user1@example.com")
        print("Pushing in-app notification to user3@example.com")

def main():
    # Create a channel and upload a new video
    channel = YouTubeChannel()
    channel.upload_new_video("Design Patterns in Python")

if __name__ == "__main__":
    main()

```

*   **What’s Wrong with This Approach?**



    * While the code works, there are several design-level concerns:
      * Tightly Coupled Code:
        * The `YouTubeChannel` class is directly responsible for how users are notified. If tomorrow we want to send an SMS or push notification, we’ll have to edit this class.
      * No Reusability:
        * The notification logic (email, app, etc.) is hardcoded. You can't reuse or extend this behavior in other places without copying code.
      * Scalability Issues:
        * Imagine having hundreds of users and multiple notification types. You’d end up cluttering this class with all the notification logic.
      * Violation of Single Responsibility Principle (SRP):&#x20;
        * The class is doing two things: handling video uploads and managing user notifications. Ideally, each class should have one responsibility.

```python
from abc import ABC, abstractmethod

# ==============================
# Observer Interface
# ==============================
class Subscriber(ABC):
    @abstractmethod
    def update(self, video_title):
        pass

# ==============================
# Concrete Observer: Email
# ==============================
class EmailSubscriber(Subscriber):
    def __init__(self, email):
        self.email = email

    def update(self, video_title):
        print(f"Email sent to {self.email}: New video uploaded - {video_title}")

# ==============================
# Concrete Observer: Mobile App
# ==============================
class MobileAppSubscriber(Subscriber):
    def __init__(self, username):
        self.username = username

    def update(self, video_title):
        print(f"In-app notification for {self.username}: New video - {video_title}")

# ==============================
# Subject Interface
# ==============================
class Channel(ABC):
    @abstractmethod
    def subscribe(self, subscriber):
        pass

    @abstractmethod
    def unsubscribe(self, subscriber):
        pass

    @abstractmethod
    def notify_subscribers(self, video_title):
        pass

# ==============================
# Concrete Subject: YouTubeChannel
# ==============================
class YouTubeChannel(Channel):
    def __init__(self, channel_name):
        self.channel_name = channel_name
        self.subscribers = []

    def subscribe(self, subscriber):
        self.subscribers.append(subscriber)

    def unsubscribe(self, subscriber):
        self.subscribers.remove(subscriber)

    def notify_subscribers(self, video_title):
        for subscriber in self.subscribers:
            subscriber.update(video_title)

    # Simulates video upload and triggers notifications
    def upload_video(self, video_title):
        print(f"{self.channel_name} uploaded: {video_title}\n")
        self.notify_subscribers(video_title)

# ==============================
# Client Code
# ==============================
def main():
    tuf = YouTubeChannel("takeUforward")

    # Add subscribers
    tuf.subscribe(MobileAppSubscriber("raj"))
    tuf.subscribe(EmailSubscriber("rahul@example.com"))

    # Upload video and notify all observers
    tuf.upload_video("observer-pattern")

if __name__ == "__main__":
    main()

```



| Problem in Old Approach                            | How Observer Pattern Solves It                                         |
| -------------------------------------------------- | ---------------------------------------------------------------------- |
| Channel is tightly coupled with notification logic | Each subscriber handles its own notification via `update()`            |
| Not extensible for new notification types          | Add new subscriber classes without modifying existing code             |
| No reusability of logic                            | Notification logic is encapsulated in reusable subscriber classes      |
| SRP Violation (upload + notify in one class)       | Upload logic stays in `YouTubeChannel`; notification logic is external |
| Difficult to manage large number of subscribers    | `subscribe()` and `unsubscribe()` methods handle this cleanly          |



* Use Cases and Limitations
  * **Recommended Scenarios for Applying the Observer Pattern**
    * State Change Propagation:
      * When a change in one object must be immediately reflected across multiple dependent objects, the Observer Pattern provides a clean way to propagate this change without direct coupling
    * Decoupling Between Core Components:&#x20;
      * In systems where the subject (publisher) should remain agnostic of how many observers exist or what actions they perform, the Observer Pattern promotes separation of concerns. This makes the system easier to extend and maintain.
    * Dynamic Subscriptions at Runtime:&#x20;
      * Situations that involve modules being added or removed dynamically (e.g., plugins, UI listeners, notification modules) benefit from the Observer Pattern, as it allows flexible attachment and detachment of observers without affecting the subject.

***



* **Situations Where the Observer Pattern May Fall Short**
  * Excessive Observer Load&#x20;
    * In high-scale systems with millions of observers (e.g., when a celebrity with 10M followers goes live), a direct notification loop becomes inefficient.&#x20;
    * Such cases are better handled using event queues, pub-sub architectures, or broadcast systems optimized for massive concurrency.
  * Strict Control Over Notification Timing
    * In environments where the timing of notifications must be tightly managed—such as financial systems or real-time analytics, deterministic control is critical.&#x20;
    * The Observer Pattern lacks fine-grained scheduling control. Systems like message brokers (e.g., Kafka, RabbitMQ) are more suitable in such scenarios, providing features like buffering, retries, and ordering.
* In short, Observer Pattern works really well with a small number of observers, but to scale, it becomes essential to move toward an event-driven architecture.

***

#### Pros and Cons

**Pros**

* Promotes Loose Coupling:\
  Observers and subjects are decoupled. They interact only through a common interface, which improves flexibility and modularity.
* Open for Extension:\
  New types of observers can be added without modifying the subject class, adhering to the Open/Closed Principle.
* Supports Dynamic Subscription:\
  Observers can be attached or detached at runtime, enabling highly configurable and adaptable systems.
* Encourages Reusability:\
  Different observer implementations can be reused across subjects or contexts without duplication of logic.

***

**Cons**

* Unpredictable Update Sequences:\
  If the order of observer notifications matters, it may be hard to manage as the pattern does not guarantee update order.
* Performance Bottlenecks at Scale:\
  Notifying a large number of observers synchronously can degrade performance in high-scale systems.
* Risk of Memory Leaks:\
  Failure to unsubscribe unused observers may result in lingering references and memory issues.
* Difficult Debugging:\
  Since interactions happen indirectly through interfaces, tracing the source of bugs or unwanted updates can be challenging.
* Tight Timing Coupling:\
  All observers are notified immediately. Delayed or controlled delivery of events is not supported natively.

***

#### Real-Life Use Cases

The Observer Pattern is widely used in real-world systems that require automatic propagation of changes across dependent components. Here are a few notable examples:

* UI Event Handling:\
  In GUI frameworks, buttons, sliders, and input fields use observers (listeners) to respond to user actions like clicks or typing.
* News or Blog Subscriptions:\
  Readers subscribe to news feeds or blog updates. When new content is published, all subscribers are notified instantly.
* Stock Market Tickers:\
  Trading platforms subscribe to stock price changes. Whenever prices update, relevant modules (charts, alerts, watchlists) are notified in real-time.
* File System Watchers:\
  IDEs or OS-level watchers use observers to track file changes. Once a file is modified, all registered tools or services (like compilers or sync tools) are triggered.
* Social Media Notifications:\
  Platforms like YouTube or Instagram notify followers when someone they follow posts new content.

<br>

<figure><img src="../../../../.gitbook/assets/image (14).png" alt=""><figcaption></figcaption></figure>
