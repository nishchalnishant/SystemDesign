> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a Coupon/Discount System — tests complex business rule validation and compounding mathematical operations using patterns.
>
> **Key concepts:**
> - Core Entities: `Cart`, `Item`, `Coupon`, `DiscountResult`.
> - Strategy Pattern (Discount Type): `PercentageDiscount`, `FlatDiscount`, `BOGODiscount` (Buy One Get One).
> - Chain of Responsibility (Validation): Before applying a coupon, it must pass a chain of checks: `ExpirationValidator` -> `MinimumCartValueValidator` -> `UserEligibilityValidator`.
> - Composite Pattern (Stacking): If users can apply multiple coupons, create a `CompositeCoupon` that contains a list of coupons and applies them sequentially to the cart total.
>
> **Key takeaway:** E-commerce pricing rules change daily. Hardcoding `if (coupon.equals("SUMMER50"))` is an instant fail. Use the Strategy pattern so the Marketing team can configure new coupons via database rows, mapped to your generic strategies.

---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, coupon-system, composite, chain-of-responsibility, strategy]
---
# Design Coupon System

> **Difficulty**: Medium
> **Asked at**: Amazon, Shopify, DoorDash
> **Key Patterns**: Composite (coupon stacking), Chain of Responsibility (validation), Strategy (discount type)

---

## Understanding the Problem

Design a coupon and discount system for an e-commerce platform that supports percentage, fixed-amount, and free-shipping discounts, with validation rules and stacking limits.

---

## Clarifying Questions

**You**: "What types of discounts do we support?"
**Interviewer**: "Percentage off, fixed amount off, free shipping."

**You**: "Can multiple coupons be applied to a single order?"
**Interviewer**: "At most one product coupon and one shipping coupon simultaneously."

**You**: "Are there conditions on coupon usage — minimum order value, expiry, one-use-per-user?"
**Interviewer**: "Yes, all three."

**You**: "Who creates coupons — admin only, or can we auto-generate?"
**Interviewer**: "Admin creates. Auto-generation is a follow-up."

**You**: "Can a coupon be for a specific product or category?"
**Interviewer**: "Yes — scope can be order-wide, category, or specific product."

---

## Final Requirements

**In scope:**
1. Coupon types: PERCENTAGE, FIXED_AMOUNT, FREE_SHIPPING
2. Validation: expiry date, minimum order value, per-user usage limit
3. Scope: order-wide, per-category, per-product
4. At most one product coupon + one shipping coupon per order
5. Apply discounts to the order total; return itemized breakdown

**Out of scope:**
- Auto-generation (follow-up)
- Flash sales / time-limited stacking
- Loyalty points integration
- Fraud detection

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|---------------|
| `Coupon` | Code, type, value, scope, validity dates, usage limits |
| `DiscountStrategy` (abstract) | Computes discount amount from order |
| `PercentageDiscount / FixedDiscount / FreeShippingDiscount` | Concrete strategies |
| `CouponValidator` | Chain: checks expiry → min order → user limit → scope |
| `ValidationRule` (abstract) | Single validation step in chain |
| `Order` | Items, subtotal, shipping cost |
| `CouponService` | Applies and validates coupons; returns DiscountResult |
| `UserCouponUsage` | Tracks how many times a user used each coupon |

---

## Class Design

### Coupon

| Requirement | What Coupon must track |
|-------------|----------------------|
| "Validation: expiry, min order, user limit" | expires_at, min_order_value, max_uses_per_user |
| "Scope: order, category, product" | scope: CouponScope, scope_id: Optional[str] |
| "Discount type" | discount_type: DiscountType, discount_value: float |

```
class Coupon:
- code: str
- discount_type: DiscountType
- discount_value: float         # e.g., 20 for 20% or $20
- scope: CouponScope            # ORDER, CATEGORY, PRODUCT
- scope_id: Optional[str]       # category_id or product_id
- min_order_value: float
- max_uses_per_user: int
- total_usage_limit: Optional[int]
- expires_at: datetime
- is_active: bool
```

### DiscountStrategy (Strategy)

