> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a Library Management System — a comprehensive OOP modeling exercise testing inheritance, state management, and business rule enforcement.
>
> **Key concepts:**
> - Core Entities: `Library`, `Book`, `BookItem` (a specific physical copy), `Member`, `Librarian`.
> - Inheritance: Differentiate between a `Book` (the abstract concept: Harry Potter, ISBN 123) and a `BookItem` (the physical copy: Barcode 999, placed on Rack 5).
> - State Pattern: `BookItem` transitions between `AVAILABLE`, `LOANED`, `LOST`, `RESERVED`.
> - Enforcement Rules: Max 5 books per user, max 10 days checkout. These are business rules that must be checked before a state transition.
> - Fine Calculation: Use the Strategy pattern if fines vary by book type or member type.
>
> **Key takeaway:** The biggest mistake candidates make is conflating `Book` and `BookItem`. A library has one `Book` record for "The Hobbit", but might own five physical `BookItem` copies. You checkout a `BookItem`, not a `Book`.

---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, library, state-pattern, observer, strategy]
---
# Design a Library Management System

> **Difficulty**: Medium  
> **Asked at**: Amazon, TCS  
> **Key Patterns**: State (book copy status), Strategy (fine calculation), Observer (reservation notifications)

---

## Understanding the Problem

Design a library management system where members can search for books, borrow and return physical copies, receive fines for late returns, and reserve books when all copies are checked out.

---

## Clarifying Questions

**You**: "Does the library have multiple copies of the same book?"  
**Interviewer**: "Yes — track copies independently from the book title record."

**You**: "How are fines calculated?"  
**Interviewer**: "Per-day rate for overdue copies; there's a maximum cap."

**You**: "When a member returns a book that others have reserved, what happens?"  
**Interviewer**: "The first member in the reservation queue should be notified and given a window to pick it up."

**You**: "Are there membership tiers with different borrowing limits?"  
**Interviewer**: "Yes — standard members can borrow 3 books, premium members can borrow 10."

**You**: "Can a member with overdue books borrow new ones?"  
**Interviewer**: "No — block borrowing until all fines are paid and overdue books returned."

**You**: "Do we need to handle book search by multiple attributes?"  
**Interviewer**: "Yes — by title, author, and ISBN."

---

## Final Requirements

**In scope:**
1. Catalog: books with multiple physical copies per title
2. Members borrow copies; each copy tracks its own status (AVAILABLE, CHECKED_OUT, RESERVED, LOST)
3. Return triggers fine calculation for overdue copies
4. Reservation queue per book; first reserver notified on return
5. Membership tiers: standard (3 books) and premium (10 books)
6. Block borrowing if member has overdue books or unpaid fines

**Out of scope:**
- Digital/e-book lending
- Inter-library transfers
- Payment processing for fines

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|----------------|
| Library | Facade; coordinates search, borrow, return, reserve |
| Book | Title-level record (isbn, title, author, genre) |
| BookCopy | One physical copy; tracks CopyStatus and current borrowal |
| CopyStatus | Enum: AVAILABLE, CHECKED_OUT, RESERVED, LOST |
| Member | Library member; tracks tier, active borrowals, fines |
| MemberTier | Enum: STANDARD (limit=3), PREMIUM (limit=10) |
| Borrowal | Links member + copy; records due_date, return_date |
| Reservation | Queued request; links member + book (title-level) |
| FineCalculator | Strategy: computes fine from overdue days |

---

## Class Design

### Book

| Requirement | What Book must track |
|-------------|----------------------|
| Identity | isbn, title, author, genre |
| Copies | list of BookCopy objects |

```
class Book:
- isbn: str
- title: str
- author: str
- genre: str
- copies: list[BookCopy]
+ available_copies() -> list[BookCopy]
```

### BookCopy

| Requirement | What BookCopy must track |
|-------------|--------------------------|
| Physical identity | copy_id, book reference |
| Availability | status: CopyStatus |
| Current loan | active_borrowal: Optional[Borrowal] |

```
class BookCopy:
- copy_id: str
- book: Book
- status: CopyStatus
- active_borrowal: Optional[Borrowal]
```

### Member

