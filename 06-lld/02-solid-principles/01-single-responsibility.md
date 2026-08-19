> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** The Single Responsibility Principle (SRP) — the first SOLID principle; states that a class should have one, and only one, reason to change.
>
> **Key concepts:**
> - The problem: a "God Object" (e.g., an `Employee` class that calculates pay, saves to DB, and formats reports). Changes to any of these three areas require modifying the same class.
> - The fix: split the class by "reason to change" (or actor). `PayCalculator`, `EmployeeRepository`, `EmployeeReportFormatter`.
> - Cohesion: SRP increases cohesion (methods in a class are highly related). Low cohesion implies SRP violation.
> - Coupling: SRP reduces coupling. A change to database schema no longer impacts payroll calculation logic.
> - How to spot violations: look for class names with "And" (e.g., `ReportAndPrinter`), very long classes, or classes importing too many unrelated packages.
>
> **Key takeaway:** A "reason to change" maps to a stakeholder or concern (DBA vs HR vs Product). If two different stakeholders can request changes that touch the same class, you have an SRP violation.

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

```java
class Employee {
    private String name;
    private double salary;

    // Responsibility 1: Employee data management
    public void setName(String name) {
        this.name = name;
    }

    public void setSalary(double salary) {
        this.salary = salary;
    }

    // Responsibility 2: Salary calculation (business logic)
    public double calculateBonus() {
        return salary * 0.1;
    }

    // Responsibility 3: Database operations
    public void save() {
        Database.execute("INSERT INTO employees VALUES (?, ?)", name, salary);
    }

    // Responsibility 4: Report generation
    public String generateReport() {
        return "Employee: " + name + ", Salary: $" + salary;
    }
}
```

**Problems:**
- Database schema change → modify Employee class
- Report format change → modify Employee class
- Bonus calculation change → modify Employee class
- Too many reasons to change!

---

### Good Example (Follows SRP)

```java
// Responsibility 1: Employee data (just data, no logic)
class Employee {
    private final String name;
    private final double salary;

    public Employee(String name, double salary) {
        this.name = name;
        this.salary = salary;
    }

    public String getName() { return name; }
    public double getSalary() { return salary; }
}


// Responsibility 2: Salary calculation
class SalaryCalculator {
    public double calculateBonus(Employee employee) {
        return employee.getSalary() * 0.1;
    }

    public double calculateTax(Employee employee) {
        return employee.getSalary() * 0.2;
    }
}


// Responsibility 3: Database operations
class EmployeeRepository {
    public void save(Employee employee) {
        Database.execute(
            "INSERT INTO employees VALUES (?, ?)",
            employee.getName(),
            employee.getSalary()
        );
    }

    public Employee findById(int id) {
        // Query database and return Employee
        return null;
    }
}


// Responsibility 4: Report generation
class EmployeeReportGenerator {
    public String generateReport(Employee employee) {
        return "Employee: " + employee.getName() + ", Salary: $" + employee.getSalary();
    }

    public void generatePdf(Employee employee) {
        // Generate PDF report
    }
}
```

**Benefits:**
- Each class has one reason to change
- Easy to test (mock database, calculator, etc.)
- Easy to maintain (change report format without touching data model)
- Clear responsibilities

---

## Real-World Example: User Authentication

### Bad (God Class)

```java
class User {
    private String username;
    private String password;

    public User(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    // Authentication logic
    public boolean login(String inputPassword) {
        return BCrypt.checkpw(inputPassword, password);
    }

    // Session management
    public void createSession() {
        SessionManager.create(username);
    }

    // Logging
    public void logLogin() {
        Logger.info("User " + username + " logged in");
    }

    // Email notification
    public void sendLoginEmail() {
        EmailService.send(username + "@example.com", "Login detected");
    }
}
```

### Good (Separated Responsibilities)

```java
// 1. Data model
class User {
    private final String username;
    private final String hashedPassword;

    public User(String username, String hashedPassword) {
        this.username = username;
        this.hashedPassword = hashedPassword;
    }

    public String getUsername() { return username; }
    public String getHashedPassword() { return hashedPassword; }
}


// 2. Authentication logic
class AuthenticationService {
    public boolean authenticate(User user, String inputPassword) {
        return BCrypt.checkpw(inputPassword, user.getHashedPassword());
    }
}


// 3. Session management
class SessionManager {
    public void createSession(User user) {
        // Create session
    }

    public void invalidateSession(String sessionId) {
        // Remove session
    }
}


// 4. Audit logging
class AuditLogger {
    public void logLoginAttempt(User user, boolean success) {
        String status = success ? "successful" : "failed";
        Logger.info("Login " + status + " for user: " + user.getUsername());
    }
}


// 5. Notification service
class NotificationService {
    public void sendLoginNotification(User user) {
        EmailService.send(user.getUsername() + "@example.com", "New login detected");
    }
}


// 6. Orchestration in a controller (one coordinator, not one God)
class LoginController {
    private final AuthenticationService authService;
    private final SessionManager sessionManager;
    private final AuditLogger auditLogger;
    private final NotificationService notificationService;

    public LoginController(
        AuthenticationService authService,
        SessionManager sessionManager,
        AuditLogger auditLogger,
        NotificationService notificationService
    ) {
        this.authService = authService;
        this.sessionManager = sessionManager;
        this.auditLogger = auditLogger;
        this.notificationService = notificationService;
    }

    public void login(String username, String password) {
        User user = userRepository.findByUsername(username);

        if (authService.authenticate(user, password)) {
            sessionManager.createSession(user);
            auditLogger.logLoginAttempt(user, true);
            notificationService.sendLoginNotification(user);
        } else {
            auditLogger.logLoginAttempt(user, false);
        }
    }
}
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
| Utility class dumping ground | `Utils.java` with unrelated methods | Group into `StringUtils`, `DateUtils`, etc. |
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

## Applied In

This concept is used by **23 problems** in this repo — a representative selection:

**Low-Level Design**

- [Design a Parking Lot](../06-problems/01-core-problems/01-design-parking-lot.md)
- [Design a Vending Machine](../06-problems/01-core-problems/04-design-vending-machine.md)
- [Design Splitwise](../06-problems/01-core-problems/05-design-splitwise.md)
- [Design BookMyShow](../06-problems/02-frequent-problems/06-design-bookmyshow.md)
- [Design Elevator System](../06-problems/02-frequent-problems/09-design-elevator-system.md)
- [Design a Nested Comment System](../06-problems/02-frequent-problems/10-design-comment-system.md)
- [Design a Hotel Management System](../06-problems/02-frequent-problems/11-design-hotel-management.md)
- [Design an ATM System](../06-problems/02-frequent-problems/12-design-atm.md)
- …and 15 more

