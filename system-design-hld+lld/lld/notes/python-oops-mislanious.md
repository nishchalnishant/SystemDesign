# Python OOPS mislanious

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





```python
#!/usr/bin/env python

# 08-class-instance-attributes-1.py

# This code shows that an Instance can access it's own
# attributes as well as Class attributes.

# We have a class attribute named 'count', and we add 1 to
# it each time we create an instance. This can help count the
# number of instances at the time of instantiation.


class InstanceCounter(object):
    count = 0

    def __init__(self, val):
        self.val = val
        InstanceCounter.count += 1

    def set_val(self, newval):
        self.val = newval

    def get_val(self):
        print(self.val)

    def get_count(self):
        print(InstanceCounter.count)


a = InstanceCounter(5)
b = InstanceCounter(10)
c = InstanceCounter(15)

```



```python
#!/usr/bin/env python

# Reference: Decorators 101 - A Gentle Introduction to Functional Programming.
# By Jillian Munson - PyGotham 2014.
# https://www.youtube.com/watch?v=yW0cK3IxlHc

# 20-decorators-2.py
# An updated version of 19-decorators-1.py

# This code snippet takes the previous example, and add a bit more information
# to the output.

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

# This will print: (NOTE: The time will change of course :P)
# # python 20-decorators-2.py
# 2016-05-29 11:46:07.444330
# This happened!
# 2016-05-29 11:46:07.444367
```

```python
#!/usr/bin/env python

# 19-decorators-1.py
# Decorators, as simple as it gets :)

# Reference: Decorators 101 - A Gentle Introduction to Functional Programming.
# By Jillian Munson - PyGotham 2014.
# https://www.youtube.com/watch?v=yW0cK3IxlHc

# Decorators are functions that compliment other functions,
# or in other words, modify a function or method.

# In the example below, we have a function named `decorated`.
# This function just prints "This happened".
# We have a decorator created named `inner_decorator()`.
# This decorator function has an function within, which
# does some operations (print stuff for simplicity) and then
# returns the return-value of the internal function.

# How does it work?
# a) The function `decorated()` gets called.
# b) Since the decorator `@my_decorator` is defined above
# `decorated()`, `my_decorator()` gets called.
# c) my_decorator() takes a function name as args, and hence `decorated()`
# gets passed as the arg.
# d) `my_decorator()` does it's job, and when it reaches `myfunction()`
# calls the actual function, ie.. decorated()
# e) Once the function `decorated()` is done, it gets back to `my_decorator()`.
# f) Hence, using a decorator can drastically change the behavior of the
# function you're actually executing.


def my_decorator(my_function):  # <-- (4)
    def inner_decorator():  # <-- (5)
        print("This happened before!")  # <-- (6)
        my_function()  # <-- (7)
        print("This happens after ")  # <-- (10)
        print("This happened at the end!")  # <-- (11)

    return inner_decorator
    # return None


@my_decorator  # <-- (3)
def my_decorated():  # <-- (2) <-- (8)
    print("This happened!")  # <-- (9)


if __name__ == "__main__":
    my_decorated()  # <-- (1)

# This prints:
# # python 19-decorators-1.py
# This happened before!
# This happened!
# This happens after
# This happened at the end!
```





```python
#!/usr/bin/env python


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
    
    
"""
This function takes 1 arguments
This happened: Hello
This function takes 2 arguments
Sum of 1and2: 3

=== Code Execution Successful ===
"""
```







```python
#!/usr/bin/env python

# 26-class-decorators.py

# Reference : https://www.youtube.com/watch?v=Slf1b3yUocc
# Talk by Mike Burns

# Till the previous examples, we saw function decorators.
# But decorators can be applied to Classes as well.
# This example deals with class decorators.

# NOTE: If you are creating a decorator for a class, you'll it
# to return a Class.

# NOTE: Similarly, if you are creating a decorator for a function,
# you'll need it to return a function.


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


# This needs further check. Erroring out.
```