```
class DiscountStrategy (abstract):
+ calculate(coupon: Coupon, order: Order) -> float  # returns discount amount

class PercentageDiscount(DiscountStrategy):
+ calculate(coupon, order) -> float  # order.applicable_subtotal * coupon.discount_value / 100

class FixedDiscount(DiscountStrategy):
+ calculate(coupon, order) -> float  # min(coupon.discount_value, order.applicable_subtotal)

class FreeShippingDiscount(DiscountStrategy):
+ calculate(coupon, order) -> float  # order.shipping_cost
```

### ValidationRule (Chain of Responsibility)

```
class ValidationRule (abstract):
- next_rule: Optional[ValidationRule]

+ set_next(rule: ValidationRule) -> ValidationRule
+ validate(coupon: Coupon, order: Order, user_id: str) -> ValidationResult

class ExpiryRule(ValidationRule): ...
class MinOrderRule(ValidationRule): ...
class UserLimitRule(ValidationRule): ...
class ScopeRule(ValidationRule): ...
```

### CouponService

```
class CouponService:
- coupon_repo: CouponRepository
- usage_repo: UserCouponUsageRepository
- validator_chain: ValidationRule
- strategies: dict[DiscountType, DiscountStrategy]

+ apply_coupon(code: str, order: Order, user_id: str) -> DiscountResult
+ validate_coupon(code: str, order: Order, user_id: str) -> ValidationResult
```

---

## Implementation

### Core Method: `apply_coupon`

**Core logic:**
1. Fetch coupon by code
2. Run validation chain (expiry, min order, user limit, scope)
3. Select discount strategy by type
4. Calculate discount amount
5. Check stacking: if SHIPPING coupon, check no other SHIPPING coupon applied; same for PRODUCT
6. Record usage
7. Return DiscountResult with breakdown

**Edge cases:**
- Coupon not found → error
- Validation fails → return which rule failed
- Stacking violation → reject second coupon of same type

```java
public DiscountResult applyCoupon(String code, Order order, String userId) {
    Coupon coupon = couponRepo.findByCode(code);
    if (coupon == null) {
        return DiscountResult.error("Coupon not found");
    }

    ValidationResult validation = validatorChain.validate(coupon, order, userId);
    if (!validation.isValid()) {
        return DiscountResult.error(validation.getReason());
    }

    // Stacking check
    if (coupon.getDiscountType() == DiscountType.FREE_SHIPPING) {
        if (order.hasShippingCoupon()) {
            return DiscountResult.error("Only one shipping coupon allowed");
        }
    } else {
        if (order.hasProductCoupon()) {
            return DiscountResult.error("Only one product coupon allowed");
        }
    }

    DiscountStrategy strategy = strategies.get(coupon.getDiscountType());
    double discountAmount = strategy.calculate(coupon, order);

    // Record usage
    usageRepo.increment(userId, coupon.getCode());

    return new DiscountResult(
        code,
        discountAmount,
        order.getTotal() - discountAmount
    );
}
```

### Validation Chain construction

```java
public ValidationRule buildValidatorChain() {
    ValidationRule expiry = new ExpiryRule();
    ValidationRule minOrder = new MinOrderRule();
    ValidationRule userLimit = new UserLimitRule(usageRepo);
    ValidationRule scope = new ScopeRule();

    expiry.setNext(minOrder).setNext(userLimit).setNext(scope);
    return expiry;
}

// Base class
public abstract class ValidationRule {
    protected ValidationRule nextRule;

    public ValidationRule setNext(ValidationRule rule) {
        this.nextRule = rule;
        return rule;
    }

    public ValidationResult validate(Coupon coupon, Order order, String userId) {
        if (nextRule != null) {
            return nextRule.validate(coupon, order, userId);
        }
        return ValidationResult.ok();
    }
}

public class ExpiryRule extends ValidationRule {
    @Override
    public ValidationResult validate(Coupon coupon, Order order, String userId) {
        if (LocalDateTime.now().isAfter(coupon.getExpiresAt())) {
            return ValidationResult.fail("Coupon has expired");
        }
        return super.validate(coupon, order, userId);
    }
}

public class MinOrderRule extends ValidationRule {
    @Override
    public ValidationResult validate(Coupon coupon, Order order, String userId) {
        if (order.getSubtotal() < coupon.getMinOrderValue()) {
            return ValidationResult.fail(
                String.format("Minimum order value $%.2f required", coupon.getMinOrderValue())
            );
        }
        return super.validate(coupon, order, userId);
    }
}
```

