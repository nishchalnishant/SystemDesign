# Chapter 1: Breaking the Surface — A Quick Dip

Java takes you to new places. From its humble release to the public as the (wimpy) version 1.02, Java seduced programmers with its friendly syntax, object-oriented features, memory management, and best of all—the promise of portability. The lure of write-once/run-anywhere is just too strong. A devoted following exploded, as programmers fought against bugs, limitations, and, oh yeah, the fact that it was dog slow. But that was ages ago. If you're just starting in Java, you're lucky. Some of us had to walk five miles in the snow, uphill both ways (barefoot), to get even the most trivial applet to work. But you, why, you get to ride the sleeker, faster, much more powerful Java of today.

## The Way Java Works

The goal is to write one application (in this example, an interactive party invitation) and have it work on whatever device your friends have.

1. **Source** — Create a source document. Use an established protocol (in this case, the Java language).
2. **Compiler** — Run your document through a source code compiler. The compiler checks for errors and won't let you compile until it's satisfied that everything will run correctly.
3. **Output (code)** — The compiler creates a new document, coded into Java bytecode. Any device capable of running Java will be able to interpret/translate this file into something it can run. The compiled bytecode is platform-independent.
4. **Virtual Machines** — Your friends don't have a physical Java Machine, but they all have a virtual Java machine (implemented in software) running inside their electronic gadgets. The virtual machine reads and runs the bytecode.

## What You'll Do in Java

You'll type a source code file, compile it using the `javac` compiler, then run the compiled bytecode on a Java virtual machine.

```java
import java.awt.*;
import java.awt.event.*;
class Party {
  public void buildInvite() {
    Frame f = new Frame();
    Label l = new Label("Party at Tim's");
    Button b = new Button("You bet");
    Button c = new Button("Shoot me");
    Panel p = new Panel();
    p.add(l);
   } // more code here...
}
```

1. **Source** — Type your source code. Save as: `Party.java`
2. **Compiler** — Compile the `Party.java` file by running `javac` (the compiler application):
   ```
   %javac Party.java
   ```
   If you don't have errors, you'll get a second document named `Party.class`. The compiler-generated `Party.class` file is made up of bytecodes.
3. **Output (code)** — Compiled code: `Party.class`
4. **Virtual Machines** — Run the program by starting the Java Virtual Machine (JVM) with the `Party.class` file:
   ```
   %java Party
   ```
   The JVM translates the bytecode into something the underlying platform understands, and runs your program.

