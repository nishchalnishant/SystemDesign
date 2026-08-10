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
- isbn: String
- title: String
- author: String
- genre: String
- copies: List<BookCopy>
+ availableCopies(): List<BookCopy>
```

### BookCopy

| Requirement | What BookCopy must track |
|-------------|--------------------------|
| Physical identity | copy_id, book reference |
| Availability | status: CopyStatus |
| Current loan | active_borrowal: Optional[Borrowal] |

```
class BookCopy:
- copyId: String
- book: Book
- status: CopyStatus
- activeBorrowal: Borrowal (nullable)
```

### Member

| Requirement | What Member must track |
|-------------|------------------------|
| Identity | member_id, name, email |
| Tier | tier: MemberTier → borrow limit |
| Activity | active_borrowals: list[Borrowal], pending_fines: float |

```
class Member:
- memberId: String
- name: String
- email: String
- tier: MemberTier
- activeBorrowals: List<Borrowal>
- pendingFines: double
+ borrowLimit(): int
+ canBorrow(): boolean
```

### Borrowal

| Requirement | What Borrowal must track |
|-------------|--------------------------|
| Loan record | borrowal_id, member, copy, borrow_date, due_date |
| Return | return_date (None until returned) |

```
class Borrowal:
- borrowalId: String
- member: Member
- copy: BookCopy
- borrowDate: LocalDate
- dueDate: LocalDate
- returnDate: LocalDate (nullable)
+ isOverdue(): boolean
+ overdueDays(): int
```

### Library

```
class Library:
- books: Map<String, Book>            // isbn -> Book
- members: Map<String, Member>        // memberId -> Member
- reservations: Map<String, Deque<Reservation>>  // isbn -> queue
- fineCalculator: FineCalculator
+ searchBooks(query: String, field: String): List<Book>
+ borrowBook(memberId: String, isbn: String): Borrowal
+ returnBook(borrowalId: String): double
+ reserveBook(memberId: String, isbn: String): Reservation
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

