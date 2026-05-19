# Python OOPS mislanious

## Topic Mindmap

```
[Python OOP]
├── Core Concept
│   ├── What → Everything in Python is an object (int, str, function, class)
│   └── Why → Python OOP is looser than Java — duck typing, no access modifiers
├── Key Constructs
│   ├── class → blueprint; object = instance of class
│   ├── __init__ → constructor called on object creation
│   ├── self → explicit reference to the current instance (unlike Java's implicit this)
│   └── Dunder methods → __str__, __repr__, __eq__, __len__, __add__ (operator overload)
├── Encapsulation in Python
│   ├── _name → convention: "internal, don't touch" (not enforced)
│   ├── __name → name mangling: _ClassName__name (compiler-enforced privacy)
│   └── @property → getter/setter without explicit method calls
├── Inheritance & MRO
│   ├── Single: class Dog(Animal) — straightforward
│   ├── Multiple: class Mule(Horse, Donkey) — Python allows it, Java does not
│   ├── MRO (C3 Linearization) → Python resolves method lookup left-to-right, depth-first
│   └── super() → calls next class in MRO chain, not necessarily direct parent
├── Dataclasses (@dataclass)
│   ├── Auto-generates __init__, __repr__, __eq__ from field annotations
│   ├── frozen=True → immutable (like Java record)
│   └── Reduces boilerplate for plain data-holding classes
├── When to Use
│   ├── ✓ Group data + behavior that naturally belongs together
│   ├── ✓ Use @dataclass for DTOs / value objects
│   └── ✓ Use __dunder__ for Pythonic API (len(obj), obj1 == obj2)
├── When NOT to Use
│   ├── ✗ Deep inheritance chains — prefer composition
│   └── ✗ Multiple inheritance with shared state — MRO confusion
├── Trade-offs
│   ├── Pro: Less ceremony than Java; duck typing enables flexible polymorphism
│   └── Con: No compile-time type safety; privacy is convention, not enforcement
├── Real-World Examples
│   ├── Django Model → class-based ORM objects with __str__ and Meta
│   └── FastAPI Pydantic → dataclass-like validation with type hints
└── Interview Angles
    ├── MRO → draw diamond problem, trace C3 linearization order
    ├── __slots__ → reduces per-instance memory by disabling __dict__
    └── Code challenge: implement a Stack class with __len__, __iter__, __repr__
```

---

*

    <figure><img src="../../../.gitbook/assets/image (139).png" alt=""><figcaption></figcaption></figure>



* All things in python are objects&#x20;
* class - a set of things, ex — animal
* object — is an instance of the class like — tiger
* each class has a method which are functions but these are called using L.method\_name() while for function we use func(L)
* Each class a constructor which has the attributes of that particular class
* this is the  constructor function where a class called ATM is created&#x20;
  * the \_\_init\_\_ method is the constructor method while the menu is the method fopr the class.
  * When the object is created for the ATM first of all the constructor is processed so for the object the pin and balance attributes or variables are created.
  * When the menu method is called on the object then it runs the menu method for the attribute

```
Class ATM:
    def __init__(self):
        self.pin=""
        self.balance=0
    def menu(self):
        user_input=input("Enter your input)
```

* Constructor&#x20;
  * it is a special/magic method magic method has \_\_ in front and end of the method name.
  * these methods are not callable by the object, these run when the object are created.
  * So the constructor makes sure the objects have a common starting point,
* What is self
  * self refers to location of the memory where the object data is stored.
  * "self" is a parameter that refers to the current instance of a class. It's used to access and modify the class's attributes and methods.&#x20;





```java
// This code shows that an Instance can access it's own
// attributes as well as Class attributes.

// We have a class attribute named 'count', and we add 1 to
// it each time we create an instance. This can help count the
// number of instances at the time of instantiation.

class InstanceCounter {
    private static int count = 0;
    private int val;
    
    public InstanceCounter(int val) {
        this.val = val;
        InstanceCounter.count++;
    }
    
    public void setVal(int newval) {
        this.val = newval;
    }
    
    public void getVal() {
        System.out.println(this.val);
    }
    
    public void getCount() {
        System.out.println(InstanceCounter.count);
    }
}

public class Main {
    public static void main(String[] args) {
        InstanceCounter a = new InstanceCounter(5);
        InstanceCounter b = new InstanceCounter(10);
        InstanceCounter c = new InstanceCounter(15);
    }
}
```