(Note: this is not meant to be a tutorial... you'll be writing real code in a moment, but for now, we just want you to get a feel for how it all fits together.)

## A Very Brief History of Java

Growth of the Java standard library over time:

| Version | Classes | Notes |
|---|---|---|
| Java 1.02 | 250 classes | Slow. Cute name and logo. Fun to use. Lots of bugs. Applets are the Big Thing. |
| Java 1.1 | 500 classes | A little faster. More capable, friendlier. Becoming very popular. Better GUI code. |
| Java 2 (versions 1.2 – 1.4) | 2300 classes | Much faster. Can (sometimes) run at native speeds. Serious, powerful. Comes in three flavors: Micro Edition (J2ME), Standard Edition (J2SE) and Enterprise Edition (J2EE). Becomes the language of choice for new enterprise (especially web-based) and mobile applications. |
| Java 5.0 (versions 1.5 and up) | 3500 classes | More power, easier to develop with. Besides adding more than a thousand additional classes, Java 5.0 (known as "Tiger") added major changes to the language itself, making it easier (at least in theory) for programmers and giving it new features that were popular in other languages. |

## Sharpen Your Pencil

Look how easy it is to write Java. Try to guess what each line of code is doing... (answers are on the next page).

```java
int size = 27;
String name = "Fido";
Dog myDog = new Dog(name, size);
x = size - 5;
if (x < 15) myDog.bark(8);

while (x > 3) {
    myDog.play();
}

int[] numList = {2,4,6,8};
System.out.print("Hello");
System.out.print("Dog: " + name);
String num = "8";
int z = Integer.parseInt(num);

try {
    readTheFile("myFile.txt");
}
catch(FileNotFoundException ex) {
    System.out.print("File not found.");
}
```

### There Are No Dumb Questions

**Q: I see Java 2 and Java 5.0, but was there a Java 3 and 4? And why is it Java 5.0 but not Java 2.0?**

A: The joys of marketing... when the version of Java shifted from 1.1 to 1.2, the changes to Java were so dramatic that the marketers decided we needed a whole new "name", so they started calling it Java 2, even though the actual version of Java was 1.2. But versions 1.3 and 1.4 were still considered Java 2. There never was a Java 3 or 4. Beginning with Java version 1.5, the marketers decided once again that the changes were so dramatic that a new name was needed (and most developers agreed), so they looked at the options. The next number in the name sequence would be "3", but calling Java 1.5 Java 3 seemed more confusing, so they decided to name it Java 5.0 to match the "5" in version "1.5".

So, the original Java, versions 1.02 (the first official release) through 1.1, were just "Java". Versions 1.2, 1.3, and 1.4 were "Java 2". And beginning with version 1.5, Java is called "Java 5.0". But you'll also see it called "Java 5" (without the ".0") and "Tiger" (its original code-name). We have no idea what will happen with the next release...

## Sharpen Your Pencil Answers

Don't worry about whether you understand any of this yet! Everything here is explained in great detail in the book, most within the first 40 pages. If Java resembles a language you've used in the past, some of this will be simple. If not, don't worry about it. We'll get there...

| Code | What it does |
|---|---|
| `int size = 27;` | declare an integer variable named 'size' and give it the value 27 |
| `String name = "Fido";` | declare a string of characters variable named 'name' and give it the value "Fido" |
| `Dog myDog = new Dog(name, size);` | declare a new Dog variable 'myDog' and make the new Dog using 'name' and 'size' |
| `x = size - 5;` | subtract 5 from 27 (value of 'size') and assign it to a variable named 'x' |
| `if (x < 15) myDog.bark(8);` | if x (value of 22) is less than 15, tell the dog to bark 8 times |
| `while (x > 3) {` | keep looping as long as x is greater than 3... |
| `myDog.play();` | tell the dog to play (whatever THAT means to a dog...) |
| `}` | this looks like the end of the loop -- everything in `{ }` is done in the loop |
| `int[] numList = {2,4,6,8};` | declare a list of integers variable 'numList', and put 2,4,6,8 into the list |
| `System.out.print("Hello");` | print out "Hello"... probably at the command-line |
| `System.out.print("Dog: " + name);` | print out "Dog: Fido" (the value of 'name' is "Fido") at the command-line |
| `String num = "8";` | declare a character string variable 'num' and give it the value of "8" |
| `int z = Integer.parseInt(num);` | convert the string of characters "8" into an actual numeric value 8 |
| `try {` | try to do something...maybe the thing we're trying isn't guaranteed to work... |
| `readTheFile("myFile.txt");` | read a text file named "myFile.txt" (or at least TRY to read the file...) |
| `}` | must be the end of the "things to try", so I guess you could try many things... |
| `catch(FileNotFoundException ex) {` | this must be where you find out if the thing you tried didn't work... |
| `System.out.print("File not found.");` | if the thing we tried failed, print "File not found" out at the command-line |
| `}` | looks like everything in the `{ }` is what to do if the 'try' didn't work... |

## Code Structure in Java

**What goes in a source file?**
A source code file (with the `.java` extension) holds one class definition. The class represents a piece of your program, although a very tiny application might need just a single class. The class must go within a pair of curly braces.

```java
public class Dog {

}
```

**What goes in a class?**
A class has one or more methods. In the Dog class, the bark method will hold instructions for how the Dog should bark. Your methods must be declared inside a class (in other words, within the curly braces of the class).

```java
public class Dog {
       void bark() {

       }
}
```

**What goes in a method?**
Within the curly braces of a method, write your instructions for how that method should be performed. Method code is basically a set of statements, and for now you can think of a method kind of like a function or procedure.

```java
public class Dog {
       void bark() {
           statement1;
           statement2;
       }
}
```

Rule of thumb:
- Put a class in a source file.
- Put methods in a class.
- Put statements in a method.

## The Way Java Works

The goal is to write one application (in this example, an interactive party invitation) and have it work on whatever device your friends have.

1. **Create a source document.** Use an established protocol (in this case, the Java language).
2. **Run your document through a source code compiler.** The compiler checks for errors and won't let you compile until it's satisfied that everything will run correctly.
3. **The compiler creates a new document, coded into Java bytecode.** Any device capable of running Java will be able to interpret/translate this file into something it can run. The compiled bytecode is platform-independent.
4. **Your friends don't have a physical Java Machine, but they all have a virtual Java machine** (implemented in software) running inside their electronic gadgets. The virtual machine reads and runs the bytecode.

## What You'll Do in Java

You'll type a source code file, compile it using the `javac` compiler, then run the compiled bytecode on a Java virtual machine.

```java
import java.awt.*;
import java.awt.event.*;
class Party {
  public void buildInvite() {
    Frame f = new Frame();
    Label l = new Label("Party at Tim's");
    Button b = new Button("You bet");
    Button c = new Button("Shoot me");
    Panel p = new Panel();
    p.add(l);
  } // more code here...
}
```

1. Type your source code. Save as: `Party.java`
2. Compile the `Party.java` file by running `javac` (the compiler application). If you don't have errors, you'll get a second document named `Party.class`. The compiler-generated `Party.class` file is made up of bytecodes.
3. Compiled code: `Party.class`
4. Run the program by starting the Java Virtual Machine (JVM) with the `Party.class` file. The JVM translates the bytecode into something the underlying platform understands, and runs your program.

(Note: this is not meant to be a tutorial... you'll be writing real code in a moment, but for now, we just want you to get a feel for how it all fits together.)

## A Very Brief History of Java

| Version | Classes | Notes |
|---|---|---|
| Java 1.02 | 250 classes | Slow. Cute name and logo. Fun to use. Lots of bugs. Applets are the Big Thing. |
| Java 1.1 | 500 classes | A little faster. More capable, friendlier. Becoming very popular. Better GUI code. |
| Java 2 (versions 1.2–1.4) | 2300 classes | Much faster. Can (sometimes) run at native speeds. Serious, powerful. Comes in three flavors: Micro Edition (J2ME), Standard Edition (J2SE), and Enterprise Edition (J2EE). Becomes the language of choice for new enterprise (especially web-based) and mobile applications. |
| Java 5.0 (versions 1.5 and up) | 3500 classes | More power, easier to develop with. Besides adding more than a thousand additional classes, Java 5.0 (known as "Tiger") added major changes to the language itself, making it easier (at least in theory) for programmers and giving it new features that were popular in other languages. |

### Sharpen your pencil

Try to guess what each line of code is doing... (answers are on the next page)

```java
int size = 27;
String name = "Fido";
Dog myDog = new Dog(name, size);
x = size - 5;
if (x < 15) myDog.bark(8);

while (x > 3) {
    myDog.play();
}

int[] numList = {2,4,6,8};
System.out.print("Hello");
System.out.print("Dog: " + name);
String num = "8";
int z = Integer.parseInt(num);

try {
    readTheFile("myFile.txt");
}
catch(FileNotFoundException ex) {
    System.out.print("File not found.");
}
```

**There Are No Dumb Questions**

**Q: I see Java 2 and Java 5.0, but was there a Java 3 and 4? And why is it Java 5.0 but not Java 2.0?**

A: The joys of marketing... when the version of Java shifted from 1.1 to 1.2, the changes to Java were so dramatic that the marketers decided we needed a whole new "name", so they started calling it Java 2, even though the actual version of Java was 1.2. But versions 1.3 and 1.4 were still considered Java 2. There never was a Java 3 or 4. Beginning with Java version 1.5, the marketers decided once again that the changes were so dramatic that a new name was needed (and most developers agreed), so they looked at the options. The next number in the name sequence would be "3", but calling Java 1.5 Java 3 seemed more confusing, so they decided to name it Java 5.0 to match the "5" in version "1.5". So, the original Java, versions 1.02 (the first official release) through 1.1, were just "Java". Versions 1.2, 1.3, and 1.4 were "Java 2". And beginning with version 1.5, Java is called "Java 5.0". But you'll also see it called "Java 5" (without the ".0") and "Tiger" (its original code-name). We have no idea what will happen with the next release...

### Sharpen your pencil — answers

Don't worry about whether you understand any of this yet! Everything here is explained in great detail in the book (most within the first 40 pages). If Java resembles a language you've used in the past, some of this will be simple. If not, don't worry about it. We'll get there...

```java
int size = 27;                              // declare an integer variable named 'size' and give it the value 27
String name = "Fido";                       // declare a String variable named 'name' and give it the value "Fido"
Dog myDog = new Dog(name, size);            // make a new Dog object, passing the name and size to the Dog constructor, and assign the new Dog object to the variable myDog
x = size - 5;                               // subtract 5 from size, and assign the result to x
if (x < 15) myDog.bark(8);                  // if x is less than 15, call the myDog object's bark() method, passing it the value 8

while (x > 3) {                             // while x is greater than 3, keep looping
    myDog.play();                           // call the myDog object's play() method
}

int[] numList = {2,4,6,8};                  // declare an array of ints and initialize it with 4 values
System.out.print("Hello");                  // print the string "Hello" to output
System.out.print("Dog: " + name);           // print the string "Dog: " concatenated with the value of name
String num = "8";                           // declare a String variable and give it the value "8"
int z = Integer.parseInt(num);              // convert the String "8" to an int and assign it to z

try {
    readTheFile("myFile.txt");              // try to call a method that might throw an exception
}
catch(FileNotFoundException ex) {
    System.out.print("File not found.");    // if the file isn't found, catch the exception and print a message
}
```

## Code Structure in Java

**What goes in a source file?** A source code file (with the `.java` extension) holds one class definition. The class represents a piece of your program, although a very tiny application might need just a single class. The class must go within a pair of curly braces.

```java
public class Dog {

}
```

**What goes in a class?** A class has one or more methods. In the Dog class, the `bark` method will hold instructions for how the Dog should bark. Your methods must be declared inside a class (in other words, within the curly braces of the class).

```java
public class Dog {
    void bark() {

    }
}
```

**What goes in a method?** Within the curly braces of a method, write your instructions for how that method should be performed. Method code is basically a set of statements, and for now you can think of a method kind of like a function or procedure.

```java
public class Dog {
    void bark() {
        statement1;
        statement2;
    }
}
```

Rule of thumb:
- Put a class in a source file.
- Put methods in a class.
- Put statements in a method.

## Anatomy of a Class

When the JVM starts running, it looks for the class you give it at the command line. Then it starts looking for a specially-written method that looks exactly like:

```java
public static void main (String[] args) {
    // your code goes here
}
```

Next, the JVM runs everything between the curly braces `{ }` of your `main` method. Every Java application has to have at least one class, and at least one main method (not one main per class; just one main per application).

```java
public class MyFirstApp {

    public static void main (String[] args) {

        System.out.print("I Rule!");
    }
}
```

- `public` — so everyone can access it
- `class` — this is a class (duh); `MyFirstApp` is the name of this class
- `public static void main (String[] args)` — `void` means there's no return value (the return type); `main` is the name of this method; `(String[] args)` are the arguments to the method — this method must be given an array of Strings, and the array will be called `args`
- `System.out.print("I Rule!");` — this says print to standard output (defaults to command-line); `"I Rule!"` is the String you want to print; every statement MUST end in a semicolon
- Closing braces close the method, then the class.

Don't worry about memorizing anything right now... this chapter is just to get you started.

## Writing a Class with a main()

In Java, everything goes in a class. You'll type your source code file (with a `.java` extension), then compile it into a new class file (with a `.class` extension). When you run your program, you're really running a class.

Running a program means telling the Java Virtual Machine (JVM) to "Load the `MyFirstApp` class, then start executing its `main()` method. Keep running 'til all the code in main is finished."

In chapter 2, we go deeper into the whole class thing, but for now, all you need to think is, how do I write Java code so that it will run? And it all begins with `main()`.

**The `main()` method is where your program starts running.** No matter how big your program is (in other words, no matter how many classes your program uses), there's got to be a `main()` method to get the ball rolling.

```java
public class MyFirstApp {
    public static void main (String[] args) {
        System.out.println("I Rule!");
        System.out.println("The World");
    }
}
```

1. Save as `MyFirstApp.java`
2. Compile: `javac MyFirstApp.java`
3. Run: `java MyFirstApp`

## What Can You Say in the main() Method?

Once you're inside main (or any method), the fun begins. You can say all the normal things that you say in most programming languages to make the computer do something. Your code can tell the JVM to:

**1. Do something** — Statements: declarations, assignments, method calls, etc.

```java
int x = 3;
String name = "Dirk";
x = x * 17;
System.out.print("x is " + x);
double d = Math.random();
// this is a comment
```

**2. Do something again and again** — Loops: `for` and `while`

```java
while (x > 12) {
    x = x - 1;
}

for (int x = 0; x < 10; x = x + 1) {
    System.out.print("x is now " + x);
}
```

**3. Do something under this condition** — Branching: `if`/`else` tests

```java
if (x == 10) {
    System.out.print("x must be 10");
} else {
    System.out.print("x isn't 10");
}

if ((x < 3) & (name.equals("Dirk"))) {
    System.out.println("Gently");
}
System.out.print("this line runs no matter what");
```

Syntax notes:
- Each statement must end in a semicolon.
- A single-line comment begins with two forward slashes.
- Most white space doesn't matter: `x = 3;` is fine spaced out however.
- Variables are declared with a name and a type (you'll learn about all the Java types in chapter 3): `int weight; // type: int, name: weight`
- Classes and methods must be defined within a pair of curly braces:
```java
public void go() {
    // amazing code here
}
```

## Looping and Looping and...

Java has three standard looping constructs: `while`, `do-while`, and `for`. You'll get the full loop scoop later in the book, but for now let's do `while`.

The syntax (not to mention logic) is simple. As long as some condition is true, you do everything inside the loop block. The loop block is bounded by a pair of curly braces, so whatever you want to repeat needs to be inside that block.

The key to a loop is the conditional test. In Java, a conditional test is an expression that results in a boolean value — in other words, something that is either true or false.

If you say something like, "While iceCreamInTheTub is true, keep scooping", you have a clear boolean test. There either is ice cream in the tub or there isn't. But if you were to say, "While Bob keep scooping", you don't have a real test. To make that work, you'd have to change it to something like, "While Bob is snoring..." or "While Bob is not wearing plaid..."

```java
while (moreBalls == true) {
    keepJuggling();
}
```

**Simple boolean tests.** You can do a simple boolean test by checking the value of a variable, using a comparison operator including:
- `<` (less than)
- `>` (greater than)
- `==` (equality) (yes, that's two equals signs)

Notice the difference between the assignment operator (a single equals sign) and the equals operator (two equals signs). Lots of programmers accidentally type `=` when they want `==`. (But not you.)

```java
int x = 4; // assign 4 to x
while (x > 3) {
    // loop code will run because
    // x is greater than 3
    x = x - 1; // or we'd loop forever
}

int z = 27;
while (z == 17) {
    // loop code will not run because
    // z is not equal to 17
}
```

**There Are No Dumb Questions**

**Q: Why does everything have to be in a class?**

A: Java is an object-oriented (OO) language. It's not like the old days when you had steam-driven compilers and wrote one monolithic source file with a pile of procedures. In chapter 2 you'll dig deeper into OO, but the short answer is that everything in Java is defined within a class (or an interface), and classes model real-world things and concepts as objects with state and behavior.

### Example of a while loop

```java
public class Loopy {
    public static void main (String[] args) {
        int x = 1;
        System.out.println("Before the Loop");
        while (x < 4) {
            System.out.println("In the loop");
            System.out.println("Value of x is " + x);
            x = x + 1;
        }
        System.out.println("Value of x is " + x);
        x = x + 1;
    }
    System.out.println("This is after the loop");
    }
}
```

Output:
```
% java Loopy
Before the Loop
In the loop
Value of x is 1
In the loop
Value of x is 2
In the loop
Value of x is 3
This is after the loop
```

**Bullet Points**

- Statements end in a semicolon `;`
- Code blocks are defined by a pair of curly braces `{ }`
- Declare an int variable with a name and a type: `int x;`
- The assignment operator is one equals sign `=`
- The equals operator uses two equals signs `==`
- A while loop runs everything within its block (defined by curly braces) as long as the conditional test is true.
- If the conditional test is false, the while loop code block won't run, and execution will move down to the code immediately after the loop block.
- Put a boolean test inside parentheses: `while (x == 4) { }`

**There Are No Dumb Questions**

**Q: Do I have to put a main in every class I write?**

A: Nope. A Java program might use dozens of classes (even hundreds), but you might only have one with a main method — the one that starts the program running. You might write test classes, though, that have main methods for testing your other classes.

**Q: In my other language I can do a boolean test on an integer. In Java, can I say something like:**
```java
int x = 1;
while (x){ }
```

A: No. A boolean and an integer are not compatible types in Java. Since the result of a conditional test must be a boolean, the only variable you can directly test (without using a comparison operator) is a boolean. For example, you can say:
```java
boolean isHot = true;
while(isHot) { }
```

## Conditional Branching

In Java, an `if` test is basically the same as the boolean test in a `while` loop – except instead of saying, "while there's still beer...", you'll say, "if there's still beer..."

```java
class IfTest {
  public static void main (String[] args) {
    int x = 3;
    if (x == 3) {
      System.out.println("x must be 3");
    }
    System.out.println("This runs no matter what");
  }
}
```

Output:
```
% java IfTest
x must be 3
This runs no matter what
```

The code above executes the line that prints "x must be 3" only if the condition (x is equal to 3) is true. Regardless of whether it's true, though, the line that prints, "This runs no matter what" will run. So depending on the value of x, either one statement or two will print out.

But we can add an `else` to the condition, so that we can say something like, "If there's still beer, keep coding, else (otherwise) get more beer, and then continue on..."

```java
class IfTest2 {
  public static void main (String[] args) {
    int x = 2;
    if (x == 3) {
      System.out.println("x must be 3");
    } else {
       System.out.println("x is NOT 3");
    }
    System.out.println("This runs no matter what");
  }
}
```

Output:
```
% java IfTest2
x is NOT 3
This runs no matter what
```

**Sidebar: System.out.print vs. System.out.println**

If you've been paying attention (of course you have) then you've noticed us switching between `print` and `println`. Did you spot the difference? `System.out.println` inserts a newline (think of println as print-newline) while `System.out.print` keeps printing to the same line. If you want each thing you print out to be on its own line, use `println`. If you want everything to stick together on one line, use `print`.

### Sharpen your pencil

Given the output:
```
% java DooBee
DooBeeDooBeeDo
```

Fill in the missing code:
```java
public class DooBee {
    public static void main (String[] args) {
        int x = 1;
        while (x < _____ ) {
          System.out._________("Doo");
          System.out._________("Bee");
          x = x + 1;
        }
        if (x == ______ ) {
             System.out.print("Do");
        }
    }
}
```

## Coding a Serious Business Application

Let's put all your new Java skills to good use with something practical. We need a class with a main(), an int and a String variable, a while loop, and an if test. A little more polish, and you'll be building that business backend in no time. But before you look at the code on this page, think for a moment about how you would code that classic children's favorite, "99 bottles of beer."

```java
public class BeerSong {
   public static void main (String[] args) {
     int beerNum = 99;
     String word = "bottles";

      while (beerNum > 0) {

          if (beerNum == 1) {
            word = "bottle"; // singular, as in ONE bottle.
          }

          System.out.println(beerNum + " " + word + " of beer on the wall");
          System.out.println(beerNum + " " + word + " of beer.");
          System.out.println("Take one down.");
          System.out.println("Pass it around.");
          beerNum = beerNum - 1;

        if (beerNum > 0) {
           System.out.println(beerNum + " " + word + " of beer on the wall");
        } else {
           System.out.println("No more bottles of beer on the wall");
        } // end else
     } // end while loop
  } // end main method
} // end class
```

There's still one little flaw in the code. It compiles and runs, but the output isn't 100% perfect. See if you can spot the flaw, and fix it.

## Java Inside Everyday Life (sidebar story)

A whimsical story illustrates Java running inside everyday appliances: Bob's alarm clock, coffee maker, toaster, cell phone, and even his dog's wireless collar all communicate via Java, delaying his coffee and toast when he hits snooze, calling to warn he'll be late, and finally triggering a "jump and bark" signal on the dog's collar to wake him for good.

While there are versions of Java running in devices including PDAs, cell phones, pagers, rings, and smart cards, even a non-Java device can be controlled as if it were one through another interface (e.g. a laptop) running Java — this is known as the **Jini surrogate architecture**.

## Let's Write a Program: Phrase-O-Matic

```java
public class PhraseOMatic {
    public static void main (String[] args) {

        // make three sets of words to choose from. Add your own!
        String[] wordListOne = {"24/7","multi-Tier","30,000 foot","B-to-B","win-win","front-end", "web-based","pervasive", "smart", "six-sigma","critical-path", "dynamic"};

        String[] wordListTwo = {"empowered", "sticky", "value-added", "oriented", "centric", "distributed", "clustered", "branded","outside-the-box", "positioned", "networked", "focused", "leveraged", "aligned", "targeted", "shared", "cooperative", "accelerated"};

        String[] wordListThree = {"process", "tipping-point", "solution", "architecture", "core competency", "strategy", "mindshare", "portal", "space", "vision", "paradigm", "mission"};

        // find out how many words are in each list
        int oneLength = wordListOne.length;
        int twoLength = wordListTwo.length;
        int threeLength = wordListThree.length;

        // generate three random numbers
        int rand1 = (int) (Math.random() * oneLength);
        int rand2 = (int) (Math.random() * twoLength);
        int rand3 = (int) (Math.random() * threeLength);

        // now build a phrase
        String phrase = wordListOne[rand1] + " " + wordListTwo[rand2] + " " + wordListThree[rand3];

        // print out the phrase
        System.out.println("What we need is a " + phrase);
    }
}
```

Note: when typing this into an editor, never hit the return key when you're typing a String (anything between "quotes"). Never let the code do its own word-wrap or line-wrap while you've closed a String — the hyphens you see on this page are real, and you can type them, but don't hit the return key until AFTER you've closed the String.

### How Phrase-O-Matic works

In a nutshell, the program makes three lists of words, then randomly picks one word from each of the three lists, and prints out the result.

**1. Create three String arrays** — the containers that will hold all the words. Declaring and creating an array is easy; here's a small one:
```java
String[] pets = {"Fido", "Zeus", "Bin"};
```
Each word is in quotes (as all good Strings must be) and separated by commas.

**2. Find out how many words are in each list.** For each of the three lists (arrays), the goal is to pick a random word, so we have to know how many words are in each list. If there are 14 words in a list, then we need a random number between 0 and 13 (Java arrays are zero-based, so the first word is at position 0, the second word position 1, and the last word is position 13 in a 14-element array). A Java array can tell you its length. In the `pets` array:
```java
int x = pets.length;
```
and `x` would now hold the value 3.

**3. Generate three random numbers.** Java ships with a set of math methods (for now, think of them as functions). The `random()` method returns a random number between 0 and not-quite-1, so we have to multiply it by the number of elements (the array length) in the list we're using. We have to force the result to be an integer (no decimals allowed!) so we put in a cast (you'll get the details in chapter 4). It's the same as if we had any floating point number that we wanted to convert to an integer:
```java
int x = (int) 24.6;
```

**4. Build the phrase**, by picking a word from each of the three lists, and smooshing them together (also inserting spaces between words). We use the `+` operator, which concatenates (smooshes) the String objects together. To get an element from an array, you give the array the index number (position) of the thing you want:
```java
String s = pets[0]; // s is now the String "Fido"
s = s + " " + "is a dog"; // s is now "Fido is a dog"
```

**5. Print the phrase** to the command-line and... voila! We're in marketing.

## Tonight's Talk: The Compiler and the JVM

*A dramatized debate over who's more important, the compiler or the JVM.*

**The Java Virtual Machine:** What, are you kidding? HELLO. I am Java. I'm the guy who actually makes a program run. The compiler just gives you a file. That's it. Just a file. You can print it out and use it for wall paper, kindling, lining the bird cage whatever, but the file doesn't do anything unless I'm there to run it. And that's another thing, the compiler has no sense of humor. Then again, if you had to spend all day checking nit-picky little syntax violations...

**The Compiler:** I don't appreciate that tone. Excuse me, but without me, what exactly would you run? There's a reason Java was designed to use a bytecode compiler, for your information. If Java were a purely interpreted...
language, where—at runtime—the virtual machine had to translate straight-from-a-text-editor source code, a Java program would run at a ludicrously glacial pace. Java's had a challenging enough time convincing people that it's finally fast and powerful enough for most jobs.

**The Java Virtual Machine:** I'm not saying you're, like, completely useless. But really, what is it that you do? Seriously. I have no idea. A programmer could just write bytecode by hand, and I'd take it. You might be out of a job soon, buddy.

**The Compiler:** Excuse me, but that's quite an ignorant (not to mention arrogant) perspective. While it is true that—theoretically—you can run any properly formatted bytecode even if it didn't come out of a Java compiler, in practice that's absurd. A programmer writing bytecode by hand is like doing your word processing by writing raw postscript. And I would appreciate it if you would not refer to me as "buddy."

**The Java Virtual Machine:** (I rest my case on the humor thing.) But you still didn't answer my question, what do you actually do? Remember that Java is a strongly-typed language, and that means I can't allow variables to hold data of the wrong type. This is a crucial safety feature, and I'm able to stop the vast majority of violations before they ever get to you. And I also—

**The Java Virtual Machine (interrupting):** But some still get through! I can throw ClassCastExceptions and sometimes I get people trying to put the wrong type of thing in an array that was declared to hold something else, and—

**The Compiler:** Excuse me, but I wasn't done. And yes, there are some datatype exceptions that can emerge at runtime, but some of those have to be allowed to support one of Java's other important features—dynamic binding. At runtime, a Java program can include new objects that weren't even known to the original programmer, so I have to allow a certain amount of flexibility. But my job is to stop anything that would never—could never—succeed at runtime. Usually I can tell when something won't work, for example, if a programmer accidentally tried to use a Button object as a Socket connection, I would detect that and thus protect him from causing harm at runtime.

**The Java Virtual Machine:** OK. Sure. But what about security? Look at all the security stuff I do, and you're like, what, checking for semicolons? Oooohhh big security risk! Thank goodness for you!

**The Compiler:** Excuse me, but I am the first line of defense, as they say. The datatype violations I previously described could wreak havoc in a program if they were allowed to manifest. I am also the one who prevents access violations, such as code trying to invoke a private method, or change a method that – for security reasons – must never be changed. I stop people from touching code they're not meant to see, including code trying to access another class' critical data. It would take hours, perhaps days even, to describe the significance of my work.

**The Java Virtual Machine:** Whatever. I have to do that same stuff too, though, just to make sure nobody snuck in after you and changed the bytecode before running it.

**The Compiler:** Of course, but as I indicated previously, if I didn't prevent what amounts to perhaps 99% of the potential problems, you would grind to a halt. And it looks like we're out of time, so we'll have to revisit this in a later chat.

**The Java Virtual Machine:** Oh, you can count on it. Buddy.

### Exercise: Code Magnets

A working Java program is all scrambled up on the fridge. Can you rearrange the code snippets to make a working Java program that produces the output listed below? Some of the curly braces fell on the floor and they were too small to pick up, so feel free to add as many of those as you need!

Scrambled fragments:
```java
if (x == 1) {
    System.out.print("d");
    x = x - 1;
}

if (x == 2) {
    System.out.print("b c");
}

class Shuffle1 {
    public static void main(String [] args) {

if (x > 2) {
    System.out.print("a");
}

int x = 3;

x = x - 1;
System.out.print("-");

while (x > 0) {
```

Output:
```
% java Shuffle1
a-b c-d
```

### Exercise: BE the compiler

Each of the Java files on this page represents a complete source file. Your job is to play compiler and determine whether each of these files will compile. If they won't compile, how would you fix them?

**A**
```java
class Exercise1b {
    int x = 5;
    while ( x > 1 ) {
        x = x - 1;

        if ( x < 3) {
            System.out.println("small x");
        }
    }
}
```

**B**
```java
class Exercise1b {
    int x = 5;
    public static void main(String [] args) {
        int x = 1;
        while ( x < 10 ) {
            if ( x > 3) {
                System.out.println("big x");
            }
        }
    }
}
```

### Puzzle: JavaCross 7.0

A standard crossword. Almost all of the solution words are from chapter 1. A few (non-Java) words from the high-tech world are also thrown in.

**Across**
- 4. Command-line invoker
- 6. Back again?
- 8. Can't go both ways
- 9. Acronym for your laptop's power
- 12. number variable type
- 13. Acronym for a chip
- 14. Say something
- 18. Quite a crew of characters
- 19. Announce a new class or method
- 21. What's a prompt good for?

**Down**
- 1. Not an integer (or _____ your boat)
- 2. Come back empty-handed
- 3. Open house
- 5. 'Things' holders
- 7. Until attitudes improve
- 10. Source code consumer
- 11. Can't pin it down
- 13. Dept. of LAN jockeys
- 15. Shocking modifier
- 16. Just gotta have one
- 17. How to get things done
- 20. Bytecode consumer

### Exercise: Mixed Messages

A short Java program is listed below. One block of the program is missing. Your challenge is to match the candidate block of code (on the left) with the output that you'd see if the block were inserted. Not all the lines of output will be used, and some of the lines of output might be used more than once. (Draw lines connecting the candidate blocks of code with their matching command-line output; answers are at the end of the chapter.)

```java
class Test {
  public static void main(String [] args) {
    int x = 0;
    int y = 0;
    while ( x < 5 ) {

      // candidate code goes here

      System.out.print(x + "" + y +" ");
      x = x + 1;
    }
  }
}
```

Candidates:
```java
y = x - y;
```
```java
y = y + x;
```
```java
y = y + 2;
if( y > 4 ) {
  y = y - 1;
}
```
```java
x = x + 1;
y = y + x;
```
```java
if ( y < 5 ) {
  x = x + 1;
  if ( y < 3 ) {
    x = x - 1;
  }
}
y = y + 2;
```

Possible output:
```
22 46
11 34 59
02 14 26 38
02 14 36 48
00 11 21 32 42
11 21 32 42 53
00 11 23 36 410
02 14 25 36 47
```

### Puzzle: Pool Puzzle

Your job is to take code snippets from the pool and place them into the blank lines in the code. You may not use the same snippet more than once, and you won't need to use all the snippets. Your goal is to make a class that will compile and run and produce the output listed. Don't be fooled — this one's harder than it looks.

Output:
```
%java PoolPuzzleOne
a noise
annoys
an oyster
```

```java
class PoolPuzzleOne {
  public static void main(String [] args) {
    int x = 0;
    while ( __________ ) {
        _____________________________
        if ( x < 1 ) {
        ___________________________
        }
        _____________________________
        if ( __________ ) {
        ____________________________
        ___________
        }
        if ( x == 1 ) {
        ____________________________
        }
        if ( ___________ ) {
        ____________________________
        }
        System.out.println("");
        ____________
    }
  }
}
```

## Puzzle Answers

### Exercise Solutions: Code Magnets

```java
class Shuffle1 {
    public static void main(String [] args) {
        int x = 3;
        while (x > 0) {
            if (x > 2) {
                System.out.print("a");
            }
            x = x - 1;
            System.out.print("-");
            if (x == 2) {
                System.out.print("b c");
            }
            if (x == 1) {
                System.out.print("d");
                x = x - 1;
            }
        }
    }
}
```
Output:
```
% java Shuffle1
a-b c-d
```

### Exercise Solutions: BE the compiler

**A** (the `Exercise1b` file with the `while` loop directly in the class body, no `main` method): This file won't compile without a class declaration wrapping the loop code properly — the `while` loop code must be inside a method. It can't just be hanging out inside the class.

**B** (the `Exercise1b` file with `main`, a shadowed local `x = 1`, and `while (x < 10)` with no increment of `x` inside the loop): This will compile and run (no output, since `x` starts at 1 and the `if (x > 3)` never fires), but without a line incrementing `x`, it would run forever in an infinite `while` loop.

*(Note: the book's answer page also shows a third file, `Foo`, containing the same code as A but properly wrapped inside a class with a `main` method — confirming that once wrapped in a class and method correctly, that version compiles and prints "small x".)*

```java
class Foo {
    public static void main(String [] args) {
        int x = 5;
        while ( x > 1 ) {
            x = x - 1;
            if ( x < 3) {
                System.out.println("small x");
            }
        }
    }
}
```

### Puzzle Answers: JavaCross 7.0

The completed crossword grid (reconstructed from the solution key) spells out these solution words:

| # | Direction | Word |
|---|---|---|
| 4 | Across | JAVAC |
| 6 | Across | LOOP |
| 8 | Across | BRANCH |
| 9 | Across | ADC |
| 12 | Across | INT |
| 13 | Across | CMR |
| 14 | Across | SYSTEMOUTPRINT |
| 18 | Across | STRING |
| 19 | Across | DECLARE |
| 21 | Across | COMMAND |
| 1 | Down | FLOAT |
| 2 | Down | VOID |
| 3 | Down | PUBLIC |
| 5 | Down | ARRAYS |
| 7 | Down | WHILE |
| 10 | Down | COMPILER |
| 11 | Down | VARIABLE |
| 13 | Down | CLI |
| 15 | Down | STATIC |
| 16 | Down | METHOD |
| 17 | Down | ITALIC |
| 20 | Down | JVM |

*(Grid transcribed from the printed solution key; letter placements in the source scan were partially garbled by two-column PDF extraction — the word list above reflects the recoverable solution words themselves.)*

### Exercise Solutions: Mixed Messages

The candidate block that makes the program compile and produce one of the listed outputs is:

```java
if ( y < 5 ) {
  x = x + 1;
  if ( y < 3 ) {
    x = x - 1;
  }
}
y = y + 2;
```

Matching output:
```
02 14 25 36 47
```

### Puzzle Answers: Pool Puzzle

Snippet pool provided:
```
x>0
x<1
x>1
x>3          x = x + 1;
x<4          x = x + 2;        System.out.print("noys ");
             x = x - 2;        System.out.print("oise ");
             x = x - 1;        System.out.print(" oyster ");
                                System.out.print("annoys");
System.out.print(" ");
System.out.print("a");         System.out.print("noise");
System.out.print("n");
System.out.print("an");
```
(Note: Each snippet from the pool can be used only once.)

Completed solution:
```java
class PoolPuzzleOne {
  public static void main(String [] args) {
    int x = 0;
    while ( x < 4 ) {
        System.out.print("a");
        if ( x < 1 ) {
            System.out.print(" ");
        }
        System.out.print("n");
        if ( x > 1 ) {
            System.out.print(" oyster");
            x = x + 2;
        }
        if ( x == 1 ) {
            System.out.print("noys");
        }
        if ( x < 1 ) {
            System.out.print("oise");
        }
        System.out.println("");
        x = x + 1;
    }
  }
}
```

Output:
```
% java PoolPuzzleOne
a noise
annoys
an oyster
```