```java
import java.time.LocalDate;
import java.util.*;

enum CopyStatus {
    AVAILABLE,
    CHECKED_OUT,
    RESERVED,
    LOST
}

enum MemberTier {
    STANDARD(3),
    PREMIUM(10);

    private final int limit;

    MemberTier(int limit) {
        this.limit = limit;
    }

    public int getLimit() {
        return limit;
    }
}

class Book {
    private final String isbn;
    private final String title;
    private final String author;
    private final String genre;
    private final List<BookCopy> copies = new ArrayList<>();

    public Book(String isbn, String title, String author, String genre) {
        this.isbn = isbn;
        this.title = title;
        this.author = author;
        this.genre = genre;
    }

    public List<BookCopy> availableCopies() {
        List<BookCopy> result = new ArrayList<>();
        for (BookCopy c : copies) {
            if (c.getStatus() == CopyStatus.AVAILABLE) {
                result.add(c);
            }
        }
        return result;
    }

    public String getIsbn() { return isbn; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getGenre() { return genre; }
    public List<BookCopy> getCopies() { return copies; }
}

class BookCopy {
    private final String copyId;
    private final Book book;
    private CopyStatus status;
    private Borrowal activeBorrowal;

    public BookCopy(String copyId, Book book) {
        this.copyId = copyId;
        this.book = book;
        this.status = CopyStatus.AVAILABLE;
        this.activeBorrowal = null;
    }

    public String getCopyId() { return copyId; }
    public Book getBook() { return book; }
    public CopyStatus getStatus() { return status; }
    public void setStatus(CopyStatus status) { this.status = status; }
    public Borrowal getActiveBorrowal() { return activeBorrowal; }
    public void setActiveBorrowal(Borrowal activeBorrowal) { this.activeBorrowal = activeBorrowal; }
}

class Member {
    private final String memberId;
    private final String name;
    private final String email;
    private MemberTier tier;
    private final List<Borrowal> activeBorrowals = new ArrayList<>();
    private double pendingFines;

    public Member(String memberId, String name, String email, MemberTier tier) {
        this.memberId = memberId;
        this.name = name;
        this.email = email;
        this.tier = tier;
        this.pendingFines = 0.0;
    }

    public int borrowLimit() {
        return tier.getLimit();
    }

    public boolean canBorrow() {
        boolean overdue = activeBorrowals.stream().anyMatch(Borrowal::isOverdue);
        return !overdue
                && pendingFines == 0.0
                && activeBorrowals.size() < borrowLimit();
    }

    public String getMemberId() { return memberId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public MemberTier getTier() { return tier; }
    public void setTier(MemberTier tier) { this.tier = tier; }
    public List<Borrowal> getActiveBorrowals() { return activeBorrowals; }
    public double getPendingFines() { return pendingFines; }
    public void setPendingFines(double pendingFines) { this.pendingFines = pendingFines; }
}

class Borrowal {
    private final String borrowalId;
    private final Member member;
    private final BookCopy copy;
    private final LocalDate borrowDate;
    private final LocalDate dueDate;
    private LocalDate returnDate;

    public Borrowal(String borrowalId, Member member, BookCopy copy,
                     LocalDate borrowDate, LocalDate dueDate) {
        this.borrowalId = borrowalId;
        this.member = member;
        this.copy = copy;
        this.borrowDate = borrowDate;
        this.dueDate = dueDate;
        this.returnDate = null;
    }

    public boolean isOverdue() {
        LocalDate check = (returnDate != null) ? returnDate : LocalDate.now();
        return check.isAfter(dueDate);
    }

    public int overdueDays() {
        if (!isOverdue()) {
            return 0;
        }
        LocalDate check = (returnDate != null) ? returnDate : LocalDate.now();
        return (int) java.time.temporal.ChronoUnit.DAYS.between(dueDate, check);
    }

    public String getBorrowalId() { return borrowalId; }
    public Member getMember() { return member; }
    public BookCopy getCopy() { return copy; }
    public LocalDate getBorrowDate() { return borrowDate; }
    public LocalDate getDueDate() { return dueDate; }
    public LocalDate getReturnDate() { return returnDate; }
    public void setReturnDate(LocalDate returnDate) { this.returnDate = returnDate; }
}

class Reservation {
    private final String reservationId;
    private final Member member;
    private final Book book;
    private final LocalDate reservedDate;

    public Reservation(String reservationId, Member member, Book book, LocalDate reservedDate) {
        this.reservationId = reservationId;
        this.member = member;
        this.book = book;
        this.reservedDate = reservedDate;
    }

    public String getReservationId() { return reservationId; }
    public Member getMember() { return member; }
    public Book getBook() { return book; }
    public LocalDate getReservedDate() { return reservedDate; }
}

class FineCalculator {
    static final double DAILY_RATE = 1.0;   // dollars per day
    static final double MAX_FINE = 25.0;

    public double calculate(Borrowal borrowal) {
        int days = borrowal.overdueDays();
        return Math.min(days * DAILY_RATE, MAX_FINE);
    }
}

class Library {
    private final Map<String, Book> books = new HashMap<>();
    private final Map<String, Member> members = new HashMap<>();
    private final Map<String, Borrowal> borrowals = new HashMap<>();
    private final Map<String, Deque<Reservation>> reservations = new HashMap<>();
    private final FineCalculator fineCalculator = new FineCalculator();

    public void addBook(Book book) {
        books.put(book.getIsbn(), book);
        reservations.put(book.getIsbn(), new ArrayDeque<>());
    }

    public void addMember(Member member) {
        members.put(member.getMemberId(), member);
    }

    public List<Book> searchBooks(String query, String field) {
        String q = query.toLowerCase();
        List<Book> result = new ArrayList<>();
        for (Book b : books.values()) {
            String value = switch (field) {
                case "title" -> b.getTitle();
                case "author" -> b.getAuthor();
                case "isbn" -> b.getIsbn();
                case "genre" -> b.getGenre();
                default -> "";
            };
            if (value.toLowerCase().contains(q)) {
                result.add(b);
            }
        }
        return result;
    }

    public Borrowal borrowBook(String memberId, String isbn, int loanDays) {
        Member member = members.get(memberId);
        Book book = books.get(isbn);

        if (!member.canBorrow()) {
            throw new IllegalStateException("Member cannot borrow: overdue books or fines outstanding");
        }

        List<BookCopy> available = book.availableCopies();
        if (available.isEmpty()) {
            throw new IllegalStateException("No available copies; consider reserving");
        }

        BookCopy copy = available.get(0);
        LocalDate today = LocalDate.now();
        Borrowal borrowal = new Borrowal(
                UUID.randomUUID().toString(),
                member,
                copy,
                today,
                today.plusDays(loanDays)
        );
        copy.setStatus(CopyStatus.CHECKED_OUT);
        copy.setActiveBorrowal(borrowal);
        member.getActiveBorrowals().add(borrowal);
        borrowals.put(borrowal.getBorrowalId(), borrowal);
        return borrowal;
    }

    public Borrowal borrowBook(String memberId, String isbn) {
        return borrowBook(memberId, isbn, 14);
    }

    public double returnBook(String borrowalId) {
        Borrowal borrowal = borrowals.get(borrowalId);
        if (borrowal.getReturnDate() != null) {
            throw new IllegalStateException("Book already returned");
        }

        borrowal.setReturnDate(LocalDate.now());
        double fine = fineCalculator.calculate(borrowal);
        if (fine > 0) {
            borrowal.getMember().setPendingFines(borrowal.getMember().getPendingFines() + fine);
        }

        BookCopy copy = borrowal.getCopy();
        Member member = borrowal.getMember();
        member.getActiveBorrowals().remove(borrowal);

        String isbn = copy.getBook().getIsbn();
        Deque<Reservation> queue = reservations.getOrDefault(isbn, new ArrayDeque<>());
        if (!queue.isEmpty()) {
            Reservation reservation = queue.pollFirst();
            copy.setStatus(CopyStatus.RESERVED);
            notify(reservation.getMember(), copy);
        } else {
            copy.setStatus(CopyStatus.AVAILABLE);
        }

        copy.setActiveBorrowal(null);
        return fine;
    }

    public Reservation reserveBook(String memberId, String isbn) {
        Member member = members.get(memberId);
        Book book = books.get(isbn);
        Reservation reservation = new Reservation(
                UUID.randomUUID().toString(),
                member,
                book,
                LocalDate.now()
        );
        reservations.get(isbn).addLast(reservation);
        return reservation;
    }

    private void notify(Member member, BookCopy copy) {
        System.out.println(String.format(
                "Notification: %s, copy %s of '%s' is ready for pickup.",
                member.getName(), copy.getCopyId(), copy.getBook().getTitle()));
    }

    public Map<String, Borrowal> getBorrowals() { return borrowals; }
}
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

```java
// Adding a copy to an existing book title
BookCopy newCopy = new BookCopy(UUID.randomUUID().toString(), book);
book.getCopies().add(newCopy);
```

### 2. "How does fine calculation work with a cap?"

Strategy pattern: `FineCalculator.calculate(borrowal)` computes `min(days * rate, max_fine)`. To change the policy (weekend exclusion, per-genre rates), subclass `FineCalculator` and swap it in Library.

```java
class WeekdayFineCalculator extends FineCalculator {
    @Override
    public double calculate(Borrowal borrowal) {
        // count only weekdays between due_date and return_date
        LocalDate due = borrowal.getDueDate();
        LocalDate ret = (borrowal.getReturnDate() != null) ? borrowal.getReturnDate() : LocalDate.now();
        long totalDays = java.time.temporal.ChronoUnit.DAYS.between(due, ret);
        int days = 0;
        for (long i = 0; i < totalDays; i++) {
            LocalDate d = due.plusDays(i);
            java.time.DayOfWeek dow = d.getDayOfWeek();
            if (dow != java.time.DayOfWeek.SATURDAY && dow != java.time.DayOfWeek.SUNDAY) {
                days++;
            }
        }
        return Math.min(days * DAILY_RATE, MAX_FINE);
    }
}
```

### 3. "How does the reservation queue work?"

Per-isbn `deque` of `Reservation` objects ordered by `reserved_date`. On return, `popleft()` gives the earliest reserver. The copy is set to RESERVED (not AVAILABLE) so it cannot be grabbed by a walk-in borrower before the notified member arrives.

```java
// Hold period: if reserver doesn't pick up within N days, release
public void expireReservations() {
    LocalDate today = LocalDate.now();
    for (Deque<Reservation> queue : reservations.values()) {
        while (!queue.isEmpty()) {
            Reservation r = queue.peekFirst();
            long daysWaiting = java.time.temporal.ChronoUnit.DAYS.between(r.getReservedDate(), today);
            if (daysWaiting > 3) {
                queue.pollFirst();
            } else {
                break;
            }
        }
    }
}
```

### 4. "How do membership tiers set borrow limits?"

`MemberTier` enum stores the limit as its value (`STANDARD=3, PREMIUM=10`). `Member.borrow_limit()` returns `self.tier.value`. Upgrading a member is a single assignment: `member.tier = MemberTier.PREMIUM`.

### 5. "How would you send overdue reminders?"

Observer pattern: register an `OverdueObserver` that the Library calls on a daily scheduler task. The observer iterates all active borrowals and emails members whose due_date has passed.

```java
class OverdueObserver {
    public void check(Library library) {
        for (Borrowal borrowal : library.getBorrowals().values()) {
            if (borrowal.getReturnDate() == null && borrowal.isOverdue()) {
                sendReminder(borrowal.getMember(), borrowal);
            }
        }
    }

    private void sendReminder(Member member, Borrowal borrowal) {
        System.out.println(String.format(
                "Reminder to %s: return '%s'",
                member.getEmail(), borrowal.getCopy().getBook().getTitle()));
    }
}
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
