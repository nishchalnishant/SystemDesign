# #7 Hotel reservation system

Below is a complete, time-boxed 1-hour interview answer for designing a Hotel Reservation System (think Booking.com / Expedia core features). It follows your preferred pattern: clarify → FR/NFR → APIs → high-level design → deep dives (availability, booking, payments, scaling) → BoE sizing → trade-offs & wrap up. Use it as a script in an interview — speak the minutes noted.

***

## 0 – 5 min — Problem recap, scope & key assumptions (set the stage)

<br>

Quickly restate and confirm scope so interviewer and you are aligned.

<br>

Goal: A system that lets users search hotels, view availability & prices, hold/confirm bookings, manage cancellations/changes, and supports hosts (inventory) and payments. Provide real-time availability, prevent double-booking, support promotions/taxes, and provide dashboards + billing.

<br>

Primary needs:

* Real-time availability & booking correctness (no double-booking).
* High search throughput and low latency for price/availability.
* Integration with external property systems (PMS) for large hotels.
* Support OTA features: cancellation policy, refunds, holds, payment capture.

<br>

Example assumptions (adjustable):

* 1M MAU, peak 20k searches/sec, peak 500 bookings/sec.
* 100k properties, average 50 rooms/property.
* Booking conversion \~0.5%; retention 30 days raw events.
* Latency targets: search P95 < 200 ms; booking end-to-end < 1s for availability verification, < 3s including payment.

***

## 5 – 15 min — Functional & Non-Functional Requirements

<br>

#### Functional Requirements (Must / Should / Nice)

<br>

Must

1. Search hotels by location/date/guests/filters; returns available properties with prices and room types.
2. Check availability for specific room(s) & date range.
3. Create booking: hold inventory, collect guest info, accept payment (or authorize), confirm booking.
4. Cancel / modify booking per policy.
5. Host management: inventory CRUD, rates, restrictions, sync with Property Management Systems (PMS) via APIs/feeds.
6. Pricing rules: support seasonal rates, dynamic pricing, taxes, fees, promotions, and currency conversion.
7. Notifications: emails/SMS for confirmations, reminders, cancellations.
8. Reporting & reconciliation: payouts to hosts, billing, chargebacks handling.

<br>

Should

* Prepaid & pay-at-hotel flows; partial payments; refunds.
* Waitlist & soft-hold (short-lived holds).
* Loyalty points & coupon codes.

<br>

Nice-to-have

* Room map visualization (inventory by room number), multi-property corporate booking tools, recommendations.

<br>

#### Non-Functional Requirements (NFR)

* Performance: Search P95 < 200 ms; booking path fast & reliable.
* Consistency: Strong correctness for booking inventory (no double-booking).
* Scalability: scale search horizontally; booking scale with partitions.
* Availability: 99.95% for reads; 99.9% for booking operations.
* Durability: booking data persisted and replicated; audit logs.
* Security: PCI-DSS for card handling, TLS, RBAC for host dashboards.
* Privacy: PII protection, GDPR delete support.
* Observability: metrics for search latency, booking success, inventory conflicts, payments.
* Cost/operability: choose managed services where practical.

***

## 15 – 25 min — External APIs & Data model (contract)

<br>

#### Key APIs (REST/gRPC)

```
GET  /search?loc=Paris&checkin=2025-10-10&checkout=2025-10-13&guests=2
GET  /hotel/{hotel_id}?checkin=&checkout=&rooms=
POST /booking/create
    {hotel_id, room_type_id, checkin, checkout, guests, guest_info, payment_method, hold_id?}
POST /booking/confirm {booking_id, payment_method}
POST /booking/cancel {booking_id, reason}
POST /inventory/update (host) {hotel_id, room_type_id, rates, availability}
GET  /booking/{id}
POST /payouts/process (admin)
```

#### Simplified data model (high-level)

