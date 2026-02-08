# LLD striver

<mark style="color:yellow;">I</mark><mark style="color:yellow;">**ntro to LLD**</mark>

* def -— LLD is where we code starts to take shape it is the bridge between the artitecture and actual implementation
* real life examples —&#x20;
  * 3bhk is hld
  * interior designing — lld
* coding example
  * authentication&#x20;
    * login class
    * logout class etc
* key characteristics of LLD
  * granular and code level
  * implementation focused
  * applies OOPs principles
* LLD vs HLD&#x20;
  * ex — code judge
    * HLD
      * frontend — buttions ui etc, from there the request goes to the backend whose contnent goes to the judge and the info is stored in the DB
    * LLD&#x20;
      * for each place how code is maintained, which class is implemented where and how.
* why is LLD important
  * avoids rework
  * improves collaboration
  * promotes scalability
  * captures best practices
* <mark style="color:purple;">**Principles of software design**</mark>&#x20;
  * <mark style="color:red;">**DRY**</mark>
    * do not repeat yourself
    * each piece of knowledge or logic should have a single unambiguous representation within the system&#x20;
    * importance&#x20;
      * reduces redundancy
      * easier maintenance
      * single point of change
    * How to apply
      * identify repetitive code
      * extrace common functionality at the root level
      * leverage existing libraries and frameworks
      * refactor code regulalarly
      * never reinvent the wheel
    * When not to use DRY
      * premature abstraction (major chunk of code)
      * performance critical code (avoid as of now, maybe later when we have time)
      * sacrificing readability&#x20;
      * legacy code  don't touch them
  * <mark style="color:red;">**KISS**</mark>
    * keep it simple stupid
    * a design should be kept as simple as possible complexity should only be introduced when absolute necessary
    * importance&#x20;
      * easier debugging&#x20;
      * improve readability
      * better maintainability
      * faster deployment
  * <mark style="color:red;">**YAGNI**</mark>
    * You are not gonna need it
    * always implement things when you actually need them&#x20;
    * never when you foresee that you might need them&#x20;
    * example — start with simple payment&#x20;
    * when not to follow&#x20;
      * well known requirement&#x20;
      * performance critical code

## <mark style="color:yellow;">Solid principles</mark>

* <mark style="color:purple;">**Singe responsibility principle**</mark>
  * Definition&#x20;
    * A class should have only one reason to change. a class should have only one job one responsibility and one purpose
    * If a class takes more that responsibility , thus responsibility become coupled and changes to one might break the others.
  * why SRP matters
    * ex —&#x20;
      * TUF compiler
        * this process has multiple sub processes, we need to have multiple classes which divides the whole process into multiple sub processes which is handled by multiple classes.
        * we can have one coordinator which coordinator which calls all the class&#x20;
  * Benefits
    * improved maintainability
    * better test coverage
    * lower risk in changes
    * reusable modules
  * common mistakes when violating SRP
    * putting DB logic and business logic in the same class
    * UI code coupled with logic&#x20;
* <mark style="color:purple;">**Open Close principles**</mark>&#x20;
  * definition
    * software entities (classes, modules and functions) should be open for extension but closed for replication&#x20;
      * one should be able to add new behaviours for a class or module
      * without changing the code
    * ex — travel adaptor
  * real life example
    * tax calculator&#x20;
      * if we have a basic tax calculator initially only for the india we have classes which calculate that .
      * Now if we want to add other countries as well we can't change the main logic we can't just add the in else condition there
  * when should we apply OCP&#x20;
    * when we have business rules that are likely to change or expand
    * when you are building a plug-in system
    * when your codebase is becoming god class with a. a lot of conditionals.
  * misconceptions&#x20;
    * OCP means never touching the old code
    * OCP tends to more classes&#x20;
    *

        <figure><img src="../../../.gitbook/assets/image (140).png" alt=""><figcaption></figcaption></figure>
* <mark style="color:purple;">Liskov substitution principles</mark>
  * if S is a subtype of T then objects of T may be replaced with objects of S without adding any of the desirable properties of that program
  * most useful for similar kind of applications
    * simple terms&#x20;
      * if class B is a subtype of class A then we should be able to use B anywhere we have used A and the behaviour should remain correct.
  * why does LSP matter --
    * lsp violation leads to&#x20;
      * broken functionality when subclasses replaces
      * fragile inheritance hierarchies
      * bugs that are hard to find
      * client code being partially completed to specific types.
  * how to find LSP violation&#x20;
    * subclasses throwing unexpected exceptions for base class methods
    * subclass changes behaviour so much that the code fails.
  * key principles&#x20;
    * design by contract abstraction polymorphism etc
    * avoid over inheritance , use composition&#x20;
    * refactor early
  * classic example
    * if we had a notification class which used to send a notification via email
    * if tomorrow we have a new notification which sends a new way of notification&#x20;
