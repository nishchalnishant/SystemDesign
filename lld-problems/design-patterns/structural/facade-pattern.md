# Facade Pattern

> **Type**: Structural  
> **Purpose**: Provides a simplified interface to a complex subsystem.

## Real-World Analogy
A **Receptionist**. Instead of you calling the HR, then IT, then Security to join a company, you talk to the Receptionist/Onboarding Manager who coordinates everything.

## Problem Statement
A `HomeTheater` system requires turning on the TV, setting the Amp to HDMI, dimming lights, and starting the Streamer.  
Client just wants `watch_movie()`.

## Implementation

```java
// Subsystem Components
class Amplifier {
    public void on() { System.out.println("Amp ON"); }
    public void setVolume(int level) { System.out.println("Amp Volume " + level); }
}

class Projector {
    public void on() { System.out.println("Projector ON"); }
    public void setInput(String src) { System.out.println("Projector Input " + src); }
}

class Lights {
    public void dim(int level) { System.out.println("Lights dimmed to " + level + "%"); }
}

// Facade
class HomeTheaterFacade {
    private Amplifier amp;
    private Projector proj;
    private Lights lights;
    
    public HomeTheaterFacade() {
        this.amp = new Amplifier();
        this.proj = new Projector();
        this.lights = new Lights();
    }
    
    public void watchMovie(String movie) {
        System.out.println("--- Get ready to watch " + movie + " ---");
        lights.dim(10);
        proj.on();
        proj.setInput("Netflix");
        amp.on();
        amp.setVolume(5);
    }
}

// Client
public class Main {
    public static void main(String[] args) {
        // Simple interface, hides complexity
        HomeTheaterFacade homeTheater = new HomeTheaterFacade();
        homeTheater.watchMovie("Inception");
    }
}
```
