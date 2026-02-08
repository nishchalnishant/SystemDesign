# Observer Pattern

> **Category**: Behavioral Pattern  
> **Purpose**: Define a one-to-many dependency so that when one object changes state, all its dependents are notified and updated automatically.

## When to Use

✅ Event handling systems (listeners)  
✅ Pub-Sub scenarios (Stock market, News feed, Notifications)  
✅ Keeping UI elements consistent with data models (MVC architecture)

## Implementation

### Example: Stock Market Ticker

```java
import java.util.ArrayList;
import java.util.List;

// 1. Subject (Observable)
interface StockSubject {
    void registerObserver(StockObserver o);
    void removeObserver(StockObserver o);
    void notifyObservers();
}

class StockMarket implements StockSubject {
    private List<StockObserver> observers;
    private String stockSymbol;
    private double price;
    
    public StockMarket() {
        observers = new ArrayList<>();
    }
    
    public void setPrice(String stockSymbol, double price) {
        this.stockSymbol = stockSymbol;
        this.price = price;
        notifyObservers(); // State changed, notify all!
    }
    
    @Override
    public void registerObserver(StockObserver o) {
        observers.add(o);
    }
    
    @Override
    public void removeObserver(StockObserver o) {
        observers.remove(o);
    }
    
    @Override
    public void notifyObservers() {
        for (StockObserver observer : observers) {
            observer.update(stockSymbol, price);
        }
    }
}

// 2. Observer Interface
interface StockObserver {
    void update(String stockSymbol, double price);
}

// 3. Concrete Observers
class MobileApp implements StockObserver {
    private String name;
    
    public MobileApp(String name) { this.name = name; }
    
    @Override
    public void update(String stockSymbol, double price) {
        System.out.println("Mobile App (" + name + "): " + stockSymbol + " is now $" + price);
    }
}

class DisplayBoard implements StockObserver {
    @Override
    public void update(String stockSymbol, double price) {
        System.out.println("Wall Display: " + stockSymbol + " -> " + price);
    }
}

// Usage
public class Main {
    public static void main(String[] args) {
        StockMarket nasdaq = new StockMarket();
        
        StockObserver app1 = new MobileApp("User A");
        StockObserver app2 = new MobileApp("User B");
        StockObserver board = new DisplayBoard();
        
        // Register
        nasdaq.registerObserver(app1);
        nasdaq.registerObserver(app2);
        nasdaq.registerObserver(board);
        
        // State change
        System.out.println("--- Market Open ---");
        nasdaq.setPrice("AAPL", 150.00);
        
        System.out.println("--- Market Update ---");
        nasdaq.removeObserver(app2); // User B unsubscribes
        nasdaq.setPrice("AAPL", 155.00);
    }
}
```

---

## Output

```
--- Market Open ---
Mobile App (User A): AAPL is now $150.0
Mobile App (User B): AAPL is now $150.0
Wall Display: AAPL -> 150.0
--- Market Update ---
Mobile App (User A): AAPL is now $155.0
Wall Display: AAPL -> 155.0
```

---

## Push vs Pull Model

### Push Model (Used above)
Subject sends data to observer: `observer.update(data)`
- **Pros**: Observer gets specific info immediately.
- **Cons**: Subject must know what observer needs (coupling).

### Pull Model
Subject notifies update, Observer behaves: `observer.update()` calls `subject.getData()`
- **Pros**: Flexible, Observer fetches only what it wants.
- **Cons**: Two calls (notify + get).

---

## Real-World Examples

1. **Java Swing / AWS**: `addActionListener()`
2. **React/Redux**: Store updates -> Components re-render
3. **Kafka/RabbitMQ**: Pub-Sub messaging (distributed Observer)
4. **YouTube**: Channel (Subject) -> Subscribers (Observers)

---

## Code Example: Java Built-in (Deprecated) vs PropertyChangeListener

Java had `java.util.Observable` (now deprecated). Use `PropertyChangeListener`:

```java
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

class NewsAgency {
    private String news;
    private PropertyChangeSupport support; // Helper class

    public NewsAgency() {
        support = new PropertyChangeSupport(this);
    }

    public void addPropertyChangeListener(PropertyChangeListener pcl) {
        support.addPropertyChangeListener(pcl);
    }

    public void setNews(String value) {
        support.firePropertyChange("news", this.news, value);
        this.news = value;
    }
}
```

---

## Pros & Cons

**Pros:**
✅ **Loose Coupling**: Subject doesn't strictly know concrete Observer classes.  
✅ **Dynamic Relationships**: Add/remove subscribers at runtime.  
✅ **Open/Closed**: Add new Observer types without modifying Subject.

**Cons:**
❌ **Memory Leaks**: "Lapsed Listener Problem" - if observers aren't removed, they aren't garbage collected.  
❌ **Ordering**: Notification order is usually random/undefined.
