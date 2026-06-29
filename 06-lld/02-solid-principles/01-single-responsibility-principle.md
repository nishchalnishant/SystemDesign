---
module: 06-lld
topic: Solid Principles
status: unread
tags: [06-lld, system-design, solid-principles]
---
# Single Responsibility Principle (SRP)

**Question**: Your `Employee` class has methods: `setName()`, `setSalary()`, `calculateBonus()`, `save()`, `generateReport()`. The database team changes the schema. The reporting team changes the format. The HR team changes the bonus rules. Every change touches this one class. How many things can break from a single change to `Employee`?

**Problem without SRP**: Every change to any concern — persistence, reporting, payroll — modifies the same class. A database schema change triggers a recompile of `Employee`, which is imported everywhere. A bug in the bonus calculation puts the entire class at risk during testing. Parallel development is impossible: two engineers cannot work on database logic and bonus logic simultaneously without merge conflicts.

**Minimal fix**: Ask "what is the single reason this class should change?" If you have more than one answer, split the class. Move persistence to `EmployeeRepository`, bonus logic to `SalaryCalculator`, reporting to `EmployeeReportGenerator`.

**Full principle**: SRP — A class should have one, and only one, reason to change. A "reason to change" maps to a stakeholder or concern. HR owns salary logic. DBA owns persistence. Product owns reporting. Each concern gets its own class.

> **Analogy**: A chef who only cooks, a waiter who only serves. Don't make the chef also take orders, manage the cash register, AND cook. Each class has one job.

---

## Topic Mindmap

```
[Single Responsibility Principle]
├── Problem It Solves
│   ├── God Class: Employee handles payroll + persistence + reporting
│   ├── Any change touches the whole class — recompile, merge conflicts
│   └── Testing one concern requires testing all concerns together
├── Core Rule
│   ├── One class = one reason to change
│   ├── "Reason to change" maps to a stakeholder or concern
│   └── HR changes salary logic; DBA changes schema; Product changes report
├── The Fix
│   ├── Employee: name, salary fields only
│   ├── SalaryCalculator: calculateBonus() — owned by HR
│   ├── EmployeeRepository: save/find — owned by DBA
│   └── EmployeeReportGenerator: generateReport() — owned by Product
├── Identifying Violations
│   ├── Class name includes "And" or "Manager" or "Handler" (broad scope)
│   ├── Multiple teams touch the same file in every sprint
│   ├── Unit test requires mocking 4+ unrelated dependencies
│   └── One bug fix breaks a seemingly unrelated feature
├── Real-World Example
│   ├── UserAuthService doing auth + email + logging = violation
│   └── Split: AuthService, EmailService, AuditLogger
├── Trade-offs
│   ├── More classes = more files to navigate
│   ├── Risk of over-splitting: 50 single-method classes is worse
│   └── Rule of thumb: split when two concerns change at different rates
├── Relation to Other SOLID Principles
│   ├── SRP is the foundation for OCP — one concern is easier to extend
│   ├── SRP reduces ISP violations — small class → naturally small interface
│   └── DIP is easier with SRP — single-purpose class is easier to abstract
└── Interview Angles
    ├── What is the difference between SRP and separation of concerns?
    ├── How do you identify when a class has too many responsibilities?
    └── Can SRP lead to over-engineering? How do you balance it?
```

## The Problem

When a class has multiple responsibilities, changes to one responsibility can break the others. The class becomes a "God Class" — it knows too much and does too much.

### Bad Example (Violates SRP)

```python
class Employee:
    def __init__(self, name, salary):
        self.name = name
        self.salary = salary
    
    # Responsibility 1: Employee data management
    def set_name(self, name):
        self.name = name
        
    def set_salary(self, salary):
        self.salary = salary
        
    # Responsibility 2: Salary calculation (business logic)
    def calculate_bonus(self):
        return self.salary * 0.1
        
    # Responsibility 3: Database operations
    def save(self):
        Database.execute("INSERT INTO employees VALUES (?, ?)", self.name, self.salary)
        
    # Responsibility 4: Report generation
    def generate_report(self):
        return f"Employee: {self.name}, Salary: ${self.salary}"
```

