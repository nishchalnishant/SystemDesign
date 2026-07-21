> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** The ultimate cheat sheet for passing a 45-minute Low-Level Design (LLD / Object-Oriented Design) interview. 
>
> **Key concepts:**
> - **Phase 1: Requirements (The Rules):** Before writing code, figure out exactly who is using the app and what they can do. 
> - **Phase 2: Use Cases (The Story):** Write down exactly what happens when a user clicks a button, step-by-step.
> - **Phase 3: Classes (The Nouns):** Look at your story. Every noun (Person, Car, Ticket) becomes a Class. Every verb (Park, Pay) becomes a Method.
> - **Phase 4: Design Patterns (The Magic Tricks):** Use famous coding tricks (like Strategy or Factory) to make your code flexible, so it doesn't break when the boss asks for a new feature tomorrow.
> - **Phase 5: Code (The Actual Work):** Write the Java/Python code for the single hardest part of the app.
>
> **Key takeaway:** Junior developers immediately start writing `class ParkingLot { ... }` on the whiteboard. Senior developers spend 20 minutes agreeing on the blueprint with the interviewer before writing a single line of code.

---
module: 07-interview-templates
status: unread
tags: [07-interview-templates, system-design, interview-templates, lld, object-oriented-design]
---
# LLD Interview Framework (45-60 min)

> This guide provides a bulletproof 5-step template for Object-Oriented Design interviews, using simple analogies to help you memorize the flow.

---

## 🤷‍♂️ The Mental Model

High-Level Design (HLD) asks: *"How do we build a whole city?"* (Databases, Load Balancers, Servers). 
Low-Level Design (LLD) asks: *"How do we build the plumbing inside one specific house?"* (Classes, Functions, Variables).

In an LLD interview, the interviewer gives you a prompt like: **"Design a Parking Lot."** 
They are grading you on 4 things:
1. Can you turn a real-world object (a Car) into clean computer code?
2. Do you know Design Patterns? (e.g., How to handle 5 different types of payments without writing a massive `if/else` statement).
3. Is your code readable?
4. What happens if two cars try to park in the exact same spot at the exact same millisecond? (Concurrency).

---

## ⏱️ The 45-Minute Timeline

| Phase | Time | What You Do |
|-------|------|-------------|
| **1. Requirements** | 0-5 min | Who uses this? What can they do? |
| **2. Use Cases** | 5-10 min | Write the step-by-step story. |
| **3. Classes** | 10-15 min | Find the Nouns and Verbs. |
| **4. Design Patterns** | 15-25 min | Apply famous coding tricks. |
| **5. Core Code** | 25-45 min | Actually write the code on the whiteboard. |

---

## 🏗️ Phase 1: Requirements (0-5 min)

Ask the "Three Golden Questions":
1. **Who are the Actors?** (e.g., Customer, Parking Attendant, Admin).
2. **What are the core features?** (e.g., Park a car, Pay for ticket).
3. **What are the constraints?** (e.g., "Do we need to support electric vehicle charging spots later?")

---

## 📖 Phase 2: Use Cases (5-10 min)

Write a short, step-by-step story of the "Happy Path" (when everything goes perfectly) and the "Sad Path" (when things break).

> **💡 Example Use Case (Park a Car):**
> 1. Customer arrives at the gate.
> 2. System checks if the lot is full. 
> 3. System finds an empty spot.
> 4. System prints a Ticket.
> *Sad Path:* The lot is full. The system throws a `LotFullException`.

---

## 🧱 Phase 3: Classes (The Nouns & Verbs)

Look at the story you just wrote. 
- **Every Noun is a Class.** (Customer, Spot, Ticket, ParkingLot).
- **Every Verb is a Method.** (arrive(), findSpot(), printTicket()).

