# Single Responsibility Principle (SRP)

> **Definition**: A class should have one, and only one, reason to change.

## The Problem

When a class has multiple responsibilities, changes to one responsibility can affect the others.

### ❌ Bad Example (Violates SRP)

```java
public class Employee {
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

### ✅ Good Example (Follows SRP)

```java
// Responsibility 1: Employee data (just data, no logic)
public class Employee {
    private String name;
    private double salary;
    
    public Employee(String name, double salary) {
        this.name = name;
        this.salary = salary;
    }
    
    // Getters/setters only
    public String getName() { return name; }
    public double getSalary() { return salary; }
}

// Responsibility 2: Salary calculation
public class SalaryCalculator {
    public double calculateBonus(Employee employee) {
        return employee.getSalary() * 0.1;
    }
    
    public double calculateTax(Employee employee) {
        return employee.getSalary() * 0.2;
    }
}

// Responsibility 3: Database operations
public class EmployeeRepository {
    public void save(Employee employee) {
        Database.execute(
            "INSERT INTO employees VALUES (?, ?)",
            employee.getName(),
            employee.getSalary()
        );
    }
    
    public Employee findById(int id) {
        // Query database and return Employee
    }
}

// Responsibility 4: Report generation
public class EmployeeReportGenerator {
    public String generateReport(Employee employee) {
        return "Employee: " + employee.getName() + 
               ", Salary: $" + employee.getSalary();
    }
    
    public String generatePDF(Employee employee) {
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

### ❌ Bad (God Class)

```java
public class User {
    private String username;
    private String password;
    
    // Data management
    public void setPassword(String password) {
        this.password = password;
    }
    
    // Authentication logic
    public boolean login(String inputPassword) {
        return BCrypt.checkpw(inputPassword, this.password);
    }
    
    // Session management
    public void createSession() {
        SessionManager.create(this.username);
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

---

### ✅ Good (Separated Responsibilities)

```java
// 1. Data model
public class User {
    private String username;
    private String hashedPassword;
    
    public User(String username, String hashedPassword) {
        this.username = username;
        this.hashedPassword = hashedPassword;
    }
    
    public String getUsername() { return username; }
    public String getHashedPassword() { return hashedPassword; }
}

// 2. Authentication logic
public class AuthenticationService {
    public boolean authenticate(User user, String inputPassword) {
        return BCrypt.checkpw(inputPassword, user.getHashedPassword());
    }
}

// 3. Session management
public class SessionManager {
    public void createSession(User user) {
        // Create session for user
    }
    
    public void invalidateSession(String sessionId) {
        // Remove session
    }
}

// 4. Audit logging
public class AuditLogger {
    public void logLoginAttempt(User user, boolean success) {
        Logger.info("Login " + (success ? "successful" : "failed") + 
                   " for user: " + user.getUsername());
    }
}

// 5. Notification service
public class NotificationService {
    public void sendLoginNotification(User user) {
        EmailService.send(
            user.getUsername() + "@example.com",
            "New login detected"
        );
    }
}

// Usage (orchestrate in a controller)
public class LoginController {
    private AuthenticationService authService;
    private SessionManager sessionManager;
    private AuditLogger auditLogger;
    private NotificationService notificationService;
    
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
1. **"What is this class's ONE job?"** If you can't answer in one sentence, it's doing too much.
2. **"How many reasons could this class change?"** More than one = violation.
3. **"If [X] changes, does this class need to change?"** Test for different X (DB schema, UI, business rules)

**Example:**
```
Class: OrderProcessor

Could change if:
- Order validation rules change ❌
- Payment gateway API changes ❌
- Inventory system changes ❌
- Email template changes ❌

→ Too many reasons! Split into: OrderValidator, PaymentService, InventoryService, NotificationService
```

---

## Benefits of SRP

✅ **Easier to understand**: Each class does one thing  
✅ **Easier to test**: Mock dependencies, test single responsibility  
✅ **Easier to maintain**: Change one thing without breaking others  
✅ **Easier to reuse**: Small, focused classes are more reusable  
✅ **Less coupling**: Classes depend on interfaces, not implementations  

---

## Common Mistakes

### Mistake 1: Too Granular

```java
// TOO MUCH! Not every method needs its own class
public class NameGetter {
    public String getName(User user) {
        return user.name;
    }
}
```

**Rule of Thumb**: Class should have 3-10 methods, not 1.

### Mistake 2: Anemic Domain Model

```java
// Bad: All logic in services, data class has no behavior
public class Order {
    public List<OrderItem> items;  // Just data
}

public class OrderService {
    public double calculateTotal(Order order) { ... }
    public void validateOrder(Order order) { ... }
    public void applyDiscount(Order order) { ... }
}

// Better: Move logic to domain model
public class Order {
    private List<OrderItem> items;
    
    public double calculateTotal() { ... }  // Belongs here!
    public void addItem(OrderItem item) { ... }
}

public class OrderValidator {
    public void validate(Order order) { ... }  // Separate responsibility
}
```

---

## Interview Tips

**Q: "Explain SRP with an example"**
- A: "A class should have one reason to change. For example, User class shouldn't handle authentication, database operations, and email notifications - split into UserAuthService, UserRepository, NotificationService."

**Q: "How do you balance SRP with not having too many classes?"**
- A: "Use common sense. Group related methods into one class if they change together. Don't split every method into a class."