### PercentageDiscount with scope

```java
public class PercentageDiscount implements DiscountStrategy {
    @Override
    public double calculate(Coupon coupon, Order order) {
        double applicable = getApplicableAmount(coupon, order);
        return Math.round(applicable * coupon.getDiscountValue() / 100.0 * 100.0) / 100.0;
    }

    private double getApplicableAmount(Coupon coupon, Order order) {
        switch (coupon.getScope()) {
            case ORDER:
                return order.getSubtotal();
            case CATEGORY:
                return order.getItems().stream()
                    .filter(item -> item.getCategoryId().equals(coupon.getScopeId()))
                    .mapToDouble(item -> item.getPrice() * item.getQuantity())
                    .sum();
            case PRODUCT:
                return order.getItems().stream()
                    .filter(item -> item.getProductId().equals(coupon.getScopeId()))
                    .mapToDouble(item -> item.getPrice() * item.getQuantity())
                    .sum();
            default:
                return 0;
        }
    }
}
```

---

## Verification

```
Order: subtotal=$150, shipping=$10
User: alice, usage of "SAVE20" = 0

Coupon "SAVE20":
  type=PERCENTAGE, value=20
  min_order_value=$100, max_uses_per_user=1
  expires_at=2027-01-01, scope=ORDER

apply_coupon("SAVE20", order, "alice"):
  coupon found ✓
  ExpiryRule: now < 2027-01-01 ✓
  MinOrderRule: 150 >= 100 ✓
  UserLimitRule: usage=0 < max=1 ✓
  ScopeRule: scope=ORDER, no scope_id needed ✓
  strategy = PercentageDiscount
  discount = 150 * 20 / 100 = $30
  usage_repo.increment("alice", "SAVE20")
  return DiscountResult(discount=$30, new_total=$130)
```

---

## Deep Dive & Extensibility

### 1. "How would you add BOGO (buy one get one free)?"

Add `BOGODiscount(DiscountStrategy)`:

```java
public class BOGODiscount implements DiscountStrategy {
    @Override
    public double calculate(Coupon coupon, Order order) {
        // Find items matching scope, sort by price desc
        List<Item> matching = order.getItems().stream()
            .filter(item -> matchesScope(coupon, item))
            .sorted(Comparator.comparingDouble(Item::getPrice).reversed())
            .collect(Collectors.toList());

        // Every other item is free
        double totalFree = 0;
        for (int i = 0; i < matching.size(); i++) {
            if (i % 2 == 1) {
                totalFree += matching.get(i).getPrice();
            }
        }
        return totalFree;
    }
}
```

Register `DiscountType.BOGO → BOGODiscount` in the strategies dict. No other changes.

### 2. "How would you add a 'first order only' restriction?"

Add a new `ValidationRule`:

```java
public class FirstOrderRule extends ValidationRule {
    private final OrderRepository orderRepo;

    public FirstOrderRule(OrderRepository orderRepo) {
        this.orderRepo = orderRepo;
    }

    @Override
    public ValidationResult validate(Coupon coupon, Order order, String userId) {
        if (coupon.requiresFirstOrder()) {
            int count = orderRepo.countCompletedOrders(userId);
            if (count > 0) {
                return ValidationResult.fail("Coupon valid for first order only");
            }
        }
        return super.validate(coupon, order, userId);
    }
}
```

Insert into the chain: `user_limit.set_next(first_order).set_next(scope)`. Open/Closed — no existing rules change.

### 3. "How would you prevent coupon abuse (multiple accounts)?"

- Device fingerprinting: tie coupon usage to device ID, not just user ID
- Email domain check: one use per email domain
- Payment method deduplication: same card = same user

