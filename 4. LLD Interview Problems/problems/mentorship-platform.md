# Design Mentorship Platform (Preplaced/Topmate)

> **Difficulty**: Medium  
> **Topics**: Marketplace Dynamics, Availability Management, State Machine  
> **Entities**: Mentor, Mentee, Session, Calendar.

## Problem Statement

Connect Mentees with Mentors for booked sessions.
- **Challenges**: Handling Availability (Calendar slots), Booking Concurrency, Search/Matchmaking.

## Core Domain Models

### Availability (The Hard Part)

Mentors define availability: "Mon 9-11 AM".
- **Recurring**: `RecurringSchedule` table.
- **Exceptions**: `AvailabilityOverride` table (e.g., Sick leave on specific Mon).

### Booking Flow (State Machine)

`PENDING` -> `CONFIRMED` -> `COMPLETED` / `CANCELLED`

## Implementation

### Classes

```python
class TimeSlot:
    def __init__(self, start, end):
        self.start = start
        self.end = end

class Mentor:
    def __init__(self, id, name):
        self.id = id
        self.availability = [] # List of TimeSlots

    def is_available(self, requested_slot):
        # Overlap Logic
        for slot in self.availability:
            if slot.start <= requested_slot.start and slot.end >= requested_slot.end:
                return True
        return False

class BookingSystem:
    def __init__(self):
        self.bookings = {} # ID -> Booking

    def book_session(self, mentor, mentee, slot):
        # 1. Concurrency Check (Critical)
        # In DB: SELECT * FROM Bookings WHERE mentor_id=? AND time=? FOR UPDATE
        if not mentor.is_available(slot):
            raise Exception("Slot unavailable")
            
        booking = Booking(mentor, mentee, slot)
        self.bookings[booking.id] = booking
        return booking
```

## Key Discussion Points

1.  **Concurrency**: Two mentees book same slot.
    *   **Solution**: Database locks (`SELECT ... FOR UPDATE`) or Optimistic Locking (`version` column).
2.  **Calendar Integration**: Sync with Google Calendar.
    *   Need two-way sync. Webhooks from Google Cal to update system availability.
3.  **Search**: "Find Python mentors available this weekend".
    *   Use ElasticSearch. Index mentors with `skills` and `available_slots`.
