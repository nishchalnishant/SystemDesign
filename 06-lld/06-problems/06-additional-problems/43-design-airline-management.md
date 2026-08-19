> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design an Airline Management / Flight Booking System — a seat-inventory problem structurally identical to BookMyShow: many concurrent passengers competing for a small, fixed set of seats on a single flight leg.
>
> **Key concepts:**
> - Core Entities: `Flight` (owns seat inventory), `Seat` (state: AVAILABLE, HELD, BOOKED), `SeatClass` (Enum: ECONOMY, BUSINESS), `SeatHold` (temporary lock with TTL), `Booking`, `Passenger`, `PaymentTransaction`.
> - The problem: atomically reserving a seat so two passengers never get confirmed onto the same seat, using a hold-then-confirm flow instead of instant booking.
> - Patterns:
>   - State: `Seat` transitions between Available, Held, and Booked.
>   - Strategy: pluggable fare/pricing calculation per seat class.
>   - Factory: constructing seat maps per aircraft configuration.
> - Concurrency: `holdSeat()` must be atomic per-flight (a lock scoped to the `Flight`), with holds expiring after a TTL (e.g., 10 minutes) if payment isn't completed — mirrors BookMyShow's `SeatLock` mechanism.
>
> **Key takeaway:** This is a seat-locking / inventory-allocation problem. The interesting design decision is separating "hold" (soft, temporary, reversible) from "book" (hard, confirmed, tied to payment) — this two-phase flow is the standard pattern for any perishable-inventory booking system (flights, movies, hotel rooms, event tickets).

---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, airline-management, flight-booking, state-pattern, strategy, concurrency, seat-locking]
---
# Design an Airline Management System

> **Difficulty**: Medium-Hard
> **Asked at**: Amazon, Google, Uber, Expedia
> **Key Patterns**: State (Seat lifecycle), Strategy (fare pricing), Factory (seat map construction), Observer (waitlist notification)

---

## Understanding the Problem

Design the core booking engine of an airline management system: search flights, hold a seat temporarily while the passenger enters payment details, confirm the booking once payment succeeds, and release the hold if payment doesn't complete in time. The system must guarantee that no two passengers are ever confirmed onto the same seat, even under heavy concurrent demand (e.g., a popular route going on sale).

---

## Clarifying Questions

**You**: "Do we need to support connecting flights / multi-leg itineraries, or just a single flight leg?"
**Interviewer**: "Keep it to a single leg for this session — one origin, one destination, one flight number. Mention how you'd extend to connections if we have time."

**You**: "What seat classes do we need — just economy, or also business/first?"
**Interviewer**: "Support economy and business. Each class has its own price and its own block of seats on the aircraft."

**You**: "Does the passenger pick a specific seat, or is it auto-assigned at booking and seat selection happens later (like at check-in)?"
**Interviewer**: "Let's support explicit seat selection at booking time — the passenger picks seat 14C, for example. That's the harder version and it's what most airline systems actually do for paid fares."

**You**: "Should we support overbooking, since airlines routinely oversell based on historical no-show rates?"
**Interviewer**: "Not in the base design — keep bookings strictly bounded by physical seat count. But be ready to discuss how you'd add overbooking with a waitlist as an extension."

**You**: "What's the cancellation and refund policy?"
**Interviewer**: "A confirmed booking can be cancelled before departure. On cancellation, the seat becomes available again and the payment is refunded. No partial refunds or fare-rules complexity — full refund on cancel."

**You**: "How long should a seat hold last before it expires if payment isn't completed?"
**Interviewer**: "10 minutes is standard — use that as the default, but make it configurable."

**You**: "Is thread safety a concern — multiple passengers hitting the same flight's seat map concurrently?"
**Interviewer**: "Yes, assume high concurrent load on popular flights, especially right when a sale opens."

---

## Final Requirements

**In scope:**
1. Search flights by origin, destination, and date
2. View seat map for a flight with availability per seat class
3. Hold a seat temporarily (TTL-based lock) while the passenger completes payment
4. Confirm a booking — convert a valid hold into a confirmed booking backed by a successful payment
5. Automatically expire and release holds that aren't confirmed within the TTL window
6. Cancel a confirmed booking — refund payment, free the seat
7. Support ECONOMY and BUSINESS seat classes with independent pricing
8. Thread-safe seat hold/confirm/release under concurrent access

