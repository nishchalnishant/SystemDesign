---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, system-design, problems]
---
# Design Coupon System

> **Difficulty**: Medium
> **Topics**: Strategy Pattern, Chain of Responsibility, Composite Pattern
> **Extension**: BOGO, coupon stacking, per-user usage limits, Redis atomic counters

---

## Opening Analogy

You are at an e-commerce checkout with a promo code box. You type `SUMMER20`. The system must answer two questions: (1) **Is this coupon valid for your cart?** (expiry check, minimum order value check, category restriction check, per-user usage limit check), and (2) **How much do you save?** (percentage off, flat amount off, free shipping, buy-X-get-Y). These two questions are structurally different. Validation is a series of yes/no gates (Chain of Responsibility). Discount calculation is an algorithm choice (Strategy). Keeping them separate is the core design insight.

---

## Phase 1: Requirements

### Functional
- Apply a coupon code to a cart and calculate the discounted total.
- Coupon types: percentage off (with optional cap), flat amount off, free shipping, buy-X-get-Y-free.
- Constraints: expiry date, minimum order value, category restriction, per-user usage limit, global usage limit.
- Admin creates and configures coupons (code, type, constraints).
- Support stacking: multiple coupons applied in sequence (optional, configurable).

### Non-Functional
- Validation pipeline must be O(C) where C = number of constraints on the coupon.
- Atomic enforcement of global usage limit (concurrent users must not exceed limit).
- Coupon validation must complete in < 100ms.

---

## Phase 2: Use Cases

### Actors
- **Customer** — enters code at checkout, sees discounted price.
- **Admin** — creates coupon configs; sets constraints, reward type, limits.
- **System** — validates, calculates, records usage.

### UC1: Apply Coupon
1. Customer enters `code` at checkout.
2. System fetches `Coupon` configuration from store.
3. System runs the constraint chain: each validator gate checks one rule.
4. First failing gate rejects the coupon with a descriptive message.
5. If all gates pass, system calls the reward strategy to compute discount.
6. System returns `(discountAmount, finalTotal)`.
7. System records coupon usage (increment per-user and global counters).

### UC2: Admin Creates Coupon
1. Admin defines: code, reward type + parameters, list of constraints.
2. System validates no duplicate code exists.
3. System persists coupon.

### UC3: Stack Coupons
1. Customer applies multiple codes.
2. System validates each code independently.
3. System applies discounts sequentially: first coupon's discount base = cart total; second's base = result of first.
4. Final total is returned.

---

## Phase 3: Class Diagram

```
┌──────────────────────────────────────────────┐
│                   Coupon                     │
│──────────────────────────────────────────────│
│ - code: String                               │
│ - reward: DiscountStrategy                   │
│ - validatorChain: CouponValidator            │
│ - maxUsageGlobal: int                        │
│ - maxUsagePerUser: int                       │
│ - usageCounter: AtomicInteger (global)       │
│──────────────────────────────────────────────│
│ + validate(cart, userId): ValidationResult   │
│ + apply(cart): BigDecimal (discount amount)  │
│ + recordUsage(userId): void                  │
└──────────────────────────────────────────────┘

<<interface>>                <<implementations>>
DiscountStrategy             ─────────────────────
─────────────────            PercentageOffStrategy
calculate(cart): BigDecimal  FlatOffStrategy
                             FreeShippingStrategy
                             BuyXGetYStrategy

<<interface>>                    <<chain implementations>>
CouponValidator                  ────────────────────────
─────────────────────            ExpiryValidator
+ setNext(v): CouponValidator    MinOrderValidator
+ validate(cart,userId,coupon)   CategoryValidator
  : ValidationResult             PerUserLimitValidator
                                 GlobalLimitValidator

┌──────────────────────────────────────────────┐
│                    Cart                      │
│──────────────────────────────────────────────│
│ - items: List<CartItem>                      │
│ - shippingCost: BigDecimal                   │
│──────────────────────────────────────────────│
│ + getTotal(): BigDecimal                     │
│ + hasCategory(category): boolean             │
│ + getItemsByCategory(cat): List<CartItem>    │
└──────────────────────────────────────────────┘

┌──────────────────────────┐
│    ValidationResult      │
│──────────────────────────│
│ - valid: boolean         │
│ - reason: String         │
└──────────────────────────┘

┌─────────────────────────────────────┐
│         CouponService               │
│─────────────────────────────────────│
│ - coupons: Map<code, Coupon>        │
│ - usageTracker: UsageTracker        │
│─────────────────────────────────────│
│ + applyCoupon(code, cart, userId)   │
│ + stackCoupons(codes, cart, userId) │
│ + createCoupon(config): Coupon      │
└─────────────────────────────────────┘
```

