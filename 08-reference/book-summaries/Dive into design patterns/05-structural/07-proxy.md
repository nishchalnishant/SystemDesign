# Proxy

> **Intent:** Proxy is a structural design pattern that lets you provide a **substitute or
> placeholder** for another object. A proxy **controls access** to the original object, allowing you to
> perform something **either before or after** the request gets through to the original object.

---

## Problem

*Why would you want to control access to an object?* Here is an example: you have a **massive object
that consumes a vast amount of system resources**. You need it **from time to time, but not always**.

> Database queries can be really slow.

You could implement **lazy initialization**: create this object only when it's actually needed. All of
the object's clients would need to execute some **deferred initialization code**. Unfortunately, this
would probably cause **a lot of code duplication**.

In an ideal world, we'd want to put this code **directly into our object's class**, but that isn't
always possible. For instance, the class may be part of a **closed 3rd-party library**.

---

## Solution

The Proxy pattern suggests that you create a **new proxy class with the same interface** as an
original service object. Then you update your app so that it **passes the proxy object to all of the
original object's clients**. Upon receiving a request from a client, the proxy **creates a real
service object and delegates all the work to it**.

> The proxy disguises itself as a database object. It can handle **lazy initialization and result
> caching** without the client or the real database object even knowing.

**But what's the benefit?** If you need to execute something either before or after the primary logic
of the class, the proxy lets you do this **without changing that class**. Since the proxy implements
the same interface as the original class, **it can be passed to any client that expects a real service
object**.

---

## Real-World Analogy

**Credit cards can be used for payments just the same as cash.**

A **credit card is a proxy for a bank account**, which is a **proxy for a bundle of cash**. Both
implement the **same interface**: they can be used for making a payment. A consumer feels great
because there's no need to carry loads of cash around. A shop owner is also happy since the income
from a transaction gets added electronically to the shop's bank account without the risk of losing the
deposit or getting robbed on the way to the bank.

---

## Structure

1. **The Service Interface** declares the interface of the Service. The proxy **must follow this
   interface** to be able to disguise itself as a service object.

2. **The Service** is a class that provides some useful business logic.

3. **The Proxy class** has a **reference field that points to a service object**. After the proxy
   finishes its processing (e.g., **lazy initialization, logging, access control, caching**, etc.), it
   **passes the request to the service object**.
   - Usually, **proxies manage the full lifecycle** of their service objects.

4. **The Client** should work with **both services and proxies via the same interface**. This way you
   can pass a proxy into any code that expects a service object.

```
┌────────┐      ┌───────────────────────────┐
│ Client │─────►│ «interface» ServiceInterface│
└────────┘      ├───────────────────────────┤
                │ operation()               │
                └───────────────────────────┘
                    ▲                  ▲
        ┌───────────┴──────┐   ┌───────┴────────────────────────┐
        │     Service      │◄──│           Proxy                │
        ├──────────────────┤   ├────────────────────────────────┤
        │  operation()     │   │ realService: Service           │
        └──────────────────┘   │ Proxy(s: Service)              │
                               │ checkAccess()                  │
                               │ operation() {                  │
                               │   if (checkAccess())           │
                               │     realService.operation()    │
                               │ }                              │
                               └────────────────────────────────┘
```

---

## Pseudocode

This example illustrates how the Proxy pattern can help to introduce **lazy initialization and
caching** to a **3rd-party YouTube integration library**.

The library provides us with the video downloading class. However, it's **very inefficient**. If the
client application requests the same video multiple times, the library just **downloads it over and
over**, instead of caching and reusing the first downloaded file.

The proxy class implements the **same interface** as the original downloader and delegates it all the
work. However, it keeps track of the downloaded files and **returns the cached result** when the app
requests the same video multiple times.