**Out of scope:**
- Connecting / multi-leg itineraries (discussed as an extension)
- Overbooking and waitlists (discussed as an extension)
- Dynamic/surge pricing, loyalty miles, baggage, check-in, boarding passes
- Real payment gateway integration (mocked `PaymentTransaction`)

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|---------------|
| Flight | Owns the seat inventory for one scheduled leg; drives hold/confirm/release/cancel flow |
| Seat | Represents one physical seat; tracks state (AVAILABLE, HELD, BOOKED) and class |
| SeatClass | Enum — ECONOMY, BUSINESS — determines fare and seat map partition |
| SeatHold | Temporary, TTL-bound reservation of a seat by a passenger, pending payment |
| Booking | Confirmed reservation linking passenger, seat, flight, and payment |
| Passenger | Identity and contact info of the traveler |
| PaymentTransaction | Records payment amount, status, and links to the Booking |

`Flight` owns the seat inventory and is the single point of synchronization — all hold/confirm/release operations for a given flight go through a lock scoped to that `Flight`. `SeatHold` is the anti-double-booking mechanism: a seat is available only if it has no active (non-expired) hold and no confirmed booking. `Booking` is only created once payment succeeds, at which point the hold is destroyed and the seat transitions to BOOKED. Cancelling a `Booking` triggers a refund and returns the seat to AVAILABLE.

---

## Class Design

### Seat

| Requirement | What Seat must track |
|-------------|----------------------|
| Which class it belongs to | seat_class: SeatClass |
| Current lifecycle state | status: SeatStatus (AVAILABLE, HELD, BOOKED) |
| Physical location for seat map display | seat_number: str (e.g. "14C") |

```
class Seat:
- seat_number: str
- seat_class: SeatClass       # ECONOMY, BUSINESS
- status: SeatStatus          # AVAILABLE, HELD, BOOKED

+ is_available() -> bool
+ mark_held() -> None
+ mark_booked() -> None
+ mark_available() -> None
```

### SeatHold

```
class SeatHold:
- hold_id: str
- flight_id: str
- seat_number: str
- passenger_id: str
- created_at: datetime
- expires_at: datetime

+ is_expired() -> bool
```

### SeatClass / FarePricing

```
enum SeatClass:
- ECONOMY
- BUSINESS

class FarePricingStrategy:          # abstract
+ get_fare(seat_class: SeatClass) -> float

class FixedFarePricing(FarePricingStrategy):
- fares: dict[SeatClass, float]
```

### Flight

| Requirement | What Flight must track |
|-------------|------------------------|
| Full seat inventory | seats: dict[str, Seat]        # seat_number -> Seat |
| Active holds | seat_holds: dict[str, SeatHold] # seat_number -> hold |
| Atomic hold/confirm/release | lock: ReentrantLock (per-flight) |
| Route/schedule metadata | flight_number, origin, destination, departure_time |

```
class Flight:
- flight_id: str
- flight_number: str
- origin: str
- destination: str
- departure_time: datetime
- seats: dict[str, Seat]
- seat_holds: dict[str, SeatHold]
- lock: ReentrantLock
- hold_ttl: Duration            # default 10 minutes

+ get_available_seats(seat_class: SeatClass | None) -> list[Seat]
+ hold_seat(seat_number: str, passenger_id: str) -> SeatHold
+ confirm_booking(hold_id: str, payment: PaymentTransaction) -> Booking
+ release_expired_holds() -> int
+ release_seat(seat_number: str) -> None
```

### Booking

```
class Booking:
- booking_id: str
- flight: Flight
- seat: Seat
- passenger: Passenger
- payment: PaymentTransaction
- status: BookingStatus          # CONFIRMED, CANCELLED
- booked_at: datetime
```

### PaymentTransaction

```
class PaymentTransaction:
- transaction_id: str
- amount: float
- status: PaymentStatus          # SUCCESS, FAILED, REFUNDED
```

### FlightSearchService

```
class FlightSearchService:
- flights: dict[str, Flight]

+ search(origin: str, destination: str, date: date) -> list[Flight]
+ get_flight(flight_id: str) -> Flight
```

---

## Implementation

### Core Method: searchFlights

**Core logic:**
1. Filter the flight index by origin, destination, and date
2. Return matching flights with available-seat counts per class (read-only, no locking needed)

```java
public List<Flight> searchFlights(String origin, String destination, LocalDate date) {
    List<Flight> results = new ArrayList<>();
    for (Flight flight : flightsById.values()) {
        boolean matchesRoute = flight.getOrigin().equalsIgnoreCase(origin)
            && flight.getDestination().equalsIgnoreCase(destination);
        boolean matchesDate = flight.getDepartureTime().toLocalDate().equals(date);
        if (matchesRoute && matchesDate) {
            results.add(flight);
        }
    }
    return results;
}
```

### Core Method: holdSeat

**Core logic:**
1. Acquire the flight's lock (atomic per-flight — avoids a global bottleneck across unrelated flights)
2. Lazily evict expired holds so a stale hold doesn't block a fresh request
3. Check the seat is AVAILABLE and has no active hold
4. Create a `SeatHold` with `expires_at = now + hold_ttl`, mark seat HELD
5. Release lock, return the hold