**Problems:**
- Database schema change → modify Employee class
- Report format change → modify Employee class
- Bonus calculation change → modify Employee class
- Too many reasons to change!

---

### Good Example (Follows SRP)

```python
# Responsibility 1: Employee data (just data, no logic)
class Employee:
    def __init__(self, name, salary):
        self._name = name
        self._salary = salary
        
    def get_name(self):
        return self._name
        
    def get_salary(self):
        return self._salary

# Responsibility 2: Salary calculation
class SalaryCalculator:
    def calculate_bonus(self, employee):
        return employee.get_salary() * 0.1
        
    def calculate_tax(self, employee):
        return employee.get_salary() * 0.2

# Responsibility 3: Database operations
class EmployeeRepository:
    def save(self, employee):
        Database.execute(
            "INSERT INTO employees VALUES (?, ?)",
            employee.get_name(),
            employee.get_salary()
        )
        
    def find_by_id(self, employee_id):
        # Query database and return Employee
        pass

# Responsibility 4: Report generation
class EmployeeReportGenerator:
    def generate_report(self, employee):
        return f"Employee: {employee.get_name()}, Salary: ${employee.get_salary()}"
        
    def generate_pdf(self, employee):
        # Generate PDF report
        pass
```

**Benefits:**
- Each class has one reason to change
- Easy to test (mock database, calculator, etc.)
- Easy to maintain (change report format without touching data model)
- Clear responsibilities

---

## Real-World Example: User Authentication

### Bad (God Class)

```python
class User:
    def __init__(self, username, password_hash):
        self.username = username
        self.password_hash = password_hash
        
    def set_password(self, password_hash):
        self.password_hash = password_hash
        
    # Authentication logic
    def login(self, input_password):
        return bcrypt.checkpw(input_password.encode(), self.password_hash.encode())
        
    # Session management
    def create_session(self):
        SessionManager.create(self.username)
        
    # Logging
    def log_login(self):
        Logger.info(f"User {self.username} logged in")
        
    # Email notification
    def send_login_email(self):
        EmailService.send(f"{self.username}@example.com", "Login detected")
```

### Good (Separated Responsibilities)

```python
# 1. Data model
class User:
    def __init__(self, username, hashed_password):
        self._username = username
        self._hashed_password = hashed_password
        
    def get_username(self):
        return self._username
        
    def get_hashed_password(self):
        return self._hashed_password

# 2. Authentication logic
class AuthenticationService:
    def authenticate(self, user, input_password):
        return bcrypt.checkpw(input_password.encode(), user.get_hashed_password().encode())

# 3. Session management
class SessionManager:
    def create_session(self, user):
        # Create session
        pass
        
    def invalidate_session(self, session_id):
        # Remove session
        pass

# 4. Audit logging
class AuditLogger:
    def log_login_attempt(self, user, success):
        status = "successful" if success else "failed"
        Logger.info(f"Login {status} for user: {user.get_username()}")

# 5. Notification service
class NotificationService:
    def send_login_notification(self, user):
        EmailService.send(f"{user.get_username()}@example.com", "New login detected")

# 6. Orchestration in a controller (one coordinator, not one God)
class LoginController:
    def __init__(self, user_repository, auth_service, 
                 session_manager, audit_logger, 
                 notification_service):
        self.user_repository = user_repository
        self.auth_service = auth_service
        self.session_manager = session_manager
        self.audit_logger = audit_logger
        self.notification_service = notification_service
        
    def login(self, username, password):
        user = self.user_repository.find_by_username(username)
        
        if self.auth_service.authenticate(user, password):
            self.session_manager.create_session(user)
            self.audit_logger.log_login_attempt(user, True)
            self.notification_service.send_login_notification(user)
        else:
            self.audit_logger.log_login_attempt(user, False)
```

