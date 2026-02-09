# Proxy Pattern

> **Type**: Structural  
> **Purpose**: Provides a placeholder for another object to control access to it (Lazy loading, Access control, Logging).

## Real-World Analogy
**Credit Card**: A proxy for the cash in your bank account.
**Company Proxy Server**: Controls/filters which websites employees can visit.

## Problem Statement
Loading a high-res image takes time. We want to display it only when `display()` is called, not when the object is created (Lazy Initialization).

## Implementation

```java
// Interface
interface Image {
    void display();
}

// Real Object (Heavy)
class RealImage implements Image {
    private String filename;
    
    public RealImage(String filename) {
        this.filename = filename;
        loadFromDisk();
    }
    
    private void loadFromDisk() {
        System.out.println("Loading " + filename + " from disk... (Heavy IO)");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    public void display() {
        System.out.println("Displaying " + filename);
    }
}

// Proxy Object (Lightweight)
class ProxyImage implements Image {
    private String filename;
    private RealImage realImage; // Lazy Reference
    
    public ProxyImage(String filename) {
        this.filename = filename;
        this.realImage = null;
    }
    
    public void display() {
        if (realImage == null) {
            realImage = new RealImage(filename);
        }
        realImage.display();
    }
}

// Client
public class Main {
    public static void main(String[] args) {
        Image img = new ProxyImage("photo_4k.jpg");
        System.out.println("Image object created (No disk IO yet)");
        
        // Needs to render now
        img.display(); // Loads from disk now
        img.display(); // Uses cached object
    }
}
```

## Variations
1.  **Virtual Proxy**: Lazy loading (above).
2.  **Protection Proxy**: Access control (e.g., Only "Admin" can call `delete`).
3.  **Remote Proxy**: Local object represents a remote service (gRPC stub).
