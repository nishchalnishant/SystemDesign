# Liskov Substitution Principle (LSP)

_Subtypes must be substitutable for their base types._

**Explanation:**
A subclass should be able to replace its superclass without altering the correctness of the program.

## Java Example

### Bad Design (Violating LSP)

```java
class Rectangle {
    int width, height;

    void setWidth(int width) {
        this.width = width;
    }

    void setHeight(int height) {
        this.height = height;
    }

    int getArea() {
        return width * height;
    }
}

class Square extends Rectangle {
    void setWidth(int width) {
        this.width = width;
        this.height = width; // Violates LSP
    }

    void setHeight(int height) {
        this.width = height;
        this.height = height; // Violates LSP
    }
}
```

Here, `Square` is a subclass of `Rectangle`, but it breaks LSP because changing the width also changes the height, making it behave differently from a rectangle.

### Good Design (Following LSP)

```java
interface Shape {
    int getArea();
}

class Rectangle implements Shape {
    int width, height;

    Rectangle(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getArea() {
        return width * height;
    }
}

class Square implements Shape {
    int side;

    Square(int side) {
        this.side = side;
    }

    public int getArea() {
        return side * side;
    }
}
```

Now, both `Rectangle` and `Square` can be used interchangeably without breaking behavior.

---

## Python Example

### Bad Design (Violating LSP)

```python
class Rectangle:
    def __init__(self, width, height):
        self.width = width
        self.height = height

    def set_width(self, width):
        self.width = width

    def set_height(self, height):
        self.height = height

    def get_area(self):
        return self.width * self.height

class Square(Rectangle):
    def set_width(self, width):
        self.width = width
        self.height = width  # Violates LSP

    def set_height(self, height):
        self.width = height
        self.height = height  # Violates LSP
```

👎 **Problem:**
A `Square` should not behave like a `Rectangle`. Setting width also changes the height, breaking expected behavior.

### Good Design (Following LSP)

```python
from abc import ABC, abstractmethod

class Shape(ABC):
    @abstractmethod
    def get_area(self):
        pass

class Rectangle(Shape):
    def __init__(self, width, height):
        self.width = width
        self.height = height

    def get_area(self):
        return self.width * self.height

class Square(Shape):
    def __init__(self, side):
        self.side = side

    def get_area(self):
        return self.side * self.side
```

👍 **Fix:**
Now, `Square` and `Rectangle` can be used interchangeably without breaking behavior.