* Hotel: id, name, location, timezone, policies, pms\_integration\_info
* RoomType: id, hotel\_id, capacity, amenities
* RatePlan: id, room\_type\_id, price\_rules, cancellation\_policy, currency
* Inventory: room\_type\_id, date, available\_count, holds\_count
* Booking: id, hotel\_id, room\_type\_id, checkin, checkout, guest\_info, status (held/confirmed/cancelled), price\_breakdown, payment\_info, created\_at
* Hold: hold\_id, room\_type\_id, start\_date, end\_date, expires\_at, reserved\_count, booking\_id (if consumed)

***

## 25 – 35 min — High-Level Architecture

```
Clients (web/mobile) 
   |
 CDN/API Gateway (auth, rate-limit, geo)
   |
 Frontend Services (stateless) —> Cache (Redis)
   |
 +-------------------------------+
 | Search Service (read-optimized)|
 +-------------------------------+
   |
 Booking Service (transactional) <-- Payment Gateway
   |
 Inventory Service (authoritative) <--> Host Portal / PMS Integrations
   |
 Event Bus (Kafka)  -> downstream: Notification, Billing, Reconciliation, Analytics
   |
 Data Lake / OLAP (for reports)
```

Key components explained

* Search Service: serves search queries from precomputed indexes and caches (Elasticsearch/Opensearch or custom search over materialized availability). It returns candidate hotels + price info (cached per room/date snapshot).
* Inventory Service: authoritative store of per-room\_type per-date availability. Must support transactional updates and atomic holds/commits. Implemented on a strong-consistency DB (e.g., RDBMS) or a partitioned service with per-roomtype sharding.
* Booking Service: orchestrates hold → payment authorization → convert hold to booking (confirm) or release on failure. Uses distributed transactions or compensation patterns.
* Hold mechanism: short-lived holds (e.g., 5–15 minutes) to avoid double-booking during payment; holds write into inventory atomically.
* Payment Gateway: integrate with PCI compliant processors; prefer tokenization & external vault.
* Event bus / Kafka: async flows: notify host, update analytics, process payouts.
* PMS adapters: for large hotels, sync availability and receive reservations/cancellations.

***

## 35 – 45 min — Booking flow & correctness (deep dive)

<br>

#### Flow: Search → Book (step by step)

1. User searches → Search Service queries availability index (Elasticsearch/Redis) and returns options.
2. User selects room & clicks book → Booking Service requests a Hold from Inventory Service for the requested room\_type + date range (atomic).
   * Hold operation: decrement available\_count by requested rooms and increment holds\_count, create Hold record with expiry.
   * Inventory writes must be atomic per room\_type/date (use DB transaction or compare-and-swap).
3. Booking Service returns hold\_id to frontend; client proceeds to payment.
4. Payment: Payment Gateway authorizes card (auth-only) or charges depending on rateplan.
5. On success: Booking Service commits hold → creates Booking record with status=CONFIRMED; reduces holds\_count, persists payment info (token), emits booking event.
6. On payment failure or hold expiry: Booking Service releases hold (increment available\_count) and notifies user.

<br>

#### Concurrency & correctness strategies

* Strongly consistent inventory updates:
  * Option A: Centralized DB per partition (room\_type/date) with row-level locking and transactions (ACID). Suitable for moderate scale.
  * Option B: Distributed lock per (hotel\_id, room\_type\_id) with optimistic concurrency (compare-and-swap using version numbers) or lightweight DB transactions.
  * Avoid long locks: use short holds with TTL and background reclaimers.
* Idempotency: Booking API includes client-generated idempotency keys so retrying a booking doesn’t double-charge or double-book.
* Eventual reconciliation: periodic batch job compares bookings vs inventory and repairs mismatches (audit log + manual alerts).
* PMS sync: for hotels with external PMS, reconcile inbound reservations and pushes to keep inventory authoritative.

<br>

#### Handling edge cases

* Late arrival: payment delays and hold expiration — inform user; offer re-check availability.
* Overbooking: allow controlled overbooking for hotels that tolerate it; treat as business rule.
* Race on last room: only one hold should succeed—ensure atomic decrement and check >0 before success.