| Requirement | What Member must track |
|-------------|------------------------|
| Identity | member_id, name, email |
| Tier | tier: MemberTier → borrow limit |
| Activity | active_borrowals: list[Borrowal], pending_fines: float |

```
class Member:
- member_id: str
- name: str
- email: str
- tier: MemberTier
- active_borrowals: list[Borrowal]
- pending_fines: float
+ borrow_limit() -> int
+ can_borrow() -> bool
```

### Borrowal

| Requirement | What Borrowal must track |
|-------------|--------------------------|
| Loan record | borrowal_id, member, copy, borrow_date, due_date |
| Return | return_date (None until returned) |

```
class Borrowal:
- borrowal_id: str
- member: Member
- copy: BookCopy
- borrow_date: date
- due_date: date
- return_date: Optional[date]
+ is_overdue() -> bool
+ overdue_days() -> int
```

### Library

```
class Library:
- books: dict[str, Book]            # isbn -> Book
- members: dict[str, Member]        # member_id -> Member
- reservations: dict[str, deque[Reservation]]  # isbn -> queue
- fine_calculator: FineCalculator
+ search_books(query: str, field: str) -> list[Book]
+ borrow_book(member_id: str, isbn: str) -> Borrowal
+ return_book(borrowal_id: str) -> float
+ reserve_book(member_id: str, isbn: str) -> Reservation
```

---

## Implementation

### Core Method: `return_book`

**Core logic:**
1. Look up the Borrowal by id.
2. Mark `return_date = today`.
3. Compute fine if overdue; add to member's `pending_fines`.
4. Set copy status back to AVAILABLE; detach `active_borrowal`.
5. Remove borrowal from member's `active_borrowals`.
6. Check reservation queue for this book's isbn; if non-empty, pop the first reservation, set copy status to RESERVED, and notify that member.

**Edge cases:**
- Copy already returned — raise `ValueError`.
- Reservation queue empty on return — copy becomes AVAILABLE.
- Member has multiple overdue copies — fine applies per copy independently.

