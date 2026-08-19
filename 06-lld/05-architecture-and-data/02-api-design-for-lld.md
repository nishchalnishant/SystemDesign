---
module: 06-lld
topic: Architecture and Data
status: unread
tags: [06-lld, api, rest, graphql]
---
# API Design for LLD

> **Difficulty**: Medium  
> **Key Concepts**: REST, GraphQL, Idempotency, Pagination

In a senior LLD interview, after you design your class diagrams, the interviewer will often ask: *"How will the client interact with this system?"* You must define the API contract.

## RESTful API Best Practices

Your APIs should be resource-oriented, stateless, and use standard HTTP methods.

### 1. Resource Naming
Nouns, not verbs. Plural, not singular.
- ❌ `POST /createBooking`
- ✅ `POST /bookings`
- ❌ `GET /getUser?id=123`
- ✅ `GET /users/123`

### 2. Nesting Resources
Use nesting to show relationships, but don't go deeper than two levels.
- ✅ `GET /users/123/bookings` (Good)
- ❌ `GET /users/123/bookings/456/tickets/789` (Bad - too deep. Just use `GET /tickets/789`)

### 3. HTTP Methods
- `GET`: Retrieve a resource (Idempotent)
- `POST`: Create a new resource (Not Idempotent)
- `PUT`: Replace an existing resource entirely (Idempotent)
- `PATCH`: Partially update a resource (Not strictly idempotent)
- `DELETE`: Remove a resource (Idempotent)

## Advanced SDE-3 Concepts

### Idempotency Keys (Critical for Payments/Bookings)
When designing systems like an ATM, Payment Gateway, or Booking System, network failures can cause the client to retry a `POST` request. You must ensure the action isn't executed twice.

**Solution**: The client sends an `Idempotency-Key` in the header.

```http
POST /payments
Idempotency-Key: abc-123-xyz
Content-Type: application/json

{
    "orderId": "9876",
    "amount": 150.00
}
```
If the server sees a retry with the same `Idempotency-Key`, it returns the cached response of the original successful request instead of charging the card again.

### Pagination
Never return unbounded lists in an API (e.g., `GET /users/123/transactions`).
- **Offset/Limit Pagination**: Good for static data. `?offset=100&limit=50`. (Suffers from performance issues on massive datasets).
- **Cursor-based Pagination**: Best for real-time/infinite scroll. `?after=cursor123&limit=50`. (Highly efficient as it uses indexed columns).

## Interview Example: Parking Lot API

If asked to design the API for a Parking Lot:

**1. Entry (Park Vehicle)**
```http
POST /parking-lots/{lotId}/tickets
Request: { "licensePlate": "XYZ123", "vehicleType": "CAR" }
Response (201 Created): { "ticketId": "T-999", "spotId": "A1", "entryTime": "..." }
```

**2. Exit (Unpark & Pay)**
```http
POST /parking-lots/{lotId}/tickets/{ticketId}/checkout
Response (200 OK): { "fee": 15.00, "status": "PAID", "exitTime": "..." }
```
*(Note: We use a custom action `/checkout` here because checking out is a complex business process, not just a simple resource update).*