```
 1   // The interface of a remote service.
 2   interface ThirdPartyYouTubeLib is
 3     method listVideos()
 4     method getVideoInfo(id)
 5     method downloadVideo(id)
 6
 7   // The concrete implementation of a service connector. Methods
 8   // of this class can request information from YouTube. The speed
 9   // of the request depends on a user's internet connection as
10   // well as YouTube's. The application will slow down if a lot of
11   // requests are fired at the same time, even if they all request
12   // the same information.
13   class ThirdPartyYouTubeClass implements ThirdPartyYouTubeLib is
14     method listVideos() is
15       // Send an API request to YouTube.
16
17     method getVideoInfo(id) is
18       // Get metadata about some video.
19
20     method downloadVideo(id) is
21       // Download a video file from YouTube.
22
23   // To save some bandwidth, we can cache request results and keep
24   // them for some time. But it may be impossible to put such code
25   // directly into the service class. For example, it could have
26   // been provided as part of a third party library and/or defined
27   // as `final`. That's why we put the caching code into a new
28   // proxy class which implements the same interface as the
29   // service class. It delegates to the service object only when
30   // the real requests have to be sent.
31   class CachedYouTubeClass implements ThirdPartyYouTubeLib is
32     private field service: ThirdPartyYouTubeLib
33     private field listCache, videoCache
34     field needReset
35
36     constructor CachedYouTubeClass(service: ThirdPartyYouTubeLib) is
37       this.service = service
38
39     method listVideos() is
40       if (listCache == null || needReset)
41         listCache = service.listVideos()
42       return listCache
43
44     method getVideoInfo(id) is
45       if (videoCache == null || needReset)
46         videoCache = service.getVideoInfo(id)
47       return videoCache
48
49     method downloadVideo(id) is
50       if (!downloadExists(id) || needReset)
51         service.downloadVideo(id)
52
53   // The GUI class, which used to work directly with a service
54   // object, stays unchanged as long as it works with the service
55   // object through an interface. We can safely pass a proxy
56   // object instead of a real service object since they both
57   // implement the same interface.
58   class YouTubeManager is
59     protected field service: ThirdPartyYouTubeLib
60
61     constructor YouTubeManager(service: ThirdPartyYouTubeLib) is
62       this.service = service
63
64     method renderVideoPage(id) is
65      info = service.getVideoInfo(id)
66      // Render the video page.
67
68     method renderListPanel() is
69      list = service.listVideos()
70      // Render the list of video thumbnails.
71
72     method reactOnUserInput() is
73      renderVideoPage()
74      renderListPanel()
75
76   // The application can configure proxies on the fly.
77   class Application is
78     method init() is
79      aYouTubeService = new ThirdPartyYouTubeClass()
80      aYouTubeProxy = new CachedYouTubeClass(aYouTubeService)
81      manager = new YouTubeManager(aYouTubeProxy)
82      manager.reactOnUserInput()
```

---

## Java Implementation

A complete, compilable translation of the pseudocode above (`ProxyDemo.java`). The
"downloads" are simulated with `Thread.sleep`, so the timings in the output are real and the
cache's effect is measurable.

