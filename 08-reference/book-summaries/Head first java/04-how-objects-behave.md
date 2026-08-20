# Chapter 4: How Objects Behave

*object state affects method behavior*

State affects behavior, behavior affects state. We know that objects have state and behavior, represented by instance variables and methods. But until now, we haven't looked at how state and behavior are related. We already know that each instance of a class (each object of a particular type) can have its own unique values for its instance variables. Dog A can have a name "Fido" and a weight of 70 pounds. Dog B is "Killer" and weighs 9 pounds. And if the Dog class has a method makeNoise(), well, don't you think a 70-pound dog barks a bit deeper than the little 9-pounder? (Assuming that annoying yippy sound can be considered a bark.) Fortunately, that's the whole point of an object—it has behavior that acts on its state. In other words, methods use instance variable values. Like, "if dog is less than 14 pounds, make yippy sound, else..." or "increase weight by 5". Let's go change some state.

## Objects have state and behavior

**Remember: a class describes what an object knows and what an object does**

A class is the blueprint for an object. When you write a class, you're describing how the JVM should make an object of that type. You already know that every object of that type can have different instance variable values. But what about the methods?

Can every object of that type have different method behavior?

Well... sort of.

Every instance of a particular class has the same methods, but the methods can behave differently based on the value of the instance variables.

The `Song` class has two instance variables, `title` and `artist`. The `play()` method plays a song, but the instance you call `play()` on will play the song represented by the value of the `title` instance variable for that instance. So, if you call the `play()` method on one instance you'll hear the song "Politik", while another instance plays "Darkstar". The method code, however, is the same.

```java
void play() {
    soundPlayer.playSound(title);
}
```

```java
Song t2 = new Song();
t2.setArtist("Travis");
t2.setTitle("Sing");

Song s3 = new Song();
s3.setArtist("Sex Pistols");
s3.setTitle("My Way");
```

Calling `play()` on the `t2` instance ("Sing" by Travis) will cause "Sing" to play: `t2.play();`

Calling `play()` on the `s3` instance (`s3.play();`) will cause "My Way" to play (but not the Sinatra one) — it plays the Sex Pistols version, since that's the instance's own `title`/`artist` state.

## Methods use instance variables

**The size affects the bark**

A small Dog's bark is different from a big Dog's bark. The `Dog` class has an instance variable `size`, that the `bark()` method uses to decide what kind of bark sound to make.

```java
class Dog {
    int size;
    String name;

    void bark() {
        if (size > 60) {
            System.out.println("Wooof! Wooof!");
        } else if (size > 14) {
            System.out.println("Ruff!  Ruff!");
        } else {
            System.out.println("Yip! Yip!");
        }
    }
}


class DogTestDrive {

    public static void main (String[] args) {
        Dog one = new Dog();
        one.size = 70;
        Dog two = new Dog();
        two.size = 8;
        Dog three = new Dog();
        three.size = 35;

        one.bark();
        two.bark();
        three.bark();
    }
}
```

```
File Edit Window Help Playdead

%java DogTestDrive
Wooof! Wooof!
Yip! Yip!
Ruff!  Ruff!
```

## Method parameters

**You can send things to a method**

Just as you expect from any programming language, you can pass values into your methods. You might, for example, want to tell a Dog object how many times to bark by calling:

```java
d.bark(3);
```

Depending on your programming background and personal preferences, you might use the term arguments or perhaps parameters for the values passed into a method. Although there are formal computer science distinctions that people who wear lab coats and who will almost certainly not read this book, make, we have bigger fish to fry in this book. So you can call them whatever you like (arguments, donuts, hairballs, etc.) but we're doing it like this:

**A method uses parameters. A caller passes arguments.**

Arguments are the things you pass into the methods. An argument (a value like 2, "Foo", or a reference to a Dog) lands face-down into a... wait for it... parameter. And a parameter is nothing more than a local variable. A variable with a type and a name, that can be used inside the body of the method.

But here's the important part: If a method takes a parameter, you must pass it something. And that something must be a value of the appropriate type.

```java
Dog d = new Dog();
d.bark(3);
```

Step by step:
1. Call the bark method on the Dog reference, and pass in the value 3 (as the argument to the method).
2. The bits representing the int value 3 are delivered into the bark method — this is the **argument**.
3. The bits land in the `numOfBarks` parameter (an int-sized variable) — this is the **parameter**.
4. Use the `numOfBarks` parameter as a variable in the method code.

```java
void bark(int numOfBarks) {
    while (numOfBarks > 0) {
        System.out.println("ruff");
        numOfBarks = numOfBarks - 1;
    }
}
```

## Return values

**You can get things back from a method.**

Methods can return values. Every method is declared with a return type, but until now we've made all of our methods with a `void` return type, which means they don't give anything back.

```java
void go() {
}
```

But we can declare a method to give a specific type of value back to the caller, such as:

```java
int giveSecret() {
    return 42;
}
```

If you declare a method to return a value, you must return a value of the declared type! (Or a value that is compatible with the declared type. We'll get into that more when we talk about polymorphism in chapter 7 and chapter 8.)

*Whatever you say you'll give back, you better give back!*

The compiler won't let you return the wrong type of thing. These types must match:

```java
int theSecret = life.giveSecret();

int giveSecret() {
    return 42;   // this must fit in an int!
}
```

The bits representing 42 are returned from the `giveSecret()` method, and land in the variable named `theSecret`.

## Multiple arguments

**You can send more than one thing to a method**

Methods can have multiple parameters. Separate them with commas when you declare them, and separate the arguments with commas when you pass them. Most importantly, if a method has parameters, you must pass arguments of the right type and order.

**Calling a two-parameter method, and sending it two arguments.**

```java
void go() {
    TestStuff t = new TestStuff();
    t.takeTwo(12, 34);
}

void takeTwo(int x, int y) {
    int z = x + y;
    System.out.println("Total is " + z);
}
```

The arguments you pass in land in the same order you pass them. First argument lands in the first parameter, second argument lands in the second parameter, and so on.

**You can pass variables into a method, as long as the variable type matches the parameter type.**

```java
void go() {
    int foo = 7;
    int bar = 3;
    t.takeTwo(foo, bar);
}

void takeTwo(int x, int y) {
    int z = x + y;
    System.out.println("Total is " + z);
}
```

The values of `foo` and `bar` land in the `x` and `y` parameters. So now the bits in `x` are identical to the bits in `foo` (the integer '7') and `y` are identical to the bits in `bar`.

What's the value of `z`? It's the same result you'd get if you added `foo` + `bar` at the time you passed them into the `takeTwo` method.

## Java is pass-by-value

**That means pass-by-copy.**

1. Declare an int variable and assign it the value '7'. The bit pattern for 7 goes into the variable named x.
   ```java
   int x = 7;
   ```
2. Declare a method with an int parameter named z.
   ```java
   void go(int z){ }
   ```
3. Call the go() method, passing the variable x as the argument. The bits in x are copied, and the copy lands in z.
   ```java
   foo.go(x);
   void go(int z){ }
   ```
4. Change the value of z inside the method. The value of x doesn't change! The argument passed to the z parameter was only a copy of x. The method can't change the bits that were in the calling variable x.
   ```java
   void go(int z){
       z = 0;
   }
   ```
   x doesn't even change, even if z does. x and z aren't connected.