**Edge cases:**
- Seat already booked — reject
- Seat currently held by someone else and not yet expired — reject
- Seat number doesn't exist on this flight — reject

```java
public SeatHold holdSeat(String seatNumber, String passengerId) {
    lock.lock();
    try {
        evictExpiredHolds();

        Seat seat = seats.get(seatNumber);
        if (seat == null) {
            throw new NoSuchElementException("No such seat: " + seatNumber);
        }
        if (!seat.isAvailable()) {
            throw new SeatUnavailableException("Seat " + seatNumber + " is not available");
        }

        Instant now = Instant.now();
        SeatHold hold = new SeatHold(
            UUID.randomUUID().toString(),
            flightId,
            seatNumber,
            passengerId,
            now,
            now.plus(holdTtl)
        );
        seat.markHeld();
        seatHolds.put(seatNumber, hold);
        return hold;
    } finally {
        lock.unlock();
    }
}

private void evictExpiredHolds() {
    Iterator<Map.Entry<String, SeatHold>> it = seatHolds.entrySet().iterator();
    while (it.hasNext()) {
        Map.Entry<String, SeatHold> entry = it.next();
        if (entry.getValue().isExpired()) {
            Seat seat = seats.get(entry.getKey());
            if (seat != null && seat.getStatus() == SeatStatus.HELD) {
                seat.markAvailable();
            }
            it.remove();
        }
    }
}
```

### Core Method: confirmBooking

**Core logic:**
1. Acquire the flight's lock
2. Look up the hold by `hold_id`; if missing or expired, fail cleanly (payment should not have been attempted)
3. Validate the payment succeeded
4. Mark the seat BOOKED, remove the hold, create and return a `Booking`

**Edge cases:**
- Hold expired between `holdSeat` and `confirmBooking` — payment hasn't happened yet from the airline's perspective; release and fail with a retryable error
- Payment failed — leave the hold intact so the passenger can retry within the TTL window
- Double confirm on the same hold — hold already removed on first call, second call fails

```java
public Booking confirmBooking(String holdId, PaymentTransaction payment) {
    lock.lock();
    try {
        SeatHold hold = findHoldById(holdId);
        if (hold == null) {
            throw new NoSuchElementException("No such hold: " + holdId);
        }
        if (hold.isExpired()) {
            seatHolds.remove(hold.getSeatNumber());
            Seat seat = seats.get(hold.getSeatNumber());
            if (seat != null) {
                seat.markAvailable();
            }
            throw new HoldExpiredException("Seat hold expired. Please select a seat again.");
        }
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new PaymentFailedException("Payment did not succeed; hold retained until expiry");
        }

        Seat seat = seats.get(hold.getSeatNumber());
        seat.markBooked();
        seatHolds.remove(hold.getSeatNumber());

        return new Booking(
            UUID.randomUUID().toString(),
            this,
            seat,
            hold.getPassengerId(),
            payment,
            BookingStatus.CONFIRMED,
            Instant.now()
        );
    } finally {
        lock.unlock();
    }
}

private SeatHold findHoldById(String holdId) {
    for (SeatHold hold : seatHolds.values()) {
        if (hold.getHoldId().equals(holdId)) {
            return hold;
        }
    }
    return null;
}
```

### Core Method: releaseExpiredHolds

**Core logic:**
1. Acquire the flight's lock
2. Scan `seat_holds`, remove entries where `is_expired()` is true, flip those seats back to AVAILABLE
3. Return count released (useful for metrics / testing)

```java
public int releaseExpiredHolds() {
    lock.lock();
    try {
        List<String> expiredSeatNumbers = new ArrayList<>();
        for (Map.Entry<String, SeatHold> entry : seatHolds.entrySet()) {
            if (entry.getValue().isExpired()) {
                expiredSeatNumbers.add(entry.getKey());
            }
        }
        for (String seatNumber : expiredSeatNumbers) {
            seatHolds.remove(seatNumber);
            Seat seat = seats.get(seatNumber);
            if (seat != null && seat.getStatus() == SeatStatus.HELD) {
                seat.markAvailable();
            }
        }
        return expiredSeatNumbers.size();
    } finally {
        lock.unlock();
    }
}
```

A scheduled background thread (or a `ScheduledExecutorService` tick every 30-60 seconds) calls this per active flight, in addition to the lazy eviction inside `holdSeat` — belt and suspenders, so a flight with no new hold requests doesn't leak held seats indefinitely.

### Core Method: cancelBooking

