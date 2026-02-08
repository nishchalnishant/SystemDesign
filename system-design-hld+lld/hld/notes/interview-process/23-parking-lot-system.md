# #23 parking lot system

Here’s a complete, time-boxed, 1-hour interview-ready answer for designing a Parking Lot System. It follows your system design interview structure, including functional & non-functional requirements, APIs/data model, architecture, deep dive, and trade-offs.

***

## 0 – 5 min — Problem recap, scope & assumptions

<br>

Goal: Design a system to manage a parking lot that supports multiple vehicle types, tracks occupancy, allows entry/exit, and calculates charges.

<br>

Scope for interview:

* Track vehicles entering and leaving parking lot.
* Handle multiple types of parking spaces (compact, large, handicapped).
* Calculate parking fees based on duration.
* Support multiple floors and zones.
* Optionally integrate with mobile app for availability and booking.

<br>

Assumptions:

* Parking lot has multiple levels/floors.
* Supports cars, bikes, trucks.
* Peak occupancy: 500–1000 vehicles.
* Real-time occupancy tracking needed.
* Payment is per hour, per vehicle type.

***

## 5 – 15 min — Functional & Non-Functional Requirements

<br>

#### Functional Requirements

<br>

Must

1. Vehicle entry/exit: Register vehicle entry with timestamp and exit.
2. Space allocation: Assign appropriate parking spot based on vehicle type.
3. Occupancy tracking: Track available/occupied slots in real-time.
4. Billing: Calculate parking fee based on duration and type.
5. Parking types: Support multiple vehicle types (car, bike, truck).
6. Ticket generation: Issue parking tickets or QR codes.

<br>

Should

* Support multiple floors and zones.
* Display available slots per floor/zone.
* Allow reservation of slots.

<br>

Nice-to-have

* Mobile app integration for booking/availability.
* EV charging spots management.
* Notifications for nearing exit/payment due.

***

#### Non-Functional Requirements

* Availability: System should operate 24/7 with high reliability.
* Latency: Real-time occupancy updates (<100 ms).
* Scalability: Support multiple parking lots in future.
* Durability: Persist all vehicle and billing data.
* Consistency: Strong consistency for slot allocation to avoid double booking.
* Monitoring: Track occupancy trends, payment failures, system errors.

***

## 15 – 25 min — API / Data Model

<br>

#### APIs

```
# Entry
enter_parking(vehicle_id: str, vehicle_type: str) -> ticket_id

# Exit
exit_parking(ticket_id: str) -> fee

# Query availability
get_available_slots(floor: int=None, vehicle_type: str=None) -> int

# Reserve slot
reserve_slot(vehicle_type: str, floor: int=None) -> reservation_id
```

#### Data Models

<br>

Vehicle

```
{
  "vehicle_id": "string",
  "type": "car/bike/truck",
  "entry_time": "timestamp",
  "exit_time": "timestamp",
  "ticket_id": "string"
}
```

ParkingSlot

```
{
  "slot_id": "string",
  "floor": int,
  "type": "car/bike/truck",
  "is_occupied": bool,
  "vehicle_id": "string|null"
}
```

Ticket

```
{
  "ticket_id": "string",
  "vehicle_id": "string",
  "slot_id": "string",
  "entry_time": "timestamp",
  "exit_time": "timestamp|null",
  "fee": float|null
}
```

***

## 25 – 40 min — High-level architecture & data flow

```
[Vehicle Entry/Exit] --> [Parking System API] --> [Parking Manager Service] --> [Slot Database / Redis Cache]
                                                |
                                                v
                                            [Billing Service]
```

Components

1. Parking Manager Service: Assigns slots, tracks occupancy.
2. Slot Database / Cache: Stores current state of slots; cache for fast availability queries.
3. Billing Service: Computes fees on exit.
4. Reservation Service (optional): Handles pre-booking of slots.
5. Mobile App / Kiosk: Interface for users to enter/exit, query availability.

<br>

Data Flow

1. Vehicle enters → scan ID / ticket issued → Parking Manager assigns slot → updates database/cache.
2. Vehicle exits → parking system calculates fee → updates occupancy.
3. Availability query → read from cache/database → return free slots.

***

## 40 – 50 min — Deep dive — slot allocation, scaling, concurrency

<br>

#### Slot Allocation

* Maintain priority queue per vehicle type per floor.
* Allocate nearest free slot to minimize search time.
* On exit → mark slot free → update cache and DB.

<br>

#### Scaling

* For multiple parking lots → use separate Parking Manager instances per lot.
* Horizontal scaling possible with microservices.
* Cache hot data (available slots per floor/type) for low-latency queries.

<br>

#### Concurrency

* Use atomic operations or distributed locks when allocating slots to prevent double allocation.
* Redis or DB transactions for concurrency control.

<br>

#### Fault Tolerance

* Persist all transactions in DB to prevent data loss.
* Use backup cache or DB replication for high availability.

***

## 50 – 55 min — Back-of-the-envelope calculations

<br>

Assumptions

* 1000 vehicles peak → \~2000 events/day (entry+exit).
* Each event \~200 bytes → 400 KB/day (minimal storage).
* Cache: store all slots per lot → \~10 floors \* 100 slots/floor → 1000 entries → \~50 KB in memory.
* Latency: Occupancy query from cache <1 ms; DB update on entry/exit <10 ms.

<br>

Storage

* Primary DB: relational DB (PostgreSQL/MySQL) for transactions.
* Cache: Redis for availability queries.

***

## 55 – 58 min — Monitoring & ops

<br>

Monitoring

* Occupancy trends.
* Failed slot allocations.
* Billing discrepancies.
* API latency and errors.

<br>

Operational concerns

* Handle peak traffic during events or rush hours.
* Backup DB to prevent data loss.
* Automated alerts for full occupancy or system failures.

***

## 58 – 60 min — Trade-offs, evolution & summary

<br>

#### Trade-offs

* Cache vs DB: Cache improves read latency for availability; DB ensures durability.
* Simple allocation vs optimized: Simple nearest slot allocation fast; optimized allocation may improve user experience but more complex.
* Monolithic vs Microservices: Microservices better for multi-lot scalability, modularity.

<br>

#### Evolution

1. MVP: Single lot, real-time entry/exit, basic billing.
2. Phase 2: Multiple floors, vehicle types, pre-booking.
3. Phase 3: Mobile app integration, analytics, EV charging support, predictive availability.

<br>

#### Summary

* System tracks vehicle entry/exit, slot availability, and billing.
* Uses cache for low-latency queries and DB for durability.
* Scalable, fault-tolerant, and can evolve to multiple lots, floors, and mobile integration.

***

If you want, I can next create a class diagram and sequence diagram for the parking lot system, which is very helpful to explain in interviews.

<br>

Do you want me to create those diagrams next?