```java
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// ─── The Service Interface — shared by real service AND proxy ─────────
// This is what makes the substitution invisible to the client.
interface ThirdPartyYouTubeLib {
    List<String> listVideos();
    String getVideoInfo(String id);
    void downloadVideo(String id);
}

// ─── The Real Service (slow, third-party, unmodifiable) ───────────────
class ThirdPartyYouTubeClass implements ThirdPartyYouTubeLib {

    @Override
    public List<String> listVideos() {
        experienceNetworkLatency();
        System.out.println("    [network] fetched video list from YouTube");
        return List.of("catz", "dogz", "birdz");
    }

    @Override
    public String getVideoInfo(String id) {
        experienceNetworkLatency();
        System.out.println("    [network] fetched metadata for '" + id + "'");
        return "Video metadata for " + id;
    }

    @Override
    public void downloadVideo(String id) {
        experienceNetworkLatency();
        System.out.println("    [network] downloaded video '" + id + "'");
    }

    private void experienceNetworkLatency() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

// ─── The Proxy: caching + lazy initialization ─────────────────────────
class CachedYouTubeClass implements ThirdPartyYouTubeLib {
    private ThirdPartyYouTubeLib service;          // created lazily
    private List<String> listCache;
    private final Map<String, String> videoCache = new HashMap<>();
    private final Map<String, Boolean> downloadExists = new HashMap<>();

    boolean needReset = false;

    /**
     * LAZY INITIALIZATION: the expensive service object is not built
     * until something actually needs it. If every call is a cache hit,
     * it is never built at all.
     */
    private ThirdPartyYouTubeLib service() {
        if (service == null) {
            System.out.println("    [proxy] creating the real service (first use)");
            service = new ThirdPartyYouTubeClass();
        }
        return service;
    }

    @Override
    public List<String> listVideos() {
        if (listCache == null || needReset) {
            listCache = service().listVideos();
        } else {
            System.out.println("    [proxy] CACHE HIT for video list");
        }
        return listCache;
    }

    @Override
    public String getVideoInfo(String id) {
        String cached = videoCache.get(id);
        if (cached == null || needReset) {
            cached = service().getVideoInfo(id);
            videoCache.put(id, cached);
        } else {
            System.out.println("    [proxy] CACHE HIT for metadata '" + id + "'");
        }
        return cached;
    }

    @Override
    public void downloadVideo(String id) {
        if (!downloadExists.getOrDefault(id, false) || needReset) {
            service().downloadVideo(id);
            downloadExists.put(id, true);
        } else {
            System.out.println("    [proxy] CACHE HIT — '" + id
                    + "' already on disk, skipping download");
        }
    }
}

// ─── The Client — unchanged, and unaware ──────────────────────────────
class YouTubeManager {
    protected final ThirdPartyYouTubeLib service;

    YouTubeManager(ThirdPartyYouTubeLib service) {
        this.service = service;
    }

    void renderVideoPage(String id) {
        String info = service.getVideoInfo(id);
        System.out.println("  rendering page: " + info);
    }

    void renderListPanel() {
        List<String> list = service.listVideos();
        System.out.println("  rendering thumbnails: " + list);
    }

    void reactOnUserInput(String id) {
        renderVideoPage(id);
        renderListPanel();
    }
}

// ─── Demo ─────────────────────────────────────────────────────────────
public class ProxyDemo {
    public static void main(String[] args) {
        ThirdPartyYouTubeLib naive = new ThirdPartyYouTubeClass();
        ThirdPartyYouTubeLib proxied = new CachedYouTubeClass();

        long t1 = time(new YouTubeManager(naive), "WITHOUT proxy");
        long t2 = time(new YouTubeManager(proxied), "WITH caching proxy");

        System.out.println();
        System.out.println("Naive:  " + roundToTenth(t1) + " ms (approx)");
        System.out.println("Cached: " + roundToTenth(t2) + " ms (approx)");
    }

    static long time(YouTubeManager manager, String label) {
        System.out.println(label + ":");
        long start = System.currentTimeMillis();
        manager.reactOnUserInput("catz");
        manager.reactOnUserInput("catz");     // same video again
        manager.reactOnUserInput("catz");     // and again
        return System.currentTimeMillis() - start;
    }

    /** Round to the nearest 100ms so the printed output is stable. */
    static long roundToTenth(long ms) {
        return Math.round(ms / 100.0) * 100;
    }
}
```

**Output**

