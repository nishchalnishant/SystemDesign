---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, system-design, problems]
---
# Design Library Management System

> **Difficulty**: Medium
> **Topics**: Inventory Management, Reservation Queue, Fine Calculation
> **Key Concepts**: Book copies vs. catalog, due date tracking, hold queue fairness.

---

## What Breaks Without This Design?

```python
class Library:
    def __init__(self):
        self._book_stock: dict[str, int] = {}   # isbn → available count
        self._borrowed_by: dict[str, str] = {}  # isbn → member_id

    def borrow_book(self, isbn: str, member_id: str) -> bool:
        if self._book_stock.get(isbn, 0) > 0:
            self._book_stock[isbn] -= 1
            self._borrowed_by[isbn] = member_id
            return True
        return False
```

**Concrete failures**:
1. **One borrower per book**: `borrowedBy` maps ISBN → single memberId. A library has 5 copies — the map can only record one.
2. **No due date**: No return deadline, no fine calculation.
3. **No reservation queue**: When a book is unavailable, there's no way to hold it for the next requester in order.
4. **BookItem vs. Book conflation**: ISBN represents the book catalog entry. A physical copy (BookItem) has its own ID, condition, and borrow record. Returning "the book" is ambiguous — which physical copy?

---

## Derive the Class Structure

**Force 1 — Multiple physical copies share one catalog entry**: Extract `Book` (catalog: ISBN, title, author) and `BookItem` (physical copy: barcode, condition, borrowRecord).

**Force 2 — Each borrow needs a return deadline**: Extract `BorrowRecord` with `borrowDate`, `dueDate`, `returnDate`. Fine = `max(0, (returnDate - dueDate).days * ratePerDay)`.

**Force 3 — Hold queue must be FIFO per book**: Each `Book` has a `Queue<Reservation>`. When a copy is returned, dequeue next reservation and notify member.

**Force 4 — Members have borrow limits and fine gates**: Extract `Member` with `borrowedCount`, `unpaidFines`. Block new borrows if fines outstanding.

```
God class → Book (catalog)
          → BookItem (physical copy, per-copy borrow state)
          → BorrowRecord (who borrowed, when, due)
          → Reservation (hold queue entry)
          → Member (user, limits, fines)
          → FineCalculator (pluggable rate strategy)
          → LibraryService (orchestration)
```

---

## Phase 1: Requirements

**Actors**: Member (borrows/returns/reserves), Librarian (adds books, manages catalog), System (computes fines, sends notifications).

**Must-have**:
- Search catalog by title / author / ISBN
- Borrow available BookItem; enforce max borrow limit (e.g., 5 books)
- Return BookItem; compute and record fine if overdue
- Reserve a book when all copies are checked out (FIFO queue)
- Block member with unpaid fines from borrowing

---

## Phase 2: Use Cases

### UC1: Borrow Book
1. Member searches for book by ISBN.
2. System finds a `BookItem` with status `AVAILABLE`.
3. System checks member: borrows < limit AND fines == 0.
4. System creates `BorrowRecord`, sets `BookItem.status = BORROWED`.
5. Return due date = today + loanPeriodDays.

### UC2: Return Book
1. Member returns `BookItem` (identified by barcode).
2. System retrieves `BorrowRecord`, computes fine if `returnDate > dueDate`.
3. Fine added to `member.unpaidFines`.
4. If reservation queue non-empty → dequeue next reservation, notify member, hold `BookItem` for them.
5. Else → `BookItem.status = AVAILABLE`.

### UC3: Reserve Book
1. All copies of ISBN are `BORROWED`.
2. System creates `Reservation` and appends to book's queue.
3. On next return for that ISBN, first reservation gets notified.

---

## Phase 3: Class Diagram