* Interfaace segration principle
  * client should not be forced to depend on interface that they dont use&#x20;
    * dont create large bloated interfaces
    * break them into smaller and more specific ones.
  * example&#x20;
    * uber --
      * rider and driver&#x20;
      * we dont need to have a common interface for both rider and driver. ad all the functionality of the rider is not getting used by the driver and vice versa
      * we can have one interface of rider and one for driver
  * benifits&#x20;
    * modularity and&#x20;
    * improved tesability
  * when to apply isp
    * why one interface is doing too many things
    * when some class implementing an interface throws an error.
* Dependency inversion principle
  * defination --
    * high level module should not depend on low level modules&#x20;
    * both should depend on abstraction&#x20;
    * abstraction should not depend on details
    * details should depend on abstraction&#x20;
  * ex --
    * recommendation algo&#x20;
      * recent algo
      * trending algo
      * genre
  * benifits



## UML

* <mark style="color:purple;">**Introduction**</mark>&#x20;
  * what is UML&#x20;
    * standardised visual language used to model and document the designs of software systems.
    * it allows a set of digrams to represent different aspects of a syssmte getting a clear communication among the stakeholders and helping us to understand the design.
  * How many differnt UML digrams&#x20;
    * 14
    * these can be divided into&#x20;
      * structural digrams&#x20;
        * class digram — shows class, interfaces, relationships
        * object digram — snapshot of objects of a specific point in time&#x20;
        * componenet digram — describes the main componenet and their dependencies&#x20;
        * composite structure digram — shows internal structure of a class and collaborations&#x20;
        * deployment digramm— describes the physical deployment of artifacts
        * package digrams — organises eleemnt sinto packages/ modules
        * profile digram — used to define custom steriotypes useful for modeling artifacts
      * Behaviorial digram&#x20;
        * usecase digram — shows system functionality from the user point of view
        * activity digram — flowchart of operationslike processes on workflow
        * sequence digram — shows interaction of objects in a time segment&#x20;
        * communication digram — like sequence digrams but emphasis on object and relationships
        * static machine digrams — shows different states of an object and how it transitions
        * intersection overview digrams — high level overview of digrams&#x20;
        * timining digrams — Focuese on object behaviour with respect of time&#x20;
* <mark style="color:purple;">Class UML digram \[only needed]</mark>
  * UML class notation&#x20;
    * class representation&#x20;
      * class name (TOP)
      * attributes (Middle)
      * Operation/functions/methods (bottom)
    * Visibility makers (access specifiers of attributres /methods)
      * public (+)
      * private(-)
      * protected(#)
      * package (\~)
    * attribute and methods syntax&#x20;
      * attributes : visibility name :type=default value
      * methods : visibility name (parameter list: return type ):return type&#x20;
    *

        <figure><img src="../../../.gitbook/assets/image (42).png" alt=""><figcaption></figcaption></figure>
    * Interface&#x20;
      * <\<interface>> (top)
      * interfce name (bottom)
    * abstract classes
      * _<\<abstract class>> (top)_
      * _abstraact class name (in italics, bottom)_
    * enumerations&#x20;
      * <\<ennumeration>>
      * name
  * perspective of class digram&#x20;



* Creational design&#x20;
  * Design patterns -
    * defination&#x20;
      * they are proven solution to reccuring design problem . They are not code, rather best practices shaped into reusable abstraction
    * types of design patterns (GOF) (23) \[gand of four]
      * Creational (5)
      * structural (7)
      * behavioural (11)
    * creational patterns&#x20;
      * they deal with object creation mechanism
      * ex--
        * singleton
        * factoring
        * abstraction
        * builder
        * prototype
    * Structural pattners&#x20;
      * focuses on how classes and objects are composed to form larger structure. It helps system to work together that otherwise could not because of incompatible interfaces
      * ex --
        * adapter
        * bridge
        * composite
        * Decorator
        * Facade
        * Flyweight
        * proxy
    * Behavioural patterns&#x20;
      * consumed with communication between objects
      * ex --
        * chain of responsibility
        * Command
        * interpretor
        * iterator
        * mediator
        * memento
        * observer
        * state
        * strategy
        * template
        * visitor
* Creational pattern \[solves object creation mechanism]
  * Singleton pattern&#x20;
    * what
      * ensures that the class has one instance throughout the applications lifecycle and provides a glbal access point to that instance
    * why&#x20;
      * should exist only once due to global state, resources constraints on logical reasoning
      * need to be accessed from different parts of the system
    * examples
      * db, logging, analytics
    * how to omplement
      * egar loading and lazy loading
    *