***

## 45 – 50 min — Pricing, promotions & rate engines

* Rate engine: evaluate pricing rules (base price, seasonal multipliers, occupancy-driven dynamic pricing, promo codes, taxes). Compute price breakdown on search and lock price at hold time (or accept variable price with reprice rules).
* Price guarantee / reprice: show price at hold time; if price changes pre-confirmation, apply business rule: honor displayed price for hold window or reprice with user consent.
* Coupons & inventory constraints: coupon validation is done at booking time; constraints (limited redemption) must be atomic (use separate counters with CAS).

***

## 50 – 55 min — Scaling, caching & BoE estimates

<br>

#### Sizing (example)

<br>

Assume peak 20k searches/sec and 500 bookings/sec.

<br>

Search

* Use Elasticsearch cluster sharded by geo + hotel index. Cache hot queries & tile results in Redis CDN.
* Cache search results per (loc, dates) for \~30s–2min TTL; use CDN for static assets.

<br>

Inventory & Booking

* Partition inventory by hotel\_id % N across Inventory DB instances to scale writes/holds. Choose N to keep partition load manageable.
* For 500 bookings/sec, if each booking touches \~3 date rows (avg nights), expect \~1.5k inventory writes/sec. A few DB instances with transaction throughput \~k/s suffice.

<br>

Storage

* Booking records: 500 bookings/sec -> \~43.2M bookings/day? Wait compute properly:
  * 500 bookings/sec × 3600 × 24 = 43,200,000 bookings/day — that’s huge; realistic numbers likely lower. Adjust to interviewer numbers.
* Use compression & archive older bookings to cold storage after retention period.

<br>

Example quick math (moderate scale):

* 500 bookings/sec = 43.2M/day; at 1 KB/booking = \~43.2 GB/day raw. With replication factor 3 => \~129.6 GB/day.

<br>

(Always adapt numbers per interviewer prompts.)

***

## 55 – 58 min — Observability, testing, security & ops

<br>

Monitoring

* Metrics: search latency, cache hit ratio, holds/sec, confirm/sec, booking failure rates, payment success, inventory mismatches, reconciliation errors.
* Tracing: end-to-end tracing from search → hold → payment → confirm.

<br>

Testing

* Load tests for search and booking flows (simulate race on last room).
* Chaos tests: fail inventory DB; validate reconciliation.

<br>

Security / Compliance

* PCI compliance: do not store raw card numbers; use tokenization/payments provider. TLS everywhere, audit logs, RBAC, GDPR data deletion for bookings upon request (subject to business constraints).

***

## 58 – 60 min — Trade-offs, evolution & summary (wrap-up)

<br>

#### Key trade-offs

* Strong consistency (no double-booking) vs latency & scale: prefer strong consistency for booking path; search can be eventually consistent with caches.
* Centralized DB (simplicity) vs sharded inventory service (scale + complexity).
* Short holds reduce double-booking but may decrease conversion (user may abandon). Balance hold TTL.

<br>

#### Evolution path

1. MVP: single DB inventory (ACID), simple search + cache, payment gateway integration.
2. Scale: shard inventory, introduce search index (Elasticsearch), caching layer, CDN.
3. Add PMS integrations, advanced pricing engine, ML for recommendations.
4. Geo-distributed active/active for global low-latency.

<br>

#### One-line summary

<br>

Build search as a highly cacheable read path; implement inventory & booking as the authoritative, strongly-consistent transactional core using short atomic holds + idempotent booking confirms; use async streams for notifications, reconciliation, and analytics — balancing correctness for bookings with scalability for search.

***

If you want, I can:

* convert this into a 5-slide interview deck (one slide per major phase), or
* produce a detailed sequence diagram for the booking flow (hold → payment → confirm), or
* run BoE capacity math using your specific scale numbers (events/sec, properties, avg nights). Which should I do next?