---

## Phase 4: Design Patterns Applied

### 1. Strategy Pattern — Discount calculation
**Why:** `PercentageOffStrategy`, `FlatOffStrategy`, `BuyXGetYStrategy` all answer the same question: "How much does the customer save?" They have different algorithms but the same interface: `calculate(cart)`. Strategy lets you add `FreeShippingStrategy` or `BuyXGetYStrategy` as new files with zero changes to `Coupon` or `CouponService`.

### 2. Chain of Responsibility — Constraint validation
**Why:** Each constraint is an independent yes/no check. Chaining them means each validator checks its one rule and either passes to the next or fails immediately with a specific message. This separates concerns: `ExpiryValidator` knows only about expiry; `CategoryValidator` knows only about category. Adding a new constraint (e.g., `FirstPurchaseValidator`) is adding one new class and inserting it into the chain — no changes to existing validators.

### 3. Composite Pattern — Constraint grouping (optional)
**Why:** Some coupons have compound constraints: "valid for Electronics OR Sports, but NOT both". A `CompositeValidator` can hold `AND` or `OR` logic over child validators, making complex rules expressible without hardcoding the logic into any single class.

---

## Phase 5: Key Implementation

```python
from abc import ABC, abstractmethod
from datetime import date
from decimal import Decimal

# ── Cart Model ─────────────────────────────────────────────────────────────

class CartItem:
    def __init__(self, name, category, price, quantity):
        self.name     = name
        self.category = category
        self.price    = price
        self.quantity = quantity

class Cart:
    def __init__(self, shipping_cost=Decimal("9.99")):
        self._items = []           # list of CartItem
        self._shipping_cost = shipping_cost

    def add_item(self, item):
        self._items.append(item)

    def get_total(self):
        return sum((i.price * i.quantity for i in self._items), Decimal(0))

    def get_shipping_cost(self):
        return self._shipping_cost

    def get_items(self):
        return list(self._items)

    def has_category(self, cat):
        return any(i.category.lower() == cat.lower() for i in self._items)

    def get_items_by_category(self, cat):
        return [i for i in self._items if i.category.lower() == cat.lower()]

# ── Validation ─────────────────────────────────────────────────────────────

class ValidationResult:
    def __init__(self, valid, reason=None):
        self.valid  = valid
        self.reason = reason

    @staticmethod
    def ok():
        return ValidationResult(True)

    @staticmethod
    def fail(reason):
        return ValidationResult(False, reason)

# Chain of Responsibility: each validator delegates to next if own check passes
class CouponValidator(ABC):
    def __init__(self):
        self._next = None  # CouponValidator | None

    def set_next(self, n):
        self._next = n
        return n

    def _validate_next(self, cart, user_id, coupon):
        return self._next.validate(cart, user_id, coupon) if self._next else ValidationResult.ok()

    @abstractmethod
    def validate(self, cart, user_id, coupon): ...

class ExpiryValidator(CouponValidator):
    def validate(self, cart, user_id, coupon):
        if coupon.expiry_date is not None and date.today() > coupon.expiry_date:
            return ValidationResult.fail(f"Coupon expired on {coupon.expiry_date}")
        return self._validate_next(cart, user_id, coupon)

class MinOrderValidator(CouponValidator):
    def validate(self, cart, user_id, coupon):
        if coupon.min_order_value is not None and cart.get_total() < coupon.min_order_value:
            return ValidationResult.fail(f"Minimum order value: {coupon.min_order_value}")
        return self._validate_next(cart, user_id, coupon)

class CategoryValidator(CouponValidator):
    def validate(self, cart, user_id, coupon):
        if coupon.required_category is not None and not cart.has_category(coupon.required_category):
            return ValidationResult.fail(f"Coupon valid only for: {coupon.required_category}")
        return self._validate_next(cart, user_id, coupon)

class GlobalLimitValidator(CouponValidator):
    def validate(self, cart, user_id, coupon):
        if coupon.max_global_usage > 0 and coupon.global_usage_count >= coupon.max_global_usage:
            return ValidationResult.fail("Coupon usage limit reached.")
        return self._validate_next(cart, user_id, coupon)

class PerUserLimitValidator(CouponValidator):
    def validate(self, cart, user_id, coupon):
        used = coupon.per_user_usage.get(user_id, 0)
        if coupon.max_per_user_usage > 0 and used >= coupon.max_per_user_usage:
            return ValidationResult.fail(f"You have already used this coupon {used} time(s).")
        return self._validate_next(cart, user_id, coupon)

# ── Discount Strategies ────────────────────────────────────────────────────

class DiscountStrategy(ABC):
    @abstractmethod
    def calculate(self, cart): ...

class PercentageOffStrategy(DiscountStrategy):
    def __init__(self, percentage, max_discount=None):
        self._percentage   = percentage
        self._max_discount = max_discount  # cap; None = uncapped

    def calculate(self, cart):
        discount = cart.get_total() * self._percentage / Decimal(100)
        if self._max_discount is not None:
            discount = min(discount, self._max_discount)
        return discount

class FlatOffStrategy(DiscountStrategy):
    def __init__(self, amount):
        self._amount = amount

    def calculate(self, cart):
        return min(self._amount, cart.get_total())  # cannot exceed cart total

class FreeShippingStrategy(DiscountStrategy):
    def calculate(self, cart):
        return cart.get_shipping_cost()

class BuyXGetYStrategy(DiscountStrategy):
    def __init__(self, buy_x, get_y, category):
        self._buy_x    = buy_x
        self._get_y    = get_y
        self._category = category

    def calculate(self, cart):
        items = cart.get_items_by_category(self._category)
        # Expand to individual unit prices, sort ascending (cheapest are free)
        prices = sorted(
            (item.price for item in items for _ in range(item.quantity))
        )
        discount = Decimal(0)
        set_size = self._buy_x + self._get_y
        sets     = len(prices) // set_size
        for s in range(sets):
            # The Y cheapest items in each set are free
            for y in range(self._get_y):
                discount += prices[s * set_size + y]
        return discount

# ── Coupon ─────────────────────────────────────────────────────────────────

class Coupon:
    def __init__(self, code, reward, validator_chain):
        self.code            = code
        self._reward         = reward
        self._validator_chain = validator_chain

        # Constraint fields (read by validators)
        self.expiry_date       = None
        self.min_order_value   = None
        self.required_category = None
        self.max_global_usage  = 0
        self.max_per_user_usage = 0

        # Usage tracking
        self.global_usage_count = 0
        self.per_user_usage     = {}

    def validate(self, cart, user_id):
        return self._validator_chain.validate(cart, user_id, self)

    def calculate_discount(self, cart):
        return self._reward.calculate(cart)

    def record_usage(self, user_id):
        self.global_usage_count += 1
        self.per_user_usage[user_id] = self.per_user_usage.get(user_id, 0) + 1

# ── Coupon Builder ─────────────────────────────────────────────────────────

class CouponBuilder:
    def __init__(self):
        self._code               = None
        self._reward             = None
        self._expiry_date        = None
        self._min_order_value    = None
        self._required_category  = None
        self._max_global_usage   = 0
        self._max_per_user_usage = 0

    def code(self, c):              self._code = c;                    return self
    def reward(self, r):            self._reward = r;                  return self
    def expires_on(self, d):        self._expiry_date = d;            return self
    def min_order(self, v):         self._min_order_value = v;        return self
    def category(self, cat):        self._required_category = cat;    return self
    def global_limit(self, n):      self._max_global_usage = n;       return self
    def per_user_limit(self, n):    self._max_per_user_usage = n;     return self

    def build(self):
        # Build chain: Expiry → MinOrder → Category → GlobalLimit → PerUserLimit
        head = ExpiryValidator()
        head.set_next(MinOrderValidator()) \
            .set_next(CategoryValidator()) \
            .set_next(GlobalLimitValidator()) \
            .set_next(PerUserLimitValidator())

        c = Coupon(self._code, self._reward, head)
        c.expiry_date        = self._expiry_date
        c.min_order_value    = self._min_order_value
        c.required_category  = self._required_category
        c.max_global_usage   = self._max_global_usage
        c.max_per_user_usage = self._max_per_user_usage
        return c

# ── Coupon Service ─────────────────────────────────────────────────────────

class CouponService:
    def __init__(self):
        self._coupons = {}

    def register_coupon(self, c):
        self._coupons[c.code] = c

    # Apply one coupon to cart; returns discount amount or raises
    def apply_coupon(self, code, cart, user_id):
        coupon = self._coupons.get(code)
        if coupon is None:
            raise ValueError(f"Unknown coupon: {code}")

        result = coupon.validate(cart, user_id)
        if not result.valid:
            raise RuntimeError(f"Coupon invalid: {result.reason}")

        discount = coupon.calculate_discount(cart)
        coupon.record_usage(user_id)
        print(f"Coupon {code} applied. Discount: {discount}. Final total: {cart.get_total() - discount}")
        return discount

    # Stack multiple coupons sequentially
    def stack_coupons(self, codes, cart, user_id):
        total_discount = Decimal(0)
        for code in codes:
            try:
                total_discount += self.apply_coupon(code, cart, user_id)
            except Exception as e:
                print(f"Skipping coupon {code}: {e}")
        return total_discount

# ── Demo ───────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    cart = Cart()
    cart.add_item(CartItem("MacBook",    "Electronics", Decimal("2000"), 1))
    cart.add_item(CartItem("Headphones", "Electronics", Decimal("300"),  2))

    # Coupon 1: 10% off, capped at $150, min order $500, Electronics only
    tech_discount = (
        CouponBuilder()
        .code("TECH10")
        .reward(PercentageOffStrategy(Decimal("10"), Decimal("150")))
        .min_order(Decimal("500"))
        .category("Electronics")
        .expires_on(date(2026, 12, 31))
        .per_user_limit(1)
        .global_limit(1000)
        .build()
    )

    # Coupon 2: Free shipping
    free_ship = CouponBuilder().code("FREESHIP").reward(FreeShippingStrategy()).build()

    service = CouponService()
    service.register_coupon(tech_discount)
    service.register_coupon(free_ship)

    print("Cart total:", cart.get_total())
    service.apply_coupon("TECH10",   cart, "user-1")  # Discount: 150 (capped)
    service.apply_coupon("FREESHIP", cart, "user-1")  # Discount: 9.99

    # Second use by same user → fails per-user limit
    try:
        service.apply_coupon("TECH10", cart, "user-1")
    except RuntimeError as e:
        print("Expected rejection:", e)
```