---

## How to Identify SRP Violations

Ask yourself:
1. **"What is this class's ONE job?"** — If you can't answer in one sentence, it's doing too much.
2. **"How many reasons could this class change?"** — More than one = violation.
3. **"If [X] changes, does this class need to change?"** — Test for different X (DB schema, UI, business rules).

**Example:**
```
Class: OrderProcessor

Could change if:
- Order validation rules change
- Payment gateway API changes
- Inventory system changes
- Email template changes

Too many reasons! Split into: OrderValidator, PaymentService, InventoryService, NotificationService
```

---

## When to Use in Interviews

- Any time an interviewer shows you a "fat" class doing data management, persistence, notifications, AND business logic.
- When designing a system from scratch: draw the class boundaries first, ask "who is responsible for X?"
- Common interview question: "Design a logging system" — SRP says the logger should log, not format AND persist AND send alerts.

**Talking point**: "I'd separate concerns so that if we swap databases, we don't touch the business logic. If we change the email provider, we don't touch the data model."

---

## Common Violations

| Violation | Symptom | Fix |
|---|---|---|
| God Class | One class with 20+ methods | Split by responsibility domain |
| Utility class dumping ground | `utils.py` with unrelated functions | Group into `string_utils.py`, `date_utils.py`, etc. |
| Mixed persistence + logic | `save()` method on domain object | Extract `Repository` class |
| Controller doing business logic | 100-line controller method | Extract `Service` class |

---

## Pros of SRP

- Easier to understand: each class does one thing
- Easier to test: mock dependencies, test single responsibility
- Easier to maintain: change one thing without breaking others
- Easier to reuse: small, focused classes are more reusable
- Less coupling: classes depend on interfaces, not implementations

---

## Interview Tips

**Q: "Explain SRP with an example"**
- "A class should have one reason to change. User class shouldn't handle authentication, database operations, and email notifications — split into UserAuthService, UserRepository, NotificationService."

**Q: "How do you balance SRP with not having too many classes?"**
- "Use common sense. Group related methods into one class if they change together for the same reason. Don't split every method into its own class — that's over-engineering."

---

## Interviewer Follow-Up Questions

- "How do you decide if a class violates SRP? What's the test?" → The test: if you change class X, why would you change it? If the answer is "either because business logic changed OR because the DB schema changed OR because the email template changed", that's 3 reasons — 3 responsibilities. SRP says one class should have only one reason to change. An `OrderService` that validates, persists, and sends emails has 3 reasons to change. Split into `OrderValidator`, `OrderRepository`, `OrderNotifier`.
- "SRP says 'one reason to change'. But every class changes for business reasons. Isn't that circular?" → "Reason to change" means a type of change, not any change. An `OrderRepository` changes when the DB schema changes (storage concern). An `OrderCalculator` changes when pricing rules change (business logic concern). These are different actors driving change: the DBA changes schema, the product manager changes pricing rules. SRP groups code by actor — code that changes together for the same actor lives together.
- "Can SRP go too far? What's an example of over-splitting?" → Yes — splitting a `User` class into `UserFirstName`, `UserLastName`, `UserEmail` is absurd. Each field is a separate "responsibility" only in a trivial sense — they all change for the same reason (user data model changes). The practical test: if splitting forces callers to coordinate multiple classes to do one operation, you've over-split. SRP is about cohesion at the right level of abstraction, not maximizing class count.
- "You have a 500-line class that does everything. How do you refactor it toward SRP without breaking existing behavior?" → Strangler Fig pattern at the method level: (1) Write tests for the current behavior. (2) Identify the most distinct responsibility (e.g., the persistence logic). (3) Extract it into a new class, make the original class delegate to it. (4) Run tests to verify no regression. Repeat for each responsibility. Never refactor without tests covering the extracted behavior — you need a safety net.