```
Book
  - isbn: String
  - title: String
  - author: String
  - items: List<BookItem>
  - reservationQueue: Queue<Reservation>
  + getAvailableItem(): Optional<BookItem>
  + addReservation(Reservation): void
  + nextReservation(): Optional<Reservation>

BookItem
  - barcode: String
  - book: Book
  - status: BookItemStatus   // AVAILABLE, BORROWED, RESERVED, LOST
  - currentRecord: BorrowRecord

BorrowRecord
  - recordId: String
  - member: Member
  - bookItem: BookItem
  - borrowDate: LocalDate
  - dueDate: LocalDate
  - returnDate: LocalDate    // null until returned
  + computeFine(FineCalculator): double

Reservation
  - reservationId: String
  - member: Member
  - book: Book
  - reservedAt: LocalDateTime
  - status: ReservationStatus  // PENDING, FULFILLED, CANCELLED

Member
  - memberId: String
  - name: String
  - activeRecords: List<BorrowRecord>
  - unpaidFines: double
  + canBorrow(maxLimit): boolean  // activeRecords.size() < maxLimit && unpaidFines == 0
  + payFine(amount): void

FineCalculator <<interface>>
  + calculate(dueDate: LocalDate, returnDate: LocalDate): double

PerDayFineCalculator implements FineCalculator
  - ratePerDay: double   // e.g. ₹5/day

LibraryService
  + borrowBook(memberId, isbn): BorrowRecord
  + returnBook(barcode): double   // returns fine amount
  + reserveBook(memberId, isbn): Reservation
  + searchByTitle(title): List<Book>
```

---

## Phase 4: Design Patterns

| Pattern | Where | Why |
|---------|-------|-----|
| **Strategy** | `FineCalculator` | Swap per-day / flat / tiered fine logic |
| **Observer** | Reservation notification | Member notified when reserved book becomes available |
| **Factory** | `BookItemFactory` | Create `BookItem` with correct initial status |
| **Iterator** | Reservation queue | Dequeue in strict FIFO order |

---

## Phase 5: Key Implementation

### Borrow Book

```python
import datetime

def borrow_book(self, member_id: str, isbn: str) -> BorrowRecord:
    member = self._member_repo.find_by_id(member_id)
    if not member.can_borrow(self.MAX_BORROW_LIMIT):
        raise BorrowNotAllowedException("Fine outstanding or limit reached")

    book = self._catalog.find_by_isbn(isbn)
    item = book.get_available_item()
    if item is None:
        raise NoCopyAvailableException(isbn)

    today = datetime.date.today()
    due = today + datetime.timedelta(days=self.LOAN_PERIOD_DAYS)
    record = BorrowRecord(member, item, today, due)
    item.current_record = record
    item.status = BookItemStatus.BORROWED
    member.active_records.append(record)
    return record
```

### Return Book

```python
def return_book(self, barcode: str) -> float:
    item = self._item_repo.find_by_barcode(barcode)
    record = item.current_record
    record.return_date = datetime.date.today()

    fine = record.compute_fine(self._fine_calculator)
    record.member.add_fine(fine)
    record.member.active_records.remove(record)

    book = item.book
    next_reservation = book.next_reservation()
    if next_reservation is not None:
        item.status = BookItemStatus.RESERVED
        self._notification_service.notify(next_reservation.member, book)
    else:
        item.status = BookItemStatus.AVAILABLE
    item.current_record = None
    return fine
```

### Fine Calculation

```python
import datetime

class PerDayFineCalculator(FineCalculator):
    def __init__(self, rate_per_day: float) -> None:
        self._rate_per_day = rate_per_day

    def calculate(self, due_date: datetime.date, return_date: datetime.date) -> float:
        days_late = (return_date - due_date).days
        return max(0.0, days_late * self._rate_per_day)
```

---

## Interview Tips

- **Book vs. BookItem distinction is the key insight**: State it upfront — interviewers probe whether you conflate catalog and physical inventory.
- **Reservation fairness**: FIFO via `Queue<Reservation>` — mention that `LinkedList` or `ArrayDeque` both work; `PriorityQueue` would be wrong (changes ordering).
- **Fine gate**: Block borrows while fines are unpaid — simple `unpaidFines > 0` check in `canBorrow()`.
- **Concurrency follow-up**: If two members try to borrow the last copy simultaneously, `getAvailableItem()` must be synchronized or use optimistic locking on `BookItem.status`.
