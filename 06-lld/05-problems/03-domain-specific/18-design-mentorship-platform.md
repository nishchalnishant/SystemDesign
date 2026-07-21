> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a Mentorship Platform (or calendar booking system) — focuses on availability, interval overlap detection, and two-sided marketplace matching.
>
> **Key concepts:**
> - Core Entities: `Mentor`, `Mentee`, `Session`, `Availability` (Time slots).
> - Scheduling/Conflict Detection: The hardest part. You must check if a proposed session overlaps with any existing accepted sessions. Represent time as Unix timestamps and check if `new_start < exist_end && new_end > exist_start`.
> - Strategy Pattern (Matching): Finding a mentor involves ranking them by relevance. Implement strategies like `SkillMatchStrategy`, `RatingStrategy`, or `AvailabilityStrategy`.
> - State Pattern: `Session` transitions from `REQUESTED` -> `ACCEPTED` -> `IN_PROGRESS` -> `COMPLETED`.
>
> **Key takeaway:** Handling time is tricky. Always store intervals as UTC timestamps, not formatted strings. Use a simple interval overlap check for the availability logic.

---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, mentorship-platform, scheduling, conflict-resolution, observer]
---
# Design Mentorship Platform

> **Difficulty**: Medium
> **Asked at**: Amazon, Coursera, LinkedIn
> **Key Patterns**: Strategy (matching), Observer (booking events), Conflict detection (interval overlap)

---

## Understanding the Problem

Design a mentorship platform where mentors set their availability, mentees book 1:1 sessions, and the system prevents double-booking.

---

## Clarifying Questions

**You**: "Do mentors set recurring availability or one-off slots?"
**Interviewer**: "Both — recurring weekly availability + specific date overrides."

**You**: "What's the minimum and maximum session length?"
**Interviewer**: "30 minutes minimum, 2 hours maximum."

**You**: "Can a mentee cancel a booking? What's the cancellation window?"
**Interviewer**: "Yes, up to 24 hours before the session."

**You**: "Is there a matching algorithm — does the mentee choose, or does the platform assign?"
**Interviewer**: "Mentee browses and selects; platform shows available slots."

**You**: "Do we need timezone support?"
**Interviewer**: "Yes — store all times in UTC, display in user's local timezone."

---

## Final Requirements

**In scope:**
1. Mentors define weekly availability windows and specific date overrides
2. Mentees search available slots for a given mentor in a date range
3. Book a slot — prevents double-booking via conflict detection
4. Cancel a booking if more than 24 hours before session
5. All times stored in UTC

**Out of scope:**
- Payment / subscription
- Video call integration
- Review / rating system
- Automated matching (follow-up)

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|---------------|
| `Mentor` | Profile, expertise, timezone |
| `Mentee` | Profile, timezone |
| `Availability` | Recurring or one-time time windows when mentor is free |
| `Booking` | Confirmed session between mentor and mentee |
| `TimeSlot` | A specific start/end datetime (UTC) |
| `BookingService` | Checks conflicts, creates bookings, handles cancellations |
| `AvailabilityService` | Computes open slots from availability − bookings |

`BookingService` is the core — it must atomically check for conflicts and create the booking to prevent race conditions.

---

## Class Design

### Availability

| Requirement | What Availability must track |
|-------------|------------------------------|
| "Recurring availability" | day_of_week, start_time, end_time, effective_from, effective_to |
| "One-off override" | specific_date, start_time, end_time, is_blocked (for blocking time) |

```
class RecurringAvailability:
- mentor_id: str
- day_of_week: DayOfWeek   # MON, TUE, ...
- start_time: time          # UTC
- end_time: time            # UTC
- effective_from: date
- effective_to: Optional[date]

class DateOverride:
- mentor_id: str
- date: date
- start_time: Optional[time]  # None if blocked all day
- end_time: Optional[time]
- is_blocked: bool
```

### Booking

```
class Booking:
- id: str
- mentor_id: str
- mentee_id: str
- start_at: datetime   # UTC
- end_at: datetime     # UTC
- status: BookingStatus   # CONFIRMED, CANCELLED
- created_at: datetime
- cancelled_at: Optional[datetime]
```

### BookingService

```
class BookingService:
- booking_repo: BookingRepository
- availability_service: AvailabilityService

+ book(mentor_id, mentee_id, start_at, end_at) -> Booking
+ cancel(booking_id, cancelled_by) -> bool
+ get_bookings(mentor_id, from_date, to_date) -> list[Booking]
```

