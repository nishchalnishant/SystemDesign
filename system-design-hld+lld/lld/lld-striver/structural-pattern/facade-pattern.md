# Facade Pattern

* help in forming large object structures while keeping them manageable, decoupled, and easy to work with
* provides a simplified, unified interface to a complex subsystem or group of classes.
* acts as a **single-entry point** for clients to interact with the system, hiding the underlying complexity and making the system easier to use.
* **Real-Life Analogy**
  * **Complex Subsystem (Manual Car):** Driving a manual car requires intricate knowledge of multiple components (clutch, gear shifter, accelerator) and their precise coordination to shift gears and drive. It's complex and requires the driver to manage many interactions.
  * **Facade (Automatic Car):** An automatic car acts as a facade. It provides a simplified interface (e.g., "Drive," "Reverse," "Park") to the complex underlying mechanics of gear shifting. The driver (client) no longer needs to manually coordinate the clutch and gears; the automatic transmission handles these complexities internally, making driving much easier.
* **Problem It Solves**
  *   It solves the problem of **dealing with complex subsystems** by hiding the complexities behind a single, unified interface. **For example, imagine a movie ticket booking system with:**

      * PaymentService
      * SeatReservationService
      * NotificationService
      * LoyaltyPointsService
      * TicketService

      <br>
* #### Real-Life Coding Example
  * Imagine you're developing a movie ticket booking application, like BookMyShow. Let's first take a look at a poorly structured approach to implementing the booking functionality

```python
# Service class responsible for handling payments
class PaymentService:
    def make_payment(self, account_id, amount):
        print(f"Payment of ₹{amount} successful for account {account_id}")

# Service class responsible for reserving seats
class SeatReservationService:
    def reserve_seat(self, movie_id, seat_number):
        print(f"Seat {seat_number} reserved for movie {movie_id}")

# Service class responsible for sending notifications
class NotificationService:
    def send_booking_confirmation(self, user_email):
        print(f"Booking confirmation sent to {user_email}")

# Service class for managing loyalty/reward points
class LoyaltyPointsService:
    def add_points(self, account_id, points):
        print(f"{points} loyalty points added to account {account_id}")

# Service class for generating movie tickets
class TicketService:
    def generate_ticket(self, movie_id, seat_number):
        print(f"Ticket generated for movie {movie_id}, Seat: {seat_number}")


# Client Code
if __name__ == "__main__":
    # Booking a movie ticket manually (without a facade)

    # Step 1: Make payment
    payment_service = PaymentService()
    payment_service.make_payment("user123", 500)

    # Step 2: Reserve seat
    seat_reservation_service = SeatReservationService()
    seat_reservation_service.reserve_seat("movie456", "A10")

    # Step 3: Send booking confirmation via email
    notification_service = NotificationService()
    notification_service.send_booking_confirmation("user@example.com")

    # Step 4: Add loyalty points to user's account
    loyalty_points_service = LoyaltyPointsService()
    loyalty_points_service.add_points("user123", 50)

    # Step 5: Generate the ticket
    ticket_service = TicketService()
    ticket_service.generate_ticket("movie456", "A10")

```

* While this code works, it's tightly coupled. The **`Main`** class (or client code) is **manually calling** each subsystem service in the correct order and with the correct parameters.
* This leads to:
  * **High complexity** for the client
  * **Duplicate code** if you have to do this in multiple places
  * **Violation of the Single Responsibility Principle** (the Main class knows too much)

```python
# Service class responsible for handling payments
class PaymentService:
    def make_payment(self, account_id, amount):
        print(f"Payment of ₹{amount} successful for account {account_id}")

# Service class responsible for reserving seats
class SeatReservationService:
    def reserve_seat(self, movie_id, seat_number):
        print(f"Seat {seat_number} reserved for movie {movie_id}")

# Service class responsible for sending notifications
class NotificationService:
    def send_booking_confirmation(self, user_email):
        print(f"Booking confirmation sent to {user_email}")

# Service class for managing loyalty/reward points
class LoyaltyPointsService:
    def add_points(self, account_id, points):
        print(f"{points} loyalty points added to account {account_id}")

# Service class for generating movie tickets
class TicketService:
    def generate_ticket(self, movie_id, seat_number):
        print(f"Ticket generated for movie {movie_id}, Seat: {seat_number}")


# ========== The MovieBookingFacade class  ==============
class MovieBookingFacade:
    # Constructor to initialize all the subsystem services.
    def __init__(self):
        self.payment_service = PaymentService()
        self.seat_reservation_service = SeatReservationService()
        self.notification_service = NotificationService()
        self.loyalty_points_service = LoyaltyPointsService()
        self.ticket_service = TicketService()

    # Method providing a simplified interface for booking a movie ticket
    def book_movie_ticket(self, account_id, movie_id, seat_number, user_email, amount):
        self.payment_service.make_payment(account_id, amount)
        self.seat_reservation_service.reserve_seat(movie_id, seat_number)
        self.ticket_service.generate_ticket(movie_id, seat_number)
        self.loyalty_points_service.add_points(account_id, 50)
        self.notification_service.send_booking_confirmation(user_email)

        # Indicate successful completion of the entire booking process.
        print("Movie ticket booking completed successfully!")


# Client Code
if __name__ == "__main__":
    # Booking a movie ticket manually (using facade)
    movie_booking_facade = MovieBookingFacade()
    movie_booking_facade.book_movie_ticket("user123", "movie456", "A10", "user@example.com", 500)

```

* **How Facade Pattern Solves the Issue**
  *   By introducing **`MovieBookingFacade`**, we:

      * Provide a **simple, unified interface** (bookMovieTicket()).
      * **Hide the complexity** of internal service calls from the client.
      * **Reduce coupling**, so changes in internal services don't affect the client.
      * Centralize the **workflow logic**, making it easier to update and reuse.


*   #### When to use Facade Pattern?



    * **Subsystems are complex:** This means there are too many classes and too many dependencies within the system you are trying to simplify.
    * **You want to provide a simpler API for the outer world:** The Facade acts as a simplified entry point, hiding the complexity from clients.
    * **You want to reduce coupling between subsystems and client code:** By interacting with the facade, the client code becomes less dependent on the individual components of the subsystem.
    * **You want to layer your architecture cleanly:** The Facade helps in organizing the system into distinct layers, making it more modular and understandable.


*   #### Advantages



    * **Lightweight coupling:** It reduces the dependencies between the client and the subsystem.
    * **Flexibility:** It allows the subsystem to evolve without impacting the client code.
    * **Simplifies client design:** Clients interact with a single, simplified interface instead of multiple complex objects.
    * **Promotes layered architecture:** It helps organize the system into distinct layers, improving maintainability and scalability.
    * **Better testability:** Individual subsystem components can be tested independently, and the facade itself can be tested for its orchestration logic.


*   #### Disadvantages



    * **Fragile coupling:** If the facade itself changes frequently, it can still lead to ripple effects on client code.
    * **Hidden complexity:** While it simplifies the client's view, the underlying complexity of the subsystem still exists, just hidden. This can make debugging or understanding the full flow more challenging for developers working on the subsystem.
    * **Runtime errors:** Errors originating from the complex subsystem might be harder to diagnose when only interacting through the facade.
    * **Difficult to trace:** Debugging can be more challenging as the facade adds another layer of indirection.
    * **Violation of SRP (Single Responsibility Principle):** A facade might take on too many responsibilities if it orchestrates a very large and diverse set of operations, potentially becoming a "god object."<br>

<figure><img src="../../../../.gitbook/assets/image (153).png" alt=""><figcaption></figcaption></figure>

<br>