**Core logic:**
1. Acquire the flight's lock
2. Validate the booking belongs to this flight and is CONFIRMED
3. Refund the payment (mark `PaymentTransaction` as REFUNDED)
4. Mark the seat AVAILABLE, mark the booking CANCELLED

```java
public void cancelBooking(Booking booking) {
    lock.lock();
    try {
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Only confirmed bookings can be cancelled");
        }
        booking.getPayment().setStatus(PaymentStatus.REFUNDED);
        booking.getSeat().markAvailable();
        booking.setStatus(BookingStatus.CANCELLED);
    } finally {
        lock.unlock();
    }
}
```

### Pricing: FixedFarePricing

```java
public class FixedFarePricing implements FarePricingStrategy {
    private final Map<SeatClass, Double> fares;

    public FixedFarePricing() {
        this.fares = Map.of(
            SeatClass.ECONOMY, 250.0,
            SeatClass.BUSINESS, 900.0
        );
    }

    @Override
    public double getFare(SeatClass seatClass) {
        return fares.get(seatClass);
    }
}
```

---

## Verification

**Scenario**: Passenger P1 books seat 14C (economy) on flight AA100.

1. `search_flights("JFK", "LAX", 2026-09-01)` returns `AA100` among matches
2. `hold_seat("14C", "P1")` — lock acquired, `evict_expired_holds()` finds nothing stale, seat 14C is AVAILABLE
3. `SeatHold H001` created: `created_at=10:00:00`, `expires_at=10:10:00` (10-minute TTL); seat 14C -> HELD
4. Passenger enters payment; at `10:03:00`, `confirm_booking("H001", payment)` is called
5. Hold H001 found, not expired (`10:03:00 < 10:10:00`); `payment.status == SUCCESS`
6. Seat 14C -> BOOKED; hold H001 removed from `seat_holds`
7. `Booking B001` returned: `status=CONFIRMED`, fare = `FixedFarePricing.get_fare(ECONOMY) = $250.00`

**Cancellation at a later time**:
1. `cancel_booking(B001)` — booking is CONFIRMED, proceeds
2. `payment.status -> REFUNDED`
3. Seat 14C -> AVAILABLE
4. `B001.status -> CANCELLED`

**Contrast — hold that expires unused**:
1. Passenger P2 calls `hold_seat("14D", "P2")` at `11:00:00` -> `SeatHold H002` expires at `11:10:00`
2. P2 abandons checkout; no `confirm_booking` call
3. At `11:11:00`, P3 calls `hold_seat("14D", "P3")` — `evict_expired_holds()` inside the call finds H002 expired (`11:10:00 < 11:11:00`), removes it, flips seat 14D back to AVAILABLE
4. Seat 14D is now free; P3's hold succeeds and creates `SeatHold H003`

---

## Deep Dive & Extensibility

### 1. "How do you prevent two passengers from booking the same seat concurrently?"

The `Flight`-scoped lock makes `hold_seat` atomic: check-availability and mark-held happen inside a single critical section, so two threads racing on the same seat cannot both observe AVAILABLE. One thread wins, marks the seat HELD, and the second thread's check fails against the now-HELD seat.

```java
public SeatHold holdSeat(String seatNumber, String passengerId) {
    lock.lock();
    try {
        evictExpiredHolds();
        Seat seat = seats.get(seatNumber);
        if (seat == null || !seat.isAvailable()) {
            throw new SeatUnavailableException("Seat " + seatNumber + " is not available");
        }
        seat.markHeld();
        SeatHold hold = new SeatHold(UUID.randomUUID().toString(), flightId, seatNumber,
            passengerId, Instant.now(), Instant.now().plus(holdTtl));
        seatHolds.put(seatNumber, hold);
        return hold;
    } finally {
        lock.unlock();
    }
}
```

A single lock per flight (not global) gives parallelism across flights — booking seats on `AA100` never blocks booking seats on `UA200`. For very high-traffic flights, this could be refined to per-seat locks or a compare-and-swap on seat state, but a flight has at most a few hundred seats, so one lock per flight is more than adequate and far simpler to reason about than fine-grained locking.

### 2. "How would you implement overbooking with a waitlist?"

Airlines oversell based on historical no-show rates. Introduce an `overbook_limit` per flight — the number of holds/bookings allowed to exceed physical seat count — and a `Waitlist` for passengers who can't get an immediate confirmed seat.