```
WITHOUT proxy:
    [network] fetched metadata for 'catz'
  rendering page: Video metadata for catz
    [network] fetched video list from YouTube
  rendering thumbnails: [catz, dogz, birdz]
    [network] fetched metadata for 'catz'
  rendering page: Video metadata for catz
    [network] fetched video list from YouTube
  rendering thumbnails: [catz, dogz, birdz]
    [network] fetched metadata for 'catz'
  rendering page: Video metadata for catz
    [network] fetched video list from YouTube
  rendering thumbnails: [catz, dogz, birdz]
WITH caching proxy:
    [proxy] creating the real service (first use)
    [network] fetched metadata for 'catz'
  rendering page: Video metadata for catz
    [network] fetched video list from YouTube
  rendering thumbnails: [catz, dogz, birdz]
    [proxy] CACHE HIT for metadata 'catz'
  rendering page: Video metadata for catz
    [proxy] CACHE HIT for video list
  rendering thumbnails: [catz, dogz, birdz]
    [proxy] CACHE HIT for metadata 'catz'
  rendering page: Video metadata for catz
    [proxy] CACHE HIT for video list
  rendering thumbnails: [catz, dogz, birdz]

Naive:  1200 ms (approx)
Cached: 400 ms (approx)
```

Six network round trips become two. `YouTubeManager` was **not modified** — the only change is which
object was handed to its constructor.

### Notes on the Java translation

- The proxy's power comes entirely from **implementing the same interface** as the service. That is
  the precondition for the substitution being invisible.