---

## Implementation

### Core Method: `book`

**Core logic:**
1. Validate requested slot is within mentor's availability
2. Check no existing bookings conflict with the requested slot
3. Atomically create booking (transaction or optimistic lock)
4. Return confirmed booking

**Edge cases:**
- Slot not within mentor's declared availability
- Overlapping existing booking → reject
- Two concurrent requests for the same slot (race condition)
- Session length outside 30 min–2 hour range

```python
def book(self, mentor_id, mentee_id, start_at, end_at):
    # Validate duration
    duration_minutes = (end_at - start_at).total_seconds() / 60
    if not (30 <= duration_minutes <= 120):
        raise InvalidSlotError("Session must be 30–120 minutes")

    # Validate slot is within availability
    if not self.availability_service.is_slot_available(mentor_id, start_at, end_at):
        raise SlotUnavailableError("Slot not within mentor's availability")

    # Conflict check + create — must be atomic
    with self.booking_repo.transaction():
        conflicts = self.booking_repo.find_conflicts(mentor_id, start_at, end_at)
        if conflicts:
            raise ConflictError("Time slot already booked")

        booking = Booking(
            id=generate_id(),
            mentor_id=mentor_id,
            mentee_id=mentee_id,
            start_at=start_at,
            end_at=end_at,
            status=BookingStatus.CONFIRMED,
            created_at=datetime.utcnow()
        )
        self.booking_repo.save(booking)
        return booking
```

### Conflict detection

```python
def find_conflicts(self, mentor_id, start_at, end_at):
    # Overlapping if: existing.start < requested.end AND existing.end > requested.start
    return [
        b for b in self.get_active_bookings(mentor_id)
        if b.start_at < end_at and b.end_at > start_at
    ]
```

### AvailabilityService: compute open slots

```python
def get_open_slots(self, mentor_id, from_date, to_date, slot_duration_minutes=60):
    open_slots = []
    current = from_date

    while current <= to_date:
        windows = self._get_availability_windows(mentor_id, current)
        booked = self.booking_repo.get_on_date(mentor_id, current)

        for window in windows:
            slot_start = window.start
            while slot_start + timedelta(minutes=slot_duration_minutes) <= window.end:
                slot_end = slot_start + timedelta(minutes=slot_duration_minutes)
                if not self._conflicts_with_bookings(slot_start, slot_end, booked):
                    open_slots.append(TimeSlot(slot_start, slot_end))
                slot_start = slot_end

        current += timedelta(days=1)

    return open_slots
```

### Cancellation

```python
def cancel(self, booking_id, cancelled_by):
    booking = self.booking_repo.get(booking_id)
    if not booking:
        raise BookingNotFoundError()
    if booking.status == BookingStatus.CANCELLED:
        raise AlreadyCancelledError()

    hours_until_session = (booking.start_at - datetime.utcnow()).total_seconds() / 3600
    if hours_until_session < 24:
        raise CancellationWindowError("Cannot cancel within 24 hours of session")

    booking.status = BookingStatus.CANCELLED
    booking.cancelled_at = datetime.utcnow()
    self.booking_repo.save(booking)
    return True
```

---

## Verification

```
Mentor Alice: available Mon 9:00–17:00 UTC

Mentee Bob books: 2026-07-06 (Monday) 10:00–11:00 UTC
  duration = 60 min ✓ (30–120)
  is_slot_available: Mon 10:00 within [9:00, 17:00] ✓
  find_conflicts: no existing bookings ✓
  booking created: B1

Mentee Carol tries: 2026-07-06 10:30–11:30 UTC
  is_slot_available: Mon 10:30 within [9:00, 17:00] ✓
  find_conflicts:
    B1.start(10:00) < 11:30 AND B1.end(11:00) > 10:30 → CONFLICT
  raise ConflictError("Time slot already booked")
```

---

## Deep Dive & Extensibility

### 1. "How do you handle the race condition where two mentees book the same slot simultaneously?"

Both pass the conflict check before either creates the booking. Fix: DB-level unique constraint or SELECT FOR UPDATE.

```sql
-- Optimistic: unique constraint
CREATE UNIQUE INDEX idx_mentor_time
ON bookings (mentor_id, start_at, end_at)
WHERE status = 'CONFIRMED';

-- One booking will fail with UniqueConstraintViolation — handle and return ConflictError
```