```java
class Flight {
    private int overbookLimit;              // e.g., 5 extra bookings allowed beyond seats.size()
    private final List<WaitlistEntry> waitlist = new ArrayList<>();

    public Booking bookOrWaitlist(String seatClass, Passenger passenger, PaymentTransaction payment) {
        lock.lock();
        try {
            int bookedCount = countBookedInClass(seatClass);
            int classCapacity = countSeatsInClass(seatClass);
            if (bookedCount < classCapacity + overbookLimit) {
                return createConfirmedBooking(seatClass, passenger, payment);
            }
            WaitlistEntry entry = new WaitlistEntry(passenger, seatClass, Instant.now());
            waitlist.add(entry);
            return null; // caller checks waitlist position instead of a Booking
        } finally {
            lock.unlock();
        }
    }

    public void onCancellation(String seatClass) {
        lock.lock();
        try {
            for (Iterator<WaitlistEntry> it = waitlist.iterator(); it.hasNext(); ) {
                WaitlistEntry entry = it.next();
                if (entry.getSeatClass().equals(seatClass)) {
                    it.remove();
                    notifyPassengerSeatAvailable(entry.getPassenger());
                    break;
                }
            }
        } finally {
            lock.unlock();
        }
    }
}
```

This is the Observer pattern: `onCancellation` notifies the next waitlisted passenger (email/push) that a seat opened up, giving them a fresh short-TTL hold to complete booking. The overbook limit itself is typically computed offline from historical no-show statistics and fed in as configuration, not derived at request time.

### 3. "How would you support connecting flights / multi-leg itineraries?"

Introduce an `Itinerary` that composes multiple `Flight` legs, and make holding/booking atomic across all legs — a passenger should never end up with a confirmed seat on leg 1 but a failed hold on leg 2.

```java
class Itinerary {
    private final List<Flight> legs;         // e.g., JFK->ORD, ORD->LAX

    public List<SeatHold> holdAllLegs(Map<Flight, String> seatBySelectedLeg, String passengerId) {
        List<SeatHold> acquired = new ArrayList<>();
        try {
            for (Flight leg : legs) {
                String seatNumber = seatBySelectedLeg.get(leg);
                acquired.add(leg.holdSeat(seatNumber, passengerId));
            }
            return acquired;
        } catch (SeatUnavailableException e) {
            // Roll back any holds already acquired on earlier legs
            for (SeatHold hold : acquired) {
                Flight ownerFlight = findFlightForHold(hold);
                ownerFlight.releaseSeat(hold.getSeatNumber());
            }
            throw new ItineraryUnavailableException("Could not hold seats for full itinerary", e);
        }
    }
}
```

Legs are locked in a fixed, globally consistent order (e.g., by `flight_id`) to avoid deadlock when two passengers book overlapping itineraries in opposite leg order — the same ordering discipline used to avoid deadlock with any multi-lock acquisition. Search also changes: `FlightSearchService.search` needs to enumerate valid leg combinations (same-day connection windows, minimum connection time) rather than a single direct match.

---

## Interviewer Questions by Level

**Junior**: Define the `Seat` and `SeatHold` classes and explain the three seat states. Describe why booking is two steps (hold, then confirm) instead of one. Can sketch a basic class diagram of Flight/Seat/Booking.

**Mid-level**: Implement `hold_seat` and `confirm_booking` with correct per-flight locking. Explain why a hold has a TTL and what happens if it expires mid-payment. Implement `FixedFarePricing` via Strategy. Handle the double-confirm and expired-hold edge cases correctly.

**Senior**: Justify per-flight locking over a global lock or per-seat locks. Design the overbooking + waitlist extension and explain the Observer-based notification flow. Design atomic multi-leg holding for connecting itineraries, including deadlock avoidance via consistent lock ordering. Discuss trade-offs between lazy hold-expiry (checked on next access) vs. a background sweep thread.

---

## Common Interview Questions

- Q: Why not book the seat directly instead of holding it first? A: Payment takes time (redirects, 3D-Secure, retries). Without a hold, another passenger could book the same seat while the first is still entering card details. The hold reserves the seat for a bounded window so payment can complete safely.
- Q: What happens if the hold expires exactly while `confirm_booking` is executing? A: Both operations happen inside the same per-flight lock, so `is_expired()` is checked atomically with the state transition — either the hold is still valid and confirmation proceeds, or it's expired and confirmation fails cleanly with `HoldExpiredException`. There's no window where both can be true.
- Q: Why is the lock scoped to `Flight` and not global? A: Two passengers booking different flights share no state — a global lock would serialize unrelated bookings for no reason. Per-flight locking gives full parallelism across flights while still serializing operations on the same flight's seat map.
- Q: How do you avoid leaking held seats if a passenger just abandons checkout? A: Lazy eviction inside `hold_seat` (and `confirm_booking`) catches it the next time anyone touches that flight's holds; a scheduled sweep (`release_expired_holds`) catches it even if no one else requests that flight for a while.
- Q: How would you test for double-booking under concurrency? A: Spin up N threads all calling `hold_seat("14C", ...)` for the same seat simultaneously. Assert exactly one succeeds and N-1 throw `SeatUnavailableException`, and that the seat ends in HELD (not some inconsistent state) with exactly one hold record.
- Q: What's the difference between cancelling a hold and cancelling a booking? A: A hold is soft and expires on its own — no explicit cancel API is strictly required, though one can be added to free the seat early. A booking is a committed, paid reservation — cancelling it requires a refund and is a business event, not just inventory cleanup.
- Q: How would overbooking change the seat-availability check? A: `is_available` becomes capacity-based rather than per-seat: compare confirmed-plus-held count in a class against `class_capacity + overbook_limit` instead of checking a single seat's status, and route excess demand to a waitlist instead of rejecting outright.

