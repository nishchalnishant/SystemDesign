---
module: 06-lld
topic: Architecture and Data
status: unread
tags: [06-lld, database, schema, orm]
---
# Database Schema Design for LLD

> **Difficulty**: Medium to Hard  
> **Key Concepts**: Relational modeling, Normalization, Polymorphic associations, Indexing

At the SDE-3 level, an LLD interview requires you to prove that your beautiful, memory-resident object graph can actually be saved to a disk efficiently.

## Mapping OOP to Relational Databases (ORM)

### 1. Handling Inheritance
In OOP, you might have `Vehicle`, `Car extends Vehicle`, `Truck extends Vehicle`. Relational databases don't naturally support inheritance. How do you map this?

**Strategy 1: Single Table Inheritance (STI)**
One massive `vehicles` table with a `type` column and nullable columns for subclass fields.
- *Pros*: Fast, simple queries.
- *Cons*: Lots of NULLs, doesn't scale if subclasses are vastly different.

**Strategy 2: Class Table Inheritance (Table per Type)**
A `vehicles` table with common fields, and separate `cars` and `trucks` tables with foreign keys back to `vehicles`.
- *Pros*: Clean schema, no NULLs.
- *Cons*: Requires `JOIN`s to load a complete object.

*Interview Tip: Default to Single Table Inheritance for simple hierarchies, and Class Table Inheritance for highly divergent subclasses.*

### 2. Handling Relationships

- **1-to-1 (User to Profile)**: Foreign key on either side with a `UNIQUE` constraint.
- **1-to-Many (Author to Books)**: Foreign key on the "Many" side (Book table has `author_id`).
- **Many-to-Many (Students to Courses)**: Requires a join/junction table (`student_courses`).

## Concurrency at the Database Level

When dealing with systems like BookMyShow or Ticketmaster, two users might try to book the exact same seat at the exact same millisecond. 
In memory, we solve this with `synchronized` blocks. In the database, we use locks.

### Optimistic Locking
Best when conflicts are rare.
- Add a `version` column to the `seats` table.
- When updating: `UPDATE seats SET status = 'BOOKED', version = version + 1 WHERE id = 123 AND version = 5;`
- If someone else booked it, the version will be 6, the query updates 0 rows, and you throw an Exception.

### Pessimistic Locking
Best when conflicts are guaranteed.
- Use `SELECT ... FOR UPDATE`. This locks the row at the database level so no one else can read or write it until your transaction commits.

## Interview Example: Splitwise DB Schema

A junior candidate might struggle to map Splitwise to a DB. An SDE-3 should quickly outline:

**Users Table**
- `id` (PK)
- `name`, `email`

**Expenses Table**
- `id` (PK)
- `creator_id` (FK -> Users)
- `description`, `total_amount`, `currency`, `created_at`

**Expense_Participants (The Join Table)**
- `id` (PK)
- `expense_id` (FK -> Expenses)
- `user_id` (FK -> Users)
- `amount_owed` (Decimal)
- `amount_paid` (Decimal)

*Note how the complex "Greedy Min-Cash Flow algorithm" for simplifying debts operates entirely independently of this schema, proving the value of Clean Architecture!*