### The 4 Relationships (How classes talk to each other)
You must explain how your classes are connected:
1. **IS-A (Inheritance):** A Car *is a* Vehicle. 
2. **HAS-A (Composition):** A ParkingLot *has a* ParkingSpot. If you destroy the ParkingLot, the ParkingSpots cease to exist. 
3. **HAS-A (Aggregation):** A ParkingLot *has a* Car. But if you destroy the ParkingLot, the Car simply drives away. It still exists!
4. **USES (Dependency):** A ParkingLot *uses* a PricingCalculator to figure out the fee. 

---

## 🎩 Phase 4: Design Patterns (15-25 min)

This is how you prove you are a Senior Developer. If your code relies on massive `if/else` blocks, you will fail. You must use Patterns. 

### 1. Strategy Pattern (The Interchangeable Tool)
**The Problem:** The Parking Lot charges $5/hour on weekdays, but $10/hour on weekends, and $2/hour for motorcycles. 
**The Fix:** Don't write a giant `if/else` block. Create an Interface called `PricingStrategy`. Create three separate files (`WeekdayPricing`, `WeekendPricing`, `MotorcyclePricing`). The Parking Lot just asks the Strategy for the price, without caring how the math works. 

### 2. Factory Pattern (The Assembly Line)
**The Problem:** You need to create different types of Vehicles (Car, Truck, Motorcycle), but creating them requires complex setup. 
**The Fix:** Create a `VehicleFactory`. When the app needs a new Car, it asks the Factory to build one. 

### 3. Singleton Pattern (The Highlander)
**The Problem:** You accidentally created two ParkingLots in memory, and now the system is double-booking spots.
**The Fix:** Use a Singleton to guarantee that only exactly ONE `ParkingLot` object can ever exist in the computer's memory at the same time. 

---

## 💻 Phase 5: Core Code (25-45 min)

Do not write getter and setter methods (`getName()`). It is a waste of time. 
Only write the code for the single hardest part of the system. 

> **💡 Example (Parking a Car in Java):**
```java
public synchronized Ticket parkVehicle(Vehicle vehicle) {
    // 1. Check for errors
    if (vehicle == null) throw new Exception("No vehicle!");
    
    // 2. Find a spot
    ParkingSpot spot = findAvailableSpot(vehicle.getType());
    if (spot == null) throw new Exception("Lot is full!");
    
    // 3. Park it
    spot.assignVehicle(vehicle);
    
    // 4. Give them a ticket
    Ticket ticket = new Ticket(vehicle, spot, Time.now());
    return ticket;
}
```

### The Secret Weapon: Concurrency
Notice the word `synchronized` in the code above? 
If two cars arrive at the exact same millisecond, and there is only 1 spot left, a badly written app will give both cars the exact same spot! 
By adding `synchronized`, you force the computer to process one car at a time. Mentioning this will instantly impress the interviewer. 

---

## 🎤 Phrase to use to end the interview perfectly:

> "To summarize, I separated the code using the Single Responsibility Principle. The `ParkingSpot` class only cares about holding a car, while the `PricingStrategy` class only cares about math. Because of this, if the boss asks us to add 'Holiday Pricing' tomorrow, we can just add one new file without touching or breaking any of the existing Parking Lot code."

---

## Applied In

This concept is used by **36 problems** in this repo — a representative selection:

**Low-Level Design**

- [Design a Parking Lot](../../06-lld/05-problems/01-core-problems/01-design-parking-lot.md)
- [Design a Rate Limiter](../../06-lld/05-problems/01-core-problems/02-design-rate-limiter.md)
- [Design Tic-Tac-Toe](../../06-lld/05-problems/01-core-problems/03-design-tic-tac-toe.md)
- [Design a Vending Machine](../../06-lld/05-problems/01-core-problems/04-design-vending-machine.md)
- [Design Splitwise](../../06-lld/05-problems/01-core-problems/05-design-splitwise.md)
- [Design BookMyShow](../../06-lld/05-problems/02-frequent-problems/06-design-bookmyshow.md)
- [Design Chess](../../06-lld/05-problems/02-frequent-problems/07-design-chess.md)
- [Design Snake and Ladder](../../06-lld/05-problems/02-frequent-problems/08-design-snake-and-ladder.md)
- …and 28 more