- Two patterns are stacked here, as the book does: **caching** (skip repeat work) and **lazy
  initialization** (don't even build the service until first use). `[proxy] creating the real
  service (first use)` prints exactly once.
- `needReset` is the cache-invalidation escape hatch. Real code would use a TTL or size-bounded
  eviction instead of a boolean.

### The kinds of proxy

Same structure, different intent — the name comes from what you put in the delegating method:

| Kind | What the proxy adds before/after delegating |
|---|---|
| **Virtual** | Lazy creation of an expensive object (`service()` above) |
| **Caching** | Store and reuse results (the demo's main job) |
| **Protection** | Check permissions, reject unauthorized callers |
| **Remote** | Hide network/RPC; the "service" lives on another machine |
| **Logging** | Record every call and its arguments |
| **Smart reference** | Reference counting, auto-close of unused resources |

A protection proxy in ten lines:

```java
class ProtectedYouTube implements ThirdPartyYouTubeLib {
    private final ThirdPartyYouTubeLib service;
    private final String role;

    ProtectedYouTube(ThirdPartyYouTubeLib service, String role) {
        this.service = service;
        this.role = role;
    }

    @Override
    public void downloadVideo(String id) {
        if (!role.equals("PREMIUM")) {
            throw new SecurityException("Downloads require a premium account");
        }
        service.downloadVideo(id);
    }

    @Override public List<String> listVideos()        { return service.listVideos(); }
    @Override public String getVideoInfo(String id)   { return service.getVideoInfo(id); }
}
```

### Dynamic proxies — Java's built-in support

Writing a proxy class per interface is tedious. `java.lang.reflect.Proxy` generates one at runtime:

```java
import java.lang.reflect.*;

ThirdPartyYouTubeLib logged = (ThirdPartyYouTubeLib) Proxy.newProxyInstance(
    ThirdPartyYouTubeLib.class.getClassLoader(),
    new Class<?>[]{ ThirdPartyYouTubeLib.class },
    (proxy, method, methodArgs) -> {
        System.out.println("-> " + method.getName());
        Object result = method.invoke(new ThirdPartyYouTubeClass(), methodArgs);
        System.out.println("<- " + method.getName());
        return result;
    });
```

This is the machinery behind Spring AOP, `@Transactional`, `@Cacheable`, Mockito mocks, and JPA
lazy-loaded entities. (For classes rather than interfaces, frameworks use CGLIB or ByteBuddy to
generate a subclass instead.)

### Proxy vs. Decorator vs. Adapter

All three wrap an object and implement its interface. They differ in **intent** and in **who
controls the lifecycle**:

| | Proxy | Decorator | Adapter |
|---|---|---|---|
| Intent | Control **access** to the object | **Add** responsibilities | **Change** the interface |
| Interface vs. wrappee | Identical | Identical | Different |
| Who creates the wrappee | Usually the **proxy itself** | The **client** passes it in | The client passes it in |
| Typical stacking | One layer | Many layers | One layer |

### Where this appears in the JDK

- `java.lang.reflect.Proxy` — the dynamic proxy facility itself
- `java.rmi.*` — remote proxies; the stub looks local and talks over the wire
- `java.lang.ref.WeakReference` / `SoftReference` — smart-reference proxies
- Hibernate/JPA lazy entity proxies — the object you hold isn't loaded until you touch a field
- Spring `@Transactional`, `@Cacheable`, `@Async` — every one is a proxy wrapped around your bean

---

## Applicability

*There are dozens of ways to utilize the Proxy pattern. Let's go over the most popular uses.*

### ▸ Lazy initialization (virtual proxy)

This is when you have a **heavyweight service object that wastes system resources by being always
up**, even though you only need it from time to time.

Instead of creating the object when the app launches, you can **delay the object's initialization** to
a time when it's really needed.

### ▸ Access control (protection proxy)

This is when you want **only specific clients to be able to use the service object**; for instance,
when your objects are crucial parts of an operating system and clients are various launched
applications (**including malicious ones**).

The proxy can pass the request to the service object **only if the client's credentials match some
criteria**.

### ▸ Local execution of a remote service (remote proxy)

This is when the service object is located on a **remote server**.

In this case, the proxy **passes the client request over the network**, handling all of the nasty
details of working with the network.

### ▸ Logging requests (logging proxy)

This is when you want to keep a **history of requests** to the service object. The proxy can **log
each request** before passing it to the service.

### ▸ Caching request results (caching proxy)

This is when you need to **cache results of client requests and manage the life cycle of this cache**,
especially if results are quite large.

The proxy can implement caching for **recurring requests that always yield the same results**. The
proxy may use the **parameters of requests as the cache keys**.

### ▸ Smart reference

This is when you need to be able to **dismiss a heavyweight object once there are no clients that use
it**.

The proxy can **keep track of clients** that obtained a reference to the service object or its
results. From time to time, the proxy may go over the clients and check whether they are still active.
If the client list gets empty, the proxy might **dismiss the service object and free the underlying
system resources**.

The proxy can also **track whether the client had modified the service object**. Then the unchanged
objects may be **reused by other clients**.

---

## How to Implement

1. **If there's no pre-existing service interface, create one** to make proxy and service objects
   interchangeable. Extracting the interface from the service class isn't always possible, because
   you'd need to change all of the service's clients to use that interface. **Plan B** is to make the
   **proxy a subclass of the service class**, and this way it'll inherit the interface of the service.

2. **Create the proxy class.** It should have a field for storing a reference to the service. Usually,
   proxies **create and manage the whole life cycle** of their services. On rare occasions, a service
   is **passed to the proxy via a constructor** by the client.

3. **Implement the proxy methods according to their purposes.** In most cases, after doing some work,
   the proxy should **delegate the work to the service object**.

4. **Consider introducing a creation method** that decides whether the client gets a proxy or a real
   service. This can be a simple **static method** in the proxy class or a full-blown **factory
   method**.

5. **Consider implementing lazy initialization** for the service object.

---

## Pros and Cons

**✅ Pros**

- You can **control the service object without clients knowing** about it.
- You can **manage the lifecycle** of the service object when clients don't care about it.
- The proxy **works even if the service object isn't ready or is not available**.
- **Open/Closed Principle.** You can introduce new proxies without changing the service or clients.

**❌ Cons**

- The code may become **more complicated** since you need to introduce a lot of new classes.
- The **response from the service might get delayed**.

---

## Relations with Other Patterns

- **Adapter** provides a **different** interface to the wrapped object, **Proxy** provides it with the
  **same** interface, and **Decorator** provides it with an **enhanced** interface.
- **Facade** is similar to **Proxy** in that both buffer a complex entity and initialize it on its own.
  Unlike Facade, **Proxy has the same interface as its service object**, which makes them
  interchangeable.
- **Decorator** and **Proxy** have similar structures, but very different intents. Both are built on
  the composition principle. The difference is that a **Proxy usually manages the life cycle of its
  service object on its own**, whereas the composition of **Decorators is always controlled by the
  client**.
