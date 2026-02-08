# Facade Pattern

> **Type**: Structural  
> **Purpose**: Provides a simplified interface to a complex subsystem.

## Real-World Analogy
A **Receptionist**. Instead of you calling the HR, then IT, then Security to join a company, you talk to the Receptionist/Onboarding Manager who coordinates everything.

## Problem Statement
A `HomeTheater` system requires turning on the TV, setting the Amp to HDMI, dimming lights, and starting the Streamer.  
Client just wants `watch_movie()`.

## Implementation

```python
# Subsystem Components
class Amplifier:
    def on(self): print("Amp ON")
    def set_volume(self, level): print(f"Amp Volume {level}")

class Projector:
    def on(self): print("Projector ON")
    def set_input(self, src): print(f"Projector Input {src}")

class Lights:
    def dim(self, level): print(f"Lights dimmed to {level}%")

# Facade
class HomeTheaterFacade:
    def __init__(self):
        self.amp = Amplifier()
        self.proj = Projector()
        self.lights = Lights()
    
    def watch_movie(self, movie):
        print(f"--- Get ready to watch {movie} ---")
        self.lights.dim(10)
        self.proj.on()
        self.proj.set_input("Netflix")
        self.amp.on()
        self.amp.set_volume(5)

# Client
if __name__ == "__main__":
    # Simple interface, hides complexity
    home_theater = HomeTheaterFacade()
    home_theater.watch_movie("Inception")
```