---

## Concurrency Test Harness

Runnable tests that verify thread-safety invariants. No external deps — uses `java.util.concurrent` and stdlib only.

```java
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

// --- Minimal stubs to make the harness self-contained ---

enum SeatStatus { AVAILABLE, HELD, BOOKED }
enum SeatClass { ECONOMY, BUSINESS }
enum PaymentStatus { SUCCESS, FAILED, REFUNDED }
enum BookingStatus { CONFIRMED, CANCELLED }

class SeatUnavailableException extends RuntimeException {
    public SeatUnavailableException(String message) { super(message); }
}

class HoldExpiredException extends RuntimeException {
    public HoldExpiredException(String message) { super(message); }
}

class PaymentFailedException extends RuntimeException {
    public PaymentFailedException(String message) { super(message); }
}

class Seat {
    private final String seatNumber;
    private final SeatClass seatClass;
    private volatile SeatStatus status = SeatStatus.AVAILABLE;

    public Seat(String seatNumber, SeatClass seatClass) {
        this.seatNumber = seatNumber;
        this.seatClass = seatClass;
    }

    public boolean isAvailable() { return status == SeatStatus.AVAILABLE; }
    public void markHeld() { status = SeatStatus.HELD; }
    public void markBooked() { status = SeatStatus.BOOKED; }
    public void markAvailable() { status = SeatStatus.AVAILABLE; }

    public String getSeatNumber() { return seatNumber; }
    public SeatClass getSeatClass() { return seatClass; }
    public SeatStatus getStatus() { return status; }
}

class SeatHold {
    private final String holdId;
    private final String flightId;
    private final String seatNumber;
    private final String passengerId;
    private final Instant createdAt;
    private final Instant expiresAt;

    public SeatHold(String holdId, String flightId, String seatNumber, String passengerId,
                     Instant createdAt, Instant expiresAt) {
        this.holdId = holdId;
        this.flightId = flightId;
        this.seatNumber = seatNumber;
        this.passengerId = passengerId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired() { return Instant.now().isAfter(expiresAt); }

    public String getHoldId() { return holdId; }
    public String getSeatNumber() { return seatNumber; }
    public String getPassengerId() { return passengerId; }
    public Instant getExpiresAt() { return expiresAt; }
}

class PaymentTransaction {
    private final String transactionId;
    private final double amount;
    private PaymentStatus status;

    public PaymentTransaction(String transactionId, double amount, PaymentStatus status) {
        this.transactionId = transactionId;
        this.amount = amount;
        this.status = status;
    }

    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }
}

class Booking {
    private final String bookingId;
    private final Flight flight;
    private final Seat seat;
    private final String passengerId;
    private final PaymentTransaction payment;
    private BookingStatus status;
    private final Instant bookedAt;

    public Booking(String bookingId, Flight flight, Seat seat, String passengerId,
                    PaymentTransaction payment, BookingStatus status, Instant bookedAt) {
        this.bookingId = bookingId;
        this.flight = flight;
        this.seat = seat;
        this.passengerId = passengerId;
        this.payment = payment;
        this.status = status;
        this.bookedAt = bookedAt;
    }

    public Seat getSeat() { return seat; }
    public PaymentTransaction getPayment() { return payment; }
    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }
}

class Flight {
    private final String flightId;
    final Map<String, Seat> seats = new HashMap<>();
    final Map<String, SeatHold> seatHolds = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Duration holdTtl;

    public Flight(String flightId, List<Seat> seatList, Duration holdTtl) {
        this.flightId = flightId;
        this.holdTtl = holdTtl;
        for (Seat s : seatList) {
            seats.put(s.getSeatNumber(), s);
        }
    }

    private void evictExpiredHolds() {
        Iterator<Map.Entry<String, SeatHold>> it = seatHolds.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, SeatHold> entry = it.next();
            if (entry.getValue().isExpired()) {
                Seat seat = seats.get(entry.getKey());
                if (seat != null && seat.getStatus() == SeatStatus.HELD) {
                    seat.markAvailable();
                }
                it.remove();
            }
        }
    }

    public SeatHold holdSeat(String seatNumber, String passengerId) {
        lock.lock();
        try {
            evictExpiredHolds();
            Seat seat = seats.get(seatNumber);
            if (seat == null) {
                throw new NoSuchElementException("No such seat: " + seatNumber);
            }
            if (!seat.isAvailable()) {
                throw new SeatUnavailableException("Seat " + seatNumber + " is not available");
            }
            Instant now = Instant.now();
            SeatHold hold = new SeatHold(UUID.randomUUID().toString(), flightId, seatNumber,
                passengerId, now, now.plus(holdTtl));
            seat.markHeld();
            seatHolds.put(seatNumber, hold);
            return hold;
        } finally {
            lock.unlock();
        }
    }

    private SeatHold findHoldById(String holdId) {
        for (SeatHold hold : seatHolds.values()) {
            if (hold.getHoldId().equals(holdId)) {
                return hold;
            }
        }
        return null;
    }

    public Booking confirmBooking(String holdId, PaymentTransaction payment) {
        lock.lock();
        try {
            SeatHold hold = findHoldById(holdId);
            if (hold == null) {
                throw new NoSuchElementException("No such hold: " + holdId);
            }
            if (hold.isExpired()) {
                seatHolds.remove(hold.getSeatNumber());
                Seat seat = seats.get(hold.getSeatNumber());
                if (seat != null) {
                    seat.markAvailable();
                }
                throw new HoldExpiredException("Seat hold expired");
            }
            if (payment.getStatus() != PaymentStatus.SUCCESS) {
                throw new PaymentFailedException("Payment did not succeed");
            }
            Seat seat = seats.get(hold.getSeatNumber());
            seat.markBooked();
            seatHolds.remove(hold.getSeatNumber());
            return new Booking(UUID.randomUUID().toString(), this, seat, hold.getPassengerId(),
                payment, BookingStatus.CONFIRMED, Instant.now());
        } finally {
            lock.unlock();
        }
    }

    public int releaseExpiredHolds() {
        lock.lock();
        try {
            List<String> expired = new ArrayList<>();
            for (Map.Entry<String, SeatHold> entry : seatHolds.entrySet()) {
                if (entry.getValue().isExpired()) {
                    expired.add(entry.getKey());
                }
            }
            for (String seatNumber : expired) {
                seatHolds.remove(seatNumber);
                Seat seat = seats.get(seatNumber);
                if (seat != null && seat.getStatus() == SeatStatus.HELD) {
                    seat.markAvailable();
                }
            }
            return expired.size();
        } finally {
            lock.unlock();
        }
    }

    public Seat getSeat(String seatNumber) { return seats.get(seatNumber); }
}

// --- Test harness ---

class AirlineConcurrencyTest {

    static Flight makeFlight(Duration ttl) {
        List<Seat> seats = new ArrayList<>();
        seats.add(new Seat("14C", SeatClass.ECONOMY));
        for (int i = 0; i < 20; i++) {
            seats.add(new Seat("R" + i, SeatClass.ECONOMY));
        }
        return new Flight("AA100", seats, ttl);
    }

    // ─────────────────────────────────────────────────────────────
    // TEST 1: Exactly one thread wins the hold on a contested seat.
    // 50 threads race to hold seat "14C". Exactly 1 must succeed;
    // 49 must throw SeatUnavailableException.
    // ─────────────────────────────────────────────────────────────
    static void testExactlyOneHoldWins() throws InterruptedException {
        Flight flight = makeFlight(Duration.ofMinutes(10));
        List<SeatHold> succeeded = Collections.synchronizedList(new ArrayList<>());
        List<Integer> failed = Collections.synchronizedList(new ArrayList<>());

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            final int idx = i;
            threads.add(new Thread(() -> {
                try {
                    SeatHold hold = flight.holdSeat("14C", "PASSENGER-" + idx);
                    succeeded.add(hold);
                } catch (SeatUnavailableException e) {
                    failed.add(idx);
                }
            }));
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        if (succeeded.size() != 1) throw new AssertionError("Expected exactly 1 success, got " + succeeded.size());
        if (failed.size() != 49) throw new AssertionError("Expected 49 failures, got " + failed.size());
        if (flight.getSeat("14C").getStatus() != SeatStatus.HELD) {
            throw new AssertionError("Seat 14C should be HELD");
        }

        System.out.println("PASS: testExactlyOneHoldWins");
    }

    // ─────────────────────────────────────────────────────────────
    // TEST 2: Expired holds are released and the seat becomes
    // available again for a new passenger.
    // ─────────────────────────────────────────────────────────────
    static void testExpiredHoldReleasesSeat() throws InterruptedException {
        Flight flight = makeFlight(Duration.ofMillis(200)); // short TTL for the test

        SeatHold hold = flight.holdSeat("R0", "PASSENGER-A");
        if (flight.getSeat("R0").getStatus() != SeatStatus.HELD) {
            throw new AssertionError("Seat R0 should be HELD immediately after hold");
        }

        Thread.sleep(400); // let the hold expire

        int releasedCount = flight.releaseExpiredHolds();
        if (releasedCount != 1) throw new AssertionError("Expected 1 hold released, got " + releasedCount);
        if (flight.getSeat("R0").getStatus() != SeatStatus.AVAILABLE) {
            throw new AssertionError("Seat R0 should be AVAILABLE after expiry sweep");
        }

        // A new passenger can now successfully hold the same seat.
        SeatHold newHold = flight.holdSeat("R0", "PASSENGER-B");
        if (newHold == null) throw new AssertionError("Expected new hold to succeed");
        if (flight.getSeat("R0").getStatus() != SeatStatus.HELD) {
            throw new AssertionError("Seat R0 should be HELD by the new passenger");
        }

        // Confirming the stale original hold must fail — it was already evicted.
        boolean staleConfirmRejected = false;
        try {
            flight.confirmBooking(hold.getHoldId(), new PaymentTransaction("TXN-1", 250.0, PaymentStatus.SUCCESS));
        } catch (NoSuchElementException | HoldExpiredException e) {
            staleConfirmRejected = true;
        }
        if (!staleConfirmRejected) throw new AssertionError("Stale hold should not be confirmable");

        System.out.println("PASS: testExpiredHoldReleasesSeat");
    }

    // ─────────────────────────────────────────────────────────────
    // TEST 3: Concurrent hold + confirm across many distinct seats
    // all succeed independently — no cross-seat interference.
    // ─────────────────────────────────────────────────────────────
    static void testConcurrentHoldAndConfirmDistinctSeats() throws InterruptedException {
        Flight flight = makeFlight(Duration.ofMinutes(10));
        List<Booking> bookings = Collections.synchronizedList(new ArrayList<>());

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            final String seatNumber = "R" + i;
            threads.add(new Thread(() -> {
                SeatHold hold = flight.holdSeat(seatNumber, "PASSENGER-" + seatNumber);
                PaymentTransaction payment = new PaymentTransaction("TXN-" + seatNumber, 250.0, PaymentStatus.SUCCESS);
                Booking booking = flight.confirmBooking(hold.getHoldId(), payment);
                bookings.add(booking);
            }));
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        if (bookings.size() != 20) throw new AssertionError("Expected 20 bookings, got " + bookings.size());
        for (Booking b : bookings) {
            if (b.getSeat().getStatus() != SeatStatus.BOOKED) {
                throw new AssertionError("Seat " + b.getSeat().getSeatNumber() + " should be BOOKED");
            }
        }

        System.out.println("PASS: testConcurrentHoldAndConfirmDistinctSeats");
    }

    public static void main(String[] args) throws InterruptedException {
        testExactlyOneHoldWins();
        testExpiredHoldReleasesSeat();
        testConcurrentHoldAndConfirmDistinctSeats();
        System.out.println("All concurrency tests passed.");
    }
}
```