```python
from dataclasses import dataclass, field
from datetime import date
from collections import deque
from enum import Enum, auto
from typing import Optional
import uuid


class CopyStatus(Enum):
    AVAILABLE   = auto()
    CHECKED_OUT = auto()
    RESERVED    = auto()
    LOST        = auto()


class MemberTier(Enum):
    STANDARD = 3
    PREMIUM  = 10


@dataclass
class Book:
    isbn: str
    title: str
    author: str
    genre: str
    copies: list = field(default_factory=list)

    def available_copies(self):
        return [c for c in self.copies if c.status == CopyStatus.AVAILABLE]


@dataclass
class BookCopy:
    copy_id: str
    book: Book
    status: CopyStatus = CopyStatus.AVAILABLE
    active_borrowal: Optional["Borrowal"] = None


@dataclass
class Member:
    member_id: str
    name: str
    email: str
    tier: MemberTier = MemberTier.STANDARD
    active_borrowals: list = field(default_factory=list)
    pending_fines: float = 0.0

    def borrow_limit(self) -> int:
        return self.tier.value

    def can_borrow(self) -> bool:
        overdue = any(b.is_overdue() for b in self.active_borrowals)
        return (not overdue
                and self.pending_fines == 0.0
                and len(self.active_borrowals) < self.borrow_limit())


@dataclass
class Borrowal:
    borrowal_id: str
    member: Member
    copy: BookCopy
    borrow_date: date
    due_date: date
    return_date: Optional[date] = None

    def is_overdue(self) -> bool:
        check = self.return_date or date.today()
        return check > self.due_date

    def overdue_days(self) -> int:
        if not self.is_overdue():
            return 0
        check = self.return_date or date.today()
        return (check - self.due_date).days


@dataclass
class Reservation:
    reservation_id: str
    member: Member
    book: Book
    reserved_date: date


class FineCalculator:
    DAILY_RATE = 1.0   # dollars per day
    MAX_FINE   = 25.0

    def calculate(self, borrowal: Borrowal) -> float:
        days = borrowal.overdue_days()
        return min(days * self.DAILY_RATE, self.MAX_FINE)


class Library:
    def __init__(self):
        self.books: dict[str, Book] = {}
        self.members: dict[str, Member] = {}
        self.borrowals: dict[str, Borrowal] = {}
        self.reservations: dict[str, deque] = {}
        self.fine_calculator = FineCalculator()

    def add_book(self, book: Book):
        self.books[book.isbn] = book
        self.reservations[book.isbn] = deque()

    def add_member(self, member: Member):
        self.members[member.member_id] = member

    def search_books(self, query: str, field: str = "title") -> list[Book]:
        query = query.lower()
        return [b for b in self.books.values()
                if query in getattr(b, field, "").lower()]

    def borrow_book(self, member_id: str, isbn: str,
                    loan_days: int = 14) -> Borrowal:
        member = self.members[member_id]
        book = self.books[isbn]

        if not member.can_borrow():
            raise ValueError("Member cannot borrow: overdue books or fines outstanding")

        available = book.available_copies()
        if not available:
            raise ValueError("No available copies; consider reserving")

        copy = available[0]
        today = date.today()
        borrowal = Borrowal(
            borrowal_id=str(uuid.uuid4()),
            member=member,
            copy=copy,
            borrow_date=today,
            due_date=date.fromordinal(today.toordinal() + loan_days),
        )
        copy.status = CopyStatus.CHECKED_OUT
        copy.active_borrowal = borrowal
        member.active_borrowals.append(borrowal)
        self.borrowals[borrowal.borrowal_id] = borrowal
        return borrowal

    def return_book(self, borrowal_id: str) -> float:
        borrowal = self.borrowals[borrowal_id]
        if borrowal.return_date is not None:
            raise ValueError("Book already returned")

        borrowal.return_date = date.today()
        fine = self.fine_calculator.calculate(borrowal)
        if fine > 0:
            borrowal.member.pending_fines += fine

        copy = borrowal.copy
        member = borrowal.member
        member.active_borrowals.remove(borrowal)

        isbn = copy.book.isbn
        queue = self.reservations.get(isbn, deque())
        if queue:
            reservation = queue.popleft()
            copy.status = CopyStatus.RESERVED
            self._notify(reservation.member, copy)
        else:
            copy.status = CopyStatus.AVAILABLE

        copy.active_borrowal = None
        return fine

    def reserve_book(self, member_id: str, isbn: str) -> Reservation:
        member = self.members[member_id]
        book = self.books[isbn]
        reservation = Reservation(
            reservation_id=str(uuid.uuid4()),
            member=member,
            book=book,
            reserved_date=date.today(),
        )
        self.reservations[isbn].append(reservation)
        return reservation

    def _notify(self, member: Member, copy: BookCopy):
        print(f"Notification: {member.name}, copy {copy.copy_id} of "
              f"'{copy.book.title}' is ready for pickup.")
```

---

## Verification

Scenario: Alice (STANDARD) borrows "Clean Code"; Bob reserves it; Alice returns it late.

1. `library.borrow_book("alice", isbn)` — available copy found, status → CHECKED_OUT, borrowal created.
2. Bob calls `reserve_book("bob", isbn)` — queued in `reservations[isbn]`.
3. Alice calls `return_book(borrowal_id)` 5 days overdue → fine = 5.0, added to Alice's `pending_fines`.
4. Reservation queue non-empty → copy status → RESERVED, Bob notified.
5. Next `borrow_book` attempt by Alice fails (`pending_fines > 0`).

---

## Deep Dive & Extensibility

### 1. "Why distinguish Book from BookCopy?"

Book is a logical title record (isbn, metadata). BookCopy is one physical item that can be checked out independently. Without this split you cannot express "3 copies of Clean Code: one checked out, two available."

```python
# Adding a copy to an existing book title
new_copy = BookCopy(copy_id=str(uuid.uuid4()), book=book)
book.copies.append(new_copy)
```

### 2. "How does fine calculation work with a cap?"

Strategy pattern: `FineCalculator.calculate(borrowal)` computes `min(days * rate, max_fine)`. To change the policy (weekend exclusion, per-genre rates), subclass `FineCalculator` and swap it in Library.