```java
// NOTE: Python decorators are a Python-specific feature.
// Java uses annotations (@Override, @Deprecated, etc.) instead.
// This example shows the decorator concept, which wraps functions
// to add behavior before/after execution.

// Python code (decorators are not directly translatable to Java):
/*
import datetime

def my_decorator(inner):
    def inner_decorator():
        print(datetime.datetime.utcnow())
        inner()
        print(datetime.datetime.utcnow())
    return inner_decorator

@my_decorator
def decorated():
    print("This happened!")

if __name__ == "__main__":
    decorated()

// This will print: (NOTE: The time will change of course :P)
// 2016-05-29 11:46:07.444330
// This happened!
// 2016-05-29 11:46:07.444367
*/

// Java equivalent concept (using method wrapping):
import java.time.LocalDateTime;

public class DecoratorExample {
    // Functional interface for the action
    interface Action {
        void execute();
    }
    
    // Decorator method  that wraps an action
    public static void decoratedAction(Action action) {
        System.out.println(LocalDateTime.now());
        action.execute();
        System.out.println(LocalDateTime.now());
    }
    
    public static void main(String[] args) {
        decoratedAction(() -> System.out.println("This happened!"));
    }
}
```

```java
// NOTE: Python decorators are Python-specific.
// This example demonstrates function wrapping.
// Java equivalent uses higher-order functions or lambda expressions.

// Python code (decorator pattern):
/*
def my_decorator(my_function):
    def inner_decorator():
        print("This happened before!")
        my_function()
        print("This happens after ")
        print("This happened at the end!")
    return inner_decorator

@my_decorator
def my_decorated():
    print("This happened!")

if __name__ == "__main__":
    my_decorated()

// This prints:
// This happened before!
// This happened!
// This happens after
// This happened at the end!
*/

// Java equivalent (using Runnable interface):
public class DecoratorPattern {
    // Functional interface for wrapping
    interface Action {
        void execute();
    }
    
    // Decorator that wraps an action
    public static Action myDecorator(Action myFunction) {
        return () -> {
            System.out.println("This happened before!");
            myFunction.execute();
            System.out.println("This happens after");
            System.out.println("This happened at the end!");
        };
    }
    
    public static void main(String[] args) {
        Action myDecorated = myDecorator(() -> System.out.println("This happened!"));
        myDecorated.execute();
    }
}
```





```java
// NOTE: Python decorators with arguments (*args, **kwargs)
// Java equivalent uses varargs or generics

// Python code:
/*
def decorator(inner):
    def inner_decorator(*args, **kwargs):
        print("This function takes " + str(len(args)) + " arguments")
        inner(*args)
    return inner_decorator

@decorator
def decorated(string_args):
    print("This happened: " + str(string_args))

@decorator
def alsoDecorated(num1, num2):
    print("Sum of " + str(num1) + "and" + str(num2) + ": " + str(num1 + num2))

if __name__ == "__main__":
    decorated("Hello")
    alsoDecorated(1, 2)

// Output:
// This function takes 1 arguments
// This happened: Hello
// This function takes 2 arguments
// Sum of 1and2: 3
*/

// Java equivalent (using varargs):
public class VarargsDecorator {
    // Functional interface
    interface VarAction {
        void execute(Object... args);
    }
    
    // Decorator with varargs
    public static VarAction decorator(VarAction inner) {
        return (args) -> {
            System.out.println("This function takes " + args.length + " arguments");
            inner.execute(args);
        };
    }
    
    public static void main(String[] args) {
        VarAction decorated = decorator((a) -> 
            System.out.println("This happened: " + a[0]));
        
        VarAction alsoDecorated = decorator((a) -> 
            System.out.println("Sum of " + a[0] + "and" + a[1] + ": " + 
            ((int)a[0] + (int)a[1])));
        
        decorated.execute("Hello");
        alsoDecorated.execute(1, 2);
    }
}
```







```java
// NOTE: Python class decorators are Python-specific.
// Java equivalent would use inheritance or composition.

// Python code (class decorator):
/*
def honirific(cls):
    class HonirificCls(cls):
        def full_name(self):
            return "Dr. " + super(HonirificCls, self).full_name()
    return HonirificCls

@honirific
class Name(object):
    def __init__(self, first_name, last_name):
        self.first_name = first_name
        self.last_name = last_name

    def full_name(self):
        return " ".join([self.first_name, self.last_name])

result = Name("Vimal", "A.R").full_name()
print("Full name: {0}".format(result))
*/

// Java equivalent (using inheritance):
class Name {
    protected String firstName;
    protected String lastName;
    
    public Name(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
    }
    
    public String fullName() {
        return this.firstName + " " + this.lastName;
    }
}

class HonorificName extends Name {
    public HonorificName(String firstName, String lastName) {
        super(firstName, lastName);
    }
    
    @Override
    public String fullName() {
        return "Dr. " + super.fullName();
    }
}

public class Main {
    public static void main(String[] args) {
        HonorificName person = new HonorificName("Vimal", "A.R");
        String result = person.fullName();
        System.out.println("Full name: " + result);
    }
}
```