Alternatively: SELECT FOR UPDATE locks the mentor's rows during the transaction window.

### 2. "How would you handle timezone display?"

Store all times in UTC. At the API layer, convert to user's timezone for display:

```python
import pytz

def display_slot(slot, user_timezone_str):
    tz = pytz.timezone(user_timezone_str)
    local_start = slot.start_at.replace(tzinfo=pytz.utc).astimezone(tz)
    local_end = slot.end_at.replace(tzinfo=pytz.utc).astimezone(tz)
    return f"{local_start.strftime('%I:%M %p')} – {local_end.strftime('%I:%M %p')} {tz.zone}"
```

Never store local times — always convert to UTC at input.

### 3. "How would you add automated matching (recommend mentors)?"

```python
class MentorMatcher:
    def recommend(self, mentee, limit=5):
        # Score each mentor by: expertise overlap + availability in mentee's preferred times
        scores = []
        for mentor in self.mentor_repo.get_all():
            expertise_score = self._expertise_overlap(mentee.interests, mentor.expertise)
            availability_score = self._availability_overlap(mentee.preferred_times, mentor)
            scores.append((mentor, expertise_score * 0.7 + availability_score * 0.3))
        scores.sort(key=lambda x: x[1], reverse=True)
        return [m for m, _ in scores[:limit]]
```

Inject `MentorMatcher` as a Strategy — swap for ML-based ranker without touching BookingService.

### 4. "How would you add group sessions (one mentor, many mentees)?"

Extend `Booking` with `max_capacity: int` and `attendees: list[str]`. Change `find_conflicts` to only block when `len(attendees) >= max_capacity` (not on any overlap). `book` adds the mentee to `attendees` if capacity permits.

---

## Interviewer Questions by Level

**Junior**: Mentor, Mentee, Booking entities. Book a session. Check for exact slot conflicts. Cancel within rules.

**Mid-level**: Conflict detection via interval overlap (not just exact match). Recurring availability + date overrides. Cancellation window enforcement. Transaction for conflict-check-then-create.

**Senior**: Race condition analysis and DB-level fix (unique index or SELECT FOR UPDATE). Timezone storage strategy. Slot enumeration algorithm. Group session extension. Automated matching as pluggable strategy.

---

## Common Interview Questions

- **Q**: How do you detect if two time slots overlap?
  **A**: Interval overlap condition: `A.start < B.end AND A.end > B.start`. This covers all overlap cases — partial left, partial right, containment. Negation: `A.end <= B.start OR A.start >= B.end` (no overlap).

- **Q**: Why store times in UTC?
  **A**: Avoids DST ambiguity. Two users in different timezones refer to the same UTC time. Conversion to local time happens only at display, never in storage or comparison logic.

- **Q**: How do you handle recurring availability + date overrides?
  **A**: `_get_availability_windows(mentor_id, date)` first checks if a `DateOverride` exists for that date — if so, use it (or block if `is_blocked=True`). Otherwise, look up `RecurringAvailability` by day of week.

- **Q**: What prevents two mentees from booking the same slot?
  **A**: The conflict-check and booking-create must happen in the same transaction. In a DB, either a unique constraint on (mentor_id, start_at, end_at) or SELECT FOR UPDATE ensures only one succeeds.

- **Q**: How would you allow a mentor to block time (vacation)?
  **A**: `DateOverride` with `is_blocked=True`. `_get_availability_windows` returns empty for that date, so `get_open_slots` yields nothing and `is_slot_available` returns False.

---

## Related

**Patterns applied here**

- [Observer Pattern](../../03-design-patterns/03-behavioral/observer-pattern.md)
- [State Pattern](../../03-design-patterns/03-behavioral/state-pattern.md)
- [Strategy Pattern](../../03-design-patterns/03-behavioral/strategy-pattern.md)

**SOLID focus**: [Single Responsibility](../../02-solid-principles/01-single-responsibility.md) · [Dependency Inversion](../../02-solid-principles/05-dependency-inversion.md)

**Practice next**

- [Design Ride-Sharing](22-design-ride-sharing.md)
- [Design Food Delivery](../02-frequent-problems/14-design-food-delivery.md)

Matching two sides of a marketplace is the shared problem.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