**What each test verifies:**
- `testExactlyOneHoldWins`: The per-flight lock makes check-then-mark atomic in `hold_seat`, so 50 threads racing on the same seat produce exactly one winner — no double-hold, no lost update.
- `testExpiredHoldReleasesSeat`: TTL expiry is honored both by the explicit sweep (`release_expired_holds`) and lazily inside the next `hold_seat` call; a stale hold cannot later be confirmed once evicted.
- `testConcurrentHoldAndConfirmDistinctSeats`: Locking is correctly scoped — concurrent operations on different seats within the same flight don't spuriously block or interfere with each other, only true contention on the same seat is serialized.

---

## Related

**Patterns applied here**

- [State Pattern](../../03-design-patterns/03-behavioral/state-pattern.md)
- [Factory Pattern](../../03-design-patterns/01-creational/factory-pattern.md)

**SOLID focus**: [Open/Closed](../../02-solid-principles/02-open-closed.md)

**Concurrency**: [Concurrency Patterns](../../04-concurrency/concurrency-patterns.md)

**Practice next**

- [Design BookMyShow](../02-frequent-problems/06-design-bookmyshow.md)
- [Design Elevator System](../02-frequent-problems/09-design-elevator-system.md)

Both reuse the temporary-hold-then-confirm inventory-locking shape central to this problem.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../01-oop-fundamentals/uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