---

## Phase 6: Trade-offs and Extensions

### Trade-offs

| Decision | Choice | Alternative | Reason |
|---|---|---|---|
| Validation model | Chain of Responsibility | All validators in one method | Chain enables adding new rules without modifying existing code |
| Usage counter | `AtomicInteger` (in-memory) | Redis `INCR` | In-memory is fine for single-instance; Redis is required for distributed |
| Coupon construction | Builder pattern | Constructor with many parameters | Builder avoids 7-parameter constructors that are unreadable |
| Stacking behavior | Sequential: each coupon applied to running total | Independent: each applies to original total | Sequential matches most real e-commerce behavior (and is more favorable to merchant) |

### Extensions

**Global limit with Redis (distributed, exact enforcement):**
```python
# In GlobalLimitValidator or CouponService, replace the in-memory counter with:
key   = f"coupon:usage:{coupon.code}"
count = redis_client.incr(key)
if count > coupon.max_global_usage:
    redis_client.decr(key)  # rollback
    return ValidationResult.fail("Coupon usage limit reached.")
# This is still not perfectly atomic under extreme concurrency;
# use a Lua script for true atomicity:
# EVAL "local c=redis.call('INCR',KEYS[1]); if c>tonumber(ARGV[1]) then redis.call('DECR',KEYS[1]); return 0 end; return 1"
```