```java
public class PaymentDedupRule extends ValidationRule {
    private final UserCouponUsageRepository usageRepo;

    public PaymentDedupRule(UserCouponUsageRepository usageRepo) {
        this.usageRepo = usageRepo;
    }

    @Override
    public ValidationResult validate(Coupon coupon, Order order, String userId) {
        if (coupon.isLimitPerPaymentMethod()) {
            int cardHash = order.getPaymentMethodLast4().hashCode();
            int usage = usageRepo.getByCard(coupon.getCode(), cardHash);
            if (usage >= coupon.getMaxUsesPerUser()) {
                return ValidationResult.fail("Coupon limit reached for this payment method");
            }
        }
        return super.validate(coupon, order, userId);
    }
}
```

### 4. "How would you handle race conditions on total usage limit?"

Two users simultaneously using the last slot of a coupon with `total_usage_limit=1`:

```java
// DB-level CAS
// UPDATE coupons
// SET total_used = total_used + 1
// WHERE code = ? AND total_used < total_usage_limit

// If rows affected == 0: coupon exhausted
int rowsAffected = jdbcTemplate.update(
    "UPDATE coupons SET total_used = total_used + 1 " +
    "WHERE code = ? AND total_used < total_usage_limit",
    code
);
if (rowsAffected == 0) {
    throw new CouponExhaustedException("Coupon exhausted");
}
```

Or use Redis INCR and compare against limit — atomic, no race condition.

---

## Interviewer Questions by Level

**Junior**: Coupon entity with type and value. `apply_coupon` that computes discount. Basic expiry check. Return updated total.

**Mid-level**: Strategy for discount types. Chain of Responsibility for validation rules. Stacking constraint (one product + one shipping). Scope filtering (category/product-level discount).

**Senior**: Composite coupon stacking with explicit stacking policy. BOGO as new strategy requiring no changes to existing code. Race condition on usage limit solved with CAS. Abuse prevention via payment method deduplication.

---

## Common Interview Questions

- **Q**: Why Chain of Responsibility for validation instead of a big `if` block?
  **A**: Each rule is independent and reorderable. New rules (e.g., FirstOrder, PaymentDedup) are added as new classes without touching existing ones. The chain is built at startup and injected — easy to test each rule in isolation.

- **Q**: How do you prevent applying the same coupon twice by the same user?
  **A**: `UserLimitRule` checks `usage_repo.get(user_id, coupon.code)`. Usage is incremented only after successful application. The DB increment is atomic — use transactions.

- **Q**: What's the difference between scope ORDER, CATEGORY, and PRODUCT?
  **A**: ORDER applies discount to the full subtotal. CATEGORY applies only to items in the specified category. PRODUCT applies only to the specific product. The `getApplicableAmount` method filters accordingly.

- **Q**: How do you handle a coupon that makes the order total negative?
  **A**: Cap discount at the order total: `discount = min(calculated_discount, order.subtotal)`. Coupon value can never exceed what the customer owes.

- **Q**: How would you add time-based coupons (flash sale — valid only 1–3 PM)?
  **A**: Add `valid_time_window: Optional[Tuple[time, time]]` to Coupon. Add `TimeWindowRule` to the chain — checks `time_start <= current_time <= time_end`.

---

## Related

**Patterns applied here**

- [Composite Pattern](../../03-design-patterns/02-structural/composite-pattern.md)
- [Chain of Responsibility Pattern](../../03-design-patterns/03-behavioral/chain-of-responsibility.md)
- [Strategy Pattern](../../03-design-patterns/03-behavioral/strategy-pattern.md)
- [Interpreter Pattern](../../03-design-patterns/03-behavioral/interpreter-pattern.md) — evaluate coupon rule expressions (`CART_TOTAL > 500 AND FIRST_ORDER`)

**SOLID focus**: [Open/Closed](../../02-solid-principles/02-open-closed.md) · [Liskov Substitution](../../02-solid-principles/03-liskov-substitution.md)

**Practice next**

- [Design Inventory Management](../03-domain-specific/24-design-inventory-management.md)
- [Design Splitwise](../01-core-problems/05-design-splitwise.md)

Rule composition and pricing arithmetic overlap.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
