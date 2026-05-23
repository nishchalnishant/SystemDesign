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

## Phase 5: Key Java Implementation

```java
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// ── Cart Model ─────────────────────────────────────────────────────────────

class CartItem {
    final String name;
    final String category;
    final BigDecimal price;
    final int quantity;

    CartItem(String name, String category, BigDecimal price, int qty) {
        this.name = name; this.category = category;
        this.price = price; this.quantity = qty;
    }
}

class Cart {
    private final List<CartItem> items     = new ArrayList<>();
    private BigDecimal shippingCost = new BigDecimal("9.99");

    void addItem(CartItem item) { items.add(item); }

    BigDecimal getTotal() {
        return items.stream()
                .map(i -> i.price.multiply(BigDecimal.valueOf(i.quantity)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    BigDecimal getShippingCost()  { return shippingCost; }
    List<CartItem> getItems()     { return Collections.unmodifiableList(items); }

    boolean hasCategory(String cat) {
        return items.stream().anyMatch(i -> i.category.equalsIgnoreCase(cat));
    }

    List<CartItem> getItemsByCategory(String cat) {
        return items.stream().filter(i -> i.category.equalsIgnoreCase(cat)).toList();
    }
}

// ── Validation ─────────────────────────────────────────────────────────────

class ValidationResult {
    static final ValidationResult OK = new ValidationResult(true, null);
    final boolean valid;
    final String reason;
    ValidationResult(boolean valid, String reason) { this.valid = valid; this.reason = reason; }
    static ValidationResult fail(String reason)    { return new ValidationResult(false, reason); }
}

// Chain of Responsibility: each validator delegates to next if own check passes
abstract class CouponValidator {
    private CouponValidator next;

    CouponValidator setNext(CouponValidator n) { this.next = n; return n; }

    ValidationResult validateNext(Cart cart, String userId, Coupon coupon) {
        return next != null ? next.validate(cart, userId, coupon) : ValidationResult.OK;
    }

    abstract ValidationResult validate(Cart cart, String userId, Coupon coupon);
}

class ExpiryValidator extends CouponValidator {
    public ValidationResult validate(Cart cart, String userId, Coupon coupon) {
        if (coupon.expiryDate != null && LocalDate.now().isAfter(coupon.expiryDate))
            return ValidationResult.fail("Coupon expired on " + coupon.expiryDate);
        return validateNext(cart, userId, coupon);
    }
}

class MinOrderValidator extends CouponValidator {
    public ValidationResult validate(Cart cart, String userId, Coupon coupon) {
        if (coupon.minOrderValue != null && cart.getTotal().compareTo(coupon.minOrderValue) < 0)
            return ValidationResult.fail("Minimum order value: " + coupon.minOrderValue);
        return validateNext(cart, userId, coupon);
    }
}

class CategoryValidator extends CouponValidator {
    public ValidationResult validate(Cart cart, String userId, Coupon coupon) {
        if (coupon.requiredCategory != null && !cart.hasCategory(coupon.requiredCategory))
            return ValidationResult.fail("Coupon valid only for: " + coupon.requiredCategory);
        return validateNext(cart, userId, coupon);
    }
}

class GlobalLimitValidator extends CouponValidator {
    public ValidationResult validate(Cart cart, String userId, Coupon coupon) {
        if (coupon.maxGlobalUsage > 0 && coupon.globalUsageCount.get() >= coupon.maxGlobalUsage)
            return ValidationResult.fail("Coupon usage limit reached.");
        return validateNext(cart, userId, coupon);
    }
}

class PerUserLimitValidator extends CouponValidator {
    public ValidationResult validate(Cart cart, String userId, Coupon coupon) {
        int used = coupon.perUserUsage.getOrDefault(userId, 0);
        if (coupon.maxPerUserUsage > 0 && used >= coupon.maxPerUserUsage)
            return ValidationResult.fail("You have already used this coupon " + used + " time(s).");
        return validateNext(cart, userId, coupon);
    }
}

// ── Discount Strategies ────────────────────────────────────────────────────

interface DiscountStrategy {
    BigDecimal calculate(Cart cart);
}

class PercentageOffStrategy implements DiscountStrategy {
    private final BigDecimal percentage;
    private final BigDecimal maxDiscount; // cap; null = uncapped

    PercentageOffStrategy(BigDecimal percentage, BigDecimal maxDiscount) {
        this.percentage  = percentage;
        this.maxDiscount = maxDiscount;
    }

    public BigDecimal calculate(Cart cart) {
        BigDecimal discount = cart.getTotal().multiply(percentage).divide(BigDecimal.valueOf(100));
        if (maxDiscount != null) discount = discount.min(maxDiscount);
        return discount;
    }
}

class FlatOffStrategy implements DiscountStrategy {
    private final BigDecimal amount;
    FlatOffStrategy(BigDecimal amount) { this.amount = amount; }
    public BigDecimal calculate(Cart cart) {
        return amount.min(cart.getTotal()); // cannot exceed cart total
    }
}

class FreeShippingStrategy implements DiscountStrategy {
    public BigDecimal calculate(Cart cart) { return cart.getShippingCost(); }
}

class BuyXGetYStrategy implements DiscountStrategy {
    private final int buyX;
    private final int getY;
    private final String category;

    BuyXGetYStrategy(int buyX, int getY, String category) {
        this.buyX = buyX; this.getY = getY; this.category = category;
    }

    public BigDecimal calculate(Cart cart) {
        List<CartItem> items = cart.getItemsByCategory(category);
        // Sort ascending by price; free items are the cheapest
        List<BigDecimal> prices = items.stream()
                .flatMap(i -> Collections.nCopies(i.quantity, i.price).stream())
                .sorted()
                .toList();
        BigDecimal discount = BigDecimal.ZERO;
        int totalQty = prices.size();
        int setSize  = buyX + getY;
        int sets     = totalQty / setSize;
        for (int s = 0; s < sets; s++) {
            // The Y cheapest items in each set are free
            for (int y = 0; y < getY; y++) {
                discount = discount.add(prices.get(s * setSize + y));
            }
        }
        return discount;
    }
}

// ── Coupon ─────────────────────────────────────────────────────────────────

class Coupon {
    final String code;
    final DiscountStrategy reward;
    private final CouponValidator validatorChain;

    // Constraint fields (read by validators)
    LocalDate expiryDate;
    BigDecimal minOrderValue;
    String requiredCategory;
    int maxGlobalUsage;
    int maxPerUserUsage;

    // Usage tracking
    final AtomicInteger globalUsageCount = new AtomicInteger(0);
    final Map<String, Integer> perUserUsage = new ConcurrentHashMap<>();

    Coupon(String code, DiscountStrategy reward, CouponValidator validatorChain) {
        this.code           = code;
        this.reward         = reward;
        this.validatorChain = validatorChain;
    }

    ValidationResult validate(Cart cart, String userId) {
        return validatorChain.validate(cart, userId, this);
    }

    BigDecimal calculateDiscount(Cart cart) {
        return reward.calculate(cart);
    }

    // Atomically increment usage counters after successful application
    void recordUsage(String userId) {
        globalUsageCount.incrementAndGet();
        perUserUsage.merge(userId, 1, Integer::sum);
    }
}

// ── Coupon Builder ─────────────────────────────────────────────────────────

class CouponBuilder {
    private String code;
    private DiscountStrategy reward;
    private LocalDate expiryDate;
    private BigDecimal minOrderValue;
    private String requiredCategory;
    private int maxGlobalUsage;
    private int maxPerUserUsage;

    CouponBuilder code(String c)                 { this.code = c; return this; }
    CouponBuilder reward(DiscountStrategy r)     { this.reward = r; return this; }
    CouponBuilder expiresOn(LocalDate d)         { this.expiryDate = d; return this; }
    CouponBuilder minOrder(BigDecimal v)         { this.minOrderValue = v; return this; }
    CouponBuilder category(String cat)           { this.requiredCategory = cat; return this; }
    CouponBuilder globalLimit(int n)             { this.maxGlobalUsage = n; return this; }
    CouponBuilder perUserLimit(int n)            { this.maxPerUserUsage = n; return this; }

    Coupon build() {
        // Build chain: Expiry → MinOrder → Category → GlobalLimit → PerUserLimit
        CouponValidator head = new ExpiryValidator();
        head.setNext(new MinOrderValidator())
            .setNext(new CategoryValidator())
            .setNext(new GlobalLimitValidator())
            .setNext(new PerUserLimitValidator());

        Coupon c = new Coupon(code, reward, head);
        c.expiryDate       = expiryDate;
        c.minOrderValue    = minOrderValue;
        c.requiredCategory = requiredCategory;
        c.maxGlobalUsage   = maxGlobalUsage;
        c.maxPerUserUsage  = maxPerUserUsage;
        return c;
    }
}

// ── Coupon Service ─────────────────────────────────────────────────────────

public class CouponService {
    private final Map<String, Coupon> coupons = new HashMap<>();

    void registerCoupon(Coupon c) { coupons.put(c.code, c); }

    // Apply one coupon to cart; returns discount amount or throws
    BigDecimal applyCoupon(String code, Cart cart, String userId) {
        Coupon coupon = coupons.get(code);
        if (coupon == null) throw new IllegalArgumentException("Unknown coupon: " + code);

        ValidationResult result = coupon.validate(cart, userId);
        if (!result.valid) throw new IllegalStateException("Coupon invalid: " + result.reason);

        BigDecimal discount = coupon.calculateDiscount(cart);
        coupon.recordUsage(userId);
        System.out.printf("Coupon %s applied. Discount: %s. Final total: %s%n",
                code, discount, cart.getTotal().subtract(discount));
        return discount;
    }

    // Stack multiple coupons sequentially
    BigDecimal stackCoupons(List<String> codes, Cart cart, String userId) {
        BigDecimal totalDiscount = BigDecimal.ZERO;
        for (String code : codes) {
            try {
                totalDiscount = totalDiscount.add(applyCoupon(code, cart, userId));
            } catch (Exception e) {
                System.out.println("Skipping coupon " + code + ": " + e.getMessage());
            }
        }
        return totalDiscount;
    }
}

// ── Demo ───────────────────────────────────────────────────────────────────

class CouponDemo {
    public static void main(String[] args) {
        Cart cart = new Cart();
        cart.addItem(new CartItem("MacBook", "Electronics", new BigDecimal("2000"), 1));
        cart.addItem(new CartItem("Headphones", "Electronics", new BigDecimal("300"),  2));

        // Coupon 1: 10% off, capped at $150, min order $500, Electronics only
        Coupon techDiscount = new CouponBuilder()
                .code("TECH10")
                .reward(new PercentageOffStrategy(new BigDecimal("10"), new BigDecimal("150")))
                .minOrder(new BigDecimal("500"))
                .category("Electronics")
                .expiresOn(LocalDate.of(2026, 12, 31))
                .perUserLimit(1)
                .globalLimit(1000)
                .build();

        // Coupon 2: Free shipping
        Coupon freeShip = new CouponBuilder()
                .code("FREESHIP")
                .reward(new FreeShippingStrategy())
                .build();

        CouponService service = new CouponService();
        service.registerCoupon(techDiscount);
        service.registerCoupon(freeShip);

        System.out.println("Cart total: " + cart.getTotal());
        service.applyCoupon("TECH10", cart, "user-1");  // Discount: 150 (capped)
        service.applyCoupon("FREESHIP", cart, "user-1"); // Discount: 9.99

        // Second use by same user → fails per-user limit
        try {
            service.applyCoupon("TECH10", cart, "user-1");
        } catch (IllegalStateException e) {
            System.out.println("Expected rejection: " + e.getMessage());
        }
    }
}
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
```java
// In GlobalLimitValidator or CouponService, replace AtomicInteger with:
String key = "coupon:usage:" + coupon.code;
long count = redisTemplate.opsForValue().increment(key);
if (count > coupon.maxGlobalUsage) {
    redisTemplate.opsForValue().decrement(key); // rollback
    return ValidationResult.fail("Coupon usage limit reached.");
}
// This is still not perfectly atomic under extreme concurrency;
// use a Lua script for true atomicity:
// EVAL "local c=redis.call('INCR',KEYS[1]); if c>tonumber(ARGV[1]) then redis.call('DECR',KEYS[1]); return 0 end; return 1"
```

**Composite constraint (OR logic):**
```java
class OrCompositeValidator extends CouponValidator {
    private final List<CouponValidator> branches;
    OrCompositeValidator(List<CouponValidator> branches) { this.branches = branches; }
    public ValidationResult validate(Cart cart, String userId, Coupon coupon) {
        for (CouponValidator v : branches) {
            if (v.validate(cart, userId, coupon).valid) return validateNext(cart, userId, coupon);
        }
        return ValidationResult.fail("None of the required conditions met.");
    }
}
```

**BOGO extension:**
`BuyXGetYStrategy` is already implemented above. Register it:
```java
new CouponBuilder().code("B2G1-ELEC")
    .reward(new BuyXGetYStrategy(2, 1, "Electronics"))
    .category("Electronics")
    .build();
```