**Composite constraint (OR logic):**
```python
class OrCompositeValidator(CouponValidator):
    def __init__(self, branches):
        super().__init__()
        self._branches = branches

    def validate(self, cart, user_id, coupon):
        for v in self._branches:
            if v.validate(cart, user_id, coupon).valid:
                return self._validate_next(cart, user_id, coupon)
        return ValidationResult.fail("None of the required conditions met.")
```

**BOGO extension:**
`BuyXGetYStrategy` is already implemented above. Register it:
```python
CouponBuilder().code("B2G1-ELEC") \
    .reward(BuyXGetYStrategy(2, 1, "Electronics")) \
    .category("Electronics") \
    .build()
```

---

## Interviewer Follow-Up Questions

- "What are the core entities?" → `Coupon` (code, type, value, expiry, usage_limit, current_usage, conditions), `CouponType` (PERCENTAGE_OFF, FIXED_AMOUNT_OFF, FREE_SHIPPING, BUY_X_GET_Y), `CouponUsage` (coupon_id, user_id, order_id, applied_at), `DiscountCalculator`. `Coupon` has conditions: minimum order value, applicable product categories, user eligibility (new users only, specific user IDs).
- "How do you apply a coupon code without race conditions (two users using the same limited coupon simultaneously)?" → Atomic increment with limit check: `UPDATE coupons SET current_usage = current_usage + 1 WHERE code = X AND current_usage < usage_limit`. Rows affected = 1 → valid and claimed. Rows affected = 0 → limit reached. This is a single atomic DB operation — no race condition. Never read the count then write separately — the read-modify-write gap allows two concurrent claimers to both succeed.
- "How do you implement the discount calculation for different coupon types?" → Strategy pattern: `DiscountStrategy` interface with `calculate(cart, coupon) → discount_amount`. `PercentageDiscount.calculate()` returns `cart.total * coupon.value / 100` (capped at max discount). `FixedAmountDiscount.calculate()` returns `min(coupon.value, cart.total)`. `FreeShipping.calculate()` returns `cart.shipping_fee`. `BuyXGetY.calculate()` finds qualifying items and returns the free item's price. `Coupon` holds a reference to its strategy. Adding a new discount type = one new class.
- "A coupon is 'first-time users only'. How do you enforce this?" → Condition check before applying: `CouponValidator.validate(coupon, user, cart)` returns `ValidationResult`. `FirstTimeUserCondition.check(user)`: query `orders` table for `COUNT(*) WHERE user_id = X AND status = COMPLETED`. If count > 0 → user is not first-time → validation fails. Conditions are composable: `coupon.conditions = [FirstTimeUserCondition, MinOrderAmountCondition(50)]`. All conditions must pass. This is the Specification or Chain of Responsibility pattern for validation.
- "How do you prevent a coupon code from being shared and used by multiple accounts?" → Depends on business intent. Options: (1) Per-user coupon codes (one code issued per user, stored in `user_coupons` table) — can't be shared because the code is tied to a specific user_id. (2) Global code with per-user usage limit: `CouponUsage` table tracks which users used it — `SELECT COUNT(*) WHERE coupon_id = X AND user_id = Y`. If count > 0 → already used. (3) Rate limiting by account for global codes. Most e-commerce platforms use option 2 for marketing coupons and option 1 for personalized offers.