```python
class WeekdayFineCalculator(FineCalculator):
    def calculate(self, borrowal: Borrowal) -> float:
        # count only weekdays between due_date and return_date
        due = borrowal.due_date
        ret = borrowal.return_date or date.today()
        days = sum(1 for i in range((ret - due).days)
                   if (due.toordinal() + i) % 7 not in (5, 6))
        return min(days * self.DAILY_RATE, self.MAX_FINE)
```

### 3. "How does the reservation queue work?"

Per-isbn `deque` of `Reservation` objects ordered by `reserved_date`. On return, `popleft()` gives the earliest reserver. The copy is set to RESERVED (not AVAILABLE) so it cannot be grabbed by a walk-in borrower before the notified member arrives.

```python
# Hold period: if reserver doesn't pick up within N days, release
def expire_reservations(self):
    for isbn, queue in self.reservations.items():
        today = date.today()
        while queue:
            r = queue[0]
            if (today - r.reserved_date).days > 3:
                queue.popleft()
            else:
                break
```

### 4. "How do membership tiers set borrow limits?"

`MemberTier` enum stores the limit as its value (`STANDARD=3, PREMIUM=10`). `Member.borrow_limit()` returns `self.tier.value`. Upgrading a member is a single assignment: `member.tier = MemberTier.PREMIUM`.

### 5. "How would you send overdue reminders?"

Observer pattern: register an `OverdueObserver` that the Library calls on a daily scheduler task. The observer iterates all active borrowals and emails members whose due_date has passed.

```python
class OverdueObserver:
    def check(self, library: Library):
        for borrowal in library.borrowals.values():
            if borrowal.return_date is None and borrowal.is_overdue():
                self._send_reminder(borrowal.member, borrowal)

    def _send_reminder(self, member: Member, borrowal: Borrowal):
        print(f"Reminder to {member.email}: return '{borrowal.copy.book.title}'")
```

---

## Interviewer Questions by Level

**Junior**: Why do we need BookCopy separate from Book?  
**Mid-level**: How does the reservation queue prevent a walk-in borrower from taking a reserved copy?  
**Senior**: How would you scale fine calculation to support per-genre, per-member-tier, and time-window policies without modifying Library?

---

## Common Interview Questions

- **Q: Why Book vs BookCopy?** A: Book is a catalog entry (one record per title); BookCopy is a physical item that can be independently checked out, lost, or reserved.
- **Q: How does the reservation queue work?** A: Per-isbn FIFO deque; on return the copy is set to RESERVED and the first reserver is notified, blocking other borrowers until the hold expires.
- **Q: What are the fine calculation edge cases?** A: Cap at max_fine, weekends/holidays excluded if policy requires, zero fine if returned on due_date, fine added to member balance not collected immediately.
- **Q: What if a member with overdue books tries to borrow?** A: `can_borrow()` returns False if any active borrowal is overdue or `pending_fines > 0`; the borrow call raises ValueError.
- **Q: ISBN vs internal ID — which do you use as the key?** A: ISBN for external search (standard, human-readable); internal UUID for copies to handle re-acquisitions of the same edition.
- **Q: How do you handle a copy going LOST?** A: Staff marks it LOST via `copy.status = CopyStatus.LOST`; it no longer appears in `available_copies()`; the borrowal may accrue a replacement charge.
- **Q: How would you support book renewal?** A: Add `renew_borrowal(borrowal_id, extra_days)` that extends `due_date` if no reservation is pending for that isbn.

---

## Related

**Patterns applied here**

- [Factory Pattern](../../03-design-patterns/01-creational/factory-pattern.md)
- [Observer Pattern](../../03-design-patterns/03-behavioral/observer-pattern.md)
- [State Pattern](../../03-design-patterns/03-behavioral/state-pattern.md)
- [Strategy Pattern](../../03-design-patterns/03-behavioral/strategy-pattern.md)

**SOLID focus**: [Single Responsibility](../../02-solid-principles/01-single-responsibility.md) · [Open/Closed](../../02-solid-principles/02-open-closed.md)

**Practice next**

- [Design Hotel Management](../02-frequent-problems/11-design-hotel-management.md)
- [Design Inventory Management](24-design-inventory-management.md)

Lending is reservation plus stock tracking.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
