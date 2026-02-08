# Proxy Pattern

> **Type**: Structural  
> **Purpose**: Provides a placeholder for another object to control access to it (Lazy loading, Access control, Logging).

## Real-World Analogy
**Credit Card**: A proxy for the cash in your bank account.
**Company Proxy Server**: Controls/filters which websites employees can visit.

## Problem Statement
Loading a high-res image takes time. We want to display it only when `display()` is called, not when the object is created (Lazy Initialization).

## Implementation

```python
from abc import ABC, abstractmethod
import time

class Image(ABC):
    @abstractmethod
    def display(self): pass

# Real Object (Heavy)
class RealImage(Image):
    def __init__(self, filename):
        self.filename = filename
        self._load_from_disk()
    
    def _load_from_disk(self):
        print(f"Loading {self.filename} from disk... (Heavy IO)")
        time.sleep(1)

    def display(self):
        print(f"Displaying {self.filename}")

# Proxy Object (Lightweight)
class ProxyImage(Image):
    def __init__(self, filename):
        self.filename = filename
        self.real_image = None # Lazy Reference

    def display(self):
        if self.real_image is None:
            self.real_image = RealImage(self.filename)
        self.real_image.display()

# Client
if __name__ == "__main__":
    img = ProxyImage("photo_4k.jpg")
    print("Image object created (No disk IO yet)")
    
    # Needs to render now
    img.display() # Loads from disk now
    img.display() # Uses cached object
```

## Variations
1.  **Virtual Proxy**: Lazy loading (above).
2.  **Protection Proxy**: Access control (e.g., Only "Admin" can call `delete`).
3.  **Remote Proxy**: Local object represents a remote service (gRPC stub).
