# State Pattern

> **Type**: Behavioral  
> **Purpose**: Allows an object to alter its behavior when its internal state changes. The object will appear to change its class.

## Problem Statement
**Vending Machine**.  
Behavior of "Insert Coin" or "Press Button" depends on current state (`Idle`, `HasCoin`, `Dispensing`, `OutOfStock`).

## Bad Code (If-Else)
```python
def insert_coin(self):
    if state == "Idle": accept()
    elif state == "HasCoin": reject()
    elif state == "OutOfStock": reject()
```
*Problem*: Adding a new state requires changing all methods.

## Implementation (State Pattern)

```python
from abc import ABC, abstractmethod

# 1. State Interface
class State(ABC):
    @abstractmethod
    def insert_coin(self): pass
    @abstractmethod
    def press_button(self): pass
    @abstractmethod
    def dispense(self): pass

# 2. Context (The Machine)
class VendingMachine:
    def __init__(self):
        self.idle_state = IdleState(self)
        self.has_coin_state = HasCoinState(self)
        self.current_state = self.idle_state

    def set_state(self, state):
        self.current_state = state

    def insert_coin(self): self.current_state.insert_coin()
    def press_button(self): self.current_state.press_button()

# 3. Concrete States
class IdleState(State):
    def __init__(self, machine): self.machine = machine

    def insert_coin(self):
        print("Coin inserted")
        self.machine.set_state(self.machine.has_coin_state)
    
    def press_button(self): print("Insert coin first")
    def dispense(self): print("Insert coin first")

class HasCoinState(State):
    def __init__(self, machine): self.machine = machine

    def insert_coin(self): print("Coin already inserted")
    
    def press_button(self):
        print("Button pressed...")
        self.machine.set_state(self.machine.idle_state) # Transition logic
        
    def dispense(self): print("Dispensing...")

# Client
vm = VendingMachine()
vm.insert_coin()  # State changes to HasCoin
vm.press_button() # Action allowed
```
